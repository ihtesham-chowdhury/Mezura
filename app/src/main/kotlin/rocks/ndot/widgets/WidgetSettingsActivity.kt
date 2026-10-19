package rocks.ndot.widgets

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Shapes
import rocks.ndot.widgets.common.StopwatchRenderer
import rocks.ndot.widgets.databinding.ActivityWidgetSettingsBinding
import rocks.ndot.widgets.widgets.AgeWidgetProvider
import rocks.ndot.widgets.widgets.CalendarWidgetProvider
import rocks.ndot.widgets.widgets.CalendarWidgetRenderer
import rocks.ndot.widgets.widgets.CalendarWidgetSpan
import rocks.ndot.widgets.widgets.CalendarWidgetStyle
import rocks.ndot.widgets.widgets.StopwatchWidgetProvider

/**
 * Settings for the home-screen widgets.
 * Allows configuring global palette/transparency, calendar finish and span, and stopwatch dial.
 */
class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetSettingsBinding

    private var calendarStyle = CalendarWidgetStyle.APPLE_GLASS
    private var calendarSpan = CalendarWidgetSpan.MONTH

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedStyle = AppPrefs.getDefault(this, AppPrefs.KEY_DEFAULT_CALENDAR_STYLE, "glass")
        calendarStyle = if (savedStyle == "paper") CalendarWidgetStyle.PAPER else CalendarWidgetStyle.APPLE_GLASS
        val savedSpan = AppPrefs.getDefault(this, AppPrefs.KEY_DEFAULT_CALENDAR_SPAN, "month")
        calendarSpan = if (savedSpan == "year") CalendarWidgetSpan.YEAR else CalendarWidgetSpan.MONTH

        binding.grantPermissionsButton.setOnClickListener { requestAllPermissions() }
        binding.openStopwatchButton.setOnClickListener {
            startActivity(MainActivity.openIntent(this, "stopwatch"))
        }

        buildColourChips()
        buildCalendarChips()
        buildDialChips()
        setUpTransparencySlider()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshPreviews()
    }

    // ---- appearance -------------------------------------------------------------------

    private fun buildColourChips() {
        binding.colourChipGroup.removeAllViews()
        Appearance.PALETTE.forEach { swatch ->
            val chip = Chip(this).apply {
                text = "    "
                isCheckable = true
                chipBackgroundColor = ColorStateList.valueOf(swatch.color)
                chipStrokeWidth = resources.displayMetrics.density * 2
                contentDescription = swatch.id
                setOnClickListener {
                    Appearance.setAccent(this@WidgetSettingsActivity, swatch.color)
                    markSelectedSwatch()
                    applyEverywhere()
                }
            }
            binding.colourChipGroup.addView(chip)
        }
        markSelectedSwatch()
    }

    private fun markSelectedSwatch() {
        val current = Appearance.accent(this)
        Appearance.PALETTE.forEachIndexed { index, swatch ->
            val chip = binding.colourChipGroup.getChildAt(index) as? Chip ?: return@forEachIndexed
            val selected = swatch.color == current
            chip.isChecked = selected
            chip.chipStrokeColor = ColorStateList.valueOf(if (selected) Color.BLACK else Color.TRANSPARENT)
        }
    }

    private fun setUpTransparencySlider() {
        val percent = Appearance.alphaPercent(this)
        binding.transparencySlider.value = percent.toFloat()
        showTransparencyValue(percent)

        binding.transparencySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val newPercent = value.toInt()
            Appearance.setAlphaPercent(this, newPercent)
            showTransparencyValue(newPercent)
            applyEverywhere()
        }
    }

    private fun showTransparencyValue(percent: Int) {
        binding.transparencyValue.text = getString(R.string.settings_opacity_value, percent)
    }

    // ---- calendar widget --------------------------------------------------------------

    private fun buildCalendarChips() {
        binding.calendarStyleChipGroup.removeAllViews()
        listOf(
            CalendarWidgetStyle.APPLE_GLASS to "Apple Glass",
            CalendarWidgetStyle.PAPER to "Archival Paper"
        ).forEach { (style, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = style == calendarStyle
                setOnClickListener {
                    calendarStyle = style
                    AppPrefs.setDefault(
                        this@WidgetSettingsActivity,
                        AppPrefs.KEY_DEFAULT_CALENDAR_STYLE,
                        if (style == CalendarWidgetStyle.PAPER) "paper" else "glass",
                        label
                    )
                    refreshPreviews()
                    applyEverywhere()
                }
            }
            binding.calendarStyleChipGroup.addView(chip)
        }

        binding.calendarSpanChipGroup.removeAllViews()
        listOf(
            CalendarWidgetSpan.MONTH to "Full Month",
            CalendarWidgetSpan.YEAR to "Full Year Matrix"
        ).forEach { (span, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = span == calendarSpan
                setOnClickListener {
                    calendarSpan = span
                    AppPrefs.setDefault(
                        this@WidgetSettingsActivity,
                        AppPrefs.KEY_DEFAULT_CALENDAR_SPAN,
                        if (span == CalendarWidgetSpan.YEAR) "year" else "month",
                        label
                    )
                    refreshPreviews()
                    applyEverywhere()
                }
            }
            binding.calendarSpanChipGroup.addView(chip)
        }
    }

    // ---- stopwatch --------------------------------------------------------------------

    private fun buildDialChips() {
        binding.dialChipGroup.removeAllViews()
        listOf(false to R.string.stopwatch_dial_black, true to R.string.stopwatch_dial_white)
            .forEach { (white, labelRes) ->
                val chip = Chip(this).apply {
                    text = getString(labelRes)
                    isCheckable = true
                    isChecked = white == StopwatchWidgetProvider.isWhiteDial(this@WidgetSettingsActivity)
                    setOnClickListener {
                        StopwatchWidgetProvider.setWhiteDial(this@WidgetSettingsActivity, white)
                        refreshPreviews()
                        StopwatchWidgetProvider.updateAll(this@WidgetSettingsActivity)
                    }
                }
                binding.dialChipGroup.addView(chip)
            }
    }

    // ---- previews ---------------------------------------------------------------------

    private fun applyEverywhere() {
        refreshPreviews()
        CalendarWidgetProvider.updateAll(this)
        StopwatchWidgetProvider.updateAll(this)
        AgeWidgetProvider.updateAll(this)
    }

    private fun refreshPreviews() {
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val body = Appearance.bodyColor(this)

        binding.previewCalendar.setImageBitmap(
            CalendarWidgetRenderer.renderToBitmap(
                this,
                px(260), px(180),
                calendarStyle,
                calendarSpan
            )
        )

        binding.previewStopwatch.setImageBitmap(
            StopwatchRenderer.render(
                px(150), px(175),
                face = StopwatchRenderer.Face(elapsedMs = 37_400L, splitMs = 12_000L, running = false),
                dial = if (StopwatchWidgetProvider.isWhiteDial(this)) StopwatchRenderer.Dial.WHITE
                else StopwatchRenderer.Dial.BLACK,
                accent = Appearance.accent(this),
            )
        )

        binding.previewAge.setImageBitmap(Shapes.pill(px(180), px(80), body))
    }

    // ---- permissions ------------------------------------------------------------------

    private fun requestAllPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (wanted.isNotEmpty()) requestPermissions.launch(wanted.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
                )
            }
        }
    }

    private fun refreshPermissionStatus() {
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(Manifest.permission.POST_NOTIFICATIONS)
        binding.permissionStatusText.text =
            getString(if (notifications) R.string.status_on else R.string.status_off)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
