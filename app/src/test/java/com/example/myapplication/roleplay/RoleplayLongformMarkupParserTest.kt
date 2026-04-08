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

    @Test
    fun parseParagraphs_smartSplitsSingleLongNarrationBlock() {
        val raw = "余罪盯着对方没有立刻开口，他先是把袖口一点点卷起来，像是在给自己争取一口喘息的时间。雨水顺着窗沿往下淌，室内的灯光把每一道水痕都照得发亮，他却只是盯着那张脸，试图从对方迟疑的眼神里找出一点破绽。片刻后，他才慢慢抬起下巴，压着火气问道：“你到底还瞒了我多少事？”空气像被这句话猛地扯紧了，对方没有立刻回应，只是下意识往后退了半步，连呼吸都显得乱了几分。余罪没有再逼得太快，只是顺势往前一步，把声音压得更低，也更危险。"

        val paragraphs = RoleplayLongformMarkupParser.parseParagraphs(raw)

        assertTrue(paragraphs.size >= 2)
        assertTrue(paragraphs.size <= 5)
        assertTrue(paragraphs.all { it.plainText.isNotBlank() })
    }

    @Test
    fun parseParagraphs_doesNotBreakCharacterSpeechOrThoughtTags() {
        val raw = "她没有立刻移开视线，只是静静看着你。<char>“你要真想问，就别拐弯抹角。”</char><thought>（不能再被他牵着走了。）</thought>她把手指慢慢收紧，像是终于下定了某种决心。"

        val paragraphs = RoleplayLongformMarkupParser.parseParagraphs(raw)
        val characterSpeechTexts = paragraphs.flatMap { paragraph ->
            paragraph.spans.filter { it.type == RoleplayLongformSpanType.CHARACTER_SPEECH }.map { it.text }
        }
        val thoughtTexts = paragraphs.flatMap { paragraph ->
            paragraph.spans.filter { it.type == RoleplayLongformSpanType.THOUGHT }.map { it.text }
        }

        assertEquals(listOf("“你要真想问，就别拐弯抹角。”"), characterSpeechTexts)
        assertEquals(listOf("（不能再被他牵着走了。）"), thoughtTexts)
    }

    @Test
    fun splitDisplayParagraphs_smartSplitsPlainLongText() {
        val paragraphs = RoleplayLongformMarkupParser.splitDisplayParagraphs(
            "他明明已经把话说得很慢了，却还是没有得到答案。余罪没有急着追问，只是盯着对方躲闪的眼睛，像是要把那一点点犹豫全都逼出来。片刻后，他才压低声音补上一句：“你现在不说，等会儿就更说不清了。”对方仍旧沉默，连指尖都绷得发白。余罪没有退开，只是继续把视线钉在那张脸上，像是非要把最后那点侥幸心也一点点逼碎。"
        )

        assertTrue(paragraphs.size >= 2)
        assertTrue(paragraphs.all { it.isNotBlank() })
    }
}
