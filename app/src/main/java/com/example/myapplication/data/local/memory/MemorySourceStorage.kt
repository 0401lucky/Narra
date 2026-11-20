package com.example.myapplication.data.local.memory

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.withCompanionSourceProof
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.system.json.AppJson

/** 消息存储事务内的来源钩子；不启动提取，不将旧历史自动入队。 */
internal class MemorySourceStorage(
    private val database: ChatDatabase,
    private val nowProvider: () -> Long,
) {
    private val messages = database.conversationDao()
    private val dao = database.memoryArchiveDao()
    private val companionSources =
        com.example.myapplication.data.local.companion.CompanionSourceStorage(database)
    private val invalidation =
        MemoryArchiveInvalidation(dao, database.memoryDao(), database.memoryKnowledgeDao())

    suspend fun writeMessages(
        values: List<MessageEntity>,
        companionWrite: Boolean = false
    ): List<MessageEntity> {
        val saved = values.map { incoming ->
            val old = messages.getMessage(incoming.id)
            if (!companionWrite) companionSources.verifyExternalWrite(old, incoming)
            require(old == null || old.conversationId == incoming.conversationId) { "消息不能移入另一个对话" }
            val changed = old != null && !sameSource(old, incoming)
            val previousSources = if (changed) {
                dao.listMessageSources(incoming.conversationId, incoming.id)
                    .filter { it.kind == "MESSAGE" }
            } else emptyList()
            val revision = when {
                old != null && !changed -> old.memoryRevision
                else -> maxOf(old?.memoryRevision ?: 0L, dao.maxSourceRevision(incoming.id)) + 1L
            }
            val next = incoming.copy(memoryRevision = revision)
            if (changed) invalidateMessage(
                incoming.conversationId,
                incoming.id,
                deleted = incoming.isRecalled
            )
            messages.insertMessages(listOf(next))
            if (changed && !companionWrite) companionSources.onExternalChange(old!!, next)
            // 新消息先冻结身份；没有首次来源凭据的旧历史不因编辑而自动回填。
            if (!companionWrite && (old == null || changed && previousSources.isNotEmpty() && next.status == "COMPLETED" && !next.isRecalled)) capture(
                next,
                previousSources,
            )
            next
        }
        return saved
    }

    suspend fun invalidateMessage(conversationId: String, messageId: String, deleted: Boolean) {
        if (deleted) companionSources.onDelete(messageId)
        val now = nowProvider()
        (dao.listMessageSources(conversationId, messageId) + dao.listLegacyMessageSources(
            conversationId,
            messageId
        ))
            .filter { if (deleted) it.status != "DELETED" else it.status == "AVAILABLE" || it.status == "UNKNOWN" }
            .distinctBy { it.id }
            .forEach {
                invalidation.source(it, deleted, now)
            }
    }

    suspend fun clearConversation(conversationId: String, deletingConversation: Boolean) {
        val now = nowProvider()
        companionSources.onClear(conversationId, now)
        dao.listConversationSources(conversationId)
            .filter { it.status != "DELETED" && it.kind in setOf("MESSAGE", "LEGACY_HINT") }
            .forEach { invalidation.source(it, deleted = true, now) }
        dao.listBindings(conversationId).map { it.spaceId }.distinct().forEach { spaceId ->
            dao.advanceGeneration(spaceId, now)
            dao.invalidateSpaceJobs(spaceId, "SOURCE_DELETED", now)
            dao.invalidateSpaceProposals(spaceId, now)
        }
        if (deletingConversation) dao.archiveBindings(conversationId)
    }

    private suspend fun capture(
        message: MessageEntity,
        previousSources: List<MemorySourceEvidenceEntity>,
        forceEnqueue: Boolean = false,
    ) {
        val eligibility = companionSources.eligibility(message)
        val content =
            if (eligibility == com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED) {
                contentForSource(message, eligibility = eligibility)
            } else ""
        val available = content.isNotBlank()
        if (previousSources.isNotEmpty() && !available) return
        val now = nowProvider()
        if (previousSources.isNotEmpty()) {
            // 编辑原文不能把历史身份改成当前身份，也不能向后来加入的群成员补发来源。
            previousSources.groupBy { it.spaceId }.values.forEach { revisions ->
                val previous = revisions.maxBy { it.messageRevision }
                val space = dao.getSpace(previous.spaceId) ?: return@forEach
                if (space.status == "DELETED") return@forEach
                val visible = AppJson.gson.fromJson(
                    previous.visibleCharacterIdsJson,
                    Array<String>::class.java,
                )
                saveSource(
                    previous.copy(
                        id = sourceId(message, space.id),
                        messageRevision = message.memoryRevision,
                        speakerId = speakerId(message, previous.characterId, visible.size),
                        speakerRole = message.role,
                        content = content,
                        contentLength = content.length,
                        status = "AVAILABLE",
                        occurredAt = message.createdAt,
                        ingestedAt = now,
                        contentFingerprint = MemoryArchiveIdentity.fingerprint(content),
                        contentFormatVersion = 2,
                    ),
                    enqueue = space.status == "ACTIVE" && (forceEnqueue || previous.status == "UNKNOWN" || dao.countSourceIngress(
                        previous.id
                    ) > 0),
                )
            }
            return
        }
        val bindings = dao.listBindings(message.conversationId).filterNot { it.archived }
        val visible = bindings.map { it.characterId }.sorted()
        bindings.forEach { binding ->
            val space = dao.getSpace(binding.spaceId) ?: return@forEach
            if (space.status != "ACTIVE") return@forEach
            val source = MemorySourceEvidenceEntity(
                id = sourceId(message, space.id),
                spaceId = space.id,
                characterId = binding.characterId,
                kind = "MESSAGE",
                conversationId = message.conversationId,
                messageId = message.id,
                messageRevision = message.memoryRevision,
                speakerId = speakerId(message, binding.characterId, bindings.size),
                speakerRole = message.role,
                personaKey = binding.personaKey,
                visibleCharacterIdsJson = AppJson.gson.toJson(visible),
                content = content,
                contentLength = content.length,
                status = if (message.isRecalled) "DELETED" else if (available) "AVAILABLE" else "UNKNOWN",
                occurredAt = message.createdAt,
                ingestedAt = now,
                contentFingerprint = MemoryArchiveIdentity.fingerprint(content),
                contentFormatVersion = 2,
            )
            saveSource(source, enqueue = available)
        }
    }

    suspend fun captureCompanionMessages(values: List<MessageEntity>) {
        values.forEach { message ->
            val previous = dao.listMessageSources(message.conversationId, message.id)
                .filter { it.kind == "MESSAGE" && it.messageRevision != message.memoryRevision }
            capture(message, previous, forceEnqueue = true)
        }
    }

    private suspend fun saveSource(source: MemorySourceEvidenceEntity, enqueue: Boolean) {
        if (dao.insertSource(source) != -1L && enqueue) {
            dao.insertIngress(
                MemoryIngressEventEntity(
                    spaceId = source.spaceId,
                    sourceId = source.id,
                    status = "PENDING",
                    ingestedAt = source.ingestedAt,
                )
            )
        }
    }

    private fun sourceId(message: MessageEntity, spaceId: String): String =
        MemoryArchiveIdentity.id("source", spaceId, message.id, message.memoryRevision.toString())

    private fun speakerId(message: MessageEntity, characterId: String, visibleCount: Int): String =
        message.speakerId.ifBlank {
            if (message.role == "USER") "user" else if (visibleCount == 1) characterId else ""
        }

    private fun sameSource(old: MessageEntity, next: MessageEntity): Boolean =
        old.content == next.content && old.partsJson == next.partsJson && old.attachmentsJson == next.attachmentsJson && old.status == next.status &&
                old.role == next.role && old.speakerId == next.speakerId && old.isRecalled == next.isRecalled &&
                old.systemEventKind == next.systemEventKind && old.roleplayOutputFormat == next.roleplayOutputFormat &&
                old.roleplayInteractionMode == next.roleplayInteractionMode && old.createdAt == next.createdAt &&
                old.companionTurnId == next.companionTurnId && old.companionMemoryState == next.companionMemoryState

    companion object {
        fun contentForSource(
            message: MessageEntity,
            formatVersion: Int = 2,
            eligibility: com.example.myapplication.model.CompanionSourceEligibility = com.example.myapplication.model.CompanionSourceEligibility.UNAVAILABLE,
        ): String {
            if (message.companionTurnId.isNotBlank() && eligibility != com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED) return ""
            if (formatVersion == 1) return message.content.ifBlank {
                AppJson.gson.fromJson(message.partsJson, Array<ChatMessagePart>::class.java)
                    .toList().toPlainText()
            }
            return RoleplayTranscriptFormatter.memorySemanticSegments(
                semanticMessage(
                    message,
                    eligibility
                )
            )
                .joinToString("\n\n") { it.text }
        }

        fun semanticMessage(
            message: MessageEntity,
            eligibility: com.example.myapplication.model.CompanionSourceEligibility = com.example.myapplication.model.CompanionSourceEligibility.UNAVAILABLE,
        ): ChatMessage = ChatMessage(
            id = message.id,
            conversationId = message.conversationId,
            role = MessageRole.valueOf(message.role),
            content = message.content,
            status = MessageStatus.valueOf(message.status),
            createdAt = message.createdAt,
            parts = AppJson.gson.fromJson(message.partsJson, Array<ChatMessagePart>::class.java)
                .toList(),
            attachments = AppJson.gson.fromJson(
                message.attachmentsJson,
                Array<MessageAttachment>::class.java
            ).toList(),
            speakerId = message.speakerId,
            speakerName = message.speakerName,
            isRecalled = message.isRecalled,
            systemEventKind = RoleplayOnlineEventKind.fromStorageValue(message.systemEventKind),
            roleplayOutputFormat = RoleplayOutputFormat.entries.firstOrNull { it.name == message.roleplayOutputFormat }
                ?: RoleplayOutputFormat.UNSPECIFIED,
            roleplayInteractionMode = RoleplayInteractionMode.entries.firstOrNull { it.name == message.roleplayInteractionMode || it.storageValue == message.roleplayInteractionMode },
            memoryRevision = message.memoryRevision,
            companionTurnId = message.companionTurnId,
            companionMemoryState = com.example.myapplication.model.CompanionMemoryState.fromStorageValue(
                message.companionMemoryState
            ),
        ).withCompanionSourceProof(eligibility)
    }
}
