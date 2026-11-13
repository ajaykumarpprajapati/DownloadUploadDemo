package com.example.downloaduploaddemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.downloaduploaddemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                // Keep read access so a rescheduled job can still open the file later.
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val size = TransferEngine.describe(this, uri).second
                start(TransferWork.TYPE_UPLOAD, binding.etUploadUrl.text.toString(), uri.toString(), size)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDownload.setOnClickListener {
            start(TransferWork.TYPE_DOWNLOAD, binding.etDownloadUrl.text.toString(), null, 100L * 1024 * 1024)
        }
        binding.btnUpload.setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        binding.btnCancel.setOnClickListener { cancel() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        TransferState.listener = ::render
        render(TransferState.snapshot)
    }

    override fun onStop() {
        TransferState.listener = null
        super.onStop()
    }

    private fun start(type: String, url: String, uri: String?, estimatedBytes: Long) {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: user-initiated data transfer job (we are visible, user just tapped).
            TransferJobService.schedule(this, type, url, uri, estimatedBytes)
        } else {
            ContextCompat.startForegroundService(this, TransferService.intent(this, type, url, uri))
        }
    }

    private fun cancel() {
        if (Build.VERSION.SDK_INT >= 34) TransferJobService.cancel(this)
        else TransferService.cancel(this)
    }

    private fun render(s: TransferState.Snapshot) {
        val pct = if (s.total > 0) (s.done * 100 / s.total).toInt() else 0
        binding.progress.isIndeterminate = s.running && s.total <= 0
        binding.progress.progress = pct
        binding.tvStatus.text =
            if (s.running) "${s.label}: ${s.done / 1024} KB" + if (s.total > 0) " ($pct%)" else ""
            else s.label
    }
}
