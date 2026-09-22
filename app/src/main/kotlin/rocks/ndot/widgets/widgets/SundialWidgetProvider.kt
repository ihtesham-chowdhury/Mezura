package rocks.ndot.widgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.SolarMath
import rocks.ndot.widgets.common.SundialRenderer
import rocks.ndot.widgets.common.WidgetPrefs
import rocks.ndot.widgets.common.WidgetSizing
import rocks.ndot.widgets.ui.ConfigureSundialActivity
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * A bitmap, redrawn on a schedule - unlike the KWGT build, this one has zero uncertainty
 * about *how* to draw the lens-intersection disc (real Canvas, see SundialRenderer), but the
 * same real platform floor on *how often*: WorkManager's minimum periodic interval is 15
 * minutes, full stop, for exactly the battery reason the source video's design team joked
 * about. BOOT_COMPLETED and ACTION_TIMEZONE_CHANGED (see BootReceiver) also force one
 * immediate redraw so the widget isn't stale after either.
 */
class SundialWidgetProvider : AppWidgetProvider() {

    companion object {
        const val EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID
        private const val WORK_NAME_PREFIX = "sundial_refresh_"

        fun prefs(context: Context) = WidgetPrefs(context, "sundial")

        fun hasLocation(context: Context, id: Int) = prefs(context).getString(id, "name", null) != null
        fun lat(context: Context, id: Int) = prefs(context).getFloat(id, "lat", 23.81f).toDouble()
        fun lng(context: Context, id: Int) = prefs(context).getFloat(id, "lng", 90.41f).toDouble()
        fun name(context: Context, id: Int) = prefs(context).getString(id, "name", "—") ?: "—"

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SundialWidgetProvider::class.java))
            ids.forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_sundial)

            if (!hasLocation(context, id)) {
                views.setViewVisibility(R.id.sundialImage, android.view.View.GONE)
                views.setViewVisibility(R.id.sundialSetup, android.view.View.VISIBLE)
                views.setOnClickPendingIntent(R.id.sundialSetup, configurePendingIntent(context, id))
                mgr.updateAppWidget(id, views)
                return
            }

            val state = SolarMath.dialStateNow(System.currentTimeMillis(), lat(context, id), lng(context, id), TimeZone.getDefault())
            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 90, 90)
            val bitmap = SundialRenderer.render(
                sizePx = minOf(wPx, hPx),
                state = state,
                sunColor = Appearance.accentWithAlpha(context),
                bodyColor = Appearance.bodyColor(context),
            )

            views.setViewVisibility(R.id.sundialImage, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.sundialSetup, android.view.View.GONE)
            views.setImageViewBitmap(R.id.sundialImage, bitmap)
            views.setOnClickPendingIntent(R.id.sundialImage, configurePendingIntent(context, id))
            mgr.updateAppWidget(id, views)
        }

        private fun configurePendingIntent(context: Context, id: Int) = android.app.PendingIntent.getActivity(
            context, id,
            Intent(context, ConfigureSundialActivity::class.java)
                .putExtra(EXTRA_WIDGET_ID, id)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        fun scheduleRefresh(context: Context, id: Int) {
            val request = PeriodicWorkRequestBuilder<SundialUpdateWorker>(15, TimeUnit.MINUTES)
                .setInputData(androidx.work.workDataOf(SundialUpdateWorker.KEY_WIDGET_ID to id))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PREFIX + id, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancelRefresh(context: Context, id: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + id)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            updateOne(context, appWidgetManager, it)
            if (hasLocation(context, it)) scheduleRefresh(context, it)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            cancelRefresh(context, id)
            prefs(context).clear(id, listOf("name", "lat", "lng"))
        }
    }
}
