package com.example.myapplication.ui.screen.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageDeletionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deleteMenu_requiresConfirmationAndCancelDoesNotInvokeDelete() {
        val message = ChatMessage(
            id = "chat-message-1",
            conversationId = "conversation-1",
            role = MessageRole.USER,
            content = "待删除消息",
            createdAt = 1L,
        )
        var deleteRequestCount = 0
        val confirmedMessageIds = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                var showSheet by remember { mutableStateOf(true) }
                var pendingMessage by remember { mutableStateOf<ChatMessage?>(null) }
                Box {
                    if (showSheet) {
                        ChatMessageActionSheet(
                            message = message,
                            onDismissRequest = { showSheet = false },
                            onSelectAndCopy = {},
                            onOpenPreview = null,
                            onOpenImagePreview = null,
                            onSaveImageToGallery = null,
                            onOpenSearchResults = null,
                            isRemembered = false,
                            onTranslateMessage = null,
                            onToggleMemoryMessage = null,
                            onExportMarkdown = {},
                            onShareMessage = {},
                            onEditUserMessage = null,
                            onRegenerate = null,
                            onDeleteMessage = {
                                deleteRequestCount += 1
                                pendingMessage = message
                            },
                        )
                    } else if (pendingMessage == null) {
                        Button(onClick = { showSheet = true }) {
                            Text("打开消息菜单")
                        }
                    }
                    ChatMessageDeleteConfirmationDialog(
                        message = pendingMessage,
                        messageDeletionEnabled = true,
                        onDismiss = { pendingMessage = null },
                        onConfirm = { confirmed ->
                            confirmedMessageIds += confirmed.id
                            pendingMessage = null
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("删除消息", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("删除这条消息？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertEquals(1, deleteRequestCount)
            assertEquals(emptyList<String>(), confirmedMessageIds)
        }

        composeRule.onNodeWithText("打开消息菜单").performClick()
        composeRule.onNodeWithText("删除消息", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle {
            assertEquals(2, deleteRequestCount)
            assertEquals(listOf(message.id), confirmedMessageIds)
        }
    }
}
