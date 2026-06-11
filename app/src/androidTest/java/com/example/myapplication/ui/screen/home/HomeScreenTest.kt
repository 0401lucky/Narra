package com.example.myapplication.ui.screen.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingConfig_primaryActionOpensSettings() {
        var openSettingsCalls = 0
        var openRoleplayCalls = 0

        composeRule.setContent {
            HomeScreen(
                storedSettings = AppSettings(),
                onOpenSettings = { openSettingsCalls++ },
                onOpenRoleplay = { openRoleplayCalls++ },
            )
        }

        composeRule.onNodeWithText("配置提供商")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            check(openSettingsCalls == 1)
            check(openRoleplayCalls == 0)
        }
    }

    @Test
    fun completedConfig_primaryActionOpensRoleplay() {
        val context = composeRule.activity
        var openSettingsCalls = 0
        var openRoleplayCalls = 0

        composeRule.setContent {
            HomeScreen(
                storedSettings = AppSettings(
                    baseUrl = "https://example.com",
                    apiKey = "key",
                    selectedModel = "gpt-test",
                ),
                onOpenSettings = { openSettingsCalls++ },
                onOpenRoleplay = { openRoleplayCalls++ },
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.home_open_roleplay))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            check(openRoleplayCalls == 1)
            check(openSettingsCalls == 0)
        }
    }
}
