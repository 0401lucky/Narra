package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.memory.*
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson

internal fun MemoryRecordRevisionEntity.transferDomain() = MemoryRecordRevision(
    recordId, revision, content, MemoryChangeOrigin.valueOf(origin), createdAt,
)

internal fun MemoryRecordSourceEntity.transferDomain() = MemoryTransferRecordSource(
    recordId, recordRevision, MemorySourceRef(sourceId, sourceRevision, spanStart, spanEnd),
)

internal fun MemoryEventBoxEntity.transferDomain() = MemoryEventBox(
    id,
    spaceId,
    characterId,
    personaKey,
    title,
    summary,
    summaryRevision,
    MemoryEventSummaryStatus.valueOf(summaryStatus),
    MemoryEventStatus.valueOf(status),
    currentState,
    generation,
    createdAt,
    updatedAt,
)

internal fun MemoryEventMemberEntity.transferDomain() = MemoryTransferEventMember(
    eventId, recordId, coveredRevision, MemoryEventMemberStatus.valueOf(status), addedAt,
)

internal fun MemoryResidentEntryEntity.transferDomain() = MemoryResidentEntry(
    id,
    spaceId,
    characterId,
    personaKey,
    MemoryResidentCategory.valueOf(category),
    content,
    revision,
    status,
    subjective,
    editedByUser,
    supersedesId,
    createdAt,
    updatedAt,
)

internal fun MemoryCurrentStateEntity.transferDomain() = MemoryCurrentState(
    id, spaceId, characterId, personaKey, MemoryStateKey.valueOf(stateKey.uppercase()), content,
    revision, status, expiresAt, endedAt, observedAt, editedByUser, createdAt, updatedAt,
)

internal fun MemoryDerivedLinkEntity.transferDomain() = MemoryTransferDerivedLink(
    MemoryMaterialKind.valueOf(ownerKind), ownerId, MemoryRecordVersion(recordId, recordRevision),
)

internal fun MemoryTransferOriginEntity.transferDomain() = MemoryTransferOriginLink(
    MemoryTransferObjectKind.valueOf(kind),
    when (MemoryTransferObjectKind.valueOf(kind)) {
        MemoryTransferObjectKind.SPACE -> spaceId
        MemoryTransferObjectKind.RECORD -> requireNotNull(recordId)
        MemoryTransferObjectKind.SOURCE -> requireNotNull(sourceId)
        MemoryTransferObjectKind.EVENT -> requireNotNull(eventId)
        MemoryTransferObjectKind.RESIDENT -> requireNotNull(residentId)
        MemoryTransferObjectKind.STATE -> requireNotNull(stateId)
    },
    localRevision, AppJson.gson.fromJson(originJson, MemoryTransferOrigin::class.java),
)

internal fun MemoryTransferOriginLink.entity(id: String, spaceId: String) =
    MemoryTransferOriginEntity(
        id, spaceId, kind.name,
        localId.takeIf { kind == MemoryTransferObjectKind.RECORD },
        localId.takeIf { kind == MemoryTransferObjectKind.SOURCE },
        localId.takeIf { kind == MemoryTransferObjectKind.EVENT },
        localId.takeIf { kind == MemoryTransferObjectKind.RESIDENT },
        localId.takeIf { kind == MemoryTransferObjectKind.STATE },
        localRevision, AppJson.gson.toJson(origin),
    )

internal fun MemoryRecord.importedEntity(id: String, target: MemoryRuntimeContext) =
    MemoryRecordEntity(
        id,
        target.spaceId,
        target.characterId,
        kind.name,
        category.name,
        content,
        revision,
        status.name,
        importance,
        pinned,
        pinnedUntil,
        occurredAt,
        datePrecision,
        target.personaKey,
        createdAt,
        updatedAt,
        lastUsedAt,
        null,
        null,
        "",
        "",
        "",
    )

internal fun MemorySourceEvidence.importedEntity(id: String, target: MemoryRuntimeContext) =
    MemorySourceEvidenceEntity(
        id,
        target.spaceId,
        target.characterId,
        MemorySourceKind.IMPORTED.name,
        "",
        null,
        messageRevision,
        speakerId,
        speakerRole,
        target.personaKey,
        AppJson.gson.toJson(listOf(target.characterId)),
        content,
        contentLength,
        status.name,
        occurredAt,
        ingestedAt,
        contentFingerprint,
        contentFormatVersion,
    )

internal fun MemoryResidentEntry.importedEntity(
    id: String,
    target: MemoryRuntimeContext,
    supersedesId: String?
) = MemoryResidentEntryEntity(
    id,
    target.spaceId,
    target.characterId,
    category.name,
    target.personaKey,
    content,
    revision,
    status,
    subjective,
    supersedesId,
    editedByUser,
    createdAt,
    updatedAt,
)

internal fun MemoryCurrentState.importedEntity(
    id: String,
    target: MemoryRuntimeContext,
    revision: Long
) = MemoryCurrentStateEntity(
    id,
    target.spaceId,
    target.characterId,
    key.name.lowercase(),
    target.personaKey,
    content,
    revision,
    status,
    expiresAt,
    endedAt,
    createdAt,
    updatedAt,
    editedByUser,
    observedAt,
)
