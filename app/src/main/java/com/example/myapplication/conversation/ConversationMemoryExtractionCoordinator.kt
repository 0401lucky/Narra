package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.ProviderFunction

class ConversationMemoryExtractionCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val memoryRepository: MemoryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun updateChatMemories(
        assistant: Assistant,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        recentMessageWindow: Int,
        buildMemoryInput: (List<ChatMessage>) -> String,
    ): Boolean {
        if (!assistant.memoryEnabled) {
            return false
        }
        val activeProvider = settings.activeProvider() ?: return false
        val memoryModel = activeProvider.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) {
            return false
        }
        val recentMessages = completedMessages.takeLast(recentMessageWindow)
        val memoryInput = buildMemoryInput(recentMessages)
        if (memoryInput.isBlank()) {
            return false
        }
        val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
        val memoryItems = aiPromptExtrasService.generateMemoryEntries(
            conversationExcerpt = memoryInput,
            baseUrl = activeProvider.baseUrl,
            apiKey = activeProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = activeProvider.resolvedApiProtocol(),
            provider = activeProvider,
        )
        if (memoryItems.isEmpty()) {
            return false
        }
        persistLongTermMemories(
            assistant = assistant,
            latestMessageId = latestMessageId,
            memoryItems = memoryItems,
            baseUrl = activeProvider.baseUrl,
            apiKey = activeProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = activeProvider.resolvedApiProtocol(),
        )
        return true
    }

    suspend fun updateRoleplayMemories(
        conversationId: String,
        assistant: Assistant,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        recentMessageWindow: Int,
        sceneMemoryMaxItems: Int,
        buildMemoryInput: (List<ChatMessage>) -> String,
    ): Boolean {
        if (!assistant.memoryEnabled) {
            return false
        }
        val activeProvider = settings.activeProvider() ?: return false
        val memoryModel = activeProvider.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) {
            return false
        }
        val recentMessages = completedMessages.takeLast(recentMessageWindow)
        val memoryInput = buildMemoryInput(recentMessages)
        if (memoryInput.isBlank()) {
            return false
        }
        val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
        val memoryResult = aiPromptExtrasService.generateRoleplayMemoryEntries(
            conversationExcerpt = memoryInput,
            baseUrl = activeProvider.baseUrl,
            apiKey = activeProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = activeProvider.resolvedApiProtocol(),
            provider = activeProvider,
        )
        if (memoryResult.persistentMemories.isEmpty() && memoryResult.sceneStateMemories.isEmpty()) {
            return false
        }
        persistRoleplayMemories(
            conversationId = conversationId,
            assistant = assistant,
            latestMessageId = latestMessageId,
            memoryResult = memoryResult,
            sceneMemoryMaxItems = sceneMemoryMaxItems,
            baseUrl = activeProvider.baseUrl,
            apiKey = activeProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = activeProvider.resolvedApiProtocol(),
        )
        return true
    }

    private suspend fun persistLongTermMemories(
        assistant: Assistant,
        latestMessageId: String,
        memoryItems: List<String>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
    ) {
        val scopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val scopeId = if (assistant.useGlobalMemory) {
            ""
        } else {
            assistant.id.trim()
        }
        if (scopeType == MemoryScopeType.ASSISTANT && scopeId.isBlank()) {
            return
        }
        val existingEntries = memoryRepository.listEntries().filter { entry ->
            entry.scopeType == scopeType && entry.resolvedScopeId() == scopeId
        }
        val pinnedEntries = existingEntries.filter { it.pinned }
        val mutableEntries = existingEntries.filterNot { it.pinned }
        val normalizedItems = (mutableEntries.map { normalizeMemoryContent(it.content) } + memoryItems.map(::normalizeMemoryContent))
            .filter { it.isNotBlank() }
            .distinct()
        val condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1).coerceAtMost(3)
        val condensedItems = if (normalizedItems.size > condensedTargetCount) {
            runCatching {
                aiPromptExtrasService.condenseRoleplayMemories(
                    memoryItems = normalizedItems,
                    mode = RoleplayMemoryCondenseMode.CHARACTER,
                    maxItems = condensedTargetCount,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                    apiProtocol = apiProtocol,
                    provider = null,
                )
            }.getOrDefault(normalizedItems.take(condensedTargetCount))
        } else {
            normalizedItems
        }
        mutableEntries.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
        }
        val timestamp = nowProvider()
        condensedItems
            .take((assistant.memoryMaxItems - pinnedEntries.size).coerceAtLeast(0))
            .forEachIndexed { index, content ->
                memoryRepository.upsertEntry(
                    MemoryEntry(
                        scopeType = scopeType,
                        scopeId = scopeId,
                        content = content,
                        importance = 60,
                        pinned = false,
                        sourceMessageId = latestMessageId,
                        lastUsedAt = timestamp + index,
                        createdAt = timestamp + index,
                        updatedAt = timestamp + index,
                    ),
                )
            }
    }

    private suspend fun persistRoleplayMemories(
        conversationId: String,
        assistant: Assistant,
        latestMessageId: String,
        memoryResult: StructuredMemoryExtractionResult,
        sceneMemoryMaxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
    ) {
        val existingEntries = memoryRepository.listEntries().toMutableList()
        val longTermScopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val longTermScopeId = if (assistant.useGlobalMemory) {
            ""
        } else {
            assistant.id.trim()
        }
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = longTermScopeType,
            scopeId = longTermScopeId,
            memoryItems = memoryResult.persistentMemories,
            latestMessageId = latestMessageId,
            importance = 60,
            maxItems = assistant.memoryMaxItems.coerceAtLeast(1),
            condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1).coerceAtMost(3),
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            condenseMode = RoleplayMemoryCondenseMode.CHARACTER,
        )
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = MemoryScopeType.CONVERSATION,
            scopeId = conversationId,
            memoryItems = memoryResult.sceneStateMemories,
            latestMessageId = latestMessageId,
            importance = 70,
            maxItems = sceneMemoryMaxItems,
            condensedTargetCount = 4,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            condenseMode = RoleplayMemoryCondenseMode.SCENE,
        )
    }

    private suspend fun persistMemoryGroup(
        existingEntries: MutableList<MemoryEntry>,
        scopeType: MemoryScopeType,
        scopeId: String,
        memoryItems: List<String>,
        latestMessageId: String,
        importance: Int,
        maxItems: Int,
        condensedTargetCount: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        condenseMode: RoleplayMemoryCondenseMode,
    ) {
        val normalizedScopeId = scopeId.trim()
        if (scopeType != MemoryScopeType.GLOBAL && normalizedScopeId.isBlank()) {
            return
        }
        if (memoryItems.isEmpty()) {
            return
        }
        val scopeEntries = existingEntries.filter { entry ->
            entry.scopeType == scopeType && entry.resolvedScopeId() == normalizedScopeId
        }
        val pinnedEntries = scopeEntries.filter { it.pinned }
        val mutableEntries = scopeEntries.filterNot { it.pinned }
        val timestamp = nowProvider()
        val normalizedNewItems = memoryItems
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
        val combinedItems = (mutableEntries.map { entry -> normalizeMemoryContent(entry.content) } + normalizedNewItems)
            .filter { it.isNotBlank() }
            .distinct()
        val targetCount = condensedTargetCount.coerceAtLeast(1)
        val condensedItems = if (combinedItems.size > targetCount) {
            runCatching {
                aiPromptExtrasService.condenseRoleplayMemories(
                    memoryItems = combinedItems,
                    mode = condenseMode,
                    maxItems = targetCount,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                    apiProtocol = apiProtocol,
                    provider = null,
                )
            }.getOrDefault(combinedItems.take(targetCount))
        } else {
            combinedItems
        }
        mutableEntries.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
            existingEntries.removeAll { current -> current.id == entry.id }
        }
        val availableSlots = (maxItems - pinnedEntries.size).coerceAtLeast(0)
        condensedItems
            .take(availableSlots)
            .forEachIndexed { index, content ->
                val entry = MemoryEntry(
                    scopeType = scopeType,
                    scopeId = normalizedScopeId,
                    content = content,
                    importance = importance,
                    sourceMessageId = latestMessageId.trim(),
                    lastUsedAt = timestamp + index,
                    createdAt = timestamp + index,
                    updatedAt = timestamp + index,
                )
                memoryRepository.upsertEntry(entry)
                existingEntries += entry
            }
        pruneMemoryScope(
            existingEntries = existingEntries,
            scopeType = scopeType,
            scopeId = normalizedScopeId,
            maxItems = maxItems,
        )
    }

    private suspend fun pruneMemoryScope(
        existingEntries: MutableList<MemoryEntry>,
        scopeType: MemoryScopeType,
        scopeId: String,
        maxItems: Int,
    ) {
        val scopedEntries = existingEntries
            .filter { entry ->
                entry.scopeType == scopeType && entry.resolvedScopeId() == scopeId
            }
            .sortedWith(
                compareByDescending<MemoryEntry> { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt },
            )
        scopedEntries
            .drop(maxItems.coerceAtLeast(1))
            .forEach { entry ->
                memoryRepository.deleteEntry(entry.id)
                existingEntries.removeAll { current -> current.id == entry.id }
            }
    }

    private fun normalizeMemoryContent(value: String): String {
        return value.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("-")
            .removePrefix("•")
            .trim()
    }
}
