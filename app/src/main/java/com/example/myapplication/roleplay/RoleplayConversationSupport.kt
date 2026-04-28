package com.example.myapplication.roleplay

import com.example.myapplication.conversation.PromptExcerptSupport
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ResolvedUserPersona
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.toPlainText

object RoleplayConversationSupport {
    // 词表与匹配逻辑抽至 RoleplayDirectorKeywords，便于复用、测试和扩展。

    fun resolveRoleplayNames(
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): Pair<String, String> {
        val userName = resolveUserPersona(scenario, settings).displayName
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        return userName to characterName
    }

    fun resolveUserPersona(
        scenario: RoleplayScenario?,
        settings: AppSettings,
    ): ResolvedUserPersona {
        return if (scenario == null) {
            settings.resolveUserPersona()
        } else {
            settings.resolveUserPersona(
                maskId = scenario.userPersonaMaskId,
                displayNameOverride = scenario.userDisplayNameOverride,
                personaPromptOverride = scenario.userPersonaOverride,
                avatarUriOverride = scenario.userPortraitUri,
                avatarUrlOverride = scenario.userPortraitUrl,
            )
        }
    }

    fun resolvePromptSettings(
        scenario: RoleplayScenario,
        settings: AppSettings,
    ): AppSettings {
        val persona = resolveUserPersona(scenario, settings)
        return settings.copy(
            userDisplayName = persona.displayName,
            userPersonaPrompt = if (scenario.userPersonaOverride.isBlank()) {
                persona.personaPrompt
            } else {
                ""
            },
            userAvatarUri = persona.avatarUri,
            userAvatarUrl = persona.avatarUrl,
        )
    }

    fun resolvePromptAssistant(
        scenario: RoleplayScenario,
        assistant: Assistant?,
    ): Assistant? {
        val characterNameOverride = scenario.characterDisplayNameOverride.trim()
        return if (characterNameOverride.isBlank()) {
            assistant
        } else {
            assistant?.copy(name = characterNameOverride)
        }
    }

    fun resolveAssistant(settings: AppSettings, assistantId: String): Assistant? {
        return settings.resolvedAssistants().firstOrNull { it.id == assistantId }
            ?: settings.activeAssistant()
    }

    fun resolveSelectedModelId(settings: AppSettings): String {
        return settings.activeProvider()?.selectedModel
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
    }

