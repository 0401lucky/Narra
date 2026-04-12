package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.toPlainText

object RoleplayConversationSupport {
    private val tensionHighKeywords = listOf("为什么", "凭什么", "到底", "说清楚", "解释", "你敢", "不准", "别过来", "休想", "骗")
    private val tensionTenderKeywords = listOf("靠近", "轻声", "抱", "握住", "吻", "温柔", "心疼", "安抚")
    private val actionPriorityKeywords = listOf("靠近", "后退", "伸手", "抬手", "抓住", "握住", "抱住", "推开", "看着", "逼近", "退开")
    private val emotionPriorityKeywords = listOf("难过", "委屈", "生气", "害怕", "紧张", "失望", "愤怒", "不安", "心虚", "哽咽")

    fun resolveRoleplayNames(
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): Pair<String, String> {
        val userName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        return userName to characterName
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
        return settings.activeProvider()
            ?.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION)
            .orEmpty()
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
        return RoleplayTranscriptFormatter.formatMessages(
            messages = messages,
            userName = userName,
            characterName = characterName,
            allowNarration = scenario.enableNarration,
            interactionMode = scenario.interactionMode,
        ).take(maxLength)
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
            .filter { it.role == MessageRole.ASSISTANT }
            .takeLast(3)
            .mapNotNull { message ->
                val plainText = outputParser.stripMarkup(
                    RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                ).trim()
                plainText.takeIf { it.isNotBlank() }?.take(10)
            }
            .distinct()
        val recentEmotions = messages
            .filter { it.role == MessageRole.ASSISTANT }
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
            .filter { it.role == MessageRole.ASSISTANT }
            .takeLast(2)
            .mapNotNull { message ->
                outputParser.stripMarkup(
                    RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                ).trim().takeIf { it.isNotBlank() }
            }
        val relationTension = resolveRelationTension(recentUserInput)
        val roundPriority = resolveRoundPriority(recentUserInput)
        val currentObstacle = resolveCurrentObstacle(
            repeatedOpeners = repeatedOpeners,
            recentEmotions = recentEmotions,
            recentUserInput = recentUserInput,
        )
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
            }
            if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                if (isVideoCallActive && recentUserInput.isBlank()) {
                    append("当前状态：视频通话已经接通，但用户暂时还没开口；角色不要自顾自长篇独白。\n")
                } else if (recentUserInput.isBlank()) {
                    append("当前状态：用户重新打开了聊天界面，但还没有发言。\n")
                }
                if (isVideoCallActive) {
                    append("语境提醒：当前是实时视频通话，角色能看到对方当下的表情、动作和停顿，但最终仍以简短气泡输出。\n")
                    append("避免把当前互动写成已读、输入中、隔着聊天框等待回复的普通线上聊天。\n")
                }
                resolveTimeGapGuidance(messages, nowProvider)
                    .takeIf { it.isNotBlank() }
                    ?.let { timeGuidance ->
                        append("时间差提示：")
                        append(timeGuidance)
                        append("。\n")
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
        return when {
            recentUserInput.isBlank() -> "持续互动"
            tensionHighKeywords.any { it in recentUserInput } || recentUserInput.any { it == '？' || it == '!' || it == '！' } -> {
                "高压对峙"
            }
            tensionTenderKeywords.any { it in recentUserInput } -> "暧昧靠近"
            recentUserInput.contains("吗") || recentUserInput.contains("会不会") || recentUserInput.contains("是不是") -> {
                "试探拉扯"
            }
            else -> "持续拉扯"
        }
    }

    private fun resolveRoundPriority(
        recentUserInput: String,
    ): String {
        return when {
            recentUserInput.isBlank() -> "先贴住当前氛围，再推进关系、信息或局势中的一项"
            recentUserInput.any { it == '？' || it == '?' } -> "优先正面回应对方刚抛出的追问或质疑"
            actionPriorityKeywords.any { it in recentUserInput } -> "优先接住对方刚做出的动作，并给出角色的即时反应"
            emotionPriorityKeywords.any { it in recentUserInput } -> "优先回应对方当前情绪，再决定如何推进"
            else -> "先回应当下互动，再自然推进关系、信息或局势中的一项"
        }
    }

    private fun resolveCurrentObstacle(
        repeatedOpeners: List<String>,
        recentEmotions: List<String>,
        recentUserInput: String,
    ): String {
        return when {
            repeatedOpeners.isNotEmpty() -> "避免重复模板化起手和惯性动作"
            recentEmotions.isNotEmpty() -> "避免连续复用同一类情绪标签或同一种反应节奏"
            recentUserInput.any { it == '？' || it == '?' } -> "不能回避对方刚刚逼过来的关键问题"
            recentUserInput.isBlank() -> "不要自顾自讲背景，必须贴着当前互动往前走"
            else -> "不要把关系和局势重置回初始状态"
        }
    }
}
