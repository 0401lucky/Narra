package com.example.myapplication.data.repository.phone

import com.example.myapplication.data.local.phone.PhoneSnapshotDao
import com.example.myapplication.data.local.phone.PhoneObservationEntity
import com.example.myapplication.data.local.phone.PhoneSnapshotEntity
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageItem
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneRelationshipHighlight
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSocialComment
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.PhoneViewMode
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface PhoneSnapshotRepository {
    fun observeSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): Flow<PhoneSnapshot?>

    suspend fun getSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): PhoneSnapshot?

    suspend fun upsertSnapshot(snapshot: PhoneSnapshot)

    suspend fun deleteSnapshot(conversationId: String)

    suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    )

    fun observeObservation(conversationId: String): Flow<PhoneObservationState?>

    suspend fun getObservation(conversationId: String): PhoneObservationState?

    suspend fun upsertObservation(observation: PhoneObservationState)

    suspend fun deleteObservation(conversationId: String)
}

class RoomPhoneSnapshotRepository(
    private val phoneSnapshotDao: PhoneSnapshotDao,
    private val gson: Gson = AppJson.gson,
) : PhoneSnapshotRepository {
    private val stringListType = object : TypeToken<List<String>>() {}.type

    override fun observeSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): Flow<PhoneSnapshot?> {
        if (conversationId.isBlank()) {
            return flowOf(null)
        }
        return phoneSnapshotDao.observeSnapshot(
            conversationId = conversationId,
            ownerType = ownerType.storageValue,
        ).map { entity ->
            entity?.toDomain(gson)
        }
    }

    override suspend fun getSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): PhoneSnapshot? {
        if (conversationId.isBlank()) {
            return null
        }
        return phoneSnapshotDao.getSnapshot(
            conversationId = conversationId,
            ownerType = ownerType.storageValue,
        )?.toDomain(gson)
    }

    override suspend fun upsertSnapshot(snapshot: PhoneSnapshot) {
        if (snapshot.conversationId.isBlank()) {
            return
        }
        phoneSnapshotDao.upsertSnapshot(snapshot.toEntity(gson))
    }

    override suspend fun deleteSnapshot(conversationId: String) {
        if (conversationId.isBlank()) {
            return
        }
        phoneSnapshotDao.deleteSnapshot(conversationId)
    }

    override suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ) {
        if (conversationId.isBlank()) {
            return
        }
        phoneSnapshotDao.deleteSnapshot(
            conversationId = conversationId,
            ownerType = ownerType.storageValue,
        )
    }

    override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> {
        if (conversationId.isBlank()) {
            return flowOf(null)
        }
        return phoneSnapshotDao.observeObservation(conversationId).map { entity ->
            entity?.toDomain(gson, stringListType)
        }
    }

    override suspend fun getObservation(conversationId: String): PhoneObservationState? {
        if (conversationId.isBlank()) {
            return null
        }
        return phoneSnapshotDao.getObservation(conversationId)?.toDomain(gson, stringListType)
    }

    override suspend fun upsertObservation(observation: PhoneObservationState) {
        if (observation.conversationId.isBlank()) {
            return
        }
        phoneSnapshotDao.upsertObservation(observation.toEntity(gson))
    }

    override suspend fun deleteObservation(conversationId: String) {
        if (conversationId.isBlank()) {
            return
        }
        phoneSnapshotDao.deleteObservation(conversationId)
    }
}

object EmptyPhoneSnapshotRepository : PhoneSnapshotRepository {
    override fun observeSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): Flow<PhoneSnapshot?> = flowOf(null)

    override suspend fun getSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): PhoneSnapshot? = null

    override suspend fun upsertSnapshot(snapshot: PhoneSnapshot) = Unit

    override suspend fun deleteSnapshot(conversationId: String) = Unit

    override suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ) = Unit

    override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> = flowOf(null)

    override suspend fun getObservation(conversationId: String): PhoneObservationState? = null

    override suspend fun upsertObservation(observation: PhoneObservationState) = Unit

    override suspend fun deleteObservation(conversationId: String) = Unit
}

fun PhoneSnapshot.toEntity(gson: Gson = AppJson.gson): PhoneSnapshotEntity {
    return PhoneSnapshotEntity(
        conversationId = conversationId,
        ownerType = ownerType.storageValue,
        scenarioId = scenarioId.trim(),
        assistantId = assistantId.trim(),
        updatedAt = updatedAt,
        snapshotJson = gson.toJson(this),
    )
}

