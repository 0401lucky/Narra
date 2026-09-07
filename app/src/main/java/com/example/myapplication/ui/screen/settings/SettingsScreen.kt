package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AudioLines
import com.composables.icons.lucide.Bug
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Sun
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.system.performance.BenchmarkActionIds
import com.example.myapplication.system.performance.BenchmarkReadyIds
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.performance.benchmarkActionTag
import com.example.myapplication.ui.performance.benchmarkReadyTag
import com.example.myapplication.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenRoleplay: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenVoiceSynthesisSettings: () -> Unit,
    onOpenVoiceRecognitionSettings: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onOpenComplianceSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenUserMasks: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenMemorySettings: () -> Unit,
    onOpenContextTransferSettings: () -> Unit,
    onOpenRoleplayScripts: () -> Unit,
    onOpenHome: () -> Unit,
    onNavigateBack: () -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateAppColorTheme: (AppColorTheme) -> Unit,
    onUpdateMessageTextScale: (Float) -> Unit,
    onUpdateReasoningExpandedByDefault: (Boolean) -> Unit,
    onUpdateShowThinkingContent: (Boolean) -> Unit,
    onUpdateAutoCollapseThinking: (Boolean) -> Unit,
    onUpdateAutoPreviewImages: (Boolean) -> Unit,
    onUpdateCodeBlockAutoWrap: (Boolean) -> Unit,
    onUpdateCodeBlockAutoCollapse: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val savedHasRequiredConfig = uiState.savedSettings.hasRequiredConfig()
    val canSave = !uiState.isSaving && uiState.hasDraftChanges()
    var showThemeModeSheet by rememberSaveable { mutableStateOf(false) }
    var showAppColorThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showDisplaySettingsSheet by rememberSaveable { mutableStateOf(false) }

    val providerSummary = uiState.currentProvider?.name?.ifBlank { "未命名提供商" }
        ?: "未配置提供商"
    val voiceSummary = if (uiState.voiceSynthesisSettings.enabled) {
        val voiceSettings = uiState.voiceSynthesisSettings.normalized()
        val profile = voiceSettings.defaultProfile
        "已启用 · ${profile.storageLabel()}"
    } else {
        "未启用"
    }
    val voiceRecognitionSummary = with(uiState.voiceRecognitionSettings.normalized()) {
        when {
            !enabled -> "未启用"
            !hasUsableCredential() -> "已启用 · ${engine.label} · 凭据未填全"
            else -> "已启用 · ${engine.label}"
        }
    }
    val displaySummary = buildString {
        append(displayScaleLabel(uiState.messageTextScale))
        append(" · ")
        append(if (uiState.autoPreviewImages) stringResource(R.string.settings_auto_preview) else stringResource(R.string.settings_manual_preview))
    }

    Scaffold(
        modifier = Modifier.benchmarkReadyTag(
            readyId = BenchmarkReadyIds.SETTINGS_READY,
            ready = isSettingsBenchmarkReady(uiState),
        ),
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack,
                actionLabel = when {
                    canSave -> stringResource(R.string.common_save)
                    savedHasRequiredConfig -> stringResource(R.string.settings_chat_label)
                    else -> stringResource(R.string.settings_welcome_label)
                },
                onAction = when {
                    canSave -> onSave
                    savedHasRequiredConfig -> onOpenRoleplay
                    else -> onOpenHome
                },
                actionEnabled = !uiState.isSaving,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SettingsScreenPadding,
                    top = 4.dp,
                    end = SettingsScreenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_section_model_service)) {
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Sparkles, contentDescription = null, tint = palette.accent) },
                            title = stringResource(R.string.settings_default_model_prompt),
                            supportingText = uiState.selectedModel.ifBlank { "尚未选择模型" },
                            onClick = onOpenModelSettings,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Server, contentDescription = null, tint = palette.accent) },
                            title = stringResource(R.string.settings_provider),
                            supportingText = providerSummary,
                            onClick = onOpenProviderSettings,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            modifier = Modifier.benchmarkActionTag(BenchmarkActionIds.OPEN_VOICE_SYNTHESIS),
                            leadingContent = { Icon(Lucide.AudioLines, contentDescription = null, tint = palette.accent) },
                            title = "MiMo 语音合成",
                            supportingText = voiceSummary,
                            onClick = onOpenVoiceSynthesisSettings,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Mic, contentDescription = null, tint = palette.accent) },
                            title = "语音识别",
                            supportingText = voiceRecognitionSummary,
                            onClick = onOpenVoiceRecognitionSettings,
                        )
                    }
                }
                item {
                    SettingsGroup(title = "外观与显示") {
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Palette, contentDescription = null, tint = palette.accent) },
                            title = "主题色调",
                            supportingText = uiState.appColorTheme.label,
                            onClick = { showAppColorThemeSheet = true },
                            trailingContent = { ThemeSwatchSummary(theme = uiState.appColorTheme) },
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Sun, contentDescription = null, tint = palette.accent) },
                            title = stringResource(R.string.settings_color_mode),
                            supportingText = uiState.themeMode.label,
                            onClick = { showThemeModeSheet = true },
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.SlidersHorizontal, contentDescription = null, tint = palette.accent) },
                            title = stringResource(R.string.settings_display),
                            supportingText = displaySummary,
                            onClick = { showDisplaySettingsSheet = true },
                        )
                    }
                }
                item {
                    SettingsGroup(title = "应用") {
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Download, contentDescription = null, tint = palette.accent) },
                            title = stringResource(R.string.settings_version_update),
                            onClick = onOpenUpdateSettings,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.FileText, contentDescription = null, tint = palette.accent) },
                            title = "使用条款与年龄限制",
                            onClick = onOpenComplianceSettings,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Bug, contentDescription = null, tint = palette.accent) },
                            title = "本地诊断",
                            onClick = onOpenDiagnostics,
                        )
                        SettingsGroupDivider()
                        SettingsListRow(
                            leadingContent = { Icon(Lucide.Code, contentDescription = null, tint = palette.accent) },
                            title = "脚本实验室",
                            onClick = onOpenRoleplayScripts,
                        )
                    }
                }
            }
            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }

    if (showThemeModeSheet) {
        ThemeModeSheet(
            selectedMode = uiState.themeMode,
            onDismissRequest = { showThemeModeSheet = false },
            onSelectMode = { themeMode ->
                onUpdateThemeMode(themeMode)
                showThemeModeSheet = false
            },
        )
    }

    if (showAppColorThemeSheet) {
        AppColorThemeSheet(
            selectedTheme = uiState.appColorTheme,
            onDismissRequest = { showAppColorThemeSheet = false },
            onSelectTheme = { appColorTheme ->
                onUpdateAppColorTheme(appColorTheme)
                showAppColorThemeSheet = false
            }
        )
    }

    if (showDisplaySettingsSheet) {
        DisplaySettingsSheet(
            messageTextScale = uiState.messageTextScale,
            reasoningExpandedByDefault = uiState.reasoningExpandedByDefault,
            showThinkingContent = uiState.showThinkingContent,
            autoCollapseThinking = uiState.autoCollapseThinking,
            autoPreviewImages = uiState.autoPreviewImages,
            codeBlockAutoWrap = uiState.codeBlockAutoWrap,
            codeBlockAutoCollapse = uiState.codeBlockAutoCollapse,
            onDismissRequest = { showDisplaySettingsSheet = false },
            onMessageTextScaleChange = onUpdateMessageTextScale,
            onReasoningExpandedByDefaultChange = onUpdateReasoningExpandedByDefault,
            onShowThinkingContentChange = onUpdateShowThinkingContent,
            onAutoCollapseThinkingChange = onUpdateAutoCollapseThinking,
            onAutoPreviewImagesChange = onUpdateAutoPreviewImages,
            onCodeBlockAutoWrapChange = onUpdateCodeBlockAutoWrap,
            onCodeBlockAutoCollapseChange = onUpdateCodeBlockAutoCollapse,
        )
    }
}

