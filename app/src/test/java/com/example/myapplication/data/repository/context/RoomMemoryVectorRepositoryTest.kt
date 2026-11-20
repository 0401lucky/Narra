package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.context.MemoryVectorMath
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingError
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingException
import com.example.myapplication.model.MemoryEmbeddingBatch
import com.example.myapplication.model.MemoryEmbeddingPurpose
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemoryVectorBuildResult
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.testutil.MemoryVectorTestFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomMemoryVectorRepositoryTest {
    @Test
    fun rebuild_longBody_preservesEveryChunkAndRecallsTail() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val content = longBody() + "\n稀有细节：林芷在706号房等我。"
            val record = fixture.addRecord(context, content)
            fixture.api.vector =
                { if ("林芷" in it) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f) }
            val repository = fixture.repository()
            val result = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            val chunks = MemoryEmbeddingChunker().split(content)
            assertTrue(chunks.size > MemoryEmbeddingChunker.BATCH_SIZE)
            assertEquals(chunks.size, result.chunks)
            assertEquals(chunks.map { it.text }, fixture.api.indexCalls().flatMap { it.inputs })
            val index =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .single { it.active }
            assertEquals(chunks.size, index.completedChunks)
            assertEquals("READY", index.status)
            assertFalse(index.vectorSpace.startsWith("pending:"))
            val hits = repository.search(context, listOf(record), listOf("林芷约的房号"))
            assertEquals(record.id, hits.single().recordId)
            assertTrue(hits.single().spanStart > content.length - MemoryEmbeddingChunker.MAX_CHARS * 2)
            assertTrue(
                "706号房" in content.substring(
                    hits.single().spanStart,
                    hits.single().spanEnd
                )
            )
            assertEquals(
                content,
                fixture.archive.getRecordDetails(context, record.id)!!.record.content
            )
        }
    }

    @Test
    fun rebuild_disabledConfigurationAndPolicy_makeNoRequests() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "合成经历")
            val repository = fixture.repository()
            val configured = fixture.provider
            fixture.provider = null
            assertEquals(MemoryVectorBuildResult.Disabled, repository.rebuild(context))
            assertTrue(repository.search(context, listOf(record), listOf("经历")).isEmpty())
            fixture.provider =
                configured!!.copy(embeddingModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT)
            assertEquals(MemoryVectorBuildResult.Disabled, repository.rebuild(context))
            fixture.provider = configured
            fixture.consent = false
            assertEquals(MemoryVectorBuildResult.Disabled, repository.rebuild(context))
            fixture.consent = true
            fixture.memoryEnabled = false
            assertEquals(MemoryVectorBuildResult.Disabled, repository.rebuild(context))
            fixture.memoryEnabled = true
            assertEquals(
                MemoryVectorBuildResult.Unavailable,
                repository.rebuild(context.copy(conversationId = ""))
            )
            assertTrue(fixture.api.calls.isEmpty())
        }
    }

    @Test
    fun search_personaCharacterAndArchive_filterBeforeTopK() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val a = fixture.context("身份甲")
            val b = fixture.context("身份乙")
            val other = fixture.context("身份甲", characterId = "b")
            assertEquals(a.spaceId, b.spaceId)
            val recordA = fixture.addRecord(a, "甲的合成记录")
            val recordB = fixture.addRecord(b, "乙的合成记录")
            val recordOther = fixture.addRecord(other, "另一角色的合成记录")
            val repository = fixture.repository(maxResults = 1)
            repository.rebuild(a)
            repository.rebuild(b)
            repository.rebuild(other)
            repository.rebuild(a, forceRebuild = true)
            val allRecords = listOf(recordOther, recordB, recordA)
            assertEquals(
                recordA.id,
                repository.search(a, allRecords, listOf("合成记录")).single().recordId
            )
            assertEquals(
                recordB.id,
                repository.search(b, allRecords, listOf("合成记录")).single().recordId
            )
            assertEquals(
                recordOther.id,
                repository.search(other, allRecords, listOf("合成记录")).single().recordId
            )
            fixture.archive.archiveRecallOverflow(a, 0)
            val archived = fixture.archive.listRecallRecords(a).single()
            assertEquals(MemoryRecordStatus.ARCHIVED, archived.status)
            assertEquals(
                recordA.id,
                repository.search(a, listOf(archived), listOf("合成记录")).single().recordId
            )
            assertEquals(
                1,
                fixture.database.memoryVectorDao().listIndexes(a.spaceId, b.personaKey)
                    .count { it.active })
        }
    }

    @Test
    fun search_manyChunksOfOneRecord_doNotCrowdOutOtherRecords() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val long = fixture.addRecord(context, longBody())
            val short = fixture.addRecord(context, "另一条独立经历")
            fixture.api.vector =
                { if ("独立经历" in it) floatArrayOf(0.9f, 0.1f) else floatArrayOf(1f, 0f) }
            val repository = fixture.repository(maxResults = 2)
            repository.rebuild(context)
            val hits = repository.search(context, listOf(long, short), listOf("合成查询"))
            assertEquals(listOf(long.id, short.id), hits.map { it.recordId })
            assertTrue(hits.first().score > hits.last().score)
        }
    }

    @Test
    fun rebuild_dailyNewConversation_reusesExistingGeneration() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val first = fixture.context("同一身份")
            val record = fixture.addRecord(first, "保留旧对话中的合成经历")
            val repository = fixture.repository()
            val initial = repository.rebuild(first) as MemoryVectorBuildResult.Ready
            fixture.archive.archiveConversation(first.conversationId)
            val next = fixture.context("同一身份")
            assertEquals(first.spaceId, next.spaceId)
            assertNotEquals(first.runId, next.runId)
            assertEquals(
                initial.generation,
                (repository.rebuild(next) as MemoryVectorBuildResult.Ready).generation
            )
            assertEquals(1, fixture.api.indexCalls().size)
            assertEquals(
                record.id,
                repository.search(next, fixture.archive.listRecallRecords(next), listOf("经历"))
                    .single().recordId
            )
            assertTrue(repository.search(first, listOf(record), listOf("经历")).isEmpty())
        }
    }

    @Test
    fun rebuild_independentRuns_doNotShareIndexOrRecords() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val first = fixture.context(purpose = MemoryPurpose.INDEPENDENT)
            val next = fixture.context(purpose = MemoryPurpose.INDEPENDENT)
            val record = fixture.addRecord(first, "剧情甲的合成经历")
            val otherRecord = fixture.addRecord(next, "剧情乙的合成经历")
            val repository = fixture.repository()
            repository.rebuild(first)
            repository.rebuild(next)
            assertNotEquals(first.spaceId, next.spaceId)
            assertEquals(
                otherRecord.id,
                repository.search(next, listOf(record, otherRecord), listOf("经历"))
                    .single().recordId
            )
        }
    }

    @Test
    fun search_bodyRevisionChange_immediatelyInvalidatesOldVectors() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "旧地址706")
            val repository = fixture.repository()
            val original = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            fixture.archive.editRecord(context, record.id, record.revision, "新地址809")
            assertTrue(repository.search(context, listOf(record), listOf("地址")).isEmpty())
            assertTrue(fixture.api.queryCalls().isEmpty())
            assertTrue(
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).isEmpty()
            )
            val updated = fixture.archive.listRecallRecords(context).single()
            assertEquals(2L, updated.revision)
            val rebuilt = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            assertNotEquals(original.generation, rebuilt.generation)
            assertEquals(
                2L,
                repository.search(context, listOf(updated), listOf("地址")).single().revision
            )
            assertEquals(
                "新地址809",
                fixture.archive.getRecordDetails(context, record.id)!!.record.content
            )
        }
    }

    @Test
    fun rebuild_lateResultAfterEdit_cannotRepresentNewRevision() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "旧合成内容")
            val normal = fixture.api.answer
            fixture.api.answer = { call ->
                fixture.archive.editRecord(context, record.id, record.revision, "人工新版本")
                normal(call)
            }
            val repository = fixture.repository()
            assertEquals(MemoryVectorBuildResult.Unavailable, repository.rebuild(context))
            assertTrue(
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).isEmpty()
            )
            assertEquals("人工新版本", fixture.archive.listRecallRecords(context).single().content)
            fixture.api.answer = normal
            assertTrue(repository.rebuild(context) is MemoryVectorBuildResult.Ready)
            assertEquals(
                listOf("旧合成内容", "人工新版本"),
                fixture.api.indexCalls().flatMap { it.inputs })
        }
    }

    @Test
    fun rebuild_lateResultAfterDeletion_cannotResurrectArchive() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "待删除合成内容")
            val normal = fixture.api.answer
            fixture.api.answer = { call -> fixture.archive.deleteSpace(context); normal(call) }
            assertEquals(MemoryVectorBuildResult.Unavailable, fixture.repository().rebuild(context))
            assertTrue(
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).isEmpty()
            )
            assertTrue(
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .isEmpty()
            )
        }
    }

    @Test
    fun rebuild_memoryGenerationChange_reusesOnlyStillLegalRecords() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val removed = fixture.addRecord(context, "删除的合成内容")
            val retained = fixture.addRecord(context, "保留的合成内容")
            val repository = fixture.repository()
            repository.rebuild(context)
            fixture.archive.deleteRecord(context, removed.id, removed.revision)
            val current =
                fixture.archive.getRuntimeContext(context.conversationId, context.characterId)!!
            assertTrue(current.generation > context.generation)
            assertEquals(
                retained.id,
                repository.search(current, listOf(retained), listOf("保留")).single().recordId
            )
            assertTrue(repository.rebuild(current) is MemoryVectorBuildResult.Ready)
            assertEquals(1, fixture.api.indexCalls().size)
            assertTrue(repository.search(context, listOf(retained), listOf("保留")).isEmpty())
        }
    }

    @Test
    fun rebuild_addedRecord_onlyEmbedsMissingRevision() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "已完成甲")
            fixture.addRecord(context, "已完成乙")
            val repository = fixture.repository()
            repository.rebuild(context)
            fixture.addRecord(context, "后来新增丙")
            val ready = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            assertEquals(3, ready.records)
            assertEquals(listOf("后来新增丙"), fixture.api.indexCalls().last().inputs)
            assertEquals(ready, repository.rebuild(context))
            assertEquals(2, fixture.api.indexCalls().size)
        }
    }

    @Test
    fun rebuild_incrementalUpdates_onlyRetainCurrentDerivedGeneration() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val repository = fixture.repository()
            repeat(8) { index ->
                fixture.addRecord(context, "第${index}条合成内容，原文始终保留")
                repository.rebuild(context)
                assertEquals(
                    index + 1,
                    fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).size
                )
                assertEquals(
                    1,
                    fixture.database.memoryVectorDao()
                        .listIndexes(context.spaceId, context.personaKey).size
                )
            }
            val records = fixture.archive.listRecallRecords(context)
            assertEquals(8, records.size)
            records.forEach {
                assertEquals(
                    it.content,
                    fixture.archive.getRecordDetails(context, it.id)!!.revisions.single().content
                )
            }
            assertEquals(8, fixture.api.indexCalls().size)
            assertTrue(fixture.api.indexCalls().all { it.inputs.size == 1 })
        }
    }

    @Test
    fun rebuild_lateOlderGeneration_cannotReplaceNewActiveGeneration() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成内容")
            val repository = fixture.repository()
            repository.rebuild(context)
            val dao = fixture.database.memoryVectorDao()
            val previous = dao.listIndexes(context.spaceId, context.personaKey).single { it.active }
            val previousRows = fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
            var newerGeneration = 0L
            fixture.api.answer = { call ->
                newerGeneration = dao.maxGeneration(context.spaceId) + 1
                dao.deactivateConfiguration(
                    context.spaceId,
                    context.personaKey,
                    previous.configurationKey
                )
                dao.upsertIndex(previous.copy(generation = newerGeneration, active = true))
                dao.upsertEmbeddings(previousRows.map { it.copy(generation = newerGeneration) })
                MemoryEmbeddingBatch(call.inputs.map { floatArrayOf(0f, 1f) }, 2)
            }
            assertEquals(
                MemoryVectorBuildResult.Unavailable,
                repository.rebuild(context, forceRebuild = true)
            )
            assertEquals(
                newerGeneration,
                dao.listIndexes(context.spaceId, context.personaKey).single { it.active }.generation
            )
        }
    }

    @Test
    fun rebuild_providerModelAndEndpoint_keepVectorSpacesSeparate() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "合成经历")
            val originalProvider = fixture.provider!!
            val repository = fixture.repository()
            val original = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            fixture.provider = originalProvider.copy(embeddingModel = "second-embedding")
            assertTrue(repository.search(context, listOf(record), listOf("经历")).isEmpty())
            val other = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            assertNotEquals(original.generation, other.generation)
            fixture.provider = originalProvider.copy(apiKey = "rotated-secret")
            assertEquals(
                original.generation,
                (repository.rebuild(context) as MemoryVectorBuildResult.Ready).generation
            )
            assertEquals(2, fixture.api.indexCalls().size)
            fixture.provider = originalProvider.copy(baseUrl = "https://second.example.invalid/v1/")
            assertTrue(repository.search(context, listOf(record), listOf("经历")).isEmpty())
            repository.rebuild(context)
            fixture.provider = originalProvider.copy(id = "another-provider-id")
            repository.rebuild(context)
            val indexes =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
            assertEquals(4, indexes.map { it.vectorSpace }.distinct().size)
            assertTrue(indexes.all { it.active })
        }
    }

    @Test
    fun rebuild_newDimensions_activateOnlyAfterCompleteValidation() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, longBody())
            val repository = fixture.repository()
            val initial = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            var checkedOldIndex = 0
            fixture.api.answer = { call ->
                if (call.purpose == MemoryEmbeddingPurpose.INDEX) {
                    val active = fixture.database.memoryVectorDao()
                        .listIndexes(context.spaceId, context.personaKey).single { it.active }
                    assertEquals(initial.generation, active.generation)
                    assertEquals(2, active.dimensions)
                    assertEquals(
                        record.id,
                        repository.search(context, listOf(record), listOf("合成")).single().recordId
                    )
                    checkedOldIndex++
                    MemoryEmbeddingBatch(call.inputs.map { floatArrayOf(1f, 0f, 0f) }, 3)
                } else MemoryEmbeddingBatch(call.inputs.map { floatArrayOf(1f, 0f) }, 2)
            }
            val updated =
                repository.rebuild(context, forceRebuild = true) as MemoryVectorBuildResult.Ready
            assertTrue(checkedOldIndex > 1)
            val active =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .single { it.active }
            assertEquals(updated.generation, active.generation)
            assertEquals(3, active.dimensions)
            val rows = fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
                .filter { it.generation == updated.generation }
            assertEquals(updated.chunks, rows.size)
            assertTrue(rows.all { it.dimensions == 3 && it.vectorSpace == active.vectorSpace })
        }
    }

    @Test
    fun rebuild_secondBatchFailure_keepsProgressAndRequiresExplicitRetry() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val content = longBody()
            val record = fixture.addRecord(context, content)
            val normal = fixture.api.answer
            fixture.api.answer = { call ->
                if (fixture.api.indexCalls().size == 2) throw MemoryEmbeddingException(
                    MemoryEmbeddingError.NETWORK
                )
                normal(call)
            }
            val repository = fixture.repository()
            assertTrue(repository.rebuild(context) is MemoryVectorBuildResult.Failed)
            val completedInputs = fixture.api.indexCalls().first().inputs.toSet()
            val progress = repository.progress(context).single()
            assertEquals(32, progress.completedChunks)
            assertTrue(progress.requiresManualRetry)
            assertEquals("FAILED", progress.status)
            assertTrue(repository.resumePending(context) is MemoryVectorBuildResult.NeedsReview)
            assertEquals(2, fixture.api.indexCalls().size)
            fixture.api.answer = normal
            val ready =
                repository.rebuild(context, retryUncertain = true) as MemoryVectorBuildResult.Ready
            assertTrue(fixture.api.indexCalls().drop(2).flatMap { it.inputs }
                .none { it in completedInputs })
            assertEquals(
                content,
                fixture.archive.getRecordDetails(context, record.id)!!.record.content
            )
            assertEquals(
                ready.chunks,
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
                    .count { it.generation == ready.generation })
            val history =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .single { it.generation == progress.generation }
            assertEquals("RETRIED", history.status)
            assertTrue(history.requestToken.isNotEmpty())
            assertTrue(history.pendingBatchJson != "[]")
            assertTrue(
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
                    .none { it.generation == history.generation })
        }
    }

    @Test
    fun resume_afterReopen() = runBlocking {
        MemoryVectorTestFixture(persistent = true).use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成持久内容")
            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER c3_fail_activate BEFORE UPDATE OF active ON memory_embedding_indexes WHEN NEW.active = 1 BEGIN SELECT RAISE(ABORT, 'synthetic activate failure'); END",
            )
            val result = fixture.repository().rebuild(context) as MemoryVectorBuildResult.Failed
            assertEquals("STORAGE", result.code)
            assertEquals(
                1,
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).size
            )
            fixture.reopen()
            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER c3_fail_activate")
            val resumed =
                fixture.repository().resumePending(context) as MemoryVectorBuildResult.Ready
            assertEquals(result.generation, resumed.generation)
            assertEquals(1, fixture.api.indexCalls().size)
            assertEquals(
                "合成持久内容",
                fixture.archive.listRecallRecords(context).single().content
            )
        }
    }

    @Test
    fun resume_unknownAfterReopen() = runBlocking {
        MemoryVectorTestFixture(persistent = true).use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "未知请求的合成内容")
            fixture.api.answer = { throw MemoryEmbeddingException(MemoryEmbeddingError.TIMEOUT) }
            assertTrue(fixture.repository().rebuild(context) is MemoryVectorBuildResult.Failed)
            fixture.reopen()
            assertTrue(
                fixture.repository().resumePending(context) is MemoryVectorBuildResult.NeedsReview
            )
            assertEquals(1, fixture.api.indexCalls().size)
            assertTrue(fixture.repository().progress(context).single().requiresManualRetry)
        }
    }

    @Test
    fun resume_fullRebuildActivationFailure_finishesFreshGeneration() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成内容")
            val repository = fixture.repository()
            val original = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            fixture.api.vector = { floatArrayOf(0f, 1f) }
            fixture.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER c3_fail_activate BEFORE UPDATE OF active ON memory_embedding_indexes WHEN NEW.active = 1 BEGIN SELECT RAISE(ABORT, 'synthetic activate failure'); END",
            )
            val failed =
                repository.rebuild(context, forceRebuild = true) as MemoryVectorBuildResult.Failed
            assertNotEquals(original.generation, failed.generation)
            assertEquals(
                original.generation,
                repository.progress(context).single { it.active }.generation
            )
            fixture.database.openHelper.writableDatabase.execSQL("DROP TRIGGER c3_fail_activate")
            val resumed = repository.resumePending(context) as MemoryVectorBuildResult.Ready
            assertEquals(failed.generation, resumed.generation)
            assertEquals(2, fixture.api.indexCalls().size)
            val row = fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
                .single { it.generation == resumed.generation }
            assertArrayEquals(floatArrayOf(0f, 1f), MemoryVectorMath.decode(row.vector, 2)!!, 0f)
        }
    }

    @Test
    fun resume_fullRebuild_doesNotBorrowOldActiveChunks() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, longBody())
            val repository = fixture.repository()
            val original = repository.rebuild(context) as MemoryVectorBuildResult.Ready
            val initialCalls = fixture.api.indexCalls().size
            fixture.api.answer = { call ->
                if (fixture.api.indexCalls().size == initialCalls + 2) throw MemoryEmbeddingException(
                    MemoryEmbeddingError.NETWORK
                )
                MemoryEmbeddingBatch(call.inputs.map { floatArrayOf(0f, 1f) }, 2)
            }
            assertTrue(
                repository.rebuild(
                    context,
                    forceRebuild = true
                ) is MemoryVectorBuildResult.Failed
            )
            assertTrue(repository.resumePending(context) is MemoryVectorBuildResult.NeedsReview)
            assertEquals(
                original.generation,
                repository.progress(context).single { it.active }.generation
            )
            fixture.api.answer =
                { call -> MemoryEmbeddingBatch(call.inputs.map { floatArrayOf(0f, 1f) }, 2) }
            val resumed =
                repository.rebuild(context, retryUncertain = true) as MemoryVectorBuildResult.Ready
            assertEquals(initialCalls + 3, fixture.api.indexCalls().size)
            assertEquals(resumed.chunks - 32, fixture.api.indexCalls().last().inputs.size)
            val rows = fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId)
                .filter { it.generation == resumed.generation }
            assertEquals(resumed.chunks, rows.size)
            rows.forEach {
                assertArrayEquals(
                    floatArrayOf(0f, 1f),
                    MemoryVectorMath.decode(it.vector, 2)!!,
                    0f
                )
            }
        }
    }

    @Test
    fun rebuild_cancellation_pausesWithoutSwallowingCancellation() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成内容")
            fixture.api.answer = { throw CancellationException("synthetic cancellation") }
            val repository = fixture.repository()
            try {
                repository.rebuild(context)
                throw AssertionError("取消应继续传播")
            } catch (_: CancellationException) {
                val progress = repository.progress(context).single()
                assertEquals("PAUSED", progress.status)
                assertTrue(progress.requiresManualRetry)
                assertTrue(repository.resumePending(context) is MemoryVectorBuildResult.NeedsReview)
                assertEquals(1, fixture.api.indexCalls().size)
            }
        }
    }

    @Test
    fun search_softTimeout_leavesReadyIndexAndPropagatesCancellation() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "合成经历")
            val repository = fixture.repository()
            repository.rebuild(context)
            var cancelled = false
            fixture.api.answer = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val timedOut = withContext(Dispatchers.Default) {
                withTimeoutOrNull(1_000) {
                    repository.search(
                        context,
                        listOf(record),
                        listOf("经历")
                    )
                }
            }
            assertNull(timedOut)
            assertTrue(cancelled)
            assertEquals("READY", repository.progress(context).single().status)
            assertFalse(repository.progress(context).single().requiresManualRetry)
        }
    }

    @Test
    fun search_recordEditedDuringQuery_discardsStaleResult() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "旧合成事实")
            val repository = fixture.repository()
            repository.rebuild(context)
            val normal = fixture.api.answer
            fixture.api.answer = { call ->
                fixture.archive.editRecord(
                    context,
                    record.id,
                    record.revision,
                    "新合成事实"
                ); normal(call)
            }
            assertTrue(repository.search(context, listOf(record), listOf("事实")).isEmpty())
            assertEquals(2L, fixture.archive.listRecallRecords(context).single().revision)
        }
    }

    @Test
    fun search_configurationChangesDuringQuery_discardsResult() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "合成事实")
            val repository = fixture.repository()
            repository.rebuild(context)
            val normal = fixture.api.answer
            fixture.api.answer = { call ->
                fixture.provider = fixture.provider!!.copy(embeddingModel = "other-model"); normal(
                call
            )
            }
            assertTrue(repository.search(context, listOf(record), listOf("事实")).isEmpty())
        }
    }

    @Test
    fun rebuild_twoInstances_claimOnlyOnePaidBatch() = runBlocking {
        withTimeout(5_000) {
            MemoryVectorTestFixture().use { fixture ->
                val context = fixture.context()
                fixture.addRecord(context, "并发合成事实")
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val normal = fixture.api.answer
                fixture.api.answer =
                    { call -> started.complete(Unit); release.await(); normal(call) }
                val first = async(Dispatchers.IO) { fixture.repository().rebuild(context) }
                started.await()
                val second = fixture.repository().rebuild(context)
                assertTrue(second is MemoryVectorBuildResult.NeedsReview)
                assertEquals(1, fixture.api.indexCalls().size)
                release.complete(Unit)
                assertTrue(first.await() is MemoryVectorBuildResult.Ready)
            }
        }
    }

    @Test
    fun search_invalidStoredVectors_neverTriggerSimilarityOrNetwork() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "合成事实")
            val repository = fixture.repository()
            repository.rebuild(context)
            val row = fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).single()
            fixture.database.memoryArchiveDao().upsertEmbedding(row.copy(vector = ByteArray(8)))
            assertTrue(repository.search(context, listOf(record), listOf("事实")).isEmpty())
            assertTrue(fixture.api.queryCalls().isEmpty())
            assertEquals(
                "合成事实",
                fixture.archive.getRecordDetails(context, record.id)!!.record.content
            )
        }
    }

    @Test
    fun rebuild_invalidFakeResponse_cannotActivatePartialIndex() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "合成事实")
            fixture.api.answer = { MemoryEmbeddingBatch(listOf(floatArrayOf(1f, 0f)), 3) }
            val failure = fixture.repository().rebuild(context) as MemoryVectorBuildResult.Failed
            assertEquals("PROTOCOL", failure.code)
            assertTrue(
                fixture.database.memoryArchiveDao().listEmbeddings(context.spaceId).isEmpty()
            )
            assertFalse(fixture.repository().progress(context).single().active)
        }
    }

    @Test
    fun rebuild_unknownSource_isNotIndexedOrQueried() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            val record = fixture.addRecord(context, "来源不明的合成内容")
            val details = fixture.archive.getRecordDetails(context, record.id)!!
            val source =
                fixture.database.memoryArchiveDao().getSource(details.sources.single().id)!!
            fixture.database.memoryArchiveDao()
                .upsertSource(source.copy(status = "UNKNOWN", content = ""))
            val repository = fixture.repository()
            assertEquals(0, (repository.rebuild(context) as MemoryVectorBuildResult.Ready).chunks)
            assertTrue(repository.search(context, listOf(record), listOf("来源")).isEmpty())
            assertTrue(fixture.api.calls.isEmpty())
        }
    }

    @Test
    fun progress_containsOnlyIdsVersionsAndSafeFailureCodes() = runBlocking {
        MemoryVectorTestFixture().use { fixture ->
            val context = fixture.context()
            fixture.addRecord(context, "完整敏感原文哨兵")
            fixture.api.answer =
                { throw IllegalStateException("vector-secret-sentinel 完整敏感原文哨兵") }
            val repository = fixture.repository()
            assertTrue(repository.rebuild(context) is MemoryVectorBuildResult.Failed)
            val index =
                fixture.database.memoryVectorDao().listIndexes(context.spaceId, context.personaKey)
                    .single()
            val metadata =
                index.configurationKey + index.contextJson + index.recordsJson + index.pendingBatchJson + index.failureCode
            assertFalse(metadata.contains("vector-secret-sentinel"))
            assertFalse(metadata.contains("完整敏感原文哨兵"))
            assertFalse(metadata.contains("baseUrl"))
            assertEquals("STORAGE", index.failureCode)
            assertTrue(index.requestToken.isNotEmpty())
        }
    }

    private fun longBody(): String =
        (0 until 950).joinToString("\n") { "合成经历第${it}项：" + "叙述".repeat(32) }
}
