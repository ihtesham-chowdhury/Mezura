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
import rocks.ndot.widgets.common.Foreground
import rocks.ndot.widgets.kit.Feedback
import java.util.Locale

/**
 * Runs only while something is actually counting: the Pomodoro session, the hourglass or the
 * stopwatch. It exists for two reasons. A widget's face has to be repainted by somebody -
 * RemoteViews can't animate themselves - and a timer that runs out while the app is closed
 * still has to roll over and raise its alert on time.
 *
 * It ticks every 10 seconds rather than every second when it can. The widget's countdown
 * *text* is a Chronometer, which ticks itself in the launcher for free; only the sand level
 * needs repainting, and on a 25-minute session 10 seconds moves it by well under one percent.
 * Near a deadline the delay is trimmed so the switch happens on time, not up to ten seconds late.
 */
class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 2001
        private const val TICK_MS = 10_000L
        private const val WATCH_TICK_MS = 1_000L

        /** Starts or stops the service to match whether anything is counting. */
        fun syncToState(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            val needed = TimerEngine.isRunning(context) ||
                StopwatchEngine.isRunning(context) ||
                HourglassEngine.isRunning(context)
            if (needed) {
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (_: IllegalStateException) {
                    // Background-start restrictions: the widget-tap and in-app paths are both
                    // exempt, so this only trips on an odd OEM. The engines are wall-clock
                    // based, so nothing is lost - the next foreground visit catches up.
                }
            } else {
                context.stopService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val ctx = this@TimerService
            val timerRunning = TimerEngine.isRunning(ctx)
            val watchRunning = StopwatchEngine.isRunning(ctx)
            val glassRunning = HourglassEngine.isRunning(ctx)
            if (!timerRunning && !watchRunning && !glassRunning) {
                stopSelf()
                return
            }
            // Whichever notices a deadline first - this service or the open instrument - raises
            // the alert, exactly once: a chime in the app if it's on screen, else a notification.
            if (timerRunning) {
                TimerEngine.advanceIfDue(ctx)?.let { next ->
                    if (Foreground.appVisible) chimeInApp(ctx) else PomodoroNotifier.notifyPhaseChange(ctx, next)
                }
            }
            if (glassRunning && HourglassEngine.remainingMs(ctx) <= 0L && HourglassEngine.finish(ctx)) {
                if (Foreground.appVisible) chimeInApp(ctx) else PomodoroNotifier.notifyHourglassDone(ctx)
            }
            repaintWidgets(ctx)
            updateNotification()
            handler.postDelayed(this, nextDelay())
        }
    }

    /**
     * A second while the stopwatch runs, because its widget hand visibly moves each second.
     * Otherwise the coarse tick, trimmed to land exactly on the next deadline.
     */
    private fun nextDelay(): Long {
        if (StopwatchEngine.isRunning(this)) return WATCH_TICK_MS
        var delay = TICK_MS
        if (TimerEngine.isRunning(this)) delay = minOf(delay, TimerEngine.remainingMs(this))
        if (HourglassEngine.isRunning(this)) delay = minOf(delay, HourglassEngine.remainingMs(this))
        return delay.coerceIn(50L, TICK_MS)
    }

    private fun chimeInApp(context: Context) {
        Feedback.play(context, Feedback.Sound.CHIME)
        Feedback.haptic(context, Feedback.Haptic.DONE)
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
        val (title, millis, tool) = when {
            TimerEngine.isRunning(this) -> Triple(
                getString(if (TimerEngine.phase(this) == TimerEngine.Phase.BREAK) R.string.timer_break else R.string.timer_focus),
                TimerEngine.remainingMs(this), "pomodoro"
            )
            HourglassEngine.isRunning(this) -> Triple(getString(R.string.hourglass_running), HourglassEngine.remainingMs(this), "hourglass")
            else -> Triple(getString(R.string.stopwatch_running), StopwatchEngine.elapsedMs(this), "stopwatch")
        }
        val totalSeconds = millis / 1000L
        val text = if (totalSeconds >= 3600) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, MainActivity.openIntent(this, tool),
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
