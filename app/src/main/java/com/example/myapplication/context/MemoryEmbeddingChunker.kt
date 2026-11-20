package com.example.myapplication.context

import com.example.myapplication.model.MemoryEmbeddingChunk

/** 不截断正文；每段限制只约束一次向量输入，不约束存档容量。 */
class MemoryEmbeddingChunker(
    private val maxChars: Int = MAX_CHARS,
    private val overlapChars: Int = OVERLAP_CHARS,
) {
    init {
        require(maxChars in 4..MAX_INPUT_CHARS)
        require(overlapChars in 0 until maxChars / 2)
    }

    fun split(content: String): List<MemoryEmbeddingChunk> {
        if (content.isBlank()) return emptyList()
        val chunks = mutableListOf<MemoryEmbeddingChunk>()
        var start = 0
        while (start < content.length) {
            var end = safeBoundary(content, minOf(content.length, start + maxChars))
            if (end < content.length) {
                val minimumEnd = start + maxChars / 2
                val paragraphEnd = content.lastIndexOf('\n', end - 1)
                if (paragraphEnd >= minimumEnd) end = paragraphEnd + 1
            }
            val text = content.substring(start, end)
            if (text.isNotBlank()) {
                chunks += MemoryEmbeddingChunk(chunks.size, start, end, text)
            }
            if (end == content.length) break
            start = safeBoundary(content, end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun safeBoundary(text: String, position: Int): Int =
        if (position in 1 until text.length &&
            text[position - 1].isHighSurrogate() && text[position].isLowSurrogate()
        ) position - 1 else position

    companion object {
        const val PREPROCESSING_VERSION = 1
        const val MAX_CHARS = 1_600
        const val OVERLAP_CHARS = 120
        const val MAX_INPUT_CHARS = 2_048
        const val BATCH_SIZE = 32
    }
}