fun PhoneSnapshotEntity.toDomain(gson: Gson = AppJson.gson): PhoneSnapshot? {
    return runCatching {
        gson.fromJson(snapshotJson, PhoneSnapshotPayload::class.java)?.toDomain(
            conversationId = conversationId,
            ownerType = PhoneSnapshotOwnerType.fromStorageValue(ownerType),
            scenarioId = scenarioId.trim(),
            assistantId = assistantId.trim(),
            updatedAt = updatedAt,
        )
    }.logFailure("PhoneSnapRepo") { "snapshot fromJson failed, json.len=${snapshotJson.length}" }
        .getOrNull()
}

private data class PhoneSnapshotPayload(
    val contentSemanticsVersion: Int? = null,
    val ownerName: String? = null,
    val relationshipHighlights: List<PhoneRelationshipHighlightPayload?>? = null,
    val messageThreads: List<PhoneMessageThreadPayload?>? = null,
    val notes: List<PhoneNoteEntryPayload?>? = null,
    val gallery: List<PhoneGalleryEntryPayload?>? = null,
    val shoppingRecords: List<PhoneShoppingEntryPayload?>? = null,
    val searchHistory: List<PhoneSearchEntryPayload?>? = null,
    val socialPosts: List<PhoneSocialPostPayload?>? = null,
)

private data class PhoneRelationshipHighlightPayload(
    val id: String? = null,
    val name: String? = null,
    val relationLabel: String? = null,
    val stance: String? = null,
    val note: String? = null,
)

private data class PhoneMessageThreadPayload(
    val id: String? = null,
    val contactName: String? = null,
    val relationLabel: String? = null,
    val preview: String? = null,
    val timeLabel: String? = null,
    val avatarLabel: String? = null,
    val messages: List<PhoneMessageItemPayload?>? = null,
)

private data class PhoneMessageItemPayload(
    val id: String? = null,
    val senderName: String? = null,
    val text: String? = null,
    val timeLabel: String? = null,
    val isOwner: Boolean? = null,
)

private data class PhoneNoteEntryPayload(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val timeLabel: String? = null,
    val icon: String? = null,
)

private data class PhoneGalleryEntryPayload(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val timeLabel: String? = null,
)

private data class PhoneShoppingEntryPayload(
    val id: String? = null,
    val title: String? = null,
    val status: String? = null,
    val priceLabel: String? = null,
    val note: String? = null,
    val detail: String? = null,
    val timeLabel: String? = null,
)

private data class PhoneSearchEntryPayload(
    val id: String? = null,
    val query: String? = null,
    val timeLabel: String? = null,
    val detail: PhoneSearchDetailPayload? = null,
)

private data class PhoneSearchDetailPayload(
    val title: String? = null,
    val summary: String? = null,
    val content: String? = null,
)

private data class PhoneSocialPostPayload(
    val id: String? = null,
    val authorName: String? = null,
    val authorLabel: String? = null,
    val content: String? = null,
    val timeLabel: String? = null,
    val likeCount: Int? = null,
    val likedByNames: List<String?>? = null,
    val comments: List<PhoneSocialCommentPayload?>? = null,
)

private data class PhoneSocialCommentPayload(
    val id: String? = null,
    val authorName: String? = null,
    val text: String? = null,
)

private fun PhoneSnapshotPayload.toDomain(
    conversationId: String,
    ownerType: PhoneSnapshotOwnerType,
    scenarioId: String,
    assistantId: String,
    updatedAt: Long,
): PhoneSnapshot {
    return PhoneSnapshot(
        conversationId = conversationId,
        ownerType = ownerType,
        scenarioId = scenarioId,
        assistantId = assistantId,
        contentSemanticsVersion = contentSemanticsVersion
            ?: PhoneSnapshot.DEFAULT_PHONE_CONTENT_SEMANTICS_VERSION,
        ownerName = ownerName.orEmpty(),
        updatedAt = updatedAt,
        relationshipHighlights = relationshipHighlights.orEmpty()
            .mapNotNull { it?.toDomain() },
        messageThreads = messageThreads.orEmpty()
            .mapNotNull { it?.toDomain() },
        notes = notes.orEmpty()
            .mapNotNull { it?.toDomain() },
        gallery = gallery.orEmpty()
            .mapNotNull { it?.toDomain() },
        shoppingRecords = shoppingRecords.orEmpty()
            .mapNotNull { it?.toDomain() },
        searchHistory = searchHistory.orEmpty()
            .mapNotNull { it?.toDomain() },
        socialPosts = socialPosts.orEmpty()
            .mapNotNull { it?.toDomain() },
    )
}

private fun PhoneRelationshipHighlightPayload.toDomain(): PhoneRelationshipHighlight {
    return PhoneRelationshipHighlight(
        id = id.orEmpty(),
        name = name.orEmpty(),
        relationLabel = relationLabel.orEmpty(),
        stance = stance.orEmpty(),
        note = note.orEmpty(),
    )
}

