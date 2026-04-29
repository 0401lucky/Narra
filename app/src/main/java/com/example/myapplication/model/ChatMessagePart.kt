package com.example.myapplication.model

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class ChatMessagePartType {
    TEXT,
    IMAGE,
    FILE,
    ACTION,
    SPECIAL,
    STATUS,
}

enum class ChatActionType {
    EMOJI,
    VOICE_MESSAGE,
    AI_PHOTO,
    LOCATION,
    POKE,
    VIDEO_CALL;

    val protocolValue: String
        get() = when (this) {
            EMOJI -> "emoji"
            VOICE_MESSAGE -> "voice_message"
            AI_PHOTO -> "ai_photo"
            LOCATION -> "location"
            POKE -> "poke"
            VIDEO_CALL -> "video_call"
        }

    companion object {
        fun fromProtocolValue(value: String): ChatActionType? {
            return entries.firstOrNull { it.protocolValue == value.trim().lowercase() }
        }
    }
}

enum class ChatSpecialType {
    TRANSFER,
    INVITE,
    GIFT,
    TASK,
    PUNISH,
    ;

    val displayName: String
        get() = when (this) {
            TRANSFER -> "转账"
            INVITE -> "邀约"
            GIFT -> "礼物"
            TASK -> "委托"
            PUNISH -> "惩罚"
        }

    val protocolValue: String
        get() = name.lowercase()

    companion object {
        fun fromProtocolValue(value: String): ChatSpecialType? {
            return entries.firstOrNull { it.protocolValue == value.trim().lowercase() }
        }
    }
}

enum class TransferDirection {
    USER_TO_ASSISTANT,
    ASSISTANT_TO_USER,
}

enum class TransferStatus {
    PENDING,
    RECEIVED,
    REJECTED,
}

enum class GiftImageStatus {
    GENERATING,
    SUCCEEDED,
    FAILED;

    val storageValue: String
        get() = name.lowercase()

    companion object {
        fun fromStorageValue(value: String): GiftImageStatus? {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() }
        }
    }
}

@Immutable
data class ChatMessagePart(
    val type: ChatMessagePartType = ChatMessagePartType.TEXT,
    val text: String = "",
    val uri: String = "",
    val mimeType: String = "",
    val fileName: String = "",
    val actionType: ChatActionType? = null,
    val actionId: String = "",
    val actionMetadata: Map<String, String> = emptyMap(),
    val specialType: ChatSpecialType? = null,
    val specialId: String = "",
    val specialDirection: TransferDirection? = null,
    val specialStatus: TransferStatus? = null,
    val specialCounterparty: String = "",
    val specialAmount: String = "",
    val specialNote: String = "",
    val specialMetadata: Map<String, String> = emptyMap(),
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val replyToSpeakerName: String = "",
)

fun textMessagePart(
    text: String,
    replyToMessageId: String = "",
    replyToPreview: String = "",
    replyToSpeakerName: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.TEXT,
        text = text,
        replyToMessageId = replyToMessageId.trim(),
        replyToPreview = replyToPreview.trim(),
        replyToSpeakerName = replyToSpeakerName.trim(),
    )
}

fun statusMessagePart(
    rawText: String,
    title: String = "状态",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.STATUS,
        text = rawText.trim(),
        specialMetadata = normalizeSpecialMetadata(
            mapOf(
                "title" to title.trim().ifBlank { "状态" },
                "raw" to rawText.trim(),
            ),
        ),
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

fun emojiMessagePart(
    description: String,
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.EMOJI,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            mapOf(ACTION_DESCRIPTION_KEY to description),
        ),
    )
}

fun voiceMessageActionPart(
    content: String,
    durationSeconds: Int? = null,
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    val normalizedContent = content.trim()
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.VOICE_MESSAGE,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            buildMap {
                put(ACTION_CONTENT_KEY, normalizedContent)
                durationSeconds
                    ?.coerceIn(1, 60)
                    ?.let { normalizedDuration ->
                        put(ACTION_DURATION_SECONDS_KEY, normalizedDuration.toString())
                    }
            },
        ),
    )
}

fun aiPhotoMessagePart(
    description: String,
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.AI_PHOTO,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            mapOf(ACTION_DESCRIPTION_KEY to description),
        ),
    )
}

