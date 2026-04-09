package com.example.myapplication.model

import androidx.compose.runtime.Immutable

enum class PhoneSnapshotOwnerType(val storageValue: String, val displayName: String) {
    CHARACTER("character", "TA的手机"),
    USER("user", "我的手机");

    companion object {
        fun fromStorageValue(value: String): PhoneSnapshotOwnerType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: CHARACTER
        }
    }
}

enum class PhoneViewMode(val storageValue: String) {
    USER_LOOKS_CHARACTER_PHONE("user_looks_character_phone"),
    CHARACTER_LOOKS_USER_PHONE("character_looks_user_phone");

    companion object {
        fun fromStorageValue(value: String): PhoneViewMode {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() }
                ?: USER_LOOKS_CHARACTER_PHONE
        }
    }
}

enum class PhoneSnapshotSection(val storageValue: String, val displayName: String) {
    MESSAGES("messages", "消息"),
    NOTES("notes", "备忘录"),
    GALLERY("gallery", "相册"),
    SHOPPING("shopping", "购物"),
    SEARCH("search", "搜索");

    companion object {
        fun fromStorageValue(value: String): PhoneSnapshotSection? {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() }
        }
    }
}

@Immutable
data class PhoneSnapshot(
    val conversationId: String,
    val ownerType: PhoneSnapshotOwnerType = PhoneSnapshotOwnerType.CHARACTER,
    val scenarioId: String = "",
    val assistantId: String = "",
    val ownerName: String = "",
    val updatedAt: Long = 0L,
    val relationshipHighlights: List<PhoneRelationshipHighlight> = emptyList(),
    val messageThreads: List<PhoneMessageThread> = emptyList(),
    val notes: List<PhoneNoteEntry> = emptyList(),
    val gallery: List<PhoneGalleryEntry> = emptyList(),
    val shoppingRecords: List<PhoneShoppingEntry> = emptyList(),
    val searchHistory: List<PhoneSearchEntry> = emptyList(),
) {
    fun hasContent(): Boolean {
        return relationshipHighlights.isNotEmpty() ||
            messageThreads.isNotEmpty() ||
            notes.isNotEmpty() ||
            gallery.isNotEmpty() ||
            shoppingRecords.isNotEmpty() ||
            searchHistory.isNotEmpty()
    }

    fun mergeSections(
        sections: PhoneSnapshotSections,
        requestedSections: Set<PhoneSnapshotSection>,
        updatedAt: Long,
        ownerType: PhoneSnapshotOwnerType = this.ownerType,
        scenarioId: String = this.scenarioId,
        assistantId: String = this.assistantId,
        ownerName: String = this.ownerName,
    ): PhoneSnapshot {
        val normalizedRequested = requestedSections.ifEmpty { PhoneSnapshotSection.entries.toSet() }
        return copy(
            ownerType = ownerType,
            scenarioId = scenarioId,
            assistantId = assistantId,
            ownerName = ownerName,
            updatedAt = updatedAt,
            relationshipHighlights = if (PhoneSnapshotSection.MESSAGES in normalizedRequested) {
                sections.relationshipHighlights.orEmpty()
            } else {
                relationshipHighlights
            },
            messageThreads = if (PhoneSnapshotSection.MESSAGES in normalizedRequested) {
                sections.messageThreads.orEmpty()
            } else {
                messageThreads
            },
            notes = if (PhoneSnapshotSection.NOTES in normalizedRequested) {
                sections.notes.orEmpty()
            } else {
                notes
            },
            gallery = if (PhoneSnapshotSection.GALLERY in normalizedRequested) {
                sections.gallery.orEmpty()
            } else {
                gallery
            },
            shoppingRecords = if (PhoneSnapshotSection.SHOPPING in normalizedRequested) {
                sections.shoppingRecords.orEmpty()
            } else {
                shoppingRecords
            },
            searchHistory = if (PhoneSnapshotSection.SEARCH in normalizedRequested) {
                sections.searchHistory.orEmpty()
            } else {
                searchHistory
            },
        )
    }

    fun withSearchDetail(
        entryId: String,
        detail: PhoneSearchDetail,
        updatedAt: Long,
    ): PhoneSnapshot {
        if (entryId.isBlank()) {
            return this
        }
        return copy(
            updatedAt = updatedAt,
            searchHistory = searchHistory.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(detail = detail)
                } else {
                    entry
                }
            },
        )
    }
}

@Immutable
data class PhoneSnapshotSections(
    val relationshipHighlights: List<PhoneRelationshipHighlight>? = null,
    val messageThreads: List<PhoneMessageThread>? = null,
    val notes: List<PhoneNoteEntry>? = null,
    val gallery: List<PhoneGalleryEntry>? = null,
    val shoppingRecords: List<PhoneShoppingEntry>? = null,
    val searchHistory: List<PhoneSearchEntry>? = null,
)

@Immutable
data class PhoneRelationshipHighlight(
    val id: String,
    val name: String,
    val relationLabel: String,
    val stance: String,
    val note: String,
)

@Immutable
data class PhoneMessageThread(
    val id: String,
    val contactName: String,
    val relationLabel: String = "",
    val preview: String,
    val timeLabel: String,
    val avatarLabel: String = "",
    val messages: List<PhoneMessageItem> = emptyList(),
)

@Immutable
data class PhoneMessageItem(
    val id: String,
    val senderName: String,
    val text: String,
    val timeLabel: String,
    val isOwner: Boolean,
)

@Immutable
data class PhoneNoteEntry(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val timeLabel: String,
    val icon: String = "",
)

@Immutable
data class PhoneGalleryEntry(
    val id: String,
    val title: String,
    val summary: String,
    val description: String,
    val timeLabel: String,
)

@Immutable
data class PhoneShoppingEntry(
    val id: String,
    val title: String,
    val status: String,
    val priceLabel: String,
    val note: String,
    val detail: String,
    val timeLabel: String,
)

@Immutable
data class PhoneSearchEntry(
    val id: String,
    val query: String,
    val timeLabel: String,
    val detail: PhoneSearchDetail? = null,
)

@Immutable
data class PhoneSearchDetail(
    val title: String,
    val summary: String,
    val content: String,
)
