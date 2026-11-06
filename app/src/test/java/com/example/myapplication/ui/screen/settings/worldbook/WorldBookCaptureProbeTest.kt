package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.WorldBookRenderTest
import com.example.myapplication.model.WorldBookScopeType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 捕获前设施探针：不生成/接纳 PNG，不把尚未打开的独立窗口替换为底页。 */
class WorldBookCaptureProbeTest : WorldBookRenderTest() {
    @Test
    fun ordinaryPage_usesFixtureAndShadowClock() {
        assertEquals("10 分钟前", formatRelativeTime(FixedEpochMillis - 600_000L))
        renderList(ScreenshotCases.require("E06_list-matcha-light"))
        pageNode(hasText(BookName) and hasClickAction()).assertIsDisplayed()
        pageNode(hasText("10 分钟前")).assertIsDisplayed()
        assertEquals("10 分钟前", formatRelativeTime(FixedEpochMillis - 600_000L))
        assertTrue(events.isEmpty())
        probeRenderedRoot("ordinary-page", composeRule.onNodeWithTag(StageTag))
    }

    @Test
    fun realDialog_isIndependentAndMissingWindowFails() {
        renderList(ScreenshotCases.require("E06_list-help-dialog-light"))
        assertThrows(AssertionError::class.java) { dialogRoot("世界书") }
        captureClick(pageNode(hasContentDescription("世界书：查看说明")), "help")
        dialogRoot("世界书").assertIsDisplayed()
        probeRenderedRoot("list-dialog", dialogRoot("世界书"))
        windowNode(dialogMatcher("世界书"), hasText("知道了") and hasClickAction()).performClick()
        assertThrows(AssertionError::class.java) { dialogRoot("世界书") }
        assertTrue(events.isEmpty())
    }

    @Test
    fun bookPopup_isIndependentAndLastChoiceUsesRealScroll() {
        renderEditor(ScreenshotCases.require("E06_editor-book-popup-light"))
        assertThrows(AssertionError::class.java) { popupRoot(LastBookName) }
        openBookPopup()
        popupToEnd(LastBookName)
        probeRenderedRoot("book-popup", popupRoot(LastBookName), popup = true)
        windowNode(
            popupMatcher(LastBookName),
            hasText(LastBookName) and hasClickAction()
        ).performClick()
        assertThrows(AssertionError::class.java) { popupRoot(LastBookName) }
        pageNode(hasText(LastBookName) and hasSetTextAction()).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun conversationPopup_usesFixedRelativeTimeAndRealLastId() {
        renderEditor(ScreenshotCases.require("E06_editor-conversation-popup-light"))
        openConversationPopup()
        popupToEnd(LastConversationTitle)
        windowNode(
            popupMatcher(LastConversationTitle),
            hasText("50分钟前"),
            unmerged = true
        ).assertIsDisplayed()
        probeRenderedRoot("conversation-popup", popupRoot(LastConversationTitle), popup = true)
        windowNode(
            popupMatcher(LastConversationTitle),
            hasText(LastConversationTitle) and hasClickAction()
        ).performClick()
        reveal(hasText(LastConversationTitle) and hasClickAction()).assertIsDisplayed()
        pageNode(hasText("保存") and hasClickAction()).performClick()
        assertEquals(Conversations.last().id, savedEntries.single().scopeId)
        assertEquals(WorldBookScopeType.CONVERSATION, savedEntries.single().scopeType)
        assertEquals(listOf("save:${EditorEntry.id}", "back"), events)
    }

    @Test
    fun bodySheet_lastLineIsInsideRealTextViewport() {
        renderEditor(ScreenshotCases.require("E06_editor-body-sheet-light"))
        openBodySheet()
        File("build/outputs/p06-capture-probes/body-unmerged-before.txt").apply { parentFile?.mkdirs() }
            .writeText(dialogRoot("编辑正文").printToString())
        bodyToEnd()
        probeRenderedRoot("body-sheet-tail", dialogRoot("编辑正文"))
        assertTrue(events.isEmpty())
    }
}
