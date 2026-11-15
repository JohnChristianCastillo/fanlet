package dev.johncastles.fanlet.overlay

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.johncastles.fanlet.R

/** The notification channel and the "bars are off" fallback notification. */
object OverlayNotifications {

    const val CHANNEL_ID = "overlay_status"

    /** Shown when Android did not allow the bars to start on their own. */
    private const val RESTART_NOTIFICATION_ID = 2
    private const val REQUEST_RESTART = 10

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * "Edge bars are off. Tap to turn them back on." Tapping starts the service directly:
     * starting a foreground service from a notification the user taps is allowed.
     */
    fun showRestart(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // notifications declined: nothing to show
        }
        ensureChannel(context)
        val start = PendingIntent.getForegroundService(
            context,
            REQUEST_RESTART,
            Intent(context, OverlayService::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_restart_title))
            .setContentText(context.getString(R.string.notification_restart_text))
            .setContentIntent(start)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(RESTART_NOTIFICATION_ID, notification)
    }

    fun cancelRestart(context: Context) {
        NotificationManagerCompat.from(context).cancel(RESTART_NOTIFICATION_ID)
    }
}
