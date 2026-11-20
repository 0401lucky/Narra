package com.example.myapplication.testutil

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryVectorRepository
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingClient
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryEmbeddingBatch
import com.example.myapplication.model.MemoryEmbeddingPurpose
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayScenario
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.UUID

internal data class RecordedEmbeddingCall(
    val providerId: String,
    val model: String,
    val inputs: List<String>,
    val purpose: MemoryEmbeddingPurpose,
    val expectedDimensions: Int?,
)

/** 只有显式合成向量，绝不委托真实 HTTP 客户端。 */
internal class RecordingMemoryEmbeddings : MemoryEmbeddingClient {
    val calls = mutableListOf<RecordedEmbeddingCall>()
    var vector: (String) -> FloatArray = { floatArrayOf(1f, 0f) }
    var answer: suspend (RecordedEmbeddingCall) -> MemoryEmbeddingBatch = { call ->
        val values = call.inputs.map(vector)
        MemoryEmbeddingBatch(values, values.first().size)
    }

    override suspend fun embed(
        provider: ProviderSettings,
        inputs: List<String>,
        purpose: MemoryEmbeddingPurpose,
        expectedDimensions: Int?,
    ): MemoryEmbeddingBatch {
        val call = RecordedEmbeddingCall(
            provider.id,
            provider.resolveFunctionModel(ProviderFunction.EMBEDDING),
            inputs.toList(),
            purpose,
            expectedDimensions
        )
        calls += call
        return answer(call)
    }

    fun indexCalls() = calls.filter { it.purpose == MemoryEmbeddingPurpose.INDEX }
    fun queryCalls() = calls.filter { it.purpose == MemoryEmbeddingPurpose.QUERY }
}

internal class MemoryVectorTestFixture(private val persistent: Boolean = false) : AutoCloseable {
    private val application: Context = ApplicationProvider.getApplicationContext<Application>()
    private val name = "v3-${UUID.randomUUID().toString().take(8)}.db"
    private val factory = ApiServiceFactory()
    val api = RecordingMemoryEmbeddings()
    var consent = true
    var memoryEnabled = true
    val characters = mutableSetOf("a", "b")
    var now = 1_800_000_000_000L
    var provider: ProviderSettings? = ProviderSettings(
        id = "embedding-provider",
        baseUrl = "https://embedding.example.invalid/v1/",
        apiKey = "vector-secret-sentinel",
        selectedModel = "chat-must-not-be-used",
        embeddingModel = "embedding-test",
        embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
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
        archive = RoomMemoryArchiveRepository(database, { now }, { it in characters })
        store = RoomConversationStore(database) { now }
    }

    fun reopen() {
        check(persistent)
        database.close()
        open()
    }

    fun repository(
        maxResults: Int = 64,
        chunker: MemoryEmbeddingChunker = MemoryEmbeddingChunker(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) = RoomMemoryVectorRepository(
        database, archive, api, factory, { provider },
        { context -> consent && memoryEnabled && context.characterId in characters },
        dispatcher, { now }, chunker, maxResults,
    )

    suspend fun context(
        persona: String = "",
        characterId: String = "a",
        purpose: MemoryPurpose = MemoryPurpose.CONTINUOUS,
    ): MemoryRuntimeContext {
        val id = "c-${UUID.randomUUID().toString().take(8)}"
        store.upsertConversationMetadata(
            Conversation(
                id,
                "合成对话",
                "",
                now,
                now,
                assistantId = characterId
            )
        )
        return archive.bindConversation(
            RoleplayScenario(
                id = "s-$id",
                assistantId = characterId,
                userPersonaOverride = persona,
                memoryPurpose = purpose
            ),
            id, listOf(characterId), "run-$id",
        ).single()
    }

    suspend fun addRecord(context: MemoryRuntimeContext, content: String): MemoryRecord {
        val messageId = "m-${UUID.randomUUID()}"
        now++
        store.upsertMessages(
            listOf(
                ChatMessage(
                    messageId,
                    context.conversationId,
                    MessageRole.USER,
                    content,
                    createdAt = now
                )
            )
        )
        val source =
            archive.listPendingIngress(context).single { it.source.messageId == messageId }.source
        val job = archive.planJob(context, UUID.randomUUID().toString(), listOf(source.fullRef()))
        checkNotNull(archive.claimJob(job.id, "vector-test"))
        check(archive.saveExtraction(job.id, "vector-test", "synthetic-extracted-artifact"))
        val committed = archive.commitJob(
            job.id, "vector-test", MemoryCommitBatch(
                records = listOf(MemoryRecordDraft("N0", content, listOf(source.fullRef()))),
            )
        ) as MemoryWriteResult.Committed
        return checkNotNull(archive.getRecordDetails(context, committed.recordIds.single())).record
    }

    override fun close() {
        database.close()
        if (persistent) application.deleteDatabase(name)
    }
}
