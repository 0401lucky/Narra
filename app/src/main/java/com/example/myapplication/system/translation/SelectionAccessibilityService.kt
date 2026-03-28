package com.example.myapplication.system.translation

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myapplication.ChatApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelectionAccessibilityService : AccessibilityService() {
    private companion object {
        const val MinSelectedTextLength = 2
        const val MaxSelectedTextLength = 180
        const val DuplicateSubmitWindowMillis = 1_500L
        const val SelectionStableDelayMillis = 550L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var serviceEnabled = false
    private var selectedTextEnabled = true
    private var lastSubmittedText = ""
    private var lastSubmittedAt = 0L
    private var pendingSelectionJob: Job? = null
    private var pendingSelectionText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as ChatApplication
        serviceScope.launch {
            app.aiSettingsRepository.settingsFlow.collectLatest { settings ->
                serviceEnabled = settings.screenTranslationSettings.serviceEnabled
                selectedTextEnabled = settings.screenTranslationSettings.selectedTextEnabled
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString().orEmpty()
        val appLabel = resolveApplicationLabel(packageName)
        if (packageName.isNotBlank()) {
            ForegroundAppSnapshot.packageName = packageName
            ForegroundAppSnapshot.appLabel = appLabel
        }

        if (!serviceEnabled || !selectedTextEnabled) {
            clearPendingSelection()
            return
        }
        if (event.eventType != TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            return
        }
        if (packageName == this.packageName) {
            return
        }

        val text = extractSelectedText(event.source)
        if (text.isBlank()) {
            clearPendingSelection()
            return
        }
        if (text == pendingSelectionText) {
            return
        }

        pendingSelectionJob?.cancel()
        pendingSelectionText = text
        val capturedPackageName = packageName
        val capturedAppLabel = appLabel
        pendingSelectionJob = serviceScope.launch {
            delay(SelectionStableDelayMillis)
            val stableText = pendingSelectionText
            if (stableText.isBlank()) {
                return@launch
            }
            submitStableSelection(
                text = stableText,
                packageName = capturedPackageName,
                appLabel = capturedAppLabel,
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        clearPendingSelection()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun submitStableSelection(
        text: String,
        packageName: String,
        appLabel: String,
    ) {
        val now = System.currentTimeMillis()
        if (text == lastSubmittedText && now - lastSubmittedAt < DuplicateSubmitWindowMillis) {
            return
        }
        lastSubmittedText = text
        lastSubmittedAt = now
        pendingSelectionText = ""

        ScreenTranslatorService.submitSelectedText(
            context = this,
            text = text,
            sourcePackage = packageName,
            sourceAppLabel = appLabel,
        )
    }

    private fun clearPendingSelection() {
        pendingSelectionJob?.cancel()
        pendingSelectionJob = null
        pendingSelectionText = ""
        ScreenTranslatorService.dismissSelectedTextPrompt(this)
    }

    private fun extractSelectedText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        if (node.isPassword || node.isEditable) {
            return ""
        }
        val nodeText = node.text?.toString().orEmpty()
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (nodeText.isBlank() || start < 0 || end <= start || end > nodeText.length) {
            return ""
        }
        val selectedText = nodeText.substring(start, end).trim()
        if (selectedText.length !in MinSelectedTextLength..MaxSelectedTextLength) {
            return ""
        }
        if (selectedText.none { it.isLetterOrDigit() || Character.isIdeographic(it.code) }) {
            return ""
        }
        return selectedText
    }

    private fun resolveApplicationLabel(packageName: String): String {
        if (packageName.isBlank()) {
            return ""
        }
        return runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrElse { packageName }
    }
}
