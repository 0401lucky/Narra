package com.example.myapplication.system.performance

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class F3BenchmarkAiDependencyPolicyTest {
    @Test
    fun resolve_f3Session_doesNotEvaluateRealProviders() {
        val dependencies = F3BenchmarkAiDependencyPolicy.resolve(
            runtimePolicy = runtimePolicy(BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY),
            aiGatewayProvider = { error("不应求值真实 aiGateway") },
            aiPromptExtrasServiceProvider = { error("不应求值真实 aiPromptExtrasService") },
        )

        assertTrue(dependencies.violationTracker?.hasNoViolations() == true)
    }

    @Test
    fun resolve_nonF3OrNull_evaluatesAndReturnsRealProviders() {
        val gateway = TestAiGateway()
        val promptExtras = java.lang.reflect.Proxy.newProxyInstance(
            AiPromptExtrasService::class.java.classLoader,
            arrayOf(AiPromptExtrasService::class.java),
        ) { _, _, _ -> error("本测试不会调用真实 prompt extras") } as AiPromptExtrasService

        listOf(
            runtimePolicy(BenchmarkScenarioId.F2_ROLEPLAY_SCROLL),
            null,
        ).forEach { policy ->
            val dependencies = F3BenchmarkAiDependencyPolicy.resolve(
                runtimePolicy = policy,
                aiGatewayProvider = { gateway },
                aiPromptExtrasServiceProvider = { promptExtras },
            )

            assertSame(gateway, dependencies.aiGateway)
            assertSame(promptExtras, dependencies.aiPromptExtrasService)
            assertNull(dependencies.violationTracker)
        }
    }

    @Test
    fun f3Gateway_parseRemainsLocal_butExternalCallsCountAndThrow() = runTest {
        val dependencies = F3BenchmarkAiDependencyPolicy.resolve(
            runtimePolicy = runtimePolicy(BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY),
            aiGatewayProvider = { error("不应求值") },
            aiPromptExtrasServiceProvider = { error("不应求值") },
        )

        val parsed = dependencies.aiGateway.parseAssistantSpecialOutput(
            content = "正文<play id=\"gift-1\" type=\"gift\" target=\"用户\" item=\"花\" note=\"\" />",
            existingParts = emptyList(),
        )
        assertEquals("正文", parsed.content)
        assertTrue(dependencies.violationTracker?.hasNoViolations() == true)

        val thrown = runCatching {
            dependencies.aiGateway.sendMessage(emptyList())
        }.exceptionOrNull()
        assertTrue(thrown is BenchmarkAiCallBlockedException)
        assertEquals(1, dependencies.violationTracker?.snapshot()?.violationCount)

        val streamThrown = runCatching {
            dependencies.aiGateway.sendMessageStream(emptyList())
        }.exceptionOrNull()
        assertTrue(streamThrown is BenchmarkAiCallBlockedException)
        assertEquals(2, dependencies.violationTracker?.snapshot()?.violationCount)

        val promptThrown = runCatching {
            dependencies.aiPromptExtrasService.generateRoleplaySuggestions(
                conversationExcerpt = "x",
                systemPrompt = "",
                playerStyleReference = "",
                baseUrl = "",
                apiKey = "",
                modelId = "",
            )
        }.exceptionOrNull()
        assertTrue(promptThrown is BenchmarkAiCallBlockedException)
        assertEquals(3, dependencies.violationTracker?.snapshot()?.violationCount)
        assertFalse(dependencies.violationTracker?.hasNoViolations() == true)
    }

    private fun runtimePolicy(scenarioId: BenchmarkScenarioId): BenchmarkRuntimePolicy {
        return BenchmarkRuntimePolicy.resolve(
            appChannel = BenchmarkContract.BASELINE_CHANNEL,
            descriptor = BenchmarkSessionDescriptor(
                schemaVersion = BenchmarkContract.SCHEMA_VERSION,
                sessionId = "session-1234567890",
                scenarioId = scenarioId,
                fixtureVersion = BenchmarkContract.FIXTURE_VERSION,
                phase = BenchmarkPhase.MEASURE,
                createdAtEpochMillis = 1_000L,
                ttlMillis = BenchmarkContract.DEFAULT_TTL_MILLIS,
            ),
            requestedSessionId = "session-1234567890",
            nowEpochMillis = 1_000L,
        )
    }

    private class TestAiGateway : AiGateway {
        override suspend fun generateImage(prompt: String, modelId: String): List<ImageGenerationResult> = emptyList()
        override suspend fun sendMessage(messages: List<ChatMessage>, systemPrompt: String, promptEnvelope: PromptEnvelope, toolingOptions: GatewayToolingOptions): AssistantReply = error("不调用")
        override fun sendMessageStream(messages: List<ChatMessage>, systemPrompt: String, promptMode: PromptMode, promptEnvelope: PromptEnvelope, toolingOptions: GatewayToolingOptions): Flow<ChatStreamEvent> = emptyFlow()
        override fun parseAssistantSpecialOutput(content: String, existingParts: List<ChatMessagePart>, statusCardsEnabled: Boolean, hideStatusBlocksInBubble: Boolean): ParsedAssistantSpecialOutput = ParsedAssistantSpecialOutput(content, existingParts)
    }
}
