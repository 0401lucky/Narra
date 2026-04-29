package com.example.myapplication.data.repository.mailbox

import com.example.myapplication.data.local.mailbox.MailboxDao
import com.example.myapplication.data.local.mailbox.MailboxLetterEntity
import com.example.myapplication.data.local.mailbox.MailboxSettingsEntity
import com.example.myapplication.model.MailboxBox
import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MailboxProactiveFrequency
import com.example.myapplication.model.MailboxSenderType
import com.example.myapplication.model.MailboxSettings
import com.example.myapplication.model.MailboxSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

interface MailboxRepository {
    fun observeLetters(
        scenarioId: String,
        box: MailboxBox,
    ): Flow<List<MailboxLetter>>

    fun observeLetter(letterId: String): Flow<MailboxLetter?>

    fun observeUnreadCount(scenarioId: String): Flow<Int>

    suspend fun getLetter(letterId: String): MailboxLetter?

    suspend fun saveDraft(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        replyToLetterId: String = "",
        allowMemory: Boolean = true,
        existingLetterId: String = "",
    ): MailboxLetter

    suspend fun sendLetter(letter: MailboxLetter): MailboxLetter

    suspend fun insertIncomingLetter(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        tags: List<String>,
        mood: String,
        replyToLetterId: String = "",
        allowMemory: Boolean = true,
        memoryCandidate: String = "",
        source: MailboxSource = MailboxSource.AI_REPLY,
    ): MailboxLetter

    suspend fun markRead(letterId: String)

    suspend fun archive(letterId: String)

    suspend fun moveToTrash(letterId: String)

    suspend fun linkMemory(
        letterId: String,
        memoryId: String,
    )

    suspend fun delete(letterId: String)

    suspend fun deleteLettersForConversation(conversationId: String)

    fun observeSettings(scenarioId: String): Flow<MailboxSettings>

    suspend fun getSettings(scenarioId: String): MailboxSettings

    suspend fun updateSettings(settings: MailboxSettings): MailboxSettings

    suspend fun markProactiveLetterCreated(scenarioId: String): MailboxSettings
}

