package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ImageUrlContentPartDto
import com.example.myapplication.model.ImageUrlDto
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.TextContentPartDto
import com.example.myapplication.model.decodeOnlineThoughtText
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.model.toMessageAttachmentOrNull
import com.example.myapplication.model.toPlainText

internal object GatewayRequestMessageBuilder {
    private const val defaultChatFormattingPrompt = """你是一个注重可读性的中文助手。
默认使用清晰、克制的 Markdown 排版回答：
- 根据内容自然分段，段落之间留空行，避免大段文字堆叠。
- 有并列信息时优先使用短列表或编号列表。
- 有明确主题时使用简短小标题；内容很短时不要强行加标题。
- 代码、命令、JSON、SQL 等结构化内容使用 Markdown 代码块。
- 只要给出可复制执行的命令、终端输入、配置片段或脚本，必须用 fenced code block 包裹，并尽量标注语言，如 ```powershell```、```bash```、```json```。
- 用户明确要求纯文本、原样输出或指定格式时，优先严格遵循用户要求。
- 保持表达简洁准确，不为了排版额外添加空话。
"""

    suspend fun build(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptMode: PromptMode,
        imagePayloadResolver: suspend (MessageAttachment) -> String,
        filePromptResolver: suspend (MessageAttachment) -> String,
    ): List<ChatMessageDto> {
        val completedMessages = messages
            .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
        val effectiveSystemPrompt = buildSystemPrompt(
            systemPrompt = systemPrompt,
            promptMode = promptMode,
            messages = completedMessages,
        )
        return buildList {
            if (effectiveSystemPrompt.isNotBlank()) {
                add(ChatMessageDto(role = "system", content = effectiveSystemPrompt))
            }
            completedMessages.forEach { message ->
                val requestContent = buildRequestContent(
                    message = message,
                    imagePayloadResolver = imagePayloadResolver,
                    filePromptResolver = filePromptResolver,
                ) ?: return@forEach
                add(
                    ChatMessageDto(
                        role = if (message.role == MessageRole.USER) "user" else "assistant",
                        content = requestContent,
                    ),
                )
            }
        }
    }

    private fun buildSystemPrompt(
        systemPrompt: String,
        promptMode: PromptMode,
        messages: List<ChatMessage>,
    ): String {
        val customPrompt = systemPrompt.trim()
        val specialPlayPrompt = "\n\n${GatewaySpecialPlaySupport.defaultSpecialPlayPrompt}"
        return when (promptMode) {
            PromptMode.ROLEPLAY -> {
                if (customPrompt.isBlank()) {
                    GatewaySpecialPlaySupport.defaultSpecialPlayPrompt
                } else {
                    "$customPrompt$specialPlayPrompt"
                }
            }
            PromptMode.CHAT -> {
                if (customPrompt.isBlank()) {
                    "$defaultChatFormattingPrompt$specialPlayPrompt"
                } else {
                    "$customPrompt\n\n$defaultChatFormattingPrompt$specialPlayPrompt"
                }
            }
        }
    }

    private suspend fun buildRequestContent(
        message: ChatMessage,
        imagePayloadResolver: suspend (MessageAttachment) -> String,
        filePromptResolver: suspend (MessageAttachment) -> String,
    ): Any? {
        val replyPrefix = buildReplyPrefix(message)
        if (message.role != MessageRole.USER) {
            val assistantParts = normalizeChatMessageParts(message.parts)
            if (assistantParts.isNotEmpty()) {
                val assistantContext = assistantParts.mapNotNull { part ->
                    when (part.type) {
                        ChatMessagePartType.TEXT -> when {
                            part.isOnlineThoughtPart() -> {
                                decodeOnlineThoughtText(part.text)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { thought -> "【心声】$thought" }
                            }
                            else -> part.text.takeIf { it.isNotBlank() }
                        }
                        ChatMessagePartType.ACTION -> null
                        ChatMessagePartType.SPECIAL -> GatewaySpecialPlaySupport.buildSpecialPlayPrompt(part)
                        ChatMessagePartType.IMAGE,
                        ChatMessagePartType.FILE,
                        -> null
                    }
                }.joinToString(separator = "\n\n").trim()
                if (assistantContext.isNotBlank()) {
                    return listOf(replyPrefix, assistantContext)
                        .filter { it.isNotBlank() }
                        .joinToString(separator = "\n")
                        .trim()
                }
            }
            return listOf(
                replyPrefix,
                message.content
                .ifBlank { message.parts.toPlainText() }
                .takeIf { it.isNotBlank() }.orEmpty(),
            ).filter { it.isNotBlank() }
                .joinToString(separator = "\n")
                .takeIf { it.isNotBlank() }
        }

        val userParts = buildRequestMessageParts(message)
        if (userParts.isEmpty()) {
            return listOf(replyPrefix, message.content)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
                .takeIf { it.isNotBlank() }
        }

        val hasNonTextPart = userParts.any { it.type != ChatMessagePartType.TEXT }
        if (!hasNonTextPart) {
            return listOf(replyPrefix, userParts.toPlainText())
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
                .takeIf { it.isNotBlank() }
        }

        return buildList {
            if (replyPrefix.isNotBlank()) {
                add(TextContentPartDto(text = replyPrefix))
            }
            userParts.forEach { part ->
                when (part.type) {
                    ChatMessagePartType.TEXT -> {
                        if (part.text.isNotBlank()) {
                            add(TextContentPartDto(text = part.text))
                        }
                    }

                    ChatMessagePartType.IMAGE -> {
                        val attachment = part.toMessageAttachmentOrNull()
                            ?: return@forEach
                        val url = imagePayloadResolver(attachment)
                        add(
                            ImageUrlContentPartDto(
                                imageUrl = ImageUrlDto(url = url),
                            ),
                        )
                    }

                    ChatMessagePartType.FILE -> {
                        val attachment = part.toMessageAttachmentOrNull()
                            ?: return@forEach
                        add(TextContentPartDto(text = filePromptResolver(attachment)))
                    }

                    ChatMessagePartType.ACTION -> Unit

                    ChatMessagePartType.SPECIAL -> {
                        GatewaySpecialPlaySupport.buildSpecialPlayPrompt(part)?.let { prompt ->
                            add(TextContentPartDto(text = prompt))
                        }
                    }
                }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun buildReplyPrefix(message: ChatMessage): String {
        if (message.replyToPreview.isBlank()) {
            return ""
        }
        return buildString {
            append("【引用上一条消息】\n")
            append("来自：")
            append(message.replyToSpeakerName.ifBlank { "对方" })
            append('\n')
            append("内容：")
            append(message.replyToPreview.trim())
        }
    }

    private fun buildRequestMessageParts(message: ChatMessage): List<ChatMessagePart> {
        if (message.parts.isNotEmpty()) {
            return normalizeChatMessageParts(message.parts)
        }

        if (message.attachments.isEmpty()) {
            return if (message.content.isBlank()) {
                emptyList()
            } else {
                listOf(textMessagePart(message.content))
            }
        }

        return normalizeChatMessageParts(
            buildList {
                if (message.content.isNotBlank()) {
                    add(textMessagePart(message.content))
                }
                addAll(message.attachments.map { attachment -> attachment.toChatMessagePart() })
            },
        )
    }
}
