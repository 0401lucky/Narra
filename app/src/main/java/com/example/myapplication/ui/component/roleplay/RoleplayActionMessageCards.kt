package com.example.myapplication.ui.component.roleplay

import android.media.MediaPlayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.actionMetadataValue
import com.example.myapplication.model.VoiceAudioStatus
import com.example.myapplication.model.hasReadyVoiceAudio
import com.example.myapplication.model.voiceAudioErrorMessage
import com.example.myapplication.model.voiceAudioPath
import com.example.myapplication.model.voiceAudioStatus
import com.example.myapplication.model.voiceMessageContent
import com.example.myapplication.model.voiceMessageDurationLabel
import com.example.myapplication.model.voiceMessageDurationSeconds
import kotlin.math.abs

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
    if (actionPart.actionType == ChatActionType.AI_PHOTO) {
        RoleplayAiPhotoCard(
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
private fun RoleplayAiPhotoCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    isUserMessage: Boolean,
) {
    val description = actionPart.actionMetadataValue("description")
        .trim()
        .ifBlank { message.copyText.ifBlank { "照片" } }
    var isFlipped by rememberSaveable(message.sourceMessageId, actionPart.actionId) {
        mutableStateOf(false)
    }
    val photoRotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "roleplay_ai_photo_flip",
    )
    val showBack = photoRotationY > 90f
    val density = LocalDensity.current.density
    val accentColor = if (isUserMessage) colors.userAccent else colors.characterAccent
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isFlipped = !isFlipped }
            .graphicsLayer {
                this.rotationY = photoRotationY
                cameraDistance = 18f * density
            },
        shape = RoundedCornerShape(22.dp),
        overlayColor = if (isUserMessage) colors.panelBackgroundStrong else colors.panelBackground,
    ) {
        Box(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .padding(12.dp)
                .graphicsLayer {
                    if (showBack) {
                        this.rotationY = 180f
                    }
                },
        ) {
            if (showBack) {
                RoleplayAiPhotoBack(
                    description = description,
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                )
            } else {
                RoleplayAiPhotoFront(
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }
        }
    }
}

@Composable
private fun RoleplayAiPhotoFront(
    accentColor: Color,
    colors: ImmersiveRoleplayColors,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.24f),
                    colors.panelBackgroundStrong.copy(alpha = 0.72f),
                ),
            ),
            shape = RoundedCornerShape(18.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = "照片",
            tint = colors.textPrimary.copy(alpha = 0.72f),
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = "照片",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun RoleplayAiPhotoBack(
    description: String,
    accentColor: Color,
    colors: ImmersiveRoleplayColors,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(
            color = colors.panelBackgroundStrong.copy(alpha = 0.78f),
            shape = RoundedCornerShape(18.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "照片",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
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
    var playbackError by rememberSaveable(message.sourceMessageId, actionPart.actionId) { mutableStateOf("") }
    var mediaPlayer by remember(message.sourceMessageId, actionPart.actionId) { mutableStateOf<MediaPlayer?>(null) }
    val voiceContent = actionPart.voiceMessageContent()
    val durationSeconds = actionPart.voiceMessageDurationSeconds()
    val audioStatus = actionPart.voiceAudioStatus()
    val canPlayAudio = actionPart.hasReadyVoiceAudio()
    val statusLabel = when (audioStatus) {
        VoiceAudioStatus.GENERATING -> "生成中"
        VoiceAudioStatus.READY -> if (canPlayAudio) "可播放" else "音频缺失"
        VoiceAudioStatus.FAILED -> "生成失败"
        null -> if (isUserMessage) "文字语音" else "文字兜底"
    }
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

    fun stopPlayback() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            player.release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    fun togglePlayback() {
        if (!canPlayAudio) {
            isExpanded = true
            playbackError = when (audioStatus) {
                VoiceAudioStatus.GENERATING -> "语音正在生成，稍后再试"
                VoiceAudioStatus.FAILED -> actionPart.voiceAudioErrorMessage().ifBlank { "语音生成失败" }
                VoiceAudioStatus.READY -> "本地音频文件不可用"
                null -> "暂时只有文字内容"
            }
            return
        }
        if (isPlaying) {
            stopPlayback()
            return
        }
        playbackError = ""
        runCatching {
            val player = MediaPlayer()
            player.setDataSource(actionPart.voiceAudioPath())
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) {
                    mediaPlayer = null
                }
                isPlaying = false
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                failedPlayer.release()
                if (mediaPlayer === failedPlayer) {
                    mediaPlayer = null
                }
                isPlaying = false
                isExpanded = true
                playbackError = "语音播放失败，已保留文字内容"
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            isPlaying = true
        }.onFailure { throwable ->
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            isExpanded = true
            playbackError = throwable.message?.takeIf { it.isNotBlank() } ?: "语音播放失败"
        }
    }

    DisposableEffect(message.sourceMessageId, actionPart.actionId) {
        onDispose {
            stopPlayback()
        }
    }

    // 语音时长动态宽度
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
                        ) { togglePlayback() },
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
                    text = "${actionPart.voiceMessageDurationLabel()} · $statusLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted,
                )
            }
            if (isExpanded && (voiceContent.isNotBlank() || playbackError.isNotBlank())) {
                Column(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (playbackError.isNotBlank()) {
                        Text(
                            text = playbackError,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                        )
                    }
                    if (voiceContent.isNotBlank()) {
                        Text(
                            text = voiceContent,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                letterSpacing = 0.4.sp,
                            ),
                            color = colors.textPrimary.copy(alpha = 0.88f),
                        )
                    }
                }
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
