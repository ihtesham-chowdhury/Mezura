package rocks.ndot.widgets.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import rocks.ndot.widgets.databinding.ActivityConfigureAgeBinding
import rocks.ndot.widgets.widgets.AgeWidgetProvider
import java.util.Calendar

class ConfigureAgeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigureAgeBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        binding = ActivityConfigureAgeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val defaultDob = Calendar.getInstance().apply { add(Calendar.YEAR, -25) }
        binding.datePicker.maxDate = System.currentTimeMillis()
        binding.datePicker.init(
            defaultDob.get(Calendar.YEAR), defaultDob.get(Calendar.MONTH), defaultDob.get(Calendar.DAY_OF_MONTH), null
        )

        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        val dob = Calendar.getInstance().apply {
            set(binding.datePicker.year, binding.datePicker.month, binding.datePicker.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        AgeWidgetProvider.prefs(this).edit(appWidgetId) { prefix ->
            putLong("${prefix}dob", dob.timeInMillis)
            putString("${prefix}unit", AgeWidgetProvider.AgeUnit.YEARS.name)
        }

        val mgr = AppWidgetManager.getInstance(this)
        AgeWidgetProvider.updateOne(this, mgr, appWidgetId)
        AgeWidgetProvider.scheduleRefresh(this, appWidgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
