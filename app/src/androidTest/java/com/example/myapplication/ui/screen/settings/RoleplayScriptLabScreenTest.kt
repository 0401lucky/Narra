package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.RoleplayScriptDraft
import com.example.myapplication.viewmodel.RoleplayScriptLabUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayScriptLabScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scriptLab_keepsTrialRunAndPermissionHelpAccessible() {
        var trialRunCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                RoleplayScriptLabScreen(
                    uiState = RoleplayScriptLabUiState(
                        templates = emptyList(),
                        draft = RoleplayScriptDraft(
                            grantedPermissions = setOf(RoleplayScriptPermission.READ_VARIABLES),
                        ),
                    ),
                    bindingOptions = emptyList(),
                    onCreateScript = {},
                    onSelectScript = {},
                    onApplyTemplate = {},
                    onUpdateName = {},
                    onUpdateScope = {},
                    onUpdateOwnerId = {},
                    onUpdateSource = {},
                    onUpdateEnabled = {},
                    onTogglePermission = {},
                    onUpdateTestEvent = {},
                    onUpdateTestUserText = {},
                    onUpdateTestPromptText = {},
                    onUpdateTestAssistantText = {},
                    onUpdateTestVariablesText = {},
                    onRunScriptTest = { trialRunCalls++ },
                    onSaveScript = {},
                    onDeleteSelectedScript = {},
                    onConsumeMessage = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("读取变量"))
        composeRule.onNodeWithContentDescription("读取变量：查看说明")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("知道了").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("运行当前脚本"))
        composeRule.onNodeWithText("运行当前脚本").performClick()
        composeRule.runOnIdle {
            assertEquals(1, trialRunCalls)
        }
    }
}
