package com.example.myapplication.data.repository.roleplay

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySpaceStatus
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayScenario
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayRunStorageTest {
    private lateinit var database: ChatDatabase
    private lateinit var conversations: ConversationRepository
    private lateinit var archive: RoomMemoryArchiveRepository
    private lateinit var repository: RoomRoleplayRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ChatDatabase::class.java,
        ).allowMainThreadQueries().build()
        conversations = ConversationRepository(RoomConversationStore(database))
        archive =
            RoomMemoryArchiveRepository(database, isCharacterAvailable = { it == "test-character" })
        repository = createRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun independentRestart_preservesOriginalMemoryAndAssignsNewRun() = runBlocking {
        val scenario = testScenario()
        repository.upsertScenario(scenario)
        val original = repository.startScenario(scenario.id).session
        conversations.appendMessages(
            original.conversationId,
            listOf(
                ChatMessage(
                    id = "original-message",
                    conversationId = original.conversationId,
                    role = MessageRole.USER,
                    content = "仅供隔离测试的旧剧情经历",
                    createdAt = 1L,
                ),
            ),
            selectedModel = "",
        )
        val context =
            requireNotNull(archive.getRuntimeContext(original.conversationId, "test-character"))
        val source = archive.listPendingIngress(context).single().source.fullRef()
        val job = archive.planJob(context, "test-extraction", listOf(source))
        assertNotNull(archive.claimJob(job.id, "test-worker"))
        assertTrue(archive.saveExtraction(job.id, "test-worker", "{}"))
        val committed = archive.commitJob(
            job.id,
            "test-worker",
            MemoryCommitBatch(
                records = listOf(
                    MemoryRecordDraft(
                        "N0",
                        "应保留的记忆",
                        listOf(source)
                    )
                )
            ),
        ) as MemoryWriteResult.Committed

        val restarted = repository.restartScenario(scenario.id).session
        val nextContext =
            requireNotNull(archive.getRuntimeContext(restarted.conversationId, "test-character"))

        assertEquals(original.id, restarted.id)
        assertNotEquals(original.runId, restarted.runId)
        assertNotEquals(context.spaceId, nextContext.spaceId)
        assertNotEquals(original.conversationId, restarted.conversationId)
        assertEquals(
            "original-message",
            conversations.listMessages(original.conversationId).single().id
        )
        assertNull(archive.getRecordDetails(context, committed.recordIds.single()))
        assertTrue(archive.listRecords(context).isEmpty())
        val archiveContext = context.copy(
            conversationId = "",
            generation = requireNotNull(archive.getSpace(context.spaceId)).generation,
        )
        val details =
            requireNotNull(archive.getRecordDetails(archiveContext, committed.recordIds.single()))
        assertEquals("应保留的记忆", details.record.content)
        assertEquals("仅供隔离测试的旧剧情经历", details.sources.single().content)
        assertEquals("应保留的记忆", details.revisions.single().content)
        assertEquals(MemorySpaceStatus.ARCHIVED, archive.getSpace(context.spaceId)?.status)
        assertNull(archive.getRuntimeContext(original.conversationId, "test-character"))
        assertTrue(archive.listRecords(nextContext).isEmpty())
    }

    @Test
    fun bindingFailure_rollsBackArchiveConversationAndSessionTogether() = runBlocking {
        val scenario = testScenario()
        repository.upsertScenario(scenario)
        val original = repository.startScenario(scenario.id).session
        val originalContext =
            requireNotNull(archive.getRuntimeContext(original.conversationId, "test-character"))
        val failingArchive = object : MemoryArchiveRepository by archive {
            override suspend fun bindConversation(
                scenario: RoleplayScenario,
                conversationId: String,
                characterIds: List<String>,
                runId: String,
            ): List<MemoryRuntimeContext> {
                archive.bindConversation(scenario, conversationId, characterIds, runId)
                error("synthetic_native_binding_failure")
            }
        }

        val failure =
            runCatching { createRepository(failingArchive).restartScenario(scenario.id) }.exceptionOrNull()

        assertEquals("synthetic_native_binding_failure", failure?.message)
        assertEquals(original, repository.getSessionByScenario(scenario.id))
        assertEquals(setOf(original.conversationId), conversations.listConversationIds())
        assertEquals(
            originalContext,
            archive.getRuntimeContext(original.conversationId, "test-character")
        )
        assertEquals(MemorySpaceStatus.ACTIVE, archive.getSpace(originalContext.spaceId)?.status)
        assertEquals(1, archive.listSpaces("test-character").size)
    }

    private fun createRepository(memoryArchive: MemoryArchiveRepository = archive) =
        RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversations,
            database = database,
            memoryArchiveRepository = memoryArchive,
        )

    private fun testScenario() = RoleplayScenario(
        id = "test-story",
        title = "隔离事务测试",
        assistantId = "test-character",
        memoryPurpose = MemoryPurpose.INDEPENDENT,
    )
}
