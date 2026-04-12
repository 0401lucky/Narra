package com.example.myapplication.ui.component.roleplay

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.model.actionMetadataValue
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.ExpandedDraftEditorDialog
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.component.copyPlainTextToClipboard

internal data class ImmersiveRoleplayColors(
    val textPrimary: Color,
    val textMuted: Color,
    val characterAccent: Color,
    val userAccent: Color,
    val thoughtText: Color,
    val panelBackground: Color,
    val panelBackgroundStrong: Color,
    val panelBorder: Color,
    val errorText: Color,
    val errorBackground: Color,
    val errorBackgroundStrong: Color,
)

internal enum class RoleplayMessageBubbleMode {
    DEFAULT,
    ONLINE_PHONE,
}

@Composable
internal fun rememberImmersiveRoleplayColors(
    backdropState: ImmersiveBackdropState,
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val errorText = Color(0xFFFFB4AB)
    return remember(palette, errorText) {
        ImmersiveRoleplayColors(
            textPrimary = palette.onGlass,
            textMuted = palette.onGlassMuted,
            characterAccent = palette.characterAccent,
            userAccent = palette.userAccent,
            thoughtText = palette.thoughtText,
            panelBackground = palette.panelTint.copy(alpha = 0.24f),
            panelBackgroundStrong = palette.panelTintStrong.copy(alpha = 0.34f),
            panelBorder = palette.panelBorder.copy(alpha = 0.28f),
            errorText = errorText,
            errorBackground = errorText.copy(alpha = 0.12f),
            errorBackgroundStrong = errorText.copy(alpha = 0.18f),
        )
    }
}

