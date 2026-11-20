package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.memory.MemoryConversationBindingEntity
import com.example.myapplication.data.local.memory.MemoryProcessingCursorEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.data.local.memory.MemorySpaceEntity
import com.example.myapplication.model.MemoryCategory
import com.example.myapplication.model.MemoryConversationBinding
import com.example.myapplication.model.MemoryProcessingCursor
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceKind
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.model.MemorySpace
import com.example.myapplication.model.MemorySpaceStatus
import com.example.myapplication.system.json.AppJson

internal fun MemorySpaceEntity.toDomain() = MemorySpace(
    id, characterId, MemoryPurpose.fromStorageValue(purpose), scenarioId, runId, generation,
    MemorySpaceStatus.valueOf(status), createdAt, updatedAt,
)

internal fun MemoryConversationBindingEntity.toDomain() = MemoryConversationBinding(
    conversationId, characterId, spaceId, MemoryPurpose.fromStorageValue(purpose), scenarioId,
    runId, personaKey, archived, createdAt,
)

internal fun MemoryRecordEntity.toDomain() = MemoryRecord(
    id, spaceId, characterId, MemoryRecordKind.valueOf(kind), MemoryCategory.valueOf(category),
    content, revision, MemoryRecordStatus.valueOf(status), importance, pinned, pinnedUntil,
    occurredAt, datePrecision, personaKey, createdAt, updatedAt, lastUsedAt, legacyScopeType,
    legacyScopeId, legacySourceMessageId,
)

internal fun MemorySourceEvidenceEntity.toDomain() = MemorySourceEvidence(
    id,
    spaceId,
    characterId,
    MemorySourceKind.valueOf(kind),
    conversationId,
    messageId.orEmpty(),
    messageRevision,
    speakerId,
    speakerRole,
    personaKey,
    AppJson.gson.fromJson(visibleCharacterIdsJson, Array<String>::class.java).toList(),
    content,
    contentLength,
    MemorySourceStatus.valueOf(status),
    occurredAt,
    ingestedAt,
    contentFingerprint,
    contentFormatVersion,
)

internal fun MemoryProcessingCursorEntity.toDomain() = MemoryProcessingCursor(
    spaceId, committedSequence, historyBackfilled, updatedAt,
)
