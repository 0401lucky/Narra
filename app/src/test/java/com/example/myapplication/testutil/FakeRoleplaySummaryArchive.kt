package com.example.myapplication.testutil

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MEMORY_ENGINE_COMPATIBILITY
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySemanticText
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceKind
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter

/** 摘要成功路径专用：来源必须来自登记消息及当前持久值，其他档案 API 仍立即失败。 */
class FakeRoleplaySummaryArchive(
    private val store: FakeConversationStore,
    val memoryContext: MemoryRuntimeContext,
    sourceMessages: List<ChatMessage>,
    private val newMessageRoles: Map<String, MessageRole> = emptyMap(),
    private val onMessagesRead: (List<ChatMessage>) -> Unit = {},
) : MemoryArchiveRepository by fakeSummaryRuntimeArchive({ listOf(memoryContext) }) {
    private data class FrozenSource(
        val message: ChatMessage,
        val source: MemorySourceEvidence,
        val segments: List<MemorySemanticText>,
    )

    private val frozen = linkedMapOf<String, FrozenSource>()

    init {
        require(ConversationSummaryCoverageSupport.isValidMemoryContext(memoryContext))
        require(sourceMessages.map { it.id }.distinct().size == sourceMessages.size)
        require(sourceMessages.none { it.id in newMessageRoles })
        sourceMessages.forEach { message ->
            require(message.conversationId == memoryContext.conversationId)
            require(message.status == MessageStatus.COMPLETED && !message.isRecalled && message.memoryRevision > 0)
            freeze(message)
        }
    }

    override suspend fun getEngineVersion(): Int = MEMORY_ENGINE_COMPATIBILITY

    override suspend fun getRuntimeContext(conversationId: String, characterId: String): MemoryRuntimeContext? {
        if (conversationId != memoryContext.conversationId || characterId != memoryContext.characterId) return null
        val conversation = store.getConversation(conversationId) ?: return null
        if (conversation.assistantId != characterId) return null
        onMessagesRead(store.listMessages(conversationId))
        return memoryContext
    }

    override suspend fun getSource(context: MemoryRuntimeContext, sourceId: String): MemorySourceEvidence? {
        if (context != memoryContext || getRuntimeContext(context.conversationId, context.characterId) != context) return null
        val current = store.listMessages(context.conversationId).firstOrNull {
            MemoryArchiveIdentity.id("source", context.spaceId, it.id, it.memoryRevision.toString()) == sourceId
        } ?: return null
        if (current.status != MessageStatus.COMPLETED || current.isRecalled || current.memoryRevision <= 0) return null
        val saved = frozen[sourceId] ?: run {
            // 仅测试显式登记的下一轮消息可在实际写入后获得新来源，未知 ID 不自动可见。
            if (newMessageRoles[current.id] != current.role) return null
            if (current.role == MessageRole.ASSISTANT && current.speakerId.isNotBlank() && current.speakerId != context.characterId) return null
            freeze(current)
        }
        return saved.source.takeIf { saved.message == current }
    }

    override suspend fun getSourceSegments(context: MemoryRuntimeContext, sourceId: String): List<MemorySemanticText> {
        val source = getSource(context, sourceId) ?: return emptyList()
        val segments = frozen.getValue(sourceId).segments
        check(segments.joinToString("\n\n") { it.text } == source.content)
        return segments
    }

    private fun freeze(message: ChatMessage): FrozenSource {
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(message)
        val content = segments.joinToString("\n\n") { it.text }
        val id = MemoryArchiveIdentity.id("source", memoryContext.spaceId, message.id, message.memoryRevision.toString())
        val snapshot = FrozenSource(
            message = message,
            source = MemorySourceEvidence(
                id = id,
                spaceId = memoryContext.spaceId,
                characterId = memoryContext.characterId,
                kind = MemorySourceKind.MESSAGE,
                conversationId = memoryContext.conversationId,
                messageId = message.id,
                messageRevision = message.memoryRevision,
                speakerId = message.speakerId.ifBlank { if (message.role == MessageRole.USER) "user" else memoryContext.characterId },
                speakerRole = message.role.name,
                personaKey = memoryContext.personaKey,
                visibleCharacterIds = listOf(memoryContext.characterId),
                content = content,
                contentLength = content.length,
                status = if (content.isBlank()) MemorySourceStatus.UNKNOWN else MemorySourceStatus.AVAILABLE,
                occurredAt = message.createdAt,
                ingestedAt = message.createdAt,
                contentFingerprint = MemoryArchiveIdentity.fingerprint(content),
                contentFormatVersion = 2,
            ),
            segments = segments,
        )
        frozen[id] = snapshot
        return snapshot
    }
}
