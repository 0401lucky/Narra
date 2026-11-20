package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingError
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingException
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryArchiveVectorTransferTest {
    @Test
    fun cachedVectorsSurviveImport() = runBlocking {
        MemoryVectorTestFixture(persistent = true).use { source ->
            MemoryVectorTestFixture(persistent = true).use { destination ->
                val from = source.context()
                val content = "长段落。".repeat(800) + "\n尾部：芮珩等在706号房。"
                val record = source.addRecord(from, content)
                source.api.vector =
                    { if ("706号房" in it) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f) }
                val sourceVectors = source.repository()
                assertTrue(sourceVectors.rebuild(from) is MemoryVectorBuildResult.Ready)
                val sourceTransfer = source.transfer(sourceVectors)
                val codec = ContextTransferCodec()
                val bundle = codec.decode(
                    codec.encode(
                        ContextDataBundle(
                            version = 2,
                            exportedAt = source.now,
                            memoryArchive = sourceTransfer.exportArchive()
                        )
                    )
                )
                assertEquals(1, bundle.memoryArchive!!.vectors.size)
                assertEquals(
                    MemoryEmbeddingChunker().split(content).size,
                    bundle.memoryArchive!!.vectors.single().chunks.size
                )
                val target = destination.context()
                val existing = destination.addRecord(target, "已有本机合法向量")
                val targetVectors = destination.repository()
                targetVectors.rebuild(target)
                destination.api.calls.clear()
                val repository = destination.transfer(targetVectors)
                val plan = repository.prepareImport(bundle, targetsFor(bundle, target))
                val result =
                    repository.importArchive(bundle, plan) as MemoryArchiveImportResult.Imported
                assertEquals(1, result.restoredVectorIndexes)
                assertTrue(destination.api.calls.isEmpty())
                destination.reopen()
                val vectors = destination.repository()
                val id =
                    result.objectMappings.single { it.kind == MemoryTransferObjectKind.RECORD && it.originalId == record.id }.localId
                val imported =
                    requireNotNull(destination.archive.getRecordDetails(target, id)).record
                val index = destination.database.memoryVectorDao()
                    .listIndexes(target.spaceId, target.personaKey).single { it.active }
                assertEquals(2L, index.generation)
                assertFalse(index.recordsJson.contains(record.id))
                assertTrue(index.recordsJson.contains(id) && index.recordsJson.contains(existing.id))
                val hit = vectors.search(target, listOf(imported), listOf("706号房")).single()
                assertTrue(content.substring(hit.spanStart, hit.spanEnd).contains("706号房"))
                assertTrue(destination.api.indexCalls().isEmpty())
                assertEquals(1, destination.api.queryCalls().size)
                assertEquals(
                    "READY",
                    vectors.archiveProgress(
                        target.copy(
                            conversationId = "",
                            scenarioId = "",
                            runId = ""
                        )
                    ).single { it.active }.status
                )
                destination.provider = destination.provider!!.copy(id = "different-provider")
                val changed = vectors.archiveProgress(
                    target.copy(
                        conversationId = "",
                        scenarioId = "",
                        runId = ""
                    )
                )
                assertTrue(changed.none { it.active || it.status == "READY" })
                assertEquals(1, changed.single().pendingImportedRecords)
            }
        }
    }

    @Test
    fun incompatibleCacheKeepsBodies() = runBlocking {
        MemoryVectorTestFixture().use { source ->
            val context = source.context()
            val record = source.addRecord(context, "长文本。".repeat(450) + "合成尾部706")
            val vectorRepository = source.repository()
            vectorRepository.rebuild(context)
            val graph = source.transfer(vectorRepository).exportArchive()
            val valid = graph.vectors.single()
            val cases: List<(MemoryTransferVectorIndex) -> MemoryTransferVectorIndex> = listOf(
                { it.copy(providerId = "different") },
                { it.copy(endpoint = "https://other.example.invalid/v1/") },
                { it.copy(model = "another-model") },
                { it.copy(preprocessingVersion = 99) },
                { it.copy(dimensions = 3) },
                { it.copy(configurationKey = "0".repeat(64)) },
                { it.copy(records = it.records.map { ref -> ref.copy(revision = ref.revision + 1) }) },
                { it.copy(records = it.records.map { ref -> ref.copy(fingerprint = "0".repeat(64)) }) },
                {
                    it.copy(chunks = it.chunks.mapIndexed { index, chunk ->
                        if (index == 0) chunk.copy(
                            spanStart = chunk.spanStart + 1
                        ) else chunk
                    })
                },
                { it.copy(chunks = it.chunks.dropLast(1)) },
                {
                    it.copy(chunks = it.chunks.map { chunk ->
                        chunk.copy(
                            values = listOf(
                                Float.NaN,
                                1f
                            )
                        )
                    })
                },
                {
                    it.copy(chunks = it.chunks.map { chunk ->
                        chunk.copy(
                            values = listOf(
                                0f,
                                0f
                            )
                        )
                    })
                },
            )
            for (mutate in cases) MemoryVectorTestFixture().use { destination ->
                val target = destination.context()
                val bundle = ContextDataBundle(
                    version = 2,
                    exportedAt = source.now,
                    memoryArchive = graph.copy(vectors = listOf(mutate(valid)))
                )
                val transfer = destination.transfer(destination.repository())
                val result = transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                ) as MemoryArchiveImportResult.Imported
                assertEquals(0, result.restoredVectorIndexes)
                assertEquals(1, result.vectorsNeedingRebuild)
                assertEquals(
                    record.content,
                    requireNotNull(destination.archive.getRecallSnapshot(target)).records.single().content
                )
                assertTrue(
                    destination.database.memoryVectorDao()
                        .listIndexes(target.spaceId, target.personaKey).none { it.active })
                assertTrue(destination.api.calls.isEmpty())
            }
        }
    }

    @Test
    fun userPauseKeepsReceivedChunks() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成经历：暂停时仍保存现成向量")
            val originalAnswer = fixture.api.answer
            fixture.api.answer = { call ->
                val result = originalAnswer(call)
                fixture.archive.setProcessingPaused(context, 0L, true)
                result
            }
            val vectors = fixture.repository()
            val first = vectors.rebuild(context)
            assertEquals("USER_PAUSED", (first as MemoryVectorBuildResult.Failed).code)
            assertEquals(1, fixture.api.indexCalls().size)
            val index =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .single()
            assertEquals(index.totalChunks, index.completedChunks)
            assertEquals("", index.requestToken)
            vectors.resumePending(context)
            assertEquals(1, fixture.api.indexCalls().size)
            fixture.archive.setProcessingPaused(context, 1L, false)
            assertTrue(vectors.resumePending(context) is MemoryVectorBuildResult.Ready)
            assertEquals(1, fixture.api.indexCalls().size)
            fixture.archive.setProcessingPaused(context, 2L, true)
            assertTrue(
                vectors.search(
                    context,
                    requireNotNull(fixture.archive.getRecallSnapshot(context)).records,
                    listOf("经历")
                ).isNotEmpty()
            )
        }
    }

    @Test
    fun laterAutomaticWorkDoesNotChargeImportedText() = runBlocking {
        MemoryVectorTestFixture().use { source ->
            MemoryVectorTestFixture(persistent = true).use { destination ->
                val from = source.context()
                val importedText = "仅用户明确重建才索引的导入哨兵706"
                source.addRecord(from, importedText)
                val graph =
                    source.transfer(source.repository()).exportArchive(includeVectors = false)
                val bundle =
                    ContextDataBundle(version = 2, exportedAt = source.now, memoryArchive = graph)
                val target = destination.context()
                val transfer = destination.transfer(destination.repository())
                transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                )
                destination.reopen()
                val vectors = destination.repository()
                val importedId =
                    requireNotNull(destination.archive.getRecallSnapshot(target)).records.single().id
                val initial = vectors.resumePending(target) as MemoryVectorBuildResult.Ready
                assertEquals(1, initial.pendingImportedRecords)
                assertEquals(0, initial.records)
                assertTrue(destination.api.calls.isEmpty())
                val ordinary = destination.addRecord(target, "后续普通新消息原料")
                val partial = vectors.rebuild(target) as MemoryVectorBuildResult.Ready
                assertEquals(1, partial.pendingImportedRecords)
                assertEquals(
                    listOf(ordinary.content),
                    destination.api.indexCalls().flatMap { it.inputs })
                val progress = vectors.progress(target).single { it.active }
                assertEquals("PARTIAL", progress.status)
                assertEquals(1, progress.pendingImportedRecords)
                val current = destination.database.memoryVectorDao()
                    .listIndexes(target.spaceId, target.personaKey).single { it.active }
                assertFalse(current.recordsJson.contains(importedId))
                destination.api.calls.clear()
                val explicit =
                    vectors.rebuild(target, forceRebuild = true) as MemoryVectorBuildResult.Ready
                assertEquals(0, explicit.pendingImportedRecords)
                assertTrue(destination.api.indexCalls().flatMap { it.inputs }
                    .contains(importedText))
                destination.api.calls.clear()
                destination.addRecord(target, "另一次普通新消息")
                vectors.rebuild(target)
                assertFalse(destination.api.indexCalls().flatMap { it.inputs }
                    .contains(importedText))
                val imported =
                    requireNotNull(destination.archive.getRecallSnapshot(target)).records.single { it.content == importedText }
                assertTrue(vectors.search(target, listOf(imported), listOf("706")).isNotEmpty())
            }
        }
    }

    @Test
    fun explicitImportedWorkResumesFrozenScope() = runBlocking {
        MemoryVectorTestFixture().use { source ->
            MemoryVectorTestFixture(persistent = true).use { destination ->
                val from = source.context()
                val content =
                    (0 until 4000).joinToString("\n") { "合成导入经历第${it}段：芮珩在706号港湾。" }
                source.addRecord(from, content)
                val graph =
                    source.transfer(source.repository()).exportArchive(includeVectors = false)
                val bundle =
                    ContextDataBundle(version = 2, exportedAt = source.now, memoryArchive = graph)
                val target = destination.context()
                val repository = destination.transfer(destination.repository())
                repository.importArchive(
                    bundle,
                    repository.prepareImport(bundle, targetsFor(bundle, target))
                )
                val originalAnswer = destination.api.answer
                destination.api.answer = { call ->
                    val response = originalAnswer(call)
                    destination.archive.setProcessingPaused(target, 0L, true)
                    response
                }
                val paused = destination.repository()
                    .rebuild(target, forceRebuild = true) as MemoryVectorBuildResult.Failed
                assertEquals("USER_PAUSED", paused.code)
                assertEquals(1, destination.api.indexCalls().size)
                val frozen = destination.database.memoryVectorDao()
                    .listIndexes(target.spaceId, target.personaKey).single()
                assertTrue(frozen.fullRebuild)
                assertEquals(MemoryEmbeddingChunker.BATCH_SIZE, frozen.completedChunks)
                assertTrue(frozen.totalChunks > frozen.completedChunks)
                destination.reopen()
                destination.api.answer = originalAnswer
                destination.archive.setProcessingPaused(target, 1L, false)
                val resumed =
                    destination.repository().resumePending(target) as MemoryVectorBuildResult.Ready
                assertEquals(frozen.generation, resumed.generation)
                assertEquals(0, resumed.pendingImportedRecords)
                val chunks = MemoryEmbeddingChunker().split(content)
                assertEquals(
                    chunks.map { it.text },
                    destination.api.indexCalls().flatMap { it.inputs })
                destination.api.answer =
                    { throw MemoryEmbeddingException(MemoryEmbeddingError.TIMEOUT) }
                assertTrue(
                    destination.repository()
                        .rebuild(target, forceRebuild = true) is MemoryVectorBuildResult.Failed
                )
                val calls = destination.api.indexCalls().size
                destination.reopen()
                destination.api.answer = originalAnswer
                assertTrue(
                    destination.repository()
                        .resumePending(target) is MemoryVectorBuildResult.NeedsReview
                )
                assertEquals(calls, destination.api.indexCalls().size)
            }
        }
    }

    private fun MemoryVectorTestFixture.transfer(vectors: RoomMemoryVectorRepository) =
        RoomMemoryArchiveTransferRepository(
            database,
            archive,
            vectors,
            isCharacterAvailable = { it in characters },
            nowProvider = { now },
        )
}
