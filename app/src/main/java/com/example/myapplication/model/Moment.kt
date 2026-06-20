package com.example.myapplication.model

import java.util.UUID

data class MomentPost(
    val id: String = UUID.randomUUID().toString(),
    val authorType: MomentAuthorType = MomentAuthorType.USER,
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUri: String = "",
    val authorLabel: String = "",
    val content: String = "",
    val location: String = "",
    val likedByNames: List<String> = emptyList(),
    val comments: List<MomentComment> = emptyList(),
    val media: MomentMedia? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val likeCount: Int
        get() = likedByNames.size

    fun toggleLike(viewerName: String): MomentPost {
        val normalizedViewerName = viewerName.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME }
        return if (normalizedViewerName in likedByNames) {
            copy(likedByNames = likedByNames.filterNot { it == normalizedViewerName })
        } else {
            copy(likedByNames = likedByNames + normalizedViewerName)
        }
    }
}

data class MomentComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val authorType: MomentAuthorType = MomentAuthorType.ASSISTANT,
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUri: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
)

data class MomentMedia(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val prompt: String = "",
    val imageUri: String = "",
    val mimeType: String = "",
    val fileName: String = "",
    val status: MomentMediaStatus = MomentMediaStatus.GENERATING,
    val errorMessage: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class MomentsSettings(
    val backgroundGenerationEnabled: Boolean = true,
    val coverImageUri: String = "",
)

data class MomentPostDraft(
    val content: String = "",
    val imagePrompt: String = "",
    val likedBy: List<String> = emptyList(),
    val seedComments: List<MomentCommentDraft> = emptyList(),
)

data class MomentCommentDraft(
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val authorType: MomentAuthorType = MomentAuthorType.ASSISTANT,
)

data class MomentAssistantContext(
    val id: String,
    val name: String,
    val persona: String,
    val commentStyle: MomentCommentStyle = MomentCommentStyle.NATURAL,
)

enum class MomentAuthorType(val storageValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
    NPC("npc"),
    SYSTEM("system");

    companion object {
        fun fromStorageValue(value: String): MomentAuthorType {
            return entries.firstOrNull { it.storageValue == value } ?: ASSISTANT
        }
    }
}

enum class MomentMediaStatus(val storageValue: String) {
    GENERATING("generating"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    companion object {
        fun fromStorageValue(value: String): MomentMediaStatus {
            return entries.firstOrNull { it.storageValue == value } ?: GENERATING
        }
    }
}

enum class MomentAutoPostFrequency(
    val storageValue: String,
    val label: String,
    val minIntervalMillis: Long,
) {
    LOW("low", "低频", 12L * 60L * 60L * 1000L),
    STANDARD("standard", "标准", 6L * 60L * 60L * 1000L),
    HIGH("high", "高频", 3L * 60L * 60L * 1000L);

    companion object {
        fun fromStorageValue(value: String): MomentAutoPostFrequency {
            return entries.firstOrNull { it.storageValue == value } ?: STANDARD
        }
    }
}

enum class MomentCommentStyle(val storageValue: String, val label: String) {
    RESTRAINED("restrained", "克制"),
    NATURAL("natural", "自然"),
    ACTIVE("active", "积极");

    companion object {
        fun fromStorageValue(value: String): MomentCommentStyle {
            return entries.firstOrNull { it.storageValue == value } ?: NATURAL
        }
    }
}

val MomentNpcFallbackNames = listOf(
    "林夏",
    "陈琪",
    "周屿",
    "许安",
    "姜禾",
    "宋知予",
    "沈念",
    "顾南枝",
    "叶棠",
    "温梨",
)

fun sanitizeMomentDisplayName(
    name: String,
    stableKey: String = "",
): String {
    val normalized = name.trim()
    return if (normalized.isPlaceholderMomentDisplayName()) {
        stableMomentFallbackName(stableKey.ifBlank { normalized })
    } else {
        normalized
    }
}

fun stableMomentFallbackName(stableKey: String): String {
    val key = stableKey.trim().ifBlank { "moment" }
    val index = (key.hashCode() and Int.MAX_VALUE) % MomentNpcFallbackNames.size
    return MomentNpcFallbackNames[index]
}

fun String.isPlaceholderMomentDisplayName(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return true
    return normalized in MomentPlaceholderNames
}

private val MomentPlaceholderNames = setOf(
    "默认角色",
    "角色",
    "NPC",
    "npc",
    "朋友A",
    "路人A",
)
