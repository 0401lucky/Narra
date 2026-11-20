package com.example.myapplication.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEmbeddingChunkerTest {
    @Test
    fun split_longBody_indexesEveryCharacterIncludingTail() {
        val content = "早先的合成经历。".repeat(9_000) + "最后约定在11月19日706号房见林芷。"
        val chunks = MemoryEmbeddingChunker().split(content)
        val covered = BooleanArray(content.length)
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.index)
            assertEquals(content.substring(chunk.start, chunk.end), chunk.text)
            assertTrue(chunk.text.length <= MemoryEmbeddingChunker.MAX_CHARS)
            for (position in chunk.start until chunk.end) covered[position] = true
        }
        assertTrue(covered.all { it })
        assertTrue(chunks.last().text.endsWith("706号房见林芷。"))
        assertEquals(content.length, chunks.last().end)
    }

    @Test
    fun split_surrogateAtLimit_preservesUtf16BoundariesAndOverlap() {
        val content = "甲".repeat(1_599) + "😀" + "乙".repeat(3_000) + "🧭尾部"
        val chunks = MemoryEmbeddingChunker().split(content)
        chunks.forEach { chunk ->
            assertFalse(chunk.text.first().isLowSurrogate())
            assertFalse(chunk.text.last().isHighSurrogate())
            assertEquals(content.substring(chunk.start, chunk.end), chunk.text)
        }
        assertEquals(1_599, chunks.first().end)
        assertTrue(
            chunks.zipWithNext()
                .all { (left, right) -> right.start < left.end && right.end > left.end })
        assertTrue(chunks.any { "😀" in it.text })
        assertTrue(chunks.last().text.endsWith("🧭尾部"))
    }

    @Test
    fun split_paragraphBoundary_keepsCompleteSourceSpans() {
        val content = "甲".repeat(1_100) + "\n" + "乙".repeat(2_000)
        val chunks = MemoryEmbeddingChunker().split(content)
        assertEquals(1_101, chunks.first().end)
        assertEquals(981, chunks[1].start)
        assertEquals(content.length, chunks.last().end)
    }

    @Test
    fun split_blankInput_doesNotCreatePaidInputs() {
        assertTrue(MemoryEmbeddingChunker().split("").isEmpty())
        assertTrue(MemoryEmbeddingChunker().split(" \n\t").isEmpty())
        assertEquals(
            listOf("  保留两边空格  "),
            MemoryEmbeddingChunker().split("  保留两边空格  ").map { it.text })
    }
}
