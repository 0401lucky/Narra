package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionButtonWithDraftChanges_callsSaveInsteadOfImmediateNavigation() {
        var saveCalls = 0
        var openChatCalls = 0
        var openHomeCalls = 0

        composeRule.setContent {
            ChatAppTheme {
                SettingsScreen(
                    uiState = SettingsUiState(
                        savedSettings = AppSettings(themeMode = ThemeMode.SYSTEM),
                        themeMode = ThemeMode.DARK,
                    ),
                    onSave = { saveCalls++ },
                    onConsumeMessage = {},
                    onOpenChat = { openChatCalls++ },
                    onOpenProviderSettings = {},
                    onOpenPresetSettings = {},
                    onOpenVoiceSynthesisSettings = {},
                    onOpenSearchToolSettings = {},
                    onOpenUpdateSettings = {},
                    onOpenUserMasks = {},
                    onOpenModelSettings = {},
                    onOpenAssistantSettings = {},
                    onOpenWorldBookSettings = {},
                    onOpenMemorySettings = {},
                    onOpenContextTransferSettings = {},
                    onOpenScreenTranslationSettings = {},
                    onOpenHome = { openHomeCalls++ },
                    onNavigateBack = {},
                    onUpdateThemeMode = {},
                    onUpdateMessageTextScale = {},
                    onUpdateReasoningExpandedByDefault = {},
                    onUpdateShowThinkingContent = {},
                    onUpdateAutoCollapseThinking = {},
                    onUpdateAutoPreviewImages = {},
                    onUpdateCodeBlockAutoWrap = {},
                    onUpdateCodeBlockAutoCollapse = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save))
            .performClick()

        composeRule.runOnIdle {
            check(saveCalls == 1)
            check(openChatCalls == 0)
            check(openHomeCalls == 0)
        }
    }
}
