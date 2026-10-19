package rocks.ndot.widgets.instruments.folio

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.ndot.widgets.kit.Ink
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object CoverImageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val lru = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }

    fun get(key: String): ImageBitmap? = lru.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        lru.put(key, bitmap)
    }
}

/**
 * Procedural HSL color helper.
 */
fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

/**
 * 2:3 CoverImage composable that handles:
 * - Real cover rendering from file with spine shadow, 1dp highlight, and bottom shade
 * - Deterministic typographic fallback cover when no cover is present
 * - 5dp flush BRASS progress bar on the bottom edge
 * - Lift and shadow on press
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverImage(
    book: BookItem,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    showProgressBar: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coverKey = book.customCoverPath ?: book.coverLocalPath
    var loadedBitmap by remember(book.id, book.coverLocalPath, book.customCoverPath) {
        mutableStateOf<ImageBitmap?>(
            coverKey?.let { CoverImageCache.get(it) }
        )
    }

    LaunchedEffect(book.id, book.coverLocalPath, book.customCoverPath) {
        val path = coverKey
        if (path.isNullOrEmpty()) {
            loadedBitmap = null
            return@LaunchedEffect
        }
        val cached = CoverImageCache.get(path)
        if (cached != null) {
            loadedBitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val file = book.resolveCoverFile(context)
            if (file != null && file.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val imageBmp = bmp.asImageBitmap()
                        CoverImageCache.put(path, imageBmp)
                        withContext(Dispatchers.Main) {
                            loadedBitmap = imageBmp
                        }
                    }
                } catch (e: Throwable) {
                    // Fall back to generated cover
                }
            }
        }
    }

    var isPressed by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "coverScale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .scale(scaleAnim)
            .shadow(if (isPressed) 8.dp else 4.dp, shape = shape)
            .clip(shape)
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick?.invoke() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
    ) {
        val bitmap = loadedBitmap
        if (bitmap != null) {
            RealCoverCanvas(
                bitmap = bitmap,
                progressFraction = if (showProgressBar) book.progressFraction else 0f,
                cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GeneratedFallbackCover(
                title = book.title,
                author = book.author,
                progressFraction = if (showProgressBar) book.progressFraction else 0f,
                cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RealCoverCanvas(
    bitmap: ImageBitmap,
    progressFraction: Float,
    cornerRadiusPx: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Draw real image center-cropped
        val dstSize = size
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val scale = maxOf(dstSize.width / srcW, dstSize.height / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = (dstSize.width - scaledW) / 2f
        val top = (dstSize.height - scaledH) / 2f

        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(scaledW.toInt(), scaledH.toInt())
        )

        // Spine shadow: 10%-width left gradient (black 25% -> 0)
        val spineW = size.width * 0.10f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                startX = 0f,
                endX = spineW
            ),
            size = Size(spineW, size.height)
        )

        // 1dp inner left highlight (white 18%)
        val highlightW = 1.dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(0f, 0f),
            size = Size(highlightW, size.height)
        )

        // Soft bottom shade (black 0 -> 22%)
        val bottomShadeH = size.height * 0.25f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                startY = size.height - bottomShadeH,
                endY = size.height
            ),
            topLeft = Offset(0f, size.height - bottomShadeH),
            size = Size(size.width, bottomShadeH)
        )

        // 5dp flush BRASS progress bar on bottom edge
        if (progressFraction > 0f) {
            val barH = 5.dp.toPx()
            val barW = size.width * progressFraction.coerceIn(0f, 1f)
            val barTop = size.height - barH

            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, barTop, barW, size.height),
                        bottomLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        topLeft = CornerRadius(0f, 0f),
                        topRight = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        bottomRight = CornerRadius(if (progressFraction >= 0.98f) cornerRadiusPx else 0f, if (progressFraction >= 0.98f) cornerRadiusPx else 0f)
                    )
                )
            }
            drawPath(path, color = Ink.BRASS)
        }
    }
}

@Composable
fun GeneratedFallbackCover(
    title: String,
    author: String,
    progressFraction: Float,
    cornerRadiusPx: Float,
    modifier: Modifier = Modifier
) {
    val seed = abs((title + author).hashCode())
    val hue = (seed % 360).toFloat()
    val topColor = hslToColor(hue, 0.30f, 0.22f)
    val botColor = hslToColor(hue, 0.30f, 0.15f)
    val motifIdx = seed % 4

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background vertical gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, botColor),
                    startY = 0f,
                    endY = size.height
                )
            )

            // Faint motif (12% alpha)
            drawMotif(motifIdx, size)

            // 1dp inset brass frame at 8dp
            val inset = 8.dp.toPx()
            val strokeW = 1.dp.toPx()
            drawRoundRect(
                color = Ink.BRASS.copy(alpha = 0.45f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeW)
            )

            // Spine shadow
            val spineW = size.width * 0.10f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
                    startX = 0f,
                    endX = spineW
                ),
                size = Size(spineW, size.height)
            )

            // 1dp inner left highlight
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(0f, 0f),
                size = Size(1.dp.toPx(), size.height)
            )

            // Flush 5dp BRASS progress bar on bottom edge
            if (progressFraction > 0f) {
                val barH = 5.dp.toPx()
                val barW = size.width * progressFraction.coerceIn(0f, 1f)
                val barTop = size.height - barH

                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, barTop, barW, size.height),
                            bottomLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            topLeft = CornerRadius(0f, 0f),
                            topRight = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            bottomRight = CornerRadius(if (progressFraction >= 0.98f) cornerRadiusPx else 0f, if (progressFraction >= 0.98f) cornerRadiusPx else 0f)
                        )
                    )
                }
                drawPath(path, color = Ink.BRASS)
            }
        }

        // Title and author text overlay (Bangla-aware system font)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            BasicText(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Ink.TEXT_WARM,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            )

            if (author.isNotEmpty()) {
                BasicText(
                    text = author,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Ink.TEXT_MUTED_V4,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Default,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawMotif(motifIdx: Int, size: Size) {
    val faintColor = Color.White.copy(alpha = 0.10f)
    when (motifIdx) {
        0 -> { // Stripes
            val stripeDist = 16.dp.toPx()
            var x = 0f
            while (x < size.width + size.height) {
                drawLine(
                    color = faintColor,
                    start = Offset(x, 0f),
                    end = Offset(x - size.height, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += stripeDist
            }
        }
        1 -> { // Dot grid
            val gap = 14.dp.toPx()
            var gx = gap
            while (gx < size.width) {
                var gy = gap
                while (gy < size.height) {
                    drawCircle(faintColor, radius = 1.2.dp.toPx(), center = Offset(gx, gy))
                    gy += gap
                }
                gx += gap
            }
        }
        2 -> { // Concentric arcs
            val center = Offset(size.width / 2f, size.height / 2f)
            for (r in 1..4) {
                drawCircle(
                    color = faintColor,
                    radius = r * 22.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
        3 -> { // Arch
            val w = size.width * 0.6f
            val h = size.height * 0.7f
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = faintColor,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

/**
 * Renders candidate cover thumbnail with real network image fallback to procedural jacket.
 */
