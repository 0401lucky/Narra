package com.example.myapplication.ui.screenshot

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction

/**
 * Compose foundation 1.10.4 的旧 value/onValueChange TextField 有 ScrollBy，但不暴露 VerticalScrollAxisRange。
 * 只读真实布局 modifier 的 scrollerPosition，不设置其 offset；滚动由正式 ScrollBy 语义动作完成。
 * API 形状改变就失败，不能退回用 fixture 长度或假滚动值作为证据。
 */
internal data class LegacyTextFieldScrollProbe(
    val offset: Float,
    val maximum: Float,
    val viewport: Rect,
    val viewportSize: Int
) {
    companion object {
        fun read(field: SemanticsNodeInteraction): LegacyTextFieldScrollProbe {
            val candidates = descendants(field.fetchSemanticsNode().layoutInfo)
                .flatMap { it.getModifierInfo().asSequence() }
                .filter { it.modifier.javaClass.name == "androidx.compose.foundation.text.VerticalScrollLayoutModifier" }
                .toList()
            check(candidates.size == 1) { "必须找到唯一真实 TextField 纵向滚动 modifier，实际 ${candidates.size}" }
            val modifier = candidates.single()
            val getter = modifier.modifier.javaClass.getDeclaredMethod("getScrollerPosition")
                .apply { isAccessible = true }
            val position = getter.invoke(modifier.modifier)
            fun number(name: String) = position.javaClass.getMethod(name).invoke(position) as Number
            return LegacyTextFieldScrollProbe(
                offset = number("getOffset").toFloat(),
                maximum = number("getMaximum").toFloat(),
                viewport = modifier.coordinates.boundsInRoot(),
                viewportSize = number("getViewportSize").toInt(),
            )
        }

        private fun descendants(info: LayoutInfo): Sequence<LayoutInfo> = sequence {
            yield(info)
            val getter =
                info.javaClass.methods.single { it.name == "getChildren\$ui" && it.parameterCount == 0 }
            val children = getter.invoke(info) as List<*>
            children.forEach { yieldAll(descendants(it as LayoutInfo)) }
        }
    }
}
