package com.example.myapplication.ui.screen.settings

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

/** 同一真实页面运行时切换八种生产 scheme；同时检查 TextLayout 和含 graphicsLayer 的像素。 */
class ContextTransferColorsTest : ContextTransferRenderTest() {
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
    fun overviewAndAllResourceGroups_useReadableTextOnActualSurfacesAcrossEightSchemes() {
        val current = renderColors()
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            listOf(
                "当前共有 3 条",
                "当前共有 18 条",
                "当前共有 12 条",
                "当前共有 7 条",
                "当前共有 4 条"
            ).forEach { label ->
                val node = reveal(hasText(label))
                assertPair(
                    pageNode(hasText(label), unmerged = true),
                    surfaceBeside(node),
                    scheme.onSurfaceVariant
                )
            }
            listOf("资料库备份", "角色包", "世界书", "记忆档案包", "预设包").forEach { title ->
                revealSection(title)
                listOf("导出", "导入").forEach { label ->
                    val button = sectionButton(title, label)
                    val text = pageNode(
                        hasText(label) and hasAnyAncestor(groupMatcher(title)),
                        unmerged = true
                    )
                    assertPair(
                        text,
                        surfaceBeside(text),
                        if (label == "导出") scheme.onPrimary else scheme.onSurface
                    )
                    button.assertIsEnabled()
                }
            }
        }
        assertCaptureIsolation()
    }

    @Test
    fun busyButtons_keepReadableRenderedTextAcrossEightSchemes() {
        state.value = OverviewState.copy(isBusy = true, importPreview = Preview)
        val current = renderColors()
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            pageList().performScrollToNode(hasContentDescription("资料库备份：查看说明"))
            val controls = composeRule.onAllNodes(
                hasText("处理中…") and hasClickAction() and hasAnyAncestor(groupMatcher("资料库备份"))
            )
            controls.assertCountEquals(2)
            repeat(2) { index ->
                val button = controls[index].performScrollTo().assertIsNotEnabled()
                composeRule.mainClock.advanceTimeBy(400)
                composeRule.waitForIdle()
                val text = composeRule.onAllNodes(
                    hasText("处理中…") and hasAnyAncestor(groupMatcher("资料库备份")),
                    useUnmergedTree = true
                )[index]
                assertPair(text, surfaceBeside(text), scheme.onSurfaceVariant)
                assertRenderedTextContrast(text, surfaceBeside(text))
                button.assertIsNotEnabled()
            }
            scrollToEnd()
            listOf("处理中…", "取消").forEach { label ->
                val preview =
                    hasAnyDescendant(hasText("来源：上下文数据包")) and hasScrollToIndexAction().not() and
                            hasAnyDescendant(hasScrollToIndexAction()).not() and hasTestTag(StageTag).not() and isRoot().not()
                val text = pageNode(hasText(label) and hasAnyAncestor(preview), unmerged = true)
                assertPair(text, surfaceBeside(text), scheme.onSurfaceVariant)
                assertRenderedTextContrast(text, surfaceBeside(text))
            }
        }
        assertCaptureIsolation()
    }

    @Test
    fun previewCountsNoticesAndConflicts_useTheirRealReadingSurfacesAcrossEightSchemes() {
        state.value = OverviewState.copy(importPreview = Preview)
        val current = renderColors()
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            listOf("来源：上下文数据包", "安全上限提示").forEach { label ->
                reveal(hasText(label))
                val text = pageNode(hasText(label), unmerged = true)
                assertPair(text, surfaceBeside(text), scheme.onSurface)
            }
            val notices = Preview.noticeMessages.joinToString("\n") { "• $it" }
            reveal(hasText(notices))
            assertPair(
                pageNode(hasText(notices), unmerged = true),
                surfaceBeside(pageNode(hasText(notices), unmerged = true)),
                scheme.onSurfaceVariant
            )
            listOf("2 条", "6 条", "3 条", "1 条").forEach { label ->
                // 两类摘要/角色都是2条；逐个读取真实文本节点，不把字段常量当颜色证据。
                pageList().performScrollToNode(hasText(label))
                composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                    .fetchSemanticsNodes().indices.forEach { index ->
                    val text = composeRule.onAllNodesWithText(
                        label,
                        useUnmergedTree = true
                    )[index].performScrollTo()
                    assertPair(text, surfaceBeside(text), scheme.onSurfaceVariant)
                }
            }
            scrollToEnd()
            val conflicts =
                Preview.conflicts.take(5).joinToString("\n") { "• ${it.typeLabel} / ${it.title}" }
            val conflictText = pageNode(hasText(conflicts), unmerged = true).performScrollTo()
            assertPair(conflictText, surfaceBeside(conflictText), scheme.onSurfaceVariant)
            scrollToEnd()
            listOf("确认导入", "取消").forEach { label ->
                val text = pageNode(hasText(label), unmerged = true)
                assertPair(
                    text,
                    surfaceBeside(text),
                    if (label == "确认导入") scheme.onPrimary else scheme.onSurface
                )
            }
        }
        assertCaptureIsolation()
    }

    @Test
    fun sourceAndHelpDialogs_keepReadableBodyAndActionsAcrossEightSchemes() {
        val current = renderColors()
        revealSection("资料库备份")
        sectionButton("资料库备份", "导入").performTouchInput { click() }
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = dialogRoot(SourceTitle)
            listOf(SourceTitle, "从相册（PNG）", "从文件（JSON）", "取消").forEach { label ->
                val text = windowNode(dialogMatcher(SourceTitle), hasText(label), unmerged = true)
                assertPair(text, surfaceBeside(text, root), scheme.onSurface)
            }
            val body = windowNode(
                dialogMatcher(SourceTitle),
                hasText("从相册选 Tavern PNG 角色卡，或从文件选 JSON / 文本。"),
                unmerged = true
            )
            assertPair(body, surfaceBeside(body, root), scheme.onSurfaceVariant)
        }
        windowNode(
            dialogMatcher(SourceTitle),
            hasText("取消") and hasClickAction()
        ).performTouchInput { click() }
        reveal(hasContentDescription("$IntroTitle：查看说明")).performTouchInput { click() }
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = dialogRoot(IntroTitle)
            val close = windowNode(dialogMatcher(IntroTitle), hasText("知道了"), unmerged = true)
            assertPair(close, surfaceBeside(close, root), scheme.onSurface)
            val body = windowNode(
                dialogMatcher(IntroTitle),
                hasText("角色包、世界书包、记忆档案包和预设包都在这里导入导出。只处理本地资料，不包含 API Key。"),
                unmerged = true
            )
            assertPair(body, surfaceBeside(body, root), scheme.onSurfaceVariant)
        }
        assertCaptureIsolation()
    }

    private fun renderColors(): MutableState<ColorScheme> {
        val current = mutableStateOf(schemes.first())
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        setColorSchemeStateStage(ScreenshotCases.require("E06_transfer-matcha-light"), current) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ContextTransferScreen(
                    uiState = state.value,
                    onExportJson = { _, _ -> error("配色验证不得导出") },
                    onPreviewImportPayload = { _, _ -> error("配色验证不得导入") },
                    onConfirmImport = { error("配色验证不得写入") },
                    onDismissImportPreview = { error("配色验证不得清预览") },
                    onConsumeMessage = { error("配色验证没有消息") },
                    onNavigateBack = { error("配色验证不得导航") },
                )
            }
        }
        return current
    }

    private fun assertPair(text: SemanticsNodeInteraction, surface: Color, expected: Color) {
        val foreground = textLayout(text).layoutInput.style.color
        assertEquals(expected, foreground)
        assertTrue(
            "实际文字与承载面必须至少4.5：$foreground / $surface",
            roleplayTextContrast(foreground, surface, surface) >= 4.5f
        )
    }

    private fun surfaceBeside(
        text: SemanticsNodeInteraction,
        root: SemanticsNodeInteraction = composeRule.onNodeWithTag(StageTag)
    ): Color {
        val bitmap = root.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("缺少实际像素")
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val pixels = bitmap.asImageBitmap().toPixelMap()
        return pixels[(bounds.left - 2).roundToInt()
            .coerceIn(0, pixels.width - 1), bounds.center.y.roundToInt()
            .coerceIn(0, pixels.height - 1)]
    }

    private fun assertRenderedTextContrast(text: SemanticsNodeInteraction, surface: Color) {
        val bitmap = text.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("缺少实际文字像素")
        val pixels = bitmap.asImageBitmap().toPixelMap()
        var strongestInkContrast = 1f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                strongestInkContrast = maxOf(
                    strongestInkContrast,
                    roleplayTextContrast(pixels[x, y], surface, surface)
                )
            }
        }
        if (strongestInkContrast < 4.5f) {
            val directory = File("build/outputs/p06-color-probes").apply { mkdirs() }
            File(directory, "busy-text-low-contrast.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(
            "包含实际透明叠层后的文字对比不足：$strongestInkContrast / surface=$surface",
            strongestInkContrast >= 4.5f
        )
    }
}
