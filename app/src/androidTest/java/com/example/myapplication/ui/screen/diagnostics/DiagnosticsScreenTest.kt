package com.example.myapplication.ui.screen.diagnostics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.system.diagnostics.HistoricalExitQuerySupport
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.DiagnosticsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun diagnosticsScreen_showsLocalSummaryAndTriggersExplicitExport() {
        var exportCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                DiagnosticsScreen(
                    uiState = DiagnosticsUiState(
                        isLoading = false,
                        supportStatus = HistoricalExitQuerySupport.SUPPORTED,
                        recordCount = 3,
                        latestExitTimestampEpochMillis = 1_700_000_000_000L,
                    ),
                    onExport = { exportCalls++ },
                    onConsumeMessage = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(DIAGNOSTICS_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("共 3 条，最多保留 20 条").assertIsDisplayed()
        composeRule.onNodeWithText("导出诊断信息")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, exportCalls)
        }
    }

    @Test
    fun diagnosticsScreen_apiBelow30ShowsUnsupportedMessage() {
        composeRule.setContent {
            ChatAppTheme {
                DiagnosticsScreen(
                    uiState = DiagnosticsUiState(
                        isLoading = false,
                        supportStatus = HistoricalExitQuerySupport.UNSUPPORTED,
                    ),
                    onExport = {},
                    onConsumeMessage = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Android 11 以下不支持历史退出查询")
            .assertIsDisplayed()
    }
}
