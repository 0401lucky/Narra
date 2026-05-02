package com.example.myapplication.data.local.mailbox

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MailboxDao {
    @Query(
        """
        SELECT * FROM mailbox_letters
        WHERE scenarioId = :scenarioId AND box = :box
        ORDER BY isRead ASC, updatedAt DESC
        """,
    )
    fun observeLetters(
        scenarioId: String,
        box: String,
    ): Flow<List<MailboxLetterEntity>>

    @Query("SELECT * FROM mailbox_letters WHERE id = :letterId LIMIT 1")
    fun observeLetter(letterId: String): Flow<MailboxLetterEntity?>

    @Query("SELECT * FROM mailbox_letters WHERE id = :letterId LIMIT 1")
    suspend fun getLetter(letterId: String): MailboxLetterEntity?

    @Query(
        """
        SELECT COUNT(*) FROM mailbox_letters
        WHERE scenarioId = :scenarioId AND box = 'inbox' AND isRead = 0
        """,
    )
    fun observeUnreadCount(scenarioId: String): Flow<Int>

    @Upsert
    suspend fun upsertLetter(letter: MailboxLetterEntity)

    @Query("UPDATE mailbox_letters SET isRead = 1, readAt = :readAt, updatedAt = :readAt WHERE id = :letterId")
    suspend fun markRead(
        letterId: String,
        readAt: Long,
    )

    @Query("UPDATE mailbox_letters SET box = :box, updatedAt = :updatedAt WHERE id = :letterId")
    suspend fun moveToBox(
        letterId: String,
        box: String,
        updatedAt: Long,
    )

    @Query("UPDATE mailbox_letters SET linkedMemoryId = :memoryId, updatedAt = :updatedAt WHERE id = :letterId")
    suspend fun linkMemory(
        letterId: String,
        memoryId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM mailbox_letters WHERE id = :letterId")
    suspend fun deleteLetter(letterId: String)

    @Query("DELETE FROM mailbox_letters WHERE conversationId = :conversationId")
    suspend fun deleteLettersForConversation(conversationId: String)

    @Query("DELETE FROM mailbox_letters WHERE scenarioId = :scenarioId")
    suspend fun deleteLettersForScenario(scenarioId: String)

    @Query("SELECT * FROM mailbox_settings WHERE scenarioId = :scenarioId LIMIT 1")
    fun observeSettings(scenarioId: String): Flow<MailboxSettingsEntity?>

    @Query("SELECT * FROM mailbox_settings WHERE scenarioId = :scenarioId LIMIT 1")
    suspend fun getSettings(scenarioId: String): MailboxSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: MailboxSettingsEntity)

    @Query("DELETE FROM mailbox_settings WHERE scenarioId = :scenarioId")
    suspend fun deleteSettingsForScenario(scenarioId: String)
}
