package rocks.ndot.widgets.instruments.folio

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MezuraBookStatsCardRenderer(
    stats: BookCompletionStats,
    style: BookStatsDesignStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var masterBitmap by remember(stats, style) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(stats, style) {
        withContext(Dispatchers.Default) {
            val bmp = MezuraMasterPlateEngine.renderMasterPlate(context, stats, style)
            withContext(Dispatchers.Main) {
                masterBitmap = bmp
            }
        }
    }

    val bmp = masterBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = style.displayTitle,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF14161C)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "RENDERING ${style.displayTitle.uppercase()}...",
                style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono)
            )
        }
    }
}

/**
 * Universal DrawScope extension used by both Compose UI live preview and 1080x1440 PNG bitmap exporter.
 */
fun DrawScope.drawSelectedMezuraStyle(
    stats: BookCompletionStats,
    style: BookStatsDesignStyle,
    s: Float
) {
    when (style) {
        BookStatsDesignStyle.STYLE_1_ARCHIVAL_SPLIT_FLAP -> drawStyle1ArchivalSplitFlap(stats, s)
        BookStatsDesignStyle.STYLE_2_PERFORATED_LED -> drawStyle2PerforatedLed(stats, s)
        BookStatsDesignStyle.STYLE_3_PARCHMENT_CLIPBOARD -> drawStyle3ParchmentClipboard(stats, s)
        BookStatsDesignStyle.STYLE_4_CELESTIAL_ATLAS -> drawStyle4CelestialAtlas(stats, s)
        BookStatsDesignStyle.STYLE_5_DRAFTING_BLUEPRINT -> drawStyle5DraftingBlueprint(stats, s)
        BookStatsDesignStyle.STYLE_6_FROSTED_GLASS -> drawStyle6FrostedGlass(stats, s)
        BookStatsDesignStyle.STYLE_7_OBSIDIAN_EDITORIAL -> drawStyle8ObsidianEditorial(stats, s)
    }
}

/* ============================================================================
 * SHARED DRAWING PRIMITIVES (SCREWS, STARS, COVERS, SEALS, DOT-MATRIX LED)
 * ============================================================================ */

private fun DrawScope.drawBrassScrew(cx: Float, cy: Float, r: Float, angleDeg: Float = 35f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE0C482), Color(0xFF9C7B3C), Color(0xFF4F3A16)),
            center = Offset(cx - r * 0.25f, cy - r * 0.25f),
            radius = r * 1.3f
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    rotate(angleDeg, pivot = Offset(cx, cy)) {
        drawLine(
            color = Color(0xFF2E200B),
            start = Offset(cx - r * 0.7f, cy),
            end = Offset(cx + r * 0.7f, cy),
            strokeWidth = r * 0.32f
        )
    }
}

private fun DrawScope.drawHexScrew(cx: Float, cy: Float, r: Float) {
    drawCircle(color = Color(0xFF0B0C0E), radius = r * 1.15f, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF4E535C), Color(0xFF23262B)),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(color = Color(0xFF141619), radius = r * 0.48f, center = Offset(cx, cy))
}

private fun DrawScope.drawBookCoverWithSpine(
    stats: BookCompletionStats,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    s: Float,
    drawPageBlockEdges: Boolean = false
) {
    // Drop shadow
    drawRoundRect(
        color = Color(0x55000000),
        topLeft = Offset(x - 3f * s, y + 5f * s),
        size = Size(w, h),
        cornerRadius = CornerRadius(4f * s, 4f * s)
    )
    if (drawPageBlockEdges) {
        drawRoundRect(
            color = Color(0xFFE8DFD1),
            topLeft = Offset(x + 2.5f * s, y + 2.5f * s),
            size = Size(w, h),
            cornerRadius = CornerRadius(3f * s, 3f * s)
        )
    }
    val bmp = stats.coverBitmap
    if (bmp != null) {
        drawImage(
            image = bmp,
            dstOffset = IntOffset(x.toInt(), y.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt())
        )
    } else {
        // Fallback burnt-orange hardcover
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFEB6B24), Color(0xFFC94414), Color(0xFF4A1208)),
                startY = y,
                endY = y + h
            ),
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = CornerRadius(3f * s, 3f * s)
        )
        val shortTitle = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
        drawNativeText(
            text = shortTitle,
            x = x + 8f * s,
            y = y + 28f * s,
            sizePx = 11f * s,
            color = android.graphics.Color.parseColor("#F5EBD6"),
            isBold = true
        )
    }
    // Hardcover spine groove highlight on left
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x55000000),
                Color(0x33FFFFFF),
                Color(0x44000000),
                Color.Transparent
            ),
            startX = x,
            endX = x + 10f * s
        ),
        topLeft = Offset(x, y),
        size = Size(10f * s, h)
    )
}

fun createStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = -PI / 2.0 + i * (PI / 5.0)
        val px = cx + (r * cos(angle)).toFloat()
        val py = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}

fun DrawScope.drawFacetedBrassStar(
    cx: Float,
    cy: Float,
    outerR: Float,
    innerR: Float = outerR * 0.44f,
    isFilled: Boolean = true,
    showGlow: Boolean = true
) {
    val tips = FloatArray(10)
    val valleys = FloatArray(10)
    for (k in 0 until 5) {
        val tipAngle = -PI / 2.0 + k * (2.0 * PI / 5.0)
        tips[k * 2] = cx + (outerR * cos(tipAngle)).toFloat()
        tips[k * 2 + 1] = cy + (outerR * sin(tipAngle)).toFloat()

        val valleyAngle = tipAngle + (PI / 5.0)
        valleys[k * 2] = cx + (innerR * cos(valleyAngle)).toFloat()
        valleys[k * 2 + 1] = cy + (innerR * sin(valleyAngle)).toFloat()
    }

    if (isFilled) {
        if (showGlow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x60FFAA20), Color(0x20FF8800), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = outerR * 1.55f
                ),
                radius = outerR * 1.55f,
                center = Offset(cx, cy)
            )
        }
        drawCircle(
            color = Color(0x40000000),
            radius = outerR * 0.95f,
            center = Offset(cx, cy + outerR * 0.12f)
        )

        for (k in 0 until 5) {
            val tx = tips[k * 2]
            val ty = tips[k * 2 + 1]
            val prevK = (k + 4) % 5
            val vxPrev = valleys[prevK * 2]
            val vyPrev = valleys[prevK * 2 + 1]
            val vxNext = valleys[k * 2]
            val vyNext = valleys[k * 2 + 1]

            val leftFacet = Path().apply {
                moveTo(cx, cy)
                lineTo(vxPrev, vyPrev)
                lineTo(tx, ty)
                close()
            }
            val rightFacet = Path().apply {
                moveTo(cx, cy)
                lineTo(tx, ty)
                lineTo(vxNext, vyNext)
                close()
            }

            val lightIntensity = when (k) {
                0 -> 1.0f
                4 -> 0.95f
                1 -> 0.85f
                3 -> 0.75f
                else -> 0.65f
            }

            val litTop = Color(1.0f, 0.96f * lightIntensity + 0.04f, 0.80f * lightIntensity, 1.0f)
            val litMid = Color((0.95f * lightIntensity).coerceIn(0.7f, 1.0f), (0.78f * lightIntensity).coerceIn(0.55f, 0.85f), (0.35f * lightIntensity).coerceIn(0.2f, 0.45f), 1.0f)
            val litBase = Color((0.75f * lightIntensity).coerceIn(0.5f, 0.8f), (0.55f * lightIntensity).coerceIn(0.35f, 0.65f), (0.18f * lightIntensity).coerceIn(0.1f, 0.25f), 1.0f)

            drawPath(
                path = leftFacet,
                brush = Brush.linearGradient(
                    colors = listOf(litTop, litMid, litBase),
                    start = Offset(cx, cy),
                    end = Offset((vxPrev + tx) / 2f, (vyPrev + ty) / 2f)
                )
            )

            val shadowTop = Color(0xFF9E7224)
            val shadowMid = Color(0xFF6B4B14)
            val shadowBase = Color(0xFF452E0A)
            drawPath(
                path = rightFacet,
                brush = Brush.linearGradient(
                    colors = listOf(shadowTop, shadowMid, shadowBase),
                    start = Offset(cx, cy),
                    end = Offset((vxNext + tx) / 2f, (vyNext + ty) / 2f)
                )
            )

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0x99FFF0BA), Color(0x22C99B4E)),
                    start = Offset(cx, cy),
                    end = Offset(tx, ty)
                ),
                start = Offset(cx, cy),
                end = Offset(tx, ty),
                strokeWidth = maxOf(1f, outerR * 0.05f)
            )
        }

        val starOutline = createStarPath(cx, cy, outerR, innerR)
        drawPath(
            path = starOutline,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xCCFFF2C2), Color(0x669E712B)),
                center = Offset(cx - outerR * 0.2f, cy - outerR * 0.3f),
                radius = outerR * 1.2f
            ),
            style = Stroke(width = maxOf(1f, outerR * 0.045f))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF7D179), Color(0xFF8A5F19)),
                center = Offset(cx - outerR * 0.03f, cy - outerR * 0.03f),
                radius = outerR * 0.12f
            ),
            radius = outerR * 0.09f,
            center = Offset(cx, cy)
        )
    } else {
        drawCircle(
            color = Color(0xFF131518),
            radius = outerR * 1.08f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF383C45), Color(0xFF181A1D)),
                start = Offset(cx - outerR, cy - outerR),
                end = Offset(cx + outerR, cy + outerR)
            ),
            radius = outerR * 1.08f,
            center = Offset(cx, cy),
            style = Stroke(width = maxOf(1f, outerR * 0.07f))
        )

        for (k in 0 until 5) {
            val tx = tips[k * 2]
            val ty = tips[k * 2 + 1]
            val prevK = (k + 4) % 5
            val vxPrev = valleys[prevK * 2]
            val vyPrev = valleys[prevK * 2 + 1]
            val vxNext = valleys[k * 2]
            val vyNext = valleys[k * 2 + 1]

            val leftFacet = Path().apply {
                moveTo(cx, cy)
                lineTo(vxPrev, vyPrev)
                lineTo(tx, ty)
                close()
            }
            val rightFacet = Path().apply {
                moveTo(cx, cy)
                lineTo(tx, ty)
                lineTo(vxNext, vyNext)
                close()
            }

            drawPath(path = leftFacet, color = Color(0xFF22252B))
            drawPath(path = rightFacet, color = Color(0xFF17191D))
            drawLine(
                color = Color(0xFF2E323A),
                start = Offset(cx, cy),
                end = Offset(tx, ty),
                strokeWidth = maxOf(1f, outerR * 0.035f)
            )
        }

        val starOutline = createStarPath(cx, cy, outerR, innerR)
        drawPath(
            path = starOutline,
            color = Color(0xFF323640),
            style = Stroke(width = maxOf(1f, outerR * 0.04f))
        )
    }
}

