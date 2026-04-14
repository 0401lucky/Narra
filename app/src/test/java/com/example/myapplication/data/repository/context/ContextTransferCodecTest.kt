package com.example.myapplication.data.repository.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.WorldBookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTransferCodecTest {
    private val codec = ContextTransferCodec()

    @Test
    fun encodeAndDecode_preservesBundleFields() {
        val bundle = ContextDataBundle(
            version = 1,
            assistants = listOf(
                Assistant(
                    id = "assistant-1",
                    name = "侦探助手",
                    scenario = "雾都侦探社",
                    linkedWorldBookBookIds = listOf("book-white-tower"),
                ),
            ),
            worldBookEntries = listOf(
                WorldBookEntry(
                    id = "world-1",
                    bookId = "book-white-tower",
                    title = "白塔城",
                    content = "北境贸易都会",
                    keywords = listOf("白塔城"),
                    secondaryKeywords = listOf("商会"),
                    selective = true,
                    caseSensitive = true,
                    insertionOrder = 50,
                    sourceBookName = "白塔设定",
                ),
            ),
            memoryEntries = listOf(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "用户喜欢短句回复",
                ),
            ),
            conversationSummaries = listOf(
                ConversationSummary(
                    conversationId = "c1",
                    summary = "已经确认主要线索指向北境商会。",
                    coveredMessageCount = 12,
                ),
            ),
        )

        val rawJson = codec.encode(bundle)
        val decoded = codec.decode(rawJson)

        assertTrue(rawJson.contains("白塔城"))
        assertEquals("侦探助手", decoded.assistants.single().name)
        assertEquals(listOf("book-white-tower"), decoded.assistants.single().linkedWorldBookBookIds)
        assertEquals("白塔城", decoded.worldBookEntries.single().title)
        assertEquals("book-white-tower", decoded.worldBookEntries.single().bookId)
        assertEquals(listOf("商会"), decoded.worldBookEntries.single().secondaryKeywords)
        assertTrue(decoded.worldBookEntries.single().selective)
        assertTrue(decoded.worldBookEntries.single().caseSensitive)
        assertEquals(50, decoded.worldBookEntries.single().insertionOrder)
        assertEquals("白塔设定", decoded.worldBookEntries.single().sourceBookName)
        assertEquals("用户喜欢短句回复", decoded.memoryEntries.single().content)
        assertEquals("已经确认主要线索指向北境商会。", decoded.conversationSummaries.single().summary)
    }
}
