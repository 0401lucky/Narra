package com.example.myapplication.ui.screen.settings.worldbook

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.myapplication.ui.screenshot.P06RenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 独立测试 Popup，不向生产菜单添加像素标记，也不计入 E06 的 63 张图。 */
class P06PopupPixelGuardTest : P06RenderTest() {
    @Test
    fun pixelGuard_rejectsActivityPixelsAndCapturesIndependentPopupOwner() {
        renderP06(ScreenshotCases.require("E06_editor-book-popup-light")) {
            Box(Modifier.fillMaxSize().background(Color.Blue)) {
                Text(
                    "底页像素哨兵",
                    color = Color.White
                )
            }
            Popup {
                Box(Modifier.size(120.dp).background(Color.Magenta)) {
                    Text(
                        "Popup 像素哨兵",
                        color = Color.White
                    )
                }
            }
        }
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode()
            .fetchImage(RoborazziOptions.RecordOptions())!!
        assertThrows(AssertionError::class.java) { requirePopupPixels(stage) }
        val root = popupRoot("Popup 像素哨兵")
        val popup = renderPopupBitmap(root)
        assertEquals(240, popup.width)
        assertEquals(240, popup.height)
        requirePopupPixels(popup)
        probeRenderedRoot("popup-pixel-sentinel", root, popup = true)
    }

    private fun requirePopupPixels(bitmap: Bitmap) {
        assertEquals(
            "像素必须来自品红 Popup，不能来自蓝色 Activity",
            Color.Magenta.toArgb(),
            bitmap.getPixel(8, bitmap.height - 8)
        )
    }
}
