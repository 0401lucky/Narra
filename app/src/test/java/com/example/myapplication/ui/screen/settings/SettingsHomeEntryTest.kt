package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeEntryTest : ScreenshotRenderTest() {
    @Test
    fun realPhoneProfile_settingsEntryDispatchesOnlySettingsCallback() {
        val events = mutableListOf<String>()
        renderMessagesShellPage(
            ScreenshotCases.require("H01_p01-messages-matcha-light"),
            AppColorTheme.MATCHA,
            false,
            callbacks = noOpPhoneCallbacks().copy(
                onOpenSettings = { events += "settings" },
                onOpenAssistantSettings = { events += "assistants" },
                onOpenWorldBookSettings = { events += "worldbooks" },
                onOpenMemorySettings = { events += "memory" },
            ),
        )
        composeRule.onNodeWithTag("phone_tab_Profile").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("phone_tab_Profile").assertIsSelected()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("设置"))
        composeRule.onNode(hasScrollToNodeAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        val settings = composeRule.onNode(hasText("设置") and hasClickAction()).assertIsDisplayed()
        val bounds = settings.fetchSemanticsNode().touchBoundsInRoot
        assertTrue(bounds.width >= 96f && bounds.height >= 96f)
        assertTrue("设置入口的真实点击区不能被主壳底栏覆盖",
            bounds.bottom <= composeRule.onNodeWithTag("phone_bottom_bar").fetchSemanticsNode().boundsInRoot.top)
        settings.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("settings"), events) }
    }
}
