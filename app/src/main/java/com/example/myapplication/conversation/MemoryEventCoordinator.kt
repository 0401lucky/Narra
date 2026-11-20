package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import kotlinx.coroutines.CancellationException
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

/** 事件整理只由增量写入、显式重整和应用恢复触发，不创建后台周期收费任务。 */
class MemoryEventCoordinator(
    private val archive: MemoryArchiveRepository,
    private val ai: AiPromptExtrasService,
    private val currentSettings: suspend () -> AppSettings,
    private val hasCurrentConsent: suspend () -> Boolean,
    private val policy: MemoryEventSummaryPolicy = MemoryEventSummaryPolicy(),
    private val leaseMillis: Long = 120_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val waitForLease: suspend (Long) -> Unit = { delay(it) },
    private val concurrency: Semaphore = Semaphore(2),
) {
    private val spaces = ConcurrentHashMap<String, Mutex>()
    private val recoveries = ConcurrentHashMap<String, Mutex>()
    private val received = ConcurrentHashMap<String, Pair<Int, PromptCompletionResult>>()

    suspend fun processAutomatic(context: MemoryRuntimeContext): MemoryEventProcessingProgress =
        locked(context) {
            if (archive.isProcessingPaused(context)) return@locked MemoryEventProcessingProgress(
                failureCode = "USER_PAUSED"
            )
            if (!enabled() || !allowed(
                    context,
                    automatic = true
                )
            ) return@locked MemoryEventProcessingProgress()
            if (context.conversationId.isBlank() || archive.getRuntimeContext(
                    context.conversationId,
                    context.characterId
                ) != context
            ) return@locked MemoryEventProcessingProgress(failureCode = "CONTEXT_UNAVAILABLE")
            val relations = archive.consumeEventRelations(context)
            var progress = MemoryEventProcessingProgress(appliedRelations = relations.applied)
            val settings = currentSettings()
            val options = MemoryEventSummaryOptions(
                characterName = settings.resolvedAssistants()
                    .firstOrNull { it.id == context.characterId }?.name.orEmpty(),
                userName = settings.resolvedUserDisplayName(),
                timeZoneId = java.util.TimeZone.getDefault().id,
                policy = policy
            )
            archive.listEvents(context).filter { it.status == MemoryEventStatus.ACTIVE }
                .forEach { event ->
                    val result = try {
                        when (val plan = archive.planEventSummary(context, event.id, options)) {
                            is MemoryEventPlanResult.Planned -> runJob(plan.job)
                            is MemoryEventPlanResult.NeedsReview -> MemoryEventProcessingProgress(
                                failureCode = "REQUIRES_MANUAL_REVIEW"
                            )

                            is MemoryEventPlanResult.Conflict -> MemoryEventProcessingProgress(
                                failureCode = plan.reason.name
                            )

                            MemoryEventPlanResult.NotNeeded -> MemoryEventProcessingProgress()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IllegalArgumentException) {
                        MemoryEventProcessingProgress(failureCode = "INPUT_UNAVAILABLE")
                    }
                    progress = progress.plus(result)
                }
            progress
        }

    /** C6 的显式操作入口。expectedGeneration 防双击；未知付费记录另需明确 retryUncertain。 */
    suspend fun rebuild(
        context: MemoryRuntimeContext,
        eventId: String,
        expectedGeneration: Long,
        retryUncertain: Boolean = false
    ): MemoryEventProcessingProgress = locked(context) {
        if (archive.isProcessingPaused(context)) return@locked MemoryEventProcessingProgress(
            failureCode = "USER_PAUSED"
        )
        if (!enabled() || !allowed(
                context,
                automatic = false
            )
        ) return@locked MemoryEventProcessingProgress(failureCode = "MEMORY_DISABLED")
        val settings = currentSettings()
        val options = MemoryEventSummaryOptions(
            manualRebuild = true,
            expectedGeneration = expectedGeneration,
            retryUncertain = retryUncertain,
            characterName = settings.resolvedAssistants()
                .firstOrNull { it.id == context.characterId }?.name.orEmpty(),
            userName = settings.resolvedUserDisplayName(),
            timeZoneId = java.util.TimeZone.getDefault().id,
            policy = policy
        )
        when (val plan = archive.planEventSummary(context, eventId, options)) {
            is MemoryEventPlanResult.Planned -> runJob(plan.job)
            is MemoryEventPlanResult.NeedsReview -> MemoryEventProcessingProgress(failureCode = "REQUIRES_MANUAL_REVIEW")
            is MemoryEventPlanResult.Conflict -> MemoryEventProcessingProgress(failureCode = plan.reason.name)
            MemoryEventPlanResult.NotNeeded -> MemoryEventProcessingProgress()
        }
    }

    suspend fun resumePlanned(context: MemoryRuntimeContext): MemoryEventProcessingProgress =
        locked(context) {
            if (!enabled()) return@locked MemoryEventProcessingProgress()
            var progress = MemoryEventProcessingProgress()
            archive.listRecoverableJobs(context.spaceId)
                .filter { it.context == context && isEvent(it) && it.requestJson.isNotBlank() }
                .forEach { progress = progress.plus(runJob(it)) }
            progress
        }

    suspend fun resumeEventJob(
        managementContext: MemoryRuntimeContext,
        jobId: String
    ): MemoryEventProcessingProgress = locked(managementContext) {
        val current = archive.getManagementContext(
            managementContext.characterId,
            managementContext.spaceId,
            managementContext.personaKey
        )
        if (managementContext.conversationId.isNotBlank() || current != managementContext) return@locked MemoryEventProcessingProgress(
            failureCode = "CONTEXT_UNAVAILABLE"
        )
        val job = archive.getJob(jobId)
            ?: return@locked MemoryEventProcessingProgress(failureCode = "JOB_CHANGED")
        if (!isEvent(job) || job.context.characterId != managementContext.characterId || job.context.spaceId != managementContext.spaceId || job.context.personaKey != managementContext.personaKey) return@locked MemoryEventProcessingProgress(
            failureCode = "CONTEXT_UNAVAILABLE"
        )
        if (job.status == MemoryJobStatus.COMMITTED) return@locked MemoryEventProcessingProgress()
        if (job.status == MemoryJobStatus.INVALIDATED || job.context.generation != managementContext.generation ||
            job.purpose == MemoryJobPurpose.EVENT_SUMMARY && archive.getRuntimeContext(
                job.context.conversationId,
                job.context.characterId
            ) != job.context
        ) return@locked MemoryEventProcessingProgress(failureCode = "REQUIRES_MANUAL_REVIEW")
        if (!enabled()) return@locked MemoryEventProcessingProgress(failureCode = "MEMORY_DISABLED")
        runJob(job)
    }

    /** 活跃 lease 不可偷取；仅等待本应用内已有任务，恢复不会从原消息创建额外范围。 */
    suspend fun resumePlannedOnStartup(context: MemoryRuntimeContext): MemoryEventProcessingProgress =
        recoveries.getOrPut(context.spaceId) { Mutex() }.withLock {
            var total = resumePlanned(context)
            while (true) {
                if (archive.isProcessingPaused(context)) return@withLock total.copy(failureCode = "USER_PAUSED")
                if (!enabled() || !allowed(context, automatic = false)) return@withLock total.copy(
                    failureCode = "MEMORY_DISABLED"
                )
                val jobs = archive.listRecoverableJobs(context.spaceId)
                val events =
                    jobs.filter { it.context == context && isEvent(it) && safelyResumable(it) }
                if (events.isEmpty()) break
                val activeLease =
                    jobs.filter { it.leaseOwner.isNotBlank() && it.leaseUntil > now() }
                        .minOfOrNull { it.leaseUntil } ?: break
                waitForLease((activeLease - now()).coerceIn(1L, 1_000L))
                val resumed = resumePlanned(context)
                total = total.copy(
                    committedEvents = total.committedEvents + resumed.committedEvents,
                    completedBatches = total.completedBatches + resumed.completedBatches,
                    failureCode = resumed.failureCode
                )
            }
            total
        }

    private suspend fun safelyResumable(job: MemoryProcessingJob): Boolean {
        if (archive.isProcessingPaused(job.context, job.id)) return false
        if (!allowed(job.context, job.purpose != MemoryJobPurpose.EVENT_REBUILD)) return false
        val work: MemoryEventSummaryWork
        val artifact: MemoryEventSummaryArtifact
        try {
            work = MemoryEventSummaryBatches.readWork(job.requestJson)
            artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
            MemoryEventSummaryBatches.outputs(work, artifact)
        } catch (_: RuntimeException) {
            return false
        }
        if (artifact.pendingBatch != null && received[job.id]?.first != artifact.pendingBatch) return false
        if (job.artifact.isBlank() && job.requestState != MemoryRequestState.NOT_STARTED) return false
        val event = archive.getEventDetails(job.context, job.eventId)?.event ?: return false
        return event.generation == work.snapshot.expectedGeneration && event.summaryRevision == work.snapshot.summaryRevision
    }

    private suspend fun runJob(planned: MemoryProcessingJob): MemoryEventProcessingProgress =
        coroutineScope {
            if (archive.isProcessingPaused(
                    planned.context,
                    planned.id
                )
            ) return@coroutineScope MemoryEventProcessingProgress(failureCode = "USER_PAUSED")
            val automatic = planned.purpose != MemoryJobPurpose.EVENT_REBUILD
            if (!allowed(
                    planned.context,
                    automatic
                )
            ) return@coroutineScope MemoryEventProcessingProgress(failureCode = "MEMORY_DISABLED")
            val before = try {
                MemoryEventSummaryBatches.readArtifact(planned.artifact)
            } catch (_: RuntimeException) {
                return@coroutineScope MemoryEventProcessingProgress(failureCode = "INVALID_RESPONSE")
            }
            if (before.pendingBatch != null && received[planned.id]?.first != before.pendingBatch) return@coroutineScope MemoryEventProcessingProgress(
                failureCode = "NETWORK_UNCERTAIN"
            )
            if (planned.artifact.isBlank() && planned.requestState != MemoryRequestState.NOT_STARTED) return@coroutineScope MemoryEventProcessingProgress(
                failureCode = "NETWORK_UNCERTAIN"
            )
            val owner = UUID.randomUUID().toString()
            val job = archive.claimJob(planned.id, owner, leaseMillis)
                ?: return@coroutineScope MemoryEventProcessingProgress(failureCode = "LEASE_UNAVAILABLE")
            val heartbeat = launch {
                while (true) {
                    delay((leaseMillis / 3).coerceAtLeast(100L))
                    if (!archive.renewLease(
                            job.id,
                            owner,
                            leaseMillis
                        )
                    ) throw EventFailure("LEASE_LOST")
                }
            }
            var phase = "PREPARE"
            var requested = false
            var completed = 0
            try {
                val work = MemoryEventSummaryBatches.readWork(job.requestJson)
                var artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
                while (true) {
                    phase = "VALIDATE"
                    val outputs = MemoryEventSummaryBatches.outputs(work, artifact)
                    if (outputs.size == work.batchEnds.size && artifact.pendingBatch == null) break
                    val index = outputs.size
                    var completion = received[job.id]?.takeIf { it.first == index }?.second
                    if (artifact.pendingBatch != null && completion == null) throw EventFailure("NETWORK_UNCERTAIN")
                    if (completion == null) {
                        if (archive.isProcessingPaused(
                                job.context,
                                job.id
                            )
                        ) throw EventFailure("USER_PAUSED")
                        phase = "PREPARE"
                        if (!enabled() || !allowed(
                                job.context,
                                automatic
                            )
                        ) throw EventFailure("MEMORY_DISABLED")
                        val settings = currentSettings()
                        val provider = settings.resolveFunctionProvider(ProviderFunction.MEMORY)
                            ?.takeIf { it.hasBaseCredentials() }
                            ?: throw EventFailure("MODEL_UNAVAILABLE")
                        val model = settings.resolveFunctionModel(ProviderFunction.MEMORY)
                            .takeIf(String::isNotBlank) ?: throw EventFailure("MODEL_UNAVAILABLE")
                        val snapshot = MemoryEventSummaryBatches.batch(work, index, outputs)
                        MemoryEventSummaryFormatter.format(snapshot)
                        if (!archive.beginEventSummaryBatch(
                                job.id,
                                owner,
                                index
                            )
                        ) throw EventFailure("JOB_CHANGED")
                        phase = "REQUEST"
                        requested = true
                        completion = ai.requestMemoryEventSummary(
                            snapshot,
                            provider.baseUrl,
                            provider.apiKey,
                            model,
                            provider.resolvedApiProtocol(),
                            provider
                        )
                        received[job.id] = index to completion
                    }
                    phase = "SAVE"
                    withContext(NonCancellable) {
                        if (!archive.saveEventSummaryBatch(
                                job.id,
                                owner,
                                index,
                                completion
                            )
                        ) throw EventFailure("SAVE_REJECTED")
                    }
                    received.remove(job.id)
                    completed++
                    if (archive.isProcessingPaused(
                            job.context,
                            job.id
                        )
                    ) throw EventFailure("USER_PAUSED")
                    artifact = MemoryEventSummaryBatches.readArtifact(
                        archive.getJob(job.id)?.artifact ?: throw EventFailure("JOB_CHANGED")
                    )
                }
                if (archive.isProcessingPaused(
                        job.context,
                        job.id
                    )
                ) throw EventFailure("USER_PAUSED")
                if (!enabled() || !allowed(
                        job.context,
                        automatic
                    )
                ) throw EventFailure("MEMORY_DISABLED")
                phase = "COMMIT"
                heartbeat.cancelAndJoin()
                when (val result = archive.commitEventSummary(job.id, owner)) {
                    is MemoryWriteResult.Committed -> MemoryEventProcessingProgress(
                        if (result.alreadyCommitted) 0 else 1,
                        completed
                    )

                    is MemoryWriteResult.Conflict -> throw EventFailure(result.reason.name)
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    heartbeat.cancelAndJoin()
                    archive.releaseJob(
                        job.id,
                        owner,
                        MemoryJobStatus.PAUSED,
                        if (requested) "CANCELLED_DURING_REQUEST" else "CANCELLED"
                    )
                    if (archive.getJob(job.id)?.status == MemoryJobStatus.INVALIDATED) received.remove(
                        job.id
                    )
                }
                throw cancelled
            } catch (failure: Exception) {
                val code = when {
                    failure is EventFailure -> failure.code
                    phase == "REQUEST" -> "NETWORK_UNCERTAIN"
                    phase == "SAVE" -> "SAVE_FAILED"
                    phase == "VALIDATE" -> "INVALID_RESPONSE"
                    phase == "COMMIT" -> "COMMIT_FAILED"
                    else -> "PREPARATION_FAILED"
                }
                heartbeat.cancelAndJoin()
                archive.releaseJob(job.id, owner, MemoryJobStatus.PAUSED, code)
                if (archive.getJob(job.id)?.status in setOf(
                        null,
                        MemoryJobStatus.INVALIDATED,
                        MemoryJobStatus.COMMITTED
                    )
                ) received.remove(job.id)
                MemoryEventProcessingProgress(completedBatches = completed, failureCode = code)
            } finally {
                heartbeat.cancelAndJoin()
            }
        }

    private suspend fun enabled() = archive.getEngineVersion() >= MEMORY_ENGINE_CURRENT
    private suspend fun allowed(context: MemoryRuntimeContext, automatic: Boolean): Boolean {
        if (!hasCurrentConsent()) return false
        val settings = currentSettings()
        return (!automatic || settings.memoryAutoSummaryEvery > 0) && settings.resolvedAssistants()
            .any { it.id == context.characterId && it.memoryEnabled }
    }

    private fun isEvent(job: MemoryProcessingJob) =
        job.purpose in setOf(MemoryJobPurpose.EVENT_SUMMARY, MemoryJobPurpose.EVENT_REBUILD)

    private suspend fun <T> locked(context: MemoryRuntimeContext, block: suspend () -> T): T =
        spaces.getOrPut(context.spaceId) { Mutex() }.withLock { concurrency.withPermit { block() } }

    private fun MemoryEventProcessingProgress.plus(other: MemoryEventProcessingProgress) =
        MemoryEventProcessingProgress(
            committedEvents + other.committedEvents,
            completedBatches + other.completedBatches,
            appliedRelations + other.appliedRelations,
            failureCode.ifBlank { other.failureCode },
        )

    private class EventFailure(val code: String) : IllegalStateException(code)
}
