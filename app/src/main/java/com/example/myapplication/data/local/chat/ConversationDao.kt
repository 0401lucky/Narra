package com.example.myapplication.data.local.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    fun observeConversationsByAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun listConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun listMessages(conversationId: String): List<MessageEntity>

    @Query("""
        SELECT m.* FROM messages m
        JOIN memory_conversation_bindings b ON b.conversationId = m.conversationId AND b.characterId = :characterId
        JOIN roleplay_scenarios scene ON scene.id = b.scenarioId AND scene.chatType = 'single' AND scene.assistantId = :characterId
        WHERE m.conversationId IN (:conversationIds) AND b.runId IN (:runIds)
        AND b.spaceId = :spaceId AND b.personaKey = :personaKey AND b.purpose = :purpose
        AND m.status = 'COMPLETED' AND m.isRecalled = 0 AND m.role IN ('USER', 'ASSISTANT')
        AND (EXISTS (SELECT 1 FROM memory_source_evidence s
            WHERE s.conversationId = m.conversationId AND s.messageId = m.id AND s.messageRevision = m.memoryRevision
            AND s.spaceId = :spaceId AND s.characterId = :characterId AND s.personaKey = :personaKey AND s.kind = 'MESSAGE'
            AND (s.status = 'AVAILABLE' OR (s.status = 'UNKNOWN' AND m.companionMemoryState = 'control'))
            AND s.visibleCharacterIdsJson LIKE :visibleToken ESCAPE '\')
            OR (:includePendingInputs = 1 AND m.role = 'USER' AND m.companionTurnId != ''
                AND m.companionMemoryState IN ('waiting_turn', 'control')
                AND EXISTS (SELECT 1 FROM memory_source_evidence s
                    WHERE s.conversationId = m.conversationId AND s.messageId = m.id
                    AND s.messageRevision > 0 AND s.messageRevision <= m.memoryRevision
                    AND s.spaceId = :spaceId AND s.characterId = :characterId AND s.personaKey = :personaKey
                    AND s.kind = 'MESSAGE' AND s.speakerRole = 'USER'
                    AND s.status IN ('UNKNOWN', 'CHANGED', 'AVAILABLE')
                    AND s.visibleCharacterIdsJson LIKE :visibleToken ESCAPE '\')))
        AND ((m.companionTurnId = '' AND m.companionMemoryState = 'legacy'
            AND NOT EXISTS (SELECT 1 FROM companion_turns linked
                WHERE linked.inputMessageId = m.id OR linked.replyMessageId = m.id)) OR EXISTS (
            SELECT 1 FROM companion_turns t JOIN companion_encounters e ON e.id = t.encounterId
            JOIN companion_attempts a ON a.id = t.selectedAttemptId AND a.turnId = t.id AND a.state = 'COMMITTED'
            WHERE t.id = m.companionTurnId AND t.committedRevision > 0 AND e.id IN (:encounterIds)
            AND e.conversationId = b.conversationId AND e.scenarioId = b.scenarioId AND e.runId = b.runId
            AND e.spaceId = :spaceId AND e.characterId = :characterId AND e.personaKey = :personaKey
            AND ((m.role = 'USER' AND t.inputMessageId = m.id AND t.inputRevision = m.memoryRevision
                    AND ((t.kind = 'INPUT' AND m.companionMemoryState = 'ready') OR (t.kind = 'CONTINUE' AND m.companionMemoryState = 'control')))
                OR (m.role = 'ASSISTANT' AND t.replyMessageId = m.id AND t.replyRevision = m.memoryRevision
                    AND m.speakerId = :characterId AND m.companionMemoryState = 'ready')))
            OR (:includePendingInputs = 1 AND EXISTS (
                SELECT 1 FROM companion_turns t JOIN companion_encounters e ON e.id = t.encounterId
                WHERE t.id = m.companionTurnId AND t.committedRevision = 0 AND t.selectedAttemptId IS NULL
                AND t.replyMessageId IS NULL AND t.replyRevision IS NULL
                AND m.role = 'USER' AND t.inputMessageId = m.id AND t.inputRevision = m.memoryRevision
                AND e.id IN (:encounterIds) AND e.state IN ('ACTIVE', 'ENDED')
                AND e.conversationId = b.conversationId AND e.scenarioId = b.scenarioId AND e.runId = b.runId
                AND e.spaceId = :spaceId AND e.characterId = :characterId AND e.personaKey = :personaKey
                AND ((t.kind = 'INPUT' AND m.companionMemoryState = 'waiting_turn')
                    OR (t.kind = 'CONTINUE' AND m.companionMemoryState = 'control')))))
        AND (:beforeAt IS NULL OR m.createdAt < :beforeAt OR (m.createdAt = :beforeAt AND m.id < :beforeId))
        AND (:fromAt IS NULL OR m.createdAt > :fromAt OR (m.createdAt = :fromAt AND m.id >= :fromId))
        ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit
    """)
    suspend fun listCompanionReadingPage(
        conversationIds: List<String>, runIds: List<String>, encounterIds: List<String>,
        spaceId: String, characterId: String, personaKey: String, purpose: String, visibleToken: String,
        beforeAt: Long?, beforeId: String?, fromAt: Long?, fromId: String?, limit: Int,
        includePendingInputs: Boolean = false,
    ): List<MessageEntity>

    // 观察关联表失效以及正文变化；数值仅用于触发刷新，不充当原文/版本凭证。
    @Query("""
        SELECT COUNT(*) FROM messages m
        LEFT JOIN memory_source_evidence s ON s.messageId = m.id
        LEFT JOIN memory_conversation_bindings b ON b.conversationId = m.conversationId
        LEFT JOIN companion_turns t ON t.id = m.companionTurnId
        LEFT JOIN companion_encounters e ON e.id = t.encounterId
        LEFT JOIN companion_attempts a ON a.id = t.selectedAttemptId
        LEFT JOIN memory_spaces space ON space.id = b.spaceId
        WHERE m.conversationId IN (:conversationIds)
    """)
    fun observeCompanionReadingChanges(conversationIds: List<String>): Flow<Long>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'ASSISTANT' ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatestAssistantMessage(conversationId: String): MessageEntity?

    @Upsert
    suspend fun upsertConversationEntity(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessages(conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id = :messageId")
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
    ): Int

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Transaction
    suspend fun replaceConversationSnapshot(
        conversation: ConversationEntity,
        conversationId: String,
        messages: List<MessageEntity>,
    ) {
        upsertConversationEntity(conversation)
        clearMessages(conversationId)
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }

    @Transaction
    suspend fun upsertConversationWithMessages(
        conversation: ConversationEntity,
        messages: List<MessageEntity>,
    ) {
        upsertConversationEntity(conversation)
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }

    @Transaction
    suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: ConversationEntity,
    ) {
        clearMessages(conversationId)
        upsertConversationEntity(conversation)
    }
}
