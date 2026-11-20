package com.example.myapplication.conversation

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptCompletionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryCoverageSupportTest {
    private val messages = (1..5).map {
        ChatMessage("m$it", "conv", MessageRole.USER, "原文$it", createdAt = it.toLong())
    }

    @Test
    fun verifiedCoverage_usesCurrentSourcesInsteadOfStoredCount() {
        val summary = summary(messages.take(2)).copy(coveredMessageCount = 900)
        assertEquals(
            2,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, messages)
        )
        assertEquals(
            messages,
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(summary, messages, 1)
        )
        assertEquals(
            messages.takeLast(3),
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(summary, messages, 3)
        )
    }

    @Test
    fun legacySummaryCount_neverAuthorizesTrimming() {
        val summary = ConversationSummary("conv", summary = "未知旧覆盖", coveredMessageCount = 900)
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, messages)
        )
        assertEquals(
            messages,
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(summary, messages, 1)
        )
        assertEquals(
            "未知旧覆盖",
            ConversationSummaryCoverageSupport.usableSummary(summary, messages)!!.summary
        )
        assertEquals(900, summary.coveredMessageCount)
    }

    @Test
    fun sourceEdits_deletionsAndIdentityChangesInvalidateTheAggregate() {
        val summary = summary(messages.take(3))
        val first = messages.first()
        val changedSources = listOf(
            first.copy(content = "改后的正文"),
            first.copy(memoryRevision = 2),
            first.copy(createdAt = 100),
            first.copy(speakerId = "another-character"),
            first.copy(speakerName = "另一位说话者"),
            first.copy(role = MessageRole.ASSISTANT),
            first.copy(isRecalled = true),
            first.copy(status = MessageStatus.LOADING),
            first.copy(conversationId = "other-conversation"),
        )
        changedSources.forEach { changed ->
            val current = listOf(changed) + messages.drop(1)
            assertEquals(
                0,
                ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, current)
            )
            assertNull(ConversationSummaryCoverageSupport.usableSummary(summary, current))
        }
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, messages.drop(1))
        )
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(
                summary,
                messages.filterNot { it.id == "m2" })
        )
        assertEquals("完整摘要", summary.summary)
    }

    @Test
    fun overlappingSegments_useUnionAndStopBeforeMiddleHole() {
        val segments = listOf(
            segment("one", messages.take(2)),
            segment("two", messages.subList(1, 3)),
            segment("later", messages.takeLast(1)),
        )
        val coverage =
            ConversationSummaryCoverageSupport.continuousCoverage(messages, null, segments)
        assertEquals(3, coverage.coveredMessageCount)
        assertEquals(listOf("one", "two"), coverage.segments.map { it.id })
    }

    @Test
    fun partialMessageSpan_cannotClaimAWholeMessage() {
        val summary = summary(messages.take(1))
        val partial =
            summary.copy(sourceSnapshot = summary.sourceSnapshot.map { it.copy(endOffset = it.endOffset - 1) })
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(partial, messages)
        )
        assertNull(ConversationSummaryCoverageSupport.usableSummary(partial, messages))
    }

    @Test
    fun trim_keepsUncoveredErrorMessagesEvenWhenCompletedCountWouldBeEnough() {
        val summary = summary(messages.take(2))
        val error =
            ChatMessage("error", "conv", MessageRole.ASSISTANT, "仍可重试", MessageStatus.ERROR)
        val request = listOf(messages[0], error, messages[1], messages[2])
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, request)
        )
        assertEquals(
            request,
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(summary, request, 1)
        )
    }

    @Test
    fun unfinishedMessagesRemainHolesInProcessedAndVerifiedCoverage() {
        for (status in listOf(MessageStatus.ERROR, MessageStatus.LOADING)) {
            val hole = ChatMessage("hole", "conv", MessageRole.ASSISTANT, "尚未完成", status)
            val withMiddleHole = listOf(messages[0], hole) + messages.drop(1)
            val segments =
                listOf(segment("first", messages.take(1)), segment("later", messages.drop(1)))
            assertEquals(
                1,
                ConversationSummaryCoverageSupport.continuousCoverage(
                    withMiddleHole,
                    null,
                    segments
                ).coveredMessageCount
            )
            assertEquals(
                0,
                ConversationSummaryCoverageSupport.processedSummaryCoverage(
                    summary(messages),
                    withMiddleHole
                )
            )
            assertEquals(
                0,
                ConversationSummaryCoverageSupport.continuousCoverage(
                    listOf(hole) + messages,
                    null,
                    segments
                ).coveredMessageCount
            )
            assertEquals(
                2..5,
                ConversationSummaryCoverageSupport.segmentRange(segments.last(), withMiddleHole)
            )
        }
    }

    @Test
    fun unknownTransportCompletion_keepsSourcesReusableAndLeavesRawMessagesVisible() {
        val summary =
            summary(messages.take(4)).copy(completionStatus = PromptCompletionStatus.UNKNOWN)
        assertEquals(
            4,
            ConversationSummaryCoverageSupport.processedSummaryCoverage(summary, messages)
        )
        assertEquals(
            0,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, messages)
        )
        assertEquals(
            messages,
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(summary, messages, 1)
        )
    }

    @Test
    fun completeInput_keepsLegacyRawSupplementAndRejectsUnavailableSources() {
        val message = messages.first().copy(content = "完整旧原文🙂\r\n第二行")
        val before = ConversationSummaryCoverageSupport.sourceSnapshot(message)
        val input = ConversationSummaryCoverageSupport.completeInput(
            listOf(message), "只保留了展示片段"
        )
        assertTrue(input.contains(message.content))
        assertEquals(before, ConversationSummaryCoverageSupport.sourceSnapshot(message))
        assertEquals(message.content.length, before.endOffset)
        assertEquals("", ConversationSummaryCoverageSupport.completeInput(listOf(message), "片段") { false })
        assertEquals(
            "",
            ConversationSummaryCoverageSupport.completeInput(
                listOf(message.copy(status = MessageStatus.ERROR)), "片段"
            )
        )
    }

    private fun summary(sources: List<ChatMessage>) = ConversationSummary(
        "conv", summary = "完整摘要", coveredMessageCount = sources.size,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(sources),
        completionStatus = PromptCompletionStatus.COMPLETED,
    )

    private fun segment(id: String, sources: List<ChatMessage>) = ConversationSummarySegment(
        id, "conv", startMessageId = sources.first().id, endMessageId = sources.last().id,
        startCreatedAt = sources.first().createdAt, endCreatedAt = sources.last().createdAt,
        messageCount = sources.size, summary = "完整分段",
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(sources),
        completionStatus = PromptCompletionStatus.COMPLETED,
    )
}
