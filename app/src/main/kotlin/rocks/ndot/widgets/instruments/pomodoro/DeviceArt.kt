package rocks.ndot.widgets.instruments.pomodoro

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
import rocks.ndot.widgets.kit.PixelFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The three Pomodoro devices. Proportions are fractions of the device width, U. */
enum class Device(val id: String, val label: String, val aspect: Float, val light: Boolean, val sound: rocks.ndot.widgets.kit.Feedback.Sound) {
    MODULE("module", "Module", 0.64f, false, rocks.ndot.widgets.kit.Feedback.Sound.KEY_DOWN),
    SLAB("slab", "Slab", 0.62f, false, rocks.ndot.widgets.kit.Feedback.Sound.KEY_METAL),
    POCKET("pocket", "Pocket", 0.86f, true, rocks.ndot.widgets.kit.Feedback.Sound.KEY_RUBBER);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: MODULE

        /** Room above and below the body for the Slab's crown and the Module's orange base. */
        const val PAD_TOP = 0.075f
        const val PAD_BOTTOM = 0.07f
    }
}

/** Everything a device face shows. Built by the screen from the engine's snapshot. */
class DeviceState(
    val time: String,
    val onBreak: Boolean,
    val running: Boolean,
    val idle: Boolean,
    val progress: Float,
    val session: Int,
    val goal: Int,
    val totalLeft: String,
    val wheel: Float,
    val seconds: Float,
    val pressed: Int,
    val soundOn: Boolean,
    val done: Boolean,
)

/**
 * Draws the devices. A straight port of the canvas prototype the designs were worked out in, so
 * the numbers here are the ones that were looked at.
 */
object DeviceArt {

    const val WHEEL = 100
    const val VOLUME = 101

    private class Key(val x: Float, val kind: String, val label: String = "", val accent: Boolean = false)

    private object Module {
        val screen = floatArrayOf(0.045f, 0.05f, 0.70f, 0.29f)
        val wheelWell = floatArrayOf(0.765f, 0.05f, 0.19f, 0.29f)
        val tray = floatArrayOf(0.045f, 0.375f, 0.91f, 0.215f)
        val keys = listOf(Key(0.060f, "stop", accent = true), Key(0.282f, "play"), Key(0.504f, "cycle"), Key(0.726f, "clock"))
        const val KEY_Y = 0.39f
        const val KEY_W = 0.208f
        const val KEY_H = 0.185f
    }

    private object Slab {
        val crown = floatArrayOf(0.80f, -0.055f, 0.11f, 0.075f)
        val screen = floatArrayOf(0.05f, 0.05f, 0.90f, 0.285f)
        val keys = listOf(Key(0.05f, "play", "Play", true), Key(0.285f, "dots", "Cycles"), Key(0.52f, "bolt", "Work"), Key(0.755f, "sprout", "Break"))
        const val KEY_Y = 0.375f
        const val KEY_W = 0.195f
        const val KEY_H = 0.20f
    }

    private object Pocket {
        val screen = floatArrayOf(0.05f, 0.05f, 0.90f, 0.47f)
        val keys = listOf(Key(0.05f, "play", "START", true), Key(0.515f, "reset", "RESET"))
        const val KEY_Y = 0.555f
        const val KEY_W = 0.435f
        const val KEY_H = 0.17f
        val vol = floatArrayOf(0.05f, 0.765f, 0.085f, 0.065f)
    }

    // ---- hit testing, in device units (x/U, y/U from the body's top-left) ----

    fun hit(device: Device, ux: Float, uy: Float): Int {
        fun inRect(r: FloatArray, pad: Float = 0.01f) = ux in (r[0] - pad)..(r[0] + r[2] + pad) && uy in (r[1] - pad)..(r[1] + r[3] + pad)
        fun inKey(x: Float, y: Float, w: Float, h: Float) = ux in (x - 0.008f)..(x + w + 0.008f) && uy in (y - 0.01f)..(y + h + 0.01f)
        when (device) {
            Device.MODULE -> {
                if (inRect(Module.wheelWell)) return WHEEL
                Module.keys.forEachIndexed { i, k -> if (inKey(k.x, Module.KEY_Y, Module.KEY_W, Module.KEY_H)) return i }
            }
            Device.SLAB -> {
                if (inRect(Slab.crown, 0.04f)) return WHEEL
                Slab.keys.forEachIndexed { i, k -> if (inKey(k.x, Slab.KEY_Y, Slab.KEY_W, Slab.KEY_H)) return i }
            }
            Device.POCKET -> {
                if (inRect(Pocket.vol, 0.02f)) return VOLUME
                Pocket.keys.forEachIndexed { i, k -> if (inKey(k.x, Pocket.KEY_Y, Pocket.KEY_W, Pocket.KEY_H)) return i }
            }
        }
        return -1
    }

