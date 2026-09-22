package rocks.ndot.widgets.widgets

import android.content.Context

/**
 * One Pomodoro session, shared by every face that shows it: the sand-timer widget on the home
 * screen and the Pomodoro devices in the app. Starting it in one place starts it everywhere -
 * they are views of the same session, not independent countdowns.
 *
 * Wall-clock time is the source of truth so a session survives the app being killed. The
 * widget's visible countdown is a Chronometer (free, smooth), and phase changes are driven by
 * [TimerService] so they happen on time even with the screen off.
 */
object TimerEngine {

    enum class Phase { IDLE, FOCUS, BREAK }

    /** Focus lengths offered as presets, in minutes. Any length from 1 to 180 can be dialled in. */
    val FOCUS_CHOICES = listOf(5, 10, 25, 45, 60, 90)

    /** Break lengths offered as presets, in minutes. */
    val BREAK_CHOICES = listOf(5, 10, 15)

    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180

    private const val PREFS = "timer"
    private const val KEY_PHASE = "phase"
    private const val KEY_RUNNING = "running"
    private const val KEY_ENDS_AT = "endsAtWall"
    private const val KEY_REMAINING = "remainingMs"
    private const val KEY_FOCUS_MIN = "focusMin"
    private const val KEY_BREAK_MIN = "breakMin"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_GOAL = "goal"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun focusMinutes(context: Context) = prefs(context).getInt(KEY_FOCUS_MIN, 25)
    fun breakMinutes(context: Context) = prefs(context).getInt(KEY_BREAK_MIN, 5)

