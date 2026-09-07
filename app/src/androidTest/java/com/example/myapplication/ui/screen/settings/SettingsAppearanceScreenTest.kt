package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppearanceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun themeSheet_exposesCurrentSelectionAndChangesTheme() {
        var selectedTheme: AppColorTheme? = null
        setSettingsContent(onUpdateAppColorTheme = { selectedTheme = it })

        openSettingsRow("主题色调")
        composeRule.onNode(hasText(AppColorTheme.MATCHA.label) and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText(AppColorTheme.OCEAN.label) and isSelectable())
            .assertIsNotSelected()
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(AppColorTheme.OCEAN, selectedTheme) }
    }

    @Test
    fun modeSheet_exposesCurrentSelectionAndChangesMode() {
        var selectedMode: ThemeMode? = null
        setSettingsContent(onUpdateThemeMode = { selectedMode = it })

        openSettingsRow(composeRule.activity.getString(R.string.settings_color_mode))
        composeRule.onNode(hasText(ThemeMode.SYSTEM.label) and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText(ThemeMode.DARK.label) and isSelectable()).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ThemeMode.DARK, selectedMode) }
    }

    @Test
    fun displaySheet_largeFontCanScrollToLastSettingAndToggleLabel() {
        var autoCollapse: Boolean? = null
        setSettingsContent(
            fontScale = 1.6f,
            onUpdateCodeBlockAutoCollapse = { autoCollapse = it },
        )

        openSettingsRow(composeRule.activity.getString(R.string.settings_display))
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_code_block_auto_collapse))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(true, autoCollapse) }
    }

    private fun openSettingsRow(label: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).assertIsDisplayed().performClick()
    }

    private fun setSettingsContent(
        fontScale: Float = 1f,
        onUpdateAppColorTheme: (AppColorTheme) -> Unit = {},
        onUpdateThemeMode: (ThemeMode) -> Unit = {},
        onUpdateCodeBlockAutoCollapse: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
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
                        onOpenUserMasks = {},
                        onOpenModelSettings = {},
                        onOpenAssistantSettings = {},
                        onOpenWorldBookSettings = {},
                        onOpenMemorySettings = {},
                        onOpenContextTransferSettings = {},
                        onOpenRoleplayScripts = {},
                        onOpenHome = {},
                        onNavigateBack = {},
                        onUpdateThemeMode = onUpdateThemeMode,
                        onUpdateAppColorTheme = onUpdateAppColorTheme,
                        onUpdateMessageTextScale = {},
                        onUpdateReasoningExpandedByDefault = {},
                        onUpdateShowThinkingContent = {},
                        onUpdateAutoCollapseThinking = {},
                        onUpdateAutoPreviewImages = {},
                        onUpdateCodeBlockAutoWrap = {},
                        onUpdateCodeBlockAutoCollapse = onUpdateCodeBlockAutoCollapse,
                    )
                }
            }
        }
    }
}
