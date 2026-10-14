package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * H04：P01/P02/P03 在 320x640dp + 200% 字体、浅/深的现状基准。
 *
 * 只记录现状；本任务内不修窄屏大字体下的既有缺陷，发现的缺陷登记在 snapshots/manifest.md。
 */
@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class PageScreenshotH04Test : ScreenshotRenderTest() {

    @Test
    fun h04_p01_messages_narrow_light() = renderMessagesNarrow("light")

    @Test
    fun h04_p01_messages_narrow_dark() = renderMessagesNarrow("dark")

    @Test
    fun h04_p02_assistants_narrow_light() = renderAssistantsNarrow("light")

    @Test
    fun h04_p02_assistants_narrow_dark() = renderAssistantsNarrow("dark")

    @Test
    fun h04_p03_online_narrow_light() = renderOnlineNarrow("light")

    @Test
    fun h04_p03_online_narrow_dark() = renderOnlineNarrow("dark")

    private fun renderMessagesNarrow(shadeToken: String) {
        useNarrowFontScale()
        val case = ScreenshotCases.require("H04_p01-messages-narrow-$shadeToken")
        renderMessagesShellPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = shadeToken == "dark",
        )
        composeRule.onNodeWithContentDescription("新增").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.SuwanDisplayName).assertIsDisplayed()
        captureCase(case)
    }

    private fun renderAssistantsNarrow(shadeToken: String) {
        useNarrowFontScale()
        val case = ScreenshotCases.require("H04_p02-assistants-narrow-$shadeToken")
        renderAssistantListPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = shadeToken == "dark",
        )
        composeRule.onNodeWithText("搜索角色").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.SuwanAssistant.name).assertIsDisplayed()
        captureCase(case)
    }

    private fun renderOnlineNarrow(shadeToken: String) {
        useNarrowFontScale()
        val case = ScreenshotCases.require("H04_p03-online-narrow-$shadeToken")
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = shadeToken == "dark",
            messages = ScreenshotFixture.stageMessages(),
        )
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.UserLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }
}
