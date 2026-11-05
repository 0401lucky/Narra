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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.brandColor
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
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
                horizontalPadding = NarraUi.space16,
                titleMaxLines = Int.MAX_VALUE,
                titleIsHeading = true,
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
                    start = NarraUi.space16,
                    top = NarraUi.space4,
                    end = NarraUi.space16,
                    bottom = NarraUi.space32,
                ),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
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
                                "添加提供商后即可选择会话模型。"
                            } else {
                                "没有找到相关结果。"
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            onClick = openAddProviderDialog,
                            modifier = Modifier.fillMaxWidth(),
                            shape = NarraShapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = palette.surface,
                                contentColor = palette.title,
                            ),
                            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = NarraUi.space16,
                                        vertical = NarraUi.space16
                                    ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "新增提供商",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.title,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredProviders, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            health = uiState.connectionHealthMap[provider.id]
                                ?: ConnectionHealth.UNKNOWN,
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
        label = { Text("搜索提供商") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = palette.body)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = NarraUi.space8),
        shape = NarraShapes.medium,
        colors = providerTextFieldColors(palette.surfaceTint),
        singleLine = true,
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val providerType = provider.resolvedType()
    val brandColor = providerType.brandColor(isDark)
    val modelCount = provider.resolvedModels().size

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = NarraShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = palette.surface,
            contentColor = palette.title,
        ),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
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
                    contentDescription = null,
                    tint = palette.body.copy(alpha = 0.6f),
                )
            }

            Text(
                text = provider.name.ifBlank { providerType.displayName },
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = palette.title,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionHealthDot(health = health)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                ) {
                    Text(
                        text = provider.toCardSubtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                    )
                    if (health != ConnectionHealth.UNKNOWN) {
                        Text(
                            text = when (health) {
                                ConnectionHealth.CHECKING -> "检查连接中"
                                ConnectionHealth.HEALTHY -> "连接正常"
                                ConnectionHealth.UNHEALTHY -> "连接失败"
                                ConnectionHealth.UNKNOWN -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (health == ConnectionHealth.UNHEALTHY) MaterialTheme.colorScheme.error else palette.body,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                ProviderStatusPill(
                    text = if (provider.enabled) "启用" else "停用",
                    containerColor = if (provider.enabled) palette.accentSoft else palette.surfaceTint,
                    contentColor = if (provider.enabled) MaterialTheme.colorScheme.onPrimaryContainer else palette.body,
                    modifier = Modifier.testTag("provider_status_${provider.id}"),
                )
                ProviderStatusPill(
                    text = "$modelCount 个模型",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                    modifier = Modifier.testTag("provider_count_${provider.id}"),
                )
            }
        }
    }
}

@Composable
private fun ProviderStatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = NarraShapes.pill,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NarraUi.space8, vertical = NarraUi.space4),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 9dp 状态圆点与文字共同呈现连接状态，CHECKING 保留脉冲动画。 */
@Composable
fun ConnectionHealthDot(
    health: ConnectionHealth,
    modifier: Modifier = Modifier,
) {
    val color = when (health) {
        ConnectionHealth.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
        ConnectionHealth.CHECKING -> MaterialTheme.colorScheme.tertiary
        ConnectionHealth.HEALTHY -> MaterialTheme.colorScheme.primary
        ConnectionHealth.UNHEALTHY -> MaterialTheme.colorScheme.error
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
            .clip(NarraShapes.pill)
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
        shape = NarraShapes.pill,
        color = brandColor.copy(alpha = 0.15f),
        contentColor = palette.title,
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NarraUi.space8)
                    .clip(NarraShapes.pill),
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
                    color = palette.title,
                )
            }
        }
    }
}

private fun ProviderSettings.toCardSubtitle(): String {
    return when {
        hasRequiredConfig() -> selectedModel
        hasBaseCredentials() -> "未选择模型"
        else -> "待配置连接"
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
