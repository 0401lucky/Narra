package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart

class ChatImageGenerationSupport(
    private val imageSaver: suspend (String) -> SavedImageFile,
) {
    suspend fun buildCompletedAssistant(
        loadingMessage: ChatMessage,
        results: List<ImageGenerationResult>,
    ): ChatMessage {
        val firstResult = results.firstOrNull()
            ?: throw IllegalStateException("图片生成接口未返回数据")
        val imageAttachments = results.mapNotNull { result ->
            if (result.b64Data.isNotBlank()) {
                val savedImage = imageSaver(result.b64Data)
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = savedImage.path,
                    mimeType = savedImage.mimeType,
                    fileName = savedImage.fileName,
                )
            } else if (result.url.isNotBlank()) {
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = result.url,
                    mimeType = "",
                    fileName = "generated-remote",
                )
            } else {
                null
            }
        }

        val contentText = firstResult.revisedPrompt.takeIf { it.isNotBlank() } ?: "图片已生成"
        val assistantParts = normalizeChatMessageParts(
            buildList {
                if (contentText.isNotBlank()) {
                    add(textMessagePart(contentText))
                }
                imageAttachments.forEach { attachment ->
                    add(
                        imageMessagePart(
                            uri = attachment.uri,
                            mimeType = attachment.mimeType,
                            fileName = attachment.fileName,
                        ),
                    )
                }
            },
        )

        return loadingMessage.copy(
            content = contentText,
            status = MessageStatus.COMPLETED,
            attachments = imageAttachments,
            parts = assistantParts,
        )
    }

    fun buildCancelledAssistant(loadingMessage: ChatMessage): ChatMessage {
        return loadingMessage.copy(
            content = "已取消",
            status = MessageStatus.ERROR,
        )
    }

    fun buildFailedAssistant(
        loadingMessage: ChatMessage,
        errorText: String,
    ): ChatMessage {
        return loadingMessage.copy(
            content = errorText,
            status = MessageStatus.ERROR,
        )
    }
}
