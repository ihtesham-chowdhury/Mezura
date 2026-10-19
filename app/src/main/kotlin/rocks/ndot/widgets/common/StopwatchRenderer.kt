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
 * White dial: Swiss classic, preserved pixel-identically.
 * Black dial ("Obsidian"): High-contrast nocturnal instrument with sunburst dial, applied lume
 * markers (#CFF7E0), signal-orange seconds hand (#FF5B2E), recessed guilloché sub-dial, and ceramic bezel.
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

    /** The width:height the watch fills best: a round case plus the crown above it. */
    const val ASPECT = 0.83f

    class Geometry(val cx: Float, val cy: Float, val r: Float, val crownZone: Float) {
        fun crownHit(x: Float, y: Float, minHalf: Float): Boolean {
            val half = maxOf(r * 0.26f, minHalf)
            return x in (cx - half)..(cx + half) && y in 0f..(crownZone + r * 0.08f)
        }

        val rightPusherTipX: Float get() = cx + cos(RIGHT_PUSHER_ANGLE).toFloat() * r * 1.18f
        val rightPusherTipY: Float get() = cy + sin(RIGHT_PUSHER_ANGLE).toFloat() * r * 1.18f

        val leftPusherTipX: Float get() = cx + cos(LEFT_PUSHER_ANGLE).toFloat() * r * 1.18f
        val leftPusherTipY: Float get() = cy + sin(LEFT_PUSHER_ANGLE).toFloat() * r * 1.18f

        // Backward compatibility accessors
        val pusherTipX: Float get() = rightPusherTipX
        val pusherTipY: Float get() = rightPusherTipY

        fun pusherHit(x: Float, y: Float, minRadius: Float): Boolean {
            val reach = maxOf(r * 0.24f, minRadius)
            val reachSq = reach * reach
            val dxR = x - rightPusherTipX
            val dyR = y - rightPusherTipY
            if (dxR * dxR + dyR * dyR <= reachSq) return true
            val dxL = x - leftPusherTipX
            val dyL = y - leftPusherTipY
            return dxL * dxL + dyL * dyL <= reachSq
        }
    }

    private val RIGHT_PUSHER_ANGLE = (-54.0) * PI / 180.0
    private val LEFT_PUSHER_ANGLE = (-126.0) * PI / 180.0
    private val PUSHER_ANGLE = RIGHT_PUSHER_ANGLE

    fun geometry(widthPx: Int, heightPx: Int): Geometry {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val crownZone = h * 0.17f
        val caseD = minOf(w, h - crownZone) * 0.97f
        val r = caseD / 2f
        return Geometry(w / 2f, crownZone + r, r, crownZone)
    }

    fun draw(c: Canvas, widthPx: Int, heightPx: Int, face: Face, dial: Dial, accent: Int) {
        val g = geometry(widthPx, heightPx)
        drawControls(c, g, dial, 0f, 0f)
        drawBody(c, g, dial)
        drawHands(c, g, face, dial, accent)
    }

    fun drawControls(c: Canvas, g: Geometry, dial: Dial, crownPress: Float, pusherPress: Float) {
        // Symmetrical knurled side pushers at 10:30 and 1:30
        drawKnurledPusher(c, g.cx, g.cy, g.r, LEFT_PUSHER_ANGLE, dial, pusherPress)
        drawKnurledPusher(c, g.cx, g.cy, g.r, RIGHT_PUSHER_ANGLE, dial, pusherPress)

        // Mechanical top winding crown & looped bow ring
        drawBowAndCrown(c, g.cx, g.crownZone, g.r, dial, crownPress)
    }

    fun drawBody(c: Canvas, g: Geometry, dial: Dial) {
        drawCase(c, g.cx, g.cy, g.r, dial)
        drawDialFace(c, g.cx, g.cy, g.r, dial)
        drawSecondsTrack(c, g.cx, g.cy, g.r, dial)
        drawSubDialMarks(c, g.cx, g.cy, g.r, dial)
        drawSignature(c, g.cx, g.cy, g.r, dial)
    }

    fun drawHands(c: Canvas, g: Geometry, face: Face, dial: Dial, accent: Int) {
        drawSubDialHand(c, g.cx, g.cy, g.r, face, dial)
        drawSplitHand(c, g.cx, g.cy, g.r, face, accent)
        drawSecondHand(c, g.cx, g.cy, g.r, face, dial)
        drawCrystal(c, g.cx, g.cy, g.r)
    }

    // -- case & controls ----------------------------------------------------------------

    private fun drawKnurledPusher(
        c: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        angleRad: Double,
        dial: Dial,
        press: Float
    ) {
        c.save()
        c.translate(cx, cy)
        c.rotate((angleRad * 180.0 / PI).toFloat())

        val isBlack = (dial == Dial.BLACK)
        val metalBase = if (isBlack) 0xFF2D2F34.toInt() else 0xFFB8BAC0.toInt()
        val metalHi = if (isBlack) 0xFF70737C.toInt() else 0xFFF0F2F6.toInt()
        val metalLo = if (isBlack) 0xFF141518.toInt() else 0xFF6C6F77.toInt()

        // 1. Base Mounting Collar (Stepped Bushing on case perimeter)
        val collarX0 = r * 0.97f
        val collarX1 = r * 1.07f
        val collarHalfW = r * 0.088f
        val collarRect = RectF(collarX0, -collarHalfW, collarX1, collarHalfW)
        val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -collarHalfW, 0f, collarHalfW,
                intArrayOf(metalLo, metalHi, metalBase, metalLo),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(collarRect, r * 0.015f, r * 0.015f, collarPaint)

        // Stepped collar ring / lip
        val lipX0 = collarX1 - r * 0.022f
        val lipX1 = collarX1
        val lipHalfW = collarHalfW * 0.85f
        val lipRect = RectF(lipX0, -lipHalfW, lipX1, lipHalfW)
        val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -lipHalfW, 0f, lipHalfW,
                intArrayOf(metalLo, metalHi, metalLo),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(lipRect, r * 0.01f, r * 0.01f, lipPaint)

        // 2. Sliding Plunger (Knurled Cylindrical Cap)
        val inward = press * r * 0.045f
        val capX0 = collarX1 - inward
        val capX1 = r * 1.20f - inward
        val capHalfW = r * 0.10f
        val capRect = RectF(capX0, -capHalfW, capX1 - r * 0.02f, capHalfW)

        // Plunger Barrel Body
        val capBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -capHalfW, 0f, capHalfW,
                intArrayOf(metalLo, metalHi, metalBase, metalLo),
                floatArrayOf(0f, 0.32f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(capRect, r * 0.02f, r * 0.02f, capBodyPaint)

        // Fine Knurling Fluting Lines along the barrel
        val fluteShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isBlack) Color.argb(190, 10, 10, 12) else Color.argb(160, 45, 47, 52)
            strokeWidth = maxOf(1f, r * 0.011f)
        }
        val fluteHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isBlack) Color.argb(160, 190, 195, 205) else Color.argb(220, 255, 255, 255)
            strokeWidth = maxOf(1f, r * 0.009f)
        }

        val fluteStartX = capX0 + r * 0.015f
        val fluteEndX = capX1 - r * 0.025f
        val fluteCount = 9
        for (i in 0 until fluteCount) {
            val fy = -capHalfW * 0.82f + i * (capHalfW * 1.64f / (fluteCount - 1))
            c.drawLine(fluteStartX, fy - 0.7f, fluteEndX, fy - 0.7f, fluteShadow)
            c.drawLine(fluteStartX, fy + 0.7f, fluteEndX, fy + 0.7f, fluteHighlight)
        }

        // Chamfered Button Head End-Cap (Outer tip of pusher)
        val endCapRect = RectF(capX1 - r * 0.025f, -capHalfW * 0.95f, capX1, capHalfW * 0.95f)
        val endCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                capX1, 0f, capHalfW,
                intArrayOf(metalHi, metalBase, metalLo),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(endCapRect, r * 0.02f, r * 0.02f, endCapPaint)

        c.restore()
    }

    private fun drawBowAndCrown(
        c: Canvas,
        cx: Float,
        crownZone: Float,
        r: Float,
        dial: Dial,
        crownPress: Float
    ) {
        val isBlack = (dial == Dial.BLACK)
        val metalBase = if (isBlack) 0xFF2D2F34.toInt() else 0xFFB8BAC0.toInt()
        val metalHi = if (isBlack) 0xFF70737C.toInt() else 0xFFF0F2F6.toInt()
        val metalLo = if (isBlack) 0xFF141518.toInt() else 0xFF6C6F77.toInt()

        // 1. Arched Bow Ring (Looping up over the top of the crown)
        val ringR = r * 0.22f
        val ringCy = crownZone - r * 0.15f
        val strokeW = r * 0.048f

        // Ambient shadow of ring
        val ringShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW * 1.15f
            color = Color.argb(80, 0, 0, 0)
        }
        c.drawCircle(cx, ringCy + r * 0.015f, ringR, ringShadow)

        // Main metallic torus ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            shader = metalSweep(cx, ringCy, ringR, dial)
        }
        c.drawCircle(cx, ringCy, ringR, ringPaint)

        // Specular tubular highlight arc along the outer rim of the ring
        val ringSpecular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, r * 0.009f)
            color = if (isBlack) Color.argb(150, 200, 205, 215) else Color.argb(220, 255, 255, 255)
        }
        c.drawCircle(cx, ringCy, ringR + strokeW * 0.35f, ringSpecular)

        // 2. Neck / Pedestal Collar on 12 o'clock case rim
        val neckW = r * 0.18f
        val neckTop = crownZone - r * 0.05f
        val neckBottom = crownZone + r * 0.03f
        val neckRect = RectF(cx - neckW / 2f, neckTop, cx + neckW / 2f, neckBottom)
        val neckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                neckRect.left, 0f, neckRect.right, 0f,
                intArrayOf(metalLo, metalHi, metalBase, metalLo),
                floatArrayOf(0f, 0.32f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(neckRect, r * 0.02f, r * 0.02f, neckPaint)

        // 3. Winding Crown Body (Depressible knob nestled inside bow)
        val depressY = crownPress * r * 0.05f
        val crownW = r * 0.28f
        val crownTop = crownZone - r * 0.22f + depressY
        val crownBottom = crownZone - r * 0.05f + depressY
        val crownRect = RectF(cx - crownW / 2f, crownTop, cx + crownW / 2f, crownBottom)

        // Crown cylinder body
        val crownBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                crownRect.left, 0f, crownRect.right, 0f,
                intArrayOf(metalLo, metalHi, metalBase, metalLo),
                floatArrayOf(0f, 0.32f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(crownRect, r * 0.025f, r * 0.025f, crownBody)

        // Crown Vertical Flutes / Knurls
        val crownFluteShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isBlack) Color.argb(190, 10, 10, 12) else Color.argb(160, 45, 47, 52)
            strokeWidth = maxOf(1f, r * 0.012f)
        }
        val crownFluteHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isBlack) Color.argb(160, 190, 195, 205) else Color.argb(220, 255, 255, 255)
            strokeWidth = maxOf(1f, r * 0.009f)
        }

        val fluteY0 = crownTop + r * 0.015f
        val fluteY1 = crownBottom - r * 0.015f
        val crownFluteCount = 10
        for (i in 0 until crownFluteCount) {
            val fx = crownRect.left + r * 0.02f + i * ((crownW - r * 0.04f) / (crownFluteCount - 1))
            c.drawLine(fx - 0.7f, fluteY0, fx - 0.7f, fluteY1, crownFluteShadow)
            c.drawLine(fx + 0.7f, fluteY0, fx + 0.7f, fluteY1, crownFluteHi)
        }

        // Crown Top Chamfer / Bevel Cap
        val capH = r * 0.028f
        val capRect = RectF(cx - crownW * 0.44f, crownTop - capH / 2f, cx + crownW * 0.44f, crownTop + capH / 2f)
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                capRect.left, 0f, capRect.right, 0f,
                intArrayOf(metalLo, metalHi, metalLo),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(capRect, capH / 2f, capH / 2f, capPaint)
    }

    private fun drawCase(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(r * 0.09f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(cx, cy + r * 0.05f, r * 0.98f, shadow)

        if (dial == Dial.BLACK) {
            // Obsidian Glossy Ceramic Bezel
            val ceramic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF101114.toInt()
            }
            c.drawCircle(cx, cy, r, ceramic)

            // Steel inner edge (angular gradient: #8E9097 -> #E9EAEE -> #70737A -> #CFD0D5)
            val steelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = SweepGradient(
                    cx, cy,
                    intArrayOf(
                        0xFF8E9097.toInt(), 0xFFE9EAEE.toInt(),
                        0xFF70737A.toInt(), 0xFFCFD0D5.toInt(),
                        0xFF8E9097.toInt()
                    ),
                    null
                )
                style = Paint.Style.STROKE
                strokeWidth = r * 0.024f
            }
            c.drawCircle(cx, cy, r * 0.885f, steelEdge)
        } else {
            val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = metalSweep(cx, cy, r, dial) }
            c.drawCircle(cx, cy, r, bezel)

            val chamfer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = r * 0.03f
                color = Color.argb(90, 0, 0, 0)
            }
            c.drawCircle(cx, cy, r * 0.885f, chamfer)
        }
    }

    private fun drawDialFace(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val faceR = r * 0.87f
        if (dial == Dial.BLACK) {
            // Obsidian Base: radial gradient #0E0F12 (center) -> #050506 (edge)
            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, faceR,
                    intArrayOf(0xFF0E0F12.toInt(), 0xFF050506.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(cx, cy, faceR, basePaint)

            // Sunburst: sweep gradient of 36 narrow alternating wedges at 4% white
            val wedges = 36
            val sunburstColors = IntArray(wedges + 1) { i ->
                if (i % 2 == 0) Color.argb(12, 255, 255, 255) else Color.TRANSPARENT
            }
            val sunburst = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = SweepGradient(cx, cy, sunburstColors, null)
            }
            c.drawCircle(cx, cy, faceR, sunburst)

            // Fine radial brushing texture (concentric hairlines at 3% white)
            val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
                color = Color.argb(8, 255, 255, 255)
            }
            for (step in 1..10) {
                c.drawCircle(cx, cy, faceR * (step / 11f), brushPaint)
            }
        } else {
            val base = 0xFFF4F3EF.toInt()
            val edge = 0xFFD9D7D0.toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - faceR * 0.25f, cy - faceR * 0.3f, faceR * 1.35f,
                    intArrayOf(lighten(base, 0.06f), base, edge),
                    floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(cx, cy, faceR, paint)
        }
    }

    // -- markings -----------------------------------------------------------------------

    private fun drawSecondsTrack(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val faceR = r * 0.87f
        val outer = faceR * 0.96f

        if (dial == Dial.BLACK) {
            // Obsidian Minute Track: hairlines #D6D3CB at 85%
            val fine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(216, 214, 211, 203)
                strokeWidth = maxOf(0.8f, r * 0.006f)
            }
            val second = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD6D3CB.toInt()
                strokeWidth = maxOf(1.2f, r * 0.012f)
            }

            for (i in 0 until 300) {
                val deg = i * 1.2f - 90f
                val rad = deg * PI.toFloat() / 180f
                val isSecond = i % 5 == 0
                val len = if (isSecond) faceR * 0.075f else faceR * 0.040f
                val paint = if (isSecond) second else fine
                val x0 = cx + cos(rad) * outer
                val y0 = cy + sin(rad) * outer
                val x1 = cx + cos(rad) * (outer - len)
                val y1 = cy + sin(rad) * (outer - len)
                c.drawLine(x0, y0, x1, y1, paint)
            }

            // Applied Bar Markers filled with lume #CFF7E0 with soft glow
            val lumeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCFF7E0.toInt()
                style = Paint.Style.FILL
            }
            val lumeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                style = Paint.Style.STROKE
                strokeWidth = maxOf(0.8f, r * 0.008f)
            }

            val barR = outer - faceR * 0.09f
            val barLen = faceR * 0.12f
            val barHalfW = maxOf(1.8f, r * 0.018f)

            for (n in 1..12) {
                val deg = n * 30f - 90f
                val rad = deg * PI.toFloat() / 180f
                val is12 = (n == 12)

                if (is12) {
                    // Double bar at 12 o'clock
                    val sep = barHalfW * 1.8f
                    val px = -sin(rad)
                    val py = cos(rad)

                    listOf(-sep, sep).forEach { s ->
                        c.save()
                        c.translate(cx + px * s, cy + py * s)
                        c.rotate(deg + 90f)
                        val rect = RectF(-barHalfW, -barR, barHalfW, -barR + barLen)
                        c.drawRoundRect(rect, barHalfW * 0.5f, barHalfW * 0.5f, lumeFill)
                        c.drawRoundRect(rect, barHalfW * 0.5f, barHalfW * 0.5f, lumeOutline)
                        c.restore()
                    }
                } else {
                    c.save()
                    c.translate(cx, cy)
                    c.rotate(deg + 90f)
                    val rect = RectF(-barHalfW, -barR, barHalfW, -barR + barLen)
                    c.drawRoundRect(rect, barHalfW * 0.5f, barHalfW * 0.5f, lumeFill)
                    c.drawRoundRect(rect, barHalfW * 0.5f, barHalfW * 0.5f, lumeOutline)
                    c.restore()
                }
            }

            // Numerals every five
            val numeral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFC9C6BE.toInt()
                typeface = condensed
                textSize = faceR * 0.170f
                textAlign = Paint.Align.CENTER
            }
            val numeralR = outer - faceR * 0.230f
            for (n in 1..12) {
                val value = n * 5
                val deg = value * 6f - 90f
                val rad = deg * PI.toFloat() / 180f
                val x = cx + cos(rad) * numeralR
                val y = cy + sin(rad) * numeralR - (numeral.fontMetrics.ascent + numeral.fontMetrics.descent) / 2f
                c.drawText(value.toString(), x, y, numeral)
            }
        } else {
            // Original Swiss White Dial Track (Untouched)
            val ink = inkColor(dial)
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
    }

    private fun drawSubDialMarks(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        val ink = inkColor(dial)
        val subR = r * 0.200f
        val subCy = cy - r * 0.340f
        val detailed = r > 70f

        if (dial == Dial.BLACK) {
            // Recessed Sub-dial with Inner Shadow on Top-Left & Concentric Guilloché
            val subBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A0B0E.toInt()
            }
            c.drawCircle(cx, subCy, subR, subBg)

            // Concentric Guilloché (8 rings at 4% white)
            val guilloche = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.7f
                color = Color.argb(10, 255, 255, 255)
            }
            for (ring in 1..8) {
                c.drawCircle(cx, subCy, subR * (ring / 9f), guilloche)
            }

            // Recess rim
            val recess = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(1f, r * 0.010f)
                color = 0x33FFFFFF.toInt()
            }
            c.drawCircle(cx, subCy, subR, recess)

            // Lume ticks
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCFF7E0.toInt()
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
                    color = 0xFFC9C6BE.toInt()
                    typeface = condensed
                    textSize = subR * 0.36f
                    textAlign = Paint.Align.CENTER
                }
                for (n in 1..6) {
                    val value = n * 5
                    val deg = value * 12f - 90f
                    val rad = deg * PI.toFloat() / 180f
                    val x = cx + cos(rad) * (subR * 0.58f)
                    val y = subCy + sin(rad) * (subR * 0.58f) - (numeral.fontMetrics.ascent + numeral.fontMetrics.descent) / 2f
                    c.drawText(value.toString(), x, y, numeral)
                }

                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(160, 201, 198, 190)
                    typeface = condensed
                    textSize = subR * 0.26f
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("MIN", cx, subCy - subR * 0.42f, label)
            }
        } else {
            // Original White Subdial Marks
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
                    val y = subCy + sin(rad) * (subR * 0.58f) - (numeral.fontMetrics.ascent + numeral.fontMetrics.descent) / 2f
                    c.drawText(value.toString(), x, y, numeral)
                }

                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(160, Color.red(ink), Color.green(ink), Color.blue(ink))
                    typeface = condensed
                    textSize = subR * 0.26f
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("MIN", cx, subCy - subR * 0.42f, label)
            }
        }
    }

    private fun drawSubDialHand(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, dial: Dial) {
        val subR = r * 0.200f
        val subCy = cy - r * 0.340f

        val minutes = (face.elapsedMs / 60_000.0) % 30.0
        val deg = (minutes * 12.0).toFloat() - 90f
        val rad = deg * PI.toFloat() / 180f

        val handColor = if (dial == Dial.BLACK) 0xFFFF5B2E.toInt() else inkColor(dial)
        val hand = handPaint.apply {
            reset()
            isAntiAlias = true
            color = handColor
            strokeWidth = maxOf(1.6f, r * 0.020f)
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(
            cx - cos(rad) * subR * 0.18f, subCy - sin(rad) * subR * 0.18f,
            cx + cos(rad) * subR * 0.74f, subCy + sin(rad) * subR * 0.74f,
            hand
        )
        hand.style = Paint.Style.FILL
        c.drawCircle(cx, subCy, subR * 0.085f, hand)
    }

    private fun drawSignature(c: Canvas, cx: Float, cy: Float, r: Float, dial: Dial) {
        if (r <= 70f) return
        val textColor = if (dial == Dial.BLACK) 0xFFC9C6BE.toInt() else inkColor(dial)
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            typeface = condensed
            textSize = r * 0.115f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.26f
        }
        c.drawText("MEZURA", cx, cy + r * 0.36f, name)

        val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
            typeface = condensed
            textSize = r * 0.075f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        val specText = if (dial == Dial.BLACK) "CHRONOGRAPH" else "1/5 SEC"
        c.drawText(specText, cx, cy + r * 0.475f, spec)
    }

    // -- hands --------------------------------------------------------------------------

    private fun drawSplitHand(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, accent: Int) {
        val split = face.splitMs ?: return
        val deg = ((split % 60_000L) / 60_000.0 * 360.0).toFloat() - 90f
        drawNeedle(c, cx, cy, r, deg, accent, r * 0.016f, 0.80f)
    }

    private fun drawSecondHand(c: Canvas, cx: Float, cy: Float, r: Float, face: Face, dial: Dial) {
        val deg = ((face.elapsedMs % 60_000L) / 60_000.0 * 360.0).toFloat() - 90f

        if (dial == Dial.BLACK) {
            // Signal-orange #FF5B2E seconds hand
            val orangeColor = 0xFFFF5B2E.toInt()

            // Faint motion-blur ghost arc behind hand (10% alpha)
            if (face.running) {
                val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(25, 255, 91, 46)
                }
                c.save()
                c.drawPath(needlePath(cx, cy, r, deg - 4f, r * 0.020f, 0.845f), ghostPaint)
                c.restore()
            }

            drawNeedle(c, cx, cy, r, deg, orangeColor, r * 0.018f, 0.850f)

            // Counterweight lume dot
            val rad = (deg + 180f) * PI.toFloat() / 180f
            val dotCenterR = r * 0.12f
            val dotCenter = PointF2(cx + cos(rad) * dotCenterR, cy + sin(rad) * dotCenterR)
            val lumeDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCFF7E0.toInt()
            }
            c.drawCircle(dotCenter.x, dotCenter.y, r * 0.022f, lumeDot)

            // Center Pinion: Steel ring + black ring + orange dot
            val pinionSteel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCFD0D5.toInt()
            }
            c.drawCircle(cx, cy, r * 0.055f, pinionSteel)

            val pinionBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF141518.toInt()
            }
            c.drawCircle(cx, cy, r * 0.038f, pinionBlack)

            val pinionOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = orangeColor
            }
            c.drawCircle(cx, cy, r * 0.018f, pinionOrange)
        } else {
            val handColor = 0xFF1A1A1C.toInt()
            if (!c.isHardwareAccelerated || android.os.Build.VERSION.SDK_INT >= 28) {
                if (shadowRadius != r) {
                    shadowRadius = r
                    shadowPaint.maskFilter = android.graphics.BlurMaskFilter(r * 0.02f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.save()
                c.translate(r * 0.012f, r * 0.018f)
                c.drawPath(needlePath(cx, cy, r, deg, r * 0.020f, 0.845f), shadowPaint)
                c.restore()
            }

            drawNeedle(c, cx, cy, r, deg, handColor, r * 0.020f, 0.845f)

            val capOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - r * 0.02f, cy - r * 0.02f, r * 0.075f,
                    intArrayOf(lighten(handColor, 0.3f), darken(handColor, 0.3f)),
                    null, Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(cx, cy, r * 0.055f, capOuter)
            val capInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF4F3EF.toInt()
            }
            c.drawCircle(cx, cy, r * 0.020f, capInner)
        }
    }

    private fun drawNeedle(
        c: Canvas, cx: Float, cy: Float, r: Float, deg: Float, color: Int, halfWidth: Float, reach: Float,
    ) {
        needlePaint.color = color
        c.drawPath(needlePath(cx, cy, r, deg, halfWidth, reach), needlePaint)
    }

    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
    private var shadowRadius = -1f
    private val needle = Path()

    private fun needlePath(cx: Float, cy: Float, r: Float, deg: Float, halfWidth: Float, reach: Float): Path {
        val rad = deg * PI.toFloat() / 180f
        val dx = cos(rad)
        val dy = sin(rad)
        val px = -dy
        val py = dx

        val tipR = r * reach
        val tailR = r * 0.17f

        return needle.apply {
            reset()
            moveTo(cx + dx * tipR, cy + dy * tipR)
            lineTo(cx + dx * tipR * 0.22f + px * halfWidth, cy + dy * tipR * 0.22f + py * halfWidth)
            lineTo(cx - dx * tailR + px * halfWidth * 1.7f, cy - dy * tailR + py * halfWidth * 1.7f)
            lineTo(cx - dx * tailR - px * halfWidth * 1.7f, cy - dy * tailR - py * halfWidth * 1.7f)
            lineTo(cx + dx * tipR * 0.22f - px * halfWidth, cy + dy * tipR * 0.22f - py * halfWidth)
            close()
        }
    }

    private fun drawCrystal(c: Canvas, cx: Float, cy: Float, r: Float) {
        val faceR = r * 0.87f
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, faceR, Path.Direction.CW) })
        // Specular glare crescent
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

    private data class PointF2(val x: Float, val y: Float)
}
