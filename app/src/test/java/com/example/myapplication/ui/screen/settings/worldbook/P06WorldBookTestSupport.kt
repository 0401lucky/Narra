package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.WorldBookRenderTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** P06 行为专用辅助；不修改已经冻结的前后截图路径。 */
abstract class P06WorldBookTestSupport : WorldBookRenderTest() {
    protected val normalCase get() = ScreenshotCases.require("E06_editor-existing-light")
    protected val narrowCase get() = ScreenshotCases.require("E06_editor-new-required-narrow-light")

    protected fun touch(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        node.performTouchInput { click() }
        composeRule.waitForIdle()
        return node
    }

    protected fun input(label: String): SemanticsNodeInteraction =
        reveal(hasText(label) and hasSetTextAction())

    protected fun keywordInput(label: String): SemanticsNodeInteraction =
        reveal(hasContentDescription(label) and hasSetTextAction())

    protected fun setSection(title: String, expanded: Boolean) {
        val node = reveal(hasText(title) and hasClickAction())
        val state = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        if ((state == "已展开") != expanded) touch(node)
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, if (expanded) "已展开" else "已收起"
            )
        )
    }

    protected fun selectScope(scope: WorldBookScopeType) {
        setSection("作用域", true)
        touch(reveal(hasText(scope.label) and isSelectable()))
    }

    protected fun toggle(title: String): SemanticsNodeInteraction =
        reveal(hasContentDescription(title) and isToggleable())

    protected fun saveDraft() {
        touch(pageNode(hasText("保存") and hasClickAction()))
    }

    protected fun selectListFilter(index: Int, label: String) {
        pageList().performScrollToNode(hasText("作用域"))
        val horizontal = hasScrollToIndexAction() and
                SemanticsMatcher("横向筛选列表") { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) } and
                hasAnyAncestor(hasTestTag(StageTag))
        composeRule.onAllNodes(horizontal)[index].performScrollToNode(hasText(label) and isSelectable())
        touch(pageNode(hasText(label) and isSelectable()).performScrollTo())
    }

    protected fun assertCompleteText(node: SemanticsNodeInteraction, value: String) {
        val layout = textLayout(node)
        assertEquals(value, layout.layoutInput.text.text)
        val diagnostics =
            "value=$value size=${layout.size} constraints=${layout.layoutInput.constraints} " +
                    "overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} " +
                    "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                    "exceededMaxLines=${layout.multiParagraph.didExceedMaxLines} " +
                    "lines=${layout.lineCount} maxLines=${layout.layoutInput.maxLines} " +
                    "bounds=${node.fetchSemanticsNode().boundsInRoot} " +
                    (0 until layout.lineCount).joinToString { line ->
                        "line$line=${layout.getLineLeft(line)},${layout.getLineTop(line)},${
                            layout.getLineRight(
                                line
                            )
                        },${layout.getLineBottom(line)} ellipsized=${layout.isLineEllipsized(line)}"
                    }
        // 段落保留测量约束宽度，Text 可按内容缩窄；实际行边界仍须落在 Text 布局内。
        assertTrue(
            "文字段落不得超出测量约束：$diagnostics",
            layout.multiParagraph.width <= layout.layoutInput.constraints.maxWidth + 0.5f &&
                    layout.multiParagraph.height <= layout.size.height + 0.5f
        )
        assertFalse("文字不得超过行数限制：$diagnostics", layout.multiParagraph.didExceedMaxLines)
        (0 until layout.lineCount).forEach { line ->
            assertFalse("文字不得被省略：$value", layout.isLineEllipsized(line))
            assertTrue(
                "真实行边界不得超出布局：$diagnostics",
                layout.getLineLeft(line) >= -0.5f && layout.getLineTop(line) >= -0.5f &&
                        layout.getLineRight(line) <= layout.size.width + 0.5f &&
                        layout.getLineBottom(line) <= layout.size.height + 0.5f
            )
        }
    }

    protected fun assertActionBounds(
        name: String,
        node: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction = pageList(),
    ) {
        recordBounds(name, node, viewport)
        requireFullyVisible(name)
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("真实触控宽度至少48dp：$name $bounds", bounds.width >= 95.5f)
        assertTrue("真实触控高度至少48dp：$name $bounds", bounds.height >= 95.5f)
    }
}