fun locationMessagePart(
    locationName: String,
    coordinates: String = "",
    address: String = "",
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.LOCATION,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            mapOf(
                ACTION_LOCATION_NAME_KEY to locationName,
                ACTION_COORDINATES_KEY to coordinates,
                ACTION_ADDRESS_KEY to address,
            ),
        ),
    )
}

fun pokeMessagePart(
    target: String = "",
    suffix: String = "",
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.POKE,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            mapOf(
                ACTION_POKE_TARGET_KEY to target,
                ACTION_POKE_SUFFIX_KEY to suffix,
            ),
        ),
    )
}

fun ChatMessagePart.pokeTarget(): String {
    return actionMetadataValue(ACTION_POKE_TARGET_KEY)
}

fun ChatMessagePart.pokeSuffix(): String {
    return actionMetadataValue(ACTION_POKE_SUFFIX_KEY)
}

fun videoCallMessagePart(
    reason: String,
    id: String = UUID.randomUUID().toString(),
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.ACTION,
        actionType = ChatActionType.VIDEO_CALL,
        actionId = id,
        actionMetadata = normalizeActionMetadata(
            mapOf(ACTION_REASON_KEY to reason),
        ),
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

fun inviteMessagePart(
    id: String = UUID.randomUUID().toString(),
    target: String,
    place: String,
    time: String,
    note: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.SPECIAL,
        specialType = ChatSpecialType.INVITE,
        specialId = id,
        specialMetadata = normalizeSpecialMetadata(
            mapOf(
                "target" to target,
                "place" to place,
                "time" to time,
                "note" to note,
            ),
        ),
    )
}

fun giftMessagePart(
    id: String = UUID.randomUUID().toString(),
    target: String,
    item: String,
    note: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.SPECIAL,
        specialType = ChatSpecialType.GIFT,
        specialId = id,
        specialMetadata = normalizeSpecialMetadata(
            mapOf(
                "target" to target,
                "item" to item,
                "note" to note,
            ),
        ),
    )
}

fun taskMessagePart(
    id: String = UUID.randomUUID().toString(),
    title: String,
    objective: String,
    reward: String = "",
    deadline: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.SPECIAL,
        specialType = ChatSpecialType.TASK,
        specialId = id,
        specialMetadata = normalizeSpecialMetadata(
            mapOf(
                "title" to title,
                "objective" to objective,
                "reward" to reward,
                "deadline" to deadline,
            ),
        ),
    )
}

