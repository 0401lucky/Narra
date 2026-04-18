package com.example.myapplication.ui.screen.chat

import com.example.myapplication.ui.component.*

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.toSpecialPlayCopyText
import com.example.myapplication.ui.component.extractReasoningStepTitle
import com.example.myapplication.viewmodel.TranslationUiState
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConversationExportOptions(
    val includeReasoning: Boolean = true,
    val includeModelName: Boolean = true,
    val includeImageLinks: Boolean = true,
)

data class MessageExportOptions(
    val includeReasoning: Boolean = true,
    val includeModelName: Boolean = true,
    val includeCreatedAt: Boolean = true,
    val includeImageLinks: Boolean = true,
    val includeCitations: Boolean = true,
)

internal data class ChatMessageActionAvailability(
    val canEditUserMessage: Boolean,
    val canPreview: Boolean,
    val canRegenerate: Boolean,
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
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
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
                    text = stringResource(R.string.translation_result),
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
                title = stringResource(R.string.translation_original),
                body = translation.sourceText,
            )

            SheetPreviewCard(
                title = if (translation.isLoading) stringResource(R.string.translation_translating) else stringResource(R.string.translation_translated),
                body = if (translation.isLoading) stringResource(R.string.translation_loading_hint) else translation.translatedText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NarraOutlinedButton(
                    onClick = {
                        clipboardScope.copyPlainTextToClipboard(clipboard, "translation-result", translation.translatedText)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.translation_copy))
                }
                NarraOutlinedButton(
                    onClick = onReplaceInput,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.translation_replace_input))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NarraFilledTonalButton(
                    onClick = onAppendToInput,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.translation_insert_input))
                }
                NarraButton(
                    onClick = onSendAsMessage,
                    modifier = Modifier.weight(1f),
                    enabled = !translation.isLoading && translation.translatedText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.translation_send))
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
                    text = stringResource(R.string.export_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = title.ifBlank { stringResource(R.string.export_unnamed_conversation) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ExportOptionRow(
                title = stringResource(R.string.export_include_thinking),
                checked = options.includeReasoning,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeReasoning = it))
                },
            )
            ExportOptionRow(
                title = stringResource(R.string.export_include_model_name),
                checked = options.includeModelName,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeModelName = it))
                },
            )
            ExportOptionRow(
                title = stringResource(R.string.export_include_image_links),
                checked = options.includeImageLinks,
                onCheckedChange = {
                    onUpdateOptions(options.copy(includeImageLinks = it))
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NarraOutlinedButton(
                    onClick = onCopyPlainText,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.export_copy_plain_text))
                }
                NarraOutlinedButton(
                    onClick = onShareConversation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.export_system_share))
                }
            }

            NarraButton(
                onClick = onExportMarkdown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_markdown))
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
                text = body.ifBlank { stringResource(R.string.export_no_content) },
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