    // ---- drawing ----

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelOn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelDim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    fun draw(c: Canvas, device: Device, x0: Float, y0: Float, u: Float, st: DeviceState) {
        when (device) {
            Device.MODULE -> drawModule(c, x0, y0, u, st)
            Device.SLAB -> drawSlab(c, x0, y0, u, st)
            Device.POCKET -> drawPocket(c, x0, y0, u, st)
        }
    }

    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) {
        rect.set(x, y, x + w, y + h)
        val rad = min(r, min(w / 2f, h / 2f))
        c.drawRoundRect(rect, rad, rad, p)
    }

    private fun vGrad(y0: Float, y1: Float, vararg stops: Pair<Float, Int>): Shader =
        LinearGradient(0f, y0, 0f, y1, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)

    private fun hGrad(x0: Float, x1: Float, vararg stops: Pair<Float, Int>): Shader =
        LinearGradient(x0, 0f, x1, 0f, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)

    private fun solid(color: Int): Paint = fill.apply { shader = null; this.color = color; clearShadowLayer() }
    private fun shaded(s: Shader): Paint = fill.apply { shader = s; color = Color.BLACK; clearShadowLayer() }

    private fun shadowed(color: Int, blur: Float, dy: Float, shadow: Int): Paint = fill.apply {
        shader = null
        this.color = color
        setShadowLayer(blur, 0f, dy, shadow)
    }

    private fun pixelTime(c: Canvas, time: String, x: Float, y: Float, cell: Float, on: Int, dim: Int?) {
        pixelOn.color = on
        val dimPaint = dim?.let { pixelDim.apply { color = it } }
        PixelFont.draw(c, time, x, y, cell, pixelOn, dimPaint)
    }

    // =========================== MODULE ===========================

    private fun drawModule(c: Canvas, x0: Float, y0: Float, u: Float, st: DeviceState) {
        val h = u * Device.MODULE.aspect
        fun X(f: Float) = x0 + f * u
        fun Y(f: Float) = y0 + f * u

        // The orange underside peeking out below the shell.
        rr(c, x0 + u * 0.01f, y0 + u * 0.03f, u * 0.98f, h, u * 0.06f, shadowed(0xFFE8741C.toInt(), u * 0.07f, u * 0.045f, 0x99000000.toInt()))
        rr(c, x0 + u * 0.01f, y0 + u * 0.03f, u * 0.98f, h, u * 0.06f,
            shaded(vGrad(y0 + h - u * 0.02f, y0 + h + u * 0.03f, 0f to 0xFFF28A2E.toInt(), 1f to 0xFFB8560F.toInt())))
        // Aluminium shell.
        rr(c, x0, y0, u, h - u * 0.012f, u * 0.06f,
            shaded(vGrad(y0, y0 + h, 0f to 0xFFECECEA.toInt(), 0.5f to 0xFFD8D8D5.toInt(), 1f to 0xFFBDBDB9.toInt())))
        stroke.shader = null; stroke.color = 0xB3FFFFFF.toInt(); stroke.strokeWidth = u * 0.004f
        rr(c, x0 + u * 0.004f, y0 + u * 0.004f, u - u * 0.008f, h - u * 0.02f, u * 0.056f, stroke)

        // Screen bezel and LCD.
        val s = Module.screen
        rr(c, X(s[0]) - u * 0.006f, Y(s[1]) - u * 0.006f, s[2] * u + u * 0.012f, s[3] * u + u * 0.012f, u * 0.03f, solid(0xFF9A9A98.toInt()))
        rr(c, X(s[0]), Y(s[1]), s[2] * u, s[3] * u, u * 0.026f, solid(0xFF0D0D0D.toInt()))
        val cell = min(u * 0.0165f, (0.44f * u) / PixelFont.width(st.time))
        pixelTime(c, st.time, X(s[0] + 0.035f), Y(s[1] + 0.07f), cell, 0xFFF4F4F0.toInt(), 0x0AFFFFFF)

        // Work / Break tags.
        val tagX = X(s[0] + 0.49f)
        val tagW = u * 0.165f
        val tagH = u * 0.052f
        text.typeface = serif; text.textSize = u * 0.032f; text.textAlign = Paint.Align.CENTER
        listOf("Work" to !st.onBreak, "Break" to st.onBreak).forEachIndexed { i, (label, on) ->
            val ty = Y(s[1] + 0.055f + i * 0.075f)
            val col = if (on && !st.idle) 0xFFF5A623.toInt() else if (on) 0xFFB88A3A.toInt() else 0xFF5A5A5A.toInt()
            stroke.color = col; stroke.strokeWidth = u * 0.004f
            rr(c, tagX, ty, tagW, tagH, u * 0.008f, stroke)
            text.color = col
            c.drawText(label, tagX + tagW / 2f, ty + tagH / 2f - (text.ascent() + text.descent()) / 2f, text)
        }
        // Progress dashes.
        val segs = 14
        val py = Y(s[1] + s[3] - 0.055f)
        val pw = (s[2] - 0.07f) * u
        for (i in 0 until segs) {
            val w = (if (i % 3 == 1) 0.5f else 1f) * pw / segs * 0.8f
            val px = X(s[0] + 0.035f) + i * pw / segs
            c.drawRect(px, py, px + w, py + u * 0.012f, solid(if (i.toFloat() / segs < st.progress) 0xFFF4F4F0.toInt() else 0xFF333333.toInt()))
        }

        // Wheel well and the ribbed orange wheel.
        val ww = Module.wheelWell
        rr(c, X(ww[0]), Y(ww[1]), ww[2] * u, ww[3] * u, u * 0.028f, solid(0xFF1A1A1A.toInt()))
        val wX = X(ww[0] + 0.03f)
        val wY = Y(ww[1] + 0.035f)
        val wW = (ww[2] - 0.06f) * u
        val wH = (ww[3] - 0.07f) * u
        rr(c, wX, wY, wW, wH, u * 0.018f, shaded(vGrad(wY, wY + wH,
            0f to 0xFFB75A12.toInt(), 0.25f to 0xFFF39A3C.toInt(), 0.55f to 0xFFF7A64A.toInt(), 1f to 0xFFA54E0C.toInt())))
        c.save()
        rect.set(wX, wY, wX + wW, wY + wH)
        c.clipRect(rect)
        val ribs = 16
        val off = ((st.wheel % 1f) + 1f) % 1f
        for (i in -1..ribs) {
            val v = (i + off) / ribs
            val yy = wY + (0.5f - cos(v * PI).toFloat() / 2f) * wH
            stroke.color = 0x8C783205.toInt(); stroke.strokeWidth = u * 0.004f
            c.drawLine(wX + wW * 0.12f, yy, wX + wW * 0.88f, yy, stroke)
            stroke.color = 0x59FFDCAA
            c.drawLine(wX + wW * 0.12f, yy + u * 0.004f, wX + wW * 0.88f, yy + u * 0.004f, stroke)
        }
        c.restore()

        // Key tray and keys.
        val t = Module.tray
        rr(c, X(t[0]), Y(t[1]), t[2] * u, t[3] * u, u * 0.03f, solid(0xFF2A2A2A.toInt()))
        rr(c, X(t[0]), Y(t[1]), t[2] * u, u * 0.012f, u * 0.03f, solid(0x59000000))
        Module.keys.forEachIndexed { i, k ->
            moduleKey(c, X(k.x), Y(Module.KEY_Y), Module.KEY_W * u, Module.KEY_H * u, u, k, st.pressed == i, i == 1 && st.running)
        }
    }

    private fun moduleKey(c: Canvas, x: Float, y: Float, w: Float, h: Float, u: Float, k: Key, pressed: Boolean, alt: Boolean) {
        val d = if (pressed) u * 0.007f else 0f
        val orange = k.accent
        rr(c, x, y + u * 0.012f, w, h - u * 0.004f, u * 0.022f, solid(0x73000000))
        rr(c, x, y + d + u * 0.006f, w, h - u * 0.006f, u * 0.022f, solid(if (orange) 0xFFB8560F.toInt() else 0xFFB9B9B5.toInt()))
        rr(c, x, y + d, w, h - u * 0.012f, u * 0.022f, shaded(vGrad(y + d, y + d + h,
            0f to (if (orange) 0xFFF7A045.toInt() else 0xFFFBFBF9.toInt()),
            1f to (if (orange) 0xFFEE8A2A.toInt() else 0xFFE9E9E6.toInt()))))
        val cx = x + w / 2f
        val cy = y + d + (h - u * 0.012f) / 2f
        val r = min(w, h) * 0.36f
        c.drawCircle(cx, cy, r, shadowed(if (orange) 0xFFF39433.toInt() else 0xFFF6F6F3.toInt(), u * 0.02f, if (pressed) u * 0.002f else u * 0.008f, 0x40000000))
        fill.clearShadowLayer()
        c.drawCircle(cx, cy, r, shaded(RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.1f,
            if (orange) 0xFFFFC07A.toInt() else 0xFFFFFFFF.toInt(),
            if (orange) 0xFFE57E1E.toInt() else 0xFFE2E2DE.toInt(), Shader.TileMode.CLAMP)))
        icon(c, if (alt) "pause" else k.kind, cx, cy, r * 0.62f, 0xFF1A1A1A.toInt())
    }

    // =========================== SLAB ===========================

    private fun drawSlab(c: Canvas, x0: Float, y0: Float, u: Float, st: DeviceState) {
        val h = u * Device.SLAB.aspect
        fun X(f: Float) = x0 + f * u
        fun Y(f: Float) = y0 + f * u

        // The knurled crown, behind the body.
        val cr = Slab.crown
        val crownTurn = ((st.wheel % 1f) + 1f) % 1f
        rr(c, X(cr[0]), Y(cr[1]), cr[2] * u, cr[3] * u + u * 0.02f, u * 0.012f, shaded(hGrad(X(cr[0]), X(cr[0] + cr[2]),
            0f to 0xFF8D8D8D.toInt(), 0.35f to 0xFFF2F2F2.toInt(), 0.6f to 0xFFC9C9C9.toInt(), 1f to 0xFF6D6D6D.toInt())))
        stroke.color = 0x8C3C3C3C.toInt(); stroke.strokeWidth = u * 0.003f
        for (i in 0 until 15) {
            // The flutes slide round as the crown turns: spacing follows the cylinder's curve.
            val v = (i + crownTurn) / 14f
            if (v <= 0f || v >= 1f) continue
            val xx = X(cr[0]) + cr[2] * u * (0.5f - cos(v * PI).toFloat() / 2f)
            c.drawLine(xx, Y(cr[1]) + u * 0.006f, xx, Y(cr[1] + cr[3]), stroke)
        }

        // Body.
        rr(c, x0, y0 + u * 0.012f, u, h, u * 0.05f, shadowed(0xFF8F8F8F.toInt(), u * 0.08f, u * 0.045f, 0xA6000000.toInt()))
        fill.clearShadowLayer()
        rr(c, x0, y0, u, h, u * 0.05f, shaded(vGrad(y0, y0 + h, 0f to 0xFFF0F0EF.toInt(), 0.45f to 0xFFD6D6D4.toInt(), 1f to 0xFFAEAEAC.toInt())))
        // Brushing: fine horizontal lines, fixed so they don't shimmer between frames.
        c.save()
        rect.set(x0, y0, x0 + u, y0 + h)
        c.clipRect(rect)
        val rnd = Random(3)
        stroke.strokeWidth = 0.6f
        repeat(150) {
            val light = rnd.nextFloat() < 0.5f
            val alpha = (255 * (0.05f + rnd.nextFloat() * 0.08f)).toInt()
            stroke.color = if (light) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 90, 90, 90)
            val yy = y0 + rnd.nextFloat() * h
            c.drawLine(x0, yy, x0 + u, yy, stroke)
        }
        c.restore()
        stroke.color = 0xCCFFFFFF.toInt(); stroke.strokeWidth = u * 0.004f
        rr(c, x0 + u * 0.003f, y0 + u * 0.003f, u - u * 0.006f, h - u * 0.006f, u * 0.048f, stroke)

        // Screen.
        val s = Slab.screen
        rr(c, X(s[0]), Y(s[1]), s[2] * u, s[3] * u, u * 0.035f, solid(0xFF050505.toInt()))
        rr(c, X(s[0]), Y(s[1]), s[2] * u, s[3] * u, u * 0.035f, shaded(vGrad(Y(s[1]), Y(s[1] + s[3]), 0f to 0x12FFFFFF, 0.4f to 0x00FFFFFF)))
        val cell = min(u * 0.0215f, (0.50f * u) / PixelFont.width(st.time))
        pixelTime(c, st.time, X(s[0] + 0.035f), Y(s[1] + 0.045f), cell, 0xFFF5F5F5.toInt(), null)

        // Phase pill and segmented progress bar.
        val barY = Y(s[1] + 0.215f)
        rr(c, X(s[0] + 0.035f), barY, u * 0.12f, u * 0.04f, u * 0.01f, solid(0xFFE8453C.toInt()))
        text.typeface = Fonts.mediumTypeface; text.textSize = u * 0.022f; text.textAlign = Paint.Align.CENTER; text.color = Color.WHITE
        c.drawText(if (st.done) "Done" else if (st.onBreak) "Break" else "Work", X(s[0] + 0.095f), barY + u * 0.02f - (text.ascent() + text.descent()) / 2f, text)
        val segW = floatArrayOf(0.07f, 0.03f, 0.10f, 0.03f, 0.12f, 0.05f, 0.03f)
        var bx = X(s[0] + 0.165f)
        segW.forEachIndexed { i, w ->
            rr(c, bx, barY, w * u, u * 0.04f, u * 0.01f, solid(if (i.toFloat() / segW.size < st.progress) 0xFFE8453C.toInt() else 0xFF3A3A3A.toInt()))
            bx += (w + 0.012f) * u
        }

        // Info lines.
        text.textAlign = Paint.Align.RIGHT; text.color = 0xFFF0F0F0.toInt(); text.textSize = u * 0.026f; text.typeface = Fonts.mediumTypeface
        val goal = if (st.goal > 0) st.goal.toString() else "∞"
        val breaks = if (st.goal > 0) (st.goal - 1).coerceAtLeast(0).toString() else "∞"
        val breakNo = if (st.onBreak) st.session else (st.session - 1).coerceAtLeast(0)
        listOf("${st.session}/$goal Work cycle", "$breakNo/$breaks Break cycle", "${st.totalLeft} Total left").forEachIndexed { i, line ->
            c.drawText(line, X(s[0] + s[2] - 0.035f), Y(s[1] + 0.06f + i * 0.045f), text)
        }

        Slab.keys.forEachIndexed { i, k ->
            slabKey(c, X(k.x), Y(Slab.KEY_Y), Slab.KEY_W * u, Slab.KEY_H * u, u, k, st.pressed == i, i == 0 && st.running)
        }
    }

    private fun slabKey(c: Canvas, x: Float, y: Float, w: Float, h: Float, u: Float, k: Key, pressed: Boolean, alt: Boolean) {
        val d = if (pressed) u * 0.008f else 0f
        rr(c, x - u * 0.004f, y - u * 0.004f, w + u * 0.008f, h + u * 0.012f, u * 0.03f, solid(0x59000000))
        rr(c, x, y + d + u * 0.008f, w, h - u * 0.008f, u * 0.026f, solid(if (k.accent) 0xFF8C1712.toInt() else 0xFF9A9A98.toInt()))
        rr(c, x, y + d, w, h - u * 0.014f, u * 0.026f, shaded(vGrad(y, y + h,
            0f to (if (k.accent) 0xFFE0322A.toInt() else 0xFFF7F7F6.toInt()),
            1f to (if (k.accent) 0xFFBF211A.toInt() else 0xFFDCDCDA.toInt()))))
        val ink = if (k.accent) 0xE6FFFFFF.toInt() else 0xFF1C1C1C.toInt()
        icon(c, if (alt) "pause" else k.kind, x + w / 2f, y + d + h * 0.36f, u * 0.05f, ink)
        text.typeface = Fonts.mediumTypeface; text.textSize = u * 0.03f; text.textAlign = Paint.Align.CENTER; text.color = ink
        c.drawText(if (alt) "Pause" else k.label, x + w / 2f, y + d + h * 0.72f - (text.ascent() + text.descent()) / 2f, text)
    }

    // =========================== POCKET ===========================

    /** A sleeping cat, curled up, and the same cat sitting up for the break. '#' is a pixel. */
    private val catAsleep = listOf(
        "...#...#..........",
        "...##.##..........",
        "..######....####..",
        ".########..######.",
        ".#-##-###.########",
        ".########.########",
        "..######.#########",
        "...###############",
        ".################.",
        "..##############..",
    )
    private val catAwake = listOf(
        "...#...#..........",
        "...##.##..........",
        "..######..........",
        ".#o##o###....##...",
        ".########...##....",
        "..######...##.....",
        "...#####..###.....",
        "..#########.......",
        "..##########......",
        "..##.##.###.......",
    )

    private fun drawPocket(c: Canvas, x0: Float, y0: Float, u: Float, st: DeviceState) {
        val h = u * Device.POCKET.aspect
        fun X(f: Float) = x0 + f * u
        fun Y(f: Float) = y0 + f * u

        rr(c, x0, y0, u, h, u * 0.09f, shadowed(0xFFC4C4C4.toInt(), u * 0.05f, u * 0.025f, 0x47000000))
        fill.clearShadowLayer()
        rr(c, x0, y0, u, h, u * 0.09f, shaded(vGrad(y0, y0 + h, 0f to 0xFFD2D2D2.toInt(), 1f to 0xFFB9B9B9.toInt())))
        stroke.color = 0x8CFFFFFF.toInt(); stroke.strokeWidth = u * 0.005f
        rr(c, x0 + u * 0.012f, y0 + u * 0.012f, u - u * 0.024f, h - u * 0.024f, u * 0.08f, stroke)

        // Screen: dark frame, green LCD with faint scanlines.
        val s = Pocket.screen
        rr(c, X(s[0]), Y(s[1]), s[2] * u, s[3] * u, u * 0.05f, solid(0xFF161616.toInt()))
        val lx = X(s[0] + 0.018f)
        val ly = Y(s[1] + 0.018f)
        val lw = (s[2] - 0.036f) * u
        val lh = (s[3] - 0.036f) * u
        rr(c, lx, ly, lw, lh, u * 0.035f, shaded(vGrad(ly, ly + lh, 0f to 0xFF9AAB83.toInt(), 1f to 0xFF8C9D75.toInt())))
        c.save()
        rect.set(lx, ly, lx + lw, ly + lh)
        c.clipRect(rect)
        val scan = solid(0x09000000)
        var yy = ly
        while (yy < ly + lh) { c.drawRect(lx, yy, lx + lw, yy + u * 0.002f, scan); yy += u * 0.0045f }
        c.restore()

        val ink = 0xFF2C3A24.toInt()
        text.color = ink; text.textAlign = Paint.Align.CENTER
        text.typeface = Fonts.mediumTypeface; text.textSize = u * 0.034f
        val header = when {
            st.done -> "ALL DONE"
            st.onBreak -> "BREAK"
            else -> "SESSION ${st.session}"
        }
        c.drawText(header, lx + lw / 2f, ly + lh * 0.12f - (text.ascent() + text.descent()) / 2f, text)
        text.typeface = Fonts.lightTypeface; text.textSize = if (st.time.length > 5) u * 0.12f else u * 0.15f
        c.drawText(st.time, lx + lw / 2f, ly + lh * 0.40f - (text.ascent() + text.descent()) / 2f, text)

        // The pet. Asleep while you focus, up and about on the break.
        val sprite = if (st.onBreak) catAwake else catAsleep
        val px = u * 0.0125f
        val breathe = if (st.running && !st.onBreak) (sin(st.seconds * 2.2f) * 0.5f + 0.5f) * px * 0.35f else 0f
        val spw = sprite[0].length * px
        val sx0 = lx + lw / 2f - spw / 2f
        val sy0 = ly + lh * 0.62f
        val pixel = solid(ink)
        sprite.forEachIndexed { r, row ->
            row.forEachIndexed { col, ch ->
                if (ch == '#') {
                    // Only the back rises with each breath, not the head.
                    val lift = if (col > 8 && r >= 2) breathe else 0f
                    c.drawRect(sx0 + col * px, sy0 + r * px - lift, sx0 + col * px + px * 0.92f, sy0 + r * px + px * 0.92f - lift, pixel)
                }
            }
        }
        if (st.running && !st.onBreak) {
            // Zs drifting up and fading, one after another.
            text.typeface = Fonts.mediumTypeface; text.textAlign = Paint.Align.LEFT
            for (i in 0 until 3) {
                val phase = ((st.seconds * 0.45f + i / 3f) % 1f)
                text.textSize = u * (0.022f + phase * 0.018f)
                text.color = Color.argb((255 * (1f - phase)).toInt().coerceIn(0, 255), 0x2C, 0x3A, 0x24)
                c.drawText("z", sx0 - px * 1.5f + phase * px * 3f, sy0 - phase * px * 6f, text)
            }
        }

        Pocket.keys.forEachIndexed { i, k ->
            pocketKey(c, X(k.x), Y(Pocket.KEY_Y), Pocket.KEY_W * u, Pocket.KEY_H * u, u, k, st.pressed == i, i == 0 && st.running)
        }

        // Volume key and the speaker grille, whose holes light up in a travelling wave while it runs.
        val v = Pocket.vol
        rr(c, X(v[0]), Y(v[1]), v[2] * u, v[3] * u, u * 0.018f, solid(if (st.pressed == VOLUME) 0xFF1A1A1A.toInt() else 0xFF2B2B2B.toInt()))
        icon(c, if (st.soundOn) "speaker" else "mute", X(v[0] + v[2] / 2f), Y(v[1] + v[3] / 2f), u * 0.045f, 0xFFE8E8E8.toInt())
        val gx0 = X(0.175f)
        val gx1 = X(0.95f)
        val gy0 = Y(0.765f)
        val on = solid(0xFFF2F2F2.toInt())
        val off = Paint(fill).apply { color = 0xFF3A3A3A.toInt() }
        for (r in 0 until 5) for (col in 0 until 30) {
            val x = gx0 + (gx1 - gx0) * col / 29f
            val y = gy0 + r * u * 0.016f
            val wave = if (st.running) (sin(col * 0.45f - st.seconds * 5f + r * 0.6f) + 1f) / 2f else 0f
            c.drawCircle(x, y, u * 0.0048f, if (wave > 0.72f) on else off)
        }
    }

    private fun pocketKey(c: Canvas, x: Float, y: Float, w: Float, h: Float, u: Float, k: Key, pressed: Boolean, alt: Boolean) {
        val d = if (pressed) u * 0.007f else 0f
        rr(c, x - u * 0.005f, y - u * 0.005f, w + u * 0.01f, h + u * 0.014f, u * 0.03f, solid(0x40000000))
        rr(c, x, y + d + u * 0.01f, w, h - u * 0.01f, u * 0.026f, solid(if (k.accent) 0xFF9B1410.toInt() else 0xFF1C1C1C.toInt()))
        rr(c, x, y + d, w, h - u * 0.016f, u * 0.026f, shaded(vGrad(y, y + h,
            0f to (if (k.accent) 0xFFE3261D.toInt() else 0xFF4A4A4A.toInt()),
            1f to (if (k.accent) 0xFFC8190F.toInt() else 0xFF383838.toInt()))))
        val ink = if (k.accent) 0xF2FFFFFF.toInt() else 0xFFE6E6E6.toInt()
        text.typeface = Fonts.mediumTypeface; text.textSize = u * 0.033f; text.textAlign = Paint.Align.LEFT; text.color = ink
        c.drawText(if (alt) "PAUSE" else k.label, x + u * 0.025f, y + d + u * 0.02f - text.ascent(), text)
        icon(c, if (alt) "pause" else k.kind, x + w - u * 0.05f, y + d + h - u * 0.058f, u * 0.045f, ink)
    }

    // =========================== icons ===========================

    private fun icon(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, color: Int) {
        val f = solid(color)
        stroke.shader = null; stroke.color = color; stroke.strokeWidth = s * 0.13f
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        path.reset()
        when (kind) {
            "play" -> {
                path.moveTo(cx - s * 0.30f, cy - s * 0.38f); path.lineTo(cx + s * 0.40f, cy); path.lineTo(cx - s * 0.30f, cy + s * 0.38f); path.close()
                c.drawPath(path, f)
            }
            "pause" -> {
                c.drawRect(cx - s * 0.32f, cy - s * 0.36f, cx - s * 0.10f, cy + s * 0.36f, f)
                c.drawRect(cx + s * 0.10f, cy - s * 0.36f, cx + s * 0.32f, cy + s * 0.36f, f)
            }
            "stop" -> {
                c.save(); c.rotate(45f, cx, cy)
                c.drawRect(cx - s * 0.26f, cy - s * 0.26f, cx + s * 0.26f, cy + s * 0.26f, f)
                c.restore()
            }
            "cycle" -> {
                rect.set(cx - s * 0.34f, cy - s * 0.34f, cx + s * 0.34f, cy + s * 0.34f)
                c.drawArc(rect, -27f, 162f, false, stroke)
                c.drawArc(rect, 153f, 162f, false, stroke)
                path.moveTo(cx + s * 0.40f, cy - s * 0.24f); path.lineTo(cx + s * 0.30f, cy - s * 0.02f); path.lineTo(cx + s * 0.14f, cy - s * 0.2f)
                c.drawPath(path, stroke)
            }
            "clock" -> {
                c.drawCircle(cx, cy, s * 0.38f, f)
                stroke.color = Color.WHITE; stroke.strokeWidth = s * 0.09f
                path.moveTo(cx, cy - s * 0.22f); path.lineTo(cx, cy); path.lineTo(cx + s * 0.16f, cy + s * 0.08f)
                c.drawPath(path, stroke)
            }
            "reset" -> {
                rect.set(cx - s * 0.34f, cy - s * 0.34f, cx + s * 0.34f, cy + s * 0.34f)
                c.drawArc(rect, -63f, 306f, false, stroke)
                path.moveTo(cx + s * 0.12f, cy - s * 0.46f); path.lineTo(cx + s * 0.24f, cy - s * 0.28f); path.lineTo(cx + s * 0.02f, cy - s * 0.22f)
                c.drawPath(path, stroke)
            }
            "dots" -> for (i in 0 until 8) {
                val a = i / 8f * 2f * PI.toFloat()
                f.alpha = (255 * (0.35f + 0.65f * i / 8f)).toInt()
                c.drawCircle(cx + cos(a) * s * 0.3f, cy + sin(a) * s * 0.3f, s * 0.06f, f)
            }
            "sprout" -> {
                c.drawLine(cx, cy + s * 0.4f, cx, cy - s * 0.05f, stroke)
                c.save(); c.rotate(-28f, cx - s * 0.18f, cy - s * 0.12f)
                rect.set(cx - s * 0.38f, cy - s * 0.22f, cx + s * 0.02f, cy - s * 0.02f); c.drawOval(rect, f)
                c.restore()
                c.save(); c.rotate(28f, cx + s * 0.18f, cy - s * 0.2f)
                rect.set(cx - s * 0.02f, cy - s * 0.30f, cx + s * 0.38f, cy - s * 0.10f); c.drawOval(rect, f)
                c.restore()
            }
            "bolt" -> {
                path.moveTo(cx + s * 0.08f, cy - s * 0.45f); path.lineTo(cx - s * 0.25f, cy + s * 0.05f); path.lineTo(cx, cy + s * 0.05f)
                path.lineTo(cx - s * 0.08f, cy + s * 0.45f); path.lineTo(cx + s * 0.25f, cy - s * 0.05f); path.lineTo(cx, cy - s * 0.05f); path.close()
                c.drawPath(path, f)
            }
            "speaker", "mute" -> {
                path.moveTo(cx - s * 0.35f, cy - s * 0.12f); path.lineTo(cx - s * 0.15f, cy - s * 0.12f); path.lineTo(cx + s * 0.05f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.05f, cy + s * 0.32f); path.lineTo(cx - s * 0.15f, cy + s * 0.12f); path.lineTo(cx - s * 0.35f, cy + s * 0.12f); path.close()
                c.drawPath(path, f)
                if (kind == "speaker") {
                    rect.set(cx - s * 0.17f, cy - s * 0.22f, cx + s * 0.27f, cy + s * 0.22f); c.drawArc(rect, -46f, 92f, false, stroke)
                    rect.set(cx - s * 0.33f, cy - s * 0.38f, cx + s * 0.43f, cy + s * 0.38f); c.drawArc(rect, -46f, 92f, false, stroke)
                } else {
                    c.drawLine(cx + s * 0.18f, cy - s * 0.16f, cx + s * 0.44f, cy + s * 0.16f, stroke)
                    c.drawLine(cx + s * 0.44f, cy - s * 0.16f, cx + s * 0.18f, cy + s * 0.16f, stroke)
                }
            }
        }
        f.alpha = 255
    }
}
