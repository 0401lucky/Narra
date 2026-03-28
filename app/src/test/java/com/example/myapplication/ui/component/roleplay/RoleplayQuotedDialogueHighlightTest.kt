package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayQuotedDialogueHighlightTest {
    @Test
    fun buildQuotedDialogueAnnotatedString_highlightsChineseQuotedText() {
        val narrationColor = Color(0xFFCCCCCC)
        val dialogueColor = Color(0xFF90CAF9)
        val text = "她轻声说：“今晚别再躲我。”然后移开视线。"

        val annotated = buildQuotedDialogueAnnotatedString(
            text = text,
            narrationColor = narrationColor,
            dialogueColor = dialogueColor,
        )

        assertEquals(text, annotated.text)
        assertTrue(
            annotated.spanStyles.any { range ->
                range.item.color == dialogueColor &&
                    annotated.text.substring(range.start, range.end) == "“今晚别再躲我。”"
            },
        )
        assertTrue(
            annotated.spanStyles.any { range ->
                range.item.color == narrationColor &&
                    annotated.text.substring(range.start, range.end).contains("她轻声说：")
            },
        )
    }

    @Test
    fun buildQuotedDialogueAnnotatedString_highlightsEnglishQuotedText() {
        val narrationColor = Color(0xFFCCCCCC)
        val dialogueColor = Color(0xFF80D8FF)
        val text = "He paused, then said \"Stay behind me.\" and closed the door."

        val annotated = buildQuotedDialogueAnnotatedString(
            text = text,
            narrationColor = narrationColor,
            dialogueColor = dialogueColor,
        )

        assertEquals(text, annotated.text)
        assertTrue(
            annotated.spanStyles.any { range ->
                range.item.color == dialogueColor &&
                    annotated.text.substring(range.start, range.end) == "\"Stay behind me.\""
            },
        )
    }
}
