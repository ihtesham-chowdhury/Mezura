package rocks.ndot.widgets.instruments.folio

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

object MezuraMasterPlateRenderer {
    @Volatile
    private var cachedBengaliTypeface: Typeface? = null

    private fun resolveTypefaceForText(text: String, requestedTypeface: Typeface): Typeface {
        if (text.any { it in '\u0980'..'\u09FF' }) {
            return cachedBengaliTypeface ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        return requestedTypeface
    }


    private val ASSET_PATHS = mapOf(
        1 to "book_stats/style_1.png",
        2 to "book_stats/style_2.jpg",
        3 to "book_stats/style_3.jpg",
        4 to "book_stats/style_4.jpg",
        5 to "book_stats/style_5.jpg",
        6 to "book_stats/style_6.jpg",
        7 to "book_stats/style_7.jpg",
        8 to "book_stats/style_8.png",
        9 to "book_stats/style_9.jpg"
    )

    fun renderMasterPlate(
        context: Context,
        styleId: Int,
        stats: BookCompletionStats,
        coverBitmap: Bitmap?
    ): Bitmap? {
        if (cachedBengaliTypeface == null) {
            try {
                cachedBengaliTypeface = Typeface.createFromAsset(context.assets, "fonts/noto_sans_bengali.ttf")
            } catch (_: Exception) {}
        }
        val clampedStyle = styleId.coerceIn(1, 9)
        val assetPath = ASSET_PATHS[clampedStyle] ?: return null
        val decoded = try {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                })
            }
        } catch (e: Exception) {
            val altPath = if (assetPath.endsWith(".jpg")) assetPath.replace(".jpg", ".png") else assetPath.replace(".png", ".jpg")
            try {
                context.assets.open(altPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    })
                }
            } catch (_: Exception) {
                null
            }
        } ?: return null

        // Upscale Style 1 (1122x1402) to 1792x2240 so it matches the 2K crispness of all other plates
        val master = if (clampedStyle == 1 && decoded.width < 1600) {
            Bitmap.createScaledBitmap(decoded, 1792, 2240, true).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val working = master.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(working)
        val w = working.width.toFloat()
        val h = working.height.toFloat()

        when (clampedStyle) {
            1 -> compositeStyle1(canvas, working, master, w, h, stats, coverBitmap)
            2 -> compositeStyle2(canvas, working, master, w, h, stats, coverBitmap)
            3 -> compositeStyle3(canvas, working, master, w, h, stats, coverBitmap)
            4 -> compositeStyle4(canvas, working, master, w, h, stats, coverBitmap)
            5 -> compositeStyle5(canvas, working, master, w, h, stats, coverBitmap)
            6 -> compositeStyle6(canvas, working, master, w, h, stats, coverBitmap)
            7 -> compositeStyle7(canvas, working, master, w, h, stats, coverBitmap)
            8 -> compositeStyle8(canvas, working, master, w, h, stats, coverBitmap)
            9 -> compositeStyle9(canvas, working, master, w, h, stats, coverBitmap)
        }

        master.recycle()
        return working
    }

    // =========================================================================
    // NATIVE LEFT-TO-RIGHT STAR SLOT CLONER (ZERO BLURRY EDGES, ZERO MIDDLE BUG)
    // =========================================================================

    /**
     * In all 9 master plates, slots 0, 1, 2, 3 are natively LIT and slot 4 is natively UNLIT.
     * Instead of erasing the row or drawing vector stars:
     * - Slots 0 .. (rating - 1) must be LIT (if slot 4 needs to be lit for rating == 5, clone from slot 2).
     * - Slots rating .. 4 must be UNLIT (if slots 0..3 need to be unlit for rating < 4, clone from slot 4).
     */

    private fun stampNativeMasterStarsLeftToRight(
        c: Canvas,
        master: Bitmap,
        w: Float,
        h: Float,
        rating: Int,
        slotCentersX: FloatArray,
        slotCenterY: Float,
        boxWidthNorm: Float,
        boxHeightNorm: Float
    ) {
        val clamped = rating.coerceIn(0, 5)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val halfW = (boxWidthNorm * w) / 2f
        val halfH = (boxHeightNorm * h) / 2f
        val cy = slotCenterY * h

        // Source rect for a native LIT star (taken from slot 2 of unmodified master)
        val litCx = slotCentersX[2] * w
        val litSrc = Rect(
            (litCx - halfW).toInt().coerceIn(0, master.width - 1),
            (cy - halfH).toInt().coerceIn(0, master.height - 1),
            (litCx + halfW).toInt().coerceIn(1, master.width),
            (cy + halfH).toInt().coerceIn(1, master.height)
        )

        // Source rect for a native UNLIT star (taken from slot 4 of unmodified master)
        val unlitCx = slotCentersX[4] * w
        val unlitSrc = Rect(
            (unlitCx - halfW).toInt().coerceIn(0, master.width - 1),
            (cy - halfH).toInt().coerceIn(0, master.height - 1),
            (unlitCx + halfW).toInt().coerceIn(1, master.width),
            (cy + halfH).toInt().coerceIn(1, master.height)
        )

        for (i in 0 until 5) {
            val dstCx = slotCentersX[i] * w
            val dst = RectF(dstCx - halfW, cy - halfH, dstCx + halfW, cy + halfH)
            val shouldBeLit = (i < clamped)
            if (shouldBeLit && i == 4) {
                c.drawBitmap(master, litSrc, dst, paint)
            } else if (!shouldBeLit && i < 4) {
                c.drawBitmap(master, unlitSrc, dst, paint)
            }
        }
    }

    private fun eraseCoonsSmooth(
        bitmap: Bitmap,
        l: Float, t: Float, r: Float, b: Float,
        grainSigma: Float = 1.1f
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val x1 = (l * w).toInt().coerceIn(2, w - 3)
        val y1 = (t * h).toInt().coerceIn(2, h - 3)
        val x2 = (r * w).toInt().coerceIn(x1 + 1, w - 2)
        val y2 = (b * h).toInt().coerceIn(y1 + 1, h - 2)
        val bw = x2 - x1
        val bh = y2 - y1

        val topR = FloatArray(bw); val topG = FloatArray(bw); val topB = FloatArray(bw)
        val botR = FloatArray(bw); val botG = FloatArray(bw); val botB = FloatArray(bw)
        for (x in 0 until bw) {
            var tr = 0f; var tg = 0f; var tb = 0f
            var br = 0f; var bg = 0f; var bb = 0f
            var cnt = 0
            for (dx in -6..6) {
                val sx = (x1 + x + dx).coerceIn(0, w - 1)
                val pT = bitmap.getPixel(sx, (y1 - 2).coerceAtLeast(0))
                val pB = bitmap.getPixel(sx, (y2 + 2).coerceAtMost(h - 1))
                tr += Color.red(pT); tg += Color.green(pT); tb += Color.blue(pT)
                br += Color.red(pB); bg += Color.green(pB); bb += Color.blue(pB)
                cnt++
            }
            topR[x] = tr / cnt; topG[x] = tg / cnt; topB[x] = tb / cnt
            botR[x] = br / cnt; botG[x] = bg / cnt; botB[x] = bb / cnt
        }

        val leftR = FloatArray(bh); val leftG = FloatArray(bh); val leftB = FloatArray(bh)
        val rightR = FloatArray(bh); val rightG = FloatArray(bh); val rightB = FloatArray(bh)
        for (y in 0 until bh) {
            var lr = 0f; var lg = 0f; var lb = 0f
            var rr = 0f; var rg = 0f; var rb = 0f
            var cnt = 0
            for (dy in -6..6) {
                val sy = (y1 + y + dy).coerceIn(0, h - 1)
                val pL = bitmap.getPixel((x1 - 2).coerceAtLeast(0), sy)
                val pR = bitmap.getPixel((x2 + 2).coerceAtMost(w - 1), sy)
                lr += Color.red(pL); lg += Color.green(pL); lb += Color.blue(pL)
                rr += Color.red(pR); rg += Color.green(pR); rb += Color.blue(pR)
                cnt++
            }
            leftR[y] = lr / cnt; leftG[y] = lg / cnt; leftB[y] = lb / cnt
            rightR[y] = rr / cnt; rightG[y] = rg / cnt; rightB[y] = rb / cnt
        }

        val c00R = topR.first(); val c00G = topG.first(); val c00B = topB.first()
        val c10R = topR.last();  val c10G = topG.last();  val c10B = topB.last()
        val c01R = botR.first(); val c01G = botG.first(); val c01B = botB.first()
        val c11R = botR.last();  val c11G = botG.last();  val c11B = botB.last()

        val rowPixels = IntArray(bw)
        val rnd = java.util.Random(42L)
        for (y in 0 until bh) {
            val v = if (bh > 1) y.toFloat() / (bh - 1).toFloat() else 0.5f
            for (x in 0 until bw) {
                val u = if (bw > 1) x.toFloat() / (bw - 1).toFloat() else 0.5f
                val n = if (grainSigma > 0f) (rnd.nextGaussian().toFloat() * grainSigma) else 0f
                val rVal = ((1f - u) * leftR[y] + u * rightR[y] + (1f - v) * topR[x] + v * botR[x] -
                    ((1f - u) * (1f - v) * c00R + u * (1f - v) * c10R + (1f - u) * v * c01R + u * v * c11R) + n)
                    .toInt().coerceIn(0, 255)
                val gVal = ((1f - u) * leftG[y] + u * rightG[y] + (1f - v) * topG[x] + v * botG[x] -
                    ((1f - u) * (1f - v) * c00G + u * (1f - v) * c10G + (1f - u) * v * c01G + u * v * c11G) + n)
                    .toInt().coerceIn(0, 255)
                val bVal = ((1f - u) * leftB[y] + u * rightB[y] + (1f - v) * topB[x] + v * botB[x] -
                    ((1f - u) * (1f - v) * c00B + u * (1f - v) * c10B + (1f - u) * v * c01B + u * v * c11B) + n)
                    .toInt().coerceIn(0, 255)
                rowPixels[x] = Color.argb(255, rVal, gVal, bVal)
            }
            bitmap.setPixels(rowPixels, 0, bw, x1, y1 + y, bw, 1)
        }
    }

    private fun eraseAmberInkOnly(
        bitmap: Bitmap,
        l: Float, t: Float, r: Float, b: Float
    ) {
        val w = bitmap.width; val h = bitmap.height
        val x1 = (l * w).toInt().coerceIn(0, w - 1)
        val y1 = (t * h).toInt().coerceIn(0, h - 1)
        val x2 = (r * w).toInt().coerceIn(x1 + 1, w)
        val y2 = (b * h).toInt().coerceIn(y1 + 1, h)
        val bw = x2 - x1; val bh = y2 - y1
        val pixels = IntArray(bw * bh)
        bitmap.getPixels(pixels, 0, bw, x1, y1, bw, bh)

        val mask = BooleanArray(bw * bh)
        for (i in pixels.indices) {
            val p = pixels[i]
            val rC = Color.red(p); val gC = Color.green(p); val bC = Color.blue(p)
            // Warm amber/yellow text has R > 95 and R > B + 18 (whereas blueprint sky/grid has B > R)
            if ((rC > 95 && rC > bC + 18) || (rC > 140 && gC > 110)) {
                mask[i] = true
            }
        }
        val rnd = java.util.Random(99L)
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                var hit = false
                for (dy in -2..2) {
                    val sy = (y + dy).coerceIn(0, bh - 1)
                    for (dx in -2..2) {
                        val sx = (x + dx).coerceIn(0, bw - 1)
                        if (mask[sy * bw + sx]) { hit = true; break }
                    }
                    if (hit) break
                }
                if (hit) {
                    // Find nearest non-amber blueprint background pixel on the same row
                    var bgR = 9; var bgG = 42; var bgB = 78
                    for (step in 3..28 step 3) {
                        val sxL = (x - step).coerceAtLeast(0)
                        val sxR = (x + step).coerceAtMost(bw - 1)
                        if (!mask[y * bw + sxL]) {
                            val p = pixels[y * bw + sxL]
                            bgR = Color.red(p); bgG = Color.green(p); bgB = Color.blue(p)
                            break
                        } else if (!mask[y * bw + sxR]) {
                            val p = pixels[y * bw + sxR]
                            bgR = Color.red(p); bgG = Color.green(p); bgB = Color.blue(p)
                            break
                        }
                    }
                    val n = (rnd.nextGaussian() * 1.2).toInt()
                    pixels[y * bw + x] = Color.argb(
                        255,
                        (bgR + n).coerceIn(0, 255),
                        (bgG + n).coerceIn(0, 255),
                        (bgB + n).coerceIn(0, 255)
                    )
                }
            }
        }
        bitmap.setPixels(pixels, 0, bw, x1, y1, bw, bh)
    }


    private fun eraseDarkInkFast(
        bitmap: Bitmap,
        l: Float, t: Float, r: Float, b: Float,
        diffThreshold: Int = 14
    ) {
        val w = bitmap.width; val h = bitmap.height
        val x1 = (l * w).toInt().coerceIn(0, w - 1)
        val y1 = (t * h).toInt().coerceIn(0, h - 1)
        val x2 = (r * w).toInt().coerceIn(x1 + 1, w)
        val y2 = (b * h).toInt().coerceIn(y1 + 1, h)
        val bw = x2 - x1; val bh = y2 - y1
        val pixels = IntArray(bw * bh)
        bitmap.getPixels(pixels, 0, bw, x1, y1, bw, bh)

        val bgR = IntArray(bw * bh); val bgG = IntArray(bw * bh); val bgB = IntArray(bw * bh)
        val sampleR = IntArray(21); val sampleG = IntArray(21); val sampleB = IntArray(21)
        val tmpR = IntArray(bw * bh); val tmpG = IntArray(bw * bh); val tmpB = IntArray(bw * bh)

        for (y in 0 until bh) {
            val rowOff = y * bw
            for (x in 0 until bw) {
                var idx = 0
                for (dx in -20..20 step 2) {
                    val sx = (x + dx).coerceIn(0, bw - 1)
                    val p = pixels[rowOff + sx]
                    sampleR[idx] = Color.red(p); sampleG[idx] = Color.green(p); sampleB[idx] = Color.blue(p)
                    idx++
                }
                sampleR.sort(); sampleG.sort(); sampleB.sort()
                tmpR[rowOff + x] = sampleR[18]; tmpG[rowOff + x] = sampleG[18]; tmpB[rowOff + x] = sampleB[18]
            }
        }
        for (x in 0 until bw) {
            for (y in 0 until bh) {
                var idx = 0
                for (dy in -20..20 step 2) {
                    val sy = (y + dy).coerceIn(0, bh - 1)
                    val pos = sy * bw + x
                    sampleR[idx] = tmpR[pos]; sampleG[idx] = tmpG[pos]; sampleB[idx] = tmpB[pos]
                    idx++
                }
                sampleR.sort(); sampleG.sort(); sampleB.sort()
                val pos = y * bw + x
                bgR[pos] = sampleR[18]; bgG[pos] = sampleG[18]; bgB[pos] = sampleB[18]
            }
        }
        val mask = BooleanArray(bw * bh)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (maxOf(bgR[i] - Color.red(p), bgG[i] - Color.green(p), bgB[i] - Color.blue(p)) > diffThreshold) {
                mask[i] = true
            }
        }
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                var hit = false
                for (dy in -2..2) {
                    val sy = (y + dy).coerceIn(0, bh - 1)
                    for (dx in -2..2) {
                        val sx = (x + dx).coerceIn(0, bw - 1)
                        if (mask[sy * bw + sx]) { hit = true; break }
                    }
                    if (hit) break
                }
                if (hit) {
                    val pos = y * bw + x
                    pixels[pos] = Color.argb(255, bgR[pos], bgG[pos], bgB[pos])
                }
            }
        }
        bitmap.setPixels(pixels, 0, bw, x1, y1, bw, bh)
    }

    private fun eraseAnnularSectorClean(
        bitmap: Bitmap,
        cx: Float, cy: Float,
        rIn: Float, rOut: Float,
        degStart: Float, degEnd: Float
    ) {
        val w = bitmap.width; val h = bitmap.height
        val x1 = (cx - rOut - 4f).toInt().coerceIn(0, w - 1)
        val y1 = (cy - rOut - 4f).toInt().coerceIn(0, h - 1)
        val x2 = (cx + rOut + 4f).toInt().coerceIn(x1 + 1, w)
        val y2 = (cy + rOut + 4f).toInt().coerceIn(y1 + 1, h)
        val bw = x2 - x1; val bh = y2 - y1
        val pixels = IntArray(bw * bh)
        bitmap.getPixels(pixels, 0, bw, x1, y1, bw, bh)
        val rnd = java.util.Random(404L)

        for (y in 0 until bh) {
            val dy = (y1 + y) - cy
            for (x in 0 until bw) {
                val dx = (x1 + x) - cx
                val dist = hypot(dx, dy)
                if (dist in rIn..rOut) {
                    val ang = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()) + 360f) % 360f
                    val inAng = if (degStart <= degEnd) (ang in degStart..degEnd) else (ang >= degStart || ang <= degEnd)
                    if (inAng) {
                        val p = pixels[y * bw + x]
                        if (Color.red(p) > 32 || Color.green(p) > 38) {
                            val n = (rnd.nextGaussian() * 1.2).toInt()
                            pixels[y * bw + x] = Color.argb(255, (13 + n).coerceIn(8, 22), (22 + n).coerceIn(14, 32), (43 + n).coerceIn(30, 56))
                        }
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, bw, x1, y1, bw, bh)
    }

    private fun drawCrispFiveStarsLeftToRight(
        c: Canvas,
        w: Float,
        h: Float,
        rating: Int,
        slotCentersX: FloatArray,
        centerYNorm: Float,
        outerRadiusNormW: Float,
        litFillColor: Int,
        unlitFillColor: Int?,
        unlitStrokeColor: Int,
        medallionBgColor: Int? = null,
        glowColor: Int? = null
    ) {
        val clamped = rating.coerceIn(0, 5)
        val cy = centerYNorm * h
        val outerR = outerRadiusNormW * w
        val strokeW = (outerR * 0.15f).coerceAtLeast(1.8f)

        for (i in 0 until 5) {
            val cx = slotCentersX[i] * w
            val isLit = (i < clamped)
            // Inset unlit star radius by strokeW * 0.55f so its outer stroke perimeter matches lit outerR to the pixel!
            val effectiveOuterR = if (isLit) outerR else (outerR - strokeW * 0.55f)
            val effectiveInnerR = effectiveOuterR * 0.42f

            if (medallionBgColor != null) {
                c.drawCircle(cx, cy, outerR * 1.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLit) medallionBgColor else Color.argb(85, 160, 148, 130)
                })
                if (isLit) {
                    c.drawCircle(cx, cy, outerR * 1.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = strokeW * 0.9f; color = litFillColor
                    })
                }
            }

            val starPath = Path().apply {
                for (pt in 0 until 10) {
                    val rad = if (pt % 2 == 0) effectiveOuterR else effectiveInnerR
                    val ang = -Math.PI / 2.0 + pt * (Math.PI / 5.0)
                    val px = cx + (rad * kotlin.math.cos(ang)).toFloat()
                    val py = cy + (rad * kotlin.math.sin(ang)).toFloat()
                    if (pt == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }

            if (isLit) {
                if (glowColor != null) {
                    c.drawCircle(cx, cy, outerR * 1.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(cx, cy, outerR * 1.75f, glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    })
                }
                c.drawPath(starPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = litFillColor
                })
            } else {
                if (unlitFillColor != null) {
                    c.drawPath(starPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = unlitFillColor
                    })
                }
                c.drawPath(starPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeW
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    color = unlitStrokeColor
                })
            }
        }
    }

    /**
     * Erases a rectangular region in ONE unified top-to-bottom vertical interpolation pass
     * from yTop (clean surface above "I'VE READ") to yBot (clean surface below the stars),
     * with horizontal cosine feathering at the left and right edges.
     * Because every column x interpolates strictly between its own (x, yTop) and (x, yBot),
     * the card's horizontal lighting gradient is preserved 100% with ZERO vertical lines
     * and ZERO horizontal lines.
     */
    /**
     * Seamlessly erases the upper-right header & star block ("I'VE READ", Title, Author,
     * Date, "Gave it", and all 5 stars) on Cards 3 (Clipboard), 5 (Blueprint), and 6 (Glass)
     * with ZERO vertical lines, ZERO horizontal lines, and ZERO cover-shadow bleed.
     */
    private fun eraseHeaderFromCleanTopAndRight(
        bmp: Bitmap,
        leftFrac: Float = 0.370f,
        topFrac: Float = 0.045f,
        rightFrac: Float = 0.908f,
        bottomFrac: Float = 0.332f
    ) {
        val w = bmp.width
        val h = bmp.height
        val x1 = (leftFrac * w).toInt().coerceIn(2, w - 4)
        val x2 = (rightFrac * w).toInt().coerceIn(x1 + 1, w - 4)
        val y1 = (topFrac * h).toInt().coerceIn(4, h - 4)
        val y2 = (bottomFrac * h).toInt().coerceIn(y1 + 1, h - 4)

        // Precompute clean horizontal profile H(x) along y1 (averaged over 4 rows above y1)
        val topR = IntArray(w)
        val topG = IntArray(w)
        val topB = IntArray(w)
        for (x in x1..x2) {
            var r = 0; var g = 0; var b = 0
            for (dy in 1..4) {
                val c = bmp.getPixel(x, (y1 - dy).coerceAtLeast(0))
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            topR[x] = r / 4
            topG[x] = g / 4
            topB[x] = b / 4
        }

        // Precompute clean vertical profile V(y) along x2 (averaged over 4 cols right of x2)
        val rightR = IntArray(h)
        val rightG = IntArray(h)
        val rightB = IntArray(h)
        for (y in y1..y2) {
            var r = 0; var g = 0; var b = 0
            for (dx in 1..4) {
                val c = bmp.getPixel((x2 + dx).coerceAtMost(w - 1), y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            rightR[y] = r / 4
            rightG[y] = g / 4
            rightB[y] = b / 4
        }

        // Corner anchor at (x2, y1)
        val cornerR = topR[x2]
        val cornerG = topG[x2]
        val cornerB = topB[x2]

        val featherPx = (0.015f * w).toInt().coerceAtLeast(10)

        for (y in y1..y2) {
            val dR = rightR[y] - cornerR
            val dG = rightG[y] - cornerG
            val dB = rightB[y] - cornerB

            val distTop = y - y1
            val distBot = y2 - y
            val vFade = when {
                distTop < featherPx -> {
                    val u = distTop.toFloat() / featherPx.toFloat()
                    0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                }
                distBot < featherPx -> {
                    val u = distBot.toFloat() / featherPx.toFloat()
                    0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                }
                else -> 1f
            }

            for (x in x1..x2) {
                val cleanR = (topR[x] + dR).coerceIn(0, 255)
                val cleanG = (topG[x] + dG).coerceIn(0, 255)
                val cleanB = (topB[x] + dB).coerceIn(0, 255)

                val distLeft = x - x1
                val distRight = x2 - x
                val hFade = when {
                    distLeft < featherPx -> {
                        val u = distLeft.toFloat() / featherPx.toFloat()
                        0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                    }
                    distRight < featherPx -> {
                        val u = distRight.toFloat() / featherPx.toFloat()
                        0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                    }
                    else -> 1f
                }

                val fade = (vFade * hFade).coerceIn(0f, 1f)
                if (fade >= 0.999f) {
                    bmp.setPixel(x, y, (0xFF shl 24) or (cleanR shl 16) or (cleanG shl 8) or cleanB)
                } else {
                    val orig = bmp.getPixel(x, y)
                    val or = (orig shr 16) and 0xFF
                    val og = (orig shr 8) and 0xFF
                    val ob = orig and 0xFF
                    val fr = (or * (1f - fade) + cleanR * fade).toInt()
                    val fg = (og * (1f - fade) + cleanG * fade).toInt()
                    val fb = (ob * (1f - fade) + cleanB * fade).toInt()
                    bmp.setPixel(x, y, (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb)
                }
            }
        }
    }

    private fun eraseVertSmooth(
        bmp: Bitmap,
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float
    ) {
        val w = bmp.width
        val h = bmp.height
        val x1 = (leftFrac * w).toInt().coerceIn(2, w - 3)
        val x2 = (rightFrac * w).toInt().coerceIn(x1 + 1, w - 3)
        val y1 = (topFrac * h).toInt().coerceIn(3, h - 4)
        val y2 = (bottomFrac * h).toInt().coerceIn(y1 + 1, h - 4)

        val spanY = (y2 - y1).coerceAtLeast(1).toFloat()
        val featherPx = 8

        for (x in x1..x2) {
            var rTop = 0; var gTop = 0; var bTop = 0
            var rBot = 0; var gBot = 0; var bBot = 0
            for (dy in 1..4) {
                val cT = bmp.getPixel(x, (y1 - dy).coerceAtLeast(0))
                rTop += (cT shr 16) and 0xFF
                gTop += (cT shr 8) and 0xFF
                bTop += cT and 0xFF

                val cB = bmp.getPixel(x, (y2 + dy).coerceAtMost(h - 1))
                rBot += (cB shr 16) and 0xFF
                gBot += (cB shr 8) and 0xFF
                bBot += cB and 0xFF
            }
            rTop /= 4; gTop /= 4; bTop /= 4
            rBot /= 4; gBot /= 4; bBot /= 4

            for (y in y1..y2) {
                val t = (y - y1).toFloat() / spanY
                val s = t * t * (3f - 2f * t)
                val interpR = (rTop * (1f - s) + rBot * s).toInt().coerceIn(0, 255)
                val interpG = (gTop * (1f - s) + gBot * s).toInt().coerceIn(0, 255)
                val interpB = (bTop * (1f - s) + bBot * s).toInt().coerceIn(0, 255)

                val dyTop = y - y1
                val dyBot = y2 - y
                val vWeight = when {
                    dyTop < featherPx -> (0.5f - 0.5f * kotlin.math.cos((dyTop.toFloat() / featherPx) * Math.PI.toFloat()))
                    dyBot < featherPx -> (0.5f - 0.5f * kotlin.math.cos((dyBot.toFloat() / featherPx) * Math.PI.toFloat()))
                    else -> 1f
                }

                if (vWeight >= 0.999f) {
                    bmp.setPixel(x, y, (0xFF shl 24) or (interpR shl 16) or (interpG shl 8) or interpB)
                } else {
                    val orig = bmp.getPixel(x, y)
                    val or = (orig shr 16) and 0xFF
                    val og = (orig shr 8) and 0xFF
                    val ob = orig and 0xFF
                    val fr = (or * (1f - vWeight) + interpR * vWeight).toInt().coerceIn(0, 255)
                    val fg = (og * (1f - vWeight) + interpG * vWeight).toInt().coerceIn(0, 255)
                    val fb = (ob * (1f - vWeight) + interpB * vWeight).toInt().coerceIn(0, 255)
                    bmp.setPixel(x, y, (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb)
                }
            }
        }
    }

    private fun eraseHorizSmooth(
        bmp: Bitmap,
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float
    ) {
        val w = bmp.width
        val h = bmp.height
        val x1 = (leftFrac * w).toInt().coerceIn(2, w - 3)
        val x2 = (rightFrac * w).toInt().coerceIn(x1 + 1, w - 3)
        val y1 = (topFrac * h).toInt().coerceIn(2, h - 3)
        val y2 = (bottomFrac * h).toInt().coerceIn(y1 + 1, h - 3)

        val spanX = (x2 - x1).coerceAtLeast(1).toFloat()

        for (y in y1..y2) {
            var rL = 0; var gL = 0; var bL = 0
            var rR = 0; var gR = 0; var bR = 0
            for (dx in 1..3) {
                val cL = bmp.getPixel((x1 - dx).coerceAtLeast(0), y)
                rL += (cL shr 16) and 0xFF
                gL += (cL shr 8) and 0xFF
                bL += cL and 0xFF

                val cR = bmp.getPixel((x2 + dx).coerceAtMost(w - 1), y)
                rR += (cR shr 16) and 0xFF
                gR += (cR shr 8) and 0xFF
                bR += cR and 0xFF
            }
            rL /= 3; gL /= 3; bL /= 3
            rR /= 3; gR /= 3; bR /= 3

            for (x in x1..x2) {
                val t = (x - x1).toFloat() / spanX
                val s = t * t * (3f - 2f * t)
                val interpR = (rL * (1f - s) + rR * s).toInt().coerceIn(0, 255)
                val interpG = (gL * (1f - s) + gR * s).toInt().coerceIn(0, 255)
                val interpB = (bL * (1f - s) + bR * s).toInt().coerceIn(0, 255)
                bmp.setPixel(x, y, (0xFF shl 24) or (interpR shl 16) or (interpG shl 8) or interpB)
            }
        }
    }

    private fun drawLedMatrixStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, isLit: Boolean) {
        val innerR = outerR * 0.42f
        val path = Path()
        val numPoints = 5
        val startAngle = -Math.PI / 2.0
        for (i in 0 until numPoints * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = startAngle + i * Math.PI / numPoints
            val x = (cx + cos(angle) * r).toFloat()
            val y = (cy + sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        if (isLit) {
            // 1. Soft warm outer orange bloom/glow
            val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0x66FF8A22.toInt()
                maskFilter = BlurMaskFilter(outerR * 0.55f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(path, bloomPaint)

            // 2. Radiant fiery-amber star body
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    cx, cy, outerR * 1.05f,
                    intArrayOf(0xFFFFF0B8.toInt(), 0xFFFFAA33.toInt(), 0xFFFF5500.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(path, fillPaint)

            // 3. Crisp bright amber rim
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outerR * 0.08f
                color = 0xFFFFDD88.toInt()
            }
            canvas.drawPath(path, strokePaint)

            // 4. Center incandescent pinpoint
            val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xEEFFFFFF.toInt()
            }
            canvas.drawCircle(cx, cy, outerR * 0.22f, centerPaint)
        } else {
            // Unlit recessed gunmetal LED aperture
            val recessedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFF181C20.toInt()
            }
            canvas.drawPath(path, recessedPaint)

            val unlitRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outerR * 0.08f
                color = 0xFF2D333B.toInt()
            }
            canvas.drawPath(path, unlitRimPaint)
        }
    }

    private fun drawBookCoverCenterCropWithShadow(
        canvas: Canvas,
        coverBitmap: Bitmap?,
        dstRect: RectF,
        cornerRadiusPx: Float,
        shadowColor: Int,
        borderColor: Int
    ) {
        val w = canvas.width.toFloat()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter(0.012f * w, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(dstRect.left, dstRect.top + 0.006f * w, dstRect.right, dstRect.bottom + 0.006f * w),
            cornerRadiusPx, cornerRadiusPx, shadowPaint
        )

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            val save = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(dstRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            val srcW = coverBitmap.width.toFloat().coerceAtLeast(1f)
            val srcH = coverBitmap.height.toFloat().coerceAtLeast(1f)
            val dstW = dstRect.width()
            val dstH = dstRect.height()
            val scale = kotlin.math.max(dstW / srcW, dstH / srcH)
            val scaledW = srcW * scale
            val scaledH = srcH * scale
            val dx = dstRect.left + (dstW - scaledW) * 0.5f
            val dy = dstRect.top + (dstH - scaledH) * 0.5f

            val matrix = android.graphics.Matrix().apply {
                postScale(scale, scale)
                postTranslate(dx, dy)
            }
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(coverBitmap, matrix, bmpPaint)
            canvas.restoreToCount(save)
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (0.0018f * w).coerceAtLeast(1.5f)
            color = borderColor
        }
        canvas.drawRoundRect(dstRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
    }

    /**
     * Sharpens the base template bitmap in-place using a subtle luminance unsharp mask
     * so original rivets, etched lines, and paper grain look crisp 2K HD without drawing
     * any duplicate shapes.
     */
    private fun sharpenArchivalTemplateInPlace(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        System.arraycopy(src, 0, dst, 0, src.size)

        val amount = 0.35f // Subtle crisp HD micro-contrast
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = src[idx]
                val l = src[idx - 1]
                val r = src[idx + 1]
                val u = src[idx - w]
                val d = src[idx + w]

                val cR = (c shr 16) and 0xFF
                val cG = (c shr 8) and 0xFF
                val cB = c and 0xFF

                val avgR = (((l shr 16 and 0xFF) + (r shr 16 and 0xFF) + (u shr 16 and 0xFF) + (d shr 16 and 0xFF)) shr 2)
                val avgG = (((l shr 8 and 0xFF) + (r shr 8 and 0xFF) + (u shr 8 and 0xFF) + (d shr 8 and 0xFF)) shr 2)
                val avgB = (((l and 0xFF) + (r and 0xFF) + (u and 0xFF) + (d and 0xFF)) shr 2)

                val nR = (cR + ((cR - avgR) * amount).toInt()).coerceIn(0, 255)
                val nG = (cG + ((cG - avgG) * amount).toInt()).coerceIn(0, 255)
                val nB = (cB + ((cB - avgB) * amount).toInt()).coerceIn(0, 255)

                dst[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
            }
        }
        bmp.setPixels(dst, 0, w, 0, 0, w, h)
    }

    private fun fillCleanLedStarBayInterior(canvas: Canvas, w: Float, h: Float) {
        // Recessed LED star slot strictly inside [0.516, 0.290, 0.938, 0.355]
        val bayRect = RectF(0.516f * w, 0.290f * h, 0.938f * w, 0.355f * h)
        val insetX = 0.005f * w
        val insetY = 0.004f * h
        val interiorRect = RectF(bayRect.left + insetX, bayRect.top + insetY, bayRect.right - insetX, bayRect.bottom - insetY)
        val cornerR = 0.008f * w

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF111215.toInt()
        }
        canvas.drawRoundRect(interiorRect, cornerR, cornerR, bgPaint)
    }

    private fun drawObsidianCoverClean(
        canvas: Canvas,
        coverBitmap: Bitmap?,
        dstRect: RectF
    ) {
        val w = canvas.width.toFloat()
        val cornerRadiusPx = 0.008f * w

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            val srcW = coverBitmap.width.toFloat().coerceAtLeast(1f)
            val srcH = coverBitmap.height.toFloat().coerceAtLeast(1f)
            val dstW = dstRect.width()
            val dstH = dstRect.height()

            // Letterbox / fit cleanly inside dstRect preserving aspect ratio
            val scale = kotlin.math.min(dstW / srcW, dstH / srcH)
            val fittedW = srcW * scale
            val fittedH = srcH * scale
            val fittedRect = RectF(
                dstRect.centerX() - fittedW / 2f,
                dstRect.centerY() - fittedH / 2f,
                dstRect.centerX() + fittedW / 2f,
                dstRect.centerY() + fittedH / 2f
            )

            val save = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(fittedRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            val matrix = android.graphics.Matrix().apply {
                postScale(scale, scale)
                postTranslate(fittedRect.left, fittedRect.top)
            }
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(coverBitmap, matrix, bmpPaint)
            canvas.restoreToCount(save)

            // Subtle warm gold hairline frame
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (0.0016f * w).coerceAtLeast(1.5f)
                color = 0x44C89D54
            }
            canvas.drawRoundRect(fittedRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
        }
    }

    private fun draw3DCopperPaperclip(
        c: Canvas,
        cx: Float,
        topY: Float,
        clipW: Float,
        clipH: Float,
        tiltDeg: Float = -4f
    ) {
        c.save()
        c.rotate(tiltDeg, cx, topY + clipH * 0.4f)
        val wireStroke = (clipW * 0.16f).coerceAtLeast(3.5f)

        // 1. Soft drop shadow onto book cover & card
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = wireStroke * 1.15f
            color = Color.argb(120, 12, 10, 8)
            maskFilter = BlurMaskFilter(wireStroke * 0.6f, BlurMaskFilter.Blur.NORMAL)
        }
        val outerRect = RectF(cx - clipW * 0.46f, topY, cx + clipW * 0.46f, topY + clipH)
        val innerRect = RectF(cx - clipW * 0.24f, topY + clipH * 0.16f, cx + clipW * 0.24f, topY + clipH * 0.86f)
        c.drawRoundRect(RectF(outerRect.left + 2.5f, outerRect.top + 3f, outerRect.right + 2.5f, outerRect.bottom + 3f), clipW * 0.46f, clipW * 0.46f, shadowPaint)
        c.drawRoundRect(RectF(innerRect.left + 2f, innerRect.top + 2.5f, innerRect.right + 2f, innerRect.bottom + 2.5f), clipW * 0.24f, clipW * 0.24f, shadowPaint)

        // 2. Rich Brushed Copper Base Wire
        val copperBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = wireStroke
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                outerRect.left, outerRect.top, outerRect.right, outerRect.bottom,
                intArrayOf(Color.rgb(148, 78, 34), Color.rgb(224, 142, 84), Color.rgb(172, 94, 44), Color.rgb(112, 54, 20)),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(outerRect, clipW * 0.46f, clipW * 0.46f, copperBase)
        c.drawRoundRect(innerRect, clipW * 0.24f, clipW * 0.24f, copperBase)

        // 3. Specular Metallic Ridge Highlight along the copper wire
        val copperHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = wireStroke * 0.32f
            color = Color.argb(175, 255, 212, 172)
        }
        c.drawRoundRect(RectF(outerRect.left - 0.5f, outerRect.top - 0.5f, outerRect.right - 0.5f, outerRect.bottom - 0.5f), clipW * 0.46f, clipW * 0.46f, copperHighlight)
        c.drawRoundRect(RectF(innerRect.left - 0.5f, innerRect.top - 0.5f, innerRect.right - 0.5f, innerRect.bottom - 0.5f), clipW * 0.24f, clipW * 0.24f, copperHighlight)
        c.restore()
    }

    private fun drawCenterCropBitmap(c: Canvas, bmp: Bitmap, dst: RectF, radiusPx: Float) {
        val save = c.save()
        if (radiusPx > 0f) {
            val clipPath = Path().apply { addRoundRect(dst, radiusPx, radiusPx, Path.Direction.CW) }
            c.clipPath(clipPath)
        } else {
            c.clipRect(dst)
        }
        val scale = max(dst.width() / bmp.width.toFloat(), dst.height() / bmp.height.toFloat())
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        val dx = dst.centerX() - sw / 2f
        val dy = dst.centerY() - sh / 2f
        c.drawBitmap(bmp, null, RectF(dx, dy, dx + sw, dy + sh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restoreToCount(save)
    }

    private fun patchHSlice(
        c: Canvas, master: Bitmap, w: Float, h: Float,
        dstL: Float, dstT: Float, dstR: Float, dstB: Float,
        srcL: Float, srcR: Float
    ) {
        val src = Rect((srcL * w).toInt(), (dstT * h).toInt(), (srcR * w).toInt(), (dstB * h).toInt())
        val dst = RectF(dstL * w, dstT * h, dstR * w, dstB * h)
        c.drawBitmap(master, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun patchTiled(
        c: Canvas, master: Bitmap, w: Float, h: Float,
        dstL: Float, dstT: Float, dstR: Float, dstB: Float,
        srcL: Float, srcT: Float, srcR: Float, srcB: Float
    ) {
        val sx1 = (srcL * w).toInt().coerceIn(0, master.width - 1)
        val sy1 = (srcT * h).toInt().coerceIn(0, master.height - 1)
        val tw = ((srcR - srcL) * w).toInt().coerceAtLeast(4)
        val th = ((srcB - srcT) * h).toInt().coerceAtLeast(4)
        val tile = Bitmap.createBitmap(master, sx1, sy1, tw.coerceAtMost(master.width - sx1), th.coerceAtMost(master.height - sy1))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        c.drawRect(dstL * w, dstT * h, dstR * w, dstB * h, paint)
    }

    private fun drawPlateText(
        c: Canvas, w: Float, h: Float,
        text: String, xNorm: Float, yNorm: Float, sizeNormH: Float,
        color: Int, typeface: Typeface = Typeface.DEFAULT_BOLD,
        align: Paint.Align = Paint.Align.LEFT, letterSpacingEm: Float = 0f
    ) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.color = color
            this.textSize = sizeNormH * h
            this.typeface = resolveTypefaceForText(text, typeface)
            this.textAlign = align
            this.letterSpacing = letterSpacingEm
            this.isSubpixelText = true
            this.isLinearText = true
        }
        val fm = p.fontMetrics
        val baselineY = (yNorm * h) - (fm.ascent + fm.descent) / 2f
        c.drawText(text, xNorm * w, baselineY, p)
    }

    private fun drawPlateAutoFitTitle(
        c: Canvas, w: Float, h: Float,
        title: String, xNorm: Float, topYNorm: Float, maxWidthNorm: Float,
        initialSizeNormH: Float, color: Int, typeface: Typeface,
        maxLines: Int = 2, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        var sizePx = initialSizeNormH * h
        val minSizePx = 0.016f * h
        val maxW = (maxWidthNorm * w).toInt().coerceAtLeast(60)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.color = color
            this.typeface = resolveTypefaceForText(title, typeface)
            this.isSubpixelText = true
            this.isLinearText = true
        }
        var layout: StaticLayout
        while (true) {
            tp.textSize = sizePx
            layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, maxW)
                .setAlignment(align)
                .setLineSpacing(0f, 1.04f)
                .setIncludePad(false)
                .build()
            if (layout.lineCount <= maxLines || sizePx <= minSizePx) break
            sizePx *= 0.90f
        }
        c.save()
        c.translate(xNorm * w, topYNorm * h)
        layout.draw(c)
        c.restore()
        return topYNorm + (layout.height / h)
    }

    private fun drawCurvedPlateText(
        c: Canvas, text: String,
        cx: Float, cy: Float, radius: Float,
        startAngleDeg: Float, sweepAngleDeg: Float,
        sizePx: Float, color: Int, typeface: Typeface,
        letterSpacingEm: Float = 0.03f
    ) {
        val path = Path().apply {
            addArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), startAngleDeg, sweepAngleDeg)
        }
        val maxArcLength = abs(Math.toRadians(sweepAngleDeg.toDouble())).toFloat() * radius * 0.92f
        var curSize = sizePx
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = typeface
            this.textAlign = Paint.Align.CENTER
            this.letterSpacing = letterSpacingEm
        }
        while (curSize > sizePx * 0.58f) {
            p.textSize = curSize
            if (p.measureText(text) <= maxArcLength) break
            curSize *= 0.92f
        }
        c.drawTextOnPath(text, path, 0f, curSize * 0.34f, p)
    }

    // =========================================================================
    // 1. ARCHIVAL BRASS & SPLIT-FLAP LEDGER (`style_1.png` @ 2K 1792x2240)
    // =========================================================================
    private fun compositeStyle1(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // Disabled harsh in-place template sharpening to eradicate halos around text
        val labelColor = Color.rgb(48, 40, 30)
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val serifReg = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val sansBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val grotesqueBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        // 1. Top "BOOK COMPLETED" Header
        eraseCoonsSmooth(working, 0.315f, 0.044f, 0.685f, 0.068f)
        drawPlateText(c, w, h, "BOOK COMPLETED", 0.500f, 0.056f, 0.0145f, labelColor, serifBold, Paint.Align.CENTER, 0.28f)

        // 2. Erase entire old orange book [0.082, 0.088, 0.426, 0.420] to clean parchment
        eraseCoonsSmooth(working, 0.082f, 0.088f, 0.426f, 0.420f)

        // 3. Render sleek, properly-proportioned portrait hardcover (20% more compact so it never looks oversized!)
        val x1 = 0.118f * w; val y1 = 0.116f * h; val x2 = 0.382f * w; val y2 = 0.390f * h
        val spineW = 0.020f * w
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 22, 16, 10)
            maskFilter = BlurMaskFilter(0.008f * w, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(x1 - 0.005f * w, y1 + 0.006f * h, x2 + 0.004f * w, y2 + 0.008f * h), 8f, 8f, shadowPaint)
        if (cover != null) {
            val sampleCol = cover.getPixel((cover.width * 0.08f).toInt().coerceAtLeast(0), cover.height / 2)
            val sr = Color.red(sampleCol); val sg = Color.green(sampleCol); val sb = Color.blue(sampleCol)
            c.drawRect(x1, y1, x1 + spineW, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    x1, y1, x1 + spineW, y1,
                    intArrayOf(
                        Color.rgb((sr * 0.45f).toInt(), (sg * 0.45f).toInt(), (sb * 0.45f).toInt()),
                        Color.rgb((sr * 0.88f).toInt(), (sg * 0.88f).toInt(), (sb * 0.88f).toInt()),
                        Color.rgb((sr * 0.60f).toInt(), (sg * 0.60f).toInt(), (sb * 0.60f).toInt())
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            drawCenterCropBitmap(c, cover, RectF(x1 + spineW, y1, x2, y2), 0f)
            c.drawLine(x1 + spineW, y1, x1 + spineW, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 15, 12, 10); strokeWidth = (0.002f * w).coerceAtLeast(2f)
            })
        }



        // 4. Erase right metadata block (keep "MY RATING" at y=0.338 and native stars at y=0.379 intact!)
        eraseCoonsSmooth(working, 0.440f, 0.100f, 0.935f, 0.320f)
        val bottomTitleY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.452f, 0.122f, 0.460f, 0.031f, Color.rgb(26, 23, 20), serifBold, maxLines = 2)
        val ruleY = (bottomTitleY + 0.014f).coerceAtMost(0.215f)
        c.drawRect(0.452f * w, ruleY * h, 0.535f * w, (ruleY + 0.0032f) * h, Paint().apply { color = Color.rgb(156, 123, 62) })
        drawPlateText(c, w, h, stats.authorName, 0.452f, ruleY + 0.034f, 0.024f, Color.rgb(51, 44, 35), serifReg)
        drawPlateText(c, w, h, "${stats.startDateFormatted}  —  ${stats.endDateFormatted}", 0.452f, ruleY + 0.076f, 0.016f, Color.rgb(51, 44, 35), Typeface.DEFAULT_BOLD)

        // 5. "MY RATING" Label above stars
        eraseCoonsSmooth(working, 0.445f, 0.322f, 0.650f, 0.352f)
        drawPlateText(c, w, h, "MY RATING", 0.452f, 0.338f, 0.0125f, labelColor, sansBold, Paint.Align.LEFT, 0.22f)

        // 6. Guaranteed Crisp 5-Star Vector Rendering
        eraseCoonsSmooth(working, 0.445f, 0.352f, 0.715f, 0.404f)
        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.474f, 0.525f, 0.576f, 0.627f, 0.678f),
            centerYNorm = 0.379f, outerRadiusNormW = 0.019f,
            litFillColor = Color.rgb(184, 138, 58),
            unlitFillColor = Color.rgb(214, 202, 180),
            unlitStrokeColor = Color.rgb(142, 114, 62)
        )

        // 7. Split-Flap 3-Digit Counter
        val digits = stats.totalPagesRead.coerceIn(0, 999).toString().padStart(3, '0')
        val flapL = floatArrayOf(0.142f, 0.240f, 0.336f)
        val flapR = floatArrayOf(0.238f, 0.334f, 0.418f)
        for (i in 0..2) {
            val fl = flapL[i]; val fr = flapR[i]
            val ft = 0.482f; val fb = 0.570f; val fmid = (ft + fb) / 2f
            c.drawRect(fl * w, ft * h, fr * w, fmid * h, Paint().apply { color = Color.rgb(34, 31, 27) })
            c.drawRect(fl * w, fmid * h, fr * w, fb * h, Paint().apply { color = Color.rgb(25, 23, 20) })
            drawPlateText(c, w, h, digits[i].toString(), (fl + fr) / 2f, fmid, 0.064f, Color.rgb(236, 226, 208), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
            c.drawLine(fl * w, fmid * h, fr * w, fmid * h, Paint().apply { color = Color.rgb(12, 11, 10); strokeWidth = 0.0035f * h })
        }

        // 8. Metric Values & Subtitles
        eraseCoonsSmooth(working, 0.210f, 0.592f, 0.420f, 0.622f)
        drawPlateText(c, w, h, "PAGES READ", 0.218f, 0.608f, 0.0125f, labelColor, sansBold, Paint.Align.LEFT, 0.20f)

        eraseCoonsSmooth(working, 0.628f, 0.556f, 0.935f, 0.586f)
        drawPlateText(c, w, h, "TOTAL READING TIME", 0.632f, 0.570f, 0.0120f, labelColor, sansBold, Paint.Align.LEFT, 0.18f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.632f, 0.520f, 0.036f, Color.rgb(28, 25, 21), grotesqueBold)

        eraseCoonsSmooth(working, 0.180f, 0.645f, 0.425f, 0.732f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.200f, 0.692f, 0.050f, Color.rgb(28, 25, 21), grotesqueBold)

        eraseCoonsSmooth(working, 0.185f, 0.730f, 0.480f, 0.758f)
        drawPlateText(c, w, h, "AVERAGE PAGES / HOUR", 0.198f, 0.744f, 0.0115f, labelColor, sansBold, Paint.Align.LEFT, 0.14f)

        eraseCoonsSmooth(working, 0.625f, 0.645f, 0.855f, 0.732f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.640f, 0.692f, 0.050f, Color.rgb(28, 25, 21), grotesqueBold)

        eraseCoonsSmooth(working, 0.628f, 0.730f, 0.935f, 0.758f)
        drawPlateText(c, w, h, "MOST PAGES IN ONE DAY", 0.640f, 0.744f, 0.0115f, labelColor, sansBold, Paint.Align.LEFT, 0.14f)

        eraseCoonsSmooth(working, 0.180f, 0.775f, 0.425f, 0.860f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.200f, 0.822f, 0.050f, Color.rgb(28, 25, 21), grotesqueBold)

        eraseCoonsSmooth(working, 0.185f, 0.858f, 0.490f, 0.886f)
        drawPlateText(c, w, h, "MOST MINUTES IN ONE DAY", 0.198f, 0.872f, 0.0115f, labelColor, sansBold, Paint.Align.LEFT, 0.14f)

        eraseCoonsSmooth(working, 0.665f, 0.796f, 0.840f, 0.850f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.678f, 0.822f, 0.024f, Color.rgb(28, 25, 21), grotesqueBold)

        eraseCoonsSmooth(working, 0.665f, 0.848f, 0.865f, 0.872f)
        drawPlateText(c, w, h, "LONGEST STREAK", 0.678f, 0.860f, 0.0105f, labelColor, sansBold, Paint.Align.LEFT, 0.14f)

        eraseCoonsSmooth(working, 0.305f, 0.916f, 0.695f, 0.952f)
        drawPlateText(c, w, h, "MEZURA  ·  READING RECORD", 0.500f, 0.933f, 0.0135f, Color.rgb(51, 43, 33), serifBold, Paint.Align.CENTER, 0.16f)
    }

    // =========================================================================
    // 2. PERFORATED GUNMETAL & DOT-MATRIX LED PINBOARD (`style_2.jpg`)
    // =========================================================================
    private fun compositeStyle2(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        c.drawRect(0.116f * w, 0.058f * h, 0.384f * w, 0.354f * h, Paint().apply { color = Color.rgb(14, 16, 19) })
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.120f * w, 0.062f * h, 0.380f * w, 0.350f * h), 0.005f * w)
        }

        // Erase Title/Author/Date strictly inside right bay [0.490, 0.095, 0.960, 0.268]
        patchHSlice(c, master, w, h, 0.490f, 0.095f, 0.960f, 0.268f, 0.468f, 0.490f)

        // Clip strictly to the right bay [0.500..0.948] so long titles/authors NEVER bleed onto the book cover!
        c.save()
        c.clipRect(0.500f * w, 0.095f * h, 0.948f * w, 0.268f * h)
        val botY = drawPlateAutoFitTitle(
            c, w, h, stats.bookTitle,
            xNorm = 0.504f, topYNorm = 0.104f, maxWidthNorm = 0.440f,
            initialSizeNormH = 0.030f, color = Color.rgb(255, 196, 77),
            typeface = Typeface.DEFAULT_BOLD, maxLines = 2, align = Layout.Alignment.ALIGN_CENTER
        )
        val authY = (botY + 0.022f).coerceAtMost(0.195f)
        drawPlateText(c, w, h, stats.authorName, 0.724f, authY, 0.021f, Color.rgb(220, 226, 232), Typeface.DEFAULT, Paint.Align.CENTER)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.724f, 0.236f, 0.0145f, Color.rgb(255, 158, 44), Typeface.MONOSPACE, Paint.Align.CENTER, 0.04f)
        c.restore()

        val slotRect = RectF(0.516f * w, 0.290f * h, 0.938f * w, 0.355f * h)
        fillCleanLedStarBayInterior(c, w, h)

        val rating = stats.starRating.coerceIn(0, 5)
        val starCy = slotRect.centerY()
        val starSlotH = slotRect.height()
        val starOuterR = (starSlotH * 0.60f) / 2f
        val starFirstX = 0.564f * w
        val starLastX  = 0.885f * w
        val starStepX  = (starLastX - starFirstX) / 4f

        for (i in 0 until 5) {
            val cx = starFirstX + i * starStepX
            drawLedMatrixStar(c, cx, starCy, starOuterR, isLit = i < rating)
        }

        // Center-Aligned LED Numbers inside the 5 Black LED Windows
        patchTiled(c, master, w, h, 0.080f, 0.436f, 0.450f, 0.524f, 0.082f, 0.440f, 0.140f, 0.518f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.265f, 0.480f, 0.054f, Color.rgb(255, 82, 36), Typeface.MONOSPACE, Paint.Align.CENTER, 0.05f)

        patchTiled(c, master, w, h, 0.538f, 0.436f, 0.930f, 0.524f, 0.082f, 0.440f, 0.140f, 0.518f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.734f, 0.480f, 0.034f, Color.rgb(255, 82, 36), Typeface.MONOSPACE, Paint.Align.CENTER, 0.02f)

        patchTiled(c, master, w, h, 0.080f, 0.614f, 0.450f, 0.702f, 0.082f, 0.440f, 0.140f, 0.518f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.265f, 0.658f, 0.054f, Color.rgb(255, 82, 36), Typeface.MONOSPACE, Paint.Align.CENTER, 0.05f)

        patchTiled(c, master, w, h, 0.538f, 0.614f, 0.842f, 0.702f, 0.082f, 0.440f, 0.140f, 0.518f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.690f, 0.658f, 0.054f, Color.rgb(255, 82, 36), Typeface.MONOSPACE, Paint.Align.CENTER, 0.05f)

        patchTiled(c, master, w, h, 0.090f, 0.808f, 0.395f, 0.922f, 0.082f, 0.440f, 0.140f, 0.518f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.242f, 0.865f, 0.064f, Color.rgb(255, 82, 36), Typeface.MONOSPACE, Paint.Align.CENTER, 0.05f)

        // Bottom Chassis Footer
        patchHSlice(c, master, w, h, 0.040f, 0.958f, 0.220f, 0.994f, 0.225f, 0.285f)
        patchHSlice(c, master, w, h, 0.295f, 0.958f, 0.705f, 0.994f, 0.225f, 0.285f)
        drawPlateText(c, w, h, "MEZURA", 0.055f, 0.968f, 0.008f, Color.rgb(116, 124, 136), Typeface.MONOSPACE, Paint.Align.LEFT, 0.08f)
        drawPlateText(c, w, h, "DIGITAL", 0.055f, 0.981f, 0.010f, Color.rgb(134, 142, 154), Typeface.MONOSPACE, Paint.Align.LEFT, 0.08f)
        drawPlateText(c, w, h, "RECORDED ON MEZURA", 0.500f, 0.976f, 0.014f, Color.rgb(134, 142, 154), Typeface.MONOSPACE, Paint.Align.CENTER, 0.14f)
    }

    // =========================================================================
    // 3. WARM PARCHMENT CLIPBOARD & CHARCOAL BENTO (`style_3.jpg`)
    // =========================================================================
    private fun compositeStyle3(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // 1. Erase ENTIRE old demo book + left shadow [0.068, 0.056, 0.342, 0.336] (zero orange bleed!)
        eraseCoonsSmooth(working, 0.068f, 0.056f, 0.342f, 0.336f)
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.084f * w, 0.064f * h, 0.330f * w, 0.324f * h), 0.008f * w)
        }
        // Stamp masked 3D brass bulldog clip jaws over top center of cover
        val cx1 = (0.145f * w).toInt(); val cy1 = (0.015f * h).toInt()
        val cw = ((0.275f - 0.145f) * w).toInt(); val ch = ((0.086f - 0.015f) * h).toInt()
        val clipBmp = Bitmap.createBitmap(master, cx1, cy1, cw, ch).copy(Bitmap.Config.ARGB_8888, true)
        val coverTopPx = (0.064f * h).toInt()
        val pixels = IntArray(cw * ch)
        clipBmp.getPixels(pixels, 0, cw, 0, 0, cw, ch)
        for (y in 0 until ch) {
            if (cy1 + y < coverTopPx) continue
            for (x in 0 until cw) {
                val p = pixels[y * cw + x]
                if (Color.red(p) > 155 && (Color.red(p) - Color.green(p)) > 40 && Color.blue(p) < 120) {
                    pixels[y * cw + x] = Color.TRANSPARENT
                }
            }
        }
        clipBmp.setPixels(pixels, 0, cw, 0, 0, cw, ch)
        c.drawBitmap(clipBmp, cx1.toFloat(), cy1.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        clipBmp.recycle()

        // 2. Hero Text & Native Left-to-Right Star Medallions
        // Single unified pass erasing full header zone [0.370, 0.045, 0.908, 0.332]
        eraseHeaderFromCleanTopAndRight(working, 0.370f, 0.045f, 0.908f, 0.332f)
        drawPlateText(c, w, h, "I'VE READ", 0.388f, 0.075f, 0.016f, Color.rgb(108, 98, 90), Typeface.create(Typeface.SERIF, Typeface.ITALIC), letterSpacingEm = 0.04f)
        val bottomY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.388f, 0.110f, 0.510f, 0.031f, Color.rgb(22, 25, 32), Typeface.DEFAULT_BOLD, maxLines = 2)
        drawPlateText(c, w, h, stats.authorName, 0.388f, (bottomY + 0.024f).coerceAtMost(0.205f), 0.024f, Color.rgb(37, 40, 48), Typeface.DEFAULT)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.388f, 0.242f, 0.017f, Color.rgb(43, 46, 54), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "Gave it", 0.388f, 0.294f, 0.019f, Color.rgb(108, 98, 90), Typeface.create(Typeface.SERIF, Typeface.ITALIC))

        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.551f, 0.605f, 0.659f, 0.713f, 0.767f),
            centerYNorm = 0.292f,
            outerRadiusNormW = 0.018f,
            litFillColor = Color.rgb(218, 176, 94),
            unlitFillColor = null,
            unlitStrokeColor = Color.rgb(140, 110, 60),
            medallionBgColor = Color.rgb(66, 49, 24)
        )

        // 3. Bento Cards & Embossed Seal
        eraseCoonsSmooth(working, 0.072f, 0.392f, 0.318f, 0.482f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.086f, 0.436f, 0.060f, Color.rgb(235, 201, 153), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.532f, 0.392f, 0.935f, 0.478f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.545f, 0.438f, 0.038f, Color.rgb(214, 138, 58), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.072f, 0.580f, 0.245f, 0.682f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.086f, 0.632f, 0.060f, Color.rgb(235, 201, 153), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.532f, 0.580f, 0.758f, 0.678f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.545f, 0.628f, 0.060f, Color.rgb(235, 201, 153), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.072f, 0.768f, 0.325f, 0.872f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.086f, 0.820f, 0.060f, Color.rgb(235, 201, 153), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.742f, 0.772f, 0.925f, 0.842f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.836f, 0.808f, 0.034f, Color.rgb(214, 138, 56), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.605f, 0.868f, 0.725f, 0.896f)
        drawPlateText(c, w, h, "MEZURA", 0.665f, 0.883f, 0.012f, Color.rgb(95, 98, 104), Typeface.create(Typeface.SERIF, Typeface.BOLD), Paint.Align.CENTER, 0.14f)
    }

    // =========================================================================
    // 4. ASTROLABE & CELESTIAL STAR ATLAS (`style_4.jpg`)
    // =========================================================================
    private fun compositeStyle4(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val serifReg = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        val covRect = RectF(0.430f * w, 0.088f * h, 0.570f * w, 0.232f * h)
        c.drawRoundRect(covRect, 0.007f * w, 0.007f * w, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 22, 43) })
        if (cover != null) {
            drawCenterCropBitmap(c, cover, covRect, 0.007f * w)
        }
        c.drawRoundRect(covRect, 0.007f * w, 0.007f * w, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (0.0024f * w).coerceAtLeast(2.5f)
            color = Color.rgb(212, 175, 98)
        })

        // 2. Erase bottom annular bands under Top Medallion (cx=0.500*w, cy=0.162*h) & redraw gold concentric arcs + curved text
        val mcx = 0.500f * w; val mcy = 0.162f * h
        eraseAnnularSectorClean(working, mcx, mcy, 0.104f * h, 0.248f * h, 18f, 162f)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = (0.0012f * h).coerceAtLeast(1.8f); color = Color.argb(220, 184, 152, 90)
        }
        for (rArc in floatArrayOf(0.148f * h, 0.182f * h, 0.214f * h, 0.244f * h)) {
            c.drawArc(RectF(mcx - rArc, mcy - rArc, mcx + rArc, mcy + rArc), 20f, 140f, false, arcPaint)
        }
        drawCurvedPlateText(c, stats.bookTitle, mcx, mcy, 0.132f * h, 155f, -130f, 0.023f * h, Color.rgb(245, 220, 150), serifBold, 0.02f)
        drawCurvedPlateText(c, stats.authorName, mcx, mcy, 0.166f * h, 150f, -120f, 0.0175f * h, Color.rgb(224, 196, 128), serifReg, 0.02f)
        drawCurvedPlateText(c, "• ${stats.startDateFormatted} - ${stats.endDateFormatted} •", mcx, mcy, 0.199f * h, 150f, -120f, 0.013f * h, Color.rgb(229, 198, 126), Typeface.DEFAULT_BOLD, 0.04f)

        // 3. Left Astrolabe Curved Total Time (cx=0.180*w, cy=0.495*h)
        val acx = 0.180f * w; val acy = 0.495f * h
        eraseAnnularSectorClean(working, acx, acy, 0.104f * h, 0.156f * h, 195f, 348f)
        drawCurvedPlateText(c, "${stats.totalReadingTimeFormatted} TOTAL TIME", acx, acy, 0.118f * h, 202f, 136f, 0.015f * h, Color.rgb(242, 215, 140), Typeface.DEFAULT_BOLD, 0.03f)

        // 4. Center Spiral Curved Pages Read (cx=0.500*w, cy=0.500*h)
        val scx = 0.500f * w; val scy = 0.500f * h
        eraseAnnularSectorClean(working, scx, scy, 0.088f * h, 0.136f * h, 15f, 142f)
        drawCurvedPlateText(c, "${stats.totalPagesRead} PAGES READ", scx, scy, 0.112f * h, 138f, -116f, 0.0175f * h, Color.rgb(245, 220, 150), Typeface.DEFAULT_BOLD, 0.03f)

        // 5. Remaining Metrics & Native Left-to-Right Stars
        c.drawRect(0.752f * w, 0.422f * h, 0.832f * w, 0.462f * h, Paint().apply { color = Color.rgb(13, 22, 43) })
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.792f, 0.443f, 0.028f, Color.rgb(245, 220, 150), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        c.save()
        c.rotate(-28f, 0.775f * w, 0.592f * h)
        c.drawRect(0.725f * w, 0.568f * h, 0.825f * w, 0.614f * h, Paint().apply { color = Color.rgb(13, 22, 43) })
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.775f, 0.592f, 0.032f, Color.rgb(245, 220, 150), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        c.restore()

        c.drawRect(0.220f * w, 0.712f * h, 0.335f * w, 0.758f * h, Paint().apply { color = Color.rgb(13, 22, 43) })
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.275f, 0.736f, 0.032f, Color.rgb(245, 220, 150), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        c.drawRect(0.382f * w, 0.748f * h, 0.618f * w, 0.798f * h, Paint().apply { color = Color.rgb(13, 22, 43) })
        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.415f, 0.457f, 0.500f, 0.542f, 0.585f),
            centerYNorm = 0.774f, outerRadiusNormW = 0.0175f,
            litFillColor = Color.rgb(255, 224, 140),
            unlitFillColor = Color.rgb(20, 32, 58),
            unlitStrokeColor = Color.rgb(184, 152, 90),
            glowColor = Color.argb(95, 255, 210, 102)
        )

        c.drawRect(0.725f * w, 0.754f * h, 0.850f * w, 0.798f * h, Paint().apply { color = Color.rgb(13, 22, 43) })
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.788f, 0.776f, 0.026f, Color.rgb(245, 220, 150), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        c.drawRect(0.268f * w, 0.912f * h, 0.732f * w, 0.946f * h, Paint().apply { color = Color.rgb(11, 19, 38) })
        drawPlateText(c, w, h, "MEZURA CELESTIAL ATLAS", 0.500f, 0.929f, 0.0185f, Color.rgb(232, 200, 125), serifBold, Paint.Align.CENTER, 0.10f)
    }

    // =========================================================================
    // 5. DRAFTING BLUEPRINT & ARCHIVAL ENGINEERING LEDGER (`style_5.jpg`)
    // =========================================================================
    private fun compositeStyle5(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // 1. Wipe 100% of the old orange book and old paperclip
        eraseCoonsSmooth(working, 0.072f, 0.052f, 0.340f, 0.334f)
        eraseCoonsSmooth(working, 0.108f, 0.035f, 0.178f, 0.055f)
        // 2. Redraw top card border line across x = 0.105f..0.180f at y = 0.0462f * h
        c.drawLine(0.105f * w, 0.0462f * h, 0.180f * w, 0.0462f * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(69, 65, 58); strokeWidth = (0.0018f * w).coerceAtLeast(2f)
        })
        // 3. Draw coverBitmap at [0.084f, 0.065f, 0.330f, 0.324f]
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.084f * w, 0.065f * h, 0.330f * w, 0.324f * h), 0.006f * w)
        }
        // 4. Call pure vector 3D copper paperclip repositioned to top-center of cover cx = 0.207f * w
        draw3DCopperPaperclip(c, cx = 0.207f * w, topY = 0.038f * h, clipW = 0.034f * w, clipH = 0.066f * h, tiltDeg = -4f)

        // 3. Hero Text & Native Left-to-Right Stars
        // Single unified pass erasing full header zone [0.370, 0.040, 0.908, 0.332]
        eraseHeaderFromCleanTopAndRight(working, 0.370f, 0.040f, 0.908f, 0.332f)

        // Redraw faint drafting grid lines over erased box on Card 5 (Blueprint):
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = 0x163A6B88
        }
        val step = 0.025f * w
        var gx = ((0.370f * w) / step).toInt() * step
        while (gx <= 0.908f * w) {
            c.drawLine(gx, 0.040f * h, gx, 0.332f * h, gridPaint)
            gx += step
        }
        var gy = ((0.040f * h) / step).toInt() * step
        while (gy <= 0.332f * h) {
            c.drawLine(0.370f * w, gy, 0.908f * w, gy, gridPaint)
            gy += step
        }

        drawPlateText(c, w, h, "I'VE READ", 0.388f, 0.075f, 0.016f, Color.rgb(54, 76, 98), Typeface.create(Typeface.SERIF, Typeface.ITALIC), letterSpacingEm = 0.04f)
        val bottomY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.388f, 0.110f, 0.510f, 0.031f, Color.rgb(20, 32, 46), Typeface.DEFAULT_BOLD, maxLines = 2)
        drawPlateText(c, w, h, stats.authorName, 0.388f, (bottomY + 0.024f).coerceAtMost(0.205f), 0.024f, Color.rgb(140, 65, 30), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.388f, 0.242f, 0.018f, Color.rgb(26, 40, 56), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "Gave it", 0.388f, 0.294f, 0.019f, Color.rgb(54, 76, 98), Typeface.create(Typeface.SERIF, Typeface.ITALIC))

        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.551f, 0.605f, 0.659f, 0.713f, 0.767f),
            centerYNorm = 0.292f,
            outerRadiusNormW = 0.018f,
            litFillColor = Color.rgb(198, 154, 68),
            unlitFillColor = null,
            unlitStrokeColor = Color.rgb(42, 56, 72)
        )

        // 4. Blueprint Metric Cards & MEZURA Footer/Seal
        eraseDarkInkFast(working, 0.076f, 0.395f, 0.305f, 0.478f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.086f, 0.436f, 0.060f, Color.rgb(22, 35, 52), Typeface.DEFAULT_BOLD)

        eraseDarkInkFast(working, 0.520f, 0.375f, 0.920f, 0.478f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.542f, 0.438f, 0.038f, Color.rgb(158, 71, 30), Typeface.DEFAULT_BOLD)

        eraseDarkInkFast(working, 0.076f, 0.585f, 0.238f, 0.692f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.086f, 0.632f, 0.060f, Color.rgb(22, 35, 52), Typeface.DEFAULT_BOLD)

        eraseDarkInkFast(working, 0.532f, 0.585f, 0.758f, 0.685f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.545f, 0.628f, 0.060f, Color.rgb(22, 35, 52), Typeface.DEFAULT_BOLD)

        eraseDarkInkFast(working, 0.076f, 0.772f, 0.325f, 0.870f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.086f, 0.820f, 0.060f, Color.rgb(22, 35, 52), Typeface.DEFAULT_BOLD)

        eraseDarkInkFast(working, 0.742f, 0.775f, 0.918f, 0.840f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.832f, 0.808f, 0.034f, Color.rgb(22, 35, 52), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.425f, 0.946f, 0.565f, 0.985f)
        drawPlateText(c, w, h, "Mezura", 0.495f, 0.966f, 0.024f, Color.rgb(58, 67, 78), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.605f, 0.910f, 0.725f, 0.938f)
        drawPlateText(c, w, h, "MEZURA", 0.665f, 0.925f, 0.010f, Color.rgb(175, 164, 148), Typeface.create(Typeface.SERIF, Typeface.BOLD), Paint.Align.CENTER, 0.14f)
    }

    // =========================================================================
    // 6. SMOKED FROSTED GLASSMORPHISM & AMBER BOKEH (`style_6.jpg`)
    // =========================================================================
    private fun compositeStyle6(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // Pre-fill [0.078f, 0.058f, 0.335f, 0.330f] with dark smoked glass to eliminate thin orange line
        c.drawRoundRect(
            RectF(0.078f * w, 0.058f * h, 0.335f * w, 0.330f * h),
            0.010f * w, 0.010f * w,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 32, 36) }
        )
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.082f * w, 0.060f * h, 0.333f * w, 0.328f * h), 0.010f * w)
        }
        // Single unified pass erasing full header zone [0.370, 0.045, 0.908, 0.332]
        eraseHeaderFromCleanTopAndRight(working, 0.370f, 0.045f, 0.908f, 0.332f)
        drawPlateText(c, w, h, "I'VE READ", 0.388f, 0.075f, 0.016f, Color.rgb(200, 204, 210), Typeface.create(Typeface.SERIF, Typeface.ITALIC), letterSpacingEm = 0.04f)
        val bottomY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.388f, 0.110f, 0.510f, 0.031f, Color.WHITE, Typeface.DEFAULT_BOLD, maxLines = 2)
        drawPlateText(c, w, h, stats.authorName, 0.388f, (bottomY + 0.024f).coerceAtMost(0.205f), 0.024f, Color.rgb(245, 155, 56), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.388f, 0.242f, 0.018f, Color.rgb(234, 236, 239), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "Gave it", 0.388f, 0.294f, 0.019f, Color.rgb(200, 204, 210), Typeface.create(Typeface.SERIF, Typeface.ITALIC))

        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.551f, 0.605f, 0.659f, 0.713f, 0.767f),
            centerYNorm = 0.292f,
            outerRadiusNormW = 0.018f,
            litFillColor = Color.rgb(255, 178, 56),
            unlitFillColor = Color.argb(40, 255, 255, 255),
            unlitStrokeColor = Color.argb(90, 255, 255, 255)
        )

        eraseCoonsSmooth(working, 0.072f, 0.392f, 0.318f, 0.482f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.086f, 0.436f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.532f, 0.392f, 0.935f, 0.478f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.545f, 0.438f, 0.038f, Color.rgb(255, 155, 40), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.072f, 0.580f, 0.245f, 0.682f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.086f, 0.632f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.532f, 0.580f, 0.758f, 0.678f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.545f, 0.628f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.072f, 0.768f, 0.325f, 0.872f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.086f, 0.820f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.742f, 0.772f, 0.925f, 0.842f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.836f, 0.808f, 0.034f, Color.rgb(255, 158, 44), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.400f, 0.938f, 0.600f, 0.985f)
        drawPlateText(c, w, h, "Mezura", 0.500f, 0.962f, 0.026f, Color.rgb(216, 220, 230), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    }

    // =========================================================================
    // 7. BOTANICAL & TOPOGRAPHICAL FIELD SURVEY ATLAS (`style_7.jpg`)
    // =========================================================================
    private fun compositeStyle7(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val serifReg = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val serifItalic = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

        // 1. Exact Specimen Frame Cover Placement [0.412, 0.148, 0.588, 0.362]
        eraseCoonsSmooth(working, 0.408f, 0.144f, 0.592f, 0.366f)
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.412f * w, 0.148f * h, 0.588f * w, 0.362f * h), 0.003f * w)
        }

        // 2. Guaranteed Zero-Double-Text Erasing via `eraseCoonsSmooth` on all 9 dynamic text boxes
        eraseCoonsSmooth(working, 0.220f, 0.056f, 0.780f, 0.136f)
        drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.240f, 0.062f, 0.520f, 0.034f, Color.rgb(23, 21, 18), serifBold, maxLines = 1, align = Layout.Alignment.ALIGN_CENTER)
        drawPlateText(c, w, h, stats.authorName, 0.500f, 0.122f, 0.023f, Color.rgb(43, 38, 32), serifReg, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.310f, 0.378f, 0.690f, 0.406f)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.500f, 0.392f, 0.0165f, Color.rgb(31, 27, 23), serifItalic, Paint.Align.CENTER)

        // Native Left-to-Right Botanical Wildflowers + erased "4/5 STARS" text underneath
        stampNativeMasterStarsLeftToRight(
            c, master, w, h, stats.starRating,
            floatArrayOf(0.155f, 0.203f, 0.251f, 0.299f, 0.347f),
            0.244f, 0.046f, 0.042f
        )
        eraseCoonsSmooth(working, 0.165f, 0.270f, 0.345f, 0.308f)
        drawPlateText(c, w, h, "${stats.starRating}/5 STARS", 0.251f, 0.288f, 0.019f, Color.rgb(28, 25, 21), serifItalic, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.242f, 0.342f, 0.358f, 0.398f)
        drawPlateText(c, w, h, "${stats.longestStreakDays} DAY", 0.298f, 0.360f, 0.0185f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)
        drawPlateText(c, w, h, "STREAK", 0.298f, 0.382f, 0.017f, Color.rgb(28, 25, 21), serifReg, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.155f, 0.418f, 0.420f, 0.452f)
        drawPlateText(c, w, h, "${stats.totalPagesRead} PAGES READ", 0.288f, 0.436f, 0.020f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.065f, 0.654f, 0.285f, 0.708f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.175f, 0.670f, 0.019f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)
        drawPlateText(c, w, h, "TOTAL TIME", 0.175f, 0.692f, 0.0165f, Color.rgb(28, 25, 21), serifReg, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.785f, 0.530f, 0.935f, 0.588f)
        drawPlateText(c, w, h, "${stats.averagePagesPerHour} PGS/H", 0.858f, 0.548f, 0.019f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)
        drawPlateText(c, w, h, "SPEED", 0.858f, 0.570f, 0.017f, Color.rgb(28, 25, 21), serifItalic, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.765f, 0.658f, 0.935f, 0.718f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.848f, 0.676f, 0.024f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)
        drawPlateText(c, w, h, "MAX PAGES/DAY", 0.848f, 0.698f, 0.0145f, Color.rgb(28, 25, 21), serifReg, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.720f, 0.815f, 0.915f, 0.872f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.815f, 0.834f, 0.024f, Color.rgb(28, 25, 21), serifBold, Paint.Align.CENTER)
        drawPlateText(c, w, h, "MAX MINS/DAY", 0.815f, 0.856f, 0.0145f, Color.rgb(28, 25, 21), serifReg, Paint.Align.CENTER)

        eraseCoonsSmooth(working, 0.340f, 0.960f, 0.660f, 0.992f)
        drawPlateText(c, w, h, "MEZURA ATLAS", 0.500f, 0.976f, 0.019f, Color.rgb(140, 125, 104), serifBold, Paint.Align.CENTER, 0.22f)
    }

    // =========================================================================
    // 8. MINIMALIST OBSIDIAN & WARM AMBER EDITORIAL POSTER (`style_8.png`)
    // =========================================================================
    private fun compositeStyle8(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // 1. Erase demo cover and dark box by interpolating vertically across x=0.045..0.450, y=0.085..0.520
        eraseVertSmooth(working, 0.045f, 0.085f, 0.450f, 0.520f)

        // 2. Place book cover in NormRect(0.054f, 0.099f, 0.437f, 0.505f) letterboxed/fitted without distortion
        val obsidianCoverRect = RectF(0.054f * w, 0.099f * h, 0.437f * w, 0.505f * h)
        drawObsidianCoverClean(c, cover, obsidianCoverRect)

        // 2. Top-Right MEZURA Header
        eraseCoonsSmooth(working, 0.780f, 0.042f, 0.935f, 0.075f)
        drawPlateText(c, w, h, "MEZURA", 0.922f, 0.060f, 0.0155f, Color.rgb(158, 132, 108), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT, 0.14f)

        // 3. Keep native "I've read" (y=0.145) & "Gave it" (y=0.478) untouched! Erase ONLY Title & Author [0.472, 0.178, 0.938, 0.440]
        eraseCoonsSmooth(working, 0.472f, 0.178f, 0.938f, 0.440f)
        val bottomY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.485f, 0.195f, 0.435f, 0.042f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD, maxLines = 2)
        drawPlateText(c, w, h, stats.authorName, 0.485f, (bottomY + 0.030f).coerceAtMost(0.365f), 0.026f, Color.rgb(224, 200, 178), Typeface.DEFAULT)

        // Restore stars next to "Gave it" (zero blur, crisp vector)
        eraseCoonsSmooth(working, 0.610f, 0.455f, 0.890f, 0.505f)
        drawCrispFiveStarsLeftToRight(
            c, w, h, stats.starRating,
            slotCentersX = floatArrayOf(0.638f, 0.692f, 0.746f, 0.800f, 0.854f),
            centerYNorm = 0.480f, outerRadiusNormW = 0.0185f,
            litFillColor = Color.rgb(228, 178, 102),
            unlitFillColor = Color.rgb(32, 20, 14),
            unlitStrokeColor = Color.rgb(138, 102, 66)
        )

        // 4. Caliper Timeline Dates
        eraseCoonsSmooth(working, 0.072f, 0.524f, 0.928f, 0.556f)
        drawPlateText(c, w, h, stats.startDateFormatted, 0.078f, 0.542f, 0.0145f, Color.rgb(200, 175, 150), Typeface.DEFAULT_BOLD, Paint.Align.LEFT, 0.08f)
        drawPlateText(c, w, h, "${stats.longestStreakDays} DAYS", 0.500f, 0.542f, 0.0145f, Color.rgb(230, 208, 186), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.12f)
        drawPlateText(c, w, h, stats.endDateFormatted, 0.922f, 0.542f, 0.0145f, Color.rgb(200, 175, 150), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT, 0.08f)

        // 5. Primary Metrics
        eraseCoonsSmooth(working, 0.072f, 0.620f, 0.430f, 0.742f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.078f, 0.685f, 0.092f, Color.rgb(242, 156, 80), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.462f, 0.640f, 0.938f, 0.742f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.478f, 0.692f, 0.046f, Color.rgb(240, 149, 69), Typeface.DEFAULT_BOLD)

        // 6. Bottom 4 Columns Aligned Exactly to Column Starts (0.078f, 0.306f, 0.518f, 0.732f)
        eraseCoonsSmooth(working, 0.072f, 0.814f, 0.282f, 0.870f)
        eraseCoonsSmooth(working, 0.296f, 0.814f, 0.494f, 0.870f)
        eraseCoonsSmooth(working, 0.508f, 0.814f, 0.706f, 0.870f)
        eraseCoonsSmooth(working, 0.720f, 0.814f, 0.932f, 0.870f)

        drawPlateText(c, w, h, "${stats.averagePagesPerHour} pages/h", 0.078f, 0.844f, 0.022f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.mostPagesInOneDay} pages", 0.306f, 0.844f, 0.022f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.mostMinutesInOneDay} min", 0.518f, 0.844f, 0.022f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        val dayUnit = if (stats.longestStreakDays == 1) "day" else "days"
        drawPlateText(c, w, h, "${stats.longestStreakDays} $dayUnit", 0.732f, 0.844f, 0.022f, Color.rgb(240, 149, 69), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.340f, 0.932f, 0.660f, 0.968f)
        drawPlateText(c, w, h, "RECORDED ON MEZURA", 0.500f, 0.948f, 0.0135f, Color.rgb(140, 114, 90), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.16f)
    }

    // =========================================================================
    // 9. ORBITAL TELEMETRY & CYAN-AMBER BLUEPRINT RADAR (`style_9.jpg`)
    // =========================================================================
    private fun compositeStyle9(c: Canvas, working: Bitmap, master: Bitmap, w: Float, h: Float, stats: BookCompletionStats, cover: Bitmap?) {
        // 1. Keep "MISSION REPORT: I'VE READ" (y = 0.055..0.078) 100% untouched! Erase ONLY Title/Author/Date [0.245, 0.080, 0.755, 0.184]
        eraseAmberInkOnly(working, 0.245f, 0.080f, 0.755f, 0.184f)
        val botY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.255f, 0.084f, 0.490f, 0.032f, Color.rgb(255, 210, 102), Typeface.DEFAULT_BOLD, maxLines = 1, align = Layout.Alignment.ALIGN_CENTER)
        drawPlateText(c, w, h, stats.authorName, 0.500f, (botY + 0.018f).coerceAtMost(0.145f), 0.022f, Color.rgb(242, 190, 84), Typeface.DEFAULT, Paint.Align.CENTER)
        drawPlateText(c, w, h, "${stats.startDateFormatted} - ${stats.endDateFormatted}", 0.500f, 0.172f, 0.0155f, Color.rgb(242, 184, 68), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.05f)

        // 2. Erase old paperclip above top book frame & draw user's book cover strictly inside TOP CYAN FRAME [0.412, 0.226, 0.588, 0.368]!
        eraseAmberInkOnly(working, 0.480f, 0.182f, 0.548f, 0.228f)
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.412f * w, 0.226f * h, 0.588f * w, 0.368f * h), 0.003f * w)
        }
        draw3DCopperPaperclip(c, cx = 0.512f * w, topY = 0.194f * h, clipW = 0.028f * w, clipH = 0.054f * h, tiltDeg = -14f)

        // 3. Selective Amber-Ink Erasing on All 6 Orbital Nodes (ZERO visible dark-blue boxes over cyan grid lines!)
        eraseAmberInkOnly(working, 0.205f, 0.330f, 0.335f, 0.375f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.270f, 0.352f, 0.032f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseAmberInkOnly(working, 0.715f, 0.258f, 0.945f, 0.298f)
        drawPlateText(c, w, h, stats.totalReadingTimeFormatted, 0.830f, 0.278f, 0.024f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseAmberInkOnly(working, 0.785f, 0.522f, 0.945f, 0.560f)
        drawPlateText(c, w, h, "${stats.averagePagesPerHour} PGS/H", 0.862f, 0.542f, 0.023f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseAmberInkOnly(working, 0.075f, 0.695f, 0.255f, 0.734f)
        drawPlateText(c, w, h, "${stats.averagePagesPerHour} PGS/H", 0.165f, 0.714f, 0.023f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseAmberInkOnly(working, 0.780f, 0.660f, 0.900f, 0.706f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.842f, 0.682f, 0.030f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        eraseAmberInkOnly(working, 0.515f, 0.705f, 0.655f, 0.758f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.585f, 0.730f, 0.030f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        // 4. Native Left-to-Right Glowing Orbs
        stampNativeMasterStarsLeftToRight(
            c, master, w, h, stats.starRating,
            floatArrayOf(0.390f, 0.445f, 0.500f, 0.555f, 0.610f),
            0.850f, 0.046f, 0.034f
        )
        // Erase only the rating digit ("4/5") at [0.435, 0.874, 0.492, 0.902]
        c.drawRect(0.432f * w, 0.874f * h, 0.568f * w, 0.902f * h, Paint().apply { color = Color.rgb(8, 38, 74) })
        drawPlateText(c, w, h, "${stats.starRating}/5 RATING", 0.500f, 0.888f, 0.0185f, Color.rgb(0, 229, 255), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.08f)

        eraseAmberInkOnly(working, 0.765f, 0.795f, 0.885f, 0.838f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.826f, 0.816f, 0.024f, Color.rgb(255, 184, 51), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        c.drawRect(0.290f * w, 0.928f * h, 0.710f * w, 0.970f * h, Paint().apply { color = Color.rgb(7, 34, 66) })
        c.drawRect(0.285f * w, 0.926f * h, 0.715f * w, 0.972f * h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.002f * h; color = Color.rgb(72, 176, 255)
        })
        drawPlateText(c, w, h, "MEZURA TELEMETRY", 0.500f, 0.949f, 0.021f, Color.rgb(102, 194, 255), Typeface.MONOSPACE, Paint.Align.CENTER, 0.14f)
    }
}


object MezuraMasterPlateEngine {
    fun renderMasterPlate(
        context: Context,
        stats: BookCompletionStats,
        style: BookStatsDesignStyle,
        widthPx: Int = 1080,
        heightPx: Int = 1440
    ): Bitmap {
        val coverBmp = stats.coverBitmap?.asAndroidBitmap()
            ?: BookStore.loadCoverBitmap(context, stats.bookId)
        return MezuraMasterPlateRenderer.renderMasterPlate(
            context = context,
            styleId = style.masterPlateId,
            stats = stats,
            coverBitmap = coverBmp
        ) ?: Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    }
}
