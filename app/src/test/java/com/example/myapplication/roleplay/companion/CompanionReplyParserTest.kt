package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*
import org.junit.Assert.*
import org.junit.Test

class CompanionReplyParserTest {
    private val parser = CompanionReplyParser()
    private val enabled = CompanionPresentationSnapshot(
        sourceSettingsRevision = 7,
        observation = CompanionObservationSettings(enabled = true),
    )

    @Test
    fun xmlProjection_preservesRawAndRealNodeSpans() {
        val raw = " \r\n<narration sprite=\"normal\">窗边 &amp; 桌角。</narration>\r\n" +
                "<dialogue speaker=\"艾丽\" emotion=\"微笑\" sprite=\"happy\" voice_emotion=\"calm\">你好。👩🏽‍🚀</dialogue>\r\n "
        val reply = parse(raw)
        assertEquals(raw, reply.raw)
        assertEquals(listOf("窗边 & 桌角。", "你好。👩🏽‍🚀"), reply.beats.map { it.content })
        assertEquals(2, reply.beats.size)
        assertEquals("normal", reply.beats[0].spriteKey)
        assertEquals("happy", reply.beats[1].spriteKey)
        assertEquals(CompanionVoiceEmotion.CALM, reply.beats[1].voiceEmotion)
        assertEquals("微笑", reply.beats[1].emotion)
        assertEquals("<narration sprite=\"normal\">窗边 &amp; 桌角。</narration>", slice(reply, 0))
        assertTrue(slice(reply, 1).startsWith("<dialogue"))
        assertTrue(slice(reply, 1).endsWith("</dialogue>"))
        assertEquals(reply.beats.map { it.id }, parse(raw).beats.map { it.id })
        assertNotEquals(reply.beats[0].id, parse(raw, revision = 2).beats[0].id)
    }

    @Test
    fun adjacentSameKindWithDifferentExpressions_remainDifferentBeats() {
        val reply =
            parse("<dialogue sprite=\"happy\">好。</dialogue><dialogue sprite=\"sad\">不过……</dialogue>")
        assertEquals(2, reply.beats.size)
        assertEquals(listOf("happy", "sad"), reply.beats.map { it.spriteKey })
        assertNotEquals(reply.beats[0].id, reply.beats[1].id)
    }

    @Test
    fun voiceAndSpriteAreIndependent_invalidValuesDoNotDestroyText() {
        val raw =
            "<dialogue sprite=\"../file\" emotion=\"自由情绪\" voice_emotion=\"whisper-fast\" future=\"keep\">仍然可读。</dialogue>"
        val reply = parse(raw)
        assertEquals("仍然可读。", reply.displayText)
        assertNull(reply.beats.single().spriteKey)
        assertNull(reply.beats.single().voiceEmotion)
        assertEquals("自由情绪", reply.beats.single().emotion)
        assertTrue(CompanionReplyDiagnostic.INVALID_SPRITE in reply.diagnostics)
        assertTrue(CompanionReplyDiagnostic.INVALID_VOICE_EMOTION in reply.diagnostics)
        assertEquals(raw, reply.raw)
    }

    @Test
    fun plainTextAndLegacyXml_remainReadableWithoutPresentation() {
        for (raw in listOf(
            "普通文本 [happy] 与 [whispers]。",
            "<narration>旧旁白。</narration><thought>旧心声。</thought>"
        )) {
            val reply = parse(raw, snapshot = null)
            assertEquals(raw, reply.raw)
            assertTrue(reply.beats.isNotEmpty())
            assertNull(reply.observation)
            assertTrue(CompanionReplyDiagnostic.MISSING_PRESENTATION in reply.diagnostics)
        }
        assertTrue(parse("普通文本 [happy] 与 [whispers]。").displayText.contains("[happy]"))
    }

