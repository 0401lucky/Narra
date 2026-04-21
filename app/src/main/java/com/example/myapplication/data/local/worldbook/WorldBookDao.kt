package com.example.myapplication.data.local.worldbook

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 默认列表排序（observeEntries / listEntries 共用）：
 *   先按"是否归属到某本书"分组（无书的条目排在本书之后）；
 *   再按书名（不区分大小写）升序；
 *   同一本书内按 insertionOrder ASC、createdAt ASC；
 *   最后保留 updatedAt DESC 作为稳定性辅助。
 */
@Dao
interface WorldBookDao {
    @Query(DEFAULT_LIST_QUERY)
    fun observeEntries(): Flow<List<WorldBookEntryEntity>>

    @Query(DEFAULT_LIST_QUERY)
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

    @Query(
        """
        UPDATE worldbook_entries
        SET sourceBookName = :newName, updatedAt = :updatedAt
        WHERE bookId = :bookId
        """,
    )
    suspend fun updateBookName(bookId: String, newName: String, updatedAt: Long): Int

    @Query("DELETE FROM worldbook_entries WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String): Int
}

/**
 * Room 的 @Query 只接字符串字面量，无法在 interface 内部直接引用 companion 常量；
 * 把排序语句提到顶层 `const val`，KSP 在编译期会把它内联回注解里，
 * 从而让 observeEntries / listEntries 共用同一份 SQL，避免将来漏改其一。
 */
private const val DEFAULT_LIST_QUERY = """
    SELECT * FROM worldbook_entries
    ORDER BY
        CASE WHEN sourceBookName = '' THEN 1 ELSE 0 END,
        sourceBookName COLLATE NOCASE ASC,
        insertionOrder ASC,
        createdAt ASC,
        updatedAt DESC
"""

