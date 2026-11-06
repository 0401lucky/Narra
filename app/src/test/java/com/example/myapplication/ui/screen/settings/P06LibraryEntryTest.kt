package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.test.*
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotRenderTest
import org.junit.Assert.assertEquals
import org.junit.Test

class P06LibraryEntryTest : ScreenshotRenderTest() {
    @Test
    fun phoneProfile_worldBookEntryDispatchesOnlyWorldBookNavigation() =
        checkEntry("世界书", "worldbook")

    @Test
    fun phoneProfile_transferEntryDispatchesOnlyTransferNavigation() =
        checkEntry("资料导入导出", "transfer")

    private fun checkEntry(title: String, expected: String) {
        val events = mutableListOf<String>()
        renderMessagesShellPage(
            ScreenshotCases.require("H01_p01-messages-matcha-light"), AppColorTheme.MATCHA, false,
            callbacks = noOpPhoneCallbacks().copy(
                onOpenWorldBookSettings = { events += "worldbook" },
                onOpenContextTransferSettings = { events += "transfer" },
                onOpenSettings = { events += "settings" },
                onOpenAssistantSettings = { events += "assistant" },
            )
        )
        composeRule.onNodeWithTag("phone_tab_Profile").performTouchInput { click() }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        composeRule.onNode(hasText(title) and hasClickAction()).performScrollTo()
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(expected), events) }
    }
}
