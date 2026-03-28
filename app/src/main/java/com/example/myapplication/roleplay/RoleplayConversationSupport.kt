package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.toPlainText

object RoleplayConversationSupport {
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
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
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
                    message.parts.toPlainText()
                        .ifBlank { message.content },
                ).trim()
                plainText.takeIf { it.isNotBlank() }?.take(10)
            }
            .distinct()
        val recentEmotions = messages
            .filter { it.role == MessageRole.ASSISTANT }
            .takeLast(3)
            .flatMap { message ->
                outputParser.parseAssistantOutput(
                    rawContent = message.parts.toPlainText().ifBlank { message.content },
                    characterName = characterName,
                    allowNarration = scenario.enableNarration,
                ).mapNotNull { segment ->
                    segment.emotion.trim().takeIf { it.isNotBlank() }
                }
            }
            .distinct()
        return buildString {
            if (recentUserInput.isNotBlank()) {
                append("优先回应 ")
                append(userName.ifBlank { "玩家" })
                append(" 刚刚提到的具体细节：")
                append(recentUserInput.take(80))
                append("。\n")
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
            append("这一轮至少推进一项：关系、信息或局势。")
        }.trim()
    }
}
