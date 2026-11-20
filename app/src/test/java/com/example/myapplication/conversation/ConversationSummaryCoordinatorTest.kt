package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ConversationSummaryCoordinatorTest {
    @Test
    fun updateConversationSummary_persistsGeneratedSummary() = runBlocking {
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "你好",
                createdAt = 1L
            ),
            ChatMessage(
                id = "m2",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "你好呀",
                createdAt = 2L
            ),
            ChatMessage(
                id = "m3",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "继续总结",
                createdAt = 3L
            ),
            ChatMessage(
                id = "m4",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "好的",
                createdAt = 4L
            ),
        )
        val repository = FakeConversationSummaryRepository(sourceMessages = messages)
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 123L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            buildSummaryInput = { messages ->
                messages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, modelId, _ ->
                assertTrue(conversationText.contains("你好"))
                assertEquals("summary-model", modelId)
                completed("这是摘要")
            },
        )

        assertTrue(result.updated)
        assertEquals("这是摘要", result.summaryText)
        assertEquals(3, result.coveredMessageCount)
        assertEquals(
            ConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                summary = "这是摘要",
                coveredMessageCount = 3,
                updatedAt = 123L,
                sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages.take(3)),
                completionStatus = PromptCompletionStatus.COMPLETED,
            ),
            repository.getSummary("conv-1"),
        )
    }

    @Test
    fun updateConversationSummary_skipsWhenExistingCoverageIsEnough() = runBlocking {
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "a",
                createdAt = 1L
            ),
            ChatMessage(
                id = "m2",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "b",
                createdAt = 2L
            ),
            ChatMessage(
                id = "m3",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "c",
                createdAt = 3L
            ),
            ChatMessage(
                id = "m4",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "d",
                createdAt = 4L
            ),
        )
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "已有摘要",
                    coveredMessageCount = 3,
                    sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                        messages.take(
                            3
                        )
                    ),
                    completionStatus = PromptCompletionStatus.COMPLETED,
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            buildSummaryInput = { error("不应再生成摘要") },
            generateSummary = { _, _, _, _, _ -> error("不应调用生成") },
        )

        assertFalse(result.updated)
        assertEquals("已有摘要", repository.getSummary("conv-1")?.summary)
    }

    @Test
    fun updateConversationSummary_followsDefaultChatModelWhenTitleSummaryModeUsesDefault() =
        runBlocking {
            val repository = FakeConversationSummaryRepository()
            val coordinator = ConversationSummaryCoordinator(
                aiPromptExtrasService = unusedPromptExtrasService(),
                conversationSummaryRepository = repository,
                nowProvider = { 123L },
            )
            val provider = ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "chat-model",
            )

            val result = coordinator.updateConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                completedMessages = listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "conv-1",
                        role = MessageRole.USER,
                        content = "a",
                        createdAt = 1L
                    ),
                    ChatMessage(
                        id = "m2",
                        conversationId = "conv-1",
                        role = MessageRole.ASSISTANT,
                        content = "b",
                        createdAt = 2L
                    ),
                    ChatMessage(
                        id = "m3",
                        conversationId = "conv-1",
                        role = MessageRole.USER,
                        content = "c",
                        createdAt = 3L
                    ),
                    ChatMessage(
                        id = "m4",
                        conversationId = "conv-1",
                        role = MessageRole.ASSISTANT,
                        content = "d",
                        createdAt = 4L
                    ),
                ),
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
                config = SummaryGenerationConfig(
                    triggerMessageCount = 2,
                    recentMessageWindow = 1,
                    minCoveredMessageCount = 2,
                ),
                buildSummaryInput = { messages ->
                    messages.joinToString(separator = "\n") { it.content }
                },
                generateSummary = { _, _, _, modelId, _ ->
                    assertEquals("chat-model", modelId)
                    completed("这是摘要")
                },
            )

            assertTrue(result.updated)
        }

    @Test
    fun updateConversationSummary_forceRefreshRegeneratesSameCoverageSummary() = runBlocking {
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "旧摘要",
                    coveredMessageCount = 3,
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 456L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "a",
                    createdAt = 1L
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "b",
                    createdAt = 2L
                ),
                ChatMessage(
                    id = "m3",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "c",
                    createdAt = 3L
                ),
                ChatMessage(
                    id = "m4",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "d",
                    createdAt = 4L
                ),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            forceRefresh = true,
            buildSummaryInput = { messages ->
                messages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { _, _, _, _, _ -> completed("新摘要") },
        )

        assertTrue(result.updated)
        assertEquals("新摘要", repository.getSummary("conv-1")?.summary)
        assertEquals(456L, repository.getSummary("conv-1")?.updatedAt)
    }

    @Test
    fun updateConversationSummary_forceRefreshSummarizesShortHistory() = runBlocking {
        val repository = FakeConversationSummaryRepository()
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 567L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "你还记得旧车站吗？",
                createdAt = 1L
            ),
            ChatMessage(
                id = "m2",
                conversationId = "conv-1",
                role = MessageRole.ASSISTANT,
                content = "我记得你没有走。",
                createdAt = 2L
            ),
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 12,
                recentMessageWindow = 8,
                minCoveredMessageCount = 4,
            ),
            forceRefresh = true,
            buildSummaryInput = { summaryMessages ->
                summaryMessages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, modelId, _ ->
                assertEquals("summary-model", modelId)
                assertTrue(conversationText.contains("旧车站"))
                assertTrue(conversationText.contains("没有走"))
                completed("短剧情摘要")
            },
        )

        assertTrue(result.updated)
        assertEquals(2, result.coveredMessageCount)
        assertEquals("短剧情摘要", repository.getSummary("conv-1")?.summary)
        assertEquals(2, repository.getSummary("conv-1")?.coveredMessageCount)
    }

    @Test
    fun updateConversationSummary_splitsLongHistoryIntoSegmentsAndRefreshesTotalSummary() =
        runBlocking {
            val repository = FakeConversationSummaryRepository()
            val coordinator = ConversationSummaryCoordinator(
                aiPromptExtrasService = unusedPromptExtrasService(),
                conversationSummaryRepository = repository,
                nowProvider = { 789L },
            )
            val provider = ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "chat-model",
                titleSummaryModel = "summary-model",
            )
            val generatedInputs = mutableListOf<String>()
            val messages = (1..8).map { index ->
                ChatMessage(
                    id = "m$index",
                    conversationId = "conv-1",
                    role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                    content = "第 $index 条剧情内容，包含较长描述用于触发分段。" + "细节".repeat(180),
                    createdAt = index.toLong(),
                )
            }

            val result = coordinator.updateConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                completedMessages = messages,
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
                config = SummaryGenerationConfig(
                    triggerMessageCount = 2,
                    recentMessageWindow = 1,
                    minCoveredMessageCount = 2,
                    segmentTargetCharacterCount = 70,
                    maxSegmentsPerRun = 10,
                ),
                buildSummaryInput = { summaryMessages ->
                    summaryMessages.joinToString(separator = "\n") { it.content }
                },
                generateSummary = { conversationText, _, _, _, _ ->
                    generatedInputs += conversationText
                    completed("摘要 ${generatedInputs.size}")
                },
            )

            val segments = repository.listSummarySegments("conv-1")
            assertTrue(result.updated)
            assertTrue(segments.size > 1)
            assertEquals(7, result.coveredMessageCount)
            assertEquals(7, segments.sumOf { it.messageCount })
            assertEquals("摘要 ${generatedInputs.size}", repository.getSummary("conv-1")?.summary)
            assertTrue(generatedInputs.last().contains("【合并要求】"))
            assertTrue(generatedInputs.last().contains("【分段摘要档案】"))
            assertTrue(generatedInputs.last().contains("当前状态"))
            assertTrue(generatedInputs.last().contains("未解问题"))
        }

    @Test
    fun updateConversationSummary_skipsMessagesAlreadyCoveredBySegments() = runBlocking {
        val messages = (1..5).map { index ->
            ChatMessage(
                id = "m$index", conversationId = "conv-1", role = MessageRole.USER,
                content = "剧情 $index", createdAt = index.toLong(),
            )
        }
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "已有总摘要",
                    coveredMessageCount = 2,
                    sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                        messages.take(
                            2
                        )
                    ),
                    completionStatus = PromptCompletionStatus.COMPLETED,
                ),
            ),
            initialSegments = listOf(
                ConversationSummarySegment(
                    id = "seg-1",
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    startMessageId = "m1",
                    endMessageId = "m2",
                    startCreatedAt = 1L,
                    endCreatedAt = 2L,
                    messageCount = 2,
                    summary = "前两条已经压缩",
                    sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                        messages.take(
                            2
                        )
                    ),
                    completionStatus = PromptCompletionStatus.COMPLETED,
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val generatedInputs = mutableListOf<String>()
        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
                segmentTargetCharacterCount = 1_000,
                maxSegmentsPerRun = 10,
            ),
            buildSummaryInput = { summaryMessages ->
                summaryMessages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, _, _ ->
                generatedInputs += conversationText
                completed("新摘要 ${generatedInputs.size}")
            },
        )

        assertTrue(result.updated)
        assertFalse(generatedInputs.first().contains("剧情 1"))
        assertFalse(generatedInputs.first().contains("剧情 2"))
        assertTrue(generatedInputs.first().contains("剧情 3"))
        assertTrue(generatedInputs.first().contains("剧情 4"))
        assertEquals(4, repository.getSummary("conv-1")?.coveredMessageCount)
    }

    @Test
    fun updateConversationSummary_forceRefreshKeepsExistingSegmentsWhenCoverageIsComplete() =
        runBlocking {
            val messages = (1..5).map { index ->
                ChatMessage(
                    id = "m$index", conversationId = "conv-1", role = MessageRole.USER,
                    content = "剧情 $index", createdAt = index.toLong(),
                )
            }
            val repository = FakeConversationSummaryRepository(
                initialSummaries = listOf(
                    ConversationSummary(
                        conversationId = "conv-1",
                        assistantId = "assistant-1",
                        summary = "旧总摘要",
                        coveredMessageCount = 4,
                        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                            messages.take(
                                4
                            )
                        ),
                        completionStatus = PromptCompletionStatus.COMPLETED,
                    ),
                ),
                initialSegments = listOf(
                    ConversationSummarySegment(
                        id = "seg-1",
                        conversationId = "conv-1",
                        assistantId = "assistant-1",
                        startMessageId = "m1",
                        endMessageId = "m4",
                        startCreatedAt = 1L,
                        endCreatedAt = 4L,
                        messageCount = 4,
                        summary = "已有分段摘要",
                        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                            messages.take(
                                4
                            )
                        ),
                        completionStatus = PromptCompletionStatus.COMPLETED,
                    ),
                ),
            )
            val coordinator = ConversationSummaryCoordinator(
                aiPromptExtrasService = unusedPromptExtrasService(),
                conversationSummaryRepository = repository,
                nowProvider = { 999L },
            )
            val provider = ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "chat-model",
                titleSummaryModel = "summary-model",
            )
            val result = coordinator.updateConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                completedMessages = messages,
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
                config = SummaryGenerationConfig(
                    triggerMessageCount = 2,
                    recentMessageWindow = 1,
                    minCoveredMessageCount = 2,
                ),
                forceRefresh = true,
                buildSummaryInput = { error("已全覆盖时不应重新读取原始消息") },
                generateSummary = { conversationText, _, _, _, _ ->
                    assertTrue(conversationText.contains("【合并要求】"))
                    assertTrue(conversationText.contains("【已有总摘要】"))
                    assertTrue(conversationText.contains("旧总摘要"))
                    assertTrue(conversationText.contains("当前状态"))
                    assertTrue(conversationText.contains("未解问题"))
                    assertTrue(conversationText.contains("已有分段摘要"))
                    completed("新总摘要")
                },
            )

            assertTrue(result.updated)
            assertEquals("新总摘要", repository.getSummary("conv-1")?.summary)
            assertEquals(4, repository.getSummary("conv-1")?.coveredMessageCount)
            assertEquals(1, repository.listSummarySegments("conv-1").size)
        }

    @Test
    fun updateConversationSummary_emptyFirstSegmentPreservesOldSummaryAndStopsAtHole() = runTest {
        val messages = sourceMessages(3)
        val old = ConversationSummary("conv-1", summary = "保留旧摘要", coveredMessageCount = 30)
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)
        var calls = 0

        val result = update(repository, messages) {
            calls++
            completed("")
        }

        assertFalse(result.updated)
        assertEquals(0, result.coveredMessageCount)
        assertEquals(1, calls)
        assertEquals(old, repository.getSummary("conv-1"))
        assertTrue(repository.listSummarySegments("conv-1").isEmpty())
        assertEquals(messages, repository.sourceMessages)
    }

    @Test
    fun updateConversationSummary_blankFirstInputCannotUseLaterSegmentCount() = runTest {
        val messages = sourceMessages(3)
        val later = segment("later", messages.drop(1))
        val repository = FakeConversationSummaryRepository(
            initialSegments = listOf(later),
            sourceMessages = messages
        )

        val result = update(repository, messages, input = { "" }) { error("空材料不应请求模型") }

        assertFalse(result.updated)
        assertEquals(0, result.coveredMessageCount)
        assertNull(repository.getSummary("conv-1"))
        assertEquals(listOf(later), repository.listSummarySegments("conv-1"))
    }

    @Test
    fun updateConversationSummary_middleHoleDoesNotAbsorbLaterStoredSegments() = runTest {
        val messages = sourceMessages(4)
        val later = segment("later", messages.drop(2), text = "后段不得变成前段")
        val repository = FakeConversationSummaryRepository(
            initialSegments = listOf(later),
            sourceMessages = messages
        )
        var calls = 0

        val result = update(repository, messages) {
            calls++
            completed(if (calls == 1) "首条有效摘要" else "")
        }

        assertEquals(2, calls)
        assertEquals(1, result.coveredMessageCount)
        assertEquals("首条有效摘要", result.summaryText)
        assertEquals(
            listOf("m1"),
            repository.getSummary("conv-1")!!.sourceSnapshot.map { it.messageId })
        assertEquals(
            messages, ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(
                repository.getSummary("conv-1"), messages, recentWindow = 2,
            )
        )
    }

    @Test
    fun updateConversationSummary_overlapsCannotSkipTheNextUncoveredMessage() = runTest {
        val messages = sourceMessages(4)
        val repository = FakeConversationSummaryRepository(
            initialSegments = listOf(
                segment("one", messages.take(2)),
                segment("two", messages.subList(1, 3))
            ),
            sourceMessages = messages,
        )
        val inputs = mutableListOf<String>()

        val result = update(repository, messages) { text ->
            inputs += text
            completed("摘要")
        }

        assertTrue(inputs.first().contains("消息编号:4"))
        assertFalse(inputs.first().contains("消息编号:3"))
        assertEquals(4, result.coveredMessageCount)
        assertEquals(
            listOf("m1", "m2", "m3", "m4"),
            repository.getSummary("conv-1")!!.sourceSnapshot.map { it.messageId })
    }

    @Test
    fun updateConversationSummary_secondBatchFailureRetainsFirstArtifactAcrossCoordinatorRestart() =
        runTest {
            val messages = sourceMessages(3)
            val repository = FakeConversationSummaryRepository(sourceMessages = messages)
            var calls = 0
            val failure = runCatching {
                update(repository, messages) {
                    if (++calls == 2) throw IOException("合成模型失败")
                    completed("第一段产物")
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertEquals(
                listOf("m1"),
                repository.listSummarySegments("conv-1").map { it.startMessageId })
            assertNull(repository.getSummary("conv-1"))
            val resumedInputs = mutableListOf<String>()
            val resumed = update(repository, messages) { text ->
                resumedInputs += text
                completed("恢复后的摘要")
            }
            assertFalse(resumedInputs.any { it.contains("消息编号:1") })
            assertTrue(resumedInputs.any { it.contains("消息编号:2") })
            assertTrue(resumedInputs.any { it.contains("消息编号:3") })
            assertEquals(3, resumed.coveredMessageCount)
        }

    @Test
    fun updateConversationSummary_cancellationPropagatesAndDoesNotPublishIncompletePrefix() =
        runTest {
            val messages = sourceMessages(3)
            val repository = FakeConversationSummaryRepository(sourceMessages = messages)
            val cancellation = CancellationException("合成取消")
            var calls = 0

            val failure = runCatching {
                update(repository, messages) {
                    if (++calls == 2) throw cancellation
                    completed("已保存的第一段")
                }
            }.exceptionOrNull()

            assertTrue(failure === cancellation)
            assertEquals(1, repository.listSummarySegments("conv-1").size)
            assertNull(repository.getSummary("conv-1"))
            assertEquals(messages, repository.sourceMessages)
        }

    @Test
    fun updateConversationSummary_longMessageIncludesTailEvenWhenLegacyFormatterClips() = runTest {
        val text = "完整长材料".repeat(4_000) + "尾部约定：11月19日 706 号房 🧭"
        val messages = sourceMessages(1).map { it.copy(content = text) }
        val repository = FakeConversationSummaryRepository(sourceMessages = messages)

        val result =
            update(repository, messages, input = { it.single().content.take(200) }) { input ->
                assertTrue(input.contains(text))
                assertTrue(input.contains("11月19日 706 号房 🧭"))
                completed("尾部约定已保留")
            }

        assertEquals(1, result.coveredMessageCount)
        val source = repository.getSummary("conv-1")!!.sourceSnapshot.single()
        assertEquals(0, source.startOffset)
        assertEquals(text.length, source.endOffset)
        assertEquals(text, repository.sourceMessages!!.single().content)
    }

    @Test
    fun updateConversationSummary_truncatedNonblankResponseDoesNotOverwriteOldSummary() = runTest {
        val messages = sourceMessages(2)
        val old = ConversationSummary("conv-1", summary = "旧摘要正文", coveredMessageCount = 99)
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)

        val failure = runCatching {
            update(repository, messages) {
                PromptCompletionResult(
                    "半段非空内容",
                    PromptCompletionStatus.TRUNCATED
                )
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(old, repository.getSummary("conv-1"))
        assertTrue(repository.listSummarySegments("conv-1").isEmpty())
    }

    @Test
    fun updateConversationSummary_emptyMergeKeepsOldSummaryAndReusesSavedSegment() = runTest {
        val messages = sourceMessages(2)
        val old = summary(messages.take(1), text = "旧的有效前缀")
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)
        var calls = 0

        val first = update(repository, messages) {
            completed(if (++calls == 1) "第二条产物" else "")
        }

        assertFalse(first.updated)
        assertEquals(old, repository.getSummary("conv-1"))
        assertEquals("m2", repository.listSummarySegments("conv-1").single().startMessageId)
        val result = update(repository, messages) { text ->
            assertTrue(text.contains("【合并要求】"))
            assertTrue(text.contains("第二条产物"))
            assertFalse(text.contains("消息编号:2"))
            completed("完整合并摘要")
        }
        assertEquals(2, result.coveredMessageCount)
    }

    @Test
    fun updateConversationSummary_sourceChangedDuringGenerationRejectsLateArtifact() = runTest {
        val messages = sourceMessages(1)
        val old = ConversationSummary("conv-1", summary = "旧摘要")
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)

        val result = update(repository, messages) {
            repository.sourceMessages =
                listOf(messages.single().copy(content = "用户刚改成的新事实", memoryRevision = 2))
            completed("迟到的旧事实")
        }

        assertFalse(result.updated)
        assertEquals(old, repository.getSummary("conv-1"))
        assertTrue(repository.listSummarySegments("conv-1").isEmpty())
    }

    @Test
    fun updateConversationSummary_newerSummaryWinsAgainstLateGeneration() = runTest {
        val messages = sourceMessages(1)
        val old = ConversationSummary("conv-1", summary = "旧摘要")
        val replacement = old.copy(summary = "用户保留的新摘要", updatedAt = 500)
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)

        val result = update(repository, messages) {
            repository.upsertSummary(replacement)
            completed("迟到的覆盖")
        }

        assertFalse(result.updated)
        assertEquals(replacement, repository.getSummary("conv-1"))
    }

    @Test
    fun updateConversationSummary_unknownCompletionIsPersistedWithoutRepeatedAutomaticRequests() =
        runTest {
            val messages = sourceMessages(1)
            val repository = FakeConversationSummaryRepository(sourceMessages = messages)
            val first = update(repository, messages) {
                PromptCompletionResult(
                    "兼容响应产物",
                    PromptCompletionStatus.UNKNOWN
                )
            }
            assertTrue(first.updated)
            assertEquals(0, first.coveredMessageCount)
            val stored = repository.getSummary("conv-1")!!

            repeat(3) {
                val resumed =
                    update(repository, messages) { error("已有UNKNOWN产物不得重复自动付费") }
                assertFalse(resumed.updated)
                assertEquals(0, resumed.coveredMessageCount)
            }

            assertEquals(stored, repository.getSummary("conv-1"))
            assertEquals(PromptCompletionStatus.UNKNOWN, stored.completionStatus)
            assertEquals(listOf("m1"), stored.sourceSnapshot.map { it.messageId })
        }

    @Test
    fun updateConversationSummary_completedMergeCannotUpgradeUnknownSourceUntilRawRefresh() =
        runTest {
            val messages = sourceMessages(2)
            val old =
                summary(messages.take(1), PromptCompletionStatus.UNKNOWN, "未经完成证明的旧段")
            val unknownSegment =
                segment("unknown", messages.take(1), PromptCompletionStatus.UNKNOWN, old.summary)
            val repository =
                FakeConversationSummaryRepository(listOf(old), listOf(unknownSegment), messages)

            val merged = update(repository, messages) { completed("本次合并完整返回") }

            assertTrue(merged.updated)
            assertEquals(0, merged.coveredMessageCount)
            assertEquals(
                PromptCompletionStatus.UNKNOWN,
                repository.getSummary("conv-1")!!.completionStatus
            )
            update(repository, messages) { error("合并产物也必须复用") }
            val refreshedInputs = mutableListOf<String>()
            val refreshed = update(repository, messages, forceRefresh = true) { text ->
                refreshedInputs += text
                completed("从完整来源重新整理")
            }
            assertTrue(refreshedInputs.any { it.contains("消息编号:1") })
            assertFalse(refreshedInputs.any { it.contains("消息编号:2") })
            assertEquals(2, refreshed.coveredMessageCount)
            assertEquals(
                PromptCompletionStatus.COMPLETED,
                repository.getSummary("conv-1")!!.completionStatus
            )
        }

    @Test
    fun updateConversationSummary_forcedUnknownResponseIsSavedWithoutAutomaticRebilling() =
        runTest {
            val messages = sourceMessages(1)
            val repository = FakeConversationSummaryRepository(sourceMessages = messages)

            val result = update(repository, messages, forceRefresh = true) {
                PromptCompletionResult("手动刷新收到的兼容摘要", PromptCompletionStatus.UNKNOWN)
            }

            assertTrue(result.updated)
            assertEquals("手动刷新收到的兼容摘要", repository.getSummary("conv-1")!!.summary)
            assertEquals(0, result.coveredMessageCount)
            update(repository, messages) { error("已收到的手动产物不得自动重新收费") }
        }

    @Test
    fun updateConversationSummary_recoveryMergeIncludesAllTwentyStoredSegments() = runTest {
        val messages = sourceMessages(20)
        val segments = messages.mapIndexed { index, message ->
            segment(
                "s$index",
                listOf(message),
                text = "档案段$index"
            )
        }
        val repository =
            FakeConversationSummaryRepository(initialSegments = segments, sourceMessages = messages)
        var calls = 0

        val result = update(repository, messages) { text ->
            calls++
            assertTrue(text.contains("【合并要求】"))
            (0 until 20).forEach { assertTrue(text.contains("档案段$it")) }
            completed("全量分段摘要")
        }

        assertEquals(1, calls)
        assertEquals(20, result.coveredMessageCount)
        assertEquals(20, repository.getSummary("conv-1")!!.sourceSnapshot.size)
    }

    @Test
    fun updateConversationSummary_sourceChangesDuringMergeKeepPriorSummary() = runTest {
        val messages = sourceMessages(2)
        val old = summary(messages.take(1), text = "旧前缀摘要")
        val repository = FakeConversationSummaryRepository(listOf(old), sourceMessages = messages)

        val result = update(repository, messages) { text ->
            if (text.contains("【合并要求】")) {
                repository.sourceMessages = listOf(
                    messages.first().copy(content = "改后的第一条", memoryRevision = 2),
                    messages.last()
                )
            }
            completed("迟到合并结果")
        }

        assertFalse(result.updated)
        assertEquals(old, repository.getSummary("conv-1"))
        assertNull(
            ConversationSummaryCoverageSupport.usableSummary(
                old,
                repository.sourceMessages!!
            )
        )
        assertEquals(1, repository.listSummarySegments("conv-1").size)
    }

    @Test
    fun unfinishedMiddleMessageKeepsCoverageHoleAndReusesLaterSegment() = runTest {
        for (status in listOf(MessageStatus.ERROR, MessageStatus.LOADING)) {
            val original = sourceMessages(3)
            val messages =
                original.mapIndexed { index, message -> if (index == 1) message.copy(status = status) else message }
            val repository = FakeConversationSummaryRepository(sourceMessages = messages)
            val inputs = mutableListOf<String>()
            val first = update(repository, messages) { text ->
                inputs += text
                assertFalse(text.contains("消息编号:2"))
                completed("分段${inputs.size}")
            }
            assertEquals(1, first.coveredMessageCount)
            assertEquals(
                listOf("m1"),
                repository.getSummary("conv-1")!!.sourceSnapshot.map { it.messageId })
            assertEquals(2, repository.listSummarySegments("conv-1").size)
            assertEquals(2, inputs.size)
            update(repository, messages) { error("缺口后的现有分段不能被重复提取") }

            val repaired =
                original.mapIndexed { index, message -> if (index == 1) message.copy(memoryRevision = 2L) else message }
            repository.sourceMessages = repaired
            val next =
                update(repository, repaired) { text -> inputs += text; completed("补齐后摘要") }
            assertEquals(3, next.coveredMessageCount)
            assertEquals(4, inputs.size)
            assertEquals(1, inputs.count { it.contains("消息编号:3") })
            assertEquals(
                listOf("m1", "m2", "m3"),
                repository.getSummary("conv-1")!!.sourceSnapshot.map { it.messageId })
        }
    }

    private suspend fun update(
        repository: ConversationSummaryRepository,
        messages: List<ChatMessage>,
        forceRefresh: Boolean = false,
        input: (List<ChatMessage>) -> String = { values -> values.joinToString("\n") { it.content } },
        generate: suspend (String) -> PromptCompletionResult,
    ): SummaryUpdateResult {
        val provider = ProviderSettings(
            id = "synthetic",
            baseUrl = "https://example.invalid/v1/",
            apiKey = "synthetic",
            selectedModel = "summary"
        )
        return ConversationSummaryCoordinator(
            unusedPromptExtrasService(),
            repository,
            nowProvider = { 1_000L })
            .updateConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                completedMessages = messages,
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id
                ),
                config = SummaryGenerationConfig(
                    0,
                    0,
                    1,
                    segmentTargetCharacterCount = 1_200,
                    maxSegmentsPerRun = 10
                ),
                forceRefresh = forceRefresh,
                buildSummaryInput = input,
                generateSummary = { text, _, _, _, _ -> generate(text) },
            )
    }

    private fun sourceMessages(count: Int): List<ChatMessage> = (1..count).map { index ->
        ChatMessage(
            "m$index",
            "conv-1",
            MessageRole.USER,
            "消息编号:$index\n" + "细节".repeat(650),
            createdAt = index.toLong()
        )
    }

    private fun summary(
        messages: List<ChatMessage>,
        status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED,
        text: String = "有效摘要",
    ) = ConversationSummary(
        conversationId = "conv-1",
        assistantId = "assistant-1",
        summary = text,
        coveredMessageCount = if (status == PromptCompletionStatus.COMPLETED) messages.size else 0,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages),
        completionStatus = status,
    )

    private fun segment(
        id: String,
        messages: List<ChatMessage>,
        status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED,
        text: String = "有效分段$id",
    ) = ConversationSummarySegment(
        id,
        "conv-1",
        "assistant-1",
        messages.first().id,
        messages.last().id,
        messages.first().createdAt,
        messages.last().createdAt,
        messages.size,
        text,
        sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages),
        completionStatus = status,
    )

    private fun unusedPromptExtrasService(): AiPromptExtrasService {
        return object : AiPromptExtrasService {
            override suspend fun generateTitle(
                firstUserMessage: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?
            ): String = error("不应调用")

            override suspend fun generateChatSuggestions(
                conversationSummary: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?
            ): List<String> = error("不应调用")

            override suspend fun generateConversationSummary(
                conversationText: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?
            ): String = error("不应调用")

            override suspend fun generateRoleplayConversationSummary(
                conversationText: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?
            ): String = error("不应调用")

            override suspend fun generateMemoryEntries(
                conversationExcerpt: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?,
                existingMemories: List<String>,
                userName: String,
                characterName: String,
                extractionPromptOverride: String
            ): List<String> = error("不应调用")

            override suspend fun generateRoleplayMemoryEntries(
                conversationExcerpt: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?,
                existingMemories: List<String>,
            ): StructuredMemoryExtractionResult = error("不应调用")

            override suspend fun generateRoleplaySuggestions(
                conversationExcerpt: String,
                systemPrompt: String,
                playerStyleReference: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?,
                longformMode: Boolean,
            ): List<RoleplaySuggestionUiModel> = error("不应调用")

            override suspend fun condenseRoleplayMemories(
                memoryItems: List<String>,
                mode: RoleplayMemoryCondenseMode,
                maxItems: Int,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?,
            ): List<String> = error("不应调用")
        }
    }

    private fun completed(content: String) =
        PromptCompletionResult(content, PromptCompletionStatus.COMPLETED)
}
