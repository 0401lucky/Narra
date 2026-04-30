package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextGovernanceSupportTest {
    @Test
    fun buildActualPromptDebugDump_keepsOnlyActualPromptBody() {
        val debugDump = ContextGovernanceSupport.buildActualPromptDebugDump(
            promptContextDebugDump = """
                【上下文调试】
                - 记忆注入数：1

                旧系统提示词不应出现在复制原文里。
            """.trimIndent(),
            actualSystemPrompt = "最终系统提示词",
            hasSummary = true,
            coveredMessageCount = 8,
            completedMessageCount = 10,
            triggerMessageCount = 4,
        )

        assertTrue(debugDump.contains("【上下文调试】"))
        assertTrue(debugDump.contains("【实际发送系统提示词】"))
        assertTrue(debugDump.contains("最终系统提示词"))
        assertTrue(debugDump.contains("覆盖 8 条"))
        assertFalse(debugDump.contains("旧系统提示词不应出现在复制原文里。"))
    }

    @Test
    fun buildSnapshot_allowsManualRefreshWhenMessagesExist() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val snapshot = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            assistant = null,
            promptMode = PromptMode.CHAT,
            selectedModel = "chat-model",
            requestMessages = listOf(message("m1"), message("m2"), message("m3")),
            effectiveRequestMessages = listOf(message("m1"), message("m2"), message("m3")),
            promptContext = PromptContextResult(systemPrompt = "prompt"),
            completedMessageCount = 3,
            triggerMessageCount = 4,
            recentWindow = 2,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
        )

        assertTrue(snapshot.hasActionableSummaryRefresh)
        assertTrue(snapshot.summaryState == ContextSummaryState.READY_IDLE)
    }

    @Test
    fun buildSnapshot_requiresToolAbilityBeforeShowingRoleplayMemoryWrite() {
        val providerWithoutTool = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "rp-model",
            titleSummaryModel = "summary-model",
            models = listOf(
                ModelInfo(
                    modelId = "rp-model",
                    abilities = emptySet(),
                    abilitiesCustomized = true,
                ),
            ),
        )
        val assistant = Assistant(
            id = "assistant-1",
            memoryEnabled = true,
        )

        val snapshot = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(providerWithoutTool),
                selectedProviderId = providerWithoutTool.id,
            ),
            assistant = assistant,
            promptMode = PromptMode.ROLEPLAY,
            selectedModel = "rp-model",
            requestMessages = listOf(message("m1"), message("m2"), message("m3"), message("m4"), message("m5")),
            effectiveRequestMessages = listOf(message("m4"), message("m5")),
            promptContext = PromptContextResult(systemPrompt = "prompt"),
            completedMessageCount = 5,
            triggerMessageCount = 2,
            recentWindow = 2,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions.localContextOnly(
                com.example.myapplication.model.GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = assistant,
                    conversation = com.example.myapplication.model.Conversation(
                        id = "c1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                    userInputText = "hi",
                    recentMessages = emptyList(),
                ),
            ),
            rawDebugDump = "debug",
        )

        assertFalse("写入记忆" in snapshot.enabledTools)
        assertTrue(snapshot.hasActionableSummaryRefresh)
    }

    @Test
    fun buildSnapshot_marksSummaryAsStaleWhenCoverageCannotReplaceOlderMessages() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val requestMessages = (1..10).map { message("m$it") }

        val snapshot = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            assistant = null,
            promptMode = PromptMode.CHAT,
            selectedModel = "chat-model",
            requestMessages = requestMessages,
            effectiveRequestMessages = requestMessages,
            promptContext = PromptContextResult(
                systemPrompt = "prompt",
                summaryCoveredMessageCount = 3,
            ),
            completedMessageCount = requestMessages.size,
            triggerMessageCount = 4,
            recentWindow = 4,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
        )

        assertTrue(snapshot.summaryState == ContextSummaryState.STALE)
    }

    private fun message(id: String): ChatMessage {
        return ChatMessage(
            id = id,
            conversationId = "c1",
            role = MessageRole.USER,
            content = id,
        )
    }
}
