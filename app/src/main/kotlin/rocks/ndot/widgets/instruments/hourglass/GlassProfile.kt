package rocks.ndot.widgets.instruments.hourglass

/**
 * The shape of one hourglass, and the sand arithmetic that goes with it.
 *
 * A profile is a curve of half-width against height: t = 0 at the neck, 1 at the rim, symmetric
 * top and bottom. Sand levels come from *volume*, not height - a round glass holds r squared
 * per slice - so the top drains slowly while it's wide and then races down the narrow cone at
 * the end, exactly as a real one does.
 */
class GlassProfile(segments: Array<FloatArray>) {

    private val n = 240
    private val widths = FloatArray(n + 1)
    private val volume = FloatArray(n + 1)
    val totalVolume: Float

    init {
        // Sample each cubic segment densely, then resample at evenly spaced t.
        val pts = ArrayList<FloatArray>()
        for (s in segments) {
            for (i in 0..60) {
                val u = i / 60f
                pts.add(floatArrayOf(bez(s[0], s[2], s[4], s[6], u), bez(s[1], s[3], s[5], s[7], u)))
            }
        }
        pts.sortBy { it[0] }
        var j = 0
        for (i in 0..n) {
            val t = i.toFloat() / n
            while (j < pts.size - 2 && pts[j + 1][0] < t) j++
            val (t0, f0) = pts[j].let { it[0] to it[1] }
            val (t1, f1) = pts[j + 1].let { it[0] to it[1] }
            widths[i] = if (t1 > t0) f0 + (f1 - f0) * ((t - t0) / (t1 - t0)).coerceIn(0f, 1f) else f0
        }
        for (i in 1..n) {
            volume[i] = volume[i - 1] + (widths[i] * widths[i] + widths[i - 1] * widths[i - 1]) / 2f / n
        }
        totalVolume = volume[n]
    }

    /** Half-width as a fraction of the glass's widest point. */
    fun widthAt(t: Float): Float {
        val x = t.coerceIn(0f, 1f) * n
        val i = x.toInt()
        if (i >= n) return widths[n]
        return widths[i] + (widths[i + 1] - widths[i]) * (x - i)
    }

    /** The height, from the neck, that holds [v] of volume. */
    fun tForVolume(v: Float): Float {
        if (v <= 0f) return 0f
        if (v >= totalVolume) return 1f
        var lo = 0
        var hi = n
        while (hi - lo > 1) {
            val m = (lo + hi) ushr 1
            if (volume[m] < v) lo = m else hi = m
        }
        return (lo + (v - volume[lo]) / (volume[hi] - volume[lo])) / n
    }

    /**
     * Sand heights for [remaining] (1 = all on top). The top pile spans t in [0, top]; the
     * bottom pile spans t in [bottom, 1]. The sand fills [FILL] of one bulb, leaving the air
     * gap a real glass always has.
     */
    fun levels(remaining: Float): Pair<Float, Float> {
        val sand = totalVolume * FILL
        val top = tForVolume(sand * remaining)
        val bottom = tForVolume(totalVolume - sand * (1f - remaining))
        return top to bottom
    }

    companion object {
        const val FILL = 0.72f

        private fun bez(a: Float, b: Float, c: Float, d: Float, u: Float): Float {
            val v = 1 - u
            return v * v * v * a + 3 * v * v * u * b + 3 * v * u * u * c + u * u * u * d
        }

        // Each row: one cubic segment in (t, width) space - x0,y0, cx1,cy1, cx2,cy2, x1,y1.
        val CLASSIC = GlassProfile(arrayOf(
            floatArrayOf(0f, 0.075f, 0.09f, 0.075f, 0.15f, 0.60f, 0.37f, 0.89f),
            floatArrayOf(0.37f, 0.89f, 0.52f, 1.03f, 0.80f, 1.03f, 0.93f, 0.86f),
            floatArrayOf(0.93f, 0.86f, 0.97f, 0.80f, 0.99f, 0.76f, 1.0f, 0.74f),
        ))
        val DUSK = GlassProfile(arrayOf(
            floatArrayOf(0f, 0.06f, 0.11f, 0.06f, 0.13f, 0.46f, 0.40f, 0.80f),
            floatArrayOf(0.40f, 0.80f, 0.60f, 0.99f, 0.86f, 1.00f, 1.0f, 0.84f),
        ))
        val PRISM = GlassProfile(arrayOf(
            floatArrayOf(0f, 0.12f, 0.05f, 0.12f, 0.09f, 0.40f, 0.33f, 0.68f),
            floatArrayOf(0.33f, 0.68f, 0.58f, 0.90f, 0.86f, 0.99f, 1.0f, 1.0f),
        ))
        val DOT = GlassProfile(arrayOf(
            floatArrayOf(0f, 0.08f, 0.09f, 0.08f, 0.20f, 0.46f, 0.45f, 0.78f),
            floatArrayOf(0.45f, 0.78f, 0.68f, 1.0f, 0.88f, 1.0f, 1.0f, 1.0f),
        ))
    }
}
