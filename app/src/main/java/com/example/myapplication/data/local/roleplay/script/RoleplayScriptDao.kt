package com.example.myapplication.data.local.roleplay.script

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleplayScriptDao {
    @Query("SELECT * FROM roleplay_scripts ORDER BY scope ASC, ownerId ASC, updatedAt DESC, name COLLATE NOCASE ASC")
    fun observeScripts(): Flow<List<RoleplayScriptEntity>>

    @Query("SELECT * FROM roleplay_scripts ORDER BY scope ASC, ownerId ASC, updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun listScripts(): List<RoleplayScriptEntity>

    @Query(
        """
        SELECT * FROM roleplay_scripts
        WHERE scope = :scope AND ownerId = :ownerId
        ORDER BY updatedAt DESC, name COLLATE NOCASE ASC
        """,
    )
    suspend fun listScriptsByOwner(scope: String, ownerId: String): List<RoleplayScriptEntity>

    @Query("SELECT * FROM roleplay_scripts WHERE id = :scriptId LIMIT 1")
    suspend fun getScript(scriptId: String): RoleplayScriptEntity?

    @Upsert
    suspend fun upsertScript(script: RoleplayScriptEntity)

    @Query("DELETE FROM roleplay_scripts WHERE id = :scriptId")
    suspend fun deleteScript(scriptId: String)

    @Query("SELECT * FROM roleplay_script_state WHERE scriptId = :scriptId ORDER BY stateKey ASC")
    suspend fun listState(scriptId: String): List<RoleplayScriptStateEntity>

    @Upsert
    suspend fun upsertStateValues(values: List<RoleplayScriptStateEntity>)

    @Query("DELETE FROM roleplay_script_state WHERE scriptId = :scriptId AND stateKey = :stateKey")
    suspend fun deleteStateValue(scriptId: String, stateKey: String)

    @Query("DELETE FROM roleplay_script_state WHERE scriptId = :scriptId")
    suspend fun deleteState(scriptId: String)
}
