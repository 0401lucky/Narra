package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.context.MemoryVectorMath
import com.example.myapplication.context.MemoryVectorSearch
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryEmbeddingEntity
import com.example.myapplication.data.local.memory.MemoryEmbeddingIndexEntity
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingClient
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingConfiguration
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingError
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingException
import com.example.myapplication.data.repository.embedding.MemoryVectorChunkRef
import com.example.myapplication.data.repository.embedding.MemoryVectorIndexPlan
import com.example.myapplication.data.repository.embedding.PlannedMemoryVectorChunk
import com.example.myapplication.model.MemoryEmbeddingBatch
import com.example.myapplication.model.MemoryEmbeddingPurpose
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryArchiveTransferData
import com.example.myapplication.model.MemoryTransferVectorIndex
import com.example.myapplication.model.MemoryTransferVectorRecord
import com.example.myapplication.model.MemoryTransferVectorChunk
import com.example.myapplication.model.MemoryVectorBuildResult
import com.example.myapplication.model.MemoryVectorHit
import com.example.myapplication.model.MemoryVectorIndexProgress
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 索引状态属于空间/身份，运行快照仅作为本次操作凭据，不充当缓存身份。 */
class RoomMemoryVectorRepository(
    private val database: ChatDatabase,
    private val archiveRepository: MemoryArchiveRepository,
    private val embeddingClient: MemoryEmbeddingClient,
    private val apiServiceFactory: ApiServiceFactory,
    private val provider: suspend () -> ProviderSettings?,
    private val isAllowed: suspend (MemoryRuntimeContext) -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val chunker: MemoryEmbeddingChunker = MemoryEmbeddingChunker(),
    private val maxResults: Int = 64,
) : MemoryVectorSearch {
    private val dao = database.memoryVectorDao()
    private val buildLocks = ConcurrentHashMap<String, Mutex>()

    init {
        require(maxResults > 0)
    }

    /** 仅导出当前配置有完整证明的缓存，不猜测旧 Provider 的 endpoint。 */
    internal suspend fun exportTransferIndexes(data: MemoryArchiveTransferData): List<MemoryTransferVectorIndex> {
        val settings = configuredProvider() ?: return emptyList()
        val configuration = try {
            configurationKey(settings)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        val endpoint = MemoryEmbeddingConfiguration.endpoint(settings, apiServiceFactory)
        val groups = data.records.groupBy { it.spaceId to it.personaKey }
        return buildList {
            for ((group, records) in groups) {
                val index = dao.listIndexes(group.first, group.second).firstOrNull {
                    it.active && it.status == READY && it.configurationKey == configuration &&
                            it.requestToken.isEmpty() && it.preprocessingVersion == MemoryEmbeddingChunker.PREPROCESSING_VERSION
                } ?: continue
                val manifest = MemoryVectorIndexPlan.parseRecords(index.recordsJson) ?: continue
                val byId = records.associateBy { it.id }
                val selected = manifest.map { ref -> byId[ref.recordId] }
                    .takeIf { it.none { row -> row == null } }
                    ?.filterNotNull() ?: continue
                val plan = MemoryVectorIndexPlan.create(selected, chunker)
                val rows = dao.listUsableEmbeddings(
                    index.spaceId, selected.firstOrNull()?.characterId ?: continue,
                    index.personaKey, index.vectorSpace, index.generation
                )
                if (index.recordsJson != plan.recordsJson || !plan.isComplete(index, rows)) continue
                add(
                    MemoryTransferVectorIndex(
                        index.spaceId,
                        index.personaKey,
                        settings.id,
                        endpoint,
                        settings.resolveFunctionModel(ProviderFunction.EMBEDDING),
                        index.preprocessingVersion,
                        index.dimensions,
                        configuration,
                        index.vectorSpace,
                        index.generation,
                        plan.records.map {
                            MemoryTransferVectorRecord(
                                it.recordId,
                                it.revision,
                                it.fingerprint,
                                it.chunkCount
                            )
                        },
                        rows.filter { plan.accepts(it, index.dimensions) }.map {
                            MemoryTransferVectorChunk(
                                it.recordId,
                                it.recordRevision,
                                it.chunkIndex,
                                it.spanStart,
                                it.spanEnd,
                                requireNotNull(
                                    MemoryVectorMath.decode(
                                        it.vector,
                                        index.dimensions
                                    )
                                ).toList()
                            )
                        })
                )
            }
        }
    }

    /** 用户明确恢复现成缓存；只校验和写库，绝不调用 INDEX/QUERY API。 */
    internal suspend fun restoreTransferIndex(
        context: MemoryRuntimeContext,
        imported: MemoryTransferVectorIndex,
        recordIds: Map<String, String>,
    ): Boolean {
        if (!isAllowed(context)) return false
        val settings = configuredProvider() ?: return false
        val configuration = try {
            configurationKey(settings)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (imported.providerId != settings.id || imported.endpoint != MemoryEmbeddingConfiguration.endpoint(
                settings,
                apiServiceFactory
            ) ||
            imported.model != settings.resolveFunctionModel(ProviderFunction.EMBEDDING) || imported.configurationKey != configuration ||
            imported.preprocessingVersion != MemoryEmbeddingChunker.PREPROCESSING_VERSION || imported.dimensions !in 1..MemoryVectorMath.MAX_DIMENSIONS ||
            imported.vectorSpace != vectorSpace(
                configuration,
                imported.dimensions
            ) || imported.originalGeneration <= 0L || imported.records.isEmpty()
        ) return false
        return database.withTransaction {
            val snapshot =
                archiveRepository.getRecallSnapshot(context) ?: return@withTransaction false
            val currentRecords = snapshot.records.associateBy { it.id }
            val importedRecords = mutableListOf<MemoryRecord>()
            for (ref in imported.records) {
                val localId = recordIds[ref.recordId] ?: return@withTransaction false
                val record = currentRecords[localId] ?: return@withTransaction false
                if (record.revision != ref.revision || MemoryArchiveIdentity.fingerprint(record.content) != ref.fingerprint) return@withTransaction false
                importedRecords += record
            }
            if (importedRecords.map { it.id }
                    .distinct().size != importedRecords.size) return@withTransaction false
            val plan = MemoryVectorIndexPlan.create(importedRecords, chunker)
            val expectedManifest = plan.records.associateBy { it.recordId }
            if (imported.records.any { ref ->
                    val expected = expectedManifest[recordIds[ref.recordId]]
                    expected == null || expected.chunkCount != ref.chunkCount || expected.revision != ref.revision || expected.fingerprint != ref.fingerprint
                } || imported.chunks.size != plan.chunks.size) return@withTransaction false
            val generation = dao.maxGeneration(context.spaceId) + 1L
            if (generation <= 0L) return@withTransaction false
            val rows = mutableListOf<MemoryEmbeddingEntity>()
            for (chunk in imported.chunks) {
                val localId = recordIds[chunk.recordId] ?: return@withTransaction false
                val values =
                    MemoryVectorMath.normalized(chunk.values.toFloatArray(), imported.dimensions)
                        ?: return@withTransaction false
                val row = MemoryEmbeddingEntity(
                    localId,
                    chunk.recordRevision,
                    chunk.chunkIndex,
                    imported.vectorSpace,
                    generation,
                    context.spaceId,
                    imported.dimensions,
                    chunk.spanStart,
                    chunk.spanEnd,
                    MemoryVectorMath.encode(values),
                    READY,
                    nowMillis()
                )
                if (!plan.accepts(row, imported.dimensions)) return@withTransaction false
                rows += row
            }
            if (rows.map { it.recordId to it.chunkIndex }
                    .distinct().size != plan.chunks.size) return@withTransaction false
            // 已有完整缓存也并入本机新代次，恢复资料不能丢掉其它合法记录的向量。
            val existingIndexes = dao.listIndexes(context.spaceId, context.personaKey).filter {
                it.active && it.status == READY && it.configurationKey == configuration && it.dimensions == imported.dimensions
            }
            val existingPlan = MemoryVectorIndexPlan.create(
                currentRecords.values.filter { it.id !in plan.revisions },
                chunker
            )
            val reusable = reusableRows(context, existingIndexes, existingPlan, imported.dimensions)
            val reusableRecords =
                existingPlan.records.filter { ref -> reusable.keys.count { it.first == ref.recordId } == ref.chunkCount }
                    .map { currentRecords.getValue(it.recordId) }
            val completePlan =
                MemoryVectorIndexPlan.create(importedRecords + reusableRecords, chunker)
            val reusableIds = reusableRecords.map { it.id }.toSet()
            rows += reusable.values.filter { it.recordId in reusableIds }
                .map { it.copy(vectorSpace = imported.vectorSpace, generation = generation) }
            if (!archiveRepository.validateRecallRecords(
                    context,
                    completePlan.revisions
                ) || !isCurrentConfiguration(context, configuration)
            ) return@withTransaction false
            val index = MemoryEmbeddingIndexEntity(
                context.spaceId,
                imported.vectorSpace,
                generation,
                settings.id,
                imported.model,
                imported.dimensions,
                imported.preprocessingVersion,
                READY,
                false,
                nowMillis(),
                context.personaKey,
                configuration,
                AppJson.gson.toJson(context),
                completePlan.recordsJson,
                completePlan.chunks.size,
                completePlan.chunks.size
            )
            if (!completePlan.isComplete(index, rows)) return@withTransaction false
            dao.upsertEmbeddings(rows)
            dao.deactivateConfiguration(context.spaceId, context.personaKey, configuration)
            dao.upsertIndex(index.copy(active = true))
            true
        }
    }

    override suspend fun search(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        queries: List<String>,
    ): List<MemoryVectorHit> = withContext(dispatcher) {
        if (queries.none { it.isNotBlank() } || records.isEmpty() || !isAllowed(context)) return@withContext emptyList()
        val settings = configuredProvider() ?: return@withContext emptyList()
        val configuration = configurationKey(settings)
        val allowedRecords = records.filter { belongsTo(it, context) }.associateBy { it.id }
        if (allowedRecords.isEmpty()) return@withContext emptyList()
        val snapshot = database.withTransaction {
            if (!archiveRepository.validateRecallRecords(
                    context,
                    allowedRecords.mapValues { it.value.revision })
            ) return@withTransaction null
            val index = dao.listIndexes(context.spaceId, context.personaKey).firstOrNull {
                it.active && it.status == READY && it.configurationKey == configuration &&
                        it.preprocessingVersion == MemoryEmbeddingChunker.PREPROCESSING_VERSION &&
                        it.dimensions in 1..MemoryVectorMath.MAX_DIMENSIONS
            } ?: return@withTransaction null
            val manifest =
                MemoryVectorIndexPlan.parseRecords(index.recordsJson)?.associateBy { it.recordId }
                    ?: return@withTransaction null
            val plan = MemoryVectorIndexPlan.create(allowedRecords.values.filter { record ->
                val saved = manifest[record.id]
                saved?.revision == record.revision && saved.fingerprint == MemoryArchiveIdentity.fingerprint(
                    record.content
                )
            }, chunker)
            val rows = usableRows(context, index).filter { plan.accepts(it, index.dimensions) }
            VectorSnapshot(index, rows.mapNotNull { row ->
                MemoryVectorMath.decode(row.vector, index.dimensions)
                    ?.let { VectorCandidate(row, it) }
            })
        } ?: return@withContext emptyList()
        if (snapshot.candidates.isEmpty()) return@withContext emptyList()

        val queryTexts = queries.flatMap { chunker.split(it) }.map { it.text }.distinct()
        val best = mutableMapOf<String, MemoryVectorHit>()
        val candidateRevisions =
            snapshot.candidates.associate { it.row.recordId to it.row.recordRevision }
        for (batch in queryTexts.chunked(MemoryEmbeddingChunker.BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            if (!isCurrentConfiguration(context, configuration) ||
                !archiveRepository.validateRecallRecords(context, candidateRevisions)
            ) return@withContext emptyList()
            val embedded = checkedBatch(
                embeddingClient.embed(
                    settings,
                    batch,
                    MemoryEmbeddingPurpose.QUERY,
                    snapshot.index.dimensions
                ),
                batch.size, snapshot.index.dimensions,
            )
            for (query in embedded.vectors) {
                for (candidate in snapshot.candidates) {
                    currentCoroutineContext().ensureActive()
                    val score = MemoryVectorMath.cosine(query, candidate.vector) ?: continue
                    if (score <= 0.0) continue
                    val row = candidate.row
                    val previous = best[row.recordId]
                    if (previous == null || score > previous.score ||
                        score == previous.score && row.chunkIndex < previous.chunkIndex
                    ) {
                        best[row.recordId] = MemoryVectorHit(
                            row.recordId,
                            row.recordRevision,
                            row.chunkIndex,
                            row.spanStart,
                            row.spanEnd,
                            score,
                        )
                    }
                }
            }
        }
        if (!isCurrentConfiguration(context, configuration)) return@withContext emptyList()
        val stillValid = database.withTransaction {
            val index = currentIndex(snapshot.index)
            index?.active == true && index.status == READY &&
                    archiveRepository.validateRecallRecords(context, candidateRevisions)
        }
        if (!stillValid) return@withContext emptyList()
        // 每条记录先聚合最佳段，再限制结果数，长记录或其他范围不会挤占名额。
        best.values.sortedWith(compareByDescending<MemoryVectorHit> { it.score }.thenBy { it.recordId })
            .take(maxResults)
    }

    suspend fun rebuild(
        context: MemoryRuntimeContext,
        forceRebuild: Boolean = false,
        retryUncertain: Boolean = false,
    ): MemoryVectorBuildResult = withContext(dispatcher) {
        if (!isAllowed(context)) return@withContext MemoryVectorBuildResult.Disabled
        if (archiveRepository.isProcessingPaused(context)) return@withContext MemoryVectorBuildResult.Failed(
            0L,
            "USER_PAUSED"
        )
        val settings = configuredProvider() ?: return@withContext MemoryVectorBuildResult.Disabled
        val configuration = try {
            configurationKey(settings)
        } catch (_: IllegalArgumentException) {
            return@withContext MemoryVectorBuildResult.Failed(
                0,
                MemoryEmbeddingError.CONFIGURATION.name
            )
        }
        val lockKey = MemoryArchiveIdentity.id(context.spaceId, context.personaKey, configuration)
        var pendingImportedRecords = 0
        val result = buildLocks.getOrPut(lockKey) { Mutex() }.withLock {
            if (!archiveRepository.validateRecallRecords(
                    context,
                    emptyMap()
                )
            ) return@withLock MemoryVectorBuildResult.Unavailable
            val snapshot = archiveRepository.getRecallSnapshot(context)
                ?: return@withLock MemoryVectorBuildResult.Unavailable
            val records = snapshot.records.filter { belongsTo(it, context) }
            val selection = automaticIndexSelection(
                context,
                records,
                snapshot.importedRecordIds,
                configuration,
                forceRebuild
            )
            pendingImportedRecords = selection.second
            val plan = selection.first
            if (plan.chunks.isEmpty()) return@withLock MemoryVectorBuildResult.Ready(0, 0, 0)
            val preparation =
                prepare(context, settings, configuration, plan, forceRebuild, retryUncertain)
            if (preparation is Preparation.Stop) return@withLock preparation.result
            var index = (preparation as Preparation.Continue).index
            var requestToken = ""
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (archiveRepository.isProcessingPaused(context)) return@withLock MemoryVectorBuildResult.Failed(
                        index.generation,
                        "USER_PAUSED"
                    )
                    if (!isCurrentConfiguration(
                            context,
                            configuration
                        )
                    ) return@withLock MemoryVectorBuildResult.Disabled
                    when (val next = claim(context, index, plan)) {
                        is NextBatch.Stop -> return@withLock next.result
                        is NextBatch.Complete -> {
                            index = next.index
                            return@withLock activate(context, index, plan)
                        }

                        is NextBatch.Request -> {
                            index = next.index
                            requestToken = next.index.requestToken
                            if (!isCurrentConfiguration(context, configuration)) {
                                abandonUnsent(index)
                                return@withLock MemoryVectorBuildResult.Disabled
                            }
                            if (archiveRepository.isProcessingPaused(context)) {
                                abandonUnsent(index)
                                return@withLock MemoryVectorBuildResult.Failed(
                                    index.generation,
                                    "USER_PAUSED"
                                )
                            }
                            val batch = checkedBatch(
                                embeddingClient.embed(
                                    settings,
                                    next.chunks.map { it.text },
                                    MemoryEmbeddingPurpose.INDEX,
                                    index.dimensions.takeIf { it > 0 },
                                ),
                                next.chunks.size, index.dimensions.takeIf { it > 0 },
                            )
                            // 已收到的合法结果先落盘；后续激活失败恢复时不再次请求模型。
                            val saved = withContext(NonCancellable + dispatcher) {
                                saveBatch(context, index, plan, next.chunks, batch)
                            } ?: return@withLock MemoryVectorBuildResult.Unavailable
                            index = saved
                            requestToken = ""
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                MemoryVectorBuildResult.Unavailable
            } catch (cancellation: CancellationException) {
                recordFailure(index, requestToken, PAUSED, "CANCELLED")
                throw cancellation
            } catch (failure: MemoryEmbeddingException) {
                val code = failure.code.name + (failure.httpStatus?.let { "_$it" } ?: "")
                recordFailure(index, requestToken, FAILED, code)
                MemoryVectorBuildResult.Failed(index.generation, code)
            } catch (_: Exception) {
                recordFailure(index, requestToken, FAILED, "STORAGE")
                AppLogger.w(TAG, "Index storage operation failed")
                MemoryVectorBuildResult.Failed(index.generation, "STORAGE")
            }
        }
        if (result is MemoryVectorBuildResult.Ready) result.copy(pendingImportedRecords = pendingImportedRecords) else result
    }

    /** 普通维护只复用完整导入缓存；新增 INDEX 费用必须来自用户的显式 FULL 确认。 */
    private suspend fun automaticIndexSelection(
        context: MemoryRuntimeContext,
        records: List<MemoryRecord>,
        importedIds: Set<String>,
        configuration: String,
        explicitRebuild: Boolean,
    ): Pair<MemoryVectorIndexPlan, Int> = database.withTransaction {
        val all = MemoryVectorIndexPlan.create(records, chunker)
        if (explicitRebuild || importedIds.isEmpty()) return@withTransaction all to 0
        val indexes = dao.listIndexes(context.spaceId, context.personaKey).filter {
            it.configurationKey == configuration && it.preprocessingVersion == MemoryEmbeddingChunker.PREPROCESSING_VERSION
        }
        // FULL 的冻结 manifest 是此前明确操作的范围；后来新导入的 ID 不继承这次授权。
        val fullWork = indexes.firstOrNull {
            !it.active && it.fullRebuild && it.status in setOf(
                PLANNED,
                REQUESTING,
                PAUSED,
                FAILED
            )
        }
        val frozen = fullWork?.let { MemoryVectorIndexPlan.parseRecords(it.recordsJson) }.orEmpty()
            .associateBy { it.recordId }
        val approved = all.records.filter { frozen[it.recordId] == it }.map { it.recordId }.toSet()
        val reusableIndexes = if (fullWork != null) listOf(fullWork) else indexes
        val dimensions =
            reusableIndexes.firstOrNull { it.dimensions in 1..MemoryVectorMath.MAX_DIMENSIONS }?.dimensions
                ?: 0
        val cached = if (dimensions == 0) emptySet() else {
            val rows = reusableRows(context, reusableIndexes, all, dimensions)
            all.records.filter { ref -> rows.keys.count { it.first == ref.recordId } == ref.chunkCount }
                .map { it.recordId }.toSet()
        }
        val selected =
            records.filter { it.id !in importedIds || it.id in cached || it.id in approved }
        MemoryVectorIndexPlan.create(
            selected,
            chunker
        ) to records.count { it.id in importedIds && it.id !in cached && it.id !in approved }
    }

    /** 只续建确定未请求的段；未知请求返回 NeedsReview，调用者不应循环付费重试。 */
    suspend fun resumePending(context: MemoryRuntimeContext): MemoryVectorBuildResult =
        rebuild(context)

    suspend fun progress(context: MemoryRuntimeContext): List<MemoryVectorIndexProgress> =
        withContext(dispatcher) {
            database.withTransaction {
                if (!archiveRepository.validateRecallRecords(
                        context,
                        emptyMap()
                    )
                ) return@withTransaction emptyList()
                val management = archiveRepository.getManagementContext(
                    context.characterId,
                    context.spaceId,
                    context.personaKey
                )
                    ?: return@withTransaction emptyList()
                archiveProgress(management)
            }
        }

    suspend fun archiveProgress(context: MemoryRuntimeContext): List<MemoryVectorIndexProgress> =
        withContext(dispatcher) {
            val settings = configuredProvider() ?: return@withContext emptyList()
            val configuration = try {
                configurationKey(settings)
            } catch (_: IllegalArgumentException) {
                return@withContext emptyList()
            }
            database.withTransaction {
                if (archiveRepository.getManagementContext(
                        context.characterId,
                        context.spaceId,
                        context.personaKey
                    ) != context
                ) return@withTransaction emptyList()
                val indexes = dao.listIndexes(context.spaceId, context.personaKey).filter {
                    it.configurationKey == configuration && it.preprocessingVersion == MemoryEmbeddingChunker.PREPROCESSING_VERSION
                }
                val records = database.memoryArchiveDao()
                    .listRecallRecords(context.spaceId, context.characterId, context.personaKey)
                    .filter { it.content.isNotBlank() }.map { it.toDomain() }
                val importedIds = mutableSetOf<String>()
                for (record in records) {
                    val sources =
                        database.memoryArchiveDao().listRecordSources(record.id, record.revision)
                    if (sources.any { ref ->
                            database.memoryArchiveDao().getSource(ref.sourceId)?.let {
                                it.kind == "IMPORTED" && it.status == "AVAILABLE" && it.messageRevision == ref.sourceRevision
                            } == true
                        }) importedIds += record.id
                }
                val cachedIds = mutableSetOf<String>()
                val progress = indexes.map { index ->
                    val refs = MemoryVectorIndexPlan.parseRecords(index.recordsJson)
                    val manifestRecords = refs?.map { ref ->
                        archiveRepository.getRecordDetails(
                            context,
                            ref.recordId
                        )?.record
                    }
                    val plan = manifestRecords?.takeIf {
                        it.all { record ->
                            record != null && belongsTo(
                                record,
                                context
                            )
                        }
                    }
                        ?.filterNotNull()?.let { MemoryVectorIndexPlan.create(it, chunker) }
                    val rows = usableRows(context, index)
                    val complete = plan != null && plan.isComplete(index, rows)
                    if (plan != null) {
                        val validKeys = rows.filter { plan.accepts(it, index.dimensions) }
                            .map { it.recordId to it.chunkIndex }.toSet()
                        plan.records.filter { ref ->
                            validKeys.count { it.first == ref.recordId } == ref.chunkCount &&
                                    refs?.any { it.recordId == ref.recordId && it.fingerprint == ref.fingerprint && it.revision == ref.revision } == true
                        }.forEach { cachedIds += it.recordId }
                    }
                    MemoryVectorIndexProgress(
                        index.generation,
                        if (index.status == READY && !complete) STALE else index.status,
                        index.active && complete,
                        index.totalChunks,
                        index.completedChunks,
                        index.requestToken.isNotEmpty() && index.status != RETRIED,
                        index.failureCode
                    )
                }
                val pending = (importedIds - cachedIds).size
                if (progress.isEmpty() && pending > 0) listOf(
                    MemoryVectorIndexProgress(
                        0L,
                        "IMPORTED_PENDING",
                        false,
                        0,
                        0,
                        false,
                        "",
                        pending
                    )
                )
                else progress.map {
                    it.copy(
                        status = if (it.status == READY && pending > 0) "PARTIAL" else it.status,
                        pendingImportedRecords = pending
                    )
                }
            }
        }

    private suspend fun prepare(
        context: MemoryRuntimeContext,
        settings: ProviderSettings,
        configuration: String,
        plan: MemoryVectorIndexPlan,
        force: Boolean,
        retry: Boolean,
    ): Preparation = database.withTransaction {
        if (archiveRepository.isProcessingPaused(context)) return@withTransaction Preparation.Stop(
            MemoryVectorBuildResult.Failed(0L, "USER_PAUSED")
        )
        if (!archiveRepository.validateRecallRecords(
                context,
                plan.revisions
            )
        ) return@withTransaction Preparation.Stop(MemoryVectorBuildResult.Unavailable)
        val indexes = dao.listIndexes(context.spaceId, context.personaKey)
            .filter { it.configurationKey == configuration }
        val ready =
            indexes.firstOrNull { it.active && it.status == READY && it.recordsJson == plan.recordsJson }
        val unfinished = indexes.firstOrNull {
            !it.active && it.generation > (ready?.generation ?: 0) &&
                    it.status in setOf(PLANNED, REQUESTING, PAUSED, FAILED) &&
                    (it.recordsJson == plan.recordsJson || it.fullRebuild)
        }
        if (!force) {
            if (unfinished != null && unfinished.requestToken.isNotEmpty() && !retry) {
                return@withTransaction Preparation.Stop(
                    MemoryVectorBuildResult.NeedsReview(
                        unfinished.generation,
                        unfinished.completedChunks
                    )
                )
            }
            if (unfinished != null && unfinished.recordsJson == plan.recordsJson && unfinished.requestToken.isEmpty()) {
                val rebound = unfinished.copy(
                    contextJson = AppJson.gson.toJson(context),
                    status = PLANNED,
                    failureCode = "",
                    updatedAt = nowMillis()
                )
                dao.upsertIndex(rebound)
                return@withTransaction Preparation.Continue(rebound)
            }
            if (unfinished == null && ready != null && plan.isComplete(
                    ready,
                    usableRows(context, ready)
                )
            ) {
                return@withTransaction Preparation.Stop(readyResult(ready, plan))
            }
        }
        // FULL 中断后的成功段只能来自该次重建，不能拿旧 active 的向量补足新代次。
        val fullRebuild = force || unfinished?.fullRebuild == true
        val reuseIndexes = when {
            force -> emptyList()
            unfinished?.fullRebuild == true -> listOf(unfinished)
            else -> indexes
        }
        val dimensions = reuseIndexes.firstOrNull {
            it.dimensions in 1..MemoryVectorMath.MAX_DIMENSIONS && it.preprocessingVersion == MemoryEmbeddingChunker.PREPROCESSING_VERSION
        }?.dimensions ?: 0
        val reusable = if (dimensions == 0) emptyMap() else reusableRows(
            context,
            reuseIndexes,
            plan,
            dimensions
        )
        val needed = plan.chunks.map { it.ref }.filter { it.key !in reusable }
        val uncertain = indexes.filter { index ->
            index.requestToken.isNotEmpty() && index.status != RETRIED &&
                    (index == unfinished || pendingOverlaps(index, needed))
        }
        if (uncertain.isNotEmpty() && !retry) {
            return@withTransaction Preparation.Stop(
                MemoryVectorBuildResult.NeedsReview(
                    uncertain.first().generation,
                    uncertain.first().completedChunks
                ),
            )
        }
        if (retry) {
            uncertain.forEach {
                dao.upsertIndex(
                    it.copy(
                        status = RETRIED,
                        active = false,
                        updatedAt = nowMillis()
                    )
                )
            }
        }
        val generation = Math.addExact(dao.maxGeneration(context.spaceId), 1L)
        val index = MemoryEmbeddingIndexEntity(
            spaceId = context.spaceId,
            personaKey = context.personaKey,
            vectorSpace = vectorSpace(configuration, dimensions),
            generation = generation,
            providerId = settings.id,
            model = settings.resolveFunctionModel(ProviderFunction.EMBEDDING),
            dimensions = dimensions,
            preprocessingVersion = MemoryEmbeddingChunker.PREPROCESSING_VERSION,
            status = PLANNED,
            active = false,
            updatedAt = nowMillis(),
            configurationKey = configuration,
            contextJson = AppJson.gson.toJson(context),
            recordsJson = plan.recordsJson,
            totalChunks = plan.chunks.size,
            completedChunks = reusable.size,
            pendingBatchJson = "[]",
            requestToken = "",
            failureCode = "",
            fullRebuild = fullRebuild,
        )
        dao.upsertIndex(index)
        dao.upsertEmbeddings(reusable.values.map {
            it.copy(
                vectorSpace = index.vectorSpace,
                generation = generation,
                updatedAt = nowMillis()
            )
        })
        Preparation.Continue(index)
    }

    private suspend fun claim(
        context: MemoryRuntimeContext,
        previous: MemoryEmbeddingIndexEntity,
        plan: MemoryVectorIndexPlan,
    ): NextBatch = database.withTransaction {
        val index = currentIndex(previous) ?: return@withTransaction NextBatch.Stop(
            MemoryVectorBuildResult.Unavailable
        )
        if (archiveRepository.isProcessingPaused(context)) return@withTransaction NextBatch.Stop(
            MemoryVectorBuildResult.Failed(index.generation, "USER_PAUSED")
        )
        if (index.requestToken.isNotEmpty()) return@withTransaction NextBatch.Stop(
            MemoryVectorBuildResult.NeedsReview(index.generation, index.completedChunks)
        )
        if (index.contextJson != AppJson.gson.toJson(context) || index.recordsJson != plan.recordsJson ||
            index.status !in setOf(PLANNED, PAUSED, FAILED) ||
            !archiveRepository.validateRecallRecords(context, plan.revisions)
        ) return@withTransaction NextBatch.Stop(MemoryVectorBuildResult.Unavailable)
        val readyKeys = usableRows(context, index).filter { plan.accepts(it, index.dimensions) }
            .map { it.recordId to it.chunkIndex }.toSet()
        val pending = plan.chunks.filter { it.ref.key !in readyKeys }
        if (pending.isEmpty()) return@withTransaction NextBatch.Complete(index)
        val blocked = dao.listIndexes(context.spaceId, context.personaKey).any {
            it.configurationKey == index.configurationKey && it.requestToken.isNotEmpty() && it.status != RETRIED &&
                    pendingOverlaps(it, pending.map { chunk -> chunk.ref })
        }
        if (blocked) return@withTransaction NextBatch.Stop(
            MemoryVectorBuildResult.NeedsReview(
                index.generation,
                readyKeys.size
            )
        )
        val batch = pending.take(MemoryEmbeddingChunker.BATCH_SIZE)
        val claimed = index.copy(
            status = REQUESTING,
            pendingBatchJson = AppJson.gson.toJson(batch.map { it.ref }),
            requestToken = UUID.randomUUID().toString(),
            completedChunks = readyKeys.size,
            failureCode = "",
            updatedAt = nowMillis(),
        )
        dao.upsertIndex(claimed)
        NextBatch.Request(claimed, batch)
    }

    private suspend fun saveBatch(
        context: MemoryRuntimeContext,
        claimed: MemoryEmbeddingIndexEntity,
        plan: MemoryVectorIndexPlan,
        chunks: List<PlannedMemoryVectorChunk>,
        batch: MemoryEmbeddingBatch,
    ): MemoryEmbeddingIndexEntity? {
        if (!isCurrentConfiguration(context, claimed.configurationKey)) {
            recordFailure(claimed, claimed.requestToken, PAUSED, "DISABLED")
            return null
        }
        return database.withTransaction {
            val current = currentIndex(claimed) ?: return@withTransaction null
            if (current.requestToken != claimed.requestToken || current.status != REQUESTING ||
                current.contextJson != AppJson.gson.toJson(context) || current.recordsJson != plan.recordsJson
            ) return@withTransaction null
            if (!archiveRepository.validateRecallRecords(context, plan.revisions)) {
                dao.upsertIndex(
                    current.copy(
                        status = STALE,
                        failureCode = "SOURCE_CHANGED",
                        updatedAt = nowMillis()
                    )
                )
                return@withTransaction null
            }
            val resolved = current.copy(
                vectorSpace = vectorSpace(current.configurationKey, batch.dimensions),
                dimensions = batch.dimensions,
                status = PLANNED,
                pendingBatchJson = "[]",
                requestToken = "",
                completedChunks = current.completedChunks + chunks.size,
                updatedAt = nowMillis(),
            )
            dao.upsertIndex(resolved)
            dao.upsertEmbeddings(chunks.zip(batch.vectors).map { (chunk, vector) ->
                MemoryEmbeddingEntity(
                    chunk.ref.recordId,
                    chunk.ref.revision,
                    chunk.ref.chunkIndex,
                    resolved.vectorSpace,
                    resolved.generation,
                    context.spaceId,
                    batch.dimensions,
                    chunk.ref.start,
                    chunk.ref.end,
                    MemoryVectorMath.encode(vector),
                    READY,
                    nowMillis(),
                )
            })
            if (current.vectorSpace != resolved.vectorSpace) {
                dao.deleteIndex(
                    current.spaceId,
                    current.personaKey,
                    current.vectorSpace,
                    current.generation
                )
            }
            resolved
        }
    }

    private suspend fun activate(
        context: MemoryRuntimeContext,
        previous: MemoryEmbeddingIndexEntity,
        plan: MemoryVectorIndexPlan,
    ): MemoryVectorBuildResult {
        if (!isCurrentConfiguration(
                context,
                previous.configurationKey
            )
        ) return MemoryVectorBuildResult.Disabled
        return database.withTransaction {
            val index =
                currentIndex(previous) ?: return@withTransaction MemoryVectorBuildResult.Unavailable
            if (archiveRepository.isProcessingPaused(context)) return@withTransaction MemoryVectorBuildResult.Failed(
                index.generation,
                "USER_PAUSED"
            )
            if (index.requestToken.isNotEmpty() || index.contextJson != AppJson.gson.toJson(context) ||
                !archiveRepository.validateRecallRecords(context, plan.revisions) ||
                !plan.isComplete(index, usableRows(context, index))
            ) return@withTransaction MemoryVectorBuildResult.Unavailable
            val indexes = dao.listIndexes(context.spaceId, context.personaKey)
                .filter { it.configurationKey == index.configurationKey }
            if (indexes.any { it.active && it.generation > index.generation }) {
                removeSupersededIndex(index)
                return@withTransaction MemoryVectorBuildResult.Unavailable
            }
            dao.deactivateConfiguration(context.spaceId, context.personaKey, index.configurationKey)
            val active = index.copy(
                status = READY,
                active = true,
                completedChunks = plan.chunks.size,
                failureCode = "",
                updatedAt = nowMillis()
            )
            dao.upsertIndex(active)
            // 完整新代次已就绪才能清派生缓存；更晚的构建和未决请求凭据继续保留。
            indexes.filter { it.generation < active.generation }
                .forEach { removeSupersededIndex(it) }
            readyResult(active, plan)
        }
    }

    private suspend fun removeSupersededIndex(index: MemoryEmbeddingIndexEntity) {
        dao.deleteGenerationEmbeddings(
            index.spaceId,
            index.personaKey,
            index.vectorSpace,
            index.generation
        )
        if (index.requestToken.isEmpty()) {
            dao.deleteIndex(index.spaceId, index.personaKey, index.vectorSpace, index.generation)
        } else if (index.status != RETRIED) {
            dao.upsertIndex(
                index.copy(
                    active = false,
                    status = STALE,
                    failureCode = "SUPERSEDED",
                    updatedAt = nowMillis()
                )
            )
        }
    }

    private suspend fun reusableRows(
        context: MemoryRuntimeContext,
        indexes: List<MemoryEmbeddingIndexEntity>,
        plan: MemoryVectorIndexPlan,
        dimensions: Int,
    ): Map<Pair<String, Int>, MemoryEmbeddingEntity> {
        val result = mutableMapOf<Pair<String, Int>, MemoryEmbeddingEntity>()
        val expected = plan.records.associateBy { it.recordId }
        for (index in indexes) {
            if (index.dimensions != dimensions || index.preprocessingVersion != MemoryEmbeddingChunker.PREPROCESSING_VERSION) continue
            val manifest =
                MemoryVectorIndexPlan.parseRecords(index.recordsJson)?.associateBy { it.recordId }
                    ?: continue
            usableRows(context, index).forEach { row ->
                if (manifest[row.recordId] == expected[row.recordId] && plan.accepts(
                        row,
                        dimensions
                    )
                ) {
                    result.putIfAbsent(row.recordId to row.chunkIndex, row)
                }
            }
        }
        return result
    }

    private fun pendingOverlaps(
        index: MemoryEmbeddingIndexEntity,
        needed: List<MemoryVectorChunkRef>
    ): Boolean {
        if (needed.isEmpty()) return false
        val pending = MemoryVectorIndexPlan.parsePending(index.pendingBatchJson) ?: return true
        if (pending.isEmpty()) return true
        val neededSet = needed.toSet()
        return pending.any { it in neededSet }
    }

    private suspend fun recordFailure(
        index: MemoryEmbeddingIndexEntity,
        token: String,
        status: String,
        code: String
    ) {
        withContext(NonCancellable + dispatcher) {
            try {
                database.withTransaction {
                    val current = currentIndex(index) ?: return@withTransaction
                    if (current.requestToken != token || current.status == RETRIED || current.active) return@withTransaction
                    dao.upsertIndex(
                        current.copy(
                            status = status,
                            failureCode = code,
                            updatedAt = nowMillis()
                        )
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 原持久 requestToken 仍阻止重复请求，写失败不能被解释为已完成。
                AppLogger.w(TAG, "Could not persist index failure state")
            }
        }
    }

    private suspend fun abandonUnsent(index: MemoryEmbeddingIndexEntity) =
        database.withTransaction {
            val current = currentIndex(index) ?: return@withTransaction
            if (current.requestToken == index.requestToken && current.status == REQUESTING) {
                dao.upsertIndex(
                    current.copy(
                        status = PAUSED,
                        requestToken = "",
                        pendingBatchJson = "[]",
                        failureCode = "DISABLED",
                        updatedAt = nowMillis()
                    )
                )
            }
        }

    private suspend fun usableRows(
        context: MemoryRuntimeContext,
        index: MemoryEmbeddingIndexEntity
    ) =
        dao.listUsableEmbeddings(
            context.spaceId,
            context.characterId,
            context.personaKey,
            index.vectorSpace,
            index.generation
        )

    private suspend fun currentIndex(index: MemoryEmbeddingIndexEntity) =
        dao.getIndex(index.spaceId, index.personaKey, index.vectorSpace, index.generation)

    private suspend fun configuredProvider(): ProviderSettings? = provider()?.takeIf {
        it.enabled && it.id.isNotBlank() && it.hasBaseCredentials() &&
                it.resolvedApiProtocol() == ProviderApiProtocol.OPENAI_COMPATIBLE &&
                it.resolveFunctionModel(ProviderFunction.EMBEDDING).isNotBlank()
    }

    private fun configurationKey(settings: ProviderSettings): String =
        MemoryEmbeddingConfiguration.key(settings, apiServiceFactory)

    private suspend fun isCurrentConfiguration(
        context: MemoryRuntimeContext,
        expected: String
    ): Boolean {
        if (!isAllowed(context)) return false
        val current = configuredProvider() ?: return false
        return try {
            configurationKey(current) == expected
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun checkedBatch(
        batch: MemoryEmbeddingBatch,
        count: Int,
        expected: Int?
    ): MemoryEmbeddingBatch {
        if (batch.vectors.size != count || batch.dimensions !in 1..MemoryVectorMath.MAX_DIMENSIONS ||
            expected != null && expected != batch.dimensions
        ) throw MemoryEmbeddingException(MemoryEmbeddingError.PROTOCOL)
        return MemoryEmbeddingBatch(batch.vectors.map {
            MemoryVectorMath.normalized(it, batch.dimensions) ?: throw MemoryEmbeddingException(
                MemoryEmbeddingError.PROTOCOL
            )
        }, batch.dimensions)
    }

    private fun belongsTo(record: MemoryRecord, context: MemoryRuntimeContext): Boolean =
        record.spaceId == context.spaceId && record.characterId == context.characterId && record.personaKey == context.personaKey &&
                record.status in setOf(MemoryRecordStatus.ACTIVE, MemoryRecordStatus.ARCHIVED)

    private fun vectorSpace(configuration: String, dimensions: Int): String =
        MemoryEmbeddingConfiguration.vectorSpace(configuration, dimensions)

    private fun readyResult(index: MemoryEmbeddingIndexEntity, plan: MemoryVectorIndexPlan) =
        MemoryVectorBuildResult.Ready(index.generation, plan.records.size, plan.chunks.size)

    private data class VectorCandidate(val row: MemoryEmbeddingEntity, val vector: FloatArray)
    private data class VectorSnapshot(
        val index: MemoryEmbeddingIndexEntity,
        val candidates: List<VectorCandidate>
    )

    private sealed interface Preparation {
        data class Continue(val index: MemoryEmbeddingIndexEntity) : Preparation
        data class Stop(val result: MemoryVectorBuildResult) : Preparation
    }

    private sealed interface NextBatch {
        data class Request(
            val index: MemoryEmbeddingIndexEntity,
            val chunks: List<PlannedMemoryVectorChunk>
        ) : NextBatch

        data class Complete(val index: MemoryEmbeddingIndexEntity) : NextBatch
        data class Stop(val result: MemoryVectorBuildResult) : NextBatch
    }

    private companion object {
        const val TAG = "MemoryVectorIndex"
        const val PLANNED = "PLANNED"
        const val REQUESTING = "REQUESTING"
        const val READY = "READY"
        const val FAILED = "FAILED"
        const val PAUSED = "PAUSED"
        const val STALE = "STALE"
        const val RETRIED = "RETRIED"
    }
}