@Composable
fun AsyncCoverThumbnail(
    imageUrl: String?,
    fallbackTitle: String,
    fallbackAuthor: String,
    modifier: Modifier = Modifier,
    cornerRadiusPx: Float = 16f
) {
    var bitmap by remember(imageUrl) {
        mutableStateOf<ImageBitmap?>(imageUrl?.let { CoverImageCache.get(it) })
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = CoverImageCache.get(imageUrl)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val downloaded = BookMetadataFetcher.fetchCoverBitmap(imageUrl)
        if (downloaded != null) {
            val imgBmp = downloaded.asImageBitmap()
            CoverImageCache.put(imageUrl, imgBmp)
            bitmap = imgBmp
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Canvas(modifier = modifier) {
            val r = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            val path = Path().apply {
                addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), r))
            }
            clipPath(path) {
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
                // Spine shadow on left
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                        startX = 0f,
                        endX = size.width * 0.12f
                    )
                )
                // Outline hairline
                drawRoundRect(
                    color = Ink.HAIRLINE_V4,
                    size = size,
                    cornerRadius = r,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    } else {
        GeneratedFallbackCover(
            title = fallbackTitle,
            author = fallbackAuthor,
            progressFraction = 0f,
            cornerRadiusPx = cornerRadiusPx,
            modifier = modifier
        )
    }
}

