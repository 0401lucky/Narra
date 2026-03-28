package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.component.NarraIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.TransferPlayCard
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassChip
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplayEmotionChip
import com.example.myapplication.ui.component.roleplay.RoleplayLongformCard
import com.example.myapplication.ui.component.roleplay.RoleplayQuotedDialogueHighlightColor
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.buildCharacterDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.buildQuotedDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState

@Composable
fun RoleplayReadingMode(
    messages: List<RoleplayMessageUiModel>,
    scenarioTitle: String,
    backgroundUri: String,
    onDismiss: () -> Unit,
) {
    val backdropState = rememberImmersiveBackdropState(backgroundUri)
    val palette = backdropState.palette

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
                .background(Color.Black.copy(alpha = 0.18f)),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(32.dp),
                blurRadius = 32.dp,
                overlayColor = palette.readingSurface,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 18.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "阅读模式",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.onGlass,
                                )
                                Text(
                                    text = scenarioTitle.ifBlank { "剧情回顾" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onGlassMuted,
                                )
                                ImmersiveGlassChip(
                                    text = "${messages.size} 条剧情",
                                    backdropState = backdropState,
                                )
                            }
                            NarraIconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = palette.onGlass)
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
                                    text = "还没有剧情内容",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = palette.onGlassMuted.copy(alpha = 0.72f),
                                )
                            }
                        }
                    }

                    items(
                        messages,
                        key = { "${it.sourceMessageId}-${it.createdAt}-${it.content.hashCode()}" },
                    ) { message ->
                        when (message.contentType) {
                            RoleplayContentType.NARRATION -> NarrationReadingBlock(message, backdropState)
                            RoleplayContentType.SYSTEM -> SystemReadingBlock(message, backdropState)
                            RoleplayContentType.DIALOGUE -> DialogueReadingBlock(message, backdropState)
                            RoleplayContentType.LONGFORM -> LongformReadingBlock(message, backdropState)
                            RoleplayContentType.SPECIAL_TRANSFER -> TransferReadingBlock(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NarrationReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
) {
    val paragraphs = remember(message.content) {
        message.content.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backdropState.palette.panelTint.copy(alpha = 0.22f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    lineHeight = 30.sp,
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
) {
    val isUser = message.speaker == RoleplaySpeaker.USER
    val palette = backdropState.palette
    val nameColor = if (isUser) palette.userAccent else palette.characterAccent
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = if (isUser) {
                Modifier
                    .fillMaxWidth(0.88f)
                    .background(
                        color = palette.panelTint.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = palette.panelTintStrong.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            },
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
                        containerColor = palette.panelTint.copy(alpha = 0.20f),
                    )
                }
            }
            val paragraphs = remember(message.content) {
                message.content.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            }
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
                            lineHeight = 30.sp,
                            letterSpacing = 0.3.sp,
                        ),
                        color = palette.onGlass,
                    )
                }
            }
        }
    }
}

@Composable
private fun LongformReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
) {
    RoleplayLongformCard(
        speakerName = message.speakerName,
        content = message.content,
        richTextSource = message.richTextSource,
        containerColor = Color.Transparent,
        titleColor = backdropState.palette.characterAccent,
        bodyColor = backdropState.palette.onGlass,
        accentColor = RoleplayQuotedDialogueHighlightColor,
        thoughtColor = backdropState.palette.thoughtText,
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
        ImmersiveGlassChip(
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
        TransferPlayCard(
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
