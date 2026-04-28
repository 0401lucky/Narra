package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings

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
    radius = 22.dp,
    paddingHorizontal = 18.dp,
    paddingVertical = 16.dp,
    showTail = true,
)

@Composable
internal fun rememberRoleplayNoBackgroundSkinSpec(
    settings: RoleplayNoBackgroundSkinSettings,
): RoleplayNoBackgroundSkinSpec {
    val normalized = settings.normalized()
    return remember(normalized) {
        val preset = resolveRoleplayNoBackgroundPresetSpec(normalized.preset)
        preset.copy(
            maxWidthFraction = normalized.maxWidthPercent.coerceIn(64, 92) / 100f,
            radius = normalized.bubbleRadiusDp.dp,
            paddingHorizontal = normalized.bubblePaddingHorizontalDp.dp,
            paddingVertical = normalized.bubblePaddingVerticalDp.dp,
            showTail = normalized.showBubbleTail,
        )
    }
}

@Composable
internal fun rememberRoleplayDialogueBubbleStyle(
    backdropState: ImmersiveBackdropState,
    settings: RoleplayNoBackgroundSkinSettings,
    bubbleMode: RoleplayMessageBubbleMode,
): RoleplayDialogueBubbleStyle {
    val skinSpec = rememberRoleplayNoBackgroundSkinSpec(settings)
    return remember(backdropState.hasImage, skinSpec, bubbleMode) {
        if (!backdropState.hasImage) {
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
    val tailCorner = if (showTail) 8.dp else radius
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

private fun resolveRoleplayNoBackgroundPresetSpec(
    preset: RoleplayNoBackgroundSkinPreset,
): RoleplayNoBackgroundSkinSpec {
    return when (preset) {
        RoleplayNoBackgroundSkinPreset.WECHAT -> skin(
            preset = preset,
            background = Color(0xFFEDEDED),
            panel = Color.White,
            panelStrong = Color.White,
            border = Color(0xFFDADDE3),
            accent = Color(0xFF07C160),
            userBubble = Color(0xFF95EC69),
            characterBubble = Color.White,
            userText = Color(0xFF111111),
            characterText = Color(0xFF111111),
            mutedText = Color(0xFF6D7784),
            maxWidth = 82,
            radius = 8,
            paddingX = 12,
            paddingY = 9,
            showTail = true,
        )

        RoleplayNoBackgroundSkinPreset.QQ -> skin(
            preset = preset,
            background = Color(0xFFEAF3FF),
            panel = Color.White,
            panelStrong = Color.White,
            border = Color(0xFFD5E4F7),
            accent = Color(0xFF0099FF),
            userBubble = Color(0xFF0099FF),
            characterBubble = Color.White,
            userText = Color.White,
            characterText = Color(0xFF1D2530),
            mutedText = Color(0xFF69768A),
            maxWidth = 78,
            radius = 16,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.TELEGRAM -> skin(
            preset = preset,
            background = Color(0xFFE6F3FA),
            panel = Color(0xFFF9FDFF),
            panelStrong = Color.White,
            border = Color(0xFFCFE1EA),
            accent = Color(0xFF2AABEE),
            userBubble = Color(0xFFD8F2FF),
            characterBubble = Color.White,
            userText = Color(0xFF102635),
            characterText = Color(0xFF102635),
            mutedText = Color(0xFF607685),
            maxWidth = 76,
            radius = 16,
            paddingX = 12,
            paddingY = 8,
            showTail = true,
        )

        RoleplayNoBackgroundSkinPreset.KAKAO -> skin(
            preset = preset,
            background = Color(0xFFBACEDE),
            panel = Color(0xFFF8FBFE),
            panelStrong = Color.White,
            border = Color(0xFF9FB4C8),
            accent = Color(0xFFFEE500),
            userBubble = Color(0xFFFEE500),
            characterBubble = Color.White,
            userText = Color(0xFF191600),
            characterText = Color(0xFF18212B),
            mutedText = Color(0xFF596D80),
            maxWidth = 82,
            radius = 10,
            paddingX = 12,
            paddingY = 9,
            showTail = true,
        )

        RoleplayNoBackgroundSkinPreset.RETRO -> skin(
            preset = preset,
            background = Color(0xFFE9EEF5),
            panel = Color(0xFFFFFCF6),
            panelStrong = Color.White,
            border = Color(0xFFBCC7D3),
            accent = Color(0xFF5CA7F8),
            userBubble = Color(0xFF5CA7F8),
            characterBubble = Color(0xFFFFFCF6),
            userText = Color.White,
            characterText = Color(0xFF263241),
            mutedText = Color(0xFF687585),
            maxWidth = 82,
            radius = 14,
            paddingX = 12,
            paddingY = 9,
            showTail = true,
        )

        RoleplayNoBackgroundSkinPreset.POLKADOT -> skin(
            preset = preset,
            background = Color(0xFFF5E6CF),
            panel = Color(0xFFFFF8EE),
            panelStrong = Color(0xFFFFFBF4),
            border = Color(0xFFE5C9A7),
            accent = Color(0xFFD85A50),
            userBubble = Color(0xFFD85A50),
            characterBubble = Color(0xFFFFF8EE),
            userText = Color.White,
            characterText = Color(0xFF3A2D25),
            mutedText = Color(0xFF8A6F5C),
            maxWidth = 82,
            radius = 16,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.PIXEL -> skin(
            preset = preset,
            background = Color(0xFFECEAF3),
            panel = Color(0xFFFFFBF0),
            panelStrong = Color.White,
            border = Color(0xFF4D5D79),
            accent = Color(0xFF4B7BD7),
            userBubble = Color(0xFF4B7BD7),
            characterBubble = Color(0xFFFFFBF0),
            userText = Color.White,
            characterText = Color(0xFF172033),
            mutedText = Color(0xFF5C6880),
            maxWidth = 78,
            radius = 10,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.ROSE -> skin(
            preset = preset,
            background = Color(0xFFFFEDF4),
            panel = Color.White,
            panelStrong = Color.White,
            border = Color(0xFFF2C8D7),
            accent = Color(0xFFE58BAD),
            userBubble = Color(0xFFF5DDE6),
            characterBubble = Color.White,
            userText = Color(0xFF3A2130),
            characterText = Color(0xFF3A2130),
            mutedText = Color(0xFF9A667A),
            maxWidth = 80,
            radius = 18,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.NOIR -> skin(
            preset = preset,
            background = Color(0xFF0E0B14),
            panel = Color(0xFF171120),
            panelStrong = Color(0xFF21172F),
            border = Color(0xFF5D4C70),
            accent = Color(0xFFB7A0D6),
            userBubble = Color(0xFF21172F),
            characterBubble = Color(0xFF171120),
            userText = Color(0xFFF4EDFF),
            characterText = Color(0xFFF4EDFF),
            mutedText = Color(0xFFC1B3D5),
            maxWidth = 79,
            radius = 8,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.Y2K -> skin(
            preset = preset,
            background = Color(0xFFF5F8FF),
            panel = Color.White,
            panelStrong = Color.White,
            border = Color(0xFFCFE0FF),
            accent = Color(0xFF4E9DFF),
            userBubble = Color(0xFFCCE4FF),
            characterBubble = Color.White,
            userText = Color(0xFF122641),
            characterText = Color(0xFF122641),
            mutedText = Color(0xFF6B7C98),
            maxWidth = 82,
            radius = 6,
            paddingX = 12,
            paddingY = 9,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.IMESSAGE -> skin(
            preset = preset,
            background = Color(0xFFF2F2F7),
            panel = Color.White,
            panelStrong = Color.White,
            border = Color(0xFFD7D7DF),
            accent = Color(0xFF0A84FF),
            userBubble = Color(0xFF0A84FF),
            characterBubble = Color(0xFFE5E5EA),
            userText = Color.White,
            characterText = Color(0xFF111111),
            mutedText = Color(0xFF6E6E73),
            maxWidth = 76,
            radius = 20,
            paddingX = 14,
            paddingY = 10,
            showTail = false,
        )

        RoleplayNoBackgroundSkinPreset.LIQUID_GLASS -> skin(
            preset = preset,
            background = Color(0xFFEAF0F7),
            panel = Color.White.copy(alpha = 0.78f),
            panelStrong = Color.White.copy(alpha = 0.9f),
            border = Color.White.copy(alpha = 0.72f),
            accent = Color(0xFF7CA7D9),
            userBubble = Color.White.copy(alpha = 0.64f),
            characterBubble = Color.White.copy(alpha = 0.58f),
            userText = Color(0xFF1E314A),
            characterText = Color(0xFF1E314A),
            mutedText = Color(0xFF62748A),
            maxWidth = 78,
            radius = 18,
            paddingX = 14,
            paddingY = 10,
            showTail = false,
        )
    }
}

private fun skin(
    preset: RoleplayNoBackgroundSkinPreset,
    background: Color,
    panel: Color,
    panelStrong: Color,
    border: Color,
    accent: Color,
    userBubble: Color,
    characterBubble: Color,
    userText: Color,
    characterText: Color,
    mutedText: Color,
    maxWidth: Int,
    radius: Int,
    paddingX: Int,
    paddingY: Int,
    showTail: Boolean,
): RoleplayNoBackgroundSkinSpec {
    return RoleplayNoBackgroundSkinSpec(
        preset = preset,
        displayName = preset.displayName,
        background = background,
        panel = panel,
        panelStrong = panelStrong,
        border = border,
        accent = accent,
        userAccent = if (userBubble.luminance() < 0.45f) userText else accent,
        userBubble = userBubble,
        characterBubble = characterBubble,
        narrationBubble = panelStrong,
        userText = userText,
        characterText = characterText,
        mutedText = mutedText,
        maxWidthFraction = maxWidth / 100f,
        radius = radius.dp,
        paddingHorizontal = paddingX.dp,
        paddingVertical = paddingY.dp,
        showTail = showTail,
    )
}
