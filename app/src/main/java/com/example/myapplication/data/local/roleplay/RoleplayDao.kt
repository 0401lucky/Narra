package com.example.myapplication.data.local.roleplay

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleplayDao {
    @Query("SELECT * FROM roleplay_scenarios ORDER BY updatedAt DESC, createdAt DESC")
    fun observeScenarios(): Flow<List<RoleplayScenarioEntity>>

    @Query("SELECT * FROM roleplay_scenarios WHERE id = :scenarioId LIMIT 1")
    fun observeScenario(scenarioId: String): Flow<RoleplayScenarioEntity?>

    @Query("SELECT * FROM roleplay_sessions WHERE scenarioId = :scenarioId LIMIT 1")
    fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySessionEntity?>

    @Query("SELECT * FROM roleplay_sessions ORDER BY updatedAt DESC, createdAt DESC")
    fun observeSessions(): Flow<List<RoleplaySessionEntity>>

    @Query("SELECT * FROM roleplay_diary_entries WHERE conversationId = :conversationId ORDER BY sortOrder ASC, createdAt DESC")
    fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntryEntity>>

    @Query("SELECT * FROM roleplay_scenarios ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listScenarios(): List<RoleplayScenarioEntity>

    @Query("SELECT * FROM roleplay_sessions ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listSessions(): List<RoleplaySessionEntity>

    @Query("SELECT * FROM roleplay_diary_entries WHERE conversationId = :conversationId ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntryEntity>

    @Query("SELECT * FROM roleplay_scenarios WHERE id = :scenarioId LIMIT 1")
    suspend fun getScenario(scenarioId: String): RoleplayScenarioEntity?

    @Query("SELECT * FROM roleplay_sessions WHERE scenarioId = :scenarioId LIMIT 1")
    suspend fun getSessionByScenario(scenarioId: String): RoleplaySessionEntity?

    @Query("SELECT * FROM roleplay_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): RoleplaySessionEntity?

    @Query("SELECT * FROM roleplay_online_meta WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMetaEntity?

    @Upsert
    suspend fun upsertOnlineMeta(meta: RoleplayOnlineMetaEntity)

    @Query("DELETE FROM roleplay_online_meta WHERE conversationId = :conversationId")
    suspend fun deleteOnlineMeta(conversationId: String)

    @Upsert
    suspend fun upsertScenario(scenario: RoleplayScenarioEntity)

    @Upsert
    suspend fun upsertSession(session: RoleplaySessionEntity)

    @Upsert
    suspend fun upsertDiaryEntries(entries: List<RoleplayDiaryEntryEntity>)

    @Query("DELETE FROM roleplay_scenarios WHERE id = :scenarioId")
    suspend fun deleteScenario(scenarioId: String)

    @Query("DELETE FROM roleplay_diary_entries WHERE conversationId = :conversationId")
    suspend fun deleteDiaryEntriesForConversation(conversationId: String)

    @Transaction
    suspend fun replaceDiaryEntriesForConversation(
        conversationId: String,
        entries: List<RoleplayDiaryEntryEntity>,
    ) {
        deleteDiaryEntriesForConversation(conversationId)
        if (entries.isNotEmpty()) {
            upsertDiaryEntries(entries)
        }
    }
}
