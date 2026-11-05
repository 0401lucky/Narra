package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screen.roleplay.TAG_ROLEPLAY_SETTINGS_LIST
import org.junit.Test

class SessionSidebarScreenshotE05Test : SessionSidebarRenderTest() {
    @Test fun e05_main_matcha_light() = main(AppColorTheme.MATCHA, false)
    @Test fun e05_main_matcha_dark() = main(AppColorTheme.MATCHA, true)
    @Test fun e05_main_vanilla_light() = main(AppColorTheme.VANILLA, false)
    @Test fun e05_main_vanilla_dark() = main(AppColorTheme.VANILLA, true)
    @Test fun e05_main_ocean_light() = main(AppColorTheme.OCEAN, false)
    @Test fun e05_main_ocean_dark() = main(AppColorTheme.OCEAN, true)
    @Test fun e05_main_dynamic_light() = dynamic(false)
    @Test fun e05_main_dynamic_dark() = dynamic(true)
    @Test fun e05_main_narrow_light() = narrow(false)
    @Test fun e05_main_narrow_dark() = narrow(true)
    @Test fun e05_theme_narrow_light() = theme(false)
    @Test fun e05_theme_narrow_dark() = theme(true)
    @Test fun e05_preset_sheet_light() {
        val case = ScreenshotCases.require("E05_preset-sheet-light")
        renderSidebar(case)
        open("本会话预设")
        composeRule.onNode(hasScrollToNodeAction() and hasAnyAncestor(isRoot() and hasAnyDescendant(hasText("管理预设库"))))
            .performScrollToNode(hasText("预设8：长篇叙事与细腻情绪的完整规则"))
        composeRule.onNodeWithText("预设8：长篇叙事与细腻情绪的完整规则").assertIsDisplayed()
        captureWindow(case, "本会话预设", "管理预设库")
    }
    @Test fun e05_mask_sheet_dark() {
        val case = ScreenshotCases.require("E05_mask-sheet-dark")
        renderSidebar(case, dark = true)
        open("用户身份"); open("本角色面具")
        composeRule.onNode(hasScrollToNodeAction() and hasAnyAncestor(isRoot() and hasAnyDescendant(hasText("管理面具"))))
            .performScrollToNode(hasText("面具8：在漫长雨夜中旅行的记录者"))
        composeRule.onNodeWithText("面具8：在漫长雨夜中旅行的记录者").assertIsDisplayed()
        captureWindow(case, "本角色面具", "管理面具")
    }
    @Test fun e05_help_dialog_dark() {
        val case = ScreenshotCases.require("E05_help-dialog-dark")
        renderSidebar(case, dark = true); open("主题")
        composeRule.onNodeWithContentDescription("线上背景玻璃气泡：查看说明").performScrollTo().performClick()
        composeRule.onNode(hasScrollAction() and hasAnyAncestor(windowMatcher("线上背景玻璃气泡")), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10000f) }
        captureWindow(case, "线上背景玻璃气泡", "知道了")
    }
    @Test fun e05_model_sheet_light() {
        val case = ScreenshotCases.require("E05_model-sheet-light")
        renderSidebar(case); open("快捷切换"); open("切换模型")
        composeRule.onNode(hasText(LongModelName) and hasAnyAncestor(windowMatcher("选择模型")), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        captureWindow(case, "选择模型", LongModelName)
    }
    @Test fun e05_reset_dialog_dark() {
        val case = ScreenshotCases.require("E05_reset-dialog-dark")
        renderSidebar(case, dark = true); open("重置")
        captureWindow(case, "清空剧情", "取消")
    }
    @Test fun e05_group_narrow_light() {
        val case = ScreenshotCases.require("E05_group-narrow-light")
        renderSidebar(case, group = true); open("群聊设置"); captureSidebar(case)
    }
    private fun main(theme: AppColorTheme, dark: Boolean) {
        val case = ScreenshotCases.require("E05_main-${theme.storageValue}-${if (dark) "dark" else "light"}")
        renderSidebar(case, theme, dark); captureSidebar(case)
    }
    private fun dynamic(dark: Boolean) {
        val case = ScreenshotCases.require("E05_main-dynamic-${if (dark) "dark" else "light"}")
        renderSidebar(case, dark = dark, dynamic = true); captureSidebar(case)
    }
    private fun narrow(dark: Boolean) {
        val case = ScreenshotCases.require("E05_main-narrow-${if (dark) "dark" else "light"}")
        renderSidebar(case, dark = dark); open("高级选项")
        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST).performScrollToNode(hasText("重置"))
        captureSidebar(case)
    }
    private fun theme(dark: Boolean) {
        val case = ScreenshotCases.require("E05_theme-narrow-${if (dark) "dark" else "light"}")
        renderSidebar(case, dark = dark); open("主题")
        composeRule.onNodeWithTag("roleplay_line_height_relaxed").performScrollTo().assertIsDisplayed()
        captureSidebar(case)
    }
}