internal fun isSettingsBenchmarkReady(uiState: SettingsUiState): Boolean {
    val selectedProviderId = uiState.selectedProviderId
    return selectedProviderId.isNotBlank() &&
        uiState.providers.any { provider -> provider.id == selectedProviderId }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ThemeModeSheet(
    selectedMode: ThemeMode,
    onDismissRequest: () -> Unit,
    onSelectMode: (ThemeMode) -> Unit,
) {
    val palette = rememberSettingsPalette()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_color_mode),
                style = MaterialTheme.typography.titleLarge,
                color = palette.title,
            )
            ThemeMode.entries.forEach { themeMode ->
                val icon = when (themeMode) {
                    ThemeMode.SYSTEM -> Lucide.Monitor
                    ThemeMode.LIGHT -> Lucide.Sun
                    ThemeMode.DARK -> Lucide.Moon
                }
                val description = when (themeMode) {
                    ThemeMode.SYSTEM -> "跟随手机的深浅色设置"
                    ThemeMode.LIGHT -> "始终使用明亮背景"
                    ThemeMode.DARK -> "始终使用深色背景"
                }
                val selected = themeMode == selectedMode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelectMode(themeMode) },
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) palette.accentSoft.copy(alpha = 0.72f) else palette.surface,
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 0.75.dp,
                        color = if (selected) palette.accent.copy(alpha = 0.72f) else palette.border,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) palette.accent else palette.body,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(themeMode.label, style = MaterialTheme.typography.titleMedium, color = palette.title)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = palette.body)
                        }
                        if (selected) {
                            Icon(Lucide.Check, contentDescription = null, tint = palette.accent)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun DisplaySettingsSheet(
    messageTextScale: Float,
    reasoningExpandedByDefault: Boolean,
    showThinkingContent: Boolean,
    autoCollapseThinking: Boolean,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    onDismissRequest: () -> Unit,
    onMessageTextScaleChange: (Float) -> Unit,
    onReasoningExpandedByDefaultChange: (Boolean) -> Unit,
    onShowThinkingContentChange: (Boolean) -> Unit,
    onAutoCollapseThinkingChange: (Boolean) -> Unit,
    onAutoPreviewImagesChange: (Boolean) -> Unit,
    onCodeBlockAutoWrapChange: (Boolean) -> Unit,
    onCodeBlockAutoCollapseChange: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_display),
                style = MaterialTheme.typography.titleLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_message_text_scale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = displayScaleLabel(messageTextScale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = messageTextScale,
                    onValueChange = onMessageTextScaleChange,
                    valueRange = 0.85f..1.25f,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("思考内容", "")
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_thinking_default_expanded),
                    checked = reasoningExpandedByDefault,
                    onCheckedChange = onReasoningExpandedByDefaultChange,
                )
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_show_thinking_in_progress),
                    checked = showThinkingContent,
                    onCheckedChange = onShowThinkingContentChange,
                )
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_auto_collapse_after),
                    checked = autoCollapseThinking,
                    onCheckedChange = onAutoCollapseThinkingChange,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSectionHeader("图片与代码", "")
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_auto_preview_images),
                    checked = autoPreviewImages,
                    onCheckedChange = onAutoPreviewImagesChange,
                )
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_code_block_auto_wrap),
                    checked = codeBlockAutoWrap,
                    onCheckedChange = onCodeBlockAutoWrapChange,
                )
                DisplaySwitchRow(
                    title = stringResource(R.string.settings_code_block_auto_collapse),
                    checked = codeBlockAutoCollapse,
                    onCheckedChange = onCodeBlockAutoCollapseChange,
                )
            }
        }
    }
}

