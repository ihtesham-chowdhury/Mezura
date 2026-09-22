package rocks.ndot.widgets.widgets

import android.content.Context

/**
 * A count-up stopwatch with laps - separate from [TimerEngine], which counts down. They were
 * previously the same thing wearing two faces, which is exactly why it felt redundant.
 *
 * Wall-clock is the source of truth so a running stopwatch survives the app being killed:
 * elapsed = accumulated + (now - startedAt).
 */
object StopwatchEngine {

    private const val PREFS = "stopwatch"
    private const val KEY_RUNNING = "running"
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_ACCUMULATED = "accumulated"
    private const val KEY_LAPS = "laps"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isRunning(context: Context) = prefs(context).getBoolean(KEY_RUNNING, false)

    fun elapsedMs(context: Context): Long {
        val p = prefs(context)
        val accumulated = p.getLong(KEY_ACCUMULATED, 0L)
        return if (p.getBoolean(KEY_RUNNING, false)) {
            accumulated + (System.currentTimeMillis() - p.getLong(KEY_STARTED_AT, System.currentTimeMillis()))
        } else {
            accumulated
        }
    }

    fun start(context: Context) {
        if (isRunning(context)) return
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun stop(context: Context) {
        if (!isRunning(context)) return
        val elapsed = elapsedMs(context)
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_ACCUMULATED, elapsed)
            .apply()
    }

    fun toggle(context: Context) {
        if (isRunning(context)) stop(context) else start(context)
    }

    /** Clears the run and every lap. */
    fun reset(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_ACCUMULATED, 0L)
            .putLong(KEY_STARTED_AT, 0L)
            .remove(KEY_LAPS)
            .apply()
    }

    /** Records the current reading. Returns the full list, newest last. */
    fun recordLap(context: Context): List<Long> {
        val laps = laps(context) + elapsedMs(context)
        prefs(context).edit().putString(KEY_LAPS, laps.joinToString(",")).apply()
        return laps
    }

    fun laps(context: Context): List<Long> =
        prefs(context).getString(KEY_LAPS, "")
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

    /** The most recent lap, which the face shows as a frozen second hand. */
    fun lastLap(context: Context): Long? = laps(context).lastOrNull()

    /** mm:ss.t - tenths, which is what the dial's 1/5 second graduation implies. */
    fun format(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (elapsedMs % 1000) / 100
        return "%02d:%02d.%d".format(minutes, seconds, tenths)
    }
}
