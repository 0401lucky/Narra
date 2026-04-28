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
        if (recentMessageWindow <= 0) {
            return false
        }
        val memoryProvider = settings.resolveFunctionProvider(ProviderFunction.MEMORY) ?: return false
        val memoryModel = settings.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) {
            return false
        }
        val recentMessages = completedMessages.takeLast(recentMessageWindow)
        val memoryInput = buildMemoryInput(recentMessages)
        if (memoryInput.isBlank()) {
            return false
        }
        val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
        val existingMemoriesForPrompt = collectExistingChatMemories(assistant)
        val memoryItems = aiPromptExtrasService.generateMemoryEntries(
            conversationExcerpt = memoryInput,
            baseUrl = memoryProvider.baseUrl,
            apiKey = memoryProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = memoryProvider.resolvedApiProtocol(),
            provider = memoryProvider,
            existingMemories = existingMemoriesForPrompt,
            userName = settings.resolvedUserDisplayName(),
            characterName = assistant.name.trim().ifBlank { "角色" },
            extractionPromptOverride = settings.memoryExtractionPrompt,
        )
        if (memoryItems.isEmpty()) {
            return false
        }
        persistLongTermMemories(
            assistant = assistant,
            latestMessageId = latestMessageId,
            memoryItems = memoryItems,
            baseUrl = memoryProvider.baseUrl,
            apiKey = memoryProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = memoryProvider.resolvedApiProtocol(),
        )
        memoryRepository.pruneToCapacity(settings.memoryCapacity)
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
        if (recentMessageWindow <= 0) {
            return false
        }
        val memoryProvider = settings.resolveFunctionProvider(ProviderFunction.MEMORY) ?: return false
        val memoryModel = settings.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) {
            return false
        }
        val recentMessages = completedMessages.takeLast(recentMessageWindow)
        val memoryInput = buildMemoryInput(recentMessages)
        if (memoryInput.isBlank()) {
            return false
        }
        val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
        val existingMemoriesForPrompt = collectExistingRoleplayMemories(
            conversationId = conversationId,
            assistant = assistant,
        )
        val memoryResult = aiPromptExtrasService.generateRoleplayMemoryEntries(
            conversationExcerpt = memoryInput,
            baseUrl = memoryProvider.baseUrl,
            apiKey = memoryProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = memoryProvider.resolvedApiProtocol(),
            provider = memoryProvider,
            existingMemories = existingMemoriesForPrompt,
        )
        if (memoryResult.persistentMemories.isEmpty() && memoryResult.sceneStateMemories.isEmpty() && memoryResult.mentalStateSnapshot.isBlank()) {
            return false
        }
        persistRoleplayMemories(
            conversationId = conversationId,
            assistant = assistant,
            latestMessageId = latestMessageId,
            memoryResult = memoryResult,
            sceneMemoryMaxItems = sceneMemoryMaxItems,
            baseUrl = memoryProvider.baseUrl,
            apiKey = memoryProvider.apiKey,
            modelId = memoryModel,
            apiProtocol = memoryProvider.resolvedApiProtocol(),
        )
        // 心境快照作为独立条目保存
        if (memoryResult.mentalStateSnapshot.isNotBlank()) {
            persistMentalState(
                conversationId = conversationId,
                characterId = assistant.id.trim(),
                mentalState = memoryResult.mentalStateSnapshot,
                latestMessageId = latestMessageId,
            )
        }
        memoryRepository.pruneToCapacity(settings.memoryCapacity)
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
        val condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1)
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
                        characterId = assistant.id.trim(),
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
            characterId = assistant.id.trim(),
            memoryItems = memoryResult.persistentMemories,
            latestMessageId = latestMessageId,
            importance = 60,
            maxItems = assistant.memoryMaxItems.coerceAtLeast(1),
            condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1),
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
            characterId = assistant.id.trim(),
            memoryItems = memoryResult.sceneStateMemories,
            latestMessageId = latestMessageId,
            importance = 70,
            maxItems = sceneMemoryMaxItems,
            condensedTargetCount = sceneMemoryMaxItems.coerceAtLeast(1),
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
        characterId: String = "",
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
                    characterId = characterId,
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

    /**
     * 提取当前 assistant 已存的长期记忆作为"已知信息"——用于让记忆提取 prompt 做去重，
     * 对齐 Tavo 提取行为。
     */
    private suspend fun collectExistingChatMemories(assistant: Assistant): List<String> {
        val scopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val scopeId = if (assistant.useGlobalMemory) "" else assistant.id.trim()
        return memoryRepository.listEntries()
            .asSequence()
            .filter { entry -> entry.scopeType == scopeType && entry.resolvedScopeId() == scopeId }
            .map { it.content }
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    /**
     * 同上，针对沉浸剧情：覆盖 ASSISTANT/GLOBAL（人物记忆）+ CONVERSATION（场景记忆）+ 心境快照。
     */
    private suspend fun collectExistingRoleplayMemories(
        conversationId: String,
        assistant: Assistant,
    ): List<String> {
        val longScopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val longScopeId = if (assistant.useGlobalMemory) "" else assistant.id.trim()
        return memoryRepository.listEntries()
            .asSequence()
            .filter { entry ->
                (entry.scopeType == longScopeType && entry.resolvedScopeId() == longScopeId) ||
                    (entry.scopeType == MemoryScopeType.CONVERSATION && entry.resolvedScopeId() == conversationId)
            }
            .map { it.content }
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    /**
     * 心境快照使用固定 ID（按 conversationId 派生），每次 upsert 覆盖旧值，
     * 确保一个场景只保留最新的角色心境。
     */
    private suspend fun persistMentalState(
        conversationId: String,
        characterId: String,
        mentalState: String,
        latestMessageId: String,
    ) {
        val timestamp = nowProvider()
        val entryId = "$MENTAL_STATE_ID_PREFIX$conversationId"
        memoryRepository.upsertEntry(
            MemoryEntry(
                id = entryId,
                scopeType = MemoryScopeType.CONVERSATION,
                scopeId = conversationId,
                characterId = characterId,
                content = "$MENTAL_STATE_CONTENT_PREFIX$mentalState",
                importance = 80,
                pinned = false,
                sourceMessageId = latestMessageId,
                lastUsedAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    companion object {
        const val MENTAL_STATE_ID_PREFIX = "mental-state:"
        const val MENTAL_STATE_CONTENT_PREFIX = "【心境】"
    }
}
