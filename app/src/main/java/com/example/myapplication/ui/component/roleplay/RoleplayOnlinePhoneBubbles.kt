package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.model.RoleplayMessageUiModel

@Composable
internal fun OnlinePhoneNarrationBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    useGlassBubble: Boolean = backdropState.hasImage,
) {
    val shape = NarraShapes.large
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
    OnlinePhoneReadingSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(0.96f),
        shape = shape,
        overlayColor = colors.narrationBubbleBackground,
        useGlassBubble = useGlassBubble,
    ) {
            Column(
                modifier = Modifier.padding(horizontal = NarraUi.space16, vertical = NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.narrationText,
                            dialogueColor = resolveRoleplayDialogueHighlightColor(
                                hasImage = useGlassBubble,
                                colors = colors,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp * lineHeightScale,
                            letterSpacing = 0.5.sp,
                        ),
                        color = colors.narrationText,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OnlinePhoneThoughtBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    useGlassBubble: Boolean = backdropState.hasImage,
) {
    var expanded by rememberSaveable(
        message.sourceMessageId,
        message.createdAt,
        message.content,
    ) {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
    ) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier
                .wrapContentWidth()
                .clickable { expanded = !expanded },
            shape = NarraShapes.pill,
            overlayColor = colors.systemBackground,
        ) {
            Row(
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).padding(horizontal = NarraUi.space8, vertical = NarraUi.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = colors.systemText,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (expanded) "\u6536\u8D77\u5FC3\u58F0" else "\u67E5\u770B\u5FC3\u58F0",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.systemText,
                )
            }
        }
        if (expanded) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                OnlinePhoneReadingSurface(
                    backdropState = backdropState,
                    modifier = Modifier.widthIn(max = maxWidth * 0.76f),
                    shape = NarraShapes.large,
                    overlayColor = colors.narrationBubbleBackground,
                    useGlassBubble = useGlassBubble,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = NarraUi.space16, vertical = NarraUi.space12),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    ) {
                        val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = buildQuotedDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = colors.thoughtText,
                                    dialogueColor = resolveRoleplayDialogueHighlightColor(
                                        hasImage = useGlassBubble,
                                        colors = colors,
                                    ),
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
    }
}

@Composable
private fun OnlinePhoneReadingSurface(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier,
    shape: Shape,
    overlayColor: Color,
    useGlassBubble: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    if (useGlassBubble) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = modifier,
            shape = shape,
            overlayColor = overlayColor,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
            minimumOverlayAlpha = RoleplayDialogueReadingMinimumAlpha,
            content = content,
        )
    } else {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = modifier,
            shape = shape,
            overlayColor = overlayColor,
            content = content,
        )
    }
}