    @Test
    fun observationUsesStableIdsAndFrozenLabels() {
        val snapshot = enabled.copy(
            observation = enabled.observation.copy(
                customFields = listOf(CompanionObservationField("obs_coat", "旧衣着名称")),
            )
        )
        val raw =
            "<observe version=\"1\"><field id=\"time\">傍晚</field><field id=\"obs_coat\">蓝 &amp; 白</field></observe><dialogue>坐吧。</dialogue>"
        val first = parse(raw, snapshot = snapshot)
        assertEquals(listOf("时间", "旧衣着名称"), first.observation!!.fields.map { it.label })
        assertEquals("蓝 & 白", first.observation.fields.last().value)
        val renamed = snapshot.copy(
            observation = snapshot.observation.copy(
                customFields = listOf(CompanionObservationField("obs_coat", "新名称"))
            )
        )
        assertEquals("新名称", parse(raw, snapshot = renamed).observation!!.fields.last().label)
        assertEquals("旧衣着名称", first.observation.fields.last().label)
        assertEquals("坐吧。", first.displayText)
        assertFalse(first.displayText.contains("傍晚"))
    }

    @Test
    fun unknownOrDeletedField_clearsWholeObservationButKeepsBody() {
        val raw =
            "<observe version=\"1\"><field id=\"time\">晚上</field><field id=\"obs_deleted\">旧值</field></observe><dialogue>正文。</dialogue>"
        val reply = parse(raw)
        assertNull(reply.observation)
        assertTrue(CompanionReplyDiagnostic.UNKNOWN_OBSERVATION_FIELD in reply.diagnostics)
        assertEquals("正文。", reply.displayText)
        assertEquals(raw, reply.raw)
    }

    @Test
    fun malformedUnclosedObservation_cannotSwallowFollowingNarrationAndDialogue() {
        val raw = "<observe version=\"1\"><field id=\"time\">旧值\r\n" +
                "<narration>新的旁白。</narration><dialogue>新台词。</dialogue>"
        val reply = parse(raw)
        assertNull(reply.observation)
        assertEquals(listOf("新的旁白。", "新台词。"), reply.beats.map { it.content })
        assertTrue(CompanionReplyDiagnostic.MALFORMED_OBSERVATION in reply.diagnostics)
        assertEquals(raw, reply.raw)
    }

    @Test
    fun malformedObservationWithClosingTag_keepsNestedValidBody() {
        val raw =
            "<observe version=\"1\"><field id=\"time\">坏块</field><dialogue>有效正文。</dialogue></observe>"
        val reply = parse(raw)
        assertNull(reply.observation)
        assertEquals("有效正文。", reply.displayText)
    }

    @Test
    fun absentDisabledAndIncompleteObservation_neverReusePreviousValue() {
        val raw =
            "<observe version=\"1\"><field id=\"place\">窗边</field></observe><dialogue>好。</dialogue>"
        assertNotNull(parse(raw).observation)
        assertNull(parse("<dialogue>下一轮没有观测。</dialogue>").observation)
        assertNull(
            parse(
                raw,
                snapshot = enabled.copy(observation = enabled.observation.copy(enabled = false))
            ).observation
        )
        assertNull(
            parser.parse(
                raw,
                source(raw),
                enabled,
                "艾丽",
                allowObservation = false
            ).observation
        )
    }

    @Test
    fun duplicateBlocksFieldsAndOversizeValues_clearObservationOnly() {
        val blocks = listOf(
            "<observe version=\"1\"><field id=\"time\">甲</field><field id=\"time\">乙</field></observe>",
            "<observe version=\"1\"><field id=\"time\">甲</field></observe><observe version=\"1\"><field id=\"time\">乙</field></observe>",
            "<observe version=\"1\"><field id=\"time\">${"长".repeat(401)}</field></observe>",
            "<observe version=\"2\"><field id=\"time\">甲</field></observe>",
        )
        blocks.forEach { block ->
            val reply = parse("$block<dialogue>留住正文。</dialogue>")
            assertNull(reply.observation)
            assertEquals("留住正文。", reply.displayText)
        }
    }

