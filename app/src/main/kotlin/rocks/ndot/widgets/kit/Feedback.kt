package rocks.ndot.widgets.kit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import rocks.ndot.widgets.app.AppPrefs
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound and buzz the instruments make. Nothing is shipped as an audio file: each sound
 * is synthesised once - a short noise transient for the mechanical "contact", a few damped
 * partials for the body - and kept in a static AudioTrack, so a press plays with no decode or
 * allocation latency.
 *
 * The physical metaphor decides the sound: a plastic key tocks, a metal one clicks, a rubber
 * membrane thumps, a detent ticks.
 */
object Feedback {

    enum class Sound {
        /** A plastic key going down - the white and orange Module keys. */
        KEY_DOWN,
        /** The same key coming back up: shorter, brighter, quieter. */
        KEY_UP,
        /** A machined aluminium key - the Slab. */
        KEY_METAL,
        /** A rubber membrane over a dome switch - the Pocket. */
        KEY_RUBBER,
        /** One notch of a wheel, dial or crown. */
        DETENT,
        /** A wooden object set down - the hourglass landing after it's turned over. */
        CLACK,
        /** A timer running out: two soft bell strikes. */
        CHIME,
    }

    enum class Haptic { TICK, CLICK, HEAVY, DOUBLE, DONE }

    private const val RATE = 44_100
    private val tracks = HashMap<Sound, AudioTrack>()
    @Volatile
    private var vibrator: Vibrator? = null

    fun play(context: Context, sound: Sound) {
        if (!AppPrefs.sound(context)) return
        try {
            val track = synchronized(tracks) { tracks.getOrPut(sound) { build(synth(sound)) } }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: Exception) {
            // No output device, audio focus trouble, a locked-down ROM: silence beats a crash.
        }
    }

    fun haptic(context: Context, haptic: Haptic) {
        if (!AppPrefs.haptics(context)) return
        val v = vibrator ?: obtainVibrator(context)?.also { vibrator = it } ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (haptic) {
                Haptic.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                Haptic.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Haptic.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                Haptic.DOUBLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                Haptic.DONE -> VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40, 90, 140), -1)
            }
        } else {
            when (haptic) {
                Haptic.TICK -> VibrationEffect.createOneShot(6, 90)
                Haptic.CLICK -> VibrationEffect.createOneShot(12, 180)
                Haptic.HEAVY -> VibrationEffect.createOneShot(22, 255)
                Haptic.DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 12, 70, 12), -1)
                Haptic.DONE -> VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40, 90, 140), -1)
            }
        }
        try {
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    /** A sound and a buzz together - what nearly every press wants. */
    fun press(context: Context, sound: Sound, haptic: Haptic = Haptic.CLICK) {
        play(context, sound)
        haptic(context, haptic)
    }

    /** Builds the tracks ahead of time, so even the very first press is instant. */
    fun prewarm(context: Context) {
        val app = context.applicationContext
        Thread {
            Sound.entries.forEach { sound ->
                try {
                    synchronized(tracks) { if (sound !in tracks) tracks[sound] = build(synth(sound)) }
                } catch (_: Exception) {
                }
            }
            obtainVibrator(app)?.let { vibrator = it }
        }.start()
    }

    private fun obtainVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (_: Exception) {
        null
    }

    // ---- synthesis ----

    private class Partial(val hz: Double, val amp: Double, val decay: Double, val delay: Double = 0.0)

    private fun synth(sound: Sound): ShortArray = when (sound) {
        Sound.KEY_DOWN -> render(
            ms = 55, gain = 0.62, noiseAmp = 0.55, noiseDecay = 520.0, noiseLowpass = 0.55,
            partials = listOf(Partial(740.0, 0.55, 75.0), Partial(1880.0, 0.25, 150.0), Partial(3300.0, 0.10, 260.0)),
        )
        Sound.KEY_UP -> render(
            ms = 32, gain = 0.34, noiseAmp = 0.45, noiseDecay = 900.0, noiseLowpass = 0.35,
            partials = listOf(Partial(1320.0, 0.45, 140.0), Partial(2900.0, 0.2, 260.0)),
        )
        Sound.KEY_METAL -> render(
            ms = 42, gain = 0.55, noiseAmp = 0.5, noiseDecay = 1100.0, noiseLowpass = 0.1,
            partials = listOf(Partial(3150.0, 0.45, 120.0), Partial(5230.0, 0.25, 180.0), Partial(1560.0, 0.2, 110.0)),
        )
        Sound.KEY_RUBBER -> render(
            ms = 60, gain = 0.7, noiseAmp = 0.35, noiseDecay = 380.0, noiseLowpass = 0.82,
            partials = listOf(Partial(310.0, 0.7, 60.0), Partial(760.0, 0.25, 110.0)),
        )
        Sound.DETENT -> render(
            ms = 14, gain = 0.32, noiseAmp = 0.6, noiseDecay = 1600.0, noiseLowpass = 0.2,
            partials = listOf(Partial(4200.0, 0.4, 420.0)),
        )
        Sound.CLACK -> render(
            ms = 110, gain = 0.7, noiseAmp = 0.5, noiseDecay = 300.0, noiseLowpass = 0.7,
            partials = listOf(Partial(520.0, 0.6, 45.0), Partial(1130.0, 0.3, 70.0), Partial(2010.0, 0.12, 120.0)),
        )
        Sound.CHIME -> render(
            ms = 2200, gain = 0.42, noiseAmp = 0.0, noiseDecay = 1.0, noiseLowpass = 0.0,
            partials = listOf(
                // A bell's partials are inharmonic; these ratios are the classic ones.
                Partial(880.0, 1.0, 2.4), Partial(880.0 * 2.76, 0.45, 4.0), Partial(880.0 * 5.40, 0.2, 6.5),
                Partial(660.0, 1.0, 2.2, delay = 0.42), Partial(660.0 * 2.76, 0.45, 3.8, delay = 0.42),
                Partial(660.0 * 5.40, 0.18, 6.0, delay = 0.42),
            ),
        )
    }

    private fun render(
        ms: Int, gain: Double, noiseAmp: Double, noiseDecay: Double, noiseLowpass: Double,
        partials: List<Partial>,
    ): ShortArray {
        val count = RATE * ms / 1000
        val out = ShortArray(count)
        val random = Random(ms * 31 + partials.size)
        var lp = 0.0
        // A 1.5ms attack ramp keeps the tonal part from clicking on its own.
        val attack = RATE * 0.0015
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            val white = random.nextDouble() * 2.0 - 1.0
            lp = lp * noiseLowpass + white * (1.0 - noiseLowpass)
            var v = lp * noiseAmp * exp(-noiseDecay * t)
            for (p in partials) {
                val tt = t - p.delay
                if (tt < 0) continue
                val ramp = ((tt * RATE) / attack).coerceAtMost(1.0)
                v += sin(2.0 * PI * p.hz * tt) * p.amp * exp(-p.decay * tt) * ramp
            }
            // Fade the last 3ms so nothing ends on a discontinuity.
            val tail = ((count - i) / (RATE * 0.003)).coerceAtMost(1.0)
            out[i] = (v * gain * tail * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun build(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }
}
