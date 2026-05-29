package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
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

    @Test
    fun buildSnapshot_addsContextTrimmingSection() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val requestMessages = (1..10).map { message("m$it") }
        val effectiveMessages = requestMessages.takeLast(4)

        val snapshot = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            assistant = null,
            promptMode = PromptMode.CHAT,
            selectedModel = "chat-model",
            requestMessages = requestMessages,
            effectiveRequestMessages = effectiveMessages,
            promptContext = PromptContextResult(
                systemPrompt = "prompt",
                summaryCoveredMessageCount = 6,
            ),
            completedMessageCount = requestMessages.size,
            triggerMessageCount = 4,
            recentWindow = 4,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
        )

        val trimmingSection = snapshot.contextSections.firstOrNull { section ->
            section.sourceType == ContextLogSourceType.SUMMARY && section.title == "上下文裁剪"
        } ?: error("未找到上下文裁剪日志")
        assertTrue(trimmingSection.content.contains("裁剪前消息数：10"))
        assertTrue(trimmingSection.content.contains("实际发送原文消息数：4"))
        assertTrue(trimmingSection.content.contains("旧消息已由摘要覆盖，未作为原文发送：是"))
    }

    @Test
    fun buildSnapshot_marksMissingSummaryCoverageAsStaleAndKeepsAllHistorySections() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val requestMessages = (1..6).map { message("m$it") }
        val promptContext = PromptContextResult(
            systemPrompt = "prompt",
            contextSections = requestMessages.map { message ->
                ContextLogSection(
                    sourceType = ContextLogSourceType.CHAT_HISTORY,
                    title = "聊天历史",
                    content = message.content,
                )
            },
        )

        val snapshot = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            assistant = null,
            promptMode = PromptMode.ROLEPLAY,
            selectedModel = "chat-model",
            requestMessages = requestMessages,
            effectiveRequestMessages = requestMessages,
            promptContext = promptContext,
            completedMessageCount = requestMessages.size,
            triggerMessageCount = 4,
            recentWindow = 4,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
        )

        val chatHistoryContent = snapshot.contextSections
            .filter { it.sourceType == ContextLogSourceType.CHAT_HISTORY }
            .joinToString(separator = "\n") { it.content }

        assertTrue(snapshot.summaryState == ContextSummaryState.STALE)
        assertTrue(chatHistoryContent.contains("m1"))
        assertTrue(chatHistoryContent.contains("m6"))
        assertTrue(snapshot.sentMessageCount == requestMessages.size)
    }

    @Test
    fun buildSnapshot_appendsRuntimeDecorationSectionAndCountsItsTokens() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "rp-model",
            titleSummaryModel = "summary-model",
        )
        val requestMessages = (1..4).map { message("m$it") }
        val decorationSection = ContextLogSection(
            sourceType = ContextLogSourceType.SYSTEM_RULE,
            title = "运行时导演注记",
            content = "【本轮导演提示】\n优先接住她刚刚的逼问，再推进局势。",
        )

        fun snapshot(extraSections: List<ContextLogSection>) = ContextGovernanceSupport.buildSnapshot(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            assistant = null,
            promptMode = PromptMode.ROLEPLAY,
            selectedModel = "rp-model",
            requestMessages = requestMessages,
            effectiveRequestMessages = requestMessages,
            promptContext = PromptContextResult(systemPrompt = "prompt"),
            completedMessageCount = requestMessages.size,
            triggerMessageCount = 12,
            recentWindow = 4,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
            extraContextSections = extraSections,
        )

        val withDecoration = snapshot(listOf(decorationSection))
        val withoutDecoration = snapshot(emptyList())

        val matchedSection = withDecoration.contextSections.firstOrNull { section ->
            section.title == "运行时导演注记"
        } ?: error("未找到运行时装饰分区")
        assertTrue(matchedSection.content.contains("优先接住她刚刚的逼问"))
        assertTrue(matchedSection.sourceType == ContextLogSourceType.SYSTEM_RULE)
        assertTrue(
            "装饰增量的 token 必须计入估算",
            withDecoration.estimatedContextTokens > withoutDecoration.estimatedContextTokens,
        )
        assertTrue(
            withDecoration.estimatedContextTokens ==
                withoutDecoration.estimatedContextTokens + decorationSection.tokenEstimate,
        )
    }

    @Test
    fun buildSnapshot_withoutExtraSectionsKeepsChatPathUnchanged() {
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val requestMessages = (1..3).map { message("m$it") }
        val promptContext = PromptContextResult(
            systemPrompt = "prompt",
            contextSections = listOf(
                ContextLogSection(
                    sourceType = ContextLogSourceType.SYSTEM_RULE,
                    title = "系统规则",
                    content = "保持友好。",
                ),
            ),
        )

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
            promptContext = promptContext,
            completedMessageCount = requestMessages.size,
            triggerMessageCount = 4,
            recentWindow = 2,
            minCoveredMessageCount = 2,
            toolingOptions = com.example.myapplication.model.GatewayToolingOptions(),
            rawDebugDump = "debug",
        )

        // chat 路径不传装饰增量：分区仅为 promptContext + 上下文裁剪，无额外节。
        assertTrue(snapshot.contextSections.none { it.title == "运行时导演注记" })
        val expectedTokens = (promptContext.contextSections + snapshot.contextSections.last())
            .sumOf { it.tokenEstimate }
        assertTrue(snapshot.estimatedContextTokens == expectedTokens)
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
