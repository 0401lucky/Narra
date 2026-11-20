package com.example.myapplication.data.local.companion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionDao {
    @Insert
    suspend fun insertEncounter(value: CompanionEncounterEntity)
    @Upsert
    suspend fun upsertEncounter(value: CompanionEncounterEntity)

    @Query("SELECT * FROM companion_encounters WHERE id = :id")
    suspend fun getEncounter(id: String): CompanionEncounterEntity?

    @Query("SELECT * FROM companion_encounters WHERE activeKey = :key")
    suspend fun getActiveEncounter(key: String): CompanionEncounterEntity?

    @Query("SELECT * FROM companion_encounters WHERE conversationId = :conversationId ORDER BY createdAt, id")
    suspend fun listEncounters(conversationId: String): List<CompanionEncounterEntity>

    @Query("SELECT * FROM companion_encounters WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeEncounters(conversationId: String): Flow<List<CompanionEncounterEntity>>

    @Insert
    suspend fun insertTurn(value: CompanionTurnEntity)
    @Upsert
    suspend fun upsertTurn(value: CompanionTurnEntity)

    @Query("SELECT * FROM companion_turns WHERE id = :id")
    suspend fun getTurn(id: String): CompanionTurnEntity?

    @Query("SELECT * FROM companion_turns WHERE encounterId = :encounterId ORDER BY ordinal")
    suspend fun listTurns(encounterId: String): List<CompanionTurnEntity>

    @Query("SELECT * FROM companion_turns WHERE inputMessageId = :messageId OR replyMessageId = :messageId")
    suspend fun getMessageTurn(messageId: String): CompanionTurnEntity?

    @Insert
    suspend fun insertAttempt(value: CompanionAttemptEntity)
    @Upsert
    suspend fun upsertAttempt(value: CompanionAttemptEntity)

    @Query("SELECT * FROM companion_attempts WHERE id = :id")
    suspend fun getAttempt(id: String): CompanionAttemptEntity?

    @Query("SELECT * FROM companion_attempts WHERE turnId = :turnId ORDER BY id")
    suspend fun listAttempts(turnId: String): List<CompanionAttemptEntity>

    @Query("SELECT a.* FROM companion_attempts a INNER JOIN companion_turns t ON t.id = a.turnId WHERE t.encounterId = :encounterId ORDER BY t.ordinal, a.id")
    suspend fun listEncounterAttempts(encounterId: String): List<CompanionAttemptEntity>

    @Query("UPDATE companion_attempts SET artifactJson = NULL, artifactHash = NULL, state = CASE WHEN state = 'COMMITTED' THEN 'COMMITTED' WHEN requestedAt IS NULL THEN 'CANCELLED' ELSE 'UNCERTAIN' END, claimToken = NULL, leaseUntil = NULL WHERE turnId = :turnId")
    suspend fun eraseTurnArtifacts(turnId: String)
}