private fun DrawScope.drawStarRow(
    x: Float,
    y: Float,
    starRadius: Float,
    spacing: Float,
    rating: Int,
    filledColor: Color,
    emptyStrokeColor: Color,
    medallionBg: Color? = null,
    glowColor: Color? = null
) {
    for (i in 0 until 5) {
        val cx = x + starRadius + i * (starRadius * 2f + spacing)
        val cy = y
        val isLit = i < rating
        if (medallionBg != null && isLit) {
            drawCircle(color = medallionBg, radius = starRadius * 1.15f, center = Offset(cx, cy))
            drawCircle(
                color = filledColor,
                radius = starRadius * 1.15f,
                center = Offset(cx, cy),
                style = Stroke(width = starRadius * 0.15f)
            )
        }
        drawFacetedBrassStar(
            cx = cx,
            cy = cy,
            outerR = starRadius,
            innerR = starRadius * 0.44f,
            isFilled = isLit,
            showGlow = glowColor != null
        )
    }
}

private fun DrawScope.drawNativeText(
    text: String,
    x: Float,
    y: Float,
    sizePx: Float,
    color: Int,
    isBold: Boolean = false,
    isSerif: Boolean = false,
    isItalic: Boolean = false,
    isMono: Boolean = false,
    align: Paint.Align = Paint.Align.LEFT,
    letterSpacingEm: Float = 0f,
    shadowRadius: Float = 0f,
    shadowColor: Int = android.graphics.Color.TRANSPARENT
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sizePx
            this.textAlign = align
            this.letterSpacing = letterSpacingEm
            val family = when {
                isSerif -> Typeface.SERIF
                isMono -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
            val style = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            this.typeface = Typeface.create(family, style)
            if (shadowRadius > 0f) {
                setShadowLayer(shadowRadius, 0f, 0f, shadowColor)
            }
        }
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}

private fun DrawScope.drawEmbossedMezuraLibrarySeal(
    cx: Float,
    cy: Float,
    r: Float,
    s: Float,
    tintColor: Color
) {
    drawCircle(
        color = tintColor,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 1.4f * s)
    )
    drawCircle(
        color = tintColor,
        radius = r * 0.82f,
        center = Offset(cx, cy),
        style = Stroke(width = 0.8f * s)
    )
    for (deg in 0 until 360 step 6) {
        val rad = deg * (PI / 180.0)
        val x1 = cx + (r * 0.88f * cos(rad)).toFloat()
        val y1 = cy + (r * 0.88f * sin(rad)).toFloat()
        val x2 = cx + (r * 0.96f * cos(rad)).toFloat()
        val y2 = cy + (r * 0.96f * sin(rad)).toFloat()
        drawLine(tintColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 0.7f * s)
    }
    val argb = tintColor.toArgb()
    drawNativeText("MEZURA", cx, cy - 8f * s, 7.5f * s, argb, isBold = true, isSerif = true, align = Paint.Align.CENTER, letterSpacingEm = 0.18f)
    drawNativeText("LIBRARY", cx, cy + 2f * s, 7.5f * s, argb, isBold = true, isSerif = true, align = Paint.Align.CENTER, letterSpacingEm = 0.18f)
    drawNativeText("LEDGER", cx, cy + 12f * s, 7.5f * s, argb, isBold = true, isSerif = true, align = Paint.Align.CENTER, letterSpacingEm = 0.18f)
}

/* ============================================================================
 * 1ST DESIGN: ARCHIVAL BRASS & SPLIT-FLAP LEDGER
 * ============================================================================ */
private fun DrawScope.drawStyle1ArchivalSplitFlap(stats: BookCompletionStats, s: Float) {
    // 1. Archival warm rag paper background
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFEEE7D7), Color(0xFFE5DEC9), Color(0xFFDFD6C0))
        )
    )
    drawRoundRect(
        color = Color(0xFFB7AA92),
        topLeft = Offset(10f * s, 10f * s),
        size = Size(340f * s, 460f * s),
        cornerRadius = CornerRadius(6f * s, 6f * s),
        style = Stroke(width = 1f * s)
    )
    drawBrassScrew(16f * s, 16f * s, 4.2f * s)
    drawBrassScrew(344f * s, 16f * s, 4.2f * s)
    drawBrassScrew(16f * s, 464f * s, 4.2f * s)
    drawBrassScrew(344f * s, 464f * s, 4.2f * s)

    // 2. Top Header
    drawLine(Color(0xFF7D705B), Offset(32f * s, 26f * s), Offset(114f * s, 26f * s), 0.8f * s)
    drawLine(Color(0xFF7D705B), Offset(246f * s, 26f * s), Offset(328f * s, 26f * s), 0.8f * s)
    drawNativeText(
        text = "BOOK COMPLETED",
        x = 180f * s,
        y = 29f * s,
        sizePx = 9.5f * s,
        color = 0xFF332B21.toInt(),
        isSerif = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.32f
    )
    drawBrassScrew(180f * s, 37f * s, 2.5f * s)

    // 3. Hero Book & Metadata
    drawBookCoverWithSpine(stats, 38f * s, 48f * s, 102f * s, 148f * s, s)
    val titleDisplay = if (stats.bookTitle.length > 22) stats.bookTitle.take(20) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 154f * s, 80f * s, 20f * s, 0xFF1A1714.toInt(), isBold = true, isSerif = true)
    drawRect(Color(0xFF9C7B3E), Offset(154f * s, 94f * s), Size(32f * s, 2.2f * s))
    drawNativeText(stats.authorName, 154f * s, 116f * s, 14f * s, 0xFF332C23.toInt(), isSerif = true)
    drawNativeText(
        "${stats.startDateFormatted}  —  ${stats.endDateFormatted}",
        154f * s,
        138f * s,
        9.5f * s,
        0xFF3D352B.toInt(),
        letterSpacingEm = 0.06f
    )
    drawNativeText("MY RATING", 154f * s, 162f * s, 7.5f * s, 0xFF4A4135.toInt(), letterSpacingEm = 0.28f)
    drawStarRow(
        x = 154f * s,
        y = 176f * s,
        starRadius = 7.5f * s,
        spacing = 5f * s,
        rating = stats.starRating,
        filledColor = Color(0xFFB5893D),
        emptyStrokeColor = Color(0xFF8C6E3A)
    )

    // Dividers
    drawLine(Color(0xFFC5B9A2), Offset(28f * s, 214f * s), Offset(332f * s, 214f * s), 0.9f * s)
    drawLine(Color(0xFFC5B9A2), Offset(175f * s, 224f * s), Offset(175f * s, 298f * s), 0.9f * s)

    // 4. Row 1 Left: 3-Digit Split-Flap Counter
    val flapX = 38f * s
    val flapY = 226f * s
    val flapW = 116f * s
    val flapH = 54f * s
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFFD1B16E), Color(0xFF8A6B32), Color(0xFF5E461C)),
            start = Offset(flapX, flapY),
            end = Offset(flapX + flapW, flapY + flapH)
        ),
        topLeft = Offset(flapX, flapY),
        size = Size(flapW, flapH),
        cornerRadius = CornerRadius(5f * s, 5f * s)
    )
    drawRoundRect(
        color = Color(0xFF141311),
        topLeft = Offset(flapX + 5f * s, flapY + 5f * s),
        size = Size(flapW - 10f * s, flapH - 10f * s),
        cornerRadius = CornerRadius(3f * s, 3f * s)
    )
    val digits = stats.pagesRead.toString().padStart(3, '0').takeLast(3)
    digits.forEachIndexed { idx, ch ->
        val dx = flapX + (8f + idx * 34f) * s
        val dy = flapY + 7f * s
        val dw = 31f * s
        val dh = 40f * s
        drawRoundRect(Color(0xFF24221F), Offset(dx, dy), Size(dw, dh), CornerRadius(2.5f * s, 2.5f * s))
        drawNativeText(
            text = ch.toString(),
            x = dx + dw / 2f,
            y = dy + dh * 0.76f,
            sizePx = 28f * s,
            color = 0xFFEAE0CE.toInt(),
            isBold = true,
            align = Paint.Align.CENTER
        )
        drawLine(Color(0xFF0D0C0A), Offset(dx, dy + dh / 2f), Offset(dx + dw, dy + dh / 2f), 1.5f * s)
    }
    drawNativeText("PAGES READ", 96f * s, 295f * s, 8f * s, 0xFF3D352B.toInt(), align = Paint.Align.CENTER, letterSpacingEm = 0.25f)

    // Row 1 Right: Total Reading Time
    drawArc(
        color = Color(0xFF8C6B32),
        startAngle = 145f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(186f * s, 236f * s),
        size = Size(36f * s, 36f * s),
        style = Stroke(width = 3.5f * s, cap = StrokeCap.Round)
    )
    drawNativeText(stats.formattedTimeLower, 230f * s, 258f * s, 18f * s, 0xFF1C1915.toInt(), isBold = true, isSerif = true)
    drawNativeText("TOTAL READING TIME", 230f * s, 274f * s, 7f * s, 0xFF3D352B.toInt(), letterSpacingEm = 0.18f)

    // Row 2: Average Pages/Hour & Most Pages/Day
    drawLine(Color(0xFFC5B9A2), Offset(28f * s, 308f * s), Offset(332f * s, 308f * s), 0.9f * s)
    drawLine(Color(0xFFC5B9A2), Offset(175f * s, 316f * s), Offset(175f * s, 366f * s), 0.9f * s)

    drawNativeText("${stats.averagePagesPerHour}", 70f * s, 344f * s, 24f * s, 0xFF1C1915.toInt(), isBold = true, isSerif = true)
    drawNativeText("AVERAGE PAGES / HOUR", 70f * s, 358f * s, 7f * s, 0xFF3D352B.toInt(), letterSpacingEm = 0.18f)

    drawNativeText("${stats.maxPagesInOneDay}", 232f * s, 344f * s, 24f * s, 0xFF1C1915.toInt(), isBold = true, isSerif = true)
    drawNativeText("MOST PAGES IN ONE DAY", 232f * s, 358f * s, 7f * s, 0xFF3D352B.toInt(), letterSpacingEm = 0.18f)

    // Row 3: Most Minutes/Day & Longest Streak Pill
    drawLine(Color(0xFFC5B9A2), Offset(28f * s, 374f * s), Offset(332f * s, 374f * s), 0.9f * s)
    drawLine(Color(0xFFC5B9A2), Offset(186f * s, 382f * s), Offset(186f * s, 428f * s), 0.9f * s)

    drawNativeText("${stats.maxMinutesInOneDay}", 70f * s, 408f * s, 24f * s, 0xFF1C1915.toInt(), isBold = true, isSerif = true)
    drawNativeText("MOST MINUTES IN ONE DAY", 70f * s, 422f * s, 7f * s, 0xFF3D352B.toInt(), letterSpacingEm = 0.18f)

    drawRoundRect(
        color = Color(0xFFE3DAC6),
        topLeft = Offset(216f * s, 388f * s),
        size = Size(102f * s, 36f * s),
        cornerRadius = CornerRadius(18f * s, 18f * s)
    )
    drawRoundRect(
        color = Color(0xFF8C7248),
        topLeft = Offset(216f * s, 388f * s),
        size = Size(102f * s, 36f * s),
        cornerRadius = CornerRadius(18f * s, 18f * s),
        style = Stroke(width = 1f * s)
    )
    drawNativeText("${stats.longestStreakDays} DAY", 248f * s, 405f * s, 11.5f * s, 0xFF1C1915.toInt(), isBold = true)
    drawNativeText("LONGEST STREAK", 248f * s, 417f * s, 6f * s, 0xFF3D352B.toInt(), letterSpacingEm = 0.16f)

    // Footer — MEZURA BRANDING
    drawLine(Color(0xFF9E9078), Offset(32f * s, 440f * s), Offset(328f * s, 440f * s), 0.8f * s)
    drawNativeText(
        text = "MEZURA  ·  READING RECORD",
        x = 180f * s,
        y = 456f * s,
        sizePx = 7.5f * s,
        color = 0xFF3B3328.toInt(),
        isSerif = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.28f
    )
}

