package rocks.ndot.widgets.common

import android.content.Context
import android.content.SharedPreferences

/**
 * A user can drop the same widget on their home screen more than once, and Android gives
 * each placement its own [appWidgetId] - so state has to be namespaced per id, never global.
 * This wraps one SharedPreferences file per widget *kind* (not per id) and prefixes every
 * key with the id, which keeps one file per provider instead of one file per placement.
 */
class WidgetPrefs(context: Context, kind: String) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("widget_$kind", Context.MODE_PRIVATE)

    fun edit(appWidgetId: Int, block: SharedPreferences.Editor.(prefix: String) -> Unit) {
        prefs.edit().apply { block(this, "$appWidgetId.") }.apply()
    }

    fun getLong(appWidgetId: Int, key: String, default: Long) =
        prefs.getLong("$appWidgetId.$key", default)

    fun getInt(appWidgetId: Int, key: String, default: Int) =
        prefs.getInt("$appWidgetId.$key", default)

    fun getBoolean(appWidgetId: Int, key: String, default: Boolean) =
        prefs.getBoolean("$appWidgetId.$key", default)

    fun getString(appWidgetId: Int, key: String, default: String?) =
        prefs.getString("$appWidgetId.$key", default)

    fun getFloat(appWidgetId: Int, key: String, default: Float) =
        prefs.getFloat("$appWidgetId.$key", default)

    /** Call from onDeleted() so a removed widget doesn't leak its keys forever. */
    fun clear(appWidgetId: Int, keys: List<String>) {
        prefs.edit().apply { keys.forEach { remove("$appWidgetId.$it") } }.apply()
    }
}
