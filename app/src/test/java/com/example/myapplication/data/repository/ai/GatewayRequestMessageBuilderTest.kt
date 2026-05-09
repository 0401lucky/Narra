package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptHistoryInjection
import com.example.myapplication.model.PromptMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRequestMessageBuilderTest {
    @Test
    fun build_insertsHistoryInjectionsByDepth() = runTest {
        val messages = listOf(
            ChatMessage(id = "m1", role = MessageRole.USER, content = "第一句"),
            ChatMessage(id = "m2", role = MessageRole.ASSISTANT, content = "第二句"),
            ChatMessage(id = "m3", role = MessageRole.USER, content = "第三句"),
        )
        val envelope = PromptEnvelope(
            historyInjections = listOf(
                PromptHistoryInjection(
                    role = PresetPromptRole.SYSTEM,
                    content = "插在最新历史前",
                    depth = 1,
                    order = 200,
                    sourceTitle = "Depth 1",
                ),
                PromptHistoryInjection(
                    role = PresetPromptRole.USER,
                    content = "插在历史末尾",
                    depth = 0,
                    order = 100,
                    sourceTitle = "Depth 0",
                ),
            ),
        )

        val requestMessages = GatewayRequestMessageBuilder.build(
            messages = messages,
            systemPrompt = "系统提示",
            promptMode = PromptMode.CHAT,
            promptEnvelope = envelope,
            imagePayloadResolver = { "" },
            filePromptResolver = { "" },
        )

        val contents = requestMessages.map { it.content.toString() }
        assertTrue(contents.first().contains("系统提示"))
        assertEquals(
            listOf(
                "第一句",
                "第二句",
                "插在最新历史前",
                "第三句",
                "插在历史末尾",
            ),
            contents.drop(1),
        )
        assertEquals("system", requestMessages[3].role)
        assertEquals("user", requestMessages[5].role)
    }
}
