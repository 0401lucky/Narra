package com.example.myapplication.ui.component.roleplay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.ui.screenshot.*
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class OfflineRoleplayAccessibilityTest : OfflineRoleplayRenderTest() {
    @Test fun expandedSuggestionsAndQuickActions_withProposal_keepReadableViewportAndSendReachable() {
        useNarrowFontScale()
        val input = mutableStateOf("你好")
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"),
            scenario = OfflineRoleplayFixture.DialogueScenario.copy(characterDisplayNameOverride = OfflineRoleplayFixture.LongName),
            messages = listOf(ScreenshotFixture.userMessage("visible", "可读消息正文")),
            suggestions = OfflineRoleplayFixture.Suggestions, presence = true, input = input,
            proposal = PendingMemoryProposal(id = "p04-proposal", conversationId = "p04-conversation",
                scopeType = MemoryScopeType.ASSISTANT, content = "她希望一直记住雨夜便利店里这场温暖的相遇。",
                createdAt = ScreenshotFixture.FixedEpochMillis),
            quickActions = listOf(RoleplayInputQuickAction("钱包", Icons.Default.Wallet, Color.Blue, {})),
        )
        composeRule.onNodeWithContentDescription("更多玩法").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        val viewport = composeRule.onNodeWithTag("roleplay_scene_message_list").fetchSemanticsNode().boundsInRoot
        assertTrue("展开组合至少保留一行200%正文的阅读空间，实际=$viewport", viewport.height >= 96f)
        val body = composeRule.onNodeWithText("可读消息正文", substring = true)
        body.assertIsDisplayed()
        val layout = textLayout(body)
        val bounds = body.fetchSemanticsNode().boundsInRoot
        assertTrue("至少首行正文必须完整可读", viewport.height >= layout.getLineBottom(0))
        assertTrue("正文应位于消息视口内", bounds.bottom <= viewport.bottom)
        assertLayout()
    }

    @Test fun headerActions_haveDistinctTouchTargetsAndCallbacks_andRestoreChrome() {
        useNarrowFontScale()
        var back = 0
        var settings = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"),
            scenario = OfflineRoleplayFixture.DialogueScenario.copy(characterDisplayNameOverride = OfflineRoleplayFixture.LongName),
            onBack = { back++ }, onSettings = { settings++ })
        val backButton = composeRule.onNodeWithContentDescription("返回")
        val settingsButton = composeRule.onNodeWithContentDescription("沉浸设置")
        val title = composeRule.onNodeWithTag("roleplay_scene_title")
        listOf(backButton, title, settingsButton).forEach { assertTouch(it) }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertTrue(backButton.fetchSemanticsNode().boundsInRoot.right <= title.fetchSemanticsNode().boundsInRoot.left)
        assertTrue(title.fetchSemanticsNode().boundsInRoot.right <= settingsButton.fetchSemanticsNode().boundsInRoot.left)
        backButton.performTouchInput { click() }
        settingsButton.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, back); assertEquals(1, settings) }
        title.performClick()
        val handle = composeRule.onNodeWithTag("roleplay_chrome_handle")
        assertTouch(handle)
        handle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        title.assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, back); assertEquals(1, settings) }
    }

    @Test fun suggestions_applyRefreshCollapse_areNamedAndInvokeRealCallbacks() {
        useNarrowFontScale()
        var applied = ""
        var refresh = 0
        var cleared = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"),
            suggestions = OfflineRoleplayFixture.Suggestions, onApply = { applied = it },
            onGenerate = { refresh++ }, onClear = { cleared++ })
        val list = composeRule.onNodeWithTag("roleplay_scene_suggestions")
        val suggestion = OfflineRoleplayFixture.Suggestions.first()
        list.performScrollToNode(hasTestTag("roleplay_suggestion_${suggestion.id}"))
        val action = composeRule.onNodeWithTag("roleplay_suggestion_${suggestion.id}")
        action.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertTouch(action)
        action.performTouchInput { click() }
        list.performScrollToIndex(0)
        val refreshButton = composeRule.onNodeWithText("换一批")
        refreshButton.performScrollTo()
        refreshButton.performClick()
        composeRule.onNodeWithText("收起").performClick()
        composeRule.runOnIdle { assertEquals(suggestion.text, applied); assertEquals(1, refresh); assertEquals(1, cleared) }
        assertLayout()
    }

    @Test fun collapsedSuggestion_generates_andSendingLongform_canStopAndCancel() {
        useNarrowFontScale()
        var generated = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"), messages = emptyList(), onGenerate = { generated++ })
        val generate = composeRule.onNode(hasClickAction() and hasText("AI帮写", substring = true))
        assertTouch(generate)
        generate.performClick()
        composeRule.runOnIdle { assertEquals(1, generated) }
    }

    @Test fun longformSending_stopAndCancelRemainReachable_withPresence() {
        useNarrowFontScale()
        var cancelled = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-dark"), dark = true,
            scenario = OfflineRoleplayFixture.LongformScenario, presence = true, sending = true,
            messages = listOf(OfflineRoleplayFixture.MixedMessages.last().copy(isStreaming = true)), onCancel = { cancelled++ })
        val stop = composeRule.onNodeWithContentDescription("取消")
        assertTouch(stop)
        stop.performClick()
        val cancel = composeRule.onNodeWithText("取消")
        assertTouch(composeRule.onNode(hasClickAction() and hasText("取消")))
        cancel.performClick()
        composeRule.onNodeWithTag("roleplay_input_editor").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(2, cancelled) }
        assertLayout()
    }

    @Test fun generatingSuggestion_cancelKeepsInputAndMessageAccessible() {
        useNarrowFontScale()
        var cancelled = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"), generating = true, onClear = { cancelled++ })
        val list = composeRule.onNodeWithTag("roleplay_scene_suggestions")
        list.performScrollToNode(hasText("取消"))
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(1, cancelled) }
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        assertLayout()
    }

    @Test fun inputSendQuickActionAndMemoryProposal_keepCallbacksAndIndependentTargets() {
        useNarrowFontScale()
        val input = mutableStateOf("")
        var sent = 0
        var wallet = 0
        var approve = 0
        var reject = 0
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-light"), input = input,
            onInput = { input.value = it }, onSend = { sent++ },
            proposal = PendingMemoryProposal(id = "proposal-actions", conversationId = "p04-conversation",
                scopeType = MemoryScopeType.GLOBAL, content = "请记住这场相遇。", createdAt = ScreenshotFixture.FixedEpochMillis),
            onApprove = { approve++ }, onReject = { reject++ },
            quickActions = listOf(RoleplayInputQuickAction("钱包", Icons.Default.Wallet, Color.Blue, { wallet++ })))
        composeRule.onNodeWithText("记住").performScrollTo().performClick()
        composeRule.onNodeWithText("忽略").performScrollTo().performClick()
        composeRule.onNodeWithTag("roleplay_input_editor").performTextInput("来自真实编辑器的消息")
        composeRule.onNodeWithContentDescription("更多玩法").performClick()
        composeRule.onNodeWithText("钱包").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.runOnIdle {
            assertEquals("来自真实编辑器的消息", input.value)
            assertEquals(1, sent); assertEquals(1, wallet); assertEquals(1, approve); assertEquals(1, reject)
        }
    }

    @Test fun speakingPortrait_largeFont_keepsNameFallbackAndStatusApart() {
        useNarrowFontScale()
        renderOfflinePage(ScreenshotCases.require("E04_scene-narrow-dark"), dark = true,
            scenario = OfflineRoleplayFixture.LongformScenario, presence = true, sending = true,
            showHelper = false)
        composeRule.onNodeWithText("说话中…", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val fallback = composeRule.onNodeWithText("角色", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithText("说话中…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertFalse("立绘状态不能覆盖角色信息：$fallback / $status", fallback.overlaps(status))
    }

    @Test fun dialogueList_scrollsFromFirstMessageToEnd_withInputStillReachable() {
        val messages = (1..40).map { index -> ScreenshotFixture.characterMessage("scroll-$index", "第 $index 条对白正文") }
        renderOfflinePage(ScreenshotCases.require("E04_scene-matcha-light"), messages = messages, showHelper = false)
        val list = composeRule.onNodeWithTag("roleplay_scene_message_list")
        list.performScrollToIndex(0)
        composeRule.onNodeWithText(messages.first().content).assertIsDisplayed()
        list.performScrollToIndex(messages.lastIndex)
        composeRule.onNodeWithText(messages.last().content).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
    }

    private fun assertLayout() {
        val header = composeRule.onNodeWithTag("roleplay_scene_header").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("roleplay_scene_message_list").fetchSemanticsNode().boundsInRoot
        val input = composeRule.onNodeWithTag("roleplay_scene_input").fetchSemanticsNode().boundsInRoot
        assertTrue("顶栏应在消息之前", header.bottom <= list.top)
        assertTrue("正文必须保留可读高度", list.height >= 96f)
        assertTrue("输入区不遮挡消息", list.bottom <= input.top)
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode().boundsInRoot
        assertTrue("输入应在页面内", input.bottom <= stage.bottom)
    }

    private fun assertTouch(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("触控宽度不足：$bounds", bounds.width >= minimum - 0.5f)
        assertTrue("触控高度不足：$bounds", bounds.height >= minimum - 0.5f)
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