/* ============================================================================
 * 2ND DESIGN: PERFORATED GUNMETAL & DOT-MATRIX LED PINBOARD
 * ============================================================================ */
private fun DrawScope.drawStyle2PerforatedLed(stats: BookCompletionStats, s: Float) {
    drawRect(color = Color(0xFF141619))
    // Perforated pegboard holes
    val step = 4.8f * s
    var y = step
    while (y < size.height - 22f * s) {
        var x = step
        while (x < size.width) {
            drawCircle(Color(0xFF08090B), radius = 1.3f * s, center = Offset(x, y))
            x += step
        }
        y += step
    }

    // Top-left brushed steel book frame
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color(0xFF3A3E45), Color(0xFF22252A))),
        topLeft = Offset(34f * s, 24f * s),
        size = Size(111f * s, 152f * s),
        cornerRadius = CornerRadius(5f * s, 5f * s)
    )
    drawBookCoverWithSpine(stats, 43f * s, 33f * s, 93f * s, 134f * s, s)

    // Top-right bolted dark plate
    drawRect(Color(0xFF1D1F23), Offset(165f * s, 0f), Size(195f * s, 192f * s))
    drawHexScrew(174f * s, 10f * s, 3.5f * s)
    drawHexScrew(350f * s, 10f * s, 3.5f * s)
    drawHexScrew(174f * s, 182f * s, 3.5f * s)
    drawHexScrew(350f * s, 182f * s, 3.5f * s)

    drawNativeText("I'VE READ", 262f * s, 38f * s, 13f * s, 0xFFE6ECF2.toInt(), isMono = true, isBold = true, align = Paint.Align.CENTER, letterSpacingEm = 0.14f)
    val titleDisplay = if (stats.bookTitle.length > 18) stats.bookTitle.take(16) + "..." else stats.bookTitle
    drawNativeText(
        titleDisplay,
        262f * s,
        74f * s,
        21f * s,
        0xFFFFC44D.toInt(),
        isBold = true,
        align = Paint.Align.CENTER,
        shadowRadius = 14f * s,
        shadowColor = 0xFFFF6D00.toInt()
    )
    drawNativeText(stats.authorName, 262f * s, 98f * s, 14f * s, 0xFFDCE2E8.toInt(), align = Paint.Align.CENTER)
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 262f * s, 122f * s, 9f * s, 0xFFFF9E2C.toInt(), isMono = true, align = Paint.Align.CENTER)

    // Recessed Star Slot
    drawRoundRect(Color(0xFF101215), Offset(185f * s, 136f * s), Size(154f * s, 36f * s), CornerRadius(5f * s, 5f * s))
    drawStarRow(
        x = 197f * s,
        y = 154f * s,
        starRadius = 10f * s,
        spacing = 7f * s,
        rating = stats.starRating,
        filledColor = Color(0xFFFFB838),
        emptyStrokeColor = Color(0xFF08090B),
        glowColor = Color(0x88FF7B00)
    )

    // 4 Middle LED Modules
    drawLedModule(16f * s, 198f * s, 160f * s, 78f * s, "${stats.pagesRead}", 0xFFFFB838.toInt(), "PAGES READ", s)
    drawLedModule(184f * s, 198f * s, 160f * s, 78f * s, stats.formattedTimeUpper, 0xFFFF4B26.toInt(), "TOTAL TIME", s, valueSize = 14f)
    drawLedModule(16f * s, 284f * s, 160f * s, 78f * s, "${stats.maxPagesInOneDay}", 0xFFFFB838.toInt(), "MAX PAGES/DAY", s)
    drawLedModule(184f * s, 284f * s, 160f * s, 78f * s, "${stats.maxMinutesInOneDay}", 0xFFFFB838.toInt(), "MAX MINS/DAY", s)

    // Bottom Full-Width Speed + Circular LED Tachometer Module
    drawRoundRect(Color(0xFF1D1F23), Offset(16f * s, 368f * s), Size(328f * s, 88f * s), CornerRadius(6f * s, 6f * s))
    drawRoundRect(Color(0xFF0A0908), Offset(28f * s, 382f * s), Size(218f * s, 60f * s), CornerRadius(4f * s, 4f * s))
    drawNativeText("${stats.averagePagesPerHour}", 96f * s, 428f * s, 38f * s, 0xFFFFB838.toInt(), isMono = true, isBold = true, align = Paint.Align.CENTER, shadowRadius = 12f * s, shadowColor = 0xFFFF6D00.toInt())
    drawNativeText("PGS/HR", 196f * s, 408f * s, 13f * s, 0xFFF0F4F8.toInt(), isMono = true, isBold = true, align = Paint.Align.CENTER)
    drawNativeText("SPEED", 196f * s, 428f * s, 13f * s, 0xFFF0F4F8.toInt(), isMono = true, isBold = true, align = Paint.Align.CENTER)

    // Circular LED Gauge
    val gCx = 296f * s
    val gCy = 412f * s
    val gR = 34f * s
    drawCircle(Color(0xFF0A0A0C), radius = gR, center = Offset(gCx, gCy))
    drawCircle(Color(0xFF3A3E45), radius = gR, center = Offset(gCx, gCy), style = Stroke(width = 2f * s))
    for (i in 0..16) {
        val deg = 135f + i * (270f / 16f)
        val rad = deg * (PI / 180.0)
        val px = gCx + (gR * 0.78f * cos(rad)).toFloat()
        val py = gCy + (gR * 0.78f * sin(rad)).toFloat()
        drawCircle(Color(0xFFFFAE2B), radius = 1.6f * s, center = Offset(px, py))
    }
    drawLine(Color(0xFFFFAE2B), Offset(gCx + 5f * s, gCy + 5f * s), Offset(gCx - 18f * s, gCy - 18f * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)

    // Footer — MEZURA BRANDING
    drawNativeText(
        text = "RECORDED ON MEZURA",
        x = 180f * s,
        y = 472f * s,
        sizePx = 8.5f * s,
        color = 0xFF5A606A.toInt(),
        isMono = true,
        isBold = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.2f
    )
}

