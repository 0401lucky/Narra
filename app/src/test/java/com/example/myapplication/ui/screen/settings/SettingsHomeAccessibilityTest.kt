package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.R
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsHomeAccessibilityTest : SettingsHomeRenderTest() {
    @Test
    fun home_narrowLargeFont_longTextAndEveryVisibleDestinationRemainReachable() {
        renderHome()
        val heading = composeRule.onNode(hasText("设置") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        heading.assertIsDisplayed()
        assertTextFits(heading, "设置")
        val back = composeRule.onNodeWithContentDescription("返回")
        val save = composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save))
        assertTouchTarget(back)
        assertTouchTarget(save)
        assertFalse(back.fetchSemanticsNode().touchBoundsInRoot.overlaps(save.fetchSemanticsNode().touchBoundsInRoot))
        val destinations = listOf(
            composeRule.activity.getString(R.string.settings_default_model_prompt) to "models",
            composeRule.activity.getString(R.string.settings_provider) to "providers",
            "MiMo 语音合成" to "voice-synthesis",
            "语音识别" to "voice-recognition",
            composeRule.activity.getString(R.string.settings_version_update) to "updates",
            "使用条款与年龄限制" to "compliance",
            "本地诊断" to "diagnostics",
            "脚本实验室" to "scripts",
        )
        destinations.forEach { (label, _) ->
            val row = homeRow(label)
            row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            assertTouchTarget(row)
            assertTextFits(composeRule.onNodeWithText(label, useUnmergedTree = true), label)
            val summary = when (label) {
                composeRule.activity.getString(R.string.settings_default_model_prompt) -> LongModelName
                composeRule.activity.getString(R.string.settings_provider) -> LongProviderName
                else -> null
            }
            summary?.let { assertTextFits(composeRule.onNodeWithText(it, useUnmergedTree = true), it) }
            row.performTouchInput { click() }
        }
        save.assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(destinations.map { it.second }, events) }
        listOf("世界书", "记忆管理", "角色管理").forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun applicationRows_narrowLargeFont_adjacentTouchEdgesDoNotTriggerOtherActions() {
        renderHome(dark = true)
        homeRow("脚本实验室")
        val diagnostics = composeRule.onNodeWithText("本地诊断")
        val scripts = composeRule.onNodeWithText("脚本实验室")
        assertTouchTarget(diagnostics)
        assertTouchTarget(scripts)
        assertFalse(diagnostics.fetchSemanticsNode().touchBoundsInRoot.overlaps(scripts.fetchSemanticsNode().touchBoundsInRoot))
        diagnostics.performTouchInput { click(Offset(center.x, height - 1f)) }
        scripts.performTouchInput { click(Offset(center.x, 1f)) }
        composeRule.runOnIdle { assertEquals(listOf("diagnostics", "scripts"), events) }
    }
}
