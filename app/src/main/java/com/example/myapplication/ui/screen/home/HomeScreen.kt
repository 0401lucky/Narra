package com.example.myapplication.ui.screen.home

import com.example.myapplication.ui.component.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    storedSettings: AppSettings,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoleplay: () -> Unit,
) {
    val hasRequiredConfig = storedSettings.hasRequiredConfig()
    val activeProvider = storedSettings.activeProvider()
    val assistantCount = storedSettings.resolvedAssistants().size
    val providerLabel = activeProvider?.name?.ifBlank { "未命名提供商" }
        ?: if (storedSettings.hasBaseCredentials()) "旧版连接兜底" else "还没有提供商"
    val modelLabel = activeProvider?.selectedModel?.takeIf { it.isNotBlank() }
        ?: storedSettings.selectedModel.ifBlank { "未选择模型" }
    val connectionLabel = if (hasRequiredConfig) "已就绪" else "需要配置"

    // Animation state for entry
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val slideAnim by animateDpAsState(
        targetValue = if (isVisible) 0.dp else HomeEnterOffsetY,
        animationSpec = tween(durationMillis = HomeEnterFadeDurationMillis),
        label = "slide"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = HomeEnterFadeDurationMillis),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.home_top_bar_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .offset { IntOffset(0, slideAnim.roundToPx()) }
                    .padding(bottom = 16.dp)
                    .graphicsLayer(alpha = alphaAnim),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            HomePhoneEntryCard(
                assistantCount = assistantCount,
                hasRequiredConfig = hasRequiredConfig,
                providerLabel = providerLabel,
                modelLabel = modelLabel,
                connectionLabel = connectionLabel,
                onOpenRoleplay = onOpenRoleplay,
                onOpenSettings = onOpenSettings,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = if (hasRequiredConfig) {
                    stringResource(id = R.string.home_ready_subtitle)
                } else {
                    stringResource(id = R.string.home_missing_config_subtitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

                AnimatedHoverButton(
                    text = stringResource(id = R.string.home_open_chat),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = onOpenChat,
                    enabled = hasRequiredConfig,
                    isPrimary = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedHoverButton(
                    text = stringResource(id = R.string.home_open_settings),
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings,
                    enabled = true,
                    isPrimary = false,
                )

                if (!hasRequiredConfig) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                    text = stringResource(id = R.string.home_config_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HomePhoneEntryCard(
    assistantCount: Int,
    hasRequiredConfig: Boolean,
    providerLabel: String,
    modelLabel: String,
    connectionLabel: String,
    onOpenRoleplay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                HomePrimaryButtonShadowElevation,
                androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryCardCornerRadius),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.home_private_phone_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$assistantCount 位角色 · $connectionLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeStatusPill("提供商", providerLabel)
                HomeStatusPill("模型", modelLabel)
            }

            AnimatedHoverButton(
                text = if (hasRequiredConfig) stringResource(id = R.string.home_open_roleplay) else "配置提供商",
                icon = if (hasRequiredConfig) Icons.Default.PhoneAndroid else Icons.Default.Settings,
                onClick = if (hasRequiredConfig) onOpenRoleplay else onOpenSettings,
                enabled = true,
                isPrimary = true,
            )
        }
    }
}

@Composable
private fun HomeStatusPill(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun AnimatedHoverButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    isPrimary: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shadowElevation by animateDpAsState(
        targetValue = if (isPressed) {
            HomePrimaryButtonPressedShadowElevation
        } else {
            HomePrimaryButtonShadowElevation
        },
        animationSpec = tween(durationMillis = HomeEnterFadeDurationMillis / 4),
        label = "button_shadow"
    )

    if (isPrimary) {
        NarraButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(HomePrimaryButtonHeight)
                .shadow(
                    elevation = shadowElevation,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryButtonCornerRadius),
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary,
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryButtonCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            interactionSource = interactionSource
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    } else {
        NarraOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(HomePrimaryButtonHeight),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryButtonCornerRadius),
            interactionSource = interactionSource
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
