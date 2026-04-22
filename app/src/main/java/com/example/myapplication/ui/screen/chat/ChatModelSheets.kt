package com.example.myapplication.ui.screen.chat

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ReasoningBudgetPreset
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.resolveReasoningBudgetLabel
import com.example.myapplication.model.supportsThinkingBudgetControl
import com.example.myapplication.ui.component.ModelIcon

internal data class ModelPickerQuickAction(
    val id: String,
    val title: String,
    val supportingText: String = "",
    val selected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReasoningBudgetSheet(
    provider: ProviderSettings?,
    currentModel: String,
    isSavingModel: Boolean,
    reasoningBudgetHint: String,
    onDismissRequest: () -> Unit,
    onUpdateThinkingBudget: (String, Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedModelAbilities = remember(provider, currentModel) {
        provider?.resolveModelAbilities(currentModel) ?: inferModelAbilities(currentModel)
    }
    val selectedModelSupportsReasoning = remember(selectedModelAbilities, currentModel) {
        currentModel.isNotBlank() && ModelAbility.REASONING in selectedModelAbilities
    }
    val canAdjustThinkingBudget = remember(provider, currentModel) {
        provider?.let { supportsThinkingBudgetControl(it, currentModel) } == true
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.model_thinking_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (canAdjustThinkingBudget) stringResource(R.string.model_thinking_adjustable_hint) else stringResource(R.string.model_thinking_server_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (provider == null || currentModel.isBlank() || !selectedModelSupportsReasoning) {
                NoticeCard(
                    title = stringResource(R.string.model_thinking_not_enabled_title),
                    body = stringResource(R.string.model_thinking_not_enabled_body),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(ChatMediumCardRadius),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = provider.name.ifBlank { stringResource(R.string.model_current_provider) },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = buildString {
                                    append(currentModel)
                                    append(" · ")
                                    append(resolveReasoningBudgetLabel(provider.thinkingBudget))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            )
                        }
                    }
                }

                if (canAdjustThinkingBudget) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.model_thinking_degree),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ReasoningBudgetPreset.entries.forEach { preset ->
                            SheetOptionRow(
                                title = preset.label,
                                supportingText = if (preset.budget == null) {
                                    stringResource(R.string.model_budget_auto)
                                } else {
                                    "预估约 ${preset.budget} tokens"
                                },
                                selected = preset.budget == provider.thinkingBudget,
                                onClick = {
                                    onUpdateThinkingBudget(provider.id, preset.budget)
                                },
                            )
                        }
                    }
                }

                if (reasoningBudgetHint.isNotBlank()) {
                    NoticeCard(
                        title = if (canAdjustThinkingBudget) stringResource(R.string.model_hint_adjustable) else stringResource(R.string.model_hint_api_limit),
                        body = reasoningBudgetHint,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                if (!canAdjustThinkingBudget) {
                    NarraFilledTonalButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSavingModel,
                    ) {
                        Text(stringResource(R.string.model_got_it))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    providerOptions: List<ProviderSettings>,
    currentProviderId: String,
    currentModel: String,
    sheetTitle: String = "选择模型",
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    onDismissRequest: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    quickActions: (ProviderSettings?) -> List<ModelPickerQuickAction> = { emptyList() },
    onSelectQuickAction: ((String, String) -> Unit)? = null,
    onSelectModel: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProviderId by rememberSaveable(currentProviderId, providerOptions) {
        mutableStateOf(currentProviderId)
    }
    var searchQuery by rememberSaveable(selectedProviderId) { mutableStateOf("") }
    val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }
    val selectedProvider = remember(providerOptions, selectedProviderId) {
        providerOptions.firstOrNull { it.id == selectedProviderId }
            ?: providerOptions.firstOrNull()
    }
    val matchedProviderOptions = remember(providerOptions, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            providerOptions.filter { provider ->
                buildProviderSearchKeywords(provider).contains(normalizedQuery)
            }
        }
    }
    val visibleProviderOptions = remember(providerOptions, normalizedQuery, matchedProviderOptions) {
        when {
            normalizedQuery.isBlank() -> providerOptions
            matchedProviderOptions.isNotEmpty() -> matchedProviderOptions
            else -> providerOptions
        }
    }
    val selectedProviderMatchesSearch = remember(selectedProvider, matchedProviderOptions, normalizedQuery) {
        normalizedQuery.isBlank() || selectedProvider?.id?.let { providerId ->
            matchedProviderOptions.any { it.id == providerId }
        } == true
    }
    val modelInfos = remember(selectedProvider) {
        buildList {
            addAll(selectedProvider?.resolvedModels().orEmpty())
            val selectedModelId = selectedProvider?.selectedModel.orEmpty()
            if (selectedModelId.isNotBlank() && none { it.modelId == selectedModelId }) {
                add(
                    ModelInfo(
                        modelId = selectedModelId,
                        displayName = selectedModelId,
                    ),
                )
            }
        }
    }
    val filteredModelInfos = remember(modelInfos, normalizedQuery, selectedProviderMatchesSearch, matchedProviderOptions) {
        filterModelInfosForQuery(
            modelInfos = modelInfos,
            normalizedQuery = normalizedQuery,
            providerMatchesQuery = normalizedQuery.isNotBlank() &&
                matchedProviderOptions.isNotEmpty() &&
                selectedProviderMatchesSearch,
        )
    }
    val quickActionOptions = remember(selectedProvider) {
        quickActions(selectedProvider)
    }
    val selectedProviderHasCredentials = selectedProvider?.hasBaseCredentials() == true
    val isSelectedProviderLoading = isLoadingModels && loadingProviderId == selectedProvider?.id

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (providerOptions.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleProviderOptions, key = { it.id }) { provider ->
                        FilterChip(
                            selected = provider.id == selectedProvider?.id,
                            onClick = {
                                selectedProviderId = provider.id
                                searchQuery = ""
                                onSelectProvider(provider.id)
                            },
                            label = {
                                Text(
                                    text = provider.name.ifBlank { stringResource(R.string.model_unnamed_provider) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                ModelIcon(
                                    modelName = provider.selectedModel.ifBlank { provider.name },
                                    size = 18.dp,
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.model_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
            )

            if (quickActionOptions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    quickActionOptions.forEach { option ->
                        SheetOptionRow(
                            title = option.title,
                            supportingText = option.supportingText.ifBlank { null },
                            selected = option.selected,
                            onClick = {
                                selectedProvider?.id?.let { providerId ->
                                    onSelectQuickAction?.invoke(providerId, option.id)
                                }
                            },
                        )
                    }
                }
            }

            when {
                !selectedProviderHasCredentials -> {
                    NoticeCard(
                        title = stringResource(R.string.model_no_credentials_title),
                        body = stringResource(R.string.model_no_credentials_body),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                normalizedQuery.isNotBlank() &&
                    matchedProviderOptions.isNotEmpty() &&
                    !selectedProviderMatchesSearch &&
                    filteredModelInfos.isEmpty() -> {
                    NoticeCard(
                        title = stringResource(R.string.model_filtered_providers_title),
                        body = "上方已找到 ${matchedProviderOptions.size} 个匹配提供商，点一下对应提供商后就能查看它下面的模型。",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                modelInfos.isEmpty() -> {
                    NoticeCard(
                        title = stringResource(R.string.model_list_empty_title),
                        body = stringResource(R.string.model_list_empty_body),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                filteredModelInfos.isEmpty() -> {
                    NoticeCard(
                        title = stringResource(R.string.model_not_found_title),
                        body = stringResource(R.string.model_not_found_body),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(filteredModelInfos, key = { it.modelId }) { model ->
                            val isCurrentModel =
                                model.modelId == currentModel && selectedProvider?.id == currentProviderId
                            SheetOptionRow(
                                title = model.displayName,
                                selected = isCurrentModel,
                                onClick = {
                                    selectedProvider?.id?.let { providerId ->
                                        onSelectModel(providerId, model.modelId)
                                    }
                                },
                                onLongClick = {
                                    selectedProvider?.id?.let { providerId ->
                                        onDismissRequest()
                                        onOpenProviderDetail(providerId)
                                    }
                                },
                                leadingContent = {
                                    ModelIcon(modelName = model.modelId, size = 36.dp)
                                },
                                supportingContent = model.abilities
                                    .ifEmpty { inferModelAbilities(model.modelId) }
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { abilities ->
                                        {
                                            ModelAbilityIconRow(abilities = abilities)
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SheetOptionRow(
    title: String,
    supportingText: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val headlineColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingSpacing = when {
        !supportingText.isNullOrBlank() -> 8.dp
        supportingContent != null -> 6.dp
        else -> 0.dp
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(ChatMediumCardRadius))
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier.clickable(onClick = onClick)
            },
        )

    Surface(
        modifier = rowModifier,
        shape = RoundedCornerShape(ChatMediumCardRadius),
        color = containerColor,
        contentColor = headlineColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(supportingSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = headlineColor,
                    )
                    if (selected) {
                        StatusPill(
                            text = stringResource(R.string.model_current_label),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                    )
                }
                supportingContent?.invoke()
            }
        }
    }
}

@Composable
internal fun ModelAbilityIconRow(abilities: Set<ModelAbility>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        abilities.sortedBy(ModelAbility::ordinal).forEach { ability ->
            ModelAbilityIconBadge(ability = ability)
        }
    }
}

@Composable
private fun ModelAbilityIconBadge(ability: ModelAbility) {
    val (containerColor, contentColor) = modelAbilityBadgeColors(ability)

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = modelAbilityIcon(ability),
                contentDescription = ability.label,
                modifier = Modifier.size(13.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun modelAbilityBadgeColors(ability: ModelAbility): Pair<Color, Color> {
    val isDark = isSystemInDarkTheme()
    return when (ability) {
        ModelAbility.VISION -> if (isDark) Color(0xFF1B3A2E) to Color(0xFF74D7A8) else Color(0xFFE8F5E9) to Color(0xFF4CAF50)
        ModelAbility.IMAGE_EDITING -> if (isDark) Color(0xFF253245) to Color(0xFF90CAF9) else Color(0xFFE8F0FE) to Color(0xFF3F51B5)
        ModelAbility.REASONING -> if (isDark) Color(0xFF1B2A45) to Color(0xFF8AB4F8) else Color(0xFFE3F2FD) to Color(0xFF2196F3)
        ModelAbility.TOOL -> if (isDark) Color(0xFF3D2810) to Color(0xFFD4956A) else Color(0xFFFFF3E0) to Color(0xFFFF9800)
        ModelAbility.IMAGE_GENERATION -> if (isDark) Color(0xFF2D1B3D) to Color(0xFFCE93D8) else Color(0xFFF3E5F5) to Color(0xFF9C27B0)
    }
}

private fun modelAbilityIcon(ability: ModelAbility): ImageVector {
    return when (ability) {
        ModelAbility.VISION -> Icons.Outlined.Visibility
        ModelAbility.IMAGE_EDITING -> Icons.Outlined.Edit
        ModelAbility.REASONING -> Icons.Outlined.Psychology
        ModelAbility.TOOL -> Icons.Outlined.Build
        ModelAbility.IMAGE_GENERATION -> Icons.Default.Image
    }
}

internal fun buildModelSearchKeywords(model: ModelInfo): String {
    val abilities = model.abilities.ifEmpty { inferModelAbilities(model.modelId) }
    return buildString {
        append(model.modelId.lowercase())
        append(' ')
        append(model.displayName.lowercase())
        if (abilities.isNotEmpty()) {
            append(' ')
            append(abilities.joinToString(separator = " ") { it.label.lowercase() })
        }
    }
}

internal fun buildProviderSearchKeywords(provider: ProviderSettings): String {
    return buildString {
        append(provider.id.lowercase())
        append(' ')
        append(provider.name.ifBlank { "未命名提供商" }.lowercase())
        provider.baseUrl.trim().takeIf { it.isNotBlank() }?.let { baseUrl ->
            append(' ')
            append(baseUrl.lowercase())
        }
        provider.selectedModel.trim().takeIf { it.isNotBlank() }?.let { modelId ->
            append(' ')
            append(modelId.lowercase())
        }
    }
}

internal fun filterModelInfosForQuery(
    modelInfos: List<ModelInfo>,
    normalizedQuery: String,
    providerMatchesQuery: Boolean,
): List<ModelInfo> {
    if (normalizedQuery.isBlank()) {
        return modelInfos
    }
    if (providerMatchesQuery) {
        return modelInfos
    }
    return modelInfos.filter { model ->
        buildModelSearchKeywords(model).contains(normalizedQuery)
    }
}
