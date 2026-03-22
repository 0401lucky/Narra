package com.example.myapplication.ui.screen.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.component.ModelIcon

internal val SettingsScreenPadding = 20.dp

private val SettingsGroupSpacing = 8.dp
private val SettingsGroupRadius = 26.dp
private val SettingsAvatarRadius = 16.dp

internal data class SettingsPalette(
    val background: Color,
    val surface: Color,
    val surfaceTint: Color,
    val elevatedSurface: Color,
    val border: Color,
    val title: Color,
    val body: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentStrong: Color,
    val accentOnStrong: Color,
    val subtleChip: Color,
    val subtleChipContent: Color,
)

@Composable
internal fun rememberSettingsPalette(): SettingsPalette {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        SettingsPalette(
            background = colorScheme.background,
            surface = colorScheme.surface,
            surfaceTint = colorScheme.background,
            elevatedSurface = colorScheme.surface,
            border = colorScheme.outlineVariant.copy(alpha = 0.5f),
            title = colorScheme.onSurface,
            body = colorScheme.onSurfaceVariant,
            accent = colorScheme.primary,
            accentSoft = colorScheme.primaryContainer,
            accentStrong = colorScheme.primary,
            accentOnStrong = colorScheme.onPrimary,
            subtleChip = colorScheme.secondaryContainer,
            subtleChipContent = colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = rememberSettingsPalette()

    Surface(
        color = Color.Transparent, // Let the background show through
        contentColor = palette.title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = SettingsScreenPadding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = palette.surface.copy(alpha = 0.8f),
                border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.3f)),
                shadowElevation = 2.dp,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.title,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.body.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.accentSoft.copy(alpha = 0.65f),
                    contentColor = palette.accentStrong,
                    border = BorderStroke(1.dp, palette.accentStrong.copy(alpha = 0.1f)),
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onAction() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
fun SettingsPageIntro(
    overline: String,
    title: String,
    summary: String,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.3f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.accentSoft.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.title,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.body.copy(alpha = 0.85f),
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    description: String,
) {
    val palette = rememberSettingsPalette()

    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing)) {
        SettingsSectionHeader(title = title, description = subtitle.orEmpty())
        SettingsGroup(content = content)
    }
}

@Composable
fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsGroupRadius),
        color = palette.surface,
        border = BorderStroke(
            width = 1.dp,
            color = palette.border,
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsGroupDivider() {
    val palette = rememberSettingsPalette()

    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
        color = palette.border.copy(alpha = 0.44f),
    )
}

@Composable
fun SettingsListRow(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    showArrow: Boolean = onClick != null && trailingContent == null,
) {
    val palette = rememberSettingsPalette()
    val containerColor = if (highlighted) {
        palette.accentSoft.copy(alpha = 0.72f)
    } else {
        Color.Transparent
    }
    val headlineColor = when {
        enabled -> palette.title
        else -> palette.body.copy(alpha = 0.65f)
    }
    val supportingColor = when {
        enabled -> palette.body
        else -> palette.body.copy(alpha = 0.6f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        color = containerColor,
        contentColor = headlineColor,
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
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = headlineColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                trailingContent != null -> trailingContent()
                showArrow -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.accent.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
fun SettingsPlaceholderRow(
    title: String,
    subtitle: String,
) {
    val palette = rememberSettingsPalette()

    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.title,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.body,
        )
    }
}

@Composable
fun SettingsStatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor.copy(alpha = if (isSystemInDarkTheme()) 0.42f else 0.92f),
        contentColor = contentColor,
        border = BorderStroke(1.dp, containerColor.copy(alpha = 0.5f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
fun SettingsHintCard(
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(containerColor.copy(alpha = 0.88f)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(contentColor),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.title,
                    )
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.body,
                )
            }
        }
    }
}

@Composable
fun SettingsNoticeCard(
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color,
) {
    SettingsHintCard(
        title = title,
        body = body,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
fun SettingsLetterAvatar(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(SettingsAvatarRadius),
        color = containerColor.copy(alpha = if (isSystemInDarkTheme()) 0.46f else 0.72f),
        contentColor = contentColor,
        border = BorderStroke(1.dp, containerColor.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun SettingsModelAvatar(
    modelName: String,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(SettingsAvatarRadius),
        color = palette.subtleChip,
        contentColor = palette.subtleChipContent,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.48f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ModelIcon(
                modelName = modelName,
                size = 24.dp,
            )
        }
    }
}

@Composable
fun SettingsModelOptionRow(
    title: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsListRow(
        leadingContent = {
            SettingsModelAvatar(modelName = title)
        },
        title = title,
        supportingText = supportingText,
        onClick = onClick,
        highlighted = selected,
        showArrow = false,
    )
}

@Composable
fun AnimatedSettingButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isPrimary: Boolean,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val palette = rememberSettingsPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "settings_button_scale",
    )

    if (isPrimary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale)
                .shadow(if (isPressed && enabled) 4.dp else 16.dp, RoundedCornerShape(20.dp), ambientColor = palette.accentStrong, spotColor = palette.accentStrong),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accentStrong,
                contentColor = palette.accentOnStrong,
            ),
            interactionSource = interactionSource,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(20.dp),
            interactionSource = interactionSource,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.surfaceTint,
                contentColor = palette.title,
            ),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.58f)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun rememberSettingsSnackbarHostState(
    message: String?,
    onConsumeMessage: () -> Unit,
): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }

    return snackbarHostState
}

@Composable
fun rememberSettingsOutlineColors() = run {
    val palette = rememberSettingsPalette()
    OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = palette.surfaceTint,
        focusedContainerColor = palette.surfaceTint,
        unfocusedBorderColor = palette.border.copy(alpha = 0.55f),
        focusedBorderColor = palette.accentStrong,
        unfocusedLabelColor = palette.body,
        focusedLabelColor = palette.accent,
        unfocusedTextColor = palette.title,
        focusedTextColor = palette.title,
        cursorColor = palette.accentStrong,
    )
}
