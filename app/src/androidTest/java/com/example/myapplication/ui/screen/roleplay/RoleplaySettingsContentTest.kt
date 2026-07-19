package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplaySettingsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun togglingImmersiveAndReadabilityOptions_updatesSelectionState() {
        var settings by mutableStateOf(AppSettings())

        composeRule.setContent {
            RoleplaySettingsContent(
                activePage = RoleplaySettingsPanelPage.THEME,
                scenario = null,
                assistant = null,
                settings = settings,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList(),
                currentModel = "",
                currentProviderId = "",
                providerOptions = emptyList(),
                backdropState = roleplayTestBackdropState(),
                latestPromptDebugDump = "",
                contextGovernance = null,
                recentMemoryProposalHistory = emptyList(),
                longMemoryCount = 0,
                sceneMemoryCount = 0,
                isRefreshingConversationSummary = false,
                longformCharsText = settings.roleplayLongformTargetChars.toString(),
                onLongformCharsTextChange = {},
                onNavigateToPage = {},
                onOpenReadingMode = {},
                onOpenModelPicker = {},
                onOpenContextLog = {},
                onUpdateShowRoleplayPresenceStrip = { enabled ->
                    settings = settings.copy(showRoleplayPresenceStrip = enabled)
                },
                onUpdateShowRoleplayAiHelper = { enabled ->
                    settings = settings.copy(showRoleplayAiHelper = enabled)
                },
                onUpdateScenarioNarrationEnabled = {},
                onUpdateScenarioDeepImmersionEnabled = {},
                onUpdateScenarioTimeAwarenessEnabled = {},
                onUpdateScenarioNetMemeEnabled = {},
                onUpdateScenarioOnlineProactiveReplyEnabled = {},
                systemHighContrastEnabled = false,
                onUpdateRoleplayImmersiveMode = { mode ->
                    settings = settings.copy(roleplayImmersiveMode = mode)
                },
                onUpdateRoleplayHighContrast = { enabled ->
                    settings = settings.copy(roleplayHighContrast = enabled)
                },
                onUpdateRoleplayLineHeightScale = { scale ->
                    settings = settings.copy(roleplayLineHeightScale = scale)
                },
                onUpdateRoleplayNoBackgroundSkin = { skin ->
                    settings = settings.copy(roleplayNoBackgroundSkin = skin)
                },
                onUpdateShowOnlineRoleplayNarration = { enabled ->
                    settings = settings.copy(showOnlineRoleplayNarration = enabled)
                },
                onUpdateRoleplayLongformTargetChars = {},
                onUpdateScenarioInteractionMode = {},
                onUpdateScenarioOnlineReplyRange = { _, _ -> },
                onAddGroupParticipant = {},
                onToggleGroupParticipantMuted = {},
                onRemoveGroupParticipant = {},
                onUpdateGroupReplyMode = {},
                onOpenProviderDetail = {},
                onOpenProviderSettings = {},
                onOpenAssistantPrompt = {},
                onOpenUserMasks = {},
                onOpenWorldBookSettings = {},
                onOpenLongMemorySettings = {},
                onUpdateAssistantMemoryEnabled = {},
                onRefreshConversationSummary = {},
                onShowRestartDialog = {},
                onShowResetDialog = {},
            )
        }

        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST)
            .performScrollToNode(hasTestTag("roleplay_immersive_hide_system_bars"))
        composeRule.onNodeWithTag("roleplay_immersive_hide_system_bars")
            .performClick()
        composeRule.onNodeWithTag("roleplay_immersive_hide_system_bars")
            .assertIsSelected()

        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST)
            .performScrollToNode(hasTestTag("roleplay_high_contrast_switch"))
        composeRule.onNodeWithTag("roleplay_high_contrast_switch")
            .performClick()
        composeRule.onNodeWithTag("roleplay_high_contrast_switch")
            .assertIsOn()

        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST)
            .performScrollToNode(hasTestTag("roleplay_line_height_relaxed"))
        composeRule.onNodeWithTag("roleplay_line_height_relaxed")
            .performClick()
        composeRule.onNodeWithTag("roleplay_line_height_relaxed")
            .assertIsSelected()

        composeRule.runOnIdle {
            check(settings.roleplayImmersiveMode == RoleplayImmersiveMode.HIDE_SYSTEM_BARS)
            check(settings.roleplayHighContrast)
            check(settings.roleplayLineHeightScale == RoleplayLineHeightScale.RELAXED)
        }
    }

    @Test
    fun selectingNoBackgroundSkinPreset_updatesPreviewState() {
        var settings by mutableStateOf(AppSettings())

        composeRule.setContent {
            RoleplaySettingsContent(
                activePage = RoleplaySettingsPanelPage.THEME,
                scenario = null,
                assistant = null,
                settings = settings,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList(),
                currentModel = "",
                currentProviderId = "",
                providerOptions = emptyList(),
                backdropState = roleplayTestBackdropState(),
                latestPromptDebugDump = "",
                contextGovernance = null,
                recentMemoryProposalHistory = emptyList(),
                longMemoryCount = 0,
                sceneMemoryCount = 0,
                isRefreshingConversationSummary = false,
                longformCharsText = settings.roleplayLongformTargetChars.toString(),
                onLongformCharsTextChange = {},
                onNavigateToPage = {},
                onOpenReadingMode = {},
                onOpenModelPicker = {},
                onOpenContextLog = {},
                onUpdateShowRoleplayPresenceStrip = {},
                onUpdateShowRoleplayAiHelper = {},
                onUpdateScenarioNarrationEnabled = {},
                onUpdateScenarioDeepImmersionEnabled = {},
                onUpdateScenarioTimeAwarenessEnabled = {},
                onUpdateScenarioNetMemeEnabled = {},
                onUpdateScenarioOnlineProactiveReplyEnabled = {},
                systemHighContrastEnabled = false,
                onUpdateRoleplayImmersiveMode = {},
                onUpdateRoleplayHighContrast = {},
                onUpdateRoleplayLineHeightScale = {},
                onUpdateRoleplayNoBackgroundSkin = { skin ->
                    settings = settings.copy(roleplayNoBackgroundSkin = skin)
                },
                onUpdateShowOnlineRoleplayNarration = {},
                onUpdateRoleplayLongformTargetChars = {},
                onUpdateScenarioInteractionMode = {},
                onUpdateScenarioOnlineReplyRange = { _, _ -> },
                onAddGroupParticipant = {},
                onToggleGroupParticipantMuted = {},
                onRemoveGroupParticipant = {},
                onUpdateGroupReplyMode = {},
                onOpenProviderDetail = {},
                onOpenProviderSettings = {},
                onOpenAssistantPrompt = {},
                onOpenUserMasks = {},
                onOpenWorldBookSettings = {},
                onOpenLongMemorySettings = {},
                onUpdateAssistantMemoryEnabled = {},
                onRefreshConversationSummary = {},
                onShowRestartDialog = {},
                onShowResetDialog = {},
            )
        }

        composeRule.onNodeWithTag("roleplay_skin_preview").assertExists()
        composeRule.onNodeWithTag("roleplay_skin_preset_imessage").performClick()

        composeRule.runOnIdle {
            check(settings.roleplayNoBackgroundSkin.preset == RoleplayNoBackgroundSkinPreset.IMESSAGE)
        }
    }
}
