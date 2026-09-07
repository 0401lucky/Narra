package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplaySystemBarsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun outgoingDiaryDisposed_doesNotOverrideResumedImmersiveChat() {
        val diary = TestDestination()
        val chat = TestDestination()
        var showDiary by mutableStateOf(true)
        var showChat by mutableStateOf(false)
        composeRule.runOnUiThread {
            diary.registry.currentState = Lifecycle.State.RESUMED
            chat.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.setContent {
            ChatAppTheme(darkTheme = false) {
                if (showDiary) {
                    CompositionLocalProvider(LocalLifecycleOwner provides diary) {
                        ApplyRoleplaySurfaceSystemBars(Color.White, RoleplayImmersiveMode.NONE)
                    }
                }
                if (showChat) {
                    CompositionLocalProvider(LocalLifecycleOwner provides chat) {
                        ApplyRoleplaySurfaceSystemBars(Color.Black, RoleplayImmersiveMode.HIDE_SYSTEM_BARS)
                    }
                }
            }
        }
        val controller = WindowCompat.getInsetsController(composeRule.activity.window, composeRule.activity.window.decorView)
        composeRule.runOnIdle {
            assertTrue(controller.isAppearanceLightStatusBars)
            diary.registry.currentState = Lifecycle.State.STARTED
            chat.registry.currentState = Lifecycle.State.STARTED
            showChat = true
        }
        composeRule.runOnIdle { chat.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle {
            assertFalse(controller.isAppearanceLightStatusBars)
            showDiary = false
        }
        composeRule.runOnIdle {
            assertFalse(controller.isAppearanceLightStatusBars)
            assertEquals(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                controller.systemBarsBehavior,
            )
            chat.registry.currentState = Lifecycle.State.STARTED
            assertTrue(controller.isAppearanceLightStatusBars)
        }
    }
}

private class TestDestination : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}
