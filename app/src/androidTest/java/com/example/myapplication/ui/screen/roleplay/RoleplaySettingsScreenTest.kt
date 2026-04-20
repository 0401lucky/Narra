package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplaySettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun restartConfirmation_waitsForSuccessCallbackBeforeNavigatingBack() {
        var navigateBackCalls = 0
        var restartCalls = 0
        var pendingSuccess: (() -> Unit)? = null

        composeRule.setContent {
            RoleplaySettingsScreen(
                scenario = RoleplayScenario(
                    id = "scene-1",
                    title = "雨夜",
                    assistantId = "assistant-1",
                ),
                assistant = null,
                settings = AppSettings(),
                contextStatus = RoleplayContextStatus(),
                currentModel = "",
                currentProviderId = "",
                providerOptions = emptyList(),
                isLoadingModels = false,
                loadingProviderId = "",
                isSavingModel = false,
                latestPromptDebugDump = "",
                contextGovernance = null,
                recentMemoryProposalHistory = emptyList(),
                onOpenReadingMode = {},
                onUpdateShowRoleplayPresenceStrip = {},
                onUpdateShowRoleplayStatusStrip = {},
                onUpdateShowOnlineRoleplayNarration = {},
                onUpdateShowRoleplayAiHelper = {},
                onUpdateScenarioNarrationEnabled = {},
                onUpdateScenarioDeepImmersionEnabled = {},
                onUpdateScenarioTimeAwarenessEnabled = {},
                onUpdateScenarioNetMemeEnabled = {},
                onUpdateRoleplayLongformTargetChars = {},
                onUpdateScenarioInteractionMode = {},
                onUpdateRoleplayImmersiveMode = {},
                onUpdateRoleplayHighContrast = {},
                onUpdateRoleplayLineHeightScale = {},
                onSelectProvider = {},
                onSelectModel = { _, _ -> },
                onOpenProviderDetail = {},
                onRefreshConversationSummary = {},
                onRestartSession = { onSuccess ->
                    restartCalls++
                    pendingSuccess = onSuccess
                },
                onResetSession = {},
                onNavigateBack = { navigateBackCalls++ },
            )
        }

        val restartLabel = composeRule.activity.getString(R.string.roleplay_settings_action_restart)
        val confirmLabel = composeRule.activity.getString(R.string.roleplay_restart_dialog_confirm)

        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST)
            .performScrollToNode(hasText(restartLabel))
        composeRule.onNodeWithText(restartLabel).performClick()
        composeRule.onNodeWithText(confirmLabel).performClick()

        composeRule.runOnIdle {
            check(restartCalls == 1)
            check(navigateBackCalls == 0)
            check(pendingSuccess != null)
            pendingSuccess?.invoke()
        }

        composeRule.runOnIdle {
            check(navigateBackCalls == 1)
        }
    }
}
