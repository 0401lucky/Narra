package com.example.myapplication.roleplay

import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.model.textMessagePart
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class RoleplayGroupMemberContext(
    val participant: RoleplayGroupParticipant,
    val assistant: Assistant?,
) {
    val displayName: String
        get() = participant.displayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }

    val avatarUri: String
        get() = participant.avatarUriOverride.trim()
            .ifBlank { assistant?.avatarUri?.trim().orEmpty() }
}

data class RoleplayGroupReplyTurn(
    val participantId: String,
    val assistantId: String,
    val displayName: String,
    val intent: String = "",
    val reason: String = "",
)

data class RoleplayGroupReplyPlan(
    val turns: List<RoleplayGroupReplyTurn>,
    val notice: String = "",
)

data class RoleplayGroupDirectorRequest(
    val scenario: RoleplayScenario,
    val members: List<RoleplayGroupMemberContext>,
    val recentMessages: List<ChatMessage>,
    val latestUserMessage: ChatMessage,
)

interface RoleplayGroupDirector {
    suspend fun plan(request: RoleplayGroupDirectorRequest): RoleplayGroupReplyPlan
}

class DefaultRoleplayGroupDirector(
    private val aiGateway: AiGateway,
) : RoleplayGroupDirector {
    override suspend fun plan(request: RoleplayGroupDirectorRequest): RoleplayGroupReplyPlan {
        if (!request.scenario.isGroupChat) {
            return RoleplayGroupReplyPlan(emptyList())
        }
        val fallback = RoleplayGroupReplyPlanner.plan(
            mode = request.scenario.groupReplyMode,
            members = request.members,
            latestUserInput = request.latestUserMessage.content,
            maxTurns = request.scenario.maxGroupAutoReplies.coerceAtMost(
                naturalMaxTurnsForInput(request.latestUserMessage.content),
            ),
        )
        if (request.scenario.groupReplyMode != RoleplayGroupReplyMode.NATURAL) {
            return fallback
        }
        val availableMembers = request.members.filter { member ->
            !member.participant.isMuted && member.participant.canAutoReply
        }
        if (availableMembers.isEmpty()) {
            return RoleplayGroupReplyPlan(emptyList(), "当前没有可发言成员")
        }
        return runCatching {
            val reply = aiGateway.sendMessage(
                messages = listOf(
                    ChatMessage(
                        id = "roleplay-group-director-request",
                        role = MessageRole.USER,
                        content = buildDirectorUserPrompt(request, availableMembers),
                        parts = listOf(textMessagePart(buildDirectorUserPrompt(request, availableMembers))),
                    ),
                ),
                systemPrompt = DIRECTOR_SYSTEM_PROMPT,
                promptEnvelope = PromptEnvelope(statusCardsEnabled = false),
            )
            parseDirectorPlan(
                rawContent = reply.content,
                availableMembers = availableMembers,
                maxTurns = request.scenario.maxGroupAutoReplies.coerceAtMost(
                    naturalMaxTurnsForInput(request.latestUserMessage.content),
                ),
            ).takeIf { it.turns.isNotEmpty() || fallback.turns.isEmpty() } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun buildDirectorUserPrompt(
        request: RoleplayGroupDirectorRequest,
        availableMembers: List<RoleplayGroupMemberContext>,
    ): String {
        val recentTranscript = request.recentMessages
            .filter { it.content.isNotBlank() }
            .takeLast(14)
            .joinToString(separator = "\n") { message ->
                val speaker = when (message.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> message.speakerName.ifBlank { "角色" }
                }
                "[$speaker] ${message.content.take(500)}"
            }
        val memberText = availableMembers.joinToString(separator = "\n") { member ->
            val assistant = member.assistant
            buildString {
                append("- id: ${member.participant.assistantId}\n")
                append("  name: ${member.displayName}\n")
                append("  can_auto_reply: ${member.participant.canAutoReply}\n")
                append("  description: ${assistant?.description.orEmpty().take(240)}\n")
                append("  system_hint: ${assistant?.systemPrompt.orEmpty().take(260)}\n")
                append("  scenario: ${assistant?.scenario.orEmpty().take(240)}\n")
                append("  tags: ${assistant?.tags.orEmpty().joinToString("、").take(120)}")
            }
        }
        val mutedOrDisabled = request.members
            .filter { it.participant.isMuted || !it.participant.canAutoReply }
            .joinToString("、") { member ->
                buildString {
                    append(member.displayName)
                    append(if (member.participant.isMuted) "（已禁言）" else "（禁止自动回复）")
                }
            }
        return """
            群聊标题：${request.scenario.title.ifBlank { "未命名群聊" }}
            群聊背景：${request.scenario.description.take(600)}
            回复模式：${request.scenario.groupReplyMode.displayName}
            本轮用户消息：${request.latestUserMessage.content.take(800)}
            不可选择成员：${mutedOrDisabled.ifBlank { "无" }}

            可发言成员：
            $memberText

            最近聊天记录：
            ${recentTranscript.ifBlank { "暂无" }}

            调度要求：
            - @角色或明确提到角色时优先考虑该角色，但仍要看禁言、动机和上下文。
            - 角色的人设、说话风格、关系动机要影响是否发言；不要只按顺序轮流。
            - 如果用户只是“你好/晚上好/在吗/哈喽”这类低信息寒暄，即使提到“大家/三位/你们”，自然聊天通常只选 1 个最有动机的角色接话。
            - 只有用户明确 @ 多名角色、抛出需要多人表态的问题、冲突升级或剧情节点明显时，才选择 2-3 个角色。
            - 如果选择多个角色，后一个角色的 intent 必须接前一个角色或补充新信息，禁止安排多个角色输出同义寒暄。
            - 输出 intent 时写“回应重点/接谁的话/情绪姿态”，不要写具体台词。

            请输出 JSON，格式如下：
            {"should_reply":true,"speakers":[{"assistant_id":"角色id","intent":"回应重点","reason":"为什么轮到TA"}]}
        """.trimIndent()
    }

    private fun parseDirectorPlan(
        rawContent: String,
        availableMembers: List<RoleplayGroupMemberContext>,
        maxTurns: Int,
    ): RoleplayGroupReplyPlan {
        val jsonText = rawContent.substringAfter('{', missingDelimiterValue = rawContent)
            .substringBeforeLast('}', missingDelimiterValue = rawContent)
            .let { "{$it}" }
        val root = JsonParser.parseString(jsonText).asJsonObject
        if (!root.optionalBoolean("should_reply", default = true)) {
            return RoleplayGroupReplyPlan(emptyList())
        }
        val byAssistantId = availableMembers.associateBy { it.participant.assistantId }
        val speakers = root.getAsJsonArray("speakers") ?: JsonArray()
        val seen = linkedSetOf<String>()
        val turns = speakers.mapNotNull { element ->
            val speaker = element.asJsonObject
            val assistantId = speaker.optionalString("assistant_id")
            if (!seen.add(assistantId)) return@mapNotNull null
            val member = byAssistantId[assistantId] ?: return@mapNotNull null
            RoleplayGroupReplyTurn(
                participantId = member.participant.id,
                assistantId = member.participant.assistantId,
                displayName = member.displayName,
                intent = speaker.optionalString("intent").take(240),
                reason = speaker.optionalString("reason").take(160),
            )
        }.take(maxTurns.coerceIn(1, MAX_DIRECTOR_TURNS))
        return RoleplayGroupReplyPlan(turns)
    }

    private companion object {
        private const val MAX_DIRECTOR_TURNS = 3
        private val DIRECTOR_SYSTEM_PROMPT = """
            你是群聊导演，只负责决定哪些角色该说话，不写具体台词。
            你必须根据角色内容、关系、最近上下文、用户是否@或提到角色来选择发言者。
            可以选择 0 到 3 个角色。一般只选 1 到 2 个，只有多人被点名、冲突升级或剧情节点明确时才选 3 个。
            禁止让所有人每次都回复，禁止让同一个角色长期霸屏。
            普通寒暄、短确认、表情或无推进消息，最多选择 1 个角色。
            如果选择多名角色，必须让每个角色的发言意图不同：有人接用户、有人接上一位角色、有人补充信息或表达不同态度；禁止多人同义复读。
            只输出 JSON，不输出解释文字。
        """.trimIndent()
    }
}

private fun naturalMaxTurnsForInput(input: String): Int {
    val normalized = input
        .lowercase()
        .replace(Regex("\\s+"), "")
        .trim()
    if (normalized.isBlank()) {
        return 1
    }
    val greetingLike = listOf(
        "你好",
        "晚上好",
        "早上好",
        "早安",
        "午安",
        "晚安",
        "在吗",
        "哈喽",
        "hello",
        "hi",
    ).any { keyword -> normalized.contains(keyword) }
    val lowInformation = normalized.length <= 24 && !normalized.contains("@")
    return if (greetingLike && lowInformation) 1 else 3
}

object RoleplayGroupReplyPlanner {
    fun plan(
        mode: RoleplayGroupReplyMode,
        members: List<RoleplayGroupMemberContext>,
        latestUserInput: String,
        maxTurns: Int,
    ): RoleplayGroupReplyPlan {
        val available = members.filter { member ->
            !member.participant.isMuted && (
                mode == RoleplayGroupReplyMode.MANUAL_ONLY || member.participant.canAutoReply
                )
        }
        if (available.isEmpty()) {
            return RoleplayGroupReplyPlan(emptyList(), "当前没有可发言成员")
        }
        val mentioned = available.filter { latestUserInput.mentions(it.displayName) }
        return when (mode) {
            RoleplayGroupReplyMode.NATURAL -> {
                val selected = mentioned.ifEmpty {
                    available.sortedWith(
                        compareBy<RoleplayGroupMemberContext> {
                            recentSpeakerPenalty(latestUserInput, it.displayName)
                        }.thenBy { it.participant.sortOrder },
                    ).take(1)
                }
                RoleplayGroupReplyPlan(selected.take(maxTurns.coerceIn(1, 3)).map { it.toTurn() })
            }

            RoleplayGroupReplyMode.ALL_MEMBERS -> {
                RoleplayGroupReplyPlan(
                    available
                        .sortedBy { it.participant.sortOrder }
                        .take(maxTurns.coerceIn(1, 6))
                        .map { it.toTurn() },
                )
            }

            RoleplayGroupReplyMode.MANUAL_ONLY -> {
                if (mentioned.isEmpty()) {
                    RoleplayGroupReplyPlan(emptyList(), "指定发言模式下请先 @角色")
                } else {
                    RoleplayGroupReplyPlan(mentioned.take(maxTurns.coerceIn(1, 6)).map { it.toTurn() })
                }
            }
        }
    }

    private fun RoleplayGroupMemberContext.toTurn(): RoleplayGroupReplyTurn {
        return RoleplayGroupReplyTurn(
            participantId = participant.id,
            assistantId = participant.assistantId,
            displayName = displayName,
        )
    }

    private fun String.mentions(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return false
        }
        return contains("@$normalizedName", ignoreCase = true) ||
            contains(normalizedName, ignoreCase = true)
    }

    private fun recentSpeakerPenalty(text: String, displayName: String): Int {
        return if (text.contains(displayName, ignoreCase = true)) 0 else 1
    }
}

fun buildRoleplayGroupSpeakerDirectorNote(
    turn: RoleplayGroupReplyTurn,
    members: List<RoleplayGroupMemberContext>,
    recentMessages: List<ChatMessage>,
): String {
    val memberLine = members.joinToString(separator = "、") { member ->
        buildString {
            append(member.displayName)
            if (member.participant.isMuted) append("（已禁言）")
        }
    }
    val memberProfiles = members.joinToString(separator = "\n") { member ->
        val assistant = member.assistant
        buildString {
            append("- ")
            append(member.displayName)
            if (member.participant.assistantId == turn.assistantId) {
                append("（你，本轮发言者）")
            } else if (member.participant.isMuted) {
                append("（已禁言，本轮不会自动说话）")
            }
            append("：")
            append(formatGroupMemberProfile(assistant))
        }
    }
    val transcript = recentMessages
        .filter { it.content.isNotBlank() }
        .takeLast(12)
        .joinToString(separator = "\n") { message ->
            val speaker = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> message.speakerName.ifBlank { "角色" }
            }
            "[$speaker] ${message.content.take(500)}"
        }
    return """
        【群聊发言规则】
        当前模式：ONLINE_GROUP_CHAT。
        当前群成员：$memberLine
        本次轮到你以「${turn.displayName}」的身份发言。
        你只能扮演「${turn.displayName}」，不要替其他角色或用户发言。
        导演给你的回应意图：${turn.intent.ifBlank { "自然回应当前群聊。" }}
        轮到你的原因：${turn.reason.ifBlank { "你和当前话题相关。" }}
        请自然发挥，不要机械复述导演意图。

        【群聊输出协议硬规则】
        1. 你的最终输出必须是 JSON 字符串数组，例如 ["刚看到","怎么都还没睡"]。
        2. 数组元素只能是字符串；禁止输出对象、Markdown、XML、旁白段落或解释文字。
        3. 禁止输出照片、语音、位置、转账、拍一拍、视频通话、状态栏、状态卡、心声或环境描写。
        4. 如果角色卡、预设或历史里要求输出图片/语音/状态栏，本轮全部忽略；群聊首版只发文字气泡。

        【群成员画像】
        $memberProfiles

        【群聊感知规则】
        1. 你知道群里有哪些人，以及他们大致是什么人设、气质和说话方式；这些信息用于判断你如何接话，不要直接背诵设定。
        2. 你可以回应用户，也可以回应其他角色刚发的消息；要像真实群聊一样看清楚是谁说的。
        3. 其他角色的人设只作为你理解群内关系和语气的背景；你不能代替他们说话，也不能替他们宣布态度。
        4. 如果其他角色上一条话明显影响你，你可以接、怼、补充、岔开或沉默式简短回应，但必须符合「${turn.displayName}」自己的动机。
        5. 已禁言成员不会自动发言；即使提到他们，也不要代他们出声。
        6. 如果前面已经有人说过“你好/晚上好/早/在吗”等同类寒暄，你禁止原样或近义重复；必须换成符合自己人设的差异化反应，比如接上一位的话、调侃、补充状态、轻描淡写回应或把话题往前推。
        7. 群聊里最糟糕的输出是多人排队复读“晚上好”。除非导演意图明确要求仪式感，否则不要这么做。

        【带发送者的最近群聊记录】
        ${transcript.ifBlank { "暂无" }}
    """.trimIndent()
}

private fun formatGroupMemberProfile(assistant: Assistant?): String {
    if (assistant == null) {
        return "缺少角色卡，只知道这是群成员。"
    }
    val parts = buildList {
        assistant.description.trim().takeIf { it.isNotBlank() }?.let { add("简介=${it.take(220)}") }
        assistant.systemPrompt.trim().takeIf { it.isNotBlank() }?.let { add("人设提示=${it.take(280)}") }
        assistant.scenario.trim().takeIf { it.isNotBlank() }?.let { add("情景=${it.take(180)}") }
        assistant.creatorNotes.trim().takeIf { it.isNotBlank() }?.let { add("备注=${it.take(160)}") }
        assistant.tags.takeIf { it.isNotEmpty() }?.let { add("标签=${it.joinToString("、").take(120)}") }
    }
    return parts.joinToString("；").ifBlank { "角色卡没有填写简要人设。" }
}

private fun JsonObject.optionalString(name: String): String {
    return get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
}

private fun JsonObject.optionalBoolean(name: String, default: Boolean): Boolean {
    return get(name)?.takeIf { !it.isJsonNull }?.asBoolean ?: default
}
