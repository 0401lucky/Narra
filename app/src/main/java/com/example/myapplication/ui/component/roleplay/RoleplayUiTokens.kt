package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.unit.dp

// 人像卡片尺寸与动效
internal val RoleplayPortraitCardWidth = 172.dp
internal val RoleplayPortraitRequestHeight = 220.dp
internal val RoleplayPortraitSpeakingPulseDurationMillis = 1200
internal val RoleplayPortraitAlphaDurationMillis = 400
internal const val RoleplayPortraitCollapsedScale = 0.90f

// 空状态与流式动效
internal val RoleplayEmptyStateGlowRadius = 300.dp
internal const val RoleplayEmptyStateGlowDurationMillis = 2400
internal const val RoleplayStreamingCursorPulseMillis = 520

// 交互图标尺寸
internal val RoleplayInteractiveIconButtonSize = 48.dp

// Palette 缓存容量
internal const val ImmersivePaletteCacheMaxEntries = 20

/**
 * 沉浸式玻璃态设计令牌集合。
 *
 * 所有散落在设置页、消息气泡、Tab 选择器里的圆角 / 边距 / alpha / 描边宽度统一收口在这里。
 * 当需要整体调色、调节质感时只改这一处即可。
 */
internal object RoleplayGlassTokens {
    // 圆角
    val CardCornerRadius = 28.dp
    val SectionCornerRadius = 18.dp
    val TabCornerRadius = 16.dp
    val ChipCornerRadius = 20.dp
    val HeroCornerRadius = 30.dp

    // 内容内边距
    val SectionPaddingHorizontal = 18.dp
    val SectionPaddingVertical = 16.dp
    val SwitchRowPaddingHorizontal = 18.dp
    val SwitchRowPaddingVertical = 12.dp
    val HeroPaddingHorizontal = 22.dp
    val HeroPaddingVertical = 20.dp

    // 垂直间距
    val SectionSpacingBetween = 16.dp
    val SectionSpacingInner = 12.dp
    val SwitchRowGap = 14.dp

    // 分割线缩进（图标左侧对齐）
    val DividerIndentStart = 56.dp
    val DividerIndentEnd = 18.dp

    // 玻璃面板透明度
    const val PanelBorderAlphaStrong = 0.44f
    const val PanelBorderAlphaMild = 0.30f
    const val SurfaceOverlayAlpha = 0.10f

    // Tab 选中/未选中底色 alpha
    const val TabSelectedBgAlpha = 0.38f
    const val TabUnselectedBgAlpha = 0.18f
    const val TabSelectedAccentAlpha = 0.72f
    val TabSelectedBorderWidth = 1.5.dp
}
