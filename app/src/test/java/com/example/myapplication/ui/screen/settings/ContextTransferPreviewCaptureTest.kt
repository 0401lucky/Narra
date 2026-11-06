package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.test.hasText
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import org.junit.Assert.assertThrows
import org.junit.Test

/** 标题可见并不意味着正文可见；捕获前置必须拒绝旧的最小滚动位置。 */
class ContextTransferPreviewCaptureTest : ContextTransferRenderTest() {
    @Test
    fun dynamicPreview_requiresVisibleBodyCountsAndFirstConflict() {
        renderTransfer(
            ScreenshotCases.require("E06_transfer-preview-dynamic-light"),
            preview = true
        )
        reveal(hasText("安全上限提示"))
        assertThrows(AssertionError::class.java) { requirePreviewVisible(noticeOnly = false) }
        alignPreviewForCapture(noticeOnly = false)
        requirePreviewVisible(noticeOnly = false)
        assertCaptureIsolation()
    }

    @Test
    fun narrowNotice_requiresCompleteRealBodyInsteadOfOnlyHeader() {
        renderTransfer(
            ScreenshotCases.require("E06_transfer-preview-notice-narrow-light"),
            preview = true
        )
        reveal(hasText("安全上限提示"))
        assertThrows(AssertionError::class.java) { requirePreviewVisible(noticeOnly = true) }
        alignPreviewForCapture(noticeOnly = true)
        requirePreviewVisible(noticeOnly = true)
        assertCaptureIsolation()
    }
}
