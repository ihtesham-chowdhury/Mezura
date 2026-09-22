package rocks.ndot.widgets.widgets

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import rocks.ndot.widgets.MainActivity
import rocks.ndot.widgets.R

/** A buzz and an alert when a phase flips - the cue the silent widget face can't give you
 *  if the screen is off or you're in another app. */
object PomodoroNotifier {

    private const val CHANNEL_ID = "pomodoro_phase"
    private const val NOTIFICATION_ID = 2002

    fun notifyPhaseChange(context: Context, newPhase: TimerEngine.Phase) {
        if (newPhase == TimerEngine.Phase.IDLE) return
        vibrate(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
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
        }

        val title = context.getString(
            if (newPhase == TimerEngine.Phase.BREAK) R.string.pomodoro_notif_break_title
            else R.string.pomodoro_notif_work_title
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pomodoro)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        // POST_NOTIFICATIONS is asked for in MainActivity; without it this quietly does
        // nothing and the vibration above still marks the change.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 20, 60, 20)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
