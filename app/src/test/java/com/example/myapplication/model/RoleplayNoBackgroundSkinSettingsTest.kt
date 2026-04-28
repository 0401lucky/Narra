package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayNoBackgroundSkinSettingsTest {
    @Test
    fun defaultSkin_usesWechatPresetAndExpectedBubbleValues() {
        val skin = RoleplayNoBackgroundSkinSettings()

        assertEquals(RoleplayNoBackgroundSkinPreset.WECHAT, skin.preset)
        assertEquals(82, skin.maxWidthPercent)
        assertEquals(8, skin.bubbleRadiusDp)
        assertEquals(12, skin.bubblePaddingHorizontalDp)
        assertEquals(9, skin.bubblePaddingVerticalDp)
        assertEquals(true, skin.showBubbleTail)
    }

    @Test
    fun normalized_clampsBubbleValuesToSafeRanges() {
        val skin = RoleplayNoBackgroundSkinSettings(
            maxWidthPercent = 180,
            bubbleRadiusDp = -4,
            bubblePaddingHorizontalDp = 80,
            bubblePaddingVerticalDp = 0,
        ).normalized()

        assertEquals(92, skin.maxWidthPercent)
        assertEquals(4, skin.bubbleRadiusDp)
        assertEquals(20, skin.bubblePaddingHorizontalDp)
        assertEquals(6, skin.bubblePaddingVerticalDp)
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