fun punishMessagePart(
    id: String = UUID.randomUUID().toString(),
    method: String,
    count: String,
    intensity: PunishIntensity = PunishIntensity.MEDIUM,
    reason: String = "",
    note: String = "",
): ChatMessagePart {
    return ChatMessagePart(
        type = ChatMessagePartType.SPECIAL,
        specialType = ChatSpecialType.PUNISH,
        specialId = id,
        specialMetadata = normalizeSpecialMetadata(
            mapOf(
                PUNISH_METHOD_KEY to method,
                PUNISH_COUNT_KEY to count,
                PUNISH_INTENSITY_KEY to intensity.storageValue,
                PUNISH_REASON_KEY to reason,
                PUNISH_NOTE_KEY to note,
            ),
        ),
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

        ChatMessagePartType.ACTION -> null
        ChatMessagePartType.SPECIAL -> null
        ChatMessagePartType.STATUS -> null
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
                normalized += part.copy(
                    text = part.text,
                    uri = "",
                    mimeType = "",
                    fileName = "",
                    actionType = null,
                    actionId = "",
                    actionMetadata = emptyMap(),
                    specialType = null,
                    specialId = "",
                    specialDirection = null,
                    specialStatus = null,
                    specialCounterparty = "",
                    specialAmount = "",
                    specialNote = "",
                    specialMetadata = emptyMap(),
                    replyToMessageId = part.replyToMessageId.trim(),
                    replyToPreview = part.replyToPreview.trim(),
                    replyToSpeakerName = part.replyToSpeakerName.trim(),
                )
            }

            ChatMessagePartType.IMAGE,
            ChatMessagePartType.FILE,
            -> {
                if (part.uri.isBlank()) {
                    return@forEach
                }
                normalized += part.copy(
                    text = "",
                    actionType = null,
                    actionId = "",
                    actionMetadata = emptyMap(),
                    specialType = null,
                    specialId = "",
                    specialDirection = null,
                    specialStatus = null,
                    specialCounterparty = "",
                    specialAmount = "",
                    specialNote = "",
                    specialMetadata = emptyMap(),
                    replyToMessageId = "",
                    replyToPreview = "",
                    replyToSpeakerName = "",
                )
            }

            ChatMessagePartType.ACTION -> {
                if (!part.isValidActionPart()) {
                    return@forEach
                }
                normalized += part.copy(
                    text = "",
                    uri = "",
                    mimeType = "",
                    fileName = "",
                    actionId = part.actionId.ifBlank { UUID.randomUUID().toString() },
                    actionMetadata = normalizeActionMetadata(part.actionMetadata),
                    specialType = null,
                    specialId = "",
                    specialDirection = null,
                    specialStatus = null,
                    specialCounterparty = "",
                    specialAmount = "",
                    specialNote = "",
                    specialMetadata = emptyMap(),
                    replyToMessageId = part.replyToMessageId.trim(),
                    replyToPreview = part.replyToPreview.trim(),
                    replyToSpeakerName = part.replyToSpeakerName.trim(),
                )
            }

            ChatMessagePartType.SPECIAL -> {
                if (!part.isValidSpecialPart()) {
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
                    specialMetadata = normalizeSpecialMetadata(part.specialMetadata),
                    actionType = null,
                    actionId = "",
                    actionMetadata = emptyMap(),
                    replyToMessageId = "",
                    replyToPreview = "",
                    replyToSpeakerName = "",
                )
            }

            ChatMessagePartType.STATUS -> {
                if (part.text.isBlank()) {
                    return@forEach
                }
                normalized += part.copy(
                    text = part.text.trim(),
                    uri = "",
                    mimeType = "",
                    fileName = "",
                    actionType = null,
                    actionId = "",
                    actionMetadata = emptyMap(),
                    specialType = null,
                    specialId = "",
                    specialDirection = null,
                    specialStatus = null,
                    specialCounterparty = "",
                    specialAmount = "",
                    specialNote = "",
                    specialMetadata = normalizeSpecialMetadata(part.specialMetadata),
                    replyToMessageId = "",
                    replyToPreview = "",
                    replyToSpeakerName = "",
                )
            }
        }
    }

    return normalized
}

fun List<ChatMessagePart>.toPlainText(): String {
    return normalizeChatMessageParts(this)
        .filter { it.type == ChatMessagePartType.TEXT }
        .joinToString(separator = "\n\n") { part ->
            if (part.isOnlineThoughtPart()) {
                part.onlineThoughtContent()
            } else {
                part.text.trim()
            }
        }
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
        normalized.any { it.type == ChatMessagePartType.ACTION } -> normalized.firstOrNull {
            it.type == ChatMessagePartType.ACTION
        }?.actionFallbackText().orEmpty().ifBlank { specialFallback }
        normalized.any { it.type == ChatMessagePartType.SPECIAL } -> normalized.firstOrNull {
            it.type == ChatMessagePartType.SPECIAL
        }?.specialPlayFallbackText().orEmpty().ifBlank { specialFallback }
        normalized.any { it.type == ChatMessagePartType.STATUS } -> "状态卡"
        else -> ""
    }
}

fun ChatMessagePart.isActionPart(): Boolean {
    return type == ChatMessagePartType.ACTION && actionType != null
}

fun ChatMessagePart.isSpecialPlayPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType != null
}

fun ChatMessagePart.isTransferPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.TRANSFER
}

fun ChatMessagePart.isInvitePart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.INVITE
}

fun ChatMessagePart.isGiftPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.GIFT
}

fun ChatMessagePart.isTaskPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.TASK
}

fun ChatMessagePart.isPunishPart(): Boolean {
    return type == ChatMessagePartType.SPECIAL && specialType == ChatSpecialType.PUNISH
}

fun ChatMessagePart.punishIntensity(): PunishIntensity? {
    if (!isPunishPart()) {
        return null
    }
    return PunishIntensity.fromStorageValue(specialMetadataValue(PUNISH_INTENSITY_KEY))
}

fun ChatMessagePart.punishIntensityLabel(): String {
    return punishIntensity()?.displayName ?: "中"
}

fun ChatMessagePart.giftImageStatus(): GiftImageStatus? {
    if (!isGiftPart()) {
        return null
    }
    return GiftImageStatus.fromStorageValue(specialMetadataValue(GIFT_IMAGE_STATUS_KEY))
}

fun ChatMessagePart.giftImageUri(): String {
    return specialMetadataValue(GIFT_IMAGE_URI_KEY)
}

