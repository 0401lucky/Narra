package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelsAccessibilityTest : ProviderSettingsRenderTest() {
    @Test
    fun finalModel_largeFontActionsRemainUnclippedAndDoNotSelectTheCard() {
        renderProvider()
        selectModels()
        scrollToEnd()
        val card = hasText(LastModelName) and isSelectable()
        val edit = pageControl(hasContentDescription("设置模型能力") and hasAnyAncestor(card))
        val remove = pageControl(hasContentDescription("移除模型") and hasAnyAncestor(card))
        val add = pageControl(hasText("添加新模型") and hasClickAction())
        val fetch = pageControl(hasContentDescription("获取模型"))
        val tab = pageControl(hasText("模型") and hasClickAction())
        listOf(edit, remove).forEach { assertTouchTarget(it, pageScroll()); assertAbove(it, add) }
        listOf(add, fetch, tab).forEach { assertTouchTarget(it) }
        assertAbove(add, tab)
        assertFalse(edit.fetchSemanticsNode().touchBoundsInRoot.overlaps(remove.fetchSemanticsNode().touchBoundsInRoot))
        edit.performTouchInput { click(Offset(width - 1f, center.y)) }
        windowControl(
            "覆盖模型能力",
            hasText("取消") and hasClickAction()
        ).performTouchInput { click() }
        remove.performTouchInput { click(Offset(1f, center.y)) }
        assertEquals(listOf("remove:$ProviderId:$LastModelId"), events)
        pageControl(card).performTouchInput { click(Offset(center.x, height / 3f)) }
        pageControl(card).assertIsSelected()
        assertEquals(
            listOf("remove:$ProviderId:$LastModelId", "select:$ProviderId:$LastModelId"),
            events
        )
    }

    @Test
    fun loadingAndMissingCredentials_disableOnlyFetch_andExposeLoadingSemantics() {
        state.value = state.value.copy(isLoadingModels = true, loadingProviderId = ProviderId)
        renderProvider()
        selectModels()
        val fetch = pageControl(hasContentDescription("获取模型"))
        fetch.assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在获取模型"
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
            .performTouchInput { click() }
        pageControl(hasText("添加新模型") and hasClickAction()).assertIsEnabled()
        composeRule.runOnIdle {
            state.value = state.value.copy(loadingProviderId = OtherProviderId)
        }
        fetch.assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "正在获取其他提供商的模型"
            )
        )
        composeRule.runOnIdle {
            state.value = state.value.copy(isLoadingModels = false, loadingProviderId = "")
            updateProvider(ProviderId) { it.copy(apiKey = "") }
        }
        fetch.assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "请先填写连接信息"
            )
        )
            .performTouchInput { click() }
        assertTrue(events.isEmpty())
    }

    @Test
    fun pendingFetch_belongsToItsProvider_onlyThatDetailCanConfirmIt() {
        state.value = state.value.copy(
            pendingFetchedModels = fetchedModels(),
            pendingFetchProviderId = OtherProviderId
        )
        renderProvider()
        selectModels()
        composeRule.onNode(windowMatcher("选择模型"), useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle { activeProviderId.value = OtherProviderId }
        selectModels()
        windowRoot("选择模型").assertIsDisplayed()
        windowControl(
            "选择模型",
            hasText("确认选择") and hasClickAction()
        ).performTouchInput { click() }
        assertTrue(events.single { it.startsWith("confirm:") }
            .startsWith("confirm:$OtherProviderId:"))
        assertEquals(1, events.count { it == "dismiss-fetch" })
    }

    @Test
    fun clearModels_cancelDoesNotMutate_andConfirmRunsExactlyOnce() {
        renderProvider()
        selectModels()
        reveal(hasText("清空已添加") and hasClickAction()).performTouchInput { click() }
        windowControl(
            "清空模型",
            hasText("取消") and hasClickAction()
        ).performTouchInput { click() }
        assertTrue(events.isEmpty())
        reveal(hasText("清空已添加") and hasClickAction()).performTouchInput { click() }
        val confirm = windowControl("清空模型", hasText("确认清空") and hasClickAction())
        assertTouchTarget(confirm, windowRoot("清空模型"))
        confirm.performTouchInput { click() }
        assertEquals(listOf("clear:$ProviderId"), events)
        composeRule.onNode(windowMatcher("清空模型"), useUnmergedTree = true).assertDoesNotExist()
    }
}
