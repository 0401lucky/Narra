package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.conversation.MemoryExtractionParser
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryKnowledgeConsumptionEntity
import com.example.myapplication.data.local.memory.MemoryProcessingJobEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.CancellationException

/** 消费已经保存的动作，不发起第二轮模型整理；新提取在原提交事务中发布。 */
internal class MemoryKnowledgeStore(
    private val database: ChatDatabase,
    private val validContext: suspend (MemoryRuntimeContext) -> Boolean,
    private val validRuntime: suspend (MemoryRuntimeContext) -> Boolean,
    private val validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
    private val limits: suspend (String) -> MemoryResidentLimits,
    private val now: () -> Long,
) {
    private val archive = database.memoryArchiveDao()
    private val dao = database.memoryKnowledgeDao()
    private val storage = MemoryKnowledgeStorage(database, validSource)

    suspend fun residents(
        context: MemoryRuntimeContext,
        limit: Int,
        offset: Int
    ): List<MemoryResidentEntry> = database.withTransaction {
        if (!validContext(context)) emptyList() else dao.listResidents(
            context.spaceId, context.characterId,
            context.personaKey, limit.coerceIn(1, 1000), offset.coerceAtLeast(0)
        ).filter { row ->
            MemoryResidentCategory.entries.any { it.name == row.category }
        }.map { it.toDomain() }
    }

    suspend fun states(context: MemoryRuntimeContext): List<MemoryCurrentState> =
        database.withTransaction {
            if (!validContext(context)) emptyList() else dao.listStates(
                context.spaceId,
                context.characterId,
                context.personaKey
            )
                .filter { row ->
                    row.status != "DELETED" && MemoryStateKey.entries.any {
                        it.name.equals(
                            row.stateKey,
                            ignoreCase = true
                        )
                    }
                }
                .map {
                    it.toDomain().let { state ->
                        if (state.status == "ACTIVE" && state.expiresAt?.let { it <= now() } == true) state.copy(
                            status = "EXPIRED"
                        ) else state
                    }
                }
        }

    suspend fun residentDetails(context: MemoryRuntimeContext, id: String): MemoryResidentDetails? =
        database.withTransaction {
            val entry = dao.getResident(id) ?: return@withTransaction null
            if (!validContext(context) || !sameOwner(
                    context,
                    entry.spaceId,
                    entry.characterId,
                    entry.personaKey
                )
            ) return@withTransaction null
            if (MemoryResidentCategory.entries.none { it.name == entry.category }) return@withTransaction null
            val revisions = storage.revisions("RESIDENT", id)
            MemoryResidentDetails(entry.toDomain(), revisions, storage.sources(context, revisions))
        }

    suspend fun stateDetails(
        context: MemoryRuntimeContext,
        id: String
    ): MemoryCurrentStateDetails? = database.withTransaction {
        val entry = dao.getState(id) ?: return@withTransaction null
        if (!validContext(context) || !sameOwner(
                context,
                entry.spaceId,
                entry.characterId,
                entry.personaKey
            )
        ) return@withTransaction null
        if (MemoryStateKey.entries.none {
                it.name.equals(
                    entry.stateKey,
                    true
                )
            }) return@withTransaction null
        val revisions = storage.revisions("STATE", id)
        val state = entry.toDomain().let {
            if (it.status == "ACTIVE" && it.expiresAt?.let { time -> time <= now() } == true) it.copy(
                status = "EXPIRED"
            ) else it
        }
        MemoryCurrentStateDetails(state, revisions, storage.sources(context, revisions))
    }

    suspend fun targets(context: MemoryRuntimeContext): MemoryKnowledgeTargets =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction MemoryKnowledgeTargets()
            val capacities = limits(context.characterId).normalized()
            val counts = mutableMapOf<MemoryResidentCategory, Int>()
            var chars = 0
            val residents =
                dao.listResidents(context.spaceId, context.characterId, context.personaKey, 1000, 0)
                    .mapNotNull { row ->
                        val category =
                            MemoryResidentCategory.entries.firstOrNull { it.name == row.category }
                                ?: return@mapNotNull null
                        if (row.status != "READY" || row.content.isBlank() || chars + row.content.length > 12_000 ||
                            counts.getOrDefault(category, 0) >= capacities.capacity(category) ||
                            storage.evidence(context, "RESIDENT", row.id, row.revision) == null
                        ) return@mapNotNull null
                        counts[category] = counts.getOrDefault(category, 0) + 1
                        chars += row.content.length
                        MemoryResidentTarget(
                            row.id,
                            row.revision,
                            category,
                            row.content,
                            row.subjective,
                            row.editedByUser
                        )
                    }
            val states = dao.listStates(context.spaceId, context.characterId, context.personaKey)
                .mapNotNull { row ->
                    val key =
                        MemoryStateKey.entries.firstOrNull { it.name.equals(row.stateKey, true) }
                            ?: return@mapNotNull null
                    val valid = storage.evidence(context, "STATE", row.id, row.revision) != null
                    val expired = row.expiresAt?.let { it <= now() } == true
                    val active = valid && row.status == "ACTIVE" && row.endedAt == null && !expired
                    MemoryStateTarget(
                        row.id, row.revision, key, if (active) row.content else "",
                        when {
                            !valid -> "UNVERIFIED"
                            row.status == "ACTIVE" && expired -> "EXPIRED"
                            else -> row.status
                        }, row.editedByUser
                    )
                }
            MemoryKnowledgeTargets(residents = residents, states = states)
        }

    /** 新产物在提取事务内比对持久原响应，调用方不能伪造经过解析的动作。 */
    fun validActions(job: MemoryProcessingJobEntity, batch: MemoryCommitBatch): Boolean {
        if (batch.residents.isEmpty() && batch.states.isEmpty()) return true
        return try {
            val parsed = parsed(job).second
            parsed.residents == batch.residents && parsed.states == batch.states
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun consume(context: MemoryRuntimeContext): MemoryKnowledgeProgress {
        var progress = MemoryKnowledgeProgress()
        while (true) {
            val next = database.withTransaction {
                if (!validRuntime(context)) return@withTransaction MemoryKnowledgeProgress(
                    failureCode = "SCOPE_CHANGED"
                )
                if (archive.isProcessingPaused(
                        context.spaceId,
                        ""
                    )
                ) return@withTransaction MemoryKnowledgeProgress(
                    failureCode = "USER_PAUSED"
                )
                val jobs = dao.listUnconsumedJobs(
                    context.spaceId,
                    context.characterId,
                    context.personaKey,
                    40
                )
                if (jobs.isEmpty()) return@withTransaction null
                var page = MemoryKnowledgeProgress()
                jobs.forEach { job ->
                    if (archive.isProcessingPaused(
                            context.spaceId,
                            job.id
                        )
                    ) return@withTransaction page.copy(
                        failureCode = "USER_PAUSED"
                    )
                    val parsed = try {
                        parsed(job)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        remember(job.id, 0, 0, listOf("INVALID_ARTIFACT"))
                        page =
                            page.plus(MemoryKnowledgeProgress(consumedJobs = 1, skippedActions = 1))
                        return@forEach
                    }
                    val batch = parsed.second
                    val sourceRefs = archive.listJobSources(job.id).map {
                        MemorySourceRef(
                            it.sourceId,
                            it.sourceRevision,
                            it.spanStart,
                            it.spanEnd
                        )
                    }
                    val sourceValid =
                        job.generation == context.generation && sourceRefs.isNotEmpty() && sourceRefs.all {
                            validSource(
                                context,
                                it
                            )
                        }
                    val outputs = archive.listJobRecords(job.id).associateBy { it.localItemId }
                    val outputsValid = batch.records.all { draft ->
                        outputs[draft.localId]?.let {
                            sameOwner(context, it.spaceId, it.characterId, it.personaKey) &&
                                    it.revision == 1L && it.status in setOf("ACTIVE", "ARCHIVED")
                        } == true
                    } && batch.corrections.all { correction ->
                        archive.getRecord(correction.recordId)?.let {
                            sameOwner(context, it.spaceId, it.characterId, it.personaKey) &&
                                    it.revision == correction.expectedRevision + 1 && it.status in setOf(
                                "ACTIVE",
                                "ARCHIVED"
                            )
                        } == true
                    }
                    if (!sourceValid || !outputsValid) {
                        val skipped = batch.residents.size + batch.states.size
                        remember(
                            job.id,
                            0,
                            0,
                            List(skipped) { if (!sourceValid) "SOURCE_CHANGED" else "RECORD_CHANGED" })
                        page = page.plus(MemoryKnowledgeProgress(1, skippedActions = skipped))
                    } else page = page.plus(apply(job, context, batch, parsed.first))
                }
                page
            } ?: return progress
            progress = progress.plus(next)
            if (next.failureCode.isNotBlank()) return progress
        }
    }

    suspend fun apply(
        job: MemoryProcessingJobEntity, context: MemoryRuntimeContext, batch: MemoryCommitBatch,
        snapshot: MemoryExtractionSnapshot? = null,
    ): MemoryKnowledgeProgress {
        if (dao.getConsumption(job.id) != null) return MemoryKnowledgeProgress()
        if (batch.residents.isEmpty() && batch.states.isEmpty()) {
            remember(job.id, 0, 0, emptyList())
            return MemoryKnowledgeProgress(consumedJobs = 1)
        }
        val frozen = snapshot ?: parsed(job).first
        val skipped = mutableListOf<String>()
        var residentWrites = 0
        var stateWrites = 0
        batch.residents.forEachIndexed { index, action ->
            if (!action.stable) {
                skipped += "TEMPORARY_CANDIDATE"; return@forEachIndexed
            }
            if (action.sources.isEmpty() || action.sources.any { !validSource(context, it) }) {
                skipped += "SOURCE_CHANGED"; return@forEachIndexed
            }
            val target = if (action.operation == MemoryResidentOperation.ADD) {
                dao.listResidents(context.spaceId, context.characterId, context.personaKey, 1000, 0)
                    .firstOrNull {
                        it.status == "READY" && it.content == action.content && it.category == action.category.name && it.subjective == action.subjective
                    }
            } else dao.getResident(action.targetId)
            if (target != null && (!sameOwner(
                    context,
                    target.spaceId,
                    target.characterId,
                    target.personaKey
                ) ||
                        target.status == "DELETED" || target.category != action.category.name || target.editedByUser ||
                        action.operation != MemoryResidentOperation.ADD && target.revision != action.expectedRevision)
            ) {
                skipped += if (target.editedByUser) "MANUAL_PROTECTED" else "RECORD_CHANGED"; return@forEachIndexed
            }
            if (action.operation != MemoryResidentOperation.ADD && (target == null || frozen.knowledgeTargets?.residents?.none {
                    it.id == action.targetId && it.revision == action.expectedRevision && it.category == action.category
                } != false)) {
                skipped += "TARGET_NOT_FROZEN"; return@forEachIndexed
            }
            val reinforce =
                action.operation == MemoryResidentOperation.REINFORCE || action.operation == MemoryResidentOperation.ADD && target != null
            val previousEvidence =
                target?.let { storage.evidence(context, "RESIDENT", it.id, it.revision) }
            if (reinforce && (target?.status != "READY" || previousEvidence == null)) {
                skipped += "SOURCE_CHANGED"; return@forEachIndexed
            }
            val support = supportingRecords(job.id, batch, action.sources)
            // 退役仍保留旧正文，因此原来源与结束声明都必须继续参与失效和删除。
            val retainedEvidence = when {
                action.operation == MemoryResidentOperation.RETIRE && target != null ->
                    storage.historicalEvidence(context, "RESIDENT", target.id, target.revision)

                reinforce -> previousEvidence
                else -> null
            }
            val refs =
                retainedEvidence?.sources.orEmpty() + action.sources
            val records =
                (retainedEvidence?.records.orEmpty() + support).distinct()
            if (reinforce && previousEvidence?.sources?.toSet() == refs.toSet() && previousEvidence.records.toSet() == records.toSet()) {
                skipped += "UNCHANGED"; return@forEachIndexed
            }
            val timestamp = now()
            target?.let { storage.rememberResident(context, it) }
            val entry = MemoryResidentEntryEntity(
                target?.id ?: MemoryArchiveIdentity.id("resident", job.id, index.toString()),
                context.spaceId,
                context.characterId,
                action.category.name,
                context.personaKey,
                if (action.operation == MemoryResidentOperation.RETIRE) target?.content.orEmpty() else action.content,
                (target?.revision ?: 0L) + 1L,
                if (action.operation == MemoryResidentOperation.RETIRE) "ARCHIVED" else "READY",
                if (action.operation == MemoryResidentOperation.RETIRE) requireNotNull(target).subjective else action.subjective,
                target?.supersedesId,
                false,
                target?.createdAt ?: timestamp,
                timestamp,
            )
            if (target == null && dao.getResident(entry.id) != null) {
                skipped += "DELETED_TARGET"; return@forEachIndexed
            }
            archive.upsertResidentEntry(entry)
            storage.writeRevision(
                context, "RESIDENT", entry.id, entry.revision, entry.content, entry.status,
                if (target == null) MemoryChangeOrigin.EXTRACTION else MemoryChangeOrigin.CORRECTION,
                entry.subjective, null, null, refs.distinct(), records, timestamp
            )
            residentWrites++
        }
        batch.states.forEach { action ->
            if (action.sources.isEmpty() || action.sources.any { !validSource(context, it) }) {
                skipped += "SOURCE_CHANGED"; return@forEach
            }
            val current = dao.listStates(context.spaceId, context.characterId, context.personaKey)
                .firstOrNull { it.stateKey.equals(action.key.name, true) }
            val observedAt =
                action.sources.mapNotNull { archive.getSource(it.sourceId)?.occurredAt }.maxOrNull()
                    ?: 0L
            if (observedAt <= 0) {
                skipped += "MISSING_TIME"; return@forEach
            }
            if (current != null && observedAt < current.observedAt) {
                skipped += "OLDER_STATE"; return@forEach
            }
            val frozenTarget = frozen.knowledgeTargets?.states?.firstOrNull { it.key == action.key }
            if (action.expectedRevision != null) {
                if ((current?.revision ?: 0L) != action.expectedRevision ||
                    (frozenTarget?.revision ?: 0L) != action.expectedRevision ||
                    frozenTarget != null && current?.id != frozenTarget.id
                ) {
                    skipped += "RECORD_CHANGED"; return@forEach
                }
            } else if (current != null && (action.operation == MemoryStateOperation.END || current.editedByUser ||
                        current.observedAt <= 0 || observedAt <= current.observedAt)
            ) {
                skipped += "LEGACY_TARGET_UNVERIFIED"; return@forEach
            }
            if (action.operation == MemoryStateOperation.END && current == null) {
                skipped += "MISSING_TARGET"; return@forEach
            }
            val expiry = if (action.operation == MemoryStateOperation.END) current?.expiresAt
            else action.expiresAt ?: observedAt + action.key.defaultLifetimeMillis
            if (expiry != null && (expiry <= 0 || expiry - observedAt > MAX_STATE_LIFETIME_MILLIS)) {
                skipped += "INVALID_EXPIRY"; return@forEach
            }
            val timestamp = now()
            current?.let { storage.rememberState(context, it) }
            val entry = MemoryCurrentStateEntity(
                current?.id ?: MemoryArchiveIdentity.id(
                    "state",
                    context.spaceId,
                    context.personaKey,
                    action.key.name
                ),
                context.spaceId,
                context.characterId,
                action.key.name.lowercase(),
                context.personaKey,
                action.content,
                (current?.revision ?: 0) + 1,
                when {
                    action.operation == MemoryStateOperation.END -> "ENDED"
                    expiry != null && expiry <= timestamp -> "EXPIRED"
                    else -> "ACTIVE"
                },
                expiry,
                if (action.operation == MemoryStateOperation.END) observedAt else null,
                current?.createdAt ?: timestamp,
                timestamp,
                false,
                observedAt,
            )
            archive.upsertCurrentState(entry)
            storage.writeRevision(
                context, "STATE", entry.id, entry.revision, entry.content, entry.status,
                MemoryChangeOrigin.EXTRACTION, false, entry.expiresAt, entry.endedAt,
                action.sources, supportingRecords(job.id, batch, action.sources), timestamp
            )
            stateWrites++
        }
        remember(job.id, residentWrites, stateWrites, skipped)
        return MemoryKnowledgeProgress(1, residentWrites, stateWrites, skipped.size)
    }

    private suspend fun supportingRecords(
        jobId: String,
        batch: MemoryCommitBatch,
        refs: List<MemorySourceRef>
    ): List<MemoryRecordVersion> {
        fun overlap(other: List<MemorySourceRef>) = other.any { source ->
            refs.any {
                it.sourceId == source.sourceId && it.sourceRevision == source.sourceRevision && it.start < source.end && source.start < it.end
            }
        }

        val outputs = archive.listJobRecords(jobId).associateBy { it.localItemId }
        return (batch.records.filter { overlap(it.sources) }.mapNotNull { draft ->
            outputs[draft.localId]?.let { MemoryRecordVersion(it.id, 1L) }
        } + batch.corrections.filter { overlap(it.sources) }.map {
            MemoryRecordVersion(it.recordId, it.expectedRevision + 1)
        }).filter { ref ->
            archive.getRecord(ref.recordId)?.let {
                it.revision == ref.revision && it.status in setOf(
                    "ACTIVE",
                    "ARCHIVED"
                )
            } == true
        }
    }

    private fun parsed(job: MemoryProcessingJobEntity): Pair<MemoryExtractionSnapshot, MemoryCommitBatch> {
        val snapshot = AppJson.gson.fromJson(job.requestJson, MemoryExtractionSnapshot::class.java)
        val artifact = AppJson.gson.fromJson(job.artifact, MemoryExtractionArtifact::class.java)
        require(artifact.version == 1) { "不支持的提取产物" }
        return snapshot to MemoryExtractionParser.parse(artifact.completion, snapshot)
    }

    private suspend fun remember(
        jobId: String,
        residents: Int,
        states: Int,
        skipped: List<String>
    ) {
        dao.insertConsumption(
            MemoryKnowledgeConsumptionEntity(
                jobId, residents, states, skipped.size,
                AppJson.gson.toJson(skipped.distinct()), now()
            )
        )
    }

    private fun MemoryKnowledgeProgress.plus(other: MemoryKnowledgeProgress) =
        MemoryKnowledgeProgress(
            consumedJobs + other.consumedJobs,
            residentWrites + other.residentWrites,
            stateWrites + other.stateWrites,
            skippedActions + other.skippedActions,
            other.failureCode.ifBlank { failureCode },
        )

    companion object {
        const val MAX_STATE_LIFETIME_MILLIS = 366 * 24 * 60 * 60 * 1000L
        fun sameOwner(
            context: MemoryRuntimeContext,
            spaceId: String,
            characterId: String,
            personaKey: String
        ) =
            context.spaceId == spaceId && context.characterId == characterId && context.personaKey == personaKey
    }
}
