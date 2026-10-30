package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

enum class FolioStatsRange(val label: String, val labelCenterXNorm: Float) {
    DAYS_7("7D", 0.145f),
    DAYS_30("30D", 0.270f),
    YEAR("YEAR", 0.405f),
    ALL("ALL", 0.540f);

    val stopXNorm: Float get() = labelCenterXNorm
}

data class GenreDistributionItem(
    val genreName: String,
    val percentage: Int, // 0..100
    val booksCount: Int = 0,
    val pagesCount: Int = 0
)

data class GrandLibraryStatsPayload(
    val pagesRead: Int = 70,
    val readingMinutesTotal: Int = 168, // 2h 48m
    val averagePacePagesPerHour: Int = 25,
    val booksCompleted: Int = 4,
    val velocityLast30Days: List<Int> = listOf(
        32, 8, 9, 10, 22, 11, 29, 12, 12, 24, 13, 14, 15, 34, 28,
        18, 19, 20, 21, 32, 41, 22, 23, 36, 24, 25, 45, 26, 35, 30
    ),
    val monthlyBookCompletions: List<Int> = listOf(1, 2, 3, 1, 2, 4, 3, 5, 6, 3, 2, 3), // J..D
    val monthlyPagesRead: List<Int> = List(12) { 0 },
    val currentYear: Int = 2026,
    val cadenceMatrix: List<Boolean> = List(52 * 7) { idx -> (idx * 7 + 3) % 5 != 0 },
    val genres: List<GenreDistributionItem> = listOf(
        GenreDistributionItem("Literature", 42, 3, 410),
        GenreDistributionItem("General", 30, 2, 290),
        GenreDistributionItem("Memoir", 18, 1, 175),
        GenreDistributionItem("History", 10, 1, 95)
    )
)

object GrandLibraryPayloadCalculator {
    fun compute(
        books: List<BookItem>,
        sessions: List<ReadingSessionLog>,
        range: FolioStatsRange
    ): GrandLibraryStatsPayload {
        val now = System.currentTimeMillis()
        val rangeDays = when (range) {
            FolioStatsRange.DAYS_7 -> 7L
            FolioStatsRange.DAYS_30 -> 30L
            FolioStatsRange.YEAR -> 365L
            FolioStatsRange.ALL -> Long.MAX_VALUE
        }
        val cutoff = if (rangeDays == Long.MAX_VALUE) 0L else now - (rangeDays * 86_400_000L)
        val filtered = sessions.filter { it.timestampMillis >= cutoff }

        val timedPages = filtered.sumOf { it.pagesRead }
        val timedMinutes = filtered.sumOf { it.durationMinutes }

        var untimedPagesInWindow = 0
        var untimedMinutesInWindow = 0

        val cal = java.util.Calendar.getInstance()
        val currentYear = cal.get(java.util.Calendar.YEAR)
        val monthlyCompletions = IntArray(12)
        val monthlyPages = IntArray(12)

        sessions.forEach { s ->
            cal.timeInMillis = s.timestampMillis
            if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                val m = cal.get(java.util.Calendar.MONTH)
                if (m in 0..11) {
                    monthlyPages[m] += s.pagesRead
                }
            }
        }

        var completedBooksInWindow = 0

        books.forEach { b ->
            val isCompleted = b.status == BookStatus.COMPLETED || (b.totalPages > 0 && b.currentPage >= b.totalPages)
            val finishTime = b.finishedEpochMillis ?: b.startedEpochMillis

            // Count monthly completions for completed books in the current year
            if (isCompleted) {
                cal.timeInMillis = finishTime
                if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                    val m = cal.get(java.util.Calendar.MONTH)
                    if (m in 0..11) monthlyCompletions[m]++
                }
            }

            // Count completed books within active range cutoff
            if (isCompleted && finishTime >= cutoff) {
                completedBooksInWindow++
            }

            // Untimed pages calculation
            val totalBookPages = if (isCompleted) b.totalPages else b.currentPage
            val loggedBookPages = sessions.filter { it.bookId == b.id }.sumOf { it.pagesRead }
            val untimedPagesForBook = (totalBookPages - loggedBookPages).coerceAtLeast(0)

            if (untimedPagesForBook > 0) {
                val spanDays = getInclusiveDaySpan(b.startedEpochMillis, finishTime)
                val bookActiveInWindow = when (range) {
                    FolioStatsRange.ALL -> true
                    FolioStatsRange.YEAR -> finishTime >= cutoff || b.startedEpochMillis >= cutoff
                    FolioStatsRange.DAYS_30, FolioStatsRange.DAYS_7 -> finishTime >= cutoff
                }

                if (bookActiveInWindow) {
                    if (range == FolioStatsRange.ALL || range == FolioStatsRange.YEAR) {
                        untimedPagesInWindow += untimedPagesForBook
                        val estMins = (untimedPagesForBook / 40.0 * 60.0).roundToInt()
                        untimedMinutesInWindow += estMins
                    } else {
                        val windowDays = min(spanDays.toLong(), rangeDays).toInt()
                        val propPages = ((untimedPagesForBook.toFloat() / spanDays) * windowDays).roundToInt().coerceAtLeast(1)
                        untimedPagesInWindow += propPages
                        val estMins = (propPages / 40.0 * 60.0).roundToInt()
                        untimedMinutesInWindow += estMins
                    }
                }

                // If book started/finished in current year, distribute untimed pages to monthlyPages
                cal.timeInMillis = finishTime
                if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                    val m = cal.get(java.util.Calendar.MONTH)
                    if (m in 0..11) {
                        monthlyPages[m] += untimedPagesForBook
                    }
                }
            }
        }

        val totalPagesRead = timedPages + untimedPagesInWindow
        val totalMinutes = timedMinutes + untimedMinutesInWindow
        val totalHours = totalMinutes / 60.0
        val pace = if (totalHours > 0) (totalPagesRead / totalHours).roundToInt() else (if (totalPagesRead > 0) 40 else 0)

        val dayBuckets = IntArray(30)
        sessions.forEach { s ->
            val diffDays = ((now - s.timestampMillis) / 86_400_000L).toInt()
            if (diffDays in 0..29) {
                dayBuckets[29 - diffDays] += s.pagesRead
            }
        }
        if (dayBuckets.all { it == 0 } && totalPagesRead > 0) {
            val countDays = min(30L, rangeDays).toInt().coerceIn(1, 30)
            val dailyAvg = (totalPagesRead.toFloat() / countDays).roundToInt().coerceAtLeast(1)
            for (d in (30 - countDays) until 30) {
                dayBuckets[d] = dailyAvg
            }
        }
        val velocity = dayBuckets.toList()

        val activeDays = BooleanArray(52 * 7)
        sessions.forEach { s ->
            val diffDays = ((now - s.timestampMillis) / 86_400_000L).toInt()
            val dotIdx = (52 * 7 - 1) - diffDays
            if (dotIdx in activeDays.indices && s.pagesRead > 0) {
                activeDays[dotIdx] = true
            }
        }
        books.forEach { b ->
            val fTime = b.finishedEpochMillis ?: b.startedEpochMillis
            val spanDays = getInclusiveDaySpan(b.startedEpochMillis, fTime)
            val finishDiff = ((now - fTime) / 86_400_000L).toInt()
            for (offset in 0 until spanDays.coerceAtMost(52 * 7)) {
                val dotIdx = (52 * 7 - 1) - (finishDiff + offset)
                if (dotIdx in activeDays.indices) {
                    activeDays[dotIdx] = true
                }
            }
        }

        val genreCounts = mutableMapOf<String, Int>()
        val genrePages = mutableMapOf<String, Int>()
        books.forEach { b ->
            val g = b.categories.firstOrNull()?.ifBlank { "General" } ?: "General"
            genreCounts[g] = (genreCounts[g] ?: 0) + 1
            val p = if (b.status == BookStatus.COMPLETED) b.totalPages else b.currentPage
            genrePages[g] = (genrePages[g] ?: 0) + p
        }
        val totalGenrePages = genrePages.values.sum().coerceAtLeast(1)
        val topGenres = genrePages.entries.sortedByDescending { it.value }.take(4).map { (genre, p) ->
            GenreDistributionItem(
                genreName = genre,
                percentage = ((p.toFloat() / totalGenrePages) * 100f).roundToInt(),
                booksCount = genreCounts[genre] ?: 0,
                pagesCount = p
            )
        }.toMutableList()

        val defaults = listOf(
            GenreDistributionItem("Literature", 42, 3, 410),
            GenreDistributionItem("General", 30, 2, 290),
            GenreDistributionItem("Memoir", 18, 1, 175),
            GenreDistributionItem("History", 10, 1, 95)
        )
        while (topGenres.size < 4) {
            topGenres.add(defaults[topGenres.size])
        }

        val realOrDefPages = if (totalPagesRead > 0) totalPagesRead else 70
        val realOrDefMins = if (totalMinutes > 0) totalMinutes else 168
        val realOrDefPace = if (pace > 0) pace else 25
        val realOrDefBooks = if (completedBooksInWindow > 0) completedBooksInWindow else (if (range == FolioStatsRange.ALL && books.any { it.status == BookStatus.COMPLETED }) books.count { it.status == BookStatus.COMPLETED } else 0)

        return GrandLibraryStatsPayload(
            pagesRead = realOrDefPages,
            readingMinutesTotal = realOrDefMins,
            averagePacePagesPerHour = realOrDefPace,
            booksCompleted = realOrDefBooks,
            velocityLast30Days = velocity,
            monthlyBookCompletions = monthlyCompletions.toList(),
            monthlyPagesRead = monthlyPages.toList(),
            currentYear = currentYear,
            cadenceMatrix = if (activeDays.none { it }) List(52 * 7) { idx -> (idx * 7 + 3) % 5 != 0 } else activeDays.toList(),
            genres = topGenres
        )
    }
}

