package com.example.myapplication.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
internal fun GlassMessageContainer(
    modifier: Modifier = Modifier,
    shape: Shape,
    tint: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    reduceVisualEffects: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = glassPalette(tint = tint)
    val containerModifier = if (reduceVisualEffects) {
        modifier
            .clip(shape)
            .background(brush = palette.containerBrush, shape = shape)
            .border(width = 1.dp, brush = palette.borderBrush, shape = shape)
    } else {
        modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.shadowColor,
                spotColor = palette.shadowColor,
            )
            .clip(shape)
            .background(brush = palette.containerBrush, shape = shape)
            .border(width = 1.dp, brush = palette.borderBrush, shape = shape)
    }

    Box(
        modifier = containerModifier,
    ) {
        if (!reduceVisualEffects) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(brush = palette.sheenBrush, shape = shape),
            )
        }

        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun glassPalette(
    tint: androidx.compose.ui.graphics.Color,
): MessageBubbleGlassPalette {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f

    return remember(tint, surface, isDark) {
        MessageBubbleGlassPalette(
            containerBrush = Brush.verticalGradient(
                colors = listOf(
                    if (isDark) {
                        surface.copy(alpha = 0.94f)
                    } else {
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.96f)
                    },
                    tint.copy(alpha = if (isDark) 0.10f else 0.08f),
                    surface.copy(alpha = if (isDark) 0.86f else 0.92f),
                ),
            ),
            sheenBrush = Brush.linearGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.08f else 0.18f),
                    tint.copy(alpha = if (isDark) 0.03f else 0.05f),
                    androidx.compose.ui.graphics.Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(560f, 280f),
            ),
            borderBrush = Brush.verticalGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.14f else 0.30f),
                    tint.copy(alpha = if (isDark) 0.10f else 0.08f),
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.05f else 0.10f),
                ),
            ),
            shadowColor = tint.copy(alpha = if (isDark) 0.10f else 0.06f),
            flatContainerColor = if (isDark) {
                surface.copy(alpha = 0.96f)
            } else {
                androidx.compose.ui.graphics.Color.White
            },
            flatBorderColor = tint.copy(alpha = if (isDark) 0.10f else 0.08f),
        )
    }
}

private data class MessageBubbleGlassPalette(
    val containerBrush: Brush,
    val sheenBrush: Brush,
    val borderBrush: Brush,
    val shadowColor: androidx.compose.ui.graphics.Color,
    val flatContainerColor: androidx.compose.ui.graphics.Color,
    val flatBorderColor: androidx.compose.ui.graphics.Color,
)
