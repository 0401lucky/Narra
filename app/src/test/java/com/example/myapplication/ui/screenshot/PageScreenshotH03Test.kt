package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import org.junit.Test

/**
 * H03：P03 内容边界现状 —— 跨多屏长回复、固定流式中间帧、回合生成失败。
 *
 * 流式用例关闭自动推进的测试时钟，并把光标脉冲固定在第 130ms（tween 520ms 的四分之一相位），
 * 保证“中间帧”在不同宿主、不同运行之间可复现。
 */
class PageScreenshotH03Test : ScreenshotRenderTest() {

    @Test
    fun h03_p03_online_long_reply() {
        val case = ScreenshotCases.require("H03_p03-online-long-reply")
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = listOf(
                ScreenshotFixture.characterMessage("long-reply-1", ScreenshotFixture.LongReplyText),
            ),
        )
        composeRule.onNodeWithText("线上模式").assertIsDisplayed()
        composeRule.onNodeWithText("第 1 段", substring = true).assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun h03_p03_online_streaming_frame() {
        val case = ScreenshotCases.require("H03_p03-online-streaming-frame")
        stopAnimationClock()
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = listOf(ScreenshotFixture.StreamingPartialMessage),
            isSending = true,
        )
        advanceAnimationClock(StreamingFrameMillis)
        composeRule.onNodeWithText(ScreenshotFixture.StreamingPartialText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("正在输入", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("停止生成").assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun h03_p03_online_error_message() {
        val case = ScreenshotCases.require("H03_p03-online-error-message")
        renderOnlineRoleplayPage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = listOf(ScreenshotFixture.ErrorMessage),
        )
        composeRule.onNodeWithText(ScreenshotFixture.ErrorMessageText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("本回合生成失败，可长按重回").assertIsDisplayed()
        captureCase(case)
    }

    private companion object {
        /** RoleplayStreamingCursorPulseMillis = 520ms 的四分之一相位。 */
        const val StreamingFrameMillis = 130L
    }
}