fun ChatMessagePart.giftImageMimeType(): String {
    return specialMetadataValue(GIFT_IMAGE_MIME_TYPE_KEY)
}

fun ChatMessagePart.giftImageFileName(): String {
    return specialMetadataValue(GIFT_IMAGE_FILE_NAME_KEY)
}

fun ChatMessagePart.giftImageErrorMessage(): String {
    return specialMetadataValue(GIFT_IMAGE_ERROR_KEY)
}

fun ChatMessagePart.hasGiftGeneratedImage(): Boolean {
    return isGiftPart() &&
        giftImageStatus() == GiftImageStatus.SUCCEEDED &&
        giftImageUri().isNotBlank()
}

fun ChatMessagePart.withGiftImageGenerating(): ChatMessagePart {
    if (!isGiftPart()) {
        return this
    }
    return copy(
        specialMetadata = normalizeSpecialMetadata(
            specialMetadata +
                mapOf(
                    GIFT_IMAGE_STATUS_KEY to GiftImageStatus.GENERATING.storageValue,
                    GIFT_IMAGE_URI_KEY to "",
                    GIFT_IMAGE_MIME_TYPE_KEY to "",
                    GIFT_IMAGE_FILE_NAME_KEY to "",
                    GIFT_IMAGE_ERROR_KEY to "",
                ),
        ),
    )
}

fun ChatMessagePart.withGiftImageSuccess(
    imageUri: String,
    mimeType: String,
    fileName: String,
): ChatMessagePart {
    if (!isGiftPart()) {
        return this
    }
    return copy(
        specialMetadata = normalizeSpecialMetadata(
            specialMetadata +
                mapOf(
                    GIFT_IMAGE_STATUS_KEY to GiftImageStatus.SUCCEEDED.storageValue,
                    GIFT_IMAGE_URI_KEY to imageUri,
                    GIFT_IMAGE_MIME_TYPE_KEY to mimeType,
                    GIFT_IMAGE_FILE_NAME_KEY to fileName,
                    GIFT_IMAGE_ERROR_KEY to "",
                ),
        ),
    )
}

fun ChatMessagePart.withGiftImageFailure(
    errorMessage: String,
): ChatMessagePart {
    if (!isGiftPart()) {
        return this
    }
    return copy(
        specialMetadata = normalizeSpecialMetadata(
            specialMetadata +
                mapOf(
                    GIFT_IMAGE_STATUS_KEY to GiftImageStatus.FAILED.storageValue,
                    GIFT_IMAGE_URI_KEY to "",
                    GIFT_IMAGE_MIME_TYPE_KEY to "",
                    GIFT_IMAGE_FILE_NAME_KEY to "",
                    GIFT_IMAGE_ERROR_KEY to errorMessage,
                ),
        ),
    )
}

fun ChatMessagePart.isValidTransferPart(): Boolean {
    return isTransferPart() &&
        specialId.isNotBlank() &&
        specialDirection != null &&
        specialStatus != null &&
        specialCounterparty.isNotBlank() &&
        specialAmount.isNotBlank()
}

fun ChatMessagePart.isValidSpecialPart(): Boolean {
    return when (specialType) {
        ChatSpecialType.TRANSFER -> isValidTransferPart()
        ChatSpecialType.INVITE -> {
            isInvitePart() &&
                specialId.isNotBlank() &&
                specialMetadataValue("target").isNotBlank() &&
                specialMetadataValue("place").isNotBlank() &&
                specialMetadataValue("time").isNotBlank()
        }
        ChatSpecialType.GIFT -> {
            isGiftPart() &&
                specialId.isNotBlank() &&
                specialMetadataValue("target").isNotBlank() &&
                specialMetadataValue("item").isNotBlank()
        }
        ChatSpecialType.TASK -> {
            isTaskPart() &&
                specialId.isNotBlank() &&
                specialMetadataValue("title").isNotBlank() &&
                specialMetadataValue("objective").isNotBlank()
        }
        ChatSpecialType.PUNISH -> {
            isPunishPart() &&
                specialId.isNotBlank() &&
                specialMetadataValue(PUNISH_METHOD_KEY).isNotBlank() &&
                specialMetadataValue(PUNISH_COUNT_KEY).isNotBlank() &&
                punishIntensity() != null
        }
        null -> false
    }
}

