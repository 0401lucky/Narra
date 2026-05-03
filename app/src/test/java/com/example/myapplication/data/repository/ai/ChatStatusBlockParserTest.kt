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

    @Test
    fun extract_normalizesEscapedNewlinesInsideStatusCard() {
        val parts = ChatStatusBlockParser.extract(
            "状态栏：时间：2026年5月2日 03:35\\n地点：大渡口 陆家老宅 阳台\\n状态：指尖冰凉，盯着屏幕\n\n回来了。",
        )

        assertEquals(ChatMessagePartType.STATUS, parts.first().type)
        assertTrue(parts.first().text.contains("时间：2026年5月2日 03:35\n地点：大渡口"))
        assertTrue(parts.first().text.contains("\n状态：指尖冰凉"))
        assertEquals(ChatMessagePartType.TEXT, parts.last().type)
        assertEquals("回来了。", parts.last().text)
    }

    @Test
    fun extract_detectsSlashWrappedStatusBlock() {
        val parts = ChatStatusBlockParser.extract(
            """
                status/心情：被激到，眼眶泛红但死撑着没掉下来
                声音：有点哑/status

                我上个月喝多了，当着全家人的面说了。
            """.trimIndent(),
        )

        assertEquals(ChatMessagePartType.STATUS, parts.first().type)
        assertTrue(parts.first().text.contains("心情：被激到"))
        assertTrue(parts.first().text.contains("声音：有点哑"))
        assertEquals(ChatMessagePartType.TEXT, parts.last().type)
        assertEquals("我上个月喝多了，当着全家人的面说了。", parts.last().text)
    }
}
