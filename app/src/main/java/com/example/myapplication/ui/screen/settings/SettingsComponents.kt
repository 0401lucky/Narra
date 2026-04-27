package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.narraButtonColors
import com.example.myapplication.ui.component.narraOutlinedButtonColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    val accentOnStrong: Color,
    val subtleChip: Color,
    val subtleChipContent: Color,
) {
    // 兼容历史字段：accent 与 accentStrong 在 GPT 装饰期曾是两个不同变量，但始终指向同一颜色。
    // 收敛为 accent 后，保留一个只读别名，避免修改所有调用点。
    val accentStrong: Color get() = accent
}

@Composable
internal fun rememberSettingsPalette(): SettingsPalette {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    return remember(colorScheme, darkTheme) {
        if (darkTheme) {
            SettingsPalette(
                background = colorScheme.background,
                surface = colorScheme.surface,
                surfaceTint = colorScheme.surfaceVariant,
                elevatedSurface = colorScheme.surfaceContainerHigh,
                border = colorScheme.outlineVariant.copy(alpha = 0.5f),
                title = colorScheme.onSurface,
                body = colorScheme.onSurfaceVariant,
                accent = colorScheme.primary,
                accentSoft = colorScheme.primaryContainer,
                accentOnStrong = colorScheme.onPrimary,
                subtleChip = colorScheme.secondaryContainer,
                subtleChipContent = colorScheme.onSecondaryContainer,
            )
        } else {
            SettingsPalette(
                background = Color(0xFFF7F3EE),
                surface = Color(0xFFF0ECE7),
                surfaceTint = Color(0xFFE8E2DA),
                elevatedSurface = Color(0xFFF9F6F1),
                border = Color(0xFFD7D0C8),
                title = Color(0xFF2F2A24),
                body = Color(0xFF6F675E),
                accent = Color(0xFF7A6B5D),
                accentSoft = Color(0xFFE7DED3),
                accentOnStrong = Color(0xFF2F2A24),
                subtleChip = Color(0xFFEAE3D9),
                subtleChipContent = Color(0xFF5F554A),
            )
        }
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
    actionEnabled: Boolean = true,
) {
    val palette = rememberSettingsPalette()

    Surface(
        color = Color.Transparent,
        contentColor = palette.title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = SettingsScreenPadding, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NarraIconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.title,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Cursive,
                        fontSize = 28.sp,
                    ),
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
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
                NarraFilledTonalButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPageIntro(
    title: String,
    summary: String = "",
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
                if (summary.isNotBlank()) {
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
}

@Composable
fun SettingsSectionHeader(
    title: String,
    description: String,
) {
    val palette = rememberSettingsPalette()
    var showInfoDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Cursive,
                fontSize = 22.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = palette.accent,
        )
        if (description.isNotBlank()) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "查看说明",
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { showInfoDialog = true },
                tint = palette.accent.copy(alpha = 0.7f),
            )
        }
    }

    if (showInfoDialog && description.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("知道了")
                }
            },
            containerColor = palette.surface,
            titleContentColor = palette.title,
            textContentColor = palette.body,
        )
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
        shape = RoundedCornerShape(24.dp),
        color = palette.surface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 0.75.dp,
            color = palette.border.copy(alpha = 0.72f),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
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
    supportingText: String = "",
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
            .clip(RoundedCornerShape(24.dp))
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
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
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                trailingContent != null -> trailingContent()
                showArrow -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.body.copy(alpha = 0.72f),
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

/**
 * 设置页的主/次按钮包装。原 240 行 gradient 装饰器已废弃，
 * 现在直接复用 NarraButton / NarraOutlinedButton，统一按钮行为与 bounceClick 动画。
 */
@Composable
fun AnimatedSettingButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isPrimary: Boolean,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val contentPadding = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 20.dp,
        vertical = 14.dp,
    )
    if (isPrimary) {
        NarraButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = narraButtonColors(),
            contentPadding = contentPadding,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        NarraOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = narraOutlinedButtonColors(),
            contentPadding = contentPadding,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
        focusedBorderColor = palette.accent,
        unfocusedLabelColor = palette.body,
        focusedLabelColor = palette.accent,
        unfocusedTextColor = palette.title,
        focusedTextColor = palette.title,
        cursorColor = palette.accent,
    )
}
