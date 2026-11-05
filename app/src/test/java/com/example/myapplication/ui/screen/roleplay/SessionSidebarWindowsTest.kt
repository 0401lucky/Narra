package com.example.myapplication.ui.screen.roleplay

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotFixture
import com.example.myapplication.ui.screenshot.SessionSidebarRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionSidebarWindowsTest : SessionSidebarRenderTest() {
    @Test
    fun presetSheet_selectLastAndManageAndDismiss_haveSeparateCallbacks() {
        renderSidebar(ScreenshotCases.require("E05_preset-sheet-light"))
        open("本会话预设")
        scrollWindow("本会话预设", "预设8：长篇叙事与细腻情绪的完整规则")
        windowText("本会话预设", "预设8：长篇叙事与细腻情绪的完整规则").performClick()
        windowGone("本会话预设")
        assertEquals(listOf("preset:preset-8"), events)
        open("本会话预设")
        windowText("本会话预设", "管理预设库").assertIsDisplayed().performClick()
        assertEquals(listOf("preset:preset-8", "manage-presets"), events)
        open("本会话预设")
        dismissSheet("本会话预设")
        assertEquals(listOf("preset:preset-8", "manage-presets"), events)
    }

    @Test
    fun maskSheet_lastChoiceAndManageAndDismiss_doNotSaveGlobalSettings() {
        renderSidebar(ScreenshotCases.require("E05_mask-sheet-dark"), dark = true)
        open("用户身份")
        open("本角色面具")
        scrollWindow("本角色面具", "面具8：在漫长雨夜中旅行的记录者")
        windowText("本角色面具", "面具8：在漫长雨夜中旅行的记录者").performClick()
        windowGone("本角色面具")
        assertEquals(listOf("mask:mask-8"), events)
        open("本角色面具")
        windowText("本角色面具", "管理面具").assertIsDisplayed().performClick()
        open("本角色面具")
        dismissSheet("本角色面具")
        assertEquals(listOf("mask:mask-8", "manage-masks"), events)
    }

    @Test
    fun modelSheet_providerAndModelCallbacks_preserveSelectionTiming() {
        renderSidebar(ScreenshotCases.require("E05_model-sheet-light"))
        open("快捷切换")
        open("切换模型")
        windowText("选择模型", "合成备用提供商").performClick()
        assertEquals(listOf("provider:fixture-provider-backup"), events)
        windowRoot("选择模型").assertExists()
        windowText("选择模型", "fixture-backup").performScrollTo().performClick()
        assertEquals(listOf("provider:fixture-provider-backup", "model:fixture-provider-backup:fixture-backup"), events)
        windowGone("选择模型")
        open("切换模型")
        dismissSheet("选择模型")
        assertEquals(2, events.size)
        assertFalse(events.contains("back"))
    }

    @Test
    fun restart_cancelDoesNothingAndConfirmWaitsForSuccessCallback() {
        assertConfirmFlow("重启", "重开剧情", "重开", "restart") { pendingRestart }
    }

    @Test
    fun reset_cancelDoesNothingAndConfirmWaitsForSuccessCallback() {
        assertConfirmFlow("重置", "清空剧情", "确定清空", "reset") { pendingReset }
    }

    @Test
    fun addMember_actualDialogCancelAndSelection_areIndependent() {
        renderSidebar(ScreenshotCases.require("E05_main-narrow-light"))
        composeRule.onNodeWithContentDescription("添加成员").performClick()
        windowRoot("添加成员").assertExists()
        windowText("添加成员", "取消").performClick()
        assertEquals(emptyList<String>(), events)
        composeRule.onNodeWithContentDescription("添加成员").performClick()
        windowText("添加成员", "林澈").performClick()
        windowGone("添加成员")
        assertEquals(listOf("add:${ScreenshotFixture.LinchenAssistant.id}"), events)
    }

    @Test
    fun groupMuteAndRemove_cancelAndConfirmOnlyAffectNamedMember() {
        renderSidebar(ScreenshotCases.require("E05_group-narrow-light"), group = true)
        open("群聊设置")
        val name = "苏晚：名字很长的群聊参与成员"
        composeRule.onNodeWithContentDescription("$name：禁言").performScrollTo().performClick()
        assertEquals(listOf("mute:member-0"), events)
        composeRule.onNodeWithContentDescription("$name：移除成员").performScrollTo().performClick()
        windowText("移除群成员", "取消").performClick()
        assertEquals(listOf("mute:member-0"), events)
        composeRule.onNodeWithContentDescription("$name：移除成员").performClick()
        windowText("移除群成员", "移除").performClick()
        assertEquals(listOf("mute:member-0", "remove:member-0"), events)
        windowGone("移除群成员")
    }

    @Test
    fun longformDigits_editSanitizesAndCallsUpdateOnce() {
        renderSidebar(
            ScreenshotCases.require("E05_main-narrow-light"),
            scenarioOverride = ScreenshotFixture.SuwanScenario.copy(interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM),
        )
        open("情景设定")
        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST).performScrollToNode(hasText("默认字数"))
        composeRule.onNodeWithText("默认字数").performScrollTo().performTextReplacement("a12345")
        assertEquals(listOf("chars:1234"), events)
    }

    @Test
    fun portraitUrl_keepsImmediateScenarioCallbackAndGlobalDraftUntouched() {
        renderSidebar(ScreenshotCases.require("E05_main-narrow-light"))
        open("背景与立绘")
        composeRule.onAllNodesWithText("图片链接（可选）").onFirst().performScrollTo()
            .performTextReplacement("https://fixture.invalid/portrait.png")
        assertEquals(listOf("visual:||https://fixture.invalid/portrait.png||"), events)
        assertEquals(skin, settings.value.roleplayNoBackgroundSkin)
    }

    private fun assertConfirmFlow(
        entry: String,
        title: String,
        confirm: String,
        event: String,
        pending: () -> (() -> Unit)?,
    ) {
        renderSidebar(ScreenshotCases.require("E05_main-narrow-light"))
        open(entry)
        windowText(title, "取消").assertIsDisplayed().performClick()
        assertEquals(emptyList<String>(), events)
        open(entry)
        windowText(title, confirm).assertIsDisplayed().performClick()
        assertEquals(listOf(event), events)
        assertNotNull(pending())
        composeRule.onAllNodesWithText("会话设定").onFirst().assertIsDisplayed()
        composeRule.runOnIdle { pending()!!.invoke() }
        assertEquals(listOf(event, "back"), events)
    }

    private fun windowText(title: String, text: String) = composeRule.onNode(
        hasText(text) and hasAnyAncestor(windowMatcher(title)), useUnmergedTree = true,
    )

    private fun scrollWindow(title: String, text: String) {
        composeRule.onNode(
            hasScrollToNodeAction() and hasAnyAncestor(windowMatcher(title)), useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
        windowText(title, text).assertIsDisplayed()
    }

    private fun dismissSheet(title: String) {
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss) and hasAnyAncestor(windowMatcher(title)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        composeRule.waitForIdle()
        windowGone(title)
    }

    private fun windowGone(title: String) {
        composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
    }
}
