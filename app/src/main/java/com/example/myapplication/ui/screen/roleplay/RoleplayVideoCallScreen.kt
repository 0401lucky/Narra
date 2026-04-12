package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplayInputBar
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.RoleplayMessageItem
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import kotlinx.coroutines.delay

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
    val visibleMessages = remember(messages, activeVideoCallStartedAt) {
        messages.filter { message ->
            message.contentType != RoleplayContentType.SYSTEM &&
                activeVideoCallStartedAt > 0L &&
                message.createdAt >= activeVideoCallStartedAt
        }
    }
    val listState = rememberLazyListState()
    val shouldStickToBottom by remember(listState, visibleMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (visibleMessages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
            }
        }
    }

    LaunchedEffect(visibleMessages.firstOrNull()?.sourceMessageId, visibleMessages.firstOrNull()?.createdAt) {
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.content, visibleMessages.lastOrNull()?.isStreaming) {
        if (visibleMessages.isNotEmpty() && shouldStickToBottom) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    val statusBarTopPadding = if (settings.roleplayImmersiveMode.storageValue == "none") {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val elapsedText = rememberVideoCallElapsedText(activeVideoCallStartedAt)

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
                            text = "通话已接通，你先发一句，TA 才会接着回你。",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = visibleMessages,
                            key = { _, item -> "${item.sourceMessageId}-${item.createdAt}-${item.contentType}-${item.copyText.hashCode()}" },
                        ) { _, message ->
                            RoleplayMessageItem(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                onRetryTurn = {},
                                onEditUserMessage = {},
                                onConfirmTransferReceipt = {},
                                bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoleplayInputBar(
                    colors = colors,
                    backdropState = backdropState,
                    input = input,
                    inputFocusToken = inputFocusToken,
                    isSending = isSending,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onCancel = onCancelSending,
                    onOpenSpecialPlay = {},
                    showActionButton = false,
                    showExpandButton = false,
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