fun ChatMessagePart.isValidActionPart(): Boolean {
    if (!isActionPart()) {
        return false
    }
    return when (actionType) {
        ChatActionType.EMOJI -> actionMetadataValue(ACTION_DESCRIPTION_KEY).isNotBlank()
        ChatActionType.VOICE_MESSAGE -> actionMetadataValue(ACTION_CONTENT_KEY).isNotBlank()
        ChatActionType.AI_PHOTO -> actionMetadataValue(ACTION_DESCRIPTION_KEY).isNotBlank()
        ChatActionType.LOCATION -> actionMetadataValue(ACTION_LOCATION_NAME_KEY).isNotBlank()
        ChatActionType.POKE -> true
        ChatActionType.VIDEO_CALL -> actionMetadataValue(ACTION_REASON_KEY).isNotBlank()
        null -> false
    }
}

fun ChatMessagePart.formatTransferAmount(): String {
    val normalizedAmount = specialAmount.trim()
    if (normalizedAmount.isBlank()) {
        return "¥0.00"
    }
    return if (normalizedAmount.startsWith("¥")) {
        normalizedAmount
    } else {
        "¥$normalizedAmount"
    }
}

fun ChatMessagePart.transferDirectionLabel(): String {
    return when (specialDirection) {
        TransferDirection.USER_TO_ASSISTANT -> "转账给 ${specialCounterparty.ifBlank { "对方" }}"
        TransferDirection.ASSISTANT_TO_USER -> "${specialCounterparty.ifBlank { "对方" }} 向你转账"
        null -> "转账"
    }
}

fun ChatMessagePart.transferStatusLabel(): String {
    return when (specialStatus) {
        TransferStatus.PENDING -> when (specialDirection) {
            TransferDirection.USER_TO_ASSISTANT -> "待对方收款"
            TransferDirection.ASSISTANT_TO_USER -> "请确认收款"
            null -> "待收款"
        }

        TransferStatus.RECEIVED -> "已收款"
        TransferStatus.REJECTED -> "已退回"
        null -> "处理中"
    }
}

fun TransferStatus.transferResultText(): String {
    return when (this) {
        TransferStatus.PENDING -> "待收款"
        TransferStatus.RECEIVED -> "已收款"
        TransferStatus.REJECTED -> "已退回"
    }
}

fun ChatMessagePart.toTransferCopyText(): String {
    if (!isTransferPart()) {
        return ""
    }
    return buildString {
        append(transferDirectionLabel())
        append('\n')
        append(formatTransferAmount())
        if (specialNote.isNotBlank()) {
            append("\n备注：")
            append(specialNote)
        }
        append('\n')
        append(transferStatusLabel())
    }
}

fun ChatMessagePart.specialMetadataValue(key: String): String {
    return specialMetadata[key].orEmpty().trim()
}

fun ChatMessagePart.actionMetadataValue(key: String): String {
    return actionMetadata[key].orEmpty().trim()
}

fun ChatMessagePart.voiceMessageContent(): String {
    return actionMetadataValue(ACTION_CONTENT_KEY)
}

fun ChatMessagePart.voiceMessageDurationSeconds(): Int {
    if (actionType != ChatActionType.VOICE_MESSAGE) {
        return 0
    }
    return resolveVoiceMessageDurationSeconds(
        content = voiceMessageContent(),
        preferredDurationSeconds = actionMetadataValue(ACTION_DURATION_SECONDS_KEY).toIntOrNull(),
    )
}

fun ChatMessagePart.voiceMessageDurationLabel(): String {
    if (actionType != ChatActionType.VOICE_MESSAGE) {
        return ""
    }
    return "${voiceMessageDurationSeconds()}″"
}

fun ChatMessagePart.specialPlayTitle(): String {
    return when (specialType) {
        ChatSpecialType.TRANSFER -> transferDirectionLabel()
        ChatSpecialType.INVITE -> "邀约 ${specialMetadataValue("target").ifBlank { "对方" }}"
        ChatSpecialType.GIFT -> "送给 ${specialMetadataValue("target").ifBlank { "对方" }} 的礼物"
        ChatSpecialType.TASK -> specialMetadataValue("title").ifBlank { "新的委托" }
        ChatSpecialType.PUNISH -> ChatSpecialType.PUNISH.displayName
        null -> "特殊玩法"
    }
}

