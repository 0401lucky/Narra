package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayDiaryEntryEntity
import com.example.myapplication.data.local.roleplay.RoleplayOnlineMetaEntity
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.Conversation
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayRepositoryTest {
    @Test
    fun startScenario_createsConversationAndSessionOnFirstLaunch() = runBlocking {
        val dao = FakeRoleplayDao(
            scenarios = listOf(
                RoleplayScenarioEntity(
                    id = "scene-1",
                    title = "初遇",
                    description = "",
                    assistantId = "assistant-1",
                    backgroundUri = "",
                    userDisplayNameOverride = "",
                    userPersonaOverride = "",
                    userPortraitUri = "",
                    userPortraitUrl = "",
                    characterDisplayNameOverride = "",
                    characterPortraitUri = "",
                    characterPortraitUrl = "",
                    openingNarration = "",
                    enableNarration = true,
                    enableRoleplayProtocol = true,
                    autoHighlightSpeaker = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val conversationStore = FakeConversationStore()
        val conversationRepository = ConversationRepository(
            conversationStore = conversationStore,
            nowProvider = { 10L },
        )
        val repository = RoomRoleplayRepository(
            roleplayDao = dao,
            conversationRepository = conversationRepository,
            nowProvider = { 10L },
        )

        val startResult = repository.startScenario("scene-1")
        val session = startResult.session

        assertEquals("scene-1", session.scenarioId)
        assertTrue(session.conversationId.isNotBlank())
        assertEquals("assistant-1", conversationStore.listConversations().single().assistantId)
        assertEquals(session.conversationId, dao.getSessionByScenario("scene-1")?.conversationId)
        assertTrue(!startResult.reusedExistingSession)
        assertTrue(!startResult.hasHistory)
    }

    @Test
    fun startScenario_reusesExistingSessionWhenConversationStillExists() = runBlocking {
        val conversation = Conversation(
            id = "conversation-1",
            title = "旧剧情",
            model = "",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "assistant-1",
        )
        val dao = FakeRoleplayDao(
            scenarios = listOf(
                RoleplayScenarioEntity(
                    id = "scene-1",
                    title = "初遇",
                    description = "",
                    assistantId = "assistant-1",
                    backgroundUri = "",
                    userDisplayNameOverride = "",
                    userPersonaOverride = "",
                    userPortraitUri = "",
                    userPortraitUrl = "",
                    characterDisplayNameOverride = "",
                    characterPortraitUri = "",
                    characterPortraitUrl = "",
                    openingNarration = "",
                    enableNarration = true,
                    enableRoleplayProtocol = true,
                    autoHighlightSpeaker = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            sessions = listOf(
                RoleplaySessionEntity(
                    id = "session-1",
                    scenarioId = "scene-1",
                    conversationId = conversation.id,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val conversationStore = FakeConversationStore(
            conversations = listOf(conversation),
        )
        val conversationRepository = ConversationRepository(
            conversationStore = conversationStore,
            nowProvider = { 10L },
        )
        val repository = RoomRoleplayRepository(
            roleplayDao = dao,
            conversationRepository = conversationRepository,
            nowProvider = { 10L },
        )

        val startResult = repository.startScenario("scene-1")
        val session = startResult.session

        assertEquals("session-1", session.id)
        assertEquals(conversation.id, session.conversationId)
        assertEquals(1, conversationStore.listConversations().size)
        assertTrue(startResult.reusedExistingSession)
    }

    @Test
    fun startScenario_reusesExistingSessionAndCleansOrphanedLoadingMessages() = runBlocking {
        val conversation = Conversation(
            id = "conversation-1",
            title = "旧剧情",
            model = "chat-model",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "assistant-1",
        )
        val dao = FakeRoleplayDao(
            scenarios = listOf(
                RoleplayScenarioEntity(
                    id = "scene-1",
                    title = "初遇",
                    description = "",
                    assistantId = "assistant-1",
                    backgroundUri = "",
                    userDisplayNameOverride = "",
                    userPersonaOverride = "",
                    userPortraitUri = "",
                    userPortraitUrl = "",
                    characterDisplayNameOverride = "",
                    characterPortraitUri = "",
                    characterPortraitUrl = "",
                    openingNarration = "",
                    enableNarration = true,
                    enableRoleplayProtocol = true,
                    autoHighlightSpeaker = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            sessions = listOf(
                RoleplaySessionEntity(
                    id = "session-1",
                    scenarioId = "scene-1",
                    conversationId = conversation.id,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val conversationStore = FakeConversationStore(
            conversations = listOf(conversation),
            messagesByConversation = mapOf(
                conversation.id to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = conversation.id,
                        role = MessageRole.USER,
                        content = "还记得昨晚吗？",
                        createdAt = 1L,
                    ),
                    ChatMessage(
                        id = "assistant-loading",
                        conversationId = conversation.id,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        status = MessageStatus.LOADING,
                        createdAt = 2L,
                    ),
                ),
            ),
        )
        val conversationRepository = ConversationRepository(
            conversationStore = conversationStore,
            nowProvider = { 10L },
        )
        val repository = RoomRoleplayRepository(
            roleplayDao = dao,
            conversationRepository = conversationRepository,
            nowProvider = { 10L },
        )

        val startResult = repository.startScenario("scene-1")
        val savedMessages = conversationStore.listMessages(conversation.id)

        assertTrue(startResult.reusedExistingSession)
        assertTrue(startResult.hasHistory)
        assertTrue(startResult.conversationMessages.none { it.status == MessageStatus.LOADING })
        assertTrue(savedMessages.none { it.status == MessageStatus.LOADING })
        assertEquals(1, savedMessages.size)
        assertEquals(1, conversationStore.replaceConversationSnapshotCount)
    }

    @Test
    fun startScenario_seedsOpeningNarrationIntoNewConversation() = runBlocking {
        val dao = FakeRoleplayDao(
            scenarios = listOf(
                RoleplayScenarioEntity(
                    id = "scene-1",
                    title = "初遇",
                    description = "",
                    assistantId = "assistant-1",
                    backgroundUri = "",
                    userDisplayNameOverride = "",
                    userPersonaOverride = "",
                    userPortraitUri = "",
                    userPortraitUrl = "",
                    characterDisplayNameOverride = "",
                    characterPortraitUri = "",
                    characterPortraitUrl = "",
                    openingNarration = "夜色渐深。",
                    enableNarration = true,
                    enableRoleplayProtocol = true,
                    autoHighlightSpeaker = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val conversationStore = FakeConversationStore()
        val conversationRepository = ConversationRepository(
            conversationStore = conversationStore,
            nowProvider = { 10L },
        )
        val repository = RoomRoleplayRepository(
            roleplayDao = dao,
            conversationRepository = conversationRepository,
            nowProvider = { 10L },
        )

        val startResult = repository.startScenario("scene-1")
        val seededMessages = conversationStore.listMessages(startResult.session.conversationId)

        assertEquals(1, seededMessages.size)
        assertEquals(MessageRole.ASSISTANT, seededMessages.single().role)
        assertTrue(seededMessages.single().id.startsWith("rp-opening-"))
        assertTrue(seededMessages.single().content.contains("<narration>"))
        assertTrue(seededMessages.single().content.contains("夜色渐深。"))
    }

    @Test
    fun deleteScenario_removesBoundConversation() = runBlocking {
        val conversation = Conversation(
            id = "conversation-1",
            title = "旧剧情",
            model = "",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "assistant-1",
        )
        val dao = FakeRoleplayDao(
            scenarios = listOf(
                RoleplayScenarioEntity(
                    id = "scene-1",
                    title = "初遇",
                    description = "",
                    assistantId = "assistant-1",
                    backgroundUri = "",
                    userDisplayNameOverride = "",
                    userPersonaOverride = "",
                    userPortraitUri = "",
                    userPortraitUrl = "",
                    characterDisplayNameOverride = "",
                    characterPortraitUri = "",
                    characterPortraitUrl = "",
                    openingNarration = "",
                    enableNarration = true,
                    enableRoleplayProtocol = true,
                    autoHighlightSpeaker = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            sessions = listOf(
                RoleplaySessionEntity(
                    id = "session-1",
                    scenarioId = "scene-1",
                    conversationId = conversation.id,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val conversationStore = FakeConversationStore(
            conversations = listOf(conversation),
        )
        val conversationRepository = ConversationRepository(
            conversationStore = conversationStore,
            nowProvider = { 10L },
        )
        val repository = RoomRoleplayRepository(
            roleplayDao = dao,
            conversationRepository = conversationRepository,
            nowProvider = { 10L },
        )

        repository.deleteScenario("scene-1")

        assertTrue(conversationStore.listConversations().isEmpty())
        assertEquals(emptyList<RoleplayScenarioEntity>(), dao.listScenarios())
        assertNotNull(dao.deletedScenarioIds.singleOrNull())
    }
}

private class FakeRoleplayDao(
    scenarios: List<RoleplayScenarioEntity> = emptyList(),
    sessions: List<RoleplaySessionEntity> = emptyList(),
) : RoleplayDao {
    private val scenariosState = MutableStateFlow(scenarios)
    private val sessionsState = MutableStateFlow(sessions)
    private val diaryEntriesState = MutableStateFlow<List<RoleplayDiaryEntryEntity>>(emptyList())
    val deletedScenarioIds = mutableListOf<String>()

    override fun observeScenarios(): Flow<List<RoleplayScenarioEntity>> {
        return scenariosState
    }

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenarioEntity?> {
        return scenariosState.map { list -> list.firstOrNull { it.id == scenarioId } }
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySessionEntity?> {
        return sessionsState.map { list -> list.firstOrNull { it.scenarioId == scenarioId } }
    }

    override fun observeSessions(): Flow<List<RoleplaySessionEntity>> {
        return sessionsState
    }

    override fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntryEntity>> {
        return diaryEntriesState.map { list -> list.filter { it.conversationId == conversationId } }
    }

    override suspend fun listScenarios(): List<RoleplayScenarioEntity> {
        return scenariosState.value
    }

    override suspend fun listSessions(): List<RoleplaySessionEntity> {
        return sessionsState.value
    }

    override suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntryEntity> {
        return diaryEntriesState.value.filter { it.conversationId == conversationId }
    }

    override suspend fun getScenario(scenarioId: String): RoleplayScenarioEntity? {
        return scenariosState.value.firstOrNull { it.id == scenarioId }
    }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySessionEntity? {
        return sessionsState.value.firstOrNull { it.scenarioId == scenarioId }
    }

    override suspend fun getSession(sessionId: String): RoleplaySessionEntity? {
        return sessionsState.value.firstOrNull { it.id == sessionId }
    }

    override suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMetaEntity? = null

    override suspend fun upsertOnlineMeta(meta: RoleplayOnlineMetaEntity) = Unit

    override suspend fun deleteOnlineMeta(conversationId: String) = Unit

    override suspend fun upsertScenario(scenario: RoleplayScenarioEntity) {
        scenariosState.value = scenariosState.value.filterNot { it.id == scenario.id } + scenario
    }

    override suspend fun upsertSession(session: RoleplaySessionEntity) {
        sessionsState.value = sessionsState.value.filterNot { it.id == session.id } + session
    }

    override suspend fun upsertDiaryEntries(entries: List<RoleplayDiaryEntryEntity>) {
        val targetIds = entries.map { it.id }.toSet()
        diaryEntriesState.value = diaryEntriesState.value.filterNot { it.id in targetIds } + entries
    }

    override suspend fun deleteScenario(scenarioId: String) {
        deletedScenarioIds += scenarioId
        scenariosState.value = scenariosState.value.filterNot { it.id == scenarioId }
        sessionsState.value = sessionsState.value.filterNot { it.scenarioId == scenarioId }
    }

    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) {
        diaryEntriesState.value = diaryEntriesState.value.filterNot { it.conversationId == conversationId }
    }
}
