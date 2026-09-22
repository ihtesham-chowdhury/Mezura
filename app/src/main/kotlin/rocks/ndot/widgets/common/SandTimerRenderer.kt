package rocks.ndot.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A real sand timer rather than a flat pictogram: brass caps and posts, glass with its own
 * tint and highlight, a sand surface that dips toward the neck, a mound that builds underneath,
 * and loose grains scattering around the stream.
 *
 * [remainingFraction] is 1.0 at the start of a session and 0.0 when it runs out. Both chambers
 * are funnels, so the sand height follows the square root of the remaining volume - that is
 * what makes the level fall slowly at first and drop away quickly at the end, as a real one
 * does. [tick] only shuffles the loose grains, so the stream looks alive between repaints.
 */
object SandTimerRenderer {

    private val brass = intArrayOf(0xC9, 0x9A, 0x46)

    fun render(
        widthPx: Int,
        heightPx: Int,
        remainingFraction: Float,
        running: Boolean,
        sandColor: Int,
        tick: Int,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val frac = remainingFraction.coerceIn(0f, 1f)

        val padX = w * 0.10f
        val padY = h * 0.045f
        val capH = h * 0.065f
        val gx0 = padX
        val gx1 = w - padX
        val cx = w / 2f
        val topCapY = padY
        val botCapY = h - padY - capH
        val glassTop = topCapY + capH
        val glassBot = botCapY
        val neckY = (glassTop + glassBot) / 2f
        val neckHalf = (gx1 - gx0) * 0.045f
        val inset = (gx1 - gx0) * 0.06f
        val bx0 = gx0 + inset
        val bx1 = gx1 - inset

        drawPosts(c, gx0, gx1, glassTop, glassBot, capH)

        val glass = glassPath(bx0, bx1, cx, glassTop, glassBot, neckY, neckHalf)
        drawGlassTint(c, glass, bx0, bx1)
        drawSand(c, glass, bx0, bx1, cx, glassTop, glassBot, neckY, frac, running, sandColor, tick)
        drawGlassHighlight(c, glass, bx0, bx1, glassTop, glassBot)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, w * 0.006f)
            color = Color.argb(115, 255, 255, 255)
        }
        c.drawPath(glass, outline)

        drawCap(c, gx0, gx1, topCapY, capH, w)
        drawCap(c, gx0, gx1, botCapY, capH, w)

        return bmp
    }

    /** The two funnels meeting at the neck, as one closed outline. */
    private fun glassPath(
        bx0: Float, bx1: Float, cx: Float,
        glassTop: Float, glassBot: Float, neckY: Float, neckHalf: Float,
    ) = Path().apply {
        moveTo(bx0, glassTop)
        lineTo(bx1, glassTop)
        quadTo(bx1, neckY - (neckY - glassTop) * 0.30f, cx + neckHalf, neckY)
        quadTo(bx1, neckY + (glassBot - neckY) * 0.30f, bx1, glassBot)
        lineTo(bx0, glassBot)
        quadTo(bx0, neckY + (glassBot - neckY) * 0.30f, cx - neckHalf, neckY)
        quadTo(bx0, neckY - (neckY - glassTop) * 0.30f, bx0, glassTop)
        close()
    }

    private fun drawPosts(c: Canvas, gx0: Float, gx1: Float, glassTop: Float, glassBot: Float, capH: Float) {
        val postW = (gx1 - gx0) * 0.055f
        floatArrayOf(gx0 + postW * 0.6f, gx1 - postW * 0.6f).forEach { px ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    px - postW / 2f, 0f, px + postW / 2f, 0f,
                    intArrayOf(shade(brass, -0.45f), shade(brass, 0.25f), shade(brass, -0.50f)),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
                )
            }
            c.drawRect(px - postW / 2f, glassTop - capH * 0.2f, px + postW / 2f, glassBot + capH * 0.2f, paint)
        }
    }

    private fun drawGlassTint(c: Canvas, glass: Path, bx0: Float, bx1: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bx0, 0f, bx1, 0f,
                intArrayOf(
                    Color.argb(77, 210, 235, 245),
                    Color.argb(26, 255, 255, 255),
                    Color.argb(41, 190, 220, 235),
                    Color.argb(87, 160, 195, 215)
                ),
                floatArrayOf(0f, 0.35f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawPath(glass, paint)
    }

    private fun drawSand(
        c: Canvas, glass: Path, bx0: Float, bx1: Float, cx: Float,
        glassTop: Float, glassBot: Float, neckY: Float,
        frac: Float, running: Boolean, sandColor: Int, tick: Int,
    ) {
        c.save()
        c.clipPath(glass)

        val light = mix(sandColor, Color.WHITE, 0.30f)
        val dark = mix(sandColor, Color.BLACK, 0.35f)

        // Upper chamber: the surface sinks toward the neck, dipping in the middle where the
        // sand is funnelling away.
        if (frac > 0.001f) {
            val surfaceY = neckY - (neckY - glassTop) * sqrt(frac)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, surfaceY, 0f, neckY,
                    intArrayOf(light, sandColor), null, Shader.TileMode.CLAMP
                )
            }
            val p = Path().apply {
                moveTo(bx0, surfaceY)
                quadTo(cx, surfaceY + (neckY - surfaceY) * 0.30f, bx1, surfaceY)
                lineTo(bx1, neckY)
                lineTo(bx0, neckY)
                close()
            }
            c.drawPath(p, paint)
        }

        // Lower chamber: a mound with a softly peaked top.
        val spent = 1f - frac
        var moundTopY = glassBot
        if (spent > 0.001f) {
            val moundH = (glassBot - neckY) * sqrt(spent)
            moundTopY = glassBot - moundH
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, moundTopY, 0f, glassBot,
                    intArrayOf(light, sandColor, dark),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
            val p = Path().apply {
                moveTo(bx0, glassBot)
                lineTo(bx0, moundTopY + moundH * 0.30f)
                quadTo(cx, moundTopY - moundH * 0.16f, bx1, moundTopY + moundH * 0.30f)
                lineTo(bx1, glassBot)
                close()
            }
            c.drawPath(p, paint)
        }

        // The stream, plus loose grains so it reads as moving rather than as a drawn line.
        if (running && frac > 0.001f && frac < 0.999f) {
            val streamW = (bx1 - bx0) * 0.012f
            val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, neckY, 0f, maxOf(moundTopY, neckY + 1f),
                    intArrayOf(sandColor, withAlpha(sandColor, 140)), null, Shader.TileMode.CLAMP
                )
            }
            c.drawRect(cx - streamW, neckY, cx + streamW, maxOf(moundTopY, neckY), streamPaint)

            val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light }
            val fall = maxOf(1f, moundTopY - neckY)
            val grainR = maxOf(0.6f, (bx1 - bx0) * 0.009f)
            for (i in 0 until 14) {
                val seed = i * 7.3f + tick * 3.1f
                val t = ((noise(seed) + tick * 0.13f) % 1f + 1f) % 1f
                val y = neckY + t * fall
                val spread = (noise(seed + 3.7f) - 0.5f) * (bx1 - bx0) * 0.055f * t
                c.drawCircle(cx + spread, y, grainR, grain)
            }

            val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(sandColor, 90) }
            for (i in 0 until 5) {
                val seed = i * 4.1f + tick * 2.7f
                val dx = (noise(seed) - 0.5f) * (bx1 - bx0) * 0.20f
                val dy = noise(seed + 1.3f) * (bx1 - bx0) * 0.03f
                c.drawCircle(cx + dx, moundTopY - dy, (bx1 - bx0) * 0.010f, dust)
            }
        }
        c.restore()
    }

    private fun drawGlassHighlight(c: Canvas, glass: Path, bx0: Float, bx1: Float, glassTop: Float, glassBot: Float) {
        c.save()
        c.clipPath(glass)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bx0, 0f, bx0 + (bx1 - bx0) * 0.4f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(77, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(bx0 + (bx1 - bx0) * 0.10f, glassTop, bx0 + (bx1 - bx0) * 0.26f, glassBot, paint)
        c.restore()
    }

    private fun drawCap(c: Canvas, gx0: Float, gx1: Float, y: Float, capH: Float, w: Float) {
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, y, 0f, y + capH,
                intArrayOf(shade(brass, 0.42f), rgb(brass), shade(brass, -0.42f)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(gx0, y, gx1, y + capH), capH * 0.32f, capH * 0.32f, body)

        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(77, 255, 255, 255)
            strokeWidth = maxOf(1f, w * 0.004f)
        }
        c.drawLine(gx0 + capH * 0.3f, y + capH * 0.30f, gx1 - capH * 0.3f, y + capH * 0.30f, sheen)
    }

    // -- helpers ------------------------------------------------------------------------

    /** Deterministic so grains stay put within a repaint and only move when [tick] does. */
    private fun noise(seed: Float): Float {
        val x = sin(seed * 12.9898) * 43758.5453
        return (x - floor(x)).toFloat()
    }

    private fun rgb(c: IntArray) = Color.rgb(c[0], c[1], c[2])

    private fun shade(c: IntArray, amount: Float): Int = if (amount >= 0) {
        Color.rgb(
            (c[0] + (255 - c[0]) * amount).toInt().coerceIn(0, 255),
            (c[1] + (255 - c[1]) * amount).toInt().coerceIn(0, 255),
            (c[2] + (255 - c[2]) * amount).toInt().coerceIn(0, 255)
        )
    } else {
        Color.rgb(
            (c[0] * (1 + amount)).toInt().coerceIn(0, 255),
            (c[1] * (1 + amount)).toInt().coerceIn(0, 255),
            (c[2] * (1 + amount)).toInt().coerceIn(0, 255)
        )
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
    )

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
