package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import kotlin.math.absoluteValue

/**
 * 世界书列表页的纯函数 / 枚举集合。
 *
 * 承担职责：
 * - 书脊色（`bookSpineColor`）
 * - 条目关键词"真词"提取（`firstRealKeywords`）
 * - 相对时间（`formatRelativeTime`）
 * - 作用域/状态筛选器与组合（`filterEntries` / `activeFilterCount`）
 *
 * 所有函数都设计为可在 JVM 单元测试中直接覆盖。
 */

/**
 * 根据 bookId 生成稳定的书脊色：HSL 色相轮转，饱和度/亮度固定。
 * 同一个 bookId 永远返回同样颜色，不同 bookId 基本不会碰撞。
 */
internal fun bookSpineColor(bookId: String): Color {
    val hue = (bookId.hashCode().absoluteValue % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.52f, lightness = 0.62f)
}
