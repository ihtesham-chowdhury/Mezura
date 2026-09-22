package rocks.ndot.widgets.widgets

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import rocks.ndot.widgets.MainActivity
import rocks.ndot.widgets.R

/** A buzz and an alert when a timer changes phase or runs out - the cue a silent widget face
 *  can't give you if the screen is off or you're in another app. */
object PomodoroNotifier {

    private const val CHANNEL_ID = "pomodoro_phase"
    private const val NOTIFICATION_ID = 2002
    private const val HOURGLASS_NOTIFICATION_ID = 2003

    fun notifyPhaseChange(context: Context, newPhase: TimerEngine.Phase) {
        val title = context.getString(
            when (newPhase) {
                TimerEngine.Phase.BREAK -> R.string.pomodoro_notif_break_title
                TimerEngine.Phase.FOCUS -> R.string.pomodoro_notif_work_title
                TimerEngine.Phase.IDLE -> R.string.pomodoro_notif_done_title
            }
        )
        post(context, NOTIFICATION_ID, title, "pomodoro")
    }

    fun notifyHourglassDone(context: Context) {
        post(context, HOURGLASS_NOTIFICATION_ID, context.getString(R.string.hourglass_notif_title), "hourglass")
    }

    private fun post(context: Context, id: Int, title: String, tool: String) {
        vibrate(context)
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, id, MainActivity.openIntent(context, tool),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        // POST_NOTIFICATIONS is asked for in the widget settings; without it this quietly does
        // nothing and the vibration above still marks the change.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pomodoro_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
        )
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 20, 60, 20)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
        }
    }
}
