package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.shouldUseRoleplayGlassBubble
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B02：背景真实状态与气泡表现的分离（P03 页面级）。
 *
 * - 未配置背景：背景 URI 为空 + imageBitmap 为 null → 皮肤气泡；
 * - 已加载背景 + 线上玻璃开/关：走真实 shouldUseRoleplayGlassBubble 判定；
 * - 已配置但未加载成功：背景 URI 非空 + imageBitmap 为 null（真实语义），行为等同未配置。
 */
class BubbleScreenshotB02Test : ScreenshotRenderTest() {

    @Test
    fun b02_p03_no_background() {
        val case = ScreenshotCases.require("B02_p03-no-background")
        renderOnlineBackgroundCase(case, backdropImageLoaded = false, onlineGlassEnabled = true)
        assertGlassExpectation(loaded = false, onlineGlassEnabled = true, expectedGlass = false)
        assertStageTexts()
        captureCase(case)
    }

    @Test
    fun b02_p03_background_glass_on() {
        val case = ScreenshotCases.require("B02_p03-background-glass-on")
        renderOnlineBackgroundCase(case, backdropImageLoaded = true, onlineGlassEnabled = true)
        assertGlassExpectation(loaded = true, onlineGlassEnabled = true, expectedGlass = true)
        assertStageTexts()
        captureCase(case)
    }

    @Test
    fun b02_p03_background_glass_off() {
        val case = ScreenshotCases.require("B02_p03-background-glass-off")
        renderOnlineBackgroundCase(case, backdropImageLoaded = true, onlineGlassEnabled = false)
        assertGlassExpectation(loaded = true, onlineGlassEnabled = false, expectedGlass = false)
        assertStageTexts()
        captureCase(case)
    }

    @Test
    fun b02_p03_background_load_failed() {
        val case = ScreenshotCases.require("B02_p03-background-load-failed")
        // 已配置背景但未加载成功：scenario.backgroundUri 非空、backdropState.imageBitmap 为 null。
        renderOnlineBackgroundCase(
            case = case,
            backdropImageLoaded = false,
            onlineGlassEnabled = true,
            configuredBackgroundUri = ScreenshotFixture.MissingBackgroundUri,
        )
        assertGlassExpectation(loaded = false, onlineGlassEnabled = true, expectedGlass = false)
        assertStageTexts()
        captureCase(case)
    }

    private fun renderOnlineBackgroundCase(
        case: ScreenshotCase,
        backdropImageLoaded: Boolean,
        onlineGlassEnabled: Boolean,
        configuredBackgroundUri: String = "",
    ) {
        val scenario = ScreenshotFixture.onlineScenario(
            id = ScreenshotFixture.SuwanScenarioId,
            assistantId = ScreenshotFixture.SuwanAssistant.id,
            title = ScreenshotFixture.SuwanScenarioTitle,
            backgroundUri = configuredBackgroundUri,
        )
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = ScreenshotFixture.stageMessages(),
            scenario = scenario,
            backdropImage = if (backdropImageLoaded) ScreenshotFixture.syntheticBackdrop() else null,
            skin = RoleplayNoBackgroundSkinSettings(onlineBackgroundGlassEnabled = onlineGlassEnabled),
        )
    }

    private fun assertGlassExpectation(loaded: Boolean, onlineGlassEnabled: Boolean, expectedGlass: Boolean) {
        assertEquals(
            "线上玻璃判定与用例意图不一致",
            expectedGlass,
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = loaded,
                bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
                onlineBackgroundGlassEnabled = onlineGlassEnabled,
            ),
        )
    }

    private fun assertStageTexts() {
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.NarrationLineText, substring = true).assertIsDisplayed()
    }
}
