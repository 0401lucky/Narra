package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingsActionsTest : ProviderSettingsRenderTest() {
    @Test
    fun list_largeFontSearchAndBothProviderCards_remainReachableWithIndependentNavigation() {
        renderProvider(list = true)
        val search = pageControl(hasSetTextAction())
        assertTouchTarget(search)
        search.performTextInput("fixture-model-5")
        val first = reveal(hasText(LongProviderName) and hasClickAction())
        assertTouchTarget(first, pageScroll())
        first.performTouchInput { click() }
        composeRule.onNodeWithText(OtherProviderName).assertDoesNotExist()
        reveal(hasSetTextAction()).performTextReplacement("no-fixture-match")
        pageControl(hasText("没有匹配的提供商")).assertIsDisplayed()
        reveal(hasSetTextAction()).performTextClearance()
        scrollToEnd()
        val last = pageControl(hasText(OtherProviderName) and hasClickAction())
        assertTouchTarget(last, pageScroll())
        last.performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf("open:$ProviderId", "open:$OtherProviderId"), events)
            assertTrue(healthRequests.isEmpty())
        }
    }

    @Test
    fun template_waitsForTheReturnedDraftId_andNavigatesExactlyOnce() {
        renderProvider(list = true)
        pageControl(hasText("新增") and hasClickAction()).performTouchInput { click() }
        windowControl(
            "选择提供商模板",
            hasText("OpenAI") and hasClickAction()
        ).performTouchInput { click() }
        composeRule.runOnIdle {
            assertFalse(events.any { it.startsWith("open:") })
            state.value =
                state.value.copy(providers = state.value.providers + fixtureProvider().copy(id = "unrelated-draft"))
        }
        composeRule.waitForIdle()
        assertFalse(events.any { it.startsWith("open:") })
        composeRule.runOnIdle {
            state.value =
                state.value.copy(providers = state.value.providers + fixtureProvider().copy(id = CreatedProviderId))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { updateProvider(CreatedProviderId) { it.copy(name = "新草稿已出现") } }
        composeRule.waitForIdle()
        assertEquals(listOf("open:$CreatedProviderId"), events.filter { it.startsWith("open:") })
    }

    @Test
    fun template_cancelOnlyDismisses_andMissingWindowsCannotFallbackToStage() {
        renderProvider(list = true)
        listOf(
            "选择提供商模板",
            "选择模型",
            "手动添加模型",
            "覆盖模型能力",
            "清空模型",
            "接口协议"
        ).forEach { title ->
            assertThrows(AssertionError::class.java) { windowRoot(title) }
        }
        pageControl(hasText("新增") and hasClickAction()).performTouchInput { click() }
        val cancel = windowControl("选择提供商模板", hasText("取消") and hasClickAction())
        assertTouchTarget(cancel, windowRoot("选择提供商模板"))
        cancel.performTouchInput { click() }
        composeRule.onNode(windowMatcher("选择提供商模板"), useUnmergedTree = true)
            .assertDoesNotExist()
        assertEquals(listOf("show-template", "dismiss-template"), events)
    }

    @Test
    fun healthRequests_followOriginalCredentialsKey_andRemainLocal() {
        state.value =
            state.value.copy(connectionHealthMap = mapOf(ProviderId to ConnectionHealth.HEALTHY))
        renderProvider(list = true)
        assertEquals(listOf(OtherProviderId), healthRequests)
        composeRule.runOnIdle {
            updateProvider(OtherProviderId) {
                it.copy(
                    name = "名称修改不重复检测",
                    enabled = true
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(OtherProviderId), healthRequests)
        composeRule.runOnIdle { updateProvider(OtherProviderId) { it.copy(baseUrl = "https://changed.fixture.invalid/") } }
        composeRule.waitForIdle()
        assertEquals(listOf(OtherProviderId, OtherProviderId), healthRequests)
        composeRule.runOnIdle { updateProvider(OtherProviderId) { it.copy(apiKey = "") } }
        composeRule.waitForIdle()
        assertEquals(2, healthRequests.size)
    }

    @Test
    fun emptyDraftList_requestsExistingEnsureCallback_withoutCreatingAService() {
        state.value = state.value.copy(providers = emptyList(), selectedProviderId = "")
        renderProvider(ScreenshotCases.require("E11_list-matcha-light"), list = true)
        pageControl(hasText("还没有提供商")).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("ensure"), events) }
    }

    @Test
    fun missingProvider_keepsExistingNoticeAndBackCallback() {
        activeProviderId.value = "missing-fixture-id"
        renderProvider()
        pageControl(hasText("没有找到这个提供商")).assertIsDisplayed()
        pageControl(hasContentDescription("返回")).performTouchInput { click() }
        assertEquals(listOf("back"), events)
    }
}
