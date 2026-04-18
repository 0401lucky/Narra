package com.example.myapplication.ui.component.roleplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.example.myapplication.R
import com.example.myapplication.ui.component.ExpandedDraftEditorDialog
import com.example.myapplication.ui.component.NarraIconButton

@Composable
internal fun RoleplayInputBar(
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (() -> Unit)?,
    onOpenSpecialPlay: () -> Unit,
    quickActions: List<RoleplayInputQuickAction> = emptyList(),
    showActionButton: Boolean = true,
    showExpandButton: Boolean = true,
) {
    val canSend = input.isNotBlank() && !isSending
    val hasQuickActions = quickActions.isNotEmpty()
    var showActionMenu by remember { mutableStateOf(false) }
    var showActionPanel by remember { mutableStateOf(false) }
    var showExpandedEditor by rememberSaveable { mutableStateOf(false) }
    var allowNextInlineNewline by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    LaunchedEffect(inputFocusToken) {
        if (inputFocusToken > 0L && !isSending) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val handleInputChange: (String) -> Unit = { nextValue ->
        val insertedTrailingNewline = nextValue.length == input.length + 1 &&
            nextValue.endsWith('\n') &&
            nextValue.dropLast(1) == input
        if (insertedTrailingNewline) {
            if (allowNextInlineNewline) {
                allowNextInlineNewline = false
                onInputChange(nextValue)
            } else {
                val trimmedValue = nextValue.dropLast(1)
                val canSendWithNewValue = trimmedValue.isNotBlank() && !isSending
                onInputChange(trimmedValue)
                if (canSendWithNewValue) {
                    onSend()
                }
            }
        } else {
            allowNextInlineNewline = false
            onInputChange(nextValue)
        }
    }

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        overlayColor = colors.panelBackgroundStrong,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 6.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showActionButton && hasQuickActions) {
                AnimatedVisibility(
                    visible = showActionPanel,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    RoleplayQuickActionPanel(
                        actions = quickActions,
                        textColor = colors.textPrimary,
                        mutedTextColor = colors.textMuted,
                        onActionClick = { action ->
                            showActionPanel = false
                            action.onClick()
                        },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showActionButton) {
                    Box {
                        NarraIconButton(
                            onClick = {
                                if (hasQuickActions) {
                                    showActionPanel = !showActionPanel
                                } else {
                                    showActionMenu = true
                                }
                            },
                            enabled = !isSending,
                            modifier = Modifier
                                .size(RoleplayInteractiveIconButtonSize)
                                .background(
                                    color = if (hasQuickActions && showActionPanel) {
                                        colors.characterAccent.copy(alpha = 0.2f)
                                    } else {
                                        colors.panelBackground
                                    },
                                    shape = CircleShape,
                                ),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (hasQuickActions && showActionPanel) {
                                    colors.characterAccent
                                } else {
                                    colors.textPrimary
                                },
                            ),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.roleplay_more_actions),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (!hasQuickActions) {
                            DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.roleplay_special_play)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = { showActionMenu = false; onOpenSpecialPlay() },
                                )
                            }
                        }
                    }
                }
                BasicTextField(
                    value = input,
                    onValueChange = handleInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .padding(vertical = 8.dp)
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                if (canSend) {
                                    showActionPanel = false
                                    onSend()
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
                    enabled = !isSending,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.characterAccent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                showActionPanel = false
                                onSend()
                            }
                        },
                    ),
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            Text(
                                stringResource(id = R.string.roleplay_input_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textMuted.copy(alpha = 0.56f),
                            )
                        }
                        innerTextField()
                    },
                )
                if (showExpandButton) {
                    NarraIconButton(
                        onClick = {
                            showActionPanel = false
                            showExpandedEditor = true
                        },
                        enabled = !isSending,
                        modifier = Modifier.size(RoleplayInteractiveIconButtonSize),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colors.panelBackground,
                            contentColor = colors.textPrimary,
                            disabledContainerColor = colors.panelBackground.copy(alpha = 0.45f),
                            disabledContentColor = colors.textMuted.copy(alpha = 0.55f),
                        ),
                    ) {
                        Icon(
                            Icons.Default.OpenInFull,
                            contentDescription = stringResource(id = R.string.roleplay_expand_editor),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (isSending && onCancel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.characterAccent)
                        NarraIconButton(
                            onClick = {
                                showActionPanel = false
                                onCancel()
                            },
                            modifier = Modifier.size(RoleplayInteractiveIconButtonSize),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.common_cancel),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else {
                    NarraIconButton(
                        onClick = {
                            showActionPanel = false
                            view.performHapticFeedback(HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                            onSend()
                        },
                        enabled = canSend,
                        modifier = Modifier.size(RoleplayInteractiveIconButtonSize),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (canSend) colors.characterAccent.copy(alpha = 0.88f) else colors.panelBackground,
                            contentColor = if (canSend) Color.Black.copy(alpha = 0.88f) else colors.textMuted.copy(alpha = 0.55f),
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(id = R.string.common_send),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    ExpandedDraftEditorDialog(
        visible = showExpandedEditor,
        value = input,
        placeholder = stringResource(id = R.string.roleplay_input_placeholder),
        onSave = onInputChange,
        onDismissRequest = { showExpandedEditor = false },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        accentColor = colors.characterAccent,
    )
}

@Composable
private fun RoleplayQuickActionPanel(
    actions: List<RoleplayInputQuickAction>,
    textColor: Color,
    mutedTextColor: Color,
    onActionClick: (RoleplayInputQuickAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.roleplay_quick_actions_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            actions.chunked(4).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            color = action.accentColor.copy(alpha = 0.12f),
                            tonalElevation = 0.dp,
                            onClick = { onActionClick(action) },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            color = action.accentColor,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = mutedTextColor.copy(alpha = 0.92f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(4 - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
