package rocks.ndot.widgets.instruments.metronome

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import rocks.ndot.widgets.kit.Fonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Both metronome faces, ported from the canvas prototypes.
 *
 * Studio is laid out in fixed units - 372 wide by 616 tall - and scaled to fit; its touch
 * targets are the same rectangles it draws. Pendulum is proportional to its canvas.
 */
object MetronomeArt {

    const val STUDIO_W = 372f
    const val STUDIO_H = 616f

    enum class Region { NONE, SUBDIVISION, SIGNATURE, MINUS, PLUS, TAP, DIAL, PLAY }

    private const val DIAL_CX = 186f
    private const val DIAL_CY = 304.5f
    private const val DIAL_R = 148.8f
    private const val LCD_X = 84f
    private const val LCD_W = 204f

    class StudioState(
        val bpm: Int,
        val signature: String,
        val subdivision: Int,
        val beats: Int,
        val beat: Int,
        val pulse: Float,
        val playing: Boolean,
        val pressed: Region,
        val dialTurn: Float,
    )

    fun studioHit(ux: Float, uy: Float): Region {
        fun inside(x: Float, y: Float, w: Float, h: Float) = ux in x..(x + w) && uy in y..(y + h)
        val dx = ux - DIAL_CX
        val dy = uy - DIAL_CY
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        return when {
            inside(14f, 64f, 64f, 76f) -> Region.SUBDIVISION
            inside(294f, 64f, 64f, 76f) -> Region.SIGNATURE
            inside(LCD_X, 64f, 64f, 76f) -> Region.MINUS
            inside(LCD_X + LCD_W - 64f, 64f, 64f, 76f) -> Region.PLUS
            d <= DIAL_R * 0.52f -> Region.TAP
            d <= DIAL_R * 1.04f -> Region.DIAL
            inside(10f, 474f, 352f, 56f) -> Region.PLAY
            else -> Region.NONE
        }
    }

