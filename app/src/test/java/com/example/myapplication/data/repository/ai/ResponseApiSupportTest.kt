package com.example.myapplication.data.repository.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseApiSupportTest {
    @Test
    fun parseStreamEvent_returnsContentDeltaForStringDelta() {
        val event = ResponseApiSupport.parseStreamEvent(
            """{"type":"response.output_text.delta","delta":"你好"}""",
        )

        assertEquals(ResponseApiStreamEvent.ContentDelta("你好"), event)
    }

    @Test
    fun parseStreamEvent_doesNotThrowWhenDeltaIsJsonObject() {
        val event = ResponseApiSupport.parseStreamEvent(
            """{"type":"response.output_text.delta","delta":{"text":"嵌套对象"}}""",
        )

        assertEquals(ResponseApiStreamEvent.ContentDelta(""), event)
    }

    @Test
    fun parseStreamEvent_doesNotThrowWhenDeltaIsJsonNull() {
        val event = ResponseApiSupport.parseStreamEvent(
            """{"type":"response.reasoning_text.delta","delta":null}""",
        )

        assertEquals(ResponseApiStreamEvent.ReasoningDelta(""), event)
    }

    @Test
    fun parseStreamEvent_returnsNullForInvalidJson() {
        assertNull(ResponseApiSupport.parseStreamEvent("not-json"))
    }
}
