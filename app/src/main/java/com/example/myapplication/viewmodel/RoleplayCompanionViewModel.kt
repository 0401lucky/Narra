package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.conversation.CompanionGenerationResult
import com.example.myapplication.conversation.CompanionRequestCoordinator
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class CompanionEntryPhase { NONE, LOADING, PREPARE, GENERATING, PREVIEW, ACCEPTING, ACTIVE }
enum class CompanionRecoveryAction { NONE, GENERATE, SAVE, ACCEPT }

data class RoleplayCompanionUiState(
    val scenarioId: String = "",
    val entryId: String? = null,
    val encounter: CompanionEncounter? = null,
    val phase: CompanionEntryPhase = CompanionEntryPhase.NONE,
    val origin: CompanionEntryOrigin? = null,
    val turn: CompanionTurn? = null,
    val attemptId: String? = null,
    val preview: String = "",
    val completionStatus: PromptCompletionStatus? = null,
    val recoveryAction: CompanionRecoveryAction = CompanionRecoveryAction.NONE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val zoneId: String = "",
    val settingsRevision: Long = 0,
    val pendingResults: List<CompanionPendingResult> = emptyList(),
    val previewReply: ParsedCompanionReply? = null,
    val observationEnabled: Boolean = false,
) {
    val isPreparing: Boolean get() = phase != CompanionEntryPhase.NONE && phase != CompanionEntryPhase.ACTIVE
    val canAccept: Boolean
        get() = !isBusy && phase == CompanionEntryPhase.PREVIEW &&
                completionStatus == PromptCompletionStatus.COMPLETED && recoveryAction == CompanionRecoveryAction.NONE
}

