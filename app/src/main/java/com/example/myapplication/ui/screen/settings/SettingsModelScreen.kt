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
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
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
    onUpdateTitleSummaryModel: (String, String) -> Unit,
    onUpdateChatSuggestionModel: (String, String) -> Unit,
    onUpdateMemoryModel: (String, String) -> Unit,
    onUpdateTranslationModel: (String, String) -> Unit,
    onUpdatePhoneSnapshotModel: (String, String) -> Unit,
    onUpdateSearchModel: (String, String) -> Unit,
    onUpdateGiftImageModel: (String, String) -> Unit,
    onUpdateTitleSummaryModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdateChatSuggestionModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdateMemoryModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdateTranslationModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdatePhoneSnapshotModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdateSearchModelMode: (String, ProviderFunctionModelMode) -> Unit,
    onUpdateGiftImageModelMode: (String, ProviderFunctionModelMode) -> Unit,
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
                        emptyStateText = "还没选择默认聊天模型",
                        onClick = { selectingRole = "chat" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.TITLE_SUMMARY)
                    RoleModelCard(
                        icon = Icons.Outlined.Subtitles,
                        title = "标题/摘要模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "title" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.CHAT_SUGGESTION)
                    RoleModelCard(
                        icon = Icons.Outlined.QuestionAnswer,
                        title = "聊天建议模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "suggestion" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.MEMORY)
                    RoleModelCard(
                        icon = Icons.Outlined.Psychology,
                        title = "记忆模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "memory" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.TRANSLATION)
                    RoleModelCard(
                        icon = Icons.Outlined.Translate,
                        title = "翻译模型",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "translation" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.PHONE_SNAPSHOT)
                    RoleModelCard(
                        icon = Icons.Outlined.Build,
                        title = "查手机模型",
                        subtitle = "用于手机快照与搜索详情",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "phone_snapshot" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.SEARCH)
                    RoleModelCard(
                        icon = Icons.Outlined.Search,
                        title = "搜索模型",
                        subtitle = "仅 LLM 搜索",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
                        emptyStateText = cardState.emptyStateText,
                        onClick = { selectingRole = "search" },
                    )
                }

                item {
                    val cardState = provider.toRoleModelCardState(ProviderFunction.GIFT_IMAGE)
                    RoleModelCard(
                        icon = Icons.Outlined.Image,
                        title = "礼物生图模型",
                        subtitle = "用于礼物卡自动生成图片",
                        statusLabel = cardState.statusLabel,
                        currentModelId = cardState.displayModelId,
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
        val providerRoleState = provider.toRoleModelCardState(selectedFunction)
        val roleTitle = when (selectingRole) {
            "chat" -> "选择聊天模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "title" -> "选择标题总结模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "suggestion" -> "选择聊天建议模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "memory" -> "选择记忆模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "translation" -> "选择翻译模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "phone_snapshot" -> "选择查手机模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "search" -> "选择搜索模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            "gift_image" -> "选择礼物生图模型 · ${provider?.name.orEmpty().ifBlank { "当前提供商" }}"
            else -> "选择模型"
        }

        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = currentProviderId,
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
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = {},
            quickActions = { selectedProvider ->
                if (selectedFunction == null || selectedFunction == ProviderFunction.CHAT) {
                    emptyList()
                } else {
                    val mode = selectedProvider.resolveFunctionModeSafely(selectedFunction)
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
                when (selectingRole) {
                    "title" -> onUpdateTitleSummaryModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "suggestion" -> onUpdateChatSuggestionModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "memory" -> onUpdateMemoryModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "translation" -> onUpdateTranslationModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "phone_snapshot" -> onUpdatePhoneSnapshotModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "search" -> onUpdateSearchModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                    "gift_image" -> onUpdateGiftImageModelMode(
                        selectedProviderId,
                        if (optionId == QuickActionFollowDefault) ProviderFunctionModelMode.FOLLOW_DEFAULT else ProviderFunctionModelMode.DISABLED,
                    )
                }
                selectingRole = ""
            },
            onSelectModel = { selectedProviderId, model ->
                when (selectingRole) {
                    "chat" -> onSelectedModelChange(selectedProviderId, model)
                    "title" -> onUpdateTitleSummaryModel(selectedProviderId, model)
                    "suggestion" -> onUpdateChatSuggestionModel(selectedProviderId, model)
                    "memory" -> onUpdateMemoryModel(selectedProviderId, model)
                    "translation" -> onUpdateTranslationModel(selectedProviderId, model)
                    "phone_snapshot" -> onUpdatePhoneSnapshotModel(selectedProviderId, model)
                    "search" -> onUpdateSearchModel(selectedProviderId, model)
                    "gift_image" -> onUpdateGiftImageModel(selectedProviderId, model)
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
                        text = currentModelId.ifBlank { emptyStateText },
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
)

private fun ProviderSettings?.toRoleModelCardState(
    function: ProviderFunction?,
): RoleModelCardState {
    if (function == null || function == ProviderFunction.CHAT) {
        return RoleModelCardState(
            statusLabel = "默认模型",
            displayModelId = this?.selectedModel.orEmpty(),
            pickerSelectedModelId = this?.selectedModel.orEmpty(),
            emptyStateText = "还没选择默认聊天模型",
        )
    }

    val mode = resolveFunctionModeSafely(function)
    val explicitModel = this?.resolveExplicitFunctionModel(function).orEmpty()
    val resolvedModel = this?.resolveFunctionModel(function).orEmpty()
    return when (mode) {
        ProviderFunctionModelMode.FOLLOW_DEFAULT -> RoleModelCardState(
            statusLabel = "跟随默认",
            displayModelId = resolvedModel,
            pickerSelectedModelId = "",
            emptyStateText = "将跟随默认聊天模型",
        )
        ProviderFunctionModelMode.CUSTOM -> RoleModelCardState(
            statusLabel = "单独指定",
            displayModelId = explicitModel,
            pickerSelectedModelId = explicitModel,
            emptyStateText = "请选择模型",
        )
        ProviderFunctionModelMode.DISABLED -> RoleModelCardState(
            statusLabel = "已关闭",
            displayModelId = "",
            pickerSelectedModelId = "",
            emptyStateText = "当前已关闭",
        )
    }
}

private fun ProviderSettings?.resolveFunctionModeSafely(
    function: ProviderFunction,
): ProviderFunctionModelMode {
    if (this == null) {
        return if (function == ProviderFunction.GIFT_IMAGE) {
            ProviderFunctionModelMode.DISABLED
        } else {
            ProviderFunctionModelMode.FOLLOW_DEFAULT
        }
    }
    return resolveFunctionModelMode(function)
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
