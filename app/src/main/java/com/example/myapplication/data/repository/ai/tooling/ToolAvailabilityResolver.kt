package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.context.MemoryScopeSupport
import com.example.myapplication.context.WorldBookScopeSupport
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings

data class ResolvedGatewayTooling(
    val enabledToolNames: Set<String> = emptySet(),
    val toolContext: ToolContext,
)

class ToolAvailabilityResolver(
    private val searchRepository: SearchRepository,
    private val memoryRepository: MemoryRepository,
    private val worldBookRepository: WorldBookRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val memoryWriteService: MemoryWriteService = NoOpMemoryWriteService,
) {
    suspend fun resolve(
        settings: AppSettings,
        activeProvider: ProviderSettings?,
        selectedModel: String,
        promptMode: PromptMode,
        toolingOptions: GatewayToolingOptions,
    ): ResolvedGatewayTooling {
        val runtimeContext = toolingOptions.runtimeContext
        val modelSupportsTools = selectedModel.isNotBlank() &&
            ModelAbility.TOOL in activeProvider?.resolveModelAbilities(selectedModel).orEmpty()
        val enabledToolNames = linkedSetOf<String>()
        if (modelSupportsTools && runtimeContext != null) {
            if (hasReadableMemories(runtimeContext)) {
                enabledToolNames += ReadMemoryTool.NAME
            }
            if (hasCachedSummary(runtimeContext)) {
                enabledToolNames += GetConversationSummaryTool.NAME
            }
            if (hasAccessibleWorldBookEntries(runtimeContext)) {
                enabledToolNames += SearchWorldBookTool.NAME
            }
            if (canSaveMemory(runtimeContext)) {
                enabledToolNames += SaveMemoryTool.NAME
            }
        }
        val searchToolConfig = resolveSearchToolConfig(
            settings = settings,
            activeProvider = activeProvider,
            modelSupportsTools = modelSupportsTools,
            promptMode = promptMode,
            enabledToolNames = toolingOptions.enabledToolNames,
        )
        if (searchToolConfig != null) {
            enabledToolNames += SearchWebTool.NAME
        }
        return ResolvedGatewayTooling(
            enabledToolNames = enabledToolNames,
            toolContext = ToolContext(
                searchRepository = searchRepository,
                memoryRepository = memoryRepository,
                worldBookRepository = worldBookRepository,
                conversationSummaryRepository = conversationSummaryRepository,
                memoryWriteService = memoryWriteService,
                runtimeContext = runtimeContext,
                searchToolConfig = searchToolConfig,
            ),
        )
    }

    private suspend fun hasReadableMemories(
        runtimeContext: GatewayToolRuntimeContext,
    ): Boolean {
        val assistant = runtimeContext.assistant ?: return false
        if (!assistant.memoryEnabled) {
            return false
        }
        val conversation = runtimeContext.conversation ?: return false
        return MemoryScopeSupport.filterAccessibleEntries(
            entries = memoryRepository.listEntries(),
            assistant = assistant,
            conversation = conversation,
        ).isNotEmpty()
    }

    private suspend fun hasAccessibleWorldBookEntries(
        runtimeContext: GatewayToolRuntimeContext,
    ): Boolean {
        val conversation = runtimeContext.conversation ?: return false
        return WorldBookScopeSupport.filterAccessibleEntries(
            entries = worldBookRepository.listEnabledEntries(),
            assistant = runtimeContext.assistant,
            conversation = conversation,
        ).isNotEmpty()
    }

    private suspend fun hasCachedSummary(
        runtimeContext: GatewayToolRuntimeContext,
    ): Boolean {
        val conversationId = runtimeContext.conversation?.id.orEmpty()
        if (conversationId.isBlank()) {
            return false
        }
        return conversationSummaryRepository.getSummary(conversationId)
            ?.summary
            ?.isNotBlank() == true
    }

    private fun canSaveMemory(
        runtimeContext: GatewayToolRuntimeContext,
    ): Boolean {
        return runtimeContext.promptMode == PromptMode.ROLEPLAY &&
            runtimeContext.assistant?.memoryEnabled == true &&
            runtimeContext.conversation != null
    }

    private fun resolveSearchToolConfig(
        settings: AppSettings,
        activeProvider: ProviderSettings?,
        modelSupportsTools: Boolean,
        promptMode: PromptMode,
        enabledToolNames: Set<String>,
    ): SearchToolConfig? {
        if (
            !modelSupportsTools ||
            promptMode != PromptMode.CHAT ||
            SearchWebTool.NAME !in enabledToolNames
        ) {
            return null
        }
        val source = settings.activeSearchSource(activeProvider) ?: return null
        return SearchToolConfig(
            source = source,
            resultCount = settings.resolvedSearchSettings().defaultResultCount,
        )
    }
}
