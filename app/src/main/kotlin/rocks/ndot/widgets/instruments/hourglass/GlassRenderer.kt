package rocks.ndot.widgets.instruments.hourglass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class GlassDesign(val id: String, val label: String, val light: Boolean) {
    CLASSIC("classic", "Classic", false),
    DUSK("dusk", "Dusk", false),
    PRISM("prism", "Prism", true),
    DOT("dot", "Dot", false);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * The four hourglasses, ported from the canvas prototype they were designed in.
 *
 * Only the sand moves, so everything else - background, frame, glass reflections - is recorded
 * once into [Picture]s for the current size and replayed each frame. Per frame there are two
 * sand shapes, a stream and a few dozen grains to draw, which keeps even the wooden Classic
 * comfortably inside a 120Hz frame budget.
 */
class GlassRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val glass = Path()
    private val topSand = Path()
    private val bottomSand = Path()
    private val work = Path()
    private val rect = RectF()

    private var key = ""
    private var canvasW = 1
    private var canvasH = 1
    private var background = Picture()
    private var behind = Picture()
    private var over = Picture()
    private var front = Picture()
    private lateinit var geo: Geo

    private var dotKey = ""
    private var dotSand = Picture()

    /** Where the glass sits for the current box: centre, half height H, widest half-width W. */
    private class Geo(val design: GlassDesign, val p: GlassProfile, val cx: Float, val cy: Float, val h: Float, val w: Float)

    /**
     * Draws the whole scene. [remaining] is 1 with all the sand on top. [flipDegrees] is non-zero
     * while the glass is being turned over; [settled] draws the sand lying flat, as it does the
     * moment a glass is set down.
     */
    fun draw(
        c: Canvas, design: GlassDesign, width: Float, height: Float, box: RectF,
        remaining: Float, seconds: Float, running: Boolean, settled: Boolean, flipDegrees: Float, accent: Int,
    ) {
        prepare(design, width, height, box, accent)
        val g = geo
        c.drawPicture(background)
        c.save()
        if (flipDegrees != 0f) c.rotate(flipDegrees, g.cx, g.cy)
        c.drawPicture(behind)

        val (tTop, tBot) = g.p.levels(remaining)
        val dip = if (settled || !running) 0f else g.h * 0.055f * min(1f, remaining * 6f) * min(1f, (1f - remaining) * 30f)
        val mound = if (settled) 0f else g.h * 0.075f * min(1f, (1f - remaining) * 5f) * (0.55f + 0.45f * min(1f, remaining * 3f))

        if (design == GlassDesign.DOT) {
            drawDotSand(c, g, tTop, tBot, dip, mound, remaining, seconds, running, accent)
        } else {
            c.save()
            c.clipPath(glass)
            buildTopSand(g, tTop, dip, topSand)
            buildBottomSand(g, tBot, mound, bottomSand)
            when (design) {
                GlassDesign.CLASSIC -> classicSand(c, g)
                GlassDesign.DUSK -> duskSand(c, g, tTop, tBot, dip, mound)
                GlassDesign.PRISM -> prismSand(c, g, seconds)
                GlassDesign.DOT -> Unit
            }
            val streamColor = when (design) {
                GlassDesign.CLASSIC -> 0xFFE6C864.toInt()
                GlassDesign.DUSK -> 0xFFE8866B.toInt()
                else -> hsl((320f + seconds * 12f) % 360f, 0.9f, 0.62f)
            }
            val streamWidth = max(1.4f, g.w * if (design == GlassDesign.DUSK) 0.028f else 0.03f)
            stream(c, g, remaining, tBot, mound, seconds, streamColor, streamWidth, running)
            c.restore()
        }
        c.drawPicture(over)
        c.drawPicture(front)
        c.restore()
    }

    // ---- static layers ----

    private fun prepare(design: GlassDesign, width: Float, height: Float, box: RectF, accent: Int) {
        val wanted = "$design:$width:$height:${box.left}:${box.top}:${box.right}:${box.bottom}:$accent"
        if (wanted == key) return
        key = wanted
        dotKey = ""
        canvasW = max(1, width.toInt())
        canvasH = max(1, height.toInt())
        val cx = box.centerX()
        val cy = box.centerY()
        geo = when (design) {
            GlassDesign.CLASSIC -> min(box.height() / 2.36f, box.width() * 0.96f / 1.65f).let { Geo(design, GlassProfile.CLASSIC, cx, cy, it, it * 0.50f) }
            GlassDesign.DUSK -> min(box.height() / 2.12f, box.width() / 2f / (0.47f * 1.5f)).let { Geo(design, GlassProfile.DUSK, cx, cy, it, it * 0.47f) }
            GlassDesign.PRISM -> min(box.height() / 2.2f, box.width() / 2f / (0.50f * 1.7f)).let { Geo(design, GlassProfile.PRISM, cx, cy, it, it * 0.50f) }
            GlassDesign.DOT -> min(box.height() / 2.25f, box.width() / 2f / (0.52f * 1.35f)).let { Geo(design, GlassProfile.DOT, cx, cy, it, it * 0.52f) }
        }
        buildGlass(geo, glass)
        background = record(width, height) { drawBackground(it, geo, width, height) }
        behind = record(width, height) { drawBehind(it, geo) }
        over = record(width, height) { drawOver(it, geo, accent) }
        front = record(width, height) { drawFront(it, geo) }
    }

    private inline fun record(width: Float, height: Float, block: (Canvas) -> Unit): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(max(1, width.toInt()), max(1, height.toInt()))
        block(canvas)
        picture.endRecording()
        return picture
    }

    private fun drawBackground(c: Canvas, g: Geo, width: Float, height: Float) {
        when (g.design) {
            GlassDesign.CLASSIC -> {
                fill.shader = RadialGradient(width / 2f, g.cy - g.h * 0.1f, max(width, height) * 0.75f,
                    intArrayOf(0xFF46535F.toInt(), 0xFF2C353F.toInt(), 0xFF1B2129.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width, height, fill)
                val plateY = g.cy + g.h + g.h * 0.14f * 0.55f
                val plateW = g.w * 1.65f
                fill.shader = RadialGradient(g.cx, plateY + g.h * 0.1f, plateW * 1.15f, 0x8C000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                rect.set(g.cx - plateW * 1.15f, plateY + g.h * 0.087f - g.h * 0.126f, g.cx + plateW * 1.15f, plateY + g.h * 0.087f + g.h * 0.126f)
                c.drawOval(rect, fill)
            }
            GlassDesign.DUSK -> {
                val tableY = g.cy + g.h * 0.62f
                fill.shader = null
                fill.color = 0xFF34203F.toInt(); c.drawRect(0f, 0f, width, tableY, fill)
                fill.color = 0xFF2F6280.toInt(); c.drawRect(0f, tableY, width, height, fill)
                fill.color = 0xFF3B7898.toInt(); c.drawRect(0f, tableY, width, tableY + max(2f, g.h * 0.018f), fill)
                fill.color = 0xFF244D66.toInt()
                rect.set(g.cx + g.w * 0.1f - g.w * 1.45f, g.cy + g.h * 1.02f - g.h * 0.07f, g.cx + g.w * 0.1f + g.w * 1.45f, g.cy + g.h * 1.02f + g.h * 0.07f)
                c.drawOval(rect, fill)
            }
            GlassDesign.PRISM -> {
                fill.shader = LinearGradient(0f, 0f, 0f, height, 0xFFDAD7D3.toInt(), 0xFFB3B0AC.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width, height, fill)
                fill.shader = RadialGradient(g.cx, g.cy + g.h * 1.06f, g.w * 1.7f, 0x59463C50, 0x00463C50, Shader.TileMode.CLAMP)
                rect.set(g.cx - g.w * 1.7f, g.cy + g.h * 0.96f, g.cx + g.w * 1.7f, g.cy + g.h * 1.16f)
                c.drawOval(rect, fill)
            }
            GlassDesign.DOT -> {
                fill.shader = null; fill.color = Color.BLACK
                c.drawRect(0f, 0f, width, height, fill)
            }
        }
        fill.shader = null
    }

    private fun drawBehind(c: Canvas, g: Geo) {
        when (g.design) {
            GlassDesign.CLASSIC -> {
                val plateH = g.h * 0.14f
                val yTop = g.cy - g.h - plateH * 0.55f
                val yBot = g.cy + g.h + plateH * 0.55f
                // The back post, seen through the glass.
                fill.alpha = 255
                post(c, g.cx, yTop + plateH * 0.5f, yBot - plateH * 0.5f, g.w * 0.10f, front = false, alpha = 0.75f)
                tintGlass(c, 0x12DCEBF5)
            }
            GlassDesign.DUSK -> tintGlass(c, 0x1AA0D7E6)
            GlassDesign.PRISM -> tintGlass(c, 0x33FFFFFF)
            GlassDesign.DOT -> dotStatic(c, g)
        }
    }

    private fun tintGlass(c: Canvas, color: Int) {
        fill.shader = null; fill.color = color
        c.drawPath(glass, fill)
    }

    private fun drawOver(c: Canvas, g: Geo, accent: Int) {
        when (g.design) {
            GlassDesign.CLASSIC -> {
                c.save(); c.clipPath(glass)
                highlights(c, g, 0x38FFFFFF, 0x1AFFFFFF)
                c.restore()
                stroke.shader = null; stroke.color = 0x8CE1EEF8.toInt(); stroke.strokeWidth = max(1.2f, g.w * 0.018f)
                c.drawPath(glass, stroke)
            }
            GlassDesign.DUSK -> {
                stroke.shader = null; stroke.color = 0xFF9FD4E2.toInt(); stroke.strokeWidth = max(1.6f, g.w * 0.03f)
                c.drawPath(glass, stroke)
                c.save(); c.clipPath(glass)
                highlights(c, g, 0x4DC8F0FA, 0x38C8F0FA)
                c.restore()
                rims(c, g, 0xFF7FB8CB.toInt(), 0xFFBFEAF4.toInt(), g.h * 0.035f, 1.04f, max(1.2f, g.w * 0.018f))
            }
            GlassDesign.PRISM -> {
                c.save(); c.clipPath(glass)
                // Thick glass bends light at its walls: soft coloured bands hugging the sides.
                val bands = LinearGradient(0f, g.cy - g.h, 0f, g.cy + g.h,
                    intArrayOf(0xFFFF5A8C.toInt(), 0xFFFFBE46.toInt(), 0xFF785AFF.toInt(), 0xFF3CC8FF.toInt(), 0xFFFF5A8C.toInt()),
                    floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
                for (side in intArrayOf(-1, 1)) for (top in booleanArrayOf(true, false)) {
                    work.reset()
                    for (i in 0..60) {
                        val t = 0.05f + 0.93f * i / 60f
                        val x = g.cx + side * g.p.widthAt(t) * g.w * 0.93f
                        val y = g.cy + (if (top) -1 else 1) * t * g.h
                        if (i == 0) work.moveTo(x, y) else work.lineTo(x, y)
                    }
                    stroke.shader = bands
                    for ((lw, a) in listOf(0.20f to 0.10f, 0.12f to 0.14f, 0.06f to 0.20f)) {
                        stroke.alpha = (255 * a).toInt(); stroke.strokeWidth = g.w * lw
                        c.drawPath(work, stroke)
                    }
                    stroke.alpha = 255
                }
                stroke.shader = null
                highlights(c, g, 0x8CFFFFFF.toInt(), 0x59FFFFFF)
                c.restore()
                stroke.color = 0xD9FFFFFF.toInt(); stroke.strokeWidth = max(1.2f, g.w * 0.02f)
                c.drawPath(glass, stroke)
                stroke.color = 0x405A506E; stroke.strokeWidth = 1f
                c.drawPath(glass, stroke)
                rims(c, g, 0x59FFFFFF, 0xE6FFFFFF.toInt(), g.h * 0.05f, 1f, max(1.2f, g.w * 0.02f))
            }
            GlassDesign.DOT -> Unit
        }
        stroke.shader = null
    }

    private fun drawFront(c: Canvas, g: Geo) {
        if (g.design != GlassDesign.CLASSIC) return
        val plateH = g.h * 0.14f
        val plateW = g.w * 1.65f
        val yTop = g.cy - g.h - plateH * 0.55f
        val yBot = g.cy + g.h + plateH * 0.55f
        plate(c, g.cx, yTop, plateW, plateH, top = true)
        plate(c, g.cx, yBot, plateW, plateH, top = false)
        val postX = g.w * 1.24f
        post(c, g.cx - postX, yTop + plateH * 0.5f, yBot - plateH * 0.5f, g.w * 0.13f, front = true, alpha = 1f)
        post(c, g.cx + postX, yTop + plateH * 0.5f, yBot - plateH * 0.5f, g.w * 0.13f, front = true, alpha = 1f)
    }

    // ---- geometry ----

    private fun buildGlass(g: Geo, out: Path) {
        out.reset()
        val n = 90
        for (i in n downTo 0) {
            val t = i.toFloat() / n
            val x = g.cx - g.p.widthAt(t) * g.w
            val y = g.cy - t * g.h
            if (i == n) out.moveTo(x, y) else out.lineTo(x, y)
        }
        for (i in 1..n) { val t = i.toFloat() / n; out.lineTo(g.cx - g.p.widthAt(t) * g.w, g.cy + t * g.h) }
        out.lineTo(g.cx + g.p.widthAt(1f) * g.w, g.cy + g.h)
        for (i in n - 1 downTo 0) { val t = i.toFloat() / n; out.lineTo(g.cx + g.p.widthAt(t) * g.w, g.cy + t * g.h) }
        for (i in 1..n) { val t = i.toFloat() / n; out.lineTo(g.cx + g.p.widthAt(t) * g.w, g.cy - t * g.h) }
        out.close()
    }

    /** The top pile: resting on the neck, surface at [tTop], dipping into a funnel while it drains. */
    private fun buildTopSand(g: Geo, tTop: Float, dip: Float, out: Path) {
        out.reset()
        if (tTop <= 0.0005f) return
        val n = 40
        val ws = g.p.widthAt(tTop) * g.w
        val ySurf = g.cy - tTop * g.h
        out.moveTo(g.cx - ws, ySurf)
        for (i in 1..n) {
            val u = -1f + 2f * i / n
            val d = dip * (1f - min(1f, abs(u))).pow(2.2f)
            out.lineTo(g.cx + u * ws, ySurf + d)
        }
        for (i in n downTo 0) { val t = tTop * i / n; out.lineTo(g.cx + g.p.widthAt(t) * g.w, g.cy - t * g.h) }
        for (i in 0..n) { val t = tTop * i / n; out.lineTo(g.cx - g.p.widthAt(t) * g.w, g.cy - t * g.h) }
        out.close()
    }

    /** The bottom pile: level at [tBot], heaped into a cone under the stream. */
    private fun buildBottomSand(g: Geo, tBot: Float, mound: Float, out: Path) {
        out.reset()
        if (tBot >= 0.9995f) return
        val n = 48
        val wb = g.p.widthAt(tBot) * g.w
        val yLevel = g.cy + tBot * g.h
        out.moveTo(g.cx - wb, yLevel + mound * 0.35f)
        for (i in 1..n) {
            val u = -1f + 2f * i / n
            val k = 1f - abs(u)
            out.lineTo(g.cx + u * wb, yLevel - mound * (k.pow(1.35f) * 1.35f - 0.35f))
        }
        for (i in 0..n) { val t = tBot + (1f - tBot) * i / n; out.lineTo(g.cx + g.p.widthAt(t) * g.w, g.cy + t * g.h) }
        for (i in n downTo 0) { val t = tBot + (1f - tBot) * i / n; out.lineTo(g.cx - g.p.widthAt(t) * g.w, g.cy + t * g.h) }
        out.close()
    }

    // ---- sand ----

    private fun classicSand(c: Canvas, g: Geo) {
        val shade = LinearGradient(g.cx - g.w, 0f, g.cx + g.w, 0f,
            intArrayOf(0x733C2800, 0x2EFFF0AA, 0x00000000, 0x803C2800.toInt()), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        for (path in arrayOf(topSand, bottomSand)) {
            if (path.isEmpty) continue
            fill.shader = sandShader(0xFFD8B54A.toInt(), 0xFF9C7424.toInt(), 0xFFF3DC8A.toInt(), "classic")
            c.drawPath(path, fill)
            fill.shader = shade
            c.drawPath(path, fill)
        }
        fill.shader = null
    }

    private fun duskSand(c: Canvas, g: Geo, tTop: Float, tBot: Float, dip: Float, mound: Float) {
        fill.shader = null
        if (!topSand.isEmpty) {
            fill.color = 0xFFE07C62.toInt(); c.drawPath(topSand, fill)
            c.save(); c.clipPath(topSand)
            fill.color = 0xFFC4604B.toInt(); c.drawRect(g.cx - g.w * 2f, g.cy - tTop * g.h * 0.55f, g.cx + g.w * 2f, g.cy + g.h, fill)
            fill.color = 0xFFF39C80.toInt(); c.drawRect(g.cx - g.w * 2f, g.cy - tTop * g.h - 2f, g.cx + g.w * 2f, g.cy - tTop * g.h + g.h * 0.035f + dip * 0.25f, fill)
            c.restore()
        }
        if (!bottomSand.isEmpty) {
            fill.color = 0xFFE07C62.toInt(); c.drawPath(bottomSand, fill)
            c.save(); c.clipPath(bottomSand)
            fill.color = 0xFFC4604B.toInt()
            rect.set(g.cx + g.w * 0.25f - g.w * 0.9f, g.cy + g.h * 0.95f - g.h * 0.18f, g.cx + g.w * 0.25f + g.w * 0.9f, g.cy + g.h * 0.95f + g.h * 0.18f)
            c.drawOval(rect, fill)
            fill.color = 0xFFF39C80.toInt()
            val ly = g.cy + tBot * g.h - mound * 0.3f
            rect.set(g.cx - g.w * 0.1f - g.w * 0.45f, ly - g.h * 0.035f, g.cx - g.w * 0.1f + g.w * 0.45f, ly + g.h * 0.035f)
            c.drawOval(rect, fill)
            c.restore()
        }
    }

    private fun prismSand(c: Canvas, g: Geo, seconds: Float) {
        val hue = (seconds * 12f) % 360f
        val liquid = LinearGradient(g.cx - g.w, 0f, g.cx + g.w, 0f,
            intArrayOf(hsl((250f + hue) % 360f, 0.9f, 0.62f), hsl((300f + hue) % 360f, 0.85f, 0.62f),
                hsl((345f + hue) % 360f, 0.9f, 0.62f), hsl((35f + hue) % 360f, 0.95f, 0.60f)),
            floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
        for (path in arrayOf(topSand, bottomSand)) {
            if (path.isEmpty) continue
            fill.shader = liquid; fill.alpha = 255
            c.drawPath(path, fill)
            fill.shader = sandShader(0x00FFFFFF, 0xE6280050.toInt(), 0xE6FFFFFF.toInt(), "prism")
            fill.alpha = 90
            c.drawPath(path, fill)
            fill.alpha = 255
        }
        fill.shader = null
    }

    // Grain motion is deterministic from the clock - no particle state to keep in sync.
    private val grainSpeed = FloatArray(26)
    private val grainPhase = FloatArray(26)
    private val grainX = FloatArray(26)
    private val splashPhase = FloatArray(14)
    private val splashAngle = FloatArray(14)
    private val splashSpeed = FloatArray(14)

    init {
        val r = Random(11)
        for (i in 0 until 26) { grainSpeed[i] = 0.9f + r.nextFloat() * 0.6f; grainPhase[i] = r.nextFloat(); grainX[i] = r.nextFloat() - 0.5f }
        for (i in 0 until 14) {
            splashPhase[i] = r.nextFloat()
            splashAngle[i] = (-Math.PI / 2 + (r.nextFloat() - 0.5f) * 2.2f).toFloat()
            splashSpeed[i] = 5f + r.nextFloat() * 9f
        }
    }

    private fun stream(c: Canvas, g: Geo, remaining: Float, tBot: Float, mound: Float, seconds: Float, color: Int, width: Float, running: Boolean) {
        if (!running || remaining <= 0.0005f) return
        val yTop = g.cy - g.h * 0.01f
        val yEnd = g.cy + tBot * g.h - mound
        fill.shader = null; fill.color = color
        c.drawRect(g.cx - width / 2f, yTop, g.cx + width / 2f, yEnd, fill)
        val grain = max(1.6f, g.w * 0.012f)
        val scale = g.h / 300f
        fill.alpha = 217
        for (i in 0 until 26) {
            val u = (seconds * grainSpeed[i] * 0.9f + grainPhase[i]) % 1f
            val y = yTop + u * u * (yEnd - yTop)
            val x = g.cx + grainX[i] * width * 2.2f
            c.drawRect(x - grain / 2f, y, x + grain / 2f, y + grain, fill)
        }
        for (i in 0 until 14) {
            val u = (seconds * 2.2f + splashPhase[i]) % 1f
            val x = g.cx + cos(splashAngle[i]) * splashSpeed[i] * scale * u * 1.6f
            val y = yEnd + sin(splashAngle[i]) * splashSpeed[i] * scale * u * 1.2f + 16f * scale * u * u
            fill.alpha = (204 * (1f - u)).toInt()
            c.drawRect(x - grain / 2f, y - grain / 2f, x + grain / 2f, y + grain / 2f, fill)
        }
        fill.alpha = 255
    }

    // ---- DOT ----

    private fun dotStep(g: Geo) = max(6f, g.h * 0.052f)

    /** The glass drawn as dots: its outline, the rims, and every unlit cell inside it. */
    private fun dotStatic(c: Canvas, g: Geo) {
        val step = dotStep(g)
        val rd = step * 0.34f
        fill.shader = null
        fill.color = 0xFF1B1B1B.toInt()
        val cols = kotlin.math.ceil(g.w * 1.2f / step).toInt()
        val rows = kotlin.math.ceil(g.h / step).toInt()
        for (gy in -rows..rows) for (gx in -cols..cols) {
            val x = g.cx + gx * step
            val y = g.cy + gy * step
            if (inGlass(g, x, y)) c.drawCircle(x, y, rd, fill)
        }
        // Outline: dots at even spacing along each wall, just outside it.
        fill.color = 0xFFE8E8E8.toInt()
        for (side in intArrayOf(-1, 1)) for (top in booleanArrayOf(true, false)) {
            work.reset()
            for (i in 0..200) {
                val t = i / 200f
                val x = g.cx + side * (g.p.widthAt(t) * g.w + step * 0.6f)
                val y = g.cy + (if (top) -1 else 1) * t * g.h
                if (i == 0) work.moveTo(x, y) else work.lineTo(x, y)
            }
            val pm = PathMeasure(work, false)
            val pos = FloatArray(2)
            var d = 0f
            while (d <= pm.length) {
                pm.getPosTan(d, pos, null)
                c.drawCircle(pos[0], pos[1], rd * 0.8f, fill)
                d += step
            }
        }
        // Caps: two rows of dots top and bottom.
        for (s in intArrayOf(-1, 1)) {
            val rw = g.p.widthAt(1f) * g.w + step * 1.6f
            for (row in 0 until 2) {
                var x = -rw
                while (x <= rw + 0.1f) {
                    c.drawCircle(g.cx + x, g.cy + s * (g.h + step * (0.9f + row)), rd, fill)
                    x += step
                }
            }
        }
    }

    private fun inGlass(g: Geo, x: Float, y: Float): Boolean {
        val t = abs(y - g.cy) / g.h
        if (t > 1f) return false
        return abs(x - g.cx) <= g.p.widthAt(t) * g.w
    }

    private fun drawDotSand(c: Canvas, g: Geo, tTop: Float, tBot: Float, dip: Float, mound: Float, remaining: Float, seconds: Float, running: Boolean, accent: Int) {
        val step = dotStep(g)
        val rd = step * 0.34f
        // The lit cells only change when the sand crosses a dot, so they're re-recorded then.
        val q = "${(tTop * 400).toInt()}:${(tBot * 400).toInt()}:${(dip / step * 4).toInt()}:${(mound / step * 4).toInt()}"
        if (q != dotKey) {
            dotKey = q
            dotSand = Picture()
            val rc = dotSand.beginRecording(canvasW, canvasH)
            fill.shader = null; fill.color = accent
            val cols = kotlin.math.ceil(g.w * 1.2f / step).toInt()
            val rows = kotlin.math.ceil(g.h / step).toInt()
            val ws = g.p.widthAt(tTop) * g.w
            val wb = g.p.widthAt(tBot) * g.w
            for (gy in -rows..rows) for (gx in -cols..cols) {
                val x = g.cx + gx * step
                val y = g.cy + gy * step
                if (!inGlass(g, x, y)) continue
                val sand = if (y <= g.cy) {
                    if (tTop <= 0.0005f || ws <= 0f) false else {
                        val u = ((x - g.cx) / ws).coerceIn(-1f, 1f)
                        val surface = g.cy - tTop * g.h + dip * (1f - abs(u)).pow(2.2f)
                        y >= surface
                    }
                } else {
                    if (tBot >= 0.9995f || wb <= 0f) false else {
                        val u = ((x - g.cx) / wb).coerceIn(-1f, 1f)
                        val k = 1f - abs(u)
                        val surface = g.cy + tBot * g.h - mound * (k.pow(1.35f) * 1.35f - 0.35f)
                        y >= surface
                    }
                }
                if (sand) rc.drawCircle(x, y, rd, fill)
            }
            dotSand.endRecording()
        }
        c.drawPicture(dotSand)
        // The falling grains: a column of dots stepping down through the neck.
        if (running && remaining > 0.0005f) {
            val yEnd = g.cy + tBot * g.h - mound
            val head = (seconds * 7f) % 1f
            fill.shader = null; fill.color = accent
            var i = 0
            while (true) {
                val y = g.cy + (i + head) * step
                if (y > yEnd - step * 0.5f) break
                fill.alpha = if (i % 2 == 0) 255 else 140
                c.drawCircle(g.cx, y, rd * 0.9f, fill)
                i++
            }
            fill.alpha = 255
        }
    }

    // ---- shared details ----

    private fun highlights(c: Canvas, g: Geo, strong: Int, soft: Int) {
        for (top in booleanArrayOf(true, false)) {
            val sgn = if (top) -1f else 1f
            work.reset()
            for (i in 0..30) {
                val t = 0.28f + 0.60f * i / 30f
                val x = g.cx - g.p.widthAt(t) * g.w * 0.80f
                val y = g.cy + sgn * t * g.h
                if (i == 0) work.moveTo(x, y) else work.lineTo(x, y)
            }
            stroke.shader = null; stroke.color = strong; stroke.strokeWidth = g.w * 0.07f
            c.drawPath(work, stroke)
            work.reset()
            for (i in 0..30) {
                val t = 0.45f + 0.35f * i / 30f
                val x = g.cx + g.p.widthAt(t) * g.w * 0.84f
                val y = g.cy + sgn * t * g.h
                if (i == 0) work.moveTo(x, y) else work.lineTo(x, y)
            }
            stroke.color = soft; stroke.strokeWidth = g.w * 0.025f
            c.drawPath(work, stroke)
        }
    }

    private fun rims(c: Canvas, g: Geo, fillColor: Int, edge: Int, halfHeight: Float, widen: Float, edgeWidth: Float) {
        for (s in intArrayOf(-1, 1)) {
            val rw = g.p.widthAt(1f) * g.w * widen
            rect.set(g.cx - rw, g.cy + s * g.h - halfHeight, g.cx + rw, g.cy + s * g.h + halfHeight)
            fill.shader = null; fill.color = fillColor
            c.drawOval(rect, fill)
            stroke.shader = null; stroke.color = edge; stroke.strokeWidth = edgeWidth
            c.drawOval(rect, stroke)
        }
    }

    /** One end of the wooden frame, seen from eye level. */
    private fun plate(c: Canvas, cx: Float, cy: Float, pw: Float, ph: Float, top: Boolean) {
        val faceY = if (top) cy + ph / 2f else cy - ph / 2f
        fill.shader = if (top) {
            LinearGradient(cx - pw, 0f, cx + pw, 0f, intArrayOf(0xFF2A1608.toInt(), 0xFF5A3515.toInt(), 0xFF2A1608.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(cx - pw, 0f, cx + pw, 0f, intArrayOf(0xFF8A5A2C.toInt(), 0xFFE2AE72.toInt(), 0xFFD49A5C.toInt(), 0xFF7A4A20.toInt()), floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        rect.set(cx - pw * 0.985f, faceY - ph * 0.30f, cx + pw * 0.985f, faceY + ph * 0.30f)
        c.drawOval(rect, fill)

        // A turned lip where the glass seats into the wood.
        val lipW = pw * 0.80f
        val lipH = ph * 0.34f
        val lipY = if (top) cy + ph / 2f else cy - ph / 2f - lipH
        fill.shader = LinearGradient(cx - lipW, 0f, cx + lipW, 0f,
            intArrayOf(0xFF4B2A12.toInt(), 0xFFC98C4C.toInt(), 0xFFE6B178.toInt(), 0xFF8B5325.toInt(), 0xFF3A1F0B.toInt()),
            floatArrayOf(0f, 0.3f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP)
        rect.set(cx - lipW, lipY, cx + lipW, lipY + lipH)
        c.drawRoundRect(rect, lipH * 0.45f, lipH * 0.45f, fill)
        fill.shader = null
        fill.color = if (top) 0x59140A02 else 0x40FFE1B4
        val ly = if (top) lipY + lipH else lipY
        rect.set(cx - lipW * 0.98f, ly - lipH * 0.35f, cx + lipW * 0.98f, ly + lipH * 0.35f)
        c.drawOval(rect, fill)

        // The plate's side band.
        val x = cx - pw
        val y = cy - ph / 2f
        val r = ph * 0.32f
        fill.shader = LinearGradient(0f, y, 0f, y + ph,
            intArrayOf(0xFFB77C3F.toInt(), 0xFFD9A064.toInt(), 0xFF9A6130.toInt(), 0xFF6D3F1B.toInt(), 0xFF4B2A12.toInt()),
            floatArrayOf(0f, 0.18f, 0.5f, 0.85f, 1f), Shader.TileMode.CLAMP)
        rect.set(x, y, x + pw * 2f, y + ph)
        c.drawRoundRect(rect, r, r, fill)
        fill.shader = null

        c.save()
        work.reset(); work.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(work)
        val rnd = Random(if (top) 3 else 5)
        for (i in 0 until 22) {
            stroke.shader = null
            stroke.color = Color.argb((255 * (0.10f + rnd.nextFloat() * 0.18f)).toInt(), 60, 30, 8)
            stroke.strokeWidth = 0.6f + rnd.nextFloat() * 0.9f
            val yy = y + rnd.nextFloat() * ph
            work.reset()
            work.moveTo(x, yy)
            work.cubicTo(x + pw * 0.6f, yy + (rnd.nextFloat() - 0.5f) * 3f, x + pw * 1.4f, yy + (rnd.nextFloat() - 0.5f) * 3f, x + pw * 2f, yy)
            c.drawPath(work, stroke)
        }
        stroke.color = 0x8C281405.toInt(); stroke.strokeWidth = 1.2f
        c.drawLine(x, cy + ph * 0.05f, x + pw * 2f, cy + ph * 0.05f, stroke)
        stroke.color = 0x40FFDCAA
        c.drawLine(x, cy + ph * 0.05f + 1.3f, x + pw * 2f, cy + ph * 0.05f + 1.3f, stroke)
        fill.shader = LinearGradient(x, 0f, x + pw * 2f, 0f,
            intArrayOf(0x8C140800.toInt(), 0x00140800, 0x1FFFE6BE, 0x00140800, 0x99140800.toInt()),
            floatArrayOf(0f, 0.12f, 0.32f, 0.88f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(x, y, x + pw * 2f, y + ph, fill)
        fill.shader = null
        c.restore()
    }

    /** A turned spindle: knobs at the ends, a bead in the middle, rings between. */
    private fun post(c: Canvas, x: Float, yA: Float, yB: Float, radius: Float, front: Boolean, alpha: Float) {
        val length = yB - yA
        fun profile(v: Float): Float {
            fun knob(center: Float, spread: Float, amount: Float) = amount * exp(-((v - center) / spread).pow(2))
            var r = 0.42f + 0.10f * (abs(v - 0.5f) * 2f).pow(1.5f)
            r += knob(0.05f, 0.035f, 0.55f) + knob(0.95f, 0.035f, 0.55f) + knob(0.13f, 0.02f, 0.28f) + knob(0.87f, 0.02f, 0.28f) +
                knob(0.5f, 0.03f, 0.40f) + knob(0.43f, 0.012f, 0.18f) + knob(0.57f, 0.012f, 0.18f)
            return min(1f, r)
        }
        work.reset()
        val n = 120
        for (i in 0..n) {
            val v = i.toFloat() / n
            val xx = x - profile(v) * radius
            val yy = yA + v * length
            if (i == 0) work.moveTo(xx, yy) else work.lineTo(xx, yy)
        }
        for (i in n downTo 0) { val v = i.toFloat() / n; work.lineTo(x + profile(v) * radius, yA + v * length) }
        work.close()
        fill.shader = if (front) {
            LinearGradient(x - radius, 0f, x + radius, 0f,
                intArrayOf(0xFF4A2811.toInt(), 0xFFC98C4C.toInt(), 0xFFE6B178.toInt(), 0xFF8B5325.toInt(), 0xFF3A1F0B.toInt()),
                floatArrayOf(0f, 0.28f, 0.42f, 0.7f, 1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(x - radius, 0f, x + radius, 0f,
                intArrayOf(0xFF2D1A0C.toInt(), 0xFF6E4520.toInt(), 0xFF24150A.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        fill.alpha = (255 * alpha).toInt()
        c.drawPath(work, fill)
        fill.shader = null
        fill.alpha = 255
        stroke.shader = null; stroke.color = Color.argb((128 * alpha).toInt(), 30, 14, 4); stroke.strokeWidth = 1f
        for (v in floatArrayOf(0.09f, 0.17f, 0.46f, 0.54f, 0.83f, 0.91f)) {
            val rr = profile(v) * radius
            c.drawLine(x - rr, yA + v * length, x + rr, yA + v * length, stroke)
        }
    }

    private val textures = HashMap<String, Shader>()

    /** A tiling grain texture, generated once per look. */
    private fun sandShader(base: Int, dark: Int, light: Int, name: String): Shader = textures.getOrPut(name) {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(base, android.graphics.PorterDuff.Mode.SRC)
        val p = Paint()
        val r = Random(7)
        repeat(1400) {
            p.color = if (r.nextFloat() < 0.55f) dark else light
            p.alpha = (255 * (0.25f + r.nextFloat() * 0.45f)).toInt()
            val s = if (r.nextFloat() < 0.85f) 1f else 1.6f
            val x = r.nextFloat() * size
            val y = r.nextFloat() * size
            canvas.drawRect(x, y, x + s, y + s, p)
        }
        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    companion object {
        /** HSL to an ARGB int, for the Prism's slowly turning colours. */
        fun hsl(h: Float, s: Float, l: Float): Int {
            val c = (1f - abs(2f * l - 1f)) * s
            val hp = h / 60f
            val x = c * (1f - abs(hp % 2f - 1f))
            val (r1, g1, b1) = when (floor(hp).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = l - c / 2f
            return Color.rgb(((r1 + m) * 255).toInt().coerceIn(0, 255), ((g1 + m) * 255).toInt().coerceIn(0, 255), ((b1 + m) * 255).toInt().coerceIn(0, 255))
        }
    }
}
