package com.example.myapplication.ui.screen.roleplay

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.SessionSidebarRenderTest
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSidebarColorsTest : SessionSidebarRenderTest() {
    @Test
    fun skinTiles_useActualMaterialSurfaceAndTextAcrossThemeChanges() {
        val schemes = AppColorTheme.entries.flatMap { theme ->
            listOf(false, true).map { dark -> resolveAppColorScheme(theme, dark) }
        } + listOf(false, true).map { dark ->
            resolveAppColorScheme(AppColorTheme.MATCHA, dark, lightColorScheme())
        }
        val currentScheme = mutableStateOf(schemes.first())
        settings.value = settings.value.copy(roleplayNoBackgroundSkin = skin.copy(preset = RoleplayNoBackgroundSkinPreset.NOIR))
        renderSidebar(ScreenshotCases.require("E05_theme-narrow-light"), colorSchemeState = currentScheme)
        open("主题")
        schemes.forEach { scheme ->
            composeRule.runOnIdle { currentScheme.value = scheme }
            val selected = composeRule.onNodeWithTag("roleplay_skin_preset_noir").performScrollTo()
            val layout = textLayout("哥特夜诗")
            val pixels = selected.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!
                .asImageBitmap().toPixelMap()
            val surface = pixels[8, pixels.height / 2]
            assertEquals(scheme.secondaryContainer, surface)
            assertEquals(scheme.onSecondaryContainer, layout.layoutInput.style.color)
            assertTrue(roleplayTextContrast(layout.layoutInput.style.color, surface, surface) >= 4.5f)
            assertFalse("200% 标签必须自然换行，不能被裁切", layout.hasVisualOverflow)
            val ordinary = composeRule.onNodeWithTag("roleplay_skin_preset_narra").performScrollTo()
            val ordinaryPixels = ordinary.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!
                .asImageBitmap().toPixelMap()
            val ordinarySurface = ordinaryPixels[8, ordinaryPixels.height / 2]
            val ordinaryText = textLayout("Narra").layoutInput.style.color
            assertEquals(scheme.surface, ordinarySurface)
            assertEquals(scheme.onSurface, ordinaryText)
            assertTrue(roleplayTextContrast(ordinaryText, ordinarySurface, ordinarySurface) >= 4.5f)
        }
        composeRule.runOnIdle { assertEquals(skin.copy(preset = RoleplayNoBackgroundSkinPreset.NOIR), settings.value.roleplayNoBackgroundSkin) }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
