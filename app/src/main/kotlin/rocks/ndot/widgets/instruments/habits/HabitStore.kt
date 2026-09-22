package rocks.ndot.widgets.instruments.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One habit and every day it was done, as epoch days. */
data class Habit(
    val id: Long,
    val name: String,
    val color: Int,
    val createdDay: Long,
    val done: Set<Long>,
) {
    fun isDone(day: Long) = day in done

    /** Days in a row up to today - or up to yesterday, if today simply hasn't been done yet. */
    fun currentStreak(today: Long): Int {
        var day = if (isDone(today)) today else today - 1
        var count = 0
        while (isDone(day)) {
            count++
            day--
        }
        return count
    }

    fun longestStreak(): Int {
        if (done.isEmpty()) return 0
        var best = 1
        var run = 1
        val sorted = done.sorted()
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}

/**
 * Habits live in one small JSON document in SharedPreferences: no database, no account, no
 * sync. A few habits over a few years is a few kilobytes.
 */
object HabitStore {

    /** The colours a habit can be. Index 0, Nothing red, is the default. */
    val COLORS = listOf(0xFFD71921, 0xFFF2F2F2, 0xFFC6F432, 0xFF4DA3FF, 0xFFFFB020, 0xFFB388FF).map { it.toInt() }

    private const val PREFS = "habits"
    private const val KEY = "list"

    fun today(): Long = LocalDate.now().toEpochDay()

    fun load(context: Context): List<Habit> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val days = o.optJSONArray("done") ?: JSONArray()
                Habit(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    color = o.optInt("color", 0).coerceIn(0, COLORS.size - 1),
                    createdDay = o.optLong("created", today()),
                    done = (0 until days.length()).map { days.getLong(it) }.toSet(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, habits: List<Habit>): List<Habit> {
        val array = JSONArray()
        habits.forEach { h ->
            array.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("color", h.color)
                put("created", h.createdDay)
                put("done", JSONArray().apply { h.done.sorted().forEach { put(it) } })
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        return habits
    }

    fun add(context: Context, name: String, color: Int): List<Habit> {
        val habits = load(context)
        val id = (habits.maxOfOrNull { it.id } ?: 0L) + 1L
        return save(context, habits + Habit(id, name.trim(), color, today(), emptySet()))
    }

    fun toggle(context: Context, id: Long, day: Long): List<Habit> =
        save(context, load(context).map { h ->
            if (h.id != id) h else h.copy(done = if (h.isDone(day)) h.done - day else h.done + day)
        })

    fun update(context: Context, id: Long, name: String, color: Int): List<Habit> =
        save(context, load(context).map { if (it.id == id) it.copy(name = name.trim(), color = color) else it })

    fun delete(context: Context, id: Long): List<Habit> = save(context, load(context).filter { it.id != id })
}
