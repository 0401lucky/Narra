package com.example.myapplication.data.local.moments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    @Transaction
    @Query("SELECT * FROM moment_posts ORDER BY createdAt DESC, id DESC")
    fun observeTimeline(): Flow<List<MomentPostWithRelations>>

    @Transaction
    @Query("SELECT * FROM moment_posts ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun listTimeline(limit: Int): List<MomentPostWithRelations>

    @Transaction
    @Query("SELECT * FROM moment_posts WHERE id = :postId")
    suspend fun getPost(postId: String): MomentPostWithRelations?

    @Query(
        """
        SELECT createdAt FROM moment_posts
        WHERE authorType = 'assistant' AND authorId = :assistantId
        ORDER BY createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun latestAssistantPostCreatedAt(assistantId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: MomentPostEntity)

    @Query("UPDATE moment_posts SET likedByNamesJson = :likedByNamesJson, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePostLikes(postId: String, likedByNamesJson: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComment(comment: MomentCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(comments: List<MomentCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: MomentMediaEntity)

    @Query("SELECT * FROM moment_media WHERE postId = :postId LIMIT 1")
    suspend fun getMediaByPostId(postId: String): MomentMediaEntity?
}
