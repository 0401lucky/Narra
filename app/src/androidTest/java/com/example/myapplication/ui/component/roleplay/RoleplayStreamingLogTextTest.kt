package com.example.myapplication.ui.component.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayStreamingLogTextTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun streamingText_semanticsExposeOnlyBodyAndCursorIsDrawnSeparately() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.background(Color.White)) {
                    StreamingLogText(
                        content = "正在生成正文",
                        textColor = Color.Black,
                        accentColor = Color.Magenta,
                        modifier = Modifier.testTag(StreamingTextTag),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val node = composeRule.onNodeWithTag(StreamingTextTag)
        val semanticsText = node.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals("正在生成正文", semanticsText)

        val pixels = node.captureToImage().toPixelMap()
        var hasCursorPixel = false
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.7f && pixel.green < 0.4f && pixel.blue > 0.7f && pixel.alpha > 0.2f) {
                    hasCursorPixel = true
                    break
                }
            }
            if (hasCursorPixel) break
        }
        assertTrue("应绘制独立的流式光标", hasCursorPixel)
    }

    @Test
    fun streamingText_emptyAndGrowingBody_keepExactAccessibleText() {
        val content = mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                StreamingLogText(
                    content = content.value,
                    textColor = Color.Black,
                    accentColor = Color.Magenta,
                    modifier = Modifier.testTag(StreamingTextTag),
                )
            }
        }

        val node = composeRule.onNodeWithTag(StreamingTextTag)
        assertEquals(
            "",
            node.fetchSemanticsNode().config[SemanticsProperties.Text].single().text,
        )

        val longContent = "长正文增长。".repeat(120)
        composeRule.runOnIdle { content.value = longContent }

        assertEquals(
            longContent,
            node.fetchSemanticsNode().config[SemanticsProperties.Text].single().text,
        )
    }

    private companion object {
        const val StreamingTextTag = "roleplay-streaming-log-text"
    }
}
