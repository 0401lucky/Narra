package com.example.myapplication.ui.screen.roleplay

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayInteractionSpec
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.toInteractionSpec
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsGroupDivider
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

@Composable
fun RoleplayScenarioEditScreen(
    scenario: RoleplayScenario?,
    settings: AppSettings,
    assistants: List<Assistant>,
    onSave: (RoleplayScenario) -> Unit,
    onDelete: ((String) -> Unit)?,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    val snackbarHostState = remember { SnackbarHostState() }
    val isNew = scenario == null
    val baseScenario = scenario ?: RoleplayScenario()

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

    var title by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.title) }
    var description by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.description) }
    var assistantId by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.assistantId) }
    var backgroundUri by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.backgroundUri) }
    var userDisplayNameOverride by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userDisplayNameOverride) }
    var userPersonaOverride by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userPersonaOverride) }
    var userPortraitUri by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userPortraitUri) }
    var userPortraitUrl by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userPortraitUrl) }
    var characterDisplayNameOverride by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterDisplayNameOverride) }
    var characterPortraitUri by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterPortraitUri) }
    var characterPortraitUrl by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterPortraitUrl) }
    var openingNarration by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.openingNarration) }
    val normalizedSpec = remember(baseScenario.id) { baseScenario.toInteractionSpec().normalized() }
    var interactionMode by rememberSaveable(baseScenario.id) {
        mutableStateOf(normalizedSpec.interactionMode)
    }
    // enableNarration / enableDeepImmersion 仅在沉浸设置页"场景插件"区维护，
    // 这里直接透传基线值以免重复 state 与老版本覆盖。
    val enableNarration = baseScenario.enableNarration
    var enableRoleplayProtocol by rememberSaveable(baseScenario.id) {
        mutableStateOf(normalizedSpec.enableRoleplayProtocol)
    }
    var longformModeEnabled by rememberSaveable(baseScenario.id) {
        mutableStateOf(normalizedSpec.longformModeEnabled)
    }
    var autoHighlightSpeaker by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.autoHighlightSpeaker) }
    val enableDeepImmersion = baseScenario.enableDeepImmersion
    val isOnlinePhoneMode = interactionMode == RoleplayInteractionMode.ONLINE_PHONE

    fun applyInteractionSpec(transform: (RoleplayInteractionSpec) -> RoleplayInteractionSpec) {
        val next = transform(
            RoleplayInteractionSpec(
                interactionMode = interactionMode,
                longformModeEnabled = longformModeEnabled,
                enableRoleplayProtocol = enableRoleplayProtocol,
            ),
        )
        interactionMode = next.interactionMode
        longformModeEnabled = next.longformModeEnabled
        enableRoleplayProtocol = next.enableRoleplayProtocol
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        backgroundUri = uri.toString()
    }
    val userPortraitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        userPortraitUri = uri.toString()
        userPortraitUrl = ""
    }
    val characterPortraitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        characterPortraitUri = uri.toString()
        characterPortraitUrl = ""
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (isNew) "新建场景" else "编辑场景",
                subtitle = "配置独立场景",
                onNavigateBack = onNavigateBack,
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
            item {
                SettingsSectionHeader(
                    title = "场景基础",
                    description = "把角色人格交给 Assistant，把具体故事现场放在这里。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("场景标题") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("场景描述") },
                            minLines = 3,
                            maxLines = 5,
                            supportingText = { Text("这段文本会直接进入 system prompt。") },
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "绑定角色",
                    description = "场景复用现有助手设定，Assistant 决定人格和长期记忆。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        assistants.forEachIndexed { index, assistant ->
                            AssistantPickRow(
                                assistant = assistant,
                                selected = assistant.id == assistantId,
                                onClick = { assistantId = assistant.id },
                            )
                            if (index != assistants.lastIndex) {
                                SettingsGroupDivider()
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "视觉资源",
                    description = "背景用于烘托场景，用户与角色立绘可以覆盖默认头像。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        ScenarioImagePickerCard(
                            title = "背景图",
                            subtitle = "用于列表卡片封面与剧情内场景背景。",
                            value = backgroundUri,
                            onPick = { backgroundLauncher.launch(arrayOf("image/*")) },
                            onClear = { backgroundUri = "" },
                        )
                        ScenarioImagePickerCard(
                            title = "用户立绘",
                            subtitle = "本地图片优先；填写 http/https 链接时使用链接图片。",
                            value = userPortraitUri.ifBlank { userPortraitUrl },
                            onPick = { userPortraitLauncher.launch(arrayOf("image/*")) },
                            onClear = {
                                userPortraitUri = ""
                                userPortraitUrl = ""
                            },
                            urlValue = userPortraitUrl,
                            onUrlChange = { userPortraitUrl = it },
                            outlineColors = outlineColors,
                        )
                        ScenarioImagePickerCard(
                            title = "角色立绘",
                            subtitle = "本地图片优先；填写 http/https 链接时使用链接图片。",
                            value = characterPortraitUri.ifBlank { characterPortraitUrl },
                            onPick = { characterPortraitLauncher.launch(arrayOf("image/*")) },
                            onClear = {
                                characterPortraitUri = ""
                                characterPortraitUrl = ""
                            },
                            urlValue = characterPortraitUrl,
                            onUrlChange = { characterPortraitUrl = it },
                            outlineColors = outlineColors,
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "显示名与开场",
                    description = "显示名只影响 RP 界面，不会改动原 Assistant。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = userDisplayNameOverride,
                            onValueChange = { userDisplayNameOverride = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户显示名覆写") },
                            singleLine = true,
                            supportingText = {
                                Text("留空则使用 ${settings.resolvedUserDisplayName()}")
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = characterDisplayNameOverride,
                            onValueChange = { characterDisplayNameOverride = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("角色显示名覆写") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = userPersonaOverride,
                            onValueChange = { userPersonaOverride = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户人设覆写") },
                            minLines = 4,
                            maxLines = 8,
                            supportingText = {
                                Text("留空则回退到全局个人资料中的用户人设。")
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = openingNarration,
                            onValueChange = { openingNarration = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    if (isOnlinePhoneMode) "开场旁白" else "开场旁白",
                                )
                            },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "交互模式",
                    description = "决定剧情的基础表达形态，三选一。",
                )
            }
            item {
                SettingsGroup {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RoleplayInteractionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = interactionMode == mode,
                                onClick = {
                                    applyInteractionSpec { it.withInteractionMode(mode) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                label = { Text(mode.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accentSoft,
                                    selectedLabelColor = palette.accent,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = "演出开关",
                    description = "补充输出协议、长文与焦点高亮等行为。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        SwitchRow(
                            title = "长文小说模式",
                            subtitle = "开启后，助手会输出更接近长篇小说的纯长文段落，适合重剧情用户。",
                            value = longformModeEnabled,
                            onValueChange = { enabled ->
                                applyInteractionSpec { it.withLongform(enabled) }
                            },
                        )
                        SettingsGroupDivider()
                        SwitchRow(
                            title = "启用 RP 协议输出",
                            subtitle = if (longformModeEnabled) {
                                "长文小说模式下会忽略这项设置。"
                            } else if (interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                                "线上模式默认走 JSON 数组协议，并兼容旧 thought / narration 历史。"
                            } else {
                                "开启后要求模型输出 narration/dialogue 标签，结构更稳定。"
                            },
                            value = enableRoleplayProtocol,
                            onValueChange = { enabled ->
                                applyInteractionSpec { it.withRoleplayProtocol(enabled) }
                            },
                        )
                        SettingsGroupDivider()
                        SwitchRow(
                            title = "自动高亮当前说话方",
                            subtitle = "根据最近一轮发言和回复状态，自动强调当前剧情焦点角色。",
                            value = autoHighlightSpeaker,
                            onValueChange = { autoHighlightSpeaker = it },
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedSettingButton(
                            text = if (isNew) "创建场景" else "保存场景",
                            onClick = {
                                onSave(
                                    baseScenario.copy(
                                        title = title.trim(),
                                        description = description.trim(),
                                        assistantId = assistantId,
                                        backgroundUri = backgroundUri.trim(),
                                        userDisplayNameOverride = userDisplayNameOverride.trim(),
                                        userPersonaOverride = userPersonaOverride.replace("\r\n", "\n").trim(),
                                        userPortraitUri = userPortraitUri.trim(),
                                        userPortraitUrl = userPortraitUrl.trim(),
                                        characterDisplayNameOverride = characterDisplayNameOverride.trim(),
                                        characterPortraitUri = characterPortraitUri.trim(),
                                        characterPortraitUrl = characterPortraitUrl.trim(),
                                        openingNarration = openingNarration.trim(),
                                        interactionMode = interactionMode,
                                        enableNarration = enableNarration,
                                        enableRoleplayProtocol = enableRoleplayProtocol,
                                        longformModeEnabled = longformModeEnabled,
                                        autoHighlightSpeaker = autoHighlightSpeaker,
                                        enableDeepImmersion = enableDeepImmersion,
                                    ),
                                )
                            },
                            enabled = title.isNotBlank(),
                            isPrimary = true,
                        )
                        if (!isNew && onDelete != null) {
                            Text(
                                text = "谨慎操作",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                            )
                            AnimatedSettingButton(
                                text = "删除场景",
                                onClick = { onDelete(baseScenario.id) },
                                enabled = true,
                                isPrimary = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantPickRow(
    assistant: Assistant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AssistantAvatar(
            name = assistant.name.ifBlank { "助手" },
            iconName = assistant.iconName.ifBlank { "auto_stories" },
            avatarUri = assistant.avatarUri,
            size = 40.dp,
            containerColor = palette.subtleChip,
            contentColor = palette.subtleChipContent,
            cornerRadius = 12.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = assistant.name.ifBlank { "默认助手" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
                maxLines = 1,
            )
            Text(
                text = assistant.description.ifBlank { "未填写助手描述" },
                style = MaterialTheme.typography.labelMedium,
                color = palette.body,
                maxLines = 2,
            )
        }
        if (selected) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = palette.accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中",
                        tint = palette.accentOnStrong,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, palette.border),
            ) {}
        }
    }
}

@Composable
private fun ScenarioImagePickerCard(
    title: String,
    subtitle: String,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    urlValue: String? = null,
    onUrlChange: ((String) -> Unit)? = null,
    outlineColors: androidx.compose.material3.TextFieldColors? = null,
) {
    val palette = rememberSettingsPalette()
    val density = LocalDensity.current
    val previewRequestSize = with(density) {
        IntSize(
            width = 96.dp.roundToPx().coerceAtLeast(1),
            height = 96.dp.roundToPx().coerceAtLeast(1),
        )
    }
    val imageState = rememberUserProfileAvatarState(
        avatarUri = value.takeIf { it.startsWith("content://") || it.startsWith("file://") }.orEmpty(),
        avatarUrl = value.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty(),
        requestSize = previewRequestSize,
    )
    val hasValue = value.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.title,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = palette.body,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(96.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surfaceTint,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
                ) {
                    if (imageState.loadState == UserAvatarLoadState.Success &&
                        imageState.imageBitmap != null
                    ) {
                        Image(
                            bitmap = imageState.imageBitmap,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (hasValue) "已选择" else "未设置",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.body,
                            )
                        }
                    }
                }
                if (hasValue) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp),
                        shape = CircleShape,
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.border),
                        onClick = onClear,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空$title",
                                tint = palette.body,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                AnimatedSettingButton(
                    text = if (hasValue) "更换图片" else "选择图片",
                    onClick = onPick,
                    enabled = true,
                    isPrimary = false,
                )
            }
        }
        if (onUrlChange != null && outlineColors != null) {
            OutlinedTextField(
                value = urlValue.orEmpty(),
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("$title 链接") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = outlineColors,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = value,
            onCheckedChange = onValueChange,
        )
    }
}