/** 页面拥有请求 Job，Room 拥有结果。导航恢复只读取状态，绝不自动重发收费请求。 */
class RoleplayCompanionViewModel(
    private val savedState: SavedStateHandle,
    private val roleplay: RoleplayRepository,
    private val companion: CompanionRepository,
    private val requests: CompanionRequestCoordinator,
    private val settingsStore: SettingsStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(RoleplayCompanionUiState())
    val uiState = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var openedKey: String? = null
    private var openedEntryId: String? = null
    private var openedSessionKey: String = ""

    fun open(scenarioId: String, entryId: String?, sessionKey: String = "") {
        val key = "$scenarioId:${entryId ?: sessionKey}"
        if (openedKey == key) return
        cancel()
        openedKey = key
        openedEntryId = entryId
        openedSessionKey = sessionKey
        mutableState.value = RoleplayCompanionUiState(
            scenarioId = scenarioId,
            phase = if (entryId == null) CompanionEntryPhase.NONE else CompanionEntryPhase.LOADING
        )
        runOperation {
            if (entryId != null) {
                val prepared = roleplay.prepareCompanionScenario(scenarioId, entryId)
                currentCoroutineContext().ensureActive()
                savedState[ENTRY_KEY] = key
                mutableState.update { it.copy(entryId = entryId, origin = prepared.origin) }
                hydrate(prepared.encounter)
            } else {
                val selection = roleplay.resumeCompanionScenario(scenarioId)
                if (selection != null) {
                    currentCoroutineContext().ensureActive()
                    mutableState.update {
                        it.copy(entryId = selection.entryId, origin = selection.origin)
                    }
                    hydrate(selection.encounter)
                } else {
                    refreshPendingResults()
                }
            }
        }
    }

    fun refresh() {
        if (openedKey == null || operation?.isActive == true) return
        openedKey = null
        open(mutableState.value.scenarioId, openedEntryId, openedSessionKey)
    }

    fun chooseOpening(mode: CompanionOpeningMode) {
        val state = mutableState.value
        val current = state.encounter ?: return
        if (state.isBusy || current.state != CompanionEncounterState.DRAFT) return
        runOperation {
            val selection = roleplay.selectCompanionOpening(
                requireNotNull(state.entryId) { "开场准备记录缺失，请返回后开始新一场" },
                current.id,
                mode,
                newId()
            )
            val selected = selection.encounter
            currentCoroutineContext().ensureActive()
            mutableState.update {
                it.copy(
                    encounter = selected,
                    origin = selection.origin,
                    preview = "",
                    previewReply = null,
                    errorMessage = null
                )
            }
            if (mode == CompanionOpeningMode.DIRECT) {
                hydrate(selected)
            } else {
                val turn = companion.listTurns(selected.id).lastOrNull() ?: companion.beginTurn(
                    selected.id, CompanionTurnKind.OPENING,
                    descriptor = CompanionRequestDescriptor(entryOrigin = state.origin),
                ).turn
                generateNewAttempt(turn)
            }
        }
    }

    fun regenerate() {
        val turn = mutableState.value.turn ?: return
        if (mutableState.value.isBusy) return
        runOperation { generateNewAttempt(turn, allowUncertainRetry = true) }
    }

    fun send(
        text: String,
        parts: List<ChatMessagePart> = emptyList(),
        onInputSaved: () -> Unit = {}
    ) {
        if (text.isBlank() && parts.isEmpty()) return
        sendTurn(CompanionTurnKind.INPUT, text, parts, onInputSaved)
    }

    fun continueScene() = sendTurn(CompanionTurnKind.CONTINUE)

    private fun sendTurn(
        kind: CompanionTurnKind, text: String = "", parts: List<ChatMessagePart> = emptyList(),
        onInputSaved: () -> Unit = {}
    ) {
        val state = mutableState.value
        val encounter = state.encounter ?: return
        if (state.isBusy || state.phase != CompanionEntryPhase.ACTIVE) return
        runOperation {
            val prepared = requests.beginTurn(encounter.id, kind, text, parts, state.origin)
            currentCoroutineContext().ensureActive()
            mutableState.update {
                it.copy(
                    turn = prepared.turn,
                    attemptId = prepared.attempt.id,
                    recoveryAction = CompanionRecoveryAction.NONE,
                    errorMessage = null,
                    previewReply = null,
                )
            }
            onInputSaved()
            generatePreparedAttempt(prepared.attempt.id)
        }
    }

    fun acceptOpening() {
        val state = mutableState.value
        val attemptId = state.attemptId ?: return
        if (!state.canAccept && state.recoveryAction != CompanionRecoveryAction.ACCEPT || state.isBusy) return
        runOperation {
            mutableState.update { it.copy(phase = CompanionEntryPhase.ACCEPTING) }
            applyResult(requests.acceptOpening(attemptId), accepting = true)
        }
    }

    fun recover() {
        val state = mutableState.value
        val attemptId = state.attemptId ?: return
        when (state.recoveryAction) {
            CompanionRecoveryAction.ACCEPT -> acceptOpening()
            CompanionRecoveryAction.SAVE -> runOperation { applyResult(requests.retrySave(attemptId)) }
            CompanionRecoveryAction.GENERATE -> state.turn?.let { turn ->
                runOperation {
                    generateNewAttempt(
                        turn,
                        allowUncertainRetry = true,
                        intent = CompanionAttemptIntent.RETRY
                    )
                }
            }

            CompanionRecoveryAction.NONE -> Unit
        }
    }

    fun savePendingResult(attemptId: String) {
        val state = mutableState.value
        if (state.isBusy || state.pendingResults.none { it.attemptId == attemptId }) return
        runOperation {
            val result = requests.savePendingResult(state.scenarioId, attemptId)
            currentCoroutineContext().ensureActive()
            when (result) {
                is CompanionGenerationResult.Saved -> {
                    if (state.attemptId == attemptId) {
                        state.encounter?.id?.let { companion.getEncounter(it) }
                            ?.takeUnless { it.state == CompanionEncounterState.ENDED }
                            ?.let { hydrate(it) }
                    }
                    mutableState.update { it.copy(errorMessage = null) }
                }

                is CompanionGenerationResult.NeedsSave -> mutableState.update {
                    it.copy(errorMessage = "结果仍已保留，请稍后重试保存。不会重新生成。")
                }

                else -> mutableState.update {
                    it.copy(errorMessage = "这份结果的来源或运行已改变，未覆盖已有内容。")
                }
            }
            refreshPendingResults()
        }
    }

    fun saveZone(zoneId: String) {
        val state = mutableState.value
        val assistantId = state.encounter?.context?.characterId ?: return
        runOperation {
            val result = settingsStore.updateCompanionSettings(
                assistantId,
                state.settingsRevision,
                CompanionSettingsPatch(zoneId)
            )
            currentCoroutineContext().ensureActive()
            when (result) {
                is CompanionSettingsWriteResult.Saved -> mutableState.update {
                    it.copy(
                        zoneId = result.settings.zoneId,
                        settingsRevision = result.settings.revision, errorMessage = null
                    )
                }

                CompanionSettingsWriteResult.Conflict -> error("设置已在别处更新，请返回后重新打开")
                CompanionSettingsWriteResult.UnsupportedVersion -> error("此角色的陪伴设置版本较新，暂不能修改")
                CompanionSettingsWriteResult.CharacterMissing -> error("角色已不存在")
            }
        }
    }

    fun cancel() {
        generation++
        operation?.cancel()
        operation = null
        mutableState.update { state ->
            state.copy(
                isBusy = false,
                phase = when (state.phase) {
                    CompanionEntryPhase.GENERATING, CompanionEntryPhase.ACCEPTING ->
                        if (state.encounter?.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE
                        else if (state.preview.isNotBlank()) CompanionEntryPhase.PREVIEW else CompanionEntryPhase.PREPARE

                    else -> state.phase
                },
                recoveryAction = if (state.isBusy && state.attemptId != null && state.recoveryAction == CompanionRecoveryAction.NONE)
                    CompanionRecoveryAction.GENERATE else state.recoveryAction,
            )
        }
    }

    fun leave(onFinished: () -> Unit) {
        val state = mutableState.value
        cancel()
        runOperation {
            val encounter = state.encounter
            if (encounter != null) {
                if (state.origin?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                    roleplay.restoreCompanionOrigin(state.origin, encounter.id)
                } else if (encounter.state == CompanionEncounterState.DRAFT) {
                    companion.endEncounter(encounter.id)
                }
            }
            requests.pendingResults(state.scenarioId)
            currentCoroutineContext().ensureActive()
            onFinished()
        }
    }

    fun clearError() = mutableState.update { it.copy(errorMessage = null) }

    private suspend fun generateNewAttempt(
        turn: CompanionTurn,
        allowUncertainRetry: Boolean = false,
        intent: CompanionAttemptIntent = CompanionAttemptIntent.REGENERATE,
    ) {
        val attempt = requests.newAttempt(turn.id, allowUncertainRetry, intent)
            ?: error("当前请求尚不能重试，请等待或重新打开本场")
        currentCoroutineContext().ensureActive()
        mutableState.update {
            it.copy(
                turn = turn, attemptId = attempt.id, errorMessage = null,
                phase = if (it.encounter?.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE else CompanionEntryPhase.GENERATING,
                recoveryAction = CompanionRecoveryAction.NONE,
                previewReply = null,
            )
        }
        generatePreparedAttempt(attempt.id)
    }

    private suspend fun generatePreparedAttempt(attemptId: String) {
        try {
            applyResult(requests.generate(attemptId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            // 输入已持久化但 claim 写入失败时，保留显式重试入口，不让下一轮掩盖它。
            mutableState.update {
                it.copy(
                    recoveryAction = CompanionRecoveryAction.GENERATE,
                    phase = if (it.encounter?.state == CompanionEncounterState.ACTIVE)
                        CompanionEntryPhase.ACTIVE else CompanionEntryPhase.PREPARE,
                )
            }
            throw failure
        }
    }

    private suspend fun hydrate(encounter: CompanionEncounter) {
        check(encounter.state != CompanionEncounterState.ENDED) { "这份开场已废弃，请返回后开始新一场" }
        companion.recoverEncounter(encounter.id)
        val turn = companion.listTurns(encounter.id).lastOrNull()
        val attempt =
            (turn?.activeAttemptId ?: turn?.selectedAttemptId)?.let { companion.getAttempt(it) }
        val appSettings = settingsStore.settingsFlow.first()
        val settings = appSettings.companionSettings(encounter.context.characterId)
        val characterName = appSettings.resolvedAssistants()
            .firstOrNull { it.id == encounter.context.characterId }?.name.orEmpty()
            .ifBlank { "角色" }
        val pendingArtifact = attempt?.id?.let { requests.pendingArtifact(it) }
        val artifact = pendingArtifact ?: attempt?.artifact
        currentCoroutineContext().ensureActive()
        mutableState.update {
            it.copy(
                encounter = encounter,
                turn = turn,
                attemptId = attempt?.id,
                origin = attempt?.descriptor?.entryOrigin ?: it.origin,
                preview = artifact?.rawReply.orEmpty(),
                previewReply = if (attempt != null && artifact != null) com.example.myapplication.roleplay.companion.CompanionReplyProjector.preview(
                    attempt,
                    artifact.rawReply,
                    characterName,
                    artifact.completionStatus == PromptCompletionStatus.COMPLETED,
                ) else null,
                observationEnabled = settings.writing.observation.enabled,
                completionStatus = artifact?.completionStatus,
                phase = if (encounter.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE else
                    if (artifact != null) CompanionEntryPhase.PREVIEW else CompanionEntryPhase.PREPARE,
                zoneId = settings.zoneId,
                settingsRevision = settings.revision,
                recoveryAction = if (pendingArtifact != null) CompanionRecoveryAction.SAVE else when (attempt?.state) {
                    CompanionAttemptState.NOT_STARTED -> if (turn?.kind != CompanionTurnKind.OPENING ||
                        attempt.descriptor.historyScope != null
                    ) CompanionRecoveryAction.GENERATE else CompanionRecoveryAction.NONE

                    CompanionAttemptState.RESULT_READY -> when {
                        artifact?.completionStatus != PromptCompletionStatus.COMPLETED -> CompanionRecoveryAction.GENERATE
                        encounter.state == CompanionEncounterState.ACTIVE -> CompanionRecoveryAction.SAVE
                        else -> CompanionRecoveryAction.NONE
                    }

                    CompanionAttemptState.REQUESTED, CompanionAttemptState.UNCERTAIN, CompanionAttemptState.FAILED,
                    CompanionAttemptState.CANCELLED -> CompanionRecoveryAction.GENERATE

                    else -> CompanionRecoveryAction.NONE
                },
            )
        }
        refreshPendingResults()
    }

    private suspend fun refreshPendingResults() {
        val pending = requests.pendingResults(mutableState.value.scenarioId)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(pendingResults = pending) }
    }

    private suspend fun applyResult(result: CompanionGenerationResult, accepting: Boolean = false) {
        val state = mutableState.value
        val encounter = state.encounter?.id?.let { companion.getEncounter(it) } ?: return
        currentCoroutineContext().ensureActive()
        when (result) {
            is CompanionGenerationResult.Saved -> {
                hydrate(encounter)
                mutableState.update {
                    it.copy(
                        errorMessage = null,
                        recoveryAction = CompanionRecoveryAction.NONE
                    )
                }
            }

            is CompanionGenerationResult.Generated -> {
                val artifact = companion.getAttempt(result.attemptId)?.artifact
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(
                        phase = if (encounter.state == CompanionEncounterState.ACTIVE)
                            CompanionEntryPhase.ACTIVE else CompanionEntryPhase.PREVIEW,
                        preview = artifact?.rawReply.orEmpty(),
                        completionStatus = result.completionStatus,
                        recoveryAction = if (result.completionStatus == PromptCompletionStatus.COMPLETED) CompanionRecoveryAction.NONE else CompanionRecoveryAction.GENERATE,
                        errorMessage = if (result.completionStatus == PromptCompletionStatus.COMPLETED) null else "这份结果未确认完整，尚未加入经历。可重新生成。"
                    )
                }
            }

            is CompanionGenerationResult.NeedsSave -> mutableState.update {
                it.copy(
                    phase = if (encounter.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE else CompanionEntryPhase.PREVIEW,
                    recoveryAction = if (accepting) CompanionRecoveryAction.ACCEPT else CompanionRecoveryAction.SAVE,
                    errorMessage = if (result.artifactPersisted) "结果已保留，请重试保存。不会重新生成。" else "结果尚未写入存储，请在离开前重试保存。",
                )
            }

            is CompanionGenerationResult.Failed -> mutableState.update {
                it.copy(
                    phase = if (encounter.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE else CompanionEntryPhase.PREPARE,
                    recoveryAction = CompanionRecoveryAction.GENERATE,
                    errorMessage = "本次请求结果不确定。原文已保留；重新生成将发起新请求。",
                )
            }

            is CompanionGenerationResult.Rejected -> mutableState.update {
                it.copy(
                    phase = if (encounter.state == CompanionEncounterState.ACTIVE) CompanionEntryPhase.ACTIVE else CompanionEntryPhase.PREPARE,
                    recoveryAction = CompanionRecoveryAction.GENERATE,
                    errorMessage = "本轮场次或来源已改变，未覆盖已有内容。请重试或开始新一场。",
                )
            }
        }
        val currentAttempt = mutableState.value.attemptId?.let { companion.getAttempt(it) }
        val currentArtifact = currentAttempt?.let { requests.pendingArtifact(it.id) ?: it.artifact }
        if (currentAttempt != null && currentArtifact != null) {
            val settings = settingsStore.settingsFlow.first()
            val characterName = settings.resolvedAssistants()
                .firstOrNull { it.id == currentAttempt.context.characterId }?.name.orEmpty()
                .ifBlank { "角色" }
            currentCoroutineContext().ensureActive()
            mutableState.update {
                it.copy(
                    preview = currentArtifact.rawReply,
                    previewReply = com.example.myapplication.roleplay.companion.CompanionReplyProjector.preview(
                        currentAttempt, currentArtifact.rawReply, characterName,
                        currentArtifact.completionStatus == PromptCompletionStatus.COMPLETED,
                    ),
                    observationEnabled = settings.companionSettings(currentAttempt.context.characterId).writing.observation.enabled,
                )
            }
        }
        refreshPendingResults()
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        val owner = generation
        mutableState.update { it.copy(isBusy = true) }
        operation = viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (owner == generation) mutableState.update {
                    it.copy(
                        errorMessage = SensitiveTextRedactor.throwableMessageForUi(
                            failure,
                            "陪伴操作未完成，请重试"
                        ),
                        phase = if (it.phase == CompanionEntryPhase.LOADING) CompanionEntryPhase.PREPARE else it.phase
                    )
                }
            } finally {
                if (owner == generation) {
                    mutableState.update { it.copy(isBusy = false) }
                    operation = null
                }
            }
        }
    }

    override fun onCleared() {
        cancel()
    }

    companion object {
        private const val ENTRY_KEY = "companion.entry.key"

        fun factory(
            savedState: SavedStateHandle,
            roleplay: RoleplayRepository,
            companion: CompanionRepository,
            requests: CompanionRequestCoordinator,
            settingsStore: SettingsStore
        ) = typedViewModelFactory {
            RoleplayCompanionViewModel(savedState, roleplay, companion, requests, settingsStore)
        }
    }
}
