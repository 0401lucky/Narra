package com.example.myapplication.system.translation

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ScreenCapturePermissionActivity : ComponentActivity() {
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        ScreenTranslatorService.submitCapturePermissionResult(
            context = this,
            resultCode = result.resultCode,
            dataIntent = result.data,
            requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L),
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            return
        }

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "screen_capture_request_id"

        fun createIntent(activity: Activity): Intent {
            return Intent(activity, ScreenCapturePermissionActivity::class.java)
        }

        fun createIntent(context: android.content.Context, requestId: Long = 0L): Intent {
            return Intent(context, ScreenCapturePermissionActivity::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
        }
    }
}
