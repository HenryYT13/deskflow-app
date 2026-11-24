package com.deskflow.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deskflow.app.R
import com.deskflow.app.ui.MainActivity

/**
 * Posts high-priority alerts on the user's phone for alarms, anti-oversleep
 * wakeup checks, Google Calendar 15m reminders, and Focus completions.
 */
object NotificationHelper {
    private const val ALERT_CHANNEL_ID = "deskflow_alerts_v1"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "DeskFlow Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarms, wake-up checks, Focus & Calendar reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 180, 350)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun showAlert(context: Context, title: String, body: String, notifId: Int, urgent: Boolean = true) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        if (urgent) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }
}
