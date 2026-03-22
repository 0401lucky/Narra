package com.example.myapplication.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubbleRenderModeTest {
    @Test
    fun shouldRenderWithMarkdown_returnsFalseForPlainParagraph() {
        assertFalse(
            shouldRenderWithMarkdown("这是普通回复，没有列表、链接或代码块。"),
        )
    }

    @Test
    fun shouldRenderWithMarkdown_returnsTrueForStructuredMarkdown() {
        assertTrue(
            shouldRenderWithMarkdown(
                """
                # 标题

                - 第一项
                - 第二项
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun shouldRenderWithMarkdown_returnsTrueForInlineMarkdownHints() {
        assertTrue(
            shouldRenderWithMarkdown("请查看 `val answer = 42`，或者访问 [文档](https://example.com)。"),
        )
    }
}
