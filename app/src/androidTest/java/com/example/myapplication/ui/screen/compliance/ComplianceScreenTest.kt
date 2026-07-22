package com.example.myapplication.ui.screen.compliance

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.ComplianceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComplianceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gate_beforeReadingAndChecking_keepsAcceptDisabled() {
        composeRule.setContent {
            ChatAppTheme {
                ComplianceScreen(
                    uiState = ComplianceUiState(isLoading = false),
                    mode = ComplianceScreenMode.GATE,
                )
            }
        }

        composeRule.onNodeWithTag(COMPLIANCE_ACCEPT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(COMPLIANCE_TERMS_CHECKBOX_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(COMPLIANCE_ADULT_CHECKBOX_TAG).assertIsNotEnabled()
    }

    @Test
    fun gate_readToEndAndCheckBothItems_enablesAccept() {
        var acceptCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                ComplianceScreen(
                    uiState = ComplianceUiState(isLoading = false),
                    mode = ComplianceScreenMode.GATE,
                    onAccept = { acceptCalls++ },
                )
            }
        }

        repeat(12) {
            composeRule.onNodeWithTag(COMPLIANCE_CONTENT_TAG).performTouchInput {
                swipeUp()
            }
        }
        composeRule.onNodeWithTag(COMPLIANCE_TERMS_CHECKBOX_TAG).performClick()
        composeRule.onNodeWithTag(COMPLIANCE_ADULT_CHECKBOX_TAG).performClick()
        composeRule.onNodeWithTag(COMPLIANCE_ACCEPT_BUTTON_TAG).assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(1, acceptCalls)
        }
    }

    @Test
    fun viewOnly_showsCurrentAcceptanceInfoAndBackCallback() {
        var backCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                ComplianceScreen(
                    uiState = ComplianceUiState(
                        isLoading = false,
                        acceptedPolicyVersion = "2026-07-15-v1",
                        acceptedAtEpochMillis = 1_735_689_600_000L,
                    ),
                    mode = ComplianceScreenMode.VIEW_ONLY,
                    onNavigateBack = { backCalls++ },
                )
            }
        }

        composeRule.onNodeWithTag(COMPLIANCE_ACCEPTANCE_INFO_TAG).assertExists()
        composeRule.onNodeWithText("当前条款版本：2026-07-15-v1").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backCalls)
        }
    }
}
