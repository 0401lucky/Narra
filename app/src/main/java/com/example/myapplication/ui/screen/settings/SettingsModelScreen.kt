package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import com.example.myapplication.ui.component.AppSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
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
import com.example.myapplication.ui.component.ModelIcon
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
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val provider = uiState.currentProvider
    val providerId = provider?.id.orEmpty()
    val providerOptions = remember(uiState.providers) {
        uiState.providers.filter { it.enabled }
    }
    val currentProviderId = uiState.selectedProviderId.ifBlank { provider?.id.orEmpty() }
    val currentModel = provider?.selectedModel.orEmpty()
    var selectingRole by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { SettingsTopBar(title = "模型", onNavigateBack = onNavigateBack) },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                    overline = "模型",
                    title = uiState.selectedModel.ifBlank { "还没选择默认模型" },
                    summary = "为不同功能分配合适的模型，优化成本和性能。",
                )
            }

            item {
                RoleModelCard(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "聊天模型",
                    subtitle = "全局默认的聊天模型",
                    currentModelId = uiState.selectedModel,
                    onClick = { selectingRole = "chat" },
                )
            }

            item {
                RoleModelCard(
                    icon = Icons.Outlined.Subtitles,
                    title = "标题总结模型",
                    subtitle = "用于总结对话标题的模型，推荐使用快速且便宜的模型",
                    currentModelId = provider?.titleSummaryModel.orEmpty(),
                    onClick = { selectingRole = "title" },
                )
            }

            item {
                RoleModelCard(
                    icon = Icons.Outlined.QuestionAnswer,
                    title = "聊天建议模型",
                    subtitle = "用于生成对话建议的模型，推荐使用快速且便宜的模型",
                    currentModelId = provider?.chatSuggestionModel.orEmpty(),
                    onClick = { selectingRole = "suggestion" },
                )
            }

            item {
                RoleModelCard(
                    icon = Icons.Outlined.Psychology,
                    title = "记忆模型",
                    subtitle = "用于自动提取长期记忆的模型，建议使用便宜且稳定的模型",
                    currentModelId = provider?.memoryModel.orEmpty(),
                    onClick = { selectingRole = "memory" },
                )
            }

            item {
                RoleModelCard(
                    icon = Icons.Outlined.Translate,
                    title = "翻译模型",
                    subtitle = "用于翻译功能的模型",
                    currentModelId = provider?.translationModel.orEmpty(),
                    onClick = { selectingRole = "translation" },
                )
            }
        }
    }

    if (selectingRole.isNotBlank() && providerOptions.isNotEmpty()) {
        val currentSelectedForRole = when (selectingRole) {
            "chat" -> uiState.selectedModel
            "title" -> provider?.titleSummaryModel.orEmpty()
            "suggestion" -> provider?.chatSuggestionModel.orEmpty()
            "memory" -> provider?.memoryModel.orEmpty()
            "translation" -> provider?.translationModel.orEmpty()
            else -> ""
        }
        val roleTitle = when (selectingRole) {
            "chat" -> "选择聊天模型"
            "title" -> "选择标题总结模型"
            "suggestion" -> "选择聊天建议模型"
            "memory" -> "选择记忆模型"
            "translation" -> "选择翻译模型"
            else -> "选择模型"
        }

        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = currentProviderId,
            currentModel = currentSelectedForRole.ifBlank { currentModel },
            isLoadingModels = uiState.isLoadingModels,
            loadingProviderId = uiState.loadingProviderId,
            isSavingModel = uiState.isSaving,
            onDismissRequest = { selectingRole = "" },
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = {},
            onSelectModel = { selectedProviderId, model ->
                when (selectingRole) {
                    "chat" -> onSelectedModelChange(selectedProviderId, model)
                    "title" -> onUpdateTitleSummaryModel(selectedProviderId, model)
                    "suggestion" -> onUpdateChatSuggestionModel(selectedProviderId, model)
                    "memory" -> onUpdateMemoryModel(selectedProviderId, model)
                    "translation" -> onUpdateTranslationModel(selectedProviderId, model)
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
    subtitle: String,
    currentModelId: String,
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
        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.border.copy(alpha=0.2f)),
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
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                if (currentModelId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModelIcon(modelName = currentModelId, size = 20.dp)
                        Text(
                            text = currentModelId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = palette.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                } else {
                    Text(
                        text = "选择模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = palette.surfaceTint,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = "参数设置",
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
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.6f) else androidx.compose.ui.graphics.Color.Transparent,
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
                                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        },
    )
}
