package com.example.myapplication.data.local.companion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.local.chat.ConversationEntity

@Entity(
    tableName = "companion_encounters",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class, parentColumns = ["id"],
        childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["conversationId", "createdAt", "id"]),
        Index(value = ["activeKey"], unique = true),
        Index(value = ["characterId", "personaKey", "createdAt", "id"]),
    ],
)
data class CompanionEncounterEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val scenarioId: String,
    val runId: String,
    val spaceId: String,
    val characterId: String,
    val personaKey: String,
    val generation: Long,
    val openingMode: String,
    val state: String,
    val revision: Long,
    val activeKey: String?,
    val createdAt: Long,
    val endedAt: Long?,
)

@Entity(
    tableName = "companion_turns",
    foreignKeys = [ForeignKey(
        entity = CompanionEncounterEntity::class, parentColumns = ["id"],
        childColumns = ["encounterId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["encounterId", "ordinal"], unique = true),
        Index(value = ["inputMessageId"], unique = true),
        Index(value = ["replyMessageId"], unique = true),
    ],
)
data class CompanionTurnEntity(
    @PrimaryKey val id: String,
    val encounterId: String,
    val ordinal: Long,
    val kind: String,
    val inputMessageId: String?,
    val replyMessageId: String?,
    val inputRevision: Long?,
    val replyRevision: Long?,
    val mutationRevision: Long,
    val committedRevision: Long,
    val selectedAttemptId: String?,
    val activeAttemptId: String?,
    val createdAt: Long,
    val editedAt: Long?,
)

@Entity(
    tableName = "companion_attempts",
    foreignKeys = [ForeignKey(
        entity = CompanionTurnEntity::class, parentColumns = ["id"],
        childColumns = ["turnId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["turnId"]), Index(value = ["state", "leaseUntil"])],
)
data class CompanionAttemptEntity(
    @PrimaryKey val id: String,
    val turnId: String,
    val expectedTurnRevision: Long,
    val expectedInputRevision: Long?,
    val expectedReplyRevision: Long?,
    val frozenRuntimeJson: String,
    val requestDescriptorJson: String,
    val requestFingerprint: String,
    val state: String,
    val claimToken: String?,
    val leaseUntil: Long?,
    val requestedAt: Long?,
    val receivedAt: Long?,
    val artifactJson: String?,
    val artifactHash: String?,
)
