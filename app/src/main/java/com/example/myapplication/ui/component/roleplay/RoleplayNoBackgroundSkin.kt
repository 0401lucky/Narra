package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.RoleplaySkinPalette
import com.example.myapplication.ui.theme.readableRoleplayText
import com.example.myapplication.ui.theme.readableRoleplayTextOnSurfaces
import com.example.myapplication.ui.theme.roleplayBrandSkinPalette

@Immutable
internal data class RoleplayNoBackgroundSkinSpec(
    val preset: RoleplayNoBackgroundSkinPreset,
    val displayName: String,
    val background: Color,
    val panel: Color,
    val panelStrong: Color,
    val border: Color,
    val accent: Color,
    val userAccent: Color,
    val userBubble: Color,
    val characterBubble: Color,
    val narrationBubble: Color,
    val userText: Color,
    val characterText: Color,
    val mutedText: Color,
    val maxWidthFraction: Float,
    val radius: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val showTail: Boolean,
)

@Immutable
internal data class RoleplayDialogueBubbleStyle(
    val maxWidthFraction: Float,
    val radius: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val showTail: Boolean,
)

internal val DefaultRoleplayDialogueBubbleStyle = RoleplayDialogueBubbleStyle(
    maxWidthFraction = 0.82f,
    radius = NarraUi.radiusLarge,
    paddingHorizontal = NarraUi.space16,
    paddingVertical = NarraUi.space16,
    showTail = true,
)

internal fun shouldUseRoleplayGlassBubble(
    hasBackgroundImage: Boolean,
    bubbleMode: RoleplayMessageBubbleMode,
    onlineBackgroundGlassEnabled: Boolean,
): Boolean {
    return hasBackgroundImage &&
        (bubbleMode != RoleplayMessageBubbleMode.ONLINE_PHONE || onlineBackgroundGlassEnabled)
}

@Composable
internal fun rememberRoleplayNoBackgroundSkinSpec(
    settings: RoleplayNoBackgroundSkinSettings,
): RoleplayNoBackgroundSkinSpec {
    val normalized = settings.normalized()
    val colorScheme = MaterialTheme.colorScheme
    return remember(normalized, colorScheme) {
        resolveRoleplayNoBackgroundSkinSpec(normalized, colorScheme)
    }
}

@Composable
internal fun rememberRoleplayDialogueBubbleStyle(
    backdropState: ImmersiveBackdropState,
    settings: RoleplayNoBackgroundSkinSettings,
    bubbleMode: RoleplayMessageBubbleMode,
): RoleplayDialogueBubbleStyle {
    val skinSpec = rememberRoleplayNoBackgroundSkinSpec(settings)
    val useGlassBubble = shouldUseRoleplayGlassBubble(
        hasBackgroundImage = backdropState.hasImage,
        bubbleMode = bubbleMode,
        onlineBackgroundGlassEnabled = settings.onlineBackgroundGlassEnabled,
    )
    return remember(useGlassBubble, skinSpec, bubbleMode) {
        if (!useGlassBubble) {
            RoleplayDialogueBubbleStyle(
                maxWidthFraction = skinSpec.maxWidthFraction,
                radius = skinSpec.radius,
                paddingHorizontal = skinSpec.paddingHorizontal,
                paddingVertical = skinSpec.paddingVertical,
                showTail = skinSpec.showTail,
            )
        } else {
            val maxWidth = if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) 0.82f else 0.86f
            DefaultRoleplayDialogueBubbleStyle.copy(maxWidthFraction = maxWidth)
        }
    }
}

internal fun RoleplayDialogueBubbleStyle.shape(isUser: Boolean): RoundedCornerShape {
    val tailCorner = if (showTail) NarraUi.radiusSmall else radius
    return if (isUser) {
        RoundedCornerShape(
            topStart = radius,
            topEnd = tailCorner,
            bottomStart = radius,
            bottomEnd = radius,
        )
    } else {
        RoundedCornerShape(
            topStart = tailCorner,
            topEnd = radius,
            bottomStart = radius,
            bottomEnd = radius,
        )
    }
}

internal fun resolveRoleplayNoBackgroundSkinSpec(
    settings: RoleplayNoBackgroundSkinSettings,
    colorScheme: ColorScheme,
): RoleplayNoBackgroundSkinSpec {
    val normalized = settings.normalized()
    val brand = roleplayBrandSkinPalette(normalized.preset)
    val isDark = colorScheme.surface.luminance() < 0.5f
    val preserveDarkBrand = normalized.preset == RoleplayNoBackgroundSkinPreset.NOIR
    val isLiquid = normalized.preset == RoleplayNoBackgroundSkinPreset.LIQUID_GLASS
    val palette = if (brand == null) {
        RoleplaySkinPalette(
            background = colorScheme.background,
            panel = colorScheme.surfaceContainerLow,
            panelStrong = colorScheme.surfaceContainerHigh,
            border = colorScheme.outlineVariant,
            accent = colorScheme.primary,
            userBubble = colorScheme.primaryContainer,
            characterBubble = colorScheme.surfaceContainerLow,
            userText = colorScheme.onPrimaryContainer,
            characterText = colorScheme.onSurface,
            mutedText = colorScheme.onSurfaceVariant,
        )
    } else if (isDark && !preserveDarkBrand) {
        brand.copy(
            background = colorScheme.background,
            panel = colorScheme.surfaceContainerLow.copy(alpha = brand.panel.alpha),
            panelStrong = colorScheme.surfaceContainerHigh.copy(alpha = brand.panelStrong.alpha),
            characterBubble = colorScheme.surfaceContainerLow.copy(alpha = brand.characterBubble.alpha),
            userBubble = if (isLiquid) colorScheme.surfaceContainerHigh.copy(alpha = brand.userBubble.alpha) else brand.userBubble,
            characterText = colorScheme.onSurface,
            mutedText = colorScheme.onSurfaceVariant,
        )
    } else {
        brand
    }
    val backing = palette.background
    val narrationBubble = if (brand == null) colorScheme.surfaceContainerHighest else palette.panelStrong
    val characterText = readableRoleplayText(palette.characterText, palette.characterBubble, backing)
    val userText = readableRoleplayText(palette.userText, palette.userBubble, backing)
    return RoleplayNoBackgroundSkinSpec(
        preset = normalized.preset,
        displayName = normalized.preset.displayName,
        background = backing,
        panel = palette.panel,
        panelStrong = palette.panelStrong,
        border = palette.border,
        accent = readableRoleplayText(palette.accent, palette.characterBubble, backing),
        userAccent = readableRoleplayText(palette.accent, palette.userBubble, backing),
        userBubble = palette.userBubble,
        characterBubble = palette.characterBubble,
        narrationBubble = narrationBubble,
        userText = userText,
        characterText = characterText,
        mutedText = readableRoleplayTextOnSurfaces(palette.mutedText, listOf(narrationBubble.compositeOver(backing), backing)),
        // 所有已有设置（包括仍等于旧默认的值）具有优先权，不推测用户是否手调。
        maxWidthFraction = normalized.maxWidthPercent / 100f,
        radius = normalized.bubbleRadiusDp.dp,
        paddingHorizontal = normalized.bubblePaddingHorizontalDp.dp,
        paddingVertical = normalized.bubblePaddingVerticalDp.dp,
        showTail = normalized.showBubbleTail,
    )
}
