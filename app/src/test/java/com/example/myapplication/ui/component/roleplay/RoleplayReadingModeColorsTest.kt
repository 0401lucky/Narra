package com.example.myapplication.ui.component.roleplay

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.*
import com.example.myapplication.ui.theme.resolveAppColorScheme
import org.junit.Test

class RoleplayReadingModeColorsTest : OfflineRoleplayRenderTest() {
    @Test fun actualReadingSurfaces_allThemesAndFixedDynamicSchemes_keepMixedTextReadable() {
        val schemes = AppColorTheme.entries.flatMap { theme -> listOf(false, true).map { resolveAppColorScheme(theme, it) } } + listOf(
            resolveAppColorScheme(AppColorTheme.MATCHA, false, lightColorScheme(primary = Color(0xFF315DA8))),
            resolveAppColorScheme(AppColorTheme.MATCHA, true, darkColorScheme(primary = Color(0xFFA8C8FF))),
        )
        val scheme = mutableStateOf(schemes.first())
        renderReadingPage(ScreenshotCases.require("E04_reading-mixed-background"), schemeState = scheme)
        val list = composeRule.onNodeWithTag("roleplay_reading_list")
        schemes.forEachIndexed { index, next ->
            composeRule.runOnIdle { scheme.value = next }
            drainMainLooper()
            listOf(ScreenshotFixture.UserLineText, ScreenshotFixture.CharacterLineText,
                ScreenshotFixture.NarrationLineText, OfflineRoleplayFixture.ThoughtText, "雨声渐缓。").forEach { text ->
                list.performScrollToNode(hasText(text, substring = true))
                try { assertRenderedTextContrast(text, substring = true) }
                catch (failure: AssertionError) { throw AssertionError("reading scheme[$index] ${failure.message}", failure) }
            }
        }
    }

    @Test fun actualSyntheticBackdrop_mixedReadingText_passesContrast() {
        renderReadingPage(ScreenshotCases.require("E04_reading-mixed-background"), withBackground = true)
        val list = composeRule.onNodeWithTag("roleplay_reading_list")
        listOf(ScreenshotFixture.UserLineText, ScreenshotFixture.CharacterLineText,
            ScreenshotFixture.NarrationLineText, OfflineRoleplayFixture.ThoughtText, "雨声渐缓。").forEach { text ->
            list.performScrollToNode(hasText(text, substring = true))
            assertRenderedTextContrast(text, substring = true)
        }
    }

    @Test fun highContrastAndSavedLineHeight_areAppliedToRealReadingText() {
        renderReadingPage(ScreenshotCases.require("E04_reading-mixed-background"), highContrast = true,
            lineHeightScale = 1.4f,
            messages = listOf(ScreenshotFixture.characterMessage("high-reading", "高对比阅读正文")))
        assertRenderedTextContrast("高对比阅读正文")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithText("高对比阅读正文", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertEquals(42f, layouts.single().layoutInput.style.lineHeight.value, 0.001f)
    }
}
