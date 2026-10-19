package rocks.ndot.widgets.instruments.trace

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import rocks.ndot.widgets.instruments.folio.BookStore
import rocks.ndot.widgets.instruments.habits.EntryKind
import rocks.ndot.widgets.instruments.habits.HabitMasterStore
import rocks.ndot.widgets.kit.Ink
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class ActivityType(val label: String, val color: Color) {
    FOCUS("Focus", Ink.AMBER),
    READING("Reading", Color(0xFFD4AF37)),      // Brass
    BREATHING("Breathing", Color(0xFF64B5F6)),  // Sky blue
    BREAK("Break", Color(0xFF81C784)),          // Soft sage
    METRONOME("Rhythm", Color(0xFFFFB74D)),
    HABIT("Practice", Ink.SAGE)
}

enum class SessionOutcome {
    COMPLETED,
    INTERRUPTED,
    SKIPPED,
    CANCELLED
}

data class TraceEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val activityType: ActivityType,
    val sourceInstrument: String, // "pomodoro", "folio", "breathing", "hourglass", "metronome", "habits"
    val title: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long = (endedAtMillis - startedAtMillis).coerceAtLeast(0) / 1000L,
    val outcome: SessionOutcome = SessionOutcome.COMPLETED,
    val sequenceRunId: String? = null,
    val sequenceName: String? = null,
    val metadata: String? = null // e.g. "P. 42-65", "Cyclic Sigh 5m"
) {
    val epochDay: Long get() = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(startedAtMillis),
        ZoneId.systemDefault()
    ).toLocalDate().toEpochDay()

    val formattedStartTime: String get() {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAtMillis), ZoneId.systemDefault())
        return dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
    }

    val formattedDuration: String get() {
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        return if (mins >= 60) {
            "${mins / 60}h ${mins % 60}m"
        } else if (mins > 0) {
            "${mins}m"
        } else {
            "${secs}s"
        }
    }
}

object TraceLedger {
    private const val FILE_NAME = "ndot_trace_ledger.json"
    private var cachedEvents: MutableList<TraceEvent>? = null

    private fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (cachedEvents != null) return

        val file = getFile(context)
        if (!file.exists()) {
            cachedEvents = mutableListOf()
            return
        }

