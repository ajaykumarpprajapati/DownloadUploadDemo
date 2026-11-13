package com.example.downloaduploaddemo

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.util.concurrent.CancellationException

/**
 * The actual job, shared by both entry points (UIDT job on Android 14+,
 * foreground service below). Runs on the caller's background thread.
 */
object TransferWork {

    const val TYPE_DOWNLOAD = "download"
    const val TYPE_UPLOAD = "upload"

    enum class Outcome { SUCCESS, CANCELLED, FAILED }

    /**
     * @param onNotify called (throttled) with title/done/total so the caller can
     *                 refresh its own notification the way its API requires.
     */
    fun run(
        context: Context,
        type: String,
        url: String,
        uri: String?,
        cancelled: () -> Boolean,
        onNotify: (title: String, done: Long, total: Long) -> Unit,
    ): Outcome {
        val isUpload = type == TYPE_UPLOAD
        val label = if (isUpload) "Uploading" else "Downloading"
        var lastNotify = 0L

        val onProgress = { done: Long, total: Long ->
            TransferState.update(TransferState.Snapshot(true, label, done, total))
            val now = System.currentTimeMillis()
            if (now - lastNotify > 500) { // don't flood the notification manager
                lastNotify = now
                onNotify(label, done, total)
            }
        }

        return try {
            TransferState.update(TransferState.Snapshot(true, label))
            val result = if (isUpload) {
                TransferEngine.upload(context, url, Uri.parse(uri), cancelled, onProgress)
                "Upload complete"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
                TransferEngine.download(url, File(dir, name), cancelled, onProgress)
                "Download complete: $name"
            }
            TransferState.update(TransferState.Snapshot(false, result))
            Notifs.finished(context, isUpload, result)
            Outcome.SUCCESS
        } catch (e: CancellationException) {
            TransferState.update(TransferState.Snapshot(false, "Cancelled"))
            Outcome.CANCELLED
        } catch (e: Exception) {
            val msg = "$label failed: ${e.message}"
            TransferState.update(TransferState.Snapshot(false, msg))
            Notifs.finished(context, isUpload, msg)
            Outcome.FAILED
        }
    }
}
