package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassChip
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.screen.chat.ModelPickerSheet
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplaySettingsScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    latestPromptDebugDump: String,
    onOpenReadingMode: () -> Unit,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateRoleplayLongformTargetChars: (Int) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onRestartSession: () -> Unit,
    onResetSession: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val backdropState = rememberImmersiveBackdropState(scenario?.backgroundUri.orEmpty())
    val palette = backdropState.palette
    val outlineColors = rememberSettingsOutlineColors()
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmResetDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmRestartDialog by rememberSaveable { mutableStateOf(false) }
    var longformCharsText by rememberSaveable(settings.roleplayLongformTargetChars) {
        mutableStateOf(settings.roleplayLongformTargetChars.toString())
    }
    val scenarioTitle = scenario?.title?.trim().orEmpty().ifBlank { "沉浸扮演" }
    val characterName = scenario?.characterDisplayNameOverride
        ?.trim()
        .orEmpty()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "角色" }

    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Scaffold(
            topBar = {
                SettingsTopBar(
                    title = "沉浸设置",
                    subtitle = scenarioTitle,
                    onNavigateBack = onNavigateBack,
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = SettingsScreenPadding,
                    top = 4.dp,
                    end = SettingsScreenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                RoleplaySettingsHero(
                    backdropState = backdropState,
                    scenarioTitle = scenarioTitle,
                    characterName = characterName,
                    currentModel = currentModel,
                    contextStatus = contextStatus,
                    onOpenReadingMode = onOpenReadingMode,
                )
            }

            item {
                ImmersiveSettingsCard(backdropState) {
                    RoleplaySettingSwitchRow(
                        palette = palette,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "显示身份条",
                        supportingText = "控制顶部用户和角色卡片是否显示，默认保留人物感。",
                        checked = settings.showRoleplayPresenceStrip,
                        onCheckedChange = onUpdateShowRoleplayPresenceStrip,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = palette.panelBorder.copy(alpha = 0.44f),
                    )
                    RoleplaySettingSwitchRow(
                        palette = palette,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ViewAgenda,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "显示状态条",
                        supportingText = "控制身份条下方的剧情摘要、记忆和世界书状态胶囊。",
                        checked = settings.showRoleplayStatusStrip,
                        onCheckedChange = onUpdateShowRoleplayStatusStrip,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = palette.panelBorder.copy(alpha = 0.44f),
                    )
                    RoleplaySettingSwitchRow(
                        palette = palette,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "显示 AI 帮写",
                        supportingText = "控制沉浸式页面底部的输入建议区和生成入口。",
                        checked = settings.showRoleplayAiHelper,
                        onCheckedChange = onUpdateShowRoleplayAiHelper,
                    )
                }
            }

            item {
                ImmersiveSettingsCard(backdropState) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ImmersiveGlassSurface(
                                backdropState = backdropState,
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                blurRadius = 16.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = palette.onGlass,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "长剧情默认字数",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.onGlass,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "仅在长剧情模式下生效，当前默认目标为约 ${settings.roleplayLongformTargetChars} 字。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onGlassMuted,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = longformCharsText,
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(4)
                                longformCharsText = digits
                                digits.toIntOrNull()?.let { value ->
                                    onUpdateRoleplayLongformTargetChars(value)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            label = { Text("默认字数") },
                            supportingText = { Text("建议范围 300 - 2000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedBorderColor = palette.panelBorder.copy(alpha = 0.3f),
                                focusedBorderColor = palette.panelBorder.copy(alpha = 0.5f),
                                unfocusedTextColor = palette.onGlass,
                                focusedTextColor = palette.onGlass,
                                unfocusedLabelColor = palette.onGlassMuted,
                                focusedLabelColor = palette.onGlassMuted,
                                unfocusedSupportingTextColor = palette.onGlassMuted,
                                focusedSupportingTextColor = palette.onGlassMuted,
                            ),
                            singleLine = true,
                        )
                    }
                }
            }

            item {
                ImmersiveSettingsCard(backdropState) {
                    SettingsListRow(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "阅读模式",
                        supportingText = "切到完整剧情回看页，适合连续浏览对白、旁白和长文本。",
                        onClick = onOpenReadingMode,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = palette.panelBorder.copy(alpha = 0.44f),
                    )
                    SettingsListRow(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "切换模型",
                        supportingText = currentModel.ifBlank { "当前未选择模型" },
                        onClick = { showModelSheet = true },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = palette.panelBorder.copy(alpha = 0.44f),
                    )
                    SettingsListRow(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.SettingsSuggest,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "查看提示词",
                        supportingText = "检查当前世界书、记忆、摘要和剧情指令是否按预期注入。",
                        onClick = { showPromptDebugSheet = true },
                        enabled = latestPromptDebugDump.isNotBlank(),
                    )
                }
            }

            item {
                ImmersiveSettingsCard(backdropState) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "当前沉浸状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.onGlass,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ImmersiveGlassChip(
                                text = if (contextStatus.isContinuingSession) "继续剧情" else "新剧情",
                                backdropState = backdropState,
                            )
                            ImmersiveGlassChip(
                                text = "世界书 ${contextStatus.worldBookHitCount}",
                                backdropState = backdropState,
                            )
                            ImmersiveGlassChip(
                                text = "记忆 ${contextStatus.memoryInjectionCount}",
                                backdropState = backdropState,
                            )
                        }
                        Text(
                            text = buildString {
                                append(if (contextStatus.isContinuingSession) "正在延续旧剧情。" else "当前是新的剧情入口。")
                                if (contextStatus.hasSummary) {
                                    append(" 摘要已覆盖 ${contextStatus.summaryCoveredMessageCount} 条消息。")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onGlassMuted,
                        )
                    }
                }
            }

            item {
                ImmersiveSettingsCard(backdropState) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedSettingButton(
                            text = "重开剧情",
                            onClick = { showConfirmRestartDialog = true },
                            enabled = scenario != null,
                            isPrimary = false,
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            },
                        )
                        AnimatedSettingButton(
                            text = "清空剧情",
                            onClick = { showConfirmResetDialog = true },
                            enabled = scenario != null,
                            isPrimary = false,
                            leadingIcon = {
                                Icon(Icons.Default.WarningAmber, contentDescription = null)
                            },
                        )
                    }
                }
            }
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = currentProviderId,
            currentModel = currentModel,
            isLoadingModels = isLoadingModels,
            loadingProviderId = loadingProviderId,
            isSavingModel = isSavingModel,
            onDismissRequest = { showModelSheet = false },
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = { providerId ->
                showModelSheet = false
                onOpenProviderDetail(providerId)
            },
            onSelectModel = { providerId, model ->
                onSelectModel(providerId, model)
                showModelSheet = false
            },
        )
    }

    if (showPromptDebugSheet) {
        val promptSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPromptDebugSheet = false },
            sheetState = promptSheetState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = "上下文调试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    SelectionContainer {
                        Text(
                            text = latestPromptDebugDump.ifBlank { "暂无提示词调试信息" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (showConfirmRestartDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRestartDialog = false },
            title = { Text("重开剧情") },
            text = { Text("将创建一条新的剧情线，旧剧情历史不会继续沿用。是否继续？") },
            confirmButton = {
                NarraTextButton(
                    onClick = {
                        showConfirmRestartDialog = false
                        onRestartSession()
                        onNavigateBack()
                    },
                ) {
                    Text("重开")
                }
            },
            dismissButton = {
                NarraTextButton(onClick = { showConfirmRestartDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showConfirmResetDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmResetDialog = false },
            title = { Text("清空剧情") },
            text = { Text("会保留场景配置，只清空当前剧情消息。此操作不可撤销。") },
            confirmButton = {
                NarraTextButton(
                    onClick = {
                        showConfirmResetDialog = false
                        onResetSession()
                        onNavigateBack()
                    },
                ) {
                    Text("确定清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                NarraTextButton(onClick = { showConfirmResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RoleplaySettingsHero(
    backdropState: ImmersiveBackdropState,
    scenarioTitle: String,
    characterName: String,
    currentModel: String,
    contextStatus: RoleplayContextStatus,
    onOpenReadingMode: () -> Unit,
) {
    val palette = backdropState.palette

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        overlayColor = Color.White.copy(alpha = 0.1f),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = scenarioTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.onGlass,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "当前沉浸角色：$characterName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onGlassMuted,
                        )
                    }
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        overlayColor = Color.White.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(onClick = onOpenReadingMode)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "阅读模式",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.onGlass,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsStatusPill(
                        text = if (contextStatus.isContinuingSession) "继续剧情" else "新剧情",
                        containerColor = palette.chipTint,
                        contentColor = palette.chipText,
                    )
                    SettingsStatusPill(
                        text = "世界书 ${contextStatus.worldBookHitCount}",
                        containerColor = palette.panelTint,
                        contentColor = palette.onGlassMuted,
                    )
                    SettingsStatusPill(
                        text = "记忆 ${contextStatus.memoryInjectionCount}",
                        containerColor = palette.panelTint,
                        contentColor = palette.onGlassMuted,
                    )
                    currentModel.takeIf { it.isNotBlank() }?.let { modelName ->
                        SettingsStatusPill(
                            text = modelName,
                            containerColor = palette.panelTintStrong,
                            contentColor = palette.onGlass,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleplaySettingSwitchRow(
    palette: com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette,
    icon: @Composable () -> Unit,
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.onGlass,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onGlassMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ImmersiveSettingsCard(
    backdropState: ImmersiveBackdropState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        overlayColor = Color.White.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
