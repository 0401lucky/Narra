package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumModelCard(
    modelInfo: ModelInfo,
    brandColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditAbilities: () -> Unit,
    onRemove: () -> Unit = {},
) {
    val palette = rememberSettingsPalette()
    val bg = if (isSelected) palette.accentSoft else palette.surface
    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else palette.title
    val bodyColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else palette.body
    val border =
        if (isSelected) palette.accentStrong.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.2f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NarraShapes.medium)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = NarraShapes.medium,
        color = bg,
        contentColor = titleColor,
        border = BorderStroke(1.dp, border),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                Surface(
                    shape = NarraShapes.small,
                    color = brandColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ModelIcon(modelName = modelInfo.modelId, size = 24.dp)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                ) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                    )
                    if (isSelected) {
                        Text(
                            text = "当前模型",
                            style = MaterialTheme.typography.labelMedium,
                            color = bodyColor,
                        )
                    }
                }
            }
            if (modelInfo.abilities.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_model_abilities_${modelInfo.modelId}"),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    modelInfo.abilities.forEach { ability ->
                        ModelAbilityChip(ability = ability)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarraIconButton(
                    onClick = onEditAbilities,
                    modifier = Modifier.size(NarraMinimumTouchTarget),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "设置模型能力",
                        tint = bodyColor
                    )
                }
                NarraIconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(NarraMinimumTouchTarget),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "移除模型",
                        tint = bodyColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelAbilityOverrideDialog(
    modelInfo: ModelInfo,
    onDismissRequest: () -> Unit,
    onSave: (Set<ModelAbility>?) -> Unit,
) {
    val autoAbilities = remember(modelInfo.modelId) {
        com.example.myapplication.model.inferModelAbilities(modelInfo.modelId)
    }
    var editedAbilities by remember(
        modelInfo.modelId,
        modelInfo.abilitiesCustomized,
        modelInfo.abilities
    ) {
        mutableStateOf(modelInfo.abilities)
    }
    val shouldResetToAuto = editedAbilities == autoAbilities
    com.example.myapplication.ui.component.NarraAlertDialog(
        title = "覆盖模型能力",
        message = if (modelInfo.abilitiesCustomized) "当前已手动覆盖。" else "手动勾选你希望覆盖的能力。",
        onDismiss = onDismissRequest,
        onConfirm = { onSave(if (shouldResetToAuto) null else editedAbilities) },
        confirmLabel = "保存",
        dismissLabel = "取消",
        shape = NarraShapes.large,
        actionContentColor = MaterialTheme.colorScheme.onSurface,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
                ) {
                    ModelAbility.entries.forEach { ability ->
                        FilterChip(
                            selected = ability in editedAbilities,
                            modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                            shape = NarraShapes.pill,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            onClick = {
                                editedAbilities =
                                    if (ability in editedAbilities) editedAbilities - ability else editedAbilities + ability
                            },
                            label = { Text(ability.label) },
                            leadingIcon = {
                                Icon(
                                    abilityIcon(ability),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                        )
                    }
                }
                if (modelInfo.abilitiesCustomized) {
                    NarraTextButton(
                        onClick = { onSave(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text("恢复自动")
                    }
                }
            }
        },
    )
}

@Composable
internal fun ModelAbilityChip(ability: ModelAbility) {
    val (chipColor, chipContentColor) = abilityColors(ability)
    val icon = abilityIcon(ability)

    if (ability == ModelAbility.TOOL || ability == ModelAbility.REASONING) {
        Surface(shape = NarraShapes.pill, color = chipColor, contentColor = chipContentColor) {
            Box(modifier = Modifier.padding(NarraUi.space8), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = ability.label,
                    modifier = Modifier.size(14.dp),
                    tint = chipContentColor
                )
            }
        }
    } else {
        Surface(shape = NarraShapes.pill, color = chipColor, contentColor = chipContentColor) {
            Row(
                modifier = Modifier.padding(horizontal = NarraUi.space8, vertical = NarraUi.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space4)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = chipContentColor
                )
                Text(
                    text = ability.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipContentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun abilityColors(ability: ModelAbility): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (ability) {
        ModelAbility.VISION -> colors.secondaryContainer to colors.onSecondaryContainer
        ModelAbility.IMAGE_EDITING -> colors.tertiaryContainer to colors.onTertiaryContainer
        ModelAbility.REASONING -> colors.primaryContainer to colors.onPrimaryContainer
        ModelAbility.TOOL -> colors.surfaceVariant to colors.onSurfaceVariant
        ModelAbility.IMAGE_GENERATION -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
}

private fun abilityIcon(ability: ModelAbility): ImageVector {
    return when (ability) {
        ModelAbility.VISION -> Icons.Outlined.Visibility
        ModelAbility.IMAGE_EDITING -> Icons.Outlined.Edit
        ModelAbility.REASONING -> Icons.Outlined.Psychology
        ModelAbility.TOOL -> Icons.Outlined.Build
        ModelAbility.IMAGE_GENERATION -> Icons.Outlined.Image
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FetchedModelSelectionBottomSheet(
    fetchedModels: List<ModelInfo>,
    existingModelIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val initialSelection = remember(fetchedModels, existingModelIds) {
        existingModelIds.toSet()
    }
    var selectedIds by remember(fetchedModels) {
        mutableStateOf(initialSelection)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredModels = remember(fetchedModels, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) fetchedModels
        else fetchedModels.filter {
            it.modelId.lowercase().contains(query) || it.displayName.lowercase().contains(query)
        }
    }

    val newSelectedCount = selectedIds.count { it !in existingModelIds }
    val removedCount = existingModelIds.count { it !in selectedIds }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = NarraShapes.large,
        containerColor = palette.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.border) },
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NarraUi.space16)
                    .padding(bottom = NarraUi.space12),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                    ) {
                        Text(
                            "选择模型",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.title,
                        )
                        Text(
                            buildString {
                                append("已选 ${selectedIds.size}/${fetchedModels.size}")
                                if (newSelectedCount > 0) append(" · +$newSelectedCount 新增")
                                if (removedCount > 0) append(" · -$removedCount 移除")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
                        )
                    }
                    NarraTextButton(onClick = {
                        selectedIds = if (selectedIds.size == fetchedModels.size) {
                            emptySet()
                        } else {
                            fetchedModels.map { it.modelId }.toSet()
                        }
                    }) {
                        Text(
                            if (selectedIds.size == fetchedModels.size) "取消全选" else "全选",
                            color = palette.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索模型") },
                    shape = NarraShapes.medium,
                    colors = providerTextFieldColors(palette.surfaceTint),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = palette.body,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            NarraIconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(NarraMinimumTouchTarget),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "清除",
                                    tint = palette.body,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = NarraUi.space16,
                        vertical = NarraUi.space8
                    ),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
                ) {
                    if (filteredModels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = NarraUi.space32),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "没有找到匹配的模型",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body,
                                )
                            }
                        }
                    } else {
                        items(filteredModels, key = { it.modelId }) { model ->
                            val isChecked = model.modelId in selectedIds
                            val isExisting = model.modelId in existingModelIds
                            FetchedModelRow(
                                modelInfo = model,
                                isChecked = isChecked,
                                isExisting = isExisting,
                                onToggle = {
                                    selectedIds = if (isChecked) {
                                        selectedIds - model.modelId
                                    } else {
                                        selectedIds + model.modelId
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = palette.background,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NarraUi.space16, vertical = NarraUi.space16),
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NarraOutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = NarraMinimumTouchTarget),
                            contentPadding = PaddingValues(
                                horizontal = NarraUi.space8,
                                vertical = NarraUi.space12
                            ),
                            shape = NarraShapes.medium,
                            border = BorderStroke(1.dp, palette.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.title),
                        ) {
                            Text("取消", fontWeight = FontWeight.Bold)
                        }
                        NarraButton(
                            onClick = {
                                onConfirm(selectedIds)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1.5f)
                                .heightIn(min = NarraMinimumTouchTarget),
                            contentPadding = PaddingValues(
                                horizontal = NarraUi.space8,
                                vertical = NarraUi.space12
                            ),
                            shape = NarraShapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentStrong,
                                contentColor = palette.accentOnStrong,
                            ),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "确认选择",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FetchedModelRow(
    modelInfo: ModelInfo,
    isChecked: Boolean,
    isExisting: Boolean,
    onToggle: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val bg = if (isChecked) palette.accentSoft else Color.Transparent
    val contentColor =
        if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else palette.title
    val borderColor = if (isChecked) palette.accentStrong.copy(alpha = 0.3f) else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NarraMinimumTouchTarget)
            .clip(NarraShapes.medium)
            .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = NarraShapes.medium,
        color = bg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NarraUi.space12, vertical = NarraUi.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = palette.accentStrong,
                    uncheckedColor = palette.border,
                    checkmarkColor = palette.accentOnStrong,
                ),
                modifier = Modifier.size(22.dp),
            )

            Surface(
                shape = NarraShapes.small,
                color = palette.surfaceTint,
                border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                modifier = Modifier.size(36.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ModelIcon(modelName = modelInfo.modelId, size = 20.dp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
            ) {
                Text(
                    text = modelInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                )
                if (isExisting) {
                    Surface(
                        shape = NarraShapes.pill,
                        color = palette.subtleChip,
                        contentColor = palette.subtleChipContent,
                    ) {
                        Text(
                            "已有",
                            modifier = Modifier.padding(
                                horizontal = NarraUi.space8,
                                vertical = NarraUi.space4
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space4),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
                ) {
                    val ioBg = MaterialTheme.colorScheme.secondaryContainer
                    val ioColor = MaterialTheme.colorScheme.onSecondaryContainer
                    val hasVision = modelInfo.abilities.contains(ModelAbility.VISION)

                    Surface(shape = NarraShapes.pill, color = ioBg, contentColor = ioColor) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = NarraUi.space8,
                                vertical = NarraUi.space4
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NarraUi.space4),
                        ) {
                            Text(
                                "T",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            if (hasVision) {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = "Vision",
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                            Text(
                                ">",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Text(
                                "T",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    modelInfo.abilities.filter { it != ModelAbility.VISION }.forEach { ability ->
                        ModelAbilityChip(ability = ability)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManualAddModelDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (modelId: String, displayName: String) -> Unit,
) {
    val inputColors = providerTextFieldColors(MaterialTheme.colorScheme.surfaceVariant)
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    NarraAlertDialog(
        title = "手动添加模型",
        message = "手动输入模型 ID 和显示名称。适用于未暴露获取接口的提供商。",
        onDismiss = onDismissRequest,
        onConfirm = {
            val cleanedId = modelId.trim()
            if (cleanedId.isBlank()) {
                errorText = "模型 ID 不能为空"
            } else {
                onConfirm(cleanedId, displayName.trim().ifBlank { cleanedId })
                onDismissRequest()
            }
        },
        confirmLabel = "添加",
        dismissLabel = "取消",
        shape = NarraShapes.large,
        actionContentColor = MaterialTheme.colorScheme.onSurface,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = NarraUi.space8),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it; errorText = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                        .semantics { errorText?.let { error(it) } },
                    label = { Text("模型 ID *") },
                    isError = errorText != null,
                    supportingText = { errorText?.let { Text(it) } },
                    colors = inputColors,
                    shape = NarraShapes.medium,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget),
                    label = { Text("显示名称") },
                    colors = inputColors,
                    shape = NarraShapes.medium,
                    singleLine = true,
                )
            }
        }
    )
}
