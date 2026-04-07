package com.example.myapplication.ui.component

import android.view.accessibility.AccessibilityManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val AccessibilityHighTextContrastKey = "high_text_contrast_enabled"

@Composable
fun rememberSystemHighTextContrastEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember(context) {
        mutableStateOf(resolveSystemHighTextContrastEnabled(context))
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = resolveSystemHighTextContrastEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return enabled
}

private fun resolveSystemHighTextContrastEnabled(context: android.content.Context): Boolean {
    val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    if (accessibilityManager == null) {
        return false
    }
    return Settings.Secure.getInt(
        context.contentResolver,
        AccessibilityHighTextContrastKey,
        0,
    ) == 1
}
