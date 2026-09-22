package rocks.ndot.widgets.common

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import rocks.ndot.widgets.widgets.AgeWidgetProvider
import rocks.ndot.widgets.widgets.HourglassEngine
import rocks.ndot.widgets.widgets.HourglassWidgetProvider
import rocks.ndot.widgets.widgets.PomodoroNotifier
import rocks.ndot.widgets.widgets.StopwatchWidgetProvider
import rocks.ndot.widgets.widgets.SundialWidgetProvider
import rocks.ndot.widgets.widgets.TimerEngine
import rocks.ndot.widgets.widgets.TimerService

/**
 * A reboot kills the foreground service that repaints the running timer, so it has to be
 * brought back. The timer's own deadline is wall-clock, so it survives the reboot on its own;
 * only the thing repainting it needs restarting. WorkManager's periodic jobs do come back by
 * themselves, but re-running them here clears any staleness immediately instead of waiting up
 * to fifteen minutes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                StopwatchWidgetProvider.updateAll(context)
                HourglassWidgetProvider.updateAll(context)
                AgeWidgetProvider.updateAll(context)
                SundialWidgetProvider.updateAll(context)

                // If a session was mid-flight, pick it back up; if it had already run out
                // while the phone was off, roll it on to the next phase first.
                TimerEngine.advanceIfDue(context)
                if (HourglassEngine.isRunning(context) && HourglassEngine.remainingMs(context) <= 0L) {
                    if (HourglassEngine.finish(context)) PomodoroNotifier.notifyHourglassDone(context)
                }
                // Covers a focus session, an hourglass or a stopwatch that was still running.
                TimerService.syncToState(context)

                rearmPeriodicWork(context)
            }
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                SundialWidgetProvider.updateAll(context)
            }
        }
    }

    private fun rearmPeriodicWork(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.getAppWidgetIds(ComponentName(context, SundialWidgetProvider::class.java)).forEach { id ->
            if (SundialWidgetProvider.hasLocation(context, id)) SundialWidgetProvider.scheduleRefresh(context, id)
        }
        mgr.getAppWidgetIds(ComponentName(context, AgeWidgetProvider::class.java)).forEach { id ->
            if (AgeWidgetProvider.dobMillis(context, id) >= 0) AgeWidgetProvider.scheduleRefresh(context, id)
        }
    }
}