class RoomMailboxRepository(
    private val mailboxDao: MailboxDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : MailboxRepository {
    override fun observeLetters(
        scenarioId: String,
        box: MailboxBox,
    ): Flow<List<MailboxLetter>> {
        if (scenarioId.isBlank()) {
            return flowOf(emptyList())
        }
        return mailboxDao.observeLetters(
            scenarioId = scenarioId.trim(),
            box = box.storageValue,
        ).map { letters -> letters.map(::toDomain) }
    }

    override fun observeLetter(letterId: String): Flow<MailboxLetter?> {
        if (letterId.isBlank()) {
            return flowOf(null)
        }
        return mailboxDao.observeLetter(letterId.trim()).map { it?.let(::toDomain) }
    }

    override fun observeUnreadCount(scenarioId: String): Flow<Int> {
        if (scenarioId.isBlank()) {
            return flowOf(0)
        }
        return mailboxDao.observeUnreadCount(scenarioId.trim())
    }

    override suspend fun getLetter(letterId: String): MailboxLetter? {
        if (letterId.isBlank()) {
            return null
        }
        return mailboxDao.getLetter(letterId.trim())?.let(::toDomain)
    }

    override suspend fun saveDraft(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        replyToLetterId: String,
        allowMemory: Boolean,
        existingLetterId: String,
    ): MailboxLetter {
        require(scenarioId.isNotBlank()) { "当前聊天不存在，无法保存草稿" }
        require(conversationId.isNotBlank()) { "当前会话不存在，无法保存草稿" }
        val timestamp = nowProvider()
        val existing = existingLetterId.takeIf { it.isNotBlank() }?.let { mailboxDao.getLetter(it) }
        val draft = MailboxLetter(
            id = existing?.id ?: UUID.randomUUID().toString(),
            scenarioId = scenarioId.trim(),
            conversationId = conversationId.trim(),
            assistantId = assistantId.trim(),
            senderType = MailboxSenderType.USER,
            box = MailboxBox.DRAFT,
            subject = subject.trim(),
            content = content.trim(),
            excerpt = buildExcerpt(content),
            tags = emptyList(),
            mood = "",
            replyToLetterId = replyToLetterId.trim(),
            isRead = true,
            isStarred = false,
            allowMemory = allowMemory,
            memoryCandidate = "",
            linkedMemoryId = existing?.linkedMemoryId.orEmpty(),
            source = MailboxSource.MANUAL,
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
            sentAt = 0L,
            readAt = existing?.readAt ?: 0L,
        )
        mailboxDao.upsertLetter(draft.toEntity())
        return draft
    }

    override suspend fun sendLetter(letter: MailboxLetter): MailboxLetter {
        require(letter.scenarioId.isNotBlank()) { "当前聊天不存在，无法寄信" }
        require(letter.conversationId.isNotBlank()) { "当前会话不存在，无法寄信" }
        require(letter.content.isNotBlank()) { "正文还没有内容" }
        val timestamp = nowProvider()
        val sent = letter.copy(
            id = letter.id.ifBlank { UUID.randomUUID().toString() },
            senderType = MailboxSenderType.USER,
            box = MailboxBox.SENT,
            subject = letter.subject.trim().ifBlank { "未命名的信" },
            content = letter.content.trim(),
            excerpt = buildExcerpt(letter.content),
            isRead = true,
            source = MailboxSource.MANUAL,
            createdAt = letter.createdAt.takeIf { it > 0 } ?: timestamp,
            updatedAt = timestamp,
            sentAt = timestamp,
            readAt = letter.readAt,
        )
        mailboxDao.upsertLetter(sent.toEntity())
        return sent
    }

    override suspend fun insertIncomingLetter(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        tags: List<String>,
        mood: String,
        replyToLetterId: String,
        allowMemory: Boolean,
        memoryCandidate: String,
        source: MailboxSource,
    ): MailboxLetter {
        require(scenarioId.isNotBlank()) { "当前聊天不存在，无法保存来信" }
        require(conversationId.isNotBlank()) { "当前会话不存在，无法保存来信" }
        require(content.isNotBlank()) { "回信正文为空" }
        val timestamp = nowProvider()
        val letter = MailboxLetter(
            id = UUID.randomUUID().toString(),
            scenarioId = scenarioId.trim(),
            conversationId = conversationId.trim(),
            assistantId = assistantId.trim(),
            senderType = MailboxSenderType.CHARACTER,
            box = MailboxBox.INBOX,
            subject = subject.trim().ifBlank { "关于你那封信" },
            content = content.trim(),
            excerpt = buildExcerpt(content),
            tags = normalizeTags(tags),
            mood = mood.trim(),
            replyToLetterId = replyToLetterId.trim(),
            isRead = false,
            isStarred = false,
            allowMemory = allowMemory,
            memoryCandidate = memoryCandidate.trim(),
            linkedMemoryId = "",
            source = source,
            createdAt = timestamp,
            updatedAt = timestamp,
            sentAt = timestamp,
            readAt = 0L,
        )
        mailboxDao.upsertLetter(letter.toEntity())
        return letter
    }

    override suspend fun markRead(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        mailboxDao.markRead(letterId.trim(), nowProvider())
    }

    override suspend fun archive(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        mailboxDao.moveToBox(letterId.trim(), MailboxBox.ARCHIVE.storageValue, nowProvider())
    }

    override suspend fun moveToTrash(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        mailboxDao.moveToBox(letterId.trim(), MailboxBox.TRASH.storageValue, nowProvider())
    }

    override suspend fun linkMemory(
        letterId: String,
        memoryId: String,
    ) {
        if (letterId.isBlank() || memoryId.isBlank()) {
            return
        }
        mailboxDao.linkMemory(letterId.trim(), memoryId.trim(), nowProvider())
    }

    override suspend fun delete(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        mailboxDao.deleteLetter(letterId.trim())
    }

    override suspend fun deleteLettersForConversation(conversationId: String) {
        if (conversationId.isBlank()) {
            return
        }
        mailboxDao.deleteLettersForConversation(conversationId.trim())
    }

    override fun observeSettings(scenarioId: String): Flow<MailboxSettings> {
        val normalizedScenarioId = scenarioId.trim()
        if (normalizedScenarioId.isBlank()) {
            return flowOf(MailboxSettings())
        }
        return mailboxDao.observeSettings(normalizedScenarioId)
            .map { entity -> entity?.toDomain() ?: MailboxSettings(scenarioId = normalizedScenarioId) }
    }

    override suspend fun getSettings(scenarioId: String): MailboxSettings {
        val normalizedScenarioId = scenarioId.trim()
        if (normalizedScenarioId.isBlank()) {
            return MailboxSettings()
        }
        return mailboxDao.getSettings(normalizedScenarioId)?.toDomain()
            ?: MailboxSettings(scenarioId = normalizedScenarioId)
    }

    override suspend fun updateSettings(settings: MailboxSettings): MailboxSettings {
        require(settings.scenarioId.isNotBlank()) { "当前聊天不存在，无法保存信箱设置" }
        val updated = settings.copy(
            scenarioId = settings.scenarioId.trim(),
            updatedAt = nowProvider(),
        )
        mailboxDao.upsertSettings(updated.toEntity())
        return updated
    }

    override suspend fun markProactiveLetterCreated(scenarioId: String): MailboxSettings {
        val normalizedScenarioId = scenarioId.trim()
        require(normalizedScenarioId.isNotBlank()) { "当前聊天不存在，无法更新主动来信时间" }
        val timestamp = nowProvider()
        val updated = getSettings(normalizedScenarioId).copy(
            lastProactiveLetterAt = timestamp,
            updatedAt = timestamp,
        )
        mailboxDao.upsertSettings(updated.toEntity())
        return updated
    }
}

internal fun MailboxLetter.toEntity(): MailboxLetterEntity {
    return MailboxLetterEntity(
        id = id,
        scenarioId = scenarioId,
        conversationId = conversationId,
        assistantId = assistantId,
        senderType = senderType.storageValue,
        box = box.storageValue,
        subject = subject,
        content = content,
        excerpt = excerpt.ifBlank { buildExcerpt(content) },
        tagsCsv = normalizeTags(tags).joinToString(","),
        mood = mood,
        replyToLetterId = replyToLetterId,
        isRead = isRead,
        isStarred = isStarred,
        allowMemory = allowMemory,
        memoryCandidate = memoryCandidate,
        linkedMemoryId = linkedMemoryId,
        source = source.storageValue,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sentAt = sentAt,
        readAt = readAt,
    )
}

internal fun toDomain(entity: MailboxLetterEntity): MailboxLetter {
    return MailboxLetter(
        id = entity.id,
        scenarioId = entity.scenarioId,
        conversationId = entity.conversationId,
        assistantId = entity.assistantId,
        senderType = MailboxSenderType.fromStorageValue(entity.senderType),
        box = MailboxBox.fromStorageValue(entity.box),
        subject = entity.subject,
        content = entity.content,
        excerpt = entity.excerpt.ifBlank { buildExcerpt(entity.content) },
        tags = entity.tagsCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct(),
        mood = entity.mood,
        replyToLetterId = entity.replyToLetterId,
        isRead = entity.isRead,
        isStarred = entity.isStarred,
        allowMemory = entity.allowMemory,
        memoryCandidate = entity.memoryCandidate,
        linkedMemoryId = entity.linkedMemoryId,
        source = MailboxSource.fromStorageValue(entity.source),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        sentAt = entity.sentAt,
        readAt = entity.readAt,
    )
}

internal fun MailboxSettings.toEntity(): MailboxSettingsEntity {
    return MailboxSettingsEntity(
        scenarioId = scenarioId,
        autoReplyToUserLetters = autoReplyToUserLetters,
        includeRecentChatByDefault = includeRecentChatByDefault,
        includePhoneCluesByDefault = includePhoneCluesByDefault,
        allowMemoryByDefault = allowMemoryByDefault,
        proactiveFrequency = proactiveFrequency.storageValue,
        lastProactiveLetterAt = lastProactiveLetterAt,
        updatedAt = updatedAt,
    )
}

internal fun MailboxSettingsEntity.toDomain(): MailboxSettings {
    return MailboxSettings(
        scenarioId = scenarioId,
        autoReplyToUserLetters = autoReplyToUserLetters,
        includeRecentChatByDefault = includeRecentChatByDefault,
        includePhoneCluesByDefault = includePhoneCluesByDefault,
        allowMemoryByDefault = allowMemoryByDefault,
        proactiveFrequency = MailboxProactiveFrequency.fromStorageValue(proactiveFrequency),
        lastProactiveLetterAt = lastProactiveLetterAt,
        updatedAt = updatedAt,
    )
}

internal fun buildExcerpt(content: String): String {
    return content
        .replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { normalized ->
            if (normalized.length <= 72) normalized else normalized.take(72).trimEnd() + "..."
        }
}

internal fun normalizeTags(tags: List<String>): List<String> {
    return tags
        .map { it.trim().trim(',') }
        .filter { it.isNotBlank() }
        .map { if (it.length <= 8) it else it.take(8) }
        .distinct()
        .take(4)
}
