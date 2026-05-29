package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.roleplay.RoleplayOutputParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayObservationSupportTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observeMappedMessages_onlyReactsToMapperInputs() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val currentRawMessages = MutableStateFlow(emptyList<com.example.myapplication.model.ChatMessage>())
        val settings = MutableStateFlow(AppSettings())
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = "assistant-1",
            characterDisplayNameOverride = "陆宴清",
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        var nowProviderCallCount = 0
        val uiState = MutableStateFlow(
            RoleplayUiState(
                currentScenario = scenario,
                currentAssistant = assistant,
                isSending = true,
                streamingContent = "我在想你。",
            ),
        )
        val observerScope = CoroutineScope(coroutineContext + Job())

        RoleplayObservationSupport.observeMappedMessages(
            scope = observerScope,
            currentRawMessages = currentRawMessages,
            settings = settings,
            uiState = uiState,
            outputParser = RoleplayOutputParser(),
            nowProvider = {
                nowProviderCallCount += 1
                100L
            },
        )

        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)
        assertEquals(1, uiState.value.messages.size)
        assertEquals("我在想你。", uiState.value.messages.single().content)

        uiState.update { current ->
            current.copy(errorMessage = "这不该触发重新映射")
        }
        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)

        uiState.update { current ->
            current.copy(noticeMessage = "同样不该触发重新映射")
        }
        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)

        uiState.update { current ->
            current.copy(streamingContent = "还是先等你开口。")
        }
        advanceUntilIdle()

        assertEquals(2, nowProviderCallCount)
        assertTrue(uiState.value.messages.single().content.contains("还是先等你开口"))
        observerScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun observeCurrentMessages_skipsRoomUpdatesWhileSending() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val optimisticMessage = ChatMessage(
            id = "optimistic-1",
            role = MessageRole.USER,
            content = "乐观写入的消息",
        )
        val lateRoomMessage = ChatMessage(
            id = "room-late-1",
            role = MessageRole.ASSISTANT,
            content = "Room 迟到帧",
        )
        val currentRawMessages = MutableStateFlow(listOf(optimisticMessage))
        val currentScenarioId = MutableStateFlow<String?>("scene-1")
        val roomMessages = MutableStateFlow(listOf(lateRoomMessage))
        var sending = true
        val repository = MessagesOnlyRoleplayRepository(roomMessages)
        val observerScope = CoroutineScope(coroutineContext + Job())

        RoleplayObservationSupport.observeCurrentMessages(
            scope = observerScope,
            roleplayRepository = repository,
            currentRawMessages = currentRawMessages,
            currentScenarioId = currentScenarioId,
            isSending = { sending },
        )

        advanceUntilIdle()

        // 发送期间 Room 迟到帧不得覆盖发送链路的乐观写入。
        assertEquals(listOf(optimisticMessage), currentRawMessages.value)

        sending = false
        val nextRoomMessage = ChatMessage(
            id = "room-next-1",
            role = MessageRole.ASSISTANT,
            content = "发送结束后的 Room 快照",
        )
        roomMessages.value = listOf(nextRoomMessage)
        advanceUntilIdle()

        // 非发送期 Room 恢复驱动 currentRawMessages。
        assertEquals(listOf(nextRoomMessage), currentRawMessages.value)
        observerScope.coroutineContext[Job]?.cancel()
    }

    private class MessagesOnlyRoleplayRepository(
        private val messages: Flow<List<ChatMessage>>,
    ) : RoleplayRepository {
        override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> = messages

        override fun observeScenarios(): Flow<List<RoleplayScenario>> = error("测试未使用")

        override fun observeChatSummaries(): Flow<List<RoleplayChatSummary>> = error("测试未使用")

        override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> = error("测试未使用")

        override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> = error("测试未使用")

        override fun observeSessions(): Flow<List<RoleplaySession>> = error("测试未使用")

        override fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>> = error("测试未使用")

        override suspend fun listScenarios(): List<RoleplayScenario> = error("测试未使用")

        override suspend fun getScenario(scenarioId: String): RoleplayScenario? = error("测试未使用")

        override suspend fun upsertScenario(scenario: RoleplayScenario) = error("测试未使用")

        override suspend fun deleteScenario(scenarioId: String) = error("测试未使用")

        override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult = error("测试未使用")

        override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult = error("测试未使用")

        override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? = error("测试未使用")

        override suspend fun getSession(sessionId: String): RoleplaySession? = error("测试未使用")

        override suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry> = error("测试未使用")

        override suspend fun replaceDiaryEntries(
            conversationId: String,
            scenarioId: String,
            entries: List<RoleplayDiaryDraft>,
        ): List<RoleplayDiaryEntry> = error("测试未使用")

        override suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta? = error("测试未使用")

        override suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta) = error("测试未使用")

        override suspend fun deleteOnlineMeta(conversationId: String) = error("测试未使用")

        override suspend fun deleteDiaryEntriesForConversation(conversationId: String) = error("测试未使用")
    }
}