    fun setFocusMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_FOCUS_MIN, minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)).apply()
        if (phase(context) == Phase.IDLE) resetKeepingCount(context)
    }

    fun setBreakMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_BREAK_MIN, minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)).apply()
    }

    /** Focus sessions in one set. After the last one the timer stops instead of rolling into
     *  another break; 0 means keep cycling for ever. */
    fun goal(context: Context) = prefs(context).getInt(KEY_GOAL, 4)

    fun setGoal(context: Context, sessions: Int) {
        prefs(context).edit().putInt(KEY_GOAL, sessions.coerceIn(0, 12)).apply()
    }

    fun phase(context: Context): Phase =
        runCatching { Phase.valueOf(prefs(context).getString(KEY_PHASE, Phase.IDLE.name)!!) }
            .getOrDefault(Phase.IDLE)

    fun isRunning(context: Context) = prefs(context).getBoolean(KEY_RUNNING, false)

    fun completed(context: Context) = prefs(context).getInt(KEY_COMPLETED, 0)

    /** True once every session of the set has been worked through. */
    fun isSetComplete(context: Context): Boolean {
        val goal = goal(context)
        return phase(context) == Phase.IDLE && goal > 0 && completed(context) >= goal
    }

    fun phaseLengthMs(context: Context, phase: Phase = phase(context)): Long = when (phase) {
        Phase.BREAK -> breakMinutes(context) * 60_000L
        else -> focusMinutes(context) * 60_000L
    }

    /** Milliseconds left in the current phase, whether running or paused. */
    fun remainingMs(context: Context): Long = snapshot(context).remainingAt(System.currentTimeMillis())

    /** 1.0 at the start of a phase, 0.0 when it runs out - the fill and sand level both use it. */
    fun remainingFraction(context: Context): Float = snapshot(context).fractionAt(System.currentTimeMillis())

    fun start(context: Context) {
        val current = phase(context)
        // A finished set starts over rather than counting past its goal.
        if (current == Phase.IDLE && isSetComplete(context)) {
            prefs(context).edit().putInt(KEY_COMPLETED, 0).apply()
        }
        val startingPhase = if (current == Phase.IDLE) Phase.FOCUS else current
        val remaining = if (current == Phase.IDLE) phaseLengthMs(context, Phase.FOCUS) else remainingMs(context)
        prefs(context).edit()
            .putString(KEY_PHASE, startingPhase.name)
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + remaining)
            .apply()
    }

    fun pause(context: Context) {
        val remaining = remainingMs(context)
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_REMAINING, remaining)
            .apply()
    }

    fun toggle(context: Context) {
        if (isRunning(context)) pause(context) else start(context)
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putString(KEY_PHASE, Phase.IDLE.name)
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_REMAINING, phaseLengthMs(context, Phase.FOCUS))
            .putInt(KEY_COMPLETED, 0)
            .apply()
    }

    private fun resetKeepingCount(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_REMAINING, phaseLengthMs(context, Phase.FOCUS))
            .apply()
    }

    /**
     * Focus -> break -> focus, or IDLE once the set's last focus session is done. Returns the
     * phase just entered. [countIt] is false when the user skips ahead by hand, so an abandoned
     * session isn't recorded as a completed one.
     */
    fun advancePhase(context: Context, countIt: Boolean = true): Phase {
        val p = prefs(context)
        val current = phase(context)
        val completed = if (current == Phase.FOCUS && countIt) completed(context) + 1 else completed(context)
        val goal = goal(context)
        if (current == Phase.FOCUS && countIt && goal > 0 && completed >= goal) {
            p.edit()
                .putString(KEY_PHASE, Phase.IDLE.name)
                .putBoolean(KEY_RUNNING, false)
                .putInt(KEY_COMPLETED, completed)
                .putLong(KEY_REMAINING, phaseLengthMs(context, Phase.FOCUS))
                .apply()
            return Phase.IDLE
        }
        val next = if (current == Phase.FOCUS) Phase.BREAK else Phase.FOCUS
        val length = phaseLengthMs(context, next)
        p.edit()
            .putString(KEY_PHASE, next.name)
            .putInt(KEY_COMPLETED, completed)
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + length)
            .apply()
        return next
    }

    /**
     * Rolls the phase over if, and only if, it has actually run out. Both [TimerService] and
     * the open app watch the deadline; making the check-and-advance atomic means whichever gets
     * there first does it, and the other finds nothing to do.
     */
    @Synchronized
    fun advanceIfDue(context: Context): Phase? {
        if (!isRunning(context) || phase(context) == Phase.IDLE) return null
        if (remainingMs(context) > 0L) return null
        return advancePhase(context)
    }

    /** Jumps straight to the start of [target], keeping whether the clock was running. */
    fun switchTo(context: Context, target: Phase) {
        if (target == Phase.IDLE) {
            reset(context)
            return
        }
        val length = phaseLengthMs(context, target)
        val running = isRunning(context)
        prefs(context).edit()
            .putString(KEY_PHASE, target.name)
            .putBoolean(KEY_RUNNING, running)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + length)
            .putLong(KEY_REMAINING, length)
            .apply()
    }

    /** Everything a face needs, read once, so a 120Hz redraw doesn't hit SharedPreferences. */
    data class Snapshot(
        val phase: Phase,
        val running: Boolean,
        val endsAt: Long,
        val pausedRemaining: Long,
        val focusMs: Long,
        val breakMs: Long,
        val completed: Int,
        val goal: Int,
    ) {
        val lengthMs: Long get() = if (phase == Phase.BREAK) breakMs else focusMs

        fun remainingAt(now: Long): Long = when {
            phase == Phase.IDLE -> focusMs
            running -> (endsAt - now).coerceAtLeast(0L)
            else -> pausedRemaining.coerceIn(0L, lengthMs)
        }

        fun fractionAt(now: Long): Float =
            if (lengthMs <= 0L) 0f else (remainingAt(now).toFloat() / lengthMs).coerceIn(0f, 1f)

        /** The session number shown on the devices: 1-based, and never past the goal. */
        val sessionNumber: Int
            get() = if (goal > 0) (completed + if (phase == Phase.BREAK) 0 else 1).coerceIn(1, goal)
            else completed + if (phase == Phase.BREAK) 0 else 1

        val setComplete: Boolean get() = phase == Phase.IDLE && goal > 0 && completed >= goal
    }

    fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        val phase = phase(context)
        val focusMs = p.getInt(KEY_FOCUS_MIN, 25) * 60_000L
        val breakMs = p.getInt(KEY_BREAK_MIN, 5) * 60_000L
        val length = if (phase == Phase.BREAK) breakMs else focusMs
        return Snapshot(
            phase = phase,
            running = p.getBoolean(KEY_RUNNING, false),
            endsAt = p.getLong(KEY_ENDS_AT, 0L),
            pausedRemaining = p.getLong(KEY_REMAINING, length),
            focusMs = focusMs,
            breakMs = breakMs,
            completed = p.getInt(KEY_COMPLETED, 0),
            goal = p.getInt(KEY_GOAL, 4),
        )
    }
}
