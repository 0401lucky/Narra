package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.textMessagePart
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

    fun observeScenario(scenarioId: String): Flow<RoleplayScenario?>

    fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?>

    fun observeSessions(): Flow<List<RoleplaySession>>

    fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>>

    suspend fun listScenarios(): List<RoleplayScenario>

    suspend fun getScenario(scenarioId: String): RoleplayScenario?

    suspend fun upsertScenario(scenario: RoleplayScenario)

    suspend fun deleteScenario(scenarioId: String)

    suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun getSessionByScenario(scenarioId: String): RoleplaySession?

    suspend fun getSession(sessionId: String): RoleplaySession?
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRoleplayRepository(
    private val roleplayDao: RoleplayDao,
    private val conversationRepository: ConversationRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : RoleplayRepository {
    override fun observeScenarios(): Flow<List<RoleplayScenario>> {
        return roleplayDao.observeScenarios().map { scenarios ->
            scenarios.map(::toScenarioDomain)
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
        val session = roleplayDao.getSessionByScenario(scenarioId)
        if (session != null) {
            conversationRepository.deleteConversationById(session.conversationId)
        }
        roleplayDao.deleteScenario(scenarioId)
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
                val historyMessages = conversationRepository.listMessages(existingSession.conversationId)
                return RoleplaySessionStartResult(
                    session = toSessionDomain(refreshedSession),
                    reusedExistingSession = true,
                    hasHistory = historyMessages.any { !it.isOpeningNarrationMessage(scenario.id) },
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

    private fun toScenarioDomain(entity: RoleplayScenarioEntity): RoleplayScenario {
        return RoleplayScenario(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            assistantId = entity.assistantId,
            backgroundUri = entity.backgroundUri,
            userDisplayNameOverride = entity.userDisplayNameOverride,
            userPortraitUri = entity.userPortraitUri,
            userPortraitUrl = entity.userPortraitUrl,
            characterDisplayNameOverride = entity.characterDisplayNameOverride,
            characterPortraitUri = entity.characterPortraitUri,
            characterPortraitUrl = entity.characterPortraitUrl,
            openingNarration = entity.openingNarration,
            enableNarration = entity.enableNarration,
            enableRoleplayProtocol = entity.enableRoleplayProtocol,
            longformModeEnabled = entity.longformModeEnabled,
            autoHighlightSpeaker = entity.autoHighlightSpeaker,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toScenarioEntity(scenario: RoleplayScenario): RoleplayScenarioEntity {
        return RoleplayScenarioEntity(
            id = scenario.id,
            title = scenario.title.trim(),
            description = scenario.description.trim(),
            assistantId = scenario.assistantId.trim().ifBlank { com.example.myapplication.model.DEFAULT_ASSISTANT_ID },
            backgroundUri = scenario.backgroundUri.trim(),
            userDisplayNameOverride = scenario.userDisplayNameOverride.trim(),
            userPortraitUri = scenario.userPortraitUri.trim(),
            userPortraitUrl = scenario.userPortraitUrl.trim(),
            characterDisplayNameOverride = scenario.characterDisplayNameOverride.trim(),
            characterPortraitUri = scenario.characterPortraitUri.trim(),
            characterPortraitUrl = scenario.characterPortraitUrl.trim(),
            openingNarration = scenario.openingNarration.trim(),
            enableNarration = scenario.enableNarration,
            enableRoleplayProtocol = scenario.enableRoleplayProtocol,
            longformModeEnabled = scenario.longformModeEnabled,
            autoHighlightSpeaker = scenario.autoHighlightSpeaker,
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

    private suspend fun seedOpeningNarrationIfNeeded(
        conversationId: String,
        scenario: RoleplayScenario,
    ) {
        val openingNarration = scenario.openingNarration.trim()
        if (openingNarration.isBlank()) {
            return
        }
        val timestamp = nowProvider()
        conversationRepository.appendMessages(
            conversationId = conversationId,
            messages = listOf(
                ChatMessage(
                    id = openingNarrationMessageId(scenario.id, conversationId),
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = if (scenario.enableRoleplayProtocol) {
                        "<narration>${escapeXml(openingNarration)}</narration>"
                    } else {
                        openingNarration
                    },
                    createdAt = timestamp,
                    parts = listOf(
                        textMessagePart(
                            if (scenario.enableRoleplayProtocol) {
                                "<narration>${escapeXml(openingNarration)}</narration>"
                            } else {
                                openingNarration
                            },
                        ),
                    ),
                ),
            ),
            selectedModel = "",
        )
    }

    private fun normalizeAssistantId(assistantId: String): String {
        return assistantId.trim().ifBlank { DEFAULT_ASSISTANT_ID }
    }

    private fun openingNarrationMessageId(
        scenarioId: String,
        conversationId: String,
    ): String {
        return "rp-opening-$scenarioId-$conversationId"
    }

    private fun ChatMessage.isOpeningNarrationMessage(scenarioId: String): Boolean {
        return id.startsWith("rp-opening-$scenarioId-")
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
