package com.example.myapplication.ui.component.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveGlassReadingStyleTest {

    @Test
    fun resolveImmersiveReadingGlassSpec_panelKeepsOverlayThin() {
        val spec = resolveImmersiveReadingGlassSpec(ImmersiveReadingGlassVariant.PANEL)

        assertEquals(0f, spec.blurRadius.value, 0.001f)
        assertEquals(0.24f, spec.overlayAlpha, 0.001f)
        assertEquals(0.07f, spec.borderAlpha, 0.001f)
        assertEquals(0.035f, spec.contentDarkenAlpha, 0.001f)
    }

    @Test
    fun resolveImmersiveReadingGlassSpec_readingContentDoesNotBlurBackdrop() {
        val variants = listOf(
            ImmersiveReadingGlassVariant.PANEL,
            ImmersiveReadingGlassVariant.CARD,
            ImmersiveReadingGlassVariant.DIALOGUE,
            ImmersiveReadingGlassVariant.THOUGHT,
        )

        variants.forEach { variant ->
            val spec = resolveImmersiveReadingGlassSpec(variant)

            assertEquals(0f, spec.blurRadius.value, 0.001f)
        }
    }

    @Test
    fun resolveImmersiveReadingGlassSpec_mainSceneContentDoesNotBlurBackdrop() {
        val longformCard = resolveImmersiveReadingGlassSpec(ImmersiveReadingGlassVariant.CARD)
        val dialogueBubble = resolveImmersiveReadingGlassSpec(ImmersiveReadingGlassVariant.DIALOGUE)

        assertEquals(0f, longformCard.blurRadius.value, 0.001f)
        assertEquals(0f, dialogueBubble.blurRadius.value, 0.001f)
        assertTrue(longformCard.overlayAlpha < 0.24f)
        assertTrue(dialogueBubble.overlayAlpha < 0.18f)
    }

    @Test
    fun resolveImmersiveReadingGlassSpec_chromeIsStrongerThanCard() {
        val chrome = resolveImmersiveReadingGlassSpec(ImmersiveReadingGlassVariant.CHROME)
        val card = resolveImmersiveReadingGlassSpec(ImmersiveReadingGlassVariant.CARD)

        assertTrue(chrome.overlayAlpha > card.overlayAlpha)
        assertTrue(chrome.contentDarkenAlpha >= card.contentDarkenAlpha)
        assertTrue(chrome.borderAlpha >= card.borderAlpha)
    }

    @Test
    fun adjustImmersiveReadingOverlayColor_brightBackgroundGetsStrongerTint() {
        val base = androidx.compose.ui.graphics.Color(0x5F7A8694)
        val dark = adjustImmersiveReadingOverlayColor(
            baseColor = base,
            backgroundLuminance = 0.20f,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
        )
        val bright = adjustImmersiveReadingOverlayColor(
            baseColor = base,
            backgroundLuminance = 0.90f,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
        )

        assertTrue(bright.alpha > dark.alpha)
        assertTrue(bright.red < dark.red)
        assertTrue(bright.green < dark.green)
        assertTrue(bright.blue < dark.blue)
    }

    @Test
    fun adjustImmersiveReadingContentDarkenAlpha_brightBackgroundGetsStrongerContrast() {
        val dark = adjustImmersiveReadingContentDarkenAlpha(
            baseAlpha = 0.03f,
            backgroundLuminance = 0.20f,
            variant = ImmersiveReadingGlassVariant.CARD,
        )
        val bright = adjustImmersiveReadingContentDarkenAlpha(
            baseAlpha = 0.03f,
            backgroundLuminance = 0.95f,
            variant = ImmersiveReadingGlassVariant.CARD,
        )

        assertEquals(0.03f, dark, 0.001f)
        assertTrue(bright > dark)
    }

    @Test
    fun resolveImmersiveReadingScrimAlpha_increasesWithBrightnessAndClamps() {
        val dark = resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = -1f,
            variant = ImmersiveReadingScrimVariant.READING,
        )
        val bright = resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = 2f,
            variant = ImmersiveReadingScrimVariant.READING,
        )

        assertEquals(0.025f, dark, 0.001f)
        assertEquals(0.075f, bright, 0.001f)
    }

    @Test
    fun resolveImmersiveReadingScrimAlpha_detailIsHeavierThanDiaryList() {
        val diaryList = resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = 0.7f,
            variant = ImmersiveReadingScrimVariant.DIARY_LIST,
        )
        val detail = resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = 0.7f,
            variant = ImmersiveReadingScrimVariant.DIARY_DETAIL,
        )

        assertTrue(detail > diaryList)
    }
}
