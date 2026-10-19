package rocks.ndot.widgets.instruments.horizon

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

enum class Chronotype(val label: String, val shiftHours: Double) {
    EARLY("Early Bird", -1.0),
    INTERMEDIATE("Intermediate", 0.0),
    LATE("Night Owl", 1.25)
}

enum class WindowType { PEAK_FOCUS, DIP_WINDOW }

data class AlertnessWindow(
    val type: WindowType,
    val startMin: Int,
    val endMin: Int,
    val label: String,
)

data class HorizonBlock(
    val id: String,
    val label: String,
    val startMin: Int,
    val endMin: Int,
    val colorIdx: Int = 0
)

object EnergyModel {

    // Circadian baseline: (tau hours since wake, alertness 0..1)
    private val anchors = listOf(
        0.0 to 0.35,  // sleep inertia
        1.0 to 0.55,
        3.0 to 0.95,  // late-morning peak
        5.0 to 0.80,
        7.5 to 0.50,  // post-lunch dip
        9.5 to 0.68,
        11.5 to 0.80, // early-evening rise
        14.0 to 0.55,
        16.0 to 0.32  // wind-down
    )

    private fun fade(t: Double): Double {
        return if (t <= 13.0) 1.0 else (1.0 - (t - 13.0) / 3.0 * 0.7).coerceIn(0.3, 1.0)
    }

    private fun catmullRom(points: List<Pair<Double, Double>>, x: Double): Double {
        if (points.isEmpty()) return 0.5
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second

        var i = 0
        while (i < points.size - 1 && points[i + 1].first < x) {
            i++
        }

        val p0 = points[max(0, i - 1)]
        val p1 = points[i]
        val p2 = points[min(points.size - 1, i + 1)]
        val p3 = points[min(points.size - 1, i + 2)]

        val t = if (p2.first > p1.first) ((x - p1.first) / (p2.first - p1.first)).coerceIn(0.0, 1.0) else 0.0
        val t2 = t * t
        val t3 = t2 * t

        val v = 0.5 * (
            (2.0 * p1.second) +
            (-p0.second + p2.second) * t +
            (2.0 * p0.second - 5.0 * p1.second + 4.0 * p2.second - p3.second) * t2 +
            (-p0.second + 3.0 * p1.second - 3.0 * p2.second + p3.second) * t3
        )
        return v
    }

    fun alertness(tauHours: Double, wakingHours: Double = 16.0, shiftHours: Double = 0.0): Double {
        val t = tauHours * 16.0 / max(1.0, wakingHours) - shiftHours
        val base = catmullRom(anchors, t.coerceIn(0.0, 16.0))
        val ultradian = 0.06 * cos(2.0 * PI * (t - 0.75) / 1.5) * fade(t)
        return (base + ultradian).coerceIn(0.05, 1.0)
    }

    fun meanAlertness(
        startMin: Int,
        endMin: Int,
        wakeMin: Int,
        sleepMin: Int,
        shiftHours: Double = 0.0
    ): Double {
        val wakingMins = (sleepMin - wakeMin).coerceAtLeast(60)
        val wakingHours = wakingMins / 60.0
        if (endMin <= startMin) return 0.5

        var sum = 0.0
        var count = 0
        var m = startMin
        while (m <= endMin) {
            val tau = (m - wakeMin).coerceAtLeast(0) / 60.0
            sum += alertness(tau, wakingHours, shiftHours)
            count++
            m += 5
        }
        return if (count > 0) sum / count else 0.5
    }

    /**
     * Identifies Peak Focus (runs with A >= 0.82 for at least 30 min)
     * and Dip Window (run around minimum in tau in [5, 10] where A <= 0.58).
     */
    fun findWindows(
        wakeMin: Int,
        sleepMin: Int,
        shiftHours: Double = 0.0
    ): List<AlertnessWindow> {
        val wakingMins = (sleepMin - wakeMin).coerceAtLeast(60)
        val wakingHours = wakingMins / 60.0
        val windows = mutableListOf<AlertnessWindow>()

        // 1. Peak Focus search
        var inPeak = false
        var peakStart = 0
        var m = wakeMin
        while (m <= sleepMin) {
            val tau = (m - wakeMin) / 60.0
            val a = alertness(tau, wakingHours, shiftHours)
            if (a >= 0.82) {
                if (!inPeak) {
                    inPeak = true
                    peakStart = m
                }
            } else {
                if (inPeak) {
                    inPeak = false
                    if (m - peakStart >= 30) {
                        windows.add(
                            AlertnessWindow(
                                type = WindowType.PEAK_FOCUS,
                                startMin = peakStart,
                                endMin = m,
                                label = "PEAK FOCUS ${formatClock(peakStart)}–${formatClock(m)}"
                            )
                        )
                    }
                }
            }
            m += 5
        }
        if (inPeak && (sleepMin - peakStart >= 30)) {
            windows.add(
                AlertnessWindow(
                    type = WindowType.PEAK_FOCUS,
                    startMin = peakStart,
                    endMin = sleepMin,
                    label = "PEAK FOCUS ${formatClock(peakStart)}–${formatClock(sleepMin)}"
                )
            )
        }

        // 2. Dip Window search (tau in [5, 10])
        var dipMinTau = 7.5
        var minVal = 1.0
        var stepM = wakeMin + (5 * 60)
        val endDipRange = min(sleepMin, wakeMin + (10 * 60))
        while (stepM <= endDipRange) {
            val tau = (stepM - wakeMin) / 60.0
            val a = alertness(tau, wakingHours, shiftHours)
            if (a < minVal) {
                minVal = a
                dipMinTau = tau
            }
            stepM += 5
        }

        if (minVal <= 0.58) {
            val dipCenterMin = (wakeMin + dipMinTau * 60).toInt()
            windows.add(
                AlertnessWindow(
                    type = WindowType.DIP_WINDOW,
                    startMin = dipCenterMin - 30,
                    endMin = dipCenterMin + 30,
                    label = "DIP WINDOW ${formatClock(dipCenterMin)}"
                )
            )
        }

        return windows
    }

    fun caffeineCutoffMin(sleepMin: Int, caffeineHours: Double = 9.0): Int {
        return (sleepMin - (caffeineHours * 60).toInt()).coerceAtLeast(0)
    }

    /**
     * Suggests the best starting minute for a block of given duration,
     * sliding in 5-min steps, avoiding existing blocks, and maximizing mean alertness.
     */
    fun suggestSlot(
        durationMin: Int,
        wakeMin: Int,
        sleepMin: Int,
        existingBlocks: List<HorizonBlock>,
        shiftHours: Double = 0.0
    ): Int {
        var bestStart = wakeMin
        var bestScore = -1.0

        var cand = wakeMin
        while (cand + durationMin <= sleepMin) {
            val candEnd = cand + durationMin
            // Check collision
            val collides = existingBlocks.any { b ->
                max(cand, b.startMin) < min(candEnd, b.endMin)
            }
            if (!collides) {
                val score = meanAlertness(cand, candEnd, wakeMin, sleepMin, shiftHours)
                if (score > bestScore) {
                    bestScore = score
                    bestStart = cand
                }
            }
            cand += 5
        }
        return bestStart
    }

    private fun formatClock(totalMin: Int): String {
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return String.format("%02d:%02d", h, m)
    }
}
