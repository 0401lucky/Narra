package com.example.myapplication.data.repository.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicProtocolSupportTest {
    @Test
    fun parseStreamData_returnsContentForTextDelta() {
        val delta = AnthropicProtocolSupport.parseStreamData(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"你好"}}""",
        )

        assertEquals("你好", delta.content)
    }

    @Test
    fun parseStreamData_doesNotThrowWhenTextDeltaIsJsonObject() {
        val delta = AnthropicProtocolSupport.parseStreamData(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":{"nested":"对象"}}}""",
        )

        assertEquals("", delta.content)
    }

    @Test
    fun parseStreamData_doesNotThrowWhenTextDeltaIsJsonNull() {
        val delta = AnthropicProtocolSupport.parseStreamData(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":null}}""",
        )

        assertEquals("", delta.content)
    }

    @Test
    fun parseStreamData_flagsMessageStop() {
        val delta = AnthropicProtocolSupport.parseStreamData(
            """{"type":"message_stop"}""",
        )

        assertTrue(delta.stop)
    }
}
