package rocks.ndot.widgets.app

import android.content.Context

/**
 * App-wide preferences for the instruments: which design each one wears, sound and haptics,
 * and the few settings each instrument remembers. Widget settings live elsewhere
 * ([rocks.ndot.widgets.common.Appearance], the engines) and are untouched by this.
 */
object AppPrefs {

    private const val PREFS = "instruments"

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
