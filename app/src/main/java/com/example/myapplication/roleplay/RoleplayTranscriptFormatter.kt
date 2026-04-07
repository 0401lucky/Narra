package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.toPlainText

object RoleplayTranscriptFormatter {
    private val parser = RoleplayOutputParser()

    fun formatMessages(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
    ): String {
        return messages
            .flatMap { message ->
                formatMessage(
                    message = message,
                    userName = userName,
                    characterName = characterName,
                    allowNarration = allowNarration,
                )
            }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun formatMessage(
        message: ChatMessage,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
    ): List<String> {
        return when (message.role) {
            MessageRole.USER -> {
                val content = message.parts.toPlainText()
                    .ifBlank { message.content }
                    .trim()
                if (content.isBlank()) {
                    emptyList()
                } else {
                    listOf("${userName.ifBlank { "用户" }}：$content")
                }
            }

            MessageRole.ASSISTANT -> {
                val content = RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
                if (content.isBlank()) {
                    emptyList()
                } else {
                    when (RoleplayMessageFormatSupport.resolveAssistantMessageOutputFormat(message)) {
                        com.example.myapplication.model.RoleplayOutputFormat.LONGFORM -> {
                            formatLongformMessage(
                                rawContent = content,
                                characterName = characterName,
                            )
                        }

                        else -> {
                            parser.parseAssistantOutput(
                                rawContent = content,
                                characterName = characterName.ifBlank { "角色" },
                                allowNarration = allowNarration,
                            ).mapNotNull { segment ->
                                val prefix = when (segment.speaker) {
                                    RoleplaySpeaker.USER -> userName.ifBlank { "用户" }
                                    RoleplaySpeaker.NARRATOR -> "旁白"
                                    RoleplaySpeaker.SYSTEM -> segment.speakerName.ifBlank { "系统" }
                                    RoleplaySpeaker.CHARACTER -> characterName.ifBlank { "角色" }
                                }
                                segment.content.trim().takeIf { it.isNotBlank() }?.let { readableContent ->
                                    "$prefix：$readableContent"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatLongformMessage(
        rawContent: String,
        characterName: String,
    ): List<String> {
        val normalizedCharacterName = characterName.ifBlank { "角色" }
        return RoleplayLongformMarkupParser.stripMarkupForDisplay(rawContent)
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(RoleplayLongformMarkupParser.stripMarkupForDisplay(rawContent))
            }
            .mapNotNull { paragraph ->
                paragraph.takeIf { it.isNotBlank() }?.let {
                    "$normalizedCharacterName：$it"
                }
            }
    }
}
