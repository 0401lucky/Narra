package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayLongformTextTest {

    @Test
    fun buildCharacterDialogueAnnotatedString_withoutQuotes_highlightsWholeText() {
        val narrationColor = Color(0xFFCCCCCC)
        val dialogueColor = Color(0xFF90CAF9)

        val rendered = buildCharacterDialogueAnnotatedString(
            text = "你好。",
            narrationColor = narrationColor,
            dialogueColor = dialogueColor,
        )

        assertEquals("你好。", rendered.text)
        assertEquals(1, rendered.spanStyles.size)
        assertEquals(dialogueColor, rendered.spanStyles.single().item.color)
        assertEquals(FontWeight.SemiBold, rendered.spanStyles.single().item.fontWeight)
    }

    @Test
    fun buildCharacterDialogueAnnotatedString_withQuotes_keepsNarrationAndQuotedHighlight() {
        val narrationColor = Color(0xFFCCCCCC)
        val dialogueColor = Color(0xFF90CAF9)

        val rendered = buildCharacterDialogueAnnotatedString(
            text = "他压低声音说：\"你好\"，随后沉默。",
            narrationColor = narrationColor,
            dialogueColor = dialogueColor,
        )

        assertEquals("他压低声音说：\"你好\"，随后沉默。", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.color == narrationColor })
        assertTrue(rendered.spanStyles.any { it.item.color == dialogueColor })
    }
}
