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
import dev.chrisbanes.haze.hazeChild
import com.example.myapplication.ui.component.roleplay.LocalImmersiveHazeState
import dev.chrisbanes.haze.HazeStyle

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
    val hazeState = LocalImmersiveHazeState.current
    val containerModifier = if (reduceVisualEffects) {
        modifier
            .clip(shape)
            .background(brush = palette.containerBrush, shape = shape)
            .border(width = 1.dp, brush = palette.borderBrush, shape = shape)
    } else {
        val baseMod = modifier
            .shadow(
                elevation = 1.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.shadowColor.copy(alpha=0.02f),
                spotColor = palette.shadowColor.copy(alpha=0.02f),
            )
            .clip(shape)

        val hazeMod = if (hazeState != null) {
            baseMod.hazeChild(state = hazeState, shape = shape, style = HazeStyle(blurRadius = 32.dp, backgroundColor = androidx.compose.ui.graphics.Color.Transparent, tint = dev.chrisbanes.haze.HazeTint(androidx.compose.ui.graphics.Color.Transparent)))
        } else {
            baseMod
        }

        hazeMod
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

        // Use an alpha shadow for the text to improve contrast against bright background areas
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val effectiveContentColor = if (!reduceVisualEffects && !isDark) contentColor.copy(alpha = 0.98f) else contentColor
        CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
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
                        surface.copy(alpha = 0.15f)
                    } else {
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.20f)
                    },
                    tint.copy(alpha = if (isDark) 0.05f else 0.04f),
                    surface.copy(alpha = if (isDark) 0.12f else 0.18f),
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
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.35f else 0.50f),
                    tint.copy(alpha = if (isDark) 0.15f else 0.25f),
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.10f else 0.20f),
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
