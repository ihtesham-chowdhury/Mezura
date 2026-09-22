package rocks.ndot.widgets.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The mechanical click of a stopwatch pusher, synthesised rather than shipped as an audio file:
 * a very short noise transient plus a damped tone, which is essentially what a metal pusher
 * sounds like. Start is pitched slightly higher than stop, the way a real two-position pusher
 * sounds different going down and coming back.
 *
 * Tracks are built once and replayed with reloadStaticData(), so pressing the button has no
 * decode or allocation latency.
 */
object Clicks {

    private const val SAMPLE_RATE = 44_100

    enum class Kind { START, STOP, LAP }

    private val tracks = HashMap<Kind, AudioTrack>()

    fun play(context: Context, kind: Kind, withHaptic: Boolean = true) {
        try {
            val track = tracks.getOrPut(kind) { buildTrack(samplesFor(kind)) }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: Exception) {
            // Audio focus, a missing output device, a locked-down ROM - a silent click is a
            // far better outcome here than a crash.
        }
        if (withHaptic) haptic(context, kind)
    }

    /** Release the tracks when the screen that uses them goes away. */
    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun samplesFor(kind: Kind): ShortArray {
        val durationMs = when (kind) {
            Kind.START -> 34
            Kind.STOP -> 40
            Kind.LAP -> 26
        }
        val toneHz = when (kind) {
            Kind.START -> 2350.0
            Kind.STOP -> 1750.0
            Kind.LAP -> 3050.0
        }
        val decay = when (kind) {
            Kind.START -> 120.0
            Kind.STOP -> 95.0
            Kind.LAP -> 165.0
        }

        val count = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(count)
        val random = Random(kind.ordinal * 7717)

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-decay * t)
            // The transient is the part the ear reads as "mechanical"; it dies almost at once.
            val transient = (random.nextDouble() * 2.0 - 1.0) * exp(-900.0 * t) * 0.55
            val tone = sin(2.0 * Math.PI * toneHz * t) * 0.45
            val value = (transient + tone * envelope) * envelope * Short.MAX_VALUE * 0.55
            out[i] = value.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun buildTrack(samples: ShortArray): AudioTrack {
        val bytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private fun haptic(context: Context, kind: Kind) {
        val ms = if (kind == Kind.LAP) 8L else 14L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }
}
