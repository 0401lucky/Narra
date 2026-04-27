package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

@Composable
fun RoleplayScenarioListScreen(
    scenarios: List<RoleplayScenario>,
    assistants: List<Assistant>,
    continuingScenarioIds: Set<String>,
    onAddScenario: () -> Unit,
    onEditScenario: (String) -> Unit,
    onPlayScenario: (String) -> Unit,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val assistantsById = assistants.associateBy { it.id }
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

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "聊天管理",
                subtitle = "管理聊天资料与独立会话",
                onNavigateBack = onNavigateBack,
                actionLabel = "新建",
                onAction = onAddScenario,
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (scenarios.isEmpty()) {
                item { EmptyScenarioCard(onAddScenario = onAddScenario) }
            } else {
                items(scenarios, key = { it.id }) { scenario ->
                    RoleplayScenarioCard(
                        scenario = scenario,
                        assistant = assistantsById[scenario.assistantId],
                        hasExistingSession = scenario.id in continuingScenarioIds,
                        onEdit = { onEditScenario(scenario.id) },
                        onPlay = { onPlayScenario(scenario.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyScenarioCard(onAddScenario: () -> Unit) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = palette.accentSoft,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            SettingsStatusPill(
                text = "从零开始",
                containerColor = palette.surfaceTint,
                contentColor = palette.body,
            )
            Text(
                text = "还没有聊天资料",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Text(
                text = "创建一份聊天资料，绑定角色、背景和用户形象，即可进入沉浸式对话。",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = palette.body,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            NarraButton(
                onClick = onAddScenario,
                modifier = Modifier.padding(top = 4.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "新建聊天资料",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleplayScenarioCard(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    hasExistingSession: Boolean,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val hasBackground = scenario.backgroundUri.isNotBlank()
    val backgroundState = rememberUserProfileAvatarState(
        avatarUri = scenario.backgroundUri.takeIf { it.isNotBlank() }.orEmpty(),
        avatarUrl = "",
        requestSize = IntSize(720, 480),
    )
    val bitmapReady = hasBackground &&
        backgroundState.loadState == UserAvatarLoadState.Success &&
        backgroundState.imageBitmap != null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(28.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 统一 140dp header：有图走图 + scrim，无图走 accentSoft 渐变 + 水印，保证卡片高度与节奏稳定。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            ) {
                if (bitmapReady) {
                    Image(
                        bitmap = backgroundState.imageBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.55f),
                                    ),
                                ),
                            ),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        palette.accentSoft,
                                        palette.surface,
                                    ),
                                ),
                            ),
                    )
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = palette.accent.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 18.dp)
                            .size(88.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = scenario.title.ifBlank { "未命名聊天" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (bitmapReady) Color.White else palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = assistant?.name?.ifBlank { "默认助手" } ?: "默认助手",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (bitmapReady) Color.White.copy(alpha = 0.82f) else palette.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasExistingSession) {
                        SettingsStatusPill(
                            text = "可继续",
                            containerColor = palette.accent,
                            contentColor = palette.accentOnStrong,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (bitmapReady) {
                            Color.Black.copy(alpha = 0.35f)
                        } else {
                            palette.surface.copy(alpha = 0.85f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (bitmapReady) Color.White.copy(alpha = 0.4f) else palette.border,
                        ),
                        onClick = onEdit,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑聊天资料",
                                tint = if (bitmapReady) Color.White else palette.title,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = scenario.description.ifBlank {
                        "尚未填写聊天背景补充，进入后会直接按角色设定展开。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.body,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SettingsStatusPill(
                        text = scenario.interactionMode.displayName,
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                    if (scenario.longformModeEnabled &&
                        scenario.interactionMode != RoleplayInteractionMode.OFFLINE_LONGFORM
                    ) {
                        SettingsStatusPill(
                            text = "长文",
                            containerColor = palette.surfaceTint,
                            contentColor = palette.body,
                        )
                    }
                    if (scenario.enableDeepImmersion) {
                        SettingsStatusPill(
                            text = "深度沉浸",
                            containerColor = palette.surfaceTint,
                            contentColor = palette.body,
                        )
                    }
                    if (scenario.enableNetMeme) {
                        SettingsStatusPill(
                            text = "网络热梗",
                            containerColor = palette.surfaceTint,
                            contentColor = palette.body,
                        )
                    }
                }

                NarraButton(
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (hasExistingSession) "继续聊天" else "开始聊天",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
