package com.example.myapplication.ui.screen.settings.memory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SimpleMemoryEditorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleMemoryEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editor_waitsForInitialSnapshotBeforeSaving() {
        composeRule.setContent {
            ChatAppTheme {
                SimpleMemoryEditorContent(
                    state = SimpleMemoryEditorUiState(initialized = false),
                    assistantName = "陆承渊",
                    onCoreDraftChange = {},
                    onSceneDraftChange = {},
                    onSave = {},
                    onConsumeMessage = {},
                    onOpenAdvancedManagement = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("陆承渊").assertIsDisplayed()
        composeRule.onNodeWithTag("simple_memory_save").assertIsNotEnabled()
    }

    @Test
    fun editor_showsCoreAndSceneDraftsWhenReady() {
        composeRule.setContent {
            ChatAppTheme {
                SimpleMemoryEditorContent(
                    state = SimpleMemoryEditorUiState(
                        initialized = true,
                        coreDraft = "用户喜欢短句",
                        sceneDraft = "雨夜停在码头",
                        showSceneSection = true,
                    ),
                    assistantName = "陆承渊",
                    onCoreDraftChange = {},
                    onSceneDraftChange = {},
                    onSave = {},
                    onConsumeMessage = {},
                    onOpenAdvancedManagement = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("simple_memory_core").assertIsDisplayed()
        composeRule.onNodeWithTag("simple_memory_scene").assertIsDisplayed()
        composeRule.onNodeWithTag("simple_memory_save").assertIsDisplayed()
    }
}
