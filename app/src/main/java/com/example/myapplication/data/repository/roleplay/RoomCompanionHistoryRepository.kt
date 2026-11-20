package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryConversationBindingEntity
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.AllowedCompanionHistoryScope
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionHistoryBinding
import com.example.myapplication.model.CompanionHistoryCursor
import com.example.myapplication.model.CompanionHistorySelection
import com.example.myapplication.model.CompanionMessageRevision
import com.example.myapplication.model.CompanionSourceEligibility
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.companionSourceEligibility
import com.example.myapplication.system.json.AppJson

/** 只从当前空间的授权绑定选原文，绝不接收 UI 已加载消息作为兜底。 */
class RoomCompanionHistoryRepository(
    private val database: ChatDatabase,
    private val archive: MemoryArchiveRepository,
) : CompanionHistoryRepository {
    private val dao = database.memoryArchiveDao()
    private val store = RoomConversationStore(database)

    override suspend fun freeze(
        context: MemoryRuntimeContext,
        cutoff: CompanionHistoryCursor,
        selection: CompanionHistorySelection,
        originalScope: AllowedCompanionHistoryScope?,
    ): AllowedCompanionHistoryScope = database.withTransaction {
        check(archive.getRuntimeContext(context.conversationId, context.characterId) == context) {
            "当前角色、身份或记忆运行已改变"
        }
        check(originalScope == null || originalScope.context == context) { "原回合授权已失效" }
        val boundary = originalScope?.cutoff ?: cutoff
        val maxMessages = selection.maxMessages.coerceIn(0, 1_000)
        val starts = originalScope?.startsAfter ?: selection.startsAfter
        val exclusions = originalScope?.excludedMessageIds ?: selection.excludedMessageIds
        val originalIds = originalScope?.messages?.map { it.messageId }?.toSet()
        val bindings = allowedBindings(context).filter { binding ->
            selection.allowedConversationIds?.contains(binding.conversationId) != false &&
                    (originalScope == null || originalScope.bindings.any { it == binding })
        }
        val messages = bindings.flatMap { binding ->
            eligibleMessages(context, binding).filter { message ->
                val cursor = CompanionHistoryCursor(message.createdAt, message.id)
                cursor <= boundary && starts[message.conversationId]?.let { cursor > it } != false &&
                        message.id !in exclusions && originalIds?.contains(message.id) != false
            }
        }.distinctBy { it.id }.sortedWith(compareBy({ it.createdAt }, { it.id }))
            .takeLast(maxMessages)
        AllowedCompanionHistoryScope(
            context = context,
            bindings = bindings,
            messages = messages.map {
                CompanionMessageRevision(
                    it.id,
                    it.memoryRevision,
                    it.conversationId
                )
            },
            cutoff = boundary,
            startsAfter = starts,
            excludedMessageIds = exclusions,
            maxMessages = maxMessages,
        )
    }

    override suspend fun read(scope: AllowedCompanionHistoryScope): List<ChatMessage> =
        database.withTransaction {
            val messages = readValidated(scope)
            check(messages != null) { "本轮授权原文已改变，请重新发起" }
            messages
        }

    override suspend fun validate(scope: AllowedCompanionHistoryScope): Boolean =
        database.withTransaction { readValidated(scope) != null }

    private suspend fun readValidated(scope: AllowedCompanionHistoryScope): List<ChatMessage>? {
        if (archive.getRuntimeContext(
                scope.context.conversationId,
                scope.context.characterId
            ) != scope.context ||
            scope.maxMessages !in 0..1_000 || scope.messages.size > scope.maxMessages ||
            scope.messages.map { it.messageId }.distinct().size != scope.messages.size
        ) return null
        val allowed = allowedBindings(scope.context)
        if (scope.bindings.any { it !in allowed }) return null
        val byId =
            scope.bindings.flatMap { eligibleMessages(scope.context, it) }.associateBy { it.id }
        return scope.messages.map { ref ->
            val message = byId[ref.messageId] ?: return null
            val cursor = CompanionHistoryCursor(message.createdAt, message.id)
            if (message.conversationId != ref.conversationId || message.memoryRevision != ref.revision ||
                cursor > scope.cutoff || scope.startsAfter[message.conversationId]?.let { cursor <= it } == true ||
                message.id in scope.excludedMessageIds
            ) return null
            message
        }
    }

    private suspend fun allowedBindings(context: MemoryRuntimeContext): List<CompanionHistoryBinding> {
        val current =
            dao.getBinding(context.conversationId, context.characterId) ?: return emptyList()
        val continuous = MemoryPurpose.fromStorageValue(current.purpose) == MemoryPurpose.CONTINUOUS
        return dao.listSpaceBindings(context.spaceId).filter { binding ->
            binding.characterId == context.characterId && binding.personaKey == context.personaKey &&
                    (continuous || binding.conversationId == context.conversationId && binding.runId == context.runId)
        }.filter { binding ->
            val scenario = database.roleplayDao().getScenario(binding.scenarioId)
            scenario?.chatType == "single" && scenario.assistantId == context.characterId &&
                    dao.listBindings(binding.conversationId).size == 1
        }.map { it.domain() }
    }

    private suspend fun eligibleMessages(
        context: MemoryRuntimeContext,
        binding: CompanionHistoryBinding,
    ): List<ChatMessage> {
        // 首次身份凭据优先于会话后来切换的 persona，旧无证明行不在此补造授权。
        val sources = dao.listConversationSources(binding.conversationId).filter { source ->
            source.spaceId == context.spaceId && source.characterId == context.characterId &&
                    source.personaKey == context.personaKey && source.kind == "MESSAGE" && source.status == "AVAILABLE" &&
                    runCatching {
                        AppJson.gson.fromJson(
                            source.visibleCharacterIdsJson,
                            Array<String>::class.java
                        )
                            ?.contains(context.characterId) == true
                    }.getOrDefault(false)
        }.map { it.messageId to it.messageRevision }.toSet()
        return store.listMessages(binding.conversationId).filter { message ->
            message.companionSourceEligibility() == CompanionSourceEligibility.TEXT_ALLOWED &&
                    (message.id to message.memoryRevision) in sources
        }
    }

    private fun MemoryConversationBindingEntity.domain() = CompanionHistoryBinding(
        conversationId, scenarioId, runId, personaKey, MemoryPurpose.fromStorageValue(purpose),
    )
}
