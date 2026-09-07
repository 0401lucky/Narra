package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.UserProfileAvatar
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Video
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
    ApplyRoleplaySurfaceSystemBars(
        immersiveMode = settings.roleplayImmersiveMode,
    )
    val colorScheme = MaterialTheme.colorScheme
    val characterName = remember(scenario.characterDisplayNameOverride, assistant?.name) {
        scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }
    val userPersona = remember(scenario, settings) {
        RoleplayConversationSupport.resolveUserPersona(scenario, settings)
    }
    val userName = userPersona.displayName.ifBlank { "你" }
    val presentationState = remember(messages, activeVideoCallStartedAt) {
        buildVideoCallPresentationState(
            messages = messages,
            activeVideoCallStartedAt = activeVideoCallStartedAt,
        )
    }
    val visibleMessages = presentationState.visibleMessages
    val transcriptState = rememberLazyListState()
    val immersiveMode = settings.roleplayImmersiveMode
    val statusBarTopPadding = if (immersiveMode == RoleplayImmersiveMode.HIDE_SYSTEM_BARS) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val elapsedText = rememberVideoCallElapsedText(activeVideoCallStartedAt)
    val portraitState = rememberUserProfileAvatarState(
        avatarUri = scenario.characterPortraitUri,
        avatarUrl = scenario.characterPortraitUrl,
        requestSize = IntSize(1080, 1600),
    )
    val portrait = portraitState.imageBitmap.takeIf {
        portraitState.loadState == UserAvatarLoadState.Success
    }
    val hasSceneMedia = backdropState.hasImage || portrait != null

    LaunchedEffect(visibleMessages.lastOrNull()?.sourceMessageId, visibleMessages.lastOrNull()?.content) {
        if (visibleMessages.isNotEmpty()) {
            transcriptState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .testTag("video_call_screen"),
    ) {
        backdropState.imageBitmap?.let { background ->
            Image(
                bitmap = background,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().roleplayImmersivePadding(immersiveMode),
                contentScale = ContentScale.Crop,
            )
        }
        if (portrait != null) {
            Image(
                bitmap = portrait,
                contentDescription = characterName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTopPadding + 64.dp, bottom = 88.dp)
                    .testTag("video_call_portrait"),
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(
                    if (immersiveMode == RoleplayImmersiveMode.HIDE_SYSTEM_BARS) {
                        Modifier
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasSceneMedia) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(colorScheme.background, colorScheme.background.copy(alpha = 0.9f)),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = statusBarTopPadding + 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = characterName,
                        modifier = Modifier.testTag("video_call_character_name"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Lucide.Video,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isVideoCallActive) "视频通话  $elapsedText" else "正在接通...",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                UserProfileAvatar(
                    displayName = userName,
                    avatarUri = userPersona.avatarUri,
                    avatarUrl = userPersona.avatarUrl,
                    modifier = Modifier.size(40.dp).testTag("video_call_user_avatar"),
                    requestSize = IntSize(160, 160),
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val compact = maxHeight < 300.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    if (portrait == null && !compact) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(if (visibleMessages.isEmpty()) 1f else 0.9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoCallCharacterAvatar(
                                characterName = characterName,
                                assistant = assistant,
                                size = 128.dp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (portrait != null || compact) 1f else 1.3f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (!isVideoCallActive && activeVideoCallStartedAt <= 0L) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (visibleMessages.isEmpty()) {
                            Text(
                                text = "通话已接通",
                                modifier = Modifier
                                    .padding(24.dp)
                                    .then(
                                        if (hasSceneMedia) {
                                            Modifier.background(colorScheme.surface, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            VideoCallTranscript(
                                messages = visibleMessages,
                                state = transcriptState,
                                hasSceneMedia = hasSceneMedia,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }

            VideoCallInputBar(
                input = input,
                inputFocusToken = inputFocusToken,
                isSending = isSending,
                onInputChange = onInputChange,
                onSend = onSend,
                onCancelSending = onCancelSending,
                onHangup = onHangup,
            )
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

@Composable
private fun VideoCallTranscript(
    messages: List<RoleplayMessageUiModel>,
    state: androidx.compose.foundation.lazy.LazyListState,
    hasSceneMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = state,
        reverseLayout = true,
        modifier = modifier.testTag("video_call_transcript"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        items(messages.asReversed(), key = { it.uiItemId }) { message ->
            VideoCallMessage(message = message, hasSceneMedia = hasSceneMedia)
        }
    }
}

@Composable
private fun VideoCallMessage(
    message: RoleplayMessageUiModel,
    hasSceneMedia: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isThought = message.contentType == RoleplayContentType.THOUGHT
    val isUser = message.speaker == RoleplaySpeaker.USER
    val speakerLabel = remember(message.speaker, message.speakerName, message.contentType) {
        resolveVideoCallSpeakerLabel(message)
    }
    val bodyStyle = MaterialTheme.typography.bodyLarge.let { baseStyle ->
        if (isThought) {
            baseStyle.copy(fontStyle = FontStyle.Italic)
        } else {
            baseStyle
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when {
            isThought -> Alignment.Center
            isUser -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        },
    ) {
        Surface(
            modifier = Modifier.widthIn(max = maxWidth * 0.88f),
            shape = RoundedCornerShape(8.dp),
            color = when {
                isThought && !hasSceneMedia -> Color.Transparent
                isUser -> colorScheme.secondaryContainer
                hasSceneMedia -> colorScheme.surface
                else -> colorScheme.surfaceContainerLow
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (speakerLabel.isNotBlank()) {
                    Text(
                        text = speakerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = message.content.trim().ifBlank { message.copyText.trim() },
                    style = bodyStyle,
                    color = if (isUser) colorScheme.onSecondaryContainer else colorScheme.onSurface,
                )
            }
        }
    }
}

internal fun resolveVideoCallSpeakerLabel(message: RoleplayMessageUiModel): String {
    val explicitName = message.speakerName.trim()
    if (message.contentType == RoleplayContentType.THOUGHT) {
        val ownerName = explicitName.ifBlank {
            when (message.speaker) {
                RoleplaySpeaker.USER -> "你"
                RoleplaySpeaker.CHARACTER -> "TA"
                RoleplaySpeaker.NARRATOR -> "画面"
                RoleplaySpeaker.SYSTEM -> ""
            }
        }
        return ownerName
            .takeIf { it.isNotBlank() }
            ?.let { name -> if (name.endsWith("心声")) name else "${name}心声" }
            .orEmpty()
    }
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
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onHangup: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(inputFocusToken) {
        if (inputFocusToken > 0L && !isSending) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("video_call_controls"),
        color = colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .focusRequester(focusRequester)
                        .testTag("video_call_input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                    cursorBrush = SolidColor(colorScheme.primary),
                    maxLines = 3,
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (input.isBlank()) {
                                Text(
                                    text = "说点什么...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                NarraIconButton(
                    onClick = {
                        if (isSending) onCancelSending() else onSend()
                    },
                    enabled = isSending || input.isNotBlank(),
                    modifier = Modifier.size(48.dp).testTag("video_call_send"),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = colorScheme.primary,
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                ) {
                    Icon(
                        imageVector = if (isSending) Lucide.Square else Lucide.Send,
                        contentDescription = if (isSending) "取消发送" else "发送消息",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            NarraIconButton(
                onClick = onHangup,
                modifier = Modifier
                    .size(56.dp)
                    .background(colorScheme.error, CircleShape)
                    .testTag("video_call_hangup"),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = colorScheme.onError,
                ),
            ) {
                Icon(
                    imageVector = Lucide.PhoneOff,
                    contentDescription = "挂断视频通话",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoCallCharacterAvatar(
    characterName: String,
    assistant: Assistant?,
    size: Dp,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    UserProfileAvatar(
        displayName = characterName,
        avatarUri = assistant?.avatarUri.orEmpty(),
        avatarUrl = "",
        modifier = Modifier.size(size).testTag("video_call_character_avatar"),
        requestSize = IntSize(sizePx, sizePx),
        textStyle = MaterialTheme.typography.headlineLarge,
    )
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
