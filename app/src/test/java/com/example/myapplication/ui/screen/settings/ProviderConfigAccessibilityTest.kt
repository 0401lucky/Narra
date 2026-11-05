package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigAccessibilityTest : ProviderSettingsRenderTest() {
    @Test
    fun veryLongProviderTitle_keepsTheFormUsable_andCanScrollItsCompleteName() {
        val longName = LongProviderName.repeat(4)
        updateProvider(ProviderId) { it.copy(name = longName) }
        renderProvider()
        scrollToEnd()
        assertTouchTarget(pageControl(hasText("保存设置") and hasClickAction()), pageScroll())
        val heading = hasText(longName) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val titleScroll = composeRule.onNode(hasScrollAction() and hasAnyDescendant(heading))
        scrollToEnd(titleScroll, "title")
        assertTrue(titleScroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        assertTextFits(composeRule.onNode(heading, useUnmergedTree = true))
        assertTouchTarget(pageControl(hasContentDescription("返回")))
    }

    @Test
    fun configuration_largeFontLabelsValuesAndCallbacks_preserveProviderIdentity() {
        renderProvider()
        val toggle = pageControl(isToggleable() and hasText("是否启用"))
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertTouchTarget(toggle)
        toggle.performTouchInput { click() }
        val edits = listOf(
            "名称" to "本地合成草稿的新名称",
            "服务地址" to "https://edited.fixture.invalid/v1/",
            "请求路径" to "/custom/new/chat"
        )
        edits.forEach { (label, value) ->
            val input = reveal(hasText(label) and hasSetTextAction())
            assertTouchTarget(input, pageScroll())
            input.performTextReplacement(value)
        }
        val key = reveal(hasText("API Key") and hasSetTextAction())
        assertTouchTarget(key, pageScroll())
        key.performTextReplacement("synthetic-only-replacement")
        key.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.runOnIdle {
            val provider = state.value.providers.single { it.id == ProviderId }
            assertEquals(edits[0].second, provider.name)
            assertEquals(edits[1].second, provider.baseUrl)
            assertEquals(edits[2].second, provider.chatCompletionsPath)
            assertEquals("synthetic-only-replacement", provider.apiKey)
            assertFalse(provider.enabled)
            assertEquals(fixtureState().providers[1], state.value.providers[1])
            assertEquals(1, events.count { it == "toggle:$ProviderId" })
        }
    }

    @Test
    fun protocolAndMode_largeFontOptions_changeOnlyTheirOwnFields() {
        renderProvider()
        val responses = reveal(hasText("Responses API") and isSelectable())
        assertTouchTarget(responses, pageScroll())
        responses.performTouchInput { click() }.assertIsSelected()
        val anthropic = reveal(hasText("Anthropic") and isSelectable())
        assertTouchTarget(anthropic, pageScroll())
        anthropic.performTouchInput { click() }.assertIsSelected()
        composeRule.onNodeWithText("请求路径").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                listOf("mode:$ProviderId:RESPONSES", "protocol:$ProviderId:ANTHROPIC"),
                events
            )
            assertEquals(ProviderApiProtocol.ANTHROPIC, state.value.providers[0].apiProtocol)
            assertEquals(OpenAiTextApiMode.RESPONSES, state.value.providers[0].openAiTextApiMode)
        }
    }

    @Test
    fun tabsAndKeyVisibility_areKeyedByProviderId() {
        renderProvider()
        reveal(hasContentDescription("显示密钥")).performTouchInput { click() }
        pageControl(hasContentDescription("隐藏密钥")).assertIsDisplayed()
        assertEquals(
            fixtureProvider().apiKey,
            textLayout(pageControl(hasText("API Key") and hasSetTextAction())).layoutInput.text.text
        )
        selectModels()
        pageControl(hasText("模型") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        composeRule.runOnIdle { activeProviderId.value = OtherProviderId }
        composeRule.waitForIdle()
        pageControl(hasText("配置") and hasClickAction()).assertIsSelected()
        reveal(hasText("API Key") and hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        pageControl(hasContentDescription("显示密钥")).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun longHeaderAndSaveArea_largeFont_remainAboveTabsWithFullTouchTargets() {
        renderProvider()
        assertTextFits(
            composeRule.onNode(
                hasText(LongProviderName) and SemanticsMatcher.keyIsDefined(
                    SemanticsProperties.Heading
                ), useUnmergedTree = true
            )
        )
        val back = pageControl(hasContentDescription("返回"))
        assertTouchTarget(back)
        scrollToEnd()
        val save = pageControl(hasText("保存设置") and hasClickAction())
        val delete = pageControl(hasContentDescription("删除提供商"))
        val config = pageControl(hasText("配置") and hasClickAction())
        val models = pageControl(hasText("模型") and hasClickAction())
        assertTouchTarget(save, pageScroll())
        assertTouchTarget(delete, pageScroll())
        assertTouchTarget(config)
        assertTouchTarget(models)
        assertAbove(save, config)
        assertAbove(delete, config)
        assertFalse(save.fetchSemanticsNode().touchBoundsInRoot.overlaps(delete.fetchSemanticsNode().touchBoundsInRoot))
        save.performTouchInput { click() }
        assertEquals(listOf("save"), events)
    }

    @Test
    fun saveWhileInFlight_isDisabledAndDoesNotRepeatCallback() {
        state.value = state.value.copy(isSaving = true)
        renderProvider()
        scrollToEnd()
        val save = pageControl(hasText("保存中...") and hasClickAction())
        save.assertIsNotEnabled().performTouchInput { click() }
        assertTrue(events.isEmpty())
        composeRule.runOnIdle { state.value = state.value.copy(isSaving = false) }
        pageControl(hasText("保存设置") and hasClickAction()).assertIsEnabled()
            .performTouchInput { click() }
        assertEquals(listOf("save"), events)
    }

    @Test
    fun lastProviderCannotBeDeleted_otherProvidersKeepExplicitDeleteAndBackOrder() {
        state.value = state.value.copy(providers = listOf(fixtureProvider()))
        renderProvider()
        scrollToEnd()
        pageControl(hasContentDescription("删除提供商")).assertIsNotEnabled()
            .performTouchInput { click() }
        assertTrue(events.isEmpty())
        composeRule.runOnIdle { state.value = fixtureState() }
        pageControl(hasContentDescription("删除提供商")).assertIsEnabled()
            .performTouchInput { click() }
        assertEquals(listOf("delete:$ProviderId", "back"), events)
    }
}
