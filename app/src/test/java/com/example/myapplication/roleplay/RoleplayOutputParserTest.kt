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
    fun parseAssistantOutput_recognizesThoughtSegments() {
        val result = parser.parseAssistantOutput(
            rawContent = """
                <thought>指尖停在发送键上方，却还是没按下去。</thought>
                <dialogue speaker="character">算了，你先忙。</dialogue>
            """.trimIndent(),
            characterName = "霜岚",
            allowNarration = true,
        )

        assertEquals(2, result.size)
        assertEquals(RoleplayContentType.THOUGHT, result[0].contentType)
        assertEquals(RoleplaySpeaker.CHARACTER, result[0].speaker)
        assertEquals("指尖停在发送键上方，却还是没按下去。", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
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

    @Test
    fun parseAssistantOutput_splitsQuotedDialogueFromPlainNarration() {
        val result = parser.parseAssistantOutput(
            rawContent = "他皱了皱眉头，把手插进口袋。 '我不知道你在说什么。'",
            characterName = "余罪",
            allowNarration = true,
        )

        assertEquals(2, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertEquals("他皱了皱眉头，把手插进口袋。", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertEquals("我不知道你在说什么。", result[1].content)
    }

    @Test
    fun parseAssistantOutput_splitsStarActionAndPlainDialogue() {
        val result = parser.parseAssistantOutput(
            rawContent = "*我抬眼看向他* 你刚才那句话，到底是什么意思？",
            characterName = "林晚",
            allowNarration = true,
        )

        assertEquals(2, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertEquals("我抬眼看向他", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertTrue(result[1].content.contains("到底是什么意思"))
    }

    @Test
    fun parseAssistantOutput_keepsInlineQuotedWordInsideNarrationFlow() {
        val result = parser.parseAssistantOutput(
            rawContent = "他整个人都显得太“硬”了，像是块没被打磨过的石头。 “待会我们队长问起来，你知道该怎么说吧？”",
            characterName = "余罪",
            allowNarration = true,
        )

        assertEquals(2, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertEquals("他整个人都显得太“硬”了，像是块没被打磨过的石头。", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertEquals("待会我们队长问起来，你知道该怎么说吧？", result[1].content)
    }

    @Test
    fun parseAssistantOutput_keepsEmbeddedQuotedSpeechAsSeparateDialogue() {
        val result = parser.parseAssistantOutput(
            rawContent = "他看了你一眼，说：“别紧张。”随后把资料推了过来。",
            characterName = "余罪",
            allowNarration = true,
        )

        assertEquals(3, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertEquals("他看了你一眼，说：", result[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertEquals("别紧张。", result[1].content)
        assertEquals(RoleplayContentType.NARRATION, result[2].contentType)
        assertEquals("随后把资料推了过来。", result[2].content)
    }

    @Test
    fun parseAssistantOutput_treatsUntaggedNarrationAroundDialogueTagsAsNarration() {
        val result = parser.parseAssistantOutput(
            rawContent = """
                余罪的呼吸在那一瞬间停滞了。

                他手里的手机屏幕还亮着。

                <dialogue speaker="character" emotion="压抑的怒火">“你再说一遍试试。”</dialogue>

                他的声音低得可怕，像是从牙缝里挤出来的。
            """.trimIndent(),
            characterName = "余罪",
            allowNarration = true,
        )

        assertEquals(3, result.size)
        assertEquals(RoleplayContentType.NARRATION, result[0].contentType)
        assertTrue(result[0].content.contains("停滞了"))
        assertTrue(result[0].content.contains("手机屏幕还亮着"))
        assertEquals(RoleplayContentType.DIALOGUE, result[1].contentType)
        assertEquals("压抑的怒火", result[1].emotion)
        assertEquals("你再说一遍试试。", result[1].content)
        assertEquals(RoleplayContentType.NARRATION, result[2].contentType)
        assertTrue(result[2].content.contains("他的声音低得可怕"))
    }

    @Test
    fun parseAssistantOutput_splitsNarrationMixedInsideDialogueTag() {
        val result = parser.parseAssistantOutput(
            rawContent = """
                <dialogue speaker="character" emotion="倔强的挑衅">“我是不是废物，轮不到你来评判。”他冷笑一声，下巴扬起的弧度又硬又傲，“你以为你是谁？”</dialogue>
            """.trimIndent(),
            characterName = "余罪",
            allowNarration = true,
        )

        assertEquals(3, result.size)
        assertEquals(RoleplayContentType.DIALOGUE, result[0].contentType)
        assertEquals("倔强的挑衅", result[0].emotion)
        assertEquals("我是不是废物，轮不到你来评判。", result[0].content)
        assertEquals(RoleplayContentType.NARRATION, result[1].contentType)
        assertTrue(result[1].content.contains("他冷笑一声"))
        assertEquals(RoleplayContentType.DIALOGUE, result[2].contentType)
        assertEquals("倔强的挑衅", result[2].emotion)
        assertEquals("你以为你是谁？", result[2].content)
    }

    @Test
    fun parseAssistantOutput_dropsMalformedProtocolAttributesFromBody() {
        val result = parser.parseAssistantOutput(
            rawContent = """
                <dialogue speaker="沈晏清" emotion="无意中带着一丝讨好"
                声音放得很低。
            """.trimIndent(),
            characterName = "沈晏清",
            allowNarration = true,
        )

        assertEquals(1, result.size)
        assertEquals(RoleplayContentType.DIALOGUE, result.single().contentType)
        assertEquals("声音放得很低。", result.single().content)
    }

    @Test
    fun stripMarkup_normalizesNarrativeAliasAndMalformedProtocolForStreaming() {
        val stripped = parser.stripMarkup(
            """
                <narrative>手指抵着杯壁磨了磨。</narrative>
                <dialogue speaker="沈晏清" emotion="无意中带着一丝讨好"
                声音放得很低。
            """.trimIndent(),
        )

        assertTrue(!stripped.contains("<narrative"))
        assertTrue(!stripped.contains("speaker="))
        assertTrue(stripped.contains("手指抵着杯壁磨了磨。"))
        assertTrue(stripped.contains("声音放得很低。"))
    }

    @Test
    fun stripMarkup_removesLlMControlTokensFromVisibleText() {
        val stripped = parser.stripMarkup(
            """
                <dialogue speaker="character">你先别躲。</dialogue>
                <|end_of_sentence|>
                end_of_sentence|>
            """.trimIndent(),
        )

        assertEquals("你先别躲。", stripped)
    }
}
