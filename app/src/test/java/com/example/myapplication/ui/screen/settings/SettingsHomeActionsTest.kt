package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.test.*
import com.example.myapplication.R
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeActionsTest : SettingsHomeRenderTest() {
    @Test
    fun primaryAction_draftSavingSavedAndUnconfigured_useOnlyTheCurrentCallback() {
        renderHome()
        assertTrue(state.value.hasDraftChanges())
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf("save"), events)
            assertTrue("纯Screen只请求保存，不自行清草稿", state.value.hasDraftChanges())
            state.value = state.value.copy(isSaving = true)
        }
        val roleplayLabel = composeRule.activity.getString(R.string.settings_chat_label)
        composeRule.onNodeWithText(roleplayLabel).assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf("save"), events)
            state.value = state.value.copy(isSaving = false, messageTextScale = state.value.savedSettings.messageTextScale)
            assertFalse(state.value.hasDraftChanges())
        }
        composeRule.onNodeWithText(roleplayLabel).assertIsEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            val incomplete = state.value.providers.single().copy(apiKey = "", selectedModel = "")
            state.value = state.value.copy(
                providers = listOf(incomplete),
                savedSettings = state.value.savedSettings.copy(providers = listOf(incomplete)),
            )
            assertFalse(state.value.hasDraftChanges())
            assertFalse(state.value.savedSettings.hasRequiredConfig())
        }
        val home = composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_welcome_label))
        assertTouchTarget(home)
        home.performTouchInput { click() }
        composeRule.onNodeWithContentDescription("返回").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("save", "roleplay", "home", "back"), events) }
    }

    @Test
    fun message_isShownConsumedOnceAndDoesNotReturnOnUnrelatedRecomposition() {
        val message = "合成保存失败：配置未写入"
        state.value = state.value.copy(message = message)
        renderHome()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(events.isEmpty()) }
        composeRule.mainClock.advanceTimeBy(3_000)
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf("consume"), events)
            assertEquals(null, state.value.message)
            state.value = state.value.copy(codeBlockAutoWrap = true)
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.onNodeWithText(message).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf("consume"), events) }
    }
}
