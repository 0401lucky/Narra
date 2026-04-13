package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveRoleplayColors
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import kotlinx.coroutines.delay

internal data class VideoCallPresentationState(
    val carryoverCount: Int,
    val visibleMessages: List<RoleplayMessageUiModel>,
)

@Composable
internal fun RoleplayVideoCallScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    messages: List<RoleplayMessageUiModel>,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    activeVideoCallStartedAt: Long,
    isVideoCallActive: Boolean,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onHangup: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    if (scenario == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("场景不存在或已被删除")
        }
        return
    }

    BackHandler(onBack = onHangup)

    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )
    ApplyRoleplaySystemBars(
        backdropState = backdropState,
        immersiveMode = settings.roleplayImmersiveMode,
    )
    val colors = rememberImmersiveRoleplayColors(backdropState)
    val characterName = remember(scenario.characterDisplayNameOverride, assistant?.name) {
        scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }
    val userName = remember(scenario.userDisplayNameOverride, settings.userDisplayName) {
        scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
            .ifBlank { "你" }
    }
    val presentationState = remember(messages, activeVideoCallStartedAt) {
        buildVideoCallPresentationState(
            messages = messages,
            activeVideoCallStartedAt = activeVideoCallStartedAt,
        )
    }
    val visibleMessages = presentationState.visibleMessages
    val transcriptScrollState = rememberScrollState()
    val statusBarTopPadding = if (settings.roleplayImmersiveMode.storageValue == "none") {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val elapsedText = rememberVideoCallElapsedText(activeVideoCallStartedAt)

    LaunchedEffect(visibleMessages.lastOrNull()?.sourceMessageId, visibleMessages.lastOrNull()?.content) {
        if (visibleMessages.isNotEmpty()) {
            transcriptScrollState.animateScrollTo(transcriptScrollState.maxValue)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        VideoCallMainPortrait(
            scenario = scenario,
            characterName = characterName,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.38f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.54f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(
                    if (settings.roleplayImmersiveMode.storageValue == "none") {
                        Modifier
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = statusBarTopPadding + 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    blurRadius = 18.dp,
                    overlayColor = colors.panelBackgroundStrong.copy(alpha = 0.88f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = characterName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isVideoCallActive) "视频通话中..." else "正在接通视频通话...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                        Text(
                            text = elapsedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                    }
                }
                VideoCallFloatingPreview(
                    name = userName,
                    avatarUri = scenario.userPortraitUri.ifBlank { settings.userAvatarUri },
                    avatarUrl = scenario.userPortraitUrl.ifBlank { settings.userAvatarUrl },
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(width = 96.dp, height = 136.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                if (!isVideoCallActive && activeVideoCallStartedAt <= 0L) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        color = colors.panelBackgroundStrong.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.characterAccent,
                            )
                            Text(
                                text = "正在接通视频通话...",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (visibleMessages.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        color = colors.panelBackgroundStrong.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            text = buildVideoCallEmptyHint(
                                carryoverCount = presentationState.carryoverCount,
                            ),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    VideoCallFloatingTranscript(
                        messages = visibleMessages,
                        carryoverCount = presentationState.carryoverCount,
                        colors = colors,
                        backdropState = backdropState,
                        scrollState = transcriptScrollState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxHeight(0.78f)
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }

                AppSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoCallInputBar(
                    colors = colors,
                    backdropState = backdropState,
                    input = input,
                    inputFocusToken = inputFocusToken,
                    isSending = isSending,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onCancelSending = onCancelSending,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    NarraIconButton(
                        onClick = onHangup,
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                color = Color(0xFFFF4F4F),
                                shape = CircleShape,
                            ),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "挂断视频通话",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun buildVideoCallPresentationState(
    messages: List<RoleplayMessageUiModel>,
    activeVideoCallStartedAt: Long,
): VideoCallPresentationState {
    if (activeVideoCallStartedAt <= 0L) {
        return VideoCallPresentationState(
            carryoverCount = 0,
            visibleMessages = emptyList(),
        )
    }
    val conversationMessages = messages.filter { message ->
        message.contentType != RoleplayContentType.SYSTEM
    }
    return VideoCallPresentationState(
        carryoverCount = conversationMessages.count { it.createdAt < activeVideoCallStartedAt },
        visibleMessages = conversationMessages
            .filter { it.createdAt >= activeVideoCallStartedAt },
    )
}

private fun buildVideoCallEmptyHint(carryoverCount: Int): String {
    return if (carryoverCount > 0) {
        "通话已接通，已承接前面的聊天内容，直接接着上一句继续聊。"
    } else {
        "通话已接通，你先发一句，TA 才会接着回你。"
    }
}

@Composable
private fun VideoCallFloatingTranscript(
    messages: List<RoleplayMessageUiModel>,
    carryoverCount: Int,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (carryoverCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = colors.panelBackgroundStrong.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "已承接通话前 $carryoverCount 条聊天内容",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }

            messages.forEachIndexed { index, message ->
                val isLatest = index == messages.lastIndex
                VideoCallFloatingMessageCard(
                    message = message,
                    isLatest = isLatest,
                    colors = colors,
                    backdropState = backdropState,
                )
            }
        }
    }
}

@Composable
private fun VideoCallFloatingMessageCard(
    message: RoleplayMessageUiModel,
    isLatest: Boolean,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
) {
    val speakerLabel = remember(message.speaker, message.speakerName) {
        resolveVideoCallSpeakerLabel(message)
    }
    val cardWidthFraction = if (isLatest) 0.92f else 0.84f
    val bubbleAlpha = if (isLatest) 0.88f else 0.70f
    val horizontalArrangement = when (message.speaker) {
        RoleplaySpeaker.USER -> Arrangement.End
        RoleplaySpeaker.CHARACTER -> Arrangement.Start
        RoleplaySpeaker.NARRATOR,
        RoleplaySpeaker.SYSTEM,
        -> Arrangement.Center
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
    ) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.fillMaxWidth(cardWidthFraction),
            shape = RoundedCornerShape(28.dp),
            blurRadius = 16.dp,
            overlayColor = colors.panelBackgroundStrong.copy(alpha = bubbleAlpha),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (speakerLabel.isNotBlank()) {
                    Text(
                        text = speakerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = message.content.trim().ifBlank { message.copyText.trim() },
                    style = if (isLatest) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = colors.textPrimary,
                )
            }
        }
    }
}

private fun resolveVideoCallSpeakerLabel(message: RoleplayMessageUiModel): String {
    val explicitName = message.speakerName.trim()
    if (explicitName.isNotBlank()) {
        return explicitName
    }
    return when (message.speaker) {
        RoleplaySpeaker.USER -> "你"
        RoleplaySpeaker.CHARACTER -> "TA"
        RoleplaySpeaker.NARRATOR -> "画面"
        RoleplaySpeaker.SYSTEM -> ""
    }
}

@Composable
private fun VideoCallInputBar(
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        blurRadius = 18.dp,
        overlayColor = colors.panelBackgroundStrong.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.characterAccent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (isSending) {
                            onCancelSending()
                        } else if (input.isNotBlank()) {
                            onSend()
                        }
                    },
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isBlank()) {
                            Text(
                                text = "直接继续聊...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            NarraIconButton(
                onClick = {
                    if (isSending) {
                        onCancelSending()
                    } else {
                        onSend()
                    }
                },
                enabled = isSending || input.isNotBlank(),
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isSending) {
                            Color.White.copy(alpha = 0.16f)
                        } else {
                            colors.characterAccent.copy(alpha = 0.96f)
                        },
                        shape = CircleShape,
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.White.copy(alpha = 0.72f),
                ),
            ) {
                Icon(
                    imageVector = if (isSending) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isSending) "取消发送" else "发送消息",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoCallMainPortrait(
    scenario: RoleplayScenario,
    characterName: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val requestSize = with(density) {
        IntSize(
            width = 1080,
            height = 1920,
        )
    }
    val imageState = rememberUserProfileAvatarState(
        avatarUri = scenario.characterPortraitUri,
        avatarUrl = scenario.characterPortraitUrl,
        requestSize = requestSize,
    )
    Box(modifier = modifier) {
        if (imageState.loadState == UserAvatarLoadState.Success && imageState.imageBitmap != null) {
            Image(
                bitmap = imageState.imageBitmap,
                contentDescription = characterName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.30f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun VideoCallFloatingPreview(
    name: String,
    avatarUri: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val requestSize = with(density) {
        IntSize(
            width = 240,
            height = 320,
        )
    }
    val imageState = rememberUserProfileAvatarState(
        avatarUri = avatarUri,
        avatarUrl = avatarUrl,
        requestSize = requestSize,
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFAEDCFF).copy(alpha = 0.94f))
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFB7E3FF)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageState.loadState == UserAvatarLoadState.Success && imageState.imageBitmap != null) {
                Image(
                    bitmap = imageState.imageBitmap,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = name.take(1).ifBlank { "你" },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun rememberVideoCallElapsedText(
    activeVideoCallStartedAt: Long,
): String {
    var now by remember(activeVideoCallStartedAt) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(activeVideoCallStartedAt) {
        if (activeVideoCallStartedAt <= 0L) {
            return@LaunchedEffect
        }
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val totalSeconds = if (activeVideoCallStartedAt > 0L) {
        ((now - activeVideoCallStartedAt).coerceAtLeast(0L)) / 1000L
    } else {
        0L
    }
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
