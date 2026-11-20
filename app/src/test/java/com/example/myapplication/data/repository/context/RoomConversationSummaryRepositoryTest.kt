package com.example.myapplication.data.repository.context

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.ConversationSummarySegmentEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.PromptCompletionStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomConversationSummaryRepositoryTest {
    private lateinit var database: ChatDatabase
    private lateinit var store: RoomConversationStore
    private lateinit var repository: RoomMemoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java
        )
            .allowMainThreadQueries().build()
        store = RoomConversationStore(database)
        repository = RoomMemoryRepository(database.memoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun save_roundTripsFullSourceSnapshotsAndCompletionEvidence() = runBlocking {
        val messages = seed()
        val summary = summary(messages)
        val segment = segment(messages)

        assertTrue(repository.saveSummarySegmentIfSourcesCurrent(segment, null))
        assertTrue(repository.saveSummaryIfSourcesCurrent(summary, null))
        assertEquals(summary, repository.getSummary("conv"))
        assertEquals(listOf(segment), repository.listSummarySegments("conv"))
        val stored = database.memoryDao().getConversationSummary("conv")!!
        assertEquals("completed", stored.completionStatus)
        assertTrue(stored.sourceSnapshotJson.contains("fingerprint"))
        assertTrue(stored.sourceSnapshotJson.contains("messageRevision"))
        assertEquals(messages, repository.getSummarySourceMessages("conv"))
    }

    @Test
    fun save_rawContentChangeWithoutRevisionBumpRejectsLateResult() = runBlocking {
        val messages = seed()
        val old = summary(messages)
        repository.upsertSummary(old)
        database.openHelper.writableDatabase.execSQL("UPDATE messages SET content = '修改后的事实' WHERE id = 'm1'")

        assertFalse(repository.saveSummaryIfSourcesCurrent(old.copy(summary = "迟到摘要"), old))
        assertFalse(repository.saveSummarySegmentIfSourcesCurrent(segment(messages), old))
        assertEquals(old, repository.getSummary("conv"))
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(
                old,
                repository.getSummarySourceMessages("conv")
            )
        )
        assertTrue(repository.listSummarySegments("conv").isEmpty())
    }

    @Test
    fun save_deletedSourceCannotBeReplacedByTheFollowingMessage() = runBlocking {
        val messages = seed()
        database.openHelper.writableDatabase.execSQL("DELETE FROM messages WHERE id = 'm1'")

        assertFalse(repository.saveSummaryIfSourcesCurrent(summary(messages), null))
        assertFalse(repository.saveSummarySegmentIfSourcesCurrent(segment(messages), null))
        assertNull(repository.getSummary("conv"))
        assertEquals(listOf("m2"), store.listMessages("conv").map { it.id })
    }

    @Test
    fun publish_rechecksSourceInsideTransactionAfterRepositoryValidation() = runBlocking {
        val messages = seed()
        val dao = database.memoryDao()
        val guarded = RoomMemoryRepository(object : MemoryDao by dao {
            override suspend fun publishConversationSummary(
                summary: ConversationSummaryEntity,
                expectedSummary: ConversationSummaryEntity?,
                expectedMessages: List<MessageEntity>,
            ): Boolean {
                database.openHelper.writableDatabase.execSQL("UPDATE messages SET createdAt = 99 WHERE id = 'm1'")
                return dao.publishConversationSummary(summary, expectedSummary, expectedMessages)
            }
        })

        assertFalse(guarded.saveSummaryIfSourcesCurrent(summary(messages), null))
        assertNull(repository.getSummary("conv"))
        assertEquals(99L, store.listMessages("conv").single { it.id == "m1" }.createdAt)
    }

    @Test
    fun saveSegment_rechecksSourceInsideTransactionBeforePublishingArtifact() = runBlocking {
        val messages = seed()
        val dao = database.memoryDao()
        val guarded = RoomMemoryRepository(object : MemoryDao by dao {
            override suspend fun saveConversationSummarySegment(
                segment: ConversationSummarySegmentEntity,
                expectedSummary: ConversationSummaryEntity?,
                expectedMessages: List<MessageEntity>,
            ): Boolean {
                database.openHelper.writableDatabase.execSQL("UPDATE messages SET memoryRevision = 2 WHERE id = 'm1'")
                return dao.saveConversationSummarySegment(
                    segment,
                    expectedSummary,
                    expectedMessages
                )
            }
        })

        assertFalse(guarded.saveSummarySegmentIfSourcesCurrent(segment(messages), null))
        assertTrue(repository.listSummarySegments("conv").isEmpty())
    }

    @Test
    fun publish_newerSummaryAndDatabaseFailurePreserveTheExistingBody() = runBlocking {
        val messages = seed()
        val old = summary(messages)
        repository.upsertSummary(old)
        val newer = old.copy(summary = "较新的摘要", updatedAt = 20)
        repository.upsertSummary(newer)
        assertFalse(repository.saveSummaryIfSourcesCurrent(old.copy(summary = "迟到摘要"), old))
        assertEquals(newer, repository.getSummary("conv"))

        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_summary BEFORE UPDATE ON conversation_summaries BEGIN SELECT RAISE(ABORT, 'synthetic'); END",
        )
        val failure = runCatching {
            repository.saveSummaryIfSourcesCurrent(
                newer.copy(summary = "写入失败"),
                newer
            )
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(newer, repository.getSummary("conv"))
        assertEquals(messages, store.listMessages("conv"))
    }

    @Test
    fun unknownCompletion_keepsArtifactAndSourceEvidenceWithoutReliableCoverage() = runBlocking {
        val messages = seed()
        val summary = summary(messages).copy(
            completionStatus = PromptCompletionStatus.UNKNOWN,
            coveredMessageCount = 0
        )

        assertTrue(repository.saveSummaryIfSourcesCurrent(summary, null))
        val loaded = repository.getSummary("conv")!!
        assertEquals(summary, loaded)
        assertEquals(
            2,
            ConversationSummaryCoverageSupport.processedSummaryCoverage(loaded, messages)
        )
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(loaded, messages)
        )
    }

    @Test
    fun invalidSnapshot_keepsStoredBodyButCannotAuthorizeCoverage() = runBlocking {
        val messages = seed()
        database.memoryDao().upsertConversationSummary(
            ConversationSummaryEntity(
                "conv", summary = "保留正文", coveredMessageCount = 99,
                sourceSnapshotJson = "[{\"messageId\":null}]", completionStatus = "completed",
            ),
        )

        val loaded = repository.getSummary("conv")!!
        assertEquals("保留正文", loaded.summary)
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(loaded, messages)
        )
        assertNull(ConversationSummaryCoverageSupport.usableSummary(loaded, messages))
    }

    @Test
    fun scopedCachesKeepBothCharactersArtifactsForTheSamePublicSourceRange() = runBlocking {
        val messages = seed()
        val contexts = bindContexts()
        val contextA = contexts.single { it.characterId == "a" }
        val contextB = contexts.single { it.characterId == "b" }
        val summaryA =
            summary(messages).copy(memoryContext = contextA, summary = "A_PRIVATE_AGGREGATE")
        val summaryB = summary(messages).copy(
            assistantId = "b",
            memoryContext = contextB,
            summary = "B_PRIVATE_AGGREGATE"
        )
        val segmentA = segment(messages).copy(id = "segment-a", memoryContext = contextA)
        val segmentB =
            segment(messages).copy(id = "segment-b", assistantId = "b", memoryContext = contextB)

        assertTrue(repository.saveSummarySegmentIfSourcesCurrent(segmentA, null))
        assertTrue(repository.saveSummaryIfSourcesCurrent(summaryA, null))
        assertTrue(repository.saveSummarySegmentIfSourcesCurrent(segmentB, null))
        assertTrue(repository.saveSummaryIfSourcesCurrent(summaryB, null))

        val reopenedRepository = RoomMemoryRepository(database.memoryDao())
        assertEquals(summaryA, reopenedRepository.getSummaryForContext(contextA))
        assertEquals(summaryB, reopenedRepository.getSummaryForContext(contextB))
        assertNull(reopenedRepository.getSummary("conv"))
        assertEquals(
            setOf("segment-a", "segment-b"),
            reopenedRepository.listSummarySegments("conv").map { it.id }.toSet()
        )
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM conversation_summary_segments WHERE isAggregate = 1")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
    }

    @Test
    fun publishRechecksPersonaInsideTransactionWithoutRequiringMessageEdits() = runBlocking {
        val messages = seed()
        val context = bindContexts().single { it.characterId == "a" }
        val dao = database.memoryDao()
        val guarded = RoomMemoryRepository(object : MemoryDao by dao {
            override suspend fun publishConversationSummary(
                summary: ConversationSummaryEntity,
                expectedSummary: ConversationSummaryEntity?,
                expectedMessages: List<MessageEntity>,
            ): Boolean {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE memory_conversation_bindings SET personaKey = 'new-persona' WHERE conversationId = 'conv' AND characterId = 'a'",
                )
                return dao.publishConversationSummary(summary, expectedSummary, expectedMessages)
            }
        })

        assertFalse(
            guarded.saveSummaryIfSourcesCurrent(
                summary(messages).copy(memoryContext = context),
                null
            )
        )
        assertNull(repository.getSummaryForContext(context))
        assertNull(
            dao.getConversationSummaryCache(
                "conv",
                ConversationSummaryCoverageSupport.memoryContextKey(context)
            )
        )
        assertEquals(messages, store.listMessages("conv"))
    }

    @Test
    fun saveSegmentRechecksGenerationInsideTransaction() = runBlocking {
        val messages = seed()
        val context = bindContexts().single { it.characterId == "a" }
        val dao = database.memoryDao()
        val guarded = RoomMemoryRepository(object : MemoryDao by dao {
            override suspend fun saveConversationSummarySegment(
                segment: ConversationSummarySegmentEntity,
                expectedSummary: ConversationSummaryEntity?,
                expectedMessages: List<MessageEntity>,
            ): Boolean {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE memory_spaces SET generation = generation + 1 WHERE id = ?",
                    arrayOf(context.spaceId),
                )
                return dao.saveConversationSummarySegment(
                    segment,
                    expectedSummary,
                    expectedMessages
                )
            }
        })

        assertFalse(
            guarded.saveSummarySegmentIfSourcesCurrent(
                segment(messages).copy(memoryContext = context),
                null
            )
        )
        assertTrue(repository.listSummarySegments("conv").isEmpty())
        assertEquals(messages, store.listMessages("conv"))
    }

    @Test
    fun archivedRuntimeCannotReadOrPublishItsOldCachedSummary() = runBlocking {
        val messages = seed()
        val context = bindContexts().single { it.characterId == "a" }
        val saved = summary(messages).copy(memoryContext = context)
        assertTrue(repository.saveSummaryIfSourcesCurrent(saved, null))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE memory_conversation_bindings SET archived = 1 WHERE conversationId = 'conv' AND characterId = 'a'",
        )

        assertNull(repository.getSummaryForContext(context))
        assertFalse(
            repository.saveSummaryIfSourcesCurrent(
                saved.copy(summary = "迟到归档结果"),
                saved
            )
        )
        assertEquals(
            saved.summary, database.memoryDao().getConversationSummaryCache(
                "conv", ConversationSummaryCoverageSupport.memoryContextKey(context),
            )!!.summary
        )
    }

    @Test
    fun boundRuntimeCannotUseLegacyApiButItsOldBodyRemainsStored() = runBlocking {
        val messages = seed()
        val old =
            ConversationSummary("conv", summary = "LEGACY_PRIVATE_BODY", coveredMessageCount = 99)
        repository.upsertSummary(old)
        val context = bindContexts().single { it.characterId == "a" }

        assertNull(repository.getSummary("conv"))
        assertFalse(repository.saveSummaryIfSourcesCurrent(summary(messages), old))
        assertTrue(
            repository.saveSummaryIfSourcesCurrent(
                summary(messages).copy(memoryContext = context),
                null
            )
        )
        val preserved = database.memoryDao().getConversationSummaryCache("conv", "")!!
        assertEquals("LEGACY_PRIVATE_BODY", preserved.summary)
        assertEquals(99, preserved.coveredMessageCount)
        assertEquals("[]", preserved.sourceSnapshotJson)
    }

    @Test
    fun legacyObserverStopsEmittingTheOldBodyWhenAConversationBecomesBound() = runBlocking {
        seed()
        val old = ConversationSummary("conv", summary = "旧的未证明摘要")
        repository.upsertSummary(old)
        val values = Channel<ConversationSummary?>(Channel.UNLIMITED)
        val collector = launch { repository.observeSummary("conv").collect { values.send(it) } }
        try {
            assertEquals(old, withTimeout(5_000) { values.receive() })
            bindContexts()
            withTimeout(5_000) {
                while (values.receive() != null) { /* Room 可以合并或重复表级失效通知。 */
                }
            }
        } finally {
            collector.cancelAndJoin()
            values.close()
        }
    }

    @Test
    fun projectionFailureRollsBackTheNewScopeCacheAndPreservesOtherCharacter() = runBlocking {
        val messages = seed()
        val contexts = bindContexts()
        val contextA = contexts.single { it.characterId == "a" }
        val contextB = contexts.single { it.characterId == "b" }
        val savedA = summary(messages).copy(memoryContext = contextA)
        assertTrue(repository.saveSummaryIfSourcesCurrent(savedA, null))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_scope_summary BEFORE UPDATE ON conversation_summaries BEGIN SELECT RAISE(ABORT, 'synthetic'); END",
        )

        val failure = runCatching {
            repository.saveSummaryIfSourcesCurrent(
                summary(messages).copy(
                    assistantId = "b",
                    memoryContext = contextB
                ), null
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(savedA, repository.getSummaryForContext(contextA))
        assertNull(repository.getSummaryForContext(contextB))
        assertNull(
            database.memoryDao().getConversationSummaryCache(
                "conv",
                ConversationSummaryCoverageSupport.memoryContextKey(contextB)
            )
        )
    }

    @Test
    fun upsertOrphanedLegacySummaryPreservesHistoricalBodyWithoutCreatingAConversation() =
        runBlocking {
            val old = ConversationSummary(
                "missing-original-conversation",
                summary = "旧库保留正文",
                coveredMessageCount = 7
            )

            repository.upsertSummary(old)

            assertEquals(old, repository.getSummary(old.conversationId))
            assertNull(store.getConversation(old.conversationId))
        }

    private suspend fun bindContexts(): List<MemoryRuntimeContext> =
        RoomMemoryArchiveRepository(database).bindConversation(
            RoleplayScenario(
                id = "scene",
                assistantId = "a",
                memoryPurpose = MemoryPurpose.CONTINUOUS,
                userPersonaOverride = "合成身份"
            ),
            "conv", listOf("a", "b"), "run",
        )

    private suspend fun seed(): List<ChatMessage> {
        store.upsertConversationWithMessages(
            Conversation("conv", "合成对话", createdAt = 1, updatedAt = 2, assistantId = "a"),
            listOf(
                ChatMessage("m1", "conv", MessageRole.USER, "11月19日 706 号房", createdAt = 1),
                ChatMessage(
                    "m2",
                    "conv",
                    MessageRole.ASSISTANT,
                    "我记住了 🧭",
                    createdAt = 2,
                    speakerId = "a",
                    speakerName = "角色A"
                ),
            ),
        )
        return store.listMessages("conv")
    }

    private fun summary(messages: List<ChatMessage>) = ConversationSummary(
        "conv", "a", "双方约定了时间地点", messages.size, updatedAt = 10,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages),
        completionStatus = PromptCompletionStatus.COMPLETED,
    )

    private fun segment(messages: List<ChatMessage>) = ConversationSummarySegment(
        "segment",
        "conv",
        "a",
        messages.first().id,
        messages.last().id,
        messages.first().createdAt,
        messages.last().createdAt,
        messages.size,
        "时间地点分段",
        10,
        10,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages),
        completionStatus = PromptCompletionStatus.COMPLETED,
    )
}
