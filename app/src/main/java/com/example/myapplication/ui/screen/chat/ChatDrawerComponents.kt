package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.ui.component.NarraTextButton
import java.util.Calendar

private sealed interface DrawerDestructiveAction {
    data class ClearCurrentConversation(val title: String) : DrawerDestructiveAction
    data class ClearConversation(val conversationId: String, val title: String) : DrawerDestructiveAction
    data class DeleteConversation(val conversationId: String, val title: String) : DrawerDestructiveAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationDrawerContent(
    conversations: List<Conversation>,
    currentConversationId: String,
    currentProviderName: String,
    currentModel: String,
    userDisplayName: String,
    userAvatarUri: String,
    userAvatarUrl: String,
    currentAssistant: Assistant?,
    assistants: List<Assistant>,
    onSelectAssistant: (String) -> Unit,
    onOpenAssistantDetail: (String) -> Unit,
    onCreateConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onClearConversation: (String) -> Unit,
    onClearCurrentConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenRoleplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDestructiveAction by remember { mutableStateOf<DrawerDestructiveAction?>(null) }
    val drawerPalette = rememberChatDrawerPalette()
    val unnamedConversationTitle = stringResource(R.string.drawer_unnamed)
    val greeting = remember {
        resolveDrawerGreeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    val conversationSections = remember(conversations, searchQuery) {
        buildDrawerConversationSections(
            conversations = conversations,
            searchQuery = searchQuery,
        )
    }
    val currentConversationTitle = remember(conversations, currentConversationId, unnamedConversationTitle) {
        conversations.firstOrNull { it.id == currentConversationId }
            ?.title
            ?.ifBlank { unnamedConversationTitle }
            ?: unnamedConversationTitle
    }

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = 352.dp)
            .fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = drawerPalette.background,
        drawerContentColor = drawerPalette.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DrawerProfileHeader(
                userDisplayName = userDisplayName,
                userAvatarUri = userAvatarUri,
                userAvatarUrl = userAvatarUrl,
                currentModel = currentModel,
                greeting = greeting,
                accentContainerColor = drawerPalette.accentContainer,
                accentContentColor = drawerPalette.accentContent,
                primaryTextColor = drawerPalette.onSurface,
                secondaryTextColor = drawerPalette.onSurfaceVariant,
                onClick = onEditProfile,
            )

            DrawerSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                containerColor = drawerPalette.card,
                textColor = drawerPalette.onSurface,
                hintColor = drawerPalette.onSurfaceVariant,
            )

