package com.example.myapplication.system.performance

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.example.myapplication.compliance.CompliancePolicy
import com.example.myapplication.data.local.ComplianceConsentStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.ConversationEntity
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.roleplay.RoleplayGroupParticipantEntity
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import java.io.File

internal class BaselineFixturePreparer(
    private val context: Context,
) {
    suspend fun prepare(fixtureId: BenchmarkFixtureId): FixtureSafeSummary {
        val expectedDataset = DeterministicFixtureFactory.create(fixtureId)
        return when (fixtureId) {
            BenchmarkFixtureId.S0_FIRST_RUN -> {
                clearCurrentState()
                expectedDataset.safeSummary()
            }

            BenchmarkFixtureId.S1_RETURNING_CORE,
            BenchmarkFixtureId.S2_POPULATED,
            -> prepareCurrentDatabase(expectedDataset)

            BenchmarkFixtureId.S3_SUPPORTED_UPGRADE -> {
                acceptCurrentCompliance()
                LegacyUpgradeFixtureBuilder(context).create(expectedDataset)
            }
        }
    }

    private fun clearCurrentState() {
        context.deleteDatabase(CHAT_DATABASE_NAME)
        listOf(
            File(context.filesDir, "datastore/compliance_consent.preferences_pb"),
            File(context.filesDir, "datastore/compliance_consent.preferences_pb.tmp"),
        ).forEach { file ->
            if (file.exists() && !file.delete()) {
                error("首次启动 fixture 无法清理合规状态")
            }
        }
    }

    private suspend fun prepareCurrentDatabase(dataset: FixtureDataset): FixtureSafeSummary {
        acceptCurrentCompliance()
        val database = Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            CHAT_DATABASE_NAME,
        ).addMigrations(*ChatDatabase.ALL_MIGRATIONS)
            .build()
        return try {
            database.withTransaction {
                val conversationDao = database.conversationDao()
                val roleplayDao = database.roleplayDao()

                roleplayDao.deleteScenario(BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID)
                conversationDao.deleteConversation(BenchmarkFixtureIds.CHAT_CONVERSATION_ID)
                conversationDao.deleteConversation(BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID)

                conversationDao.replaceConversationSnapshot(
                    conversation = conversationEntity(
                        id = BenchmarkFixtureIds.CHAT_CONVERSATION_ID,
                        title = "普通聊天基准",
                        updatedAt = dataset.chatMessages.lastOrNull()?.createdAtEpochMillis
                            ?.plus(CHAT_CONVERSATION_RECENCY_OFFSET_MILLIS),
                    ),
                    conversationId = BenchmarkFixtureIds.CHAT_CONVERSATION_ID,
                    messages = dataset.chatMessages.map { message -> message.toEntity() },
                )
                conversationDao.replaceConversationSnapshot(
                    conversation = conversationEntity(
                        id = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                        title = "角色扮演基准",
                        updatedAt = dataset.roleplayMessages.lastOrNull()?.createdAtEpochMillis,
                    ),
                    conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                    messages = dataset.roleplayMessages.map { message -> message.toEntity() },
                )
                roleplayDao.upsertScenario(roleplayScenarioEntity())
                roleplayDao.upsertGroupParticipants(listOf(roleplayParticipantEntity()))
                roleplayDao.upsertSession(roleplaySessionEntity())

                val actualDataset = dataset.copy(
                    chatMessages = conversationDao.listMessages(BenchmarkFixtureIds.CHAT_CONVERSATION_ID)
                        .map { message -> message.toFixtureRecord() },
                    roleplayMessages = conversationDao.listMessages(BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID)
                        .map { message -> message.toFixtureRecord() },
                )
                val expectedSummary = dataset.safeSummary()
                val actualSummary = actualDataset.safeSummary()
                check(actualSummary == expectedSummary) { "benchmark fixture 写入后校验不一致" }
                actualSummary
            }
        } finally {
            database.close()
        }
    }

    private suspend fun acceptCurrentCompliance() {
        ComplianceConsentStore(context).accept(
            policyVersion = CompliancePolicy.CURRENT_VERSION,
            acceptedAtEpochMillis = BenchmarkFixtureIds.FIXED_CONSENT_EPOCH_MILLIS,
        )
    }

    private fun conversationEntity(
        id: String,
        title: String,
        updatedAt: Long?,
    ): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            model = "",
            createdAt = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS,
            updatedAt = updatedAt ?: BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS,
            assistantId = BenchmarkFixtureIds.ASSISTANT_ID,
            searchEnabled = false,
        )
    }

    private fun FixtureMessageRecord.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            status = status,
            createdAt = createdAtEpochMillis,
            attachmentsJson = attachmentsJson,
            partsJson = partsJson,
            speakerId = speakerId,
            speakerName = speakerName,
            speakerAvatarUri = speakerAvatarUri,
            roleplayInteractionMode = roleplayInteractionMode,
        )
    }

    private fun MessageEntity.toFixtureRecord(): FixtureMessageRecord {
        return FixtureMessageRecord(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            status = status,
            createdAtEpochMillis = createdAt,
            attachmentsJson = attachmentsJson,
            partsJson = partsJson,
            speakerId = speakerId,
            speakerName = speakerName,
            speakerAvatarUri = speakerAvatarUri,
            roleplayInteractionMode = roleplayInteractionMode,
        )
    }

    private fun roleplayScenarioEntity(): RoleplayScenarioEntity {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return RoleplayScenarioEntity(
            id = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
            title = "角色扮演基准场景",
            description = "固定离线性能验证场景",
            assistantId = BenchmarkFixtureIds.ASSISTANT_ID,
            backgroundUri = "",
            userDisplayNameOverride = "基准用户",
            userPersonaOverride = "",
            userPortraitUri = "",
            userPortraitUrl = "",
            characterDisplayNameOverride = "基准角色",
            characterPortraitUri = "",
            characterPortraitUrl = "",
            openingNarration = "",
            enableNarration = true,
            enableRoleplayProtocol = true,
            autoHighlightSpeaker = true,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun roleplayParticipantEntity(): RoleplayGroupParticipantEntity {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return RoleplayGroupParticipantEntity(
            id = "benchmark-roleplay-participant-v1",
            scenarioId = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
            assistantId = BenchmarkFixtureIds.ASSISTANT_ID,
            displayNameOverride = "基准角色",
            sortOrder = 0,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun roleplaySessionEntity(): RoleplaySessionEntity {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return RoleplaySessionEntity(
            id = BenchmarkFixtureIds.ROLEPLAY_SESSION_ID,
            scenarioId = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
            conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    companion object {
        const val CHAT_DATABASE_NAME = "chat.db"
        private const val CHAT_CONVERSATION_RECENCY_OFFSET_MILLIS = 1_000L
    }
}
