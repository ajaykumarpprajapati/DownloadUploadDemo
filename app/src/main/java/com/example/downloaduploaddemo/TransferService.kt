package com.example.downloaduploaddemo

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import java.util.concurrent.Executors

/** Fallback for Android 13 and below: a foreground service of type dataSync. */
class TransferService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            return START_NOT_STICKY
        }
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: TransferWork.TYPE_DOWNLOAD
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val uri = intent?.getStringExtra(EXTRA_URI)
        val isUpload = type == TransferWork.TYPE_UPLOAD
        cancelled = false

        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TransferService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Must be called within a few seconds of startForegroundService().
        ServiceCompat.startForeground(
            this, Notifs.NOTIFICATION_ID,
            Notifs.progress(this, isUpload, "Starting…", 0, -1, cancelIntent),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val nm = getSystemService(NotificationManager::class.java)
        executor.execute {
            TransferWork.run(
                this, type, url, uri, cancelled = { cancelled },
                onNotify = { title, done, total ->
                    nm.notify(
                        Notifs.NOTIFICATION_ID,
                        Notifs.progress(this, isUpload, title, done, total, cancelIntent)
                    )
                }
            )
            // Keep the final "complete/failed" notification; drop only the ongoing state.
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /** Android 15+: dataSync services are capped (~6h per day). Stop cleanly when told. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled = true
        stopSelf(startId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.example.downloaduploaddemo.CANCEL"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_URL = "url"
        private const val EXTRA_URI = "uri"

        fun intent(context: Context, type: String, url: String, uri: String?): Intent =
            Intent(context, TransferService::class.java)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_URI, uri)

        fun cancel(context: Context) {
            context.startService(Intent(context, TransferService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