    fun resolveSuggestionModelId(settings: AppSettings): String {
        return settings.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION)
    }

    fun buildTranscriptInput(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        maxLength: Int,
    ): String {
        val (userName, characterName) = resolveRoleplayNames(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
        )
        val transcriptSegments = RoleplayTranscriptFormatter.formatMessageSegments(
            messages = messages,
            userName = userName,
            characterName = characterName,
            allowNarration = scenario.enableNarration,
            interactionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario),
        )
        return PromptExcerptSupport.joinLatestSegments(
            segments = transcriptSegments,
            maxLength = maxLength,
        )
    }

    fun resolveLatestUserInputText(requestMessages: List<ChatMessage>): String {
        val latestUserMessage = requestMessages.lastOrNull { it.role == MessageRole.USER } ?: return ""
        return latestUserMessage.parts.toPlainText()
            .ifBlank { latestUserMessage.content.trim() }
    }

    fun buildDynamicDirectorNote(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        outputParser: RoleplayOutputParser,
        isVideoCallActive: Boolean = false,
        referenceCandidates: List<OnlineMessageReferenceCandidate> = emptyList(),
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): String {
        val (userName, characterName) = resolveRoleplayNames(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
        )
        val recentUserInput = messages
            .lastOrNull { it.role == MessageRole.USER }
            ?.parts
            ?.toPlainText()
            .orEmpty()
            .ifBlank { messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty() }
            .trim()
        val repeatedOpeners = messages
            .filter { it.role == MessageRole.ASSISTANT && it.systemEventKind == RoleplayOnlineEventKind.NONE }
            .takeLast(3)
            .mapNotNull { message ->
                val plainText = outputParser.stripMarkup(
                    RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                ).trim()
                plainText.takeIf { it.isNotBlank() }?.take(10)
            }
            .distinct()
        val recentEmotions = messages
            .filter { it.role == MessageRole.ASSISTANT && it.systemEventKind == RoleplayOnlineEventKind.NONE }
            .takeLast(3)
            .flatMap { message ->
                when (RoleplayMessageFormatSupport.resolveAssistantMessageOutputFormat(message)) {
                    com.example.myapplication.model.RoleplayOutputFormat.LONGFORM -> emptyList()
                    else -> outputParser.parseAssistantOutput(
                        rawContent = RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                        characterName = characterName,
                        allowNarration = scenario.enableNarration,
                    ).mapNotNull { segment ->
                        segment.emotion.trim().takeIf { it.isNotBlank() }
                    }
                }
            }
            .distinct()
        val recentAssistantTexts = messages
            .filter { it.role == MessageRole.ASSISTANT && it.systemEventKind == RoleplayOnlineEventKind.NONE }
            .takeLast(2)
            .mapNotNull { message ->
                outputParser.stripMarkup(
                    RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                ).trim().takeIf { it.isNotBlank() }
            }
        val recentClichePhrases = RoleplayAntiClicheSupport.detectRecentClichePhrases(recentAssistantTexts)
        val relationTension = resolveRelationTension(recentUserInput)
        val roundPriority = resolveRoundPriority(recentUserInput)
        val currentObstacle = resolveCurrentObstacle(
            repeatedOpeners = repeatedOpeners,
            recentEmotions = recentEmotions,
            recentUserInput = recentUserInput,
        )
        val currentInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario)
        val continuityAnchor = recentAssistantTexts.lastOrNull()
            ?.take(42)
            ?.takeIf { it.isNotBlank() }
        return buildString {
            append("当前关系张力：")
            append(relationTension)
            append("。\n")
            append("当前目标或优先推进点：")
            append(roundPriority)
            append("。\n")
            append("当前阻碍：")
            append(currentObstacle)
            append("。\n")
            if (recentUserInput.isNotBlank()) {
                append("优先回应 ")
                append(userName.ifBlank { "玩家" })
                append(" 刚刚提到的具体细节：")
                append(recentUserInput.take(80))
                append("。\n")
            }
            if (!continuityAnchor.isNullOrBlank()) {
                append("优先接住上一轮已经抛出的线索或态度：")
                append(continuityAnchor)
                append("。\n")
                append("不要把上一轮已经表达过的核心态度换个说法再重复：")
                append(continuityAnchor)
                append("。\n")
            }
            if (currentInteractionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                if (isVideoCallActive && recentUserInput.isBlank()) {
                    append("当前状态：视频通话已经接通，但用户暂时还没开口；角色不要自顾自长篇独白。\n")
                } else if (recentUserInput.isBlank()) {
                    append("当前状态：用户重新打开了聊天界面，但还没有发言。\n")
                }
                if (isVideoCallActive) {
                    append("语境提醒：当前是实时视频通话，角色能看到对方当下的表情、动作和停顿，但最终仍以简短气泡输出。\n")
                    append("避免把当前互动写成已读、输入中、隔着聊天框等待回复的普通线上聊天。\n")
                }
                // 时间断层旁白：30 分钟以上触发
                if (scenario.enableTimeAwareness) {
                    val latestMessageTimestamp = messages
                        .filter { it.createdAt > 0L }
                        .maxOfOrNull { it.createdAt }
                        ?: 0L
                    val currentTime = nowProvider()
                    TimeGapNarrationSupport.buildTimeGapNarration(latestMessageTimestamp, currentTime)
                        ?.let { narration ->
                            append(narration)
                            append("\n")
                        }
                    // 高层关系影响指导（6小时以上才触发附加行为提示）
                    resolveTimeGapGuidance(messages, nowProvider)
                        .takeIf { it.isNotBlank() }
                        ?.let { timeGuidance ->
                            append("时间差提示：")
                            append(timeGuidance)
                            append("。\n")
                        }
                }
                RoleplayOnlineReferenceSupport.formatCandidatesForPrompt(referenceCandidates)
                    .takeIf { it.isNotBlank() }
                    ?.let { formattedCandidates ->
                        append(formattedCandidates)
                        append('\n')
                    }
            }
            if (repeatedOpeners.isNotEmpty()) {
                append("避免直接复用最近出现过的起手句或动作模板：")
                append(repeatedOpeners.joinToString("、"))
                append("。\n")
            }
            if (recentEmotions.isNotEmpty()) {
                append("减少重复使用这些情绪标签：")
                append(recentEmotions.joinToString("、"))
                append("。\n")
            }
            if (recentClichePhrases.isNotEmpty()) {
                append("避免继续复用这些油腻八股词：")
                append(recentClichePhrases.joinToString("、"))
                append("。\n")
            }
            append("本轮必须新增一个有效推进点：新的观察、动作反馈、信息补充、态度转折四者至少其一。\n")
            append("如果上一轮已经质问、安抚、表态或试探过，本轮不要只做同义复述，要换成接招、补充、转折或抛出新线索。\n")
            append("允许停顿、试探、反问和转折，让 ")
            append(characterName)
            append(" 像在临场反应，不要每轮都完整解释动机。\n")
            append("推进时先回应，再顺势往前推一小步，不要一下子替双方做完所有决定。")
        }.trim()
    }

    private fun resolveTimeGapGuidance(
        messages: List<ChatMessage>,
        nowProvider: () -> Long,
    ): String {
        val latestTimestamp = messages
            .filter { it.createdAt > 0L }
            .maxOfOrNull { it.createdAt }
            ?: return ""
        val gapMillis = (nowProvider() - latestTimestamp).coerceAtLeast(0L)
        val hours = gapMillis / (60 * 60 * 1000)
        val days = gapMillis / (24 * 60 * 60 * 1000)
        return when {
            gapMillis < 6 * 60 * 60 * 1000L -> ""
            gapMillis < 24 * 60 * 60 * 1000L -> "距离上次聊天约 $hours 小时，角色可以表现轻微等待感或挂念感"
            gapMillis < 3 * 24 * 60 * 60 * 1000L -> "距离上次聊天约 $days 天，角色可按人设表现明显在意、埋怨、试探或冷淡"
            gapMillis < 14 * 24 * 60 * 60 * 1000L -> "距离上次聊天已超过 $days 天，角色可自然带出失联后的情绪积压"
            else -> "距离上次聊天已超过 $days 天，角色可强烈体现断联后的情绪与关系后效"
        }
    }

    fun openingNarrationMessageId(
        scenarioId: String,
        conversationId: String,
    ): String {
        return "rp-opening-$scenarioId-$conversationId"
    }

    fun isOpeningNarrationMessageId(
        messageId: String,
        scenarioId: String,
    ): Boolean {
        return messageId.startsWith("rp-opening-$scenarioId-")
    }

    private fun resolveRelationTension(
        recentUserInput: String,
    ): String {
        val text = RoleplayDirectorKeywords.normalize(recentUserInput)
        return when {
            text.isBlank() -> "持续互动"
            RoleplayDirectorKeywords.containsAny(text, RoleplayDirectorKeywords.tensionHigh)
                || RoleplayDirectorKeywords.hasQuestionMark(text)
                || RoleplayDirectorKeywords.hasExclamation(text) -> "高压对峙"
            RoleplayDirectorKeywords.containsAny(text, RoleplayDirectorKeywords.tensionTender) -> "暧昧靠近"
            text.contains("吗") || text.contains("会不会") || text.contains("是不是") -> "试探拉扯"
            else -> "持续拉扯"
        }
    }

    private fun resolveRoundPriority(
        recentUserInput: String,
    ): String {
        val text = RoleplayDirectorKeywords.normalize(recentUserInput)
        return when {
            text.isBlank() -> "先贴住当前氛围，再推进关系、信息或局势中的一项"
            RoleplayDirectorKeywords.hasQuestionMark(text) -> "优先正面回应对方刚抛出的追问或质疑"
            RoleplayDirectorKeywords.containsAny(text, RoleplayDirectorKeywords.actionPriority) ->
                "优先接住对方刚做出的动作，并给出角色的即时反应"
            RoleplayDirectorKeywords.containsAny(text, RoleplayDirectorKeywords.emotionPriority) ->
                "优先回应对方当前情绪，再决定如何推进"
            else -> "先回应当下互动，再自然推进关系、信息或局势中的一项"
        }
    }

    private fun resolveCurrentObstacle(
        repeatedOpeners: List<String>,
        recentEmotions: List<String>,
        recentUserInput: String,
    ): String {
        val text = RoleplayDirectorKeywords.normalize(recentUserInput)
        return when {
            repeatedOpeners.isNotEmpty() -> "避免重复模板化起手和惯性动作"
            recentEmotions.isNotEmpty() -> "避免连续复用同一类情绪标签或同一种反应节奏"
            RoleplayDirectorKeywords.hasQuestionMark(text) -> "不能回避对方刚刚逼过来的关键问题"
            text.isBlank() -> "不要自顾自讲背景，必须贴着当前互动往前走"
            else -> "不要把关系和局势重置回初始状态"
        }
    }
}
