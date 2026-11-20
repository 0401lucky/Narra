package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryArchiveInvalidation
import com.example.myapplication.data.local.memory.MemoryProcessingControlEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.data.local.memory.MemorySourceStorage
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import java.util.UUID

/** 档案管理的读取/事务；不会将旧绑定重新激活，也不发起模型请求。 */
internal class MemoryArchiveManagementStore(
    private val database: ChatDatabase,
    private val archive: MemoryArchiveRepository,
    private val validContext: suspend (MemoryRuntimeContext) -> Boolean,
    private val receipt: suspend (String) -> MemoryRecallReceipt?,
    private val now: () -> Long,
) {
    private val dao = database.memoryArchiveDao()
    private val records = MemoryRecordStorage(database)
    private val invalidation =
        MemoryArchiveInvalidation(dao, database.memoryDao(), database.memoryKnowledgeDao())

    suspend fun personas(context: MemoryRuntimeContext): List<MemoryArchivePersona> =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction emptyList()
            dao.listArchivePersonaKeys(context.spaceId, context.characterId)
                .filter(String::isNotBlank)
                .ifEmpty { listOf(MEMORY_UNKNOWN_PERSONA) }.map { key ->
                    val label = when {
                        key == MEMORY_UNKNOWN_PERSONA -> "未指定或历史未知身份"
                        key.startsWith("mask:") -> "扮演身份 ${key.removePrefix("mask:")}"
                        key.startsWith("override:") -> "自定义身份 ${
                            key.removePrefix("override:").take(8)
                        }"

                        else -> "历史身份 $key"
                    }
                    MemoryArchivePersona(key, label)
                }
        }

    suspend fun conversations(context: MemoryRuntimeContext): List<MemoryArchiveConversation> =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction emptyList()
            val sources = dao.listSpaceSources(context.spaceId)
                .filter { it.characterId == context.characterId }
            dao.listSpaceBindings(context.spaceId).filter { it.characterId == context.characterId }
                .map { binding ->
                    val conversation =
                        database.conversationDao().getConversation(binding.conversationId)
                    val messages = database.conversationDao().listMessages(binding.conversationId)
                    val hasUnknown =
                        messages.any { message -> sources.none { it.messageId == message.id } }
                    val personas = (sources.filter { it.conversationId == binding.conversationId }
                        .map { it.personaKey } + binding.personaKey +
                            if (hasUnknown) listOf(MEMORY_UNKNOWN_PERSONA) else emptyList())
                        .filter(String::isNotBlank).distinct()
                    MemoryArchiveConversation(
                        binding.conversationId,
                        conversation?.title ?: "原对话已不存在",
                        binding.scenarioId,
                        binding.runId,
                        binding.archived,
                        conversation != null,
                        messages.size,
                        personas,
                    )
                }
        }

    suspend fun messages(
        context: MemoryRuntimeContext, conversationId: String, limit: Int, offset: Int,
    ): MemoryArchivePage<MemoryArchiveMessage> = database.withTransaction {
        val safeLimit = limit.coerceIn(1, 1000)
        val safeOffset = offset.coerceAtLeast(0)
        if (!validContext(context) || !ownsConversation(context, conversationId)) {
            return@withTransaction MemoryArchivePage(emptyList(), 0, safeOffset, safeLimit)
        }
        val sources = dao.listConversationSources(conversationId).filter {
            it.spaceId == context.spaceId && it.characterId == context.characterId
        }.groupBy { it.messageId }
        val entities = database.conversationDao().listMessages(conversationId)
        val messages = toSummarySourceMessages(entities)
        check(messages.size == entities.size) { "原对话内容无法读取" }
        val visible = messages.mapNotNull { message ->
            val versions = sources[message.id].orEmpty()
            val source =
                versions.maxWithOrNull(compareBy<MemorySourceEvidenceEntity> { it.messageRevision }.thenBy { it.id })
            val persona = source?.personaKey ?: MEMORY_UNKNOWN_PERSONA
            if (persona != context.personaKey) null else MemoryArchiveMessage(
                message,
                persona,
                source?.toDomain()
            )
        }
        MemoryArchivePage(
            visible.drop(safeOffset).take(safeLimit),
            visible.size,
            safeOffset,
            safeLimit
        )
    }

    suspend fun latestAssistant(
        context: MemoryRuntimeContext,
        conversationId: String
    ): MemoryArchiveMessage? = database.withTransaction {
        if (!validContext(context) || !ownsConversation(
                context,
                conversationId
            )
        ) return@withTransaction null
        val entity = database.conversationDao().getLatestAssistantMessage(conversationId)
            ?: return@withTransaction null
        val message =
            toSummarySourceMessages(listOf(entity)).singleOrNull() ?: error("本轮消息无法读取")
        val source = dao.listMessageSources(conversationId, entity.id).filter {
            it.spaceId == context.spaceId && it.characterId == context.characterId
        }
            .maxWithOrNull(compareBy<MemorySourceEvidenceEntity> { it.messageRevision }.thenBy { it.id })
        MemoryArchiveMessage(
            message,
            source?.personaKey ?: MEMORY_UNKNOWN_PERSONA,
            source?.toDomain()
        )
    }

    suspend fun query(
        context: MemoryRuntimeContext,
        query: MemoryArchiveQuery
    ): MemoryArchivePage<MemoryArchiveItem> =
        database.withTransaction {
            val limit = query.limit.coerceIn(1, 200)
            val offset = query.offset.coerceAtLeast(0)
            if (!validContext(context)) return@withTransaction MemoryArchivePage(
                emptyList(),
                0,
                offset,
                limit
            )
            val search = query.search.trim()
            val status =
                if (query.status == MemoryArchiveStatusFilter.ALL) "" else query.status.name
            if (query.tab == MemoryArchiveTab.RECORDS) {
                val items = dao.queryManagementRecords(
                    context.spaceId, context.characterId, context.personaKey,
                    query.category?.name.orEmpty(), status, search, limit, offset,
                ).map { MemoryArchiveItem.Record(it.toDomain()) }
                val count = dao.countManagementRecords(
                    context.spaceId, context.characterId, context.personaKey,
                    query.category?.name.orEmpty(), status, search,
                )
                return@withTransaction MemoryArchivePage(items, count, offset, limit)
            }
            val items: List<MemoryArchiveItem> = when (query.tab) {
                MemoryArchiveTab.EVENTS -> archive.listEvents(context).filter { event ->
                    val statusMatches = if (query.status == MemoryArchiveStatusFilter.STALE) {
                        event.summaryStatus == MemoryEventSummaryStatus.STALE
                    } else matchesStatus(event.status.name, query.status)
                    statusMatches &&
                            "${event.title}\n${event.summary}\n${event.currentState}".contains(
                                search,
                                ignoreCase = true
                            ) &&
                            (query.category == null || archive.getEventDetails(
                                context,
                                event.id
                            )?.members.orEmpty()
                                .any { it.record.category == query.category })
                }.map { MemoryArchiveItem.Event(it) }

                MemoryArchiveTab.RESIDENTS -> dao.listResidentEntries(context.spaceId).filter {
                    it.characterId == context.characterId && it.personaKey == context.personaKey && it.status != "DELETED" &&
                            MemoryResidentCategory.entries.any { category -> category.name == it.category }
                }.map { it.toDomain() }.filter {
                    (query.residentCategory == null || it.category == query.residentCategory) &&
                            matchesStatus(it.status, query.status) && it.content.contains(
                        search,
                        ignoreCase = true
                    )
                }
                    .sortedWith(compareByDescending<MemoryResidentEntry> { it.updatedAt }.thenBy { it.id })
                    .map { MemoryArchiveItem.Resident(it) }

                MemoryArchiveTab.STATES -> archive.listCurrentStates(context).filter {
                    (query.stateKey == null || it.key == query.stateKey) && matchesStatus(
                        it.status,
                        query.status
                    ) &&
                            it.content.contains(search, ignoreCase = true)
                }.map { MemoryArchiveItem.State(it) }

                MemoryArchiveTab.RECORDS -> error("经历应在数据库分页")
            }
            MemoryArchivePage(items.drop(offset).take(limit), items.size, offset, limit)
        }

    suspend fun revision(
        context: MemoryRuntimeContext,
        id: String,
        revision: Long
    ): MemoryRecordRevisionDetails? =
        database.withTransaction {
            val record = ownedRecord(context, id) ?: return@withTransaction null
            val saved = dao.listRevisions(id).firstOrNull { it.revision == revision }
                ?: return@withTransaction null
            val refs = dao.listRecordSources(id, revision)
                .map { MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd) }
            val sources =
                refs.map { it.sourceId }.distinct().mapNotNull { dao.getSource(it)?.toDomain() }
            MemoryRecordRevisionDetails(
                record.toDomain(),
                MemoryRecordRevision(
                    id,
                    saved.revision,
                    saved.content,
                    MemoryChangeOrigin.valueOf(saved.origin),
                    saved.createdAt
                ),
                sources,
                refs,
            )
        }

    suspend fun archivedReceipt(
        context: MemoryRuntimeContext, conversationId: String, messageId: String, requestId: String,
    ): MemoryRecallReceipt? = database.withTransaction {
        if (context.conversationId.isNotBlank() || requestId.isBlank() || !validContext(context) || !ownsConversation(
                context,
                conversationId
            )
        ) return@withTransaction null
        val message =
            database.conversationDao().getMessage(messageId) ?: return@withTransaction null
        if (message.conversationId != conversationId || message.role != "ASSISTANT" || message.memoryRecallRequestId != requestId) return@withTransaction null
        val saved = receipt(requestId) ?: return@withTransaction null
        val binding =
            dao.getBinding(conversationId, context.characterId) ?: return@withTransaction null
        saved.takeIf {
            it.anchorMessageId == messageId && it.context.spaceId == context.spaceId &&
                    it.context.characterId == context.characterId && it.context.personaKey == context.personaKey &&
                    it.context.conversationId == conversationId && it.context.runId == binding.runId &&
                    it.context.scenarioId == binding.scenarioId
        }
    }

    suspend fun add(
        context: MemoryRuntimeContext,
        draft: ManualMemoryRecordDraft
    ): MemoryWriteResult = database.withTransaction {
        if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        if (draft.content.isBlank() || context.personaKey.isBlank()) return@withTransaction conflict(
            MemoryConflict.INVALID_INPUT
        )
        val timestamp = now()
        val content = draft.content.trim()
        val sourceId = UUID.randomUUID().toString()
        val record = MemoryRecordEntity(
            UUID.randomUUID().toString(),
            context.spaceId,
            context.characterId,
            draft.kind.name,
            draft.category.name,
            content,
            1L,
            "ACTIVE",
            draft.importance.coerceIn(0, 100),
            draft.pinned,
            draft.pinnedUntil,
            draft.occurredAt,
            if (draft.occurredAt == null) "unknown" else "exact",
            context.personaKey,
            timestamp,
            timestamp,
            0L,
            null,
            null,
            "",
            "",
            "",
        )
        dao.insertSource(
            MemorySourceEvidenceEntity(
                sourceId,
                context.spaceId,
                context.characterId,
                "MANUAL",
                "",
                null,
                1L,
                "user",
                "USER",
                context.personaKey,
                AppJson.gson.toJson(listOf(context.characterId)),
                content,
                content.length,
                "AVAILABLE",
                timestamp,
                timestamp,
                MemoryArchiveIdentity.fingerprint(content),
            )
        )
        dao.upsertRecord(record)
        records.writeRevision(
            record,
            MemoryChangeOrigin.MANUAL,
            listOf(MemorySourceRef(sourceId, 1L, 0, content.length)),
            timestamp
        )
        MemoryWriteResult.Committed(listOf(record.id))
    }

    suspend fun metadata(
        context: MemoryRuntimeContext, id: String, expectedRevision: Long,
        transform: (MemoryRecordEntity) -> MemoryRecordEntity,
    ): MemoryWriteResult = database.withTransaction {
        if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        val previous = ownedRecord(context, id)?.takeIf { it.revision == expectedRevision }
            ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
        val changed = transform(previous)
        if (changed == previous) return@withTransaction MemoryWriteResult.Committed(
            listOf(id),
            alreadyCommitted = true
        )
        val refs = dao.listRecordSources(id, previous.revision)
            .map { MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd) }
        val timestamp = now()
        invalidation.record(previous, deleted = false, timestamp)
        val next = changed.copy(revision = previous.revision + 1, updatedAt = timestamp)
        dao.upsertRecord(next)
        records.writeRevision(next, MemoryChangeOrigin.MANUAL, refs, timestamp)
        MemoryWriteResult.Committed(listOf(id))
    }

    suspend fun editLegacy(entry: MemoryEntry): MemoryWriteResult = database.withTransaction {
        val previous = dao.getRecord(entry.id)
            ?.takeIf { it.legacyScopeType.isNotBlank() && it.status != "DELETED" }
            ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
        if (entry.archiveRevision == null || previous.revision != entry.archiveRevision) return@withTransaction conflict(
            MemoryConflict.RECORD_CHANGED
        )
        val projected = previous.legacyEntry()
        if (entry.scopeType != projected.scopeType || entry.scopeId != projected.scopeId ||
            entry.characterId != projected.characterId || entry.sourceMessageId != projected.sourceMessageId || entry.content.isBlank()
        ) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
        val context = archive.getManagementContext(
            previous.characterId,
            previous.spaceId,
            previous.personaKey
        )
            ?: return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        val content = entry.content.trim()
        val timestamp = now()
        val changed = previous.copy(
            content = content,
            importance = entry.importance.coerceIn(0, 100),
            pinned = entry.pinned
        )
        if (changed == previous) return@withTransaction MemoryWriteResult.Committed(
            listOf(entry.id),
            alreadyCommitted = true
        )
        val refs = if (previous.content == content) {
            dao.listRecordSources(previous.id, previous.revision)
                .map { MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd) }
        } else {
            val sourceId = UUID.randomUUID().toString()
            dao.insertSource(
                MemorySourceEvidenceEntity(
                    sourceId,
                    context.spaceId,
                    context.characterId,
                    "MANUAL",
                    "",
                    null,
                    1L,
                    "user",
                    "USER",
                    context.personaKey,
                    AppJson.gson.toJson(listOf(context.characterId)),
                    content,
                    content.length,
                    "AVAILABLE",
                    timestamp,
                    timestamp,
                    MemoryArchiveIdentity.fingerprint(content),
                )
            )
            listOf(MemorySourceRef(sourceId, 1L, 0, content.length))
        }
        invalidation.record(previous, deleted = false, timestamp)
        val next = changed.copy(
            revision = previous.revision + 1, updatedAt = timestamp,
            status = if (previous.content != content) "ACTIVE" else previous.status
        )
        dao.upsertRecord(next)
        records.writeRevision(next, MemoryChangeOrigin.MANUAL, refs, timestamp)
        MemoryWriteResult.Committed(listOf(entry.id))
    }

    suspend fun archiveSpace(context: MemoryRuntimeContext, archived: Boolean): MemoryWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            val space = requireNotNull(dao.getSpace(context.spaceId))
            val status = if (archived) "ARCHIVED" else "ACTIVE"
            if (space.status == status) return@withTransaction MemoryWriteResult.Committed(
                emptyList(),
                alreadyCommitted = true
            )
            val timestamp = now()
            dao.upsertSpace(
                space.copy(
                    status = status,
                    generation = space.generation + 1,
                    updatedAt = timestamp
                )
            )
            dao.archiveSpaceBindings(space.id)
            dao.invalidateSpaceJobs(space.id, "SPACE_CHANGED", timestamp)
            dao.invalidateSpaceProposals(space.id, timestamp)
            MemoryWriteResult.Committed(emptyList())
        }

    suspend fun control(context: MemoryRuntimeContext, jobId: String): MemoryProcessingControl? =
        database.withTransaction {
            if (!validControl(context, jobId)) return@withTransaction null
            dao.getProcessingControl(context.spaceId, controlJobId(jobId))?.domain()
                ?.copy(jobId = jobId) ?: MemoryProcessingControl(context.spaceId, jobId)
        }

    suspend fun pause(
        context: MemoryRuntimeContext,
        version: Long,
        paused: Boolean,
        jobId: String
    ): MemoryProcessingControlResult =
        database.withTransaction {
            if (!validControl(
                    context,
                    jobId
                )
            ) return@withTransaction MemoryProcessingControlResult.Conflict(MemoryConflict.SPACE_CHANGED)
            val controlId = controlJobId(jobId)
            val previous = dao.getProcessingControl(context.spaceId, controlId)
            if ((previous?.controlVersion
                    ?: 0L) != version
            ) return@withTransaction MemoryProcessingControlResult.Conflict(MemoryConflict.JOB_CHANGED)
            val next = MemoryProcessingControlEntity(
                context.spaceId,
                controlId,
                paused,
                version + 1,
                now()
            )
            dao.upsertProcessingControl(next)
            MemoryProcessingControlResult.Updated(next.domain().copy(jobId = jobId))
        }

    suspend fun inspect(
        context: MemoryRuntimeContext,
        selection: MemoryMessageSelection
    ): MemorySelectionInspection = database.withTransaction {
        fun invalid(code: String) = MemorySelectionInspection(
            selection,
            emptyList(),
            selection.messageIds,
            emptyList(),
            code
        )
        if (!validContext(context) || !ownsConversation(
                context,
                selection.conversationId
            )
        ) return@withTransaction invalid("CONTEXT_UNAVAILABLE")
        if (selection.messageIds.isEmpty() || selection.messageIds.size > 1000 || selection.messageIds.distinct().size != selection.messageIds.size) return@withTransaction invalid(
            "INVALID_SELECTION"
        )
        val available = selection.messageIds.filter { id ->
            database.conversationDao().getMessage(id)?.let {
                it.conversationId == selection.conversationId && it.status == "COMPLETED" && !it.isRecalled &&
                        MemorySourceStorage.contentForSource(
                            it, 2,
                            com.example.myapplication.data.local.companion.CompanionSourceStorage(
                                database
                            ).eligibility(it)
                        ).isNotBlank()
            } == true
        }
        val jobs = selection.messageIds.chunked(200).flatMap {
            dao.listSelectionJobs(
                context.spaceId,
                context.characterId,
                selection.conversationId,
                it
            )
        }.distinctBy { it.id }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val overlaps = jobs.map { job ->
            val saved = requireNotNull(archive.getJob(job.id))
            val ids = saved.sources.mapNotNull { dao.getSource(it.sourceId)?.messageId }.distinct()
                .filter { it in selection.messageIds }
            MemorySelectionOverlap(
                saved.id,
                saved.purpose,
                saved.status,
                saved.requestState,
                saved.artifact.isNotBlank(),
                saved.context == context.copy(
                    conversationId = "",
                    personaKey = saved.context.personaKey
                ),
                ids,
            )
        }
        MemorySelectionInspection(
            selection,
            available,
            selection.messageIds - available.toSet(),
            overlaps
        )
    }

    private suspend fun ownsConversation(context: MemoryRuntimeContext, id: String): Boolean =
        dao.getBinding(id, context.characterId)?.spaceId == context.spaceId

    private suspend fun ownedRecord(
        context: MemoryRuntimeContext,
        id: String
    ): MemoryRecordEntity? {
        if (!validContext(context)) return null
        return dao.getRecord(id)?.takeIf {
            it.spaceId == context.spaceId && it.characterId == context.characterId &&
                    it.personaKey == context.personaKey && it.status != "DELETED"
        }
    }

    private suspend fun validControl(context: MemoryRuntimeContext, jobId: String): Boolean {
        if (!validContext(context)) return false
        if (jobId.isBlank()) return true
        val job = dao.getJob(jobId) ?: return false
        return job.spaceId == context.spaceId && job.characterId == context.characterId && job.personaKey == context.personaKey
    }

    private fun matchesStatus(status: String, filter: MemoryArchiveStatusFilter): Boolean =
        when (filter) {
            MemoryArchiveStatusFilter.ALL -> true
            MemoryArchiveStatusFilter.ACTIVE -> status in setOf("ACTIVE", "READY")
            MemoryArchiveStatusFilter.ENDED -> status in setOf("ENDED", "EXPIRED")
            else -> status == filter.name
        }

    private suspend fun controlJobId(jobId: String): String =
        if (jobId.isBlank()) "" else dao.getJob(jobId)?.controlJobId?.ifBlank { jobId } ?: jobId

    private fun MemoryProcessingControlEntity.domain() =
        MemoryProcessingControl(spaceId, jobId, userPaused, controlVersion, updatedAt)

    private fun conflict(reason: MemoryConflict) = MemoryWriteResult.Conflict(reason)
}
