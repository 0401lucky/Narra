package com.example.myapplication.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayLongformMarkupParserTest {
    @Test
    fun stripMarkupForDisplay_removesInternalTags() {
        val raw = "她靠近一步。<char>“别躲我。”</char><thought>（他果然还是心软。）</thought>"

        val display = RoleplayLongformMarkupParser.stripMarkupForDisplay(raw)

        assertEquals("她靠近一步。“别躲我。”（他果然还是心软。）", display)
    }

    @Test
    fun parseParagraphs_preservesCharacterSpeechAndThoughtSpans() {
        val raw = "夜风擦过窗沿。<char>“你终于回来了。”</char><thought>（这一次不能再让他走。）</thought>"

        val paragraphs = RoleplayLongformMarkupParser.parseParagraphs(raw)

        assertEquals(1, paragraphs.size)
        assertEquals(3, paragraphs.single().spans.size)
        assertEquals(RoleplayLongformSpanType.NARRATION, paragraphs.single().spans[0].type)
        assertEquals(RoleplayLongformSpanType.CHARACTER_SPEECH, paragraphs.single().spans[1].type)
        assertEquals(RoleplayLongformSpanType.THOUGHT, paragraphs.single().spans[2].type)
        assertEquals("夜风擦过窗沿。“你终于回来了。”（这一次不能再让他走。）", paragraphs.single().plainText)
    }

    @Test
    fun parseParagraphs_whenNoTags_returnsNarrationParagraph() {
        val paragraphs = RoleplayLongformMarkupParser.parseParagraphs("她只是安静地看着你。")

        assertEquals(1, paragraphs.size)
        assertEquals(1, paragraphs.single().spans.size)
        assertEquals(RoleplayLongformSpanType.NARRATION, paragraphs.single().spans.single().type)
        assertEquals("她只是安静地看着你。", paragraphs.single().plainText)
    }

    @Test
    fun stripMarkupForDisplay_removesDanglingTagTail() {
        val display = RoleplayLongformMarkupParser.stripMarkupForDisplay("她张了张口，最后只剩下<char")

        assertEquals("她张了张口，最后只剩下", display)
    }

    @Test
    fun parseParagraphs_ignoresBlankParagraphs() {
        val paragraphs = RoleplayLongformMarkupParser.parseParagraphs("\n\n<char>“好。”</char>\n\n")

        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs.single().plainText.isNotBlank())
    }
}
