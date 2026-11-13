package com.example.downloaduploaddemo

import android.os.Handler
import android.os.Looper

/**
 * Tiny in-process hub so the running job/service can report progress to the
 * activity (if it is open). The notification is the source of truth when it is not.
 */
object TransferState {

    data class Snapshot(
        val running: Boolean = false,
        val label: String = "Idle",
        val done: Long = 0,
        val total: Long = -1,
    )

    @Volatile
    var snapshot = Snapshot()
        private set

    var listener: ((Snapshot) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    fun update(s: Snapshot) {
        snapshot = s
        main.post { listener?.invoke(s) }
    }
}
