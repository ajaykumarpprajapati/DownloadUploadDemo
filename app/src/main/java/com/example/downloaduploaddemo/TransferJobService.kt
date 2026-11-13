package com.example.downloaduploaddemo

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/**
 * Android 14+ user-initiated data transfer (UIDT) job. The user tapped a button,
 * so the system lets this run long, shows it in the Task Manager (with a Stop
 * button) and requires us to attach a notification.
 */
@RequiresApi(34)
class TransferJobService : JobService() {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val extras = params.extras
        val type = extras.getString(EXTRA_TYPE) ?: TransferWork.TYPE_DOWNLOAD
        val url = extras.getString(EXTRA_URL).orEmpty()
        val uri = extras.getString(EXTRA_URI)
        val isUpload = type == TransferWork.TYPE_UPLOAD

        // Required for UIDT jobs: attach the notification to the job.
        setNotification(
            params, Notifs.NOTIFICATION_ID,
            Notifs.progress(this, isUpload, "Starting…", 0, -1),
            JOB_END_NOTIFICATION_POLICY_DETACH // keep it (e.g. the result) after the job ends
        )

        executor.execute {
            val outcome = TransferWork.run(
                this, type, url, uri, cancelled = { stopped },
                onNotify = { title, done, total ->
                    setNotification(
                        params, Notifs.NOTIFICATION_ID,
                        Notifs.progress(this, isUpload, title, done, total),
                        JOB_END_NOTIFICATION_POLICY_DETACH
                    )
                }
            )
            // If the system stopped us, onStopJob already handled rescheduling.
            if (!stopped) {
                jobFinished(params, /* wantsReschedule = */ outcome == TransferWork.Outcome.FAILED)
            }
        }
        return true // work continues on another thread
    }

    /** System (or user, via Task Manager) is stopping the job. Retry later; download resumes. */
    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true
    }

    companion object {
        private const val JOB_ID = 1001
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_URL = "url"
        private const val EXTRA_URI = "uri"

        /** Must be called while the app is visible, right after a user action. */
        fun schedule(context: Context, type: String, url: String, uri: String?, estimatedBytes: Long) {
            val extras = PersistableBundle().apply {
                putString(EXTRA_TYPE, type)
                putString(EXTRA_URL, url)
                uri?.let { putString(EXTRA_URI, it) }
            }
            val isUpload = type == TransferWork.TYPE_UPLOAD
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, TransferJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // mandatory for UIDT
                .setEstimatedNetworkBytes(
                    if (isUpload) 0 else estimatedBytes,
                    if (isUpload) estimatedBytes else 0
                )
                .setExtras(extras)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
