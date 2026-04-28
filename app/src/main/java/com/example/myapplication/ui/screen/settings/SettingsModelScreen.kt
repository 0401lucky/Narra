package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.TopAppSnackbarHost
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.ui.screen.chat.ModelPickerQuickAction
import com.example.myapplication.ui.screen.chat.ModelPickerSheet
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
fun SettingsModelScreen(
    uiState: SettingsUiState,
    onLoadModels: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectedModelChange: (String, String) -> Unit,
    onUpdateFunctionModel: (ProviderFunction, String, String) -> Unit,
    onUpdateFunctionModelMode: (ProviderFunction, String, ProviderFunctionModelMode) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    BackHandler(onBack = onNavigateBack)

    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val provider = uiState.currentProvider
    val draftSettings = uiState.toDraftAppSettings()
    val providerOptions = remember(uiState.providers) {
        uiState.providers.filter { it.enabled }
    }
    val currentProviderId = uiState.selectedProviderId.ifBlank { provider?.id.orEmpty() }
    val currentModel = provider?.selectedModel.orEmpty()
    val currentProviderSummary = buildString {
        append(provider?.name.orEmpty().ifBlank { "未选择提供商" })
        provider?.let { current ->
            append(" · ")
            append(
                when (current.resolvedApiProtocol()) {
                    com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                        if (current.resolvedOpenAiTextApiMode() == com.example.myapplication.model.OpenAiTextApiMode.RESPONSES) {
                            "OpenAI 兼容 / Responses"
                        } else {
                            "OpenAI 兼容 / Chat Completions"
                        }
                    }

                    com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC -> "Anthropic /messages"
                },
            )
        }
    }
    var selectingRole by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { SettingsTopBar(title = "模型", onNavigateBack = onNavigateBack) },
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
                    top = 12.dp,
                    end = SettingsScreenPadding,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsPageIntro(
                        title = "默认模型与功能模型",
                    )
                }

                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "当前正在编辑的提供商",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.title,
                            )
                            Text(
                                text = currentProviderSummary,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.title,
                            )
                        }
                    }
                }

                item {
                    RoleModelCard(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        title = "聊天模型",
                        statusLabel = "默认模型",
                        currentModelId = uiState.selectedModel,
                        providerName = provider?.name.orEmpty(),
                        emptyStateText = "还没选择默认聊天模型",
                        onClick = { selectingRole = "chat" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.TITLE_SUMMARY)
                    RoleModelCard(
                        icon = Icons.Outlined.Subtitles,
                        title = "标题/摘要模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "title" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.CHAT_SUGGESTION)
                    RoleModelCard(
                        icon = Icons.Outlined.QuestionAnswer,
                        title = "聊天建议模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "suggestion" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.MEMORY)
                    RoleModelCard(
                        icon = Icons.Outlined.Psychology,
                        title = "记忆模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "memory" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.TRANSLATION)
                    RoleModelCard(
                        icon = Icons.Outlined.Translate,
                        title = "翻译模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "translation" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.PHONE_SNAPSHOT)
                    RoleModelCard(
                        icon = Icons.Outlined.Build,
                        title = "查手机模型",
                        subtitle = "用于手机快照与搜索详情",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "phone_snapshot" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.SEARCH)
                    RoleModelCard(
                        icon = Icons.Outlined.Search,
                        title = "搜索模型",
                        subtitle = "仅 LLM 搜索",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "search" },
                    )
                }

                item {
                    val cardState = draftSettings.toRoleModelCardState(ProviderFunction.GIFT_IMAGE)
                    RoleModelCard(
                        icon = Icons.Outlined.Image,
                        title = "礼物生图模型",
                        subtitle = "用于礼物卡自动生成图片",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        providerName = cardState.providerName,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "gift_image" },
                    )
                }
            }
            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }

    if (selectingRole.isNotBlank() && providerOptions.isNotEmpty()) {
        val selectedFunction = selectingRole.toProviderFunctionOrNull()
        val providerRoleState = draftSettings.toRoleModelCardState(selectedFunction)
        val pickerProvider = if (selectedFunction == null || selectedFunction == ProviderFunction.CHAT) {
            provider
        } else {
            draftSettings.resolveFunctionProvider(selectedFunction)
        }
        val pickerProviderId = pickerProvider?.id.orEmpty().ifBlank { currentProviderId }
        val roleTitle = when (selectingRole) {
            "chat" -> "选择聊天模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "title" -> "选择标题总结模型"
            "suggestion" -> "选择聊天建议模型"
            "memory" -> "选择记忆模型"
            "translation" -> "选择翻译模型"
            "phone_snapshot" -> "选择查手机模型"
            "search" -> "选择搜索模型"
            "gift_image" -> "选择礼物生图模型"
            else -> "选择模型"
        }

        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = pickerProviderId,
            currentModel = if (selectedFunction == ProviderFunction.CHAT) {
                uiState.selectedModel
            } else {
                providerRoleState.pickerSelectedModelId
            },
            sheetTitle = roleTitle,
            isLoadingModels = uiState.isLoadingModels,
            loadingProviderId = uiState.loadingProviderId,
            isSavingModel = uiState.isSaving,
            onDismissRequest = { selectingRole = "" },
            onSelectProvider = { selectedProviderId ->
                if (selectedFunction == null || selectedFunction == ProviderFunction.CHAT) {
                    onSelectProvider(selectedProviderId)
                }
            },
            onOpenProviderDetail = {},
            quickActions = { selectedProvider ->
                if (selectedFunction == null || selectedFunction == ProviderFunction.CHAT) {
                    emptyList()
                } else {
                    val mode = selectedProvider?.resolveFunctionModelMode(selectedFunction)
                        ?: if (selectedFunction == ProviderFunction.GIFT_IMAGE) {
                            ProviderFunctionModelMode.DISABLED
                        } else {
                            ProviderFunctionModelMode.FOLLOW_DEFAULT
                        }
                    listOf(
                        ModelPickerQuickAction(
                            id = QuickActionFollowDefault,
                            title = "跟随默认模型",
                            supportingText = "自动使用当前聊天模型",
                            selected = mode == ProviderFunctionModelMode.FOLLOW_DEFAULT,
                        ),
                        ModelPickerQuickAction(
                            id = QuickActionDisabled,
                            title = "关闭该功能",
                            supportingText = "彻底停用，不再回退默认模型",
                            selected = mode == ProviderFunctionModelMode.DISABLED,
                        ),
                    )
                }
            },
            onSelectQuickAction = { selectedProviderId, optionId ->
                val function = selectingRole.toProviderFunctionOrNull()
                if (function != null && function != ProviderFunction.CHAT) {
                    val mode = if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED
                    onUpdateFunctionModelMode(function, selectedProviderId, mode)
                }
                selectingRole = ""
            },
            onSelectModel = { selectedProviderId, model ->
                val function = selectingRole.toProviderFunctionOrNull()
                if (function == null || function == ProviderFunction.CHAT) {
                    onSelectedModelChange(selectedProviderId, model)
                } else {
                    onUpdateFunctionModel(function, selectedProviderId, model)
                }
                selectingRole = ""
            },
        )
    }
}

