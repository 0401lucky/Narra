package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
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
        var openRoleplayCalls = 0
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
                    onOpenRoleplay = { openRoleplayCalls++ },
                    onOpenProviderSettings = {},
                    onOpenVoiceSynthesisSettings = {},
                    onOpenVoiceRecognitionSettings = {},
                    onOpenUpdateSettings = {},
                    onOpenUserMasks = {},
                    onOpenModelSettings = {},
                    onOpenAssistantSettings = {},
                    onOpenWorldBookSettings = {},
                    onOpenMemorySettings = {},
                    onOpenContextTransferSettings = {},
                    onOpenRoleplayScripts = {},
                    onOpenHome = { openHomeCalls++ },
                    onNavigateBack = {},
                    onUpdateThemeMode = {},
                    onUpdateAppColorTheme = {},
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
            check(openRoleplayCalls == 0)
            check(openHomeCalls == 0)
        }
    }

    @Test
    fun localDiagnosticsEntry_callsDedicatedNavigationCallback() {
        var diagnosticsCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onSave = {},
                    onConsumeMessage = {},
                    onOpenRoleplay = {},
                    onOpenProviderSettings = {},
                    onOpenVoiceSynthesisSettings = {},
                    onOpenVoiceRecognitionSettings = {},
                    onOpenUpdateSettings = {},
                    onOpenDiagnostics = { diagnosticsCalls++ },
                    onOpenUserMasks = {},
                    onOpenModelSettings = {},
                    onOpenAssistantSettings = {},
                    onOpenWorldBookSettings = {},
                    onOpenMemorySettings = {},
                    onOpenContextTransferSettings = {},
                    onOpenRoleplayScripts = {},
                    onOpenHome = {},
                    onNavigateBack = {},
                    onUpdateThemeMode = {},
                    onUpdateAppColorTheme = {},
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

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("本地诊断"))
        composeRule.onNodeWithText("本地诊断").performClick()
        composeRule.runOnIdle {
            assertEquals(1, diagnosticsCalls)
        }
    }
}
