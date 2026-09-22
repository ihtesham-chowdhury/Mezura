package rocks.ndot.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A mechanical stopwatch, drawn rather than shipped as artwork so it stays crisp at any widget
 * size and can be re-themed without a second asset.
 *
 * Everything is proportional to the case radius, so the same code renders a 90dp widget tile
 * and a full-screen face. There is deliberately no panel, card or keyline behind it - the
 * silhouette ends at the case and the crown, which is what makes it read as an object sitting
 * on the wallpaper rather than a widget.
 */
object StopwatchRenderer {

    enum class Dial { BLACK, WHITE }

    /** Where the hands should point. Split is the frozen hand left behind by a split press. */
    data class Face(
        val elapsedMs: Long,
        val splitMs: Long? = null,
        val running: Boolean = false,
    )

    private val condensed: Typeface by lazy {
        Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    /** Convenience for widgets, which need a Bitmap to hand to RemoteViews. */
    fun render(widthPx: Int, heightPx: Int, face: Face, dial: Dial, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), widthPx, heightPx, face, dial, accent)
        return bmp
    }

    /**
     * Draws straight onto any canvas. The full-screen face uses this directly so a 60fps
     * sweep doesn't allocate and discard a bitmap on every single frame.
     */
    fun draw(c: Canvas, widthPx: Int, heightPx: Int, face: Face, dial: Dial, accent: Int) {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        // The crown and bow sit above the case, so the case can't simply be centred.
        val crownZone = h * 0.17f
        val caseD = minOf(w, h - crownZone) * 0.97f
        val r = caseD / 2f
        val cx = w / 2f
        val cy = crownZone + r

        drawBow(c, cx, crownZone, r, dial)
        drawPusher(c, cx, cy, r, dial)
        drawCase(c, cx, cy, r, dial)
        drawDialFace(c, cx, cy, r, dial)
        drawSecondsTrack(c, cx, cy, r, dial)
        drawSubDial(c, cx, cy, r, face, dial)
        drawSignature(c, cx, cy, r, dial)
        drawSplitHand(c, cx, cy, r, face, accent)
        drawSecondHand(c, cx, cy, r, face, dial)
        drawCrystal(c, cx, cy, r)
    }

    // -- case ---------------------------------------------------------------------------

    /** The bow (the ring you'd hang it from) and the knurled crown beneath it. */
    private fun drawBow(c: Canvas, cx: Float, crownZone: Float, r: Float, dial: Dial) {
        val metal = metalSweep(cx, crownZone * 0.45f, r * 0.22f, dial)
        val ringR = crownZone * 0.30f
        val ringCy = ringR + r * 0.02f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.055f
            shader = metal
        }
        c.drawCircle(cx, ringCy, ringR, ring)

        // Stem joining the bow to the case.
        val stemW = r * 0.13f
        val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = metal }
        c.drawRect(cx - stemW / 2f, ringCy, cx + stemW / 2f, crownZone + r * 0.06f, stem)

        // Knurled crown: a stack of fine vertical flutes reads as milling at any size.
        val crownW = r * 0.30f
        val crownTop = ringCy + ringR * 0.75f
        val crownBottom = crownZone + r * 0.04f
        val crownRect = RectF(cx - crownW / 2f, crownTop, cx + crownW / 2f, crownBottom)
        val crownBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                crownRect.left, 0f, crownRect.right, 0f,
                intArrayOf(darken(caseTint(dial), 0.55f), caseTint(dial), darken(caseTint(dial), 0.45f)),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(crownRect, r * 0.04f, r * 0.04f, crownBody)

        val flute = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            strokeWidth = maxOf(1f, r * 0.012f)
        }
        var fx = crownRect.left + crownW * 0.14f
        while (fx < crownRect.right - crownW * 0.10f) {
            c.drawLine(fx, crownRect.top + r * 0.02f, fx, crownRect.bottom - r * 0.01f, flute)
            fx += crownW * 0.17f
        }
    }

    /** The side pusher, at roughly one o'clock. */
    private fun drawPusher(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val angle = (-52.0) * PI / 180.0
        val baseX = cx + cos(angle).toFloat() * r * 0.97f
        val baseY = cy + sin(angle).toFloat() * r * 0.97f
        val tipX = cx + cos(angle).toFloat() * r * 1.17f
        val tipY = cy + sin(angle).toFloat() * r * 1.17f

        val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = metalSweep(baseX, baseY, r * 0.2f, dial)
            strokeWidth = r * 0.10f
            strokeCap = Paint.Cap.BUTT
        }
        c.drawLine(baseX, baseY, tipX, tipY, stem)

        val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                tipX - r * 0.03f, tipY - r * 0.03f, r * 0.13f,
                intArrayOf(lighten(caseTint(dial), 0.35f), darken(caseTint(dial), 0.35f)),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(tipX, tipY, r * 0.085f, cap)
    }

    private fun drawCase(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        // Contact shadow so the watch sits on the wallpaper instead of floating.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(r * 0.09f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(cx, cy + r * 0.05f, r * 0.98f, shadow)

        // Outer bezel: a sweep gradient is what sells brushed metal - alternating highlights
        // around the circumference, the way a real ring catches light.
        val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = metalSweep(cx, cy, r, dial) }
        c.drawCircle(cx, cy, r, bezel)

        // Inner chamfer, slightly darker, to give the bezel thickness.
        val chamfer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.03f
            color = Color.argb(90, 0, 0, 0)
        }
        c.drawCircle(cx, cy, r * 0.885f, chamfer)
    }

    private fun drawDialFace(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val faceR = r * 0.87f
        val base = if (dial == Dial.BLACK) 0xFF131315.toInt() else 0xFFF4F3EF.toInt()
        val edge = if (dial == Dial.BLACK) 0xFF000000.toInt() else 0xFFD9D7D0.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - faceR * 0.25f, cy - faceR * 0.3f, faceR * 1.35f,
                intArrayOf(lighten(base, 0.06f), base, edge),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cy, faceR, paint)
    }

    // -- markings -----------------------------------------------------------------------

    /**
     * The main track: a fifth-of-a-second graduation, a heavier mark each second, and a
     * numeral every five. The fine graduations are what make it read as an instrument rather
     * than a clock.
     */
    private fun drawSecondsTrack(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val ink = inkColor(dial)
        val faceR = r * 0.87f
        val outer = faceR * 0.96f

        val fine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, Color.red(ink), Color.green(ink), Color.blue(ink))
            strokeWidth = maxOf(0.8f, r * 0.006f)
            strokeCap = Paint.Cap.BUTT
        }
        val second = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            strokeWidth = maxOf(1f, r * 0.011f)
            strokeCap = Paint.Cap.BUTT
        }
        val major = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            strokeWidth = maxOf(1.6f, r * 0.026f)
            strokeCap = Paint.Cap.BUTT
        }

        for (i in 0 until 300) {
            val deg = i * 1.2f - 90f
            val rad = deg * PI.toFloat() / 180f
            val isSecond = i % 5 == 0
            val isMajor = i % 25 == 0
            val len = when {
                isMajor -> faceR * 0.115f
                isSecond -> faceR * 0.072f
                else -> faceR * 0.040f
            }
            val paint = when {
                isMajor -> major
                isSecond -> second
                else -> fine
            }
            val x0 = cx + cos(rad) * outer
            val y0 = cy + sin(rad) * outer
            val x1 = cx + cos(rad) * (outer - len)
            val y1 = cy + sin(rad) * (outer - len)
            c.drawLine(x0, y0, x1, y1, paint)
        }

        val numeral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = condensed
            textSize = faceR * 0.185f
            textAlign = Paint.Align.CENTER
        }
        val numeralR = outer - faceR * 0.215f
        for (n in 1..12) {
            val value = n * 5
            val deg = value * 6f - 90f
            val rad = deg * PI.toFloat() / 180f
            val x = cx + cos(rad) * numeralR
            val y = cy + sin(rad) * numeralR - (numeral.fontMetrics.ascent + numeral.fontMetrics.descent) / 2f
            c.drawText(value.toString(), x, y, numeral)
        }
    }

    /** The 30-minute register at twelve o'clock, with its own hand. */
    private fun drawSubDial(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, dial: Dial) {
        val ink = inkColor(dial)
        val subR = r * 0.200f
        val subCy = cy - r * 0.340f
        // Below roughly 140px of case, the register numerals and the signature stop being
        // legible and just read as grey mush - so they're dropped rather than drawn badly.
        val detailed = r > 70f

        // A faint recess ring so it reads as a sunken register.
        val recess = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, r * 0.010f)
            color = Color.argb(90, Color.red(ink), Color.green(ink), Color.blue(ink))
        }
        c.drawCircle(cx, subCy, subR, recess)

        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            strokeWidth = maxOf(0.8f, r * 0.007f)
        }
        for (i in 0 until 30) {
            val deg = i * 12f - 90f
            val rad = deg * PI.toFloat() / 180f
            val len = if (i % 5 == 0) subR * 0.24f else subR * 0.13f
            c.drawLine(
                cx + cos(rad) * subR, subCy + sin(rad) * subR,
                cx + cos(rad) * (subR - len), subCy + sin(rad) * (subR - len),
                tick
            )
        }

        if (detailed) {
            val numeral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                typeface = condensed
                textSize = subR * 0.36f
                textAlign = Paint.Align.CENTER
            }
            for (n in 1..6) {
                val value = n * 5
                val deg = value * 12f - 90f
                val rad = deg * PI.toFloat() / 180f
                val x = cx + cos(rad) * (subR * 0.58f)
                val y = subCy + sin(rad) * (subR * 0.58f) -
                    (numeral.fontMetrics.ascent + numeral.fontMetrics.descent) / 2f
                c.drawText(value.toString(), x, y, numeral)
            }

            // Sits under the 30 at the top of the register, as on a real chronograph -
            // below centre it would collide with the 15.
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, Color.red(ink), Color.green(ink), Color.blue(ink))
                typeface = condensed
                textSize = subR * 0.26f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("MIN", cx, subCy - subR * 0.42f, label)
        }

        // Minute hand: advances a thirtieth of a turn per minute, wrapping at half an hour.
        val minutes = (face.elapsedMs / 60_000.0) % 30.0
        val deg = (minutes * 12.0).toFloat() - 90f
        val rad = deg * PI.toFloat() / 180f
        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            strokeWidth = maxOf(1.6f, r * 0.020f)
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(
            cx - cos(rad) * subR * 0.18f, subCy - sin(rad) * subR * 0.18f,
            cx + cos(rad) * subR * 0.74f, subCy + sin(rad) * subR * 0.74f,
            hand
        )
        val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        c.drawCircle(cx, subCy, subR * 0.085f, pin)
    }

    private fun drawSignature(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        if (r <= 70f) return
        val ink = inkColor(dial)
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, Color.red(ink), Color.green(ink), Color.blue(ink))
            typeface = condensed
            textSize = r * 0.115f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.26f
        }
        c.drawText("NDOT", cx, cy + r * 0.36f, name)

        val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, Color.red(ink), Color.green(ink), Color.blue(ink))
            typeface = condensed
            textSize = r * 0.075f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        c.drawText("1/5 SEC", cx, cy + r * 0.475f, spec)
    }

    // -- hands --------------------------------------------------------------------------

    /** The frozen hand a split leaves behind, in the app's accent colour. */
    private fun drawSplitHand(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, accent: Int) {
        val split = face.splitMs ?: return
        val deg = ((split % 60_000L) / 60_000.0 * 360.0).toFloat() - 90f
        drawNeedle(c, cx, cy, r, deg, accent, r * 0.016f, 0.80f)
    }

    private fun drawSecondHand(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, dial: Dial) {
        val deg = ((face.elapsedMs % 60_000L) / 60_000.0 * 360.0).toFloat() - 90f
        val handColor = if (dial == Dial.BLACK) 0xFFF6F6F4.toInt() else 0xFF1A1A1C.toInt()

        // Cast a soft shadow first so the hand lifts off the dial.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(r * 0.02f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(r * 0.012f, r * 0.018f)
        needlePath(cx, cy, r, deg, r * 0.020f, 0.845f).let { c.drawPath(it, shadow) }
        c.restore()

        drawNeedle(c, cx, cy, r, deg, handColor, r * 0.020f, 0.845f)

        // Centre pinion, drawn over the hand.
        val capOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.02f, cy - r * 0.02f, r * 0.075f,
                intArrayOf(lighten(handColor, 0.3f), darken(handColor, 0.3f)),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cy, r * 0.055f, capOuter)
        val capInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dial == Dial.BLACK) 0xFF131315.toInt() else 0xFFF4F3EF.toInt()
        }
        c.drawCircle(cx, cy, r * 0.020f, capInner)
    }

    private fun drawNeedle(
        c: Canvas, cx: Float, cy: Float, r: Float, deg: Float, color: Int, halfWidth: Float, reach: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawPath(needlePath(cx, cy, r, deg, halfWidth, reach), paint)
    }

    /** A tapered needle with a short counterweight tail, like a real chronograph hand. */
    private fun needlePath(cx: Float, cy: Float, r: Float, deg: Float, halfWidth: Float, reach: Float): Path {
        val rad = deg * PI.toFloat() / 180f
        val dx = cos(rad)
        val dy = sin(rad)
        val px = -dy
        val py = dx

        val tipR = r * reach
        val tailR = r * 0.17f

        return Path().apply {
            moveTo(cx + dx * tipR, cy + dy * tipR)
            lineTo(cx + dx * tipR * 0.22f + px * halfWidth, cy + dy * tipR * 0.22f + py * halfWidth)
            lineTo(cx - dx * tailR + px * halfWidth * 1.7f, cy - dy * tailR + py * halfWidth * 1.7f)
            lineTo(cx - dx * tailR - px * halfWidth * 1.7f, cy - dy * tailR - py * halfWidth * 1.7f)
            lineTo(cx + dx * tipR * 0.22f - px * halfWidth, cy + dy * tipR * 0.22f - py * halfWidth)
            close()
        }
    }

    /** A soft off-centre sheen, so the face looks like it is under glass. */
    private fun drawCrystal(c: Canvas, cx: Float, cy: Float, r: Float) {
        val faceR = r * 0.87f
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, faceR, Path.Direction.CW) })
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - faceR, cy - faceR, cx + faceR * 0.25f, cy + faceR * 0.45f,
                intArrayOf(Color.argb(38, 255, 255, 255), Color.argb(8, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cy, faceR, sheen)
        c.restore()
    }

    // -- colour helpers -----------------------------------------------------------------

    private fun caseTint(dial: Dial) = if (dial == Dial.BLACK) 0xFF2B2B2E.toInt() else 0xFFBFC2C7.toInt()

    private fun inkColor(dial: Dial) = if (dial == Dial.BLACK) 0xFFF2F2F0.toInt() else 0xFF17171A.toInt()

    /** Alternating light and shade around the ring: the trick that reads as turned metal. */
    private fun metalSweep(cx: Float, cy: Float, r: Float, dial: Dial): Shader {
        val base = caseTint(dial)
        val hi = lighten(base, if (dial == Dial.BLACK) 0.45f else 0.55f)
        val lo = darken(base, if (dial == Dial.BLACK) 0.45f else 0.40f)
        return SweepGradient(
            cx, cy,
            intArrayOf(hi, lo, hi, lo, hi, lo, hi, lo, hi),
            floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
        )
    }

    private fun lighten(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
    )

    private fun darken(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) * (1 - amount)).toInt().coerceIn(0, 255),
        (Color.green(color) * (1 - amount)).toInt().coerceIn(0, 255),
        (Color.blue(color) * (1 - amount)).toInt().coerceIn(0, 255)
    )
}
