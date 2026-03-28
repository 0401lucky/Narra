package com.example.myapplication.conversation

import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.imageMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingReplyBufferTest {
    @Test
    fun advanceFrame_revealsContentReasoningAndImages() {
        val buffer = StreamingReplyBuffer()

        buffer.appendContent("你好")
        buffer.appendReasoning("先分析")
        buffer.appendImage(imageMessagePart(uri = "https://cdn.example.com/test.png"))

        val advanced = buffer.advanceFrame(streamCompleted = false)

        assertTrue(advanced)
        assertEquals("你好", buffer.visibleContent())
        assertEquals("先分析", buffer.visibleReasoning())
        assertEquals(2, buffer.visibleParts().size)
        assertEquals(ChatMessagePartType.TEXT, buffer.visibleParts()[0].type)
        assertEquals(ChatMessagePartType.IMAGE, buffer.visibleParts()[1].type)
        assertFalse(buffer.hasPending())
    }

    @Test
    fun advanceFrame_usesLargerBatchWhenStreamCompleted() {
        val buffer = StreamingReplyBuffer()
        buffer.appendContent("a".repeat(120))

        val advanced = buffer.advanceFrame(streamCompleted = true)

        assertTrue(advanced)
        assertEquals(96, buffer.visibleContent().length)
        assertTrue(buffer.hasPending())
    }
}
