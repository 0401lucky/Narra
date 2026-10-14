package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.shouldUseRoleplayGlassBubble
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B04：线上/线下模式与玻璃开关组合。
 *
 * - 线上：页面级 P03，全局开关真实生效（开 → 玻璃，关 → 皮肤气泡叠在背景上）；
 * - 线下：组件级（RoleplayMessageItem + OFFLINE 模式），继续原逻辑——有背景即玻璃、
 *   无背景即皮肤气泡；生产判定 `shouldUseRoleplayGlassBubble` 对非线上模式忽略全局开关，
 *   因此线下不做“开关”重复用例，避免制造不存在的差异。
 */
class BubbleScreenshotB04Test : ScreenshotRenderTest() {

    @Test
    fun b04_online_glass_on() {
        val case = ScreenshotCases.require("B04_online-glass-on")
        renderOnlineWithBackground(case, onlineGlassEnabled = true)
        assertTrue(
            "线上 + 有背景 + 开关开必须走玻璃",
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = true,
                bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
                onlineBackgroundGlassEnabled = true,
            ),
        )
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun b04_online_glass_off() {
        val case = ScreenshotCases.require("B04_online-glass-off")
        renderOnlineWithBackground(case, onlineGlassEnabled = false)
        assertFalse(
            "线上 + 有背景 + 开关关必须回退皮肤气泡",
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = true,
                bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
                onlineBackgroundGlassEnabled = false,
            ),
        )
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun b04_offline_background() {
        val case = ScreenshotCases.require("B04_offline-background")
        assertTrue(
            "线下 + 有背景继续原逻辑：玻璃（与线上开关无关）",
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = true,
                bubbleMode = RoleplayMessageBubbleMode.DEFAULT,
                onlineBackgroundGlassEnabled = false,
            ),
        )
        renderOffline(case, backdropImageLoaded = true)
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun b04_offline_no_background() {
        val case = ScreenshotCases.require("B04_offline-no-background")
        assertFalse(
            "线下 + 无背景必须回退皮肤气泡",
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = false,
                bubbleMode = RoleplayMessageBubbleMode.DEFAULT,
                onlineBackgroundGlassEnabled = true,
            ),
        )
        renderOffline(case, backdropImageLoaded = false)
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }

    private fun renderOnlineWithBackground(case: ScreenshotCase, onlineGlassEnabled: Boolean) {
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = ScreenshotFixture.stageMessages(),
            backdropImage = ScreenshotFixture.syntheticBackdrop(),
            skin = RoleplayNoBackgroundSkinSettings(onlineBackgroundGlassEnabled = onlineGlassEnabled),
        )
    }

    /** 线下组件级渲染：直接复用线上消息列表的行组件，只切换 bubbleMode。 */
    private fun renderOffline(case: ScreenshotCase, backdropImageLoaded: Boolean) {
        renderBubbleStage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = ScreenshotFixture.stageMessages(),
            backdropImage = if (backdropImageLoaded) ScreenshotFixture.syntheticBackdrop() else null,
            skin = RoleplayNoBackgroundSkinSettings(),
            bubbleMode = RoleplayMessageBubbleMode.DEFAULT,
        )
    }
}
