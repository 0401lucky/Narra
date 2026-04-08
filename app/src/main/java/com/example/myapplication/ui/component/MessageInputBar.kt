package com.example.myapplication.ui.component

import com.example.myapplication.ui.component.*

import androidx.core.net.toUri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.toMessageAttachmentOrNull

@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    currentModel: String,
    onOpenModelPicker: () -> Unit,
    showReasoningAction: Boolean,
    reasoningLabel: String,
    onOpenReasoningPicker: () -> Unit,
    searchEnabled: Boolean,
    searchAvailable: Boolean,
    onOpenSearchPicker: () -> Unit,
    onSearchUnavailableClick: (() -> Unit)? = null,
    onTranslateInputClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenSpecialPlayClick: () -> Unit,
    onRemovePart: (Int) -> Unit,
    pendingParts: List<ChatMessagePart>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    allowImageAttachments: Boolean = true,
    allowFileAttachments: Boolean = true,
    allowSpecialPlay: Boolean = true,
    isSending: Boolean = false,
    onCancelClick: (() -> Unit)? = null,
) {
    val sendInteractionSource = remember { MutableInteractionSource() }
    val cancelInteractionSource = remember { MutableInteractionSource() }
    val isPressed by sendInteractionSource.collectIsPressedAsState()
    var showAttachmentMenu by rememberSaveable { mutableStateOf(false) }
    val canSend = enabled && (value.isNotBlank() || pendingParts.isNotEmpty())
    val canOpenAttachmentMenu = enabled && !isSending && (
        allowImageAttachments || allowFileAttachments || allowSpecialPlay
    )
    val inputPlaceholder = when {
        pendingParts.any { it.type == ChatMessagePartType.IMAGE } && pendingParts.any { it.type == ChatMessagePartType.FILE } -> "补充说明，让 AI 结合图片和文件回答"
        pendingParts.any { it.type == ChatMessagePartType.IMAGE } -> "补充说明，让 AI 结合图片回答"
        pendingParts.any { it.type == ChatMessagePartType.FILE } -> "输入你的要求，让 AI 结合文件内容回答"
        else -> "输入消息，与 AI 聊天"
    }
    var showExpandedEditor by rememberSaveable { mutableStateOf(false) }
    var allowNextInlineNewline by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && (canSend || isSending)) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "send_button_scale",
    )
    val handleInputChange: (String) -> Unit = { nextValue ->
        val insertedTrailingNewline = nextValue.length == value.length + 1 &&
            nextValue.endsWith('\n') &&
            nextValue.dropLast(1) == value
        if (insertedTrailingNewline) {
            if (allowNextInlineNewline) {
                allowNextInlineNewline = false
                onValueChange(nextValue)
            } else {
                val trimmedValue = nextValue.dropLast(1)
                val canSendWithNewValue = enabled && (trimmedValue.isNotBlank() || pendingParts.isNotEmpty())
                onValueChange(trimmedValue)
                if (canSendWithNewValue) {
                    onSendClick()
                }
            }
        } else {
            allowNextInlineNewline = false
            onValueChange(nextValue)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(30.dp),
        color = if (enabled || isSending) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (enabled || isSending) 0.42f else 0.24f,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (pendingParts.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(pendingParts) { index, part ->
                                val attachment = part.toMessageAttachmentOrNull()
                                if (part.type == ChatMessagePartType.IMAGE && attachment != null) {
                                    PendingImageThumbnail(
                                        attachment = attachment,
                                        enabled = !isSending,
                                        onRemove = { onRemovePart(index) },
                                    )
                                } else {
                                    PendingAttachmentBanner(
                                        part = part,
                                        enabled = !isSending,
                                        onRemovePart = { onRemovePart(index) },
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = handleInputChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Enter &&
                                        !event.isShiftPressed
                                    ) {
                                        if (canSend) {
                                            onSendClick()
                                            true
                                        } else {
                                            false
                                        }
                                    } else if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Enter &&
                                        event.isShiftPressed
                                    ) {
                                        allowNextInlineNewline = true
                                        false
                                    } else {
                                        false
                                    }
                                },
                            enabled = enabled && !isSending,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (enabled || isSending) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                },
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (canSend) {
                                        onSendClick()
                                    }
                                },
                            ),
                            decorationBox = { innerTextField ->
                                if (value.isEmpty()) {
                                    Text(
                                        text = inputPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                innerTextField()
                            },
                        )

                        NarraIconButton(
                            onClick = { showExpandedEditor = true },
                            enabled = enabled && !isSending,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "展开输入编辑",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelSelectorButton(
                        currentModel = currentModel,
                        enabled = !isSending,
                        onClick = onOpenModelPicker,
                    )

                    if (showReasoningAction) {
                        ReasoningActionChip(
                            label = reasoningLabel,
                            enabled = !isSending,
                            onClick = onOpenReasoningPicker,
                        )
                    }

                    SearchActionChip(
                        enabled = enabled && !isSending,
                        available = searchAvailable,
                        selected = searchEnabled,
                        onClick = onOpenSearchPicker,
                    )

                    TranslationActionChip(
                        enabled = !isSending && value.isNotBlank(),
                        onClick = onTranslateInputClick,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        NarraIconButton(
                            onClick = { showAttachmentMenu = true },
                            enabled = canOpenAttachmentMenu,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加附件",
                            )
                        }

                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("选择图片") },
                                enabled = allowImageAttachments,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    onPickImageClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("上传文件") },
                                enabled = allowFileAttachments,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    onPickFileClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("特殊玩法") },
                                enabled = allowSpecialPlay,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showAttachmentMenu = false
                                    onOpenSpecialPlayClick()
                                },
                            )
                        }
                    }

                    if (isSending && onCancelClick != null) {
                        NarraIconButton(
                            onClick = onCancelClick,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            interactionSource = cancelInteractionSource,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    } else {
                        NarraIconButton(
                            onClick = onSendClick,
                            enabled = canSend,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                contentColor = if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            ),
                            interactionSource = sendInteractionSource,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    ExpandedDraftEditorDialog(
        visible = showExpandedEditor,
        value = value,
        placeholder = inputPlaceholder,
        onSave = onValueChange,
        onDismissRequest = { showExpandedEditor = false },
    )
}

@Composable
private fun SearchActionChip(
    enabled: Boolean,
    available: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        available -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        available -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        available -> MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (selected) "关闭搜索" else "开启搜索",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PendingAttachmentBanner(
    part: ChatMessagePart,
    enabled: Boolean,
    onRemovePart: () -> Unit,
) {
    val attachment = part.toMessageAttachmentOrNull()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = when (part.type) {
                    ChatMessagePartType.IMAGE -> Icons.Default.Image
                    ChatMessagePartType.FILE -> Icons.Default.Description
                    ChatMessagePartType.SPECIAL -> Icons.Default.Share
                    ChatMessagePartType.TEXT -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = when (part.type) {
                        ChatMessagePartType.IMAGE -> attachment?.fileName?.ifBlank { "已选择图片" } ?: "已选择图片"
                        ChatMessagePartType.FILE -> attachment?.fileName?.ifBlank { "已选择文件" } ?: "已选择文件"
                        ChatMessagePartType.SPECIAL -> part.specialType?.displayName?.let { "已恢复${it}卡片" } ?: "已恢复特殊玩法"
                        ChatMessagePartType.TEXT -> "待发送文本"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when (part.type) {
                        ChatMessagePartType.IMAGE -> "发送时作为图片附件上传"
                        ChatMessagePartType.FILE -> "发送时会提取文本内容作为上下文"
                        ChatMessagePartType.SPECIAL -> "发送时会按原消息结构重新发出"
                        ChatMessagePartType.TEXT -> "发送时会作为普通文本发出"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                )
            }
            NarraIconButton(
                onClick = onRemovePart,
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除附件",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorButton(
    currentModel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (currentModel.isNotBlank()) {
                ModelIcon(
                    modelName = currentModel,
                    size = 20.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ReasoningActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val chipColor = when {
        label.contains("深") || label.contains("Deep") -> MaterialTheme.colorScheme.primaryContainer
        label.contains("标准") || label.contains("Standard") -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    val contentColor = when {
        label.contains("深") || label.contains("Deep") -> MaterialTheme.colorScheme.onPrimaryContainer
        label.contains("标准") || label.contains("Standard") -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = chipColor.copy(alpha = 0.82f),
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TranslationActionChip(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "翻译输入",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PendingImageThumbnail(
    attachment: MessageAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        coil3.compose.AsyncImage(
            model = attachment.uri.toUri(),
            contentDescription = attachment.fileName.ifBlank { "已选择图片" },
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        NarraIconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除图片",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
