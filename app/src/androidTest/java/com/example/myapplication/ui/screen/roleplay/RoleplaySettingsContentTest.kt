package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.viewmodel.SettingsPreferenceDraftSupport
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun onlineBackgroundGlassSwitch_themeChangesAndReset_preserveDraftChoice() {
        var draft by mutableStateOf(
            SettingsUiState(
                roleplayNoBackgroundSkin = RoleplayNoBackgroundSkinSettings(
                    maxWidthPercent = 90,
                    bubbleRadiusDp = 24,
                    bubblePaddingHorizontalDp = 20,
                    bubblePaddingVerticalDp = 16,
                    showBubbleTail = true,
                ),
            ),
        )

        composeRule.setContent {
            val settings = AppSettings(roleplayNoBackgroundSkin = draft.roleplayNoBackgroundSkin)
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
                    draft = SettingsPreferenceDraftSupport.updateRoleplayNoBackgroundSkin(draft, skin)
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
        composeRule.onNodeWithTag("roleplay_online_background_glass_switch")
            .performScrollTo()
            .assertIsOn()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsOff()
        composeRule.onNodeWithContentDescription("线上背景玻璃气泡：查看说明").performClick()
        composeRule.onNodeWithText("作用于所有线上聊天；关闭后保留背景图，气泡使用所选主题。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithTag("roleplay_skin_preset_imessage")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()

        composeRule.runOnIdle {
            assertEquals(RoleplayNoBackgroundSkinPreset.IMESSAGE, draft.roleplayNoBackgroundSkin.preset)
            assertEquals(90, draft.roleplayNoBackgroundSkin.maxWidthPercent)
            assertFalse(draft.roleplayNoBackgroundSkin.onlineBackgroundGlassEnabled)
        }

        composeRule.onNodeWithTag("roleplay_skin_radius_slider")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        composeRule.runOnIdle {
            assertEquals(10, draft.roleplayNoBackgroundSkin.bubbleRadiusDp)
            assertFalse(draft.roleplayNoBackgroundSkin.onlineBackgroundGlassEnabled)
        }
        composeRule.onNodeWithContentDescription("重置气泡样式")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(
                RoleplayNoBackgroundSkinSettings(onlineBackgroundGlassEnabled = false),
                draft.roleplayNoBackgroundSkin,
            )
        }
        composeRule.onNodeWithTag("roleplay_online_background_glass_switch")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun settingHelp_largeFont_remainsIndependentFromSwitchAndStatus() {
        var checked by mutableStateOf(false)
        var switchCalls = 0
        val helpText = "开启后可提高聊天文字与背景之间的对比度。"
        val statusText = "系统高对比度已启用"

        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.width(280.dp)) {
                        RoleplaySettingSwitchRow(
                            palette = roleplayTestBackdropState().palette,
                            icon = { Box(modifier = Modifier.size(22.dp)) },
                            title = "高对比度",
                            supportingText = helpText,
                            statusText = statusText,
                            checked = checked,
                            switchModifier = Modifier.testTag("help_switch"),
                            onCheckedChange = {
                                checked = it
                                switchCalls++
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(helpText).assertDoesNotExist()
        composeRule.onNodeWithText(statusText).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("高对比度：查看说明")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(helpText).assertIsDisplayed()
        composeRule.runOnIdle { check(switchCalls == 0) }
        composeRule.onNodeWithText("知道了").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(helpText).assertDoesNotExist()

        composeRule.onNodeWithTag("help_switch")
            .assertIsDisplayed()
            .assertIsOff()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithText(statusText).assertIsDisplayed()
        composeRule.onNodeWithTag("help_switch").assertIsOn()
        composeRule.runOnIdle { check(switchCalls == 1) }
    }

    @Test
    fun systemHighContrastStatus_isVisibleWhileAppSwitchIsOff() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    RoleplaySettingsReadabilitySection(
                        backdropState = roleplayTestBackdropState(),
                        settings = AppSettings(roleplayHighContrast = false),
                        systemHighContrastEnabled = true,
                        onUpdateRoleplayImmersiveMode = {},
                        onUpdateRoleplayHighContrast = {},
                        onUpdateRoleplayLineHeightScale = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.roleplay_high_contrast_system_enabled),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("roleplay_high_contrast_switch").assertIsOff()
    }
}
