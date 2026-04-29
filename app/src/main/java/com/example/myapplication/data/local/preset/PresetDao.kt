package com.example.myapplication.data.local.preset

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY builtIn DESC, name COLLATE NOCASE ASC, updatedAt DESC")
    fun observePresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY builtIn DESC, name COLLATE NOCASE ASC, updatedAt DESC")
    suspend fun listPresets(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :presetId LIMIT 1")
    suspend fun getPreset(presetId: String): PresetEntity?

    @Upsert
    suspend fun upsertPreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :presetId AND builtIn = 0")
    suspend fun deleteCustomPreset(presetId: String)
}
