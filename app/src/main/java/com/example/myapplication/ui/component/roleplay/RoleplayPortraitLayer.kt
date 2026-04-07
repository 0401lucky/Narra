package com.example.myapplication.ui.component.roleplay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.R
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState

data class RoleplayPortraitSpec(
    val name: String,
    val avatarUri: String,
    val avatarUrl: String,
    val fallbackLabel: String,
)

@Composable
fun RoleplayPortraitLayer(
    user: RoleplayPortraitSpec,
    character: RoleplayPortraitSpec,
    highlightedSpeaker: RoleplaySpeaker?,
    autoHighlightSpeaker: Boolean,
    modifier: Modifier = Modifier,
    userAccentColor: Color = Color.Unspecified,
    characterAccentColor: Color = Color.Unspecified,
    isSpeaking: Boolean = false,
    onUserPortraitClick: () -> Unit = {},
    onCharacterPortraitClick: () -> Unit = {},
) {
    val resolvedUserAccentColor = if (userAccentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        userAccentColor
    }
    val resolvedCharacterAccentColor = if (characterAccentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        characterAccentColor
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        PortraitCard(
            spec = user,
            modifier = Modifier
                .width(RoleplayPortraitCardWidth)
                .fillMaxHeight(),
            emphasized = !autoHighlightSpeaker || highlightedSpeaker == RoleplaySpeaker.USER,
            isSpeaking = false,
            onClick = onUserPortraitClick,
            accentColor = resolvedUserAccentColor,
            gradientColors = listOf(
                resolvedUserAccentColor.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
            ),
        )
        PortraitCard(
            spec = character,
            modifier = Modifier
                .width(RoleplayPortraitCardWidth)
                .fillMaxHeight(),
            emphasized = !autoHighlightSpeaker || highlightedSpeaker == RoleplaySpeaker.CHARACTER,
            isSpeaking = isSpeaking,
            onClick = onCharacterPortraitClick,
            accentColor = resolvedCharacterAccentColor,
            gradientColors = listOf(
                resolvedCharacterAccentColor.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
            ),
        )
    }
}

@Composable
private fun PortraitCard(
    spec: RoleplayPortraitSpec,
    emphasized: Boolean,
    isSpeaking: Boolean,
    accentColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val requestSize = with(density) {
        IntSize(
            width = RoleplayPortraitCardWidth.roundToPx().coerceAtLeast(1),
            height = RoleplayPortraitRequestHeight.roundToPx().coerceAtLeast(1),
        )
    }
    val imageState = rememberUserProfileAvatarState(
        avatarUri = spec.avatarUri,
        avatarUrl = spec.avatarUrl,
        requestSize = requestSize,
    )
    val portraitDescription = stringResource(
        id = R.string.avatar_content_description,
        spec.name.ifBlank { spec.fallbackLabel },
    )

    // Scale: emphasized full-size, others shrink slightly
    val scale by animateFloatAsState(
        targetValue = if (emphasized) 1f else RoleplayPortraitCollapsedScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "portrait_scale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (emphasized) 1f else 0.5f,
        animationSpec = tween(durationMillis = RoleplayPortraitAlphaDurationMillis),
        label = "portrait_alpha",
    )

    // Pulsing glow on the border of the emphasized portrait
    val infiniteTransition = rememberInfiniteTransition(label = "speaking_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RoleplayPortraitSpeakingPulseDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_pulse",
    )

    val shape = RoundedCornerShape(
        topStart = 30.dp, topEnd = 30.dp,
        bottomStart = 24.dp, bottomEnd = 24.dp,
    )
    val borderWidth = if (emphasized) 2.dp else 1.dp
    val borderColor = if (emphasized) {
        accentColor.copy(alpha = glowAlpha)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    }

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = cardAlpha
        },
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .clickable(onClick = onClick)
                .background(Brush.verticalGradient(gradientColors))
                .border(width = borderWidth, color = borderColor, shape = shape),
        ) {
            if (imageState.loadState == UserAvatarLoadState.Success &&
                imageState.imageBitmap != null
            ) {
                Image(
                    bitmap = imageState.imageBitmap,
                    contentDescription = portraitDescription,
                    modifier = Modifier.fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = spec.name.ifBlank { spec.fallbackLabel },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (imageState.loadState != UserAvatarLoadState.Success) {
                    Text(
                        text = spec.fallbackLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Speaking indicator badge
            if (isSpeaking) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.88f),
                ) {
                    Text(
                        text = stringResource(id = R.string.roleplay_speaking),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
