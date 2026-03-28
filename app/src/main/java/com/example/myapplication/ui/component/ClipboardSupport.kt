package com.example.myapplication.ui.component

import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager as AndroidClipboardManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.copyPlainTextToClipboard(
    clipboard: Clipboard,
    label: String,
    text: String,
) {
    launch {
        clipboard.setClipEntry(
            ClipData.newPlainText(label, text).toClipEntry(),
        )
    }
}

internal fun readPlainTextFromClipboard(
    clipboard: Clipboard,
    context: Context,
): String {
    val nativeClipboard = clipboard.nativeClipboard as? AndroidClipboardManager ?: return ""
    return nativeClipboard.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        .orEmpty()
}