private fun DrawScope.drawLedModule(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    valueText: String,
    ledColor: Int,
    label: String,
    s: Float,
    valueSize: Float = 28f
) {
    drawRoundRect(Color(0xFF1D1F23), Offset(x, y), Size(w, h), CornerRadius(6f * s, 6f * s))
    drawRoundRect(Color(0xFF32363D), Offset(x, y), Size(w, h), CornerRadius(6f * s, 6f * s), style = Stroke(1f * s))
    drawHexScrew(x + 7f * s, y + 7f * s, 2.8f * s)
    drawHexScrew(x + w - 7f * s, y + 7f * s, 2.8f * s)
    drawHexScrew(x + 7f * s, y + h - 7f * s, 2.8f * s)
    drawHexScrew(x + w - 7f * s, y + h - 7f * s, 2.8f * s)

    drawRoundRect(Color(0xFF0A0908), Offset(x + 10f * s, y + 10f * s), Size(w - 20f * s, h - 32f * s), CornerRadius(3f * s, 3f * s))
    drawNativeText(
        text = valueText,
        x = x + w / 2f,
        y = y + 38f * s,
        sizePx = valueSize * s,
        color = ledColor,
        isMono = true,
        isBold = true,
        align = Paint.Align.CENTER,
        shadowRadius = 10f * s,
        shadowColor = ledColor
    )
    drawNativeText(
        text = label,
        x = x + w / 2f,
        y = y + h - 8f * s,
        sizePx = 11f * s,
        color = 0xFFDCE2E8.toInt(),
        isMono = true,
        isBold = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.08f
    )
}

/* ============================================================================
 * 3RD DESIGN: WARM PARCHMENT CLIPBOARD & CHARCOAL BRASS BENTO
 * ============================================================================ */
