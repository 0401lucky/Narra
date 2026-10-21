package com.example.myapplication.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset

/** 仅明确选择的品牌皮肤保留固定调色，默认 Narra 由应用 ColorScheme 提供。 */
@Immutable
internal data class RoleplaySkinPalette(
    val background: Color,
    val panel: Color,
    val panelStrong: Color,
    val border: Color,
    val accent: Color,
    val userBubble: Color,
    val characterBubble: Color,
    val userText: Color,
    val characterText: Color,
    val mutedText: Color,
)

internal fun roleplayBrandSkinPalette(preset: RoleplayNoBackgroundSkinPreset): RoleplaySkinPalette? =
    when (preset) {
        RoleplayNoBackgroundSkinPreset.NARRA -> null
        RoleplayNoBackgroundSkinPreset.WECHAT -> RoleplaySkinPalette(
            background = Color(0xFFEDEDED), panel = Color.White, panelStrong = Color.White,
            border = Color(0xFFDADDE3), accent = Color(0xFF07C160), userBubble = Color(0xFF95EC69),
            characterBubble = Color.White, userText = Color(0xFF111111),
            characterText = Color(0xFF111111), mutedText = Color(0xFF6D7784),
        )
        RoleplayNoBackgroundSkinPreset.QQ -> RoleplaySkinPalette(
            background = Color(0xFFEAF3FF), panel = Color.White, panelStrong = Color.White,
            border = Color(0xFFD5E4F7), accent = Color(0xFF0099FF), userBubble = Color(0xFF0099FF),
            characterBubble = Color.White, userText = Color.White,
            characterText = Color(0xFF1D2530), mutedText = Color(0xFF69768A),
        )
        RoleplayNoBackgroundSkinPreset.TELEGRAM -> RoleplaySkinPalette(
            background = Color(0xFFE6F3FA), panel = Color(0xFFF9FDFF), panelStrong = Color.White,
            border = Color(0xFFCFE1EA), accent = Color(0xFF2AABEE), userBubble = Color(0xFFD8F2FF),
            characterBubble = Color.White, userText = Color(0xFF102635),
            characterText = Color(0xFF102635), mutedText = Color(0xFF607685),
        )
        RoleplayNoBackgroundSkinPreset.KAKAO -> RoleplaySkinPalette(
            background = Color(0xFFBACEDE), panel = Color(0xFFF8FBFE), panelStrong = Color.White,
            border = Color(0xFF9FB4C8), accent = Color(0xFFFEE500), userBubble = Color(0xFFFEE500),
            characterBubble = Color.White, userText = Color(0xFF191600),
            characterText = Color(0xFF18212B), mutedText = Color(0xFF596D80),
        )
        RoleplayNoBackgroundSkinPreset.RETRO -> RoleplaySkinPalette(
            background = Color(0xFFE9EEF5), panel = Color(0xFFFFFCF6), panelStrong = Color.White,
            border = Color(0xFFBCC7D3), accent = Color(0xFF5CA7F8), userBubble = Color(0xFF5CA7F8),
            characterBubble = Color(0xFFFFFCF6), userText = Color.White,
            characterText = Color(0xFF263241), mutedText = Color(0xFF687585),
        )
        RoleplayNoBackgroundSkinPreset.POLKADOT -> RoleplaySkinPalette(
            background = Color(0xFFF5E6CF), panel = Color(0xFFFFF8EE), panelStrong = Color(0xFFFFFBF4),
            border = Color(0xFFE5C9A7), accent = Color(0xFFD85A50), userBubble = Color(0xFFD85A50),
            characterBubble = Color(0xFFFFF8EE), userText = Color.White,
            characterText = Color(0xFF3A2D25), mutedText = Color(0xFF8A6F5C),
        )
        RoleplayNoBackgroundSkinPreset.PIXEL -> RoleplaySkinPalette(
            background = Color(0xFFECEAF3), panel = Color(0xFFFFFBF0), panelStrong = Color.White,
            border = Color(0xFF4D5D79), accent = Color(0xFF4B7BD7), userBubble = Color(0xFF4B7BD7),
            characterBubble = Color(0xFFFFFBF0), userText = Color.White,
            characterText = Color(0xFF172033), mutedText = Color(0xFF5C6880),
        )
        RoleplayNoBackgroundSkinPreset.ROSE -> RoleplaySkinPalette(
            background = Color(0xFFFFEDF4), panel = Color.White, panelStrong = Color.White,
            border = Color(0xFFF2C8D7), accent = Color(0xFFE58BAD), userBubble = Color(0xFFF5DDE6),
            characterBubble = Color.White, userText = Color(0xFF3A2130),
            characterText = Color(0xFF3A2130), mutedText = Color(0xFF9A667A),
        )
        RoleplayNoBackgroundSkinPreset.NOIR -> RoleplaySkinPalette(
            background = Color(0xFF0E0B14), panel = Color(0xFF171120), panelStrong = Color(0xFF21172F),
            border = Color(0xFF5D4C70), accent = Color(0xFFB7A0D6), userBubble = Color(0xFF21172F),
            characterBubble = Color(0xFF171120), userText = Color(0xFFF4EDFF),
            characterText = Color(0xFFF4EDFF), mutedText = Color(0xFFC1B3D5),
        )
        RoleplayNoBackgroundSkinPreset.Y2K -> RoleplaySkinPalette(
            background = Color(0xFFF5F8FF), panel = Color.White, panelStrong = Color.White,
            border = Color(0xFFCFE0FF), accent = Color(0xFF4E9DFF), userBubble = Color(0xFFCCE4FF),
            characterBubble = Color.White, userText = Color(0xFF122641),
            characterText = Color(0xFF122641), mutedText = Color(0xFF6B7C98),
        )
        RoleplayNoBackgroundSkinPreset.IMESSAGE -> RoleplaySkinPalette(
            background = Color(0xFFF2F2F7), panel = Color.White, panelStrong = Color.White,
            border = Color(0xFFD7D7DF), accent = Color(0xFF0A84FF), userBubble = Color(0xFF0A84FF),
            characterBubble = Color(0xFFE5E5EA), userText = Color.White,
            characterText = Color(0xFF111111), mutedText = Color(0xFF6E6E73),
        )
        RoleplayNoBackgroundSkinPreset.LIQUID_GLASS -> RoleplaySkinPalette(
            background = Color(0xFFEAF0F7), panel = Color.White.copy(alpha = 0.78f),
            panelStrong = Color.White.copy(alpha = 0.9f), border = Color.White.copy(alpha = 0.72f),
            accent = Color(0xFF7CA7D9), userBubble = Color.White.copy(alpha = 0.64f),
            characterBubble = Color.White.copy(alpha = 0.58f), userText = Color(0xFF1E314A),
            characterText = Color(0xFF1E314A), mutedText = Color(0xFF62748A),
        )
    }

