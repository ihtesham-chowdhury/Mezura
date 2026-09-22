package rocks.ndot.widgets.widgets

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import rocks.ndot.widgets.MainActivity
import rocks.ndot.widgets.R
import java.util.Locale

/**
 * Runs only while a session is actually counting down, and exists for one reason: a widget's
 * fill level has to be repainted by somebody. RemoteViews can't animate on their own, and the
 * OS won't call onUpdate more often than every 30 minutes.
 *
 * It ticks every 10 seconds rather than every second on purpose. The countdown *text* is a
 * Chronometer, which ticks itself in the launcher for free; only the drain level needs
 * repainting, and on a 25-minute session 10 seconds moves it by well under one percent - far
 * too little to see a step. That keeps this to ~150 widget updates a session instead of ~1500.
 */
class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 2001
        private const val TICK_MS = 10_000L
        private const val WATCH_TICK_MS = 1_000L

        /** Runs while either the focus timer or the stopwatch is counting. */
        fun syncToState(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            val needed = TimerEngine.isRunning(context) || StopwatchEngine.isRunning(context)
            if (needed) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val timerRunning = TimerEngine.isRunning(this@TimerService)
            val watchRunning = StopwatchEngine.isRunning(this@TimerService)
            if (!timerRunning && !watchRunning) {
                stopSelf()
                return
            }
            if (timerRunning && TimerEngine.remainingMs(this@TimerService) <= 0L) {
                val next = TimerEngine.advancePhase(this@TimerService)
                PomodoroNotifier.notifyPhaseChange(this@TimerService, next)
            }
            repaintWidgets(this@TimerService)
            updateNotification()
            handler.postDelayed(this, nextDelay())
        }
    }

    /**
     * A second while the stopwatch runs, because its hand visibly moves each second. The sand
     * timer alone can afford a coarser tick, except near a phase boundary, where the delay is
     * trimmed so the switch happens on time rather than up to ten seconds late.
     */
    private fun nextDelay(): Long {
        if (StopwatchEngine.isRunning(this)) return WATCH_TICK_MS
        val remaining = TimerEngine.remainingMs(this)
        return if (remaining in 1 until TICK_MS) remaining else TICK_MS
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(tick)
        repaintWidgets(this)
        handler.postDelayed(tick, nextDelay())
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        // One last repaint so the widgets settle on their paused/finished look.
        repaintWidgets(this)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.timer_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): android.app.Notification {
        val timerRunning = TimerEngine.isRunning(this)
        val remaining = if (timerRunning) TimerEngine.remainingMs(this) else StopwatchEngine.elapsedMs(this)
        val mins = remaining / 60_000L
        val secs = (remaining / 1000L) % 60
        val phaseLabel = when {
            !timerRunning -> getString(R.string.stopwatch_running)
            TimerEngine.phase(this) == TimerEngine.Phase.BREAK -> getString(R.string.timer_break)
            else -> getString(R.string.timer_focus)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(phaseLabel)
            .setContentText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}

/** Repaints every clock-driven widget face currently on the home screen. */
internal fun repaintWidgets(context: Context) {
    HourglassWidgetProvider.updateAll(context)
    StopwatchWidgetProvider.updateAll(context)
}
