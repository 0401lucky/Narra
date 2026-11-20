package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*
import org.junit.Assert.*
import org.junit.Test

class CompanionReadingSupportTest {
    @Test
    fun speechUnits_preserveSourceKindRawSpanAndIndependentEmotion() {
        val raw =
            "<narration>窗边\r\n安静。</narration><dialogue sprite=\"happy\" voice_emotion=\"calm\">好呀🙂e\u0301。</dialogue>"
        val reply = reply("reply-one", "first-conversation", raw)
        val units = CompanionSpeechSupport.units(reply)
        assertEquals(2, units.size)
        assertEquals(RoleplayContentType.NARRATION, units[0].contentType)
        assertEquals(RoleplayContentType.DIALOGUE, units[1].contentType)
        assertEquals(CompanionVoiceEmotion.CALM, units[1].voiceEmotion)
        assertEquals("happy", reply.beats[1].spriteKey)
        units.forEach { unit ->
            assertEquals(reply.source, unit.source)
            assertEquals(raw.substring(unit.rawSpan.start, unit.rawSpan.endExclusive), unit.rawText)
            assertEquals(reply.beats.first { it.id == unit.beatId }.content, unit.text)
        }
        assertEquals(raw, reply.raw)
    }

    @Test
    fun manualSelection_spansTwoConversationsInChronologicalOrder() {
        val first = reply(
            "reply-one",
            "first-conversation",
            "<narration>第一场旁白。</narration><dialogue>第一场台词。</dialogue>"
        )
        val second = reply(
            "reply-two",
            "second-conversation",
            "<narration>第二场旁白。</narration><dialogue>第二场台词。</dialogue>"
        )
        val units = CompanionSpeechSupport.units(first) + CompanionSpeechSupport.units(second)
        val selected =
            requireNotNull(CompanionSpeechSupport.select(units, units[1].id, units[3].id))
        assertEquals(
            listOf("first-conversation", "second-conversation", "second-conversation"),
            selected.units.map { it.source.conversationId })
        assertEquals("第一场台词。\n\n第二场旁白。\n\n第二场台词。", selected.text)
        assertEquals(3, selected.ranges.size)
        assertTrue(CompanionSpeechSupport.isCurrent(selected, units))
    }

    @Test
    fun revisedOrDeletedSource_invalidatesSelectionWithoutChangingRaw() {
        val raw = "<dialogue>同一句话。</dialogue>"
        val first = reply("reply", "conversation", raw)
        val units = CompanionSpeechSupport.units(first)
        val selection = requireNotNull(CompanionSpeechSupport.select(units, units.single().id))
        val edited = CompanionSpeechSupport.units(reply("reply", "conversation", raw, revision = 2))
        assertFalse(CompanionSpeechSupport.isCurrent(selection, edited))
        assertFalse(CompanionSpeechSupport.isCurrent(selection, emptyList()))
        assertEquals(first.raw, raw)
        assertEquals(1L, selection.units.single().source.revision)
    }

    @Test
    fun partialSelection_rejectsHalfEmojiAndUnknownUnit() {
        val units = CompanionSpeechSupport.units(
            reply(
                "reply",
                "conversation",
                "<dialogue>🙂e\u0301。</dialogue>"
            )
        )
        assertNull(CompanionSpeechSupport.select(units, units.single().id, start = 1))
        assertNull(CompanionSpeechSupport.select(units, "not-a-unit"))
        assertEquals(
            "🙂",
            CompanionSpeechSupport.select(units, units.single().id, endExclusive = 2)?.text
        )
    }

    @Test
    fun anchorUsesStableSegment_thenSurvivingSourceOrNeighbour() {
        val reply =
            reply("reply", "conversation", "<narration>旁白。</narration><dialogue>台词。</dialogue>")
        val binding = CompanionReadingBinding("conversation", "scene", "run", "场景", "alice")
        val record = CompanionReadingRecord(message("reply", "conversation", reply.raw, 1), binding)
        val items = reply.beats.map { beat ->
            CompanionReadingItem(
                record, RoleplayMessageUiModel(
                    beat.id,
                    "reply",
                    beat.contentType,
                    beat.speaker,
                    beat.speakerName,
                    content = beat.content,
                    companionReply = reply,
                    companionBeat = beat
                )
            )
        }
        val anchor = CompanionReadingAnchor(
            "reply",
            items[1].id,
            record.cursor,
            43,
            false,
            reply.beats[1].rawSpan.start
        )
        assertEquals(1, CompanionReadingSupport.anchorIndex(anchor, items))
        assertEquals(0, CompanionReadingSupport.anchorIndex(anchor, listOf(items[1])))
        val neighbour = CompanionReadingRecord(
            ChatMessage(
                "next",
                "conversation",
                MessageRole.USER,
                "下一条",
                createdAt = 2_000
            ), binding
        )
        val next = CompanionReadingItem(
            neighbour,
            RoleplayMessageUiModel(
                "next-item",
                "next",
                RoleplayContentType.DIALOGUE,
                RoleplaySpeaker.USER,
                "玩家",
                content = "下一条"
            )
        )
        assertEquals(0, CompanionReadingSupport.anchorIndex(anchor, listOf(next)))
        assertEquals(
            1,
            CompanionReadingSupport.anchorIndex(anchor.copy(followingBottom = true), items)
        )
    }

    private fun reply(id: String, conversation: String, raw: String, revision: Long = 1) =
        requireNotNull(CompanionReplyProjector.project(message(id, conversation, raw, revision)))

    private fun message(id: String, conversation: String, raw: String, revision: Long) =
        ChatMessage(
            id,
            conversation,
            MessageRole.ASSISTANT,
            raw,
            createdAt = 1_000,
            speakerId = "alice",
            speakerName = "合成角色",
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            companionTurnId = "turn-$id",
            companionMemoryState = CompanionMemoryState.READY,
            memoryRevision = revision,
            companionReplyMetadata = CompanionReplyMetadata(
                id,
                conversation,
                "turn-$id",
                revision,
                companionRawHash(raw),
                "alice",
                RoleplayOutputFormat.PROTOCOL,
                RoleplayInteractionMode.OFFLINE_DIALOGUE,
                "attempt-$id",
                1,
                CompanionPresentationSnapshot()
            )
        )
}
