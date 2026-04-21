package com.example.myapplication.ui.screen.settings.worldbook

import com.example.myapplication.model.WorldBookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorldBookListStyleTest {

    @Test
    fun bookSpineColor_isStableForSameBookId() {
        val first = bookSpineColor("book-alpha")
        val second = bookSpineColor("book-alpha")
        assertEquals(first, second)
    }

    @Test
    fun bookSpineColor_differsBetweenDistinctBookIds() {
        val a = bookSpineColor("book-alpha")
        val b = bookSpineColor("book-beta")
        assertNotEquals(a, b)
    }

    @Test
    fun bookSpineColor_handlesBlankIdWithoutCrash() {
        // 不应抛异常；返回值具体为多少不 assert，防止回归
        bookSpineColor("")
    }

    private fun entryOf(
        keywords: List<String> = emptyList(),
        aliases: List<String> = emptyList(),
        secondaryKeywords: List<String> = emptyList(),
    ): WorldBookEntry = WorldBookEntry(
        id = "id",
        title = "",
        content = "",
        keywords = keywords,
        aliases = aliases,
        secondaryKeywords = secondaryKeywords,
    )

    @Test
    fun firstRealKeywords_picksFromKeywordsFirst() {
        val entry = entryOf(keywords = listOf("alpha", "beta", "gamma", "delta"))
        assertEquals(listOf("alpha", "beta", "gamma"), firstRealKeywords(entry))
    }

    @Test
    fun firstRealKeywords_fallsThroughToAliasesThenSecondary() {
        val entry = entryOf(
            keywords = listOf("alpha"),
            aliases = listOf("beta"),
            secondaryKeywords = listOf("gamma"),
        )
        assertEquals(listOf("alpha", "beta", "gamma"), firstRealKeywords(entry))
    }

    @Test
    fun firstRealKeywords_deduplicatesAndTrims() {
        val entry = entryOf(
            keywords = listOf(" alpha ", "alpha", ""),
            aliases = listOf("alpha", "beta"),
        )
        assertEquals(listOf("alpha", "beta"), firstRealKeywords(entry))
    }

    @Test
    fun firstRealKeywords_keepsRegexLiteralIntact() {
        val entry = entryOf(keywords = listOf("/foo,bar/i", "beta"))
        assertEquals(listOf("/foo,bar/i", "beta"), firstRealKeywords(entry))
    }

    @Test
    fun firstRealKeywords_respectsLimit() {
        val entry = entryOf(keywords = listOf("a", "b", "c", "d", "e"))
        assertEquals(listOf("a", "b"), firstRealKeywords(entry, limit = 2))
    }

    @Test
    fun firstRealKeywords_returnsEmptyWhenNothingAvailable() {
        assertEquals(emptyList<String>(), firstRealKeywords(entryOf()))
    }
}
