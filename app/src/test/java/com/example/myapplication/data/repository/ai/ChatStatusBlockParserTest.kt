package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessagePartType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStatusBlockParserTest {
    @Test
    fun extract_stripsLooseLeadingStatusLines() {
        val parts = ChatStatusBlockParser.extract(
            """
                > 时间：23:03 | 日期：2026年4月29日
                地点：静安区公寓卧室 | 天气：雨停后的深夜
                陆承渊·状态 | 阶段：破线 | 外在：仰躺

                <p style="text-align:center; color:gray;">—— 外滩夜色 ——</p>
                拇指停在后腰窝的位置。
            """.trimIndent(),
        )

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertTrue(!parts.single().text.contains("时间：23:03"))
        assertTrue(!parts.single().text.contains("陆承渊·状态"))
        assertTrue(parts.single().text.contains("外滩夜色"))
        assertTrue(parts.single().text.contains("拇指停在后腰窝"))
    }

    @Test
    fun extract_stripsLooseLeadingStatusLinesWithOpenFence() {
        val parts = ChatStatusBlockParser.extract(
            """
                『时间：10:07 | 日期：2026年4月21日星期二 | 地点：申江新区管委会主任办公室 | 天气：百叶窗的缝隙切碎了日光

                他把录音笔轻轻放在桌面上。
            """.trimIndent(),
        )

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertTrue(!parts.single().text.contains("时间：10:07"))
        assertTrue(parts.single().text.contains("录音笔"))
    }

    @Test
    fun extract_stripsSeparatedLeadingStatusParagraphs() {
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

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertTrue(!parts.single().text.contains("时间：23:03"))
        assertTrue(!parts.single().text.contains("陆承渊·状态"))
        assertTrue(!parts.single().text.contains("对lucky"))
        assertTrue(parts.single().text.contains("外滩夜色"))
    }

    @Test
    fun extract_stripsEscapedNewlinesInsideStatusCard() {
        val parts = ChatStatusBlockParser.extract(
            "状态栏：时间：2026年5月2日 03:35\\n地点：大渡口 陆家老宅 阳台\\n状态：指尖冰凉，盯着屏幕\n\n回来了。",
        )

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertEquals("回来了。", parts.single().text)
    }

    @Test
    fun extract_stripsSlashWrappedStatusBlock() {
        val parts = ChatStatusBlockParser.extract(
            """
                status/心情：被激到，眼眶泛红但死撑着没掉下来
                声音：有点哑/status

                我上个月喝多了，当着全家人的面说了。
            """.trimIndent(),
        )

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertEquals("我上个月喝多了，当着全家人的面说了。", parts.single().text)
    }
}
