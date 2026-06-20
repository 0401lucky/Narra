package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayDiaryTextTest {
    @Test
    fun readableAccent_darkensNeonAccentForDarkText() {
        val neonGreen = Color(0xFF7CFF00)
        val readable = resolveDiaryReadableAccentColor(
            primaryText = Color(0xFF151515),
            accent = neonGreen,
        )

        assertTrue(readable.luminance() < neonGreen.luminance())
        assertTrue(readable.luminance() < 0.45f)
    }

    @Test
    fun readableAccent_keepsLightTextReadableOnDarkSurface() {
        val neonGreen = Color(0xFF7CFF00)
        val readable = resolveDiaryReadableAccentColor(
            primaryText = Color.White,
            accent = neonGreen,
        )

        assertTrue(readable.luminance() > 0.55f)
    }
}
