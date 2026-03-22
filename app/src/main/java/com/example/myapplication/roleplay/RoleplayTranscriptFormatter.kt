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
                val content = message.parts.toPlainText()
                    .ifBlank { message.content }
                    .trim()
                if (content.isBlank()) {
                    emptyList()
                } else {
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
