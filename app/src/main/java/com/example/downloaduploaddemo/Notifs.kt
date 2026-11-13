package com.example.downloaduploaddemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifs {

    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "transfers"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Transfers", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** Ongoing notification with a progress bar (indeterminate when [total] is unknown). */
    fun progress(
        context: Context,
        isUpload: Boolean,
        title: String,
        done: Long,
        total: Long,
        cancel: PendingIntent? = null,
    ): Notification {
        ensureChannel(context)
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (isUpload) android.R.drawable.stat_sys_upload
                else android.R.drawable.stat_sys_download
            )
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
        if (total > 0) {
            b.setProgress(100, (done * 100 / total).toInt(), false)
            b.setContentText("${done / 1024} / ${total / 1024} KB")
        } else {
            b.setProgress(0, 0, true)
            b.setContentText("${done / 1024} KB")
        }
        cancel?.let { b.addAction(0, "Cancel", it) }
        return b.build()
    }

    /** Final, dismissible notification shown after the transfer ends. */
    fun finished(context: Context, isUpload: Boolean, text: String) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (isUpload) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_sys_download_done
            )
            .setContentTitle(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
