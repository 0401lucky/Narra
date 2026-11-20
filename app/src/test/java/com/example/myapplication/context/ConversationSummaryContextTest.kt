package com.example.myapplication.context

import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.PromptMode
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.fakeSummaryRuntimeArchive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryContextTest {
    private val runtime = MemoryRuntimeContext("a", "space-a", "conv", "scene", "run", "persona", 1)
    private val messages = (1..4).map {
        ChatMessage("m$it", "conv", MessageRole.USER, "原始材料$it", createdAt = it.toLong())
    }

    @Test
    fun assemble_validatesFullStoredSourcesWhenRecentInputHasAlreadyBeenTrimmed() = runTest {
        val summary = summary()
        val repository =
            FakeConversationSummaryRepository(listOf(summary), sourceMessages = messages)
        val result = assemble(repository, messages.takeLast(1))

        assertTrue(result.systemPrompt.contains(summary.summary))
        assertEquals(3, result.summaryCoveredMessageCount)
        assertTrue(result.contextSections.any { it.content.contains(summary.summary) })
    }

    @Test
    fun assemble_changedSourceExcludesOldSummaryAndSegmentFromPromptAndLog() = runTest {
        val summary = summary()
        val segment = ConversationSummarySegment(
            "segment",
            "conv",
            "a",
            "m1",
            "m3",
            1,
            3,
            3,
            "已失效分段正文",
            sourceSnapshot = summary.sourceSnapshot,
            completionStatus = PromptCompletionStatus.COMPLETED,
            memoryContext = runtime,
        )
        val current = listOf(
            messages.first().copy(content = "用户编辑后的材料", memoryRevision = 2)
        ) + messages.drop(1)
        val repository =
            FakeConversationSummaryRepository(listOf(summary), listOf(segment), current)

        val result = assemble(repository, current)

        assertFalse(result.systemPrompt.contains(summary.summary))
        assertFalse(result.systemPrompt.contains(segment.summary))
        assertFalse(result.contextSections.any {
            it.content.contains(summary.summary) || it.content.contains(
                segment.summary
            )
        })
        assertEquals(0, result.summaryCoveredMessageCount)
        assertEquals(0, result.summarySegmentCount)
        assertEquals(summary, repository.getSummaryForContext(runtime))
        assertEquals(listOf(segment), repository.listSummarySegments("conv"))
    }

    @Test
    fun assemble_legacySummaryKeepsTextWithoutClaimingReliableCoverage() = runTest {
        val legacy = ConversationSummary("conv", summary = "历史摘要原文", coveredMessageCount = 99)
        val repository =
            FakeConversationSummaryRepository(listOf(legacy), sourceMessages = messages)

        val result = assemble(repository, messages, mode = PromptMode.CHAT)

        assertTrue(result.systemPrompt.contains("历史摘要原文"))
        assertTrue(result.systemPrompt.contains("完整覆盖尚未验证"))
        assertEquals(0, result.summaryCoveredMessageCount)
        assertEquals(legacy, repository.getSummary("conv"))
        assertEquals(
            messages,
            ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(legacy, messages, 1)
        )
    }

    @Test
    fun assemble_laterSegmentsDoNotSupplyMissingAggregatePrefix() = runTest {
        val later = ConversationSummarySegment(
            "later", "conv", "a", "m3", "m4", 3, 4, 2, "只有后段的摘要",
            sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages.takeLast(2)),
            completionStatus = PromptCompletionStatus.COMPLETED,
            memoryContext = runtime,
        )
        val repository = FakeConversationSummaryRepository(
            initialSegments = listOf(later),
            sourceMessages = messages
        )

        val result = assemble(repository, messages)

        assertTrue(result.systemPrompt.contains(later.summary))
        assertEquals(0, result.summaryCoveredMessageCount)
        assertEquals(1, result.summarySegmentCount)
    }

    @Test
    fun assemble_roleplayWithoutRuntimeCannotInjectUnprovenOldSummary() = runTest {
        val old = ConversationSummary(
            "conv",
            summary = "LEGACY_PRIVATE_SENTINEL",
            coveredMessageCount = 99
        )
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)

        val result = assemble(repository, messages, runtimes = emptyList())

        assertFalse(result.systemPrompt.contains("LEGACY_PRIVATE_SENTINEL"))
        assertFalse(result.contextSections.any { it.content.contains("LEGACY_PRIVATE_SENTINEL") })
        assertEquals(old, repository.getSummary("conv"))
    }

    @Test
    fun assemble_eachCharacterUsesOwnCacheWhenTheLatestProjectionBelongsToAnother() = runTest {
        val runtimeB = runtime.copy(characterId = "b", spaceId = "space-b")
        val summaryA = summary().copy(summary = "A_PRIVATE_SENTINEL")
        val summaryB =
            summary().copy(assistantId = "b", memoryContext = runtimeB, summary = "B的公开摘要")
        val repository =
            FakeConversationSummaryRepository(listOf(summaryA, summaryB), sourceMessages = messages)
        repository.upsertSummary(summaryA)

        val result = assemble(
            repository,
            messages.takeLast(1),
            runtimes = listOf(runtime, runtimeB),
            characterId = "b"
        )

        assertTrue(result.systemPrompt.contains("B的公开摘要"))
        assertFalse(result.systemPrompt.contains("A_PRIVATE_SENTINEL"))
        assertFalse(result.contextSections.any { it.content.contains("A_PRIVATE_SENTINEL") })
        assertEquals(summaryA, repository.getSummaryForContext(runtime))
        assertEquals(summaryB, repository.getSummaryForContext(runtimeB))
    }

    @Test
    fun assemble_personaRunOrGenerationChangeRejectsOldSummaryWithoutChangingMessages() = runTest {
        val old = summary().copy(summary = "OLD_PERSONA_PRIVATE_SENTINEL")
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)
        val changes = listOf(
            runtime.copy(personaKey = "persona-new"),
            runtime.copy(runId = "run-new"),
            runtime.copy(generation = 2),
        )

        changes.forEach { next ->
            val result = assemble(repository, messages, runtimes = listOf(next))
            assertFalse(result.systemPrompt.contains("OLD_PERSONA_PRIVATE_SENTINEL"))
            assertEquals(0, result.summaryCoveredMessageCount)
        }
        assertEquals(old, repository.getSummaryForContext(runtime))
        assertEquals(messages, repository.sourceMessages)
    }

    private fun summary() = ConversationSummary(
        "conv", "a", "完整旧剧情：约定706号房", 3,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages.take(3)),
        completionStatus = PromptCompletionStatus.COMPLETED,
        memoryContext = runtime,
    )

    private suspend fun assemble(
        repository: FakeConversationSummaryRepository,
        recent: List<ChatMessage>,
        runtimes: List<MemoryRuntimeContext> = listOf(runtime),
        characterId: String = "a",
        mode: PromptMode = PromptMode.ROLEPLAY,
    ) = DefaultPromptContextAssembler(
        conversationSummaryRepository = repository,
        memoryArchiveRepository = fakeSummaryRuntimeArchive { runtimes },
    ).assemble(
        settings = AppSettings(),
        assistant = Assistant(id = characterId, name = "合成角色"),
        conversation = Conversation(
            "conv",
            createdAt = 1,
            updatedAt = 4,
            assistantId = characterId
        ),
        userInputText = "继续",
        recentMessages = recent,
        promptMode = mode,
    )
}
