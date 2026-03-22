package com.example.myapplication.data.local.worldbook

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {
    @Query(
        """
        SELECT * FROM worldbook_entries
        ORDER BY
            CASE WHEN sourceBookName = '' THEN 1 ELSE 0 END,
            sourceBookName COLLATE NOCASE ASC,
            insertionOrder ASC,
            createdAt ASC,
            updatedAt DESC
        """,
    )
    fun observeEntries(): Flow<List<WorldBookEntryEntity>>

    @Query(
        """
        SELECT * FROM worldbook_entries
        ORDER BY
            CASE WHEN sourceBookName = '' THEN 1 ELSE 0 END,
            sourceBookName COLLATE NOCASE ASC,
            insertionOrder ASC,
            createdAt ASC,
            updatedAt DESC
        """,
    )
    suspend fun listEntries(): List<WorldBookEntryEntity>

    @Query(
        """
        SELECT * FROM worldbook_entries
        WHERE enabled = 1
        ORDER BY alwaysActive DESC, priority DESC, insertionOrder ASC, createdAt ASC, updatedAt DESC
        """,
    )
    suspend fun listEnabledEntries(): List<WorldBookEntryEntity>

    @Query("SELECT * FROM worldbook_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntry(entryId: String): WorldBookEntryEntity?

    @Upsert
    suspend fun upsertEntry(entry: WorldBookEntryEntity)

    @Query("DELETE FROM worldbook_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)
}
