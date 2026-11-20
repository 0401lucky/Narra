package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.roleplay.CompanionHistoryRepository
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayOnlineReferenceSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplaySummaryWindowSupport
import com.example.myapplication.roleplay.companion.CompanionPromptSupport
import com.example.myapplication.roleplay.companion.CompanionPresentationSupport
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

/** 每次显式操作独立组装；持久 claim/产物/提交仍由 D1 的协调器拥有。 */
class CompanionRequestCoordinator(
    private val companion: CompanionRepository,
    private val turns: CompanionTurnCoordinator,
    private val history: CompanionHistoryRepository,
    private val roleplay: RoleplayRepository,
    private val conversations: ConversationRepository,
    private val archive: MemoryArchiveRepository,
    private val settingsStore: SettingsStore,
    private val assembler: PromptContextAssembler,
    private val gateway: AiGateway,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val deviceZone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun beginTurn(
        encounterId: String,
        kind: CompanionTurnKind,
        text: String = "",
        parts: List<ChatMessagePart> = emptyList(),
        origin: CompanionEntryOrigin? = null,
    ): CompanionPreparedTurn {
        require(kind != CompanionTurnKind.OPENING) { "开场沿准备回合生成" }
        val encounter = requireNotNull(companion.getEncounter(encounterId)) { "陪伴场次不存在" }
        val settings = settingsStore.settingsFlow.first()
        val assistant = requireNotNull(
            settings.resolvedAssistants()
                .find { it.id == encounter.context.characterId }) { "角色已不存在" }
        val scope = history.freeze(
            encounter.context, CompanionHistoryCursor(now(), "\uffff"),
            CompanionHistorySelection(RoleplaySummaryWindowSupport.resolveRecentWindow(assistant))
        )
        val descriptor = descriptor(settings, encounter.context.characterId, scope, origin, newId())
        val user = if (kind == CompanionTurnKind.INPUT) ChatMessage(
            id = newId(),
            conversationId = encounter.context.conversationId,
            role = MessageRole.USER,
            content = text.trim(),
            createdAt = now(),
            parts = listOfNotNull(
                text.trim().takeIf(String::isNotBlank)?.let(::textMessagePart)
            ) + parts,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        ) else null
        return companion.beginTurn(encounterId, kind, user, descriptor, newId())
    }

    suspend fun newAttempt(
        turnId: String,
        allowUncertainRetry: Boolean = false,
        intent: CompanionAttemptIntent = CompanionAttemptIntent.REGENERATE,
    ): CompanionAttempt? {
        val turn = companion.getTurn(turnId) ?: return null
        val previous =
            (turn.activeAttemptId ?: turn.selectedAttemptId)?.let { companion.getAttempt(it) }
                ?: return null
        if (intent == CompanionAttemptIntent.RETRY) {
            require(
                previous.descriptor.protocolVersion == 1 &&
                        (previous.descriptor.presentation == null || previous.descriptor.presentation.snapshotVersion == 1)
            ) {
                "本轮正文或表现版本不受支持，请明确重新生成"
            }
        }
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.resolvedAssistants().find { it.id == previous.context.characterId }
            ?: return null
        val oldScope = previous.descriptor.historyScope
        // 准备标记没有 scope；首次明确生成才冻结当时最新原文。reroll 则不越过原触发范围。
        val scope = history.freeze(
            previous.context, CompanionHistoryCursor(now(), "\uffff"),
            CompanionHistorySelection(
                oldScope?.maxMessages ?: RoleplaySummaryWindowSupport.resolveRecentWindow(assistant)
            ), oldScope
        )
        val descriptor = descriptor(
            settings, previous.context.characterId, scope, previous.descriptor.entryOrigin,
            turn.replyMessageId ?: previous.descriptor.replyMessageId ?: newId(),
            presentation = if (intent == CompanionAttemptIntent.RETRY && oldScope != null)
                previous.descriptor.presentation ?: CompanionPresentationSnapshot(
                    sourceSettingsRevision = previous.descriptor.settingsRevision,
                ) else null,
        )
        return companion.newAttempt(turnId, descriptor, allowUncertainRetry)
    }

    suspend fun generate(attemptId: String): CompanionGenerationResult {
        val result = turns.generate(attemptId) { attempt ->
            check(attempt.descriptor.protocolVersion == 1) { "本轮正文协议版本不受支持" }
            val turn = requireNotNull(companion.getTurn(attempt.turnId))
            val encounter = requireNotNull(companion.getEncounter(turn.encounterId))
            val scenario = requireNotNull(roleplay.getScenario(attempt.context.scenarioId))
            val settings = settingsStore.settingsFlow.first()
            val assistant = requireNotNull(
                settings.resolvedAssistants()
                    .find { it.id == attempt.context.characterId }) { "角色已不存在" }
            val scope = requireNotNull(attempt.descriptor.historyScope) { "本轮尚未冻结原文授权" }
            val timePolicy =
                requireNotNull(attempt.descriptor.timePolicy) { "本轮时间设置快照缺失" }
            check(
                settings.companionSettings(assistant.id).revision == attempt.descriptor.settingsRevision &&
                        scenario.enableTimeAwareness == timePolicy.enabled
            ) { "本轮设置已经改变，请重新发起" }
            check(
                scenario.assistantId == assistant.id && !scenario.isGroupChat &&
                        scenario.interactionMode == RoleplayInteractionMode.OFFLINE_DIALOGUE && !scenario.longformModeEnabled
            )
            check(
                archive.getRuntimeContext(
                    scope.context.conversationId,
                    assistant.id
                ) == scope.context
            )
            val rawHistory = history.read(scope)
            val input =
                if (turn.kind == CompanionTurnKind.INPUT) conversations.listMessages(attempt.context.conversationId)
                    .firstOrNull { it.id == turn.inputMessageId && it.memoryRevision == attempt.expectedInputRevision } else null
            if (turn.kind == CompanionTurnKind.INPUT) check(input != null) { "本轮输入已改变" }
            val inputText = input?.content.orEmpty()
            val semanticRecent = rawHistory + listOfNotNull(input)
            val promptContext = assembler.assembleCompanion(
                settings = RoleplayConversationSupport.resolvePromptSettings(
                    scenario,
                    settings,
                    includeScenarioPersona = true
                ),
                assistant = requireNotNull(
                    RoleplayConversationSupport.resolvePromptAssistant(
                        scenario,
                        assistant
                    )
                ),
                conversation = requireNotNull(conversations.getConversation(attempt.context.conversationId)),
                userInputText = inputText,
                recentMessages = semanticRecent,
                scenarioPresetId = scenario.presetId,
                companionContext = CompanionPromptContext(scope, scenario.enableTimeAwareness),
            )
            check(promptContext.memoryRecallPlan == null || promptContext.memoryRecallPlan.context == attempt.context)
            val zone = ZoneId.of(timePolicy.zoneId)
            val prompt = RoleplayPromptDecorator.decorate(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = settings,
                includeOpeningNarrationReference = false,
                companionMode = true,
                companionZoneId = zone,
                companionPresentation = attempt.descriptor.presentation,
                companionTurnKind = turn.kind,
                nowProvider = { timePolicy.requestTime },
            )
            val sanitized = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
                rawHistory, scenario, assistant, settings, RoleplayOutputParser(),
            )
            val messages = CompanionPromptSupport.messages(
                sanitized, input, turn, encounter.openingMode,
                attempt.context.conversationId
            )
            val plan = promptContext.memoryRecallPlan ?: MemoryRecallPlan(
                attempt.context,
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                0,
                settings.memoryRecallTokenBudget,
            )
            val request = MemoryRecallRequest(
                attempt.id, requireNotNull(attempt.descriptor.replyMessageId),
                turn.inputMessageId.orEmpty(), plan, now()
            )
            gateway.sendMessageStream(
                messages = messages,
                systemPrompt = prompt,
                promptMode = PromptMode.ROLEPLAY,
                promptEnvelope = promptContext.promptEnvelope.copy(
                    memoryRecallRequest = request, companionMode = true,
                    statusCardsEnabled = false, hideStatusBlocksInBubble = true,
                    companionRequestGuard = CompanionRequestGuard {
                        val currentSettings = settingsStore.settingsFlow.first()
                        val currentScenario = roleplay.getScenario(scenario.id)
                        currentSettings.resolvedAssistants().any { it.id == assistant.id } &&
                                currentScenario?.assistantId == assistant.id && !currentScenario.isGroupChat &&
                                currentScenario.interactionMode == RoleplayInteractionMode.OFFLINE_DIALOGUE &&
                                !currentScenario.longformModeEnabled &&
                                currentSettings.activeProvider()?.id.orEmpty() == attempt.descriptor.providerId &&
                                currentSettings.selectedCompanionModel() == attempt.descriptor.modelName &&
                                currentSettings.companionSettings(assistant.id).revision == attempt.descriptor.settingsRevision &&
                                currentScenario.enableTimeAwareness == timePolicy.enabled &&
                                (plan.materials.isEmpty() || currentSettings.resolvedAssistants()
                                    .firstOrNull { it.id == assistant.id }?.memoryEnabled == true) &&
                                companion.validateRequest(attempt.id, attempt.claimToken.orEmpty())
                    },
                ),
                toolingOptions = GatewayToolingOptions(),
            )
        }
        return commitOpeningReroll(attemptId, result)
    }

    suspend fun retrySave(attemptId: String): CompanionGenerationResult =
        commitOpeningReroll(attemptId, turns.retrySave(attemptId))

    suspend fun acceptOpening(attemptId: String): CompanionGenerationResult =
        turns.acceptOpening(attemptId)

    suspend fun pendingArtifact(attemptId: String): CompanionGeneratedArtifact? =
        turns.pendingArtifact(attemptId)

    suspend fun pendingResults(scenarioId: String): List<CompanionPendingResult> {
        turns.prunePendingArtifacts()
        val context = recoveryContext(scenarioId) ?: return emptyList()
        return companion.listPendingResults(context)
    }

    suspend fun savePendingResult(
        scenarioId: String,
        attemptId: String
    ): CompanionGenerationResult {
        val context = recoveryContext(scenarioId)
            ?: return CompanionGenerationResult.Rejected(CompanionConflict.CONTEXT_CHANGED)
        if (companion.listPendingResults(context).none { it.attemptId == attemptId })
            return CompanionGenerationResult.Rejected(CompanionConflict.SOURCE_CHANGED)
        return retrySave(attemptId)
    }

    private suspend fun recoveryContext(scenarioId: String): MemoryRuntimeContext? {
        val scenario =
            roleplay.getScenario(scenarioId)?.takeUnless { it.isGroupChat } ?: return null
        if (settingsStore.settingsFlow.first().resolvedAssistants()
                .none { it.id == scenario.assistantId }
        ) return null
        val session = roleplay.getSessionByScenario(scenarioId) ?: return null
        return archive.getRuntimeContext(session.conversationId, scenario.assistantId)
            ?.takeIf { it.scenarioId == scenarioId && it.runId == session.runId }
    }

    private suspend fun commitOpeningReroll(
        attemptId: String,
        result: CompanionGenerationResult
    ): CompanionGenerationResult {
        val attempt = companion.getAttempt(attemptId) ?: return result
        val turn = companion.getTurn(attempt.turnId) ?: return result
        return if (result is CompanionGenerationResult.Generated && result.completionStatus == PromptCompletionStatus.COMPLETED &&
            turn.kind == CompanionTurnKind.OPENING && turn.committedRevision > 0
        ) turns.acceptOpening(attemptId) else result
    }

    private suspend fun descriptor(
        settings: AppSettings, characterId: String, scope: AllowedCompanionHistoryScope,
        origin: CompanionEntryOrigin?, replyMessageId: String,
        presentation: CompanionPresentationSnapshot? = null,
    ): CompanionRequestDescriptor {
        val scenario = requireNotNull(roleplay.getScenario(scope.context.scenarioId))
        val zone = settings.companionSettings(characterId).zoneId.takeIf(String::isNotBlank)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone()
        val requestTime = now()
        return CompanionRequestDescriptor(
            providerId = settings.activeProvider()?.id.orEmpty(),
            modelName = settings.selectedCompanionModel(),
            settingsRevision = settings.companionSettings(characterId).revision,
            promptVersion = "${CompanionPromptSupport.VERSION}/${CompanionPresentationSupport.PROMPT_VERSION}",
            sourceMessages = scope.messages,
            historyScope = scope,
            entryOrigin = origin,
            replyMessageId = replyMessageId,
            timePolicy = CompanionTimePolicy(scenario.enableTimeAwareness, zone.id, requestTime),
            presentation = presentation ?: CompanionPresentationSupport.freeze(
                settings.companionSettings(characterId), "$replyMessageId:$requestTime",
            ),
        )
    }

    private fun AppSettings.selectedCompanionModel(): String =
        activeProvider()?.selectedModel ?: selectedModel
}
