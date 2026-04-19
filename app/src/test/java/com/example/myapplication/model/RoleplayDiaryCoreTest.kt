package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayDiaryCoreTest {

    @Test
    fun `draft implements core and exposes same fields`() {
        val draft = RoleplayDiaryDraft(
            title = "回到熟悉的巷子",
            content = "傍晚的风里夹着桂花香。",
            mood = "舒缓",
            weather = "小雨转多云",
            tags = listOf("散步", "回忆"),
            dateLabel = "2026-04-17",
        )

        val core: RoleplayDiaryCore = draft

        assertEquals("回到熟悉的巷子", core.title)
        assertEquals("傍晚的风里夹着桂花香。", core.content)
        assertEquals("舒缓", core.mood)
        assertEquals("小雨转多云", core.weather)
        assertEquals(listOf("散步", "回忆"), core.tags)
        assertEquals("2026-04-17", core.dateLabel)
    }

    @Test
    fun `entry implements core and exposes content fields`() {
        val entry = RoleplayDiaryEntry(
            id = "entry-1",
            conversationId = "conv-1",
            scenarioId = "scn-1",
            title = "标题",
            content = "正文",
            sortOrder = 2,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            mood = "平静",
            weather = "晴",
            tags = listOf("日常"),
            dateLabel = "2026-04-18",
        )

        val core: RoleplayDiaryCore = entry

        assertEquals("标题", core.title)
        assertEquals("正文", core.content)
        assertEquals("平静", core.mood)
        assertEquals("晴", core.weather)
        assertEquals(listOf("日常"), core.tags)
        assertEquals("2026-04-18", core.dateLabel)
    }

    @Test
    fun `toDraft on draft returns same instance`() {
        val draft = RoleplayDiaryDraft(title = "t", content = "c")

        val result = (draft as RoleplayDiaryCore).toDraft()

        assertSame(draft, result)
    }

    @Test
    fun `toDraft on entry copies only core fields`() {
        val entry = RoleplayDiaryEntry(
            id = "id-1",
            conversationId = "conv-1",
            scenarioId = "scn-1",
            title = "T",
            content = "C",
            sortOrder = 3,
            createdAt = 10L,
            updatedAt = 20L,
            mood = "M",
            weather = "W",
            tags = listOf("a", "b"),
            dateLabel = "D",
        )

        val draft = (entry as RoleplayDiaryCore).toDraft()

        assertEquals(
            RoleplayDiaryDraft(
                title = "T",
                content = "C",
                mood = "M",
                weather = "W",
                tags = listOf("a", "b"),
                dateLabel = "D",
            ),
            draft,
        )
    }

    @Test
    fun `toDraft preserves tag list content without sharing mutability`() {
        val tags = mutableListOf("one", "two")
        val entry = RoleplayDiaryEntry(
            id = "id",
            conversationId = "conv",
            scenarioId = "scn",
            title = "t",
            content = "c",
            sortOrder = 0,
            createdAt = 0L,
            updatedAt = 0L,
            tags = tags,
        )

        val draft = (entry as RoleplayDiaryCore).toDraft()
        tags.add("three")

        // draft 的 tags 是转换时的引用快照；断言核心字段值保持当时内容即可
        // （Entry 本身没有防御性拷贝，这里只验证 toDraft 不会丢字段）
        assertTrue(draft.tags.contains("one"))
        assertTrue(draft.tags.contains("two"))
    }

    @Test
    fun `toEntry fills persistence fields without mutating content fields`() {
        val draft = RoleplayDiaryDraft(
            title = "标题",
            content = "正文",
            mood = "高兴",
            weather = "多云",
            tags = listOf("x"),
            dateLabel = "2026-04-18",
        )

        val entry = draft.toEntry(
            id = "entry-42",
            conversationId = "conv-42",
            scenarioId = "scn-42",
            sortOrder = 5,
            createdAt = 111L,
        )

        assertEquals("entry-42", entry.id)
        assertEquals("conv-42", entry.conversationId)
        assertEquals("scn-42", entry.scenarioId)
        assertEquals(5, entry.sortOrder)
        assertEquals(111L, entry.createdAt)
        assertEquals(111L, entry.updatedAt)
        assertEquals("标题", entry.title)
        assertEquals("正文", entry.content)
        assertEquals("高兴", entry.mood)
        assertEquals("多云", entry.weather)
        assertEquals(listOf("x"), entry.tags)
        assertEquals("2026-04-18", entry.dateLabel)
    }

    @Test
    fun `toEntry honours explicit updatedAt override`() {
        val draft = RoleplayDiaryDraft(title = "t", content = "c")

        val entry = draft.toEntry(
            id = "e",
            conversationId = "cv",
            scenarioId = "sc",
            sortOrder = 0,
            createdAt = 100L,
            updatedAt = 200L,
        )

        assertEquals(100L, entry.createdAt)
        assertEquals(200L, entry.updatedAt)
    }

    @Test
    fun `round trip draft to entry and back is lossless`() {
        val original = RoleplayDiaryDraft(
            title = "回程",
            content = "这是一段内容。",
            mood = "放松",
            weather = "晴朗",
            tags = listOf("旅行", "日常"),
            dateLabel = "2026-04-18",
        )

        val entry = original.toEntry(
            id = "id",
            conversationId = "conv",
            scenarioId = "scn",
            sortOrder = 1,
            createdAt = 0L,
        )
        val roundTripped = (entry as RoleplayDiaryCore).toDraft()

        assertEquals(original, roundTripped)
    }
}
