package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longHelp_largeFont_keepsDismissVisibleAndDoesNotTriggerRowAction() {
        var rowClicks = 0
        val help = "支持 OpenAI 兼容协议，可自定义请求路径。\n".repeat(50)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.4f)) {
                ChatAppTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp)) {
                        SettingsGroup {
                            SettingsListRow(
                                title = "接口协议",
                                supportingText = "OpenAI 兼容",
                                onClick = { rowClicks++ },
                                trailingContent = { SettingsHelpButton("接口协议", help) },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText(help).assertDoesNotExist()
        composeRule.onNodeWithText("OpenAI 兼容").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("接口协议：查看说明")
            .assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("知道了").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(help).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, rowClicks) }
        composeRule.onNodeWithText("接口协议").performClick()
        composeRule.runOnIdle { assertEquals(1, rowClicks) }
    }
}