fun ChatMessagePart.specialPlayFallbackText(): String {
    return when (specialType) {
        ChatSpecialType.TRANSFER -> {
            val amount = specialAmount.trim()
            if (amount.isNotBlank()) {
                "转账 $amount"
            } else {
                ChatSpecialType.TRANSFER.displayName
            }
        }
        ChatSpecialType.INVITE -> buildString {
            append("邀约")
            val place = specialMetadataValue("place")
            if (place.isNotBlank()) {
                append("：")
                append(place)
            }
        }
        ChatSpecialType.GIFT -> buildString {
            append("礼物")
            val item = specialMetadataValue("item")
            if (item.isNotBlank()) {
                append("：")
                append(item)
            }
        }
        ChatSpecialType.TASK -> buildString {
            append("委托")
            val title = specialMetadataValue("title")
            if (title.isNotBlank()) {
                append("：")
                append(title)
            }
        }
        ChatSpecialType.PUNISH -> buildString {
            append("惩罚")
            val method = specialMetadataValue(PUNISH_METHOD_KEY)
            val count = specialMetadataValue(PUNISH_COUNT_KEY)
            if (method.isNotBlank() || count.isNotBlank()) {
                append("：")
                append(method.ifBlank { "待定方式" })
                if (count.isNotBlank()) {
                    append(" · ")
                    append(count)
                }
            }
        }
        null -> "特殊玩法"
    }
}

fun ChatMessagePart.actionFallbackText(): String {
    return when (actionType) {
        ChatActionType.EMOJI -> "表情：${actionMetadataValue(ACTION_DESCRIPTION_KEY)}"
        ChatActionType.VOICE_MESSAGE -> "语音消息"
        ChatActionType.AI_PHOTO -> "照片"
        ChatActionType.LOCATION -> buildString {
            append("位置")
            actionMetadataValue(ACTION_LOCATION_NAME_KEY).takeIf { it.isNotBlank() }?.let { name ->
                append("：")
                append(name)
            }
        }
        ChatActionType.POKE -> buildPokeDisplayText(
            target = actionMetadataValue(ACTION_POKE_TARGET_KEY),
            suffix = actionMetadataValue(ACTION_POKE_SUFFIX_KEY),
            fallback = "戳一戳",
        )
        ChatActionType.VIDEO_CALL -> "视频通话"
        null -> ""
    }
}

fun ChatMessagePart.toSpecialPlayCopyText(): String {
    return when (specialType) {
        ChatSpecialType.TRANSFER -> toTransferCopyText()
        ChatSpecialType.INVITE -> buildString {
            append("邀约对象：")
            append(specialMetadataValue("target").ifBlank { "对方" })
            append('\n')
            append("地点：")
            append(specialMetadataValue("place"))
            append('\n')
            append("时间：")
            append(specialMetadataValue("time"))
            specialMetadataValue("note").takeIf { it.isNotBlank() }?.let { note ->
                append("\n备注：")
                append(note)
            }
        }
        ChatSpecialType.GIFT -> buildString {
            append("送礼对象：")
            append(specialMetadataValue("target").ifBlank { "对方" })
            append('\n')
            append("礼物：")
            append(specialMetadataValue("item"))
            specialMetadataValue("note").takeIf { it.isNotBlank() }?.let { note ->
                append("\n附言：")
                append(note)
            }
        }
        ChatSpecialType.TASK -> buildString {
            append("委托：")
            append(specialMetadataValue("title"))
            append('\n')
            append("目标：")
            append(specialMetadataValue("objective"))
            specialMetadataValue("reward").takeIf { it.isNotBlank() }?.let { reward ->
                append("\n奖励：")
                append(reward)
            }
            specialMetadataValue("deadline").takeIf { it.isNotBlank() }?.let { deadline ->
                append("\n期限：")
                append(deadline)
            }
        }
        ChatSpecialType.PUNISH -> buildString {
            append("方式：")
            append(specialMetadataValue(PUNISH_METHOD_KEY))
            append('\n')
            append("次数：")
            append(specialMetadataValue(PUNISH_COUNT_KEY))
            append('\n')
            append("强度：")
            append(punishIntensityLabel())
            specialMetadataValue(PUNISH_REASON_KEY).takeIf { it.isNotBlank() }?.let { reason ->
                append("\n原因：")
                append(reason)
            }
            specialMetadataValue(PUNISH_NOTE_KEY).takeIf { it.isNotBlank() }?.let { note ->
                append("\n附注：")
                append(note)
            }
        }
        null -> ""
    }
}

