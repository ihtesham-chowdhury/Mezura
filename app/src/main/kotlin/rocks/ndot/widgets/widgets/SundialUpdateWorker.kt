package rocks.ndot.widgets.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SundialUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { const val KEY_WIDGET_ID = "widgetId" }

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_WIDGET_ID, -1)
        if (id == -1) return Result.failure()
        val mgr = AppWidgetManager.getInstance(applicationContext)
        // The widget may have been removed since this periodic job was scheduled.
        if (id !in mgr.getAppWidgetIds(android.content.ComponentName(applicationContext, SundialWidgetProvider::class.java))) {
            return Result.success()
        }
        SundialWidgetProvider.updateOne(applicationContext, mgr, id)
        return Result.success()
    }
}
