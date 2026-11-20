package com.example.myapplication.data.local.chat

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.context.MemoryVectorSearch
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.memory.MemoryEngineStateEntity
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryVectorRepository
import com.example.myapplication.data.repository.embedding.MemoryEmbeddingClient
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryRecallMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "mr-${UUID.randomUUID().toString().take(8)}.db"
    private val databaseFile = context.getDatabasePath(databaseName)
    private val sourceSchema: JsonObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/50.json")
            .bufferedReader(Charsets.UTF_8).use { reader ->
                AppJson.gson.fromJson(reader, JsonObject::class.java).getAsJsonObject("database")
            }
    }
    private val entities: Map<String, JsonObject> by lazy {
        sourceSchema.getAsJsonArray("entities").associate { element ->
            element.asJsonObject.let { it.get("tableName").asString to it }
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrateFrom50ToLatest_preservesAllOldColumnsReceiptsAndVectorBytes() {
        val original = createVersion50Database()

        openWithRoom().close()

        readDatabase().use { db ->
            assertEquals(original, snapshot(db))
            assertEquals(
                RAW_TEXT,
                scalar(db, "SELECT content FROM messages WHERE id = 'old-message'")
            )
            assertEquals(
                "4",
                scalar(db, "SELECT memoryRevision FROM messages WHERE id = 'old-message'")
            )
            assertEquals(
                "SENT",
                scalar(db, "SELECT status FROM memory_recall_receipts WHERE requestId = 'old-sent'")
            )
            assertEquals(
                "PREPARED",
                scalar(
                    db,
                    "SELECT status FROM memory_recall_receipts WHERE requestId = 'old-prepared'"
                )
            )
            assertEquals(
                OLD_SENT_JSON,
                scalar(
                    db,
                    "SELECT sentJson FROM memory_recall_receipts WHERE requestId = 'old-sent'"
                )
            )
            assertEquals(
                "1200",
                scalar(db, "SELECT sentAt FROM memory_recall_receipts WHERE requestId = 'old-sent'")
            )
            assertNull(
                rows(
                    db,
                    "SELECT sentAt FROM memory_recall_receipts WHERE requestId = 'old-prepared'"
                ).single()["sentAt"]
            )
            assertEquals(
                "READY",
                scalar(db, "SELECT status FROM memory_embedding_indexes WHERE generation = 7")
            )
            assertEquals(
                "1",
                scalar(db, "SELECT active FROM memory_embedding_indexes WHERE generation = 7")
            )
            assertEquals(
                "PAUSED",
                scalar(db, "SELECT status FROM memory_embedding_indexes WHERE generation = 8")
            )
            assertArrayEquals(VECTOR_ONE, vector(db, 7))
            assertArrayEquals(VECTOR_TWO, vector(db, 8))
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
        }
    }

    @Test
    fun migrateFrom50ToLatest_addsDefaultsAndKeepsPersonaPrimaryKeyAndForeignKeysEnforced() {
        createVersion50Database()

        openWithRoom().close()

        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { db ->
                db.setForeignKeyConstraintsEnabled(true)
                assertEquals(
                    "",
                    scalar(
                        db,
                        "SELECT memoryRecallRequestId FROM messages WHERE id = 'old-message'"
                    )
                )
                rows(db, "SELECT * FROM memory_recall_receipts").forEach { receipt ->
                    assertEquals("unknown", receipt["personaKey"])
                    listOf(
                        "scenarioId",
                        "runId",
                        "anchorMessageId",
                        "userMessageId",
                        "failureCode"
                    ).forEach {
                        assertEquals("", receipt[it])
                    }
                    assertEquals("[]", receipt["transmissionsJson"])
                }
                rows(db, "SELECT * FROM memory_embedding_indexes").forEach { index ->
                    assertEquals("unknown", index["personaKey"])
                    listOf(
                        "configurationKey",
                        "contextJson",
                        "requestToken",
                        "failureCode"
                    ).forEach { assertEquals("", index[it]) }
                    listOf("recordsJson", "pendingBatchJson").forEach {
                        assertEquals(
                            "[]",
                            index[it]
                        )
                    }
                    listOf(
                        "totalChunks",
                        "completedChunks",
                        "fullRebuild"
                    ).forEach { assertEquals("0", index[it]) }
                }
                val primaryKey = rows(db, "PRAGMA table_info(memory_embedding_indexes)")
                    .filter { requireNotNull(it["pk"]).toInt() > 0 }
                    .sortedBy { requireNotNull(it["pk"]).toInt() }
                    .map { it["name"] }
                assertEquals(
                    listOf("spaceId", "vectorSpace", "generation", "personaKey"),
                    primaryKey
                )
                val otherPersona = ContentValues().apply {
                    rows(db, "SELECT * FROM memory_embedding_indexes WHERE generation = 7").single()
                        .forEach { (key, value) -> put(key, value) }
                    put("personaKey", "another-persona")
                }
                db.insertOrThrow("memory_embedding_indexes", null, otherPersona)
                assertEquals(
                    "2",
                    scalar(db, "SELECT COUNT(*) FROM memory_embedding_indexes WHERE generation = 7")
                )
                expectConstraint {
                    db.insertOrThrow(
                        "memory_embedding_indexes",
                        null,
                        otherPersona
                    )
                }
                expectConstraint {
                    db.insertOrThrow(
                        "memory_embedding_indexes",
                        null,
                        ContentValues(otherPersona).apply { put("spaceId", "missing-space") })
                }
                expectConstraint {
                    db.execSQL("UPDATE memory_embeddings SET recordId = 'missing-record' WHERE generation = 7")
                }
                assertTrue(
                    rows(
                        db,
                        "PRAGMA index_list(memory_recall_receipts)"
                    ).any { it["name"] == "index_memory_recall_receipts_spaceId_createdAt" })
                assertTrue(
                    rows(
                        db,
                        "PRAGMA index_list(memory_embeddings)"
                    ).any { it["name"] == "index_memory_embeddings_spaceId_vectorSpace_generation" })
                assertTrue(rows(db, "PRAGMA foreign_key_list(memory_embedding_indexes)").any {
                    it["table"] == "memory_spaces" && it["from"] == "spaceId" && it["to"] == "id"
                })
                assertArrayEquals(VECTOR_ONE, vector(db, 7))
                assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            }
    }

    @Test
    fun migratedActiveIndex_withoutNewConfigurationIsNotUsableEvenWithValidRecallRecords() =
        runBlocking {
            createVersion50Database()
            val database = openWithRoom()
            try {
                val archive = createArchive(database)
                val runtime = requireNotNull(archive.getRuntimeContext("ca", "a"))
                assertEquals("unknown", runtime.personaKey)
                assertTrue(archive.listRecallRecords(runtime).isEmpty())
                // 新材料通过真实原文持久化与事务提交建立，不把旧未知来源直接改成可用。
                val store = RoomConversationStore(database) { CLOCK }
                store.upsertMessages(
                    listOf(
                        ChatMessage(
                            id = "verified-message",
                            conversationId = "ca",
                            role = MessageRole.USER,
                            content = "新明确约定：今天到灯塔碰面。",
                            status = MessageStatus.COMPLETED,
                            createdAt = CLOCK,
                        )
                    )
                )
                val source = archive.listPendingIngress(runtime).single().source
                val refs = listOf(source.fullRef())
                val job = archive.planToolJob(runtime, "migration-verifiable-record", refs)
                assertNotNull(archive.claimJob(job.id, "migration-test"))
                val batch = MemoryCommitBatch(
                    records = listOf(
                        MemoryRecordDraft(
                            "N0",
                            "今天在灯塔碰面。",
                            refs
                        )
                    )
                )
                assertTrue(
                    archive.saveExtraction(
                        job.id,
                        "migration-test",
                        AppJson.gson.toJson(batch)
                    )
                )
                val committed = archive.commitJob(
                    job.id,
                    "migration-test",
                    batch
                ) as MemoryWriteResult.Committed
                val records = archive.listRecallRecords(runtime)
                assertEquals(committed.recordIds.toSet(), records.map { it.id }.toSet())
                assertTrue(records.isNotEmpty())
                assertTrue(
                    archive.validateRecallRecords(
                        runtime,
                        records.associate { it.id to it.revision })
                )
                val oldIndex = requireNotNull(
                    database.memoryVectorDao().getIndex(SPACE_A, "unknown", VECTOR_SPACE, 7)
                )
                assertTrue(oldIndex.active)
                assertEquals("READY", oldIndex.status)
                assertEquals(
                    MemoryEmbeddingChunker.PREPROCESSING_VERSION,
                    oldIndex.preprocessingVersion
                )
                assertEquals("", oldIndex.configurationKey)
                assertEquals("", oldIndex.contextJson)
                val provider = ProviderSettings(
                    id = "vector-test",
                    baseUrl = "https://example.invalid/v1/",
                    apiKey = "synthetic-vector-key",
                    embeddingModel = "embed-test",
                    embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
                )
                assertTrue(provider.enabled && provider.hasBaseCredentials())
                assertEquals(
                    "embed-test",
                    provider.resolveFunctionModel(ProviderFunction.EMBEDDING)
                )
                var embeddingCalls = 0
                val vectors = RoomMemoryVectorRepository(
                    database = database,
                    archiveRepository = archive,
                    embeddingClient = MemoryEmbeddingClient { _, _, _, _ ->
                        embeddingCalls++
                        error("旧索引迁移测试禁止向量网络")
                    },
                    apiServiceFactory = ApiServiceFactory(),
                    provider = { provider },
                    isAllowed = { candidate ->
                        candidate == runtime && archive.getRuntimeContext(
                            "ca",
                            "a"
                        ) == runtime
                    },
                    nowMillis = { CLOCK },
                )

                assertTrue(vectors.search(runtime, records, listOf("灯塔碰面")).isEmpty())

                assertEquals(0, embeddingCalls)
                assertEquals(
                    oldIndex,
                    database.memoryVectorDao().getIndex(SPACE_A, "unknown", VECTOR_SPACE, 7)
                )
                assertNull(archive.getRecallReceipt(runtime, "old-sent"))
            } finally {
                database.close()
            }
            readDatabase().use { db ->
                assertArrayEquals(VECTOR_ONE, vector(db, 7))
                assertArrayEquals(VECTOR_TWO, vector(db, 8))
            }
        }

    @Test
    fun migratedLegacyMemories_remainScopedAndUnverifiedInEngineTwoLocalRecall() = runBlocking {
        createVersion50Database()
        val database = openWithRoom()
        try {
            database.memoryArchiveDao()
                .upsertEngineState(MemoryEngineStateEntity(engineVersion = 2, updatedAt = CLOCK))
            val archive = createArchive(database)
            val runtime = requireNotNull(archive.getRuntimeContext("ca", "a"))
            val snapshot = requireNotNull(archive.getRecallSnapshot(runtime))
            assertEquals(
                setOf(GLOBAL_A, ASSISTANT_A, CONVERSATION_A),
                snapshot.legacyRecords.map { it.id }.toSet()
            )
            assertTrue(snapshot.records.isEmpty())
            assertTrue(archive.listRecallRecords(runtime).isEmpty())
            var vectorCalled = false
            val engine =
                MemoryRecallEngine(archive, vectorSearch = MemoryVectorSearch { _, records, _ ->
                    vectorCalled = true
                    assertTrue("旧未验证材料不能进入向量通道", records.isEmpty())
                    emptyList()
                }, now = { CLOCK })

            val plan = requireNotNull(engine.recall(runtime, "旧约定", emptyList(), 10, 2000, true))

            assertTrue(vectorCalled)
            assertEquals(
                setOf(GLOBAL_A, ASSISTANT_A, CONVERSATION_A),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertTrue(plan.materials.all { it.ref.legacy && it.label.contains("来源未验证") })
            assertTrue(archive.validateRecallMaterials(runtime, plan.materials.map { it.ref }))
            assertEquals(
                "MISSING",
                database.memoryArchiveDao().getSource("legacy-source:$GLOBAL_A")!!.status
            )
            assertEquals(
                "UNKNOWN",
                database.memoryArchiveDao().getSource("legacy-source:$ASSISTANT_A")!!.status
            )
            assertTrue(
                database.memoryArchiveDao().getSource("legacy-source:$GLOBAL_A")!!.content.isEmpty()
            )
            val otherRole = requireNotNull(
                archive.getRecallSnapshot(
                    requireNotNull(
                        archive.getRuntimeContext(
                            "cb",
                            "b"
                        )
                    )
                )
            )
            assertEquals(setOf(GLOBAL_B), otherRole.legacyRecords.map { it.id }.toSet())
            val otherConversation = requireNotNull(
                archive.getRecallSnapshot(
                    requireNotNull(
                        archive.getRuntimeContext(
                            "co",
                            "a"
                        )
                    )
                )
            )
            assertEquals(
                setOf(GLOBAL_A, ASSISTANT_A, OTHER_CONVERSATION_A),
                otherConversation.legacyRecords.map { it.id }.toSet()
            )
            val globalOnly = createArchive(database, useGlobal = true)
            assertEquals(
                setOf(GLOBAL_A, CONVERSATION_A),
                requireNotNull(globalOnly.getRecallSnapshot(runtime)).legacyRecords.map { it.id }
                    .toSet()
            )
            val store = RoomConversationStore(database) { CLOCK }
            for (purpose in listOf(MemoryPurpose.CONTINUOUS, MemoryPurpose.INDEPENDENT)) {
                val conversationId = "new-${purpose.storageValue}"
                store.upsertConversationMetadata(
                    Conversation(
                        conversationId,
                        "新对话",
                        "",
                        CLOCK,
                        CLOCK,
                        assistantId = "a"
                    )
                )
                val fresh = archive.bindConversation(
                    RoleplayScenario(
                        id = "scenario-$conversationId", assistantId = "a", memoryPurpose = purpose,
                    ), conversationId, listOf("a"), UUID.randomUUID().toString()
                ).single()
                assertTrue(requireNotNull(archive.getRecallSnapshot(fresh)).legacyRecords.isEmpty())
                assertTrue(
                    requireNotNull(
                        engine.recall(
                            fresh,
                            "旧约定",
                            emptyList(),
                            10,
                            2000,
                            false
                        )
                    ).materials.isEmpty()
                )
            }
            assertTrue(
                database.memoryArchiveDao().listLegacyRecords()
                    .all { it.kind == "LEGACY_UNCLASSIFIED" })
        } finally {
            database.close()
        }
    }

    private fun createArchive(database: ChatDatabase, useGlobal: Boolean = false) =
        RoomMemoryArchiveRepository(
            database = database,
            nowProvider = { CLOCK },
            isCharacterAvailable = { it in setOf("a", "b") },
            usesLegacyGlobalMemory = { useGlobal },
        )

    private fun createVersion50Database(): Map<String, List<Map<String, String?>>> {
        assertEquals(50, sourceSchema.get("version").asInt)
        assertEquals("2e462a551887e65b5a213ec6b5610546", sourceSchema.get("identityHash").asString)
        databaseFile.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            entities.forEach { (table, entity) ->
                db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    db.execSQL(
                        index.asJsonObject.get("createSql").asString.replace(
                            "\${TABLE_NAME}",
                            table
                        )
                    )
                }
            }
            sourceSchema.getAsJsonArray("views")?.forEach { element ->
                val view = element.asJsonObject
                db.execSQL(
                    view.get("createSql").asString.replace(
                        "\${VIEW_NAME}",
                        view.get("viewName").asString
                    )
                )
            }
            sourceSchema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            seedVersion50Rows(db)
            db.version = 50
            assertFalse(
                rows(
                    db,
                    "PRAGMA table_info(messages)"
                ).any { it["name"] == "memoryRecallRequestId" })
            assertFalse(
                rows(
                    db,
                    "PRAGMA table_info(memory_embedding_indexes)"
                ).any {
                    it["name"] in setOf(
                        "personaKey",
                        "configurationKey",
                        "fullRebuild",
                        "activeGeneration"
                    )
                })
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            snapshot(db)
        }
    }

    private fun seedVersion50Rows(db: SQLiteDatabase) {
        val bindings = listOf(
            arrayOf("ca", "a", SPACE_A, RUN_A),
            arrayOf("cb", "b", SPACE_B, RUN_B),
            arrayOf("co", "a", SPACE_OTHER, RUN_OTHER),
        )
        bindings.forEachIndexed { index, data ->
            val (conversation, character, space, run) = data
            insert(
                db,
                "conversations",
                mapOf(
                    "id" to conversation,
                    "title" to "旧对话-$index",
                    "assistantId" to character,
                    "createdAt" to 10L + index,
                    "updatedAt" to 20L + index
                )
            )
            insert(
                db,
                "roleplay_scenarios",
                mapOf(
                    "id" to "scenario-$conversation",
                    "assistantId" to character,
                    "chatType" to "single",
                    "memoryPurpose" to "legacy_compat"
                )
            )
            insert(
                db,
                "roleplay_sessions",
                mapOf(
                    "id" to "session-$conversation",
                    "scenarioId" to "scenario-$conversation",
                    "conversationId" to conversation,
                    "runId" to run
                )
            )
            insertSpace(db, space, character)
            insert(
                db, "memory_conversation_bindings", mapOf(
                    "conversationId" to conversation,
                    "characterId" to character,
                    "spaceId" to space,
                    "purpose" to "legacy_compat",
                    "scenarioId" to "scenario-$conversation",
                    "runId" to run,
                    "personaKey" to "unknown",
                    "createdAt" to 30L + index,
                )
            )
        }
        insertSpace(db, "role-a", "a")
        insertSpace(db, "role-b", "b")
        insert(
            db, "messages", mapOf(
                "id" to "old-message",
                "conversationId" to "ca",
                "role" to "USER",
                "content" to RAW_TEXT,
                "status" to "COMPLETED",
                "createdAt" to 100L,
                "memoryRevision" to 4L,
            )
        )
        LEGACY_RECORDS.forEachIndexed { index, record ->
            val scopeId = when (record.scope) {
                "assistant" -> record.character; "conversation" -> record.conversation; else -> ""
            }
            val pointer = if (record.missing) "missing-source-$index" else "old-message"
            insert(
                db, "memory_entries", mapOf(
                    "id" to record.id,
                    "scopeType" to record.scope,
                    "scopeId" to scopeId,
                    "characterId" to record.character,
                    "content" to record.content,
                    "importance" to 70 + index,
                    "pinned" to 0,
                    "sourceMessageId" to pointer,
                    "createdAt" to 200L + index,
                    "updatedAt" to 300L + index,
                    "lastUsedAt" to 400L + index,
                )
            )
            insert(
                db, "memory_records", mapOf(
                    "id" to record.id,
                    "spaceId" to record.space,
                    "characterId" to record.character,
                    "kind" to "LEGACY_UNCLASSIFIED",
                    "category" to "DAILY",
                    "content" to record.content,
                    "revision" to 1L,
                    "status" to "ACTIVE",
                    "importance" to 70 + index,
                    "personaKey" to "unknown",
                    "datePrecision" to "unknown",
                    "createdAt" to 200L + index,
                    "updatedAt" to 300L + index,
                    "lastUsedAt" to 400L + index,
                    "legacyScopeType" to record.scope,
                    "legacyScopeId" to scopeId,
                    "legacySourceMessageId" to pointer,
                )
            )
            insert(
                db,
                "memory_record_revisions",
                mapOf(
                    "recordId" to record.id,
                    "revision" to 1L,
                    "content" to record.content,
                    "origin" to "MIGRATION",
                    "createdAt" to 300L + index
                )
            )
            insert(
                db, "memory_source_evidence", mapOf(
                    "id" to "legacy-source:${record.id}",
                    "spaceId" to record.space,
                    "characterId" to record.character,
                    "kind" to "LEGACY_HINT",
                    "conversationId" to record.conversation,
                    "personaKey" to "unknown",
                    "status" to if (record.missing) "MISSING" else "UNKNOWN",
                    "occurredAt" to 200L + index,
                    "ingestedAt" to 300L + index,
                    "contentFormatVersion" to 1,
                )
            )
            insert(
                db, "memory_record_sources", mapOf(
                    "recordId" to record.id,
                    "recordRevision" to 1L,
                    "sourceId" to "legacy-source:${record.id}",
                    "sourceRevision" to 0L,
                    "spanStart" to 0,
                    "spanEnd" to 0,
                )
            )
        }
        listOf(7L, 8L).forEach { generation ->
            insert(
                db, "memory_embedding_indexes", mapOf(
                    "spaceId" to SPACE_A,
                    "vectorSpace" to VECTOR_SPACE,
                    "generation" to generation,
                    "providerId" to "vector-test",
                    "model" to "embed-test",
                    "dimensions" to 3,
                    "preprocessingVersion" to MemoryEmbeddingChunker.PREPROCESSING_VERSION,
                    "status" to if (generation == 7L) "READY" else "PAUSED",
                    "active" to if (generation == 7L) 1 else 0,
                    "updatedAt" to 500L + generation,
                )
            )
            insert(
                db, "memory_embeddings", mapOf(
                    "recordId" to CONVERSATION_A,
                    "recordRevision" to 1L,
                    "chunkIndex" to 0,
                    "vectorSpace" to VECTOR_SPACE,
                    "generation" to generation,
                    "spaceId" to SPACE_A,
                    "dimensions" to 3,
                    "spanStart" to 0,
                    "spanEnd" to LEGACY_RECORDS.single { it.id == CONVERSATION_A }.content.length,
                    "vector" to if (generation == 7L) VECTOR_ONE else VECTOR_TWO,
                    "status" to "READY",
                    "updatedAt" to 600L + generation,
                )
            )
        }
        listOf("old-sent", "old-prepared").forEach { id ->
            insert(
                db, "memory_recall_receipts", mapOf(
                    "requestId" to id,
                    "spaceId" to SPACE_A,
                    "characterId" to "a",
                    "conversationId" to "ca",
                    "generation" to 3L,
                    "selectedJson" to OLD_SENT_JSON,
                    "sentJson" to if (id == "old-sent") OLD_SENT_JSON else "[]",
                    "omittedJson" to "[]",
                    "payloadFingerprint" to "old-payload-$id",
                    "status" to if (id == "old-sent") "SENT" else "PREPARED",
                    "createdAt" to 1100L,
                    "sentAt" to if (id == "old-sent") 1200L else null,
                )
            )
        }
        insert(
            db,
            "memory_engine_state",
            mapOf("id" to 1, "engineVersion" to 1, "updatedAt" to 1300L)
        )
    }

    private fun insertSpace(db: SQLiteDatabase, id: String, character: String) {
        insert(
            db, "memory_spaces", mapOf(
                "id" to id,
                "identityKey" to "old-identity-$id",
                "characterId" to character,
                "purpose" to "legacy_compat",
                "generation" to 3L,
                "status" to "ACTIVE",
                "createdAt" to 15L,
                "updatedAt" to 25L,
            )
        )
        insert(
            db,
            "memory_processing_cursors",
            mapOf(
                "spaceId" to id,
                "committedSequence" to 0L,
                "historyBackfilled" to 0,
                "updatedAt" to 25L
            )
        )
    }

    private fun insert(db: SQLiteDatabase, table: String, overrides: Map<String, Any?>) {
        val fields = entities.getValue(table).getAsJsonArray("fields").map { it.asJsonObject }
        assertTrue(overrides.keys.all { column -> fields.any { it.get("columnName").asString == column } })
        val values = ContentValues().apply {
            fields.forEach { field ->
                val column = field.get("columnName").asString
                val value = when {
                    overrides.containsKey(column) -> overrides[column]
                    field.get("notNull")?.asBoolean != true -> null
                    field.get("affinity").asString == "INTEGER" -> 0L
                    field.get("affinity").asString == "TEXT" -> if (column.endsWith("Json")) "[]" else ""
                    field.get("affinity").asString == "BLOB" -> byteArrayOf()
                    else -> error("旧 fixture 出现未定义的字段类型")
                }
                when (value) {
                    null -> putNull(column)
                    is String -> put(column, value)
                    is Int -> put(column, value)
                    is Long -> put(column, value)
                    is ByteArray -> put(column, value)
                    else -> error("旧 fixture 出现未定义的值类型")
                }
            }
        }
        db.insertOrThrow(table, null, values)
    }

    private fun openWithRoom(): ChatDatabase {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 51)
        val database = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
        try {
            assertEquals(ChatDatabase.CURRENT_VERSION, database.openHelper.writableDatabase.version)
            return database
        } catch (failure: Throwable) {
            database.close()
            throw failure
        }
    }

    private fun readDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun snapshot(db: SQLiteDatabase): Map<String, List<Map<String, String?>>> =
        entities.mapValues { (table, entity) ->
            val projection = entity.getAsJsonArray("fields")
                .joinToString { "`${it.asJsonObject.get("columnName").asString}`" }
            rows(db, "SELECT $projection FROM `$table` ORDER BY $projection")
        }

    private fun rows(db: SQLiteDatabase, sql: String): List<Map<String, String?>> =
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.columnNames.indices.associate { index ->
                        cursor.getColumnName(index) to when {
                            cursor.isNull(index) -> null
                            cursor.getType(index) == Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                                .joinToString("") { "%02x".format(it.toInt() and 0xff) }

                            else -> cursor.getString(index)
                        }
                    })
                }
            }
        }

    private fun scalar(db: SQLiteDatabase, sql: String): String =
        requireNotNull(rows(db, sql).single().values.single())

    private fun vector(db: SQLiteDatabase, generation: Long): ByteArray = db.rawQuery(
        "SELECT vector FROM memory_embeddings WHERE recordId = ? AND generation = ?",
        arrayOf(CONVERSATION_A, generation.toString()),
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getBlob(0)
    }

    private fun expectConstraint(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("必须由真实 SQLite 约束拒绝", rejected)
    }

    private data class LegacyRecord(
        val id: String,
        val character: String,
        val space: String,
        val scope: String,
        val conversation: String,
        val content: String,
        val missing: Boolean = false
    )

    private companion object {
        const val CLOCK = 1_800_000_000_000L
        const val SPACE_A = "space-a"
        const val SPACE_B = "space-b"
        const val SPACE_OTHER = "space-other-a"
        const val VECTOR_SPACE = "old-vector-space"
        const val GLOBAL_A = "legacy-global-a"
        const val ASSISTANT_A = "legacy-assistant-a"
        const val CONVERSATION_A = "legacy-conversation-a"
        const val OTHER_CONVERSATION_A = "legacy-conversation-other-a"
        const val GLOBAL_B = "legacy-global-b"
        const val RUN_A = "8f702c45-28a9-4b49-8b2a-02569c5b2154"
        const val RUN_B = "8c4d50b2-4895-4d7f-982c-0b49b12a5b70"
        const val RUN_OTHER = "df0ab387-8ab6-421b-b438-76f19816b6ce"
        const val OLD_SENT_JSON =
            "[{\"kind\":\"RECORD\",\"id\":\"legacy-conversation-a\",\"revision\":1,\"spanStart\":0,\"spanEnd\":4,\"contentFingerprint\":\"old-content\",\"sourceRefs\":[],\"supportingRecords\":[],\"legacy\":true}]"
        val RAW_TEXT = "旧原始消息🍀。\n".repeat(500) + "尾部数字 706 与日期 2033年10月12日。"
        val VECTOR_ONE: ByteArray =
            ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.6f).putFloat(0.8f)
                .putFloat(0f).array()
        val VECTOR_TWO: ByteArray =
            ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putFloat(1f).putFloat(0f)
                .putFloat(0f).array()
        val LEGACY_RECORDS = listOf(
            LegacyRecord(
                GLOBAL_A,
                "a",
                "role-a",
                "global",
                "",
                "旧约定：甲记得灯塔。",
                missing = true
            ),
            LegacyRecord(ASSISTANT_A, "a", "role-a", "assistant", "", "旧约定：甲喜欢风铃。"),
            LegacyRecord(
                CONVERSATION_A,
                "a",
                SPACE_A,
                "conversation",
                "ca",
                "旧约定：本会话的栀子。"
            ),
            LegacyRecord(
                OTHER_CONVERSATION_A,
                "a",
                SPACE_OTHER,
                "conversation",
                "co",
                "旧约定：另一会话的秘密。",
                missing = true
            ),
            LegacyRecord(
                GLOBAL_B,
                "b",
                "role-b",
                "global",
                "",
                "旧约定：只属于乙的内容。",
                missing = true
            ),
        )
    }
}
