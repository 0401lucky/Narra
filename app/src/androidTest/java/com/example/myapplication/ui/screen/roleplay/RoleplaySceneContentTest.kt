package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplaySceneContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingTitleTogglesChromeHandleVisibility() {
        composeRule.setContent {
            RoleplaySceneContent(
                scenario = RoleplayScenario(
                    id = "scenario-test",
                    title = "测试场景",
                    autoHighlightSpeaker = false,
                ),
                assistant = null,
                settings = AppSettings(
                    roleplayImmersiveMode = RoleplayImmersiveMode.NONE,
                ),
                contextStatus = RoleplayContextStatus(),
                messages = emptyList(),
                suggestions = emptyList(),
                input = "",
                inputFocusToken = 0L,
                isSending = false,
                isGeneratingSuggestions = false,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                snackbarHostState = SnackbarHostState(),
                backdropState = roleplayTestBackdropState(),
                onInputChange = {},
                onGenerateSuggestions = {},
                onApplySuggestion = {},
                onClearSuggestions = {},
                onRetryTurn = {},
                onEditUserMessage = {},
                onOpenSpecialPlay = {},
                onConfirmTransferReceipt = {},
                onSend = {},
                onCancelSending = {},
                onOpenPhoneCheck = {},
                onApprovePendingMemoryProposal = {},
                onRejectPendingMemoryProposal = {},
                onOpenSettings = {},
                onNavigateBack = {},
                showSpecialPlaySheet = false,
                activeSpecialPlayDraft = null,
                onDismissSpecialPlay = {},
                onOpenSpecialPlayEditor = {},
                onDismissSpecialPlayEditor = {},
                onSpecialPlayDraftChange = {},
                onSpecialPlayConfirm = {},
            )
        }

        composeRule.onNodeWithTag("roleplay_scene_title").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("roleplay_chrome_handle")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("roleplay_chrome_handle").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("roleplay_chrome_handle")
                .fetchSemanticsNodes().isEmpty()
        }
        check(
            composeRule.onAllNodesWithTag("roleplay_chrome_handle")
                .fetchSemanticsNodes().isEmpty(),
        )
        check(
            composeRule.onAllNodesWithTag("roleplay_scene_title")
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
