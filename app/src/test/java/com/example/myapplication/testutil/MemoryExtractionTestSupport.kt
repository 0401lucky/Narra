package com.example.myapplication.testutil

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.conversation.DurableMemoryExtractionCoordinator
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryEngineStateEntity
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import java.util.UUID

/** 两个协议工厂都明确拒绝网络，只有 override 的合成响应可以运行。 */
internal class RecordingMemoryAi : AiPromptExtrasService by DefaultAiPromptExtrasService(
    ApiServiceFactory(), apiServiceProvider = { _, _ -> error("测试禁止真实 OpenAI 网络") },
    anthropicApiProvider = { _, _ -> error("测试禁止真实 Anthropic 网络") },
) {
    val snapshots = mutableListOf<MemoryExtractionSnapshot>()
    val models = mutableListOf<String>()
    var answer: suspend (MemoryExtractionSnapshot) -> PromptCompletionResult =
        { memoryResponse(it) }

    override suspend fun requestMemoryExtraction(
        snapshot: MemoryExtractionSnapshot, baseUrl: String, apiKey: String, modelId: String,
        apiProtocol: ProviderApiProtocol, provider: ProviderSettings?
    ): PromptCompletionResult {
        snapshots += snapshot
        models += modelId
        return answer(snapshot)
    }
}

internal fun memoryResponse(
    snapshot: MemoryExtractionSnapshot,
    content: String = "合成经历",
    empty: Boolean = false
): PromptCompletionResult {
    val root = JsonParser.parseString(
        AppJson.gson.toJson(
            mapOf(
                "schema_version" to 2,
                "memories" to if (empty) emptyList<Any>() else listOf(
                    mapOf(
                        "local_id" to "N0",
                        "kind" to "episode",
                        "category" to "shared",
                        "content" to content,
                        "source_refs" to listOf("M0"),
                        "related_to" to emptyList<String>()
                    )
                ),
                "corrections" to emptyList<Any>(),
                "state_updates" to emptyList<Any>(),
                "resident_candidates" to emptyList<Any>(),
            )
        )
    ).asJsonObject
    root.getAsJsonArray("memories")
        .forEach { it.asJsonObject.add("occurred_date", JsonNull.INSTANCE) }
    return PromptCompletionResult(root.toString(), PromptCompletionStatus.COMPLETED)
}

internal class MemoryExtractionFixture(private val persistent: Boolean = false) : AutoCloseable {
    val application: Context = ApplicationProvider.getApplicationContext<Application>()
    private val name = "c2-${UUID.randomUUID().toString().take(8)}.db"
    var now = 1_800_000_000_000L
    var consent = true
    val ai = RecordingMemoryAi()
    var settings = AppSettings(
        providers = listOf(
            ProviderSettings(
                id = "p",
                name = "合成提供商",
                baseUrl = "https://example.invalid/v1/",
                apiKey = "synthetic-secret-sentinel",
                selectedModel = "chat-model",
                memoryModel = "memory-model"
            )
        ),
        selectedProviderId = "p",
        assistants = listOf(
            Assistant(id = "a", name = "甲", memoryEnabled = true),
            Assistant(id = "b", name = "乙", memoryEnabled = true)
        ),
        memoryAutoSummaryEvery = 1,
    )
    lateinit var database: ChatDatabase
    lateinit var archive: RoomMemoryArchiveRepository
    lateinit var store: RoomConversationStore

    init {
        open()
    }

    private fun open() {
        val builder = if (persistent) {
            application.getDatabasePath(name).parentFile?.mkdirs()
            Room.databaseBuilder(application, ChatDatabase::class.java, name)
        } else Room.inMemoryDatabaseBuilder(application, ChatDatabase::class.java)
        database = builder.allowMainThreadQueries().build()
        archive = RoomMemoryArchiveRepository(
            database,
            { now },
            { id -> settings.resolvedAssistants().any { it.id == id } })
        store = RoomConversationStore(database) { now }
    }

    fun reopen() {
        check(persistent); database.close(); open()
    }

    suspend fun enable() {
        database.memoryArchiveDao()
            .upsertEngineState(MemoryEngineStateEntity(engineVersion = 2, updatedAt = now))
    }

    suspend fun contexts(
        characters: List<String> = listOf("a"),
        persona: String = "",
        purpose: MemoryPurpose = MemoryPurpose.CONTINUOUS
    ): List<MemoryRuntimeContext> {
        val conversation = "c-${UUID.randomUUID().toString().take(8)}"
        store.upsertConversationMetadata(
            Conversation(
                conversation,
                "合成会话",
                "",
                now,
                now,
                assistantId = characters.first()
            )
        )
        return archive.bindConversation(
            RoleplayScenario(
                id = "s-$conversation",
                assistantId = characters.first(),
                userPersonaOverride = persona,
                memoryPurpose = purpose
            ),
            conversation, characters, "run-$conversation"
        )
    }

    suspend fun add(
        context: MemoryRuntimeContext,
        id: String,
        content: String,
        role: MessageRole = MessageRole.USER,
        status: MessageStatus = MessageStatus.COMPLETED,
        parts: List<ChatMessagePart> = emptyList(),
        speakerId: String = ""
    ): ChatMessage {
        now++
        store.upsertMessages(
            listOf(
                ChatMessage(
                    id,
                    context.conversationId,
                    role,
                    content,
                    status,
                    now,
                    parts = parts,
                    speakerId = speakerId
                )
            )
        )
        return store.listMessages(context.conversationId).single { it.id == id }
    }

    fun coordinator(
        repository: MemoryArchiveRepository = archive,
        spanChars: Int = 2400,
        inputChars: Int = 6000,
        leaseMillis: Long = 120_000L
    ) =
        DurableMemoryExtractionCoordinator(
            repository,
            ai,
            { settings },
            { consent },
            leaseMillis,
            spanChars,
            inputChars,
            { now },
        )

    override fun close() {
        database.close(); if (persistent) application.deleteDatabase(name)
    }
}
