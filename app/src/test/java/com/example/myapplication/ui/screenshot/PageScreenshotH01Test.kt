package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import org.junit.Test

/**
 * H01：P01/P02/P03 × 抹茶/香草/海洋 × 浅/深，常规内容。
 *
 * 验证对象：真实页面根、标题、关键操作、消息与角色数据存在。断言失败会直接让用例失败，
 * 不会静默写出错误基准。
 */
class PageScreenshotH01Test : ScreenshotRenderTest() {

    @Test
    fun h01_p01_messages_matcha_light() = renderMessagesPage("matcha", "light")

    @Test
    fun h01_p01_messages_matcha_dark() = renderMessagesPage("matcha", "dark")

    @Test
    fun h01_p01_messages_vanilla_light() = renderMessagesPage("vanilla", "light")

    @Test
    fun h01_p01_messages_vanilla_dark() = renderMessagesPage("vanilla", "dark")

    @Test
    fun h01_p01_messages_ocean_light() = renderMessagesPage("ocean", "light")

    @Test
    fun h01_p01_messages_ocean_dark() = renderMessagesPage("ocean", "dark")

    @Test
    fun h01_p02_assistants_matcha_light() = renderAssistantsPage("matcha", "light")

    @Test
    fun h01_p02_assistants_matcha_dark() = renderAssistantsPage("matcha", "dark")

    @Test
    fun h01_p02_assistants_vanilla_light() = renderAssistantsPage("vanilla", "light")

    @Test
    fun h01_p02_assistants_vanilla_dark() = renderAssistantsPage("vanilla", "dark")

    @Test
    fun h01_p02_assistants_ocean_light() = renderAssistantsPage("ocean", "light")

    @Test
    fun h01_p02_assistants_ocean_dark() = renderAssistantsPage("ocean", "dark")

    @Test
    fun h01_p03_online_play_matcha_light() = renderOnlinePlayPage("matcha", "light")

    @Test
    fun h01_p03_online_play_matcha_dark() = renderOnlinePlayPage("matcha", "dark")

    @Test
    fun h01_p03_online_play_vanilla_light() = renderOnlinePlayPage("vanilla", "light")

    @Test
    fun h01_p03_online_play_vanilla_dark() = renderOnlinePlayPage("vanilla", "dark")

    @Test
    fun h01_p03_online_play_ocean_light() = renderOnlinePlayPage("ocean", "light")

    @Test
    fun h01_p03_online_play_ocean_dark() = renderOnlinePlayPage("ocean", "dark")

    /** P01 消息首页：顶栏操作、底栏切换、会话标题与预览必须真实存在。 */
    private fun renderMessagesPage(themeToken: String, shadeToken: String) {
        val case = ScreenshotCases.require("H01_p01-messages-$themeToken-$shadeToken")
        renderMessagesShellPage(
            case = case,
            appColorTheme = AppColorTheme.fromStorageValue(themeToken),
            darkTheme = shadeToken == "dark",
        )
        composeRule.onNodeWithContentDescription("新增").assertIsDisplayed()
        composeRule.onNodeWithText("通讯录").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.SuwanDisplayName).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.SuwanLastMessage).assertIsDisplayed()
        captureCase(case)
    }

    /** P02 角色列表：顶栏、搜索框、合成角色与选中态必须真实存在。 */
    private fun renderAssistantsPage(themeToken: String, shadeToken: String) {
        val case = ScreenshotCases.require("H01_p02-assistants-$themeToken-$shadeToken")
        renderAssistantListPage(
            case = case,
            appColorTheme = AppColorTheme.fromStorageValue(themeToken),
            darkTheme = shadeToken == "dark",
        )
        composeRule.onNodeWithText("角色").assertIsDisplayed()
        composeRule.onNodeWithText("新增").assertIsDisplayed()
        composeRule.onNodeWithText("搜索角色").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.SuwanAssistant.name).assertIsDisplayed()
        composeRule.onNodeWithText("当前").assertIsDisplayed()
        captureCase(case)
    }

    /** P03 线上扮演：标题、状态行、用户/角色/旁白消息与输入区必须真实存在。 */
    private fun renderOnlinePlayPage(themeToken: String, shadeToken: String) {
        val case = ScreenshotCases.require("H01_p03-online-roleplay-$themeToken-$shadeToken")
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.fromStorageValue(themeToken),
            darkTheme = shadeToken == "dark",
            messages = ScreenshotFixture.stageMessages(),
        )
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.UserLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.NarrationLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("随便聊聊…").assertIsDisplayed()
        captureCase(case)
    }
}
