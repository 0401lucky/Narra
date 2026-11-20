package com.example.myapplication.data.local.companion

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionVoiceDao {
    @Query("SELECT * FROM companion_voice_resources WHERE cacheKey = :key LIMIT 1")
    suspend fun resourceForKey(key: String): CompanionVoiceResourceEntity?

    @Query("SELECT * FROM companion_voice_resources WHERE id = :id LIMIT 1")
    suspend fun resource(id: String): CompanionVoiceResourceEntity?

    @Query("SELECT * FROM companion_voice_resources ORDER BY lastUsedAt ASC, id ASC")
    suspend fun resources(): List<CompanionVoiceResourceEntity>

    @Upsert
    suspend fun upsertResource(resource: CompanionVoiceResourceEntity)

    @Query("DELETE FROM companion_voice_resources WHERE id = :id")
    suspend fun deleteResource(id: String)

    @Query("SELECT * FROM companion_voice_favorites ORDER BY createdAt DESC, id DESC")
    suspend fun favorites(): List<CompanionVoiceFavoriteEntity>

    @Query("SELECT * FROM companion_voice_favorites WHERE id = :id LIMIT 1")
    suspend fun favorite(id: String): CompanionVoiceFavoriteEntity?

    @Query("SELECT COUNT(*) FROM companion_voice_favorites WHERE audioId = :audioId")
    suspend fun favoriteReferences(audioId: String): Int

    @Upsert
    suspend fun upsertFavorite(favorite: CompanionVoiceFavoriteEntity)

    @Query("DELETE FROM companion_voice_favorites WHERE id = :id")
    suspend fun deleteFavorite(id: String)

    // 不 distinctUntilChanged：结果数不变的正文/版本/关联更新也必须通知声音 owner。
    @Query(
        """
        SELECT COUNT(*) FROM messages m
        LEFT JOIN companion_turns t ON t.id = m.companionTurnId
        LEFT JOIN companion_encounters e ON e.id = t.encounterId
        LEFT JOIN companion_attempts a ON a.id = t.selectedAttemptId
    """
    )
    fun observeSourceChanges(): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) FROM companion_voice_resources r
        LEFT JOIN companion_voice_favorites f ON f.audioId = r.id
    """
    )
    fun observeResourceChanges(): Flow<Long>
}
