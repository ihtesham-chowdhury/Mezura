package rocks.ndot.widgets.common

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * Bitmaps have to be rendered at the size the launcher actually gave the widget, which the OS
 * reports in dp through the widget's options bundle. Falls back to the declared minimums when
 * a launcher reports nothing (some do, especially right after a widget is dropped).
 */
object WidgetSizing {

    fun sizePx(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int,
    ): Pair<Int, Int> {
        val options = mgr.getAppWidgetOptions(appWidgetId)
        var widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        var heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        if (widthDp <= 0) widthDp = fallbackWidthDp
        if (heightDp <= 0) heightDp = fallbackHeightDp

        val density = context.resources.displayMetrics.density
        // Capped so an oversized placement can't allocate a huge bitmap for every repaint.
        val widthPx = (widthDp * density).toInt().coerceIn(64, 1080)
        val heightPx = (heightDp * density).toInt().coerceIn(64, 1080)
        return widthPx to heightPx
    }
}
