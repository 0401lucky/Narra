package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.roleplay.RoleplayOutputParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayObservationSupportTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observeMappedMessages_onlyReactsToMapperInputs() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val currentRawMessages = MutableStateFlow(emptyList<com.example.myapplication.model.ChatMessage>())
        val settings = MutableStateFlow(AppSettings())
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = "assistant-1",
            characterDisplayNameOverride = "陆宴清",
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        var nowProviderCallCount = 0
        val uiState = MutableStateFlow(
            RoleplayUiState(
                currentScenario = scenario,
                currentAssistant = assistant,
                isSending = true,
                streamingContent = "我在想你。",
            ),
        )
        val observerScope = CoroutineScope(coroutineContext + Job())

        RoleplayObservationSupport.observeMappedMessages(
            scope = observerScope,
            currentRawMessages = currentRawMessages,
            settings = settings,
            uiState = uiState,
            outputParser = RoleplayOutputParser(),
            nowProvider = {
                nowProviderCallCount += 1
                100L
            },
        )

        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)
        assertEquals(1, uiState.value.messages.size)
        assertEquals("我在想你。", uiState.value.messages.single().content)

        uiState.update { current ->
            current.copy(errorMessage = "这不该触发重新映射")
        }
        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)

        uiState.update { current ->
            current.copy(noticeMessage = "同样不该触发重新映射")
        }
        advanceUntilIdle()

        assertEquals(1, nowProviderCallCount)

        uiState.update { current ->
            current.copy(streamingContent = "还是先等你开口。")
        }
        advanceUntilIdle()

        assertEquals(2, nowProviderCallCount)
        assertTrue(uiState.value.messages.single().content.contains("还是先等你开口"))
        observerScope.coroutineContext[Job]?.cancel()
    }
}
