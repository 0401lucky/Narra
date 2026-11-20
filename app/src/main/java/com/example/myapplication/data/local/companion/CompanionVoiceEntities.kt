package com.example.myapplication.data.local.companion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "companion_voice_resources",
    indices = [
        Index(value = ["cacheKey"], unique = true),
        Index(value = ["state", "lastUsedAt", "id"]),
    ],
)
data class CompanionVoiceResourceEntity(
    @PrimaryKey val id: String,
    val requestKey: String,
    val cacheKey: String?,
    val state: String,
    val claimToken: String?,
    val leaseUntil: Long?,
    val path: String,
    val mimeType: String,
    val sha256: String,
    val sizeBytes: Long,
    val clipJson: String,
    val createdAt: Long,
    val lastUsedAt: Long,
)

/** 收藏只引用音频；删除消息、回合或场景不能级联删除用户的独立快照。 */
@Entity(
    tableName = "companion_voice_favorites",
    foreignKeys = [
        ForeignKey(
            entity = CompanionVoiceResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["audioId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["audioId"]),
        Index(value = ["characterId", "createdAt", "id"]),
        Index(value = ["sourceMessageId", "sourceRevision"]),
    ],
)
data class CompanionVoiceFavoriteEntity(
    @PrimaryKey val id: String,
    val audioId: String,
    val characterId: String,
    val sourceMessageId: String,
    val sourceRevision: Long,
    val clipJson: String,
    val createdAt: Long,
)
