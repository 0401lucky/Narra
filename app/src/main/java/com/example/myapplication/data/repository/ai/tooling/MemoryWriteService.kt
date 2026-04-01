package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.ProviderFunction
import kotlinx.coroutines.flow.first

data class MemoryWriteResult(
    val scopeType: MemoryScopeType,
    val deduplicated: Boolean,
)

interface MemoryWriteService {
    suspend fun saveSceneMemory(
        toolContext: ToolContext,
        content: String,
        importance: Int,
    ): MemoryWriteResult

    suspend fun proposePersistentMemory(
        toolContext: ToolContext,
        content: String,
        reason: String,
        importance: Int,
    ): PendingMemoryProposal

    suspend fun approveProposal(
        proposalId: String,
    ): MemoryEntry?

    suspend fun rejectProposal(
        proposalId: String,
    )
}

class DefaultMemoryWriteService(
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val pendingMemoryProposalRepository: PendingMemoryProposalRepository,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val roleplaySceneMemoryMaxItems: Int = 12,
) : MemoryWriteService {
    override suspend fun saveSceneMemory(
        toolContext: ToolContext,
        content: String,
        importance: Int,
    ): MemoryWriteResult {
        val runtimeContext = toolContext.runtimeContext
            ?: error("当前没有可用会话上下文")
        val conversation = runtimeContext.conversation
            ?: error("当前没有可用会话")
        val normalizedContent = normalizeMemoryContent(content)
        val normalizedScopeId = conversation.id
        val existingEntries = memoryRepository.listEntries()
        val duplicateEntry = existingEntries.firstOrNull { entry ->
            entry.scopeType == MemoryScopeType.CONVERSATION &&
                entry.resolvedScopeId() == normalizedScopeId &&
                normalizeMemoryContent(entry.content) == normalizedContent
        }
        val latestMessageId = runtimeContext.recentMessages.lastOrNull()?.id.orEmpty()
        val timestamp = nowProvider()
        if (duplicateEntry != null) {
            memoryRepository.upsertEntry(
                duplicateEntry.copy(
                    content = normalizedContent,
                    importance = maxOf(duplicateEntry.importance, importance),
                    sourceMessageId = latestMessageId.ifBlank { duplicateEntry.sourceMessageId },
                    lastUsedAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            enforceScopeLimit(
                scopeType = MemoryScopeType.CONVERSATION,
                scopeId = normalizedScopeId,
                maxItems = roleplaySceneMemoryMaxItems,
                condenseMode = RoleplayMemoryCondenseMode.SCENE,
            )
            return MemoryWriteResult(
                scopeType = MemoryScopeType.CONVERSATION,
                deduplicated = true,
            )
        }

        memoryRepository.upsertEntry(
            MemoryEntry(
                scopeType = MemoryScopeType.CONVERSATION,
                scopeId = normalizedScopeId,
                content = normalizedContent,
                importance = importance,
                pinned = false,
                sourceMessageId = latestMessageId,
                lastUsedAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        enforceScopeLimit(
            scopeType = MemoryScopeType.CONVERSATION,
            scopeId = normalizedScopeId,
            maxItems = roleplaySceneMemoryMaxItems,
            condenseMode = RoleplayMemoryCondenseMode.SCENE,
        )
        return MemoryWriteResult(
            scopeType = MemoryScopeType.CONVERSATION,
            deduplicated = false,
        )
    }

    override suspend fun proposePersistentMemory(
        toolContext: ToolContext,
        content: String,
        reason: String,
        importance: Int,
    ): PendingMemoryProposal {
        val runtimeContext = toolContext.runtimeContext
            ?: error("当前没有可用会话上下文")
        val assistant = runtimeContext.assistant
            ?: error("当前没有可用助手")
        val conversation = runtimeContext.conversation
            ?: error("当前没有可用会话")
        val proposal = PendingMemoryProposal(
            conversationId = conversation.id,
            assistantId = assistant.id,
            scopeType = if (assistant.useGlobalMemory) {
                MemoryScopeType.GLOBAL
            } else {
                MemoryScopeType.ASSISTANT
            },
            content = normalizeMemoryContent(content),
            reason = reason.trim(),
            importance = importance,
            createdAt = nowProvider(),
        )
        pendingMemoryProposalRepository.clearConversation(conversation.id)
        pendingMemoryProposalRepository.upsertProposal(proposal)
        return proposal
    }

    override suspend fun approveProposal(
        proposalId: String,
    ): MemoryEntry? {
        val proposal = pendingMemoryProposalRepository.getProposal(proposalId) ?: return null
        val normalizedContent = normalizeMemoryContent(proposal.content)
        val normalizedScopeId = when (proposal.scopeType) {
            MemoryScopeType.GLOBAL -> ""
            MemoryScopeType.ASSISTANT -> proposal.assistantId.trim()
            MemoryScopeType.CONVERSATION -> proposal.conversationId
        }
        val existingEntries = memoryRepository.listEntries()
        val duplicateEntry = existingEntries.firstOrNull { entry ->
            entry.scopeType == proposal.scopeType &&
                entry.resolvedScopeId() == normalizedScopeId &&
                normalizeMemoryContent(entry.content) == normalizedContent
        }
        val timestamp = nowProvider()
        val savedEntry = if (duplicateEntry != null) {
            duplicateEntry.copy(
                content = normalizedContent,
                importance = maxOf(duplicateEntry.importance, proposal.importance),
                updatedAt = timestamp,
                lastUsedAt = timestamp,
            ).also { updatedEntry ->
                memoryRepository.upsertEntry(updatedEntry)
            }
        } else {
            MemoryEntry(
                scopeType = proposal.scopeType,
                scopeId = normalizedScopeId,
                content = normalizedContent,
                importance = proposal.importance,
                pinned = false,
                lastUsedAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ).also { createdEntry ->
                memoryRepository.upsertEntry(createdEntry)
            }
        }
        val maxItems = when (proposal.scopeType) {
            MemoryScopeType.CONVERSATION -> roleplaySceneMemoryMaxItems
            MemoryScopeType.GLOBAL, MemoryScopeType.ASSISTANT -> {
                settingsStore.settingsFlow.first()
                    .resolvedAssistants()
                    .firstOrNull { assistant -> assistant.id == proposal.assistantId }
                    ?.memoryMaxItems
                    ?.takeIf { it > 0 }
                    ?: 6
            }
        }
        val condenseMode = if (proposal.scopeType == MemoryScopeType.CONVERSATION) {
            RoleplayMemoryCondenseMode.SCENE
        } else {
            RoleplayMemoryCondenseMode.CHARACTER
        }
        enforceScopeLimit(
            scopeType = proposal.scopeType,
            scopeId = normalizedScopeId,
            maxItems = maxItems,
            condenseMode = condenseMode,
        )
        pendingMemoryProposalRepository.markApproved(
            proposalId = proposalId,
            decidedAt = timestamp,
        )
        return savedEntry
    }

    override suspend fun rejectProposal(
        proposalId: String,
    ) {
        pendingMemoryProposalRepository.markRejected(
            proposalId = proposalId,
            decidedAt = nowProvider(),
        )
    }

    private suspend fun enforceScopeLimit(
        scopeType: MemoryScopeType,
        scopeId: String,
        maxItems: Int,
        condenseMode: RoleplayMemoryCondenseMode,
    ) {
        val normalizedScopeId = scopeId.trim()
        val scopedEntries = memoryRepository.listEntries()
            .filter { entry ->
                entry.scopeType == scopeType && entry.resolvedScopeId() == normalizedScopeId
            }
            .sortedWith(
                compareByDescending<MemoryEntry> { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt },
            )
        if (scopedEntries.size <= maxItems.coerceAtLeast(1)) {
            return
        }

        val pinnedEntries = scopedEntries.filter { it.pinned }
        val mutableEntries = scopedEntries.filterNot { it.pinned }
        val targetCount = when (scopeType) {
            MemoryScopeType.CONVERSATION -> 4
            MemoryScopeType.GLOBAL, MemoryScopeType.ASSISTANT -> maxItems.coerceAtLeast(1).coerceAtMost(3)
        }
        val normalizedItems = mutableEntries
            .map { entry -> normalizeMemoryContent(entry.content) }
            .filter { it.isNotBlank() }
            .distinct()
        val condensedItems = condenseItems(
            items = normalizedItems,
            mode = condenseMode,
            maxItems = targetCount,
        )

        mutableEntries.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
        }

        val timestamp = nowProvider()
        condensedItems
            .take((maxItems - pinnedEntries.size).coerceAtLeast(0))
            .forEachIndexed { index, content ->
                memoryRepository.upsertEntry(
                    MemoryEntry(
                        scopeType = scopeType,
                        scopeId = normalizedScopeId,
                        content = content,
                        importance = if (scopeType == MemoryScopeType.CONVERSATION) 70 else 60,
                        pinned = false,
                        lastUsedAt = timestamp + index,
                        createdAt = timestamp + index,
                        updatedAt = timestamp + index,
                    ),
                )
            }

        memoryRepository.listEntries()
            .filter { entry ->
                entry.scopeType == scopeType && entry.resolvedScopeId() == normalizedScopeId
            }
            .sortedWith(
                compareByDescending<MemoryEntry> { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt },
            )
            .drop(maxItems.coerceAtLeast(1))
            .forEach { entry ->
                memoryRepository.deleteEntry(entry.id)
            }
    }

    private suspend fun condenseItems(
        items: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
    ): List<String> {
        if (items.size <= maxItems.coerceAtLeast(1)) {
            return items
        }
        val settings = settingsStore.settingsFlow.first()
        val provider = settings.activeProvider() ?: return items.take(maxItems.coerceAtLeast(1))
        val modelId = provider.resolveFunctionModel(ProviderFunction.MEMORY)
        return runCatching {
            aiPromptExtrasService.condenseRoleplayMemories(
                memoryItems = items,
                mode = mode,
                maxItems = maxItems.coerceAtLeast(1),
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                modelId = modelId,
                apiProtocol = provider.resolvedApiProtocol(),
                provider = provider,
            )
        }.getOrDefault(items.take(maxItems.coerceAtLeast(1)))
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeMemoryContent(
        value: String,
    ): String {
        return value.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("-")
            .removePrefix("•")
            .trim()
    }
}

object NoOpMemoryWriteService : MemoryWriteService {
    override suspend fun saveSceneMemory(
        toolContext: ToolContext,
        content: String,
        importance: Int,
    ): MemoryWriteResult {
        error("当前环境未配置记忆写入服务")
    }

    override suspend fun proposePersistentMemory(
        toolContext: ToolContext,
        content: String,
        reason: String,
        importance: Int,
    ): PendingMemoryProposal {
        error("当前环境未配置记忆写入服务")
    }

    override suspend fun approveProposal(proposalId: String): MemoryEntry? = null

    override suspend fun rejectProposal(proposalId: String) = Unit
}