fun ChatMessagePart.toActionCopyText(): String {
    return when (actionType) {
        ChatActionType.EMOJI -> buildString {
            append("表情：")
            append(actionMetadataValue(ACTION_DESCRIPTION_KEY))
        }
        ChatActionType.VOICE_MESSAGE -> buildString {
            append("语音消息：")
            append(actionMetadataValue(ACTION_CONTENT_KEY))
        }
        ChatActionType.AI_PHOTO -> buildString {
            append("照片：")
            append(actionMetadataValue(ACTION_DESCRIPTION_KEY))
        }
        ChatActionType.LOCATION -> buildString {
            append("位置：")
            append(actionMetadataValue(ACTION_LOCATION_NAME_KEY))
            actionMetadataValue(ACTION_ADDRESS_KEY).takeIf { it.isNotBlank() }?.let { address ->
                append("\n地址：")
                append(address)
            }
            actionMetadataValue(ACTION_COORDINATES_KEY).takeIf { it.isNotBlank() }?.let { coordinates ->
                append("\n坐标：")
                append(coordinates)
            }
        }
        ChatActionType.POKE -> buildPokeDisplayText(
            target = actionMetadataValue(ACTION_POKE_TARGET_KEY),
            suffix = actionMetadataValue(ACTION_POKE_SUFFIX_KEY),
            fallback = "戳一戳",
        )
        ChatActionType.VIDEO_CALL -> buildString {
            append("视频通话")
            actionMetadataValue(ACTION_REASON_KEY).takeIf { it.isNotBlank() }?.let { reason ->
                append("\n理由：")
                append(reason)
            }
        }
        null -> ""
    }
}

private fun normalizeSpecialMetadata(source: Map<String, String>): Map<String, String> {
    return source.entries
        .mapNotNull { entry ->
            val key = entry.key.trim()
            val value = entry.value.trim()
            if (key.isBlank() || value.isBlank()) {
                null
            } else {
                key to value
            }
        }
        .toMap(linkedMapOf())
}

private fun normalizeActionMetadata(source: Map<String, String>): Map<String, String> {
    return source.entries
        .mapNotNull { entry ->
            val key = entry.key.trim()
            val value = entry.value.trim()
            if (key.isBlank()) {
                null
            } else if (key == ACTION_POKE_NOTE_KEY) {
                key to value
            } else if (value.isBlank()) {
                null
            } else {
                key to value
            }
        }
        .toMap(linkedMapOf())
}

private const val ACTION_DESCRIPTION_KEY = "description"
private const val ACTION_CONTENT_KEY = "content"
private const val ACTION_DURATION_SECONDS_KEY = "duration_seconds"
private const val ACTION_LOCATION_NAME_KEY = "location_name"
private const val ACTION_COORDINATES_KEY = "coordinates"
private const val ACTION_ADDRESS_KEY = "address"
private const val ACTION_REASON_KEY = "reason"
private const val ACTION_POKE_NOTE_KEY = "note"
private const val ACTION_POKE_TARGET_KEY = "poke_target"
private const val ACTION_POKE_SUFFIX_KEY = "poke_suffix"

private fun buildPokeDisplayText(
    target: String,
    suffix: String,
    fallback: String,
): String {
    if (suffix.isBlank() && target.isBlank()) {
        return fallback
    }
    return buildString {
        append("拍了拍")
        when (target.lowercase()) {
            "自己", "self" -> append("自己")
            "用户", "user", "对方" -> append("你")
            else -> if (target.isNotBlank()) append(target) else append("你")
        }
        if (suffix.isNotBlank()) {
            append(suffix)
        }
    }
}
private const val GIFT_IMAGE_STATUS_KEY = "gift_image_status"
private const val GIFT_IMAGE_URI_KEY = "gift_image_uri"
private const val GIFT_IMAGE_MIME_TYPE_KEY = "gift_image_mime_type"
private const val GIFT_IMAGE_FILE_NAME_KEY = "gift_image_file_name"
private const val GIFT_IMAGE_ERROR_KEY = "gift_image_error"
private const val PUNISH_METHOD_KEY = "method"
private const val PUNISH_COUNT_KEY = "count"
private const val PUNISH_INTENSITY_KEY = "intensity"
private const val PUNISH_REASON_KEY = "reason"
private const val PUNISH_NOTE_KEY = "note"
