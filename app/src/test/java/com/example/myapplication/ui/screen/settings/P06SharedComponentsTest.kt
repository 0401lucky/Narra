package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.screen.settings.worldbook.P06ColorTestSupport
import com.example.myapplication.ui.component.narraFilledTonalButtonColors
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class P06SharedComponentsTest : P06ColorTestSupport() {
    @Test
    fun introAndSectionHeader_keepOldLimitsAndForwardExplicitLayout() {
        val expanded = mutableStateOf(false)
        val current = mutableStateOf(schemes.first())
        val introTitle = "需要完整阅读的共享页面介绍标题与补充信息，还有不能省略的第二部分说明"
        val sectionTitle = "需要完整阅读的共享分组标题与补充信息，还有不能省略的第二部分说明"
        renderColors(current) {
            Column {
                Box(Modifier.width(180.dp).testTag("intro")) {
                    if (expanded.value) SettingsPageIntro(
                        introTitle,
                        contentPadding = PaddingValues(
                            horizontal = NarraUi.space24,
                            vertical = NarraUi.space16
                        ),
                        titleMaxLines = Int.MAX_VALUE, titleIsHeading = true
                    )
                    else SettingsPageIntro(introTitle)
                }
                Box(Modifier.width(180.dp).testTag("section")) {
                    if (expanded.value) SettingsSectionHeader(
                        sectionTitle, "",
                        contentPadding = PaddingValues(
                            horizontal = NarraUi.space16,
                            vertical = NarraUi.space8
                        ),
                        horizontalSpacing = NarraUi.space12, titleMaxLines = Int.MAX_VALUE,
                        titleIsHeading = true, titleColor = MaterialTheme.colorScheme.onSurface
                    )
                    else SettingsSectionHeader(sectionTitle, "")
                }
            }
        }
        for (title in listOf(introTitle, sectionTitle)) {
            val layout = textLayout(pageNode(hasText(title), unmerged = true))
            assertEquals(2, layout.lineCount)
            assertTrue(layout.isLineEllipsized(1))
            assertThrows(AssertionError::class.java) {
                assertCompleteText(pageNode(hasText(title), unmerged = true), title)
            }
        }
        assertLeftPadding("intro", introTitle, 8f)
        assertLeftPadding("section", sectionTitle, 8f)
        composeRule.runOnIdle { expanded.value = true }
        for (title in listOf(introTitle, sectionTitle)) {
            val text = pageNode(hasText(title), unmerged = true)
            assertCompleteText(text, title)
            text.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        assertLeftPadding("intro", introTitle, 48f)
        assertLeftPadding("section", sectionTitle, 32f)
        assertEquals(
            current.value.onSurface,
            textLayout(pageNode(hasText(sectionTitle), unmerged = true)).layoutInput.style.color
        )
    }

    @Test
    fun allHelpWrappers_forwardActualWindowShapeActionColorAndCloseSize() {
        val explicit = mutableStateOf(false)
        val current = mutableStateOf(schemes.last())
        val body = (1..20).joinToString("\n") { "共享帮助长正文第 $it 行，滚动后仍可关闭。" }
        renderColors(current, narrowCase) {
            Column {
                if (explicit.value) {
                    SettingsPageIntro(
                        "介绍帮助",
                        body,
                        helpDialogShape = NarraShapes.large,
                        helpActionContentColor = MaterialTheme.colorScheme.onSurface,
                        helpActionMinSize = NarraMinimumTouchTarget
                    )
                    SettingsSectionHeader(
                        "分组帮助",
                        body,
                        helpDialogShape = NarraShapes.large,
                        helpActionContentColor = MaterialTheme.colorScheme.onSurface,
                        helpActionMinSize = NarraMinimumTouchTarget
                    )
                    SettingsHelpButton(
                        "直接帮助",
                        body,
                        dialogShape = NarraShapes.large,
                        actionContentColor = MaterialTheme.colorScheme.onSurface,
                        actionMinSize = NarraMinimumTouchTarget
                    )
                } else {
                    SettingsPageIntro("介绍帮助", body)
                    SettingsSectionHeader("分组帮助", body)
                    SettingsHelpButton("直接帮助", body)
                }
            }
        }
        for (useExplicit in listOf(false, true)) {
            composeRule.runOnIdle { explicit.value = useExplicit }
            for (title in listOf("介绍帮助", "分组帮助", "直接帮助")) {
                touch(pageNode(hasContentDescription("$title：查看说明")))
                val root = dialogRoot(title)
                val image = nativeBitmap(root)
                val corner = Color(image.getPixel(8, 8))
                image.recycle()
                if (useExplicit) assertEquals("新24dp窗口圆角必须实际绘出", 0f, corner.alpha, 0.01f)
                else assertEquals("默认保持8dp窗口圆角", current.value.surface, corner)
                val scroll = windowNode(dialogMatcher(title), hasScrollAction(), unmerged = true)
                scrollToEnd(scroll, "sharedHelp")
                val close = windowNode(dialogMatcher(title), hasText("知道了") and hasClickAction())
                val closeText = windowNode(dialogMatcher(title), hasText("知道了"), unmerged = true)
                assertEquals(
                    if (useExplicit) current.value.onSurface else current.value.primary,
                    textLayout(closeText).layoutInput.style.color
                )
                if (useExplicit) {
                    assertActionBounds("共享关闭:$title", close, root)
                    assertReadable(closeText, besideText(closeText, root), root)
                }
                touch(close)
                assertThrows(AssertionError::class.java) { dialogRoot(title) }
            }
        }
    }

    @Test
    fun hintCardAndStatusPill_forwardExplicitShapeAndPadding() {
        val explicit = mutableStateOf(false)
        val current = mutableStateOf(schemes.first())
        renderColors(current) {
            Column {
                Box(Modifier.testTag("hint")) {
                    if (explicit.value) SettingsHintCard(
                        "提示标题",
                        "提示正文",
                        current.value.primaryContainer,
                        current.value.primary,
                        shape = NarraShapes.large,
                        contentPadding = PaddingValues(NarraUi.space24)
                    )
                    else SettingsHintCard(
                        "提示标题",
                        "提示正文",
                        current.value.primaryContainer,
                        current.value.primary
                    )
                }
                Box(Modifier.testTag("pill")) {
                    if (explicit.value) SettingsStatusPill(
                        "状态标签",
                        current.value.primaryContainer,
                        current.value.onPrimaryContainer,
                        shape = NarraShapes.small,
                        contentPadding = PaddingValues(
                            horizontal = NarraUi.space24,
                            vertical = NarraUi.space16
                        )
                    )
                    else SettingsStatusPill(
                        "状态标签",
                        current.value.primaryContainer,
                        current.value.onPrimaryContainer
                    )
                }
            }
        }
        assertLeftPadding("hint", "提示正文", 46f)
        assertLeftPadding("pill", "状态标签", 24f)
        composeRule.runOnIdle { explicit.value = true }
        assertLeftPadding("hint", "提示正文", 58f)
        assertLeftPadding("pill", "状态标签", 48f)
        val pill = composeRule.onNodeWithTag("pill").fetchSemanticsNode().boundsInRoot
        val text = pageNode(hasText("状态标签"), unmerged = true).fetchSemanticsNode().boundsInRoot
        assertEquals(32f, text.top - pill.top, 0.5f)
        assertReadable(
            pageNode(hasText("提示正文"), unmerged = true),
            expected = current.value.onSurfaceVariant
        )
    }

    @Test
    fun primaryButton_keepsLegacyDisabledAlphaAndForwardsOptIn() =
        checkDisabledAlpha(primary = true)

    @Test
    fun outlinedButton_keepsLegacyDisabledAlphaAndForwardsOptIn() =
        checkDisabledAlpha(primary = false)

    @Test
    fun primaryButton_forwardsShapeAndContentPadding() = checkButtonLayout(primary = true)

    @Test
    fun outlinedButton_forwardsShapeAndContentPadding() = checkButtonLayout(primary = false)

    @Test
    fun topBarAction_keepsLegacyAlphaAndForwardsExplicitDisabledColors() {
        val explicit = mutableStateOf(false)
        val current = mutableStateOf(schemes.first())
        var saves = 0
        renderColors(current) {
            if (explicit.value) SettingsTopBar(
                "共享标题", onNavigateBack = {}, actionLabel = "保存",
                onAction = { saves++ }, actionEnabled = false,
                actionColors = narraFilledTonalButtonColors().copy(
                    disabledContainerColor = current.value.surfaceVariant,
                    disabledContentColor = current.value.onSurfaceVariant
                ), actionDisabledAlpha = 1f
            )
            else SettingsTopBar(
                "共享标题",
                onNavigateBack = {},
                actionLabel = "保存",
                onAction = { saves++ },
                actionEnabled = false
            )
        }
        val action = pageNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
        val label = pageNode(hasText("保存"), unmerged = true)
        val surface = controlSurface(action)
        val legacy = current.value.secondaryContainer.copy(alpha = 0.45f)
            .compositeOver(current.value.background)
        assertEquals(legacy.red, surface.red, 0.01f)
        assertEquals(legacy.green, surface.green, 0.01f)
        assertEquals(legacy.blue, surface.blue, 0.01f)
        assertTrue(maximumInkContrast(label, surface) < 4.5f)
        composeRule.runOnIdle { explicit.value = true }
        assertReadable(label, controlSurface(action), expected = current.value.onSurfaceVariant)
        touch(action)
        assertEquals(0, saves)
    }

    private fun checkDisabledAlpha(primary: Boolean) {
        val current = mutableStateOf(schemes.first())
        var clicks = 0
        renderColors(current) {
            val scheme = MaterialTheme.colorScheme
            val colors = ButtonDefaults.buttonColors(
                containerColor = scheme.surfaceVariant,
                contentColor = scheme.onSurfaceVariant,
                disabledContainerColor = scheme.surfaceVariant,
                disabledContentColor = scheme.onSurfaceVariant
            )
            Column {
                AnimatedSettingButton("旧禁用操作", { clicks++ }, false, primary, colors = colors)
                AnimatedSettingButton(
                    "可读禁用操作",
                    { clicks++ },
                    false,
                    primary,
                    colors = colors,
                    disabledAlpha = 1f
                )
            }
        }
        val legacy = pageNode(hasText("旧禁用操作") and hasClickAction()).assertIsNotEnabled()
        val readable = pageNode(hasText("可读禁用操作") and hasClickAction()).assertIsNotEnabled()
        val legacySurface = controlSurface(legacy)
        val expectedLegacy =
            current.value.surfaceVariant.copy(alpha = 0.45f).compositeOver(current.value.background)
        assertEquals(expectedLegacy.red, legacySurface.red, 0.01f)
        assertEquals(expectedLegacy.green, legacySurface.green, 0.01f)
        assertEquals(expectedLegacy.blue, legacySurface.blue, 0.01f)
        assertTrue(
            maximumInkContrast(
                pageNode(hasText("旧禁用操作"), unmerged = true),
                legacySurface
            ) < 4.5f
        )
        assertReadable(
            pageNode(hasText("可读禁用操作"), unmerged = true),
            controlSurface(readable),
            expected = current.value.onSurfaceVariant
        )
        touch(legacy)
        touch(readable)
        assertEquals(0, clicks)
    }

    private fun checkButtonLayout(primary: Boolean) {
        val explicit = mutableStateOf(false)
        val current = mutableStateOf(schemes.first())
        var clicks = 0
        renderColors(current) {
            val colors = ButtonDefaults.buttonColors(
                containerColor = current.value.primary,
                contentColor = current.value.onPrimary
            )
            Box(Modifier.testTag("button")) {
                if (explicit.value) AnimatedSettingButton(
                    "样式操作",
                    { clicks++ },
                    true,
                    primary,
                    shape = NarraShapes.large,
                    contentPadding = PaddingValues(NarraUi.space32),
                    colors = colors
                )
                else AnimatedSettingButton("样式操作", { clicks++ }, true, primary, colors = colors)
            }
        }
        val button = composeRule.onNodeWithTag("button")
        assertEquals(104f, button.fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(current.value.primary, cornerColor(button))
        composeRule.runOnIdle { explicit.value = true }
        assertEquals(current.value.background, cornerColor(button))
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val text = pageNode(hasText("样式操作"), unmerged = true).fetchSemanticsNode().boundsInRoot
        assertEquals(64f, text.top - bounds.top, 1f)
        touch(pageNode(hasText("样式操作") and hasClickAction()))
        assertEquals(1, clicks)
    }

    private fun cornerColor(node: SemanticsNodeInteraction): Color {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val image = nativeBitmap()
        return try {
            Color(image.getPixel((bounds.left + 8).roundToInt(), (bounds.top + 8).roundToInt()))
        } finally {
            image.recycle()
        }
    }

    private fun assertLeftPadding(tag: String, title: String, expectedPixels: Float) {
        val box = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val text = pageNode(hasText(title), unmerged = true).fetchSemanticsNode().boundsInRoot
        assertEquals(expectedPixels, text.left - box.left, 0.5f)
    }
}
