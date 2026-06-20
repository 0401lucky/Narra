package com.example.myapplication.data.local.moments

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "moment_posts",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["authorType", "authorId"]),
    ],
)
data class MomentPostEntity(
    @PrimaryKey val id: String,
    val authorType: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUri: String,
    val authorLabel: String,
    val content: String,
    val location: String,
    val likedByNamesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "moment_comments",
    foreignKeys = [
        ForeignKey(
            entity = MomentPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["postId"]),
        Index(value = ["createdAt"]),
        Index(value = ["authorType", "authorId"]),
    ],
)
data class MomentCommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorType: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUri: String,
    val text: String,
    val createdAt: Long,
)

@Entity(
    tableName = "moment_media",
    foreignKeys = [
        ForeignKey(
            entity = MomentPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["postId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class MomentMediaEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val prompt: String,
    val imageUri: String,
    val mimeType: String,
    val fileName: String,
    val status: String,
    val errorMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MomentPostWithRelations(
    @Embedded val post: MomentPostEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "postId",
    )
    val comments: List<MomentCommentEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "postId",
    )
    val media: List<MomentMediaEntity> = emptyList(),
)