@Composable
private fun RoleModelCard(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    statusLabel: String,
    currentModelId: String,
    providerName: String,
    emptyStateText: String,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.2f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = palette.accentSoft,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = palette.accentStrong,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelModePill(text = statusLabel)
                    if (currentModelId.isNotBlank()) {
                        ModelIcon(modelName = currentModelId, size = 20.dp)
                    }
                    Text(
                        text = currentModelId.ifBlank { emptyStateText }.let { modelText ->
                            if (currentModelId.isBlank() || providerName.isBlank()) {
                                modelText
                            } else {
                                "$providerName · $modelText"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (currentModelId.isNotBlank()) palette.title else palette.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = palette.surfaceTint,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "选择模型",
                            tint = palette.accentStrong,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelModePill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class RoleModelCardState(
    val statusLabel: String,
    val displayModelId: String,
    val pickerSelectedModelId: String,
    val emptyStateText: String,
    val providerName: String,
)

private fun AppSettings.toRoleModelCardState(
    function: ProviderFunction?,
): RoleModelCardState {
    if (function == null || function == ProviderFunction.CHAT) {
        val provider = activeProvider()
        return RoleModelCardState(
            statusLabel = "默认模型",
            displayModelId = provider?.selectedModel.orEmpty(),
            pickerSelectedModelId = provider?.selectedModel.orEmpty(),
            emptyStateText = "还没选择默认聊天模型",
            providerName = provider?.name.orEmpty(),
        )
    }

    val provider = resolveFunctionProvider(function)
    val mode = resolveFunctionModeSafely(function)
    val explicitModel = provider?.resolveExplicitFunctionModel(function).orEmpty()
    val resolvedModel = resolveFunctionModel(function)
    return when (mode) {
        ProviderFunctionModelMode.FOLLOW_DEFAULT -> RoleModelCardState(
            statusLabel = "跟随默认",
            displayModelId = resolvedModel,
            pickerSelectedModelId = "",
            emptyStateText = "将跟随默认聊天模型",
            providerName = provider?.name.orEmpty(),
        )
        ProviderFunctionModelMode.CUSTOM -> RoleModelCardState(
            statusLabel = "单独指定",
            displayModelId = explicitModel,
            pickerSelectedModelId = explicitModel,
            emptyStateText = "请选择模型",
            providerName = provider?.name.orEmpty(),
        )
        ProviderFunctionModelMode.DISABLED -> RoleModelCardState(
            statusLabel = "已关闭",
            displayModelId = "",
            pickerSelectedModelId = "",
            emptyStateText = "当前已关闭",
            providerName = provider?.name.orEmpty(),
        )
    }
}

private fun AppSettings.resolveFunctionModeSafely(
    function: ProviderFunction,
): ProviderFunctionModelMode {
    val provider = resolveFunctionProvider(function)
    if (provider == null) {
        return if (function == ProviderFunction.GIFT_IMAGE) {
            ProviderFunctionModelMode.DISABLED
        } else {
            ProviderFunctionModelMode.FOLLOW_DEFAULT
        }
    }
    return provider.resolveFunctionModelMode(function)
}

private fun SettingsUiState.toDraftAppSettings(): AppSettings {
    return savedSettings.copy(
        providers = providers,
        selectedProviderId = selectedProviderId,
        functionModelProviderIds = functionModelProviderIds,
    )
}

private fun String.toProviderFunctionOrNull(): ProviderFunction? {
    return when (this) {
        "chat" -> ProviderFunction.CHAT
        "title" -> ProviderFunction.TITLE_SUMMARY
        "suggestion" -> ProviderFunction.CHAT_SUGGESTION
        "memory" -> ProviderFunction.MEMORY
        "translation" -> ProviderFunction.TRANSLATION
        "phone_snapshot" -> ProviderFunction.PHONE_SNAPSHOT
        "search" -> ProviderFunction.SEARCH
        "gift_image" -> ProviderFunction.GIFT_IMAGE
        else -> null
    }
}

private const val QuickActionFollowDefault = "follow_default"
private const val QuickActionDisabled = "disabled"

@Composable
private fun ModelPickerDialog(
    title: String,
    models: List<String>,
    selectedModel: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(models.size) { index ->
                    val model = models[index]
                    val isSelected = model == selectedModel
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(model) },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ModelIcon(modelName = model, size = 22.dp)
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            NarraTextButton(onClick = onDismissRequest) { Text("取消") }
        },
    )
}
