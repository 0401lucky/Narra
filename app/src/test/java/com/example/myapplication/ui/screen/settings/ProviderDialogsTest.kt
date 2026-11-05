package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDialogsTest : ProviderSettingsRenderTest() {
    @Test
    fun manualInput_emptyIdIsAssociatedWithError_andValidValuesAreTrimmed() {
        renderProvider()
        selectModels()
        pageControl(hasText("添加新模型") and hasClickAction()).performTouchInput { click() }
        val title = "手动添加模型"
        windowControl(title, hasText("添加") and hasClickAction()).performTouchInput { click() }
        val idInput =
            windowControl(title, hasText("模型 ID *") and hasSetTextAction()).performScrollTo()
        idInput.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "模型 ID 不能为空"))
        assertTouchTarget(idInput, windowRoot(title))
        assertTrue(events.isEmpty())
        idInput.performTextReplacement("  custom/model-id  ")
        idInput.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        val nameInput =
            windowControl(title, hasText("显示名称") and hasSetTextAction()).performScrollTo()
        assertTouchTarget(nameInput, windowRoot(title))
        nameInput.performTextReplacement("  合成显示名称  ")
        windowControl(title, hasText("添加") and hasClickAction()).performTouchInput { click() }
        assertEquals(listOf("manual:$ProviderId:custom/model-id:合成显示名称"), events)
        composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun manualCancel_dropsOnlyDialogState_andBlankDisplayNameUsesId() {
        renderProvider()
        selectModels()
        val title = "手动添加模型"
        pageControl(hasText("添加新模型") and hasClickAction()).performTouchInput { click() }
        windowControl(title, hasText("模型 ID *") and hasSetTextAction()).performScrollTo()
            .performTextInput("cancelled")
        windowControl(title, hasText("取消") and hasClickAction()).performTouchInput { click() }
        assertTrue(events.isEmpty())
        pageControl(hasText("添加新模型") and hasClickAction()).performTouchInput { click() }
        windowControl(title, hasText("模型 ID *") and hasSetTextAction()).performScrollTo()
            .performTextInput("  fallback-id  ")
        windowControl(title, hasText("添加") and hasClickAction()).performTouchInput { click() }
        assertEquals(listOf("manual:$ProviderId:fallback-id:fallback-id"), events)
    }

    @Test
    fun abilityCancel_keepsOverrides_andRestoreAutoIsAnExplicitAction() {
        renderProvider()
        selectModels()
        openLastAbilities()
        val title = "覆盖模型能力"
        val option = windowControl(title, hasText("工具") and hasClickAction()).performScrollTo()
        assertTouchTarget(option, windowRoot(title))
        option.performTouchInput { click() }
        windowControl(title, hasText("取消") and hasClickAction()).performTouchInput { click() }
        assertTrue(events.isEmpty())
        openLastAbilities()
        val restore =
            windowControl(title, hasText("恢复自动") and hasClickAction()).performScrollTo()
        assertTouchTarget(restore, windowRoot(title))
        restore.performTouchInput { click() }
        assertEquals(listOf("abilities:$ProviderId:$LastModelId:null"), events)
    }

    @Test
    fun abilitySave_returnsTheChosenOverride_withoutSelectingOrRemovingModel() {
        renderProvider()
        selectModels()
        openLastAbilities()
        val title = "覆盖模型能力"
        windowControl(title, hasText("推理") and hasClickAction()).performScrollTo()
            .performTouchInput { click() }
        windowControl(title, hasText("保存") and hasClickAction()).performTouchInput { click() }
        assertEquals(listOf("abilities:$ProviderId:$LastModelId:[VISION, TOOL, REASONING]"), events)
    }

    @Test
    fun fetchedModels_searchSelectionAndConfirmation_keepExistingIdsAndUseCurrentProvider() {
        renderProvider()
        selectModels()
        pageControl(hasContentDescription("获取模型")).performTouchInput { click() }
        val title = "选择模型"
        val search = windowControl(title, hasText("搜索模型") and hasSetTextAction())
        assertTouchTarget(search, windowRoot(title))
        search.performTextInput("12")
        val item = windowControl(title, hasText("云端合成模型 12") and isToggleable())
        assertTouchTarget(item, windowRoot(title))
        item.assertIsOff().performTouchInput { click() }.assertIsOn()
        search.performTextReplacement("not-a-model")
        inWindow(title, hasText("没有找到匹配的模型")).assertIsDisplayed()
        windowControl(title, hasContentDescription("清除")).performTouchInput { click() }
        val list = windowControl(title, hasScrollToNodeAction())
        scrollToEnd(list, "fetched")
        assertTouchTarget(windowControl(title, hasText("云端合成模型 12") and isToggleable()), list)
        val confirm = windowControl(title, hasText("确认选择") and hasClickAction())
        val cancel = windowControl(title, hasText("取消") and hasClickAction())
        assertTouchTarget(confirm, windowRoot(title))
        assertTouchTarget(cancel, windowRoot(title))
        assertFalse(confirm.fetchSemanticsNode().touchBoundsInRoot.overlaps(cancel.fetchSemanticsNode().touchBoundsInRoot))
        confirm.performTouchInput { click() }
        assertEquals(
            listOf(
                "fetch:$ProviderId",
                "confirm:$ProviderId:[fixture-model-1, fixture-model-12, fixture-model-2, fixture-model-3, fixture-model-4, fixture-model-5]",
                "dismiss-fetch"
            ), events
        )
    }

    @Test
    fun fetchedSelectAllAndCancel_doNotCommitSelection() {
        renderProvider()
        selectModels()
        pageControl(hasContentDescription("获取模型")).performTouchInput { click() }
        val title = "选择模型"
        windowControl(title, hasText("全选") and hasClickAction()).performTouchInput { click() }
        windowControl(title, hasText("取消全选") and hasClickAction()).performTouchInput { click() }
        windowControl(title, hasText("取消") and hasClickAction()).performTouchInput { click() }
        assertEquals(listOf("fetch:$ProviderId", "dismiss-fetch"), events)
    }

    @Test
    fun protocolHelp_usesRealWindowAndOnlyCloses_withReadableContent() {
        renderProvider()
        val explanation =
            "OpenAI 兼容协议适用于大多数兼容网关与中转站。具体请求接口由文本接口模式和请求路径决定。"
        composeRule.onNodeWithText(explanation).assertDoesNotExist()
        val help = reveal(hasContentDescription("接口协议：查看说明"))
        assertTouchTarget(help, pageScroll())
        help.performTouchInput { click() }
        inWindow("接口协议", hasText(explanation)).performScrollTo().assertIsDisplayed()
        val close = windowControl("接口协议", hasText("知道了") and hasClickAction())
        assertTouchTarget(close, windowRoot("接口协议"))
        close.performTouchInput { click() }
        assertTrue(events.isEmpty())
    }

    private fun openLastAbilities() {
        scrollToEnd()
        val last = hasText(LastModelName) and isSelectable()
        pageControl(hasContentDescription("设置模型能力") and hasAnyAncestor(last)).performTouchInput { click() }
        windowRoot("覆盖模型能力").assertIsDisplayed()
    }
}
