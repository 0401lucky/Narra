package com.example.myapplication.ui.screen.settings

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTranslationSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeFont_permissionHelpDoesNotLaunchSettings_andLanguageStillSelectable() {
        var language = ""
        showSettings(onLanguage = { language = it })
        composeRule.onNodeWithContentDescription("悬浮窗权限：查看说明").performClick()
        composeRule.onNodeWithText("用于显示悬浮球和翻译结果面板").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithTag("screen_translation_settings_list").performScrollToNode(hasText("简体中文"))
        composeRule.onNodeWithText("简体中文").performClick()
        composeRule.onNodeWithTag("screen_translation_languages").performScrollToNode(hasText("日语"))
        composeRule.onNodeWithText("日语").performClick()
        composeRule.runOnIdle { assertEquals("日语", language) }
    }

    @Test
    fun savingOrMissingOverlayPermission_preventsDuplicateStartAndSave() {
        showSettings(saving = true)
        composeRule.onNodeWithTag("screen_translation_settings_list").performScrollToNode(hasText("保存并启动后台服务"))
        composeRule.onNodeWithText("保存并启动后台服务").assertIsNotEnabled()
        composeRule.onNodeWithTag("screen_translation_settings_list").performScrollToNode(hasText("保存中…"))
        composeRule.onNodeWithText("保存中…").assertIsNotEnabled()
        if (!Settings.canDrawOverlays(composeRule.activity)) {
            composeRule.onNodeWithTag("screen_translation_settings_list").performScrollToNode(hasText("先允许悬浮窗权限，再启动翻译。"))
            composeRule.onNodeWithText("先允许悬浮窗权限，再启动翻译。").assertIsDisplayed()
        }
    }

    private fun showSettings(saving: Boolean = false, onLanguage: (String) -> Unit = {}) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.4f)) {
                ChatAppTheme(darkTheme = true) {
                    ScreenTranslationSettingsScreen(
                        uiState = SettingsUiState(isSaving = saving),
                        onNavigateBack = {},
                        onUpdateServiceEnabled = {},
                        onUpdateOverlayEnabled = {},
                        onUpdateSelectedTextEnabled = {},
                        onUpdateShowSourceText = {},
                        onUpdateTargetLanguage = onLanguage,
                        onUpdateVendorGuideDismissed = {},
                        onSaveChanges = {},
                        onSaveAndStartService = {},
                        onSaveAndStopService = {},
                    )
                }
            }
        }
    }
}
