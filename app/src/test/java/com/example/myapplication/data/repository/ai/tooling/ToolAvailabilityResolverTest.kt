package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.Assistant
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceIds
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeWorldBookRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAvailabilityResolverTest {
    private val searchRepository = object : SearchRepository {
        override suspend fun search(
            source: SearchSourceConfig,
            query: String,
            resultCount: Int,
        ): SearchResult {
            error("测试不应执行实际搜索")
        }
    }
    private val memoryRepository = FakeMemoryRepository(
        initialEntries = listOf(
            MemoryEntry(
                id = "memory-1",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-1",
                content = "用户喜欢短句回复",
            ),
        ),
    )
    private val worldBookRepository = FakeWorldBookRepository(
        initialEntries = listOf(
            WorldBookEntry(
                id = "worldbook-1",
                title = "白塔城",
                content = "白塔城是北境最大的贸易都会。",
                enabled = true,
            ),
        ),
    )
    private val summaryRepository = FakeConversationSummaryRepository(
        initialSummaries = listOf(
            ConversationSummary(
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                summary = "已经确认主要线索指向北境商会。",
                coveredMessageCount = 8,
            ),
        ),
    )

    private val resolver = ToolAvailabilityResolver(
        searchRepository = searchRepository,
        memoryRepository = memoryRepository,
        worldBookRepository = worldBookRepository,
        conversationSummaryRepository = summaryRepository,
    )

    @Test
    fun resolve_enablesSearchToolWhenAllConditionsSatisfied() = runBlocking {
        val provider = toolCapableProvider()
        val resolved = resolver.resolve(
            settings = configuredSettings(provider),
            activeProvider = provider,
            selectedModel = provider.selectedModel,
            promptMode = PromptMode.CHAT,
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertEquals(setOf(SearchWebTool.NAME), resolved.enabledToolNames)
        assertTrue(resolved.toolContext.searchToolConfig != null)
    }

    @Test
    fun resolve_disablesSearchToolOutsideChatMode() = runBlocking {
        val provider = toolCapableProvider()
        val resolved = resolver.resolve(
            settings = configuredSettings(provider),
            activeProvider = provider,
            selectedModel = provider.selectedModel,
            promptMode = PromptMode.ROLEPLAY,
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertTrue(resolved.enabledToolNames.isEmpty())
        assertTrue(resolved.toolContext.searchToolConfig == null)
    }

    @Test
    fun resolve_disablesSearchToolWhenModelHasNoToolAbility() = runBlocking {
        val provider = toolCapableProvider().copy(
            models = listOf(
                ModelInfo(
                    modelId = "gpt-4.1-mini",
                    abilities = emptySet(),
                    abilitiesCustomized = true,
                ),
            ),
        )
        val resolved = resolver.resolve(
            settings = configuredSettings(provider),
            activeProvider = provider,
            selectedModel = provider.selectedModel,
            promptMode = PromptMode.CHAT,
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertTrue(resolved.enabledToolNames.isEmpty())
        assertTrue(resolved.toolContext.searchToolConfig == null)
    }

    @Test
    fun resolve_disablesSearchToolWhenToggleClosed() = runBlocking {
        val provider = toolCapableProvider()
        val resolved = resolver.resolve(
            settings = configuredSettings(provider),
            activeProvider = provider,
            selectedModel = provider.selectedModel,
            promptMode = PromptMode.CHAT,
            toolingOptions = GatewayToolingOptions.searchOnly(false),
        )

        assertTrue(resolved.enabledToolNames.isEmpty())
        assertTrue(resolved.toolContext.searchToolConfig == null)
    }

    @Test
    fun resolve_autoEnablesLocalContextToolsWhenRuntimeContextAvailable() = runBlocking {
        val provider = toolCapableProvider()
        val resolved = resolver.resolve(
            settings = configuredSettings(provider),
            activeProvider = provider,
            selectedModel = provider.selectedModel,
            promptMode = PromptMode.ROLEPLAY,
            toolingOptions = GatewayToolingOptions.localContextOnly(
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        assertEquals(
            setOf(
                ReadMemoryTool.NAME,
                GetConversationSummaryTool.NAME,
                SearchWorldBookTool.NAME,
                SaveMemoryTool.NAME,
            ),
            resolved.enabledToolNames,
        )
    }

    private fun toolCapableProvider(): ProviderSettings {
        return ProviderSettings(
            id = "provider-1",
            name = "OpenAI",
            baseUrl = "https://example.com/v1/",
            apiKey = "saved-key",
            selectedModel = "gpt-4.1-mini",
            models = listOf(
                ModelInfo(
                    modelId = "gpt-4.1-mini",
                    abilities = setOf(ModelAbility.TOOL),
                ),
            ),
        )
    }

    private fun configuredSettings(
        provider: ProviderSettings,
    ): AppSettings {
        return AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.BRAVE,
                        type = SearchSourceType.BRAVE,
                        name = "Brave 搜索",
                        enabled = true,
                        apiKey = "search-key",
                    ),
                ),
                selectedSourceId = SearchSourceIds.BRAVE,
                defaultResultCount = 3,
            ),
        )
    }
}
