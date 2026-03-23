package com.example.myapplication.ui.screen.roleplay

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
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
    var userPortraitUri by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userPortraitUri) }
    var userPortraitUrl by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.userPortraitUrl) }
    var characterDisplayNameOverride by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterDisplayNameOverride) }
    var characterPortraitUri by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterPortraitUri) }
    var characterPortraitUrl by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.characterPortraitUrl) }
    var openingNarration by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.openingNarration) }
    var enableNarration by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.enableNarration) }
    var enableRoleplayProtocol by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.enableRoleplayProtocol) }
    var longformModeEnabled by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.longformModeEnabled) }
    var autoHighlightSpeaker by rememberSaveable(baseScenario.id) { mutableStateOf(baseScenario.autoHighlightSpeaker) }

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
                subtitle = "RP 剧情容器",
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
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        assistants.forEach { assistant ->
                            FilterChip(
                                selected = assistant.id == assistantId,
                                onClick = { assistantId = assistant.id },
                                label = { Text(assistant.name.ifBlank { "默认助手" }) },
                            )
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ImagePickerRow(
                            title = "背景图",
                            value = backgroundUri,
                            onPick = { backgroundLauncher.launch(arrayOf("image/*")) },
                            onClear = { backgroundUri = "" },
                        )
                        ImagePickerRow(
                            title = "用户立绘",
                            value = userPortraitUri.ifBlank { userPortraitUrl },
                            onPick = { userPortraitLauncher.launch(arrayOf("image/*")) },
                            onClear = {
                                userPortraitUri = ""
                                userPortraitUrl = ""
                            },
                        )
                        OutlinedTextField(
                            value = userPortraitUrl,
                            onValueChange = {
                                userPortraitUrl = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户立绘链接") },
                            singleLine = true,
                            supportingText = { Text("填写 http/https 链接时会优先使用链接图片") },
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                        )
                        ImagePickerRow(
                            title = "角色立绘",
                            value = characterPortraitUri.ifBlank { characterPortraitUrl },
                            onPick = { characterPortraitLauncher.launch(arrayOf("image/*")) },
                            onClear = {
                                characterPortraitUri = ""
                                characterPortraitUrl = ""
                            },
                        )
                        OutlinedTextField(
                            value = characterPortraitUrl,
                            onValueChange = {
                                characterPortraitUrl = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("角色立绘链接") },
                            singleLine = true,
                            supportingText = { Text("填写 http/https 链接时会优先使用链接图片") },
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
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
                            value = openingNarration,
                            onValueChange = { openingNarration = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("开场旁白") },
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
                    title = "演出开关",
                    description = "长文模式决定是否走小说流；普通模式下才使用 RP 标签协议。",
                )
            }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SwitchRow(
                            title = "长文小说模式",
                            subtitle = "开启后，助手会输出更接近长篇小说的纯长文段落，适合重剧情用户。",
                            value = longformModeEnabled,
                            onValueChange = { longformModeEnabled = it },
                        )
                        SwitchRow(
                            title = "启用旁白",
                            subtitle = "关闭后会尽量减少独立旁白段落，更偏纯对白推进。",
                            value = enableNarration,
                            onValueChange = { enableNarration = it },
                        )
                        SwitchRow(
                            title = "启用 RP 协议输出",
                            subtitle = if (longformModeEnabled) {
                                "长文小说模式下会忽略这项设置。"
                            } else {
                                "开启后要求模型输出 narration/dialogue 标签，结构更稳定。"
                            },
                            value = enableRoleplayProtocol,
                            onValueChange = { enableRoleplayProtocol = it },
                        )
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
                                        userPortraitUri = userPortraitUri.trim(),
                                        userPortraitUrl = userPortraitUrl.trim(),
                                        characterDisplayNameOverride = characterDisplayNameOverride.trim(),
                                        characterPortraitUri = characterPortraitUri.trim(),
                                        characterPortraitUrl = characterPortraitUrl.trim(),
                                        openingNarration = openingNarration.trim(),
                                        enableNarration = enableNarration,
                                        enableRoleplayProtocol = enableRoleplayProtocol,
                                        longformModeEnabled = longformModeEnabled,
                                        autoHighlightSpeaker = autoHighlightSpeaker,
                                    ),
                                )
                            },
                            enabled = title.isNotBlank(),
                            isPrimary = true,
                        )
                        if (!isNew && onDelete != null) {
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
private fun ImagePickerRow(
    title: String,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val imageState = rememberUserProfileAvatarState(
        avatarUri = value.takeIf { it.startsWith("content://") || it.startsWith("file://") }.orEmpty(),
        avatarUrl = value.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceTint,
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
                            text = if (value.isBlank()) "未设置" else "已选择",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedSettingButton(
                    text = "选择图片",
                    onClick = onPick,
                    enabled = true,
                    isPrimary = false,
                )
                AnimatedSettingButton(
                    text = "清空",
                    onClick = onClear,
                    enabled = value.isNotBlank(),
                    isPrimary = false,
                )
            }
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
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
