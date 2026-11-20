package com.example.myapplication.ui.screen.settings

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.*
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class MemoryLifecycleConfirmationTest : ScreenshotRenderTest() {
    @Test
    fun clearFromPhone200Percent_cancelDoesNothingAndConfirmUsesSelectedScenario() =
        verify(delete = false)

    @Test
    fun deleteFromPhone200Percent_cancelDoesNothingAndConfirmUsesSelectedScenario() =
        verify(delete = true)

    private fun verify(delete: Boolean) {
        useNarrowFontScale()
        val cleared = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val summary = ScreenshotFixture.ChatSummaries.first()
        renderMessagesShellPage(
            ScreenshotCases.require("E04_reading-narrow-dark"),
            AppColorTheme.MATCHA,
            darkTheme = delete,
            summaries = listOf(summary),
            callbacks = noOpPhoneCallbacks().copy(
                onClearChat = { cleared += it },
                onDeleteChat = { deleted += it })
        )

        fun open() {
            composeRule.onNodeWithTag("phone_swipe_row_${summary.scenario.id}")
                .performTouchInput { swipeLeft() }
            composeRule.onNodeWithText(if (delete) "删除" else "清空").performClick()
        }

        open()
        val root = composeRule.onNode(isDialog())
        root.assertExists()
        composeRule.onNodeWithText(if (delete) "删除会话及历史档案" else "清空剧情")
            .assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(cleared.isEmpty()); assertTrue(deleted.isEmpty()) }
        composeRule.onNodeWithTag("narra_alert_dialog_content")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        val confirm =
            composeRule.onNode(hasText(if (delete) "删除会话" else "确定清空") and hasClickAction())
        confirm.assertIsDisplayed()
        val bounds = confirm.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= minimum && bounds.height >= minimum)
        val bitmap = root.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("确认窗口渲染为空")
        val directory = File("build/outputs/c6-ui-probes").apply { mkdirs() }
        val name = if (delete) "phone-delete-200-dark" else "phone-clear-200-light"
        File(directory, "$name.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(directory, "$name-semantics.txt").writeText(root.printToString())
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertTrue(cleared.isEmpty()); assertTrue(deleted.isEmpty()) }
        open()
        composeRule.onNode(hasText(if (delete) "删除会话" else "确定清空") and hasClickAction())
            .performClick()
        composeRule.runOnIdle {
            assertEquals(if (delete) emptyList<String>() else listOf(summary.scenario.id), cleared)
            assertEquals(if (delete) listOf(summary.scenario.id) else emptyList<String>(), deleted)
        }
    }
}
