package com.example.myapplication.ui.screen.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun missingConfig_disablesOpenChatAndRoleplayButtons() {
        val context = composeRule.activity
        composeRule.setContent {
            HomeScreen(
                storedSettings = AppSettings(),
                onOpenChat = {},
                onOpenSettings = {},
                onOpenRoleplay = {},
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.home_open_chat))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.home_open_roleplay))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun completedConfig_enablesPrimaryActions() {
        val context = composeRule.activity
        composeRule.setContent {
            HomeScreen(
                storedSettings = AppSettings(
                    baseUrl = "https://example.com",
                    apiKey = "key",
                    selectedModel = "gpt-test",
                ),
                onOpenChat = {},
                onOpenSettings = {},
                onOpenRoleplay = {},
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.home_open_chat))
            .assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.home_open_roleplay))
            .assertIsEnabled()
    }
}
