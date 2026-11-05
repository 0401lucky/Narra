package com.example.myapplication.ui.screen.roleplay

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.SessionSidebarRenderTest
import com.example.myapplication.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSidebarPersistenceTest : SessionSidebarRenderTest() {
    @Test
    fun thirteenSkins_preserveHistoricalGeometryTailAndGlassUntilExplicitReset() {
        renderSidebar(ScreenshotCases.require("E05_theme-narrow-light"))
        open("主题")
        RoleplayNoBackgroundSkinPreset.entries.forEach { preset ->
            composeRule.onNodeWithTag("roleplay_skin_preset_${preset.storageValue}")
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(skin.copy(preset = preset), settings.value.roleplayNoBackgroundSkin)
            }
        }
        assertEquals(13, events.count { it == "skin" })
        composeRule.onNodeWithTag("roleplay_skin_radius_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        composeRule.runOnIdle {
            assertEquals(
                skin.copy(preset = RoleplayNoBackgroundSkinPreset.LIQUID_GLASS, bubbleRadiusDp = 25),
                settings.value.roleplayNoBackgroundSkin,
            )
        }
        composeRule.onNodeWithContentDescription("重置气泡样式").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                RoleplayNoBackgroundSkinSettings(onlineBackgroundGlassEnabled = false),
                settings.value.roleplayNoBackgroundSkin,
            )
        }
    }

    @Test
    fun topBack_successPersistsCompleteDraftAndCallsNavigationOnce() = saveThroughScreen(reading = false, fail = false)

    @Test
    fun topBack_failureKeepsDraftAndDoesNotNavigate() = saveThroughScreen(reading = false, fail = true)

    @Test
    fun reading_successPersistsCompleteDraftBeforeOpening() = saveThroughScreen(reading = true, fail = false)

    @Test
    fun reading_failureKeepsDraftAndDoesNotOpen() = saveThroughScreen(reading = true, fail = true)

    private fun saveThroughScreen(reading: Boolean, fail: Boolean) {
        val services = createTestAiServices(settings = settings.value)
        val editor = if (fail) object : AiSettingsEditor by services.settingsEditor {
            override suspend fun saveProviderSettings(providers: List<ProviderSettings>, selectedProviderId: String) {
                error("合成设置写入失败")
            }
        } else services.settingsEditor
        val viewModel = SettingsViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = editor,
            modelCatalogRepository = services.modelCatalogRepository,
        )
        drainMainLooper()
        renderSidebar(ScreenshotCases.require("E05_theme-narrow-light"), settingsViewModel = viewModel)
        drainMainLooper()
        open("主题")
        composeRule.onNodeWithTag("roleplay_skin_preset_telegram").performScrollTo().performClick()
        composeRule.onNodeWithTag("roleplay_skin_radius_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        val expected = skin.copy(preset = RoleplayNoBackgroundSkinPreset.TELEGRAM, bubbleRadiusDp = 23)
        composeRule.runOnIdle {
            assertEquals(expected, viewModel.uiState.value.roleplayNoBackgroundSkin)
            assertTrue(viewModel.uiState.value.hasDraftChanges())
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertFalse(events.contains("back"))
        if (reading) {
            open("快捷切换")
            open("阅读模式")
        } else {
            composeRule.onNodeWithContentDescription("返回").performClick()
        }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val navigation = if (reading) "reading" else "back"
            assertEquals(if (fail) 0 else 1, events.count { it == navigation })
            assertEquals(expected, viewModel.uiState.value.roleplayNoBackgroundSkin)
            assertEquals(fail, viewModel.uiState.value.hasDraftChanges())
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(if (fail) skin else expected, viewModel.storedSettings.value.roleplayNoBackgroundSkin)
        }
        assertEquals(
            if (fail) skin else expected,
            runBlocking { services.settingsStore.settingsFlow.first() }.roleplayNoBackgroundSkin,
        )
        if (fail) {
            composeRule.onAllNodesWithText(if (reading) "阅读模式" else "会话设定").onFirst().assertIsDisplayed()
        }
    }
}
