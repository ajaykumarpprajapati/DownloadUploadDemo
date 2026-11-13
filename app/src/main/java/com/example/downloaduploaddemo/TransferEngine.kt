package com.example.downloaduploaddemo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** Plain HTTP download (with resume) and multipart upload. No Android service logic here. */
object TransferEngine {

    private const val BUFFER = 64 * 1024

    /**
     * Downloads [url] into [dest]. If [dest] already holds partial data, asks the server
     * to continue from that offset (HTTP Range), so an interrupted job can resume.
     */
    fun download(
        url: String,
        dest: File,
        cancelled: () -> Boolean,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val existing = if (dest.exists()) dest.length() else 0L
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        try {
            conn.connect()
            val code = conn.responseCode
            if (code == 416) { // Range not satisfiable: we already have the whole file.
                onProgress(existing, existing)
                return
            }
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resumed) throw IOException("HTTP $code")

            val offset = if (resumed) existing else 0L
            val length = conn.contentLengthLong
            val total = if (length < 0) -1L else length + offset
            var done = offset

            FileOutputStream(dest, /* append = */ resumed).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        if (cancelled()) throw CancellationException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Streams the file behind [uri] to [url] as a multipart/form-data POST. */
    fun upload(
        context: Context,
        url: String,
        uri: Uri,
        cancelled: () -> Boolean,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val (name, size) = describe(context, uri)
        val boundary = "----DemoBoundary${System.nanoTime()}"
        val head = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        // Stream instead of buffering the whole body in memory.
        conn.setChunkedStreamingMode(BUFFER)
        try {
            var done = 0L
            conn.outputStream.use { out ->
                out.write(head)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        if (cancelled()) throw CancellationException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, size)
                    }
                } ?: throw IOException("Cannot open $uri")
                out.write(tail)
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    /** Display name and size (or -1) of a content Uri. */
    fun describe(context: Context, uri: Uri): Pair<String, Long> {
        var name = "upload.bin"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return name to size
    }
}
