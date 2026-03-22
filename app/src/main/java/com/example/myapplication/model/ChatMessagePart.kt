package com.example.myapplication.model

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class ChatMessagePartType {
    TEXT,
    IMAGE,
    FILE,
    SPECIAL,
}

enum class ChatSpecialType {
    TRANSFER,
}

enum class TransferDirection {
    USER_TO_ASSISTANT,
    ASSISTANT_TO_USER,
}

enum class TransferStatus {
    PENDING,
    RECEIVED,
}

@Immutable
data class ChatMessagePart(
    val type: ChatMessagePartType = ChatMessagePartType.TEXT,
    val text: String = "",
    val uri: String = "",
    val mimeType: String = "",
    val fileName: String = "",
    val specialType: ChatSpecialType? = null,
    val specialId: String = "",
    val specialDirection: TransferDirection? = null,
    val specialStatus: TransferStatus? = null,
    val specialCounterparty: String = "",
    val specialAmount: String = "",
    val specialNote: String = "",
)

fun textMessagePart(text: String): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.TEXT,
        text = text,
    )
}

fun imageMessagePart(
    uri: String,
    mimeType: String = "",
    fileName: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.IMAGE,
        uri = uri,
        mimeType = mimeType,
        fileName = fileName,
    )
}

fun fileMessagePart(
    uri: String,
    mimeType: String = "",
    fileName: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.FILE,
        uri = uri,
        mimeType = mimeType,
        fileName = fileName,
    )
}

fun transferMessagePart(
    id: String = UUID.randomUUID().toString(),
    direction: TransferDirection,
    status: TransferStatus = TransferStatus.PENDING,
    counterparty: String,
    amount: String,
    note: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.SPECIAL,
        specialType = ChatSpecialType.TRANSFER,
        specialId = id,
        specialDirection = direction,
        specialStatus = status,
        specialCounterparty = counterparty.trim(),
        specialAmount = amount.trim(),
        specialNote = note.trim(),
    )
}

fun MessageAttachment.toChatMessagePart(): ChatMessagePart {
    return when (type) {
        AttachmentType.IMAGE -> imageMessagePart(
            uri = uri,
            mimeType = mimeType,
            fileName = fileName,
        )

        AttachmentType.FILE -> fileMessagePart(
            uri = uri,
            mimeType = mimeType,
            fileName = fileName,
        )
    }
}

fun ChatMessagePart.toMessageAttachmentOrNull(): MessageAttachment? {
    if (uri.isBlank()) {
        return null
    }

    return when (type) {
        ChatMessagePartType.TEXT -> null
        ChatMessagePartType.IMAGE -> MessageAttachment(
            type = AttachmentType.IMAGE,
            uri = uri,
            mimeType = mimeType.ifBlank { "image/*" },
            fileName = fileName,
        )

        ChatMessagePartType.FILE -> MessageAttachment(
            type = AttachmentType.FILE,
            uri = uri,
            mimeType = mimeType.ifBlank { "text/plain" },
            fileName = fileName,
        )

        ChatMessagePartType.SPECIAL -> null
    }
}

fun List<ChatMessagePart>.toMessageAttachments(): List<MessageAttachment> {
    return normalizeChatMessageParts(this)
        .mapNotNull(ChatMessagePart::toMessageAttachmentOrNull)
}

fun normalizeChatMessageParts(parts: List<ChatMessagePart>): List<ChatMessagePart> {
    if (parts.isEmpty()) {
        return emptyList()
    }

    val normalized = mutableListOf<ChatMessagePart>()
    parts.forEach { part ->
        when (part.type) {
            ChatMessagePartType.TEXT -> {
                if (part.text.isBlank()) {
                    return@forEach
                }
                val previous = normalized.lastOrNull()
                if (previous?.type == ChatMessagePartType.TEXT) {
                    normalized[normalized.lastIndex] = previous.copy(
                        text = previous.text + part.text,
                    )
                } else {
                    normalized += part.copy(
                        text = part.text,
                        uri = "",
                        mimeType = "",
                    )
                }
            }

            ChatMessagePartType.IMAGE,
            ChatMessagePartType.FILE,
            -> {
                if (part.uri.isBlank()) {
                    return@forEach
                }
                normalized += part.copy(
                    text = "",
                )
            }

            ChatMessagePartType.SPECIAL -> {
                if (!part.isValidTransferPart()) {
                    return@forEach
                }
                normalized += part.copy(
                    text = "",
                    uri = "",
                    mimeType = "",
                    fileName = "",
                    specialCounterparty = part.specialCounterparty.trim(),
                    specialAmount = part.specialAmount.trim(),
                    specialNote = part.specialNote.trim(),
                )
            }
        }
    }

    return normalized
}

fun List<ChatMessagePart>.toPlainText(): String {
    return normalizeChatMessageParts(this)
        .filter { it.type == ChatMessagePartType.TEXT }
        .joinToString(separator = "\n\n") { it.text.trim() }
        .trim()
}

fun List<ChatMessagePart>.toContentMirror(
    imageFallback: String = "图片已生成",
    fileFallback: String = "文件已附加",
    specialFallback: String = "特殊玩法",
): String {
    val plainText = toPlainText()
    if (plainText.isNotBlank()) {
        return plainText
    }

    val normalized = normalizeChatMessageParts(this)
    return when {
        normalized.any { it.type == ChatMessagePartType.IMAGE && it.uri.isNotBlank() } -> imageFallback
        normalized.any { it.type == ChatMessagePartType.FILE && it.uri.isNotBlank() } -> fileFallback
        normalized.any { it.type == ChatMessagePartType.SPECIAL && it.specialType == ChatSpecialType.TRANSFER } -> {
            val amount = normalized.firstOrNull {
                it.type == ChatMessagePartType.SPECIAL && it.specialType == ChatSpecialType.TRANSFER
            }?.specialAmount.orEmpty()
            if (amount.isNotBlank()) {
                "转账 $amount"
            } else {
                specialFallback
            }
        }
        else -> ""
    }
}

fun ChatMessagePart.isTransferPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.TRANSFER
}

fun ChatMessagePart.isValidTransferPart(): Boolean {
    return isTransferPart() &&
        specialId.isNotBlank() &&
        specialDirection != null &&
        specialStatus != null &&
        specialCounterparty.isNotBlank() &&
        specialAmount.isNotBlank()
}
