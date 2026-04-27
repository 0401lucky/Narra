package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayDiaryEntryEntity
import com.example.myapplication.data.local.roleplay.RoleplayChatSummaryRow
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

data class RoleplaySessionStartResult(
    val session: RoleplaySession,
    val reusedExistingSession: Boolean,
    val hasHistory: Boolean,
    val assistantMismatch: Boolean,
    val conversationAssistantId: String = "",
    val conversationMessages: List<ChatMessage> = emptyList(),
)

interface RoleplayRepository {
    fun observeScenarios(): Flow<List<RoleplayScenario>>

    fun observeChatSummaries(): Flow<List<RoleplayChatSummary>>

    fun observeScenario(scenarioId: String): Flow<RoleplayScenario?>

    fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?>

    fun observeSessions(): Flow<List<RoleplaySession>>

    fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>>

    fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>>

    suspend fun listScenarios(): List<RoleplayScenario>

    suspend fun getScenario(scenarioId: String): RoleplayScenario?

    suspend fun upsertScenario(scenario: RoleplayScenario)

    suspend fun deleteScenario(scenarioId: String)

    suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun getSessionByScenario(scenarioId: String): RoleplaySession?

    suspend fun getSession(sessionId: String): RoleplaySession?

    suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry>

    suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<RoleplayDiaryDraft>,
    ): List<RoleplayDiaryEntry>

    suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta?

    suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta)

    suspend fun deleteOnlineMeta(conversationId: String)

    suspend fun deleteDiaryEntriesForConversation(conversationId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRoleplayRepository(
    private val roleplayDao: RoleplayDao,
    private val conversationRepository: ConversationRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val imageFileCleaner: suspend (String?) -> Boolean = { false },
) : RoleplayRepository {
    override fun observeScenarios(): Flow<List<RoleplayScenario>> {
        return roleplayDao.observeScenarios().map { scenarios ->
            scenarios.map(::toScenarioDomain)
        }
    }

    override fun observeChatSummaries(): Flow<List<RoleplayChatSummary>> {
        return roleplayDao.observeChatSummaryRows().map { rows ->
            rows.map(::toChatSummaryDomain)
        }
    }

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> {
        return roleplayDao.observeScenario(scenarioId).map { entity ->
            entity?.let(::toScenarioDomain)
        }
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> {
        return roleplayDao.observeSessionByScenario(scenarioId).map { entity ->
            entity?.let(::toSessionDomain)
        }
    }

    override fun observeSessions(): Flow<List<RoleplaySession>> {
        return roleplayDao.observeSessions().map { sessions ->
            sessions.map(::toSessionDomain)
        }
    }

    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> {
        return roleplayDao.observeSessionByScenario(scenarioId).flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                conversationRepository.observeMessages(session.conversationId)
            }
        }
    }

    override fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>> {
        if (conversationId.isBlank()) {
            return flowOf(emptyList())
        }
        return roleplayDao.observeDiaryEntries(conversationId).map { entries ->
            entries.map(::toDiaryEntryDomain)
        }
    }

    override suspend fun listScenarios(): List<RoleplayScenario> {
        return roleplayDao.listScenarios().map(::toScenarioDomain)
    }

    override suspend fun getScenario(scenarioId: String): RoleplayScenario? {
        return roleplayDao.getScenario(scenarioId)?.let(::toScenarioDomain)
    }

    override suspend fun upsertScenario(scenario: RoleplayScenario) {
        val timestamp = nowProvider()
        val existing = roleplayDao.getScenario(scenario.id)
        val createdAt = existing?.createdAt ?: scenario.createdAt.takeIf { it > 0 } ?: timestamp
        roleplayDao.upsertScenario(
            toScenarioEntity(
                scenario.copy(
                    createdAt = createdAt,
                    updatedAt = timestamp,
                ),
            ),
        )
    }

    override suspend fun deleteScenario(scenarioId: String) {
        val scenarioEntity = roleplayDao.getScenario(scenarioId)
        val session = roleplayDao.getSessionByScenario(scenarioId)
        if (session != null) {
            conversationRepository.deleteConversationById(session.conversationId)
            roleplayDao.deleteOnlineMeta(session.conversationId)
            roleplayDao.deleteDiaryEntriesForConversation(session.conversationId)
        }
        roleplayDao.deleteScenario(scenarioId)
        scenarioEntity?.let {
            imageFileCleaner(it.backgroundUri)
            imageFileCleaner(it.userPortraitUri)
            imageFileCleaner(it.characterPortraitUri)
        }
    }

    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult {
        val scenario = roleplayDao.getScenario(scenarioId)
            ?: error("场景不存在")
        val timestamp = nowProvider()
        val existingSession = roleplayDao.getSessionByScenario(scenarioId)
        if (existingSession != null) {
            val existingConversation = conversationRepository.getConversation(existingSession.conversationId)
            if (existingConversation != null) {
                val refreshedSession = existingSession.copy(updatedAt = timestamp)
                roleplayDao.upsertSession(refreshedSession)
                val historyMessages = cleanOrphanedLoadingMessages(
                    conversationId = existingSession.conversationId,
                    selectedModel = existingConversation.model,
                    messages = conversationRepository.listMessages(existingSession.conversationId),
                )
                return RoleplaySessionStartResult(
                    session = toSessionDomain(refreshedSession),
                    reusedExistingSession = true,
                    hasHistory = historyMessages.any {
                        !it.isOpeningNarrationMessage(scenario.id) &&
                            it.status != MessageStatus.LOADING
                    },
                    assistantMismatch = normalizeAssistantId(existingConversation.assistantId) !=
                        normalizeAssistantId(scenario.assistantId),
                    conversationAssistantId = normalizeAssistantId(existingConversation.assistantId),
                    conversationMessages = historyMessages,
                )
            }
        }

        val conversation = conversationRepository.createConversation(
            assistantId = scenario.assistantId.ifBlank { DEFAULT_ASSISTANT_ID },
        )
        seedOpeningNarrationIfNeeded(
            conversationId = conversation.id,
            scenario = toScenarioDomain(scenario),
        )
        val seededMessages = conversationRepository.listMessages(conversation.id)
        val newSession = RoleplaySessionEntity(
            id = existingSession?.id ?: java.util.UUID.randomUUID().toString(),
            scenarioId = scenarioId,
            conversationId = conversation.id,
            createdAt = existingSession?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
        roleplayDao.upsertSession(newSession)
        return RoleplaySessionStartResult(
            session = toSessionDomain(newSession),
            reusedExistingSession = false,
            hasHistory = false,
            assistantMismatch = false,
            conversationAssistantId = normalizeAssistantId(scenario.assistantId),
            conversationMessages = seededMessages,
        )
    }

    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult {
        val scenario = roleplayDao.getScenario(scenarioId)
            ?: error("场景不存在")
        val timestamp = nowProvider()
        val existingSession = roleplayDao.getSessionByScenario(scenarioId)
        existingSession?.let { session ->
            conversationRepository.deleteConversationById(session.conversationId)
            roleplayDao.deleteOnlineMeta(session.conversationId)
            roleplayDao.deleteDiaryEntriesForConversation(session.conversationId)
        }
        val conversation = conversationRepository.createConversation(
            assistantId = scenario.assistantId.ifBlank { DEFAULT_ASSISTANT_ID },
        )
        seedOpeningNarrationIfNeeded(
            conversationId = conversation.id,
            scenario = toScenarioDomain(scenario),
        )
        val seededMessages = conversationRepository.listMessages(conversation.id)
        val restartedSession = RoleplaySessionEntity(
            id = existingSession?.id ?: UUID.randomUUID().toString(),
            scenarioId = scenarioId,
            conversationId = conversation.id,
            createdAt = existingSession?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
        roleplayDao.upsertSession(restartedSession)
        return RoleplaySessionStartResult(
            session = toSessionDomain(restartedSession),
            reusedExistingSession = false,
            hasHistory = false,
            assistantMismatch = false,
            conversationAssistantId = normalizeAssistantId(scenario.assistantId),
            conversationMessages = seededMessages,
        )
    }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? {
        return roleplayDao.getSessionByScenario(scenarioId)?.let(::toSessionDomain)
    }

    override suspend fun getSession(sessionId: String): RoleplaySession? {
        return roleplayDao.getSession(sessionId)?.let(::toSessionDomain)
    }

    override suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry> {
        if (conversationId.isBlank()) {
            return emptyList()
        }
        return roleplayDao.listDiaryEntries(conversationId).map(::toDiaryEntryDomain)
    }

    override suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<RoleplayDiaryDraft>,
    ): List<RoleplayDiaryEntry> {
        if (conversationId.isBlank()) {
            return emptyList()
        }
        if (entries.isEmpty()) {
            roleplayDao.replaceDiaryEntriesForConversation(conversationId, emptyList())
            return emptyList()
        }
        val timestamp = nowProvider()
        val diaryEntities = entries.mapIndexed { index, entry ->
            RoleplayDiaryEntryEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                scenarioId = scenarioId,
                title = entry.title.trim(),
                content = entry.content.trim(),
                sortOrder = index,
                createdAt = timestamp,
                updatedAt = timestamp,
                mood = entry.mood.trim(),
                weather = entry.weather.trim(),
                tagsCsv = entry.tags.joinDiaryTags(),
                dateLabel = entry.dateLabel.trim(),
            )
        }
        roleplayDao.replaceDiaryEntriesForConversation(conversationId, diaryEntities)
        return diaryEntities.map(::toDiaryEntryDomain)
    }

    override suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta? {
        return roleplayDao.getOnlineMeta(conversationId)?.let(::toOnlineMetaDomain)
    }

    override suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta) {
        roleplayDao.upsertOnlineMeta(
            com.example.myapplication.data.local.roleplay.RoleplayOnlineMetaEntity(
                conversationId = meta.conversationId,
                lastCompensationBucket = meta.lastCompensationBucket,
                lastConsumedObservationUpdatedAt = meta.lastConsumedObservationUpdatedAt,
                lastSystemEventToken = meta.lastSystemEventToken,
                activeVideoCallSessionId = meta.activeVideoCallSessionId,
                activeVideoCallStartedAt = meta.activeVideoCallStartedAt,
                updatedAt = meta.updatedAt,
            ),
        )
    }

    override suspend fun deleteOnlineMeta(conversationId: String) {
        roleplayDao.deleteOnlineMeta(conversationId)
    }

    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) {
        if (conversationId.isBlank()) {
            return
        }
        roleplayDao.deleteDiaryEntriesForConversation(conversationId)
    }

    private suspend fun cleanOrphanedLoadingMessages(
        conversationId: String,
        selectedModel: String,
        messages: List<ChatMessage>,
    ): List<ChatMessage> {
        if (messages.none { it.status == MessageStatus.LOADING }) {
            return messages
        }
        val cleanedMessages = messages.filterNot { it.status == MessageStatus.LOADING }
        conversationRepository.replaceConversationSnapshot(
            conversationId = conversationId,
            messages = cleanedMessages,
            selectedModel = selectedModel,
        )
        return cleanedMessages
    }

    private fun toScenarioDomain(entity: RoleplayScenarioEntity): RoleplayScenario {
        val resolvedInteractionMode = if (
            entity.longformModeEnabled &&
            RoleplayInteractionMode.fromStorageValue(entity.interactionMode) == RoleplayInteractionMode.OFFLINE_DIALOGUE
        ) {
            RoleplayInteractionMode.OFFLINE_LONGFORM
        } else {
            RoleplayInteractionMode.fromStorageValue(entity.interactionMode)
        }
        return RoleplayScenario(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            descriptionPromptEnabled = entity.descriptionPromptEnabled,
            assistantId = entity.assistantId,
            backgroundUri = entity.backgroundUri,
            userDisplayNameOverride = entity.userDisplayNameOverride,
            userPersonaOverride = entity.userPersonaOverride,
            userPortraitUri = entity.userPortraitUri,
            userPortraitUrl = entity.userPortraitUrl,
            characterDisplayNameOverride = entity.characterDisplayNameOverride,
            characterPortraitUri = entity.characterPortraitUri,
            characterPortraitUrl = entity.characterPortraitUrl,
            openingNarration = entity.openingNarration,
            interactionMode = resolvedInteractionMode,
            enableNarration = entity.enableNarration,
            enableRoleplayProtocol = entity.enableRoleplayProtocol,
            longformModeEnabled = entity.longformModeEnabled,
            autoHighlightSpeaker = entity.autoHighlightSpeaker,
            enableDeepImmersion = entity.enableDeepImmersion,
            enableTimeAwareness = entity.enableTimeAwareness,
            enableNetMeme = entity.enableNetMeme,
            isPinned = entity.isPinned,
            isMuted = entity.isMuted,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toChatSummaryDomain(row: RoleplayChatSummaryRow): RoleplayChatSummary {
        val scenario = toScenarioDomain(row.toScenarioEntity())
        val session = row.sessionId?.let { sessionId ->
            RoleplaySession(
                id = sessionId,
                scenarioId = row.id,
                conversationId = row.sessionConversationId.orEmpty(),
                createdAt = row.sessionCreatedAt ?: 0L,
                updatedAt = row.sessionUpdatedAt ?: 0L,
            )
        }
        val lastMessageAt = row.lastMessageCreatedAt ?: 0L
        val lastActiveAt = maxOf(
            lastMessageAt,
            row.sessionUpdatedAt ?: 0L,
            row.updatedAt,
            row.createdAt,
        )
        return RoleplayChatSummary(
            scenario = scenario,
            session = session,
            lastMessageText = row.lastMessageContent.orEmpty().trim(),
            lastMessageAt = lastMessageAt,
            lastActiveAt = lastActiveAt,
            lastMessageRole = row.lastMessageRole
                ?.let { runCatching { MessageRole.valueOf(it) }.getOrNull() },
        )
    }

    private fun RoleplayChatSummaryRow.toScenarioEntity(): RoleplayScenarioEntity {
        return RoleplayScenarioEntity(
            id = id,
            title = title,
            description = description,
            descriptionPromptEnabled = descriptionPromptEnabled,
            assistantId = assistantId,
            backgroundUri = backgroundUri,
            userDisplayNameOverride = userDisplayNameOverride,
            userPersonaOverride = userPersonaOverride,
            userPortraitUri = userPortraitUri,
            userPortraitUrl = userPortraitUrl,
            characterDisplayNameOverride = characterDisplayNameOverride,
            characterPortraitUri = characterPortraitUri,
            characterPortraitUrl = characterPortraitUrl,
            openingNarration = openingNarration,
            interactionMode = interactionMode,
            enableNarration = enableNarration,
            enableRoleplayProtocol = enableRoleplayProtocol,
            longformModeEnabled = longformModeEnabled,
            autoHighlightSpeaker = autoHighlightSpeaker,
            enableDeepImmersion = enableDeepImmersion,
            enableTimeAwareness = enableTimeAwareness,
            enableNetMeme = enableNetMeme,
            isPinned = isPinned,
            isMuted = isMuted,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun toScenarioEntity(scenario: RoleplayScenario): RoleplayScenarioEntity {
        return RoleplayScenarioEntity(
            id = scenario.id,
            title = scenario.title.trim(),
            description = scenario.description.trim(),
            descriptionPromptEnabled = scenario.descriptionPromptEnabled,
            assistantId = scenario.assistantId.trim().ifBlank { com.example.myapplication.model.DEFAULT_ASSISTANT_ID },
            backgroundUri = scenario.backgroundUri.trim(),
            userDisplayNameOverride = scenario.userDisplayNameOverride.trim(),
            userPersonaOverride = scenario.userPersonaOverride.replace("\r\n", "\n").trim(),
            userPortraitUri = scenario.userPortraitUri.trim(),
            userPortraitUrl = scenario.userPortraitUrl.trim(),
            characterDisplayNameOverride = scenario.characterDisplayNameOverride.trim(),
            characterPortraitUri = scenario.characterPortraitUri.trim(),
            characterPortraitUrl = scenario.characterPortraitUrl.trim(),
            openingNarration = scenario.openingNarration.trim(),
            interactionMode = scenario.interactionMode.storageValue,
            enableNarration = scenario.enableNarration,
            enableRoleplayProtocol = scenario.enableRoleplayProtocol,
            longformModeEnabled = scenario.longformModeEnabled,
            autoHighlightSpeaker = scenario.autoHighlightSpeaker,
            enableDeepImmersion = scenario.enableDeepImmersion,
            enableTimeAwareness = scenario.enableTimeAwareness,
            enableNetMeme = scenario.enableNetMeme,
            isPinned = scenario.isPinned,
            isMuted = scenario.isMuted,
            createdAt = scenario.createdAt,
            updatedAt = scenario.updatedAt,
        )
    }

    private fun toSessionDomain(entity: RoleplaySessionEntity): RoleplaySession {
        return RoleplaySession(
            id = entity.id,
            scenarioId = entity.scenarioId,
            conversationId = entity.conversationId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toOnlineMetaDomain(
        entity: com.example.myapplication.data.local.roleplay.RoleplayOnlineMetaEntity,
    ): RoleplayOnlineMeta {
        return RoleplayOnlineMeta(
            conversationId = entity.conversationId,
            lastCompensationBucket = entity.lastCompensationBucket,
            lastConsumedObservationUpdatedAt = entity.lastConsumedObservationUpdatedAt,
            lastSystemEventToken = entity.lastSystemEventToken,
            activeVideoCallSessionId = entity.activeVideoCallSessionId,
            activeVideoCallStartedAt = entity.activeVideoCallStartedAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toDiaryEntryDomain(entity: RoleplayDiaryEntryEntity): RoleplayDiaryEntry {
        return RoleplayDiaryEntry(
            id = entity.id,
            conversationId = entity.conversationId,
            scenarioId = entity.scenarioId,
            title = entity.title,
            content = entity.content,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            mood = entity.mood,
            weather = entity.weather,
            tags = entity.tagsCsv.splitDiaryTags(),
            dateLabel = entity.dateLabel,
        )
    }

    private suspend fun seedOpeningNarrationIfNeeded(
        conversationId: String,
        scenario: RoleplayScenario,
    ) {
        val openingNarration = scenario.openingNarration.trim()
        if (openingNarration.isBlank()) {
            return
        }
        val timestamp = nowProvider()
        val outputFormat = RoleplayMessageFormatSupport.resolveScenarioOutputFormat(scenario)
        val renderedOpeningNarration = when (outputFormat) {
            RoleplayOutputFormat.PROTOCOL -> {
                "<narration>${escapeXml(openingNarration)}</narration>"
            }

            RoleplayOutputFormat.LONGFORM,
            RoleplayOutputFormat.PLAIN,
            RoleplayOutputFormat.UNSPECIFIED,
            -> {
                openingNarration
            }
        }
        conversationRepository.appendMessages(
            conversationId = conversationId,
            messages = listOf(
                ChatMessage(
                    id = RoleplayConversationSupport.openingNarrationMessageId(scenario.id, conversationId),
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = renderedOpeningNarration,
                    createdAt = timestamp,
                    parts = listOf(
                        textMessagePart(renderedOpeningNarration),
                    ),
                    roleplayOutputFormat = outputFormat,
                    roleplayInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario),
                ),
            ),
            selectedModel = "",
        )
    }

    private fun normalizeAssistantId(assistantId: String): String {
        return assistantId.trim().ifBlank { DEFAULT_ASSISTANT_ID }
    }

    private fun ChatMessage.isOpeningNarrationMessage(scenarioId: String): Boolean {
        return RoleplayConversationSupport.isOpeningNarrationMessageId(id, scenarioId)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

// 日记标签存储约定：; 分隔，每个标签已 trim；空标签被过滤。
private const val DIARY_TAG_DELIMITER = ";"

private fun List<String>.joinDiaryTags(): String {
    return asSequence()
        .map { it.trim().replace(DIARY_TAG_DELIMITER, " ") }
        .filter { it.isNotBlank() }
        .distinct()
        .take(6)
        .joinToString(DIARY_TAG_DELIMITER)
}

private fun String.splitDiaryTags(): List<String> {
    if (isBlank()) return emptyList()
    return split(DIARY_TAG_DELIMITER)
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}
