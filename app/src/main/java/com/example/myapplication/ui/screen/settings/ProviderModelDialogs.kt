package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.NarraTextButton

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
    val border = if (isSelected) palette.accentStrong.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.2f)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = brandColor.copy(alpha = 0.2f), modifier = Modifier.size(44.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ModelIcon(modelName = modelInfo.modelId, size = 24.dp)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = palette.title,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tertiaryBg = MaterialTheme.colorScheme.tertiaryContainer
                    val tertiaryFg = MaterialTheme.colorScheme.onTertiaryContainer
                    Surface(shape = RoundedCornerShape(50), color = tertiaryBg, contentColor = tertiaryFg) {
                        Text("聊天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    val secondaryBg = MaterialTheme.colorScheme.secondaryContainer
                    val secondaryFg = MaterialTheme.colorScheme.onSecondaryContainer
                    val hasVision = modelInfo.abilities.contains(ModelAbility.VISION)
                    Surface(shape = RoundedCornerShape(50), color = secondaryBg, contentColor = secondaryFg) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            if (hasVision) {
                                Icon(Icons.Outlined.Image, contentDescription = "Vision", modifier = Modifier.size(12.dp))
                            }
                            Text(">", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    modelInfo.abilities.filter { it != ModelAbility.VISION }.forEach { ability ->
                        ModelAbilityChip(ability = ability)
                    }
                }
            }

            com.example.myapplication.ui.component.NarraIconButton(onClick = onEditAbilities) {
                Icon(Icons.Outlined.Settings, contentDescription = "Edit Abilities", tint = palette.title)
            }

            com.example.myapplication.ui.component.NarraIconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "移除模型", tint = palette.body.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
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
    var editedAbilities by remember(modelInfo.modelId, modelInfo.abilitiesCustomized, modelInfo.abilities) {
        mutableStateOf(modelInfo.abilities)
    }
    val shouldResetToAuto = editedAbilities == autoAbilities
    // 当前已手动覆盖时，次级按钮文案换成"恢复自动"，点击直接把 abilities 置空让后端走自动推断。
    val dismissLabel = if (modelInfo.abilitiesCustomized) "恢复自动" else "取消"
    val handleDismiss: () -> Unit = {
        if (modelInfo.abilitiesCustomized) {
            onSave(null)
        } else {
            onDismissRequest()
        }
    }

    com.example.myapplication.ui.component.NarraAlertDialog(
        title = "覆盖模型能力",
        message = if (modelInfo.abilitiesCustomized) "当前已手动覆盖。" else "手动勾选你希望覆盖的能力。",
        onDismiss = handleDismiss,
        onConfirm = { onSave(if (shouldResetToAuto) null else editedAbilities) },
        confirmLabel = "保存",
        dismissLabel = dismissLabel,
        content = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelAbility.entries.forEach { ability ->
                    FilterChip(
                        selected = ability in editedAbilities,
                        onClick = {
                            editedAbilities = if (ability in editedAbilities) editedAbilities - ability else editedAbilities + ability
                        },
                        label = { Text(ability.label) },
                        leadingIcon = { Icon(abilityIcon(ability), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
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
        Surface(shape = CircleShape, color = chipColor, contentColor = chipContentColor) {
            Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = ability.label, modifier = Modifier.size(14.dp), tint = chipContentColor)
            }
        }
    } else {
        Surface(shape = RoundedCornerShape(50), color = chipColor, contentColor = chipContentColor) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = icon, contentDescription = ability.label, modifier = Modifier.size(13.dp), tint = chipContentColor)
                Text(text = ability.label, style = MaterialTheme.typography.labelSmall, color = chipContentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun abilityColors(ability: ModelAbility): Pair<Color, Color> {
    val isDark = isSystemInDarkTheme()
    return when (ability) {
        ModelAbility.VISION -> if (isDark) Color(0xFF1B3A2E) to Color(0xFF74D7A8) else Color(0xFFE8F5E9) to Color(0xFF4CAF50)
        ModelAbility.REASONING -> if (isDark) Color(0xFF1B2A45) to Color(0xFF8AB4F8) else Color(0xFFE3F2FD) to Color(0xFF2196F3)
        ModelAbility.TOOL -> if (isDark) Color(0xFF3D2810) to Color(0xFFD4956A) else Color(0xFFFFF3E0) to Color(0xFFFF9800)
        ModelAbility.IMAGE_GENERATION -> if (isDark) Color(0xFF2D1B3D) to Color(0xFFCE93D8) else Color(0xFFF3E5F5) to Color(0xFF9C27B0)
    }
}

private fun abilityIcon(ability: ModelAbility): ImageVector {
    return when (ability) {
        ModelAbility.VISION -> Icons.Outlined.Visibility
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
        else fetchedModels.filter { it.modelId.lowercase().contains(query) || it.displayName.lowercase().contains(query) }
    }

    val newSelectedCount = selectedIds.count { it !in existingModelIds }
    val removedCount = existingModelIds.count { it !in selectedIds }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.border) },
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            color = palette.accentStrong,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = palette.body,
                            modifier = Modifier.size(20.dp),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索模型名称...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body.copy(alpha = 0.6f),
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.title),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "清除",
                                tint = palette.body,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (filteredModels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NarraOutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                        ) {
                            Text("取消", fontWeight = FontWeight.Bold)
                        }
                        NarraButton(
                            onClick = {
                                onConfirm(selectedIds)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentStrong,
                                contentColor = palette.accentOnStrong,
                            ),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
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
    val borderColor = if (isChecked) palette.accentStrong.copy(alpha = 0.3f) else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = palette.accentStrong,
                    uncheckedColor = palette.border,
                    checkmarkColor = palette.accentOnStrong,
                ),
                modifier = Modifier.size(22.dp),
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.surfaceTint,
                border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                modifier = Modifier.size(36.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ModelIcon(modelName = modelInfo.modelId, size = 20.dp)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = palette.title,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isExisting) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.subtleChip,
                            contentColor = palette.subtleChipContent,
                        ) {
                            Text(
                                "已有",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val isDark = isSystemInDarkTheme()
                    val ioBg = if (isDark) Color(0xFF1B3A2E) else Color(0xFFE8F5E9)
                    val ioColor = if (isDark) Color(0xFF74D7A8) else Color(0xFF4CAF50)
                    val hasVision = modelInfo.abilities.contains(ModelAbility.VISION)

                    Surface(shape = RoundedCornerShape(50), color = ioBg, contentColor = ioColor) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            if (hasVision) {
                                Icon(Icons.Outlined.Image, contentDescription = "Vision", modifier = Modifier.size(9.dp))
                            }
                            Text(">", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
