package com.example.myapplication.data.local.phone

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneSnapshotDao {
    @Query(
        """
        SELECT * FROM phone_snapshots
        WHERE conversationId = :conversationId AND ownerType = :ownerType
        LIMIT 1
        """,
    )
    fun observeSnapshot(
        conversationId: String,
        ownerType: String,
    ): Flow<PhoneSnapshotEntity?>

    @Query(
        """
        SELECT * FROM phone_snapshots
        WHERE conversationId = :conversationId AND ownerType = :ownerType
        LIMIT 1
        """,
    )
    suspend fun getSnapshot(
        conversationId: String,
        ownerType: String,
    ): PhoneSnapshotEntity?

    @Upsert
    suspend fun upsertSnapshot(snapshot: PhoneSnapshotEntity)

    @Query("DELETE FROM phone_snapshots WHERE conversationId = :conversationId")
    suspend fun deleteSnapshot(conversationId: String)

    @Query(
        """
        DELETE FROM phone_snapshots
        WHERE conversationId = :conversationId AND ownerType = :ownerType
        """,
    )
    suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: String,
    )

    @Query("SELECT * FROM phone_observations WHERE conversationId = :conversationId LIMIT 1")
    fun observeObservation(conversationId: String): Flow<PhoneObservationEntity?>

    @Query("SELECT * FROM phone_observations WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getObservation(conversationId: String): PhoneObservationEntity?

    @Upsert
    suspend fun upsertObservation(observation: PhoneObservationEntity)

    @Query("DELETE FROM phone_observations WHERE conversationId = :conversationId")
    suspend fun deleteObservation(conversationId: String)
}
