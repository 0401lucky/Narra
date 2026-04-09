package com.example.myapplication.data.repository.phone

import com.example.myapplication.data.local.phone.PhoneSnapshotDao
import com.example.myapplication.data.local.phone.PhoneObservationEntity
import com.example.myapplication.data.local.phone.PhoneSnapshotEntity
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneViewMode
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

    fun observeObservation(conversationId: String): Flow<PhoneObservationState?>

    suspend fun getObservation(conversationId: String): PhoneObservationState?

    suspend fun upsertObservation(observation: PhoneObservationState)

    suspend fun deleteObservation(conversationId: String)
}

class RoomPhoneSnapshotRepository(
    private val phoneSnapshotDao: PhoneSnapshotDao,
    private val gson: Gson = Gson(),
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

    override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> = flowOf(null)

    override suspend fun getObservation(conversationId: String): PhoneObservationState? = null

    override suspend fun upsertObservation(observation: PhoneObservationState) = Unit

    override suspend fun deleteObservation(conversationId: String) = Unit
}

fun PhoneSnapshot.toEntity(gson: Gson = Gson()): PhoneSnapshotEntity {
    return PhoneSnapshotEntity(
        conversationId = conversationId,
        ownerType = ownerType.storageValue,
        scenarioId = scenarioId.trim(),
        assistantId = assistantId.trim(),
        updatedAt = updatedAt,
        snapshotJson = gson.toJson(this),
    )
}

fun PhoneSnapshotEntity.toDomain(gson: Gson = Gson()): PhoneSnapshot? {
    return runCatching {
        gson.fromJson(snapshotJson, PhoneSnapshot::class.java)
    }.getOrNull()?.copy(
        conversationId = conversationId,
        ownerType = PhoneSnapshotOwnerType.fromStorageValue(ownerType),
        scenarioId = scenarioId.trim(),
        assistantId = assistantId.trim(),
        updatedAt = updatedAt,
    )
}

fun PhoneObservationState.toEntity(gson: Gson = Gson()): PhoneObservationEntity {
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
    gson: Gson = Gson(),
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
