package rocks.ndot.widgets.widgets

import android.content.Context

/**
 * One timer session, shared by both faces that show it: the Pomodoro pill and the hourglass
 * sticker. Placing both doesn't start two independent countdowns - they're two views of the
 * same session, which is why the duration lives in the app's settings rather than on each
 * widget.
 *
 * Wall-clock time is the source of truth so a session survives the app being killed. The
 * visible countdown is still driven by a Chronometer in the widget itself (free, smooth), and
 * the fill/sand level is repainted on a coarser tick by [TimerService].
 */
object TimerEngine {

    enum class Phase { IDLE, FOCUS, BREAK }

    /** Focus lengths offered in the app, in minutes. */
    val FOCUS_CHOICES = listOf(5, 10, 25, 45, 60, 90)

    /** Break lengths offered in the app, in minutes. */
    val BREAK_CHOICES = listOf(5, 10, 15)

    private const val PREFS = "timer"
    private const val KEY_PHASE = "phase"
    private const val KEY_RUNNING = "running"
    private const val KEY_ENDS_AT = "endsAtWall"
    private const val KEY_REMAINING = "remainingMs"
    private const val KEY_FOCUS_MIN = "focusMin"
    private const val KEY_BREAK_MIN = "breakMin"
    private const val KEY_COMPLETED = "completed"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun focusMinutes(context: Context) = prefs(context).getInt(KEY_FOCUS_MIN, 25)
    fun breakMinutes(context: Context) = prefs(context).getInt(KEY_BREAK_MIN, 5)

    fun setFocusMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_FOCUS_MIN, minutes).apply()
        if (phase(context) == Phase.IDLE) reset(context)
    }

    fun setBreakMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_BREAK_MIN, minutes).apply()
    }

    fun phase(context: Context): Phase =
        Phase.valueOf(prefs(context).getString(KEY_PHASE, Phase.IDLE.name)!!)

    fun isRunning(context: Context) = prefs(context).getBoolean(KEY_RUNNING, false)

    fun completed(context: Context) = prefs(context).getInt(KEY_COMPLETED, 0)

    fun phaseLengthMs(context: Context, phase: Phase = phase(context)): Long = when (phase) {
        Phase.BREAK -> breakMinutes(context) * 60_000L
        else -> focusMinutes(context) * 60_000L
    }

    /** Milliseconds left in the current phase, whether running or paused. */
    fun remainingMs(context: Context): Long {
        val p = prefs(context)
        if (phase(context) == Phase.IDLE) return phaseLengthMs(context, Phase.FOCUS)
        return if (isRunning(context)) {
            (p.getLong(KEY_ENDS_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            p.getLong(KEY_REMAINING, phaseLengthMs(context)).coerceAtLeast(0L)
        }
    }

    /** 1.0 at the start of a phase, 0.0 when it runs out - the fill and sand level both use it. */
    fun remainingFraction(context: Context): Float {
        val total = phaseLengthMs(context)
        if (total <= 0L) return 0f
        return (remainingMs(context).toFloat() / total).coerceIn(0f, 1f)
    }

    fun start(context: Context) {
        val p = prefs(context)
        val startingPhase = if (phase(context) == Phase.IDLE) Phase.FOCUS else phase(context)
        val remaining = if (phase(context) == Phase.IDLE) phaseLengthMs(context, Phase.FOCUS) else remainingMs(context)
        p.edit()
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

    /** Focus -> break -> focus. Returns the phase just entered. */
    fun advancePhase(context: Context): Phase {
        val p = prefs(context)
        val current = phase(context)
        val next = if (current == Phase.FOCUS) Phase.BREAK else Phase.FOCUS
        val completed = if (current == Phase.FOCUS) completed(context) + 1 else completed(context)
        val length = phaseLengthMs(context, next)
        p.edit()
            .putString(KEY_PHASE, next.name)
            .putInt(KEY_COMPLETED, completed)
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_ENDS_AT, System.currentTimeMillis() + length)
            .apply()
        return next
    }
}
