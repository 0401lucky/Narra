package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.RoleplayMessageUiModel

@Composable
internal fun OnlinePhoneNarrationBubble(
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
internal fun OnlinePhoneThoughtBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier
                .wrapContentWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(999.dp),
            blurRadius = 14.dp,
            overlayColor = colors.panelBackground.copy(alpha = if (expanded) 0.34f else 0.24f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (expanded) "\u6536\u8D77\u5FC3\u58F0" else "\u5C55\u5F00\u5FC3\u58F0",
                    tint = colors.characterAccent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (expanded) "\u6536\u8D77\u5FC3\u58F0" else "\u67E5\u770B\u5FC3\u58F0",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.thoughtText,
                )
            }
        }
        if (expanded) {
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
    }
}
