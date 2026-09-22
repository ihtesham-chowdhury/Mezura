package rocks.ndot.widgets.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.Appearance
import rocks.ndot.widgets.common.Clicks
import rocks.ndot.widgets.common.StopwatchRenderer
import rocks.ndot.widgets.databinding.ActivityStopwatchBinding
import rocks.ndot.widgets.widgets.StopwatchEngine
import rocks.ndot.widgets.widgets.StopwatchWidgetProvider

/**
 * The full-screen stopwatch: a genuinely smooth sweep, laps, and a mechanical click on every
 * press. The widget face is the same artwork, but a widget host can only be pushed about one
 * update a second, so this is where the watch actually behaves like one.
 */
class StopwatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStopwatchBinding

    // The dial itself runs off the Choreographer; this only drives the digital readout, which
    // only needs to change ten times a second to show tenths.
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshReadout()
            ticker.postDelayed(this, 60)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.stopwatchView.apply {
            elapsedProvider = { StopwatchEngine.elapsedMs(this@StopwatchActivity) }
            lapProvider = { StopwatchEngine.lastLap(this@StopwatchActivity) }
            runningProvider = { StopwatchEngine.isRunning(this@StopwatchActivity) }
            accent = Appearance.accent(this@StopwatchActivity)
            dial = currentDial()
        }

        binding.startStopButton.setOnClickListener { toggle() }
        binding.lapButton.setOnClickListener { lap() }

        buildDialChips()
        refreshReadout()
        refreshLapList()
    }

    override fun onResume() {
        super.onResume()
        ticker.post(tick)
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        // Leaving the screen shouldn't stop the watch, but the widget should catch up.
        StopwatchWidgetProvider.updateAll(this)
        super.onPause()
    }

    override fun onDestroy() {
        Clicks.release()
        super.onDestroy()
    }

    private fun toggle() {
        val wasRunning = StopwatchEngine.isRunning(this)
        StopwatchEngine.toggle(this)
        Clicks.play(this, if (wasRunning) Clicks.Kind.STOP else Clicks.Kind.START)
        refreshControls()
        binding.stopwatchView.invalidate()
        StopwatchWidgetProvider.updateAll(this)
        StopwatchWidgetProvider.syncService(this)
    }

    private fun lap() {
        if (!StopwatchEngine.isRunning(this) && StopwatchEngine.elapsedMs(this) == 0L) return
        if (StopwatchEngine.isRunning(this)) {
            StopwatchEngine.recordLap(this)
            Clicks.play(this, Clicks.Kind.LAP)
        } else {
            // A stopped watch has nothing to split, so the second press clears it - the same
            // behaviour as the reset pusher on a two-button stopwatch.
            StopwatchEngine.reset(this)
            Clicks.play(this, Clicks.Kind.STOP)
            StopwatchWidgetProvider.updateAll(this)
        }
        refreshControls()
        refreshLapList()
        binding.stopwatchView.invalidate()
    }

    private fun refreshControls() {
        val running = StopwatchEngine.isRunning(this)
        binding.startStopButton.setText(if (running) R.string.stopwatch_stop else R.string.stopwatch_start)
        binding.lapButton.setText(if (running) R.string.stopwatch_lap else R.string.stopwatch_reset)
    }

    private fun refreshReadout() {
        binding.digital.text = StopwatchEngine.format(StopwatchEngine.elapsedMs(this))
        refreshControls()
    }

    private fun refreshLapList() {
        binding.lapList.removeAllViews()
        val laps = StopwatchEngine.laps(this)
        laps.forEachIndexed { index, total ->
            val previous = if (index == 0) 0L else laps[index - 1]
            val row = TextView(this).apply {
                text = getString(
                    R.string.stopwatch_lap_row,
                    index + 1,
                    StopwatchEngine.format(total - previous),
                    StopwatchEngine.format(total)
                )
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 6)
            }
            binding.lapList.addView(row, 0) // newest at the top
        }
    }

    private fun currentDial(): StopwatchRenderer.Dial =
        if (StopwatchWidgetProvider.isWhiteDial(this)) StopwatchRenderer.Dial.WHITE
        else StopwatchRenderer.Dial.BLACK

    private fun buildDialChips() {
        binding.dialChipGroup.removeAllViews()
        listOf(
            StopwatchRenderer.Dial.BLACK to getString(R.string.stopwatch_dial_black),
            StopwatchRenderer.Dial.WHITE to getString(R.string.stopwatch_dial_white),
        ).forEach { (dialOption, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = dialOption == currentDial()
                setOnClickListener {
                    StopwatchWidgetProvider.setWhiteDial(
                        this@StopwatchActivity,
                        dialOption == StopwatchRenderer.Dial.WHITE
                    )
                    binding.stopwatchView.dial = dialOption
                    StopwatchWidgetProvider.updateAll(this@StopwatchActivity)
                }
            }
            binding.dialChipGroup.addView(chip)
        }
    }
}
