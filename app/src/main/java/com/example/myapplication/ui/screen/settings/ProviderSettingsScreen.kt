package com.example.myapplication.ui.screen.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.example.myapplication.ui.component.AppSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onAddProviderFromTemplate: (ProviderTemplate) -> String,
    onOpenProviderDetail: (String) -> Unit,
    onCheckProviderHealth: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
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
    LaunchedEffect(uiState.providers.map { it.id }) {
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
                onAction = onShowAddDialog,
            )
        },
        snackbarHost = {
            AppSnackbarHost(hostState = snackbarHostState)
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 154.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                        title = "没有匹配的提供商",
                        subtitle = "换个关键词试试，可以搜索名称、类型、地址或模型名。",
                    )
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
    }

    if (uiState.showTemplateDialog) {
        ProviderTemplateDialog(
            onSelectTemplate = { template ->
                val newId = onAddProviderFromTemplate(template)
                onOpenProviderDetail(newId)
            },
            onDismiss = onDismissAddDialog,
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
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "选项",
                    tint = palette.title
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
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF4CAF50),
                )
                SettingsStatusPill(
                    text = "$modelCount 个模型",
                    containerColor = Color(0xFFE3F2FD),
                    contentColor = Color(0xFF2196F3),
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
