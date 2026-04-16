package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
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

    androidx.compose.material3.Scaffold(
        topBar = {
            SettingsTopBar(
                title = "沉浸扮演",
                subtitle = "独立场景与独立会话",
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = "把现有角色与上下文系统变成视觉小说式体验",
                    summary = "每个场景都有自己的背景、立绘覆写和独立会话绑定；同一个助手也可以被多个场景复用。",
                )
            }

            if (scenarios.isEmpty()) {
                item {
                    SettingsHintCard(
                        title = "还没有剧情场景",
                        body = "先创建一个场景，绑定角色、背景和用户形象，再进入沉浸式对话页。",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
                item {
                    AnimatedSettingButton(
                        text = "创建第一个场景",
                        onClick = onAddScenario,
                        enabled = true,
                        isPrimary = true,
                    )
                }
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
private fun RoleplayScenarioCard(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    hasExistingSession: Boolean,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistantAvatar(
                    name = assistant?.name.orEmpty().ifBlank { "角色" },
                    iconName = assistant?.iconName.orEmpty().ifBlank { "auto_stories" },
                    avatarUri = assistant?.avatarUri.orEmpty(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scenario.title.ifBlank { "未命名场景" },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = scenario.description.ifBlank {
                            "尚未填写场景描述，进入后会直接按角色设定展开。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsStatusPill(
                    text = assistant?.name?.ifBlank { "默认助手" } ?: "默认助手",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                if (scenario.backgroundUri.isNotBlank()) {
                    SettingsStatusPill(
                        text = "已设背景",
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (scenario.enableRoleplayProtocol) {
                    SettingsStatusPill(
                        text = "协议输出",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
                if (scenario.enableNarration) {
                    SettingsStatusPill(
                        text = if (scenario.interactionMode == com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE) {
                            "启用心声"
                        } else {
                            "启用旁白"
                        },
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (hasExistingSession) {
                    SettingsStatusPill(
                        text = "可继续",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEdit),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surfaceTint,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(
                            text = "编辑",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPlay),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.accentSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.accent)
                        Text(
                            text = if (hasExistingSession) "继续" else "开始",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
