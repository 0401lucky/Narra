package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningConfigTest {

    @Test
    fun supportsThinkingBudgetControl_gemini25() {
        val provider = ProviderSettings(
            type = ProviderType.GOOGLE,
            selectedModel = "gemini-2.5-pro",
        )

        assertTrue(supportsThinkingBudgetControl(provider))
    }

    @Test
    fun supportsThinkingBudgetControl_claudeAnthropic() {
        val provider = ProviderSettings(
            type = ProviderType.ANTHROPIC,
            selectedModel = "claude-sonnet-4-20250514",
        )

        assertTrue(supportsThinkingBudgetControl(provider))
    }

    @Test
    fun supportsThinkingBudgetControl_claudeOpenAiCompatible() {
        val provider = ProviderSettings(
            type = ProviderType.CUSTOM,
            selectedModel = "claude-sonnet-4-20250514",
        )

        assertTrue(supportsThinkingBudgetControl(provider))
    }

    @Test
    fun supportsThinkingBudgetControl_grok4Disabled() {
        val provider = ProviderSettings(
            type = ProviderType.GROK,
            selectedModel = "grok-4.2beta",
        )

        assertFalse(supportsThinkingBudgetControl(provider))
    }

    @Test
    fun mapThinkingBudgetToReasoningEffort_gemini3StandardUsesHigh() {
        val provider = ProviderSettings(
            type = ProviderType.GOOGLE,
            selectedModel = "gemini-3-flash-preview",
            thinkingBudget = REASONING_BUDGET_MEDIUM,
        )

        assertEquals("high", mapThinkingBudgetToReasoningEffort(provider))
    }

    @Test
    fun buildThinkingRequestConfig_anthropicUsesBudgetTokens() {
        val provider = ProviderSettings(
            type = ProviderType.ANTHROPIC,
            selectedModel = "claude-sonnet-4-20250514",
            thinkingBudget = REASONING_BUDGET_MEDIUM,
        )

        val config = buildThinkingRequestConfig(provider)

        assertNull(config.reasoningEffort)
        assertNotNull(config.thinking)
        assertEquals(REASONING_BUDGET_MEDIUM, config.thinking?.budgetTokens)
    }

    @Test
    fun buildThinkingRequestConfig_claudeOpenAiCompatibleUsesReasoningEffort() {
        val provider = ProviderSettings(
            type = ProviderType.CUSTOM,
            selectedModel = "claude-sonnet-4-20250514",
            thinkingBudget = REASONING_BUDGET_MEDIUM,
        )

        val config = buildThinkingRequestConfig(provider)

        assertEquals("medium", config.reasoningEffort)
        assertNull(config.thinking)
    }

    @Test
    fun buildThinkingRequestConfig_anthropicAutoDoesNotForceThinking() {
        val provider = ProviderSettings(
            type = ProviderType.ANTHROPIC,
            selectedModel = "claude-sonnet-4-20250514",
            thinkingBudget = null,
        )

        val config = buildThinkingRequestConfig(provider)

        assertNull(config.reasoningEffort)
        assertNull(config.thinking)
    }
}
