package com.example.myapplication.conversation

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.companionSourceEligibility
import com.example.myapplication.model.CompanionSourceEligibility
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MEMORY_ENGINE_CURRENT
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryExtractionArtifact
import com.example.myapplication.model.MemoryExtractionFormat
import com.example.myapplication.model.MemoryExtractionProgress
import com.example.myapplication.model.MemoryExtractionSnapshot
import com.example.myapplication.model.MemoryExtractionSpan
import com.example.myapplication.model.MemoryExtractionTarget
import com.example.myapplication.model.MemoryJobPurpose
import com.example.myapplication.model.MemoryJobStatus
import com.example.myapplication.model.MemoryMessageSelection
import com.example.myapplication.model.MemoryProcessingJob
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.memoryRequestMayHaveStarted
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 应用内触发、Room 持久恢复；不创建周期唤醒或不确定失败的自动付费重试。 */
class DurableMemoryExtractionCoordinator(
    private val archive: MemoryArchiveRepository,
    private val ai: AiPromptExtrasService,
    private val currentSettings: suspend () -> AppSettings,
    private val hasCurrentConsent: suspend () -> Boolean,
    private val leaseMillis: Long = 120_000L,
    private val maxSpanChars: Int = 2400,
    private val maxInputChars: Int = 6000,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val waitForLease: suspend (Long) -> Unit = { delay(it) },
    private val concurrency: Semaphore = Semaphore(2),
) {
    private val spaces = ConcurrentHashMap<String, Mutex>()
    private val startupRecoveries = ConcurrentHashMap<String, Mutex>()
    private val receivedArtifacts = ConcurrentHashMap<String, String>()

    suspend fun isEnabled(): Boolean = archive.getEngineVersion() >= MEMORY_ENGINE_CURRENT

    suspend fun runtimeContext(conversationId: String, characterId: String): MemoryRuntimeContext? =
        archive.getRuntimeContext(conversationId, characterId)

    suspend fun summarySourceAllowed(
        context: MemoryRuntimeContext,
        message: com.example.myapplication.model.ChatMessage
    ): Boolean {
        if (message.status != com.example.myapplication.model.MessageStatus.COMPLETED || message.isRecalled) return false
        val source = archive.getSource(
            context,
            MemoryArchiveIdentity.id(
                "source",
                context.spaceId,
                message.id,
                message.memoryRevision.toString()
            )
        ) ?: return false
        if (source.personaKey != context.personaKey || context.characterId !in source.visibleCharacterIds) return false
        if (message.companionSourceEligibility() == CompanionSourceEligibility.CONTROL_NOOP) {
            return source.messageRevision == message.memoryRevision &&
                    archive.getRuntimeContext(
                        context.conversationId,
                        context.characterId
                    ) == context
        }
        if (message.companionSourceEligibility() == CompanionSourceEligibility.UNAVAILABLE) return false
        val segments = archive.getSourceSegments(context, source.id)
        return segments.isNotEmpty() && segments.none { it.skipReason.isNotBlank() || it.privateToSpeaker && source.speakerId != context.characterId }
    }

    suspend fun processAutomatic(
        conversationId: String,
        characterId: String,
        manualTrigger: Boolean = false
    ): MemoryExtractionProgress {
        if (!isEnabled()) return MemoryExtractionProgress()
        val context = archive.getRuntimeContext(conversationId, characterId)
            ?: return MemoryExtractionProgress(failureCode = "CONTEXT_UNAVAILABLE")
        return locked(context) {
            if (archive.isProcessingPaused(context)) return@locked MemoryExtractionProgress(
                failureCode = "USER_PAUSED"
            )
            val settings = currentSettings()
            val assistant = allowedAssistant(context, settings, automatic = !manualTrigger)
                ?: return@locked MemoryExtractionProgress()
            val pending = archive.listPendingIngress(context, 1000)
            val existingJobs = archive.listRecoverableJobs(context.spaceId).filter {
                it.context == context && it.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION && it.requestJson.isNotBlank()
            }
            val scheduledRefs = existingJobs.flatMap { it.sources }
            val protectedRefs = archive.listUnresolvedAutomaticJobs(context).filter {
                it.context != context || it.status == MemoryJobStatus.INVALIDATED
            }.flatMap { it.sources }
            var requiresManualReview = false
            val spans = pending.flatMap { event ->
                val source = event.source
                val committed = archive.listCommittedSpans(source.id)
                MemoryExtractionBatcher.sourceSpans(
                    source, archive.getSourceSegments(context, source.id), context.characterId,
                    maxSpanChars = maxSpanChars, overlapChars = minOf(64, maxSpanChars / 4),
                ).filterNot { span ->
                    val blocked = protectedRefs.any { ref ->
                        ref.sourceId == span.ref.sourceId && ref.sourceRevision == span.ref.sourceRevision &&
                                ref.start < span.ref.end && ref.end > span.ref.start
                    }
                    if (blocked) requiresManualReview = true
                    blocked || MemoryExtractionBatcher.covered(span.ref, committed + scheduledRefs)
                }
            }
            val sequence = pending.associate { it.source.id to it.sequence }
            val jobs = existingJobs.toMutableList()
            val newSourceIds = spans.map { it.ref.sourceId }.toSet()
            val newSourceCount =
                logicalMessageCount(pending.map { it.source }.filter { it.id in newSourceIds })
            if (manualTrigger || newSourceCount >= settings.memoryAutoSummaryEvery) {
                for (batch in MemoryExtractionBatcher.batches(spans, maxInputChars)) {
                    jobs += plan(
                        context,
                        assistant,
                        settings,
                        batch,
                        sequence[batch.first().ref.sourceId] ?: 0L,
                        manual = false
                    )
                }
            }
            runJobs(jobs, automatic = !manualTrigger).let { result ->
                if (requiresManualReview && result.failureCode.isBlank()) result.copy(failureCode = "REQUIRES_MANUAL_REVIEW") else result
            }
        }
    }

    suspend fun processSelectedMessages(
        conversationId: String,
        characterId: String,
        messageIds: List<String>
    ): MemoryExtractionProgress {
        val context = archive.getRuntimeContext(conversationId, characterId)
            ?: return MemoryExtractionProgress(failureCode = "CONTEXT_UNAVAILABLE")
        return processSelection(context, MemoryMessageSelection(conversationId, messageIds))
    }

    /** 用户明确选择原文范围后调用；不会扩大为历史整库回填。 */
    suspend fun processSelection(
        context: MemoryRuntimeContext,
        selection: MemoryMessageSelection,
        reprocessExisting: Boolean = false,
        retryUncertain: Boolean = false,
    ): MemoryExtractionProgress = locked(context) {
        if (!isEnabled()) return@locked MemoryExtractionProgress(failureCode = "ENGINE_UNAVAILABLE")
        if (archive.isProcessingPaused(context)) return@locked MemoryExtractionProgress(failureCode = "USER_PAUSED")
        val managementContext =
            archive.getManagementContext(context.characterId, context.spaceId, context.personaKey)
                ?.takeIf { it.generation == context.generation }
                ?: return@locked MemoryExtractionProgress(failureCode = "CONTEXT_UNAVAILABLE")
        val inspection = archive.inspectSelection(managementContext, selection)
        if (inspection.failureCode.isNotBlank()) return@locked MemoryExtractionProgress(failureCode = inspection.failureCode)
        if (inspection.requiresReview && !reprocessExisting) return@locked MemoryExtractionProgress(
            failureCode = "REQUIRES_SELECTION_REVIEW"
        )
        if (inspection.hasUncertainRequest && !retryUncertain) return@locked MemoryExtractionProgress(
            failureCode = "NETWORK_UNCERTAIN"
        )
        if (retryUncertain && !reprocessExisting) return@locked MemoryExtractionProgress(failureCode = "INVALID_SELECTION")
        val selectionAttempt = if (reprocessExisting) UUID.randomUUID().toString() else ""
        val settings = currentSettings()
        val assistant = allowedAssistant(managementContext, settings, automatic = false)
            ?: return@locked MemoryExtractionProgress(failureCode = "MEMORY_DISABLED")
        val captured = archive.captureSelectedMessages(managementContext, selection)
        var total = MemoryExtractionProgress(skippedSources = captured.unavailableMessageIds.size)
        for ((persona, sources) in captured.sources.groupBy { it.personaKey }) {
            // 显式档案入口按当时身份分组，不能将未知或旧身份改挂当前扮演身份。
            val selectedContext = managementContext.copy(personaKey = persona)
            val spans = sources.flatMap { source ->
                MemoryExtractionBatcher.sourceSpans(
                    source,
                    archive.getSourceSegments(selectedContext, source.id),
                    context.characterId,
                    selectedByUser = true,
                    maxSpanChars = maxSpanChars,
                    overlapChars = minOf(64, maxSpanChars / 4)
                )
            }
            val jobs = mutableListOf<MemoryProcessingJob>()
            MemoryExtractionBatcher.batches(spans, maxInputChars).forEach { batch ->
                jobs += plan(
                    selectedContext,
                    assistant,
                    settings,
                    batch,
                    sources.indexOfFirst { it.id == batch.first().ref.sourceId }.toLong(),
                    manual = true,
                    selectionAttempt = selectionAttempt,
                )
            }
            val result = runJobs(jobs, automatic = false)
            total = total.copy(
                committedJobs = total.committedJobs + result.committedJobs,
                createdRecords = total.createdRecords + result.createdRecords,
                skippedSources = total.skippedSources + result.skippedSources,
                failureCode = result.failureCode
            )
            if (result.failureCode.isNotBlank()) break
        }
        total
    }

    /** 按原任务继续；不重选来源、不刷新成新任务键，也不改绑旧自动运行。 */
    suspend fun resumeJob(
        managementContext: MemoryRuntimeContext,
        jobId: String
    ): MemoryExtractionProgress = locked(managementContext) {
        val current = archive.getManagementContext(
            managementContext.characterId,
            managementContext.spaceId,
            managementContext.personaKey
        )
        if (managementContext.conversationId.isNotBlank() || current != managementContext) return@locked MemoryExtractionProgress(
            failureCode = "CONTEXT_UNAVAILABLE"
        )
        val job = archive.getJob(jobId)
            ?: return@locked MemoryExtractionProgress(failureCode = "JOB_CHANGED")
        if (job.context.spaceId != managementContext.spaceId || job.context.characterId != managementContext.characterId ||
            job.context.personaKey != managementContext.personaKey || job.purpose !in setOf(
                MemoryJobPurpose.AUTOMATIC_EXTRACTION,
                MemoryJobPurpose.USER_SELECTION
            )
        ) return@locked MemoryExtractionProgress(failureCode = "CONTEXT_UNAVAILABLE")
        if (job.status == MemoryJobStatus.INVALIDATED || job.context.generation != managementContext.generation ||
            job.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION && archive.getRuntimeContext(
                job.context.conversationId,
                job.context.characterId
            ) != job.context
        ) return@locked MemoryExtractionProgress(failureCode = "REQUIRES_MANUAL_REVIEW")
        if (!isEnabled() || allowedAssistant(
                job.context,
                currentSettings(),
                job.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION
            ) == null
        ) return@locked MemoryExtractionProgress(failureCode = "MEMORY_DISABLED")
        if (archive.isProcessingPaused(job.context, job.id)) return@locked MemoryExtractionProgress(
            failureCode = "USER_PAUSED"
        )
        runJob(job, automatic = job.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION)
    }

    /** 应用恢复只消费已经冻结的任务，不把历史消息变成新的收费范围。 */
    suspend fun resumePlanned(context: MemoryRuntimeContext): MemoryExtractionProgress =
        locked(context) {
            if (!isEnabled()) return@locked MemoryExtractionProgress()
            runJobs(archive.listRecoverableJobs(context.spaceId).filter {
                it.context == context && it.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION && it.requestJson.isNotBlank()
            }, automatic = true)
        }

    /** 等待只发生在当前应用协程内；延后重试仅提交已持久产物，不重新请求模型。 */
    suspend fun resumePlannedOnStartup(context: MemoryRuntimeContext): MemoryExtractionProgress =
        startupRecoveries.getOrPut(context.spaceId) { Mutex() }.withLock {
            var total = resumePlanned(context)
            val unresolvedRequest = total.failureCode == "NETWORK_UNCERTAIN"
            while (total.failureCode in setOf("LEASE_UNAVAILABLE", "NETWORK_UNCERTAIN")) {
                if (archive.isProcessingPaused(context)) return@withLock total.copy(failureCode = "USER_PAUSED")
                if (!isEnabled() || allowedAssistant(
                        context,
                        currentSettings(),
                        automatic = true
                    ) == null
                ) {
                    return@withLock total.copy(failureCode = "MEMORY_DISABLED")
                }
                if (archive.getRuntimeContext(
                        context.conversationId,
                        context.characterId
                    ) != context
                ) {
                    return@withLock total.copy(failureCode = "CONTEXT_UNAVAILABLE")
                }
                val saved = archive.listRecoverableJobs(context.spaceId).filter {
                    it.context == context && it.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION &&
                            it.requestJson.isNotBlank() && it.artifact.isNotBlank()
                }
                if (saved.isEmpty()) {
                    return@withLock total
                }
                val nextLease =
                    saved.filter { it.leaseOwner.isNotBlank() && it.leaseUntil > nowProvider() }
                        .minOfOrNull { it.leaseUntil }
                if (total.failureCode == "LEASE_UNAVAILABLE") {
                    waitForLease(
                        ((nextLease ?: (nowProvider() + 1_000L)) - nowProvider()).coerceIn(
                            1L,
                            1_000L
                        )
                    )
                }
                // delay 期间可能停用记忆、切换身份或取消；runJobs 与仓储再检查全部守卫。
                val resumed = locked(context) {
                    if (!isEnabled()) MemoryExtractionProgress(failureCode = "MEMORY_DISABLED")
                    else runJobs(saved, automatic = true)
                }
                total = MemoryExtractionProgress(
                    committedJobs = total.committedJobs + resumed.committedJobs,
                    createdRecords = total.createdRecords + resumed.createdRecords,
                    skippedSources = total.skippedSources + resumed.skippedSources,
                    failureCode = resumed.failureCode.ifBlank { if (unresolvedRequest) "NETWORK_UNCERTAIN" else "" },
                )
            }
            total
        }

    private suspend fun plan(
        context: MemoryRuntimeContext,
        assistant: Assistant,
        settings: AppSettings,
        spans: List<MemoryExtractionSpan>,
        sequence: Long,
        manual: Boolean,
        selectionAttempt: String = "",
    ): MemoryProcessingJob {
        val targets = currentTargets(context)
        val snapshot = MemoryExtractionSnapshot(
            spans, targets, assistant.name, settings.resolvedUserDisplayName(),
            if (settings.memoryExtractionPrompt.isBlank()) MemoryExtractionFormat.V2 else MemoryExtractionFormat.LEGACY_LIST,
            settings.memoryExtractionPrompt, sequence, java.util.TimeZone.getDefault().id,
            knowledgeTargets = archive.getKnowledgeTargets(context),
        )
        val frozenKey = snapshotKey(context, snapshot, manual)
        val key = if (selectionAttempt.isBlank()) frozenKey else MemoryArchiveIdentity.id(
            "explicit-reprocess",
            selectionAttempt,
            frozenKey
        )
        val refs = spans.map { it.ref }.distinct()
        return if (manual) archive.planSelectedJob(
            context,
            key,
            refs,
            targets.associate { it.id to it.revision },
            AppJson.gson.toJson(snapshot)
        ) else archive.planJob(
            context,
            key,
            refs,
            targets.associate { it.id to it.revision },
            AppJson.gson.toJson(snapshot)
        )
    }

    private suspend fun currentTargets(context: MemoryRuntimeContext): List<MemoryExtractionTarget> {
        var targetChars = 0
        return archive.listRecords(context, 60).filter {
            it.personaKey == context.personaKey && it.status in setOf(
                com.example.myapplication.model.MemoryRecordStatus.ACTIVE,
                com.example.myapplication.model.MemoryRecordStatus.ARCHIVED,
            )
        }.filter { record ->
            if (targetChars + record.content.length > 6000) false else {
                targetChars += record.content.length; true
            }
        }.sortedBy { it.id }.map { MemoryExtractionTarget(it.id, it.revision, it.content) }
    }

    private fun snapshotKey(
        context: MemoryRuntimeContext,
        snapshot: MemoryExtractionSnapshot,
        manual: Boolean
    ): String =
        MemoryArchiveIdentity.id(
            if (manual) "c2-selected" else "c2-auto",
            context.spaceId,
            context.conversationId,
            context.runId,
            context.personaKey,
            context.generation.toString(),
            MemoryArchiveIdentity.fingerprint(AppJson.gson.toJson(snapshot)),
        )

    private suspend fun refreshUnrequestedTargets(planned: MemoryProcessingJob): MemoryProcessingJob {
        val job = archive.getJob(planned.id) ?: return planned
        if (job.artifact.isNotBlank() || receivedArtifacts.containsKey(job.id) ||
            memoryRequestMayHaveStarted(
                job.requestState.name,
                job.failureCode
            ) || job.status == MemoryJobStatus.COMMITTED ||
            job.status == MemoryJobStatus.INVALIDATED
        ) return job
        val previous = snapshot(job)
        val knowledgeTargets = archive.getKnowledgeTargets(job.context)
        if (targetsCurrent(job.context, previous.targets) &&
            (previous.knowledgeTargets == null || previous.knowledgeTargets == knowledgeTargets)
        ) return job
        val next = previous.copy(
            targets = currentTargets(job.context),
            knowledgeTargets = if (previous.knowledgeTargets == null) null else knowledgeTargets
        )
        return archive.replanUnrequestedJob(
            job.id, snapshotKey(job.context, next, job.purpose == MemoryJobPurpose.USER_SELECTION),
            next.targets.associate { it.id to it.revision }, AppJson.gson.toJson(next),
        ) ?: job
    }

    private suspend fun targetsCurrent(
        context: MemoryRuntimeContext,
        targets: List<MemoryExtractionTarget>
    ): Boolean =
        targets.all { target ->
            val record = archive.getRecordDetails(context, target.id)?.record
            record != null && record.revision == target.revision && record.personaKey == context.personaKey &&
                    record.status in setOf(
                com.example.myapplication.model.MemoryRecordStatus.ACTIVE,
                com.example.myapplication.model.MemoryRecordStatus.ARCHIVED
            )
        }

    private suspend fun runJobs(
        jobs: List<MemoryProcessingJob>,
        automatic: Boolean
    ): MemoryExtractionProgress {
        var result = MemoryExtractionProgress()
        val ordered = jobs.distinctBy { it.id }
            .sortedWith(compareBy<MemoryProcessingJob> { snapshot(it).sourceSequence }
                .thenBy { snapshot(it).sources.firstOrNull()?.ref?.start ?: 0 })
        for (job in ordered) {
            if (archive.isProcessingPaused(
                    job.context,
                    job.id
                )
            ) return result.copy(failureCode = "USER_PAUSED")
            if (allowedAssistant(
                    job.context,
                    currentSettings(),
                    automatic
                ) == null
            ) return result.copy(failureCode = "MEMORY_DISABLED")
            val outcome = runJob(refreshUnrequestedTargets(job), automatic)
            result = result.copy(
                committedJobs = result.committedJobs + outcome.committedJobs,
                createdRecords = result.createdRecords + outcome.createdRecords,
                skippedSources = result.skippedSources + outcome.skippedSources,
                failureCode = outcome.failureCode
            )
            if (outcome.failureCode.isNotBlank()) break
        }
        return result
    }

    private suspend fun runJob(
        planned: MemoryProcessingJob,
        automatic: Boolean
    ): MemoryExtractionProgress = coroutineScope {
        if (planned.status == MemoryJobStatus.COMMITTED) return@coroutineScope MemoryExtractionProgress()
        if (archive.isProcessingPaused(
                planned.context,
                planned.id
            )
        ) return@coroutineScope MemoryExtractionProgress(failureCode = "USER_PAUSED")
        if (planned.status == MemoryJobStatus.INVALIDATED) return@coroutineScope MemoryExtractionProgress(
            failureCode = "REQUIRES_MANUAL_REVIEW"
        )
        if (planned.artifact.isBlank() && !receivedArtifacts.containsKey(planned.id) && memoryRequestMayHaveStarted(
                planned.requestState.name,
                planned.failureCode
            )
        ) {
            return@coroutineScope MemoryExtractionProgress(failureCode = "NETWORK_UNCERTAIN")
        }
        val owner = UUID.randomUUID().toString()
        val job = archive.claimJob(planned.id, owner, leaseMillis)
            ?: return@coroutineScope MemoryExtractionProgress(failureCode = "LEASE_UNAVAILABLE")
        if (job.artifact.isBlank() && !receivedArtifacts.containsKey(job.id) &&
            memoryRequestMayHaveStarted(job.requestState.name, job.failureCode)
        ) {
            archive.releaseJob(job.id, owner, MemoryJobStatus.PAUSED, "NETWORK_UNCERTAIN")
            return@coroutineScope MemoryExtractionProgress(failureCode = "NETWORK_UNCERTAIN")
        }
        // 已落盘的产物只需本地校验/提交；不为它启动模型请求心跳。
        val heartbeat =
            launch(start = if (job.artifact.isBlank()) CoroutineStart.DEFAULT else CoroutineStart.LAZY) {
                while (true) {
                    delay((leaseMillis / 3).coerceAtLeast(100L))
                    if (!archive.renewLease(job.id, owner, leaseMillis)) throw LeaseLostException()
                }
            }
        var started = false
        var phase = "REQUEST"
        try {
            val snapshot = snapshot(job)
            var artifact = job.artifact.ifBlank { receivedArtifacts[job.id].orEmpty() }
            if (artifact.isBlank()) {
                if (!targetsCurrent(
                        job.context,
                        snapshot.targets
                    )
                ) throw ExtractionFailure("RECORD_CHANGED")
                val semantic =
                    snapshot.sources.any { it.skipReason.isEmpty() && it.text.isNotBlank() }
                val completion = if (!semantic) PromptCompletionResult(
                    EMPTY_RESULT,
                    PromptCompletionStatus.COMPLETED
                ) else {
                    if (archive.isProcessingPaused(
                            job.context,
                            job.id
                        )
                    ) throw ExtractionFailure("USER_PAUSED")
                    val settings = currentSettings()
                    if (allowedAssistant(
                            job.context,
                            settings,
                            automatic
                        ) == null
                    ) throw ExtractionFailure("MEMORY_DISABLED")
                    val provider = settings.resolveFunctionProvider(ProviderFunction.MEMORY)
                        ?: throw ExtractionFailure("MODEL_UNAVAILABLE")
                    val model = settings.resolveFunctionModel(ProviderFunction.MEMORY)
                        .takeIf(String::isNotBlank) ?: throw ExtractionFailure("MODEL_UNAVAILABLE")
                    if (!archive.markRequestStarted(
                            job.id,
                            owner
                        )
                    ) throw ExtractionFailure("LEASE_UNAVAILABLE")
                    started = true
                    ai.requestMemoryExtraction(
                        snapshot,
                        provider.baseUrl,
                        provider.apiKey,
                        model,
                        provider.resolvedApiProtocol(),
                        provider
                    )
                }
                artifact = AppJson.gson.toJson(
                    MemoryExtractionArtifact(
                        completion = completion,
                        skipReasons = snapshot.sources.mapNotNull { it.skipReason.takeIf(String::isNotBlank) }
                            .distinct()
                    )
                )
                receivedArtifacts[job.id] = artifact
            }
            phase = "SAVE"
            if (job.artifact.isBlank()) withContext(NonCancellable) {
                if (!archive.saveExtraction(
                        job.id,
                        owner,
                        artifact
                    )
                ) throw ExtractionFailure("SAVE_REJECTED")
            }
            receivedArtifacts.remove(job.id)
            if (archive.isProcessingPaused(
                    job.context,
                    job.id
                )
            ) throw ExtractionFailure("USER_PAUSED")
            phase = "VALIDATE"
            val extracted = AppJson.gson.fromJson(artifact, MemoryExtractionArtifact::class.java)
            require(extracted.version == 1) { "不支持的提取产物版本" }
            val batch =
                if (snapshot.sources.all { it.skipReason.isNotEmpty() || it.text.isBlank() }) MemoryCommitBatch()
                else MemoryExtractionParser.parse(extracted.completion, snapshot)
            if (allowedAssistant(
                    job.context,
                    currentSettings(),
                    automatic
                ) == null
            ) throw ExtractionFailure("MEMORY_DISABLED")
            phase = "COMMIT"
            // 提交会结束 lease；先停心跳，避免它把本次正常提交误报为 lease 丢失。
            heartbeat.cancelAndJoin()
            when (val committed = archive.commitJob(job.id, owner, batch)) {
                is MemoryWriteResult.Committed -> MemoryExtractionProgress(
                    if (committed.alreadyCommitted) 0 else 1,
                    committed.recordIds.size,
                    snapshot.sources.filter { it.skipReason.isNotBlank() }.map { it.ref.sourceId }
                        .distinct().size
                )

                is MemoryWriteResult.Conflict -> throw ExtractionFailure(committed.reason.name)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                heartbeat.cancelAndJoin()
                archive.releaseJob(
                    job.id,
                    owner,
                    MemoryJobStatus.PAUSED,
                    if (started) "CANCELLED_DURING_REQUEST" else "CANCELLED"
                )
            }
            throw cancelled
        } catch (failure: Exception) {
            val code = when {
                failure is ExtractionFailure -> failure.code
                phase == "REQUEST" && started -> "NETWORK_UNCERTAIN"
                phase == "VALIDATE" -> "INVALID_RESPONSE"
                phase == "COMMIT" -> "COMMIT_FAILED"
                phase == "SAVE" -> "SAVE_FAILED"
                else -> "PREPARATION_FAILED"
            }
            heartbeat.cancelAndJoin()
            archive.releaseJob(job.id, owner, MemoryJobStatus.PAUSED, code)
            if (archive.getJob(job.id)?.status in setOf(
                    null,
                    MemoryJobStatus.INVALIDATED,
                    MemoryJobStatus.COMMITTED
                )
            ) receivedArtifacts.remove(job.id)
            MemoryExtractionProgress(failureCode = code)
        } finally {
            heartbeat.cancelAndJoin()
        }
    }

    private suspend fun allowedAssistant(
        context: MemoryRuntimeContext,
        settings: AppSettings,
        automatic: Boolean
    ): Assistant? {
        if (!hasCurrentConsent() || automatic && settings.memoryAutoSummaryEvery <= 0) return null
        return settings.resolvedAssistants()
            .firstOrNull { it.id == context.characterId && it.memoryEnabled }
    }

    private fun snapshot(job: MemoryProcessingJob): MemoryExtractionSnapshot =
        AppJson.gson.fromJson(job.requestJson, MemoryExtractionSnapshot::class.java)

    private suspend fun <T> locked(context: MemoryRuntimeContext, block: suspend () -> T): T =
        spaces.getOrPut(context.spaceId) { Mutex() }.withLock {
            concurrency.withPermit { block() }
        }

    private fun logicalMessageCount(sources: List<MemorySourceEvidence>): Int {
        var count = 0
        var previousAssistantConversation: String? = null
        sources.forEach { source ->
            if (source.speakerRole == "USER") {
                count++; previousAssistantConversation = null
            } else if (previousAssistantConversation != source.conversationId) {
                count++; previousAssistantConversation = source.conversationId
            }
        }
        return count
    }

    private class ExtractionFailure(val code: String) : IllegalStateException(code)
    private class LeaseLostException : IllegalStateException("LEASE_LOST")

    companion object {
        private const val EMPTY_RESULT =
            "{\"schema_version\":2,\"memories\":[],\"corrections\":[],\"state_updates\":[],\"resident_candidates\":[]}"
    }
}