@Composable
internal fun RoleplaySuggestionSection(
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    suggestions: List<RoleplaySuggestionUiModel>,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    isSending: Boolean,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
) {
    val view = LocalView.current
    val showPanel = suggestions.isNotEmpty() || isGeneratingSuggestions || !suggestionErrorMessage.isNullOrBlank()
    if (!showPanel) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            overlayColor = colors.panelBackground,
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(
                        enabled = !isSending,
                        onClick = onGenerateSuggestions,
                        onLongClick = {},
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Text(
                stringResource(id = R.string.roleplay_ai_helper_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isSending) {
                    stringResource(id = R.string.roleplay_sending)
                } else {
                    stringResource(id = R.string.roleplay_generate)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isSending) colors.textMuted else colors.characterAccent,
            )
        }
        }
        return
    }

    val suggestionListState = rememberLazyListState()
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        overlayColor = colors.panelBackgroundStrong,
    ) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = R.string.roleplay_ai_helper_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarraTextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                    onGenerateSuggestions()
                }, enabled = !isSending && !isGeneratingSuggestions) {
                    Text(
                        text = if (suggestions.isEmpty()) {
                            stringResource(id = R.string.common_retry)
                        } else {
                            stringResource(id = R.string.roleplay_refresh_suggestions)
                        },
                        color = colors.characterAccent,
                    )
                }
                NarraTextButton(onClick = onClearSuggestions) {
                    Text(
                        text = stringResource(id = R.string.common_collapse),
                        color = colors.textMuted,
                    )
                }
            }
        }
        if (isGeneratingSuggestions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.characterAccent)
                Text(
                    text = stringResource(id = R.string.roleplay_generating_suggestions),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        if (!suggestionErrorMessage.isNullOrBlank()) {
            Text(suggestionErrorMessage, style = MaterialTheme.typography.bodySmall, color = colors.errorText)
        }
        if (suggestions.isNotEmpty()) {
            LazyColumn(
                state = suggestionListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(suggestions, key = { it.id }) { suggestion ->
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        overlayColor = colors.panelBackground,
                    ) {
                    Column(
                        modifier = Modifier
                            .combinedClickable(
                                enabled = !isSending,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                                    onApplySuggestion(suggestion.text)
                                },
                                onLongClick = {},
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = suggestion.axis.toReadableLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.characterAccent.copy(alpha = 0.82f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                suggestion.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.characterAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            suggestion.text,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = colors.textPrimary,
                        )
                    }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun EmptyDialogueState(
    colors: ImmersiveRoleplayColors,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "empty_state_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RoleplayEmptyStateGlowDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "empty_glow_pulse",
    )
    val radiusPx = with(density) { RoleplayEmptyStateGlowRadius.toPx() }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 背景光晕效果
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            colors.characterAccent.copy(alpha = glowAlpha),
                            Color.Transparent,
                        ),
                        radius = radiusPx,
                    ),
                ),
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 装饰性分隔线
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(2.dp)
                    .background(
                        colors.characterAccent.copy(alpha = 0.4f),
                        androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    ),
            )

            androidx.compose.material3.Text(
                text = stringResource(id = R.string.roleplay_empty_state_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            androidx.compose.material3.Text(
                text = stringResource(id = R.string.roleplay_empty_state_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted.copy(alpha = 0.75f),
            )

            // 底部装饰线
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(1.dp)
                    .background(
                        colors.textMuted.copy(alpha = 0.25f),
                        androidx.compose.foundation.shape.RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
internal fun FailedTurnHint(
    colors: ImmersiveRoleplayColors,
    modifier: Modifier = Modifier,
) {
    RoleplayEmotionChip(
        text = "本回合生成失败，可长按重回",
        textColor = colors.errorText,
        containerColor = colors.errorBackground,
        modifier = modifier,
        borderColor = colors.errorText.copy(alpha = 0.24f),
    )
}

@Composable
internal fun StreamingLogText(
    content: String,
    textColor: Color,
    accentColor: Color,
    lineHeightScale: Float = 1.0f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "roleplay_log_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RoleplayStreamingCursorPulseMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "roleplay_log_cursor_alpha",
    )
    Text(
        text = buildAnnotatedString {
            append(content)
            withStyle(SpanStyle(color = accentColor.copy(alpha = cursorAlpha), fontWeight = FontWeight.Bold)) {
                append(" ▌")
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 26.sp * lineHeightScale,
            letterSpacing = 0.6.sp,
        ),
        color = textColor,
    )
}

private fun com.example.myapplication.model.RoleplaySuggestionAxis.toReadableLabel(): String {
    return when (this) {
        com.example.myapplication.model.RoleplaySuggestionAxis.PLOT -> "推进"
        com.example.myapplication.model.RoleplaySuggestionAxis.INFO -> "探索"
        com.example.myapplication.model.RoleplaySuggestionAxis.EMOTION -> "情绪"
    }
}

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
    showActionButton: Boolean = true,
    showExpandButton: Boolean = true,
) {
    val canSend = input.isNotBlank() && !isSending
    var showActionMenu by remember { mutableStateOf(false) }
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
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showActionButton) {
            Box {
                NarraIconButton(
                    onClick = { showActionMenu = true },
                    enabled = !isSending,
                    modifier = Modifier
                        .size(RoleplayInteractiveIconButtonSize)
                        .background(
                            color = colors.panelBackground,
                            shape = CircleShape,
                        ),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.roleplay_more_actions),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.roleplay_special_play)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { showActionMenu = false; onOpenSpecialPlay() },
                    )
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
                onClick = { showExpandedEditor = true },
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
                    onClick = onCancel,
                    modifier = Modifier.size(RoleplayInteractiveIconButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = RoleplayErrorActionContainerColor.copy(alpha = 0.7f),
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
internal fun RoleplayMessageItem(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
    onOpenQuotedMessage: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: (String) -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.DEFAULT,
) {
    when (message.contentType) {
        RoleplayContentType.NARRATION -> RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
            if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                OnlinePhoneNarrationBubble(
                    message = message,
                    colors = colors,
                    backdropState = backdropState,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.fillMaxWidth(0.92f),
                        shape = RoundedCornerShape(18.dp),
                        overlayColor = colors.panelBackground,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = buildQuotedDialogueAnnotatedString(
                                        text = paragraph,
                                        narrationColor = colors.textMuted,
                                        dialogueColor = RoleplayQuotedDialogueHighlightColor,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 25.sp * lineHeightScale,
                                        letterSpacing = 0.6.sp,
                                    ),
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        RoleplayContentType.THOUGHT -> RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
            if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                OnlinePhoneThoughtBubble(
                    message = message,
                    colors = colors,
                    backdropState = backdropState,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.fillMaxWidth(0.82f),
                        shape = RoundedCornerShape(18.dp),
                        overlayColor = colors.panelBackground.copy(alpha = 0.18f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = buildQuotedDialogueAnnotatedString(
                                        text = paragraph,
                                        narrationColor = colors.thoughtText,
                                        dialogueColor = RoleplayQuotedDialogueHighlightColor,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 24.sp * lineHeightScale,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = colors.thoughtText,
                                )
                            }
                        }
                    }
                }
            }
        }

        RoleplayContentType.DIALOGUE -> {
            if (message.speaker == RoleplaySpeaker.USER) {
                val isOnlinePhoneBubble = bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE
                if (isOnlinePhoneBubble) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = maxWidth * 0.82f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            UserDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                fillWidth = false,
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        RoleplayMessageMenuWrapper(
                            message,
                            onRetryTurn,
                            onEditUserMessage,
                            Modifier.fillMaxWidth(0.82f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            UserDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                fillWidth = true,
                            )
                        }
                    }
                }
            } else {
                val isError = message.messageStatus == MessageStatus.ERROR
                if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = maxWidth * 0.84f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            CharacterDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                isError = isError,
                                fillWidth = false,
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.fillMaxWidth(0.88f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage
                        ) {
                            CharacterDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                isError = isError,
                                fillWidth = true,
                            )
                        }
                    }
                }
            }
        }

        RoleplayContentType.LONGFORM -> {
            val isError = message.messageStatus == MessageStatus.ERROR
            RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isError) {
                        FailedTurnHint(colors = colors)
                    }
                    RoleplayLongformCard(
                        speakerName = message.speakerName,
                        content = message.content,
                        richTextSource = message.richTextSource,
                        containerColor = if (isError) colors.errorBackground else Color.Transparent,
                        titleColor = if (isError) colors.errorText else colors.characterAccent,
                        bodyColor = if (isError) colors.errorText.copy(alpha = 0.88f) else colors.textPrimary.copy(alpha = 0.82f),
                        accentColor = if (isError) colors.errorText else RoleplayQuotedDialogueHighlightColor,
                        thoughtColor = if (isError) colors.errorText.copy(alpha = 0.76f) else colors.thoughtText,
                        lineHeightScale = lineHeightScale,
                    )
                }
            }
        }

        RoleplayContentType.ACTION -> {
            val actionPart = message.actionPart ?: return
            val isUserMessage = message.speaker == RoleplaySpeaker.USER
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                RoleplayMessageMenuWrapper(
                    message = message,
                    onRetryTurn = onRetryTurn,
                    onEditUserMessage = onEditUserMessage,
                    modifier = if (isUserMessage) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(0.92f),
                    onQuoteMessage = onQuoteMessage,
                    onRecallMessage = onRecallMessage,
                ) {
                    RoleplayActionCard(
                        message = message,
                        actionPart = actionPart,
                        colors = colors,
                        backdropState = backdropState,
                        onOpenVideoCall = if (actionPart.actionType == ChatActionType.VIDEO_CALL && !isUserMessage) {
                            onOpenVideoCall
                        } else {
                            null
                        },
                    )
                }
            }
        }

        RoleplayContentType.SPECIAL_PLAY -> {
            val specialPart = message.specialPart ?: return
            val isUserMessage = message.speaker == RoleplaySpeaker.USER
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                RoleplayMessageMenuWrapper(
                    message = message,
                    onRetryTurn = onRetryTurn,
                    onEditUserMessage = onEditUserMessage,
                    modifier = if (isUserMessage) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(),
                    onQuoteMessage = onQuoteMessage,
                    onRecallMessage = onRecallMessage,
                ) {
                    SpecialPlayCard(
                        part = specialPart,
                        isUserMessage = isUserMessage,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        RoleplayContentType.SYSTEM -> Unit
    }
}

@Composable
private fun RoleplayActionCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onOpenVideoCall: (() -> Unit)?,
) {
    val isUserMessage = message.speaker == RoleplaySpeaker.USER
    val title = when (actionPart.actionType) {
        ChatActionType.EMOJI -> "表情"
        ChatActionType.VOICE_MESSAGE -> "语音消息"
        ChatActionType.AI_PHOTO -> "照片"
        ChatActionType.LOCATION -> "位置"
        ChatActionType.POKE -> "互动"
        ChatActionType.VIDEO_CALL -> "视频通话"
        null -> "消息"
    }
    val body = when (actionPart.actionType) {
        ChatActionType.EMOJI -> actionPart.actionMetadataValue("description")
        ChatActionType.VOICE_MESSAGE -> actionPart.actionMetadataValue("content")
        ChatActionType.AI_PHOTO -> actionPart.actionMetadataValue("description")
        ChatActionType.LOCATION -> buildString {
            append(actionPart.actionMetadataValue("location_name"))
            actionPart.actionMetadataValue("address").takeIf { it.isNotBlank() }?.let { address ->
                append("\n")
                append(address)
            }
            actionPart.actionMetadataValue("coordinates").takeIf { it.isNotBlank() }?.let { coordinates ->
                append("\n")
                append(coordinates)
            }
        }
        ChatActionType.POKE -> if (isUserMessage) "你戳了戳对方" else "戳了戳你"
        ChatActionType.VIDEO_CALL -> actionPart.actionMetadataValue("reason")
        null -> message.copyText
    }.trim().ifBlank { message.copyText }
    val cardModifier = if (actionPart.actionType == ChatActionType.VIDEO_CALL && onOpenVideoCall != null) {
        Modifier.clickable(onClick = onOpenVideoCall)
    } else {
        Modifier
    }
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier
            .fillMaxWidth()
            .then(cardModifier),
        shape = RoundedCornerShape(22.dp),
        overlayColor = if (isUserMessage) colors.panelBackgroundStrong else colors.panelBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isUserMessage) colors.userAccent else colors.characterAccent,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            if (actionPart.actionType == ChatActionType.VIDEO_CALL && onOpenVideoCall != null) {
                Text(
                    text = "点这里接通",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun UserDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    fillWidth: Boolean,
) {
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 6.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp,
    )
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        overlayColor = colors.panelBackgroundStrong,
    ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.replyToPreview.isNotBlank()) {
            RoleplayReplyPreview(
                message = message,
                colors = colors,
                onOpenQuotedMessage = onOpenQuotedMessage,
            )
        }
        Text(
            message.speakerName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.userAccent,
        )
        if (message.isStreaming) {
            StreamingLogText(
                content = message.content,
                textColor = colors.textPrimary,
                accentColor = colors.userAccent,
                lineHeightScale = lineHeightScale,
            )
        } else {
            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp * lineHeightScale,
                            letterSpacing = 0.6.sp,
                        ),
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CharacterDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    isError: Boolean,
    fillWidth: Boolean,
) {
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        overlayColor = if (isError) colors.errorBackground else colors.panelBackgroundStrong,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (message.replyToPreview.isNotBlank()) {
                RoleplayReplyPreview(
                    message = message,
                    colors = colors,
                    onOpenQuotedMessage = onOpenQuotedMessage,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    message.speakerName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) colors.errorText else colors.characterAccent,
                )
                if (message.emotion.isNotBlank()) {
                    RoleplayEmotionChip(
                        text = message.emotion,
                        textColor = if (isError) colors.errorText else colors.characterAccent,
                        containerColor = if (isError) colors.errorBackgroundStrong else colors.panelBackground,
                    )
                }
            }
            if (isError) {
                FailedTurnHint(colors = colors)
            }
            if (message.isStreaming) {
                StreamingLogText(
                    content = message.content,
                    textColor = colors.textPrimary,
                    accentColor = colors.characterAccent,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paragraphs.forEach { paragraph ->
                        val rendered = remember(paragraph, colors, isError) {
                            buildCharacterDialogueAnnotatedString(
                                text = paragraph,
                                narrationColor = if (isError) colors.errorText.copy(alpha = 0.84f) else colors.textPrimary.copy(alpha = 0.78f),
                                dialogueColor = if (isError) colors.errorText else RoleplayQuotedDialogueHighlightColor,
                            )
                        }
                        Text(
                            text = rendered,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 26.sp * lineHeightScale,
                                letterSpacing = 0.6.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlinePhoneNarrationBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(0.96f),
        shape = shape,
        blurRadius = 18.dp,
        overlayColor = colors.panelBackground.copy(alpha = 0.42f),
    ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.textMuted.copy(alpha = 0.92f),
                            dialogueColor = RoleplayQuotedDialogueHighlightColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp * lineHeightScale,
                            letterSpacing = 0.5.sp,
                        ),
                        color = colors.textMuted.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlinePhoneThoughtBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.widthIn(max = maxWidth * 0.76f),
            shape = RoundedCornerShape(18.dp),
            blurRadius = 16.dp,
            overlayColor = colors.panelBackground.copy(alpha = 0.24f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.thoughtText,
                            dialogueColor = RoleplayQuotedDialogueHighlightColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 22.sp * lineHeightScale,
                            letterSpacing = 0.4.sp,
                        ),
                        color = colors.thoughtText,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoleplayMessageMenuWrapper(
    message: RoleplayMessageUiModel,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    val canCopy = message.copyText.isNotBlank()
    val canEditUserMessage = message.sourceMessageId.isNotBlank() &&
        message.speaker == RoleplaySpeaker.USER &&
        message.contentType == RoleplayContentType.DIALOGUE &&
        message.content.isNotBlank()
    val canQuoteMessage = onQuoteMessage != null &&
        message.sourceMessageId.isNotBlank() &&
        message.contentType in setOf(
            RoleplayContentType.DIALOGUE,
            RoleplayContentType.NARRATION,
            RoleplayContentType.THOUGHT,
            RoleplayContentType.LONGFORM,
        )
    val canRecallMessage = onRecallMessage != null &&
        message.sourceMessageId.isNotBlank() &&
        message.speaker == RoleplaySpeaker.USER &&
        message.contentType == RoleplayContentType.DIALOGUE &&
        !message.isRecalled
    var showMenu by remember(message.sourceMessageId, message.copyText, message.canRetry, canEditUserMessage) {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier.combinedClickable(
            enabled = canCopy || message.canRetry || canEditUserMessage || canQuoteMessage || canRecallMessage,
            onClick = {},
            onLongClick = { showMenu = true },
        ),
    ) {
        content()
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { Text("复制内容") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboardScope.copyPlainTextToClipboard(clipboard, "roleplay-message", message.copyText)
                        Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    },
                )
            }
            if (canEditUserMessage) {
                DropdownMenuItem(
                    text = { Text("重回并编辑") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        onEditUserMessage(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
            if (canQuoteMessage) {
                DropdownMenuItem(
                    text = { Text("引用") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        onQuoteMessage?.invoke(
                            message.sourceMessageId,
                            message.speakerName,
                            message.copyText.lineSequence().firstOrNull().orEmpty().take(60),
                        )
                        showMenu = false
                    },
                )
            }
            if (canRecallMessage) {
                DropdownMenuItem(
                    text = { Text("撤回") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        onRecallMessage?.invoke(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
            if (message.canRetry && message.sourceMessageId.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("重回此回合") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        onRetryTurn(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RoleplayReplyPreview(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    onOpenQuotedMessage: ((String) -> Unit)?,
) {
    Surface(
        modifier = if (message.replyToMessageId.isNotBlank() && onOpenQuotedMessage != null) {
            Modifier.clickable { onOpenQuotedMessage(message.replyToMessageId) }
        } else {
            Modifier
        },
        shape = RoundedCornerShape(14.dp),
        color = colors.panelBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message.replyToSpeakerName.ifBlank { "引用消息" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message.replyToPreview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
