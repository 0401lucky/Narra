package com.example.myapplication.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.model.ChatMessage

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
) {
    val context = LocalContext.current

    if (shouldShowUserActions) {
        MessageBubbleUserActions(
            message = message,
            copyPayload = copyPayload,
            isRemembered = isRemembered,
            onToggleMemory = onToggleMemory,
            onOpenActionSheet = onOpenActionSheet,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }

    if (shouldShowAssistantActions) {
        MessageBubbleAssistantActions(
            message = message,
            copyPayload = copyPayload,
            isRemembered = isRemembered,
            modifier = assistantWidthModifier,
            onRetry = onRetry,
            onToggleMemory = onToggleMemory,
            onOpenActionSheet = onOpenActionSheet,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }

    if (shouldShowErrorActions) {
        MessageBubbleErrorActions(
            message = message,
            copyPayload = copyPayload,
            modifier = assistantWidthModifier,
            onRetry = onRetry,
            onOpenActionSheet = onOpenActionSheet,
            onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }
}

@Composable
private fun MessageBubbleUserActions(
    message: ChatMessage,
    copyPayload: String,
    isRemembered: Boolean,
    onToggleMemory: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    onShowToast: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
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
        if (isRemembered && onToggleMemory != null) {
            MessageActionIconButton(
                icon = Icons.Outlined.Psychology,
                contentDescription = "取消记忆",
                onClick = { onToggleMemory(message.id) },
                highlighted = true,
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
    onRetry: ((String) -> Unit)?,
    onToggleMemory: ((String) -> Unit)?,
    onOpenActionSheet: ((String) -> Unit)?,
    onShowToast: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
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
        if (onRetry != null) {
            MessageActionIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "重新生成回复",
                onClick = { onRetry(message.id) },
            )
        }
        if (isRemembered && onToggleMemory != null && copyPayload.isNotBlank()) {
            MessageActionIconButton(
                icon = Icons.Outlined.Psychology,
                contentDescription = "取消记忆",
                onClick = { onToggleMemory(message.id) },
                highlighted = true,
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
    onOpenActionSheet: ((String) -> Unit)?,
    onShowToast: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
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
        if (onOpenActionSheet != null) {
            MessageActionIconButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "更多操作",
                onClick = { onOpenActionSheet(message.id) },
            )
        }
    }
}
