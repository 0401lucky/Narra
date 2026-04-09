package com.example.myapplication.viewmodel

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.conversation.PhoneContextBuilder
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneCheckViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadSnapshot_invalidatesStaleUserPhoneSnapshot() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val settingsRepository = object : AiSettingsRepository {
            override val settingsFlow = MutableStateFlow(
                AppSettings(
                    userDisplayName = "lucky",
                    assistants = listOf(Assistant(id = "assistant-1", name = "沈砚清")),
                    selectedAssistantId = "assistant-1",
                ),
            )
        }
        val conversationRepository = ConversationRepository(
            conversationStore = FakeConversationStore(
                conversations = listOf(
                    Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                        assistantId = "assistant-1",
                    ),
                ),
            ),
        )
        val roleplayRepository = FakePhoneCheckRoleplayRepository(
            scenario = RoleplayScenario(
                id = "scene-1",
                assistantId = "assistant-1",
                userDisplayNameOverride = "lucky",
                characterDisplayNameOverride = "沈砚清",
            ),
        )
        val phoneSnapshotRepository = RecordingPhoneSnapshotRepository(
            snapshots = mutableMapOf(
                ("conversation-1" to PhoneSnapshotOwnerType.USER) to PhoneSnapshot(
                    conversationId = "conversation-1",
                    ownerType = PhoneSnapshotOwnerType.USER,
                    ownerName = "lucky",
                    contentSemanticsVersion = PhoneSnapshot.DEFAULT_PHONE_CONTENT_SEMANTICS_VERSION,
                ),
            ),
        )

        val viewModel = PhoneCheckViewModel(
            initialConversationId = "conversation-1",
            initialScenarioId = "scene-1",
            initialOwnerType = PhoneSnapshotOwnerType.USER,
            settingsRepository = settingsRepository,
            conversationRepository = conversationRepository,
            roleplayRepository = roleplayRepository,
            phoneSnapshotRepository = phoneSnapshotRepository,
            aiPromptExtrasService = NoOpAiPromptExtrasService,
            phoneContextBuilder = PhoneContextBuilder(
                promptContextAssembler = object : PromptContextAssembler {
                    override suspend fun assemble(
                        settings: AppSettings,
                        assistant: Assistant?,
                        conversation: Conversation,
                        userInputText: String,
                        recentMessages: List<ChatMessage>,
                        promptMode: PromptMode,
                        includePhoneSnapshot: Boolean,
                    ): PromptContextResult {
                        return PromptContextResult(systemPrompt = "")
                    }
                },
            ),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("lucky", state.ownerName)
        assertNull(state.snapshot)
        assertEquals("“我的手机”规则已更新，请重新生成手机内容", state.noticeMessage)
        assertEquals(
            listOf("conversation-1" to PhoneSnapshotOwnerType.USER),
            phoneSnapshotRepository.deletedScopedSnapshots,
        )
        assertEquals(listOf("conversation-1"), phoneSnapshotRepository.deletedObservations)
    }
}

private class RecordingPhoneSnapshotRepository(
    private val snapshots: MutableMap<Pair<String, PhoneSnapshotOwnerType>, PhoneSnapshot> = mutableMapOf(),
) : PhoneSnapshotRepository {
    val deletedScopedSnapshots = mutableListOf<Pair<String, PhoneSnapshotOwnerType>>()
    val deletedObservations = mutableListOf<String>()

    override fun observeSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): Flow<PhoneSnapshot?> = flowOf(snapshots[conversationId to ownerType])

    override suspend fun getSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): PhoneSnapshot? = snapshots[conversationId to ownerType]

    override suspend fun upsertSnapshot(snapshot: PhoneSnapshot) {
        snapshots[snapshot.conversationId to snapshot.ownerType] = snapshot
    }

    override suspend fun deleteSnapshot(conversationId: String) {
        snapshots.keys.removeAll { it.first == conversationId }
    }

    override suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ) {
        deletedScopedSnapshots += conversationId to ownerType
        snapshots.remove(conversationId to ownerType)
    }

    override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> = flowOf(null)

    override suspend fun getObservation(conversationId: String): PhoneObservationState? = null

    override suspend fun upsertObservation(observation: PhoneObservationState) = Unit

    override suspend fun deleteObservation(conversationId: String) {
        deletedObservations += conversationId
    }
}

private class FakePhoneCheckRoleplayRepository(
    private val scenario: RoleplayScenario?,
) : RoleplayRepository {
    override fun observeScenarios(): Flow<List<RoleplayScenario>> = flowOf(scenario?.let(::listOf).orEmpty())

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> {
        return flowOf(scenario?.takeIf { it.id == scenarioId })
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> = flowOf(null)

    override fun observeSessions(): Flow<List<RoleplaySession>> = flowOf(emptyList())

    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> = flowOf(emptyList())

    override suspend fun listScenarios(): List<RoleplayScenario> = scenario?.let(::listOf).orEmpty()

    override suspend fun getScenario(scenarioId: String): RoleplayScenario? {
        return scenario?.takeIf { it.id == scenarioId }
    }

    override suspend fun upsertScenario(scenario: RoleplayScenario) = Unit

    override suspend fun deleteScenario(scenarioId: String) = Unit

    override suspend fun startScenario(scenarioId: String) = throw UnsupportedOperationException()

    override suspend fun restartScenario(scenarioId: String) = throw UnsupportedOperationException()

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? = null

    override suspend fun getSession(sessionId: String): RoleplaySession? = null

    override suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta? = null

    override suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta) = Unit

    override suspend fun deleteOnlineMeta(conversationId: String) = Unit
}

private object NoOpAiPromptExtrasService : AiPromptExtrasService {
    override suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): String = ""

    override suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): List<String> = emptyList()

    override suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): String = ""

    override suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): String = ""

    override suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): List<String> = emptyList()

    override suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): com.example.myapplication.data.repository.StructuredMemoryExtractionResult {
        return com.example.myapplication.data.repository.StructuredMemoryExtractionResult()
    }

    override suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
        longformMode: Boolean,
    ): List<RoleplaySuggestionUiModel> = emptyList()

    override suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: com.example.myapplication.data.repository.RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings?,
    ): List<String> = emptyList()
}
