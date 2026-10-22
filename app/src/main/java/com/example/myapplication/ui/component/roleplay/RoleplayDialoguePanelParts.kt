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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.example.myapplication.R
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.readableRoleplayText
import com.example.myapplication.ui.theme.readableRoleplayTextOnSurfaces
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget

internal data class ImmersiveRoleplayColors(
    val textPrimary: Color,
    val userText: Color,
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
    val panelText: Color = textPrimary,
    val panelMutedText: Color = textMuted,
    val narrationText: Color = textMuted,
    val systemText: Color = textPrimary,
    val systemBackground: Color = panelBackgroundStrong,
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

internal fun resolveRoleplayDialogueHighlightColor(
    hasImage: Boolean,
    colors: ImmersiveRoleplayColors,
): Color {
    return if (hasImage) {
        colors.characterAccent
    } else {
        colors.textPrimary
    }
}

@Composable
internal fun rememberImmersiveRoleplayColors(
    backdropState: ImmersiveBackdropState,
    noBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
    useGlassColors: Boolean = backdropState.hasImage,
): ImmersiveRoleplayColors {
    val skinSpec = rememberRoleplayNoBackgroundSkinSpec(noBackgroundSkin)
    val colorScheme = MaterialTheme.colorScheme
    return remember(backdropState, skinSpec, colorScheme, useGlassColors) {
        resolveImmersiveRoleplayColors(backdropState, skinSpec, colorScheme, useGlassColors)
    }
}

internal fun resolveImmersiveRoleplayColors(
    backdropState: ImmersiveBackdropState,
    skinSpec: RoleplayNoBackgroundSkinSpec,
    colorScheme: ColorScheme,
    useGlassColors: Boolean,
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val backing = backdropState.imageBitmap?.let(::sampleAverageColor) ?: skinSpec.background
    val themeBackings = if (!useGlassColors && backdropState.hasImage) {
        val scrimTop = palette.scrimTop.copy(alpha = 0.14f)
        val scrimBottom = palette.scrimBottom.copy(alpha = 0.28f)
        // 关闭消息玻璃仍保留页面遮罩；透明皮肤必须针对实际透底面选字色。
        listOf(scrimTop, lerp(scrimTop, scrimBottom, 0.5f), scrimBottom).flatMap { scrim ->
            val surface = scrim.compositeOver(backing)
            listOf(
                surface,
                palette.panelHighlight.copy(alpha = 0.06f).compositeOver(surface),
                palette.shadowColor.copy(alpha = 0.02f).compositeOver(surface),
            )
        }
    } else {
        listOf(backing)
    }
    val panel = if (useGlassColors) palette.panelTintStrong.copy(alpha = 0.92f) else skinSpec.panel
    val panelStrong = if (useGlassColors) palette.readingSurface.copy(alpha = 0.94f) else skinSpec.panelStrong
    val userBubble = if (useGlassColors) palette.userAccent.copy(alpha = 0.26f) else skinSpec.userBubble
    val characterBubble = if (useGlassColors) palette.readingSurface.copy(alpha = 0.24f) else skinSpec.characterBubble
    val narrationBubble = if (useGlassColors) palette.readingSurface.copy(alpha = 0.30f) else skinSpec.narrationBubble
    fun readable(preferred: Color, surface: Color, readingGlass: Boolean = false): Color {
        if (useGlassColors && readingGlass) {
            return readableRoleplayTextOnSurfaces(preferred, roleplayDialogueReadingSurfaces(backdropState, surface))
        }
        return readableRoleplayTextOnSurfaces(preferred, themeBackings.map(surface::compositeOver))
    }
    val primaryText = readable(if (useGlassColors) palette.onGlass else skinSpec.characterText, characterBubble, true)
    val narrationText = readable(if (useGlassColors) palette.onGlassMuted else skinSpec.mutedText, narrationBubble, true)
    return ImmersiveRoleplayColors(
        textPrimary = primaryText,
        userText = readable(if (useGlassColors) palette.onGlass else skinSpec.userText, userBubble, true),
        textMuted = narrationText,
        characterAccent = readable(if (useGlassColors) palette.characterAccent else skinSpec.accent, characterBubble, true),
        userAccent = readable(if (useGlassColors) palette.userAccent else skinSpec.userAccent, userBubble, true),
        thoughtText = narrationText,
        panelBackground = panel,
        panelBackgroundStrong = panelStrong,
        panelBorder = if (useGlassColors) palette.panelBorder else skinSpec.border,
        errorText = readableRoleplayText(colorScheme.onErrorContainer, colorScheme.errorContainer, backing),
        errorBackground = colorScheme.errorContainer,
        errorBackgroundStrong = colorScheme.errorContainer,
        userBubbleBackground = userBubble,
        characterBubbleBackground = characterBubble,
        narrationBubbleBackground = narrationBubble,
        panelText = readable(if (useGlassColors) palette.onGlass else skinSpec.characterText, panelStrong),
        panelMutedText = readable(if (useGlassColors) palette.onGlassMuted else colorScheme.onSurfaceVariant, panelStrong),
        narrationText = narrationText,
        systemText = readableRoleplayText(colorScheme.onSecondaryContainer, colorScheme.secondaryContainer, backing),
        systemBackground = colorScheme.secondaryContainer,
    )
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
    maxHeight: Dp,
) {
    val view = LocalView.current
    val showPanel = suggestions.isNotEmpty() || isGeneratingSuggestions || !suggestionErrorMessage.isNullOrBlank()
    if (!showPanel) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.fillMaxWidth(),
            shape = NarraShapes.medium,
            blurRadius = 18.dp,
            overlayColor = colors.panelBackgroundStrong,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NarraMinimumTouchTarget)
                    .combinedClickable(
                        enabled = !isSending,
                        role = Role.Button,
                        onClickLabel = "生成建议",
                        onClick = onGenerateSuggestions,
                    )
                    .padding(horizontal = NarraUi.space16, vertical = NarraUi.space8),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(id = R.string.roleplay_ai_helper_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.panelText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isSending) {
                        stringResource(id = R.string.roleplay_sending)
                    } else {
                        stringResource(id = R.string.roleplay_generate)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.panelMutedText,
                )
            }
        }
        return
    }

    val suggestionListState = rememberLazyListState()
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = NarraShapes.medium,
        blurRadius = 18.dp,
        overlayColor = colors.panelBackgroundStrong,
    ) {
        LazyColumn(
            state = suggestionListState,
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("roleplay_scene_suggestions"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(NarraUi.space12),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
        ) {
            item(key = "suggestion-actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    Text(
                        stringResource(id = R.string.roleplay_ai_helper_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = colors.panelText,
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
                                color = colors.panelText,
                            )
                        }
                        NarraTextButton(onClick = onClearSuggestions) {
                            Text(
                                text = if (isGeneratingSuggestions) {
                                    stringResource(id = R.string.common_cancel)
                                } else {
                                    stringResource(id = R.string.common_collapse)
                                },
                                color = colors.panelMutedText,
                            )
                        }
                    }
                }
            }
            if (isGeneratingSuggestions) {
                item(key = "suggestion-loading") {
                    Row(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.panelText)
                        Text(
                            text = stringResource(id = R.string.roleplay_generating_suggestions),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.panelMutedText,
                        )
                    }
                }
            }
            if (!suggestionErrorMessage.isNullOrBlank()) {
                item(key = "suggestion-error") {
                    Surface(color = colors.errorBackground, shape = NarraShapes.small) {
                        Text(suggestionErrorMessage, modifier = Modifier.padding(NarraUi.space8),
                            style = MaterialTheme.typography.bodySmall, color = colors.errorText)
                    }
                }
            }
            items(suggestions, key = { it.id }) { suggestion ->
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.fillMaxWidth(),
                    shape = NarraShapes.medium,
                    overlayColor = colors.panelBackgroundStrong,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = NarraMinimumTouchTarget)
                            .testTag("roleplay_suggestion_${suggestion.id}")
                            .combinedClickable(
                                enabled = !isSending,
                                role = Role.Button,
                                onClickLabel = "使用建议：${suggestion.label}",
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                                    onApplySuggestion(suggestion.text)
                                },
                            )
                            .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                        ) {
                            Text(
                                text = suggestion.axis.toReadableLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.panelMutedText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                suggestion.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.panelText,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            suggestion.text,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = colors.panelText,
                        )
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

        Surface(color = colors.panelBackgroundStrong, shape = NarraShapes.medium) {
            Column(
                modifier = Modifier.padding(horizontal = NarraUi.space32, vertical = NarraUi.space24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(
                            colors.characterAccent.copy(alpha = 0.4f),
                            NarraShapes.pill,
                        ),
                )

                androidx.compose.material3.Text(
                    text = stringResource(id = R.string.roleplay_empty_state_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.panelText,
                )
                androidx.compose.material3.Text(
                    text = stringResource(id = R.string.roleplay_empty_state_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.panelMutedText,
                )

                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(
                            colors.textMuted.copy(alpha = 0.25f),
                            NarraShapes.pill,
                        ),
                )
            }
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
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "roleplay_log_cursor")
    val cursorAlpha = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RoleplayStreamingCursorPulseMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "roleplay_log_cursor_alpha",
    )
    val layoutText = remember(content) {
        AnnotatedString(content + RoleplayStreamingCursorLayoutPlaceholder)
    }
    val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = layoutText,
        modifier = modifier
            .clearAndSetSemantics {
                text = AnnotatedString(content)
            }
            .drawWithContent {
                drawContent()
                val cursorRect = textLayoutResult.value
                    ?.getCursorRect(content.length + RoleplayStreamingCursorGapLength)
                    ?: return@drawWithContent
                val cursorHeight = 16.sp.toPx()
                drawRect(
                    color = accentColor,
                    topLeft = Offset(
                        x = cursorRect.left,
                        y = cursorRect.top + ((cursorRect.height - cursorHeight) / 2f).coerceAtLeast(0f),
                    ),
                    size = Size(
                        width = 6.sp.toPx(),
                        height = cursorHeight.coerceAtMost(cursorRect.height),
                    ),
                    alpha = cursorAlpha.value,
                )
            },
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 26.sp * lineHeightScale,
            letterSpacing = 0.6.sp,
        ),
        color = textColor,
        onTextLayout = { textLayoutResult.value = it },
    )
}

private const val RoleplayStreamingCursorLayoutPlaceholder = "\u00A0\u2007"
private const val RoleplayStreamingCursorGapLength = 1

private fun com.example.myapplication.model.RoleplaySuggestionAxis.toReadableLabel(): String {
    return when (this) {
        com.example.myapplication.model.RoleplaySuggestionAxis.PLOT -> "\u63A8\u8FDB"
        com.example.myapplication.model.RoleplaySuggestionAxis.INFO -> "\u63A2\u7D22"
        com.example.myapplication.model.RoleplaySuggestionAxis.EMOTION -> "\u60C5\u7EEA"
    }
}
