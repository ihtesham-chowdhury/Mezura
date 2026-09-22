package rocks.ndot.widgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.StopwatchRenderer
import rocks.ndot.widgets.common.WidgetSizing
import rocks.ndot.widgets.MainActivity

/**
 * The stopwatch on the home screen. Tapping the face starts and stops it; tapping the crown
 * area opens the stopwatch in the app, where the sweep is actually smooth and laps and click
 * sounds live.
 *
 * While it runs, [TimerService] repaints it once a second. That is the honest ceiling for a
 * widget host - a real sweep would need sixty pushes a second through binder, which no
 * launcher will take - and it happens to be exactly how a quartz stopwatch ticks anyway.
 */
class StopwatchWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "rocks.ndot.widgets.action.STOPWATCH_TOGGLE"
        private const val PREFS = "stopwatch_look"
        private const val KEY_WHITE = "whiteDial"

        fun isWhiteDial(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WHITE, false)

        fun setWhiteDial(context: Context, white: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WHITE, white).apply()
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, StopwatchWidgetProvider::class.java))
                .forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_stopwatch)
            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 110, 130)

            views.setImageViewBitmap(
                R.id.stopwatchImage,
                StopwatchRenderer.render(
                    widthPx = wPx,
                    heightPx = hPx,
                    face = StopwatchRenderer.Face(
                        elapsedMs = StopwatchEngine.elapsedMs(context),
                        splitMs = StopwatchEngine.lastLap(context),
                        running = StopwatchEngine.isRunning(context),
                    ),
                    dial = if (isWhiteDial(context)) StopwatchRenderer.Dial.WHITE
                    else StopwatchRenderer.Dial.BLACK,
                    accent = Appearance.accent(context),
                )
            )

            views.setOnClickPendingIntent(
                R.id.stopwatchImage,
                PendingIntent.getBroadcast(
                    context, id + 5_000_000,
                    Intent(context, StopwatchWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.stopwatchOpen,
                PendingIntent.getActivity(
                    context, id + 6_000_000,
                    MainActivity.openIntent(context, "stopwatch"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            mgr.updateAppWidget(id, views)
        }

        /** The repaint service only needs to run while something is actually moving. */
        fun syncService(context: Context) = TimerService.syncToState(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        StopwatchEngine.toggle(context)
        updateAll(context)
        TimerService.syncToState(context)
    }
}
