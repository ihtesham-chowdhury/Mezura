package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Universal grammar and pluralization helper:
 * formatPlural(1, "DAY", "DAYS") -> "1 DAY"
 * formatPlural(5, "BOOK", "BOOKS") -> "5 BOOKS"
 */
fun formatPlural(count: Int, singular: String, plural: String? = null): String {
    val abs = kotlin.math.abs(count)
    val word = if (abs == 1) singular else (plural ?: "${singular}S")
    return "$count $word"
}

/**
 * Calculate inclusive day span between two timestamps.
 */
fun getInclusiveDaySpan(start: Long, end: Long): Int {
    val cal1 = java.util.Calendar.getInstance().apply {
        timeInMillis = start
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val cal2 = java.util.Calendar.getInstance().apply {
        timeInMillis = end
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val diffDays = TimeUnit.MILLISECONDS.toDays(max(0L, cal2.timeInMillis - cal1.timeInMillis)).toInt() + 1
    return max(1, diffDays)
}

/**
 * Strict 1-to-7 serial ordering matching the 7 active design styles (01..07).
 */
enum class BookStatsDesignStyle(
    val serialNumber: Int,      // Strictly 1..7
    val masterPlateId: Int,     // Asset file index (1, 2, 3, 4, 5, 6, 8)
    val pillLabel: String,      // "01 · ARCHIVAL" .. "07 · OBSIDIAN"
    val headerTitle: String     // "1. ..." .. "7. Minimalist Obsidian Editorial"
) {
    STYLE_1_ARCHIVAL_SPLIT_FLAP(1, 1, "01 · ARCHIVAL", "1. Archival Brass & Split-Flap"),
    STYLE_2_PERFORATED_LED(2, 2, "02 · LED MATRIX", "2. Gunmetal & Dot-Matrix LED"),
    STYLE_3_PARCHMENT_CLIPBOARD(3, 3, "03 · CLIPBOARD", "3. Warm Parchment & Charcoal Bento"),
    STYLE_4_CELESTIAL_ATLAS(4, 4, "04 · CELESTIAL", "4. Astrolabe & Celestial Atlas"),
    STYLE_5_DRAFTING_BLUEPRINT(5, 5, "05 · BLUEPRINT", "5. Drafting Blueprint Ledger"),
    STYLE_6_FROSTED_GLASS(6, 6, "06 · GLASS", "6. Smoked Frosted Glass & Bokeh"),
    STYLE_7_OBSIDIAN_EDITORIAL(7, 8, "07 · OBSIDIAN", "7. Minimalist Obsidian Editorial");

    val displayTitle: String get() = headerTitle
    val shortBadge: String get() = pillLabel

    val aspectRatio: Float
        get() = when (this) {
            STYLE_1_ARCHIVAL_SPLIT_FLAP -> 1122f / 1402f
            STYLE_7_OBSIDIAN_EDITORIAL -> 2160f / 2880f
            else -> 1792f / 2400f
        }

    companion object {
        val SERIAL_LIST: List<BookStatsDesignStyle> = entries
    }
}

/**
 * Immutable snapshot of all reading metrics required to render any of the 7 Mezura designs.
 */
data class BookCompletionStats(
    val bookId: String,
    val bookTitle: String,
    val authorName: String,
    val coverBitmap: ImageBitmap? = null,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val starRating: Int, // 1..5
    val pagesRead: Int,
    val totalReadingTimeSeconds: Long,
    val averagePagesPerHour: Int,
    val maxPagesInOneDay: Int,
    val maxMinutesInOneDay: Int,
    val longestStreakDays: Int
) {
    val totalPagesRead: Int
        get() = pagesRead

    val mostPagesInOneDay: Int
        get() = maxPagesInOneDay

    val mostMinutesInOneDay: Int
        get() = maxMinutesInOneDay

    val totalReadingTimeMillis: Long
        get() = totalReadingTimeSeconds * 1000L

    val totalReadingTimeFormatted: String
        get() {
            val totalMinutes = (totalReadingTimeMillis / 60_000L).toInt()
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return if (hours > 0) {
                "%02dh %02dm".format(hours, mins)
            } else {
                "%02dm".format(mins.coerceAtLeast(1))
            }
        }

    private val dateFormatter: SimpleDateFormat
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    val startDateFormatted: String
        get() = dateFormatter.format(Date(startDateMillis)).uppercase(Locale.US)

    val endDateFormatted: String
        get() = dateFormatter.format(Date(endDateMillis)).uppercase(Locale.US)

    val totalDaysSpan: Int
        get() {
            val diffMs = max(0L, endDateMillis - startDateMillis)
            return max(1, TimeUnit.MILLISECONDS.toDays(diffMs).toInt() + 1)
        }

    val hoursPart: Int
        get() = (totalReadingTimeSeconds / 3600L).toInt()

    val minutesPart: Int
        get() = ((totalReadingTimeSeconds % 3600L) / 60L).toInt()

    val secondsPart: Int
        get() = (totalReadingTimeSeconds % 60L).toInt()

    val hoursPadded: String
        get() = hoursPart.toString().padStart(2, '0')

    val minutesPadded: String
        get() = minutesPart.toString().padStart(2, '0')

    val secondsPadded: String
        get() = secondsPart.toString().padStart(2, '0')

    val formattedTimeLower: String
        get() = totalReadingTimeFormatted.lowercase()

    val formattedTimeUpper: String
        get() = totalReadingTimeFormatted.uppercase()

    val readingDurationFormatted: String
        get() = if (hoursPart > 0) "${hoursPart}H ${minutesPart}M" else "${minutesPart}M"

    val completedDateFormatted: String
        get() = endDateFormatted
}

sealed interface BookCompletionFlowState {
    data object Idle : BookCompletionFlowState

    /**
     * Stage 1: Triggered when a book reaches 100% or user taps Rate.
     */
    data class AwaitingStarRating(
        val book: BookItem,
        val initialRating: Int = 5
    ) : BookCompletionFlowState

    /**
     * Stage 2: Triggered right after star rating submission.
     */
    data class PromptToShareStats(
        val stats: BookCompletionStats
    ) : BookCompletionFlowState

    /**
     * Stage 3: Opens the 9-Design-Language Studio in strict 1..9 serial order.
     */
    data class SelectingShareStyle(
        val stats: BookCompletionStats,
        val selectedStyle: BookStatsDesignStyle = BookStatsDesignStyle.STYLE_1_ARCHIVAL_SPLIT_FLAP
    ) : BookCompletionFlowState
}

object BookStatsCalculator {

    fun loadCoverImageBitmap(context: Context, book: BookItem): ImageBitmap? {
        val cachedKey = book.customCoverPath ?: book.coverLocalPath
        if (cachedKey != null) {
            val cached = CoverImageCache.get(cachedKey)
            if (cached != null) return cached
        }
        val file = book.resolveCoverFile(context) ?: return null
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            bmp?.asImageBitmap()?.also {
                if (cachedKey != null) CoverImageCache.put(cachedKey, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun compute(
        context: Context,
        book: BookItem,
        sessions: List<ReadingSessionLog>,
        overrideRating: Int? = null,
        overrideFinishTime: Long? = null
    ): BookCompletionStats {
        val bookSessions = sessions.filter { it.bookId == book.id }.sortedBy { it.timestampMillis }
        val rawStart = if (bookSessions.isNotEmpty()) bookSessions.first().timestampMillis else book.startedEpochMillis
        val rawEnd = overrideFinishTime ?: book.finishedEpochMillis ?: System.currentTimeMillis()
        val startMs = min(rawStart, rawEnd)
        val endMs = max(rawStart, rawEnd)

        val spanDays = getInclusiveDaySpan(startMs, endMs)
        val pagesRead = book.totalPages.coerceAtLeast(1)
        val fallbackPace = 40 // default 40 pages / hour

        val realSessions = bookSessions.filter { it.pagesRead > 0 }
        val maxPagesDay: Int
        val maxMinsDay: Int
        val totalSec: Long
        val avgPgsPerHour: Int
        val longestStreak: Int

        if (realSessions.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val logsByDay = realSessions.groupBy { sdf.format(Date(it.timestampMillis)) }

            val totalSessionPages = realSessions.sumOf { it.pagesRead }
            val totalSessionSeconds = realSessions.sumOf {
                if (it.durationSeconds > 0) it.durationSeconds else (it.durationMinutes * 60L)
            }

            val effectivePace = if (totalSessionSeconds > 0) {
                (totalSessionPages / (totalSessionSeconds / 3600.0)).coerceIn(10.0, 150.0)
            } else fallbackPace.toDouble()

            val untimedPages = max(0, pagesRead - totalSessionPages)
            val untimedSeconds = Math.round((untimedPages / effectivePace) * 3600.0)
            totalSec = totalSessionSeconds + untimedSeconds

            var maxP = logsByDay.values.maxOfOrNull { dayLogs -> dayLogs.sumOf { it.pagesRead } } ?: 0
            var maxS = logsByDay.values.maxOfOrNull { dayLogs ->
                dayLogs.sumOf { if (it.durationSeconds > 0) it.durationSeconds else (it.durationMinutes * 60L) }
            } ?: 0L

            if (untimedPages > 0) {
                val distributedDailyPages = ceil(pagesRead.toDouble() / spanDays).toInt()
                val distributedDailySeconds = Math.round(totalSec.toDouble() / spanDays)
                maxP = max(maxP, distributedDailyPages)
                maxS = max(maxS, distributedDailySeconds)
            }

            maxPagesDay = max(1, maxP)
            maxMinsDay = max(1, (maxS / 60L).toInt())
            val consecutiveStreak = computeLongestConsecutiveDays(logsByDay.keys)
            longestStreak = if (untimedPages > 0) max(consecutiveStreak, spanDays) else max(1, consecutiveStreak)

            val hoursFloat = totalSec / 3600.0
            avgPgsPerHour = if (hoursFloat > 0.0) {
                (pagesRead / hoursFloat).roundToInt().coerceIn(1, 150)
            } else fallbackPace
        } else {
            // NO granular daily sessions exist (e.g. marked Read or slider dragged to 100%)
            totalSec = Math.round((pagesRead.toDouble() / fallbackPace) * 3600.0)
            val distributedDailyPages = ceil(pagesRead.toDouble() / spanDays).toInt()
            val distributedDailyMinutes = max(1, Math.round(totalSec.toDouble() / 60.0 / spanDays).toInt())

            maxPagesDay = max(1, distributedDailyPages)
            maxMinsDay = distributedDailyMinutes
            longestStreak = spanDays
            avgPgsPerHour = fallbackPace
        }

        val coverBmp = loadCoverImageBitmap(context, book)
        val finalRating = (overrideRating ?: if (book.rating > 0) book.rating else 5).coerceIn(1, 5)

        return BookCompletionStats(
            bookId = book.id,
            bookTitle = book.title,
            authorName = book.author.ifEmpty { "Unknown Author" },
            coverBitmap = coverBmp,
            startDateMillis = startMs,
            endDateMillis = endMs,
            starRating = finalRating,
            pagesRead = pagesRead,
            totalReadingTimeSeconds = totalSec,
            averagePagesPerHour = avgPgsPerHour,
            maxPagesInOneDay = maxPagesDay,
            maxMinutesInOneDay = maxMinsDay,
            longestStreakDays = longestStreak
        )
    }

    private fun computeLongestConsecutiveDays(dateKeys: Set<String>): Int {
        if (dateKeys.isEmpty()) return 1
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val epochDays = dateKeys.mapNotNull { key ->
            runCatching {
                val date = sdf.parse(key) ?: return@mapNotNull null
                TimeUnit.MILLISECONDS.toDays(date.time)
            }.getOrNull()
        }.distinct().sorted()

        if (epochDays.isEmpty()) return 1
        var maxStreak = 1
        var currentStreak = 1
        for (i in 1 until epochDays.size) {
            if (epochDays[i] == epochDays[i - 1] + 1L) {
                currentStreak++
                if (currentStreak > maxStreak) maxStreak = currentStreak
            } else {
                currentStreak = 1
            }
        }
        return maxStreak
    }
}
