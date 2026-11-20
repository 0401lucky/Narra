package com.example.myapplication.conversation

import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import org.junit.Assert.*
import org.junit.Test

class MemoryExtractionBatcherTest {
    @Test
    fun longMessageTailAndSurrogatePairsHaveFullCoverage() {
        val body = "长段🙂".repeat(1200) + "尾部：11月19日阿澄在706号房"
        val source = source(body)
        val spans = MemoryExtractionBatcher.sourceSpans(
            source,
            listOf(MemorySemanticText(body, MemorySemanticKind.SPEECH)),
            "a",
            maxSpanChars = 127,
            overlapChars = 17
        )
        assertTrue(spans.last().text.contains("阿澄在706号房"))
        assertTrue(MemoryExtractionBatcher.covered(source.fullRef(), spans.map { it.ref }))
        spans.forEach {
            assertFalse(it.text.first().isLowSurrogate())
            assertFalse(it.text.last().isHighSurrogate())
            assertEquals(source.id, it.ref.sourceId)
            assertEquals(9L, it.ref.sourceRevision)
            assertEquals(source.personaKey, it.personaKey)
            assertEquals(source.occurredAt, it.occurredAt)
        }
    }

    @Test
    fun publicSpeechAndPrivateThoughtAreSeparatedForGroupCharacters() {
        val message = ChatMessage(
            "m",
            "c",
            MessageRole.ASSISTANT,
            "",
            speakerId = "a",
            parts = listOf(textMessagePart("一起去706"), thoughtMessagePart("秘密只属于我"))
        )
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(message)
        val evidence = source(segments.joinToString("\n\n") { it.text }).copy(
            speakerId = "a",
            speakerRole = "ASSISTANT",
            visibleCharacterIds = listOf("a", "b")
        )
        val other = MemoryExtractionBatcher.sourceSpans(evidence, segments, "b")
        assertTrue(other.any { it.text.contains("706") })
        assertFalse(other.any { it.text.contains("秘密") })
        assertEquals("PRIVATE_TO_SPEAKER", other.last().skipReason)
        assertTrue(MemoryExtractionBatcher.covered(evidence.fullRef(), other.map { it.ref }))
        assertTrue(
            MemoryExtractionBatcher.sourceSpans(evidence, segments, "a")
                .any { it.text.contains("秘密") })
    }

    @Test
    fun protocolThoughtInsideTextPartKeepsPrivacyType() {
        val message = ChatMessage(
            "m",
            "c",
            MessageRole.ASSISTANT,
            "",
            parts = listOf(textMessagePart("<thought>我的私密想法</thought><dialogue>公开发言</dialogue>"))
        )
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(message)
        assertTrue(segments.first { it.text.contains("私密") }.privateToSpeaker)
        assertFalse(segments.first { it.text.contains("公开") }.privateToSpeaker)
    }

    @Test
    fun mediaWithoutTranscriptIsSkippedAndNeverSendsUrls() {
        val image = ChatMessage(
            "image",
            "c",
            MessageRole.USER,
            "图片已生成",
            parts = listOf(imageMessagePart("content://secret/image"))
        )
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(image)
        assertEquals("MEDIA_WITHOUT_SEMANTICS", segments.single().skipReason)
        val voice = image.copy(
            id = "voice",
            parts = listOf(voiceMessageActionPart("尾部约定是9月12日706号房"))
        )
        assertEquals(
            MemorySemanticKind.TRANSCRIPT,
            RoleplayTranscriptFormatter.memorySemanticSegments(voice).single().kind
        )
        assertTrue(
            RoleplayTranscriptFormatter.memorySemanticSegments(voice).single().text.contains(
                "706"
            )
        )
        val withUrl = image.copy(
            parts = emptyList(),
            content = "图片编码 data:image/png;base64,AABBCC== 约在七点"
        )
        val safe = RoleplayTranscriptFormatter.memorySemanticSegments(withUrl)
        assertFalse(safe.any { it.text.contains("AABBCC==") })
        assertTrue(safe.any { it.skipReason == "MEDIA_LOCATION" })
    }

