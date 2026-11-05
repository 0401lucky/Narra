package com.example.myapplication.ui.screen.settings

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** 实际 TextLayout 前景与同一页面/窗口像素承载面，包含运行中切换八种生产 scheme。 */
class ProviderColorsTest : ProviderSettingsRenderTest() {
    private val schemes = AppColorTheme.entries.flatMap { theme ->
        listOf(false, true).map { dark -> resolveAppColorScheme(theme, dark) }
    } + listOf(false, true).map { dark ->
        resolveAppColorScheme(
            AppColorTheme.MATCHA,
            dark,
            lightColorScheme()
        )
    }

    @Test
    fun providerCards_statusAndHealthText_followActualContainersAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(
            ScreenshotCases.require("E11_list-matcha-light"),
            list = true,
            colorSchemeState = current
        )
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val card = reveal(hasText(LongProviderName) and hasClickAction())
            val surface = rowSurface(card)
            assertEquals(scheme.surface, surface)
            assertPair(
                composeRule.onNodeWithText(LongProviderName, useUnmergedTree = true),
                surface,
                scheme.onSurface
            )
            assertPair(
                composeRule.onNodeWithText("连接正常", useUnmergedTree = true),
                surface,
                scheme.onSurfaceVariant
            )
            val enabled =
                composeRule.onNodeWithTag("provider_status_$ProviderId", useUnmergedTree = true)
            val enabledSurface = rowSurface(enabled)
            assertEquals(scheme.primaryContainer, enabledSurface)
            assertPair(
                composeRule.onNodeWithText("启用", useUnmergedTree = true),
                enabledSurface,
                scheme.onPrimaryContainer
            )
            val count =
                composeRule.onNodeWithTag("provider_count_$ProviderId", useUnmergedTree = true)
            val countSurface = rowSurface(count)
            assertEquals(scheme.secondaryContainer, countSurface)
            assertPair(
                composeRule.onNodeWithText("5 个模型", useUnmergedTree = true),
                countSurface,
                scheme.onSecondaryContainer
            )
            val other = reveal(hasText(OtherProviderName) and hasClickAction())
            assertPair(
                composeRule.onNodeWithText("连接失败", useUnmergedTree = true),
                rowSurface(other),
                scheme.error
            )
            val disabledSurface = rowSurface(
                composeRule.onNodeWithTag(
                    "provider_status_$OtherProviderId",
                    useUnmergedTree = true
                )
            )
            assertEquals(scheme.surfaceVariant, disabledSurface)
            assertPair(
                composeRule.onNodeWithText("停用", useUnmergedTree = true),
                disabledSurface,
                scheme.onSurfaceVariant
            )
        }
    }

    @Test
    fun config_selectedProtocolTabAndSave_pairWithRenderedSurfacesAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(colorSchemeState = current)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val option =
                reveal(hasText(ProviderApiProtocol.OPENAI_COMPATIBLE.label) and isSelectable())
            val selectedSurface = rowSurface(option)
            assertEquals(scheme.primaryContainer, selectedSurface)
            assertPair(
                composeRule.onNodeWithText(
                    ProviderApiProtocol.OPENAI_COMPATIBLE.label,
                    useUnmergedTree = true
                ), selectedSurface, scheme.onPrimaryContainer
            )
            val tab = pageControl(hasText("配置") and hasClickAction())
            assertPair(
                composeRule.onNodeWithText("配置", useUnmergedTree = true),
                rowSurface(tab),
                scheme.onPrimaryContainer
            )
            scrollToEnd()
            val save = pageControl(hasText("保存设置") and hasClickAction())
            val saveSurface = rowSurface(save)
            assertEquals(scheme.primary, saveSurface)
            assertPair(
                composeRule.onNodeWithText("保存设置", useUnmergedTree = true),
                saveSurface,
                scheme.onPrimary
            )
        }
    }

    @Test
    fun configFocusedInputLabels_areReadableOnTheActualFieldSurfaceAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(colorSchemeState = current)
        val input = reveal(hasText("名称") and hasSetTextAction())
        input.performTouchInput { click() }
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val label = composeRule.onNodeWithText("名称", useUnmergedTree = true)
            assertReadable(label, besideText(label, composeRule.onNodeWithTag(StageTag)))
        }
    }

    @Test
    fun models_selectedAndOrdinaryNames_pairWithActualCardsAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(ScreenshotCases.require("E11_models-light"), colorSchemeState = current)
        selectModels()
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val selected = reveal(hasText(LongModelName) and isSelectable())
            val surface = rowSurface(selected)
            assertEquals(scheme.primaryContainer, surface)
            assertPair(
                composeRule.onNodeWithText(LongModelName, useUnmergedTree = true),
                surface,
                scheme.onPrimaryContainer
            )
            assertPair(
                composeRule.onNodeWithText("当前模型", useUnmergedTree = true),
                surface,
                scheme.onPrimaryContainer
            )
            val other = reveal(hasText("合成模型 2") and isSelectable())
            assertPair(
                composeRule.onNodeWithText("合成模型 2", useUnmergedTree = true),
                rowSurface(other),
                scheme.onSurface
            )
        }
    }

    @Test
    fun fetchedRows_selectedAndUnselectedText_pairWithTheirRealWindowAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(ScreenshotCases.require("E11_models-light"), colorSchemeState = current)
        selectModels()
        pageControl(hasContentDescription("获取模型")).performTouchInput { click() }
        val title = "选择模型"
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val list = windowControl(title, hasScrollToNodeAction())
            list.performScrollToNode(hasText(LongModelName))
            val selected =
                windowControl(title, hasText(LongModelName) and isToggleable()).performScrollTo()
            val surface = rowSurface(selected)
            assertEquals(scheme.primaryContainer, surface)
            assertPair(inWindow(title, hasText(LongModelName)), surface, scheme.onPrimaryContainer)
            list.performScrollToNode(hasText("云端合成模型 12"))
            val ordinary = windowControl(
                title,
                hasText("云端合成模型 12") and isToggleable()
            ).performScrollTo()
            val ordinarySurface = rowSurface(ordinary)
            assertEquals(scheme.background, ordinarySurface)
            assertPair(
                inWindow(title, hasText("云端合成模型 12")),
                ordinarySurface,
                scheme.onSurface
            )
            listOf("取消", "全选").forEach { operation ->
                val label = inWindow(title, hasText(operation))
                assertReadable(label, besideText(label, windowRoot(title)))
            }
        }
    }

    @Test
    fun manualDialog_errorAndActions_useRealSurfaceContrastsAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(colorSchemeState = current)
        selectModels()
        pageControl(hasText("添加新模型") and hasClickAction()).performTouchInput { click() }
        val title = "手动添加模型"
        windowControl(title, hasText("添加") and hasClickAction()).performTouchInput { click() }
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val errorText = inWindow(title, hasText("模型 ID 不能为空")).performScrollTo()
            assertPair(errorText, besideText(errorText, windowRoot(title)), scheme.error)
            val action = inWindow(title, hasText("添加"))
            assertPair(action, besideText(action, windowRoot(title)), scheme.onSurface)
        }
    }

    @Test
    fun abilityDialog_selectedChips_useRenderedSemanticColorsAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(colorSchemeState = current)
        selectModels()
        scrollToEnd()
        pageControl(hasContentDescription("设置模型能力") and hasAnyAncestor(hasText(LastModelName) and isSelectable()))
            .performTouchInput { click() }
        val title = "覆盖模型能力"
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val chip = windowControl(title, hasText("视觉") and hasClickAction()).performScrollTo()
            val surface = rowSurface(chip)
            assertEquals(scheme.secondaryContainer, surface)
            assertPair(inWindow(title, hasText("视觉")), surface, scheme.onSecondaryContainer)
            val restore = inWindow(title, hasText("恢复自动")).performScrollTo()
            assertReadable(restore, besideText(restore, windowRoot(title)))
        }
    }

    @Test
    fun templateWindow_namesDescriptionsAndCancel_followActualSurfacesAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderProvider(list = true, colorSchemeState = current)
        pageControl(hasText("新增") and hasClickAction()).performTouchInput { click() }
        val title = "选择提供商模板"
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val card = windowControl(title, hasText("OpenAI") and hasClickAction())
            val surface = rowSurface(card)
            assertEquals(scheme.surfaceContainer, surface)
            assertPair(inWindow(title, hasText("OpenAI")), surface, scheme.onSurface)
            val cancel = inWindow(title, hasText("取消"))
            assertReadable(cancel, besideText(cancel, windowRoot(title)))
        }
    }

    private fun assertPair(text: SemanticsNodeInteraction, surface: Color, expected: Color) {
        val foreground = textLayout(text).layoutInput.style.color
        assertEquals(expected, foreground)
        assertReadable(text, surface)
    }

    private fun assertReadable(text: SemanticsNodeInteraction, surface: Color) {
        val foreground = textLayout(text).layoutInput.style.color
        assertTrue(
            "实际文字与承载面对比必须至少4.5：$foreground / $surface",
            roleplayTextContrast(foreground, surface, surface) >= 4.5f
        )
    }

    private fun rowSurface(node: SemanticsNodeInteraction): Color {
        val pixels =
            node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap()
                .toPixelMap()
        return pixels[8, pixels.height / 2]
    }

    private fun besideText(text: SemanticsNodeInteraction, root: SemanticsNodeInteraction): Color {
        val pixels =
            root.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap()
                .toPixelMap()
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val x = (bounds.left - 2).roundToInt().coerceIn(0, pixels.width - 1)
        val y = bounds.center.y.roundToInt().coerceIn(0, pixels.height - 1)
        return pixels[x, y]
    }
}