@Composable
private fun DisplaySwitchRow(
    title: String,
    supportingText: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun displayScaleLabel(scale: Float): String {
    return when {
        scale <= 0.92f -> stringResource(R.string.settings_text_scale_compact)
        scale >= 1.12f -> stringResource(R.string.settings_text_scale_large)
        else -> stringResource(R.string.settings_text_scale_standard)
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AppColorThemeSheet(
    selectedTheme: AppColorTheme,
    onDismissRequest: () -> Unit,
    onSelectTheme: (AppColorTheme) -> Unit,
) {
    val palette = rememberSettingsPalette()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = palette.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择主题色调",
                style = MaterialTheme.typography.titleLarge,
                color = palette.title,
                modifier = Modifier.padding(top = 8.dp),
            )
            AppColorTheme.entries.forEach { appColorTheme ->
                val selected = appColorTheme == selectedTheme
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelectTheme(appColorTheme) },
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) palette.accentSoft.copy(alpha = 0.62f) else palette.surface,
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 0.75.dp,
                        color = if (selected) themeAccent(appColorTheme) else palette.border,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ThemeSwatch(theme = appColorTheme, size = 40.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(appColorTheme.label, style = MaterialTheme.typography.titleMedium, color = palette.title)
                            Text(themeDescription(appColorTheme), style = MaterialTheme.typography.bodySmall, color = palette.body)
                        }
                        if (selected) {
                            Icon(Lucide.Check, contentDescription = null, tint = palette.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchSummary(theme: AppColorTheme) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        ThemeSwatch(theme = theme, size = 18.dp)
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeSwatch(theme: AppColorTheme, size: androidx.compose.ui.unit.Dp) {
    val accent = themeAccent(theme)
    val soft = themeSoft(theme)
    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        shape = CircleShape,
        color = soft,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(size / 3).clip(CircleShape).background(accent))
    }
}

private fun themeAccent(theme: AppColorTheme): Color = when (theme) {
    AppColorTheme.MATCHA -> Color(0xFF326B58)
    AppColorTheme.VANILLA -> Color(0xFF79552D)
    AppColorTheme.OCEAN -> Color(0xFF326692)
}

private fun themeSoft(theme: AppColorTheme): Color = when (theme) {
    AppColorTheme.MATCHA -> Color(0xFFDDEFE2)
    AppColorTheme.VANILLA -> Color(0xFFF3E8D8)
    AppColorTheme.OCEAN -> Color(0xFFDCEAF5)
}

private fun themeDescription(theme: AppColorTheme): String = when (theme) {
    AppColorTheme.MATCHA -> "柔和、安静，适合日常使用"
    AppColorTheme.VANILLA -> "温暖、自然，带一点复古感"
    AppColorTheme.OCEAN -> "清爽、沉静，适合专注阅读"
}
