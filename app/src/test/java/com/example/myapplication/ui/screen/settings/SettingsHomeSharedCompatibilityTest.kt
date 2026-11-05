package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeSharedCompatibilityTest : SettingsHomeRenderTest() {
    @Test
    fun bothGroupOverloads_forwardOptInShapeAndPaddingWhileOldDefaultsRemainVisible() {
        var background = androidx.compose.ui.graphics.Color.Unspecified
        var surface = androidx.compose.ui.graphics.Color.Unspecified
        renderHome(ScreenshotCases.require("E10_home-matcha-light"), content = {
            background = MaterialTheme.colorScheme.background
            surface = MaterialTheme.colorScheme.surface
            Column {
                Box(Modifier.testTag("plain")) {
                    SettingsGroup(shape = NarraShapes.large, contentPadding = PaddingValues(NarraUi.space24)) {
                        Text("无标题内容", Modifier.fillMaxWidth().testTag("plain-body"))
                    }
                }
                Box(Modifier.testTag("titled")) {
                    SettingsGroup(title = "有标题组", shape = NarraShapes.large, contentPadding = PaddingValues(NarraUi.space24)) {
                        Text("有标题内容", Modifier.fillMaxWidth().testTag("titled-body"))
                    }
                }
                Box(Modifier.testTag("legacy")) {
                    SettingsGroup { Text("旧调用内容", Modifier.fillMaxWidth().testTag("legacy-body")) }
                }
            }
        })
        listOf("plain", "titled").forEach { tag ->
            val group = composeRule.onNodeWithTag(tag)
            val bounds = group.fetchSemanticsNode().boundsInRoot
            val body = composeRule.onNodeWithTag("$tag-body").fetchSemanticsNode().boundsInRoot
            assertEquals("两个重载必须传递横向padding", 48f, body.left - bounds.left, 0.5f)
            assertEquals("两个重载必须传递纵向padding", 48f, bounds.bottom - body.bottom, 0.5f)
            val pixels = group.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
            assertEquals("两个重载必须实际使用24dp形状", background, pixels[8, pixels.height - 9])
        }
        val legacy = composeRule.onNodeWithTag("legacy")
        val legacyBounds = legacy.fetchSemanticsNode().boundsInRoot
        val legacyBody = composeRule.onNodeWithTag("legacy-body").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, legacyBody.left - legacyBounds.left, 0.5f)
        assertEquals(16f, legacyBounds.bottom - legacyBody.bottom, 0.5f)
        val pixels = legacy.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        assertEquals("旧调用保留8dp圆角的实际像素", surface, pixels[8, pixels.height - 9])
    }

    @Test
    fun legacyRow_keepsTwoLineLimitWhileExplicitLayoutCanShowCompleteText() {
        val title = "共享设置组件中需要完整保留的很长很长的标题"
        val supporting = "共享调用的长摘要仍按原有两行限制显示，只有明确选择新布局的页面才能展开全文。"
        val expanded = mutableStateOf(false)
        var clicks = 0
        renderHome(content = {
            if (expanded.value) {
                SettingsListRow(title, supporting, onClick = { clicks++ },
                    shape = NarraShapes.medium,
                    contentPadding = PaddingValues(horizontal = NarraUi.space16, vertical = NarraUi.space12),
                    horizontalSpacing = NarraUi.space12, textSpacing = NarraUi.space4,
                    titleMaxLines = Int.MAX_VALUE, supportingTextMaxLines = Int.MAX_VALUE)
            } else {
                SettingsListRow(title, supporting, onClick = { clicks++ })
            }
        })
        listOf(title, supporting).forEach { text ->
            val layout = textLayout(composeRule.onNodeWithText(text, useUnmergedTree = true))
            assertEquals(2, layout.lineCount)
            assertTrue(layout.isLineEllipsized(1))
        }
        composeRule.runOnIdle { expanded.value = true }
        listOf(title, supporting).forEach { text -> assertTextFits(composeRule.onNodeWithText(text, useUnmergedTree = true), text) }
        composeRule.onNodeWithText(title).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun legacyTopBar_keepsSingleLineWhileOptInTitleWrapsAndActionsRemainIndependent() {
        val title = "需要完整显示的设置页面标题"
        val expanded = mutableStateOf(false)
        renderHome(content = {
            SettingsTopBar(
                title = title,
                onNavigateBack = { events += "back" },
                actionLabel = "保存",
                onAction = { events += "save" },
                titleMaxLines = if (expanded.value) Int.MAX_VALUE else 1,
                horizontalPadding = if (expanded.value) NarraUi.space16 else SettingsScreenPadding,
            )
        })
        val old = textLayout(composeRule.onNodeWithText(title, useUnmergedTree = true))
        assertEquals(1, old.lineCount)
        assertTrue(old.isLineEllipsized(0))
        composeRule.runOnIdle { expanded.value = true }
        assertTextFits(composeRule.onNodeWithText(title, useUnmergedTree = true), title)
        listOf(composeRule.onNodeWithContentDescription("返回"), composeRule.onNodeWithText("保存")).forEach { node ->
            assertTouchTarget(node)
            node.performTouchInput { click() }
        }
        composeRule.runOnIdle { assertEquals(listOf("back", "save"), events) }
    }
}
