package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CharacterArtStudioUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterArtStudioScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun studio_keepsPreviewAndPrimaryActionsReachable() {
        var generateCalls = 0

        composeRule.setContent {
            ChatAppTheme {
                CharacterArtStudioScreen(
                    uiState = CharacterArtStudioUiState(
                        assistants = listOf(
                            Assistant(
                                id = "assistant-art-test",
                                name = "陆星野",
                                description = "测试角色",
                            ),
                        ),
                        selectedAssistantId = "assistant-art-test",
                        editablePrompt = "清晰的角色半身像",
                    ),
                    onSelectAssistant = {},
                    onSelectStyle = {},
                    onPromptChange = {},
                    onNegativePromptChange = {},
                    onRevisionInstructionChange = {},
                    onExtractPrompt = {},
                    onGenerateImage = { generateCalls++ },
                    onApplyAvatar = {},
                    onClearMessages = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("角色图工作台").assertIsDisplayed()
        composeRule.onNodeWithText("生成结果会显示在这里").assertIsDisplayed()
        repeat(5) {
            composeRule.onNodeWithTag("character_art_list").performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag("character_art_generate")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("character_art_list").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("character_art_apply_avatar")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle {
            assertEquals(1, generateCalls)
        }
    }
}
