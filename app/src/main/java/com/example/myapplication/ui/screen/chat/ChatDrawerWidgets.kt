package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.UserProfileAvatar

internal data class ChatDrawerPalette(
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

@Composable
internal fun rememberChatDrawerPalette(): ChatDrawerPalette {
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
internal fun AssistantDrawerBar(
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
internal fun DrawerProfileHeader(
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
        UserProfileAvatar(
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
internal fun DrawerSearchBar(
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
internal fun DrawerInfoRow(
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
internal fun DrawerConversationItem(
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

@Composable
internal fun DrawerFooterAction(
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