        try {
            val content = file.readText()
            val arr = JSONArray(content)
            val list = mutableListOf<TraceEvent>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TraceEvent(
                        eventId = o.getString("eventId"),
                        activityType = runCatching { ActivityType.valueOf(o.getString("activityType")) }.getOrDefault(ActivityType.FOCUS),
                        sourceInstrument = o.getString("sourceInstrument"),
                        title = o.getString("title"),
                        startedAtMillis = o.getLong("startedAtMillis"),
                        endedAtMillis = o.getLong("endedAtMillis"),
                        durationSeconds = o.optLong("durationSeconds", 0L),
                        outcome = runCatching { SessionOutcome.valueOf(o.getString("outcome")) }.getOrDefault(SessionOutcome.COMPLETED),
                        sequenceRunId = o.optString("sequenceRunId").takeIf { it.isNotBlank() },
                        sequenceName = o.optString("sequenceName").takeIf { it.isNotBlank() },
                        metadata = o.optString("metadata").takeIf { it.isNotBlank() }
                    )
                )
            }
            cachedEvents = list
        } catch (e: Exception) {
            cachedEvents = mutableListOf()
        }
    }

    @Synchronized
    private fun save(context: Context) {
        val arr = JSONArray()
        cachedEvents?.forEach { e ->
            arr.put(JSONObject().apply {
                put("eventId", e.eventId)
                put("activityType", e.activityType.name)
                put("sourceInstrument", e.sourceInstrument)
                put("title", e.title)
                put("startedAtMillis", e.startedAtMillis)
                put("endedAtMillis", e.endedAtMillis)
                put("durationSeconds", e.durationSeconds)
                put("outcome", e.outcome.name)
                put("sequenceRunId", e.sequenceRunId)
                put("sequenceName", e.sequenceName)
                put("metadata", e.metadata)
            })
        }
        val file = getFile(context)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun recordEvent(context: Context, event: TraceEvent) {
        ensureLoaded(context)
        // Idempotent: avoid duplicates by eventId
        cachedEvents?.removeAll { it.eventId == event.eventId }
        cachedEvents?.add(event)
        save(context)
    }

    /**
     * Aggregates all activities for a given epochDay from:
     * 1. TraceLedger recorded sessions (Focus, Break, Breathing, Metronome, Sequences)
     * 2. Canonical BookStore reading sessions (Reading)
     * 3. Habit completions from HabitMasterStore (Practice)
     */
    fun getDayEvents(context: Context, epochDay: Long): List<TraceEvent> {
        ensureLoaded(context)
        val dayStart = epochDay * 86400000L
        val dayEnd = dayStart + 86400000L

        val combined = mutableListOf<TraceEvent>()

        // 1. Explicit Trace events
        cachedEvents?.filter { it.epochDay == epochDay }?.let { combined.addAll(it) }

        // 2. Add canonical Folio ReadingSessions if not already logged in Trace
        val readingSessions = BookStore.loadSessions(context)
        readingSessions.filter { it.timestampMillis in dayStart until dayEnd }.forEach { rs ->
            val id = "folio_${rs.id}"
            if (combined.none { it.eventId == id || (it.activityType == ActivityType.READING && it.startedAtMillis == rs.timestampMillis) }) {
                val books = BookStore.loadAll(context)
                val bookTitle = books.firstOrNull { it.id == rs.bookId }?.title ?: "Book Reading"
                combined.add(
                    TraceEvent(
                        eventId = id,
                        activityType = ActivityType.READING,
                        sourceInstrument = "folio",
                        title = bookTitle,
                        startedAtMillis = rs.timestampMillis,
                        endedAtMillis = rs.timestampMillis + rs.durationSeconds * 1000L,
                        durationSeconds = rs.durationSeconds,
                        outcome = SessionOutcome.COMPLETED,
                        metadata = "Pages ${rs.startPage}–${rs.endPage} (${rs.pagesRead} pg)"
                    )
                )
            }
        }

        // 3. Add Habit entries for this day
        val habitEntries = HabitMasterStore.getEntries(context).filter { it.epochDay == epochDay && it.kind == EntryKind.LOG }
        val habits = HabitMasterStore.getAllHabits(context)
        habitEntries.forEach { entry ->
            val id = "habit_entry_${entry.id}"
            if (combined.none { it.eventId == id }) {
                val habit = habits.firstOrNull { it.id == entry.habitId }
                combined.add(
                    TraceEvent(
                        eventId = id,
                        activityType = ActivityType.HABIT,
                        sourceInstrument = "habits",
                        title = habit?.name ?: "Practice",
                        startedAtMillis = entry.createdAtMillis,
                        endedAtMillis = entry.createdAtMillis + 60000L,
                        durationSeconds = 60L,
                        outcome = SessionOutcome.COMPLETED,
                        metadata = "${entry.quantity.toInt()} ${habit?.unit ?: ""}"
                    )
                )
            }
        }

        return combined.sortedBy { it.startedAtMillis }
    }

    data class DaySummary(
        val totalActiveDurationSeconds: Long,
        val totalSessionsCount: Int,
        val durationByType: Map<ActivityType, Long>,
        val focusDurationSeconds: Long
    )

    fun getDaySummary(context: Context, epochDay: Long): DaySummary {
        val events = getDayEvents(context, epochDay)
        val validEvents = events.filter { it.outcome == SessionOutcome.COMPLETED }

        val totalDuration = validEvents.sumOf { it.durationSeconds }
        val map = HashMap<ActivityType, Long>()
        ActivityType.entries.forEach { type ->
            map[type] = validEvents.filter { it.activityType == type }.sumOf { it.durationSeconds }
        }

        return DaySummary(
            totalActiveDurationSeconds = totalDuration,
            totalSessionsCount = validEvents.size,
            durationByType = map,
            focusDurationSeconds = map[ActivityType.FOCUS] ?: 0L
        )
    }
}