    @Test
    fun oversizedObservationAttributes_areExcludedWithoutHidingFollowingBody() {
        val block = "<observe version=\"1\" pad=\"${"a".repeat(5_000)}\">" +
                "<field id=\"time\">OBS_BOUNDARY_SENTINEL</field></observe>"
        val onlyObservation = parse(block)
        assertTrue(onlyObservation.beats.isEmpty())
        assertFalse(parser.hasReadableBody(block))
        assertNull(onlyObservation.observation)
        assertTrue(CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED in onlyObservation.diagnostics)
        assertEquals(block, onlyObservation.raw)

        val body = "<dialogue>new🙂</dialogue>"
        val raw = "$block\r\n$body"
        val reply = parse(raw)
        assertEquals("new🙂", reply.displayText)
        assertNull(reply.observation)
        assertTrue(CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED in reply.diagnostics)
        assertEquals(raw, reply.raw)
        assertEquals(body, slice(reply, 0))
        assertEquals(CompanionRawSpan(block.length + 2, raw.length), reply.beats.single().rawSpan)
    }

    @Test
    fun unknownProtocolAndSnapshot_doNotInventHistoricalObservation() {
        val raw =
            "<observe version=\"1\"><field id=\"time\">傍晚</field></observe><dialogue>可读。</dialogue>"
        assertNull(
            parser.parse(
                raw,
                source(raw).copy(protocolVersion = 8),
                enabled,
                "艾丽"
            ).observation
        )
        assertNull(parse(raw, snapshot = enabled.copy(snapshotVersion = 7)).observation)
        assertEquals("可读。", parse(raw, snapshot = enabled.copy(snapshotVersion = 7)).displayText)
    }

    @Test
    fun entitiesDoNotExpandExternalResourcesAndOnlyObservationHasNoBeats() {
        val raw =
            "<observe version=\"1\"><field id=\"detail\">&lt;x&gt; &#x1F642; &amp;#65; &unknown;</field></observe>"
        val reply = parse(raw)
        assertEquals("<x> 🙂 &#65; &unknown;", reply.observation!!.fields.single().value)
        assertTrue(reply.beats.isEmpty())
        assertEquals(raw, reply.raw)
    }

    @Test
    fun bodyEntitiesDecodeOnceAfterMarkupExtraction() {
        val raw = "<dialogue>Tom&apos;s &#x1F642; &#128578; &amp;#65; &lt;tag&gt;</dialogue>"
        val reply = parse(raw)
        assertEquals("Tom's 🙂 🙂 &#65; <tag>", reply.displayText)
        assertEquals(raw, reply.raw)
        assertEquals(raw, slice(reply, 0))
    }

    @Test
    fun explicitVariableBlocksAndJsonPatchOnly_areNotStory() {
        assertFalse(parser.hasReadableBody("<UpdateVariable><JSONPatch>[{\"op\":\"replace\",\"path\":\"/x\",\"value\":1}]</JSONPatch></UpdateVariable>"))
        assertFalse(parser.hasReadableBody("[{\"op\":\"replace\",\"path\":\"/x\",\"value\":1}]"))
    }

    private fun parse(
        raw: String,
        snapshot: CompanionPresentationSnapshot? = enabled,
        revision: Long = 1
    ) =
        parser.parse(raw, source(raw).copy(revision = revision), snapshot, "艾丽")

    private fun source(raw: String) = CompanionReplySource(
        "message",
        "conversation",
        "turn",
        1,
        1,
        CompanionReplyParser.PARSER_VERSION,
        companionRawHash(raw),
    )

    private fun slice(reply: ParsedCompanionReply, index: Int) = reply.beats[index].rawSpan.let {
        reply.raw.substring(it.start, it.endExclusive)
    }
}
