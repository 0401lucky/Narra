package com.example.myapplication.ui.screen.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toPlainText
import com.example.myapplication.viewmodel.TranslationUiState

data class ConversationExportOptions(
    val includeReasoning: Boolean = true,
    val includeModelName: Boolean = true,
    val includeImageLinks: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationResultSheet(
    translation: TranslationUiState,
    onDismissRequest: () -> Unit,
    onReplaceInput: () -> Unit,
    onAppendToInput: () -> Unit,
    onSendAsMessage: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "翻译结果",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = buildString {
                        append(translation.sourceLabel)
                        append(" → ")
                        append(translation.targetLanguage)
                        if (translation.modelName.isNotBlank()) {
                            append(" · ")
                            append(translation.modelName)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SheetPreviewCard(
                title = "原文",
                body = translation.sourceText,
            )

            SheetPreviewCard(
                title = if (translation.isLoading) "译文生成中" else "译文",
                body = if (translation.isLoading) "正在调用翻译模型…" else translation.translatedText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(translation.translatedText))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text("复制")
                }
                OutlinedButton(
                    onClick = onReplaceInput,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text("替换输入框")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onAppendToInput,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text("插入输入框")
                }
                Button(
                    onClick = onSendAsMessage,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text("发送译文")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationExportSheet(
    title: String,
    options: ConversationExportOptions,
    onDismissRequest: () -> Unit,
    onUpdateOptions: (ConversationExportOptions) -> Unit,
    onExportMarkdown: () -> Unit,
    onCopyPlainText: () -> Unit,
    onShareConversation: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "导出与分享",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = title.ifBlank { "未命名会话" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ExportOptionRow(
                title = "包含思考内容",
                checked = options.includeReasoning,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeReasoning = it))
                },
            )
            ExportOptionRow(
                title = "包含模型名称",
                checked = options.includeModelName,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeModelName = it))
                },
            )
            ExportOptionRow(
                title = "包含图片链接",
                checked = options.includeImageLinks,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeImageLinks = it))
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCopyPlainText,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制纯文本")
                }
                OutlinedButton(
                    onClick = onShareConversation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("系统分享")
                }
            }

            Button(
                onClick = onExportMarkdown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导出 Markdown")
            }
        }
    }
}

@Composable
private fun ExportOptionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SheetPreviewCard(
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body.ifBlank { "暂无内容" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun buildConversationMarkdown(
    title: String,
    messages: List<ChatMessage>,
    options: ConversationExportOptions,
): String {
    return buildString {
        append("# ")
        append(title.ifBlank { "未命名会话" })
        append("\n\n")
        messages.forEach { message ->
            append("## ")
            append(buildMessageHeading(message, options.includeModelName))
            append("\n\n")
            val bodyText = normalizeChatMessageParts(message.parts).toPlainText()
                .ifBlank { message.content.trim() }
            if (bodyText.isNotBlank()) {
                append(bodyText)
                append("\n\n")
            }
            if (options.includeReasoning && message.reasoningContent.isNotBlank()) {
                append("> 思考内容\n>\n")
                message.reasoningContent.lines().forEach { line ->
                    append("> ").append(line).append('\n')
                }
                append('\n')
            }
            if (options.includeImageLinks) {
                val imageUris = resolveImageUris(message)
                if (imageUris.isNotEmpty()) {
                    append("图片链接：\n")
                    imageUris.forEach { uri ->
                        append("- ").append(uri).append('\n')
                    }
                    append('\n')
                }
            }
        }
    }.trim()
}

fun buildConversationPlainText(
    title: String,
    messages: List<ChatMessage>,
    options: ConversationExportOptions,
): String {
    return buildString {
        append(title.ifBlank { "未命名会话" })
        append("\n\n")
        messages.forEach { message ->
            append(buildMessageHeading(message, options.includeModelName))
            append('\n')
            val bodyText = normalizeChatMessageParts(message.parts).toPlainText()
                .ifBlank { message.content.trim() }
            append(bodyText.ifBlank { "无文本内容" })
            append("\n\n")
            if (options.includeReasoning && message.reasoningContent.isNotBlank()) {
                append("思考内容：\n")
                append(message.reasoningContent.trim())
                append("\n\n")
            }
            if (options.includeImageLinks) {
                val imageUris = resolveImageUris(message)
                if (imageUris.isNotEmpty()) {
                    append("图片链接：\n")
                    imageUris.forEach { uri ->
                        append("- ").append(uri).append('\n')
                    }
                    append('\n')
                }
            }
        }
    }.trim()
}

fun buildShareIntent(sharedText: String): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sharedText)
    }
}

private fun buildMessageHeading(
    message: ChatMessage,
    includeModelName: Boolean,
): String {
    val roleLabel = if (message.role == MessageRole.USER) {
        "用户"
    } else {
        "助手"
    }
    return if (includeModelName && message.modelName.isNotBlank()) {
        "$roleLabel · ${message.modelName}"
    } else {
        roleLabel
    }
}

private fun resolveImageUris(message: ChatMessage): List<String> {
    val partUris = normalizeChatMessageParts(message.parts)
        .filter { it.type == ChatMessagePartType.IMAGE && it.uri.isNotBlank() }
        .map { it.uri }
    if (partUris.isNotEmpty()) {
        return partUris
    }
    return message.attachments
        .filter { it.type == AttachmentType.IMAGE && it.uri.isNotBlank() }
        .map { it.uri }
}
