package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayStateSupportTest {
    @Test
    fun applySettings_updatesSettingsAssistantAndProviderInfo() {
        val updated = RoleplayStateSupport.applySettings(
            current = RoleplayUiState(),
            settings = AppSettings(selectedModel = "chat-model"),
            currentAssistant = Assistant(id = "assistant-1", name = "陆宴清"),
            currentModel = "chat-model",
            currentProviderId = "provider-1",
        )

        assertEquals("chat-model", updated.currentModel)
        assertEquals("provider-1", updated.currentProviderId)
        assertEquals("assistant-1", updated.currentAssistant?.id)
    }

    @Test
    fun applyCurrentSession_resetsContextWhenSessionBecomesNull() {
        val updated = RoleplayStateSupport.applyCurrentSession(
            current = RoleplayUiState(
                contextStatus = RoleplayContextStatus(
                    hasSummary = true,
                    summaryCoveredMessageCount = 8,
                    worldBookHitCount = 2,
                    memoryInjectionCount = 3,
                    isContinuingSession = true,
                ),
                contextGovernance = ContextGovernanceSnapshot(summaryCoveredMessageCount = 8),
            ),
            session = null,
        )

        assertEquals(RoleplayContextStatus(), updated.contextStatus)
        assertEquals(null, updated.contextGovernance)
    }

    @Test
    fun applySessionStartResult_appliesMismatchFlagsAndHistoryState() {
        val updated = RoleplayStateSupport.applySessionStartResult(
            current = RoleplayUiState(
                settings = AppSettings(
                    suppressRoleplayAssistantMismatchDialog = false,
                ),
                contextStatus = RoleplayContextStatus(
                    isContinuingSession = false,
                ),
            ),
            startResult = RoleplaySessionStartResult(
                session = RoleplaySession(
                    id = "session-1",
                    scenarioId = "scene-1",
                    conversationId = "conv-1",
                ),
                reusedExistingSession = true,
                hasHistory = true,
                assistantMismatch = true,
                conversationAssistantId = "assistant-old",
            ),
            previousAssistantName = "旧角色",
            currentAssistantName = "新角色",
        )

        assertTrue(updated.showAssistantMismatchDialog)
        assertEquals("旧角色", updated.previousAssistantName)
        assertEquals("新角色", updated.currentAssistantName)
        assertTrue(updated.contextStatus.isContinuingSession)
        assertFalse(updated.isScenarioLoading)
    }

    @Test
    fun applyScenarios_keepsSelectedScenarioWhenStillPresent() {
        val selectedScenario = RoleplayScenario(id = "scene-1", title = "旧标题")
        val updated = RoleplayStateSupport.applyScenarios(
            current = RoleplayUiState(currentScenario = selectedScenario),
            scenarios = listOf(
                selectedScenario.copy(title = "新标题"),
            ),
            currentScenarioId = "scene-1",
        )

        assertEquals("新标题", updated.currentScenario?.title)
        assertEquals(1, updated.scenarios.size)
    }

    @Test
    fun finishSending_resetsStreamingAndSendingFlags() {
        val updated = RoleplayStateSupport.finishSending(
            current = RoleplayUiState(
                isSending = true,
                streamingContent = "流式内容",
                errorMessage = null,
            ),
            errorMessage = "发送失败",
        )

        assertFalse(updated.isSending)
        assertEquals("", updated.streamingContent)
        assertEquals("发送失败", updated.errorMessage)
    }

    @Test
    fun applySuggestionDraft_setsInputAndClearsSuggestionState() {
        val updated = RoleplayStateSupport.applySuggestionDraft(
            current = RoleplayUiState(
                suggestions = listOf(
                    com.example.myapplication.model.RoleplaySuggestionUiModel(
                        id = "s1",
                        label = "建议",
                        text = "内容",
                        axis = com.example.myapplication.model.RoleplaySuggestionAxis.PLOT,
                    ),
                ),
                isGeneratingSuggestions = true,
                suggestionErrorMessage = "旧错误",
                errorMessage = "旧消息",
            ),
            draftText = "新的草稿",
        )

        assertEquals("新的草稿", updated.input)
        assertTrue(updated.suggestions.isEmpty())
        assertFalse(updated.isGeneratingSuggestions)
        assertEquals(null, updated.suggestionErrorMessage)
        assertEquals(null, updated.errorMessage)
    }

    @Test
    fun applySessionStartResult_skipsMismatchDialogWhenSuppressed() {
        val updated = RoleplayStateSupport.applySessionStartResult(
            current = RoleplayUiState(
                settings = AppSettings(
                    suppressRoleplayAssistantMismatchDialog = true,
                ),
            ),
            startResult = RoleplaySessionStartResult(
                session = RoleplaySession(
                    id = "session-1",
                    scenarioId = "scene-1",
                    conversationId = "conv-1",
                ),
                reusedExistingSession = true,
                hasHistory = true,
                assistantMismatch = true,
                conversationAssistantId = "assistant-old",
            ),
            previousAssistantName = "旧角色",
            currentAssistantName = "新角色",
        )

        assertFalse(updated.showAssistantMismatchDialog)
        assertEquals("", updated.previousAssistantName)
        assertEquals("", updated.currentAssistantName)
        assertTrue(updated.contextStatus.isContinuingSession)
    }

    @Test
    fun dismissAssistantMismatchDialog_withSuppressionUpdatesNotice() {
        val updated = RoleplayStateSupport.dismissAssistantMismatchDialog(
            current = RoleplayUiState(
                showAssistantMismatchDialog = true,
                previousAssistantName = "旧角色",
                currentAssistantName = "新角色",
            ),
            suppressFuturePrompt = true,
        )

        assertFalse(updated.showAssistantMismatchDialog)
        assertEquals("继续沿用旧剧情，后续不再提示", updated.noticeMessage)
    }

    @Test
    fun applyRestartSessionSuccess_clearsSuggestionsAndNotice() {
        val updated = RoleplayStateSupport.applyRestartSessionSuccess(
            current = RoleplayUiState(
                suggestions = listOf(
                    com.example.myapplication.model.RoleplaySuggestionUiModel(
                        id = "s1",
                        label = "建议",
                        text = "内容",
                        axis = com.example.myapplication.model.RoleplaySuggestionAxis.PLOT,
                    ),
                ),
                latestPromptDebugDump = "old",
                contextGovernance = ContextGovernanceSnapshot(summaryCoveredMessageCount = 5),
                showAssistantMismatchDialog = true,
                previousAssistantName = "旧角色",
                currentAssistantName = "新角色",
            ),
        )

        assertTrue(updated.suggestions.isEmpty())
        assertEquals("", updated.latestPromptDebugDump)
        assertEquals(null, updated.contextGovernance)
        assertEquals("已重开剧情", updated.noticeMessage)
        assertFalse(updated.showAssistantMismatchDialog)
    }

    @Test
    fun applyScenarioLoadFailure_marksLoadingStopped() {
        val updated = RoleplayStateSupport.applyScenarioLoadFailure(
            current = RoleplayUiState(isScenarioLoading = true, isSending = true),
            errorMessage = "启动失败",
        )

        assertFalse(updated.isScenarioLoading)
        assertFalse(updated.isSending)
        assertEquals("启动失败", updated.errorMessage)
    }

    @Test
    fun applyPreparedEdit_restoresInputAndClearsTransientState() {
        val updated = RoleplayStateSupport.applyPreparedEdit(
            current = RoleplayUiState(
                suggestions = listOf(
                    com.example.myapplication.model.RoleplaySuggestionUiModel(
                        id = "s1",
                        label = "建议",
                        text = "内容",
                        axis = com.example.myapplication.model.RoleplaySuggestionAxis.PLOT,
                    ),
                ),
                isGeneratingSuggestions = true,
                latestPromptDebugDump = "old",
                contextGovernance = ContextGovernanceSnapshot(summaryCoveredMessageCount = 2),
                streamingContent = "streaming",
            ),
            restoredInput = "恢复输入",
            inputFocusToken = 123L,
        )

        assertEquals("恢复输入", updated.input)
        assertTrue(updated.suggestions.isEmpty())
        assertFalse(updated.isGeneratingSuggestions)
        assertEquals("", updated.latestPromptDebugDump)
        assertEquals(null, updated.contextGovernance)
        assertEquals("", updated.streamingContent)
        assertEquals(123L, updated.inputFocusToken)
    }

    @Test
    fun applyPromptContext_updatesCountsAndDebugDump() {
        val snapshot = ContextGovernanceSnapshot(summaryCoveredMessageCount = 4)
        val updated = RoleplayStateSupport.applyPromptContext(
            current = RoleplayUiState(),
            summaryCoveredMessageCount = 4,
            worldBookHitCount = 2,
            memoryInjectionCount = 3,
            debugDump = "debug-dump",
            contextGovernance = snapshot,
        )

        assertTrue(updated.contextStatus.hasSummary)
        assertEquals(4, updated.contextStatus.summaryCoveredMessageCount)
        assertEquals(2, updated.contextStatus.worldBookHitCount)
        assertEquals(3, updated.contextStatus.memoryInjectionCount)
        assertEquals("debug-dump", updated.latestPromptDebugDump)
        assertEquals(snapshot, updated.contextGovernance)
    }
}
