package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.TranscriptionProtocol
import com.example.myapplication.model.VoiceRecognitionSettings
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceSettingsRedesignTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recognitionApi_protocolSelection_updatesDraftWithoutNetwork() {
        var selected = TranscriptionProtocol.TRANSCRIPTION_ENDPOINT
        composeRule.setContent {
            ChatAppTheme {
                VoiceRecognitionApiScreen(
                    settings = VoiceRecognitionSettings(
                        baseUrl = "https://example.test/v1",
                        apiKey = "test-key",
                    ),
                    isTesting = false,
                    uiMessage = null,
                    isLoadingModels = false,
                    availableModels = emptyList(),
                    onUpdateSettings = { selected = it.protocol },
                    onTestConnection = {},
                    onFetchModels = {},
                    onDismissModels = {},
                    onSaveChanges = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("多模态对话").performClick()
        composeRule.runOnIdle {
            check(selected == TranscriptionProtocol.CHAT_MULTIMODAL)
        }
    }

    @Test
    fun recognitionApi_endpointDetails_stayAvailableThroughHelp() {
        composeRule.setContent {
            ChatAppTheme {
                VoiceRecognitionApiScreen(
                    settings = VoiceRecognitionSettings(
                        baseUrl = "https://example.test/v1",
                        apiKey = "test-key",
                    ),
                    isTesting = false,
                    uiMessage = null,
                    isLoadingModels = false,
                    availableModels = emptyList(),
                    onUpdateSettings = {},
                    onTestConnection = {},
                    onFetchModels = {},
                    onDismissModels = {},
                    onSaveChanges = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("当前请求：", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("请求端点：查看说明")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("当前请求：", substring = true).assertIsDisplayed()
    }
}