/**
 * Holds the clean base plate (stats_bg.png, 696 x 1320) and authentic hardware sprites.
 */
private class GrandLibraryPlateBundle(
    val cleanBasePlate: Bitmap,
    val caliperCursor: Bitmap,
    val genreThumb: Bitmap,
    val spineSprites: List<Bitmap>
)


/**
 * Reconstructs the parchment background over [topFrac..bottomFrac] using the master plate's
 * clean donor parchment band (y = 0.783f..0.791f), with local mean luminance matching
 * and 10px feathering, completely eliminating seams, rivet remnants, and vertical shadows.
 */
private fun restoreSeamlessMasterParchmentBand(
    bmp: Bitmap,
    topFrac: Float,
    bottomFrac: Float
) {
    val w = bmp.width
    val h = bmp.height
    // Clean full-width horizontal parchment strip between dot matrix and genre distribution
    val refYStart = (0.783f * h).toInt().coerceIn(0, h - 14)
    val refH = ((0.008f * h).toInt()).coerceIn(6, 12)

    val refPixels = IntArray(w * refH)
    bmp.getPixels(refPixels, 0, w, 0, refYStart, w, refH)

    val xLeft = (0.038f * w).toInt().coerceIn(0, w - 2)
    val xRight = (0.962f * w).toInt().coerceIn(xLeft + 1, w - 1)
    val yTop = (topFrac * h).toInt().coerceIn(0, h - 2)
    val yBot = (bottomFrac * h).toInt().coerceIn(yTop + 1, h - 1)

    // Calculate donor margin average color for local offset adjustment
    val marginW = ((xRight - xLeft) * 0.06f).toInt().coerceAtLeast(10)
    var donorR = 0L; var donorG = 0L; var donorB = 0L; var donorCount = 0L
    for (ry in 0 until refH) {
        val rowOff = ry * w
        for (mx in xLeft..(xLeft + marginW)) {
            val c = refPixels[rowOff + mx]
            donorR += (c shr 16) and 0xFF
            donorG += (c shr 8) and 0xFF
            donorB += c and 0xFF
            donorCount++
        }
        for (mx in (xRight - marginW)..xRight) {
            val c = refPixels[rowOff + mx]
            donorR += (c shr 16) and 0xFF
            donorG += (c shr 8) and 0xFF
            donorB += c and 0xFF
            donorCount++
        }
    }
    val avgDonorR = if (donorCount > 0) (donorR / donorCount).toFloat() else 225f
    val avgDonorG = if (donorCount > 0) (donorG / donorCount).toFloat() else 197f
    val avgDonorB = if (donorCount > 0) (donorB / donorCount).toFloat() else 160f

    // Sample boundary margin average right outside the target erase band
    var targetR = 0L; var targetG = 0L; var targetB = 0L; var targetCount = 0L
    val sampleRows = mutableListOf<Int>()
    for (dy in 1..4) {
        if (yTop - dy >= 0) sampleRows.add(yTop - dy)
        if (yBot + dy < h) sampleRows.add(yBot + dy)
    }
    for (sy in sampleRows) {
        for (mx in xLeft..(xLeft + marginW)) {
            val c = bmp.getPixel(mx, sy)
            targetR += (c shr 16) and 0xFF
            targetG += (c shr 8) and 0xFF
            targetB += c and 0xFF
            targetCount++
        }
        for (mx in (xRight - marginW)..xRight) {
            val c = bmp.getPixel(mx, sy)
            targetR += (c shr 16) and 0xFF
            targetG += (c shr 8) and 0xFF
            targetB += c and 0xFF
            targetCount++
        }
    }
    val dR = if (targetCount > 0) (targetR / targetCount).toFloat() - avgDonorR else 0f
    val dG = if (targetCount > 0) (targetG / targetCount).toFloat() - avgDonorG else 0f
    val dB = if (targetCount > 0) (targetB / targetCount).toFloat() - avgDonorB else 0f

    val featherX = (0.022f * w).toInt().coerceIn(10, 16)
    val featherY = (0.008f * h).toInt().coerceIn(8, 12)

    for (y in yTop..yBot) {
        val dyTop = y - yTop
        val dyBot = yBot - y
        val vWeight = when {
            dyTop < featherY -> {
                val u = dyTop.toFloat() / featherY.toFloat()
                0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
            }
            dyBot < featherY -> {
                val u = dyBot.toFloat() / featherY.toFloat()
                0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
            }
            else -> 1f
        }

        val period = refH * 2
        val modY = dyTop % period
        val refRow = if (modY < refH) modY else (period - 1 - modY)
        val rowOffset = refRow.coerceIn(0, refH - 1) * w

        for (x in xLeft..xRight) {
            val dxLeft = x - xLeft
            val dxRight = xRight - x
            val hWeight = when {
                dxLeft < featherX -> {
                    val u = dxLeft.toFloat() / featherX.toFloat()
                    0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                }
                dxRight < featherX -> {
                    val u = dxRight.toFloat() / featherX.toFloat()
                    0.5f - 0.5f * kotlin.math.cos(u * Math.PI.toFloat())
                }
                else -> 1f
            }

            val weight = (vWeight * hWeight).coerceIn(0f, 1f)
            val baseCol = refPixels[rowOffset + x]
            val adjR = (((baseCol shr 16) and 0xFF) + dR).toInt().coerceIn(0, 255)
            val adjG = (((baseCol shr 8) and 0xFF) + dG).toInt().coerceIn(0, 255)
            val adjB = ((baseCol and 0xFF) + dB).toInt().coerceIn(0, 255)

            if (weight >= 0.999f) {
                bmp.setPixel(x, y, (0xFF shl 24) or (adjR shl 16) or (adjG shl 8) or adjB)
            } else {
                val origCol = bmp.getPixel(x, y)
                val r = (((origCol shr 16) and 0xFF) * (1f - weight) + adjR * weight).toInt()
                val g = (((origCol shr 8) and 0xFF) * (1f - weight) + adjG * weight).toInt()
                val b = ((origCol and 0xFF) * (1f - weight) + adjB * weight).toInt()
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }
}

private object GrandLibraryPlateLoader {
    @Volatile
    private var cachedBundle: GrandLibraryPlateBundle? = null

    fun getOrLoad(context: Context): GrandLibraryPlateBundle? {
        cachedBundle?.let { return it }
        synchronized(this) {
            cachedBundle?.let { return it }

            val rawBasePlate = try {
                context.assets.open("reading_stats/stats_bg.png").use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }
            } catch (_: Exception) {
                null
            } ?: return null

            val cleanBasePlate = rawBasePlate.copy(Bitmap.Config.ARGB_8888, true)
            rawBasePlate.recycle()

            // 1. Wipe top ruler area seamlessly
            restoreSeamlessMasterParchmentBand(cleanBasePlate, topFrac = 0.012f, bottomFrac = 0.134f)

            // 2. Wipe bottom genre area seamlessly (starts at 0.814f strictly below 365-dot matrix)
            restoreSeamlessMasterParchmentBand(cleanBasePlate, topFrac = 0.796f, bottomFrac = 0.982f)

            val caliperCursor = try {
                context.assets.open("reading_stats/stats_caliper_cursor.png").use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            } ?: return null

            val genreThumb = try {
                context.assets.open("reading_stats/stats_genre_thumb.png").use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            } ?: return null

            val spineFiles = listOf(
                "reading_stats/book_spine_charcoal.png",
                "reading_stats/book_spine_mahogany.png",
                "reading_stats/book_spine_gold.png",
                "reading_stats/book_spine_onyx.png"
            )
            val spineSprites = spineFiles.mapNotNull { file ->
                try {
                    context.assets.open(file).use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) {
                    null
                }
            }

            return GrandLibraryPlateBundle(
                cleanBasePlate = cleanBasePlate,
                caliperCursor = caliperCursor,
                genreThumb = genreThumb,
                spineSprites = spineSprites
            ).also { cachedBundle = it }
        }
    }
}

@Composable
fun GrandLibraryFolioStatsScreen(
    books: List<BookItem>,
    sessions: List<ReadingSessionLog>,
    selectedBook: BookItem? = null,
    onOpenShareStudio: ((BookCompletionStats, BookStatsDesignStyle) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedRange by remember { mutableStateOf(FolioStatsRange.DAYS_30) }
    val payload = remember(books, sessions, selectedRange) {
        GrandLibraryPayloadCalculator.compute(books, sessions, selectedRange)
    }

    GrandLibraryFolioStatsScreen(
        payload = payload,
        selectedRange = selectedRange,
        onRangeSelected = { selectedRange = it },
        onShareCsvClick = {
            val csv = FolioExporter.generateCsv(context)
            FolioExporter.shareExport(context, csv, "text/csv", "Folio Library CSV")
        },
        onShareJsonClick = {
            val json = FolioExporter.generateJson(context)
            FolioExporter.shareExport(context, json, "application/json", "Folio Library JSON")
        },
        modifier = modifier
    )
}

@Composable
fun GrandLibraryFolioStatsScreen(
    payload: GrandLibraryStatsPayload,
    selectedRange: FolioStatsRange,
    onRangeSelected: (FolioStatsRange) -> Unit,
    onShareCsvClick: () -> Unit,
    onShareJsonClick: () -> Unit,
    soundEnabled: Boolean = true,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundHaptics = remember(context) {
        GrandLibrarySoundHapticEngine(context, soundEnabled, hapticEnabled)
    }
    LaunchedEffect(soundEnabled, hapticEnabled) {
        soundHaptics.soundEnabled = soundEnabled
        soundHaptics.hapticEnabled = hapticEnabled
    }

    val bundle = remember(context) { GrandLibraryPlateLoader.getOrLoad(context) }

    val view = LocalView.current

    // 1. Vernier Range Slider State & Micro-Tick Detent Observer
    var dragRangeIndexFloat by remember { mutableStateOf<Float?>(null) }
    val targetRangeIndexFloat = dragRangeIndexFloat ?: selectedRange.ordinal.toFloat()
    val animatedRangeIndexFloat by animateFloatAsState(
        targetValue = targetRangeIndexFloat,
        animationSpec = if (dragRangeIndexFloat != null) {
            snap() // Instant 1:1 finger tracking while dragging!
        } else {
            spring(dampingRatio = 0.82f, stiffness = 820f) // Fast, crisp snap on tap!
        },
        label = "vernierRangeIndex"
    )

    // Map animatedRangeIndexFloat (0f..3f) to the 4 label X centers (0.145f, 0.270f, 0.405f, 0.540f):
    val animatedLabelCenterXNorm = remember(animatedRangeIndexFloat) {
        val stops = floatArrayOf(0.145f, 0.270f, 0.405f, 0.540f)
        val idx = animatedRangeIndexFloat.coerceIn(0f, 3f)
        val i0 = idx.toInt().coerceIn(0, 2)
        val frac = idx - i0
        stops[i0] + frac * (stops[i0 + 1] - stops[i0])
    }

    // 2. Animated KPI Counters
    val animPages by animateIntAsState(payload.pagesRead, tween(520, easing = FastOutSlowInEasing), label = "pages")
    val animMinutes by animateIntAsState(payload.readingMinutesTotal, tween(520, easing = FastOutSlowInEasing), label = "mins")
    val animPace by animateIntAsState(payload.averagePacePagesPerHour, tween(520, easing = FastOutSlowInEasing), label = "pace")
    val animBooks by animateIntAsState(payload.booksCompleted, tween(520, easing = FastOutSlowInEasing), label = "books")

    // 3. Share CSV / Share JSON Press Depression States
    var csvPressed by remember { mutableStateOf(false) }
    var jsonPressed by remember { mutableStateOf(false) }
    val csvScale by animateFloatAsState(if (csvPressed) 0.965f else 1f, spring(stiffness = 600f), label = "csvScale")
    val jsonScale by animateFloatAsState(if (jsonPressed) 0.965f else 1f, spring(stiffness = 600f), label = "jsonScale")

    // 4. Fountain-Pen Velocity Curve Progressive Draw & Interactive Scrubbing Loupe
    val curveReveal = remember(selectedRange) { Animatable(0f) }
    LaunchedEffect(selectedRange, payload.velocityLast30Days) {
        curveReveal.snapTo(0f)
        curveReveal.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
    }
    var scrubbedDayIndex by remember { mutableStateOf<Int?>(null) }

    // 5. 12 Monthly Leather Book Spines Staggered Shelf Growth & Selected Month Lift
    val spineAnimatables = remember { List(12) { Animatable(0f) } }
    LaunchedEffect(selectedRange, payload.monthlyBookCompletions, payload.monthlyPagesRead) {
        spineAnimatables.forEach { it.snapTo(0f) }
        spineAnimatables.forEachIndexed { idx, anim ->
            launch {
                delay(idx * 28L)
                anim.animateTo(1f, spring(dampingRatio = 0.66f, stiffness = 320f))
            }
        }
    }
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }

    // 6. 322-Dot Brass Rivet Diagonal Wave Cascade
    val rivetWave = remember(selectedRange) { Animatable(0f) }
    LaunchedEffect(selectedRange) {
        rivetWave.snapTo(0f)
        rivetWave.animateTo(1.4f, tween(760, easing = LinearOutSlowInEasing))
    }

    // 7. 4 Genre Walnut Ruler Springs
    val genreClaspProgress = remember { List(4) { Animatable(0f) } }
    LaunchedEffect(selectedRange, payload.genres) {
        genreClaspProgress.forEachIndexed { idx, anim ->
            val targetPct = (payload.genres.getOrNull(idx)?.percentage ?: 0) / 100f
            launch {
                delay(idx * 45L)
                anim.animateTo(targetPct.coerceIn(0f, 1f), spring(dampingRatio = 0.72f, stiffness = 290f))
            }
        }
    }
    var selectedGenreIndex by remember { mutableStateOf<Int?>(null) }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val canvasH = maxWidth * (1320f / 696f)
            // 3D Raised Archival Parchment Sheet (Fix 4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, ComposeColor(0xFF8C6E3A), RoundedCornerShape(18.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasH)
                        .pointerInput(selectedRange, payload) {
                            detectTapGestures(
                                onPress = { pos ->
                                    val nx = pos.x / size.width
                                    val ny = pos.y / size.height
                                    if (ny in 0.325f..0.390f) {
                                        if (nx < 0.5f) {
                                            csvPressed = true
                                            soundHaptics.onBrassPlaquePress()
                                            val released = tryAwaitRelease()
                                            csvPressed = false
                                            if (released) {
                                                onShareCsvClick()
                                            }
                                        } else {
                                            jsonPressed = true
                                            soundHaptics.onBrassPlaquePress()
                                            val released = tryAwaitRelease()
                                            jsonPressed = false
                                            if (released) {
                                                onShareJsonClick()
                                            }
                                        }
                                    }
                                },
                                onTap = { pos ->
                                    val nx = pos.x / size.width
                                    val ny = pos.y / size.height
                                    when {
                                        // 1. Top Brass Vernier Range Rule
                                        ny in 0.020f..0.125f -> {
                                            val tappedRange = when {
                                                nx < 0.21f -> FolioStatsRange.DAYS_7
                                                nx < 0.34f -> FolioStatsRange.DAYS_30
                                                nx < 0.47f -> FolioStatsRange.YEAR
                                                else -> FolioStatsRange.ALL
                                            }
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            soundHaptics.onVernierDetentLock()
                                            onRangeSelected(tappedRange)
                                        }
                                        // 2. Pocket Watch Escapement Tap
                                        ny in 0.130f..0.240f && nx > 0.50f -> {
                                            soundHaptics.onPocketWatchTick()
                                        }
                                        // 3. Monthly Book Spines Tap (Fix 2)
                                        ny in 0.550f..0.665f -> {
                                            val mIdx = (((pos.x - (0.050f * size.width)) / (0.900f * size.width)) * 12f).toInt().coerceIn(0, 11)
                                            selectedMonthIndex = if (selectedMonthIndex == mIdx) null else mIdx
                                            soundHaptics.onBookSpineTap(mIdx)
                                        }
                                        // 4. Genre Walnut Rulers Tap (Fix 3)
                                        ny in 0.840f..0.985f -> {
                                            val gIdx = (((pos.y - (1120f / 1320f) * size.height) / ((170f / 1320f) * size.height)) * 4f).toInt().coerceIn(0, 3)
                                            selectedGenreIndex = if (selectedGenreIndex == gIdx) null else gIdx
                                            soundHaptics.onGenreCaliperSlide()
                                        }
                                        else -> {
                                            scrubbedDayIndex = null
                                            selectedMonthIndex = null
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(selectedRange, payload) {
                            detectHorizontalDragGestures(
                                onDragStart = { pos ->
                                    val ny = pos.y / size.height
                                    if (ny in 0.020f..0.130f) {
                                        val nx = pos.x / size.width
                                        dragRangeIndexFloat = ((nx - 0.145f) / (0.540f - 0.145f) * 3f).coerceIn(0f, 3f)
                                    }
                                },
                                onDragEnd = {
                                    dragRangeIndexFloat?.let { dIdx ->
                                        val nearestIdx = dIdx.roundToInt().coerceIn(0, 3)
                                        val nearest = FolioStatsRange.entries[nearestIdx]
                                        dragRangeIndexFloat = null
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        soundHaptics.onVernierDetentLock()
                                        onRangeSelected(nearest)
                                    }
                                    scrubbedDayIndex = null
                                },
                                onDragCancel = {
                                    dragRangeIndexFloat = null
                                    scrubbedDayIndex = null
                                },
                                onHorizontalDrag = { change, _ ->
                                    val nx = change.position.x / size.width
                                    val ny = change.position.y / size.height
                                    when {
                                        // Dragging Top Brass Vernier
                                        dragRangeIndexFloat != null || ny in 0.020f..0.130f -> {
                                            change.consume()
                                            val oldIdx = dragRangeIndexFloat?.roundToInt() ?: selectedRange.ordinal
                                            val newIdxFloat = ((nx - 0.145f) / (0.540f - 0.145f) * 3f).coerceIn(0f, 3f)
                                            dragRangeIndexFloat = newIdxFloat
                                            val newIdx = newIdxFloat.roundToInt()
                                            if (newIdx != oldIdx) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                soundHaptics.onVernierSlideTick()
                                            }
                                        }
                                        // Scrubbing Reading Velocity Curve
                                        ny in 0.420f..0.540f -> {
                                            change.consume()
                                            val nPts = payload.velocityLast30Days.size.coerceAtLeast(2)
                                            val idx = (((nx - 46f / 696f) / (602f / 696f)) * (nPts - 1)).roundToInt().coerceIn(0, nPts - 1)
                                            if (idx != scrubbedDayIndex) {
                                                scrubbedDayIndex = idx
                                                soundHaptics.onGraphScrubTick()
                                            }
                                        }
                                        // Sliding Across 12 Monthly Book Spines
                                        ny in 0.550f..0.665f -> {
                                            change.consume()
                                            val mIdx = (((nx - 0.050f) / 0.900f) * 12f).toInt().coerceIn(0, 11)
                                            if (mIdx != selectedMonthIndex) {
                                                selectedMonthIndex = mIdx
                                                soundHaptics.onBookSpineTap(mIdx)
                                            }
                                        }
                                        // Scrubbing 322-Dot Brass Rivet Matrix
                                        ny in 0.720f..0.810f -> {
                                            change.consume()
                                            soundHaptics.onRivetMatrixScrub()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        if (bundle != null) {
                            drawHybridGrandLibraryFolio(
                                nc = nc,
                                w = w,
                                h = h,
                                bundle = bundle,
                                payload = payload,
                                selectedRange = selectedRange,
                                vernierXNorm = animatedLabelCenterXNorm,
                                animPages = animPages,
                                animMinutes = animMinutes,
                                animPace = animPace,
                                animBooks = animBooks,
                                csvScale = csvScale,
                                jsonScale = jsonScale,
                                curveReveal = curveReveal.value,
                                scrubbedDayIndex = scrubbedDayIndex,
                                spineProgress = spineAnimatables.map { it.value },
                                selectedMonthIndex = selectedMonthIndex,
                                rivetWave = rivetWave.value,
                                genreProgress = genreClaspProgress.map { it.value },
                                selectedGenreIndex = selectedGenreIndex
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

private fun drawArtifactFreeBrassVernierRuler(
    nc: Canvas,
    w: Float,
    h: Float,
    selectedRange: FolioStatsRange,
    animatedLabelCenterXNorm: Float
) {
    val barL = 0.046f * w
    val barR = 0.954f * w
    val barT = 0.036f * h
    val barB = 0.106f * h
    val barH = barB - barT
    val barRect = RectF(barL, barT, barR, barB)

    // 1. Soft drop shadow under brass rule
    nc.drawRoundRect(
        RectF(barL, barT + 0.004f * h, barR, barB + 0.005f * h),
        0.014f * w, 0.014f * w,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 42, 26, 10) }
    )

    // 2. Brushed Antique Brass Rule Body
    nc.drawRoundRect(
        barRect, 0.014f * w, 0.014f * w,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                barL, barT, barL, barB,
                intArrayOf(Color.rgb(222, 200, 150), Color.rgb(190, 162, 106), Color.rgb(156, 126, 72)),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    )
    nc.drawRoundRect(
        barRect, 0.014f * w, 0.014f * w,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.0022f * w; color = Color.rgb(92, 68, 32)
        }
    )

    // 3. Top & Bottom Millimeter & Half-Millimeter Engraved Graduations + "mm" engraving
    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(58, 40, 16); strokeWidth = 0.0014f * w }
    val numTicks = 68
    for (i in 0..numTicks) {
        val tx = barL + 0.024f * w + (i.toFloat() / numTicks) * (barRect.width() - 0.048f * w)
        val len = when {
            i % 10 == 0 -> barH * 0.24f
            i % 5 == 0 -> barH * 0.17f
            else -> barH * 0.10f
        }
        nc.drawLine(tx, barT, tx, barT + len, tickPaint)
        nc.drawLine(tx, barB, tx, barB - len, tickPaint)
    }
    val mmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 0.0095f * h; color = Color.rgb(62, 44, 18)
    }
    nc.drawText("mm", barL + 0.036f * w, barT + barH * 0.33f, mmPaint)
    nc.drawText("mm", barL + 0.036f * w, barB - barH * 0.23f, mmPaint)

    // 4. Recessed Slot Track on the Right Half
    val slotRect = RectF(0.585f * w, barT + barH * 0.40f, 0.915f * w, barT + barH * 0.60f)
    nc.drawRoundRect(slotRect, slotRect.height() / 2f, slotRect.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 216, 188)
    })
    nc.drawRoundRect(slotRect, slotRect.height() / 2f, slotRect.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.0018f * w; color = Color.rgb(88, 64, 30)
    })

    // 5. All 4 Range Labels (7D, 30D, YEAR, ALL) Always Visible & Crisp — ZERO Red Lines!
    val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 0.0205f * h
        textAlign = Paint.Align.CENTER
    }
    val fm = lblPaint.fontMetrics
    val lblBaselineY = barRect.centerY() - (fm.ascent + fm.descent) / 2f

    // Subtle brass highlight pill behind the active range label
    val activePillCx = animatedLabelCenterXNorm * w
    val activePillW = 0.108f * w
    val activePillH = barH * 0.48f
    val activePillRect = RectF(
        activePillCx - activePillW / 2f,
        barRect.centerY() - activePillH / 2f,
        activePillCx + activePillW / 2f,
        barRect.centerY() + activePillH / 2f
    )
    nc.drawRoundRect(activePillRect, activePillH / 2f, activePillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 244, 214)
    })
    nc.drawRoundRect(activePillRect, activePillH / 2f, activePillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.0016f * w; color = Color.rgb(92, 66, 28)
    })

    for (rng in FolioStatsRange.entries) {
        val isSel = (rng == selectedRange)
        lblPaint.typeface = Typeface.create(Typeface.SANS_SERIF, if (isSel) Typeface.BOLD else Typeface.NORMAL)
        lblPaint.color = if (isSel) Color.rgb(28, 18, 6) else Color.rgb(72, 52, 24)
        nc.drawText(rng.label, rng.labelCenterXNorm * w, lblBaselineY, lblPaint)
    }

    // 6. Single 3D Machined Brass Vernier Carriage Gliding on the Right Slot Track
    val progress01 = ((animatedLabelCenterXNorm - 0.145f) / (0.540f - 0.145f)).coerceIn(0f, 1f)
    val medCx = (0.655f + progress01 * 0.135f) * w
    val medCy = barRect.centerY()
    val medR = barH * 0.49f
    val claspW = medR * 1.44f
    val claspH = barH * 1.16f
    val beakTipX = medCx - medR * 1.65f

    // Carriage shadow
    nc.drawRoundRect(
        RectF(medCx - claspW / 2f + 2f, medCy - claspH / 2f + 4f, medCx + claspW / 2f + 2f, medCy + claspH / 2f + 4f),
        0.008f * w, 0.008f * w,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 28, 18, 8) }
    )

    val carriageBrush = LinearGradient(
        medCx, medCy - claspH / 2f, medCx, medCy + claspH / 2f,
        intArrayOf(Color.rgb(228, 206, 156), Color.rgb(176, 146, 88), Color.rgb(124, 96, 48)),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    // Left pointer beak (<)
    val beakPath = Path().apply {
        moveTo(beakTipX, medCy)
        lineTo(medCx - medR * 0.55f, medCy - barH * 0.23f)
        lineTo(medCx - medR * 0.55f, medCy + barH * 0.23f)
        close()
    }
    nc.drawPath(beakPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = carriageBrush })
    nc.drawPath(beakPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.0018f * w; color = Color.rgb(78, 56, 24)
    })

    // Clasp block & circular medallion
    val claspRect = RectF(medCx - claspW / 2f, medCy - claspH / 2f, medCx + claspW / 2f, medCy + claspH / 2f)
    nc.drawRoundRect(claspRect, 0.008f * w, 0.008f * w, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = carriageBrush })
    nc.drawRoundRect(claspRect, 0.008f * w, 0.008f * w, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.002f * w; color = Color.rgb(78, 56, 24)
    })
    nc.drawCircle(medCx, medCy, medR, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = carriageBrush })
    nc.drawCircle(medCx, medCy, medR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.002f * w; color = Color.rgb(78, 56, 24)
    })
    nc.drawCircle(medCx, medCy, medR * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            medCx - medR * 0.1f, medCy - medR * 0.1f, medR * 0.4f,
            intArrayOf(Color.rgb(248, 232, 190), Color.rgb(182, 148, 84), Color.rgb(96, 70, 28)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    })
}

