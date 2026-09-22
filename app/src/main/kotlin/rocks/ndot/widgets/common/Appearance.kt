package rocks.ndot.widgets.common

import android.content.Context
import android.graphics.Color

/**
 * Appearance is deliberately app-wide rather than per-widget-placement: you pick a colour and
 * a transparency once in the app and every widget follows it, which is what "change the colour
 * and transparency in the app" should mean. Per-placement overrides would need a config screen
 * on every widget for very little gain.
 */
object Appearance {

    private const val PREFS = "appearance"
    private const val KEY_ACCENT = "accent"
    private const val KEY_ALPHA = "alphaPercent"

    /** Swatches offered in the settings screen. Nothing's own red and yellow lead. */
    val PALETTE: List<Swatch> = listOf(
        Swatch("red", Brand.RED),
        Swatch("yellow", Brand.YELLOW),
        Swatch("blue", Color.parseColor("#2E6BFF")),
        Swatch("green", Color.parseColor("#22C55E")),
        Swatch("cyan", Color.parseColor("#22D3EE")),
        Swatch("violet", Color.parseColor("#8B5CF6")),
        Swatch("orange", Color.parseColor("#FB7B1E")),
        Swatch("white", Color.parseColor("#F2F2F5")),
    )

    data class Swatch(val id: String, val color: Int)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The accent: Pomodoro's draining fill, the Sundial's sun, the hourglass's sand. */
    fun accent(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, Brand.RED)

    fun setAccent(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, color).apply()
    }

    /** 0 = fully see-through, 100 = solid. Applies to widget bodies, never to text. */
    fun alphaPercent(context: Context): Int =
        prefs(context).getInt(KEY_ALPHA, 100).coerceIn(0, 100)

    fun setAlphaPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_ALPHA, percent.coerceIn(0, 100)).apply()
    }

    /** Re-applies [alpha] to an existing colour, keeping its RGB. */
    fun withAlpha(color: Int, percent: Int): Int = Color.argb(
        (percent.coerceIn(0, 100) * 255 / 100),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    fun bodyColor(context: Context): Int = withAlpha(Brand.BLACK, alphaPercent(context))

    fun accentWithAlpha(context: Context): Int = withAlpha(accent(context), alphaPercent(context))
}
