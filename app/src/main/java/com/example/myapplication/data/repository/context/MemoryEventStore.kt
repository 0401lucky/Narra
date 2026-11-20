package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.conversation.MemoryEventSummaryBatches
import com.example.myapplication.conversation.MemoryEventSummaryFormatter
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryEventBoxEntity
import com.example.myapplication.data.local.memory.MemoryEventMemberEntity
import com.example.myapplication.data.local.memory.MemoryJobSourceEntity
import com.example.myapplication.data.local.memory.MemoryProcessingJobEntity
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import java.util.UUID

/** 网络留在协调器；成员、请求证据与最终覆盖统一使用主库事务。 */
internal class MemoryEventStore(
    private val database: ChatDatabase,
    private val validContext: suspend (MemoryRuntimeContext) -> Boolean,
    private val validRuntime: suspend (MemoryRuntimeContext) -> Boolean,
    private val characterAvailable: suspend (String) -> Boolean,
    private val validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
    private val jobDomain: suspend (MemoryProcessingJobEntity) -> MemoryProcessingJob,
    private val now: () -> Long,
) {
    private val dao = database.memoryArchiveDao()
    private val gson = AppJson.gson

    suspend fun list(context: MemoryRuntimeContext): List<MemoryEventBox> =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction emptyList()
            dao.listEventBoxes(context.spaceId).filter { event ->
                owns(context, event) && event.status != "DELETED" &&
                        dao.listEventMembers(event.id).let { members ->
                            members.isNotEmpty() && members.all { member(context, it) != null }
                        }
            }.map { it.toDomain() }
        }

    suspend fun details(context: MemoryRuntimeContext, eventId: String): MemoryEventDetails? =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction null
            val event =
                dao.getEventBox(eventId)?.takeIf { owns(context, it) && it.status != "DELETED" }
                    ?: return@withTransaction null
            val members = mutableListOf<MemoryEventMember>()
            val rows = dao.listEventMembers(eventId)
            if (rows.isEmpty()) return@withTransaction null
            rows.forEach { row ->
                members += member(context, row) ?: return@withTransaction null
            }
            MemoryEventDetails(event.toDomain(), members)
        }

    suspend fun conclusions(context: MemoryRuntimeContext): List<MemoryEventConclusion> =
        database.withTransaction {
            if (!validRuntime(context)) return@withTransaction emptyList()
            list(context).mapNotNull { event ->
                val detail = details(context, event.id) ?: return@mapNotNull null
                if (!ready(detail) || detail.members.any {
                        !legal(
                            context,
                            it
                        )
                    }) return@mapNotNull null
                val covered = detail.members.filter { it.coveredRevision != null }
                MemoryEventConclusion(
                    event,
                    covered.map {
                        MemoryRecordVersion(
                            it.record.id,
                            requireNotNull(it.coveredRevision)
                        )
                    },
                    covered.flatMap { it.sourceRefs }.distinct()
                )
            }
        }

    suspend fun consume(context: MemoryRuntimeContext): MemoryEventRelationProgress =
        database.withTransaction {
            if (!canOperate(context)) return@withTransaction MemoryEventRelationProgress()
            if (dao.isProcessingPaused(
                    context.spaceId,
                    ""
                )
            ) return@withTransaction MemoryEventRelationProgress()
            var applied = 0
            var ignored = 0
            val eventIds = linkedSetOf<String>()
            dao.listPendingRelations(context.spaceId).forEach { intent ->
                // 同一日常空间可有多种身份，不消费另一身份尚待处理的意图。
                val fromRecord = dao.getRecord(intent.fromRecordId) ?: return@forEach
                if (intent.characterId != context.characterId || fromRecord.personaKey != context.personaKey) return@forEach
                val originJobs =
                    listOfNotNull(fromRecord.jobId, dao.getRecord(intent.targetRecordId)?.jobId)
                if (originJobs.any { dao.isProcessingPaused(context.spaceId, it) }) return@forEach
                val result =
                    if (intent.label != "same_event") conflict(MemoryEventConflict.INVALID_INPUT) else link(
                        context,
                        MemoryRecordVersion(intent.fromRecordId, intent.fromRevision),
                        MemoryRecordVersion(intent.targetRecordId, intent.targetRevision),
                        intent.eventName,
                    )
                if (result is MemoryEventMutationResult.Updated) {
                    dao.updateRelationStatus(intent.id, "APPLIED")
                    if (result.alreadyApplied) ignored++ else applied++
                    eventIds += result.eventId
                } else {
                    dao.updateRelationStatus(intent.id, "REJECTED")
                    ignored++
                }
            }
            MemoryEventRelationProgress(applied, ignored, eventIds.toList())
        }

    suspend fun link(
        context: MemoryRuntimeContext,
        from: MemoryRecordVersion,
        target: MemoryRecordVersion,
        title: String
    ): MemoryEventMutationResult = database.withTransaction {
        if (!canOperate(context)) return@withTransaction conflict(MemoryEventConflict.SCOPE_CHANGED)
        if (from.recordId.isBlank() || target.recordId.isBlank() || from.recordId == target.recordId || title.length > 200) return@withTransaction conflict(
            MemoryEventConflict.INVALID_INPUT
        )
        val fromMember =
            member(context, MemoryEventMemberEntity("", from.recordId, null, "LIVE", now()))
        val targetMember =
            member(context, MemoryEventMemberEntity("", target.recordId, null, "LIVE", now()))
        if (fromMember?.record?.revision != from.revision || targetMember?.record?.revision != target.revision) return@withTransaction conflict(
            MemoryEventConflict.RECORD_CHANGED
        )
        if (!legal(context, fromMember) || !legal(
                context,
                targetMember
            )
        ) return@withTransaction conflict(MemoryEventConflict.SOURCE_CHANGED)
        val fromEvents = dao.listRecordEvents(from.recordId)
        val targetEvents = dao.listRecordEvents(target.recordId)
        if ((fromEvents + targetEvents).any {
                !owns(
                    context,
                    it
                ) || it.status != "ACTIVE"
            }) return@withTransaction conflict(MemoryEventConflict.EVENT_CHANGED)
        val existing = (fromEvents + targetEvents).distinctBy { it.id }
        // 已独立成盒的两段历史不能被一次自动意图静默重写；可先显式解除错误关联。
        if (existing.size > 1) return@withTransaction conflict(MemoryEventConflict.DIFFERENT_EVENTS)
        existing.singleOrNull()?.let { existingEvent ->
            val detail = details(context, existingEvent.id) ?: return@withTransaction conflict(
                MemoryEventConflict.SCOPE_CHANGED
            )
            if (detail.members.any { !legal(context, it) }) return@withTransaction conflict(
                MemoryEventConflict.SOURCE_CHANGED
            )
        }
        val timestamp = now()
        val event = existing.singleOrNull() ?: MemoryEventBoxEntity(
            id = UUID.randomUUID().toString(),
            spaceId = context.spaceId,
            characterId = context.characterId,
            title = title.trim().ifBlank { "未命名事件" },
            summary = "",
            summaryRevision = 0L,
            summaryStatus = "EMPTY",
            status = "ACTIVE",
            currentState = "",
            generation = 1L,
            createdAt = timestamp,
            updatedAt = timestamp,
            personaKey = context.personaKey,
        )
        val currentMembers = dao.listEventMembers(event.id).map { it.recordId }.toSet()
        if (from.recordId in currentMembers && target.recordId in currentMembers) return@withTransaction MemoryEventMutationResult.Updated(
            event.id,
            event.generation,
            true
        )
        dao.upsertEventBox(event.copy(updatedAt = timestamp))
        listOf(from, target).filter { it.recordId !in currentMembers }.forEach {
            dao.upsertEventMember(
                MemoryEventMemberEntity(
                    event.id,
                    it.recordId,
                    null,
                    "LIVE",
                    timestamp
                )
            )
        }
        // 新成员不增加 generation；在飞快照只归档自身已覆盖成员，新进展保留 LIVE。
        MemoryEventMutationResult.Updated(event.id, event.generation)
    }

    suspend fun unlink(
        context: MemoryRuntimeContext,
        eventId: String,
        recordId: String,
        expectedGeneration: Long
    ): MemoryEventMutationResult = database.withTransaction {
        if (!canOperate(context)) return@withTransaction conflict(MemoryEventConflict.SCOPE_CHANGED)
        val event = dao.getEventBox(eventId)?.takeIf {
            owns(
                context,
                it
            ) && it.status != "DELETED" && it.generation == expectedGeneration
        }
            ?: return@withTransaction conflict(MemoryEventConflict.EVENT_CHANGED)
        val members = dao.listEventMembers(eventId)
        if (members.none { it.recordId == recordId }) return@withTransaction MemoryEventMutationResult.Updated(
            eventId,
            event.generation,
            true
        )
        val timestamp = now()
        dao.invalidateEventJobs(eventId, "EVENT_CHANGED", timestamp)
        dao.deleteEventMember(eventId, recordId)
        members.filter { it.recordId != recordId }
            .forEach { dao.upsertEventMember(it.copy(coveredRevision = null, status = "LIVE")) }
        dao.upsertEventBox(
            event.copy(
                summary = "",
                currentState = "",
                summaryStatus = "STALE",
                generation = event.generation + 1,
                updatedAt = timestamp
            )
        )
        MemoryEventMutationResult.Updated(eventId, event.generation + 1)
    }

    suspend fun archive(
        context: MemoryRuntimeContext,
        eventId: String,
        expectedGeneration: Long,
        archived: Boolean
    ): MemoryEventMutationResult = database.withTransaction {
        if (!canOperate(context)) return@withTransaction conflict(MemoryEventConflict.SCOPE_CHANGED)
        val event = dao.getEventBox(eventId)?.takeIf {
            owns(
                context,
                it
            ) && it.status != "DELETED" && it.generation == expectedGeneration
        }
            ?: return@withTransaction conflict(MemoryEventConflict.EVENT_CHANGED)
        val status = if (archived) "ARCHIVED" else "ACTIVE"
        if (event.status == status) return@withTransaction MemoryEventMutationResult.Updated(
            eventId,
            event.generation,
            true
        )
        dao.invalidateEventJobs(eventId, "EVENT_ARCHIVED", now())
        dao.upsertEventBox(
            event.copy(
                status = status,
                generation = event.generation + 1,
                updatedAt = now()
            )
        )
        MemoryEventMutationResult.Updated(eventId, event.generation + 1)
    }

    suspend fun plan(
        context: MemoryRuntimeContext,
        eventId: String,
        options: MemoryEventSummaryOptions
    ): MemoryEventPlanResult = database.withTransaction {
        if (dao.isProcessingPaused(context.spaceId, "")) return@withTransaction planConflict(
            MemoryConflict.JOB_CHANGED
        )
        if (!(if (options.manualRebuild) canOperate(context) else validRuntime(context))) return@withTransaction planConflict(
            MemoryConflict.SPACE_CHANGED
        )
        val detail = details(context, eventId)
            ?: return@withTransaction planConflict(MemoryConflict.SPACE_CHANGED)
        val event = requireNotNull(dao.getEventBox(eventId))
        if (!options.manualRebuild && event.status != "ACTIVE") return@withTransaction MemoryEventPlanResult.NotNeeded
        if (options.manualRebuild && (options.expectedGeneration == null || options.expectedGeneration != event.generation)) return@withTransaction planConflict(
            MemoryConflict.RECORD_CHANGED
        )
        if (options.retryUncertain && !options.manualRebuild) return@withTransaction planConflict(
            MemoryConflict.INVALID_INPUT
        )
        val jobs = dao.listEventJobs(eventId)
        if (!options.manualRebuild) {
            jobs.firstOrNull {
                it.status !in setOf(
                    "COMMITTED",
                    "INVALIDATED"
                ) && it.context() == context && current(it)
            }
                ?.let { return@withTransaction MemoryEventPlanResult.Planned(jobDomain(it)) }
        }
        val unresolved =
            jobs.filter { it.status != "COMMITTED" && (it.requestState != "NOT_STARTED" || it.artifact.isNotBlank()) }
        if (unresolved.isNotEmpty() && !options.retryUncertain) return@withTransaction MemoryEventPlanResult.NeedsReview(
            unresolved.map { it.id })
        if (detail.members.isEmpty()) return@withTransaction MemoryEventPlanResult.NotNeeded
        if (detail.members.any { !legal(context, it) }) return@withTransaction planConflict(
            MemoryConflict.SOURCE_CHANGED
        )
        val incremental = !options.manualRebuild && ready(detail)
        val members =
            if (incremental) detail.members.filter { it.coveredRevision == null } else detail.members
        if (members.isEmpty()) return@withTransaction MemoryEventPlanResult.NotNeeded
        val policy = options.policy
        require(policy.newMemberThreshold > 0 && policy.newContentChars > 0) { "事件整理阈值无效" }
        val changedMembers =
            detail.members.filter { it.coveredRevision == null || it.coveredRevision != it.record.revision || it.status == MemoryEventMemberStatus.LIVE }
        if (!options.manualRebuild && changedMembers.size < policy.newMemberThreshold && changedMembers.sumOf { it.record.content.length.toLong() } < policy.newContentChars) return@withTransaction MemoryEventPlanResult.NotNeeded
        val nextGeneration = event.generation + if (options.manualRebuild) 1L else 0L
        val snapshot = MemoryEventSummarySnapshot(
            eventId,
            event.title,
            if (incremental) MemoryEventSummaryMode.INCREMENTAL else MemoryEventSummaryMode.FULL,
            nextGeneration,
            event.summaryRevision,
            if (incremental) event.summary else "",
            if (incremental) event.currentState else "",
            if (incremental) detail.members.filter { it.coveredRevision != null }.map {
                MemoryRecordVersion(
                    it.record.id,
                    requireNotNull(it.coveredRevision)
                )
            } else emptyList(),
            members.map { MemoryEventSummaryMember(it.record, it.sourceRefs, it.sources) },
            options.timeZoneId,
            options.characterName,
            options.userName,
        )
        val work = MemoryEventSummaryBatches.plan(snapshot, policy.batchContentChars)
        val json = gson.toJson(work)
        val purpose =
            if (options.manualRebuild) MemoryJobPurpose.EVENT_REBUILD else MemoryJobPurpose.EVENT_SUMMARY
        var key = MemoryArchiveIdentity.id(
            "event-work",
            eventId,
            nextGeneration.toString(),
            MemoryArchiveIdentity.fingerprint(gson.toJson(context)),
            MemoryArchiveIdentity.fingerprint(json)
        )
        while (true) {
            val previous = dao.findJob(context.spaceId, key) ?: break
            if (previous.context() == context && previous.purpose == purpose.name &&
                previous.status !in setOf("COMMITTED", "INVALIDATED") && current(previous)
            ) return@withTransaction MemoryEventPlanResult.Planned(jobDomain(previous))
            key = MemoryArchiveIdentity.id("event-replanned", key, previous.id)
        }
        val sources = detail.members.flatMap { it.sourceRefs }.distinct()
        val targets = detail.members.associate { it.record.id to it.record.revision }
        val timestamp = now()
        if (options.manualRebuild) dao.upsertEventBox(
            event.copy(
                generation = nextGeneration,
                updatedAt = timestamp
            )
        )
        dao.invalidateEventJobs(
            eventId,
            if (options.manualRebuild) "EVENT_REBUILD_REQUESTED" else "EVENT_CHANGED",
            timestamp
        )
        val job = MemoryProcessingJobEntity(
            UUID.randomUUID().toString(),
            context.spaceId,
            context.characterId,
            context.conversationId,
            context.scenarioId,
            context.runId,
            context.personaKey,
            context.generation,
            key,
            gson.toJson(targets),
            "PLANNED",
            "",
            "",
            0L,
            "",
            timestamp,
            timestamp,
            purpose = purpose.name,
            requestJson = json,
            requestState = MemoryRequestState.NOT_STARTED.name,
            eventId = eventId,
        )
        dao.upsertJob(job)
        dao.insertJobSources(sources.map {
            MemoryJobSourceEntity(
                job.id,
                it.sourceId,
                it.sourceRevision,
                it.start,
                it.end
            )
        })
        MemoryEventPlanResult.Planned(jobDomain(job))
    }

    /** 请求前重新核对完整冻结范围；合法新增成员允许存在，但绝不加入旧覆盖。 */
    suspend fun current(job: MemoryProcessingJobEntity): Boolean {
        if (!isEventJob(job) || job.requestJson.isBlank()) return false
        val context = job.context()
        if (!(if (job.purpose == MemoryJobPurpose.EVENT_REBUILD.name) canOperate(context) else validRuntime(
                context
            ))
        ) return false
        val work = try {
            MemoryEventSummaryBatches.readWork(job.requestJson)
        } catch (_: RuntimeException) {
            return false
        }
        val base = work.snapshot
        val detail = details(context, job.eventId) ?: return false
        val event = detail.event
        if (base.eventId != job.eventId || event.generation != base.expectedGeneration || event.summaryRevision != base.summaryRevision ||
            (job.purpose == MemoryJobPurpose.EVENT_SUMMARY.name && event.status != MemoryEventStatus.ACTIVE)
        ) return false
        if (base.mode == MemoryEventSummaryMode.INCREMENTAL && (!ready(detail) || base.previousSummary != event.summary || base.previousState != event.currentState ||
                    base.previousCoverage.toSet() != detail.members.filter { it.coveredRevision != null }
                .map { MemoryRecordVersion(it.record.id, requireNotNull(it.coveredRevision)) }
                .toSet())
        ) return false
        val byId = detail.members.associateBy { it.record.id }
        if (base.members.any { frozen ->
                val current = byId[frozen.record.id]
                current == null || current.record.revision != frozen.record.revision || current.record.content != frozen.record.content ||
                        current.sourceRefs.toSet() != frozen.sourceRefs.toSet() || current.sources != frozen.sources
            }) return false
        val expected = base.members.map {
            MemoryRecordVersion(
                it.record.id,
                it.record.revision
            )
        } + base.previousCoverage
        if (expected.any { byId[it.recordId]?.record?.revision != it.revision }) return false
        val domain = jobDomain(job)
        if (domain.targetRevisions != expected.associate { it.recordId to it.revision } ||
            domain.sources.toSet() != expected.flatMap { byId.getValue(it.recordId).sourceRefs }
                .toSet()) return false
        return detail.members.all { legal(context, it) }
    }

    suspend fun begin(jobId: String, owner: String, batchIndex: Int): Boolean =
        database.withTransaction {
            val job = dao.getJob(jobId) ?: return@withTransaction false
            if (dao.isProcessingPaused(job.spaceId, job.id)) return@withTransaction false
            if (!ownsLease(job, owner) || !current(job)) return@withTransaction false
            val work = MemoryEventSummaryBatches.readWork(job.requestJson)
            val artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
            if (artifact.pendingBatch != null || artifact.completions.size != batchIndex || batchIndex !in work.batchEnds.indices) return@withTransaction false
            // 只有前批明确 COMPLETED 且严格结构完整，下一批才获得新的请求许可。
            val previous = MemoryEventSummaryBatches.outputs(work, artifact)
            MemoryEventSummaryFormatter.format(
                MemoryEventSummaryBatches.batch(
                    work,
                    batchIndex,
                    previous
                )
            )
            if (batchIndex == 0 && job.requestState != MemoryRequestState.NOT_STARTED.name) return@withTransaction false
            dao.upsertJob(
                job.copy(
                    status = "EXTRACTING",
                    artifact = gson.toJson(artifact.copy(pendingBatch = batchIndex)),
                    requestState = "STARTED",
                    failureCode = "REQUEST_STARTED",
                    updatedAt = now()
                )
            )
            true
        }

    suspend fun save(
        jobId: String,
        owner: String,
        batchIndex: Int,
        completion: PromptCompletionResult
    ): Boolean = database.withTransaction {
        val job = dao.getJob(jobId) ?: return@withTransaction false
        if (!ownsLease(job, owner) || !current(job)) return@withTransaction false
        val artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
        if (artifact.completions.getOrNull(batchIndex) == completion) return@withTransaction true
        if (artifact.pendingBatch != batchIndex || artifact.completions.size != batchIndex) return@withTransaction false
        // 原响应先落盘。即使 UNKNOWN/非法 JSON，也不会因此自动重发同一批。
        dao.upsertJob(
            job.copy(
                status = "EXTRACTED",
                artifact = gson.toJson(
                    artifact.copy(
                        completions = artifact.completions + completion,
                        pendingBatch = null
                    )
                ),
                failureCode = "",
                updatedAt = now()
            )
        )
        true
    }

    suspend fun commit(jobId: String, owner: String): MemoryWriteResult = database.withTransaction {
        val job = dao.getJob(jobId) ?: return@withTransaction MemoryWriteResult.Conflict(
            MemoryConflict.JOB_CHANGED
        )
        if (!isEventJob(job)) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.INVALID_INPUT)
        if (job.status == "COMMITTED") return@withTransaction MemoryWriteResult.Committed(
            emptyList(),
            alreadyCommitted = true
        )
        if (dao.isProcessingPaused(
                job.spaceId,
                job.id
            )
        ) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.JOB_CHANGED)
        if (!ownsLease(
                job,
                owner
            ) || job.status != "EXTRACTED"
        ) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.JOB_CHANGED)
        if (!current(job)) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.RECORD_CHANGED)
        val work = MemoryEventSummaryBatches.readWork(job.requestJson)
        val artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
        if (artifact.pendingBatch != null || artifact.completions.size != work.batchEnds.size) return@withTransaction MemoryWriteResult.Conflict(
            MemoryConflict.INVALID_INPUT
        )
        val outputs = try {
            MemoryEventSummaryBatches.outputs(work, artifact)
        } catch (_: IllegalArgumentException) {
            return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.INVALID_INPUT)
        }
        val final = outputs.last()
        val coverage =
            (work.snapshot.previousCoverage + outputs.flatMap { it.coveredRecords }).associate { it.recordId to it.revision }
        val event = requireNotNull(dao.getEventBox(job.eventId))
        dao.upsertEventBox(
            event.copy(
                summary = final.summary,
                currentState = final.currentState,
                summaryRevision = event.summaryRevision + 1,
                summaryStatus = "READY",
                updatedAt = now()
            )
        )
        dao.listEventMembers(event.id).forEach { member ->
            coverage[member.recordId]?.let { revision ->
                dao.upsertEventMember(
                    member.copy(
                        coveredRevision = revision,
                        status = "ARCHIVED"
                    )
                )
            }
        }
        dao.upsertJob(
            job.copy(
                status = "COMMITTED",
                leaseOwner = "",
                leaseUntil = 0L,
                failureCode = "",
                updatedAt = now()
            )
        )
        MemoryWriteResult.Committed(emptyList())
    }

    private suspend fun member(
        context: MemoryRuntimeContext,
        row: MemoryEventMemberEntity
    ): MemoryEventMember? {
        val record = dao.getRecord(row.recordId)
            ?.takeIf { it.spaceId == context.spaceId && it.characterId == context.characterId && it.personaKey == context.personaKey }
            ?: return null
        val refs = dao.listRecordSources(record.id, record.revision)
            .map { MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd) }
        val sources = refs.map { it.sourceId }.distinct().mapNotNull { dao.getSource(it) }
        if (sources.any { it.spaceId != context.spaceId || it.characterId != context.characterId || it.personaKey != context.personaKey }) return null
        return MemoryEventMember(
            record.toDomain(), row.coveredRevision,
            if (row.status == "ARCHIVED") MemoryEventMemberStatus.ARCHIVED else MemoryEventMemberStatus.LIVE,
            row.addedAt, refs, sources.map { it.toDomain() })
    }

    private suspend fun legal(context: MemoryRuntimeContext, member: MemoryEventMember): Boolean =
        member.record.status in setOf(
            MemoryRecordStatus.ACTIVE,
            MemoryRecordStatus.ARCHIVED
        ) && member.record.content.isNotBlank() &&
                member.sourceRefs.isNotEmpty() && member.sourceRefs.all { validSource(context, it) }

    private fun ready(detail: MemoryEventDetails): Boolean =
        detail.event.summaryStatus == MemoryEventSummaryStatus.READY &&
                detail.event.summary.isNotBlank() && detail.event.currentState.isNotBlank() && detail.event.summaryRevision > 0 &&
                detail.members.any { it.coveredRevision != null } && detail.members.all { it.coveredRevision == null || it.coveredRevision == it.record.revision }

    private suspend fun canOperate(context: MemoryRuntimeContext) =
        validContext(context) && characterAvailable(context.characterId)

    private fun owns(context: MemoryRuntimeContext, event: MemoryEventBoxEntity) =
        event.spaceId == context.spaceId && event.characterId == context.characterId && event.personaKey == context.personaKey

    private fun ownsLease(job: MemoryProcessingJobEntity, owner: String) =
        owner.isNotBlank() && job.leaseOwner == owner && job.leaseUntil > now() && job.status in setOf(
            "EXTRACTING",
            "EXTRACTED"
        )

    private fun conflict(reason: MemoryEventConflict) = MemoryEventMutationResult.Conflict(reason)
    private fun planConflict(reason: MemoryConflict) = MemoryEventPlanResult.Conflict(reason)

    private fun MemoryProcessingJobEntity.context() = MemoryRuntimeContext(
        characterId,
        spaceId,
        conversationId,
        scenarioId,
        runId,
        personaKey,
        generation
    )

    private fun MemoryEventBoxEntity.toDomain() = MemoryEventBox(
        id,
        spaceId,
        characterId,
        personaKey,
        title,
        summary,
        summaryRevision,
        MemoryEventSummaryStatus.entries.firstOrNull { it.name == summaryStatus }
            ?: MemoryEventSummaryStatus.STALE,
        MemoryEventStatus.entries.firstOrNull { it.name == status } ?: MemoryEventStatus.ARCHIVED,
        currentState,
        generation,
        createdAt,
        updatedAt)

    companion object {
        fun isEventJob(job: MemoryProcessingJobEntity): Boolean =
            job.eventId.isNotBlank() && job.purpose in setOf(
                MemoryJobPurpose.EVENT_SUMMARY.name,
                MemoryJobPurpose.EVENT_REBUILD.name
            )
    }
}
