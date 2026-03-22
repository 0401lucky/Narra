package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplaySpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayOutputParserTest {
    private val parser = RoleplayOutputParser()

    @Test
    fun parseAssistantOutput_splitsNarrationAndDialogueSegments() {
        val result = parser.parseAssistantOutput(
            rawContent = """
                <narration>夜色像薄纱一样垂下来。</narration>
                <dialogue speaker="character" emotion="平静">你终于来了。</dialogue>
            """.trimIndent(),
            characterName = "霜岚",
            allowNarration = true,
        )

        assertEquals(2, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertEquals(RoleplaySpeaker.NARRATOR, result[0].speaker)
        assertEquals("夜色像薄纱一样垂下来。", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertEquals(RoleplaySpeaker.CHARACTER, result[1].speaker)
        assertEquals("霜岚", result[1].speakerName)
        assertEquals("平静", result[1].emotion)
    }

    @Test
    fun parseAssistantOutput_fallsBackToSingleDialogueWhenNoTagsPresent() {
        val result = parser.parseAssistantOutput(
            rawContent = "她抬眼看向你，声音压得很低：今晚别走散。",
            characterName = "霜岚",
            allowNarration = true,
        )

        assertEquals(1, result.size)
        assertEquals(RoleplayContentType.DIALOGUE, result.single().contentType)
        assertEquals(RoleplaySpeaker.CHARACTER, result.single().speaker)
        assertTrue(result.single().content.contains("今晚别走散"))
    }
}