            DrawerInfoRow(
                icon = Icons.Default.Description,
                title = stringResource(R.string.drawer_chat_history),
                trailing = {
                    StatusPill(
                        text = "${conversations.size}",
                        containerColor = drawerPalette.accentContainer,
                        contentColor = drawerPalette.accentContent,
                    )
                },
                containerColor = drawerPalette.card,
                contentColor = drawerPalette.onSurface,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateConversation),
                shape = RoundedCornerShape(22.dp),
                color = drawerPalette.selectedCard,
                contentColor = drawerPalette.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.drawer_new_message),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = drawerPalette.onSurface,
                )
            }

            if (conversationSections.isEmpty()) {
                NoticeCard(
                    title = if (searchQuery.isBlank()) stringResource(R.string.drawer_no_history_title) else stringResource(R.string.drawer_no_search_result_title),
                    body = if (searchQuery.isBlank()) stringResource(R.string.drawer_no_history_body) else stringResource(R.string.drawer_no_search_result_body),
                    containerColor = drawerPalette.card,
                    contentColor = drawerPalette.onSurface,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    conversationSections.forEach { section ->
                        item(key = "section-${section.title}") {
                            Text(
                                text = section.title,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = drawerPalette.sectionLabel,
                            )
                        }
                        items(section.conversations, key = { it.id }) { conversation ->
                            DrawerConversationItem(
                                conversation = conversation,
                                isCurrent = conversation.id == currentConversationId,
                                onClick = { onSelectConversation(conversation.id) },
                                onClearRequest = {
                                    pendingDestructiveAction = DrawerDestructiveAction.ClearConversation(
                                        conversationId = conversation.id,
                                        title = conversation.title.ifBlank { unnamedConversationTitle },
                                    )
                                },
                                onDeleteRequest = {
                                    pendingDestructiveAction = DrawerDestructiveAction.DeleteConversation(
                                        conversationId = conversation.id,
                                        title = conversation.title.ifBlank { unnamedConversationTitle },
                                    )
                                },
                                cardColor = drawerPalette.background,
                                selectedColor = drawerPalette.selectedCard,
                                primaryTextColor = drawerPalette.onSurface,
                                secondaryTextColor = drawerPalette.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            AssistantDrawerBar(
                currentAssistant = currentAssistant,
                assistants = assistants,
                drawerPalette = drawerPalette,
                onSelectAssistant = onSelectAssistant,
                onOpenAssistantDetail = onOpenAssistantDetail,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerFooterAction(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.common_new_conversation),
                    onClick = onCreateConversation,
                    containerColor = drawerPalette.footerButton,
                    contentColor = drawerPalette.accentContent,
                )
                DrawerFooterAction(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.drawer_clear_conversation),
                    onClick = {
                        pendingDestructiveAction = DrawerDestructiveAction.ClearCurrentConversation(
                            title = currentConversationTitle,
                        )
                    },
                    containerColor = drawerPalette.footerButton,
                    contentColor = drawerPalette.accentContent,
                )
                DrawerFooterAction(
                    icon = Icons.Default.Translate,
                    contentDescription = stringResource(R.string.drawer_open_translator),
                    onClick = onOpenTranslator,
                    containerColor = drawerPalette.footerButton,
                    contentColor = drawerPalette.accentContent,
                )
                DrawerFooterAction(
                    icon = Icons.Default.AutoStories,
                    contentDescription = stringResource(R.string.drawer_immersive_roleplay),
                    onClick = onOpenRoleplay,
                    containerColor = drawerPalette.footerButton,
                    contentColor = drawerPalette.accentContent,
                )
                DrawerFooterAction(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.drawer_open_settings),
                    onClick = onOpenSettings,
                    containerColor = drawerPalette.accentContainer,
                    contentColor = drawerPalette.accentContent,
                )
            }
        }

        pendingDestructiveAction?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingDestructiveAction = null },
                title = {
                    Text(
                        text = when (action) {
                            is DrawerDestructiveAction.DeleteConversation -> stringResource(R.string.drawer_confirm_delete_title)
                            is DrawerDestructiveAction.ClearConversation,
                            is DrawerDestructiveAction.ClearCurrentConversation,
                            -> stringResource(R.string.drawer_confirm_clear_title)
                        },
                    )
                },
                text = {
                    Text(
                        text = when (action) {
                            is DrawerDestructiveAction.DeleteConversation -> {
                                stringResource(R.string.drawer_confirm_delete_message, action.title)
                            }

                            is DrawerDestructiveAction.ClearConversation -> {
                                stringResource(R.string.drawer_confirm_clear_message, action.title)
                            }

                            is DrawerDestructiveAction.ClearCurrentConversation -> {
                                stringResource(R.string.drawer_confirm_clear_message, action.title)
                            }
                        },
                    )
                },
                confirmButton = {
                    NarraTextButton(
                        onClick = {
                            when (action) {
                                is DrawerDestructiveAction.DeleteConversation -> {
                                    onDeleteConversation(action.conversationId)
                                }

                                is DrawerDestructiveAction.ClearConversation -> {
                                    onClearConversation(action.conversationId)
                                }

                                is DrawerDestructiveAction.ClearCurrentConversation -> {
                                    onClearCurrentConversation()
                                }
                            }
                            pendingDestructiveAction = null
                        },
                    ) {
                        Text(stringResource(R.string.common_confirm))
                    }
                },
                dismissButton = {
                    NarraTextButton(onClick = { pendingDestructiveAction = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}
