package com.example.myapplication.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.model.ChatMessage
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun MessageBubbleActionRows(
    message: ChatMessage,
    copyPayload: String,
    isUser: Boolean,
    isError: Boolean,
    isRemembered: Boolean,
    shouldShowUserActions: Boolean,
    shouldShowAssistantActions: Boolean,
    shouldShowErrorActions: Boolean,
    assistantWidthModifier: Modifier,
    onRetry: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    onToggleMemory: ((String) -> Unit)?,
    onTranslate: ((String) -> Unit)?,
    clipboard: Clipboard,
    clipboardScope: CoroutineScope,
) {
    val context = LocalContext.current

    if (shouldShowUserActions) {
        MessageBubbleUserActions(
            message = message,
            copyPayload = copyPayload,
            isRemembered = isRemembered,
            onTranslate = onTranslate,
            onToggleMemory = onToggleMemory,
            onOpenActionSheet = onOpenActionSheet,
            clipboard = clipboard,
            clipboardScope = clipboardScope,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }

    if (shouldShowAssistantActions) {
        MessageBubbleAssistantActions(
            message = message,
            copyPayload = copyPayload,
            isRemembered = isRemembered,
            modifier = assistantWidthModifier,
            onTranslate = onTranslate,
            onRetry = onRetry,
            onToggleMemory = onToggleMemory,
            onOpenActionSheet = onOpenActionSheet,
            clipboard = clipboard,
            clipboardScope = clipboardScope,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }

    if (shouldShowErrorActions) {
        MessageBubbleErrorActions(
            message = message,
            copyPayload = copyPayload,
            modifier = assistantWidthModifier,
            onRetry = onRetry,
            onTranslate = onTranslate,
            onOpenActionSheet = onOpenActionSheet,
            clipboard = clipboard,
            clipboardScope = clipboardScope,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }
}

@Composable
private fun MessageBubbleUserActions(
    message: ChatMessage,
    copyPayload: String,
    isRemembered: Boolean,
    onTranslate: ((String) -> Unit)?,
    onToggleMemory: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    clipboard: Clipboard,
    clipboardScope: CoroutineScope,
    onShowToast: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        MessageActionIconButton(
            icon = Icons.Outlined.ContentCopy,
            contentDescription = "复制消息",
            onClick = {
                if (copyPayload.isBlank()) {
                    return@MessageActionIconButton
                }
                clipboardScope.copyPlainTextToClipboard(clipboard, "message", copyPayload)
                onShowToast("已复制消息")
            },
        )
        if (onTranslate != null) {
            MessageActionIconButton(
                icon = Icons.Default.Translate,
                contentDescription = "翻译消息",
                onClick = { onTranslate(message.id) },
            )
        }
        if (onToggleMemory != null) {
            MessageActionIconButton(
                icon = Icons.Outlined.Psychology,
                contentDescription = if (isRemembered) "取消记忆" else "记住这条",
                onClick = { onToggleMemory(message.id) },
                highlighted = isRemembered,
            )
        }
        if (onOpenActionSheet != null) {
            MessageActionIconButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "更多操作",
                onClick = { onOpenActionSheet(message.id) },
            )
        }
    }
}

@Composable
private fun MessageBubbleAssistantActions(
    message: ChatMessage,
    copyPayload: String,
    isRemembered: Boolean,
    modifier: Modifier,
    onTranslate: ((String) -> Unit)?,
    onRetry: ((String) -> Unit)?,
    onToggleMemory: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    clipboard: Clipboard,
    clipboardScope: CoroutineScope,
    onShowToast: (String) -> Unit,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = "复制回复",
                onClick = {
                    clipboardScope.copyPlainTextToClipboard(clipboard, "assistant-reply", copyPayload)
                    onShowToast("已复制回复")
                },
            )
        }
        if (onTranslate != null && copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Default.Translate,
                contentDescription = "翻译回复",
                onClick = { onTranslate(message.id) },
            )
        }
        if (onRetry != null) {
            MessageActionIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "重新生成回复",
                onClick = { onRetry(message.id) },
            )
        }
        if (onToggleMemory != null && copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Outlined.Psychology,
                contentDescription = if (isRemembered) "取消记忆" else "记住这条",
                onClick = { onToggleMemory(message.id) },
                highlighted = isRemembered,
            )
        }
        if (onOpenActionSheet != null) {
            MessageActionIconButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "更多操作",
                onClick = { onOpenActionSheet(message.id) },
            )
        }
    }
}

@Composable
private fun MessageBubbleErrorActions(
    message: ChatMessage,
    copyPayload: String,
    modifier: Modifier,
    onRetry: ((String) -> Unit)?,
    onTranslate: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    clipboard: Clipboard,
    clipboardScope: CoroutineScope,
    onShowToast: (String) -> Unit,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = "复制错误消息",
                onClick = {
                    clipboardScope.copyPlainTextToClipboard(clipboard, "error-message", copyPayload)
                    onShowToast("已复制消息")
                },
            )
        }
        MessageActionIconButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = "重试",
            onClick = { onRetry?.invoke(message.id) },
        )
        if (onTranslate != null && copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Default.Translate,
                contentDescription = "翻译错误消息",
                onClick = { onTranslate(message.id) },
            )
        }
        if (onOpenActionSheet != null) {
            MessageActionIconButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "更多操作",
                onClick = { onOpenActionSheet(message.id) },
            )
        }
    }
}
