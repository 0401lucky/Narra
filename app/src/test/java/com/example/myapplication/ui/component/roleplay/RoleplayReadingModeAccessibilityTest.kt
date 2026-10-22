package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.screenshot.*
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class RoleplayReadingModeAccessibilityTest : OfflineRoleplayRenderTest() {
    @Test fun readingLongText_scrollToEnd_keepsCloseReachableAtLargeFont() {
        useNarrowFontScale()
        var closed = 0
        renderReadingPage(ScreenshotCases.require("E04_reading-narrow-dark"), dark = true,
            messages = listOf(ScreenshotFixture.characterMessage("reading-long-test", ScreenshotFixture.LongReplyText)),
            title = OfflineRoleplayFixture.LongName, onDismiss = { closed++ })
        composeRule.onNodeWithText("第 12 段", substring = true).performScrollTo().assertIsDisplayed()
        val close = composeRule.onNodeWithContentDescription("关闭")
        close.assertIsDisplayed()
        val bounds = close.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= minimum && bounds.height >= minimum)
        close.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun readingMixedQuotation_usesActualReadableForeground() {
        renderReadingPage(ScreenshotCases.require("E04_reading-mixed-background"),
            messages = listOf(ScreenshotFixture.characterMessage("quote-readability", "「可读的引号正文」")))
        val node = composeRule.onNodeWithText("可读的引号正文", substring = true, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode()
        val pixels = stage.fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val surface = pixels[(bounds.left - stage.boundsInRoot.left - 4).toInt(), (bounds.center.y - stage.boundsInRoot.top).toInt()]
        val foreground = layout.layoutInput.text.spanStyles.first { it.item.color != Color.Unspecified }.item.color
        assertTrue("阅读引号正文 text=$foreground actual=$surface", roleplayTextContrast(foreground, surface, surface) >= 4.5f)
    }

    @Test fun emptyReading_largeFont_keepsContentAndCloseReachable() {
        useNarrowFontScale()
        var closed = 0
        renderReadingPage(ScreenshotCases.require("E04_reading-narrow-dark"), dark = true,
            messages = emptyList(), title = "", onDismiss = { closed++ })
        val list = composeRule.onNodeWithTag("roleplay_reading_list")
        list.performScrollToNode(hasText("还没有剧情内容"))
        composeRule.onNodeWithText("还没有剧情内容").assertIsDisplayed()
        val header = composeRule.onNodeWithTag("roleplay_reading_header").fetchSemanticsNode().boundsInRoot
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertTrue("阅读头部不遮挡正文", header.bottom <= viewport.top)
        assertTrue("阅读区域应有可读空间", viewport.height >= 96f)
        composeRule.onNodeWithContentDescription("关闭").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun readingMixedTypesAndLongTitle_largeFont_canScrollEveryBodyAndClose() {
        useNarrowFontScale()
        renderReadingPage(ScreenshotCases.require("E04_reading-narrow-dark"), dark = true,
            title = OfflineRoleplayFixture.LongName,
            messages = OfflineRoleplayFixture.MixedMessages.map { it.copy(speakerName = OfflineRoleplayFixture.LongName, emotion = "温柔又有些期待") } +
                ScreenshotFixture.narrationMessage("reading-system", "剧情时间已更新").copy(contentType = com.example.myapplication.model.RoleplayContentType.SYSTEM))
        val list = composeRule.onNodeWithTag("roleplay_reading_list")
        listOf(ScreenshotFixture.UserLineText, ScreenshotFixture.CharacterLineText,
            ScreenshotFixture.NarrationLineText, OfflineRoleplayFixture.ThoughtText, "雨声渐缓。", "剧情时间已更新").forEach { text ->
            list.performScrollToNode(hasText(text, substring = true))
            composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("关闭").assertIsDisplayed()
        }
    }
}
