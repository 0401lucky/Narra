package com.example.myapplication.data.repository.context

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.*

/** v1 原文作为用户导入的资料保存；原 scope/摘要覆盖指针只有出处含义。 */
object MemoryTransferLegacyConversion {
    fun materialize(bundle: ContextDataBundle): MemoryArchiveTransferData {
        MemoryArchiveTransferValidation.validateLegacy(bundle)
        val original = bundle.memoryArchive ?: MemoryArchiveTransferData()
        val spaces = original.spaces.toMutableList()
        val records = original.records.toMutableList()
        val revisions = original.recordRevisions.toMutableList()
        val sources = original.sources.toMutableList()
        val links = original.recordSources.toMutableList()
        val origins = original.origins.toMutableList()
        val existingRecordIds = records.map { it.id }.toSet()
        fun append(
            key: String,
            owner: String,
            persona: String,
            content: String,
            createdAt: Long,
            updatedAt: Long,
            importance: Int = 0,
            pinned: Boolean = false,
            lastUsedAt: Long = 0L,
            scopeType: String? = null,
            scopeId: String? = null,
            originalId: String?,
            sourceMessageId: String? = null,
            summary: MemoryImportedSummaryMetadata? = null,
        ) {
            val characterId = owner.ifBlank { MEMORY_UNASSIGNED_CHARACTER }
            val spaceId = MemoryArchiveIdentity.id(
                "legacy-transfer-space",
                characterId,
                key.substringBefore('\u0000'),
                persona
            )
            if (spaces.none { it.id == spaceId }) spaces += MemorySpace(
                spaceId,
                characterId,
                MemoryPurpose.LEGACY_COMPAT,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            val recordId = MemoryArchiveIdentity.id("legacy-transfer-record", key)
            val sourceId = MemoryArchiveIdentity.id("legacy-transfer-source", key)
            records += MemoryRecord(
                recordId,
                spaceId,
                characterId,
                MemoryRecordKind.LEGACY_UNCLASSIFIED,
                MemoryCategory.DAILY,
                content,
                importance = importance,
                pinned = pinned,
                personaKey = persona,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
            )
            revisions += MemoryRecordRevision(
                recordId,
                1L,
                content,
                MemoryChangeOrigin.IMPORTED,
                updatedAt
            )
            val available = content.isNotBlank()
            sources += MemorySourceEvidence(
                sourceId,
                spaceId,
                characterId,
                MemorySourceKind.IMPORTED,
                "",
                "",
                1L,
                "",
                "",
                persona,
                listOf(characterId),
                content,
                content.length,
                if (available) MemorySourceStatus.AVAILABLE else MemorySourceStatus.UNKNOWN,
                createdAt,
                updatedAt,
                MemoryArchiveIdentity.fingerprint(content),
            )
            links += MemoryTransferRecordSource(
                recordId,
                1L,
                MemorySourceRef(sourceId, 1L, 0, content.length)
            )
            val origin = MemoryTransferOrigin(
                importBatchId = "",
                bundleVersion = bundle.version,
                exportedAt = bundle.exportedAt,
                importedAt = 0L,
                originalId = originalId,
                originalSpaceId = null,
                originalCharacterId = owner.ifBlank { null },
                originalPersonaKey = if (summary == null) null else persona,
                originalRevision = null,
                originalLegacyScopeType = scopeType,
                originalLegacyScopeId = scopeId,
                originalLegacySourceMessageId = sourceMessageId,
                legacySummary = summary,
                originalConversationId = summary?.conversationId,
            )
            origins += MemoryTransferOriginLink(
                MemoryTransferObjectKind.RECORD,
                recordId,
                1L,
                origin
            )
            // 旧格式没有 sourceEvidence；不能给它补造原始 MESSAGE/source ID。
            origins += MemoryTransferOriginLink(
                MemoryTransferObjectKind.SOURCE, sourceId, 1L,
                origin.copy(
                    originalId = null,
                    originalSourceKind = null,
                    originalSourceStatus = null,
                    legacySummary = null
                )
            )
        }
        bundle.memoryEntries.filterNot { it.id in existingRecordIds }.forEach {
            append(
                "${it.scopeType.storageValue}:${it.scopeId}\u0000${it.id}",
                it.characterId.ifBlank { if (it.scopeType == MemoryScopeType.ASSISTANT) it.scopeId else "" },
                MEMORY_UNKNOWN_PERSONA,
                it.content,
                it.createdAt,
                it.updatedAt,
                it.importance,
                it.pinned,
                it.lastUsedAt,
                it.scopeType.storageValue,
                it.scopeId,
                it.id,
                it.sourceMessageId,
            )
        }
        bundle.conversationSummaries.forEach {
            append(
                "summary:${it.conversationId}:${it.assistantId}:${it.memoryContext?.personaKey.orEmpty()}\u0000head",
                it.assistantId.ifBlank { it.memoryContext?.characterId.orEmpty() },
                it.memoryContext?.personaKey ?: MEMORY_UNKNOWN_PERSONA,
                it.summary,
                it.updatedAt,
                it.updatedAt,
                originalId = it.conversationId,
                summary = MemoryImportedSummaryMetadata(
                    it.conversationId,
                    it.assistantId,
                    null,
                    "",
                    "",
                    0L,
                    0L,
                    it.coveredMessageCount,
                    it.updatedAt,
                    it.updatedAt,
                    it.sourceSnapshot,
                    it.completionStatus,
                    it.memoryContext,
                ),
            )
        }
        bundle.conversationSummarySegments.forEach {
            append(
                "summary:${it.conversationId}:${it.assistantId}:${it.memoryContext?.personaKey.orEmpty()}\u0000${it.id}",
                it.assistantId.ifBlank { it.memoryContext?.characterId.orEmpty() },
                it.memoryContext?.personaKey ?: MEMORY_UNKNOWN_PERSONA,
                it.summary,
                it.createdAt,
                it.updatedAt,
                originalId = it.id,
                summary = MemoryImportedSummaryMetadata(
                    it.conversationId, it.assistantId, it.id, it.startMessageId, it.endMessageId,
                    it.startCreatedAt, it.endCreatedAt, it.messageCount, it.createdAt, it.updatedAt,
                    it.sourceSnapshot, it.completionStatus, it.memoryContext,
                ),
            )
        }
        return original.copy(
            spaces = spaces, records = records, recordRevisions = revisions, sources = sources,
            recordSources = links, origins = origins
        ).also(MemoryArchiveTransferValidation::validate)
    }

    fun sourceGroups(data: MemoryArchiveTransferData): List<MemoryImportSourceGroup> =
        data.spaces.flatMap { space ->
            val personas = buildSet {
                data.records.filter { it.spaceId == space.id }.forEach { add(it.personaKey) }
                data.sources.filter { it.spaceId == space.id }.forEach { add(it.personaKey) }
                data.residents.filter { it.spaceId == space.id }.forEach { add(it.personaKey) }
                data.states.filter { it.spaceId == space.id }.forEach { add(it.personaKey) }
                data.events.filter { it.spaceId == space.id }.forEach { add(it.personaKey) }
            }
            personas.sorted().map { persona ->
                MemoryImportSourceGroup(
                    groupKey(space.id, persona),
                    space.id,
                    space.characterId,
                    persona,
                    space.purpose,
                    data.records.count { it.spaceId == space.id && it.personaKey == persona } +
                            data.events.count { it.spaceId == space.id && it.personaKey == persona } +
                            data.residents.count { it.spaceId == space.id && it.personaKey == persona } +
                            data.states.count { it.spaceId == space.id && it.personaKey == persona },
                )
            }
        }

    fun groupKey(spaceId: String, personaKey: String): String =
        MemoryArchiveIdentity.id("transfer-group", spaceId, personaKey)
}
