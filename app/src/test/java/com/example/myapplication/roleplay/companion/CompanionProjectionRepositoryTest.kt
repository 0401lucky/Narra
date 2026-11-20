package com.example.myapplication.roleplay.companion

import android.app.Application
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayMessageUiMapper
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionTestFixture
import com.example.myapplication.conversation.CompanionTurnCoordinator
import com.example.myapplication.conversation.CompanionGenerationResult
import com.example.myapplication.conversation.ConversationSummaryCoordinator
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.conversation.SummaryGenerationConfig
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import kotlinx.coroutines.flow.flowOf
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionProjectionRepositoryTest : CompanionTestFixture() {
    private val snapshot = CompanionPresentationSnapshot(
        sourceSettingsRevision = 7,
        style = CompanionWritingStyle.LITERARY,
        observation = CompanionObservationSettings(
            enabled = true,
            customFields = listOf(CompanionObservationField("obs_coat", "旧衣着名称"))
        ),
    )
    private val raw =
        " \r\n<observe version=\"1\"><field id=\"obs_coat\">蓝色外套</field></observe>\r\n" +
                "<narration sprite=\"normal\">窗边 &amp; 桌角。</narration>\r\n<dialogue voice_emotion=\"calm\">你好。</dialogue> \r\n"

    @Test
    fun reopenProjection() = runBlocking<Unit> {
        useFileDatabase()
        val original = committed(raw)
        val beforeRow = database.conversationDao().getMessage(original.id)
        assertEquals(raw, original.content)
        assertEquals(raw, original.parts.single { it.type == ChatMessagePartType.TEXT }.text)
        reopen()
        val message = store.listMessages(original.conversationId).single { it.id == original.id }
        assertEquals(raw, RoleplayMessageFormatSupport.resolveAssistantRawContent(message))
        val projection = requireNotNull(CompanionReplyProjector.project(message))
        assertEquals(raw, projection.raw)
        assertEquals(7L, projection.source.configurationRevision)
        assertEquals(listOf("旧衣着名称"), projection.observation!!.fields.map { it.label })
        repeat(3) { assertEquals(projection, CompanionReplyProjector.project(message)) }
        assertEquals(beforeRow, database.conversationDao().getMessage(original.id))
        assertEquals(
            original.memoryRevision,
            store.listMessages(original.conversationId)
                .single { it.id == original.id }.memoryRevision
        )
    }

    @Test
    fun currentLabelChange_doesNotReinterpretCanonicalHistoryOrTranscript() = runBlocking<Unit> {
        val message = committed(raw)
        val turn = requireNotNull(repository.getTurn(message.companionTurnId))
        val encounter = requireNotNull(repository.getEncounter(turn.encounterId))
        val scenario = requireNotNull(roleplay.getScenario(encounter.context.scenarioId))
        val current = AppSettings(
            assistants = listOf(Assistant(id = "alice", name = "艾丽")),
            companionCharacterSettings = mapOf(
                "alice" to CompanionCharacterSettings(
                    revision = 20,
                    writing = CompanionWritingSettings(
                        observation = snapshot.observation.copy(
                            customFields = listOf(CompanionObservationField("obs_new", "全新名称")),
                        )
                    ),
                )
            ),
        )
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario,
            current.assistants.single(),
            current,
            listOf(message),
            null,
            RoleplayOutputParser(),
            { 0 })
        assertEquals(listOf("窗边 & 桌角。", "你好。"), mapped.map { it.content })
        assertEquals(
            "旧衣着名称",
            mapped.first().companionReply!!.observation!!.fields.single().label
        )
        assertEquals(mapped.first().uiItemId, mapped.first().companionBeat!!.id)
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            listOf(message),
            "用户",
            "艾丽",
            true,
            RoleplayInteractionMode.OFFLINE_DIALOGUE
        )
        assertTrue(transcript.contains("窗边 & 桌角。"))
        assertFalse(transcript.contains("蓝色外套"))
        assertFalse(transcript.contains("observe"))
        val semantic = RoleplayTranscriptFormatter.memorySemanticSegments(message)
        assertEquals(listOf("窗边 & 桌角。", "你好。"), semantic.map { it.text })
        assertEquals(
            listOf(MemorySemanticKind.NARRATION, MemorySemanticKind.SPEECH),
            semantic.map { it.kind })
        assertEquals(
            raw,
            store.listMessages(message.conversationId).single { it.id == message.id }.content
        )
    }

    @Test
    fun previewAndStoredReply_shareSegmentsAndObservation() = runBlocking<Unit> {
        val message = committed(raw)
        val turn = requireNotNull(repository.getTurn(message.companionTurnId))
        val attempt = requireNotNull(repository.getAttempt(requireNotNull(turn.selectedAttemptId)))
        val preview = CompanionReplyProjector.preview(attempt, raw, "艾丽", complete = true)
        val stored = requireNotNull(CompanionReplyProjector.project(message, "艾丽"))
        assertEquals(preview.beats.map { it.content }, stored.beats.map { it.content })
        assertEquals(preview.beats.map { it.rawSpan }, stored.beats.map { it.rawSpan })
        assertEquals(preview.observation, stored.observation)
        assertEquals(preview.raw, stored.raw)
    }

    @Test
    fun copiedMessageCannotReuseForeignOrChangedMetadata() = runBlocking<Unit> {
        val message = committed(raw)
        val changes = listOf(
            message.copy(content = "<dialogue>已改。</dialogue>"),
            message.copy(id = "other-message"),
            message.copy(conversationId = "other-conversation"),
            message.copy(companionTurnId = "other-turn"),
            message.copy(memoryRevision = message.memoryRevision + 1),
            message.copy(speakerId = "bob"),
            message.copy(role = MessageRole.USER),
            message.copy(roleplayOutputFormat = RoleplayOutputFormat.LONGFORM),
            message.copy(roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE),
            message.copy(status = MessageStatus.ERROR),
            message.copy(companionMemoryState = CompanionMemoryState.WAITING_TURN),
        )
        changes.forEach { changed ->
            assertFalse(requireNotNull(changed.companionReplyMetadata).matches(changed))
            assertNull(CompanionReplyProjector.project(changed)?.observation)
        }
        assertNotNull(CompanionReplyProjector.project(message)?.observation)
    }

    @Test
    fun serializedMetadataIsNeverTrustedAndRawWinsOverDisplayParts() = runBlocking<Unit> {
        val message = committed(raw)
        val encoded = JsonParser.parseString(AppJson.gson.toJson(message)).asJsonObject
        assertFalse(encoded.has("companionReplyMetadata"))
        encoded.add(
            "companionReplyMetadata",
            AppJson.gson.toJsonTree(message.companionReplyMetadata)
        )
        val imported = AppJson.gson.fromJson(encoded, ChatMessage::class.java)
        assertNull(imported.companionReplyMetadata)
        assertNull(CompanionReplyProjector.project(imported)?.observation)
        assertEquals(
            raw,
            RoleplayMessageFormatSupport.resolveAssistantRawContent(
                imported.copy(
                    parts = listOf(textMessagePart("展示正文"))
                )
            )
        )
    }

    @Test
    fun editedAndBrokenSource_recomputesOrClearsObservation() = runBlocking<Unit> {
        val message = committed(raw)
        val before = requireNotNull(CompanionReplyProjector.project(message))
        assertTrue(
            repository.editMessage(
                message.id,
                message.memoryRevision,
                "<dialogue>改写后没有观测。</dialogue>"
            )
        )
        val edited = store.listMessages(message.conversationId).single { it.id == message.id }
        val after = requireNotNull(CompanionReplyProjector.project(edited))
        assertTrue(after.source.revision > before.source.revision)
        assertNull(after.observation)
        assertNotEquals(before.beats.first().id, after.beats.first().id)
        assertEquals(
            edited.content,
            edited.parts.single { it.type == ChatMessagePartType.TEXT }.text
        )
        sql("UPDATE companion_turns SET selectedAttemptId = NULL WHERE id = '${message.companionTurnId}'")
        val broken = store.listMessages(message.conversationId).single { it.id == message.id }
        assertNull(broken.companionReplyMetadata)
        assertTrue(
            CompanionReplyDiagnostic.MISSING_SOURCE_METADATA in requireNotNull(
                CompanionReplyProjector.project(broken)
            ).diagnostics
        )
        assertEquals("改写后没有观测。", CompanionReplyProjector.project(broken)!!.displayText)
    }

    @Test
    fun observationOnlyArtifact_isNotCanonicalStory() = runBlocking<Unit> {
        val prepared = preparedWithSnapshot()
        received(prepared, "<observe version=\"1\"><field id=\"time\">傍晚</field></observe>")
        assertEquals(
            CompanionCommitResult.Conflict(CompanionConflict.INCOMPLETE_RESULT),
            repository.commitAttempt(prepared.attempt.id)
        )
        assertTrue(
            store.listMessages(prepared.attempt.context.conversationId)
                .none { it.role == MessageRole.ASSISTANT })
    }

    @Test
    fun oversizedObservationOnlyArtifact_isRejectedByCanonicalCommit() = runBlocking<Unit> {
        val prepared = preparedWithSnapshot()
        received(prepared, oversizedObservation())
        assertEquals(
            CompanionCommitResult.Conflict(CompanionConflict.INCOMPLETE_RESULT),
            repository.commitAttempt(prepared.attempt.id)
        )
        assertTrue(
            store.listMessages(prepared.attempt.context.conversationId)
                .none { it.role == MessageRole.ASSISTANT }
        )
    }

    @Test
    fun oversizedObservationOnlyReply_staysIncompleteAfterCollection() = runBlocking<Unit> {
        val prepared = preparedWithSnapshot()
        val raw = oversizedObservation()
        val result = CompanionTurnCoordinator(repository).generate(prepared.attempt.id) {
            flowOf(
                ChatStreamEvent.ContentDelta(raw),
                ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.COMPLETED),
                ChatStreamEvent.Completed
            )
        }
        assertTrue(result is CompanionGenerationResult.Generated)
        assertEquals(
            PromptCompletionStatus.INCOMPLETE,
            (result as CompanionGenerationResult.Generated).completionStatus
        )
        assertEquals(raw, repository.getAttempt(prepared.attempt.id)!!.artifact!!.rawReply)
        assertTrue(
            store.listMessages(prepared.attempt.context.conversationId)
                .none { it.role == MessageRole.ASSISTANT }
        )
    }

    @Test
    fun oversizedObservationWithBody_preservesCanonicalRawAndSemanticBody() = runBlocking<Unit> {
        val block = oversizedObservation()
        val body = "<dialogue>仍保留正文🙂。</dialogue>"
        val raw = "$block\r\n$body"
        val message = committed(raw)
        val reply = requireNotNull(CompanionReplyProjector.project(message))
        assertEquals(raw, message.content)
        assertEquals(raw, message.parts.single { it.type == ChatMessagePartType.TEXT }.text)
        assertEquals(raw, reply.raw)
        assertEquals("仍保留正文🙂。", reply.displayText)
        assertEquals(CompanionRawSpan(block.length + 2, raw.length), reply.beats.single().rawSpan)
        assertEquals(
            listOf("仍保留正文🙂。"),
            RoleplayTranscriptFormatter.memorySemanticSegments(message).map { it.text }
        )
        assertNull(reply.observation)
        assertTrue(CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED in reply.diagnostics)
    }

    @Test
    fun summaryRequest_excludesObservationAndKeepsRawSourceCoverage() = runBlocking<Unit> {
        val summaryRaw = raw.replace("蓝色外套", "OBS_SUMMARY_SENTINEL")
            .replace("你好。", "你好🙂。")
        val message = committed(summaryRaw)
        val turn = requireNotNull(repository.getTurn(message.companionTurnId))
        val context = requireNotNull(repository.getEncounter(turn.encounterId)).context
        val messages = store.listMessages(message.conversationId)
        val beforeRow = database.conversationDao().getMessage(message.id)
        val frozenSources = ConversationSummaryCoverageSupport.sourceSnapshot(messages)
        val server = MockWebServer().apply { start() }
        try {
            val factory = ApiServiceFactory()
            val extras = DefaultAiPromptExtrasService(
                apiServiceFactory = factory,
                apiServiceProvider = { baseUrl, apiKey -> factory.createLongRunning(baseUrl, apiKey) },
            )
            val provider = ProviderSettings(
                "synthetic-summary", baseUrl = server.url("/v1/").toString(),
                apiKey = "synthetic", selectedModel = "summary",
            )
            val settings = AppSettings(providers = listOf(provider), selectedProviderId = provider.id)
            val summaries = RoomMemoryRepository(database.memoryDao(), database.companionDao())
            assertEquals(
                frozenSources,
                ConversationSummaryCoverageSupport.sourceSnapshot(
                    summaries.getSummarySourceMessages(context.conversationId)
                )
            )
            val completion = mapOf(
                "choices" to listOf(mapOf(
                    "index" to 0,
                    "message" to mapOf("role" to "assistant", "content" to "一起在窗边坐了一会儿。"),
                    "finish_reason" to "stop",
                )),
            )
            server.enqueue(MockResponse().setBody(AppJson.gson.toJson(completion)))
            val coordinator = ConversationSummaryCoordinator(extras, summaries) { clock++ }
            val result = coordinator.updateConversationSummary(
                conversationId = context.conversationId, assistantId = context.characterId,
                completedMessages = messages, settings = settings,
                config = SummaryGenerationConfig(0, 0, 1), expectedMemoryContext = context,
                isSourceAllowed = { true },
                buildSummaryInput = { sources ->
                    RoleplayTranscriptFormatter.formatMessageSegments(
                        sources, "用户", "艾丽", true, RoleplayInteractionMode.OFFLINE_DIALOGUE
                    ).joinToString("\n")
                },
                generateSummary = { input, baseUrl, apiKey, modelId, protocol ->
                    extras.generateRoleplayConversationSummaryResult(
                        input, baseUrl, apiKey, modelId, protocol, provider
                    )
                },
            )
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/v1/chat/completions", request.path)
            val requestMessage = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                .getAsJsonArray("messages").single().asJsonObject
            assertEquals("user", requestMessage.get("role").asString)
            val sentText = requestMessage.get("content").asString
            assertTrue(result.updated)
            assertEquals(1, server.requestCount)
            assertTrue(sentText.contains("窗边 & 桌角。"))
            assertTrue(sentText.contains("你好🙂。"))
            assertFalse(sentText.contains("OBS_SUMMARY_SENTINEL"))
            assertFalse(sentText.contains("<observe"))
            assertEquals(frozenSources, summaries.getSummaryForContext(context)!!.sourceSnapshot)
            assertEquals(summaryRaw.length, frozenSources.single { it.messageId == message.id }.endOffset)
            assertEquals(beforeRow, database.conversationDao().getMessage(message.id))
            assertEquals(
                frozenSources,
                ConversationSummaryCoverageSupport.sourceSnapshot(store.listMessages(message.conversationId))
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun shortWordReply_survivesCollectorAndCanonicalCommit() = runBlocking<Unit> {
        val prepared = preparedWithSnapshot()
        val raw = "<dialogue>new</dialogue>"
        val result = CompanionTurnCoordinator(repository).generate(prepared.attempt.id) {
            flowOf(
                ChatStreamEvent.ContentDelta(raw),
                ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.COMPLETED),
                ChatStreamEvent.Completed
            )
        }
        assertTrue(result is CompanionGenerationResult.Saved)
        val message = (result as CompanionGenerationResult.Saved).receipt.replyMessage
        assertEquals(raw, message.content)
        assertEquals("new", CompanionReplyProjector.project(message)!!.displayText)
    }

    @Test
    fun standaloneOldAndTestLines_doNotCutTheRestOfTheReply() = runBlocking<Unit> {
        val prepared = preparedWithSnapshot()
        val text = "first\nold\nsecond\ntest\nlast"
        val raw = "<dialogue>$text</dialogue>"
        val result = CompanionTurnCoordinator(repository).generate(prepared.attempt.id) {
            flowOf(
                ChatStreamEvent.ContentDelta(raw),
                ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.COMPLETED),
                ChatStreamEvent.Completed
            )
        } as CompanionGenerationResult.Saved
        assertEquals(raw, result.receipt.replyMessage.content)
        assertEquals(
            text,
            CompanionReplyProjector.project(result.receipt.replyMessage)!!.displayText
        )
        assertEquals(
            listOf(text),
            RoleplayTranscriptFormatter.memorySemanticSegments(result.receipt.replyMessage)
                .map { it.text })
    }

    private fun oversizedObservation(): String =
        "<observe version=\"1\" pad=\"${"a".repeat(5_000)}\"><field id=\"time\">OBS_BOUNDARY_SENTINEL</field></observe>"

    private suspend fun committed(text: String): ChatMessage {
        val prepared = preparedWithSnapshot()
        received(prepared, text)
        return (repository.commitAttempt(prepared.attempt.id) as CompanionCommitResult.Committed).replyMessage
    }

    private suspend fun preparedWithSnapshot(): CompanionPreparedTurn {
        val context = runtime()
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        return repository.beginTurn(
            encounter.id, CompanionTurnKind.INPUT,
            ChatMessage(
                "input-${UUID.randomUUID()}",
                context.conversationId,
                MessageRole.USER,
                "你好",
                createdAt = clock++
            ),
            CompanionRequestDescriptor(presentation = snapshot),
        )
    }
}
