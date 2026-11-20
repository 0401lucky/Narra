package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayDiaryEntryEntity
import com.example.myapplication.data.local.roleplay.RoleplayGroupParticipantEntity
import com.example.myapplication.data.local.roleplay.RoleplayChatSummaryRow
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.MAX_ONLINE_REPLY_COUNT
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayChatType
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.ROLEPLAY_GROUP_CONVERSATION_ASSISTANT_PREFIX
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.CompanionEncounter
import com.example.myapplication.model.CompanionEncounterState
import com.example.myapplication.model.CompanionEntryOrigin
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.CompanionPreparedTurn
import com.example.myapplication.model.CompanionRequestDescriptor
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.model.CompanionSessionSelection
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class RoleplaySessionStartResult(
    val session: RoleplaySession,
    val reusedExistingSession: Boolean,
    val hasHistory: Boolean,
    val assistantMismatch: Boolean,
    val conversationAssistantId: String = "",
    val conversationMessages: List<ChatMessage> = emptyList(),
)

data class CompanionSessionPreparation(
    val session: RoleplaySession,
    val encounter: CompanionEncounter,
    val opening: CompanionPreparedTurn?,
    val origin: CompanionEntryOrigin,
)

interface RoleplayRepository {
    fun observeScenarios(): Flow<List<RoleplayScenario>>

    fun observeChatSummaries(): Flow<List<RoleplayChatSummary>>

    fun observeScenario(scenarioId: String): Flow<RoleplayScenario?>

    fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?>

    fun observeSessions(): Flow<List<RoleplaySession>>

    fun observeGroupParticipants(scenarioId: String): Flow<List<RoleplayGroupParticipant>> =
        flowOf(emptyList())

    fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>>

    fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>>

    suspend fun listScenarios(): List<RoleplayScenario>

    suspend fun getScenario(scenarioId: String): RoleplayScenario?

    suspend fun upsertScenario(scenario: RoleplayScenario)

    suspend fun upsertScenarioWithParticipants(
        scenario: RoleplayScenario,
        participants: List<RoleplayGroupParticipant>,
    ) = upsertScenario(scenario)

    suspend fun deleteScenario(scenarioId: String)

    suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun prepareCompanionScenario(
        scenarioId: String,
        entryId: String
    ): CompanionSessionPreparation =
        error("当前存储不支持陪伴开场")

    suspend fun hasCompanionHistory(scenarioId: String): Boolean = false

    suspend fun resumeCompanionScenario(scenarioId: String): CompanionSessionSelection? = null

    suspend fun selectCompanionOpening(
        entryId: String,
        expectedEncounterId: String,
        mode: CompanionOpeningMode,
        encounterId: String = UUID.randomUUID().toString(),
    ): CompanionSessionSelection = error("当前存储不支持陪伴开场")

    suspend fun restoreCompanionOrigin(origin: CompanionEntryOrigin, encounterId: String): Boolean =
        false

    suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult

    suspend fun getSessionByScenario(scenarioId: String): RoleplaySession?

    suspend fun getSession(sessionId: String): RoleplaySession?

    suspend fun listGroupParticipants(scenarioId: String): List<RoleplayGroupParticipant> =
        emptyList()

    suspend fun upsertGroupParticipant(participant: RoleplayGroupParticipant) = Unit

    suspend fun deleteGroupParticipant(participantId: String) = Unit

    suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry>

    suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<RoleplayDiaryDraft>,
    ): List<RoleplayDiaryEntry>

    suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta?

    suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta)

    suspend fun updateOnlineMeta(
        conversationId: String,
        transform: (RoleplayOnlineMeta?) -> RoleplayOnlineMeta,
    ) {
        upsertOnlineMeta(transform(getOnlineMeta(conversationId)))
    }

    suspend fun deleteOnlineMeta(conversationId: String)

    suspend fun deleteDiaryEntriesForConversation(conversationId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRoleplayRepository(
    private val roleplayDao: RoleplayDao,
    private val conversationRepository: ConversationRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val imageFileCleaner: suspend (String?) -> Boolean = { false },
    private val mailboxCleanup: suspend (String, String) -> Unit = { _, _ -> },
    private val economyCleanup: suspend (String) -> Unit = {},
    private val database: ChatDatabase? = null,
    private val memoryArchiveRepository: MemoryArchiveRepository? = null,
    private val companionRepository: CompanionRepository? = null,
) : RoleplayRepository {
    private val onlineMetaMutex = Mutex()
    private val scenarioMutex = Mutex()

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

    override fun observeGroupParticipants(scenarioId: String): Flow<List<RoleplayGroupParticipant>> {
        if (scenarioId.isBlank()) {
            return flowOf(emptyList())
        }
        return roleplayDao.observeGroupParticipants(scenarioId).map { participants ->
            participants.map(::toGroupParticipantDomain)
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
        withScenarioTransaction {
            val timestamp = nowProvider()
            val existing = roleplayDao.getScenario(scenario.id)
            val createdAt = existing?.createdAt ?: scenario.createdAt.takeIf { it > 0 } ?: timestamp
            val normalizedScenario = toScenarioEntity(
                scenario.copy(
                    createdAt = createdAt,
                    updatedAt = timestamp,
                ),
            )
            roleplayDao.upsertScenario(normalizedScenario)
            reconcileScenarioRun(existing, normalizedScenario)
        }
    }

    override suspend fun upsertScenarioWithParticipants(
        scenario: RoleplayScenario,
        participants: List<RoleplayGroupParticipant>,
    ) {
        withScenarioTransaction {
            val timestamp = nowProvider()
            val existing = roleplayDao.getScenario(scenario.id)
            val createdAt = existing?.createdAt ?: scenario.createdAt.takeIf { it > 0 } ?: timestamp
            val normalizedScenario = toScenarioEntity(
                scenario.copy(createdAt = createdAt, updatedAt = timestamp),
            )
            roleplayDao.upsertScenario(normalizedScenario)
            val participantEntities = participants
                .filter { it.assistantId.isNotBlank() }
                .distinctBy { it.assistantId.trim() }
                .mapIndexed { index, participant ->
                    toGroupParticipantEntity(
                        participant.copy(
                            scenarioId = normalizedScenario.id,
                            assistantId = participant.assistantId.trim(),
                            displayNameOverride = participant.displayNameOverride.trim(),
                            avatarUriOverride = participant.avatarUriOverride.trim(),
                            sortOrder = index,
                            createdAt = participant.createdAt.takeIf { it > 0 } ?: timestamp,
                            updatedAt = timestamp,
                        ),
                    )
                }
            roleplayDao.replaceGroupParticipantsForScenario(
                scenarioId = normalizedScenario.id,
                participants = participantEntities,
            )
            reconcileScenarioRun(existing, normalizedScenario)
        }
    }

    override suspend fun deleteScenario(scenarioId: String) {
        if (scenarioId.isBlank()) return
        val scenarioEntity = withScenarioTransaction {
            val scenario = roleplayDao.getScenario(scenarioId)
            val session = roleplayDao.getSessionByScenario(scenarioId)
            val conversationIds = buildSet {
                session?.let { add(it.conversationId) }
                memoryArchiveRepository?.listScenarioBindings(scenarioId)
                    ?.forEach { add(it.conversationId) }
            }
            memoryArchiveRepository?.deleteScenarioArchives(scenarioId)
            conversationIds.forEach { conversationId ->
                conversationRepository.deleteConversationById(conversationId)
                roleplayDao.deleteOnlineMeta(conversationId)
                roleplayDao.deleteDiaryEntriesForConversation(conversationId)
            }
            mailboxCleanup(scenarioId, session?.conversationId.orEmpty())
            economyCleanup(scenarioId)
            roleplayDao.deleteScenario(scenarioId)
            scenario
        }
        scenarioEntity?.let {
            imageFileCleaner(it.backgroundUri)
            imageFileCleaner(it.userPortraitUri)
            imageFileCleaner(it.characterPortraitUri)
        }
    }

    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult =
        withScenarioTransaction {
            startScenarioInternal(scenarioId, seedOpening = true)
        }

    private suspend fun startScenarioInternal(
        scenarioId: String,
        seedOpening: Boolean
    ): RoleplaySessionStartResult {
        val scenario = roleplayDao.getScenario(scenarioId)
            ?: error("场景不存在")
        val timestamp = nowProvider()
        val existingSession = roleplayDao.getSessionByScenario(scenarioId)
        if (existingSession != null) {
            val existingConversation =
                conversationRepository.getConversation(existingSession.conversationId)
            if (existingConversation != null) {
                val refreshedSession = existingSession.copy(
                    updatedAt = timestamp,
                    runId = existingSession.runId.ifBlank { UUID.randomUUID().toString() },
                )
                roleplayDao.upsertSession(refreshedSession)
                bindSession(scenario, refreshedSession)
                val cleanedMessages = cleanOrphanedLoadingMessages(
                    conversationId = existingSession.conversationId,
                    selectedModel = existingConversation.model,
                    messages = conversationRepository.listMessages(existingSession.conversationId),
                )
                val hasRealHistory = cleanedMessages.any {
                    !it.isOpeningNarrationMessage(scenario.id) &&
                            it.status != MessageStatus.LOADING
                }
                val historyMessages = if (hasRealHistory || !seedOpening) {
                    cleanedMessages
                } else {
                    ensureOpeningNarrationSeeded(
                        conversationId = existingSession.conversationId,
                        scenario = toScenarioDomain(scenario),
                        existingMessages = cleanedMessages,
                        selectedModel = existingConversation.model,
                    )
                }
                return RoleplaySessionStartResult(
                    session = toSessionDomain(refreshedSession),
                    reusedExistingSession = true,
                    hasHistory = historyMessages.any {
                        !it.isOpeningNarrationMessage(scenario.id) &&
                                it.status != MessageStatus.LOADING
                    },
                    assistantMismatch = normalizeAssistantId(existingConversation.assistantId) !=
                            resolveConversationAssistantId(scenario),
                    conversationAssistantId = normalizeAssistantId(existingConversation.assistantId),
                    conversationMessages = historyMessages,
                )
            }
        }

        return createNewRun(
            scenario,
            existingSession,
            resetPlayState = false,
            seedOpening = seedOpening
        )
    }

    override suspend fun hasCompanionHistory(scenarioId: String): Boolean {
        val conversationId =
            roleplayDao.getSessionByScenario(scenarioId)?.conversationId ?: return false
        return database?.companionDao()?.listEncounters(conversationId)?.isNotEmpty() == true
    }

    override suspend fun prepareCompanionScenario(
        scenarioId: String,
        entryId: String,
    ): CompanionSessionPreparation = withScenarioTransaction {
        val companion = requireNotNull(companionRepository) { "陪伴存储不可用" }
        val archive = requireNotNull(memoryArchiveRepository) { "记忆运行不可用" }
        val scenario = roleplayDao.getScenario(scenarioId) ?: error("场景不存在")
        require(
            scenario.chatType == "single" && !scenario.longformModeEnabled &&
                    scenario.interactionMode != RoleplayInteractionMode.OFFLINE_LONGFORM.storageValue
        ) { "请在单角色对白会话中开始见面" }
        val existing = companion.getEncounter(entryId)
        if (existing != null) {
            val selection = requireNotNull(companionEntryState().read(entryId)) { "开场记录不完整" }
            val selected = selection.encounter
            val session = getSessionByScenario(scenarioId) ?: error("原会话不存在")
            check(
                existing.context.scenarioId == scenarioId && existing.context.conversationId == session.conversationId &&
                        archive.getRuntimeContext(
                            session.conversationId,
                            scenario.assistantId
                        ) == existing.context &&
                        selected.state != CompanionEncounterState.ENDED &&
                        scenario.interactionMode == RoleplayInteractionMode.OFFLINE_DIALOGUE.storageValue
            ) { "原陪伴运行或开场已改变" }
            val turn = companion.listTurns(selected.id)
                .firstOrNull { it.kind == CompanionTurnKind.OPENING }
            val attempt =
                (turn?.activeAttemptId ?: turn?.selectedAttemptId)?.let { companion.getAttempt(it) }
            return@withScenarioTransaction CompanionSessionPreparation(
                session,
                selected,
                if (turn != null && attempt != null) CompanionPreparedTurn(turn, attempt) else null,
                requireNotNull(selection.origin) { "原会话返回信息缺失" },
            )
        }
        val previousOrigin = resumeCompanionScenarioInternal(scenarioId)?.origin
        // 无请求的准备标记和会话同事务保存；取消或进程重建后不能偷偷播入静态 greeting。
        roleplayDao.upsertScenario(
            scenario.copy(
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE.storageValue,
                longformModeEnabled = false,
                enableRoleplayProtocol = true,
                updatedAt = nowProvider(),
            )
        )
        val start = startScenarioInternal(scenarioId, seedOpening = false)
        val context = requireNotNull(
            archive.getRuntimeContext(
                start.session.conversationId,
                scenario.assistantId
            )
        ) {
            "当前角色或身份不可用"
        }
        val origin = previousOrigin ?: CompanionEntryOrigin(
            scenarioId,
            start.session.conversationId,
            start.session.runId,
            context.characterId,
            context.personaKey,
            RoleplayInteractionMode.fromStorageValue(scenario.interactionMode),
            scenario.longformModeEnabled,
            scenario.enableRoleplayProtocol,
        )
        val encounter = companion.createEncounter(context, CompanionOpeningMode.APPROACH, entryId)
        val opening = companion.beginTurn(
            encounter.id, CompanionTurnKind.OPENING,
            descriptor = CompanionRequestDescriptor(
                entryOrigin = origin,
                entrySelectionId = entryId
            ), turnId = "$entryId:opening",
        )
        CompanionSessionPreparation(start.session, encounter, opening, origin)
    }

    override suspend fun resumeCompanionScenario(scenarioId: String): CompanionSessionSelection? =
        withScenarioTransaction {
            resumeCompanionScenarioInternal(scenarioId)
        }

    private suspend fun resumeCompanionScenarioInternal(scenarioId: String): CompanionSessionSelection? {
        val companion = companionRepository ?: return null
        val scenario = roleplayDao.getScenario(scenarioId) ?: return null
        if (scenario.chatType != "single" || scenario.longformModeEnabled ||
            scenario.interactionMode != RoleplayInteractionMode.OFFLINE_DIALOGUE.storageValue
        ) return null
        val session = roleplayDao.getSessionByScenario(scenarioId) ?: return null
        val context =
            memoryArchiveRepository?.getRuntimeContext(session.conversationId, scenario.assistantId)
                ?: return null
        val candidates =
            companion.listEncounters(session.conversationId).filter { it.context == context }
        // D1 的 supersede 和唯一活动槽决定当前选择，不使用时间戳或随机 ID 排序。
        val selected =
            candidates.filter { it.state == CompanionEncounterState.DRAFT }.singleOrNull()
                ?: candidates.filter { it.state == CompanionEncounterState.ACTIVE }.singleOrNull()
                ?: return null
        return companionEntryState().find(selected.id, session.conversationId)
            ?: CompanionSessionSelection(selected, null, null)
    }

    override suspend fun selectCompanionOpening(
        entryId: String,
        expectedEncounterId: String,
        mode: CompanionOpeningMode,
        encounterId: String,
    ): CompanionSessionSelection = withScenarioTransaction {
        val companion = requireNotNull(companionRepository)
        val entries = companionEntryState()
        val previous = requireNotNull(entries.read(entryId)) { "入口准备记录已失效" }
        check(
            previous.encounter.id == expectedEncounterId &&
                    previous.encounter.state == CompanionEncounterState.DRAFT &&
                    memoryArchiveRepository?.getRuntimeContext(
                        previous.encounter.context.conversationId,
                        previous.encounter.context.characterId
                    ) == previous.encounter.context
        ) { "本次开场已在别处切换" }
        if (mode == previous.encounter.openingMode) return@withScenarioTransaction previous
        val selected = companion.createEncounter(previous.encounter.context, mode, encounterId)
        entries.select(entryId, expectedEncounterId, selected.id)
        if (mode != CompanionOpeningMode.DIRECT) companion.beginTurn(
            selected.id,
            CompanionTurnKind.OPENING,
            descriptor = CompanionRequestDescriptor(entryOrigin = previous.origin),
        )
        CompanionSessionSelection(selected, entryId, previous.origin)
    }

    private fun companionEntryState() =
        RoomCompanionEntryState(requireNotNull(database), requireNotNull(companionRepository))

    override suspend fun restoreCompanionOrigin(
        origin: CompanionEntryOrigin,
        encounterId: String,
    ): Boolean = withScenarioTransaction {
        val companion = companionRepository ?: return@withScenarioTransaction false
        val scenario =
            roleplayDao.getScenario(origin.scenarioId) ?: return@withScenarioTransaction false
        val session = roleplayDao.getSessionByScenario(origin.scenarioId)
            ?: return@withScenarioTransaction false
        val encounter = companion.getEncounter(encounterId) ?: return@withScenarioTransaction false
        if (companionEntryState().find(encounterId, session.conversationId)?.origin != origin)
            return@withScenarioTransaction false
        val hasNewDraft = companion.listEncounters(session.conversationId).any {
            it.id != encounterId && it.context == encounter.context && it.state == CompanionEncounterState.DRAFT
        }
        // 依赖 D1 的持久 supersede/活动槽，不能用时间戳与随机 ID 推断选择先后。
        if (encounter.state == CompanionEncounterState.ENDED || hasNewDraft ||
            session.conversationId != origin.conversationId || session.runId != origin.runId ||
            scenario.assistantId != origin.characterId || scenario.chatType != "single" ||
            MemoryArchiveIdentity.personaKey(toScenarioDomain(scenario)) != origin.personaKey ||
            scenario.interactionMode != RoleplayInteractionMode.OFFLINE_DIALOGUE.storageValue || scenario.longformModeEnabled ||
            !scenario.enableRoleplayProtocol ||
            memoryArchiveRepository?.getRuntimeContext(
                session.conversationId,
                origin.characterId
            ) != encounter.context
        ) return@withScenarioTransaction false
        companion.endEncounter(encounterId)
        roleplayDao.upsertScenario(
            scenario.copy(
                interactionMode = origin.interactionMode.storageValue,
                longformModeEnabled = origin.longformModeEnabled,
                enableRoleplayProtocol = origin.enableRoleplayProtocol,
                updatedAt = nowProvider(),
            )
        )
        true
    }

    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult =
        withScenarioTransaction {
            val scenario = roleplayDao.getScenario(scenarioId)
                ?: error("场景不存在")
            val existingSession = roleplayDao.getSessionByScenario(scenarioId)
            createNewRun(scenario, existingSession, resetPlayState = true)
        }

    private suspend fun createNewRun(
        scenario: RoleplayScenarioEntity,
        existingSession: RoleplaySessionEntity?,
        resetPlayState: Boolean,
        seedOpening: Boolean = true,
    ): RoleplaySessionStartResult {
        val timestamp = nowProvider()
        existingSession?.let { session ->
            // 重开保留原文、日记和记忆来源，只有明确删除场景才清理档案。
            memoryArchiveRepository?.archiveConversation(session.conversationId)
            if (resetPlayState) {
                roleplayDao.deleteOnlineMeta(session.conversationId)
                mailboxCleanup(scenario.id, session.conversationId)
                economyCleanup(scenario.id)
            }
        }
        val conversation = conversationRepository.createConversation(
            assistantId = resolveConversationAssistantId(scenario),
        )
        val restartedSession = RoleplaySessionEntity(
            id = existingSession?.id ?: UUID.randomUUID().toString(),
            scenarioId = scenario.id,
            conversationId = conversation.id,
            createdAt = existingSession?.createdAt ?: timestamp,
            updatedAt = timestamp,
            runId = UUID.randomUUID().toString(),
        )
        roleplayDao.upsertSession(restartedSession)
        bindSession(scenario, restartedSession)
        val seededMessages = if (seedOpening) ensureOpeningNarrationSeeded(
            conversationId = conversation.id,
            scenario = toScenarioDomain(scenario),
        ) else emptyList()
        return RoleplaySessionStartResult(
            session = toSessionDomain(restartedSession),
            reusedExistingSession = false,
            hasHistory = false,
            assistantMismatch = false,
            conversationAssistantId = resolveConversationAssistantId(scenario),
            conversationMessages = seededMessages,
        )
    }

    private suspend fun reconcileScenarioRun(
        previous: RoleplayScenarioEntity?,
        scenario: RoleplayScenarioEntity,
    ) {
        val session = roleplayDao.getSessionByScenario(scenario.id) ?: return
        if (conversationRepository.getConversation(session.conversationId) == null) return
        if (previous != null && previous.memoryPurpose != scenario.memoryPurpose) {
            createNewRun(scenario, session, resetPlayState = true)
        } else {
            val boundSession =
                session.copy(runId = session.runId.ifBlank { UUID.randomUUID().toString() })
            if (boundSession != session) roleplayDao.upsertSession(boundSession)
            bindSession(scenario, boundSession)
        }
    }

    private suspend fun bindSession(
        scenario: RoleplayScenarioEntity,
        session: RoleplaySessionEntity
    ) {
        val archive = memoryArchiveRepository ?: return
        val characterIds =
            if (RoleplayChatType.fromStorageValue(scenario.chatType) == RoleplayChatType.GROUP) {
                roleplayDao.listGroupParticipants(scenario.id).map { it.assistantId }
            } else {
                listOf(normalizeAssistantId(scenario.assistantId))
            }
        archive.bindConversation(
            toScenarioDomain(scenario),
            session.conversationId,
            characterIds,
            session.runId
        )
    }

    private suspend fun <T> withScenarioTransaction(block: suspend () -> T): T =
        scenarioMutex.withLock {
            val currentDatabase = database
            if (currentDatabase == null) {
                block()
            } else {
                currentDatabase.withTransaction { block() }
            }
        }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? {
        return roleplayDao.getSessionByScenario(scenarioId)?.let(::toSessionDomain)
    }

    override suspend fun getSession(sessionId: String): RoleplaySession? {
        return roleplayDao.getSession(sessionId)?.let(::toSessionDomain)
    }

    override suspend fun listGroupParticipants(scenarioId: String): List<RoleplayGroupParticipant> {
        if (scenarioId.isBlank()) {
            return emptyList()
        }
        return roleplayDao.listGroupParticipants(scenarioId).map(::toGroupParticipantDomain)
    }

    override suspend fun upsertGroupParticipant(participant: RoleplayGroupParticipant) {
        if (participant.scenarioId.isBlank() || participant.assistantId.isBlank()) {
            return
        }
        withScenarioTransaction {
            val timestamp = nowProvider()
            val existing = roleplayDao.getGroupParticipant(participant.id)
            roleplayDao.upsertGroupParticipants(
                listOf(
                    toGroupParticipantEntity(
                        participant.copy(
                            assistantId = participant.assistantId.trim(),
                            displayNameOverride = participant.displayNameOverride.trim(),
                            avatarUriOverride = participant.avatarUriOverride.trim(),
                            createdAt = existing?.createdAt
                                ?: participant.createdAt.takeIf { it > 0 } ?: timestamp,
                            updatedAt = timestamp,
                        ),
                    ),
                ),
            )
            roleplayDao.getScenario(participant.scenarioId)?.let { reconcileScenarioRun(it, it) }
            if (existing != null && existing.scenarioId != participant.scenarioId) {
                roleplayDao.getScenario(existing.scenarioId)?.let { reconcileScenarioRun(it, it) }
            }
        }
    }

    override suspend fun deleteGroupParticipant(participantId: String) {
        if (participantId.isBlank()) {
            return
        }
        withScenarioTransaction {
            val participant = roleplayDao.getGroupParticipant(participantId)
            roleplayDao.deleteGroupParticipant(participantId)
            participant?.let {
                roleplayDao.getScenario(it.scenarioId)
                    ?.let { scenario -> reconcileScenarioRun(scenario, scenario) }
            }
        }
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

    override suspend fun updateOnlineMeta(
        conversationId: String,
        transform: (RoleplayOnlineMeta?) -> RoleplayOnlineMeta,
    ) {
        onlineMetaMutex.withLock {
            upsertOnlineMeta(transform(getOnlineMeta(conversationId)))
        }
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
            presetId = entity.presetId,
            backgroundUri = entity.backgroundUri,
            userDisplayNameOverride = entity.userDisplayNameOverride,
            userPersonaMaskId = entity.userPersonaMaskId,
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
            enableOnlineProactiveReply = entity.enableOnlineProactiveReply,
            chatType = RoleplayChatType.fromStorageValue(entity.chatType),
            groupReplyMode = RoleplayGroupReplyMode.fromStorageValue(entity.groupReplyMode),
            enableGroupMentionAutoReply = entity.enableGroupMentionAutoReply,
            maxGroupAutoReplies = entity.maxGroupAutoReplies.coerceIn(
                1,
                com.example.myapplication.model.MAX_GROUP_AUTO_REPLIES
            ),
            onlineReplyMinCount = entity.onlineReplyMinCount.coerceIn(1, MAX_ONLINE_REPLY_COUNT),
            onlineReplyMaxCount = entity.onlineReplyMaxCount.coerceIn(1, MAX_ONLINE_REPLY_COUNT),
            isPinned = entity.isPinned,
            isMuted = entity.isMuted,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            memoryPurpose = MemoryPurpose.fromStorageValue(entity.memoryPurpose),
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
                runId = row.sessionRunId.orEmpty(),
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
            presetId = presetId,
            backgroundUri = backgroundUri,
            userDisplayNameOverride = userDisplayNameOverride,
            userPersonaMaskId = userPersonaMaskId,
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
            enableOnlineProactiveReply = enableOnlineProactiveReply,
            chatType = chatType,
            groupReplyMode = groupReplyMode,
            enableGroupMentionAutoReply = enableGroupMentionAutoReply,
            maxGroupAutoReplies = maxGroupAutoReplies,
            onlineReplyMinCount = onlineReplyMinCount,
            onlineReplyMaxCount = onlineReplyMaxCount,
            isPinned = isPinned,
            isMuted = isMuted,
            createdAt = createdAt,
            updatedAt = updatedAt,
            memoryPurpose = memoryPurpose,
        )
    }

    private fun toScenarioEntity(scenario: RoleplayScenario): RoleplayScenarioEntity {
        return RoleplayScenarioEntity(
            id = scenario.id,
            title = scenario.title.trim(),
            description = scenario.description.trim(),
            descriptionPromptEnabled = scenario.descriptionPromptEnabled,
            assistantId = scenario.assistantId.trim()
                .ifBlank { com.example.myapplication.model.DEFAULT_ASSISTANT_ID },
            presetId = scenario.presetId.trim(),
            backgroundUri = scenario.backgroundUri.trim(),
            userDisplayNameOverride = scenario.userDisplayNameOverride.trim(),
            userPersonaMaskId = scenario.userPersonaMaskId.trim(),
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
            enableOnlineProactiveReply = scenario.enableOnlineProactiveReply,
            chatType = scenario.chatType.storageValue,
            groupReplyMode = scenario.groupReplyMode.storageValue,
            enableGroupMentionAutoReply = scenario.enableGroupMentionAutoReply,
            maxGroupAutoReplies = scenario.maxGroupAutoReplies
                .coerceIn(1, com.example.myapplication.model.MAX_GROUP_AUTO_REPLIES),
            onlineReplyMinCount = scenario.onlineReplyMinCount
                .coerceIn(1, MAX_ONLINE_REPLY_COUNT),
            onlineReplyMaxCount = scenario.onlineReplyMaxCount
                .coerceIn(1, MAX_ONLINE_REPLY_COUNT),
            isPinned = scenario.isPinned,
            isMuted = scenario.isMuted,
            createdAt = scenario.createdAt,
            updatedAt = scenario.updatedAt,
            memoryPurpose = scenario.memoryPurpose.storageValue,
        )
    }

    private fun toSessionDomain(entity: RoleplaySessionEntity): RoleplaySession {
        return RoleplaySession(
            id = entity.id,
            scenarioId = entity.scenarioId,
            conversationId = entity.conversationId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            runId = entity.runId,
        )
    }

    private fun toGroupParticipantDomain(entity: RoleplayGroupParticipantEntity): RoleplayGroupParticipant {
        return RoleplayGroupParticipant(
            id = entity.id,
            scenarioId = entity.scenarioId,
            assistantId = entity.assistantId,
            displayNameOverride = entity.displayNameOverride,
            avatarUriOverride = entity.avatarUriOverride,
            sortOrder = entity.sortOrder,
            isMuted = entity.isMuted,
            canAutoReply = entity.canAutoReply,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toGroupParticipantEntity(participant: RoleplayGroupParticipant): RoleplayGroupParticipantEntity {
        return RoleplayGroupParticipantEntity(
            id = participant.id,
            scenarioId = participant.scenarioId,
            assistantId = participant.assistantId.trim(),
            displayNameOverride = participant.displayNameOverride.trim(),
            avatarUriOverride = participant.avatarUriOverride.trim(),
            sortOrder = participant.sortOrder,
            isMuted = participant.isMuted,
            canAutoReply = participant.canAutoReply,
            createdAt = participant.createdAt,
            updatedAt = participant.updatedAt,
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

    private suspend fun ensureOpeningNarrationSeeded(
        conversationId: String,
        scenario: RoleplayScenario,
        existingMessages: List<ChatMessage>? = null,
        selectedModel: String = "",
    ): List<ChatMessage> {
        val currentMessages =
            existingMessages ?: conversationRepository.listMessages(conversationId)
        if (database?.companionDao()?.listEncounters(conversationId)
                ?.isNotEmpty() == true
        ) return currentMessages
        val openingNarration = scenario.openingNarration.trim()
        if (openingNarration.isBlank()) {
            return currentMessages
        }
        if (currentMessages.any { it.isOpeningNarrationMessage(scenario.id) }) {
            return currentMessages
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
                    id = RoleplayConversationSupport.openingNarrationMessageId(
                        scenario.id,
                        conversationId
                    ),
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = renderedOpeningNarration,
                    createdAt = timestamp,
                    parts = listOf(
                        textMessagePart(renderedOpeningNarration),
                    ),
                    roleplayOutputFormat = outputFormat,
                    roleplayInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(
                        scenario
                    ),
                ),
            ),
            selectedModel = selectedModel,
        )
        return conversationRepository.listMessages(conversationId)
    }

    private fun normalizeAssistantId(assistantId: String): String {
        return assistantId.trim().ifBlank { DEFAULT_ASSISTANT_ID }
    }

    private fun resolveConversationAssistantId(scenario: RoleplayScenarioEntity): String {
        return if (RoleplayChatType.fromStorageValue(scenario.chatType) == RoleplayChatType.GROUP) {
            "$ROLEPLAY_GROUP_CONVERSATION_ASSISTANT_PREFIX${scenario.id}"
        } else {
            normalizeAssistantId(scenario.assistantId)
        }
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
