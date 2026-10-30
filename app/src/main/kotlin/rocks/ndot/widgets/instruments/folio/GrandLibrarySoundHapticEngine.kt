package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zero-dependency real-time PCM acoustic synthesizer + ultra-subtle micro-haptic engine
 * for "The Grand Library Folio" Reading Statistics screen (`rocks.ndot.widgets.instruments.folio`).
 */
class GrandLibrarySoundHapticEngine(
    context: Context,
    var soundEnabled: Boolean = true,
    var hapticEnabled: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 44100

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Ultra-subtle custom micro-vibration (1ms - 5ms with controlled amplitude 1..255).
     */
    private fun microVibrate(durationMs: Long, amplitude: Int) {
        if (!hapticEnabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmp = amplitude.coerceIn(1, 255)
                vib.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtLeast(1L), safeAmp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs.coerceAtLeast(1L))
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 1. Brass Vernier Ruler Millimeter Slide Tick (1ms micro-haptic + 3.4kHz brushed-brass impulse).
     */
    fun onVernierSlideTick() {
        microVibrate(durationMs = 1L, amplitude = 18)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 5) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 950.0)
            val metallic = 0.65 * sin(2.0 * PI * 3420.0 * t) + 0.35 * sin(2.0 * PI * 5840.0 * t)
            (metallic * env * 0.22).toFloat()
        }
    }

    /**
     * 2. Brass Vernier Range Detent Snap Lock (7D / 30D / YEAR / ALL).
     */
    fun onVernierDetentLock() {
        microVibrate(durationMs = 3L, amplitude = 48)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 16) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env1 = exp(-t * 520.0)
            val env2 = if (t > 0.0045) exp(-(t - 0.0045) * 640.0) else 0.0
            val click1 = sin(2.0 * PI * 1850.0 * t) * env1
            val click2 = sin(2.0 * PI * 4120.0 * t) * env2
            ((click1 * 0.45 + click2 * 0.55) * 0.38).toFloat()
        }
    }

    /**
     * 3. Vintage Pocket Watch Swiss Lever Escapement Double-Tick.
     */
    fun onPocketWatchTick() {
        microVibrate(durationMs = 1L, amplitude = 12)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 10) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env1 = exp(-t * 1100.0)
            val env2 = if (t > 0.0032) exp(-(t - 0.0032) * 1300.0) else 0.0
            val pallet = sin(2.0 * PI * 5600.0 * t) * env1
            val balanceRing = sin(2.0 * PI * 7200.0 * t) * env2
            ((pallet * 0.5 + balanceRing * 0.5) * 0.20).toFloat()
        }
    }

    /**
     * 4. Cast-Brass Plaque Button Press & Release ("SHARE CSV" / "SHARE JSON").
     */
    fun onBrassPlaquePress() {
        microVibrate(durationMs = 3L, amplitude = 42)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 20) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 360.0)
            val body = sin(2.0 * PI * 920.0 * t)
            val rim = sin(2.0 * PI * 2980.0 * t) * exp(-t * 780.0)
            ((body * 0.6 + rim * 0.4) * env * 0.36).toFloat()
        }
    }

    /**
     * 5. Fountain-Pen Nib & Millimeter Drafting Paper Scrubbing Tick ("READING VELOCITY CURVE").
     */
    fun onGraphScrubTick() {
        microVibrate(durationMs = 1L, amplitude = 14)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 6) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 880.0)
            val paperScratch = (Random.nextFloat() * 2f - 1f) * 0.35 + sin(2.0 * PI * 2550.0 * t) * 0.65
            (paperScratch * env * 0.18).toFloat()
        }
    }

    /**
     * 6. Carved Walnut Shelf & Leather Book Spine Tap (pitched by monthIndex 0..11).
     */
    fun onBookSpineTap(monthIndex: Int) {
        microVibrate(durationMs = 3L, amplitude = 32)
        if (!soundEnabled) return
        val baseFreq = 340.0 + (monthIndex.coerceIn(0, 11) * 22.0)
        playSynthesizedBuffer(durationMs = 30) { i, _ ->
            val t = i.toDouble() / sampleRate
            val woodEnv = exp(-t * 240.0)
            val pageRustleEnv = exp(-t * 620.0)
            val walnutThud = sin(2.0 * PI * baseFreq * t) + 0.4 * sin(2.0 * PI * (baseFreq * 1.92) * t)
            val leatherRustle = (Random.nextFloat() * 2f - 1f) * 0.18 * pageRustleEnv
            ((walnutThud * woodEnv * 0.55 + leatherRustle) * 0.34).toFloat()
        }
    }

    /**
     * 7. Stamped Gold Rivet Matrix Pin Chime ("READING CADENCE 365-DOT MATRIX").
     */
    fun onRivetMatrixScrub() {
        microVibrate(durationMs = 1L, amplitude = 10)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 5) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 1200.0)
            (sin(2.0 * PI * 4680.0 * t) * env * 0.15).toFloat()
        }
    }

    /**
     * 8. Brass-on-Walnut Vernier Clasp Tick ("GENRE DISTRIBUTION").
     */
    fun onGenreCaliperSlide() {
        microVibrate(durationMs = 2L, amplitude = 16)
        if (!soundEnabled) return
        playSynthesizedBuffer(durationMs = 8) { i, _ ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 720.0)
            val tone = 0.55 * sin(2.0 * PI * 2180.0 * t) + 0.45 * sin(2.0 * PI * 1140.0 * t)
            (tone * env * 0.24).toFloat()
        }
    }

    private fun playSynthesizedBuffer(durationMs: Int, generator: (sampleIndex: Int, totalSamples: Int) -> Float) {
        scope.launch {
            try {
                val totalSamples = ((durationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(64)
                val pcm = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val sample = generator(i, totalSamples).coerceIn(-1f, 1f)
                    pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                kotlinx.coroutines.delay(durationMs.toLong() + 25L)
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
