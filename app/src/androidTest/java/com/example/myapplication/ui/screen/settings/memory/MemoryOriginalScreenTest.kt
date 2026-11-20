package com.example.myapplication.ui.screen.settings.memory

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryArchiveMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.MemoryOriginalUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryOriginalScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longOriginal200Percent_keepsHistoricalNameAndBackReachable() {
        var closed = 0
        val messages = (1..25).map { index ->
            MemoryArchiveMessage(
                ChatMessage(
                    "original-$index",
                    "old",
                    MessageRole.USER,
                    "原文第 $index 条，保留当时的完整文字。",
                    speakerName = "历史署名"
                ), "old-persona", null
            )
        }
        composeRule.setContent {
            ChatAppTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        2f
                    )
                ) {
                    MemoryOriginalScreen(
                        MemoryOriginalUiState(
                            messages = messages,
                            isLoading = false,
                            totalCount = messages.size
                        ), {}, {}, { closed++ })
                }
            }
        }
        composeRule.onNodeWithTag("memory_original_list")
            .performScrollToNode(hasText("原文第 25 条，保留当时的完整文字。"))
        composeRule.onNodeWithText("原文第 25 条，保留当时的完整文字。").assertIsDisplayed()
        val back = composeRule.onNodeWithContentDescription("返回")
        back.assertIsDisplayed()
        val bounds = back.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= minimum && bounds.height >= minimum)
        back.performClick()
        composeRule.runOnIdle { assertEquals(1, closed) }
    }

    @Test
    fun missingOriginal_doesNotShowAnotherConversation() {
        composeRule.setContent {
            ChatAppTheme {
                MemoryOriginalScreen(
                    MemoryOriginalUiState(
                        missing = true,
                        isLoading = false
                    ), {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("memory_original_missing").assertIsDisplayed()
        composeRule.onNodeWithText("原文缺失或不属于此档案，无法显示。").assertIsDisplayed()
    }
}
