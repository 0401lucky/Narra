package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
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
                scenario = null,
                assistant = null,
                settings = settings,
                contextStatus = RoleplayContextStatus(),
                currentModel = "",
                backdropState = roleplayTestBackdropState(),
                latestPromptDebugDump = "",
                contextGovernance = null,
                recentMemoryProposalHistory = emptyList(),
                longformCharsText = settings.roleplayLongformTargetChars.toString(),
                onLongformCharsTextChange = {},
                onOpenReadingMode = {},
                onOpenModelPicker = {},
                onOpenPromptDebugSheet = {},
                onUpdateShowRoleplayPresenceStrip = { enabled ->
                    settings = settings.copy(showRoleplayPresenceStrip = enabled)
                },
                onUpdateShowRoleplayStatusStrip = { enabled ->
                    settings = settings.copy(showRoleplayStatusStrip = enabled)
                },
                onUpdateShowRoleplayAiHelper = { enabled ->
                    settings = settings.copy(showRoleplayAiHelper = enabled)
                },
                onUpdateScenarioNarrationEnabled = {},
                onUpdateScenarioDeepImmersionEnabled = {},
                onUpdateScenarioTimeAwarenessEnabled = {},
                onUpdateScenarioNetMemeEnabled = {},
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
                onUpdateShowOnlineRoleplayNarration = { enabled ->
                    settings = settings.copy(showOnlineRoleplayNarration = enabled)
                },
                onUpdateScenarioInteractionMode = {},
                onShowRestartDialog = {},
                onShowResetDialog = {},
            )
        }

        composeRule.onNodeWithTag("roleplay_immersive_hide_system_bars")
            .performClick()
        composeRule.onNodeWithTag("roleplay_immersive_hide_system_bars")
            .assertIsSelected()

        composeRule.onNodeWithTag("roleplay_high_contrast_switch")
            .performClick()
        composeRule.onNodeWithTag("roleplay_high_contrast_switch")
            .assertIsOn()

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
}
