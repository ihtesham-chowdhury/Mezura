package rocks.ndot.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.util.concurrent.TimeUnit

/**
 * Renders the sun dial exactly as the web widget draws it: the sun is a second circle of
 * equal radius rising from below and clipped to the dial, so the lens is a real geometric
 * intersection rather than a drawn arc. Unlike the KWGT build, this runs on a real Canvas -
 * no engine limitation here, just the usual "widgets can't repaint every frame" one, which is
 * why this produces one still bitmap per call rather than animating.
 */
object SundialRenderer {

    fun render(sizePx: Int, state: SolarMath.DialState, sunColor: Int, bodyColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val dialPath = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

        // Night base.
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyColor }
        c.save()
        c.clipPath(dialPath)
        c.drawCircle(cx, cy, r, basePaint)

        // The "sun": a same-radius circle whose center rises from 2r below the dial (fill=0)
        // up to level with the dial's own center (fill=1).
        val sunCy = cy + 2f * r * (1f - state.fill)
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sunColor }
        c.drawCircle(cx, sunCy, r, sunPaint)
        c.restore()

        // Face text: numeral + direction arrow, drawn once in white (clipped to the dark
        // remainder) and once in black (clipped to the sun disc), exactly like the web build,
        // so it stays legible regardless of how much of the dial the sun currently covers.
        val label = labelFor(state)
        val numeralSize = sizePx * 0.30f
        // Plain statements on an explicit receiver, not .apply{} - a couple of Paint's own
        // property names (textSize, color) collide with local names in this function, and
        // that's a real, silent-or-not-so-silent ambiguity depending on which side of an
        // assignment it's on, not worth relying on Kotlin's precedence rules to resolve
        // correctly. See the arrowPaint construction below for where this actually bit.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Typeface has no MEDIUM style constant on this API level (only NORMAL/BOLD/
        // ITALIC/BOLD_ITALIC - a numeric-weight overload exists but needs API 28+, and
        // minSdk here is 26), so BOLD stands in for the artifact's medium-weight numeral.
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = numeralSize
        textPaint.textAlign = Paint.Align.CENTER

        fun drawFace(color: Int) {
            textPaint.color = color
            val fm = textPaint.fontMetrics
            val baselineY = cy - (fm.ascent + fm.descent) / 2f - sizePx * 0.045f
            c.drawText(label, cx, baselineY, textPaint)
            if (!state.isPolar) {
                val arrowSize = sizePx * 0.075f
                val arrowY = cy + sizePx * 0.235f
                val path = Path()
                if (state.risingNotSetting) {
                    path.moveTo(cx, arrowY - arrowSize)
                    path.lineTo(cx + arrowSize, arrowY + arrowSize * 0.6f)
                    path.lineTo(cx - arrowSize, arrowY + arrowSize * 0.6f)
                } else {
                    path.moveTo(cx, arrowY + arrowSize)
                    path.lineTo(cx + arrowSize, arrowY - arrowSize * 0.6f)
                    path.lineTo(cx - arrowSize, arrowY - arrowSize * 0.6f)
                }
                path.close()
                // Plain assignment, not .apply { color = color } - that lambda's implicit
                // Paint receiver and this function's `color` parameter share the same name,
                // which is exactly the kind of ambiguity worth just not creating.
                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                arrowPaint.color = color
                c.drawPath(path, arrowPaint)
            }
        }

        // White where the dark night shows through:
        c.save()
        c.clipPath(dialPath)
        val sunOnly = Path().apply { addCircle(cx, sunCy, r, Path.Direction.CW) }
        c.clipOutPath(sunOnly)
        drawFace(Color.WHITE)
        c.restore()

        // Black where the sun disc covers the dial:
        c.save()
        c.clipPath(dialPath)
        c.clipPath(sunOnly)
        drawFace(contrastOn(sunColor))
        c.restore()

        return bmp
    }

    /**
     * The numeral sits over whatever accent colour is set, so it can't be hard-coded black -
     * a deep blue or violet sun needs white on it, a yellow one needs black. Rec. 601 luma is
     * plenty for a two-way pick.
     */
    private fun contrastOn(color: Int): Int {
        val luma = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (luma > 0.55) Brand.BLACK else Color.WHITE
    }

    private fun labelFor(state: SolarMath.DialState): String {
        if (state.isPolar) return if (state.polarIsDay) "24h" else "0h"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(state.untilNextMillis).coerceAtLeast(0)
        return if (minutes < 60) "${minutes}m" else "${TimeUnit.MILLISECONDS.toHours(state.untilNextMillis)}h"
    }
}