private fun PhoneMessageThreadPayload.toDomain(): PhoneMessageThread {
    return PhoneMessageThread(
        id = id.orEmpty(),
        contactName = contactName.orEmpty(),
        relationLabel = relationLabel.orEmpty(),
        preview = preview.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
        avatarLabel = avatarLabel.orEmpty(),
        messages = messages.orEmpty().mapNotNull { it?.toDomain() },
    )
}

private fun PhoneMessageItemPayload.toDomain(): PhoneMessageItem {
    return PhoneMessageItem(
        id = id.orEmpty(),
        senderName = senderName.orEmpty(),
        text = text.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
        isOwner = isOwner ?: false,
    )
}

private fun PhoneNoteEntryPayload.toDomain(): PhoneNoteEntry {
    return PhoneNoteEntry(
        id = id.orEmpty(),
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        content = content.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
        icon = icon.orEmpty(),
    )
}

private fun PhoneGalleryEntryPayload.toDomain(): PhoneGalleryEntry {
    return PhoneGalleryEntry(
        id = id.orEmpty(),
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        description = description.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
    )
}

private fun PhoneShoppingEntryPayload.toDomain(): PhoneShoppingEntry {
    return PhoneShoppingEntry(
        id = id.orEmpty(),
        title = title.orEmpty(),
        status = status.orEmpty(),
        priceLabel = priceLabel.orEmpty(),
        note = note.orEmpty(),
        detail = detail.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
    )
}

private fun PhoneSearchEntryPayload.toDomain(): PhoneSearchEntry {
    return PhoneSearchEntry(
        id = id.orEmpty(),
        query = query.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
        detail = detail?.toDomain(),
    )
}

private fun PhoneSearchDetailPayload.toDomain(): PhoneSearchDetail {
    return PhoneSearchDetail(
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        content = content.orEmpty(),
    )
}

private fun PhoneSocialPostPayload.toDomain(): PhoneSocialPost {
    return PhoneSocialPost(
        id = id.orEmpty(),
        authorName = authorName.orEmpty(),
        authorLabel = authorLabel.orEmpty(),
        content = content.orEmpty(),
        timeLabel = timeLabel.orEmpty(),
        likeCount = likeCount ?: 0,
        likedByNames = likedByNames.orEmpty().mapNotNull { it },
        comments = comments.orEmpty().mapNotNull { it?.toDomain() },
    )
}

private fun PhoneSocialCommentPayload.toDomain(): PhoneSocialComment {
    return PhoneSocialComment(
        id = id.orEmpty(),
        authorName = authorName.orEmpty(),
        text = text.orEmpty(),
    )
}

fun PhoneObservationState.toEntity(gson: Gson = AppJson.gson): PhoneObservationEntity {
    return PhoneObservationEntity(
        conversationId = conversationId,
        scenarioId = scenarioId.trim(),
        ownerType = ownerType.storageValue,
        viewMode = viewMode.storageValue,
        ownerName = ownerName.trim(),
        viewerName = viewerName.trim(),
        eventText = eventText.trim(),
        keyFindingsJson = gson.toJson(keyFindings),
        observedAt = observedAt,
        hasVisibleFeedback = hasVisibleFeedback,
        feedbackMessageId = feedbackMessageId,
        usedFindingKeysJson = gson.toJson(usedFindingKeys),
        updatedAt = updatedAt,
    )
}

fun PhoneObservationEntity.toDomain(
    gson: Gson = AppJson.gson,
    stringListType: java.lang.reflect.Type = object : TypeToken<List<String>>() {}.type,
): PhoneObservationState {
    return PhoneObservationState(
        conversationId = conversationId,
        scenarioId = scenarioId.trim(),
        ownerType = PhoneSnapshotOwnerType.fromStorageValue(ownerType),
        viewMode = PhoneViewMode.fromStorageValue(viewMode),
        ownerName = ownerName.trim(),
        viewerName = viewerName.trim(),
        eventText = eventText.trim(),
        keyFindings = runCatching {
            gson.fromJson<List<String>>(keyFindingsJson, stringListType).orEmpty()
        }.getOrDefault(emptyList()),
        observedAt = observedAt,
        hasVisibleFeedback = hasVisibleFeedback,
        feedbackMessageId = feedbackMessageId,
        usedFindingKeys = runCatching {
            gson.fromJson<List<String>>(usedFindingKeysJson, stringListType).orEmpty()
        }.getOrDefault(emptyList()),
        updatedAt = updatedAt,
    )
}
