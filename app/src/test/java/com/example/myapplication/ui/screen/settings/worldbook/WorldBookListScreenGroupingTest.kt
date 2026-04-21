package com.example.myapplication.ui.screen.settings.worldbook

import com.example.myapplication.model.WorldBookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookListScreenGroupingTest {
    @Test
    fun buildWorldBookBooks_groupsByBookNameAndPreservesEntryOrder() {
        val books = buildWorldBookBooks(
            listOf(
                WorldBookEntry(
                    id = "entry-2",
                    bookId = "book-lizhu",
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
                    bookId = "book-lizhu",
                    title = "璃珠都市",
                    content = "A",
                    sourceBookName = "璃珠都市设定",
                    insertionOrder = 10,
                    createdAt = 10L,
                ),
                WorldBookEntry(
                    id = "entry-3",
                    bookId = "book-white",
                    title = "白塔城",
                    content = "C",
                    sourceBookName = "白塔设定",
                    insertionOrder = 5,
                    createdAt = 5L,
                ),
            ),
        )

        assertEquals(listOf("白塔设定", "璃珠都市设定"), books.map { it.name })
        assertEquals(listOf("book-white", "book-lizhu"), books.map { it.id })
        assertEquals(listOf("璃珠都市", "夜巡守则"), books.last().entries.map { it.title })
    }

    @Test
    fun buildWorldBookBooks_groupsEntryWithBlankSourceBookNameByBookId() {
        val books = buildWorldBookBooks(
            listOf(
                WorldBookEntry(
                    id = "has-name",
                    bookId = "book-a",
                    title = "条目 1",
                    content = "a",
                    sourceBookName = "A 书",
                    insertionOrder = 10,
                    createdAt = 10L,
                ),
                WorldBookEntry(
                    id = "blank-name",
                    bookId = "book-a",
                    title = "条目 2",
                    content = "b",
                    sourceBookName = "",
                    insertionOrder = 20,
                    createdAt = 20L,
                ),
            ),
        )

        assertEquals(1, books.size)
        assertEquals("book-a", books.single().id)
        assertEquals(
            "取名以第一条非空 sourceBookName 为准",
            "A 书",
            books.single().name,
        )
        assertEquals(
            listOf("条目 1", "条目 2"),
            books.single().entries.map { it.title },
        )
    }

    @Test
    fun buildWorldBookBooks_skipsEntryWithBothBookIdAndNameBlank() {
        val books = buildWorldBookBooks(
            listOf(
                WorldBookEntry(
                    id = "standalone",
                    bookId = "",
                    title = "独立条目",
                    content = "x",
                    sourceBookName = "",
                    createdAt = 5L,
                ),
            ),
        )

        assertTrue(
            "resolvedBookId 为空的条目仍然不该被分组为书",
            books.isEmpty(),
        )
    }
}

