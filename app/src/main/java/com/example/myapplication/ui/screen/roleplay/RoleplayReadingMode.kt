package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassVariant
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingScrimVariant
import com.example.myapplication.ui.component.roleplay.RoleplayEmotionChip
import com.example.myapplication.ui.component.roleplay.RoleplayLongformCard
import com.example.myapplication.ui.component.roleplay.RoleplayQuotedDialogueHighlightColor
import com.example.myapplication.ui.component.roleplay.toLongformParagraphs
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.buildCharacterDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.buildQuotedDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.calculateImmersiveBackdropAmbientLuminance
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.resolveImmersiveReadingScrimAlpha

@Composable
fun RoleplayReadingMode(
    messages: List<RoleplayMessageUiModel>,
    scenarioTitle: String,
    backgroundUri: String,
    lineHeightScale: Float,
    highContrast: Boolean,
    onDismiss: () -> Unit,
) {
    val effectiveHighContrast = highContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = backgroundUri,
        highContrast = effectiveHighContrast,
    )
    val palette = backdropState.palette
    val ambientLuminance = remember(backdropState.imageBitmap, palette) {
        calculateImmersiveBackdropAmbientLuminance(backdropState)
    }
    val scrimAlpha = remember(ambientLuminance) {
        resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = ambientLuminance,
            variant = ImmersiveReadingScrimVariant.READING,
        )
    }
    val scrimBrush = remember(scrimAlpha) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = scrimAlpha * 0.22f),
                0.42f to Color.Black.copy(alpha = scrimAlpha * 0.58f),
                1.0f to Color.Black.copy(alpha = scrimAlpha),
            ),
        )
    }

    // 阅读模式始终显示系统栏并添加安全区 padding，不跟随 immersiveMode 设置。
    // 设计意图：阅读模式的核心目标是"高可读性的全文回顾"，需要稳定的可视区域和易于退出的系统栏。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush),
        )

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ImmersiveReadingGlassSurface(
                backdropState = backdropState,
                modifier = Modifier
                    .fillMaxWidth(0.91f)
                    .fillMaxHeight(0.965f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(30.dp),
                variant = ImmersiveReadingGlassVariant.PANEL,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 22.dp,
                        bottom = 34.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.roleplay_reading_mode_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.onGlass,
                                )
                                Text(
                                    text = scenarioTitle.ifBlank {
                                        stringResource(id = R.string.roleplay_story_recap)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onGlassMuted,
                                )
                                ReadingModeMetaChip(
                                    text = stringResource(
                                        id = R.string.roleplay_story_count,
                                        messages.size,
                                    ),
                                    backdropState = backdropState,
                                )
                            }
                            NarraIconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(id = R.string.common_close),
                                    tint = palette.onGlass,
                                )
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(id = R.string.roleplay_no_story_content),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = palette.onGlassMuted.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = messages,
                        key = { index, item ->
                            "${item.sourceMessageId}-${item.createdAt}-${item.content.hashCode()}-$index"
                        },
                    ) { _, message ->
                        when (message.contentType) {
                            RoleplayContentType.NARRATION -> NarrationReadingBlock(
                                message = message,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                            )

                            RoleplayContentType.THOUGHT -> ThoughtReadingBlock(
                                message = message,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                            )

                            RoleplayContentType.SYSTEM -> SystemReadingBlock(message, backdropState)

                            RoleplayContentType.DIALOGUE -> DialogueReadingBlock(
                                message = message,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                            )

                            RoleplayContentType.ACTION -> DialogueReadingBlock(
                                message = message,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                            )

                            RoleplayContentType.LONGFORM -> LongformReadingBlock(
                                message = message,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                            )

                            RoleplayContentType.SPECIAL_PLAY -> TransferReadingBlock(message)

                            RoleplayContentType.STATUS -> SystemReadingBlock(message, backdropState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThoughtReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    val paragraphs = remember(message.content) {
        message.content.toLongformParagraphs()
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.fillMaxWidth(0.76f),
            shape = RoundedCornerShape(20.dp),
            variant = ImmersiveReadingGlassVariant.THOUGHT,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = backdropState.palette.thoughtText,
                            dialogueColor = RoleplayQuotedDialogueHighlightColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 26.sp * lineHeightScale,
                            letterSpacing = 0.3.sp,
                        ),
                        color = backdropState.palette.thoughtText,
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrationReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    val paragraphs = remember(message.content) {
        message.content.toLongformParagraphs()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = buildQuotedDialogueAnnotatedString(
                    text = paragraph,
                    narrationColor = backdropState.palette.onGlassMuted,
                    dialogueColor = RoleplayQuotedDialogueHighlightColor,
                ),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 30.sp * lineHeightScale,
                    letterSpacing = 0.3.sp,
                ),
                color = backdropState.palette.onGlassMuted,
            )
        }
    }
}

@Composable
private fun DialogueReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    val isUser = message.speaker == RoleplaySpeaker.USER
    val palette = backdropState.palette
    val nameColor = if (isUser) palette.userAccent else palette.characterAccent
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.widthIn(max = maxWidth * if (isUser) 0.82f else 0.9f),
            shape = RoundedCornerShape(22.dp),
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
            overlayColor = if (isUser) {
                palette.userAccent.copy(alpha = 0.08f)
            } else {
                palette.panelTint.copy(alpha = 0.09f)
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.speakerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                    )
                    if (message.emotion.isNotBlank()) {
                        RoleplayEmotionChip(
                            text = message.emotion,
                            textColor = nameColor,
                            containerColor = palette.panelTint.copy(alpha = 0.12f),
                        )
                    }
                }
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = if (isUser) {
                                buildQuotedDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = palette.onGlass,
                                    dialogueColor = RoleplayQuotedDialogueHighlightColor,
                                )
                            } else {
                                buildCharacterDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = palette.onGlass,
                                    dialogueColor = RoleplayQuotedDialogueHighlightColor,
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 30.sp * lineHeightScale,
                                letterSpacing = 0.3.sp,
                            ),
                            color = palette.onGlass,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongformReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    RoleplayLongformCard(
        speakerName = message.speakerName,
        content = message.content,
        richTextSource = message.richTextSource,
        backdropState = backdropState,
        useReadingGlassStyle = true,
        containerColor = Color.Transparent,
        titleColor = backdropState.palette.characterAccent,
        bodyColor = backdropState.palette.onGlass,
        accentColor = RoleplayQuotedDialogueHighlightColor,
        thoughtColor = backdropState.palette.thoughtText,
        lineHeightScale = lineHeightScale,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 6.dp),
    )
}

@Composable
private fun SystemReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        ReadingModeMetaChip(
            text = message.content,
            backdropState = backdropState,
        )
    }
}

@Composable
private fun TransferReadingBlock(message: RoleplayMessageUiModel) {
    val specialPart = message.specialPart ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = if (message.speaker == RoleplaySpeaker.USER) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        SpecialPlayCard(
            part = specialPart,
            isUserMessage = message.speaker == RoleplaySpeaker.USER,
            onConfirmTransferReceipt = null,
            modifier = if (message.speaker == RoleplaySpeaker.USER) {
                Modifier.fillMaxWidth(0.78f)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

@Composable
private fun ReadingModeMetaChip(
    text: String,
    backdropState: ImmersiveBackdropState,
) {
    ImmersiveReadingGlassSurface(
        backdropState = backdropState,
        shape = RoundedCornerShape(999.dp),
        variant = ImmersiveReadingGlassVariant.PILL,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = backdropState.palette.onGlass,
        )
    }
}
