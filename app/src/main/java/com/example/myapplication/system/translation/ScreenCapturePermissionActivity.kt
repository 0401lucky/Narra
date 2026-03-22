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
        fun createIntent(activity: Activity): Intent {
            return Intent(activity, ScreenCapturePermissionActivity::class.java)
        }

        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, ScreenCapturePermissionActivity::class.java)
        }
    }
}
