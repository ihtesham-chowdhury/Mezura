package rocks.ndot.widgets.widgets

import android.content.Context

/**
 * The in-app hourglass: a plain countdown, separate from the Pomodoro session. It has no
 * phases and no breaks - you turn it over and it runs out, which is the whole point of it.
 *
 * Wall-clock time is the source of truth, like the other engines, so it survives the app being
 * killed; [TimerService] finishes it on time and raises the alert if the app isn't open.
 */
object HourglassEngine {

    enum class State { IDLE, RUNNING, PAUSED, DONE }

    private const val PREFS = "hourglass"
    private const val KEY_STATE = "state"
    private const val KEY_DURATION = "durationMs"
    private const val KEY_ENDS_AT = "endsAt"
    private const val KEY_REMAINING = "remainingMs"

    const val DEFAULT_DURATION_MS = 5 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Snapshot(val state: State, val durationMs: Long, val endsAt: Long, val pausedRemaining: Long) {
        fun remainingAt(now: Long): Long = when (state) {
            State.IDLE -> durationMs
            State.RUNNING -> (endsAt - now).coerceAtLeast(0L)
            State.PAUSED -> pausedRemaining.coerceIn(0L, durationMs)
            State.DONE -> 0L
        }

        /** 1.0 with all the sand on top, 0.0 when it has all run through. */
        fun fractionAt(now: Long): Float =
            if (durationMs <= 0L) 0f else (remainingAt(now).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        val state = runCatching { State.valueOf(p.getString(KEY_STATE, State.IDLE.name)!!) }.getOrDefault(State.IDLE)
        val duration = p.getLong(KEY_DURATION, DEFAULT_DURATION_MS)
        return Snapshot(state, duration, p.getLong(KEY_ENDS_AT, 0L), p.getLong(KEY_REMAINING, duration))
    }

    fun isRunning(context: Context) = snapshot(context).state == State.RUNNING

    fun remainingMs(context: Context) = snapshot(context).remainingAt(System.currentTimeMillis())

    fun start(context: Context, durationMs: Long) {
        prefs(context).edit()
            .putString(KEY_STATE, State.RUNNING.name)
            .putLong(KEY_DURATION, durationMs)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + durationMs)
            .putLong(KEY_REMAINING, durationMs)
            .apply()
    }

    fun pause(context: Context) {
        val snap = snapshot(context)
        if (snap.state != State.RUNNING) return
        prefs(context).edit()
            .putString(KEY_STATE, State.PAUSED.name)
            .putLong(KEY_REMAINING, snap.remainingAt(System.currentTimeMillis()))
            .apply()
    }

    fun resume(context: Context) {
        val snap = snapshot(context)
        if (snap.state != State.PAUSED) return
        prefs(context).edit()
            .putString(KEY_STATE, State.RUNNING.name)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + snap.pausedRemaining)
            .apply()
    }

    /** Back to full, ready to be turned over again. */
    fun reset(context: Context) {
        prefs(context).edit()
            .putString(KEY_STATE, State.IDLE.name)
            .remove(KEY_ENDS_AT)
            .apply()
    }

    /** Sets the length without starting it - what the flip clock shows while idle. */
    fun setDuration(context: Context, durationMs: Long) {
        prefs(context).edit()
            .putString(KEY_STATE, State.IDLE.name)
            .putLong(KEY_DURATION, durationMs)
            .putLong(KEY_REMAINING, durationMs)
            .apply()
    }

    /**
     * Marks the run finished. Returns true only for the call that actually finished it, so the
     * app and [TimerService] can both check the deadline without both sounding the alarm.
     */
    @Synchronized
    fun finish(context: Context): Boolean {
        val snap = snapshot(context)
        if (snap.state != State.RUNNING) return false
        prefs(context).edit()
            .putString(KEY_STATE, State.DONE.name)
            .putLong(KEY_REMAINING, 0L)
            .commit()
        return true
    }
}
