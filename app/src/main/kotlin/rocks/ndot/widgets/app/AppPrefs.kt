package rocks.ndot.widgets.app

import android.content.Context

/**
 * App-wide preferences for the instruments: which design each one wears, sound and haptics,
 * and the few settings each instrument remembers. Widget settings live elsewhere
 * ([rocks.ndot.widgets.common.Appearance], the engines) and are untouched by this.
 */
object AppPrefs {

    private const val PREFS = "instruments"

    // Universal Default keys for Startup Route, Sub-Styles & Themes
    const val KEY_DEFAULT_STARTUP_ROUTE = "default_startup_route" // "calendar", "habits", "time", "folio", "breathing", "horizon"
    const val KEY_DEFAULT_TIME_INSTRUMENT = "default_time_instrument" // "stopwatch", "pomodoro", "hourglass", "metronome"
    const val KEY_DEFAULT_HOURGLASS_STYLE = "default_hourglass_style" // "dot", "classic", "dusk", "prism"
    const val KEY_DEFAULT_POMODORO_STYLE  = "default_pomodoro_style"  // "module", "slab", "pocket"
    const val KEY_DEFAULT_METRONOME_STYLE = "default_metronome_style" // "studio", "pendulum"
    const val KEY_DEFAULT_CALENDAR_STYLE  = "default_calendar_style"  // "glass", "paper"
    const val KEY_DEFAULT_CALENDAR_SPAN   = "default_calendar_span"   // "month", "year"
    const val KEY_DEFAULT_HORIZON_THEME   = "default_horizon_theme"   // "horizon"
    const val KEY_DEFAULT_BREATH_PROTOCOL = "default_breath_protocol" // "sigh", "coherent", "box", "478"
    const val KEY_DEFAULT_COLOR_THEME     = "default_color_theme"     // "classic", "amber", "crimson", "emerald", "cobalt"
    const val KEY_DEFAULT_FOLIO_TAB       = "default_folio_tab"       // "shelf", "caliper", "stats"
    const val KEY_DEFAULT_HABITS_TAB      = "default_habits_tab"      // "city", "habits"
    const val KEY_DEFAULT_HORIZON_TAB     = "default_horizon_tab"     // "ribbon", "wave"

    fun setDefault(context: Context, key: String, value: String, humanLabel: String) {
        prefs(context).edit().putString(key, value).apply()
        rocks.ndot.widgets.kit.Feedback.confirmDefault(context)
        android.widget.Toast.makeText(context, "$humanLabel set as default", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun getDefault(context: Context, key: String, fallback: String): String {
        return prefs(context).getString(key, fallback) ?: fallback
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sound(context: Context) = prefs(context).getBoolean("sound", true)
    fun setSound(context: Context, on: Boolean) = prefs(context).edit().putBoolean("sound", on).apply()

    fun haptics(context: Context) = prefs(context).getBoolean("haptics", true)
    fun setHaptics(context: Context, on: Boolean) = prefs(context).edit().putBoolean("haptics", on).apply()

    /** Keep the screen on while an instrument is running and on screen. */
    fun keepAwake(context: Context) = prefs(context).getBoolean("keepAwake", true)
    fun setKeepAwake(context: Context, on: Boolean) = prefs(context).edit().putBoolean("keepAwake", on).apply()

    fun design(context: Context, tool: String, default: String): String =
        prefs(context).getString("design.$tool", default) ?: default

    fun setDesign(context: Context, tool: String, design: String) =
        prefs(context).edit().putString("design.$tool", design).apply()

    /** Whether notification permission has been asked for once already - it's never nagged for. */
    fun askedNotifications(context: Context) = prefs(context).getBoolean("askedNotifications", false)
    fun setAskedNotifications(context: Context) = prefs(context).edit().putBoolean("askedNotifications", true).apply()

    /** The last instrument opened, so the drawer can come back to it. */
    fun lastTool(context: Context): String? = prefs(context).getString("lastTool", null)
    fun setLastTool(context: Context, id: String) = prefs(context).edit().putString("lastTool", id).apply()

    // ---- hourglass ----

    private val DEFAULT_PRESETS = listOf(60L, 180L, 300L, 600L, 1500L, 2700L, 3600L)

    /** The hourglass preset buttons, in seconds. The user can add, change and remove them. */
    fun hourglassPresets(context: Context): List<Long> {
        val raw = prefs(context).getString("hourglass.presets", null) ?: return DEFAULT_PRESETS
        val parsed = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it in 1..(24 * 3600) }
        return if (parsed.isEmpty()) DEFAULT_PRESETS else parsed
    }

    fun setHourglassPresets(context: Context, seconds: List<Long>) =
        prefs(context).edit().putString("hourglass.presets", seconds.distinct().sorted().joinToString(",")).apply()

    // ---- metronome ----

    fun metronomeBpm(context: Context) = prefs(context).getInt("metro.bpm", 100)
    fun metronomeBeats(context: Context) = prefs(context).getInt("metro.beats", 4)
    fun metronomeSubdivision(context: Context) = prefs(context).getInt("metro.sub", 1)
    fun metronomeSound(context: Context) = prefs(context).getString("metro.sound", "click") ?: "click"
    fun metronomeBeatHaptic(context: Context) = prefs(context).getBoolean("metro.haptic", false)

    fun saveMetronome(context: Context, bpm: Int, beats: Int, subdivision: Int, sound: String, beatHaptic: Boolean) {
        prefs(context).edit()
            .putInt("metro.bpm", bpm)
            .putInt("metro.beats", beats)
            .putInt("metro.sub", subdivision)
            .putString("metro.sound", sound)
            .putBoolean("metro.haptic", beatHaptic)
            .apply()
    }
}