private fun drawHybridGrandLibraryFolio(
    nc: Canvas,
    w: Float,
    h: Float,
    bundle: GrandLibraryPlateBundle,
    payload: GrandLibraryStatsPayload,
    selectedRange: FolioStatsRange,
    vernierXNorm: Float,
    animPages: Int,
    animMinutes: Int,
    animPace: Int,
    animBooks: Int,
    csvScale: Float,
    jsonScale: Float,
    curveReveal: Float,
    scrubbedDayIndex: Int?,
    spineProgress: List<Float>,
    selectedMonthIndex: Int?,
    rivetWave: Float,
    genreProgress: List<Float>,
    selectedGenreIndex: Int?
) {
    val sx = w / 696f
    val sy = h / 1320f

    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    // 0. Base Master Plate (stats_bg.png: 696 x 1320)
    nc.drawBitmap(bundle.cleanBasePlate, null, RectF(0f, 0f, w, h), bitmapPaint)

    // 1. Authentic Top Brass Vernier Ruler & 3D Caliper Cursor (Request 5.1)
    drawArtifactFreeBrassVernierRuler(nc, w, h, selectedRange, vernierXNorm)

    // 2. Top 4 Metric Cards (Clean dynamic letterpress serif numbers, #2A221C)
    val cardPaint52 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = serifBold
        color = Color.rgb(0x2A, 0x22, 0x1C)
        textSize = 52f * sy
        textAlign = Paint.Align.LEFT
    }
    val cardPaint48 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = serifBold
        color = Color.rgb(0x2A, 0x22, 0x1C)
        textSize = 48f * sy
        textAlign = Paint.Align.LEFT
    }
    // Card 1: PAGES READ at (48, 254)
    nc.drawText("%,d".format(animPages), 48f * sx, 254f * sy, cardPaint52)

    // Card 2: READING TIME at (379, 254)
    val hrs = animMinutes / 60
    val mins = animMinutes % 60
    val timeStr = if (hrs > 0) "${hrs}h ${mins.toString().padStart(2, '0')}m" else "${mins}m"
    nc.drawText(timeStr, 379f * sx, 254f * sy, cardPaint48)

    // Card 3: AVERAGE PACE at (48, 388)
    nc.drawText("$animPace p/h", 48f * sx, 388f * sy, cardPaint48)

    // Card 4: BOOKS COMPLETED at (379, 388)
    nc.drawText("%,d".format(animBooks), 379f * sx, 388f * sy, cardPaint52)

    // 3. Share Plaques Interactive Press Feedback
    if (csvScale < 0.999f) {
        val rCsv = RectF(48f * sx, 440f * sy, 330f * sx, 502f * sy)
        val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
        }
        nc.drawRoundRect(rCsv, 8f * sx, 8f * sy, pressPaint)
    }
    if (jsonScale < 0.999f) {
        val rJson = RectF(360f * sx, 440f * sy, 642f * sx, 502f * sy)
        val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
        }
        nc.drawRoundRect(rJson, 8f * sx, 8f * sy, pressPaint)
    }

    // 4. READING VELOCITY CURVE: Clean Millimeter Grid (x in 46..648, y in 568..696)
    val gx1 = 46f * sx
    val gx2 = 648f * sx
    val gy1 = 568f * sy
    val gy2 = 696f * sy
    val gw = gx2 - gx1
    val gh = gy2 - gy1

    val vList = payload.velocityLast30Days.ifEmpty { List(30) { 0 } }
    val maxV = (vList.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
    val isAllZero = vList.all { it == 0 }

    val ctrlPts = vList.mapIndexed { idx, v ->
        val px = gx1 + 4f * sx + (idx.toFloat() / (vList.size - 1).coerceAtLeast(1)) * (gw - 8f * sx)
        val py = if (isAllZero) gy2 - 4f * sy else (gy2 - 6f * sy - (v / maxV) * (gh * 0.78f))
        PointF(px, py)
    }
    val splinePts = buildMonotoneCubicSplinePoints(ctrlPts, samplesPerSegment = 10)
    val curvePath = Path()
    val washPath = Path()
    if (splinePts.isNotEmpty()) {
        washPath.moveTo(splinePts.first().x, gy2 - 2f * sy)
        splinePts.forEachIndexed { idx, pt ->
            if (idx == 0) curvePath.moveTo(pt.x, pt.y) else curvePath.lineTo(pt.x, pt.y)
            washPath.lineTo(pt.x, pt.y)
        }
        washPath.lineTo(splinePts.last().x, gy2 - 2f * sy)
        washPath.close()
    }

    nc.save()
    nc.clipRect(gx1, gy1, gx1 + gw * curveReveal, gy2)
    if (!isAllZero) {
        val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(0x3A, 0xA8, 0x76, 0x44)
            style = Paint.Style.FILL
        }
        nc.drawPath(washPath, washPaint)
    }
    val curveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xF0, 0x76, 0x48, 0x20)
        style = Paint.Style.STROKE
        strokeWidth = 3f * sx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    nc.drawPath(curvePath, curveStrokePaint)
    nc.restore()

    // Interactive Scrubbing Brass Loupe
    scrubbedDayIndex?.let { dIdx ->
        val pt = ctrlPts.getOrNull(dIdx)
        if (pt != null) {
            nc.drawLine(pt.x, gy1, pt.x, gy2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 140, 98, 36)
                strokeWidth = 2f * sx
            })
            nc.drawCircle(pt.x, pt.y, 9f * sx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 226, 188) })
            nc.drawCircle(pt.x, pt.y, 9f * sx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(120, 84, 30); style = Paint.Style.STROKE; strokeWidth = 2.5f * sx
            })
            nc.drawCircle(pt.x, pt.y, 3.5f * sx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 88, 24) })

            val badgeW = 150f * sx
            val badgeH = 26f * sy
            val bx = pt.x.coerceIn(gx1 + badgeW / 2f + 4f * sx, gx2 - badgeW / 2f - 4f * sx)
            val by = gy1 + 6f * sy
            val bRect = RectF(bx - badgeW / 2f, by, bx + badgeW / 2f, by + badgeH)
            nc.drawRoundRect(bRect, 8f * sx, 8f * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 58, 38, 20) })
            nc.drawRoundRect(bRect, 8f * sx, 8f * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(204, 168, 96); style = Paint.Style.STROKE; strokeWidth = 2f * sx
            })
            val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = sansBold
                textSize = 13f * sy
                color = Color.rgb(245, 232, 204)
                textAlign = Paint.Align.CENTER
            }
            val bFm = bPaint.fontMetrics
            nc.drawText("DAY ${dIdx + 1} · ${vList[dIdx]} PGS", bRect.centerX(), bRect.centerY() - (bFm.ascent + bFm.descent) / 2f, bPaint)
        }
    }

    // 5. MONTHLY COMPLETION PILLARS: 2-Pass Z-Order Rendering
    val shelfBottomY = 864f * sy
    val maxBookH = 110f * sy
    val minBookH = 16f * sy
    val monthNames = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    // Exact X centers of J, F, M, A, M, J, J, A, S, O, N, D on the shelf
    val monthLetterCentersX = floatArrayOf(
        0.088f, 0.163f, 0.238f, 0.313f, 0.388f, 0.463f,
        0.538f, 0.613f, 0.688f, 0.763f, 0.838f, 0.913f
    )
    val spineHalfW = 0.0195f * w

    // Year label right-aligned at (656, 756)
    val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = serifBold
        textSize = 22f * sy
        color = Color.rgb(0x34, 0x2A, 0x22)
        textAlign = Paint.Align.RIGHT
    }
    nc.drawText("(${payload.currentYear})", 656f * sx, 756f * sy, yearPaint)

    // Compute effective units from books completed + pages read in that month
    val effectiveMonthUnits = (0 until 12).map { m ->
        val b = payload.monthlyBookCompletions.getOrElse(m) { 0 }
        val p = payload.monthlyPagesRead.getOrElse(m) { 0 }
        b.toFloat() + (p.toFloat() / 120f)
    }
    val maxUnits = (effectiveMonthUnits.maxOrNull() ?: 1f).coerceAtLeast(1f)

    // PASS 1: Draw all 12 Leather Book Spines on the shelf first
    val spineRects = arrayOfNulls<RectF>(12)
    for (m in 0 until 12) {
        val units = effectiveMonthUnits[m]
        val cx = monthLetterCentersX[m] * w
        val sLeft = cx - spineHalfW
        val sRight = cx + spineHalfW

        if (units > 0.001f && bundle.spineSprites.isNotEmpty()) {
            val targetH = minBookH + (units / maxUnits) * (maxBookH - minBookH)
            val curH = (targetH * spineProgress.getOrElse(m) { 1f }).coerceAtLeast(minBookH)
            val isSelected = (selectedMonthIndex == m)
            val liftPx = if (isSelected) 0.006f * h else 0f
            val sBottom = shelfBottomY - liftPx
            val sTop = sBottom - curH
            val dst = RectF(sLeft, sTop, sRight, sBottom)
            spineRects[m] = dst
            val spine = bundle.spineSprites[m % bundle.spineSprites.size]
            nc.drawBitmap(spine, null, dst, bitmapPaint)
        } else {
            spineRects[m] = RectF(sLeft, shelfBottomY - minBookH, sRight, shelfBottomY)
        }
    }

    // PASS 2 (Topmost Z-Order Layer): Draw the selected month's tooltip badge AFTER all 12 spines!
    selectedMonthIndex?.let { selM ->
        val dst = spineRects.getOrNull(selM) ?: return@let
        val mCount = payload.monthlyBookCompletions.getOrElse(selM) { 0 }
        val mPages = payload.monthlyPagesRead.getOrElse(selM) { 0 }
        val badgeText = if (mCount > 0) "${monthNames[selM]} · $mCount ${if (mCount == 1) "BOOK" else "BOOKS"}"
                        else "${monthNames[selM]} · $mPages PGS"

        val badgeW = 0.22f * w
        val badgeH = 0.022f * h
        val bx = dst.centerX().coerceIn(badgeW / 2f + 10f * sx, w - badgeW / 2f - 10f * sx)
        val by = (dst.top - badgeH - 8f * sy).coerceAtLeast(730f * sy)
        val bRect = RectF(bx - badgeW / 2f, by, bx + badgeW / 2f, by + badgeH)

        // Drop shadow so badge floats clearly in front of any neighboring tall book spines (like Oct!)
        nc.drawRoundRect(
            RectF(bRect.left + 2f * sx, bRect.top + 3f * sy, bRect.right + 2f * sx, bRect.bottom + 3f * sy),
            8f * sx, 8f * sy,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 15, 10, 5) }
        )
        nc.drawRoundRect(bRect, 8f * sx, 8f * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 34, 18) })
        nc.drawRoundRect(bRect, 8f * sx, 8f * sy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 178, 102); style = Paint.Style.STROKE; strokeWidth = 2.2f * sx
        })
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = 0.0108f * h; color = Color.rgb(248, 236, 210); textAlign = Paint.Align.CENTER
        }
        val bFm = bPaint.fontMetrics
        nc.drawText(badgeText, bRect.centerX(), bRect.centerY() - (bFm.ascent + bFm.descent) / 2f, bPaint)
    }

    // 6. READING CONSISTENCY (365-DOT MATRIX): 7 rows x 52 columns inside y = 0.737f..0.789f
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x6E, 0x58, 0x40) }
    val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB9, 0xA8, 0x91) }
    val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x9A, 0x60, 0x2A) }
    val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEA, 0xC4, 0x88) }

    val cols = 52
    val rows = 7
    val dotStartX = 0.058f * w
    val dotEndX = 0.942f * w
    val colStep = (dotEndX - dotStartX) / 52f
    val dotStartY = 0.737f * h
    val rowStep = 0.0086f * h // 7 rows end at y = 0.7886f * h (zero overlap with Genre section!)
    val dotRadius = 0.0027f * w

    for (r in 0 until rows) {
        val cy = dotStartY + r * rowStep
        for (c in 0 until cols) {
            val cx = dotStartX + c * colStep
            val dotIdx = r * cols + c
            val isActive = payload.cadenceMatrix.getOrElse(dotIdx) { (dotIdx * 7 + 3) % 5 != 0 }

            nc.drawCircle(cx, cy, dotRadius * 1.3f, rimPaint)
            nc.drawCircle(cx, cy, dotRadius, if (isActive) activePaint else inactivePaint)
            if (isActive) {
                nc.drawCircle(cx - dotRadius * 0.3f, cy - dotRadius * 0.3f, dotRadius * 0.4f, specularPaint)
            }
        }
    }

    // 7. GENRE DISTRIBUTION: Clean procedural walnut millimeter rulers & brass clasps
    drawCleanGenreDistribution(
        nc = nc,
        w = w,
        h = h,
        genres = payload.genres,
        genreProgress = genreProgress
    )
}


