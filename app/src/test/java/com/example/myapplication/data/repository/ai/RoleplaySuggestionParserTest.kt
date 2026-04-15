package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.RoleplaySuggestionAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplaySuggestionParserTest {
    @Test
    fun parse_parsesJsonArrayResponse() {
        val suggestions = RoleplaySuggestionParser.parse(
            """
            [
              {"axis":"plot","label":"逼近真相","text":"你刚才那句话，到底是什么意思？"},
              {"axis":"info","label":"追问细节","text":"先告诉我，这里之前到底发生了什么。"},
              {"axis":"emotion","label":"压住退路","text":"你既然早就知道，就别再瞒着我。"}
            ]
            """.trimIndent(),
        )

        assertEquals(3, suggestions.size)
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
        assertEquals("逼近真相", suggestions[0].label)
    }

    @Test
    fun parse_parsesWrappedSuggestionsObject() {
        val suggestions = RoleplaySuggestionParser.parse(
            """
            {
              "suggestions": [
                {"axis":"plot","label":"先逼一步","text":"你先把关键那句说完。"},
                {"axis":"info","label":"补齐细节","text":"那晚这里到底出了什么事？"},
                {"axis":"emotion","label":"压住情绪","text":"我不想再被你蒙在鼓里。"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(3, suggestions.size)
        assertEquals(RoleplaySuggestionAxis.INFO, suggestions[1].axis)
        assertEquals("补齐细节", suggestions[1].label)
    }

    @Test
    fun parse_parsesAxisObjectResponse() {
        val suggestions = RoleplaySuggestionParser.parse(
            """
            {
              "plot": {"label":"逼近真相","text":"你刚才那句到底是什么意思？"},
              "info": {"label":"追问细节","text":"这里之前到底发生过什么？"},
              "emotion": {"label":"逼近关系","text":"你还想把我推开到什么时候？"}
            }
            """.trimIndent(),
        )

        assertEquals(3, suggestions.size)
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
        assertEquals(RoleplaySuggestionAxis.INFO, suggestions[1].axis)
        assertEquals(RoleplaySuggestionAxis.EMOTION, suggestions[2].axis)
        assertEquals("逼近关系", suggestions[2].label)
    }

    @Test
    fun parse_returnsEmptyForMalformedStructuredPayload() {
        val suggestions = RoleplaySuggestionParser.parse(
            """
            plot: {label: "逼近真相", text: "你刚才那句到底是什么意思？"}
            info: {label: "追问细节", text: "这里之前到底发生过什么？"}
            emotion: {label: "逼近关系", text: "你还想把我推开到什么时候？"}
            """.trimIndent(),
        )

        assertTrue(suggestions.isEmpty())
    }
}
