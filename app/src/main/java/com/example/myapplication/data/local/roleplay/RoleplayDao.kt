package com.example.myapplication.data.local.roleplay

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleplayDao {
    @Query("SELECT * FROM roleplay_scenarios ORDER BY updatedAt DESC, createdAt DESC")
    fun observeScenarios(): Flow<List<RoleplayScenarioEntity>>

    @Query(
        """
        SELECT
            s.id AS id,
            s.title AS title,
            s.description AS description,
            s.descriptionPromptEnabled AS descriptionPromptEnabled,
            s.assistantId AS assistantId,
            s.backgroundUri AS backgroundUri,
            s.userDisplayNameOverride AS userDisplayNameOverride,
            s.userPersonaMaskId AS userPersonaMaskId,
            s.userPersonaOverride AS userPersonaOverride,
            s.userPortraitUri AS userPortraitUri,
            s.userPortraitUrl AS userPortraitUrl,
            s.characterDisplayNameOverride AS characterDisplayNameOverride,
            s.characterPortraitUri AS characterPortraitUri,
            s.characterPortraitUrl AS characterPortraitUrl,
            s.openingNarration AS openingNarration,
            s.interactionMode AS interactionMode,
            s.enableNarration AS enableNarration,
            s.enableRoleplayProtocol AS enableRoleplayProtocol,
            s.longformModeEnabled AS longformModeEnabled,
            s.autoHighlightSpeaker AS autoHighlightSpeaker,
            s.enableDeepImmersion AS enableDeepImmersion,
            s.enableTimeAwareness AS enableTimeAwareness,
            s.enableNetMeme AS enableNetMeme,
            s.chatType AS chatType,
            s.groupReplyMode AS groupReplyMode,
            s.enableGroupMentionAutoReply AS enableGroupMentionAutoReply,
            s.maxGroupAutoReplies AS maxGroupAutoReplies,
            s.isPinned AS isPinned,
            s.isMuted AS isMuted,
            s.createdAt AS createdAt,
            s.updatedAt AS updatedAt,
            rs.id AS sessionId,
            rs.conversationId AS sessionConversationId,
            rs.createdAt AS sessionCreatedAt,
            rs.updatedAt AS sessionUpdatedAt,
            lm.content AS lastMessageContent,
            lm.createdAt AS lastMessageCreatedAt,
            lm.role AS lastMessageRole
        FROM roleplay_scenarios s
        LEFT JOIN roleplay_sessions rs ON rs.scenarioId = s.id
        LEFT JOIN messages lm ON lm.id = (
            SELECT m.id
            FROM messages m
            WHERE m.conversationId = rs.conversationId
                AND m.status = 'COMPLETED'
                AND m.systemEventKind = 'none'
                AND m.id NOT LIKE 'rp-opening-' || s.id || '-%'
                AND (
                    TRIM(m.content) != ''
                    OR m.partsJson != '[]'
                    OR m.attachmentsJson != '[]'
                )
            ORDER BY m.createdAt DESC
            LIMIT 1
        )
        ORDER BY
            s.isPinned DESC,
            COALESCE(lm.createdAt, rs.updatedAt, s.updatedAt, s.createdAt) DESC,
            s.createdAt DESC
        """,
    )
    fun observeChatSummaryRows(): Flow<List<RoleplayChatSummaryRow>>

    @Query("SELECT * FROM roleplay_scenarios WHERE id = :scenarioId LIMIT 1")
    fun observeScenario(scenarioId: String): Flow<RoleplayScenarioEntity?>

    @Query("SELECT * FROM roleplay_sessions WHERE scenarioId = :scenarioId LIMIT 1")
    fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySessionEntity?>

    @Query("SELECT * FROM roleplay_sessions ORDER BY updatedAt DESC, createdAt DESC")
    fun observeSessions(): Flow<List<RoleplaySessionEntity>>

    @Query("SELECT * FROM roleplay_group_participants WHERE scenarioId = :scenarioId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeGroupParticipants(scenarioId: String): Flow<List<RoleplayGroupParticipantEntity>>

    @Query("SELECT * FROM roleplay_diary_entries WHERE conversationId = :conversationId ORDER BY sortOrder ASC, createdAt DESC")
    fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntryEntity>>

    @Query("SELECT * FROM roleplay_scenarios ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listScenarios(): List<RoleplayScenarioEntity>

    @Query("SELECT * FROM roleplay_sessions ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listSessions(): List<RoleplaySessionEntity>

    @Query("SELECT * FROM roleplay_group_participants WHERE scenarioId = :scenarioId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listGroupParticipants(scenarioId: String): List<RoleplayGroupParticipantEntity>

    @Query("SELECT * FROM roleplay_diary_entries WHERE conversationId = :conversationId ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntryEntity>

    @Query("SELECT * FROM roleplay_scenarios WHERE id = :scenarioId LIMIT 1")
    suspend fun getScenario(scenarioId: String): RoleplayScenarioEntity?

    @Query("SELECT * FROM roleplay_sessions WHERE scenarioId = :scenarioId LIMIT 1")
    suspend fun getSessionByScenario(scenarioId: String): RoleplaySessionEntity?

    @Query("SELECT * FROM roleplay_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): RoleplaySessionEntity?

    @Query("SELECT * FROM roleplay_group_participants WHERE id = :participantId LIMIT 1")
    suspend fun getGroupParticipant(participantId: String): RoleplayGroupParticipantEntity?

    @Query("SELECT * FROM roleplay_online_meta WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMetaEntity?

    @Upsert
    suspend fun upsertOnlineMeta(meta: RoleplayOnlineMetaEntity)

    @Query("DELETE FROM roleplay_online_meta WHERE conversationId = :conversationId")
    suspend fun deleteOnlineMeta(conversationId: String)

    @Upsert
    suspend fun upsertScenario(scenario: RoleplayScenarioEntity)

    @Upsert
    suspend fun upsertGroupParticipants(participants: List<RoleplayGroupParticipantEntity>)

    @Query("DELETE FROM roleplay_group_participants WHERE id = :participantId")
    suspend fun deleteGroupParticipant(participantId: String)

    @Query("DELETE FROM roleplay_group_participants WHERE scenarioId = :scenarioId")
    suspend fun deleteGroupParticipantsForScenario(scenarioId: String)

    @Upsert
    suspend fun upsertSession(session: RoleplaySessionEntity)

    @Upsert
    suspend fun upsertDiaryEntries(entries: List<RoleplayDiaryEntryEntity>)

    @Query("DELETE FROM roleplay_scenarios WHERE id = :scenarioId")
    suspend fun deleteScenario(scenarioId: String)

    @Query("DELETE FROM roleplay_diary_entries WHERE conversationId = :conversationId")
    suspend fun deleteDiaryEntriesForConversation(conversationId: String)

    @Transaction
    suspend fun replaceDiaryEntriesForConversation(
        conversationId: String,
        entries: List<RoleplayDiaryEntryEntity>,
    ) {
        deleteDiaryEntriesForConversation(conversationId)
        if (entries.isNotEmpty()) {
            upsertDiaryEntries(entries)
        }
    }

    @Transaction
    suspend fun replaceGroupParticipantsForScenario(
        scenarioId: String,
        participants: List<RoleplayGroupParticipantEntity>,
    ) {
        deleteGroupParticipantsForScenario(scenarioId)
        if (participants.isNotEmpty()) {
            upsertGroupParticipants(participants)
        }
    }
}