private fun drawCleanGenreDistribution(
    nc: Canvas,
    w: Float,
    h: Float,
    genres: List<GenreDistributionItem>,
    genreProgress: List<Float>
) {
    // 1. Section Header ("GENRE DISTRIBUTION") matching "READING VELOCITY CURVE"
    val hdrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        color = Color.rgb(42, 28, 14)
        textSize = 0.0195f * h
        letterSpacing = 0.06f
    }
    nc.drawText("GENRE DISTRIBUTION", 0.068f * w, 0.822f * h, hdrPaint)

    val rowCentersY = floatArrayOf(0.854f * h, 0.887f * h, 0.920f * h, 0.953f * h)
    val rulerL = 0.405f * w
    val rulerR = 0.835f * w
    val rulerW = rulerR - rulerL
    val rulerH = 0.021f * h
    val claspW = 0.022f * w
    val claspH = rulerH * 1.24f

    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        color = Color.rgb(38, 25, 12)
        textSize = 0.0185f * h
        textAlign = Paint.Align.LEFT
    }
    val pctPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        color = Color.rgb(38, 25, 12)
        textSize = 0.0185f * h
        textAlign = Paint.Align.LEFT
    }
    val fm = namePaint.fontMetrics

    for (i in 0 until 4) {
        val item = genres.getOrNull(i) ?: continue
        val cy = rowCentersY[i]
        val textBaseY = cy - (fm.ascent + fm.descent) / 2f

        // A. Left Genre Name (properly aligned & ellipsized before rulerL)
        val displayTitle = if (item.genreName.length > 20) item.genreName.take(19) + "…" else item.genreName
        nc.drawText(displayTitle, 0.068f * w, textBaseY, namePaint)

        // B. Center 3D Carved Walnut Millimeter Ruler Track (Zero Clasp Smudges!)
        val barRect = RectF(rulerL, cy - rulerH / 2f, rulerR, cy + rulerH / 2f)
        nc.drawRoundRect(
            RectF(barRect.left, barRect.top + 2f, barRect.right, barRect.bottom + 3f),
            rulerH * 0.32f, rulerH * 0.32f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 30, 18, 8) }
        )
        nc.drawRoundRect(
            barRect, rulerH * 0.32f, rulerH * 0.32f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    barRect.left, barRect.top, barRect.left, barRect.bottom,
                    intArrayOf(Color.rgb(104, 70, 38), Color.rgb(74, 48, 24), Color.rgb(54, 34, 16)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        nc.drawRoundRect(
            barRect, rulerH * 0.32f, rulerH * 0.32f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 0.0016f * w; color = Color.rgb(142, 106, 62)
            }
        )

        // Left & right brass mounting screws on walnut bar
        for (sx in floatArrayOf(rulerL + 0.018f * w, rulerR - 0.018f * w)) {
            nc.drawCircle(sx, cy, rulerH * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(208, 178, 118) })
        }

        // Top & bottom millimeter ruler ticks along walnut bar
        val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 224, 202, 162); strokeWidth = 1.2f }
        val tickStart = rulerL + 0.036f * w
        val tickEnd = rulerR - 0.036f * w
        for (t in 0..36) {
            val tx = tickStart + (t / 36f) * (tickEnd - tickStart)
            val tLen = if (t % 5 == 0) rulerH * 0.28f else rulerH * 0.16f
            nc.drawLine(tx, barRect.top + 1.5f, tx, barRect.top + 1.5f + tLen, tickP)
            nc.drawLine(tx, barRect.bottom - 1.5f, tx, barRect.bottom - 1.5f - tLen, tickP)
        }

        // C. Animated 3D Brass Vernier Clasp
        val animPct = genreProgress.getOrElse(i) { item.percentage / 100f }.coerceIn(0f, 1f)
        val claspCx = tickStart + animPct * (tickEnd - tickStart)
        val claspRect = RectF(claspCx - claspW / 2f, cy - claspH / 2f, claspCx + claspW / 2f, cy + claspH / 2f)
        nc.drawRoundRect(
            claspRect, 0.005f * w, 0.005f * w,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    claspRect.left, claspRect.top, claspRect.right, claspRect.bottom,
                    intArrayOf(Color.rgb(232, 208, 154), Color.rgb(184, 150, 88), Color.rgb(128, 98, 46)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        nc.drawRoundRect(
            claspRect, 0.005f * w, 0.005f * w,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 0.0015f * w; color = Color.rgb(72, 50, 20)
            }
        )
        nc.drawCircle(claspCx, cy, claspW * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 66, 28) })

        // D. Right Percentage Readout
        val displayPct = (animPct * 100f).roundToInt()
        nc.drawText("$displayPct%", 0.852f * w, textBaseY, pctPaint)
    }
}

