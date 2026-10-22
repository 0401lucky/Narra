package com.example.myapplication.ui.component.roleplay

import android.graphics.Bitmap
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import com.example.myapplication.model.*
import com.example.myapplication.ui.screenshot.*
import com.example.myapplication.ui.theme.resolveAppColorScheme
import org.junit.Assert.*
import org.junit.Test

/** 检查生产Text的实际颜色和实际绘制像素，不用未绘制的颜色字段代替承载面。 */
class OfflineRoleplayColorsTest : OfflineRoleplayRenderTest() {
    @Test fun narraSuggestionErrorAndText_themeSwitchAndFixedDynamicSchemes_readActualSurface() = suggestionColors(RoleplayNoBackgroundSkinPreset.NARRA)
    @Test fun noirSuggestionErrorAndText_readActualLightAndDarkSurfaces() = suggestionColors(RoleplayNoBackgroundSkinPreset.NOIR)
    @Test fun liquidSuggestionErrorAndText_readActualLightAndDarkSurfaces() = suggestionColors(RoleplayNoBackgroundSkinPreset.LIQUID_GLASS)

    private fun suggestionColors(preset: RoleplayNoBackgroundSkinPreset) {
        val scheme = mutableStateOf(resolveAppColorScheme(AppColorTheme.MATCHA, false))
        val error = "建议生成失败，请重试。"
        renderOfflinePage(ScreenshotCases.require("E04_scene-suggestions"),
            suggestions = OfflineRoleplayFixture.Suggestions, error = error, schemeState = scheme,
            skin = RoleplayNoBackgroundSkinSettings(preset = preset))
        val schemes = AppColorTheme.entries.flatMap { theme -> listOf(false, true).map { resolveAppColorScheme(theme, it) } } + listOf(
            resolveAppColorScheme(AppColorTheme.MATCHA, false, lightColorScheme(primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A42))),
            resolveAppColorScheme(AppColorTheme.MATCHA, true, darkColorScheme(primaryContainer = Color(0xFF224030), onPrimaryContainer = Color(0xFFD1F8DA))),
        )
        schemes.forEach { next ->
            composeRule.runOnIdle { scheme.value = next }
            val panel = composeRule.onNodeWithTag("roleplay_scene_suggestions")
            panel.performScrollToNode(hasText(error))
            assertRenderedTextContrast(error)
            val suggestion = OfflineRoleplayFixture.Suggestions.first()
            panel.performScrollToNode(hasTestTag("roleplay_suggestion_${suggestion.id}"))
            assertRenderedTextContrast(suggestion.text)
        }
    }

    @Test fun sceneMoodScrimAndMessageGlass_readActualSyntheticBackdrop() {
        val message = ScreenshotFixture.userMessage("scene-contrast", "真实场景玻璃文字")
        renderOfflinePage(ScreenshotCases.require("E04_scene-background"), image = ScreenshotFixture.syntheticBackdrop(),
            messages = listOf(message), showHelper = false)
        assertRenderedTextContrast(message.content)
    }

    @Test fun whiteBackdropSceneGlass_hasReadableCharacterAndUser_andOfflineIgnoresOnlineGlassSwitch() {
        val image = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        val user = ScreenshotFixture.userMessage("scene-white-user", "白图用户正文")
        val character = ScreenshotFixture.characterMessage("scene-white-character", "白图角色正文")
        renderOfflinePage(ScreenshotCases.require("E04_scene-background"), dark = true, image = image,
            skin = RoleplayNoBackgroundSkinSettings(onlineBackgroundGlassEnabled = false),
            messages = listOf(user, character), showHelper = false)
        assertRenderedTextContrast(user.content)
        assertRenderedTextContrast(character.content)
    }
}
