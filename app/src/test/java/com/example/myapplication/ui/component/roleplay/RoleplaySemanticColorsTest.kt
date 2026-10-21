package com.example.myapplication.ui.component.roleplay

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.theme.readableRoleplayText
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayBrandSkinPalette
import com.example.myapplication.ui.theme.roleplayTextContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplaySemanticColorsTest {
    @Test
    fun narra_usesActualThemeRoles_inAllAppAndFixedDynamicSchemes() {
        val schemes = AppColorTheme.entries.flatMap { theme ->
            listOf(false, true).map { dark -> resolveAppColorScheme(theme, dark) }
        } + listOf(
            resolveAppColorScheme(AppColorTheme.MATCHA, false, lightColorScheme(
                primary = Color(0xFF315DA8), primaryContainer = Color(0xFFD8E2FF),
                onPrimaryContainer = Color(0xFF001A42),
            )),
            resolveAppColorScheme(AppColorTheme.MATCHA, true, darkColorScheme(
                primary = Color(0xFFA8C8FF), primaryContainer = Color(0xFF23467D),
                onPrimaryContainer = Color(0xFFD8E2FF),
            )),
        )
        schemes.forEach { scheme ->
            val skin = resolveRoleplayNoBackgroundSkinSpec(RoleplayNoBackgroundSkinSettings(), scheme)
            val backdrop = ImmersiveBackdropState(null, IntSize(720, 1600), deriveImmersiveGlassPalette(null, scheme))
            val colors = resolveImmersiveRoleplayColors(backdrop, skin, scheme, false)
            assertEquals(scheme.primaryContainer, colors.userBubbleBackground)
            assertEquals(scheme.surfaceContainerLow, colors.characterBubbleBackground)
            assertEquals(scheme.surfaceContainerHighest, colors.narrationBubbleBackground)
            assertReadable(colors.userText, colors.userBubbleBackground, skin.background)
            assertReadable(colors.textPrimary, colors.characterBubbleBackground, skin.background)
            assertReadable(colors.narrationText, colors.narrationBubbleBackground, skin.background)
            assertReadable(colors.characterAccent, colors.characterBubbleBackground, skin.background)
            assertReadable(colors.errorText, colors.errorBackground, skin.background)
            assertReadable(colors.systemText, colors.systemBackground, skin.background)
            assertReadable(colors.panelText, colors.panelBackgroundStrong, skin.background)
            assertReadable(colors.panelMutedText, colors.panelBackgroundStrong, skin.background)
        }
    }

    @Test
    fun allBrandSkins_keepBrandBubble_andReadablePairs_inLightAndDark() {
        RoleplayNoBackgroundSkinPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val scheme = resolveAppColorScheme(AppColorTheme.OCEAN, dark)
                val skin = resolveRoleplayNoBackgroundSkinSpec(RoleplayNoBackgroundSkinSettings(preset = preset), scheme)
                val brand = roleplayBrandSkinPalette(preset)
                if (brand != null && !(dark && preset == RoleplayNoBackgroundSkinPreset.LIQUID_GLASS)) {
                    assertEquals("$preset retains user brand surface", brand.userBubble, skin.userBubble)
                }
                if (dark && brand != null && preset != RoleplayNoBackgroundSkinPreset.NOIR) {
                    assertEquals(scheme.background, skin.background)
                }
                if (preset == RoleplayNoBackgroundSkinPreset.LIQUID_GLASS) {
                    assertTrue(skin.userBubble.alpha < 1f)
                    assertTrue(skin.characterBubble.alpha < 1f)
                }
                assertReadable(skin.userText, skin.userBubble, skin.background)
                assertReadable(skin.characterText, skin.characterBubble, skin.background)
                assertReadable(skin.userAccent, skin.userBubble, skin.background)
                assertReadable(skin.accent, skin.characterBubble, skin.background)
                assertReadable(skin.mutedText, skin.narrationBubble, skin.background)
            }
        }
    }

    @Test
    fun geometryOverrides_includingHistoricalDefaults_areNeverSnappedToTokens() {
        val scheme = resolveAppColorScheme(AppColorTheme.MATCHA, false)
        listOf(
            RoleplayNoBackgroundSkinSettings(),
            RoleplayNoBackgroundSkinSettings(bubbleRadiusDp = 4, maxWidthPercent = 64, bubblePaddingHorizontalDp = 8, bubblePaddingVerticalDp = 6, showBubbleTail = false),
            RoleplayNoBackgroundSkinSettings(bubbleRadiusDp = 28, maxWidthPercent = 92, bubblePaddingHorizontalDp = 20, bubblePaddingVerticalDp = 18, showBubbleTail = true, onlineBackgroundGlassEnabled = false),
        ).forEach { settings ->
            val skin = resolveRoleplayNoBackgroundSkinSpec(settings, scheme)
            assertEquals(settings.bubbleRadiusDp.dp, skin.radius)
            assertEquals(settings.bubblePaddingHorizontalDp.dp, skin.paddingHorizontal)
            assertEquals(settings.bubblePaddingVerticalDp.dp, skin.paddingVertical)
            assertEquals(settings.maxWidthPercent / 100f, skin.maxWidthFraction)
            assertEquals(settings.showBubbleTail, skin.showTail)
        }
    }

    @Test
    fun lowContrastTransparentText_isCorrectedAgainstCompositedSurface() {
        val surface = Color.White.copy(alpha = 0.58f)
        val backing = Color(0xFFEAF0F7)
        val preferred = Color(0xFF99AABB).copy(alpha = 0.6f)
        assertTrue(roleplayTextContrast(preferred, surface, backing) < 4.5f)
        val adjusted = readableRoleplayText(preferred, surface, backing)
        assertNotEquals(preferred, adjusted)
        assertReadable(adjusted, surface, backing)
        assertEquals(adjusted, readableRoleplayText(adjusted, surface, backing))
    }

    private fun assertReadable(text: Color, surface: Color, backing: Color) {
        assertTrue("text=$text surface=$surface backing=$backing", roleplayTextContrast(text, surface, backing) >= 4.5f)
    }
}