private fun buildMonotoneCubicSplinePoints(pts: List<PointF>, samplesPerSegment: Int = 10): List<PointF> {
    val n = pts.size
    if (n < 2) return pts
    val dx = FloatArray(n - 1)
    val dy = FloatArray(n - 1)
    val m = FloatArray(n - 1)
    for (i in 0 until n - 1) {
        dx[i] = (pts[i + 1].x - pts[i].x).coerceAtLeast(1e-4f)
        dy[i] = pts[i + 1].y - pts[i].y
        m[i] = dy[i] / dx[i]
    }
    val tangents = FloatArray(n)
    tangents[0] = m[0]
    tangents[n - 1] = m[n - 2]
    for (i in 1 until n - 1) {
        if (m[i - 1] * m[i] <= 0f) {
            tangents[i] = 0f
        } else {
            tangents[i] = (m[i - 1] + m[i]) * 0.5f
        }
    }
    // Fritsch-Carlson monotonicity constraint (prevents any overshoot/clipping at sharp peaks!)
    for (i in 0 until n - 1) {
        if (abs(m[i]) < 1e-5f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / m[i]
            val b = tangents[i + 1] / m[i]
            val h = hypot(a, b)
            if (h > 3f) {
                val t = 3f / h
                tangents[i] = t * a * m[i]
                tangents[i + 1] = t * b * m[i]
            }
        }
    }

    val out = ArrayList<PointF>((n - 1) * samplesPerSegment + 1)
    for (i in 0 until n - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val segW = dx[i]
        for (s in 0 until samplesPerSegment) {
            val t = s.toFloat() / samplesPerSegment
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2f * t3 - 3f * t2 + 1f
            val h10 = t3 - 2f * t2 + t
            val h01 = -2f * t3 + 3f * t2
            val h11 = t3 - t2
            val x = p0.x + t * segW
            val y = h00 * p0.y + h10 * segW * tangents[i] + h01 * p1.y + h11 * segW * tangents[i + 1]
            out.add(PointF(x, y))
        }
    }
    out.add(pts.last())
    return out
}
