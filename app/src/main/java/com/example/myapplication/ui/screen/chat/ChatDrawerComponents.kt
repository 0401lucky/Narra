package com.example.myapplication.ui.screen.chat

import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.NarraTextButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

private data class ChatDrawerPalette(
    val background: Color,
    val card: Color,
    val selectedCard: Color,
    val accentContainer: Color,
    val accentContent: Color,
    val footerButton: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val sectionLabel: Color,
)

private data class DrawerConversationSection(
    val title: String,
    val conversations: List<Conversation>,
)

@Composable
private fun rememberChatDrawerPalette(): ChatDrawerPalette {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        ChatDrawerPalette(
            background = colorScheme.background,
            card = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedCard = colorScheme.primaryContainer.copy(alpha = 0.8f),
            accentContainer = colorScheme.secondaryContainer,
            accentContent = colorScheme.onSecondaryContainer,
            footerButton = colorScheme.surfaceVariant.copy(alpha = 0.8f),
            onSurface = colorScheme.onSurface,
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            sectionLabel = colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantDrawerBar(
    currentAssistant: Assistant?,
    assistants: List<Assistant>,
    drawerPalette: ChatDrawerPalette,
    onSelectAssistant: (String) -> Unit,
    onOpenAssistantDetail: (String) -> Unit,
) {
    var showAssistantPicker by rememberSaveable { mutableStateOf(false) }
    val assistantName = currentAssistant?.name?.ifBlank { null } ?: stringResource(R.string.drawer_default_assistant)
    val assistantFirstChar = assistantName.firstOrNull()?.toString() ?: stringResource(R.string.drawer_default_assistant_char)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = drawerPalette.card,
        contentColor = drawerPalette.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：助手图标 + 名称，点击切换助手
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showAssistantPicker = true }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AssistantAvatar(
                    name = currentAssistant?.name ?: "",
                    iconName = currentAssistant?.iconName ?: "smart_toy",
                    avatarUri = currentAssistant?.avatarUri ?: "",
                    size = 34.dp,
                    containerColor = drawerPalette.accentContainer,
                    contentColor = drawerPalette.accentContent,
                    cornerRadius = 10.dp,
                )
                Text(
                    text = assistantName,
                    style = MaterialTheme.typography.titleSmall,
                    color = drawerPalette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 右侧：圆形头像按钮，点击进入助手设置
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clickable {
                        val id = currentAssistant?.id
                        if (id != null) {
                            onOpenAssistantDetail(id)
                        }
                    },
                shape = CircleShape,
                color = drawerPalette.accentContainer,
                contentColor = drawerPalette.accentContent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = assistantFirstChar,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = drawerPalette.accentContent,
                    )
                }
            }
        }
    }

    if (showAssistantPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssistantPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.drawer_select_assistant),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                assistants.forEach { assistant ->
                    val isSelected = assistant.id == currentAssistant?.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectAssistant(assistant.id)
                                showAssistantPicker = false
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AssistantAvatar(
                                name = assistant.name,
                                iconName = assistant.iconName,
                                avatarUri = assistant.avatarUri,
                                size = 38.dp,
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                cornerRadius = 12.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = assistant.name.ifBlank { stringResource(R.string.drawer_unnamed) },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (assistant.description.isNotBlank()) {
                                    Text(
                                        text = assistant.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerProfileHeader(
    userDisplayName: String,
    userAvatarUri: String,
    userAvatarUrl: String,
    currentModel: String,
    greeting: String,
    accentContainerColor: Color,
    accentContentColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val avatarRequestSize = with(density) {
        val sizePx = 54.dp.roundToPx().coerceAtLeast(1)
        IntSize(sizePx, sizePx)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.example.myapplication.ui.component.UserProfileAvatar(
            displayName = userDisplayName,
            avatarUri = userAvatarUri,
            avatarUrl = userAvatarUrl,
            modifier = Modifier.size(54.dp),
            requestSize = avatarRequestSize,
            containerColor = accentContainerColor,
            contentColor = accentContentColor,
            textStyle = MaterialTheme.typography.titleLarge,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = userDisplayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
            )
            Text(
                text = if (currentModel.isBlank()) {
                    "$greeting · 点击编辑资料"
                } else {
                    "$greeting · $currentModel"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
            )
        }

        Surface(
            shape = CircleShape,
            color = accentContainerColor.copy(alpha = 0.9f),
            contentColor = accentContentColor,
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.drawer_edit_profile),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    containerColor: Color,
    textColor: Color,
    hintColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = textColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = hintColor,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(textColor),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.drawer_search_chat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = hintColor,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun DrawerInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun DrawerConversationItem(
    conversation: Conversation,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onClearRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    cardColor: Color,
    selectedColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
) {
    var showMenu by rememberSaveable(conversation.id) { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (isCurrent) selectedColor else cardColor,
            contentColor = primaryTextColor,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.drawer_new_conversation) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor,
                )
                Text(
                    text = buildConversationMetaText(conversation),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_clear_conversation_menu)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onClearRequest()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onDeleteRequest()
                },
            )
        }
    }
}

// CurrentModelDrawerCard removed — replaced by AssistantDrawerBar

@Composable
private fun DrawerFooterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

private fun buildDrawerConversationSections(
    conversations: List<Conversation>,
    searchQuery: String,
): List<DrawerConversationSection> {
    val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
    val filteredConversations = conversations
        .sortedByDescending { it.updatedAt }
        .filter { conversation ->
            normalizedQuery.isBlank() ||
                conversation.title.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                conversation.model.lowercase(Locale.getDefault()).contains(normalizedQuery)
        }

    val sections = linkedMapOf<String, MutableList<Conversation>>()
    filteredConversations.forEach { conversation ->
        val sectionTitle = resolveConversationSectionTitle(conversation.updatedAt)
        sections.getOrPut(sectionTitle) { mutableListOf() }.add(conversation)
    }
    return sections.map { (title, items) ->
        DrawerConversationSection(title = title, conversations = items)
    }
}

private fun buildConversationMetaText(conversation: Conversation): String {
    val timeLabel = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(conversation.updatedAt))
    return if (conversation.model.isBlank()) {
        timeLabel
    } else {
        "${conversation.model} · $timeLabel"
    }
}

private fun resolveConversationSectionTitle(timestamp: Long): String {
    if (DateUtils.isToday(timestamp)) {
        return "今天"
    }

    val targetCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (isSameDay(targetCalendar, yesterday)) {
        return "昨天"
    }

    val now = Calendar.getInstance()
    return if (now.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR)) {
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timestamp))
    } else {
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timestamp))
    }
}

private fun isSameDay(
    first: Calendar,
    second: Calendar,
): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun resolveDrawerGreeting(hourOfDay: Int): String {
    return when (hourOfDay) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
}