fun buildMessageMarkdown(
    message: ChatMessage,
    options: MessageExportOptions = MessageExportOptions(),
): String {
    return buildString {
        append("# ")
        append(buildMessageHeading(message, options.includeModelName))
        append("\n\n")
        if (options.includeCreatedAt) {
            append("_时间：")
            append(formatMessageCreatedAt(message.createdAt))
            append("_\n\n")
        }

        val bodyText = resolveMessageBodyText(message)
        if (bodyText.isNotBlank()) {
            append(bodyText)
            append("\n\n")
        }

        appendMessageReasoningMarkdown(
            message = message,
            includeReasoning = options.includeReasoning,
        )
        appendAttachmentMarkdown(
            message = message,
            includeLinks = options.includeImageLinks,
        )
        appendCitationMarkdown(
            message = message,
            includeCitations = options.includeCitations,
        )
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

fun buildMessagePlainText(
    message: ChatMessage,
    options: MessageExportOptions = MessageExportOptions(),
): String {
    return buildString {
        append(buildMessageHeading(message, options.includeModelName))
        append('\n')
        if (options.includeCreatedAt) {
            append("时间：")
            append(formatMessageCreatedAt(message.createdAt))
            append("\n\n")
        } else {
            append('\n')
        }

        append(resolveMessageBodyText(message).ifBlank { "无文本内容" })
        append("\n\n")
        appendMessageReasoningPlainText(
            message = message,
            includeReasoning = options.includeReasoning,
        )
        appendAttachmentPlainText(
            message = message,
            includeLinks = options.includeImageLinks,
        )
        appendCitationPlainText(
            message = message,
            includeCitations = options.includeCitations,
        )
    }.trim()
}

fun messageHasPreviewableText(
    message: ChatMessage,
): Boolean {
    val normalizedParts = normalizeChatMessageParts(message.parts)
    val hasTextualParts = normalizedParts.any { part ->
        (part.type == ChatMessagePartType.TEXT && part.text.isNotBlank()) ||
            part.type == ChatMessagePartType.SPECIAL
    }
    return hasTextualParts ||
        (normalizedParts.isEmpty() && message.content.isNotBlank()) ||
        message.reasoningSteps.isNotEmpty() ||
        message.reasoningContent.isNotBlank()
}

internal fun resolveMessageActionAvailability(
    message: ChatMessage,
): ChatMessageActionAvailability {
    return ChatMessageActionAvailability(
        canEditUserMessage = message.role == MessageRole.USER,
        canPreview = messageHasPreviewableText(message),
        canRegenerate = message.role == MessageRole.ASSISTANT,
    )
}

fun buildMessageSelectionPayload(
    message: ChatMessage,
): ChatMessageSelectionPayload {
    return ChatMessageSelectionPayload(
        title = buildMessageHeading(message, includeModelName = true),
        content = buildMessageMarkdown(message),
    )
}

fun buildMessagePreviewPayload(
    message: ChatMessage,
    colorScheme: androidx.compose.material3.ColorScheme,
): ChatMessagePreviewPayload.MessageHtmlPreview {
    val title = buildMessageHeading(message, includeModelName = true)
    val markdown = buildMessageMarkdown(message)
    return ChatMessagePreviewPayload.MessageHtmlPreview(
        title = title,
        html = buildMessagePreviewHtml(
            title = title,
            markdown = markdown,
            colorScheme = colorScheme,
        ),
    )
}

fun buildSearchResultPreviewPayload(
    message: ChatMessage,
): ChatSearchResultPreviewPayload? {
    val preview = parseSearchResultPreview(message) ?: return null
    return ChatSearchResultPreviewPayload(
        title = buildMessageHeading(message, includeModelName = true),
        query = preview.query,
        answer = preview.answer,
        items = preview.items,
    )
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

fun buildMessageExportFileName(message: ChatMessage): String {
    val roleLabel = if (message.role == MessageRole.USER) "user" else "assistant"
    return "message-$roleLabel-${message.createdAt}.md"
}

fun buildMessagePreviewTitle(
    message: ChatMessage,
): String {
    return buildMessageHeading(message, includeModelName = true)
}

private fun resolveMessageBodyText(message: ChatMessage): String {
    val normalizedParts = normalizeChatMessageParts(message.parts)
    val textBody = normalizedParts.toPlainText()
    if (textBody.isNotBlank()) {
        return textBody
    }
    if (message.content.isNotBlank()) {
        return message.content.trim()
    }
    return normalizedParts.firstOrNull { it.type == ChatMessagePartType.SPECIAL }
        ?.toSpecialPlayCopyText()
        .orEmpty()
        .trim()
}

private fun resolveMessageReasoningText(message: ChatMessage): String {
    return reasoningStepsToContent(message.reasoningSteps)
        .ifBlank { message.reasoningContent.trim() }
}

private fun resolveAttachmentSummaries(message: ChatMessage): List<String> {
    val normalizedParts = normalizeChatMessageParts(message.parts)
    val partSummaries = normalizedParts.mapNotNull { part ->
        when (part.type) {
            ChatMessagePartType.IMAGE -> {
                if (part.uri.isBlank()) null else "图片：${part.fileName.ifBlank { part.uri }}"
            }

            ChatMessagePartType.FILE -> {
                if (part.uri.isBlank()) null else "文件：${part.fileName.ifBlank { part.uri }}"
            }

            ChatMessagePartType.ACTION -> part.toActionCopyText().trim().takeIf { it.isNotBlank() }
            ChatMessagePartType.SPECIAL -> part.toSpecialPlayCopyText().trim().takeIf { it.isNotBlank() }
            ChatMessagePartType.TEXT -> null
        }
    }
    if (partSummaries.isNotEmpty()) {
        return partSummaries
    }
    return message.attachments.map { attachment ->
        when (attachment.type) {
            AttachmentType.IMAGE -> "图片：${attachment.fileName.ifBlank { attachment.uri }}"
            AttachmentType.FILE -> "文件：${attachment.fileName.ifBlank { attachment.uri }}"
        }
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

private fun StringBuilder.appendMessageReasoningMarkdown(
    message: ChatMessage,
    includeReasoning: Boolean,
) {
    if (!includeReasoning) {
        return
    }
    val reasoningSteps = message.reasoningSteps
    val reasoningText = resolveMessageReasoningText(message)
    if (reasoningSteps.isEmpty() && reasoningText.isBlank()) {
        return
    }

    append("### 思考过程\n\n")
    if (reasoningSteps.isNotEmpty()) {
        reasoningSteps.forEachIndexed { index, step ->
            val stepTitle = extractReasoningStepTitle(step.text)
                ?: "步骤 ${index + 1}"
            append("${index + 1}. **$stepTitle**\n\n")
            step.text.lines().forEach { line ->
                append("   ").append(line).append('\n')
            }
            append('\n')
        }
    } else {
        reasoningText.lines().forEach { line ->
            append("> ").append(line).append('\n')
        }
        append('\n')
    }
}

private fun StringBuilder.appendMessageReasoningPlainText(
    message: ChatMessage,
    includeReasoning: Boolean,
) {
    if (!includeReasoning) {
        return
    }
    val reasoningSteps = message.reasoningSteps
    val reasoningText = resolveMessageReasoningText(message)
    if (reasoningSteps.isEmpty() && reasoningText.isBlank()) {
        return
    }
    append("思考过程：\n")
    if (reasoningSteps.isNotEmpty()) {
        reasoningSteps.forEachIndexed { index, step ->
            val stepTitle = extractReasoningStepTitle(step.text) ?: "步骤 ${index + 1}"
            append("${index + 1}. $stepTitle\n")
            append(step.text.trim()).append("\n\n")
        }
    } else {
        append(reasoningText).append("\n\n")
    }
}

private fun StringBuilder.appendAttachmentMarkdown(
    message: ChatMessage,
    includeLinks: Boolean,
) {
    val attachmentSummaries = resolveAttachmentSummaries(message)
    if (attachmentSummaries.isNotEmpty()) {
        append("### 附件与结构化内容\n\n")
        attachmentSummaries.forEach { summary ->
            append("- ").append(summary).append('\n')
        }
        append('\n')
    }
    if (includeLinks) {
        val imageUris = resolveImageUris(message)
        if (imageUris.isNotEmpty()) {
            append("### 图片链接\n\n")
            imageUris.forEach { uri ->
                append("- ").append(uri).append('\n')
            }
            append('\n')
        }
    }
}

private fun StringBuilder.appendAttachmentPlainText(
    message: ChatMessage,
    includeLinks: Boolean,
) {
    val attachmentSummaries = resolveAttachmentSummaries(message)
    if (attachmentSummaries.isNotEmpty()) {
        append("附件与结构化内容：\n")
        attachmentSummaries.forEach { summary ->
            append("- ").append(summary).append('\n')
        }
        append('\n')
    }
    if (includeLinks) {
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

private fun StringBuilder.appendCitationMarkdown(
    message: ChatMessage,
    includeCitations: Boolean,
) {
    if (!includeCitations || message.citations.isEmpty()) {
        return
    }
    append("### 引用来源\n\n")
    message.citations.forEachIndexed { index, citation ->
        append("${index + 1}. [")
            .append(citation.title.ifBlank { citation.url })
            .append("](")
            .append(citation.url)
            .append(')')
        if (citation.sourceLabel.isNotBlank()) {
            append(" · ").append(citation.sourceLabel)
        }
        append('\n')
    }
    append('\n')
}

private fun StringBuilder.appendCitationPlainText(
    message: ChatMessage,
    includeCitations: Boolean,
) {
    if (!includeCitations || message.citations.isEmpty()) {
        return
    }
    append("引用来源：\n")
    message.citations.forEachIndexed { index, citation ->
        append("${index + 1}. ")
            .append(citation.title.ifBlank { citation.url })
            .append('\n')
            .append("   ")
            .append(citation.url)
        if (citation.sourceLabel.isNotBlank()) {
            append(" · ").append(citation.sourceLabel)
        }
        append("\n")
    }
    append('\n')
}

internal fun formatMessageCreatedAt(createdAt: Long): String {
    if (createdAt <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
}

private data class ParsedSearchResultPreview(
    val query: String,
    val answer: String,
    val items: List<com.example.myapplication.data.repository.search.SearchResultItem>,
)

private fun parseSearchResultPreview(
    message: ChatMessage,
): ParsedSearchResultPreview? {
    val jsonText = normalizeChatMessageParts(message.parts)
        .firstOrNull { part ->
            part.type == ChatMessagePartType.TEXT &&
                part.text.contains("\"query\"") &&
                (part.text.contains("\"items\"") || part.text.contains("\"results\""))
        }
        ?.text
        .orEmpty()
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null
    val root = runCatching {
        JsonParser.parseString(jsonText).asJsonObject
    }.getOrNull() ?: return null
    val rawItems = when {
        root.has("items") -> root.getAsJsonArray("items")
        root.has("results") -> root.getAsJsonArray("results")
        else -> null
    } ?: return null
    val items = rawItems.mapNotNull { element ->
        val item = element.asJsonObject
        val url = item.get("url")?.asString.orEmpty().trim()
        if (url.isBlank()) {
            return@mapNotNull null
        }
        com.example.myapplication.data.repository.search.SearchResultItem(
            id = item.get("id")?.asString.orEmpty(),
            title = item.get("title")?.asString.orEmpty().ifBlank { url },
            url = url,
            snippet = item.get("text")?.asString.orEmpty().ifBlank {
                item.get("snippet")?.asString.orEmpty()
            },
            sourceLabel = item.get("sourceLabel")?.asString.orEmpty().ifBlank {
                item.get("source")?.asString.orEmpty()
            },
        )
    }
    if (items.isEmpty()) {
        return null
    }
    return ParsedSearchResultPreview(
        query = root.get("query")?.asString.orEmpty(),
        answer = root.get("answer")?.asString.orEmpty(),
        items = items,
    )
}
