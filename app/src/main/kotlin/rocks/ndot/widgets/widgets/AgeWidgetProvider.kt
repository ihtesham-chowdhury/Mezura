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
import androidx.work.workDataOf
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Shapes
import rocks.ndot.widgets.common.WidgetPrefs
import rocks.ndot.widgets.common.WidgetSizing
import rocks.ndot.widgets.ui.ConfigureAgeActivity
import java.util.Locale
import java.util.concurrent.TimeUnit

class AgeWidgetProvider : AppWidgetProvider() {

    enum class AgeUnit(val labelRes: Int) {
        YEARS(R.string.age_unit_years), MONTHS(R.string.age_unit_months), WEEKS(R.string.age_unit_weeks),
        DAYS(R.string.age_unit_days), HOURS(R.string.age_unit_hours), MINUTES(R.string.age_unit_minutes),
        MOONS(R.string.age_unit_moons), HEARTBEATS(R.string.age_unit_heartbeats)
    }

    companion object {
        const val EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID
        const val ACTION_CYCLE_UNIT = "rocks.ndot.widgets.action.AGE_CYCLE_UNIT"
        private const val WORK_NAME_PREFIX = "age_refresh_"
        private const val YEAR_MILLIS = 365.2425 * 86_400_000.0
        private const val MOON_MILLIS = 29.530588 * 86_400_000.0

        fun prefs(context: Context) = WidgetPrefs(context, "age")
        fun dobMillis(context: Context, id: Int) = prefs(context).getLong(id, "dob", -1L)
        fun unitOf(context: Context, id: Int): AgeUnit =
            AgeUnit.valueOf(prefs(context).getString(id, "unit", AgeUnit.YEARS.name)!!)

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, AgeWidgetProvider::class.java))
                .forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_age)
            val dob = dobMillis(context, id)

            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 180, 90)
            views.setImageViewBitmap(
                R.id.ageBackground,
                Shapes.pill(wPx, hPx, Appearance.bodyColor(context))
            )

            if (dob < 0) {
                views.setTextViewText(R.id.ageValue, context.getString(R.string.age_tap_to_set))
                views.setTextViewText(R.id.ageUnitLabel, "")
            } else {
                val ms = (System.currentTimeMillis() - dob).coerceAtLeast(0)
                val unit = unitOf(context, id)
                views.setTextViewText(R.id.ageValue, formatValue(ms, unit))
                views.setTextViewText(R.id.ageUnitLabel, context.getString(unit.labelRes).uppercase(Locale.getDefault()))
            }

            views.setOnClickPendingIntent(R.id.ageRoot, cycleUnitPendingIntent(context, id))
            mgr.updateAppWidget(id, views)
        }

        private fun formatValue(ms: Long, unit: AgeUnit): String {
            val v: Double = when (unit) {
                AgeUnit.YEARS -> ms / YEAR_MILLIS
                AgeUnit.MONTHS -> ms / (YEAR_MILLIS / 12)
                AgeUnit.WEEKS -> ms / (7 * 86_400_000.0)
                AgeUnit.DAYS -> ms / 86_400_000.0
                AgeUnit.HOURS -> ms / 3_600_000.0
                AgeUnit.MINUTES -> ms / 60_000.0
                AgeUnit.MOONS -> ms / MOON_MILLIS
                AgeUnit.HEARTBEATS -> ms / 60_000.0 * 72.0
            }
            return when (unit) {
                AgeUnit.YEARS -> String.format(Locale.US, "%.9f", v)
                AgeUnit.MONTHS, AgeUnit.WEEKS -> String.format(Locale.US, "%.3f", v)
                else -> String.format(Locale.US, "%,.0f", v)
            }
        }

        private fun cycleUnitPendingIntent(context: Context, id: Int) = android.app.PendingIntent.getBroadcast(
            context, id,
            Intent(context, AgeWidgetProvider::class.java).setAction(ACTION_CYCLE_UNIT).putExtra(EXTRA_WIDGET_ID, id),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        fun scheduleRefresh(context: Context, id: Int) {
            val request = PeriodicWorkRequestBuilder<AgeUpdateWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(AgeUpdateWorker.KEY_WIDGET_ID to id))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME_PREFIX + id, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelRefresh(context: Context, id: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + id)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            updateOne(context, appWidgetManager, it)
            if (dobMillis(context, it) >= 0) scheduleRefresh(context, it)
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
            prefs(context).clear(id, listOf("dob", "unit"))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CYCLE_UNIT) return
        val id = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        if (id == -1) return
        if (dobMillis(context, id) < 0) {
            // No date set yet - route the tap to the configure screen instead of cycling.
            context.startActivity(
                Intent(context, ConfigureAgeActivity::class.java)
                    .putExtra(EXTRA_WIDGET_ID, id)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val units = AgeUnit.entries
        val next = units[(units.indexOf(unitOf(context, id)) + 1) % units.size]
        prefs(context).edit(id) { prefix -> putString("${prefix}unit", next.name) }
        updateOne(context, AppWidgetManager.getInstance(context), id)
    }
}
