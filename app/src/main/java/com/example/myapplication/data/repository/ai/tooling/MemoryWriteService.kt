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
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.conversation.MemoryExtractionBatcher
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.MEMORY_ENGINE_CURRENT
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.system.json.AppJson
import java.util.UUID

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
    private val memoryArchiveRepository: MemoryArchiveRepository? = null,
) : MemoryWriteService {
    override suspend fun saveSceneMemory(
        toolContext: ToolContext,
        content: String,
        importance: Int,
    ): MemoryWriteResult {
        if (archiveEnabled()) return saveArchiveScene(toolContext, content, importance)
        require(memoryArchiveRepository == null) { "当前使用兼容读取，自动记忆写入暂不可用" }
        val runtimeContext = toolContext.runtimeContext
            ?: error("当前没有可用会话上下文")
        val resolvedCharacterId = runtimeContext.assistant?.id?.trim().orEmpty()
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
                characterId = resolvedCharacterId,
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
        if (archiveEnabled()) {
            val (context, refs) = archiveSource(toolContext)
            val proposal = PendingMemoryProposal(
                conversationId = context.conversationId,
                assistantId = context.characterId,
                scopeType = MemoryScopeType.ASSISTANT,
                content = normalizeMemoryContent(content),
                reason = reason.trim(),
                importance = importance.coerceIn(0, 100),
                createdAt = nowProvider(),
                memoryContext = context,
                sourceRefs = refs,
            )
            pendingMemoryProposalRepository.upsertProposal(proposal)
            return proposal
        }
        require(memoryArchiveRepository == null) { "当前使用兼容读取，自动记忆建议暂不可用" }
        val runtimeContext = toolContext.runtimeContext
            ?: error("当前没有可用会话上下文")
        val assistant = runtimeContext.assistant
            ?: error("当前没有可用角色")
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
        proposal.memoryContext?.let { context ->
            val archive = memoryArchiveRepository ?: error("当前环境未配置记忆档案")
            require(
                settingsStore.settingsFlow.first().resolvedAssistants()
                    .any { it.id == context.characterId && it.memoryEnabled }) { "角色未开启记忆" }
            val result = archive.approveStoredProposal(proposalId)
            require(result is com.example.myapplication.model.MemoryWriteResult.Committed) { "建议来源或记忆归属已改变，请重新整理" }
            val id = result.recordIds.firstOrNull() ?: return null
            val record = archive.getRecordDetails(context, id)?.record ?: return null
            return MemoryEntry(
                id = record.id,
                scopeType = proposal.scopeType,
                scopeId = context.characterId,
                characterId = context.characterId,
                content = record.content,
                importance = record.importance,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt
            )
        }
        require(memoryArchiveRepository == null) { "旧建议请在档案中核对后手动保存" }
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
                characterId = proposal.assistantId.trim(),
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
        val targetCount = maxItems.coerceAtLeast(1)
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

        // 从被 condense 的旧条目中推断 characterId
        val inferredCharacterId = mutableEntries
            .map { it.characterId }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        val timestamp = nowProvider()
        condensedItems
            .take((maxItems - pinnedEntries.size).coerceAtLeast(0))
            .forEachIndexed { index, content ->
                memoryRepository.upsertEntry(
                    MemoryEntry(
                        scopeType = scopeType,
                        scopeId = normalizedScopeId,
                        characterId = inferredCharacterId,
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
        if (modelId.isBlank()) {
            return items.take(maxItems.coerceAtLeast(1))
        }
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
        return MemoryToolPayloadPolicy.normalizeContent(value)
    }

    private suspend fun archiveEnabled(): Boolean =
        (memoryArchiveRepository?.getEngineVersion() ?: 0) >= MEMORY_ENGINE_CURRENT

    private suspend fun archiveSource(toolContext: ToolContext): Pair<MemoryRuntimeContext, List<MemorySourceRef>> {
        val archive = requireNotNull(memoryArchiveRepository)
        val runtime = toolContext.runtimeContext ?: error("当前没有会话上下文")
        val context = runtime.memoryContext ?: error("请求缺少冻结的记忆归属")
        require(
            runtime.assistant?.id == context.characterId && runtime.conversation?.id == context.conversationId &&
                    archive.getRuntimeContext(
                        context.conversationId,
                        context.characterId
                    ) == context
        ) { "记忆归属已改变，请重新发送" }
        require(
            settingsStore.settingsFlow.first().resolvedAssistants()
                .any { it.id == context.characterId && it.memoryEnabled }) { "角色未开启记忆" }
        val message =
            runtime.recentMessages.lastOrNull { it.role == MessageRole.USER && it.status == MessageStatus.COMPLETED && !it.isRecalled }
                ?: error("记忆建议缺少已完成的用户来源")
        val source = archive.getSource(
            context,
            MemoryArchiveIdentity.id(
                "source",
                context.spaceId,
                message.id,
                message.memoryRevision.toString()
            )
        )
            ?: error("记忆建议来源尚未保存或已经改变")
        val refs = MemoryExtractionBatcher.sourceSpans(
            source,
            archive.getSourceSegments(context, source.id),
            context.characterId
        )
            .filter { it.skipReason.isBlank() && it.text.isNotBlank() }.map { it.ref }
        require(refs.isNotEmpty()) { "记忆建议没有可用的语义来源" }
        return context to refs
    }

    private suspend fun saveArchiveScene(
        toolContext: ToolContext,
        content: String,
        importance: Int
    ): MemoryWriteResult {
        val archive = requireNotNull(memoryArchiveRepository)
        val (context, refs) = archiveSource(toolContext)
        val normalized = normalizeMemoryContent(content)
        require(normalized.isNotBlank()) { "记忆正文不能为空" }
        val key = MemoryArchiveIdentity.id(
            "tool-memory",
            context.spaceId,
            AppJson.gson.toJson(refs),
            normalized
        )
        val job = archive.planToolJob(context, key, refs)
        val owner = UUID.randomUUID().toString()
        if (job.status == com.example.myapplication.model.MemoryJobStatus.COMMITTED) return MemoryWriteResult(
            MemoryScopeType.CONVERSATION,
            true
        )
        val claimed = archive.claimJob(job.id, owner) ?: error("当前记忆正在整理，请稍后重试")
        val batch = MemoryCommitBatch(
            records = listOf(
                MemoryRecordDraft(
                    "N0",
                    normalized,
                    refs,
                    importance = importance.coerceIn(0, 100)
                )
            )
        )
        if (claimed.artifact.isBlank()) check(
            archive.saveExtraction(
                job.id,
                owner,
                AppJson.gson.toJson(batch)
            )
        ) { "记忆归属已改变" }
        val result = archive.commitJob(job.id, owner, batch)
        check(result is com.example.myapplication.model.MemoryWriteResult.Committed) { "来源或记忆归属已改变，未保存" }
        return MemoryWriteResult(MemoryScopeType.CONVERSATION, result.alreadyCommitted)
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
