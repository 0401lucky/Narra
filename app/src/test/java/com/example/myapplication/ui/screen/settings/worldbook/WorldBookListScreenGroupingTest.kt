package com.example.myapplication.ui.screen.settings.worldbook

import com.example.myapplication.model.WorldBookEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldBookListScreenGroupingTest {
    @Test
    fun buildWorldBookBooks_groupsByBookNameAndPreservesEntryOrder() {
        val books = buildWorldBookBooks(
            listOf(
                WorldBookEntry(
                    id = "entry-2",
                    title = "夜巡守则",
                    content = "B",
                    sourceBookName = "璃珠都市设定",
                    insertionOrder = 20,
                    createdAt = 20L,
                ),
                WorldBookEntry(
                    id = "entry-standalone",
                    title = "独立条目",
                    content = "X",
                    sourceBookName = "",
                    insertionOrder = 1,
                    createdAt = 1L,
                ),
                WorldBookEntry(
                    id = "entry-1",
                    title = "璃珠都市",
                    content = "A",
                    sourceBookName = "璃珠都市设定",
                    insertionOrder = 10,
                    createdAt = 10L,
                ),
                WorldBookEntry(
                    id = "entry-3",
                    title = "白塔城",
                    content = "C",
                    sourceBookName = "白塔设定",
                    insertionOrder = 5,
                    createdAt = 5L,
                ),
            ),
        )

        assertEquals(listOf("白塔设定", "璃珠都市设定"), books.map { it.name })
        assertEquals(listOf("璃珠都市", "夜巡守则"), books.last().entries.map { it.title })
    }
}
