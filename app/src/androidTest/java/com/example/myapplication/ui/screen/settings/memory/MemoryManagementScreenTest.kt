package com.example.myapplication.ui.screen.settings.memory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryInjectionPosition
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.MemoryManagementUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryManagementScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longMemoryContent_canScrollAndExpand() {
        val longMemory = (1..16).joinToString(separator = "\n") { index ->
            "第 $index 段长期记忆内容，用来确认折叠后可以展开查看完整文本。"
        }

        composeRule.setContent {
            ChatAppTheme {
                MemoryManagementScreen(
                    uiState = MemoryManagementUiState(
                        memories = listOf(
                            MemoryEntry(
                                id = "memory-1",
                                scopeType = MemoryScopeType.ASSISTANT,
                                scopeId = "assistant-1",
                                characterId = "assistant-1",
                                content = longMemory,
                            ),
                        ),
                    ),
                    assistants = listOf(
                        Assistant(
                            id = "assistant-1",
                            name = "陆承渊",
                        ),
                    ),
                    memoryAutoSummaryEvery = 6,
                    memoryCapacity = 200,
                    memoryExtractionPrompt = "",
                    memoryInjectionPrompt = "",
                    memoryInjectionPosition = MemoryInjectionPosition.AFTER_WORLD_BOOK,
                    onUpdateMemoryAutoSummaryEvery = {},
                    onUpdateMemoryCapacity = {},
                    onUpdateMemoryExtractionPrompt = {},
                    onUpdateMemoryInjectionPrompt = {},
                    onUpdateMemoryInjectionPosition = {},
                    onTogglePinned = {},
                    onDeleteMemory = {},
                    onDeleteSummary = {},
                    onConsumeMessage = {},
                    onNavigateBack = {},
                    initialAssistantId = "assistant-1",
                )
            }
        }

        val expandLabel = composeRule.activity.getString(R.string.common_expand_full)
        val collapseLabel = composeRule.activity.getString(R.string.common_collapse)

        composeRule.onNodeWithTag("memory_management_list")
            .performScrollToNode(hasText(expandLabel))
        composeRule.onNodeWithText(expandLabel)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("memory_management_list")
            .performScrollToNode(hasText(collapseLabel))
        composeRule.onNodeWithText(collapseLabel)
            .assertIsDisplayed()
    }
}
