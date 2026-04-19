package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayScreenQuickActionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun offlineQuickActions_matchOnlineEntryPointsAndBehaviors() {
        var openedMomentsCount = 0
        var openedVideoCallCount = 0
        var openedPhoneOwner: PhoneSnapshotOwnerType? = null

        composeRule.setContent {
            RoleplayScreen(
                scenario = RoleplayScenario(
                    id = "offline-quick-actions",
                    title = "线下场景",
                    interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
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
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
                isSending = false,
                isGeneratingSuggestions = false,
                isScenarioLoading = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                noticeMessage = null,
                errorMessage = null,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                callbacks = roleplayScreenCallbacks(
                    onOpenPhoneCheck = { ownerType -> openedPhoneOwner = ownerType },
                    onOpenMoments = { openedMomentsCount += 1 },
                    onOpenVideoCall = { openedVideoCallCount += 1 },
                ),
            )
        }

        val moreActionsLabel = composeRule.activity.getString(R.string.roleplay_more_actions)
        val quickActionsTitle = composeRule.activity.getString(R.string.roleplay_quick_actions_title)
        val phoneOwnerTitle = composeRule.activity.getString(R.string.roleplay_phone_owner_title)
        val phoneOwnerBody = composeRule.activity.getString(R.string.roleplay_phone_owner_body)
        val phoneOwnerCharacter = composeRule.activity.getString(R.string.roleplay_phone_owner_character)
        val cancelLabel = composeRule.activity.getString(R.string.common_cancel)

        composeRule.onNodeWithContentDescription(moreActionsLabel).performClick()
        composeRule.waitForText(quickActionsTitle)
        listOf(
            "日记",
            "语音",
            "查手机",
            "转账",
            "邀约",
            "礼物",
            "委托",
            "惩罚",
            "动态",
            "视频",
        ).forEach { label ->
            composeRule.waitForText(label)
        }

        composeRule.onNodeWithText("语音").performClick()
        composeRule.waitForText("发送语音")
        composeRule.onNodeWithText(cancelLabel).performClick()

        composeRule.onNodeWithContentDescription(moreActionsLabel).performClick()
        composeRule.onNodeWithText("动态").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openedMomentsCount)
        }

        composeRule.onNodeWithContentDescription(moreActionsLabel).performClick()
        composeRule.onNodeWithText("视频").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openedVideoCallCount)
        }

        composeRule.onNodeWithContentDescription(moreActionsLabel).performClick()
        composeRule.onNodeWithText("查手机").performClick()
        composeRule.waitForText(phoneOwnerTitle)
        composeRule.waitForText(phoneOwnerBody)
        composeRule.onNodeWithText(phoneOwnerCharacter).performClick()
        composeRule.runOnIdle {
            assertEquals(PhoneSnapshotOwnerType.CHARACTER, openedPhoneOwner)
        }
    }
}

private fun roleplayScreenCallbacks(
    onOpenPhoneCheck: (PhoneSnapshotOwnerType) -> Unit = {},
    onOpenMoments: () -> Unit = {},
    onOpenVideoCall: () -> Unit = {},
): RoleplayScreenCallbacks {
    return RoleplayScreenCallbacks(
        message = RoleplayMessageCallbacks(
            onInputChange = {},
            onSend = {},
            onCancelSending = {},
            onRetryTurn = {},
            onEditUserMessage = {},
            onQuoteMessage = { _, _, _ -> },
            onClearQuotedMessage = {},
            onRecallMessage = {},
            onCaptureOnlineChat = {},
            onSendSpecialPlay = {},
            onSendVoiceMessage = {},
            onConfirmTransferReceipt = {},
        ),
        suggestion = RoleplaySuggestionCallbacks(
            onGenerateSuggestions = {},
            onApplySuggestion = {},
            onClearSuggestions = {},
        ),
        navigation = RoleplayNavigationCallbacks(
            onOpenDiary = {},
            onOpenPhoneCheck = onOpenPhoneCheck,
            onOpenMoments = onOpenMoments,
            onOpenVideoCall = onOpenVideoCall,
            onOpenReadingMode = {},
            onOpenSettings = {},
            onNavigateBack = {},
        ),
        session = RoleplaySessionCallbacks(
            onRestartSession = {},
            onDismissAssistantMismatch = {},
            onApprovePendingMemoryProposal = {},
            onRejectPendingMemoryProposal = {},
        ),
        ui = RoleplayUiCallbacks(
            onClearNoticeMessage = {},
            onClearErrorMessage = {},
        ),
    )
}

private fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.waitForText(
    text: String,
) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}
