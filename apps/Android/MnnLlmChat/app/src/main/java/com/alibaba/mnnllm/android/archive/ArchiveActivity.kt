package com.alibaba.mnnllm.android.archive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.alibaba.mnnllm.android.archive.reminder.ReminderScheduler
import com.alibaba.mnnllm.android.archive.ui.ArchiveApp

/**
 * Single-activity host for the Local Archive Mate feature (Jetpack Compose).
 * Entry point reachable from the main screen and from reminder notifications.
 */
class ArchiveActivity : ComponentActivity() {

    private val viewModel: ArchiveViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)
        requestRuntimePermissions()

        val openArchiveId = intent.getLongExtra("open_archive_id", -1L)

        setContent {
            ArchiveApp(
                viewModel = viewModel,
                initialArchiveId = if (openArchiveId > 0) openArchiveId else null
            )
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        perms.add(Manifest.permission.CAMERA)
        perms.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val toRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) requestPermissions.launch(toRequest.toTypedArray())
    }
}
