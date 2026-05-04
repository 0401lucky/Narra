package com.example.myapplication.ui.component

import org.junit.Assert.assertEquals
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

    @Test
    fun shouldRenderWithMarkdown_returnsTrueForTaskList() {
        assertTrue(
            shouldRenderWithMarkdown(
                """
                - [ ] 检查日志
                - [x] 复现问题
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun shouldRenderWithMarkdown_returnsTrueForBlockquote() {
        assertTrue(
            shouldRenderWithMarkdown(
                """
                > 先确认版本
                > 再执行修复
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun shouldRenderWithMarkdown_returnsTrueForMarkdownTableWithoutLeadingPipe() {
        assertTrue(
            shouldRenderWithMarkdown(
                """
                字段 | 说明 | 状态
                --- | --- | ---
                url | 下载地址 | 必填
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun shouldRenderWithMarkdownDuringScrolling_returnsFalseForPlainParagraph() {
        assertFalse(
            shouldRenderWithMarkdownDuringScrolling("这是普通回复，没有列表、链接或代码块。"),
        )
    }

    @Test
    fun shouldRenderWithMarkdownDuringScrolling_keepsStructuredMarkdown() {
        assertTrue(
            shouldRenderWithMarkdownDuringScrolling(
                """
                # 标题

                - 第一项
                - 第二项
                """.trimIndent(),
            ),
        )
        assertTrue(
            shouldRenderWithMarkdownDuringScrolling("请查看 [文档](https://example.com)。"),
        )
    }

    @Test
    fun shouldRenderWithMarkdownDuringScrolling_keepsInlineMarkdownLayoutStable() {
        assertTrue(
            shouldRenderWithMarkdownDuringScrolling(
                "它主要用于实现**通过按钮切换不同页面（Fragment）**的功能。",
            ),
        )
    }

    @Test
    fun normalizeCitationMarkdownForDisplay_rewritesCitationSyntax() {
        assertEquals(
            "巴黎是法国首都。[〔example.com〕](citation:abc123)",
            normalizeCitationMarkdownForDisplay(
                "巴黎是法国首都。[citation,example.com](abc123)",
            ),
        )
    }
}
