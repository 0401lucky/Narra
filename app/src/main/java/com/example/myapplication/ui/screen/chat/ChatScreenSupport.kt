package com.example.myapplication.ui.screen.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.ui.component.NarraIconButton
import kotlin.math.abs

private const val ChatBottomFollowTolerancePx = 48

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    title: String,
    onOpenConversationDrawer: () -> Unit,
    onOpenPromptDebugSheet: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onCreateConversation: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            NarraIconButton(onClick = onOpenConversationDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "历史记录",
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            NarraIconButton(onClick = onOpenPromptDebugSheet) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "查看上下文调试信息",
                )
            }
            NarraIconButton(onClick = onOpenExportSheet) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "导出与分享",
                )
            }
            NarraIconButton(onClick = onCreateConversation) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建会话",
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptDebugSheet(
    debugDump: String,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "本轮上下文",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                Text(
                    text = debugDump.ifBlank { "当前还没有可展示的上下文调试信息。发送一条消息后再查看这里。" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun buildExportFileName(title: String): String {
    val normalized = title.trim()
        .ifBlank { "conversation" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
    return "$normalized.md"
}

internal fun resolveSelectedAttachment(
    context: Context,
    uri: Uri,
    type: AttachmentType,
): MessageAttachment {
    val displayName = resolveDisplayName(context, uri).ifBlank {
        if (type == AttachmentType.IMAGE) "已选图片" else "已选文件"
    }
    val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank {
        if (type == AttachmentType.IMAGE) "image/*" else "text/plain"
    }
    if (type == AttachmentType.FILE) {
        requireLikelySupportedTextFile(displayName, mimeType)
    }

    return MessageAttachment(
        type = type,
        uri = uri.toString(),
        mimeType = mimeType,
        fileName = displayName,
    )
}

private fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }.orEmpty()
}

private fun requireLikelySupportedTextFile(
    fileName: String,
    mimeType: String,
) {
    val normalizedName = fileName.lowercase()
    val normalizedMimeType = mimeType.lowercase()
    if (normalizedMimeType.startsWith("image/")) {
        throw IllegalStateException("图片请使用图片上传入口")
    }
    if (normalizedName.endsWith(".pdf") ||
        normalizedName.endsWith(".doc") ||
        normalizedName.endsWith(".docx") ||
        normalizedName.endsWith(".xls") ||
        normalizedName.endsWith(".xlsx") ||
        normalizedName.endsWith(".ppt") ||
        normalizedName.endsWith(".pptx") ||
        normalizedName.endsWith(".zip") ||
        normalizedName.endsWith(".rar") ||
        normalizedName.endsWith(".7z") ||
        normalizedName.endsWith(".apk")
    ) {
        throw IllegalStateException("当前仅支持上传文本类文件和图片")
    }
    if (normalizedMimeType == "application/pdf" ||
        normalizedMimeType.contains("word") ||
        normalizedMimeType.contains("excel") ||
        normalizedMimeType.contains("spreadsheet") ||
        normalizedMimeType.contains("presentation") ||
        normalizedMimeType.contains("zip")
    ) {
        throw IllegalStateException("当前仅支持上传文本类文件和图片")
    }
}

internal fun isSupportedAvatarUrl(url: String): Boolean {
    return url.startsWith("https://") || url.startsWith("http://")
}

internal fun messageContentType(message: com.example.myapplication.model.ChatMessage): String {
    return when {
        message.role == com.example.myapplication.model.MessageRole.USER &&
            message.parts.any { it.type != com.example.myapplication.model.ChatMessagePartType.TEXT } ->
            "user-rich"
        message.role == com.example.myapplication.model.MessageRole.USER ->
            "user-text"
        message.status == com.example.myapplication.model.MessageStatus.LOADING ->
            "assistant-loading"
        message.status == com.example.myapplication.model.MessageStatus.ERROR ->
            "assistant-error"
        message.reasoningContent.isNotBlank() ->
            "assistant-reasoning"
        message.parts.any { it.type != com.example.myapplication.model.ChatMessagePartType.TEXT } ||
            message.attachments.isNotEmpty() ->
            "assistant-rich"
        else ->
            "assistant-text"
    }
}

internal data class ChatListMeasuredItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun isListNearBottom(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<ChatListMeasuredItem>,
    tolerancePx: Int = ChatBottomFollowTolerancePx,
): Boolean {
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return true
    }

    val boundaryStartIndex = (totalItems - 2).coerceAtLeast(0)
    val boundaryItem = visibleItems.lastOrNull { it.index >= boundaryStartIndex } ?: return false
    val distance = abs(boundaryItem.offset + boundaryItem.size - viewportEndOffset)
    return distance <= tolerancePx
}

internal fun conversationEndDeltaPx(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<ChatListMeasuredItem>,
): Int {
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return 0
    }

    val boundaryStartIndex = (totalItems - 2).coerceAtLeast(0)
    val boundaryItem = visibleItems.lastOrNull { it.index >= boundaryStartIndex } ?: return 0
    return (boundaryItem.offset + boundaryItem.size - viewportEndOffset).coerceAtLeast(0)
}

private fun LazyListState.visibleMeasuredItems(): List<ChatListMeasuredItem> {
    return layoutInfo.visibleItemsInfo.map { item ->
        ChatListMeasuredItem(
            index = item.index,
            offset = item.offset,
            size = item.size,
        )
    }
}

internal fun isListNearBottom(
    listState: LazyListState,
): Boolean {
    val layoutInfo = listState.layoutInfo
    return isListNearBottom(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleMeasuredItems(),
    )
}

internal fun conversationEndDeltaPx(
    listState: LazyListState,
): Int {
    val layoutInfo = listState.layoutInfo
    return conversationEndDeltaPx(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleMeasuredItems(),
    )
}
