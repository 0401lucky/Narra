package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneViewMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.shouldInjectDescriptionPrompt
import com.example.myapplication.model.toPlainText
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter

data class PhoneGenerationContext(
    val ownerType: PhoneSnapshotOwnerType,
    val viewerType: PhoneSnapshotOwnerType,
    val viewMode: PhoneViewMode,
    val ownerName: String,
    val viewerName: String,
    val userName: String,
    val assistantName: String,
    val relationshipDirection: String,
    val timeGapContext: String,
    val promptMode: PromptMode,
    val systemContext: String,
    val scenarioContext: String,
    val conversationExcerpt: String,
)

class PhoneContextBuilder(
    private val promptContextAssembler: PromptContextAssembler,
) {
    suspend fun build(
        settings: AppSettings,
        assistant: Assistant?,
        conversation: Conversation,
        recentMessages: List<ChatMessage>,
        scenario: RoleplayScenario? = null,
        ownerType: PhoneSnapshotOwnerType = PhoneSnapshotOwnerType.CHARACTER,
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): PhoneGenerationContext {
        val completedMessages = recentMessages.filter { message ->
            message.status == MessageStatus.COMPLETED && message.hasSendableContent()
        }
        val promptMessages = completedMessages.takeLast(
            if (scenario != null) PHONE_PROMPT_ROLEPLAY_MESSAGE_LIMIT else PHONE_PROMPT_CHAT_MESSAGE_LIMIT,
        )
        val promptMode = if (scenario != null) PromptMode.ROLEPLAY else PromptMode.CHAT
        val compactAssistant = assistant?.copy(
            memoryMaxItems = assistant.memoryMaxItems.coerceAtMost(PHONE_PROMPT_MEMORY_MAX_ITEMS),
            worldBookMaxEntries = assistant.worldBookMaxEntries.coerceAtMost(PHONE_PROMPT_WORLD_BOOK_MAX_ITEMS),
        )
        val promptSettings = scenario?.let { RoleplayConversationSupport.resolvePromptSettings(it, settings) }
            ?: settings
        val promptAssistant = scenario?.let {
            RoleplayConversationSupport.resolvePromptAssistant(it, compactAssistant)
        } ?: compactAssistant
        val assembledContext = promptContextAssembler.assemble(
            settings = promptSettings,
            assistant = promptAssistant,
            conversation = conversation,
            userInputText = "",
            recentMessages = promptMessages,
            promptMode = promptMode,
            includePhoneSnapshot = false,
        )
        return if (scenario != null) {
            val userName = RoleplayConversationSupport.resolveUserPersona(scenario, settings).displayName
            val assistantName = scenario.characterDisplayNameOverride.trim()
                .ifBlank { assistant?.name?.trim().orEmpty() }
                .ifBlank { "角色" }
            val (resolvedOwnerName, viewerName, viewMode, relationshipDirection) = when (ownerType) {
                PhoneSnapshotOwnerType.CHARACTER -> {
                    Quadruple(
                        assistantName,
                        userName,
                        PhoneViewMode.USER_LOOKS_CHARACTER_PHONE,
                        "用户正在查看角色的私人手机内容",
                    )
                }
                PhoneSnapshotOwnerType.USER -> {
                    Quadruple(
                        userName,
                        assistantName,
                        PhoneViewMode.CHARACTER_LOOKS_USER_PHONE,
                        "角色正在查看用户的私人手机内容；内容主体必须来自用户本人，但可优先保留最能触发角色反应的线索",
                    )
                }
            }
            PhoneGenerationContext(
                ownerType = ownerType,
                viewerType = if (ownerType == PhoneSnapshotOwnerType.CHARACTER) PhoneSnapshotOwnerType.USER else PhoneSnapshotOwnerType.CHARACTER,
                viewMode = viewMode,
                ownerName = resolvedOwnerName,
                viewerName = viewerName,
                userName = userName,
                assistantName = assistantName,
                relationshipDirection = relationshipDirection,
                timeGapContext = buildTimeGapContext(
                    messages = completedMessages,
                    nowProvider = nowProvider,
                ),
                promptMode = PromptMode.ROLEPLAY,
                systemContext = assembledContext.systemPrompt,
                scenarioContext = buildString {
                    if (scenario.title.isNotBlank()) {
                        append("场景标题：")
                        append(scenario.title.trim())
                        append('\n')
                    }
                    if (scenario.shouldInjectDescriptionPrompt()) {
                        append("聊天背景补充：")
                        append(scenario.description.trim())
                        append('\n')
                    }
                    if (scenario.openingNarration.isNotBlank()) {
                        append(
                            if (scenario.interactionMode == com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE) {
                                "开场旁白："
                            } else {
                                "开场旁白："
                            },
                        )
                        append(scenario.openingNarration.trim())
                    }
                }.trim(),
                conversationExcerpt = RoleplayTranscriptFormatter.formatMessages(
                    messages = promptMessages,
                    userName = userName,
                    characterName = assistantName,
                    allowNarration = scenario.enableNarration,
                    interactionMode = scenario.interactionMode,
                ),
            )
        } else {
            val userName = settings.resolvedUserDisplayName()
            val assistantName = assistant?.name?.trim().orEmpty().ifBlank { "对方" }
            val (resolvedOwnerName, viewerName, viewMode, relationshipDirection) = when (ownerType) {
                PhoneSnapshotOwnerType.CHARACTER -> {
                    Quadruple(
                        assistantName,
                        userName,
                        PhoneViewMode.USER_LOOKS_CHARACTER_PHONE,
                        "用户正在查看对方的手机内容",
                    )
                }
                PhoneSnapshotOwnerType.USER -> {
                    Quadruple(
                        userName,
                        assistantName,
                        PhoneViewMode.CHARACTER_LOOKS_USER_PHONE,
                        "对方正在查看用户的手机内容；内容主体必须来自用户本人，但可优先保留最能触发对方反应的线索",
                    )
                }
            }
            PhoneGenerationContext(
                ownerType = ownerType,
                viewerType = if (ownerType == PhoneSnapshotOwnerType.CHARACTER) PhoneSnapshotOwnerType.USER else PhoneSnapshotOwnerType.CHARACTER,
                viewMode = viewMode,
                ownerName = resolvedOwnerName,
                viewerName = viewerName,
                userName = userName,
                assistantName = assistantName,
                relationshipDirection = relationshipDirection,
                timeGapContext = buildTimeGapContext(
                    messages = completedMessages,
                    nowProvider = nowProvider,
                ),
                promptMode = PromptMode.CHAT,
                systemContext = assembledContext.systemPrompt,
                scenarioContext = "",
                conversationExcerpt = promptMessages
                    .joinToString(separator = "\n") { message ->
                        val speaker = if (message.role == MessageRole.USER) userName else assistantName
                        "$speaker：${message.parts.toPlainText().ifBlank { message.content }.trim()}"
                    }
                    .trim(),
            )
        }
    }

    private fun buildTimeGapContext(
        messages: List<ChatMessage>,
        nowProvider: () -> Long,
    ): String {
        val latestTimestamp = messages.maxOfOrNull { it.createdAt }
            ?.takeIf { it > 0L }
            ?: return ""
        val gapMillis = (nowProvider() - latestTimestamp).coerceAtLeast(0L)
        val hours = gapMillis / (60 * 60 * 1000)
        val days = gapMillis / (24 * 60 * 60 * 1000)
        return when {
            gapMillis < 6 * 60 * 60 * 1000L -> "距离上一轮互动不到 6 小时。"
            gapMillis < 24 * 60 * 60 * 1000L -> "距离上一轮互动约 $hours 小时，属于短暂间隔。"
            gapMillis < 3 * 24 * 60 * 60 * 1000L -> "距离上一轮互动约 $days 天，属于明显失联。"
            gapMillis < 14 * 24 * 60 * 60 * 1000L -> "距离上一轮互动已超过 $days 天，角色可能会对久未联系产生明显反应。"
            else -> "距离上一轮互动已超过 $days 天，属于强烈断联，角色可按人设表现压抑、埋怨、冷淡或失控的情绪。"
        }
    }

    fun buildSearchDetailContext(
        snapshot: PhoneSnapshot,
        query: String,
    ): String {
        return buildString {
            if (snapshot.relationshipHighlights.isNotEmpty()) {
                appendLine("【关系速览】")
                snapshot.relationshipHighlights.take(5).forEach { item ->
                    append("- ")
                    append(item.name)
                    if (item.relationLabel.isNotBlank()) {
                        append("（")
                        append(item.relationLabel)
                        append("）")
                    }
                    if (item.note.isNotBlank()) {
                        append("：")
                        append(item.note)
                    }
                    appendLine()
                }
            }
            snapshot.notes.firstOrNull { entry -> entry.title.contains(query, ignoreCase = true) }
                ?.let { note ->
                    appendLine("【相关备忘录】")
                    appendLine(note.title)
                    appendLine(note.summary)
                }
            snapshot.shoppingRecords.firstOrNull { entry -> entry.title.contains(query, ignoreCase = true) }
                ?.let { record ->
                    appendLine("【相关购物记录】")
                    appendLine(record.title)
                    appendLine(record.note)
                }
            snapshot.messageThreads.firstOrNull { thread ->
                thread.contactName.contains(query, ignoreCase = true) ||
                    thread.preview.contains(query, ignoreCase = true)
            }?.let { thread ->
                appendLine("【相关消息】")
                appendLine("${thread.contactName}：${thread.preview}")
            }
        }.trim()
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private companion object {
        const val PHONE_PROMPT_CHAT_MESSAGE_LIMIT = 6
        const val PHONE_PROMPT_ROLEPLAY_MESSAGE_LIMIT = 8
        const val PHONE_PROMPT_MEMORY_MAX_ITEMS = 3
        const val PHONE_PROMPT_WORLD_BOOK_MAX_ITEMS = 4
    }
}
