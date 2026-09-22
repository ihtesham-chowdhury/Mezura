package rocks.ndot.widgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Brand
import rocks.ndot.widgets.common.SandTimerRenderer
import rocks.ndot.widgets.common.WidgetSizing

/**
 * The focus timer, and the only face it has. It was previously duplicated by a pill widget
 * doing exactly the same counting, which is why that one is gone.
 *
 * Tap the glass to start or pause; tap the time underneath to reset. The sand level is repainted by [TimerService] on
 * a one-second tick while it runs; the numeral underneath is a Chronometer, which the launcher
 * counts down itself at no cost.
 */
class HourglassWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "rocks.ndot.widgets.action.SANDTIMER_TOGGLE"
        const val ACTION_RESET = "rocks.ndot.widgets.action.SANDTIMER_RESET"

        /** Sand keeps its own warm tone unless the accent has been changed from the default. */
        private const val DEFAULT_SAND = 0xFFD79A3A.toInt()

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, HourglassWidgetProvider::class.java))
                .forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_hourglass)
            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 110, 150)

            val idle = TimerEngine.phase(context) == TimerEngine.Phase.IDLE
            val running = TimerEngine.isRunning(context)
            // Idle reads as "ready to run", so the glass shows full rather than spent.
            val fraction = if (idle) 1f else TimerEngine.remainingFraction(context)

            val sand = if (Appearance.accent(context) == Brand.RED) {
                DEFAULT_SAND
            } else {
                Appearance.accent(context)
            }

            views.setImageViewBitmap(
                R.id.hourglassImage,
                SandTimerRenderer.render(
                    widthPx = wPx,
                    heightPx = (hPx * 0.82f).toInt().coerceAtLeast(64),
                    remainingFraction = fraction,
                    running = running,
                    sandColor = Appearance.withAlpha(sand, Appearance.alphaPercent(context)),
                    // Shuffles the loose grains between repaints so the stream looks alive.
                    tick = ((System.currentTimeMillis() / 1000L) % 997L).toInt(),
                )
            )

            if (idle) {
                views.setViewVisibility(R.id.hourglassTimer, View.VISIBLE)
                views.setChronometer(
                    R.id.hourglassTimer,
                    SystemClock.elapsedRealtime() + TimerEngine.phaseLengthMs(context, TimerEngine.Phase.FOCUS),
                    null, false
                )
            } else {
                views.setViewVisibility(R.id.hourglassTimer, View.VISIBLE)
                views.setChronometer(
                    R.id.hourglassTimer,
                    SystemClock.elapsedRealtime() + TimerEngine.remainingMs(context),
                    null, running
                )
            }
            views.setChronometerCountDown(R.id.hourglassTimer, true)

            views.setOnClickPendingIntent(R.id.hourglassImage, broadcast(context, ACTION_TOGGLE, id + 4_000_000))
            views.setOnClickPendingIntent(R.id.hourglassTimer, broadcast(context, ACTION_RESET, id + 4_500_000))

            mgr.updateAppWidget(id, views)
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, HourglassWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
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
        when (intent.action) {
            ACTION_TOGGLE -> TimerEngine.toggle(context)
            ACTION_RESET -> TimerEngine.reset(context)
            else -> return
        }
        repaintWidgets(context)
        TimerService.syncToState(context)
    }
}
