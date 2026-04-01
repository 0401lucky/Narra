package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.example.myapplication.ui.component.TopAppSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onOpenSearchToolSettings: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenMemorySettings: () -> Unit,
    onOpenContextTransferSettings: () -> Unit,
    onOpenScreenTranslationSettings: () -> Unit,
    onOpenHome: () -> Unit,
    onNavigateBack: () -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
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
    var showDisplaySettingsSheet by rememberSaveable { mutableStateOf(false) }

    val connectionSummary = uiState.baseUrl
        .ifBlank { uiState.savedSettings.baseUrl }
        .ifBlank { "未配置 Base URL" }
    val searchSummary = uiState.searchSettings.selectedSourceOrNull()?.let { source ->
        "${source.name} · 默认 ${uiState.searchSettings.defaultResultCount} 条"
    } ?: "未配置可用搜索源"
    val displaySummary = buildString {
        append(displayScaleLabel(uiState.messageTextScale))
        append(" · ")
        append(if (uiState.autoPreviewImages) "自动预览已开启" else "自动预览已关闭")
        append(" · ")
        append(if (uiState.reasoningExpandedByDefault) "思考默认展开" else "思考默认收起")
        append(" · ")
        append(if (uiState.showThinkingContent) "生成中显示思考" else "生成中隐藏思考")
        append(" · ")
        append(if (uiState.autoCollapseThinking) "结束后自动收起思考" else "结束后保留思考展开")
        append(" · ")
        append(if (uiState.codeBlockAutoWrap) "代码自动换行" else "代码横向滚动")
        append(" · ")
        append(if (uiState.codeBlockAutoCollapse) "代码自动折叠" else "代码完整展开")
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "设置",
                onNavigateBack = onNavigateBack,
                actionLabel = if (savedHasRequiredConfig) "聊天" else "欢迎页",
                onAction = if (savedHasRequiredConfig) onOpenChat else onOpenHome,
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
            item { SettingsSectionHeader("通用设置", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.LightMode, contentDescription = null, tint = palette.title) },
                        title = "颜色模式",
                        supportingText = uiState.themeMode.label,
                        onClick = { showThemeModeSheet = true },
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.accentSoft,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(uiState.themeMode.label, style = MaterialTheme.typography.labelMedium, color = palette.title)
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.title)
                                }
                            }
                        }
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null, tint = palette.title) },
                        title = "显示设置",
                        supportingText = displaySummary,
                        onClick = { showDisplaySettingsSheet = true },
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = palette.title) },
                        title = "版本与更新",
                        onClick = onOpenUpdateSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Face, contentDescription = null, tint = palette.title) },
                        title = "助手",
                        onClick = onOpenAssistantSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = palette.title) },
                        title = "世界书",
                        onClick = onOpenWorldBookSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.title) },
                        title = "记忆与摘要",
                        onClick = onOpenMemorySettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Backup, contentDescription = null, tint = palette.title) },
                        title = "资料导入导出",
                        onClick = onOpenContextTransferSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Translate, contentDescription = null, tint = palette.title) },
                        title = "悬浮翻译",
                        onClick = onOpenScreenTranslationSettings,
                    )
                }
            }

            item { SettingsSectionHeader("模型与服务", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.title) },
                        title = "默认模型和提示词",
                        onClick = onOpenModelSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.title) },
                        title = "提供商",
                        onClick = onOpenProviderSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = palette.title) },
                        title = "连接与凭据",
                        supportingText = connectionSummary,
                        onClick = onOpenConnectionSettings,
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.title) },
                        title = "搜索与工具",
                        supportingText = searchSummary,
                        onClick = onOpenSearchToolSettings,
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

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ThemeModeSheet(
    selectedMode: ThemeMode,
    onDismissRequest: () -> Unit,
    onSelectMode: (ThemeMode) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "颜色模式",
                style = MaterialTheme.typography.titleLarge,
            )
            ThemeMode.entries.forEach { themeMode ->
                SettingsListRow(
                    leadingContent = {
                        Icon(Icons.Default.LightMode, contentDescription = null)
                    },
                    title = themeMode.label,
                    onClick = { onSelectMode(themeMode) },
                    trailingContent = {
                        if (themeMode == selectedMode) {
                            Text("当前", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "显示设置",
                style = MaterialTheme.typography.titleLarge,
            )
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "消息字号",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = displayScaleLabel(messageTextScale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = messageTextScale,
                        onValueChange = onMessageTextScaleChange,
                        valueRange = 0.85f..1.25f,
                    )
                }
            }
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DisplaySwitchRow(
                        title = "思考默认展开",
                        checked = reasoningExpandedByDefault,
                        onCheckedChange = onReasoningExpandedByDefaultChange,
                    )
                    DisplaySwitchRow(
                        title = "生成中显示思考",
                        checked = showThinkingContent,
                        onCheckedChange = onShowThinkingContentChange,
                    )
                    DisplaySwitchRow(
                        title = "结束后自动收起思考",
                        checked = autoCollapseThinking,
                        onCheckedChange = onAutoCollapseThinkingChange,
                    )
                    DisplaySwitchRow(
                        title = "自动预览图片",
                        checked = autoPreviewImages,
                        onCheckedChange = onAutoPreviewImagesChange,
                    )
                    DisplaySwitchRow(
                        title = "代码块自动换行",
                        checked = codeBlockAutoWrap,
                        onCheckedChange = onCodeBlockAutoWrapChange,
                    )
                    DisplaySwitchRow(
                        title = "代码块自动折叠",
                        checked = codeBlockAutoCollapse,
                        onCheckedChange = onCodeBlockAutoCollapseChange,
                    )
                }
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
        modifier = Modifier.fillMaxWidth(),
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
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun displayScaleLabel(scale: Float): String {
    return when {
        scale <= 0.92f -> "紧凑"
        scale >= 1.12f -> "偏大"
        else -> "标准"
    }
}