private fun DrawScope.drawStyle3ParchmentClipboard(stats: BookCompletionStats, s: Float) {
    drawRect(color = Color(0xFFF4EDE0))
    // Top hero card
    drawRoundRect(Color(0xFFEBE2D1), Offset(18f * s, 20f * s), Size(324f * s, 148f * s), CornerRadius(16f * s, 16f * s))
    drawBookCoverWithSpine(stats, 33f * s, 34f * s, 84f * s, 120f * s, s)

    // 3D Brass Bulldog Clip at top center of book cover
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFC5A86E), Color(0xFF7D6132))),
        topLeft = Offset(55f * s, 22f * s),
        size = Size(40f * s, 17f * s),
        cornerRadius = CornerRadius(3f * s, 3f * s)
    )
    drawCircle(Color(0xFF9E8048), radius = 8f * s, center = Offset(75f * s, 17f * s))
    drawCircle(Color(0xFFF4EDE0), radius = 3.2f * s, center = Offset(75f * s, 16f * s))

    // Hero text
    drawNativeText("I'VE READ", 140f * s, 50f * s, 13f * s, 0xFF6E685F.toInt(), isItalic = true)
    val titleDisplay = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 140f * s, 80f * s, 22f * s, 0xFF161920.toInt(), isBold = true)
    drawNativeText(stats.authorName, 140f * s, 102f * s, 14.5f * s, 0xFF252830.toInt())
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 140f * s, 122f * s, 10f * s, 0xFF2B2E36.toInt())
    drawNativeText("Gave it", 140f * s, 148f * s, 14f * s, 0xFF635D53.toInt(), isSerif = true, isItalic = true)
    drawStarRow(
        x = 192f * s,
        y = 143f * s,
        starRadius = 7.2f * s,
        spacing = 6f * s,
        rating = stats.starRating,
        filledColor = Color(0xFFD6AE60),
        emptyStrokeColor = Color(0xFFCEC3B0),
        medallionBg = Color(0xFF423118)
    )

    // Bento Grid (Charcoal Cards)
    val cardBrush = Brush.verticalGradient(listOf(Color(0xFF282A2F), Color(0xFF1F2125)))
    // Row 1
    drawRoundRect(cardBrush, Offset(18f * s, 178f * s), Size(157f * s, 80f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText("${stats.pagesRead}", 31f * s, 224f * s, 31f * s, 0xFFEBC999.toInt(), isBold = true)
    drawNativeText("PAGES READ", 31f * s, 244f * s, 9.5f * s, 0xFFC89658.toInt())

    drawRoundRect(cardBrush, Offset(185f * s, 178f * s), Size(157f * s, 80f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText(stats.formattedTimeLower, 198f * s, 222f * s, 18.5f * s, 0xFFD68A3A.toInt(), isBold = true)
    drawNativeText("TOTAL READING TIME", 198f * s, 244f * s, 9f * s, 0xFFC89658.toInt())

    // Row 2
    drawRoundRect(cardBrush, Offset(18f * s, 268f * s), Size(157f * s, 80f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText("${stats.averagePagesPerHour}", 31f * s, 318f * s, 31f * s, 0xFFEBC999.toInt(), isBold = true)
    drawArc(Color(0xFFD69A4A), 140f, 260f, false, Offset(96f * s, 282f * s), Size(54f * s, 54f * s), style = Stroke(4.5f * s, cap = StrokeCap.Round))
    drawNativeText("pages/h", 123f * s, 310f * s, 8f * s, 0xFFC89658.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Speed", 123f * s, 323f * s, 10.5f * s, 0xFFF2ECE1.toInt(), isBold = true, align = Paint.Align.CENTER)

    drawRoundRect(cardBrush, Offset(185f * s, 268f * s), Size(157f * s, 80f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText("${stats.maxPagesInOneDay}", 198f * s, 316f * s, 31f * s, 0xFFEBC999.toInt(), isBold = true)
    drawNativeText("MAX PAGES/DAY", 198f * s, 336f * s, 9.5f * s, 0xFFC89658.toInt())

    // Row 3
    drawRoundRect(cardBrush, Offset(18f * s, 358f * s), Size(233f * s, 84f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText("${stats.maxMinutesInOneDay}", 31f * s, 406f * s, 31f * s, 0xFFEBC999.toInt(), isBold = true)
    drawNativeText("MAX MINS/DAY", 31f * s, 426f * s, 9.5f * s, 0xFFC89658.toInt())
    drawArc(Color(0xFFD68A38), 180f, 180f, false, Offset(154f * s, 378f * s), Size(68f * s, 68f * s), style = Stroke(3.5f * s, cap = StrokeCap.Round))
    drawLine(Color(0xFFD68A38), Offset(188f * s, 416f * s), Offset(214f * s, 388f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)

    drawRoundRect(cardBrush, Offset(261f * s, 358f * s), Size(81f * s, 84f * s), CornerRadius(14f * s, 14f * s))
    drawNativeText("${stats.longestStreakDays} DAY", 301f * s, 396f * s, 19f * s, 0xFFD68A38.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("Consecutive", 301f * s, 414f * s, 9f * s, 0xFFE5DEC9.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Streak Award", 301f * s, 426f * s, 9f * s, 0xFFE5DEC9.toInt(), align = Paint.Align.CENTER)

    // Embossed Stamp — MEZURA LIBRARY LEDGER
    drawEmbossedMezuraLibrarySeal(248f * s, 434f * s, 38f * s, s, Color(0x669E9689))
}

/* ============================================================================
 * 4TH DESIGN: ASTROLABE & CELESTIAL STAR ATLAS
 * ============================================================================ */
private fun DrawScope.drawStyle4CelestialAtlas(stats: BookCompletionStats, s: Float) {
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color(0xFF162444), Color(0xFF0E172E), Color(0xFF091020)),
            center = Offset(180f * s, 200f * s),
            radius = 340f * s
        )
    )
    // Gold double coordinate border
    drawRect(Color(0xFFB89B5E), Offset(12f * s, 12f * s), Size(336f * s, 456f * s), style = Stroke(1.2f * s))
    drawRect(Color(0xFF8C723E), Offset(16f * s, 16f * s), Size(328f * s, 448f * s), style = Stroke(0.6f * s))

    // Concentric astrolabe rings centered on top medallion (180, 78)
    val mCx = 180f * s
    val mCy = 78f * s
    listOf(52f, 68f, 92f, 114f).forEach { r ->
        drawCircle(Color(0x66C9AC6E), radius = r * s, center = Offset(mCx, mCy), style = Stroke(0.9f * s))
    }
    drawCircle(Color(0xFFB89650), radius = 48f * s, center = Offset(mCx, mCy), style = Stroke(4f * s))
    drawBookCoverWithSpine(stats, 155f * s, 44f * s, 50f * s, 68f * s, s)

    drawNativeText("CELESTIAL RECORD: I'VE READ", mCx, 24f * s, 9.5f * s, 0xFFE8C87D.toInt(), isSerif = true, isBold = true, align = Paint.Align.CENTER, letterSpacingEm = 0.14f)
    val titleDisplay = if (stats.bookTitle.length > 22) stats.bookTitle.take(20) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, mCx, 146f * s, 20f * s, 0xFFF5DC96.toInt(), isSerif = true, isBold = true, align = Paint.Align.CENTER)
    drawNativeText(stats.authorName, mCx, 165f * s, 13f * s, 0xFFDCC07A.toInt(), isSerif = true, align = Paint.Align.CENTER)
    drawNativeText("• ${stats.startDateFormatted} - ${stats.endDateFormatted} •", mCx, 182f * s, 9f * s, 0xFFE5C67E.toInt(), align = Paint.Align.CENTER)

    // Left Astrolabe Dial (Total Time)
    val aCx = 68f * s
    val aCy = 242f * s
    drawNativeText("${stats.formattedTimeLower} TOTAL TIME", aCx + 10f * s, 180f * s, 9f * s, 0xFFF2D78C.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawCircle(Color(0xFFC4A15A), radius = 52f * s, center = Offset(aCx, aCy), style = Stroke(4f * s))
    drawCircle(Color(0xFF8C6E32), radius = 38f * s, center = Offset(aCx, aCy), style = Stroke(3f * s))
    drawCircle(Color(0xFFE8C87D), radius = 22f * s, center = Offset(aCx, aCy), style = Stroke(1.5f * s))
    drawLine(Color(0xFFE8C87D), Offset(aCx - 42f * s, aCy), Offset(aCx + 42f * s, aCy), 1.2f * s)
    drawLine(Color(0xFFE8C87D), Offset(aCx, aCy - 42f * s), Offset(aCx, aCy + 42f * s), 1.2f * s)

    // Center Golden Spiral (Pages Read)
    val spCx = 180f * s
    val spCy = 246f * s
    drawCircle(Color(0xFFFFE08C), radius = 4.5f * s, center = Offset(spCx, spCy))
    for (i in 0..120) {
        val theta = i * 0.18
        val r = (4.0 + i * 0.36) * s
        val px = spCx + (r * cos(theta)).toFloat()
        val py = spCy + (r * sin(theta)).toFloat()
        drawCircle(Color(0xFFE6C57A), radius = 1.2f * s, center = Offset(px, py))
    }
    drawNativeText("${stats.pagesRead} PAGES READ", spCx + 12f * s, 298f * s, 11f * s, 0xFFF5DC96.toInt(), isBold = true, align = Paint.Align.CENTER)

    // Right Speed Protractor & Max Pages Constellation
    drawArc(Color(0xFFC9A65C), 180f, 180f, true, Offset(234f * s, 190f * s), Size(104f * s, 104f * s), style = Stroke(3f * s))
    drawNativeText("${stats.averagePagesPerHour}", 286f * s, 218f * s, 18f * s, 0xFFF5DC96.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("PGS/H SPEED", 286f * s, 232f * s, 7.5f * s, 0xFFE5C67E.toInt(), align = Paint.Align.CENTER)
    drawLine(Color(0xFFF5DC96), Offset(286f * s, 242f * s), Offset(320f * s, 204f * s), 2f * s)

    drawNativeText("${stats.maxPagesInOneDay}", 288f * s, 298f * s, 17f * s, 0xFFF5DC96.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MAX PAGES/DAY", 288f * s, 312f * s, 8.5f * s, 0xFFE5C67E.toInt(), align = Paint.Align.CENTER)

    // Bottom-Left Crescent Moon (Max Mins/Day)
    drawArc(Color(0xFFF5DC96), 110f, 160f, false, Offset(42f * s, 326f * s), Size(76f * s, 76f * s), style = Stroke(12f * s, cap = StrokeCap.Round))
    drawNativeText("${stats.maxMinutesInOneDay}", 90f * s, 362f * s, 18f * s, 0xFFF5DC96.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MAX MINS/DAY", 90f * s, 375f * s, 8f * s, 0xFFE5C67E.toInt(), align = Paint.Align.CENTER)

    // Bottom-Center Rating Stars
    drawStarRow(
        x = 144f * s,
        y = 374f * s,
        starRadius = 6.5f * s,
        spacing = 4f * s,
        rating = stats.starRating,
        filledColor = Color(0xFFFFE291),
        emptyStrokeColor = Color(0xFFB89B5E),
        glowColor = Color(0x66FFD266)
    )
    drawNativeText("Rating", 180f * s, 394f * s, 10.5f * s, 0xFFE5C67E.toInt(), isSerif = true, align = Paint.Align.CENTER)

    // Bottom-Right Solar Streak Orbit
    drawCircle(Color(0xFFC9A65C), radius = 36f * s, center = Offset(284f * s, 378f * s), style = Stroke(1.2f * s))
    drawCircle(Color(0xFFDFB968), radius = 14f * s, center = Offset(308f * s, 352f * s))
    drawNativeText("STREAK", 308f * s, 354f * s, 5.2f * s, 0xFF0E172E.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("${stats.longestStreakDays} DAY", 284f * s, 380f * s, 15f * s, 0xFFF5DC96.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("STREAK", 284f * s, 393f * s, 9f * s, 0xFFE5C67E.toInt(), align = Paint.Align.CENTER)

    // Bottom Cartouche — MEZURA CELESTIAL ATLAS
    drawRoundRect(Color(0xFF0B1326), Offset(86f * s, 434f * s), Size(188f * s, 24f * s), CornerRadius(6f * s, 6f * s))
    drawRoundRect(Color(0xFFC9A65C), Offset(86f * s, 434f * s), Size(188f * s, 24f * s), CornerRadius(6f * s, 6f * s), style = Stroke(1.3f * s))
    drawNativeText(
        text = "MEZURA CELESTIAL ATLAS",
        x = 180f * s,
        y = 450f * s,
        sizePx = 10.5f * s,
        color = 0xFFE8C87D.toInt(),
        isSerif = true,
        isBold = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.12f
    )
}

/* ============================================================================
 * 5TH DESIGN: DRAFTING BLUEPRINT & ARCHIVAL ENGINEERING LEDGER
 * (Includes Clean Fix for Top-Right Total Reading Time Card)
 * ============================================================================ */
private fun DrawScope.drawStyle5DraftingBlueprint(stats: BookCompletionStats, s: Float) {
    drawRect(color = Color(0xFFEDE3CE))
    // Outer continuous drafting ruler border
    drawRect(Color(0xFF3E3A33), Offset(10f * s, 10f * s), Size(340f * s, 460f * s), style = Stroke(1f * s))
    for (i in 0..68) {
        val pos = (10f + i * 5f) * s
        val tickLen = if (i % 2 == 0) 4.5f * s else 2.5f * s
        if (pos <= 350f * s) {
            drawLine(Color(0xFF5A5449), Offset(pos, 10f * s), Offset(pos, 10f * s + tickLen), 0.6f * s)
            drawLine(Color(0xFF5A5449), Offset(pos, 470f * s), Offset(pos, 470f * s - tickLen), 0.6f * s)
        }
    }
    for (i in 0..92) {
        val pos = (10f + i * 5f) * s
        val tickLen = if (i % 2 == 0) 4.5f * s else 2.5f * s
        if (pos <= 470f * s) {
            drawLine(Color(0xFF5A5449), Offset(10f * s, pos), Offset(10f * s + tickLen, pos), 0.6f * s)
            drawLine(Color(0xFF5A5449), Offset(350f * s, pos), Offset(350f * s - tickLen, pos), 0.6f * s)
        }
    }

    // Top Hero Engineering Card
    drawBlueprintCard(19f * s, 22f * s, 322f * s, 146f * s, s)
    drawBookCoverWithSpine(stats, 33f * s, 35f * s, 84f * s, 120f * s, s)
    drawRoundRect(Color(0xFFB57C4A), Offset(46f * s, 28f * s), Size(8f * s, 22f * s), CornerRadius(4f * s, 4f * s), style = Stroke(1.6f * s))

    drawNativeText("I'VE READ", 140f * s, 50f * s, 13f * s, 0xFF1A2838.toInt(), isItalic = true)
    val titleDisplay = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 140f * s, 80f * s, 22f * s, 0xFF14202E.toInt(), isBold = true)
    drawNativeText(stats.authorName, 140f * s, 102f * s, 15f * s, 0xFF8C411E.toInt(), isBold = true)
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 140f * s, 122f * s, 10f * s, 0xFF1A2838.toInt())
    drawNativeText("Gave it", 140f * s, 148f * s, 14f * s, 0xFF1A2838.toInt(), isSerif = true, isItalic = true)
    drawStarRow(192f * s, 143f * s, 7.2f * s, 5f * s, stats.starRating, Color(0xFFC49A45), Color(0xFF2B3848))

    // Row 1 Left: Pages Read
    drawBlueprintCard(19f * s, 178f * s, 156f * s, 80f * s, s)
    drawNativeText("${stats.pagesRead}", 31f * s, 224f * s, 30f * s, 0xFF162334.toInt(), isBold = true)
    drawNativeText("PAGES READ", 31f * s, 244f * s, 9.5f * s, 0xFF162334.toInt())

    // Row 1 Right: Total Reading Time (CLEAN FIXED RENDERING)
    drawBlueprintCard(185f * s, 178f * s, 156f * s, 80f * s, s)
    drawCircle(Color(0x331D2C3F), radius = 24f * s, center = Offset(306f * s, 214f * s), style = Stroke(0.8f * s))
    drawNativeText(stats.formattedTimeLower, 197f * s, 222f * s, 19f * s, 0xFF9E471E.toInt(), isBold = true)
    drawNativeText("TOTAL READING TIME", 197f * s, 244f * s, 9f * s, 0xFF162334.toInt())

    // Row 2 Left: Speed Protractor
    drawBlueprintCard(19f * s, 268f * s, 156f * s, 80f * s, s)
    drawNativeText("${stats.averagePagesPerHour}", 31f * s, 318f * s, 30f * s, 0xFF162334.toInt(), isBold = true)
    drawArc(Color(0xFF1D2C3F), 140f, 260f, false, Offset(96f * s, 282f * s), Size(54f * s, 54f * s), style = Stroke(1.5f * s))
    drawNativeText("pages/h", 123f * s, 310f * s, 8f * s, 0xFF162334.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Speed", 123f * s, 323f * s, 10.5f * s, 0xFF162334.toInt(), isBold = true, align = Paint.Align.CENTER)

    // Row 2 Right: Max Pages/Day
    drawBlueprintCard(185f * s, 268f * s, 156f * s, 80f * s, s)
    drawNativeText("${stats.maxPagesInOneDay}", 197f * s, 316f * s, 30f * s, 0xFF162334.toInt(), isBold = true)
    drawNativeText("MAX PAGES/DAY", 197f * s, 336f * s, 9.5f * s, 0xFF162334.toInt())

    // Row 3 Left: Max Mins/Day
    drawBlueprintCard(19f * s, 358f * s, 232f * s, 84f * s, s)
    drawNativeText("${stats.maxMinutesInOneDay}", 31f * s, 406f * s, 30f * s, 0xFF162334.toInt(), isBold = true)
    drawNativeText("MAX MINS/DAY", 31f * s, 426f * s, 9.5f * s, 0xFF162334.toInt())
    drawArc(Color(0xFF1D2C3F), 180f, 180f, false, Offset(154f * s, 378f * s), Size(68f * s, 68f * s), style = Stroke(1.5f * s))
    drawLine(Color(0xFF1D2C3F), Offset(188f * s, 416f * s), Offset(214f * s, 388f * s), 2f * s)

    // Row 3 Right: Streak
    drawBlueprintCard(261f * s, 358f * s, 80f * s, 84f * s, s)
    drawNativeText("${stats.longestStreakDays} DAY", 301f * s, 396f * s, 19f * s, 0xFF162334.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("Consecutive", 301f * s, 414f * s, 9f * s, 0xFF162334.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Streak Award", 301f * s, 426f * s, 9f * s, 0xFF162334.toInt(), align = Paint.Align.CENTER)

    // Footer & Embossed Seal — MEZURA BRANDING
    drawNativeText("Mezura", 180f * s, 463f * s, 13f * s, 0xFF3A434E.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawEmbossedMezuraLibrarySeal(242f * s, 438f * s, 34f * s, s, Color(0x779E927E))
}

private fun DrawScope.drawBlueprintCard(x: Float, y: Float, w: Float, h: Float, s: Float) {
    drawRoundRect(Color(0xFFF7F2E7), Offset(x, y), Size(w, h), CornerRadius(7f * s, 7f * s))
    drawRoundRect(Color(0xFF45413A), Offset(x, y), Size(w, h), CornerRadius(7f * s, 7f * s), style = Stroke(1f * s))
    drawBrassScrew(x + 6f * s, y + 6f * s, 2.8f * s)
    drawBrassScrew(x + w - 6f * s, y + 6f * s, 2.8f * s)
    drawBrassScrew(x + 6f * s, y + h - 6f * s, 2.8f * s)
    drawBrassScrew(x + w - 6f * s, y + h - 6f * s, 2.8f * s)
}

/* ============================================================================
 * 6TH DESIGN: SMOKED FROSTED GLASSMORPHISM & AMBER BOKEH
 * ============================================================================ */
private fun DrawScope.drawStyle6FrostedGlass(stats: BookCompletionStats, s: Float) {
    drawRect(color = Color(0xFF0C0C0E))
    // Ambient warm amber bokeh orbs
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x88D96512), Color.Transparent), Offset(85f * s, 80f * s), 130f * s),
        radius = 130f * s,
        center = Offset(85f * s, 80f * s)
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x55B84A08), Color.Transparent), Offset(45f * s, 290f * s), 95f * s),
        radius = 95f * s,
        center = Offset(45f * s, 290f * s)
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x44C95E10), Color.Transparent), Offset(305f * s, 390f * s), 85f * s),
        radius = 85f * s,
        center = Offset(305f * s, 390f * s)
    )

    // Hero Frosted Glass Card
    drawFrostedCard(18f * s, 20f * s, 324f * s, 148f * s, s)
    drawBookCoverWithSpine(stats, 33f * s, 34f * s, 84f * s, 120f * s, s)

    drawNativeText("I'VE READ", 140f * s, 50f * s, 13f * s, 0xFFB0B3BC.toInt(), isItalic = true)
    val titleDisplay = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 140f * s, 80f * s, 22f * s, 0xFFFFFFFF.toInt(), isBold = true)
    drawNativeText(stats.authorName, 140f * s, 102f * s, 15f * s, 0xFFF59B38.toInt(), isBold = true)
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 140f * s, 122f * s, 10f * s, 0xFFEAECEF.toInt())
    drawNativeText("Gave it", 140f * s, 148f * s, 14f * s, 0xFFB8BBC4.toInt(), isSerif = true, isItalic = true)
    drawStarRow(192f * s, 143f * s, 7.2f * s, 5f * s, stats.starRating, Color(0xFFFF9F2E), Color(0xFF1C1C20), glowColor = Color(0x66FF8800))

    // Row 1
    drawFrostedCard(18f * s, 178f * s, 157f * s, 80f * s, s)
    drawNativeText("${stats.pagesRead}", 31f * s, 224f * s, 31f * s, 0xFFFFFFFF.toInt(), isBold = true)
    drawNativeText("PAGES READ", 31f * s, 244f * s, 9.5f * s, 0xFFD0D3DC.toInt())

    drawFrostedCard(185f * s, 178f * s, 157f * s, 80f * s, s)
    drawNativeText(stats.formattedTimeLower, 198f * s, 222f * s, 18.5f * s, 0xFFFF9B28.toInt(), isBold = true)
    drawNativeText("TOTAL READING TIME", 198f * s, 244f * s, 9f * s, 0xFFD0D3DC.toInt())

    // Row 2
    drawFrostedCard(18f * s, 268f * s, 157f * s, 80f * s, s)
    drawNativeText("${stats.averagePagesPerHour}", 31f * s, 318f * s, 31f * s, 0xFFFFFFFF.toInt(), isBold = true)
    drawArc(Color(0xFFFFAE33), 140f, 260f, false, Offset(96f * s, 282f * s), Size(54f * s, 54f * s), style = Stroke(4.5f * s, cap = StrokeCap.Round))
    drawNativeText("pages/h", 123f * s, 310f * s, 8f * s, 0xFFD0D3DC.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Speed", 123f * s, 323f * s, 10.5f * s, 0xFFFFFFFF.toInt(), isBold = true, align = Paint.Align.CENTER)

    drawFrostedCard(185f * s, 268f * s, 157f * s, 80f * s, s)
    drawNativeText("${stats.maxPagesInOneDay}", 198f * s, 316f * s, 31f * s, 0xFFFFFFFF.toInt(), isBold = true)
    drawNativeText("MAX PAGES/DAY", 198f * s, 336f * s, 9.5f * s, 0xFFD0D3DC.toInt())

    // Row 3
    drawFrostedCard(18f * s, 358f * s, 233f * s, 84f * s, s)
    drawNativeText("${stats.maxMinutesInOneDay}", 31f * s, 406f * s, 31f * s, 0xFFFFFFFF.toInt(), isBold = true)
    drawNativeText("MAX MINS/DAY", 31f * s, 426f * s, 9.5f * s, 0xFFD0D3DC.toInt())
    drawArc(Color(0xFFFFA830), 180f, 180f, false, Offset(154f * s, 378f * s), Size(68f * s, 68f * s), style = Stroke(3.5f * s, cap = StrokeCap.Round))
    drawLine(Color(0xFFE8ECF2), Offset(188f * s, 416f * s), Offset(214f * s, 388f * s), 3f * s, cap = StrokeCap.Round)

    drawFrostedCard(261f * s, 358f * s, 81f * s, 84f * s, s)
    drawNativeText("${stats.longestStreakDays} DAY", 301f * s, 396f * s, 19f * s, 0xFFFF9E2C.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("Consecutive", 301f * s, 414f * s, 9f * s, 0xFFE6E8EF.toInt(), align = Paint.Align.CENTER)
    drawNativeText("Streak Award", 301f * s, 426f * s, 9f * s, 0xFFE6E8EF.toInt(), align = Paint.Align.CENTER)

    // Footer — MEZURA BRANDING
    drawNativeText("Mezura", 180f * s, 464f * s, 13.5f * s, 0xFFC4C7D0.toInt(), isBold = true, align = Paint.Align.CENTER)
}

private fun DrawScope.drawFrostedCard(x: Float, y: Float, w: Float, h: Float, s: Float) {
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xCC2C2A2E), Color(0xB31E1D21)),
            start = Offset(x, y),
            end = Offset(x + w, y + h)
        ),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(15f * s, 15f * s)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0x55FFFFFF), Color(0x14FFFFFF), Color(0x33FF9E3D)),
            start = Offset(x, y),
            end = Offset(x + w, y + h)
        ),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(15f * s, 15f * s),
        style = Stroke(1f * s)
    )
}