    /** The dial's angle for a touch point, for turning it. */
    fun studioAngle(ux: Float, uy: Float): Float = kotlin.math.atan2(uy - DIAL_CY, ux - DIAL_CX)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) {
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, r, r, p)
    }

    private fun centred(c: Canvas, s: String, x: Float, y: Float) {
        c.drawText(s, x, y - (text.ascent() + text.descent()) / 2f, text)
    }

    // ============================ STUDIO ============================

    /** Draws Studio with its top-left at the origin, [k] pixels per unit. */
    fun drawStudio(c: Canvas, k: Float, st: StudioState) {
        c.save()
        c.scale(k, k)
        val w = STUDIO_W

        fun panel(py: Float, ph: Float) {
            fill.shader = null; fill.color = 0xFFF1F1EF.toInt()
            fill.setShadowLayer(10f, 0f, 3f, 0x1F000000)
            rr(c, 0f, py, w, ph, 18f, fill)
            fill.clearShadowLayer()
            fill.shader = LinearGradient(0f, py, 0f, py + ph, 0xFFF6F6F4.toInt(), 0xFFEBEBE9.toInt(), Shader.TileMode.CLAMP)
            rr(c, 0f, py, w, ph, 18f, fill)
            fill.shader = null
            stroke.color = 0xE6FFFFFF.toInt(); stroke.strokeWidth = 1.2f
            rr(c, 1f, py + 1f, w - 2f, ph - 2f, 17f, stroke)
        }

        // Brand plate.
        panel(0f, 44f)
        fill.color = 0xFF8A8A8A.toInt()
        val bars = floatArrayOf(4f, 7f, 10f, 14f, 18f, 14f, 10f, 7f, 4f)
        bars.forEachIndexed { i, hh -> val bh = hh * 0.8f; c.drawRect(w / 2f - 52f + i * 3f, 22f - bh / 2f, w / 2f - 52f + i * 3f + 1.2f, 22f + bh / 2f, fill) }
        text.typeface = sans; text.textSize = 11f; text.color = 0xFF6A6A6A.toInt(); text.textAlign = Paint.Align.LEFT
        centred(c, "mezura  metronome", w / 2f - 22f, 22f)
        text.textAlign = Paint.Align.CENTER

        // Main panel: LCD row and dial.
        val y = 52f
        panel(y, 410f)
        val rowY = y + 12f
        val rowH = 76f
        fill.color = 0xFF2A2A2A.toInt()
        rr(c, 10f, rowY, w - 20f, rowH, 12f, fill)
        for ((kx, region) in listOf(14f to Region.SUBDIVISION, w - 14f - 64f to Region.SIGNATURE)) {
            val down = if (st.pressed == region) 1.5f else 0f
            fill.shader = LinearGradient(0f, rowY, 0f, rowY + rowH, 0xFFF5F5F3.toInt(), 0xFFDCDCDA.toInt(), Shader.TileMode.CLAMP)
            rr(c, kx, rowY + 4f + down, 64f, rowH - 8f, 9f, fill)
            fill.shader = null
            val cx = kx + 32f
            val cy = rowY + rowH / 2f + down
            fill.color = 0xFFEFEFED.toInt(); fill.setShadowLayer(6f, 0f, if (down > 0) 0.5f else 2f, 0x40000000)
            c.drawCircle(cx, cy, 22f, fill)
            fill.clearShadowLayer()
            text.color = 0xFF2A2A2A.toInt()
            if (region == Region.SUBDIVISION) {
                text.typeface = sans; text.textSize = if (st.subdivision == 3) 16f else 22f
                centred(c, when (st.subdivision) { 1 -> "♩"; 2 -> "♫"; 3 -> "3"; else -> "♬" }, cx, cy)
            } else {
                text.typeface = Fonts.mediumTypeface; text.textSize = 13f
                centred(c, st.signature, cx, cy)
            }
        }
        fill.shader = LinearGradient(0f, rowY, 0f, rowY + rowH, 0xFF2C3133.toInt(), 0xFF1B1E1F.toInt(), Shader.TileMode.CLAMP)
        rr(c, LCD_X, rowY + 4f, LCD_W, rowH - 8f, 7f, fill)
        fill.shader = null
        text.typeface = sans; text.textSize = 10f; text.color = 0xFFA9B0B3.toInt()
        centred(c, Metronome.tempoName(st.bpm), LCD_X + LCD_W / 2f, rowY + 17f)
        text.typeface = Fonts.condensedTypeface; text.textSize = 32f; text.color = 0xFFF4F6F6.toInt()
        centred(c, st.bpm.toString(), LCD_X + LCD_W / 2f, rowY + 39f)
        text.typeface = sans; text.textSize = 9f; text.color = 0xFF8E9699.toInt()
        centred(c, "BPM", LCD_X + LCD_W / 2f, rowY + 60f)
        fill.color = if (st.pressed == Region.MINUS) 0xFFFFFFFF.toInt() else 0xFF8E9699.toInt()
        c.drawRect(LCD_X + 14f, rowY + rowH / 2f - 1f, LCD_X + 26f, rowY + rowH / 2f + 1f, fill)
        fill.color = if (st.pressed == Region.PLUS) 0xFFFFFFFF.toInt() else 0xFF8E9699.toInt()
        c.drawRect(LCD_X + LCD_W - 26f, rowY + rowH / 2f - 1f, LCD_X + LCD_W - 14f, rowY + rowH / 2f + 1f, fill)
        c.drawRect(LCD_X + LCD_W - 21f, rowY + rowH / 2f - 6f, LCD_X + LCD_W - 19f, rowY + rowH / 2f + 6f, fill)

        // The dial.
        val dcx = DIAL_CX
        val dcy = DIAL_CY
        val r = DIAL_R
        fill.color = 0xFFEEEEEC.toInt(); fill.setShadowLayer(18f, 0f, 8f, 0x38000000)
        c.drawCircle(dcx, dcy, r, fill)
        fill.clearShadowLayer()
        stroke.color = 0xFF6A6A6A.toInt(); stroke.strokeWidth = 1.6f
        c.drawCircle(dcx, dcy, r, stroke)
        fill.shader = RadialGradient(dcx - r * 0.3f, dcy - r * 0.3f, r * 1.1f, 0xFFFBFBFA.toInt(), 0xFFDEDEDE.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(dcx, dcy, r - 2f, fill)
        fill.shader = null
        val rot = st.dialTurn
        stroke.color = 0x47000000; stroke.strokeWidth = 0.9f; stroke.strokeCap = Paint.Cap.BUTT
        for (i in 0 until 120) {
            val a = i * 3f * PI.toFloat() / 180f + rot
            c.drawLine(dcx + cos(a) * r * 0.86f, dcy + sin(a) * r * 0.86f, dcx + cos(a) * r * 0.95f, dcy + sin(a) * r * 0.95f, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND
        fill.color = 0xFF1A1A1A.toInt()
        val ia = -PI.toFloat() / 2f + rot
        c.drawCircle(dcx + cos(ia) * r * 0.80f, dcy + sin(ia) * r * 0.80f, 3f, fill)
        stroke.color = 0xFF1A1A1A.toInt(); stroke.strokeWidth = 1.8f
        for (a in floatArrayOf(PI.toFloat() * 0.75f, PI.toFloat() * 0.25f)) {
            c.drawLine(dcx + cos(a) * r * 0.99f, dcy + sin(a) * r * 0.99f, dcx + cos(a) * r * 0.90f, dcy + sin(a) * r * 0.90f, stroke)
        }
        fill.color = 0xFF111111.toInt()
        c.drawCircle(dcx, dcy, r * 0.66f, fill)
        if (st.pulse > 0f) {
            stroke.color = if (st.beat == 0 && st.beats > 1) Color.argb((255 * st.pulse).toInt(), 215, 25, 33) else Color.argb((220 * st.pulse).toInt(), 240, 240, 240)
            stroke.strokeWidth = r * 0.05f
            c.drawCircle(dcx, dcy, r * 0.60f, stroke)
        }
        val tapScale = if (st.pressed == Region.TAP) 0.965f else 1f
        val tr = r * 0.52f * tapScale
        fill.color = 0xFFF3F3F1.toInt(); fill.setShadowLayer(14f * tapScale, 0f, if (tapScale < 1f) 2f else 6f, 0x80000000.toInt())
        c.drawCircle(dcx, dcy, tr, fill)
        fill.clearShadowLayer()
        fill.shader = RadialGradient(dcx - r * 0.15f, dcy - r * 0.2f, tr * 1.1f, 0xFFFFFFFF.toInt(), 0xFFE6E6E4.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(dcx, dcy, tr, fill)
        fill.shader = null
        stroke.color = 0x2E000000; stroke.strokeWidth = 1f
        c.drawCircle(dcx, dcy, r * 0.47f * tapScale, stroke)
        text.typeface = sans; text.textSize = 13f; text.color = 0xFF5A5A5A.toInt()
        centred(c, "Tap", dcx, dcy)

        // Play bar.
        val py = y + 410f + 8f
        panel(py, 64f)
        fill.color = 0xFF2A2A2A.toInt()
        rr(c, 10f, py + 10f, w - 20f, 44f, 11f, fill)
        val barDown = if (st.pressed == Region.PLAY) 1.5f else 0f
        fill.shader = LinearGradient(0f, py + 12f, 0f, py + 52f,
            if (st.playing) 0xFFE9E9E7.toInt() else 0xFFF4F4F2.toInt(), if (st.playing) 0xFFD2D2D0.toInt() else 0xFFDDDDDB.toInt(), Shader.TileMode.CLAMP)
        rr(c, 14f, py + 14f + barDown, w - 28f, 36f - barDown, 18f, fill)
        fill.shader = null
        fill.color = 0xFF222222.toInt()
        val ic = py + 32f + barDown / 2f
        if (st.playing) {
            c.drawRect(w / 2f - 7f, ic - 7f, w / 2f + 7f, ic + 7f, fill)
        } else {
            path.reset()
            path.moveTo(w / 2f - 9f, ic - 7f); path.lineTo(w / 2f + 1f, ic); path.lineTo(w / 2f - 9f, ic + 7f); path.close()
            c.drawPath(path, fill)
            c.drawRect(w / 2f + 4f, ic - 7f, w / 2f + 6.4f, ic + 7f, fill)
            c.drawRect(w / 2f + 8.5f, ic - 7f, w / 2f + 10.9f, ic + 7f, fill)
        }

        // Dot-matrix strip: one block per beat, the current one lit.
        val ly = py + 72f
        panel(ly, 74f)
        fill.color = 0xFF1D1F20.toInt()
        rr(c, 10f, ly + 10f, w - 20f, 54f, 10f, fill)
        val gx0 = 20f
        val gw = w - 40f
        val top = ly + 16f
        val bottom = ly + 58f
        val beats = st.beats.coerceAtLeast(1)
        val gap = 6f
        val block = (gw - gap * (beats - 1)) / beats
        c.restore()
        // The dot pattern is a repeating bitmap tile, so the strip is a handful of rectangles
        // rather than a thousand circles every frame.
        for (b in 0 until beats) {
            val x0 = gx0 + b * (block + gap)
            val lit = st.playing && b == st.beat
            val color = when {
                !lit -> 0xFF34393A.toInt()
                b == 0 && beats > 1 -> 0xFFFF4A3D.toInt()
                else -> 0xFFE9F2EF.toInt()
            }
            fill.shader = dotTile(color, (3.2f * k).roundToInt().coerceAtLeast(2))
            c.drawRect(x0 * k, top * k, (x0 + block) * k, bottom * k, fill)
        }
        fill.shader = null
    }

    private val tiles = HashMap<String, Shader>()

    private fun dotTile(color: Int, step: Int): Shader = tiles.getOrPut("$color:$step") {
        val bmp = Bitmap.createBitmap(step, step, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        Canvas(bmp).drawCircle(step / 2f, step / 2f, step * 0.3f, p)
        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ============================ PENDULUM ============================

    class PendulumGeometry(val cx: Float, val baseY: Float, val topY: Float, val bw: Float, val tw: Float, val u: Float) {
        val scaleTop: Float get() = topY + 40f * u
        val scaleBottom: Float get() = baseY - 34f * u
        val pivotY: Float get() = baseY - 18f * u

        /** Where the weight sits for a tempo: slow at the top of the rod, fast near the pivot. */
        fun weightY(bpm: Int): Float {
            val span = scaleBottom - scaleTop - 20f * u
            val f = (ln(bpm / 40.0) / ln(208.0 / 40.0)).toFloat()
            return scaleTop + 10f * u + span * f
        }

        fun bpmForY(y: Float): Int {
            val span = scaleBottom - scaleTop - 20f * u
            val f = ((y - scaleTop - 10f * u) / span).toDouble()
            return (40.0 * Math.pow(208.0 / 40.0, f)).roundToInt().coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)
        }
    }

    fun pendulumGeometry(w: Float, h: Float): PendulumGeometry {
        val baseY = h * 0.90f
        val topY = h * 0.07f
        val bodyH = baseY - topY
        val bw = min(w * 0.32f, bodyH * 0.29f)
        return PendulumGeometry(w / 2f, baseY, topY, bw, bw * 0.25f, bodyH / 430f)
    }

    private val scaleMarks = intArrayOf(40, 44, 48, 52, 56, 60, 63, 66, 69, 72, 76, 80, 84, 88, 92, 96, 100, 104, 108, 112, 116, 120, 126, 132, 138, 144, 152, 160, 168, 176, 184, 192, 200, 208)

    private var bodyKey = ""
    private var body = android.graphics.Picture()

    /**
     * The case, scale and plinth never move, so they're recorded once per size and replayed;
     * only the rod, the weight and the pivot are drawn fresh each frame.
     */
    fun drawPendulum(c: Canvas, w: Float, h: Float, bpm: Int, angle: Float, beatFlash: Float) {
        val g = pendulumGeometry(w, h)
        val key = "$w:$h"
        if (key != bodyKey) {
            bodyKey = key
            body = android.graphics.Picture()
            val rc = body.beginRecording(max(1, w.toInt()), max(1, h.toInt()))
            pendulumBody(rc, g, w)
            body.endRecording()
        }
        c.drawPicture(body)
        pendulumRod(c, g, bpm, angle, beatFlash)
    }

    private fun pendulumBody(c: Canvas, g: PendulumGeometry, w: Float) {
        val u = g.u
        val cx = g.cx
        val bw = g.bw
        val tw = g.tw
        // Floor shadow.
        fill.shader = RadialGradient(cx, g.baseY + 14f * u, bw * 1.6f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, g.baseY - 20f * u, w, g.baseY + 60f * u, fill)
        fill.shader = null

        // The pyramid body in polished mahogany.
        path.reset()
        path.moveTo(cx - bw, g.baseY)
        path.lineTo(cx - tw, g.topY + 10f * u)
        path.quadTo(cx, g.topY - 6f * u, cx + tw, g.topY + 10f * u)
        path.lineTo(cx + bw, g.baseY)
        path.close()
        fill.shader = LinearGradient(cx - bw, 0f, cx + bw, 0f,
            intArrayOf(0xFF2A0F06.toInt(), 0xFF6B2A12.toInt(), 0xFF9A4520.toInt(), 0xFF7A3315.toInt(), 0xFF2A0F06.toInt()),
            floatArrayOf(0f, 0.18f, 0.42f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        c.save()
        c.clipPath(path)
        val rnd = Random(9)
        val grain = Path()
        for (i in 0 until 70) {
            stroke.color = Color.argb((255 * (0.08f + rnd.nextFloat() * 0.16f)).toInt(), 30, 8, 0)
            stroke.strokeWidth = (0.6f + rnd.nextFloat() * 1.2f) * u
            val xx = cx - bw + rnd.nextFloat() * bw * 2f
            grain.reset()
            grain.moveTo(xx, g.baseY)
            grain.cubicTo(xx + (rnd.nextFloat() - 0.5f) * 8f * u, g.baseY - (g.baseY - g.topY) * 0.4f,
                xx + (rnd.nextFloat() - 0.5f) * 8f * u, g.topY + 60f * u, cx + (xx - cx) * 0.25f, g.topY)
            c.drawPath(grain, stroke)
        }
        fill.shader = LinearGradient(cx - bw, 0f, cx + bw, 0f,
            intArrayOf(0x00FFDCBE, 0x38FFDCBE, 0x00FFDCBE), floatArrayOf(0.28f, 0.36f, 0.44f), Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        c.restore()

        // The scale window.
        val sw0 = bw * 0.30f
        val sw1 = tw * 0.62f
        val sTop = g.scaleTop
        val sBot = g.scaleBottom
        fun halfWidthAt(yy: Float) = sw1 + (sw0 - sw1) * (yy - sTop) / (sBot - sTop)
        path.reset()
        path.moveTo(cx - sw1, sTop); path.lineTo(cx + sw1, sTop); path.lineTo(cx + sw0, sBot); path.lineTo(cx - sw0, sBot); path.close()
        fill.color = 0xFFE9DCC0.toInt()
        c.drawPath(path, fill)
        stroke.color = 0xFFC9A45A.toInt(); stroke.strokeWidth = 2f * u
        c.drawPath(path, stroke)
        text.typeface = serif; text.textSize = 6.6f * u; text.textAlign = Paint.Align.LEFT; text.color = 0xFF3B2412.toInt()
        fill.color = 0xFF3B2412.toInt()
        scaleMarks.forEachIndexed { i, b ->
            val yy = g.weightY(b)
            val ww = halfWidthAt(yy)
            c.drawRect(cx - ww * 0.95f, yy - 0.4f * u, cx - ww * 0.70f, yy + 0.4f * u, fill)
            if (i % 2 == 0) c.drawText(b.toString(), cx - ww * 0.62f, yy - (text.ascent() + text.descent()) / 2f, text)
        }
        text.textAlign = Paint.Align.CENTER

        // Plinth with a brass line.
        fill.shader = LinearGradient(0f, g.baseY, 0f, g.baseY + 16f * u, 0xFF7A3315.toInt(), 0xFF2A0F06.toInt(), Shader.TileMode.CLAMP)
        rr(c, cx - bw - 10f * u, g.baseY - 2f * u, (bw + 10f * u) * 2f, 16f * u, 3f * u, fill)
        fill.shader = null
        fill.color = 0xFFC9A45A.toInt()
        c.drawRect(cx - bw - 8f * u, g.baseY - 2f * u, cx + bw + 8f * u, g.baseY - 0.5f * u, fill)
    }

    private fun pendulumRod(c: Canvas, g: PendulumGeometry, bpm: Int, angle: Float, beatFlash: Float) {
        val u = g.u
        val cx = g.cx
        // The rod and its sliding weight, swinging about a pivot low in the case.
        val pivotY = g.pivotY
        val rodLen = pivotY - g.topY + 34f * u
        c.save()
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat(), cx, pivotY)
        fill.shader = LinearGradient(cx - 2f * u, 0f, cx + 2f * u, 0f,
            intArrayOf(0xFF6D6D6D.toInt(), 0xFFF0F0F0.toInt(), 0xFF6D6D6D.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(cx - 1.6f * u, pivotY - rodLen, cx + 1.6f * u, pivotY, fill)
        val wy = g.weightY(bpm)
        fill.shader = LinearGradient(cx - 16f * u, 0f, cx + 16f * u, 0f,
            intArrayOf(0xFF6B4A14.toInt(), 0xFFF1CF7A.toInt(), 0xFFCAA24A.toInt(), 0xFF5B3E10.toInt()),
            floatArrayOf(0f, 0.35f, 0.55f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx - 10f * u, wy - 11f * u); path.lineTo(cx + 10f * u, wy - 11f * u)
        path.lineTo(cx + 15f * u, wy + 9f * u); path.lineTo(cx - 15f * u, wy + 9f * u); path.close()
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = 0xFF3A2508.toInt()
        c.drawRect(cx - 15f * u, wy + 1f * u, cx + 15f * u, wy + 2.2f * u, fill)
        c.restore()

        // Pivot cap, brightening on each tick.
        fill.color = 0xFFC9A45A.toInt()
        c.drawCircle(cx, pivotY, 4f * u, fill)
        if (beatFlash > 0f) {
            fill.color = Color.argb((200 * beatFlash).toInt(), 255, 236, 180)
            c.drawCircle(cx, pivotY, 4f * u + 4f * u * beatFlash, fill)
        }
    }
}
