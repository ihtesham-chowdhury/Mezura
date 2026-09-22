package rocks.ndot.widgets.instruments.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The metronome's clock. It is an audio stream, not a timer: a dedicated thread writes PCM into
 * a streaming AudioTrack and places every click at an exact sample position. A Handler or
 * coroutine delay drifts and jitters by milliseconds, which a musician hears immediately;
 * counting samples can't drift at all.
 *
 * The faces animate from the same clock: each beat's sample position is recorded, and
 * [position] maps the track's playback head back to "beats since start", so the pendulum and
 * the lights line up with what is actually coming out of the speaker.
 */
object Metronome {

    enum class Voice(val id: String, val label: String) {
        CLICK("click", "CLICK"),
        WOOD("wood", "WOOD"),
        BEEP("beep", "BEEP"),
        TICK("tick", "TICK");

        companion object {
            fun byId(id: String) = entries.firstOrNull { it.id == id } ?: CLICK
        }
    }

    const val MIN_BPM = 30
    const val MAX_BPM = 250
    private const val RATE = 48_000

    @Volatile var bpm: Int = 100
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }
    @Volatile var beats: Int = 4
        set(value) { field = value.coerceIn(1, 12) }
    @Volatile var subdivision: Int = 1
        set(value) { field = value.coerceIn(1, 4) }
    @Volatile var voice: Voice = Voice.CLICK
    @Volatile var accentFirst: Boolean = true

    @Volatile private var player: Player? = null

    val isPlaying: Boolean get() = player != null

    fun start() {
        if (player != null) return
        player = Player().also { it.start() }
    }

    fun stop() {
        player?.alive = false
        player = null
    }

    fun toggle() = if (isPlaying) stop() else start()

    /** Beats heard since the start, fractional, or null when stopped or not yet sounding. */
    fun position(): Double? = player?.position()

    /** Italian tempo markings, by the ranges most metronomes print on their scale. */
    fun tempoName(bpm: Int): String = when {
        bpm < 40 -> "Grave"
        bpm < 60 -> "Largo"
        bpm < 66 -> "Larghetto"
        bpm < 76 -> "Adagio"
        bpm < 108 -> "Andante"
        bpm < 120 -> "Moderato"
        bpm < 156 -> "Allegro"
        bpm < 176 -> "Vivace"
        bpm < 200 -> "Presto"
        else -> "Prestissimo"
    }

    // ---- sounds ----

    private enum class Kind { ACCENT, BEAT, SUB }

    private val cache = HashMap<Pair<Voice, Kind>, FloatArray>()

    private fun samples(voice: Voice, kind: Kind): FloatArray = synchronized(cache) {
        cache.getOrPut(voice to kind) { synth(voice, kind) }
    }

    private fun synth(voice: Voice, kind: Kind): FloatArray {
        val level = when (kind) { Kind.ACCENT -> 1f; Kind.BEAT -> 0.72f; Kind.SUB -> 0.38f }
        return when (voice) {
            Voice.CLICK -> render(28, level, noise = 0.45, noiseDecay = 1100.0, partials = when (kind) {
                Kind.ACCENT -> listOf(2600.0 to 0.6, 5200.0 to 0.2)
                else -> listOf(1900.0 to 0.6, 3800.0 to 0.18)
            }, decay = 150.0)
            Voice.WOOD -> render(70, level, noise = 0.25, noiseDecay = 500.0, partials = when (kind) {
                Kind.ACCENT -> listOf(1250.0 to 0.7, 2750.0 to 0.25)
                else -> listOf(960.0 to 0.7, 2150.0 to 0.25)
            }, decay = 55.0)
            Voice.BEEP -> render(60, level * 0.8f, noise = 0.0, noiseDecay = 1.0, partials = when (kind) {
                Kind.ACCENT -> listOf(1500.0 to 0.8)
                Kind.BEAT -> listOf(1000.0 to 0.8)
                Kind.SUB -> listOf(800.0 to 0.8)
            }, decay = 8.0)
            Voice.TICK -> {
                // A mechanical escapement: a dry filtered tick, and on the one a small bell.
                val tick = render(22, level, noise = 0.9, noiseDecay = 700.0, partials = listOf(3200.0 to 0.25), decay = 320.0, lowpass = 0.35)
                if (kind != Kind.ACCENT) tick else {
                    val bell = render(420, 0.5f, noise = 0.0, noiseDecay = 1.0, partials = listOf(1760.0 to 0.6, 1760.0 * 2.76 to 0.18), decay = 9.0)
                    FloatArray(bell.size) { i -> bell[i] + if (i < tick.size) tick[i] else 0f }
                }
            }
        }
    }

    private fun render(
        ms: Int, level: Float, noise: Double, noiseDecay: Double,
        partials: List<Pair<Double, Double>>, decay: Double, lowpass: Double = 0.2,
    ): FloatArray {
        val n = RATE * ms / 1000
        val out = FloatArray(n)
        val random = Random(ms + partials.size * 13)
        var lp = 0.0
        val attack = RATE * 0.001
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            lp = lp * lowpass + (random.nextDouble() * 2 - 1) * (1 - lowpass)
            var v = lp * noise * exp(-noiseDecay * t)
            val ramp = min(1.0, i / attack)
            for ((hz, amp) in partials) v += sin(2 * PI * hz * t) * amp * exp(-decay * t) * ramp
            val tail = min(1.0, (n - i) / (RATE * 0.004))
            out[i] = (v * level * tail * 0.8).toFloat()
        }
        return out
    }

    // ---- the audio thread ----

    /** A click in progress: its samples and how far into them playback has got. */
    private class Sounding(val data: FloatArray, var position: Int)

    private class Player : Thread("metronome") {
        @Volatile var alive = true
        @Volatile private var track: AudioTrack? = null

        // Ring of recent beats: the frame it sounded at, which beat it was, frames per beat.
        private val anchorFrame = LongArray(16)
        private val anchorBeat = LongArray(16)
        private val anchorLength = DoubleArray(16)
        @Volatile private var anchors = 0

        fun position(): Double? {
            val t = track ?: return null
            val count = anchors
            if (count == 0) return null
            val played = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            // Newest beat that has actually been heard.
            for (k in 0 until min(count, 16)) {
                val i = (count - 1 - k) and 15
                if (anchorFrame[i] <= played) {
                    return anchorBeat[i] + (played - anchorFrame[i]) / anchorLength[i]
                }
            }
            return null
        }

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunk = 240 // 5ms: small enough that a BPM change is heard on the very next click
            val minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer, chunk * 2 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } catch (_: Exception) {
                return
            }
            track = t
            val mix = FloatArray(chunk)
            val pcm = ShortArray(chunk)
            val sounding = ArrayList<Sounding>()
            var written = 0L
            var nextTick = RATE * 0.03
            var tick = 0L
            try {
                t.play()
                while (alive) {
                    java.util.Arrays.fill(mix, 0f)
                    // Schedule every click that falls inside this chunk, to the sample.
                    while (nextTick < written + chunk) {
                        val sub = subdivision
                        val perBar = beats * sub
                        val inBar = (tick % perBar).toInt()
                        val kind = when {
                            inBar == 0 && accentFirst && beats > 1 -> Kind.ACCENT
                            inBar % sub == 0 -> Kind.BEAT
                            else -> Kind.SUB
                        }
                        val offset = (nextTick - written).toInt().coerceIn(0, chunk - 1)
                        sounding.add(Sounding(samples(voice, kind), -offset))
                        if (inBar % sub == 0) {
                            val i = anchors and 15
                            anchorFrame[i] = nextTick.toLong()
                            anchorBeat[i] = tick / sub
                            anchorLength[i] = RATE * 60.0 / bpm
                            anchors += 1
                        }
                        tick++
                        nextTick += RATE * 60.0 / (bpm * sub)
                    }
                    val iterator = sounding.iterator()
                    while (iterator.hasNext()) {
                        val v = iterator.next()
                        for (i in 0 until chunk) {
                            val si = v.position + i
                            if (si >= 0 && si < v.data.size) mix[i] += v.data[si]
                        }
                        v.position += chunk
                        if (v.position >= v.data.size) iterator.remove()
                    }
                    for (i in 0 until chunk) {
                        pcm[i] = (mix[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    t.write(pcm, 0, chunk)
                    written += chunk
                }
            } catch (_: Exception) {
            } finally {
                runCatching { t.pause(); t.flush(); t.stop() }
                runCatching { t.release() }
                track = null
            }
        }
    }
}