/* ============================================================================
 * 7TH DESIGN: BOTANICAL & TOPOGRAPHICAL FIELD SURVEY ATLAS
 * ============================================================================ */
private fun DrawScope.drawStyle7FieldSurveyAtlas(stats: BookCompletionStats, s: Float) {
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color(0xFFF6EFE0), Color(0xFFEDE1C8), Color(0xFFE2D2B2))
        )
    )
    // Cartographic double border
    drawRect(Color(0xFF2B2620), Offset(14f * s, 14f * s), Size(332f * s, 444f * s), style = Stroke(1.5f * s))
    drawRect(Color(0xFF2B2620), Offset(19f * s, 19f * s), Size(322f * s, 434f * s), style = Stroke(0.8f * s))

    // Header
    val titleDisplay = if (stats.bookTitle.length > 22) stats.bookTitle.take(20) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 180f * s, 54f * s, 21f * s, 0xFF171512.toInt(), isBold = true, isSerif = true, align = Paint.Align.CENTER)
    drawNativeText(stats.authorName, 180f * s, 74f * s, 14f * s, 0xFF2B2620.toInt(), isSerif = true, align = Paint.Align.CENTER)

    // Mounted Specimen Book Box
    drawRect(Color(0xFFDCCBA8), Offset(138f * s, 84f * s), Size(84f * s, 106f * s))
    drawRect(Color(0xFF2B2620), Offset(138f * s, 84f * s), Size(84f * s, 106f * s), style = Stroke(1.2f * s))
    drawBookCoverWithSpine(stats, 143f * s, 89f * s, 74f * s, 96f * s, s)
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 180f * s, 202f * s, 9.5f * s, 0xFF1F1B17.toInt(), isItalic = true, align = Paint.Align.CENTER)

    // Left: Botanical Rating + Streak Stamp + Pocket Watch + Compass
    drawStarRow(42f * s, 122f * s, 6.5f * s, 4f * s, stats.starRating, Color(0xFF7D623C), Color(0xFF2B2620))
    drawNativeText("${stats.starRating}/5 STARS", 82f * s, 142f * s, 10f * s, 0xFF1C1915.toInt(), isBold = true, isItalic = true, align = Paint.Align.CENTER)

    drawCircle(Color(0xFF2B2620), radius = 25f * s, center = Offset(64f * s, 186f * s), style = Stroke(1f * s))
    drawCircle(Color(0xFF2B2620), radius = 18f * s, center = Offset(64f * s, 186f * s), style = Stroke(0.7f * s))
    drawNativeText("${stats.longestStreakDays} DAY", 108f * s, 180f * s, 10f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("STREAK", 108f * s, 192f * s, 10f * s, 0xFF1C1915.toInt(), align = Paint.Align.CENTER)

    // Pocket Watch (Total Time)
    drawCircle(Color(0xFF2B2620), radius = 20f * s, center = Offset(52f * s, 296f * s), style = Stroke(1.4f * s))
    drawLine(Color(0xFF2B2620), Offset(52f * s, 296f * s), Offset(62f * s, 286f * s), 1.2f * s)
    drawNativeText(stats.formattedTimeLower, 66f * s, 330f * s, 10.5f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("TOTAL TIME", 66f * s, 342f * s, 9.5f * s, 0xFF1C1915.toInt(), align = Paint.Align.CENTER)

    // Compass Rose
    drawCircle(Color(0xFF2B2620), radius = 24f * s, center = Offset(75f * s, 382f * s), style = Stroke(1f * s))
    drawNativeText("N", 75f * s, 355f * s, 7f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)

    // Centerpiece: Dendrochronology Tree Trunk Log (Pages Read)
    drawNativeText("${stats.pagesRead} PAGES READ", 122f * s, 222f * s, 11f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    val logCx = 146f * s
    val logCy = 290f * s
    drawCircle(Color(0xFF6B4D30), radius = 58f * s, center = Offset(logCx + 18f * s, logCy - 12f * s))
    drawCircle(Color(0xFFD6B485), radius = 56f * s, center = Offset(logCx, logCy))
    drawCircle(Color(0xFF3B2816), radius = 56f * s, center = Offset(logCx, logCy), style = Stroke(2f * s))
    for (ring in 1..18) {
        drawCircle(Color(0x664A3520), radius = (ring * 3f) * s, center = Offset(logCx, logCy), style = Stroke(0.7f * s))
    }

    // Right: Winding River & Topographical Metrics
    val riverPath = Path().apply {
        moveTo(328f * s, 150f * s)
        cubicTo(260f * s, 180f * s, 310f * s, 230f * s, 260f * s, 265f * s)
        cubicTo(220f * s, 295f * s, 240f * s, 360f * s, 180f * s, 385f * s)
        cubicTo(130f * s, 400f * s, 110f * s, 425f * s, 75f * s, 448f * s)
    }
    drawPath(riverPath, Color(0xFF7D95A1), style = Stroke(width = 18f * s, cap = StrokeCap.Round))
    drawPath(riverPath, Color(0xFF2B2620), style = Stroke(width = 20f * s, cap = StrokeCap.Round))

    drawNativeText("RIVER ELEVATION", 272f * s, 118f * s, 8.5f * s, 0xFF1C1915.toInt(), isItalic = true, align = Paint.Align.CENTER)
    drawNativeText("CONTOURS", 272f * s, 129f * s, 8.5f * s, 0xFF1C1915.toInt(), isItalic = true, align = Paint.Align.CENTER)

    // Speed Dial
    drawCircle(Color(0xFFD8B888), radius = 18f * s, center = Offset(308f * s, 238f * s))
    drawCircle(Color(0xFF2B2620), radius = 18f * s, center = Offset(308f * s, 238f * s), style = Stroke(1.2f * s))
    drawNativeText("${stats.averagePagesPerHour} PGS/H", 308f * s, 268f * s, 10f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("SPEED", 308f * s, 279f * s, 9.5f * s, 0xFF1C1915.toInt(), align = Paint.Align.CENTER)

    // Max Pages/Day & Max Mins/Day
    drawNativeText("${stats.maxPagesInOneDay}", 302f * s, 332f * s, 14f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MAX PAGES/DAY", 302f * s, 343f * s, 7.5f * s, 0xFF1C1915.toInt(), align = Paint.Align.CENTER)

    drawNativeText("${stats.maxMinutesInOneDay}", 288f * s, 405f * s, 14f * s, 0xFF1C1915.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MAX MINS/DAY", 288f * s, 416f * s, 7.5f * s, 0xFF1C1915.toInt(), align = Paint.Align.CENTER)

    // Bottom Margin Footer — MEZURA ATLAS
    drawNativeText(
        text = "MEZURA ATLAS",
        x = 180f * s,
        y = 473f * s,
        sizePx = 10f * s,
        color = 0xFF8C7D68.toInt(),
        isSerif = true,
        isBold = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.25f
    )
}

/* ============================================================================
 * 8TH DESIGN: MINIMALIST OBSIDIAN & WARM AMBER EDITORIAL POSTER
 * ============================================================================ */
private fun DrawScope.drawStyle8ObsidianEditorial(stats: BookCompletionStats, s: Float) {
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color(0xFF382012), Color(0xFF1A0F09), Color(0xFF0C0806)),
            center = Offset(140f * s, 160f * s),
            radius = 380f * s
        )
    )
    drawRect(Color(0xFF8C6239), Offset(10f * s, 10f * s), Size(340f * s, 460f * s), style = Stroke(0.8f * s))

    // Top Header Bar — MEZURA BRANDING
    drawNativeText("◆  FINISHED READING", 28f * s, 31f * s, 8f * s, 0xFFDCC0A6.toInt(), letterSpacingEm = 0.24f)
    drawNativeText("MEZURA", 332f * s, 31f * s, 8f * s, 0xFF9E846C.toInt(), align = Paint.Align.RIGHT, letterSpacingEm = 0.24f)
    drawLine(Color(0xFF6E4E2E), Offset(28f * s, 40f * s), Offset(332f * s, 40f * s), 0.8f * s)

    // Hero Split
    drawBookCoverWithSpine(stats, 28f * s, 56f * s, 128f * s, 186f * s, s, drawPageBlockEdges = true)
    drawNativeText("I've read", 176f * s, 80f * s, 17f * s, 0xFFD8BA9E.toInt(), isSerif = true, isItalic = true)
    val titleDisplay = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 176f * s, 122f * s, 26f * s, 0xFFFFF8F0.toInt(), isBold = true, shadowRadius = 12f * s, shadowColor = 0x55E89040.toInt())
    drawNativeText(stats.authorName, 176f * s, 188f * s, 16f * s, 0xFFE0C8B2.toInt())
    drawNativeText("Gave it", 176f * s, 236f * s, 14.5f * s, 0xFFB59B82.toInt(), isSerif = true, isItalic = true)
    drawStarRow(226f * s, 231f * s, 7.2f * s, 5f * s, stats.starRating, Color(0xFFDDB068), Color(0xFF7D5D3B))

    // Timeline Caliper Ruler
    drawNativeText(stats.startDateFormatted, 28f * s, 262f * s, 8f * s, 0xFFC8AF96.toInt(), letterSpacingEm = 0.16f)
    drawNativeText("${stats.totalDaysSpan} DAYS", 180f * s, 262f * s, 8f * s, 0xFFE6D0BA.toInt(), align = Paint.Align.CENTER, letterSpacingEm = 0.16f)
    drawNativeText(stats.endDateFormatted, 332f * s, 262f * s, 8f * s, 0xFFC8AF96.toInt(), align = Paint.Align.RIGHT, letterSpacingEm = 0.16f)

    drawLine(Color(0xFF9E7348), Offset(28f * s, 271f * s), Offset(332f * s, 271f * s), 0.9f * s)
    for (i in 0..80) {
        val tx = (28f + i * (304f / 80f)) * s
        val th = when {
            i % 10 == 0 -> 8f * s
            i % 5 == 0 -> 5.5f * s
            else -> 3f * s
        }
        drawLine(Color(0xFF7D5935), Offset(tx, 271f * s), Offset(tx, 271f * s + th), 0.6f * s)
    }
    // Glowing 100% scrubber pill
    drawRoundRect(Color(0xFF2B1C12), Offset(329f * s, 265f * s), Size(6f * s, 19f * s), CornerRadius(3f * s, 3f * s))
    drawRoundRect(Color(0xFFA8825C), Offset(329f * s, 265f * s), Size(6f * s, 19f * s), CornerRadius(3f * s, 3f * s), style = Stroke(0.8f * s))
    drawLine(Color(0xFFFF8820), Offset(332f * s, 268f * s), Offset(332f * s, 281f * s), 1.4f * s)

    // Primary Hero Metrics
    drawNativeText("${stats.pagesRead}", 28f * s, 348f * s, 54f * s, 0xFFF29C50.toInt(), isBold = true, shadowRadius = 14f * s, shadowColor = 0x55E68535.toInt())
    drawNativeText("PAGES READ", 28f * s, 366f * s, 8f * s, 0xFFC4A68A.toInt(), letterSpacingEm = 0.22f)
    drawLine(Color(0xFF6E4E2E), Offset(160f * s, 308f * s), Offset(160f * s, 368f * s), 0.8f * s)

    drawNativeText(stats.formattedTimeLower, 172f * s, 348f * s, 24f * s, 0xFFF09545.toInt(), isBold = true)
    drawNativeText("TOTAL READING TIME", 172f * s, 366f * s, 8f * s, 0xFFC4A68A.toInt(), letterSpacingEm = 0.22f)

    // Secondary 4-Column Row
    drawLine(Color(0xFF6E4E2E), Offset(28f * s, 384f * s), Offset(332f * s, 384f * s), 0.8f * s)
    listOf(104f, 180f, 256f).forEach { vx ->
        drawLine(Color(0xFF6E4E2E), Offset(vx * s, 392f * s), Offset(vx * s, 434f * s), 0.8f * s)
    }

    drawNativeText("${stats.averagePagesPerHour}", 28f * s, 415f * s, 22f * s, 0xFFFFF8F0.toInt(), isBold = true)
    drawNativeText("READING SPEED", 28f * s, 428f * s, 5.8f * s, 0xFF9E846C.toInt(), letterSpacingEm = 0.14f)

    drawNativeText("${stats.maxPagesInOneDay}", 112f * s, 415f * s, 22f * s, 0xFFFFF8F0.toInt(), isBold = true)
    drawNativeText("MAX PAGES / DAY", 112f * s, 428f * s, 5.8f * s, 0xFF9E846C.toInt(), letterSpacingEm = 0.14f)

    drawNativeText("${stats.maxMinutesInOneDay}", 188f * s, 415f * s, 22f * s, 0xFFFFF8F0.toInt(), isBold = true)
    drawNativeText("MAX MINS / DAY", 188f * s, 428f * s, 5.8f * s, 0xFF9E846C.toInt(), letterSpacingEm = 0.14f)

    drawNativeText("${stats.longestStreakDays}", 264f * s, 415f * s, 22f * s, 0xFFF09545.toInt(), isBold = true)
    drawNativeText("CONSECUTIVE STREAK", 264f * s, 428f * s, 5.5f * s, 0xFF9E846C.toInt(), letterSpacingEm = 0.12f)

    // Footer — MEZURA BRANDING
    drawLine(Color(0xFF6E4E2E), Offset(80f * s, 454f * s), Offset(118f * s, 454f * s), 0.8f * s)
    drawLine(Color(0xFF6E4E2E), Offset(242f * s, 454f * s), Offset(280f * s, 454f * s), 0.8f * s)
    drawNativeText(
        text = "RECORDED ON MEZURA",
        x = 180f * s,
        y = 457f * s,
        sizePx = 7f * s,
        color = 0xFF7D6650.toInt(),
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.24f
    )
}

/* ============================================================================
 * 9TH DESIGN: ORBITAL TELEMETRY & CYAN-AMBER BLUEPRINT RADAR
 * ============================================================================ */
private fun DrawScope.drawStyle9OrbitalTelemetry(stats: BookCompletionStats, s: Float) {
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color(0xFF0C3462), Color(0xFF072242), Color(0xFF051830)),
            center = Offset(180f * s, 240f * s),
            radius = 340f * s
        )
    )
    // Blueprint grid
    val gridStep = 24f * s
    var gx = gridStep
    while (gx < size.width) {
        drawLine(Color(0x2840A8FF), Offset(gx, 12f * s), Offset(gx, 468f * s), 0.6f * s)
        gx += gridStep
    }
    var gy = gridStep
    while (gy < size.height) {
        drawLine(Color(0x2840A8FF), Offset(12f * s, gy), Offset(348f * s, gy), 0.6f * s)
        gy += gridStep
    }
    drawRect(Color(0xFF3898E8), Offset(12f * s, 12f * s), Size(336f * s, 456f * s), style = Stroke(1f * s))

    // Top Header
    drawNativeText("MISSION REPORT: I'VE READ", 180f * s, 32f * s, 10.5f * s, 0xFFF5B942.toInt(), isBold = true, align = Paint.Align.CENTER, letterSpacingEm = 0.08f)
    val titleDisplay = if (stats.bookTitle.length > 20) stats.bookTitle.take(18) + "..." else stats.bookTitle
    drawNativeText(titleDisplay, 180f * s, 56f * s, 20f * s, 0xFFFFD266.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText(stats.authorName, 180f * s, 73f * s, 13.5f * s, 0xFFF2BE54.toInt(), align = Paint.Align.CENTER)
    drawNativeText("${stats.startDateFormatted} - ${stats.endDateFormatted}", 180f * s, 87f * s, 9.5f * s, 0xFFF2B844.toInt(), align = Paint.Align.CENTER)

    // Central Glowing Cyan Radar Rings (180, 240)
    val rCx = 180f * s
    val rCy = 240f * s
    drawCircle(Color(0x3800F0FF), radius = 126f * s, center = Offset(rCx, rCy), style = Stroke(6f * s))
    drawCircle(Color(0xFF00F5FF), radius = 126f * s, center = Offset(rCx, rCy), style = Stroke(2f * s))
    drawCircle(Color(0x883898E8), radius = 90f * s, center = Offset(rCx, rCy), style = Stroke(1f * s))
    drawCircle(Color(0xFF00F5FF), radius = 54f * s, center = Offset(rCx, rCy), style = Stroke(1.5f * s))
    drawCircle(Color(0x883898E8), radius = 26f * s, center = Offset(rCx, rCy), style = Stroke(0.8f * s))

    // Tilted Amber Elliptical Orbit
    rotate(degrees = -35f, pivot = Offset(rCx, rCy)) {
        drawOval(
            color = Color(0xFFFFB833),
            topLeft = Offset(rCx - 136f * s, rCy - 44f * s),
            size = Size(272f * s, 88f * s),
            style = Stroke(width = 1.6f * s)
        )
    }

    // Book Cover Frame at Top of Radar
    drawRect(Color(0xFF072242), Offset(140f * s, 92f * s), Size(80f * s, 92f * s))
    drawRect(Color(0xFF48B0FF), Offset(140f * s, 92f * s), Size(80f * s, 92f * s), style = Stroke(1.2f * s))
    drawBookCoverWithSpine(stats, 146f * s, 98f * s, 68f * s, 80f * s, s)

    // 6 Orbital Telemetry Nodes
    drawNativeText("${stats.pagesRead}", 90f * s, 174f * s, 17f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("PAGES READ", 90f * s, 186f * s, 8.5f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    drawOrbitalNode(286f * s, 162f * s, s)
    drawNativeText(stats.formattedTimeLower, 296f * s, 142f * s, 11f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MISSION TIME", 296f * s, 153f * s, 8.5f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    drawOrbitalNode(312f * s, 240f * s, s)
    drawNativeText("${stats.averagePagesPerHour} PGS/H", 312f * s, 266f * s, 12f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("VELOCITY", 312f * s, 277f * s, 8.5f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    drawOrbitalNode(86f * s, 332f * s, s)
    drawNativeText("${stats.averagePagesPerHour} PGS/H", 62f * s, 348f * s, 12f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("VELOCITY", 62f * s, 359f * s, 8.5f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    drawOrbitalNode(272f * s, 332f * s, s)
    drawNativeText("${stats.maxPagesInOneDay}", 300f * s, 338f * s, 15f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("MAX PAGES/DAY", 300f * s, 349f * s, 8f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    drawOrbitalNode(180f * s, 366f * s, s)
    drawNativeText("${stats.maxMinutesInOneDay}", 202f * s, 362f * s, 15f * s, 0xFFFFB833.toInt(), isBold = true)
    drawNativeText("MAX MINS/DAY", 184f * s, 382f * s, 8.5f * s, 0xFF00E5FF.toInt())

    // Bottom 5-Chamber LED Rating Box
    val boxX = 130f * s
    val boxY = 400f * s
    drawRect(Color(0xFF48B0FF), Offset(boxX, boxY), Size(100f * s, 20f * s), style = Stroke(1f * s))
    for (i in 0 until 5) {
        val cellCx = boxX + (10f + i * 20f) * s
        val cellCy = boxY + 10f * s
        if (i > 0) {
            drawLine(Color(0xFF48B0FF), Offset(boxX + i * 20f * s, boxY), Offset(boxX + i * 20f * s, boxY + 20f * s), 0.8f * s)
        }
        if (i < stats.starRating) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF0A0), Color(0xFFFFA820)), Offset(cellCx, cellCy), 6.5f * s),
                radius = 6.5f * s,
                center = Offset(cellCx, cellCy)
            )
        } else {
            drawCircle(Color(0xFF041326), radius = 6f * s, center = Offset(cellCx, cellCy))
        }
    }
    drawNativeText("${stats.starRating}/5 RATING", 180f * s, 431f * s, 9.5f * s, 0xFF00E5FF.toInt(), isBold = true, align = Paint.Align.CENTER)

    // Bottom-Right Streak Orbit
    drawCircle(Color(0xFF00F5FF), radius = 30f * s, center = Offset(298f * s, 398f * s), style = Stroke(1.6f * s))
    drawNativeText("${stats.longestStreakDays} DAY", 298f * s, 396f * s, 13f * s, 0xFFFFB833.toInt(), isBold = true, align = Paint.Align.CENTER)
    drawNativeText("STREAK", 298f * s, 408f * s, 9f * s, 0xFF00E5FF.toInt(), align = Paint.Align.CENTER)

    // Bottom Telemetry Title Block — MEZURA TELEMETRY
    drawRect(Color(0xFF48B0FF), Offset(112f * s, 444f * s), Size(136f * s, 24f * s), style = Stroke(1f * s))
    drawNativeText(
        text = "MEZURA TELEMETRY",
        x = 180f * s,
        y = 460f * s,
        sizePx = 11f * s,
        color = 0xFF66C2FF.toInt(),
        isMono = true,
        isBold = true,
        align = Paint.Align.CENTER,
        letterSpacingEm = 0.14f
    )
}

private fun DrawScope.drawOrbitalNode(cx: Float, cy: Float, s: Float) {
    drawCircle(Color(0xFFFFB833), radius = 6f * s, center = Offset(cx, cy), style = Stroke(1f * s))
    drawCircle(Color(0xFFFFB833), radius = 2.5f * s, center = Offset(cx, cy))
}
