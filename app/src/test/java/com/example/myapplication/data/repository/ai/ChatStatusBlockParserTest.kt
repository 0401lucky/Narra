package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessagePartType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStatusBlockParserTest {
    @Test
    fun extract_detectsLooseLeadingStatusLines() {
        val parts = ChatStatusBlockParser.extract(
            """
                > 时间：23:03 | 日期：2026年4月29日
                地点：静安区公寓卧室 | 天气：雨停后的深夜
                陆承渊·状态 | 阶段：破线 | 外在：仰躺

                <p style="text-align:center; color:gray;">—— 外滩夜色 ——</p>
                拇指停在后腰窝的位置。
            """.trimIndent(),
        )

        assertEquals(ChatMessagePartType.STATUS, parts.first().type)
        assertTrue(parts.first().text.contains("时间：23:03"))
        assertTrue(parts.first().text.contains("陆承渊·状态"))
        assertEquals(ChatMessagePartType.TEXT, parts.last().type)
        assertTrue(parts.last().text.contains("外滩夜色"))
        assertTrue(parts.last().text.contains("拇指停在后腰窝"))
    }

    @Test
    fun extract_detectsLooseLeadingStatusLinesWithOpenFence() {
        val parts = ChatStatusBlockParser.extract(
            """
                『时间：10:07 | 日期：2026年4月21日星期二 | 地点：申江新区管委会主任办公室 | 天气：百叶窗的缝隙切碎了日光

                他把录音笔轻轻放在桌面上。
            """.trimIndent(),
        )

        assertEquals(ChatMessagePartType.STATUS, parts.first().type)
        assertTrue(parts.first().text.contains("时间：10:07"))
        assertTrue(!parts.first().text.contains("『时间"))
        assertEquals(ChatMessagePartType.TEXT, parts.last().type)
        assertTrue(parts.last().text.contains("录音笔"))
    }

    @Test
    fun extract_mergesSeparatedLeadingStatusParagraphs() {
        val parts = ChatStatusBlockParser.extract(
            """
                『时间：23:03 ｜ 日期：2026年10月14日 星期三 ｜ 地点：静安区·江屿公寓卧室 ｜ 天气/场景：雨停后的深夜』

                陆承渊·状态
                ├ 阶段: 破线
                ├ 外在: 仰躺，左手圈着江屿的腰
                ├ 对lucky: 等到了，正在消化
                └ 底线: 没了

                —— 外滩夜色 ——
                拇指停在后腰窝的位置。
            """.trimIndent(),
        )

        assertEquals(ChatMessagePartType.STATUS, parts.first().type)
        assertTrue(parts.first().text.contains("时间：23:03"))
        assertTrue(parts.first().text.contains("陆承渊·状态"))
        assertTrue(parts.first().text.contains("阶段: 破线"))
        assertTrue(parts.first().text.contains("对lucky: 等到了"))
        assertTrue(parts.first().text.contains("底线: 没了"))
        assertEquals(ChatMessagePartType.TEXT, parts.last().type)
        assertTrue(!parts.last().text.contains("陆承渊·状态"))
        assertTrue(!parts.last().text.contains("对lucky"))
        assertTrue(parts.last().text.contains("外滩夜色"))
    }
}
