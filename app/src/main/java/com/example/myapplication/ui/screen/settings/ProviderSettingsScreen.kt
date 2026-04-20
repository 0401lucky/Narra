package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.example.myapplication.ui.component.TopAppSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.brandColor
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
fun ProviderSettingsScreen(
    uiState: SettingsUiState,
    onEnsureProviderDrafts: () -> Unit,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onAddProviderFromTemplate: (ProviderTemplate) -> String,
    onOpenProviderDetail: (String) -> Unit,
    onCheckProviderHealth: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showTemplateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingProviderDetailId by rememberSaveable { mutableStateOf("") }
    val isTemplateDialogVisible = showTemplateDialog || uiState.showTemplateDialog

    LaunchedEffect(uiState.providers.isEmpty()) {
        if (uiState.providers.isEmpty()) {
            onEnsureProviderDrafts()
        }
    }

    LaunchedEffect(uiState.showTemplateDialog) {
        if (uiState.showTemplateDialog) {
            showTemplateDialog = true
        }
    }

    LaunchedEffect(pendingProviderDetailId, uiState.providers) {
        val targetProviderId = pendingProviderDetailId
        if (targetProviderId.isBlank()) {
            return@LaunchedEffect
        }
        if (uiState.providers.any { it.id == targetProviderId }) {
            pendingProviderDetailId = ""
            onOpenProviderDetail(targetProviderId)
        }
    }

    val openAddProviderDialog = {
        showTemplateDialog = true
        onShowAddDialog()
    }
    val closeAddProviderDialog = {
        showTemplateDialog = false
        onDismissAddDialog()
    }

    BackHandler(enabled = !isTemplateDialogVisible) {
        onNavigateBack()
    }

    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    var searchQuery by remember { mutableStateOf("") }
    val filteredProviders = remember(uiState.providers, searchQuery) {
        val normalizedQuery = searchQuery.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            uiState.providers
        } else {
            uiState.providers.filter { provider ->
                provider.matchesSearch(normalizedQuery)
            }
        }
    }

    // 进入列表页自动检测所有提供商
    LaunchedEffect(
        uiState.providers.map { provider ->
            listOf(
                provider.id,
                provider.baseUrl,
                provider.apiKey,
                provider.resolvedApiProtocol().name,
            )
        },
    ) {
        uiState.providers.forEach { provider ->
            if (provider.hasBaseCredentials() &&
                uiState.connectionHealthMap[provider.id] == null
            ) {
                onCheckProviderHealth(provider.id)
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "提供商",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = openAddProviderDialog,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 154.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 4.dp,
                    end = 20.dp,
                    bottom = 28.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ProviderSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                    )
                }

                if (filteredProviders.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SettingsPlaceholderRow(
                            title = if (uiState.providers.isEmpty()) "还没有提供商" else "没有匹配的提供商",
                            subtitle = if (uiState.providers.isEmpty()) {
                                "先新增一个提供商，再填写 Base URL、API Key 和模型。"
                            } else {
                                "换个关键词试试，可以搜索名称、类型、地址或模型名。"
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            onClick = openAddProviderDialog,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = palette.surface.copy(alpha = 0.74f),
                                contentColor = palette.accentStrong,
                            ),
                            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "新增提供商",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentStrong,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredProviders, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            health = uiState.connectionHealthMap[provider.id] ?: ConnectionHealth.UNKNOWN,
                            onClick = {
                                onOpenProviderDetail(provider.id)
                            },
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

    if (isTemplateDialogVisible) {
        ProviderTemplateDialog(
            onSelectTemplate = { template ->
                showTemplateDialog = false
                val newId = onAddProviderFromTemplate(template)
                pendingProviderDetailId = newId
            },
            onDismiss = closeAddProviderDialog,
        )
    }
}

@Composable
private fun ProviderSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val palette = rememberSettingsPalette()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜索提供商", color = palette.body) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = palette.body)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = rememberSettingsOutlineColors(),
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    provider: ProviderSettings,
    health: ConnectionHealth,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val isDark = isSystemInDarkTheme()
    val providerType = provider.resolvedType()
    val brandColor = providerType.brandColor(isDark)
    val modelCount = provider.resolvedModels().size

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface.copy(alpha = 0.7f),
            contentColor = palette.title,
        ),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProviderBrandIcon(
                    provider = provider,
                    brandColor = brandColor,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "详情",
                    tint = palette.body.copy(alpha = 0.6f),
                )
            }

            Text(
                text = provider.name.ifBlank { providerType.displayName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = palette.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionHealthDot(health = health)
                Text(
                    text = provider.toCardSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsStatusPill(
                    text = if (provider.enabled) "启用" else "停用",
                    containerColor = if (provider.enabled) palette.accentSoft else palette.surfaceTint,
                    contentColor = if (provider.enabled) palette.accent else palette.body,
                )
                SettingsStatusPill(
                    text = "$modelCount 个模型",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
            }
        }
    }
}

/** 8dp 圆点信号灯，CHECKING 状态带脉冲动画。 */
@Composable
fun ConnectionHealthDot(
    health: ConnectionHealth,
    modifier: Modifier = Modifier,
) {
    val color = when (health) {
        ConnectionHealth.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
        ConnectionHealth.CHECKING -> Color(0xFFFFC107)
        ConnectionHealth.HEALTHY -> Color(0xFF4CAF50)
        ConnectionHealth.UNHEALTHY -> Color(0xFFEF5350)
    }

    val alpha = if (health == ConnectionHealth.CHECKING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
        pulse
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(9.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ProviderBrandIcon(
    provider: ProviderSettings,
    brandColor: Color,
) {
    val palette = rememberSettingsPalette()
    val providerType = provider.resolvedType()
    val iconRes = providerType.iconRes

    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = brandColor.copy(alpha = 0.15f),
        contentColor = palette.title,
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = providerType.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = brandColor,
                )
            }
        }
    }
}

private fun ProviderSettings.toCardSubtitle(): String {
    return when {
        hasRequiredConfig() -> "${baseUrl.toCompactBaseUrl()} · $selectedModel"
        hasBaseCredentials() -> "${baseUrl.toCompactBaseUrl()} · 未选择模型"
        else -> "Base URL 和 API Key 还没填完整"
    }
}

private fun ProviderSettings.matchesSearch(normalizedQuery: String): Boolean {
    return buildString {
        append(name)
        append('\n')
        append(resolvedType().displayName)
        append('\n')
        append(baseUrl)
        append('\n')
        append(selectedModel)
        append('\n')
        append(resolvedModelIds().joinToString("\n"))
    }.lowercase().contains(normalizedQuery)
}

private fun String.toCompactBaseUrl(): String {
    val normalized = trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
    if (normalized.isBlank()) {
        return "未配置"
    }
    return normalized.take(28)
}
