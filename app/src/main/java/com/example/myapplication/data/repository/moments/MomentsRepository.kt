package com.example.myapplication.data.repository.moments

import com.example.myapplication.data.local.moments.MomentCommentEntity
import com.example.myapplication.data.local.moments.MomentDao
import com.example.myapplication.data.local.moments.MomentMediaEntity
import com.example.myapplication.data.local.moments.MomentPostEntity
import com.example.myapplication.data.local.moments.MomentPostWithRelations
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentMedia
import com.example.myapplication.model.MomentMediaStatus
import com.example.myapplication.model.MomentPost
import com.example.myapplication.system.json.AppJson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MomentsRepository {
    fun observeTimeline(): Flow<List<MomentPost>>
    suspend fun listTimeline(limit: Int = 30): List<MomentPost>
    suspend fun getPost(postId: String): MomentPost?
    suspend fun upsertPost(post: MomentPost)
    suspend fun deletePost(postId: String)
    suspend fun updatePostLikes(postId: String, likedByNames: List<String>, updatedAt: Long)
    suspend fun addComment(comment: MomentComment)
    suspend fun addComments(comments: List<MomentComment>)
    suspend fun upsertMedia(media: MomentMedia)
    suspend fun getMediaByPostId(postId: String): MomentMedia?
    suspend fun latestAssistantPostCreatedAt(assistantId: String): Long?
}

class RoomMomentsRepository(
    private val dao: MomentDao,
) : MomentsRepository {
    private val gson = AppJson.gson
    private val stringListType = object : TypeToken<List<String>>() {}.type

    override fun observeTimeline(): Flow<List<MomentPost>> {
        return dao.observeTimeline().map { rows -> rows.map(::toDomain) }
    }

    override suspend fun listTimeline(limit: Int): List<MomentPost> {
        return dao.listTimeline(limit.coerceAtLeast(1)).map(::toDomain)
    }

    override suspend fun getPost(postId: String): MomentPost? {
        return dao.getPost(postId)?.let(::toDomain)
    }

    override suspend fun upsertPost(post: MomentPost) {
        dao.upsertPost(post.toEntity())
        post.media?.let { dao.upsertMedia(it.toEntity()) }
    }

    override suspend fun deletePost(postId: String) {
        dao.deletePost(postId)
    }

    override suspend fun updatePostLikes(postId: String, likedByNames: List<String>, updatedAt: Long) {
        dao.updatePostLikes(
            postId = postId,
            likedByNamesJson = encodeStringList(likedByNames),
            updatedAt = updatedAt,
        )
    }

    override suspend fun addComment(comment: MomentComment) {
        dao.upsertComment(comment.toEntity())
    }

    override suspend fun addComments(comments: List<MomentComment>) {
        if (comments.isEmpty()) return
        dao.upsertComments(comments.map { it.toEntity() })
    }

    override suspend fun upsertMedia(media: MomentMedia) {
        dao.upsertMedia(media.toEntity())
    }

    override suspend fun getMediaByPostId(postId: String): MomentMedia? {
        return dao.getMediaByPostId(postId)?.toDomain()
    }

    override suspend fun latestAssistantPostCreatedAt(assistantId: String): Long? {
        return dao.latestAssistantPostCreatedAt(assistantId)
    }

    private fun toDomain(row: MomentPostWithRelations): MomentPost {
        val post = row.post
        return MomentPost(
            id = post.id,
            authorType = MomentAuthorType.fromStorageValue(post.authorType),
            authorId = post.authorId,
            authorName = post.authorName,
            authorAvatarUri = post.authorAvatarUri,
            authorLabel = post.authorLabel,
            content = post.content,
            likedByNames = decodeStringList(post.likedByNamesJson),
            comments = row.comments
                .map { it.toDomain() }
                .sortedBy(MomentComment::createdAt),
            media = row.media
                .map { it.toDomain() }
                .maxByOrNull(MomentMedia::updatedAt),
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
        )
    }

    private fun MomentPost.toEntity(): MomentPostEntity {
        return MomentPostEntity(
            id = id,
            authorType = authorType.storageValue,
            authorId = authorId,
            authorName = authorName,
            authorAvatarUri = authorAvatarUri,
            authorLabel = authorLabel,
            content = content,
            likedByNamesJson = encodeStringList(likedByNames),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun MomentComment.toEntity(): MomentCommentEntity {
        return MomentCommentEntity(
            id = id,
            postId = postId,
            authorType = authorType.storageValue,
            authorId = authorId,
            authorName = authorName,
            authorAvatarUri = authorAvatarUri,
            text = text,
            createdAt = createdAt,
        )
    }

    private fun MomentCommentEntity.toDomain(): MomentComment {
        return MomentComment(
            id = id,
            postId = postId,
            authorType = MomentAuthorType.fromStorageValue(authorType),
            authorId = authorId,
            authorName = authorName,
            authorAvatarUri = authorAvatarUri,
            text = text,
            createdAt = createdAt,
        )
    }

    private fun MomentMedia.toEntity(): MomentMediaEntity {
        return MomentMediaEntity(
            id = id,
            postId = postId,
            prompt = prompt,
            imageUri = imageUri,
            mimeType = mimeType,
            fileName = fileName,
            status = status.storageValue,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun MomentMediaEntity.toDomain(): MomentMedia {
        return MomentMedia(
            id = id,
            postId = postId,
            prompt = prompt,
            imageUri = imageUri,
            mimeType = mimeType,
            fileName = fileName,
            status = MomentMediaStatus.fromStorageValue(status),
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun encodeStringList(values: List<String>): String {
        return gson.toJson(values.map(String::trim).filter(String::isNotBlank).distinct())
    }

    private fun decodeStringList(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(rawJson, stringListType)
                .orEmpty()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        }.getOrDefault(emptyList())
    }
}
