package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.example.myapplication.model.AppColorTheme
import org.junit.Test

/**
 * H02：现有空态记录（不新增业务空态、不构造不存在的状态）。
 *
 * - P01：没有会话 → 现成 EmptyState；
 * - P02：搜索无匹配 → 现成占位行（需要输入固定关键词，这是页面既有的交互结果）；
 * - P03：空会话 → 头部与输入区现状。
 */
class PageScreenshotH02Test : ScreenshotRenderTest() {

    @Test
    fun h02_p01_messages_empty_state() {
        val case = ScreenshotCases.require("H02_p01-messages-empty")
        renderMessagesShellPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            summaries = emptyList(),
        )
        composeRule.onNodeWithText("还没有会话").assertIsDisplayed()
        composeRule.onNodeWithText("新建会话").assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun h02_p02_assistants_no_search_result() {
        val case = ScreenshotCases.require("H02_p02-assistants-no-search-result")
        renderAssistantListPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
        )
        composeRule.onNode(hasSetTextAction()).performTextInput(ScreenshotFixture.NoMatchQuery)
        composeRule.onNodeWithText("没有匹配的角色").assertIsDisplayed()
        composeRule.onNodeWithText("试试其他关键词，或者新增一个角色。").assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun h02_p03_online_empty_session() {
        val case = ScreenshotCases.require("H02_p03-online-empty-session")
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = emptyList(),
        )
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText("随便聊聊…").assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.UserLineText, substring = true).assertDoesNotExist()
        captureCase(case)
    }
}
