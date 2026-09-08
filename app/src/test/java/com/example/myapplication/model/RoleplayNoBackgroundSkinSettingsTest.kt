package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayNoBackgroundSkinSettingsTest {
    @Test
    fun defaultSkin_usesNarraPresetAndExpectedBubbleValues() {
        val skin = RoleplayNoBackgroundSkinSettings()

        assertEquals(RoleplayNoBackgroundSkinPreset.NARRA, skin.preset)
        assertEquals(78, skin.maxWidthPercent)
        assertEquals(18, skin.bubbleRadiusDp)
        assertEquals(14, skin.bubblePaddingHorizontalDp)
        assertEquals(10, skin.bubblePaddingVerticalDp)
        assertEquals(false, skin.showBubbleTail)
        assertEquals(true, skin.onlineBackgroundGlassEnabled)
    }

    @Test
    fun normalized_clampsBubbleValuesToSafeRanges() {
        val skin = RoleplayNoBackgroundSkinSettings(
            preset = RoleplayNoBackgroundSkinPreset.PIXEL,
            maxWidthPercent = 180,
            bubbleRadiusDp = -4,
            bubblePaddingHorizontalDp = 80,
            bubblePaddingVerticalDp = 0,
            showBubbleTail = true,
            onlineBackgroundGlassEnabled = false,
        ).normalized()

        assertEquals(RoleplayNoBackgroundSkinPreset.PIXEL, skin.preset)
        assertEquals(92, skin.maxWidthPercent)
        assertEquals(4, skin.bubbleRadiusDp)
        assertEquals(20, skin.bubblePaddingHorizontalDp)
        assertEquals(6, skin.bubblePaddingVerticalDp)
        assertEquals(true, skin.showBubbleTail)
        assertEquals(false, skin.onlineBackgroundGlassEnabled)
    }

    @Test
    fun normalized_disabledBackgroundGlass_preservesValidThemeAndBubbleValues() {
        val skin = RoleplayNoBackgroundSkinSettings(
            preset = RoleplayNoBackgroundSkinPreset.NOIR,
            maxWidthPercent = 86,
            bubbleRadiusDp = 12,
            bubblePaddingHorizontalDp = 16,
            bubblePaddingVerticalDp = 8,
            showBubbleTail = true,
            onlineBackgroundGlassEnabled = false,
        )

        assertEquals(skin, skin.normalized())
    }

    @Test
    fun presetStorageValue_resolvesAllBuiltInPresets() {
        RoleplayNoBackgroundSkinPreset.entries.forEach { preset ->
            assertEquals(
                preset,
                RoleplayNoBackgroundSkinPreset.fromStorageValue(preset.storageValue),
            )
        }
    }
}
