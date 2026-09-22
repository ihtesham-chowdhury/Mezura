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
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Brand
import rocks.ndot.widgets.common.SandTimerRenderer
import rocks.ndot.widgets.common.Shapes
import rocks.ndot.widgets.common.SolarMath
import rocks.ndot.widgets.common.StopwatchRenderer
import rocks.ndot.widgets.common.SundialRenderer
import rocks.ndot.widgets.databinding.ActivityMainBinding
import rocks.ndot.widgets.ui.StopwatchActivity
import rocks.ndot.widgets.widgets.AgeWidgetProvider
import rocks.ndot.widgets.widgets.HourglassWidgetProvider
import rocks.ndot.widgets.widgets.StopwatchWidgetProvider
import rocks.ndot.widgets.widgets.SundialWidgetProvider
import rocks.ndot.widgets.widgets.TimerEngine
import java.util.TimeZone

/**
 * One card per thing, so the focus timer, the stopwatch and the rest are visibly separate
 * rather than one undifferentiated list of controls.
 *
 * Every preview is drawn by the same renderer the widget uses, so what is on this screen is
 * exactly what lands on the home screen. It is also the only place runtime permissions can be
 * requested from - a widget cannot ask for them itself.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.grantPermissionsButton.setOnClickListener { requestAllPermissions() }
        binding.openStopwatchButton.setOnClickListener {
            startActivity(Intent(this, StopwatchActivity::class.java))
        }

        buildColourChips()
        buildDurationChips()
        buildDialChips()
        setUpTransparencySlider()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshTimerState()
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
                    Appearance.setAccent(this@MainActivity, swatch.color)
                    markSelectedSwatch()
                    applyEverywhere()
                }
            }
            binding.colourChipGroup.addView(chip)
        }
        markSelectedSwatch()
    }

    /**
     * Updates the ring on the existing chips. Rebuilding the group from inside a chip's own
     * click handler would detach that chip mid-dispatch.
     */
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

        // stepSize is 5, so a full sweep fires this at most 21 times - cheap enough to push
        // straight through to the home screen on each step.
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

    // ---- focus timer ------------------------------------------------------------------

    private fun buildDurationChips() {
        binding.focusChipGroup.removeAllViews()
        TimerEngine.FOCUS_CHOICES.forEach { minutes ->
            binding.focusChipGroup.addView(
                durationChip(minutes, minutes == TimerEngine.focusMinutes(this)) {
                    TimerEngine.setFocusMinutes(this, minutes)
                    refreshTimerState()
                    applyEverywhere()
                }
            )
        }

        binding.breakChipGroup.removeAllViews()
        TimerEngine.BREAK_CHOICES.forEach { minutes ->
            binding.breakChipGroup.addView(
                durationChip(minutes, minutes == TimerEngine.breakMinutes(this)) {
                    TimerEngine.setBreakMinutes(this, minutes)
                }
            )
        }
    }

    private fun durationChip(minutes: Int, checked: Boolean, onPick: () -> Unit): Chip =
        Chip(this).apply {
            text = if (minutes % 60 == 0) getString(R.string.duration_hours, minutes / 60)
            else getString(R.string.duration_minutes, minutes)
            isCheckable = true
            isChecked = checked
            setOnClickListener { onPick() }
        }

    private fun refreshTimerState() {
        val phase = TimerEngine.phase(this)
        binding.timerStateText.text = when {
            phase == TimerEngine.Phase.IDLE -> getString(R.string.settings_timer_idle)
            TimerEngine.isRunning(this) ->
                getString(R.string.settings_timer_running, TimerEngine.remainingMs(this) / 60_000L + 1)
            else -> getString(R.string.settings_timer_paused)
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
                    isChecked = white == StopwatchWidgetProvider.isWhiteDial(this@MainActivity)
                    setOnClickListener {
                        StopwatchWidgetProvider.setWhiteDial(this@MainActivity, white)
                        refreshPreviews()
                        StopwatchWidgetProvider.updateAll(this@MainActivity)
                    }
                }
                binding.dialChipGroup.addView(chip)
            }
    }

    // ---- previews ---------------------------------------------------------------------

    /** A settings change has to reach widgets already sitting on the home screen. */
    private fun applyEverywhere() {
        refreshPreviews()
        HourglassWidgetProvider.updateAll(this)
        StopwatchWidgetProvider.updateAll(this)
        AgeWidgetProvider.updateAll(this)
        SundialWidgetProvider.updateAll(this)
    }

    private fun refreshPreviews() {
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val body = Appearance.bodyColor(this)
        val accent = Appearance.accentWithAlpha(this)

        val sand = if (Appearance.accent(this) == Brand.RED) 0xFFD79A3A.toInt() else Appearance.accent(this)
        binding.previewSand.setImageBitmap(
            SandTimerRenderer.render(
                px(120), px(160),
                remainingFraction = 0.62f, running = true,
                sandColor = Appearance.withAlpha(sand, Appearance.alphaPercent(this)),
                tick = 7,
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

        binding.previewAge.setImageBitmap(Shapes.pill(px(150), px(72), body))
        val dialState = SolarMath.dialStateNow(System.currentTimeMillis(), 23.81, 90.41, TimeZone.getDefault())
        binding.previewSundial.setImageBitmap(SundialRenderer.render(px(72), dialState, accent, body))
    }

    // ---- permissions ------------------------------------------------------------------

    private fun requestAllPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
        val location = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(Manifest.permission.POST_NOTIFICATIONS)
        binding.permissionStatusText.text =
            getString(R.string.main_permission_status, word(location), word(notifications))
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun word(ok: Boolean) = getString(if (ok) R.string.status_on else R.string.status_off)
}
