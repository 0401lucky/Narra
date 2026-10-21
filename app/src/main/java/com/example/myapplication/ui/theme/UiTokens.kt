package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NarraMinimumTouchTarget = 48.dp

/** Narra 页面共享的节奏与几何令牌，避免各页面自行散落尺寸。 */
object NarraUi {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val radiusSmall = 8.dp
    val radiusMedium = 16.dp
    val radiusLarge = 24.dp

    // 试点外页面仍使用这些历史别名，避免一次令牌迁移隐式改变全部布局。
    val pageHorizontal = 20.dp
    val pageVertical = 16.dp
    val sectionGap = 20.dp
    val rowGap = 12.dp
    val compactGap = 8.dp
    val cardRadius = 8.dp
    val smallRadius = 8.dp
    val avatarRadius = 18.dp
    val iconTile = 44.dp
    val navHeight = 60.dp
}

object NarraShapes {
    val small = RoundedCornerShape(NarraUi.radiusSmall)
    val medium = RoundedCornerShape(NarraUi.radiusMedium)
    val large = RoundedCornerShape(NarraUi.radiusLarge)
    val pill = RoundedCornerShape(percent = 50)
    val material = Shapes(
        extraSmall = small,
        small = small,
        medium = medium,
        large = large,
        extraLarge = large,
    )
}
