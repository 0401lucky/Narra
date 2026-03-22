package com.example.myapplication.ui.screen.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
fun ProviderDetailScreen(
    providerId: String,
    uiState: SettingsUiState,
    onUpdateProviderName: (String, String) -> Unit,
    onUpdateProviderBaseUrl: (String, String) -> Unit,
    onUpdateProviderApiKey: (String, String) -> Unit,
    onUpdateProviderSelectedModel: (String, String) -> Unit,
    onUpdateProviderModelAbilities: (String, String, Set<ModelAbility>?) -> Unit,
    onLoadModels: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmFetchedModels: (String, Set<String>) -> Unit,
    onDismissFetchedModels: () -> Unit,
    onRemoveModel: (String, String) -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val provider = uiState.providers.firstOrNull { it.id == providerId }

    if (provider == null) {
        Scaffold(
            topBar = { SettingsTopBar(title = "未找到", onNavigateBack = onNavigateBack) },
            containerColor = palette.background,
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
                SettingsNoticeCard(
                    title = "没有找到这个提供商",
                    body = "这个草稿可能已经被删除，返回上一页后重新选择一个可用的提供商。",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        return
    }

    var selectedTab by rememberSaveable(providerId) { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            ProviderDetailTopBar(provider = provider, onNavigateBack = onNavigateBack)
        },
        bottomBar = {
            SleekBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab == 1,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                val canLoadModels = !uiState.isLoadingModels && provider.hasBaseCredentials()
                val isFetching = uiState.isLoadingModels && uiState.loadingProviderId == provider.id
                val modelCount = provider.resolvedModels().size
                
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border.copy(alpha=0.3f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = "Models", Modifier.size(24.dp), tint = palette.title)
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text(
                                    text = modelCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Button(
                            onClick = { if (canLoadModels && !isFetching) onLoadModels(provider.id) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong, contentColor = palette.accentOnStrong),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp,0.dp,0.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (isFetching) "获取中..." else "添加新模型", 
                                    style = MaterialTheme.typography.titleSmall, 
                                    fontWeight = FontWeight.Bold 
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        Crossfade(targetState = selectedTab, label = "tab_crossfade", modifier = Modifier.padding(innerPadding)) { tab ->
            when (tab) {
                0 -> ConfigTabContent(
                    provider = provider,
                    isSaving = uiState.isSaving,
                    providerCount = uiState.providers.size,
                    onUpdateProviderName = onUpdateProviderName,
                    onUpdateProviderBaseUrl = onUpdateProviderBaseUrl,
                    onUpdateProviderApiKey = onUpdateProviderApiKey,
                    onToggleProviderEnabled = onToggleProviderEnabled,
                    onSave = onSave,
                    onDeleteProvider = onDeleteProvider,
                    onNavigateBack = onNavigateBack,
                )
                1 -> ModelTabContent(
                    provider = provider,
                    uiState = uiState,
                    onUpdateProviderSelectedModel = onUpdateProviderSelectedModel,
                    onUpdateProviderModelAbilities = onUpdateProviderModelAbilities,
                    onRemoveModel = onRemoveModel,
                    onConfirmFetchedModels = onConfirmFetchedModels,
                    onDismissFetchedModels = onDismissFetchedModels,
                )
            }
        }
    }
}

@Composable
private fun ProviderDetailTopBar(
    provider: ProviderSettings,
    onNavigateBack: () -> Unit
) {
    val palette = rememberSettingsPalette()
    val brandColor = provider.resolvedType().brandColor(isSystemInDarkTheme())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.title, modifier = Modifier.size(24.dp))
            }
            Surface(
                shape = CircleShape,
                color = brandColor.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                val iconRes = provider.resolvedType().iconRes
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            provider.name.firstOrNull()?.uppercase() ?: "?",
                            color = brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            Text(
                text = provider.name.ifBlank { "New Provider" },
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.5.sp),
                fontWeight = FontWeight.Bold,
                color = palette.title
            )
        }
        Icon(Icons.Outlined.IosShare, contentDescription = "Share", tint = palette.title, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ConfigTabContent(
    provider: ProviderSettings,
    isSaving: Boolean,
    providerCount: Int,
    onUpdateProviderName: (String, String) -> Unit,
    onUpdateProviderBaseUrl: (String, String) -> Unit,
    onUpdateProviderApiKey: (String, String) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showApiKey by rememberSaveable(provider.id) { mutableStateOf(false) }
    val palette = rememberSettingsPalette()

    val outlineColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accentStrong,
        unfocusedBorderColor = palette.border.copy(alpha = 0.8f),
        focusedLabelColor = palette.accentStrong,
        unfocusedLabelColor = palette.body,
        unfocusedContainerColor = palette.surface,
        focusedContainerColor = palette.surface,
        cursorColor = palette.accentStrong
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SettingSwitchRow(
                title = "是否启用",
                checked = provider.enabled,
                onCheckedChange = { onToggleProviderEnabled(provider.id) }
            )
        }

        item {
            OutlinedTextField(
                value = provider.name,
                onValueChange = { onUpdateProviderName(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称", fontWeight = FontWeight.Medium) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
            )
        }

        item {
            OutlinedTextField(
                value = provider.apiKey,
                onValueChange = { onUpdateProviderApiKey(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key", fontWeight = FontWeight.Medium) },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null, tint = palette.accentStrong)
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = provider.baseUrl,
                onValueChange = { onUpdateProviderBaseUrl(provider.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base Url", fontWeight = FontWeight.Medium) },
                placeholder = { Text("https://api.openai.com/v1/") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = outlineColors,
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceTint,
                    border = BorderStroke(1.dp, palette.border.copy(alpha=0.4f)),
                    modifier = Modifier.size(56.dp).clip(CircleShape).clickable(enabled = providerCount > 1) {
                        onDeleteProvider(provider.id)
                        onNavigateBack()
                    }
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", Modifier.padding(16.dp), tint = if (providerCount > 1) palette.title else palette.body.copy(alpha = 0.3f))
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong, contentColor = palette.accentOnStrong)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (isSaving) "保存中..." else "保存设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelTabContent(
    provider: ProviderSettings,
    uiState: SettingsUiState,
    onUpdateProviderSelectedModel: (String, String) -> Unit,
    onUpdateProviderModelAbilities: (String, String, Set<ModelAbility>?) -> Unit,
    onRemoveModel: (String, String) -> Unit,
    onConfirmFetchedModels: (String, Set<String>) -> Unit,
    onDismissFetchedModels: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val modelInfos = provider.resolvedModels()
    var editingModelInfo by remember(provider.id) { mutableStateOf<ModelInfo?>(null) }
    val showFetchedDialog = uiState.pendingFetchedModels.isNotEmpty() &&
        uiState.pendingFetchProviderId == provider.id

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (modelInfos.isEmpty()) {
                item {
                    SettingsPlaceholderRow(
                        title = "暂无模型",
                        subtitle = if (provider.hasBaseCredentials()) "点击右下角浮动按钮获取模型，然后选择要添加的。" else "先在配置页补全信息，再来获取。",
                    )
                }
            } else {
                items(modelInfos, key = { it.modelId }) { modelInfo ->
                    PremiumModelCard(
                        modelInfo = modelInfo,
                        isSelected = modelInfo.modelId == provider.selectedModel,
                        brandColor = provider.resolvedType().brandColor(isSystemInDarkTheme()),
                        onClick = { onUpdateProviderSelectedModel(provider.id, modelInfo.modelId) },
                        onEditAbilities = { editingModelInfo = modelInfo },
                        onRemove = { onRemoveModel(provider.id, modelInfo.modelId) },
                    )
                }
            }
        }
    }

    editingModelInfo?.let { modelInfo ->
        ModelAbilityOverrideDialog(
            modelInfo = modelInfo,
            onDismissRequest = { editingModelInfo = null },
            onSave = { abilities ->
                onUpdateProviderModelAbilities(provider.id, modelInfo.modelId, abilities)
                editingModelInfo = null
            },
        )
    }

    if (showFetchedDialog) {
        FetchedModelSelectionBottomSheet(
            fetchedModels = uiState.pendingFetchedModels,
            existingModelIds = modelInfos.map { it.modelId }.toSet(),
            onConfirm = { selectedIds ->
                onConfirmFetchedModels(provider.id, selectedIds)
            },
            onDismiss = onDismissFetchedModels,
        )
    }
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.title, fontWeight = FontWeight.Bold)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.surface,
                checkedTrackColor = palette.accentStrong,
                uncheckedThumbColor = palette.body.copy(alpha=0.6f),
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = palette.surfaceTint
            )
        )
    }
}

@Composable
fun SleekBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp).fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = palette.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, palette.border.copy(alpha=0.2f))
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SleekBottomNavItem(
                icon = Icons.Outlined.Build,
                label = "配置",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )
            SleekBottomNavItem(
                icon = Icons.Outlined.Inventory2,
                label = "模型",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SleekBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberSettingsPalette()
    val bg = if (selected) palette.accentSoft else Color.Transparent
    val contentColor = if (selected) palette.accentStrong else palette.body.copy(alpha=0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            if (selected) {
                Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            } else {
                Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PremiumModelCard(
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
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = brandColor.copy(alpha=0.2f), modifier = Modifier.size(44.dp)) {
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
                        overflow = TextOverflow.Ellipsis,
                        color = palette.title,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chat chip
                    val isDark = isSystemInDarkTheme()
                    val chatBg = if (isDark) Color(0xFF1B2A45) else Color(0xFFE3F2FD)
                    val chatColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2196F3)
                    Surface(shape = RoundedCornerShape(50), color = chatBg, contentColor = chatColor) {
                        Text("聊天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    
                    // Input Output Chip (T > T or T img > T)
                    val ioBg = if (isDark) Color(0xFF1B3A2E) else Color(0xFFE8F5E9)
                    val ioColor = if (isDark) Color(0xFF74D7A8) else Color(0xFF4CAF50)
                    val hasVision = modelInfo.abilities.contains(ModelAbility.VISION)
                    
                    Surface(shape = RoundedCornerShape(50), color = ioBg, contentColor = ioColor) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            if (hasVision) {
                                Icon(Icons.Outlined.Image, contentDescription = "Vision", modifier = Modifier.size(12.dp))
                            }
                            Text(">", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("T", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Specific Tool/Reasoning chips
                    modelInfo.abilities.filter { it != ModelAbility.VISION }.forEach { ability -> 
                        ModelAbilityChip(ability = ability) 
                    }
                }
            }

            IconButton(onClick = onEditAbilities) {
                Icon(Icons.Outlined.Settings, contentDescription = "Edit Abilities", tint = palette.title)
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "移除模型", tint = palette.body.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelAbilityOverrideDialog(
    modelInfo: ModelInfo,
    onDismissRequest: () -> Unit,
    onSave: (Set<ModelAbility>?) -> Unit,
) {
    val autoAbilities = remember(modelInfo.modelId) {
        inferModelAbilities(modelInfo.modelId)
    }
    var editedAbilities by remember(modelInfo.modelId, modelInfo.abilitiesCustomized, modelInfo.abilities) {
        mutableStateOf(modelInfo.abilities)
    }
    val shouldResetToAuto = editedAbilities == autoAbilities

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(text = "覆盖模型能力", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (modelInfo.abilitiesCustomized) "当前已手动覆盖。" else "手动勾选你希望覆盖的能力。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelAbility.entries.forEach { ability ->
                        FilterChip(
                            selected = ability in editedAbilities,
                            onClick = {
                                editedAbilities = if (ability in editedAbilities) editedAbilities - ability else editedAbilities + ability
                            },
                            label = { Text(ability.label) },
                            leadingIcon = { Icon(abilityIcon(ability), contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (modelInfo.abilitiesCustomized) {
                    TextButton(onClick = { onSave(null) }) { Text("恢复自动") }
                }
                TextButton(onClick = onDismissRequest) { Text("取消") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (shouldResetToAuto) null else editedAbilities) }) { Text("保存") }
        },
    )
}

@Composable
private fun ModelAbilityChip(ability: ModelAbility) {
    val (chipColor, chipContentColor) = abilityColors(ability)
    val icon = abilityIcon(ability)
    
    // For TOOL and REASONING, we just want a small circular chip with just the icon
    if (ability == ModelAbility.TOOL || ability == ModelAbility.REASONING) {
        Surface(shape = CircleShape, color = chipColor, contentColor = chipContentColor) {
            Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = ability.label, modifier = Modifier.size(14.dp), tint = chipContentColor)
            }
        }
    } else {
        // Fallback for others (though VISION is handled customly in the PremiumModelCard now)
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
private fun FetchedModelSelectionBottomSheet(
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
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header: Title + Stats ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "选择模型",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.title
                        )
                        Text(
                            buildString {
                                append("已选 ${selectedIds.size}/${fetchedModels.size}")
                                if (newSelectedCount > 0) append(" · +$newSelectedCount 新增")
                                if (removedCount > 0) append(" · -$removedCount 移除")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body
                        )
                    }
                    TextButton(onClick = {
                        if (selectedIds.size == fetchedModels.size) {
                            selectedIds = emptySet()
                        } else {
                            selectedIds = fetchedModels.map { it.modelId }.toSet()
                        }
                    }) {
                        Text(
                            if (selectedIds.size == fetchedModels.size) "取消全选" else "全选",
                            color = palette.accentStrong,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // ── Search bar ──
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = palette.body,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索模型名称...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body.copy(alpha = 0.6f)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
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
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.4f))

            // ── Model List ──
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
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "没有找到匹配的模型",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.body
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
                                }
                            )
                        }
                    }
                }
            }

            // ── Bottom Action Bar ──
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.border),
                    ) {
                        Text("取消", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onConfirm(selectedIds)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentStrong,
                            contentColor = palette.accentOnStrong
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                "确认选择",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                } // Close Column
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FetchedModelRow(
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
                    checkmarkColor = palette.accentOnStrong
                ),
                modifier = Modifier.size(22.dp)
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.surfaceTint,
                border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ModelIcon(modelName = modelInfo.modelId, size = 20.dp)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isDark = isSystemInDarkTheme()
                    val ioBg = if (isDark) Color(0xFF1B3A2E) else Color(0xFFE8F5E9)
                    val ioColor = if (isDark) Color(0xFF74D7A8) else Color(0xFF4CAF50)
                    val hasVision = modelInfo.abilities.contains(ModelAbility.VISION)

                    Surface(shape = RoundedCornerShape(50), color = ioBg, contentColor = ioColor) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
