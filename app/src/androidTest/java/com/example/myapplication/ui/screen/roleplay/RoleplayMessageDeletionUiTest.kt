package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.pokeMessagePart
import com.example.myapplication.ui.component.roleplay.RoleplayMessageMenuWrapper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayMessageDeletionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialogueMenu_keepsRecallAndDeleteIndependentWithConfirmation() {
        val message = RoleplayMessageUiModel(
            sourceMessageId = "roleplay-message-1",
            contentType = RoleplayContentType.DIALOGUE,
            speaker = RoleplaySpeaker.USER,
            speakerName = "我",
            content = "待删除台词",
            messageStatus = MessageStatus.COMPLETED,
        )
        var recallCount = 0
        val confirmedMessageIds = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                var pendingSourceMessageId by remember { mutableStateOf<String?>(null) }
                Box {
                    RoleplayMessageMenuWrapper(
                        message = message,
                        onRetryTurn = {},
                        onEditUserMessage = {},
                        onRecallMessage = { recallCount += 1 },
                        onDeleteMessage = { pendingSourceMessageId = it },
                    ) {
                        Text(
                            text = "角色消息气泡",
                            modifier = Modifier.testTag("roleplay-message"),
                        )
                    }
                    RoleplayMessageDeleteConfirmationDialog(
                        sourceMessageId = pendingSourceMessageId,
                        messages = listOf(message),
                        isSending = false,
                        onDismiss = { pendingSourceMessageId = null },
                        onConfirm = { sourceMessageId ->
                            confirmedMessageIds += sourceMessageId
                            pendingSourceMessageId = null
                        },
                    )
                }
            }
        }

        openRoleplayMessageMenu()
        composeRule.onNodeWithText("撤回").assertExists()
        composeRule.onNodeWithText("删除消息").assertExists().performClick()
        composeRule.onNodeWithText("确认删除消息").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertEquals(0, recallCount)
            assertEquals(emptyList<String>(), confirmedMessageIds)
        }

        openRoleplayMessageMenu()
        composeRule.onNodeWithText("撤回").performClick()
        composeRule.runOnIdle {
            assertEquals(1, recallCount)
            assertEquals(emptyList<String>(), confirmedMessageIds)
        }

        openRoleplayMessageMenu()
        composeRule.onNodeWithText("删除消息").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.runOnIdle {
            assertEquals(1, recallCount)
            assertEquals(listOf(message.sourceMessageId), confirmedMessageIds)
        }
    }

    @Test
    fun pokeMessage_menuCanDeleteUnderlyingSourceMessage() {
        val message = RoleplayMessageUiModel(
            sourceMessageId = "poke-source-message",
            contentType = RoleplayContentType.ACTION,
            speaker = RoleplaySpeaker.SYSTEM,
            speakerName = "系统",
            content = "拍了拍角色",
            actionPart = pokeMessagePart(target = "角色", suffix = "的肩膀"),
            messageStatus = MessageStatus.COMPLETED,
        )
        val confirmedMessageIds = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                var pendingSourceMessageId by remember { mutableStateOf<String?>(null) }
                Box {
                    RoleplayMessageMenuWrapper(
                        message = message,
                        onRetryTurn = {},
                        onEditUserMessage = {},
                        onDeleteMessage = { pendingSourceMessageId = it },
                    ) {
                        Text(
                            text = "拍一拍消息",
                            modifier = Modifier.testTag("roleplay-message"),
                        )
                    }
                    RoleplayMessageDeleteConfirmationDialog(
                        sourceMessageId = pendingSourceMessageId,
                        messages = listOf(message),
                        isSending = false,
                        onDismiss = { pendingSourceMessageId = null },
                        onConfirm = { sourceMessageId ->
                            confirmedMessageIds += sourceMessageId
                            pendingSourceMessageId = null
                        },
                    )
                }
            }
        }

        openRoleplayMessageMenu()
        composeRule.onNodeWithText("撤回").assertDoesNotExist()
        composeRule.onNodeWithText("删除消息").assertExists().performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(message.sourceMessageId), confirmedMessageIds)
        }
    }

    private fun openRoleplayMessageMenu() {
        composeRule.onNodeWithTag("roleplay-message")
            .performTouchInput { longClick() }
    }
}
