package com.example.myapplication.ui.screenshot

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SettingsHomeScreenshotE10Test : SettingsHomeRenderTest() {
    @Test fun e10_home_matcha_light() = home(AppColorTheme.MATCHA, false)
    @Test fun e10_home_matcha_dark() = home(AppColorTheme.MATCHA, true)
    @Test fun e10_home_vanilla_light() = home(AppColorTheme.VANILLA, false)
    @Test fun e10_home_vanilla_dark() = home(AppColorTheme.VANILLA, true)
    @Test fun e10_home_ocean_light() = home(AppColorTheme.OCEAN, false)
    @Test fun e10_home_ocean_dark() = home(AppColorTheme.OCEAN, true)
    @Test fun e10_home_dynamic_light() = dynamic(false)
    @Test fun e10_home_dynamic_dark() = dynamic(true)
    @Test fun e10_home_narrow_light() = narrow(false)
    @Test fun e10_home_narrow_dark() = narrow(true)
    @Test fun e10_theme_sheet_light() = sheet("theme", false)
    @Test fun e10_theme_sheet_dark() = sheet("theme", true)
    @Test fun e10_mode_sheet_light() = sheet("mode", false)
    @Test fun e10_mode_sheet_dark() = sheet("mode", true)
    @Test fun e10_display_sheet_light() = sheet("display", false)
    @Test fun e10_display_sheet_dark() = sheet("display", true)

    private fun home(theme: AppColorTheme, dark: Boolean) {
        val case = ScreenshotCases.require("E10_home-${theme.storageValue}-${shade(dark)}")
        state.value = fixtureState(theme)
        renderHome(case, theme, dark)
        captureHome(case)
    }

    private fun dynamic(dark: Boolean) {
        val case = ScreenshotCases.require("E10_home-dynamic-${shade(dark)}")
        renderHome(case, dark = dark, dynamic = true)
        captureHome(case)
    }

    private fun narrow(dark: Boolean) {
        val case = ScreenshotCases.require("E10_home-narrow-${shade(dark)}")
        renderHome(case, dark = dark)
        captureHome(case)
    }

    private fun sheet(kind: String, dark: Boolean) {
        val case = ScreenshotCases.require("E10_$kind-sheet-${shade(dark)}")
        renderHome(case, dark = dark)
        val label = when (kind) {
            "theme" -> "主题色调"
            "mode" -> composeRule.activity.getString(R.string.settings_color_mode)
            else -> composeRule.activity.getString(R.string.settings_display)
        }
        val title = if (kind == "theme") "选择主题色调" else label
        openSheet(label, title)
        val operation = when (kind) {
            "theme" -> AppColorTheme.MATCHA.label
            "mode" -> ThemeMode.SYSTEM.label
            else -> composeRule.activity.getString(R.string.settings_code_block_auto_collapse)
        }
        if (kind == "display") {
            val scroll = inWindow(title, hasScrollAction())
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            composeRule.waitForIdle()
            val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("显示Sheet必须实际滚动", range.maxValue() > 0f)
            assertEquals("显示Sheet应到达真实末尾", range.maxValue(), range.value(), 1f)
            assertTouchTarget(windowControl(title, hasText(operation) and isToggleable()), windowRoot(title))
        } else {
            inWindow(title, hasText(operation)).performScrollTo()
        }
        captureWindow(case, title, operation)
    }

    private fun shade(dark: Boolean) = if (dark) "dark" else "light"
}
