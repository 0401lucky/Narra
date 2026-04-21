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

    @Test
    fun formatRelativeTime_justNow_underOneMinute() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", formatRelativeTime(now - 30_000L, now = now))
    }

    @Test
    fun formatRelativeTime_minutes() {
        val now = 1_700_000_000_000L
        assertEquals("5 分钟前", formatRelativeTime(now - 5 * 60_000L, now = now))
    }

    @Test
    fun formatRelativeTime_hours() {
        val now = 1_700_000_000_000L
        assertEquals("3 小时前", formatRelativeTime(now - 3 * 3_600_000L, now = now))
    }

    @Test
    fun formatRelativeTime_days() {
        val now = 1_700_000_000_000L
        assertEquals("2 天前", formatRelativeTime(now - 2L * 86_400_000L, now = now))
    }

    @Test
    fun formatRelativeTime_fallsBackToDateBeyondSevenDays() {
        val now = 1_700_000_000_000L
        val eightDaysAgo = now - 8L * 86_400_000L
        val result = formatRelativeTime(eightDaysAgo, now = now)
        // 断言是 yyyy-MM-dd 形态（10 个字符，中间两个 `-`）
        val pattern = Regex("""^\d{4}-\d{2}-\d{2}$""")
        assert(pattern.matches(result)) { "unexpected fallback: $result" }
    }

    @Test
    fun formatRelativeTime_emptyForZeroOrNegative() {
        val now = 1_700_000_000_000L
        assertEquals("", formatRelativeTime(0L, now = now))
        assertEquals("", formatRelativeTime(-1L, now = now))
    }

    @Test
    fun formatRelativeTime_justNow_forFutureClockSkew() {
        val now = 1_700_000_000_000L
        // 服务器时钟跑前面了也不能崩，按"刚刚"兜底
        assertEquals("刚刚", formatRelativeTime(now + 10_000L, now = now))
    }
}
