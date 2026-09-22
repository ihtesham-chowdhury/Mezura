package rocks.ndot.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Widget bodies are drawn as bitmaps rather than shape drawables. A `<shape>` resource can't
 * take a runtime colour or alpha without losing its rounded outline (setBackgroundColor
 * replaces the whole drawable with a flat rectangle), and it certainly can't render a fill
 * level that moves. Drawing to a Bitmap and pushing it with setImageViewBitmap gives exact
 * control over shape, colour, transparency and fill in one go.
 */
object Shapes {

    /** A stadium, optionally filled from the bottom up to [fillFraction] in [fillColor]. */
    fun pill(widthPx: Int, heightPx: Int, bodyColor: Int, fillColor: Int, fillFraction: Float): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val radius = minOf(w, h) / 2f

        val body = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bodyColor
        c.drawPath(body, paint)

        val f = fillFraction.coerceIn(0f, 1f)
        if (f > 0f) {
            c.save()
            c.clipPath(body)
            paint.color = fillColor
            c.drawRect(0f, h - h * f, w, h, paint)
            c.restore()
        }
        return bmp
    }

    /** A plain stadium with no fill - the Age widget's body. */
    fun pill(widthPx: Int, heightPx: Int, bodyColor: Int): Bitmap =
        pill(widthPx, heightPx, bodyColor, Color.TRANSPARENT, 0f)

}
