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
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.model.MEMORY_UNKNOWN_PERSONA
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryKnowledgeMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "mk-${UUID.randomUUID().toString().take(8)}.db"
    private val databaseFile = context.getDatabasePath(databaseName)
    private val sourceSchema: JsonObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/52.json")
            .bufferedReader(Charsets.UTF_8).use { reader ->
                AppJson.gson.fromJson(reader, JsonObject::class.java).getAsJsonObject("database")
            }
    }
    private val entities: Map<String, JsonObject> by lazy {
        sourceSchema.getAsJsonArray("entities").associate { item ->
            item.asJsonObject.let { it.get("tableName").asString to it }
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrateFrom52ToLatest_preservesEveryOldColumnAndReceivedWork() {
        val original = createVersion52Database()

        openWithRoom().close()

        readDatabase().use { db ->
            assertEquals(original, snapshot(db))
            assertEquals(
                CURRENT_BODY,
                scalar(db, "SELECT content FROM messages WHERE id = 'message-current'")
            )
            assertEquals(
                "2",
                scalar(db, "SELECT revision FROM memory_records WHERE id = 'record-current'")
            )
            assertEquals(
                OLD_BODY,
                scalar(
                    db,
                    "SELECT content FROM memory_record_revisions WHERE recordId = 'record-current' AND revision = 1"
                )
            )
            assertEquals(
                "3",
                scalar(
                    db,
                    "SELECT revision FROM memory_resident_entries WHERE id = 'resident-current'"
                )
            )
            assertEquals(
                "1",
                scalar(
                    db,
                    "SELECT editedByUser FROM memory_resident_entries WHERE id = 'resident-current'"
                )
            )
            assertEquals(
                "resident-old",
                scalar(
                    db,
                    "SELECT supersedesId FROM memory_resident_entries WHERE id = 'resident-current'"
                )
            )
            assertEquals(
                (CLOCK - 500).toString(),
                scalar(db, "SELECT endedAt FROM memory_current_states WHERE id = 'state-ended'")
            )
            assertEquals(
                EXTRACTION_ARTIFACT,
                scalar(db, "SELECT artifact FROM memory_processing_jobs WHERE id = 'job-committed'")
            )
            assertEquals(
                EVENT_ARTIFACT,
                scalar(
                    db,
                    "SELECT artifact FROM memory_processing_jobs WHERE id = 'job-event-partial'"
                )
            )
            assertEquals(
                "event-current",
                scalar(
                    db,
                    "SELECT eventId FROM memory_processing_jobs WHERE id = 'job-event-partial'"
                )
            )
            assertEquals(
                "2",
                scalar(
                    db,
                    "SELECT coveredRevision FROM memory_event_members WHERE eventId = 'event-current' AND recordId = 'record-current'"
                )
            )
            assertEquals(
                "1",
                scalar(db, "SELECT engineVersion FROM memory_engine_state WHERE id = 1")
            )
            assertEquals(
                "0",
                scalar(
                    db,
                    "SELECT historyBackfilled FROM memory_processing_cursors WHERE spaceId = '$SPACE'"
                )
            )
            KNOWLEDGE_TABLES.forEach { table ->
                assertEquals(
                    "迁移不能替旧来源伪造已消费或历史证明：$table",
                    "0",
                    scalar(db, "SELECT COUNT(*) FROM $table")
                )
            }
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
        }

        openWithRoom().close()
        readDatabase().use { db -> assertEquals(original, snapshot(db)) }
    }

    @Test
    fun migrateFrom52ToLatest_addsDefaultsAndEnforcesKnowledgeReferences() {
        val original = createVersion52Database()
        openWithRoom().close()

        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { db ->
                db.setForeignKeyConstraintsEnabled(true)
                val stateColumns =
                    rows(db, "PRAGMA table_info(memory_current_states)").associateBy { it["name"] }
                listOf("editedByUser", "observedAt").forEach { column ->
                    val info = requireNotNull(stateColumns[column])
                    assertEquals("INTEGER", info["type"])
                    assertEquals("1", info["notnull"])
                    assertEquals("0", info["dflt_value"])
                }
                assertTrue(
                    rows(
                        db,
                        "SELECT editedByUser, observedAt FROM memory_current_states"
                    ).all {
                        it["editedByUser"] == "0" && it["observedAt"] == "0"
                    })
                assertIndex(
                    db,
                    "memory_knowledge_revisions",
                    "index_memory_knowledge_revisions_spaceId",
                    listOf("spaceId")
                )
                assertIndex(
                    db,
                    "memory_knowledge_sources",
                    "index_memory_knowledge_sources_sourceId",
                    listOf("sourceId")
                )
                assertIndex(
                    db,
                    "memory_current_states",
                    "index_memory_current_states_spaceId_stateKey_personaKey",
                    listOf("spaceId", "stateKey", "personaKey"),
                    unique = true,
                )
                val revisionPrimaryKey = rows(db, "PRAGMA table_info(memory_knowledge_revisions)")
                    .filter { requireNotNull(it["pk"]).toInt() > 0 }
                    .sortedBy { requireNotNull(it["pk"]).toInt() }.map { it["name"] }
                assertEquals(listOf("ownerKind", "ownerId", "revision"), revisionPrimaryKey)
                val sourcePrimaryKey = rows(db, "PRAGMA table_info(memory_knowledge_sources)")
                    .filter { requireNotNull(it["pk"]).toInt() > 0 }
                    .sortedBy { requireNotNull(it["pk"]).toInt() }.map { it["name"] }
                assertEquals(
                    listOf(
                        "ownerKind",
                        "ownerId",
                        "ownerRevision",
                        "sourceId",
                        "sourceRevision",
                        "spanStart",
                        "spanEnd"
                    ),
                    sourcePrimaryKey,
                )
                val sourceForeignKeys =
                    rows(db, "PRAGMA foreign_key_list(memory_knowledge_sources)")
                val revisionForeignKeys =
                    sourceForeignKeys.filter { it["table"] == "memory_knowledge_revisions" }
                assertEquals(
                    mapOf(
                        "ownerKind" to "ownerKind",
                        "ownerId" to "ownerId",
                        "ownerRevision" to "revision"
                    ),
                    revisionForeignKeys.associate { it["from"] to it["to"] },
                )
                assertTrue(revisionForeignKeys.all { it["on_delete"] == "CASCADE" })
                assertTrue(sourceForeignKeys.any { it["table"] == "memory_source_evidence" && it["from"] == "sourceId" && it["to"] == "id" })
                assertTrue(rows(db, "PRAGMA foreign_key_list(memory_knowledge_consumptions)").any {
                    it["table"] == "memory_processing_jobs" && it["from"] == "jobId" && it["to"] == "id"
                })

                expectConstraint { insertKnowledgeRevision(db, "missing-owner", "missing-space") }
                insertKnowledgeRevision(db, "resident-current", SPACE)
                expectConstraint { insertKnowledgeRevision(db, "resident-current", SPACE) }
                expectConstraint { insertKnowledgeSource(db, "missing-owner", "source-current") }
                expectConstraint { insertKnowledgeSource(db, "resident-current", "missing-source") }
                insertKnowledgeSource(db, "resident-current", "source-current")
                expectConstraint { insertKnowledgeSource(db, "resident-current", "source-current") }
                expectConstraint { insertKnowledgeConsumption(db, "missing-job") }
                insertKnowledgeConsumption(db, "job-committed")
                expectConstraint { insertKnowledgeConsumption(db, "job-committed") }

                db.execSQL("DELETE FROM memory_knowledge_revisions WHERE ownerKind = 'RESIDENT' AND ownerId = 'resident-current' AND revision = 3")
                assertEquals("0", scalar(db, "SELECT COUNT(*) FROM memory_knowledge_sources"))
                assertEquals(
                    "1",
                    scalar(
                        db,
                        "SELECT COUNT(*) FROM memory_source_evidence WHERE id = 'source-current'"
                    )
                )
                assertEquals("1", scalar(db, "SELECT COUNT(*) FROM memory_knowledge_consumptions"))
                assertEquals(original, snapshot(db))
                assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            }
        openWithRoom().close()
    }

    @Test
    fun migratedKnowledgeReads_keepProvenOldLinksWithoutCertifyingUnknownSources() = runBlocking {
        val original = createVersion52Database()
        val database = openWithRoom()
        try {
            val archive = RoomMemoryArchiveRepository(
                database,
                nowProvider = { CLOCK },
                isCharacterAvailable = { it == CHARACTER },
            )
            val runtime = requireNotNull(archive.getRuntimeContext(CONVERSATION, CHARACTER))
            assertEquals(MEMORY_UNKNOWN_PERSONA, runtime.personaKey)
            val recall = requireNotNull(archive.getRecallSnapshot(runtime))
            assertEquals(setOf("record-current"), recall.records.map { it.id }.toSet())
            assertEquals(setOf("resident-current"), recall.residents.map { it.id }.toSet())
            assertEquals(setOf("state-current"), recall.states.map { it.id }.toSet())
            assertTrue(recall.residents.single().sourceRefs.isNotEmpty())
            assertTrue(recall.states.single().sourceRefs.isNotEmpty())
            assertEquals(setOf("event-current"), recall.events.map { it.id }.toSet())
        } finally {
            database.close()
        }
        readDatabase().use { db ->
            assertEquals(original, snapshot(db))
            assertEquals(
                "UNKNOWN",
                scalar(db, "SELECT status FROM memory_source_evidence WHERE id = 'source-unknown'")
            )
            assertEquals(
                "MISSING",
                scalar(db, "SELECT status FROM memory_source_evidence WHERE id = 'source-missing'")
            )
            KNOWLEDGE_TABLES.forEach { table ->
                assertEquals(
                    "0",
                    scalar(db, "SELECT COUNT(*) FROM $table")
                )
            }
        }
    }

    private fun createVersion52Database(): Map<String, List<Map<String, String?>>> {
        assertEquals(52, sourceSchema.get("version").asInt)
        assertEquals("60d94346708b050227e48a6268cb4c20", sourceSchema.get("identityHash").asString)
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
            sourceSchema.getAsJsonArray("views")?.forEach { item ->
                val view = item.asJsonObject
                db.execSQL(
                    view.get("createSql").asString.replace(
                        "\${VIEW_NAME}",
                        view.get("viewName").asString
                    )
                )
            }
            sourceSchema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            seedVersion52Rows(db)
            db.version = 52
            val oldStateColumns =
                rows(db, "PRAGMA table_info(memory_current_states)").map { it["name"] }
            assertFalse("editedByUser" in oldStateColumns)
            assertFalse("observedAt" in oldStateColumns)
            KNOWLEDGE_TABLES.forEach { table ->
                assertEquals(
                    "0",
                    scalar(
                        db,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'"
                    )
                )
            }
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            snapshot(db)
        }
    }

    private fun seedVersion52Rows(db: SQLiteDatabase) {
        insert(
            db, "conversations", mapOf(
                "id" to CONVERSATION, "assistantId" to CHARACTER, "title" to "旧认知迁移合成会话",
                "createdAt" to 10L, "updatedAt" to 20L,
            )
        )
        insert(
            db, "roleplay_scenarios", mapOf(
                "id" to SCENARIO,
                "assistantId" to CHARACTER,
                "memoryPurpose" to "continuous",
                "chatType" to "single",
            )
        )
        insert(
            db, "roleplay_sessions", mapOf(
                "id" to "session-a",
                "scenarioId" to SCENARIO,
                "conversationId" to CONVERSATION,
                "runId" to RUN,
            )
        )
        insert(
            db, "memory_spaces", mapOf(
                "id" to SPACE, "identityKey" to "old-knowledge-space", "characterId" to CHARACTER,
                "purpose" to "continuous", "generation" to 7L, "status" to "ACTIVE",
                "createdAt" to 30L, "updatedAt" to 40L,
            )
        )
        insert(
            db, "memory_conversation_bindings", mapOf(
                "conversationId" to CONVERSATION, "characterId" to CHARACTER, "spaceId" to SPACE,
                "purpose" to "continuous", "scenarioId" to SCENARIO, "runId" to RUN,
                "personaKey" to MEMORY_UNKNOWN_PERSONA, "createdAt" to 50L,
            )
        )
        insert(
            db, "messages", mapOf(
                "id" to "message-current",
                "conversationId" to CONVERSATION,
                "role" to "USER",
                "content" to CURRENT_BODY,
                "status" to "COMPLETED",
                "createdAt" to 1000L,
                "speakerId" to "user",
                "memoryRevision" to 2L,
                "memoryRecallRequestId" to "old-request",
            )
        )
        RECORDS.forEach { record ->
            insert(
                db, "memory_records", mapOf(
                    "id" to record.id,
                    "spaceId" to SPACE,
                    "characterId" to CHARACTER,
                    "kind" to if (record.sourceStatus == "AVAILABLE") "FACT" else "LEGACY_UNCLASSIFIED",
                    "category" to "USER_SOCIAL",
                    "content" to record.content,
                    "revision" to record.revision,
                    "status" to "ACTIVE",
                    "importance" to 83,
                    "pinned" to 1,
                    "pinnedUntil" to CLOCK + 100_000,
                    "personaKey" to MEMORY_UNKNOWN_PERSONA,
                    "datePrecision" to "day",
                    "occurredAt" to 1000L,
                    "createdAt" to 1100L,
                    "updatedAt" to 1200L,
                )
            )
            for (revision in 1..record.revision) {
                insert(
                    db, "memory_record_revisions", mapOf(
                        "recordId" to record.id, "revision" to revision,
                        "content" to if (revision == record.revision) record.content else OLD_BODY,
                        "origin" to "MIGRATION", "createdAt" to 1100L + revision,
                    )
                )
            }
            insert(
                db, "memory_source_evidence", mapOf(
                    "id" to record.sourceId,
                    "spaceId" to SPACE,
                    "characterId" to CHARACTER,
                    "kind" to if (record.sourceStatus == "AVAILABLE") "MESSAGE" else "LEGACY_HINT",
                    "conversationId" to CONVERSATION,
                    "messageId" to if (record.sourceStatus == "AVAILABLE") "message-current" else "removed-${record.id}",
                    "messageRevision" to record.revision,
                    "speakerId" to "user",
                    "speakerRole" to "USER",
                    "personaKey" to MEMORY_UNKNOWN_PERSONA,
                    "visibleCharacterIdsJson" to "[\"$CHARACTER\"]",
                    "content" to record.content,
                    "contentLength" to record.content.length,
                    "status" to record.sourceStatus,
                    "occurredAt" to 1000L,
                    "ingestedAt" to 1100L,
                    "contentFingerprint" to MemoryArchiveIdentity.fingerprint(record.content),
                    "contentFormatVersion" to 2,
                )
            )
            insert(
                db, "memory_record_sources", mapOf(
                    "recordId" to record.id,
                    "recordRevision" to record.revision,
                    "sourceId" to record.sourceId,
                    "sourceRevision" to record.revision,
                    "spanStart" to 0,
                    "spanEnd" to record.content.length,
                )
            )
        }
        insert(
            db, "memory_source_evidence", mapOf(
                "id" to "source-old",
                "spaceId" to SPACE,
                "characterId" to CHARACTER,
                "kind" to "MESSAGE",
                "conversationId" to CONVERSATION,
                "messageId" to "message-current",
                "messageRevision" to 1L,
                "speakerId" to "user",
                "speakerRole" to "USER",
                "personaKey" to MEMORY_UNKNOWN_PERSONA,
                "visibleCharacterIdsJson" to "[\"$CHARACTER\"]",
                "content" to OLD_BODY,
                "contentLength" to OLD_BODY.length,
                "status" to "CHANGED",
                "occurredAt" to 1000L,
                "ingestedAt" to 1050L,
                "contentFingerprint" to MemoryArchiveIdentity.fingerprint(OLD_BODY),
                "contentFormatVersion" to 2,
            )
        )
        insert(
            db, "memory_record_sources", mapOf(
                "recordId" to "record-current", "recordRevision" to 1L, "sourceId" to "source-old",
                "sourceRevision" to 1L, "spanStart" to 0, "spanEnd" to OLD_BODY.length,
            )
        )
        insert(
            db, "memory_entries", mapOf(
                "id" to "legacy-memory",
                "characterId" to CHARACTER,
                "scopeType" to "assistant",
                "scopeId" to CHARACTER,
                "content" to "旧列表中未分类的合成材料",
                "importance" to 61,
                "pinned" to 1,
                "sourceMessageId" to "removed-original",
                "createdAt" to 500L,
                "updatedAt" to 600L,
            )
        )
        RESIDENTS.forEach { resident ->
            insert(
                db, "memory_resident_entries", mapOf(
                    "id" to resident.id, "spaceId" to SPACE, "characterId" to CHARACTER,
                    "category" to resident.category, "personaKey" to resident.persona,
                    "content" to "旧认知正文 ${resident.id}", "revision" to 3L,
                    "status" to if (resident.id == "resident-old") "STALE" else "READY",
                    "subjective" to if (resident.category == "RELATIONSHIP") 1 else 0,
                    "editedByUser" to if (resident.id == "resident-current") 1 else 0,
                    "supersedesId" to if (resident.id == "resident-current") "resident-old" else null,
                    "createdAt" to 2000L, "updatedAt" to 2100L,
                )
            )
            resident.recordId?.let { recordId ->
                insert(
                    db, "memory_derived_links", mapOf(
                        "ownerKind" to "RESIDENT",
                        "ownerId" to resident.id,
                        "recordId" to recordId,
                        "recordRevision" to RECORDS.single { it.id == recordId }.revision,
                    )
                )
            }
        }
        STATES.forEach { state ->
            insert(
                db, "memory_current_states", mapOf(
                    "id" to state.id,
                    "spaceId" to SPACE,
                    "characterId" to CHARACTER,
                    "stateKey" to state.key,
                    "personaKey" to state.persona,
                    "content" to "旧状态正文 ${state.id}",
                    "revision" to 4L,
                    "status" to state.status,
                    "expiresAt" to state.expiresAt,
                    "endedAt" to state.endedAt,
                    "createdAt" to 2200L,
                    "updatedAt" to 2300L,
                )
            )
            state.recordId?.let { recordId ->
                insert(
                    db, "memory_derived_links", mapOf(
                        "ownerKind" to "STATE",
                        "ownerId" to state.id,
                        "recordId" to recordId,
                        "recordRevision" to RECORDS.single { it.id == recordId }.revision,
                    )
                )
            }
        }
        insert(
            db, "memory_event_boxes", mapOf(
                "id" to "event-current",
                "spaceId" to SPACE,
                "characterId" to CHARACTER,
                "title" to "旧搬家事件",
                "summary" to "保留旧摘要：地址已更正。",
                "summaryRevision" to 5L,
                "summaryStatus" to "READY",
                "status" to "ACTIVE",
                "currentState" to "搬家已完成",
                "generation" to 4L,
                "createdAt" to 2400L,
                "updatedAt" to 2500L,
                "personaKey" to MEMORY_UNKNOWN_PERSONA,
            )
        )
        insert(
            db, "memory_event_members", mapOf(
                "eventId" to "event-current",
                "recordId" to "record-current",
                "coveredRevision" to 2L,
                "status" to "ARCHIVED",
                "addedAt" to 2450L,
            )
        )
        JOBS.forEach { job ->
            insert(
                db, "memory_processing_jobs", mapOf(
                    "id" to job.id,
                    "spaceId" to SPACE,
                    "characterId" to CHARACTER,
                    "conversationId" to CONVERSATION,
                    "scenarioId" to SCENARIO,
                    "runId" to RUN,
                    "personaKey" to MEMORY_UNKNOWN_PERSONA,
                    "generation" to 7L,
                    "idempotencyKey" to "old-${job.id}",
                    "targetRevisionsJson" to "{\"record-current\":2}",
                    "status" to job.status,
                    "artifact" to job.artifact,
                    "leaseOwner" to "old-owner",
                    "leaseUntil" to 3000L,
                    "failureCode" to if (job.requestState == "UNKNOWN") "NETWORK_UNCERTAIN" else "",
                    "createdAt" to 2800L,
                    "updatedAt" to 2900L,
                    "resultRecordIdsJson" to "{\"N0\":\"record-current\"}",
                    "cursorAfterCommit" to 1L,
                    "purpose" to job.purpose,
                    "requestJson" to "{\"frozen_source\":\"source-current\",\"title\":\"旧冻结请求\"}",
                    "requestState" to job.requestState,
                    "eventId" to if (job.purpose == "EVENT_SUMMARY") "event-current" else "",
                )
            )
            insert(
                db, "memory_job_sources", mapOf(
                    "jobId" to job.id, "sourceId" to "source-current", "sourceRevision" to 2L,
                    "spanStart" to 0, "spanEnd" to CURRENT_BODY.length,
                )
            )
        }
        insert(
            db, "memory_ingress_events", mapOf(
                "sequence" to 1L,
                "spaceId" to SPACE,
                "sourceId" to "source-current",
                "status" to "COMMITTED",
                "ingestedAt" to 1100L,
            )
        )
        insert(
            db, "memory_processing_cursors", mapOf(
                "spaceId" to SPACE,
                "committedSequence" to 1L,
                "historyBackfilled" to 0,
                "updatedAt" to 2900L,
            )
        )
        insert(
            db, "memory_recall_receipts", mapOf(
                "requestId" to "old-request",
                "spaceId" to SPACE,
                "characterId" to CHARACTER,
                "conversationId" to CONVERSATION,
                "generation" to 7L,
                "personaKey" to MEMORY_UNKNOWN_PERSONA,
                "scenarioId" to SCENARIO,
                "runId" to RUN,
                "anchorMessageId" to "message-current",
                "userMessageId" to "message-current",
                "selectedJson" to "[{\"id\":\"record-current\",\"revision\":2}]",
                "sentJson" to "[]",
                "omittedJson" to "[]",
                "payloadFingerprint" to "old-synthetic-fingerprint",
                "status" to "FAILED",
                "createdAt" to 3000L,
                "failureCode" to "CANCELLED",
                "transmissionsJson" to "[{\"sequence\":1,\"status\":\"PREPARED\"}]",
            )
        )
        insert(
            db,
            "memory_engine_state",
            mapOf("id" to 1, "engineVersion" to 1, "updatedAt" to 3100L)
        )
    }

    private fun insert(db: SQLiteDatabase, table: String, overrides: Map<String, Any?>) {
        val fields = entities.getValue(table).getAsJsonArray("fields").map { it.asJsonObject }
        assertTrue(overrides.keys.all { name -> fields.any { it.get("columnName").asString == name } })
        val values = ContentValues().apply {
            fields.forEach { field ->
                val name = field.get("columnName").asString
                val value = when {
                    overrides.containsKey(name) -> overrides[name]
                    field.get("notNull")?.asBoolean != true -> null
                    field.get("affinity").asString == "INTEGER" -> 0L
                    field.get("affinity").asString == "TEXT" -> if (name.endsWith("Json")) "[]" else ""
                    field.get("affinity").asString == "BLOB" -> byteArrayOf()
                    else -> error("旧fixture出现未定义字段类型")
                }
                when (value) {
                    null -> putNull(name)
                    is String -> put(name, value)
                    is Int -> put(name, value)
                    is Long -> put(name, value)
                    is ByteArray -> put(name, value)
                    else -> error("旧fixture出现未定义值类型")
                }
            }
        }
        db.insertOrThrow(table, null, values)
    }

    private fun insertKnowledgeRevision(db: SQLiteDatabase, ownerId: String, spaceId: String) {
        db.execSQL(
            "INSERT INTO memory_knowledge_revisions(ownerKind,ownerId,revision,spaceId,content,status,origin,subjective,expiresAt,endedAt,recordRefsJson,createdAt) VALUES ('RESIDENT',?,3,?,'派生历史正文','READY','MIGRATION',0,NULL,NULL,'[]',3200)",
            arrayOf(ownerId, spaceId),
        )
    }

    private fun insertKnowledgeSource(db: SQLiteDatabase, ownerId: String, sourceId: String) {
        db.execSQL(
            "INSERT INTO memory_knowledge_sources(ownerKind,ownerId,ownerRevision,sourceId,sourceRevision,spanStart,spanEnd) VALUES ('RESIDENT',?,3,?,2,0,?)",
            arrayOf<Any>(ownerId, sourceId, CURRENT_BODY.length),
        )
    }

    private fun insertKnowledgeConsumption(db: SQLiteDatabase, jobId: String) {
        db.execSQL(
            "INSERT INTO memory_knowledge_consumptions(jobId,residentWrites,stateWrites,skippedActions,reasonCodesJson,createdAt) VALUES (?,1,0,0,'[]',3200)",
            arrayOf(jobId),
        )
    }

    private fun assertIndex(
        db: SQLiteDatabase,
        table: String,
        name: String,
        columns: List<String>,
        unique: Boolean = false,
    ) {
        val index = rows(db, "PRAGMA index_list($table)").single { it["name"] == name }
        assertEquals(if (unique) "1" else "0", index["unique"])
        assertEquals(columns, rows(db, "PRAGMA index_info($name)").map { it["name"] })
    }

    private fun openWithRoom(): ChatDatabase {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 53)
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

    private fun expectConstraint(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            failed = true
        }
        assertTrue("必须由真实SQLite约束拒绝", failed)
    }

    private data class RecordSpec(
        val id: String,
        val sourceId: String,
        val sourceStatus: String,
        val content: String,
        val revision: Long,
    )

    private data class ResidentSpec(
        val id: String,
        val category: String,
        val recordId: String?,
        val persona: String = MEMORY_UNKNOWN_PERSONA,
    )

    private data class StateSpec(
        val id: String, val key: String, val recordId: String?,
        val persona: String = MEMORY_UNKNOWN_PERSONA, val status: String = "ACTIVE",
        val expiresAt: Long? = CLOCK + 100_000, val endedAt: Long? = null,
    )

    private data class JobSpec(
        val id: String, val status: String, val requestState: String, val artifact: String,
        val purpose: String = "AUTOMATIC_EXTRACTION",
    )

    private companion object {
        const val CLOCK = 1_800_000_000_000L
        const val CHARACTER = "a"
        const val SPACE = "space-knowledge"
        const val CONVERSATION = "conversation-knowledge"
        const val SCENARIO = "scenario-knowledge"
        const val RUN = "3b87a017-3857-4a17-8457-1c550fbbbf2a"
        const val OLD_BODY = "合成旧声明：地址为星河路128号。"
        const val CURRENT_BODY = "合成新声明：已搬到星河路182号，编号706。🍀"
        const val EXTRACTION_ARTIFACT =
            "{\"completion\":\"COMPLETED\",\"resident_candidates\":[{\"category\":\"user\",\"content\":\"旧候选完整保留\"}],\"state_updates\":[{\"state_key\":\"task\",\"operation\":\"end\"}]}"
        const val EVENT_ARTIFACT =
            "{\"pendingBatch\":1,\"completedBatches\":[{\"summary\":\"旧已完成批次🍀\"}],\"requestState\":\"STARTED\"}"
        val KNOWLEDGE_TABLES = listOf(
            "memory_knowledge_revisions",
            "memory_knowledge_sources",
            "memory_knowledge_consumptions"
        )
        val RECORDS = listOf(
            RecordSpec("record-current", "source-current", "AVAILABLE", CURRENT_BODY, 2L),
            RecordSpec(
                "record-unknown",
                "source-unknown",
                "UNKNOWN",
                "旧未知出处正文，不能猜成事实。",
                1L
            ),
            RecordSpec(
                "record-missing",
                "source-missing",
                "MISSING",
                "旧原文缺失，保留档案说明。",
                1L
            ),
        )
        val RESIDENTS = listOf(
            ResidentSpec("resident-current", "USER", "record-current"),
            ResidentSpec("resident-old", "USER", "record-current"),
            ResidentSpec("resident-unknown", "CHARACTER", "record-unknown"),
            ResidentSpec("resident-missing", "RELATIONSHIP", "record-missing"),
            ResidentSpec("resident-unlinked", "SHARED", null),
            ResidentSpec("resident-other-persona", "USER", "record-current", "persona-other"),
        )
        val STATES = listOf(
            StateSpec("state-current", "task", "record-current"),
            StateSpec("state-unknown", "mood", "record-unknown"),
            StateSpec("state-missing", "location", "record-missing"),
            StateSpec("state-unlinked", "appointment", null),
            StateSpec(
                "state-ended",
                "appointment",
                "record-current",
                "persona-other",
                "ENDED",
                CLOCK - 1000,
                CLOCK - 500
            ),
        )
        val JOBS = listOf(
            JobSpec("job-committed", "COMMITTED", "RECEIVED", EXTRACTION_ARTIFACT),
            JobSpec("job-event-partial", "EXTRACTING", "STARTED", EVENT_ARTIFACT, "EVENT_SUMMARY"),
            JobSpec("job-unknown", "FAILED", "UNKNOWN", ""),
            JobSpec("job-unrequested", "PLANNED", "NOT_STARTED", ""),
        )
    }
}
