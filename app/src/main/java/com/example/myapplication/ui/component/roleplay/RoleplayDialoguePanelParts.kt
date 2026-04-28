package com.example.myapplication.ui.component.roleplay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.example.myapplication.R
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.NarraTextButton

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
    val userBubbleBackground: Color,
    val characterBubbleBackground: Color,
    val narrationBubbleBackground: Color,
)

internal enum class RoleplayMessageBubbleMode {
    DEFAULT,
    ONLINE_PHONE,
}

internal data class RoleplayInputQuickAction(
    val label: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit,
)

@Composable
internal fun rememberImmersiveRoleplayColors(
    backdropState: ImmersiveBackdropState,
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val errorText = Color(0xFFFFB4AB)
    return remember(palette, errorText, backdropState.hasImage) {
        if (!backdropState.hasImage) {
            return@remember ImmersiveRoleplayColors(
                textPrimary = Color(0xFF243044),
                textMuted = Color(0xFF627086),
                characterAccent = Color(0xFF426F96),
                userAccent = Color(0xFF6E7C63),
                thoughtText = Color(0xFF586A79).copy(alpha = 0.9f),
                panelBackground = Color(0xFFF7FAFC).copy(alpha = 0.92f),
                panelBackgroundStrong = Color.White.copy(alpha = 0.97f),
                panelBorder = Color(0xFF5D7286).copy(alpha = 0.18f),
                errorText = Color(0xFFB3261E),
                errorBackground = Color(0xFFFFEDEA).copy(alpha = 0.94f),
                errorBackgroundStrong = Color(0xFFFFDAD6).copy(alpha = 0.96f),
                userBubbleBackground = Color(0xFFE4EEF7).copy(alpha = 0.98f),
                characterBubbleBackground = Color(0xFFFFF7E8).copy(alpha = 0.98f),
                narrationBubbleBackground = Color.White.copy(alpha = 0.94f),
            )
        }
        ImmersiveRoleplayColors(
            textPrimary = palette.onGlass,
            textMuted = palette.onGlassMuted,
            characterAccent = palette.characterAccent,
            userAccent = palette.userAccent,
            thoughtText = palette.thoughtText,
            panelBackground = Color(0xFFF2EEE8).copy(alpha = if (backdropState.hasImage) 0.22f else 0.92f),
            panelBackgroundStrong = Color(0xFFF5F1EB).copy(alpha = if (backdropState.hasImage) 0.28f else 0.96f),
            panelBorder = palette.panelBorder.copy(alpha = 0.22f),
            errorText = errorText,
            errorBackground = errorText.copy(alpha = 0.18f),
            errorBackgroundStrong = errorText.copy(alpha = 0.26f),
            userBubbleBackground = Color(0xFFE6DDD0).copy(alpha = if (backdropState.hasImage) 0.26f else 0.96f),
            characterBubbleBackground = Color(0xFFF1ECE5).copy(alpha = if (backdropState.hasImage) 0.24f else 0.96f),
            narrationBubbleBackground = Color(0xFFF4F0EA).copy(alpha = if (backdropState.hasImage) 0.2f else 0.94f),
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
            blurRadius = 18.dp,
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
        blurRadius = 18.dp,
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
        text = "\u672C\u56DE\u5408\u751F\u6210\u5931\u8D25\uFF0C\u53EF\u957F\u6309\u91CD\u56DE",
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
                append(" \u258C")
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
        com.example.myapplication.model.RoleplaySuggestionAxis.PLOT -> "\u63A8\u8FDB"
        com.example.myapplication.model.RoleplaySuggestionAxis.INFO -> "\u63A2\u7D22"
        com.example.myapplication.model.RoleplaySuggestionAxis.EMOTION -> "\u60C5\u7EEA"
    }
}
