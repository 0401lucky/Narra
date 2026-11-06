package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.*
import org.junit.Test

/** E06 共享资料库 16 个真实页面/窗口；操作始终绑定对应资源组。 */
class ContextTransferScreenshotE06Test : ContextTransferRenderTest() {
    @Test
    fun e06_transfer_matcha_light() = captureScene("E06_transfer-matcha-light")

    @Test
    fun e06_transfer_matcha_dark() = captureScene("E06_transfer-matcha-dark")

    @Test
    fun e06_transfer_vanilla_light() = captureScene("E06_transfer-vanilla-light")

    @Test
    fun e06_transfer_vanilla_dark() = captureScene("E06_transfer-vanilla-dark")

    @Test
    fun e06_transfer_ocean_light() = captureScene("E06_transfer-ocean-light")

    @Test
    fun e06_transfer_ocean_dark() = captureScene("E06_transfer-ocean-dark")

    @Test
    fun e06_transfer_preview_dynamic_light() = captureScene("E06_transfer-preview-dynamic-light")

    @Test
    fun e06_transfer_preview_dynamic_dark() = captureScene("E06_transfer-preview-dynamic-dark")

    @Test
    fun e06_transfer_first_narrow_light() = captureScene("E06_transfer-first-narrow-light")

    @Test
    fun e06_transfer_last_narrow_dark() = captureScene("E06_transfer-last-narrow-dark")

    @Test
    fun e06_transfer_preview_notice_narrow_light() =
        captureScene("E06_transfer-preview-notice-narrow-light")

    @Test
    fun e06_transfer_preview_actions_narrow_dark() =
        captureScene("E06_transfer-preview-actions-narrow-dark")

    @Test
    fun e06_transfer_source_dialog_light() = captureScene("E06_transfer-source-dialog-light")

    @Test
    fun e06_transfer_source_dialog_dark() = captureScene("E06_transfer-source-dialog-dark")

    @Test
    fun e06_transfer_help_dialog_light() = captureScene("E06_transfer-help-dialog-light")

    @Test
    fun e06_transfer_help_dialog_dark() = captureScene("E06_transfer-help-dialog-dark")

    private fun captureScene(id: String) {
        val case = ScreenshotCases.require(id)
        renderTransfer(case, preview = "preview" in id)
        when {
            "source-dialog" in id -> {
                openSource()
                captureWindow(case, SourceTitle, listOf("从相册（PNG）", "从文件（JSON）", "取消"))
            }

            "help-dialog" in id -> {
                captureClick(pageNode(hasContentDescription("$IntroTitle：查看说明")), "help:intro")
                scrollToEnd(
                    windowNode(
                        dialogMatcher(IntroTitle),
                        hasScrollAction(),
                        unmerged = true
                    ), "windowBody"
                )
                captureWindow(case, IntroTitle, listOf("知道了"))
            }

            "first-narrow" in id -> {
                revealSection("资料库备份")
                recordSection("资料库备份")
                capturePage(
                    case,
                    hasText("导入") and hasClickAction() and hasAnyAncestor(groupMatcher("资料库备份"))
                )
            }

            "last-narrow" in id -> {
                scrollToEnd()
                recordSection("预设包")
                requireFullyVisible("预设包Export")
                requireFullyVisible("预设包Import")
                capturePage(
                    case,
                    hasText("导入") and hasClickAction() and hasAnyAncestor(groupMatcher("预设包"))
                )
            }

            "preview-actions" in id -> {
                scrollToEnd()
                recordBounds("confirmPreview", pageNode(hasText("确认导入") and hasClickAction()))
                recordBounds("cancelPreview", pageNode(hasText("取消") and hasClickAction()))
                requireFullyVisible("confirmPreview")
                requireFullyVisible("cancelPreview")
                capturePage(case, hasText("确认导入") and hasClickAction())
            }

            "preview" in id -> {
                alignPreviewForCapture(noticeOnly = "notice-narrow" in id)
                capturePage(case, hasText("安全上限提示"))
            }

            else -> {
                pageNode(hasText("当前共有 4 条")).assertIsDisplayed()
                capturePage(case, hasText("资料库概览"))
            }
        }
    }
}
