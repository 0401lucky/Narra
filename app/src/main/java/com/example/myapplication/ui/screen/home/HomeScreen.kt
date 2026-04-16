package com.example.myapplication.ui.screen.home

import com.example.myapplication.ui.component.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            Text(
                text = stringResource(id = R.string.home_welcome_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(48.dp))

            // Material 3 Premium Glass Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        HomePrimaryButtonShadowElevation,
                        androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryCardCornerRadius),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(HomePrimaryCardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_config_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    ConfigRow(
                        label = stringResource(id = R.string.home_config_base_url),
                        value = if (storedSettings.baseUrl.isBlank()) {
                            stringResource(id = R.string.status_not_configured)
                        } else {
                            stringResource(id = R.string.status_configured)
                        },
                    )
                    ConfigRow(
                        label = stringResource(id = R.string.home_config_api_key),
                        value = if (storedSettings.apiKey.isBlank()) {
                            stringResource(id = R.string.status_not_configured)
                        } else {
                            stringResource(id = R.string.status_configured)
                        },
                    )
                    ConfigRow(
                        label = stringResource(id = R.string.home_config_model),
                        value = if (storedSettings.selectedModel.isBlank()) {
                            stringResource(id = R.string.status_not_selected)
                        } else {
                            storedSettings.selectedModel
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            AnimatedHoverButton(
                text = stringResource(id = R.string.home_open_chat),
                onClick = onOpenChat,
                enabled = hasRequiredConfig,
                isPrimary = true
            )

            Spacer(modifier = Modifier.height(16.dp))

                AnimatedHoverButton(
                    text = stringResource(id = R.string.home_open_settings),
                    onClick = onOpenSettings,
                    enabled = true,
                    isPrimary = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedHoverButton(
                    text = stringResource(id = R.string.home_open_roleplay),
                    onClick = onOpenRoleplay,
                    enabled = hasRequiredConfig,
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
fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AnimatedHoverButton(
    text: String,
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
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            interactionSource = interactionSource
        ) {
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
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
