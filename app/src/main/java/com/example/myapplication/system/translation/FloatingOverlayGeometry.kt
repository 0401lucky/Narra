package com.example.myapplication.system.translation

import kotlin.math.roundToInt

internal data class FloatingOverlayGeometry(
    val width: Int,
    val height: Int,
    val density: Float,
) {
    val ballSize: Int get() = dp(56)
    val panelWidth: Int get() = (width - dp(32)).coerceAtLeast(1).coerceAtMost(dp(400))
    val panelHeight: Int get() = (height - dp(32)).coerceAtLeast(1).coerceAtMost(dp(480))
    val bannerOffset: Int get() = dp(16)

    fun ballPosition(normalizedX: Float, normalizedY: Float): Pair<Int, Int> =
        (maxX * normalizedX.normalized()).roundToInt() to (maxY * normalizedY.normalized()).roundToInt()

    fun clampBall(x: Int, y: Int): Pair<Int, Int> = x.coerceIn(0, maxX) to y.coerceIn(0, maxY)

    fun snapBall(x: Int, y: Int): Pair<Float, Float> =
        (if (maxX == 0 || x + ballSize / 2 < width / 2) 0f else 1f) to
            (if (maxY == 0) 0f else y.coerceIn(0, maxY).toFloat() / maxY)

    private val maxX: Int get() = (width - ballSize).coerceAtLeast(0)
    private val maxY: Int get() = (height - ballSize).coerceAtLeast(0)
    private fun dp(value: Int): Int = (value * density).roundToInt()
    private fun Float.normalized(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
}
