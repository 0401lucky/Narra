package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.InMemoryPendingMemoryProposalRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryWriteServiceTest {
    @Test
    fun saveSceneMemory_deduplicatesWithinConversationScope() = runBlocking {
        val repository = FakeMemoryRepository(
            initialEntries = listOf(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "conversation-1",
                    content = "他已经承认知道密门位置。",
                    importance = 60,
                ),
            ),
        )
        val service = createService(repository)

        val result = service.saveSceneMemory(
            toolContext = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryRepository = repository,
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                    recentMessages = listOf(
                        com.example.myapplication.model.ChatMessage(
                            id = "message-1",
                            conversationId = "conversation-1",
                            role = com.example.myapplication.model.MessageRole.USER,
                            content = "继续问她",
                            createdAt = 1L,
                        ),
                    ),
                ),
            ),
            content = "他已经承认知道密门位置。",
            importance = 70,
        )

        assertTrue(result.deduplicated)
        assertEquals(1, repository.currentEntries().size)
        assertEquals(70, repository.currentEntries().single().importance)
    }

    @Test
    fun proposeAndApprovePersistentMemory_writesToAssistantScope() = runBlocking {
        val repository = FakeMemoryRepository()
        val proposalRepository = InMemoryPendingMemoryProposalRepository()
        val service = createService(
            memoryRepository = repository,
            proposalRepository = proposalRepository,
        )
        val toolContext = ToolContext(
            searchRepository = fakeSearchRepository(),
            memoryRepository = repository,
            runtimeContext = GatewayToolRuntimeContext(
                promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
                assistant = Assistant(
                    id = "assistant-1",
                    memoryEnabled = true,
                    useGlobalMemory = false,
                    memoryMaxItems = 6,
                ),
                conversation = Conversation(
                    id = "conversation-1",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )

        val proposal = service.proposePersistentMemory(
            toolContext = toolContext,
            content = "她不喜欢被突然逼问。",
            reason = "这是稳定偏好",
            importance = 60,
        )
        assertNotNull(proposalRepository.getProposal(proposal.id))

        val savedEntry = service.approveProposal(proposal.id)
        assertNotNull(savedEntry)
        assertEquals(MemoryScopeType.ASSISTANT, savedEntry?.scopeType)
        assertEquals("assistant-1", savedEntry?.scopeId)
        assertEquals("她不喜欢被突然逼问。", savedEntry?.content)
        assertTrue(proposalRepository.getProposal(proposal.id) == null)
    }

    @Test
    fun saveSceneMemory_respectsSceneScopeLimitWithoutOverCondensing() = runBlocking {
        val repository = FakeMemoryRepository(
            initialEntries = (1..12).map { index ->
                MemoryEntry(
                    id = "scene-$index",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "conversation-1",
                    content = "剧情记忆$index",
                    importance = 60,
                    sourceMessageId = "old-$index",
                    lastUsedAt = index.toLong(),
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                )
            },
        )
        val service = createService(repository)

        service.saveSceneMemory(
            toolContext = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryRepository = repository,
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                    recentMessages = listOf(
                        com.example.myapplication.model.ChatMessage(
                            id = "message-13",
                            conversationId = "conversation-1",
                            role = com.example.myapplication.model.MessageRole.USER,
                            content = "继续推进",
                            createdAt = 13L,
                        ),
                    ),
                ),
            ),
            content = "剧情记忆13",
            importance = 70,
        )

        val entries = repository.currentEntries()
        assertEquals(12, entries.size)
        assertTrue(entries.any { it.content == "剧情记忆13" })
        assertTrue(entries.any { it.content == "剧情记忆2" })
    }

    private fun createService(
        memoryRepository: FakeMemoryRepository,
        proposalRepository: InMemoryPendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
    ): DefaultMemoryWriteService {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            memoryModel = "memory-model",
        )
        val settingsStore = FakeSettingsStore(
            AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                        memoryMaxItems = 6,
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
        )
        return DefaultMemoryWriteService(
            settingsStore = settingsStore,
            memoryRepository = memoryRepository,
            pendingMemoryProposalRepository = proposalRepository,
            aiPromptExtrasService = object : AiPromptExtrasService {
                override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?): String = error("不应调用")
                override suspend fun generateChatSuggestions(conversationSummary: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?): List<String> = error("不应调用")
                override suspend fun generateConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?): String = error("不应调用")
                override suspend fun generateRoleplayConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?): String = error("不应调用")
                override suspend fun generateMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?, existingMemories: List<String>, userName: String, characterName: String, extractionPromptOverride: String): List<String> = error("不应调用")
                override suspend fun generateRoleplayMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?, existingMemories: List<String>): StructuredMemoryExtractionResult = error("不应调用")
                override suspend fun generateRoleplaySuggestions(conversationExcerpt: String, systemPrompt: String, playerStyleReference: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?, longformMode: Boolean): List<RoleplaySuggestionUiModel> = error("不应调用")
                override suspend fun condenseRoleplayMemories(memoryItems: List<String>, mode: RoleplayMemoryCondenseMode, maxItems: Int, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: ProviderSettings?): List<String> {
                    return memoryItems.take(maxItems)
                }
            },
            nowProvider = { 100L },
        )
    }

    private fun fakeSearchRepository(): com.example.myapplication.data.repository.search.SearchRepository {
        return object : com.example.myapplication.data.repository.search.SearchRepository {
            override suspend fun search(
                source: com.example.myapplication.model.SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): com.example.myapplication.data.repository.search.SearchResult {
                error("测试不应执行搜索")
            }
        }
    }
}
