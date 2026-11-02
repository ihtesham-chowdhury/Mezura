package rocks.ndot.widgets.instruments.folio

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*
import kotlinx.coroutines.launch

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
    // 1. ARCHIVAL BRASS & SPLIT-FLAP LEDGER (`style_1.png`)
    // =========================================================================
    private fun compositeStyle1(
        c: Canvas,
        working: Bitmap,
        master: Bitmap,
        w: Float,
        h: Float,
        stats: BookCompletionStats,
        cover: Bitmap?
    ) {
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1. Book Cover: Fill the exact pre-cut cover slot [0.1052, 0.0984, 0.3868, 0.4094]
        //    (If using raw ref_01 with baked orange cover, heal [0.088, 0.094, 0.418, 0.422] first
        //     from clean right-column parchment [0.450, 0.094, 0.780, 0.422] before drawing cover.)
        val coverSlot = RectF(0.1052f * w, 0.0984f * h, 0.3868f * w, 0.4094f * h)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 25, 18, 10)
            maskFilter = BlurMaskFilter((0.008f * w).coerceAtLeast(4f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(
            RectF(
                coverSlot.left - 0.006f * w,
                coverSlot.top + 0.005f * h,
                coverSlot.right,
                coverSlot.bottom + 0.008f * h
            ),
            4f, 4f, shadowPaint
        )
        if (cover != null) {
            drawCenterCropBitmap(c, cover, coverSlot, 0f)
        }

        // 2. Top "BOOK COMPLETED" Header & Flanking Rules:
        //    DO NOT ERASE OR REDRAW y = 0.00..0.088! "BOOK COMPLETED", both left/right rules,
        //    and the brass stud are already printed cleanly on style_1.png.

        // 3. Right Metadata Column: Larger Title, Brass Bar, Larger Author, Dates & Stars
        //    (Zero erase calls here so no faded streaks ever appear behind MY RATING or stars!)
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val serifReg = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        val bottomTitleY = drawPlateAutoFitTitle(
            c = c, w = w, h = h,
            title = stats.bookTitle,
            xNorm = 0.452f,
            topYNorm = 0.122f,
            maxWidthNorm = 0.455f,
            initialSizeNormH = 0.038f,
            color = Color.rgb(26, 23, 20),
            typeface = serifBold,
            maxLines = 2
        )
        val ruleY = (bottomTitleY + 0.014f).coerceAtMost(0.222f)
        c.drawRect(
            0.452f * w,
            ruleY * h,
            0.536f * w,
            (ruleY + 0.0038f) * h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(156, 123, 62) }
        )
        drawPlateText(
            c, w, h,
            text = stats.authorName,
            xNorm = 0.452f,
            yNorm = ruleY + 0.036f,
            sizeNormH = 0.025f,
            color = Color.rgb(45, 38, 32),
            typeface = serifReg
        )
        drawPlateText(
            c, w, h,
            text = "${stats.startDateFormatted}  —  ${stats.endDateFormatted}",
            xNorm = 0.452f,
            yNorm = ruleY + 0.080f,
            sizeNormH = 0.0155f,
            color = Color.rgb(45, 38, 32),
            typeface = Typeface.DEFAULT_BOLD
        )

        // 5 Faceted Brass Stars below the existing "MY RATING" label (y = 0.379f)
        for (i in 0 until 5) {
            drawArchivalFacetedStar(
                canvas = c,
                cx = (0.474f + i * 0.052f) * w,
                cy = 0.379f * h,
                outerR = 0.0185f * h,
                lit = i < stats.starRating.coerceIn(0, 5)
            )
        }

        // 4. Split-Flap 3-Digit Counter:
        //    Inner black cavity on style_1.png is [0.136f..0.395f].
        //    Flaps MUST stop at 0.394f so they NEVER cover the right brass bezel [0.395f..0.428f]!
        //    DO NOT erase or redraw the open-book icon or "PAGES READ" below the counter.
        val digits = stats.totalPagesRead.coerceIn(0, 999).toString().padStart(3, '0')
        val flapL = floatArrayOf(0.139f, 0.226f, 0.313f)
        val flapR = floatArrayOf(0.221f, 0.308f, 0.394f)
        for (i in 0..2) {
            val fl = flapL[i]
            val fr = flapR[i]
            val ft = 0.482f
            val fb = 0.578f
            val fmid = (ft + fb) / 2f
            c.drawRect(fl * w, ft * h, fr * w, fmid * h, Paint().apply { color = Color.rgb(32, 29, 26) })
            c.drawRect(fl * w, fmid * h, fr * w, fb * h, Paint().apply { color = Color.rgb(24, 22, 19) })
            drawPlateText(
                c, w, h,
                text = digits[i].toString(),
                xNorm = (fl + fr) / 2f,
                yNorm = fmid,
                sizeNormH = 0.062f,
                color = Color.rgb(238, 228, 212),
                typeface = Typeface.DEFAULT_BOLD,
                align = Paint.Align.CENTER
            )
            c.drawLine(
                fl * w, fmid * h, fr * w, fmid * h,
                Paint().apply {
                    color = Color.rgb(12, 11, 10)
                    strokeWidth = (0.003f * h).coerceAtLeast(1.5f)
                }
            )
        }

        // 5. Total Reading Time: Strictly HOURS and MINUTES ONLY (NO seconds "00s"!)
        //    Starts at x = 0.652f (clean gap after the gauge which ends at 0.628f).
        //    DO NOT call eraseDarkInkFast/eraseCoonsSmooth near the gauge!
        val totalMins = stats.totalReadingMinutes.coerceAtLeast(0)
        val hrsStr = "%02d".format(totalMins / 60)
        val minsStr = "%02d".format(totalMins % 60)
        drawArchivalHoursMinutesOnly(
            canvas = c,
            startX = 0.652f * w,
            baselineY = 0.536f * h,
            hoursStr = hrsStr,
            minsStr = minsStr,
            typeface = serifBold,
            numSizePx = 0.045f * h,
            unitSizePx = 0.026f * h,
            color = Color.rgb(26, 23, 20)
        )

        // 6. Middle & Bottom Stat Numbers (33, 42, 109):
        //    DO NOT erase or redraw the static labels ("AVERAGE PAGES / HOUR" at y=0.732f..0.749f,
        //    "MOST PAGES IN ONE DAY" at y=0.736f..0.748f, "MOST MINUTES IN ONE DAY" at y=0.857f..0.882f).
        //    Draw ONLY the numbers at yNorm = 0.692f (Row 2) and yNorm = 0.820f (Row 3) so there is
        //    clean, generous vertical breathing room above the static labels!
        drawPlateText(
            c, w, h,
            text = stats.averagePagesPerHour.toString(),
            xNorm = 0.194f,
            yNorm = 0.692f,
            sizeNormH = 0.048f,
            color = Color.rgb(26, 23, 20),
            typeface = serifBold,
            align = Paint.Align.LEFT
        )
        drawPlateText(
            c, w, h,
            text = stats.mostPagesInOneDay.toString(),
            xNorm = 0.654f,
            yNorm = 0.692f,
            sizeNormH = 0.048f,
            color = Color.rgb(26, 23, 20),
            typeface = serifBold,
            align = Paint.Align.LEFT
        )
        drawPlateText(
            c, w, h,
            text = stats.mostMinutesInOneDay.toString(),
            xNorm = 0.194f,
            yNorm = 0.820f,
            sizeNormH = 0.048f,
            color = Color.rgb(26, 23, 20),
            typeface = serifBold,
            align = Paint.Align.LEFT
        )

        // 7. Longest Streak Capsule ("1 DAY"):
        //    The capsule outline, flame icon, and "LONGEST STREAK" label are already on style_1.png.
        //    Draw ONLY the streak count ("1 DAY") centered above "LONGEST STREAK" at (0.768f, 0.824f).
        val streakText = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(
            c, w, h,
            text = streakText,
            xNorm = 0.768f,
            yNorm = 0.824f,
            sizeNormH = 0.024f,
            color = Color.rgb(26, 23, 20),
            typeface = Typeface.DEFAULT_BOLD,
            align = Paint.Align.CENTER
        )

        // 8. Bottom Footer ("MEZURA · READING RECORD"):
        //    Keep the baked left fluted brass cap [0.078..0.173], left rule [0.175..0.310],
        //    right rule [0.690..0.822], and right fluted brass cap [0.824..0.918] 100% untouched!
        //    Copy pure blank parchment from BELOW the footer [0.310..0.690, 0.956..0.980]
        //    onto the center text gap [0.310..0.690, 0.924..0.948] so ZERO line passes under the text.
        val srcCleanFooter = Rect(
            (0.310f * w).toInt(),
            (0.956f * h).toInt(),
            (0.690f * w).toInt(),
            (0.980f * h).toInt()
        )
        val dstCleanFooter = RectF(0.310f * w, 0.924f * h, 0.690f * w, 0.948f * h)
        c.drawBitmap(master, srcCleanFooter, dstCleanFooter, bitmapPaint)

        drawPlateText(
            c, w, h,
            text = "MEZURA  ·  READING RECORD",
            xNorm = 0.500f,
            yNorm = 0.9362f,
            sizeNormH = 0.0125f,
            color = Color.rgb(45, 38, 30),
            typeface = serifReg,
            align = Paint.Align.CENTER,
            letterSpacingEm = 0.22f
        )
    }

    private fun drawArchivalHoursMinutesOnly(
        canvas: Canvas,
        startX: Float,
        baselineY: Float,
        hoursStr: String,
        minsStr: String,
        typeface: Typeface,
        numSizePx: Float,
        unitSizePx: Float,
        color: Int
    ) {
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = numSizePx
            this.color = color
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = unitSizePx
            this.color = color
        }
        var curX = startX
        canvas.drawText(hoursStr, curX, baselineY, numPaint)
        curX += numPaint.measureText(hoursStr) + 0.06f * numSizePx
        canvas.drawText("h", curX, baselineY, unitPaint)
        curX += unitPaint.measureText("h") + 0.24f * numSizePx
        canvas.drawText(minsStr, curX, baselineY, numPaint)
        curX += numPaint.measureText(minsStr) + 0.06f * numSizePx
        canvas.drawText("m", curX, baselineY, unitPaint)
    }

    private fun drawArchivalFacetedStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outerR: Float,
        lit: Boolean
    ) {
        val innerR = outerR * 0.42f
        val pts = Array(10) { i ->
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) outerR else innerR
            PointF((cx + r * kotlin.math.cos(angle)).toFloat(), (cy + r * kotlin.math.sin(angle)).toFloat())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (!lit) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until 10) lineTo(pts[i].x, pts[i].y)
                close()
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (outerR * 0.11f).coerceAtLeast(1.5f)
            paint.color = Color.rgb(156, 124, 64)
            canvas.drawPath(path, paint)
            return
        }
        for (i in 0 until 10) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 10]
            val facet = Path().apply {
                moveTo(cx, cy)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = if (i % 2 == 0) Color.rgb(218, 176, 88) else Color.rgb(154, 112, 42)
            canvas.drawPath(facet, paint)
        }
    }
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
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 1. Heal the top border line smudge [0.095..0.205, 0.043..0.056] from the clean border+grid to its right [0.210..0.320, 0.043..0.056]
        val srcTopBorder = Rect((0.210f * w).toInt(), (0.043f * h).toInt(), (0.320f * w).toInt(), (0.056f * h).toInt())
        val dstTopBorder = RectF(0.095f * w, 0.043f * h, 0.205f * w, 0.056f * h)
        c.drawBitmap(master, srcTopBorder, dstTopBorder, bitmapPaint)

        // 2. Heal the bottom edge strip below the cover [0.080..0.335, 0.320..0.330] from clean grid [0.080..0.335, 0.330..0.340]
        val srcBotGrid = Rect((0.080f * w).toInt(), (0.330f * h).toInt(), (0.335f * w).toInt(), (0.340f * h).toInt())
        val dstBotGrid = RectF(0.080f * w, 0.320f * h, 0.335f * w, 0.330f * h)
        c.drawBitmap(master, srcBotGrid, dstBotGrid, bitmapPaint)

        // 3. Draw the cover edge-to-edge into the exact pre-cut slot [0.0920f, 0.0556f, 0.3242f, 0.3210f] with vector copper paperclip
        if (cover != null) {
            drawCenterCropBitmap(c, cover, RectF(0.0920f * w, 0.0556f * h, 0.3242f * w, 0.3210f * h), 0.006f * w)
        }
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
    // 6. SMOKED FROSTED GLASSMORPHISM & AMBER BOKEH (`style_6.png` / `style_6.jpg`)
    // =========================================================================
    private fun compositeStyle6(
        c: Canvas,
        working: Bitmap,
        master: Bitmap,
        w: Float,
        h: Float,
        stats: BookCompletionStats,
        cover: Bitmap?
    ) {
        // 1. Unify the interior of the EXISTING top glass card [0.0547..0.9453, 0.0417..0.3475]
        //    so the cover and text sit on ONE continuous smoked-glass platform while keeping
        //    the card's original 1.5px white border [0.0519..0.9475, 0.0388..0.3504] 100% intact.
        unifyTopGlassCardInterior(working)

        // 2. Book Cover: Fill the exact pre-cut slot [0.0926f, 0.0558f, 0.3237f, 0.3208f]
        //    (px = 83, 67, 290, 385 on 896x1200) using center-crop so zero black bar shows at top/bottom.
        if (cover != null) {
            val coverDst = RectF(0.0926f * w, 0.0558f * h, 0.3237f * w, 0.3208f * h)
            drawCenterCropBitmap(c, cover, coverDst, 0.011f * w)
        }

        // 3. Header Metadata on the unified glass platform
        val serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        drawPlateText(
            c, w, h,
            text = "I’VE READ",
            xNorm = 0.388f,
            yNorm = 0.088f,
            sizeNormH = 0.022f,
            color = Color.rgb(190, 194, 202),
            typeface = serifItalic
        )

        val bottomY = drawPlateAutoFitTitle(
            c = c, w = w, h = h,
            title = stats.bookTitle,
            xNorm = 0.388f,
            topYNorm = 0.118f,
            maxWidthNorm = 0.510f,
            initialSizeNormH = 0.034f,
            color = Color.WHITE,
            typeface = Typeface.DEFAULT_BOLD,
            maxLines = 2
        )
        drawPlateText(
            c, w, h,
            text = stats.authorName,
            xNorm = 0.388f,
            yNorm = (bottomY + 0.024f).coerceAtMost(0.205f),
            sizeNormH = 0.025f,
            color = Color.rgb(245, 155, 56),
            typeface = Typeface.DEFAULT_BOLD
        )
        drawPlateText(
            c, w, h,
            text = "${stats.startDateFormatted} - ${stats.endDateFormatted}",
            xNorm = 0.388f,
            yNorm = 0.244f,
            sizeNormH = 0.0185f,
            color = Color.rgb(234, 236, 239),
            typeface = Typeface.DEFAULT_BOLD
        )

        drawPlateText(
            c, w, h,
            text = "Gave it",
            xNorm = 0.388f,
            yNorm = 0.298f,
            sizeNormH = 0.024f,
            color = Color.rgb(190, 194, 202),
            typeface = serifItalic
        )
        for (i in 0 until 5) {
            drawArchivalFacetedStar(
                canvas = c,
                cx = (0.551f + i * 0.058f) * w,
                cy = 0.298f * h,
                outerR = 0.0155f * h,
                lit = i < stats.starRating.coerceIn(0, 5)
            )
        }

        // 4. Lower 5 Bento Stat Tiles (Erase baked demo numbers first, then draw live values with NO seconds!)
        val totalMins = stats.totalReadingMinutes.coerceAtLeast(0)
        val timeNoSeconds = "%02dh %02dm".format(totalMins / 60, totalMins % 60)

        // Tile 1: PAGES READ (erases baked "208")
        eraseCoonsSmooth(working, 0.072f, 0.392f, 0.318f, 0.482f)
        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.086f, 0.436f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        // Tile 2: TOTAL READING TIME (erases baked "07h 37m 45s")
        eraseCoonsSmooth(working, 0.532f, 0.392f, 0.935f, 0.478f)
        drawPlateText(c, w, h, timeNoSeconds, 0.545f, 0.438f, 0.038f, Color.rgb(255, 155, 40), Typeface.DEFAULT_BOLD)

        // Tile 3: AVERAGE SPEED pages/h (erases baked "27")
        eraseCoonsSmooth(working, 0.072f, 0.580f, 0.245f, 0.682f)
        drawPlateText(c, w, h, stats.averagePagesPerHour.toString(), 0.086f, 0.632f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        // Tile 4: MAX PAGES/DAY (erases baked "43")
        eraseCoonsSmooth(working, 0.532f, 0.580f, 0.758f, 0.678f)
        drawPlateText(c, w, h, stats.mostPagesInOneDay.toString(), 0.545f, 0.628f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        // Tile 5: MAX MINS/DAY (erases baked "97")
        eraseCoonsSmooth(working, 0.072f, 0.768f, 0.325f, 0.872f)
        drawPlateText(c, w, h, stats.mostMinutesInOneDay.toString(), 0.086f, 0.820f, 0.060f, Color.WHITE, Typeface.DEFAULT_BOLD)

        // Tile 6: Consecutive Streak Award (erases baked "1 DAY")
        eraseCoonsSmooth(working, 0.742f, 0.772f, 0.925f, 0.842f)
        val streakLbl = "${stats.longestStreakDays} ${if (stats.longestStreakDays == 1) "DAY" else "DAYS"}"
        drawPlateText(c, w, h, streakLbl, 0.836f, 0.808f, 0.036f, Color.rgb(255, 158, 44), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        // Footer: Mezura wordmark
        eraseCoonsSmooth(working, 0.400f, 0.938f, 0.600f, 0.985f)
        drawPlateText(c, w, h, "Mezura", 0.500f, 0.962f, 0.026f, Color.rgb(216, 220, 230), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    }

    /**
     * Unifies the interior of the existing top glass card [0.055..0.945, 0.042..0.347]
     * by propagating the clean smoked-glass row color from the right side [0.88..0.92]
     * across the card interior (leaving the existing 1.5px white card border untouched).
     */
    private fun unifyTopGlassCardInterior(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val yStart = (0.042f * h).toInt().coerceIn(2, h - 4)
        val yEnd = (0.347f * h).toInt().coerceIn(yStart + 1, h - 2)
        val xLeftBase = (0.055f * w).toInt().coerceIn(2, w - 4)
        val xRightBase = (0.945f * w).toInt().coerceIn(xLeftBase + 1, w - 2)
        val cornerR = (0.027f * w).toInt().coerceAtLeast(4)
        val cyTop = yStart + cornerR
        val cyBot = yEnd - cornerR
        val sampleX1 = (0.880f * w).toInt().coerceIn(0, w - 2)
        val sampleX2 = (0.925f * w).toInt().coerceIn(sampleX1 + 1, w - 1)
        val rnd = java.util.Random(42L)

        for (y in yStart until yEnd) {
            var rSum = 0; var gSum = 0; var bSum = 0; var cnt = 0
            for (sx in sampleX1..sampleX2 step 2) {
                val p = bitmap.getPixel(sx, y)
                rSum += Color.red(p); gSum += Color.green(p); bSum += Color.blue(p)
                cnt++
            }
            val baseR = if (cnt > 0) rSum / cnt else 47
            val baseG = if (cnt > 0) gSum / cnt else 45
            val baseB = if (cnt > 0) bSum / cnt else 50

            val dxCorner = when {
                y < cyTop -> {
                    val dy = (cyTop - y).toDouble()
                    (cornerR - kotlin.math.sqrt(kotlin.math.max(0.0, (cornerR * cornerR - dy * dy)))).toInt()
                }
                y > cyBot -> {
                    val dy = (y - cyBot).toDouble()
                    (cornerR - kotlin.math.sqrt(kotlin.math.max(0.0, (cornerR * cornerR - dy * dy)))).toInt()
                }
                else -> 0
            }
            val xl = (xLeftBase + dxCorner).coerceIn(0, w - 1)
            val xr = (xRightBase - dxCorner).coerceIn(xl, w - 1)
            for (x in xl until xr) {
                val n = (rnd.nextGaussian() * 0.8).toInt()
                val cr = (baseR + n).coerceIn(0, 255)
                val cg = (baseG + n).coerceIn(0, 255)
                val cb = (baseB + n).coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.argb(255, cr, cg, cb))
            }
        }
    }
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
    // 7 (Style 8). MINIMALIST OBSIDIAN EDITORIAL (`style_8.png`, 1080 x 1440)
    // =========================================================================
    private fun compositeStyle8(
        c: Canvas,
        working: Bitmap,
        master: Bitmap,
        w: Float,
        h: Float,
        stats: BookCompletionStats,
        cover: Bitmap?
    ) {
        // 1. Heal the protruding top strip of the baked dark slot [y = 0.078f..0.117f, x = 0.075f..0.435f]
        //    using horizontal warm-espresso interpolation so zero black band appears above the cover!
        val yTop1 = (0.078f * h).toInt().coerceIn(1, working.height - 2)
        val yTop2 = (0.117f * h).toInt().coerceIn(yTop1 + 1, working.height - 1)
        val xLeft = (0.075f * w).toInt().coerceIn(2, working.width - 4)
        val xRight = (0.435f * w).toInt().coerceIn(xLeft + 1, working.width - 3)
        for (py in yTop1 until yTop2) {
            val pL = working.getPixel(xLeft - 2, py)
            val pR = working.getPixel(xRight + 2, py)
            val lR = Color.red(pL); val lG = Color.green(pL); val lB = Color.blue(pL)
            val rR = Color.red(pR); val rG = Color.green(pR); val rB = Color.blue(pR)
            val span = (xRight - xLeft).coerceAtLeast(1)
            for (px in xLeft until xRight) {
                val u = (px - xLeft).toFloat() / span.toFloat()
                val cr = ((1f - u) * lR + u * rR).toInt().coerceIn(0, 255)
                val cg = ((1f - u) * lG + u * rG).toInt().coerceIn(0, 255)
                val cb = ((1f - u) * lB + u * rB).toInt().coerceIn(0, 255)
                working.setPixel(px, py, Color.argb(255, cr, cg, cb))
            }
        }
        // Restore the continuous 1px gold header rule at y = 0.0792f across x = 0.074f..0.436f
        c.drawLine(
            0.074f * w, 0.0792f * h, 0.436f * w, 0.0792f * h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(115, 198, 152, 92)
                strokeWidth = (0.001f * h).coerceAtLeast(1.2f)
            }
        )

        // 2. Draw Book Cover edge-to-edge into the EXACT 3D book front face
        //    [left = 0.0778f, top = 0.1160f, right = 0.4324f, bottom = 0.5056f] (84, 167, 467, 728)
        //    so it sits 100% flush with the 3D cream page stack on the right and bottom!
        if (cover != null) {
            val exactCoverRect = RectF(0.0778f * w, 0.1160f * h, 0.4324f * w, 0.5056f * h)
            drawCenterCropBitmap(c, cover, exactCoverRect, 0f)
        }

        // 3. Header & Metadata (Unchanged from working v1.0.9 layout, with NO seconds in time)
        eraseCoonsSmooth(working, 0.760f, 0.045f, 0.935f, 0.075f)
        drawPlateText(c, w, h, "MEZURA", 0.922f, 0.060f, 0.016f, Color.rgb(158, 132, 108), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT, 0.14f)

        val bottomY = drawPlateAutoFitTitle(c, w, h, stats.bookTitle, 0.485f, 0.195f, 0.435f, 0.044f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD, maxLines = 2)
        drawPlateText(c, w, h, stats.authorName, 0.485f, (bottomY + 0.032f).coerceAtMost(0.365f), 0.028f, Color.rgb(224, 200, 178), Typeface.DEFAULT)

        for (i in 0 until 5) {
            drawArchivalFacetedStar(
                canvas = c,
                cx = (0.642f + i * 0.054f) * w,
                cy = 0.480f * h,
                outerR = 0.0145f * h,
                lit = i < stats.starRating.coerceIn(0, 5)
            )
        }

        drawPlateText(c, w, h, stats.startDateFormatted, 0.078f, 0.542f, 0.015f, Color.rgb(200, 175, 150), Typeface.DEFAULT_BOLD, Paint.Align.LEFT, 0.08f)
        drawPlateText(c, w, h, "${stats.longestStreakDays} DAYS", 0.500f, 0.542f, 0.015f, Color.rgb(230, 208, 186), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.12f)
        drawPlateText(c, w, h, stats.endDateFormatted, 0.922f, 0.542f, 0.015f, Color.rgb(200, 175, 150), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT, 0.08f)

        val totalMins = stats.totalReadingMinutes.coerceAtLeast(0)
        val timeNoSeconds = "%02dh %02dm".format(totalMins / 60, totalMins % 60)

        drawPlateText(c, w, h, stats.totalPagesRead.toString(), 0.078f, 0.685f, 0.095f, Color.rgb(242, 156, 80), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, timeNoSeconds, 0.478f, 0.692f, 0.048f, Color.rgb(240, 149, 69), Typeface.DEFAULT_BOLD)

        drawPlateText(c, w, h, "${stats.averagePagesPerHour} pages/h", 0.078f, 0.848f, 0.024f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.mostPagesInOneDay} pages", 0.306f, 0.848f, 0.024f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        drawPlateText(c, w, h, "${stats.mostMinutesInOneDay} min", 0.518f, 0.848f, 0.024f, Color.rgb(255, 248, 240), Typeface.DEFAULT_BOLD)
        val dayUnit = if (stats.longestStreakDays == 1) "day" else "days"
        drawPlateText(c, w, h, "${stats.longestStreakDays} $dayUnit", 0.730f, 0.848f, 0.024f, Color.rgb(240, 149, 69), Typeface.DEFAULT_BOLD)

        eraseCoonsSmooth(working, 0.348f, 0.932f, 0.652f, 0.968f)
        drawPlateText(c, w, h, "RECORDED ON MEZURA", 0.500f, 0.948f, 0.014f, Color.rgb(140, 114, 90), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, 0.16f)
    }
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
    private val memoryCache = object : android.util.LruCache<String, Bitmap>(14) {}

    fun getCached(key: String): Bitmap? = synchronized(memoryCache) { memoryCache.get(key) }

    fun putCache(key: String, bmp: Bitmap) {
        synchronized(memoryCache) { memoryCache.put(key, bmp) }
    }

    fun makeCacheKey(stats: BookCompletionStats, styleId: Int): String {
        return "${stats.bookId}_${styleId}_${stats.hashCode()}"
    }

    fun renderMasterPlate(
        context: Context,
        stats: BookCompletionStats,
        style: BookStatsDesignStyle,
        widthPx: Int = 1080,
        heightPx: Int = 1440
    ): Bitmap {
        val key = makeCacheKey(stats, style.masterPlateId)
        getCached(key)?.let { return it }

        val coverBmp = stats.coverBitmap?.asAndroidBitmap()
            ?: BookStore.loadCoverBitmap(context, stats.bookId)
        val rendered = MezuraMasterPlateRenderer.renderMasterPlate(
            context = context,
            styleId = style.masterPlateId,
            stats = stats,
            coverBitmap = coverBmp
        ) ?: Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)

        putCache(key, rendered)
        return rendered
    }

    fun prefetchAsync(context: Context, stats: BookCompletionStats) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            BookStatsDesignStyle.entries.forEach { st ->
                val key = makeCacheKey(stats, st.masterPlateId)
                if (getCached(key) == null) {
                    renderMasterPlate(context, stats, st)
                }
            }
        }
    }
}
