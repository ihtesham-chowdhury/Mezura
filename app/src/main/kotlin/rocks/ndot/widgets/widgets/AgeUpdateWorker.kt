package rocks.ndot.widgets.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AgeUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { const val KEY_WIDGET_ID = "widgetId" }

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_WIDGET_ID, -1)
        if (id == -1) return Result.failure()
        val mgr = AppWidgetManager.getInstance(applicationContext)
        if (id !in mgr.getAppWidgetIds(ComponentName(applicationContext, AgeWidgetProvider::class.java))) {
            return Result.success()
        }
        AgeWidgetProvider.updateOne(applicationContext, mgr, id)
        return Result.success()
    }
}