    @Test
    fun meaningfulWebAddressesArePreserved() {
        val text =
            "官网从 https://old.example 改为 https://new.example；代码 fetch(\"https://api.example/v2\")"
        val message = ChatMessage("m", "c", MessageRole.USER, text)
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(message)
        assertEquals(text, segments.joinToString("\n\n") { it.text })
        val spans = MemoryExtractionBatcher.sourceSpans(source(text), segments, "a")
        assertEquals(text, spans.single().text)
    }

    @Test
    fun legacyInlineMediaIsExcludedBeforeSplittingItsLongPayload() {
        val body =
            "官网是 https://example.test。\n" + "data:image/png;base64," + "A".repeat(4_096) + "==\n尾部约在706号房🙂"
        val source = source(body).copy(contentFormatVersion = 1)
        val spans = MemoryExtractionBatcher.sourceSpans(
            source, listOf(MemorySemanticText(body, MemorySemanticKind.UNKNOWN)), "a",
            maxSpanChars = 128, overlapChars = 17,
        )
        assertTrue(spans.any { it.text.contains("https://example.test") })
        assertTrue(spans.any { it.text.contains("尾部约在706号房🙂") })
        assertTrue(spans.count { it.skipReason == "MEDIA_LOCATION" } > 10)
        assertTrue(spans.filter { it.skipReason == "MEDIA_LOCATION" }.all { it.text.isEmpty() })
        assertFalse(spans.any { it.text.contains("A".repeat(30)) || it.text.contains("base64") })
        assertTrue(MemoryExtractionBatcher.covered(source.fullRef(), spans.map { it.ref }))
    }

    @Test
    fun failedOrLoadingRepliesHaveNoSemanticMaterial() {
        val message = ChatMessage("m", "c", MessageRole.ASSISTANT, "尚未确认的内容")
        assertTrue(
            RoleplayTranscriptFormatter.memorySemanticSegments(message.copy(status = MessageStatus.LOADING))
                .isEmpty()
        )
        assertTrue(
            RoleplayTranscriptFormatter.memorySemanticSegments(message.copy(status = MessageStatus.ERROR))
                .isEmpty()
        )
        assertTrue(
            RoleplayTranscriptFormatter.memorySemanticSegments(message.copy(isRecalled = true))
                .isEmpty()
        )
    }

    @Test
    fun systemCardsAndNarrationHaveDifferentSourceTypes() {
        val card = ChatMessage(
            "card",
            "c",
            MessageRole.ASSISTANT,
            "视频通话已接通",
            systemEventKind = RoleplayOnlineEventKind.VIDEO_CALL_CONNECTED
        )
        assertEquals(
            MemorySemanticKind.SYSTEM_EVENT,
            RoleplayTranscriptFormatter.memorySemanticSegments(card).single().kind
        )
        assertEquals(
            MemorySemanticKind.NARRATION,
            RoleplayTranscriptFormatter.memorySemanticSegments(
                card.copy(
                    id = "rp-opening-c",
                    systemEventKind = RoleplayOnlineEventKind.NONE
                )
            ).single().kind
        )
    }

    @Test
    fun committedCountCannotSkipUncoveredGap() {
        val full = source("0123456789").fullRef()
        assertFalse(
            MemoryExtractionBatcher.covered(
                full,
                listOf(full.copy(end = 4), full.copy(start = 5))
            )
        )
        assertTrue(
            MemoryExtractionBatcher.covered(
                full,
                listOf(full.copy(end = 6), full.copy(start = 5))
            )
        )
    }

    private fun source(content: String) = MemorySourceEvidence(
        "s",
        "space",
        "a",
        MemorySourceKind.MESSAGE,
        "c",
        "m",
        9,
        "user",
        "USER",
        "persona-at-send",
        listOf("a"),
        content,
        content.length,
        MemorySourceStatus.AVAILABLE,
        123,
        999,
        "fingerprint",
        2
    )
}
