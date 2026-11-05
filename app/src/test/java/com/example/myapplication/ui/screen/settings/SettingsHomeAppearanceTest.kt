package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeAppearanceTest : SettingsHomeRenderTest() {
    @Test
    fun themeSheet_narrowLargeFont_everySelectionIsNamedAndDispatchesOnceThenDismisses() {
        renderHome()
        val title = "选择主题色调"
        AppColorTheme.entries.forEach { selected ->
            openSheet("主题色调", title)
            val current = windowControl(title, isSelectable() and hasText(state.value.appColorTheme.label))
            current.assertIsSelected()
            val option = windowControl(title, isSelectable() and hasText(selected.label)).performScrollTo()
            option.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            assertTouchTarget(option, windowRoot(title))
            assertTextFits(inWindow(title, hasText(selected.label)), selected.label)
            option.performTouchInput { click() }
            composeRule.waitForIdle()
            composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(selected, state.value.appColorTheme) }
        }
        composeRule.runOnIdle { assertEquals(AppColorTheme.entries.map { "theme:$it" }, events) }
    }

    @Test
    fun modeSheet_narrowLargeFont_everySelectionIsNamedAndDispatchesOnceThenDismisses() {
        renderHome(dark = true)
        val title = composeRule.activity.getString(R.string.settings_color_mode)
        ThemeMode.entries.forEach { selected ->
            openSheet(title, title)
            windowControl(title, isSelectable() and hasText(state.value.themeMode.label)).assertIsSelected()
            val option = windowControl(title, isSelectable() and hasText(selected.label)).performScrollTo()
            option.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            assertTouchTarget(option, windowRoot(title))
            assertTextFits(inWindow(title, hasText(selected.label)), selected.label)
            option.performTouchInput { click() }
            composeRule.waitForIdle()
            composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(selected, state.value.themeMode) }
        }
        composeRule.runOnIdle { assertEquals(ThemeMode.entries.map { "mode:$it" }, events) }
    }

    @Test
    fun displaySheet_narrowLargeFont_sliderAndAllSwitchLabelsHaveIndependentSingleCallbacks() {
        renderHome()
        val title = composeRule.activity.getString(R.string.settings_display)
        openSheet(title, title)
        val slider = windowControl(title, hasContentDescription(composeRule.activity.getString(R.string.settings_message_text_scale)))
            .performScrollTo()
        assertTouchTarget(slider, windowRoot(title))
        slider.performTouchInput { click(Offset(center.x, 1f)) }
        composeRule.runOnIdle {
            assertTrue("滑块48dp命中区的顶部边缘必须实际响应触摸", state.value.messageTextScale < 1.15f)
            assertEquals(1, events.size)
            events.clear()
        }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(1.2f) }
        composeRule.runOnIdle { assertEquals(1.2f, state.value.messageTextScale, 0.001f) }
        val switches = listOf(
            R.string.settings_thinking_default_expanded to "reasoning:false",
            R.string.settings_show_thinking_in_progress to "thinking:false",
            R.string.settings_auto_collapse_after to "collapse-thinking:false",
            R.string.settings_auto_preview_images to "preview:false",
            R.string.settings_code_block_auto_wrap to "wrap:true",
            R.string.settings_code_block_auto_collapse to "collapse-code:true",
        )
        switches.forEach { (labelId, callback) ->
            val label = composeRule.activity.getString(labelId)
            val control = windowControl(title, hasText(label) and isToggleable()).performScrollTo()
            control.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            assertTouchTarget(control, windowRoot(title))
            assertTextFits(inWindow(title, hasText(label)), label)
            inWindow(title, hasText(label)).performTouchInput { click() }
            if (callback.endsWith("true")) control.assertIsOn() else control.assertIsOff()
        }
        val lastLabel = composeRule.activity.getString(R.string.settings_code_block_auto_collapse)
        val last = windowControl(title, hasText(lastLabel) and isToggleable()).performScrollTo()
        val previousLabel = composeRule.activity.getString(R.string.settings_code_block_auto_wrap)
        val previous = windowControl(title, hasText(previousLabel) and isToggleable())
        assertFalse(previous.fetchSemanticsNode().touchBoundsInRoot.overlaps(last.fetchSemanticsNode().touchBoundsInRoot))
        last.performTouchInput { click(Offset(width - 24f, height - 32f)) }
        last.assertIsOff()
        val scroll = inWindow(title, hasScrollAction())
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("200%显示窗口确实需要滚动", range.maxValue() > 0f)
        assertEquals("末项所在的列表尾部可达", range.maxValue(), range.value(), 1f)
        last.assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("scale:1.2") + switches.map { it.second } + "collapse-code:false", events)
        }
    }

    @Test
    fun allThreeSheets_dismissOnlyClosesWindowWithoutUpdatingSettings() {
        renderHome()
        val initial = state.value
        listOf(
            "主题色调" to "选择主题色调",
            composeRule.activity.getString(R.string.settings_color_mode).let { it to it },
            composeRule.activity.getString(R.string.settings_display).let { it to it },
        ).forEach { (label, title) ->
            openSheet(label, title)
            dismissSheet(title)
        }
        composeRule.runOnIdle {
            assertEquals(initial, state.value)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun missingWindow_neverFallsBackToCapturingSettingsPage() {
        renderHome()
        val failure = assertThrows(AssertionError::class.java) { windowRoot("选择主题色调") }
        assertTrue(failure.message.orEmpty().contains("真实独立窗口"))
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }
}