/** 按实际承载表面合成透明前景，供主题与玻璃共用同一阅读对比计算。 */
internal fun roleplayTextContrast(foreground: Color, surface: Color, backing: Color): Float {
    val background = surface.compositeOver(backing)
    val text = foreground.compositeOver(background)
    return (maxOf(text.luminance(), background.luminance()) + 0.05f) /
        (minOf(text.luminance(), background.luminance()) + 0.05f)
}

internal fun readableRoleplayText(preferred: Color, surface: Color, backing: Color): Color {
    return readableRoleplayTextOnSurfaces(preferred, listOf(surface.compositeOver(backing)))
}

internal fun readableRoleplayTextOnSurfaces(preferred: Color, surfaces: List<Color>): Color {
    fun contrast(color: Color): Float = surfaces.minOf { roleplayTextContrast(color, it, it) }
    if (contrast(preferred) >= 4.5f) return preferred
    val target = listOf(Color.Black, Color.White).maxBy(::contrast)
    // 沿原色调逐步调整，只有阅读对比不足才偏向黑/白。
    var lower = 0f
    var upper = 1f
    repeat(12) {
        val middle = (lower + upper) / 2f
        if (contrast(lerp(preferred.copy(alpha = 1f), target, middle)) >= 4.5f) {
            upper = middle
        } else {
            lower = middle
        }
    }
    return lerp(preferred.copy(alpha = 1f), target, upper)
}
