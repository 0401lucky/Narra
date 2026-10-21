package com.example.myapplication.ui.component.roleplay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotFixture
import com.example.myapplication.ui.screenshot.ScreenshotRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 实际页面交互与 root 坐标验证；不写入或接纳截图基准。 */
class PilotAccessibilityTest : ScreenshotRenderTest() {
    @Test
    fun onlineJumpActions_scrollToRealFirstAndLastMessages_withAccessibleTargets() {
        val messages = (1..12).map { index ->
            ScreenshotFixture.characterMessage("jump-$index", "第 $index 条合成消息。\n这一行用于撑开真实列表。")
        }
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H01_p03-online-roleplay-matcha-light"), AppColorTheme.MATCHA, false,
            messages = messages,
        )
        val top = composeRule.onNodeWithContentDescription("跳到顶部")
        assertTouchTarget(top)
        top.performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText(messages.first().content.substringBefore('\n'), substring = true).assertIsDisplayed()
        val bottom = composeRule.onNodeWithContentDescription("跳到底部")
        assertTouchTarget(bottom)
        bottom.performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText(messages.last().content.substringBefore('\n'), substring = true).assertIsDisplayed()
    }

    @Test
    fun phoneNarrowLargeFont_tabsFitAndExposeSelection_andNewConversationRemainsReachable() {
        useNarrowFontScale()
        var createdAssistants = 0
        renderMessagesShellPage(
            ScreenshotCases.require("H04_p01-messages-narrow-light"), AppColorTheme.MATCHA, false,
            callbacks = noOpPhoneCallbacks().copy(onOpenAssistantCreate = { createdAssistants++ }),
        )
        val bar = composeRule.onNodeWithTag("phone_bottom_bar").fetchSemanticsNode().boundsInRoot
        listOf("Messages" to "消息", "Contacts" to "通讯录", "Discover" to "发现", "Profile" to "我").forEach { (id, label) ->
            val tab = composeRule.onNodeWithTag("phone_tab_$id")
            assertTouchTarget(tab)
            tab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            val caption = composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("phone_tab_$id")), useUnmergedTree = true)
            val bounds = caption.fetchSemanticsNode().boundsInRoot
            assertTrue("$label must fit nav bottom", bounds.bottom < bar.bottom)
            val layout = textLayout(caption)
            assertFalse("$label must not be clipped: size=${layout.size}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, lines=${layout.lineCount}, lineBottom=${layout.getLineBottom(layout.lineCount - 1)}", layout.hasVisualOverflow)
        }
        composeRule.onNodeWithTag("phone_tab_Contacts").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("新增").performClick()
        composeRule.runOnIdle { assertEquals(1, createdAssistants) }
        composeRule.onNodeWithTag("phone_tab_Messages").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("新增").performClick()
        composeRule.onNodeWithText("新建会话").performClick()
        composeRule.onNodeWithText("选择一个角色").assertIsDisplayed()
    }

    @Test
    fun assistantsNarrowLargeFont_searchSelectionAndMoreMenu_workOnRealViewModel() {
        useNarrowFontScale()
        renderAssistantListPage(ScreenshotCases.require("H04_p02-assistants-narrow-dark"), AppColorTheme.OCEAN, true)
        val card = composeRule.onNodeWithTag("assistant_card_${ScreenshotFixture.LinchenAssistant.id}")
        card.performScrollTo().performClick()
        drainMainLooper()
        card.assertIsSelected()
        val search = composeRule.onNodeWithTag("assistant_search")
        search.performScrollTo().performTextInput("不存在的合成角色")
        composeRule.onNodeWithText("没有匹配的角色").assertIsDisplayed()
        search.performTextClearance()
        val menu = composeRule.onAllNodesWithContentDescription("更多操作")[0]
        assertTouchTarget(menu)
        menu.performClick()
        composeRule.onNodeWithText("进入设置").assertIsDisplayed()
    }

    @Test
    fun onlineNarrowLargeFont_headerMessagesAndInputDoNotOverlap_andActionsStillWork() {
        useNarrowFontScale()
        val input = mutableStateOf("")
        var sent = 0
        var returned = 0
        var openedSettings = 0
        var openedWallet = 0
        val message = ScreenshotFixture.userMessage("large-first", "你好，今天见。")
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H04_p03-online-narrow-light"), AppColorTheme.MATCHA, false,
            messages = listOf(message), inputState = input,
            onInputChange = { input.value = it }, onSend = { sent++ }, onNavigateBack = { returned++ },
            onOpenSettings = { openedSettings++ }, onOpenWallet = { openedWallet++ },
        )
        val header = composeRule.onNodeWithTag("roleplay_online_header").fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("roleplay_online_message_list").fetchSemanticsNode().boundsInRoot
        val bubble = composeRule.onNodeWithTag("roleplay_dialogue_bubble_${message.uiItemId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val inputBounds = composeRule.onNodeWithTag("roleplay_online_input").fetchSemanticsNode().boundsInRoot
        assertTrue("header must not occlude the list", header.bottom <= viewport.top)
        assertTrue("first message must start below header", bubble.top >= header.bottom)
        assertTrue("message viewport must end above input", viewport.bottom <= inputBounds.top)
        val placeholder = composeRule.onNodeWithText("随便聊聊…", useUnmergedTree = true)
        val placeholderLayout = textLayout(placeholder)
        assertFalse("placeholder overflow width=${placeholderLayout.didOverflowWidth} height=${placeholderLayout.didOverflowHeight} size=${placeholderLayout.size}", placeholderLayout.hasVisualOverflow)
        assertTrue(placeholder.fetchSemanticsNode().boundsInRoot.bottom <= inputBounds.bottom)
        listOf("返回", "更多", "发送", "更多玩法").forEach { assertTouchTarget(composeRule.onNodeWithContentDescription(it)) }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("roleplay_input_editor").performTextInput("测试输入")
        composeRule.onNodeWithContentDescription("更多玩法").performClick()
        composeRule.onNodeWithText("钱包").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("测试输入", input.value)
            assertEquals(1, sent)
            assertEquals(1, returned)
            assertEquals(1, openedSettings)
            assertEquals(1, openedWallet)
        }
    }

    @Test
    fun onlineNarrowLargeFont_stopRemainsNamedAndReachable() {
        useNarrowFontScale()
        var cancelled = 0
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H04_p03-online-narrow-dark"), AppColorTheme.VANILLA, true,
            messages = listOf(ScreenshotFixture.characterMessage("streaming", "正在生成", isStreaming = true)),
            isSending = true, onCancel = { cancelled++ },
        )
        val stop = composeRule.onNodeWithContentDescription("停止生成")
        assertTouchTarget(stop)
        stop.performClick()
        val cancel = composeRule.onNodeWithContentDescription("取消")
        assertTouchTarget(cancel)
        cancel.assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(2, cancelled) }
    }

    @Test
    fun onlineAiHelper_primaryActionUsesAccessibleTouchTarget_whenExpandedAtLargeFont() {
        useNarrowFontScale()
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H04_p03-online-narrow-light"), AppColorTheme.MATCHA, false,
            messages = emptyList(), showAiHelper = true,
        )
        composeRule.onNodeWithContentDescription("AI 帮写").performClick()
        val action = composeRule.onNodeWithTag("roleplay_ai_helper_primary_action")
        action.performScrollTo().assertIsDisplayed()
        assertTouchTarget(action)
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
    }

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode().boundsInRoot
        assertTrue("target width ${bounds.width} < $minimum", bounds.width >= minimum - 0.5f)
        assertTrue("target height ${bounds.height} < $minimum", bounds.height >= minimum - 0.5f)
        assertTrue("target must fit stage: $bounds vs $stage", bounds.left >= stage.left && bounds.top >= stage.top && bounds.right <= stage.right && bounds.bottom <= stage.bottom)
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
