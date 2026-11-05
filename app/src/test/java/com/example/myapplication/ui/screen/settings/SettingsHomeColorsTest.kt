package com.example.myapplication.ui.screen.settings

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** 从真实文字布局取前景，从同一页面/窗口实际像素取承载面，覆盖主题切换而非仅比较调色板字段。 */
class SettingsHomeColorsTest : SettingsHomeRenderTest() {
    private val schemes = AppColorTheme.entries.flatMap { theme ->
        listOf(false, true).map { dark -> resolveAppColorScheme(theme, dark) }
    } + listOf(false, true).map { dark -> resolveAppColorScheme(AppColorTheme.MATCHA, dark, lightColorScheme()) }

    @Test
    fun home_textAndPrimaryAction_followActualSurfacesAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderHome(colorSchemeState = current)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val label = composeRule.activity.getString(R.string.settings_default_model_prompt)
            val row = homeRow(label)
            val surface = rowSurface(row)
            assertEquals(scheme.surface, surface)
            assertPair(composeRule.onNodeWithText(label, useUnmergedTree = true), surface, scheme.onSurface)
            assertPair(composeRule.onNodeWithText(LongModelName, useUnmergedTree = true), surface, scheme.onSurfaceVariant)
            val saveText = composeRule.activity.getString(R.string.common_save)
            val save = composeRule.onNodeWithText(saveText)
            val buttonSurface = rowSurface(save)
            assertEquals(scheme.secondaryContainer, buttonSurface)
            assertPair(composeRule.onNodeWithText(saveText, useUnmergedTree = true), buttonSurface, scheme.onSecondaryContainer)
            val header = composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_section_model_service), useUnmergedTree = true)
            header.performScrollTo()
            val actualBackground = pageMarginAt(header)
            assertEquals(scheme.background, actualBackground)
            assertPair(header, actualBackground, scheme.onSurfaceVariant)
        }
    }

    @Test
    fun themeSheet_selectedAndOrdinaryLabelsPairWithRenderedContainersAcrossEightSchemes() {
        verifyOptions(
            label = "主题色调",
            title = "选择主题色调",
            options = listOf(
                Triple(AppColorTheme.MATCHA.label, "柔和、安静，适合日常使用", true),
                Triple(AppColorTheme.VANILLA.label, "温暖、自然，带一点复古感", false),
                Triple(AppColorTheme.OCEAN.label, "清爽、沉静，适合专注阅读", false),
            ),
        )
    }

    @Test
    fun modeSheet_selectedAndOrdinaryLabelsPairWithRenderedContainersAcrossEightSchemes() {
        val title = composeRule.activity.getString(R.string.settings_color_mode)
        verifyOptions(title, title, listOf(
            Triple(ThemeMode.SYSTEM.label, "跟随手机的深浅色设置", true),
            Triple(ThemeMode.LIGHT.label, "始终使用明亮背景", false),
            Triple(ThemeMode.DARK.label, "始终使用深色背景", false),
        ))
    }

    @Test
    fun displaySheet_headersScaleAndSwitchTextRemainReadableAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderHome(colorSchemeState = current)
        val title = composeRule.activity.getString(R.string.settings_display)
        openSheet(title, title)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val texts = listOf(
                title to scheme.onSurface,
                composeRule.activity.getString(R.string.settings_message_text_scale) to scheme.onSurface,
                composeRule.activity.getString(R.string.settings_text_scale_large) to scheme.onSurfaceVariant,
                "思考内容" to scheme.onSurfaceVariant,
                composeRule.activity.getString(R.string.settings_thinking_default_expanded) to scheme.onSurface,
                "图片与代码" to scheme.onSurfaceVariant,
                composeRule.activity.getString(R.string.settings_code_block_auto_collapse) to scheme.onSurface,
            )
            texts.forEach { (text, expected) ->
                val label = inWindow(title, hasText(text)).performScrollTo()
                val surface = pageMarginAt(label, windowRoot(title))
                assertEquals(scheme.surface, surface)
                assertPair(label, surface, expected)
            }
        }
    }

    private fun verifyOptions(label: String, title: String, options: List<Triple<String, String, Boolean>>) {
        val current = mutableStateOf(schemes.first())
        renderHome(colorSchemeState = current)
        openSheet(label, title)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            options.forEach { (text, supporting, selected) ->
                val option = windowControl(title, isSelectable() and hasText(text)).performScrollTo()
                val surface = rowSurface(option)
                assertEquals(if (selected) scheme.primaryContainer else scheme.surface, surface)
                assertPair(inWindow(title, hasText(text)), surface, if (selected) scheme.onPrimaryContainer else scheme.onSurface)
                assertPair(inWindow(title, hasText(supporting)), surface, if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
            }
        }
    }

    private fun assertPair(text: SemanticsNodeInteraction, surface: Color, expected: Color) {
        val foreground = textLayout(text).layoutInput.style.color
        assertEquals(expected, foreground)
        assertTrue("实际文字与承载面对比必须至少4.5：$foreground / $surface", roleplayTextContrast(foreground, surface, surface) >= 4.5f)
    }

    private fun rowSurface(node: SemanticsNodeInteraction): Color {
        val pixels = node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        return pixels[8, pixels.height / 2]
    }

    private fun pageMarginAt(text: SemanticsNodeInteraction, root: SemanticsNodeInteraction = composeRule.onNodeWithTag(StageTag)): Color {
        val pixels = root.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        val y = (text.fetchSemanticsNode().boundsInRoot.center.y - root.fetchSemanticsNode().boundsInRoot.top).roundToInt()
        return pixels[8, y.coerceIn(0, pixels.height - 1)]
    }
}
