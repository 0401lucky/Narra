package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PhoneContextBuilderTest {
    @Test
    fun build_chatContextUsesAssistantAsPhoneOwner() = runBlocking {
        val builder = PhoneContextBuilder(
            promptContextAssembler = object : PromptContextAssembler {
                override suspend fun assemble(
                    settings: AppSettings,
                    assistant: Assistant?,
                    conversation: Conversation,
                    userInputText: String,
                    recentMessages: List<ChatMessage>,
                    promptMode: PromptMode,
                    includePhoneSnapshot: Boolean,
                ): PromptContextResult {
                    return PromptContextResult(systemPrompt = "【助手简介】冷静、克制。")
                }
            },
        )

        val result = builder.build(
            settings = AppSettings(userDisplayName = "lucky"),
            assistant = Assistant(id = "assistant-1", name = "沈砚清"),
            conversation = Conversation(id = "conversation-1", createdAt = 1L, updatedAt = 1L),
            recentMessages = listOf(
                ChatMessage(id = "m1", role = MessageRole.USER, content = "你今天是不是又躲我了？"),
                ChatMessage(id = "m2", role = MessageRole.ASSISTANT, content = "没有，我只是有点忙。"),
            ),
        )

        assertEquals("沈砚清", result.ownerName)
        assertEquals("lucky", result.userName)
        assertTrue(result.systemContext.contains("冷静"))
        assertTrue(result.conversationExcerpt.contains("沈砚清"))
    }

    @Test
    fun build_roleplayContextAppendsScenarioFields() = runBlocking {
        val builder = PhoneContextBuilder(
            promptContextAssembler = object : PromptContextAssembler {
                override suspend fun assemble(
                    settings: AppSettings,
                    assistant: Assistant?,
                    conversation: Conversation,
                    userInputText: String,
                    recentMessages: List<ChatMessage>,
                    promptMode: PromptMode,
                    includePhoneSnapshot: Boolean,
                ): PromptContextResult {
                    return PromptContextResult(systemPrompt = "【角色长期记忆】他对亲密关系很克制。")
                }
            },
        )

        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "深夜书房",
            description = "灯光很暗，空气里有纸张和木头味。",
            assistantId = "assistant-1",
            userDisplayNameOverride = "lucky",
            characterDisplayNameOverride = "沈砚清",
            openingNarration = "你推门进去时，他还没抬头。",
        )
        val result = builder.build(
            settings = AppSettings(),
            assistant = Assistant(id = "assistant-1", name = "沈砚清"),
            conversation = Conversation(id = "conversation-1", createdAt = 1L, updatedAt = 1L),
            recentMessages = listOf(
                ChatMessage(id = "m1", role = MessageRole.USER, content = "你是不是还在躲我？"),
            ),
            scenario = scenario,
        )

        assertEquals("沈砚清", result.ownerName)
        assertTrue(result.scenarioContext.contains("深夜书房"))
        assertTrue(result.scenarioContext.contains("你推门进去时"))
        assertEquals(PromptMode.ROLEPLAY, result.promptMode)
    }
}
