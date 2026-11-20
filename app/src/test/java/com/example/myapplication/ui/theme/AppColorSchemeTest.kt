package com.example.myapplication.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.myapplication.model.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorSchemeTest {
    @Test
    fun allAppThemes_readingAndActionText_haveAccessibleContrast() {
        AppColorTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val colors = resolveAppColorScheme(theme, dark)
                assertTrue("$theme dark=$dark reading", contrast(colors.onSurface, colors.surface) >= 4.5f)
                assertTrue("$theme dark=$dark action", contrast(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue("$theme dark=$dark secondary", contrast(colors.onSurfaceVariant, colors.surface) >= 4.5f)
            }
        }
    }

    @Test
    fun suppliedDynamicScheme_keepsDynamicAccentsAndSharedReadingSurface() {
        val dynamic = lightColorScheme(primary = Color(0xFF004488), primaryContainer = Color(0xFFDCF0FF))
        val resolved = resolveAppColorScheme(AppColorTheme.VANILLA, darkTheme = false, dynamicColorScheme = dynamic)
        assertEquals(dynamic.primary, resolved.primary)
        assertEquals(dynamic.primaryContainer, resolved.primaryContainer)
        assertEquals(resolveAppColorScheme(AppColorTheme.OCEAN, false).surface, resolved.surface)
    }

    @Test
    fun fixedThemes_keepDistinctTonalSurfaces_inLightAndDark() {
        listOf(false, true).forEach { dark ->
            val matcha = resolveAppColorScheme(AppColorTheme.MATCHA, dark)
            val vanilla = resolveAppColorScheme(AppColorTheme.VANILLA, dark)
            val ocean = resolveAppColorScheme(AppColorTheme.OCEAN, dark)
            assertEquals(
                "dark=$dark 三套固定主题 background 应保有独立色温",
                3,
                setOf(matcha.background, vanilla.background, ocean.background).size,
            )
            assertEquals(
                "dark=$dark 三套固定主题 surfaceContainer 应保有独立色温",
                3,
                setOf(matcha.surfaceContainer, vanilla.surfaceContainer, ocean.surfaceContainer).size,
            )
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
