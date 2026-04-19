package com.example.myapplication.ui.component.roleplay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.actionMetadataValue
import com.example.myapplication.model.voiceMessageContent
import com.example.myapplication.model.voiceMessageDurationLabel
import com.example.myapplication.model.voiceMessageDurationSeconds
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
internal fun RoleplayActionCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onOpenVideoCall: (() -> Unit)?,
) {
    val isUserMessage = message.speaker == RoleplaySpeaker.USER
    if (actionPart.actionType == ChatActionType.VOICE_MESSAGE) {
        RoleplayVoiceMessageCard(
            message = message,
            actionPart = actionPart,
            colors = colors,
            backdropState = backdropState,
            isUserMessage = isUserMessage,
        )
        return
    }
    val title = when (actionPart.actionType) {
        ChatActionType.EMOJI -> "\u8868\u60C5"
        ChatActionType.VOICE_MESSAGE -> "\u8BED\u97F3\u6D88\u606F"
        ChatActionType.AI_PHOTO -> "\u7167\u7247"
        ChatActionType.LOCATION -> "\u4F4D\u7F6E"
        ChatActionType.POKE -> "\u4E92\u52A8"
        ChatActionType.VIDEO_CALL -> "\u89C6\u9891\u901A\u8BDD"
        null -> "\u6D88\u606F"
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
        ChatActionType.POKE -> if (isUserMessage) "\u4F60\u6233\u4E86\u6233\u5BF9\u65B9" else "\u6233\u4E86\u6233\u4F60"
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
                    text = "\u70B9\u8FD9\u91CC\u63A5\u901A",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun RoleplayVoiceMessageCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    isUserMessage: Boolean,
) {
    var isPlaying by rememberSaveable(message.sourceMessageId, actionPart.actionId) { mutableStateOf(false) }
    var isExpanded by rememberSaveable(message.sourceMessageId, actionPart.actionId) { mutableStateOf(false) }
    val voiceContent = actionPart.voiceMessageContent()
    val durationSeconds = actionPart.voiceMessageDurationSeconds()
    val waveform = remember(actionPart.actionId, voiceContent) {
        buildVoiceWaveformSeed(
            seed = "${actionPart.actionId}:$voiceContent",
            count = 12,
        )
    }
    val infiniteTransition = rememberInfiniteTransition(label = "roleplay_voice_wave")
    val animatedWavePulses = List(waveform.size) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420 + index * 45,
                    delayMillis = index * 50,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "roleplay_voice_wave_$index",
        ).value
    }

    LaunchedEffect(isPlaying, actionPart.actionId) {
        if (isPlaying) {
            val fakePlaybackMillis = durationSeconds
                .coerceIn(1, 8) * 420L
            delay(fakePlaybackMillis)
            isPlaying = false
        }
    }

    // \u8BED\u97F3\u65F6\u957F\u52A8\u6001\u5BBD\u5EA6
    val dynamicWidth = (120 + (durationSeconds.coerceIn(1, 12) - 1) * 12).coerceAtMost(260).dp

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.widthIn(min = 120.dp, max = dynamicWidth),
        shape = RoundedCornerShape(22.dp),
        overlayColor = if (isUserMessage) colors.panelBackgroundStrong else colors.panelBackground,
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            color = if (isUserMessage) colors.userAccent else colors.characterAccent,
                            shape = CircleShape,
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "\u6682\u505C\u8BED\u97F3" else "\u64AD\u653E\u8BED\u97F3",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    waveform.forEachIndexed { index, baseHeight ->
                        val animatedHeight = if (isPlaying) {
                            baseHeight * animatedWavePulses[index]
                        } else {
                            baseHeight
                        }
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((8f + animatedHeight * 14f).dp)
                                .background(
                                    color = if (isUserMessage) {
                                        colors.userAccent.copy(alpha = 0.95f)
                                    } else {
                                        colors.characterAccent.copy(alpha = 0.92f)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
                Text(
                    text = actionPart.voiceMessageDurationLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted,
                )
            }
            if (isExpanded && voiceContent.isNotBlank()) {
                Text(
                    text = voiceContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = colors.textPrimary.copy(alpha = 0.88f),
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 12.dp,
                    ),
                )
            }
        }
    }
}

private fun buildVoiceWaveformSeed(
    seed: String,
    count: Int,
): List<Float> {
    var state = seed.hashCode().let { if (it == Int.MIN_VALUE) 7 else abs(it) + 7 }
    return List(count) {
        state = (state * 1103515245 + 12345)
        val normalized = abs(state % 1000) / 1000f
        0.35f + normalized * 0.75f
    }
}

@Composable
internal fun RoleplayPokeSystemMessage(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
) {
    val pokeText = buildString {
        val speakerName = message.speakerName.ifBlank { "\u5BF9\u65B9" }
        val content = message.content.trim()
        if (content.isNotBlank() && content != "\u6233\u4E86\u6233\u4F60" && content != "\u4F60\u6233\u4E86\u6233\u5BF9\u65B9") {
            append(speakerName)
            append(" ")
            append(content)
        } else if (message.speaker == RoleplaySpeaker.USER) {
            append("\u4F60\u6233\u4E86\u6233 ")
            append(speakerName)
        } else {
            append(speakerName)
            append(" \u6233\u4E86\u6233\u4F60")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = pokeText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            ),
            color = colors.textMuted.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}
