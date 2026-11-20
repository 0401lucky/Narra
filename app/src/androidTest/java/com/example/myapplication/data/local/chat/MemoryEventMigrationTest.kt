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
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryEventMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "me-${UUID.randomUUID().toString().take(8)}.db"
    private val databaseFile = context.getDatabasePath(databaseName)
    private val sourceSchema: JsonObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/51.json")
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
    fun migrateFrom51ToLatest_preservesOldRecordsMembersSummariesAndRequestEvidence() {
        val original = createVersion51Database()

        openWithRoom().close()

        readDatabase().use { db ->
            assertEquals(original, snapshot(db))
            assertEquals(
                BOXES.size.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_event_boxes")
            )
            assertEquals(
                BOXES.sumOf { it.members.size }.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_event_members")
            )
            assertEquals(
                "旧事件摘要 good-a",
                scalar(db, "SELECT summary FROM memory_event_boxes WHERE id = 'good-a'")
            )
            assertEquals(
                "4",
                scalar(db, "SELECT summaryRevision FROM memory_event_boxes WHERE id = 'good-a'")
            )
            assertEquals(
                "2",
                scalar(
                    db,
                    "SELECT coveredRevision FROM memory_event_members WHERE eventId = 'good-a' AND recordId = 'r-a2'"
                )
            )
            assertEquals(
                ARTIFACT,
                scalar(db, "SELECT artifact FROM memory_processing_jobs WHERE id = 'job-received'")
            )
            assertEquals(
                "RECEIVED",
                scalar(
                    db,
                    "SELECT requestState FROM memory_processing_jobs WHERE id = 'job-received'"
                )
            )
            assertEquals(
                "STARTED",
                scalar(
                    db,
                    "SELECT requestState FROM memory_processing_jobs WHERE id = 'job-started'"
                )
            )
            assertEquals(
                "UNKNOWN",
                scalar(
                    db,
                    "SELECT requestState FROM memory_processing_jobs WHERE id = 'job-unknown'"
                )
            )
            assertEquals(
                "NOT_STARTED",
                scalar(
                    db,
                    "SELECT requestState FROM memory_processing_jobs WHERE id = 'job-planned'"
                )
            )
            assertEquals(
                "COMMITTED",
                scalar(db, "SELECT status FROM memory_processing_jobs WHERE id = 'job-committed'")
            )
            assertEquals(
                "2",
                scalar(
                    db,
                    "SELECT committedSequence FROM memory_processing_cursors WHERE spaceId = 'space-a'"
                )
            )
            assertEquals(
                "1",
                scalar(db, "SELECT engineVersion FROM memory_engine_state WHERE id = 1")
            )
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
        }
    }

    @Test
    fun migrateFrom51ToLatest_backfillsOnlyNonemptyConsistentOwnershipAndKeepsOldBadLinks() {
        createVersion51Database()

        openWithRoom().close()

        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { db ->
                db.setForeignKeyConstraintsEnabled(true)
                val personas = rows(db, "SELECT id, personaKey FROM memory_event_boxes").associate {
                    requireNotNull(it["id"]) to requireNotNull(it["personaKey"])
                }
                assertEquals(BOXES.associate { it.id to it.expectedPersona }, personas)
                assertEquals(PERSONA_B, personas["other-persona"])
                assertTrue(
                    rows(
                        db,
                        "SELECT eventId FROM memory_processing_jobs"
                    ).all { it["eventId"] == "" })
                val index = rows(db, "PRAGMA index_list(memory_processing_jobs)")
                    .single { it["name"] == "index_memory_processing_jobs_eventId_status" }
                assertEquals("0", index["unique"])
                assertEquals(
                    listOf("eventId", "status"),
                    rows(
                        db,
                        "PRAGMA index_info(index_memory_processing_jobs_eventId_status)"
                    ).map { it["name"] })
                val foreignKeys = rows(db, "PRAGMA foreign_key_list(memory_event_members)")
                assertTrue(foreignKeys.any { it["table"] == "memory_event_boxes" && it["from"] == "eventId" && it["to"] == "id" })
                assertTrue(foreignKeys.any { it["table"] == "memory_records" && it["from"] == "recordId" && it["to"] == "id" })
                expectConstraint {
                    db.execSQL("INSERT INTO memory_event_members(eventId,recordId,coveredRevision,status,addedAt) VALUES ('good-a','missing-record',NULL,'LIVE',1)")
                }
                expectConstraint {
                    db.execSQL("INSERT INTO memory_event_members(eventId,recordId,coveredRevision,status,addedAt) VALUES ('missing-event','r-a1',NULL,'LIVE',1)")
                }
                // 跨角色/空间是旧领域坏关联，FK仍指向真实记录；升级不删除这些审计材料。
                assertEquals(
                    "r-cross-character",
                    scalar(
                        db,
                        "SELECT recordId FROM memory_event_members WHERE eventId = 'cross-character'"
                    )
                )
                assertEquals(
                    "r-cross-space",
                    scalar(
                        db,
                        "SELECT recordId FROM memory_event_members WHERE eventId = 'cross-space'"
                    )
                )
                assertEquals(
                    "旧事件摘要 mixed",
                    scalar(db, "SELECT summary FROM memory_event_boxes WHERE id = 'mixed'")
                )
                assertEquals("unknown", personas["whitespace"])
                assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            }

        openWithRoom().close()

        readDatabase().use { db ->
            assertEquals(
                BOXES.associate { it.id to it.expectedPersona },
                rows(db, "SELECT id, personaKey FROM memory_event_boxes").associate {
                    requireNotNull(it["id"]) to requireNotNull(it["personaKey"])
                })
        }
    }

    @Test
    fun migratedEventReads_onlyExposeProvenSameRoleSpaceAndPersonaSummaries() = runBlocking {
        createVersion51Database()
        val database = openWithRoom()
        try {
            val archive = RoomMemoryArchiveRepository(
                database,
                nowProvider = { CLOCK },
                isCharacterAvailable = { it in setOf("a", "b") })
            val runtimeA = requireNotNull(archive.getRuntimeContext("ca", "a"))
            val runtimeB = requireNotNull(archive.getRuntimeContext("cb", "b"))
            assertEquals(PERSONA_A, runtimeA.personaKey)
            assertEquals(PERSONA_B, runtimeB.personaKey)
            assertEquals(setOf("good-a"), archive.listEvents(runtimeA).map { it.id }.toSet())
            val details = requireNotNull(archive.getEventDetails(runtimeA, "good-a"))
            assertEquals(setOf("r-a1", "r-a2"), details.members.map { it.record.id }.toSet())
            assertEquals("旧事件摘要 good-a", details.event.summary)
            assertTrue(details.members.all { it.sources.isNotEmpty() && it.sourceRefs.isNotEmpty() })
            val recallA = requireNotNull(archive.getRecallSnapshot(runtimeA))
            assertEquals(setOf("good-a"), recallA.events.map { it.id }.toSet())
            val recallB = requireNotNull(archive.getRecallSnapshot(runtimeB))
            assertEquals(setOf("good-b"), recallB.events.map { it.id }.toSet())
            BOXES.filter { it.id != "good-a" }.forEach { box ->
                assertNull(archive.getEventDetails(runtimeA, box.id))
            }
            assertNull(archive.getEventDetails(runtimeB, "good-a"))
            assertNotNull(archive.getEventDetails(runtimeB, "good-b"))

            // 默认未指定身份同样使用 unknown，不能因此读到未知旧盒中的其它身份或空间。
            val defaultRuntime = archive.bindConversation(
                RoleplayScenario(
                    id = "scenario-ca", assistantId = "a", memoryPurpose = MemoryPurpose.CONTINUOUS
                ),
                "ca", listOf("a"), RUN_A
            ).single()
            assertEquals(MEMORY_UNKNOWN_PERSONA, defaultRuntime.personaKey)
            assertEquals(
                setOf("unknown-member"),
                archive.listEvents(defaultRuntime).map { it.id }.toSet()
            )
            assertNotNull(archive.getEventDetails(defaultRuntime, "unknown-member"))
            val defaultRecall = requireNotNull(archive.getRecallSnapshot(defaultRuntime))
            assertEquals(setOf("unknown-member"), defaultRecall.events.map { it.id }.toSet())
            assertEquals(setOf("unknown-member"), defaultRecall.eventMemberships.keys)
            BOXES.filter { it.id != "unknown-member" }.forEach { box ->
                assertNull(archive.getEventDetails(defaultRuntime, box.id))
            }
        } finally {
            database.close()
        }
        readDatabase().use { db ->
            assertEquals(
                BOXES.size.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_event_boxes")
            )
            assertEquals(
                BOXES.sumOf { it.members.size }.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_event_members")
            )
            assertEquals(
                "旧事件摘要 cross-space",
                scalar(db, "SELECT summary FROM memory_event_boxes WHERE id = 'cross-space'")
            )
        }
    }

    private fun createVersion51Database(): Map<String, List<Map<String, String?>>> {
        assertEquals(51, sourceSchema.get("version").asInt)
        assertEquals("1d3b8d72eabc8eaf7fb239c6aa511471", sourceSchema.get("identityHash").asString)
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
            seedVersion51Rows(db)
            db.version = 51
            assertFalse(
                rows(
                    db,
                    "PRAGMA table_info(memory_event_boxes)"
                ).any { it["name"] == "personaKey" })
            assertFalse(
                rows(
                    db,
                    "PRAGMA table_info(memory_processing_jobs)"
                ).any { it["name"] == "eventId" })
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            snapshot(db)
        }
    }

    private fun seedVersion51Rows(db: SQLiteDatabase) {
        listOf(
            RuntimeSpec("ca", "a", SPACE_A, PERSONA_A, RUN_A),
            RuntimeSpec("cb", "b", SPACE_B, PERSONA_B, RUN_B),
            RuntimeSpec("co", "a", SPACE_OTHER, PERSONA_A, RUN_OTHER),
        ).forEachIndexed { index, runtime ->
            insert(
                db,
                "conversations",
                mapOf(
                    "id" to runtime.conversation,
                    "assistantId" to runtime.character,
                    "title" to "旧会话-$index",
                    "createdAt" to 10L,
                    "updatedAt" to 20L
                )
            )
            insert(
                db,
                "roleplay_scenarios",
                mapOf(
                    "id" to "scenario-${runtime.conversation}",
                    "assistantId" to runtime.character,
                    "memoryPurpose" to "continuous",
                    "chatType" to "single"
                )
            )
            insert(
                db,
                "roleplay_sessions",
                mapOf(
                    "id" to "session-${runtime.conversation}",
                    "scenarioId" to "scenario-${runtime.conversation}",
                    "conversationId" to runtime.conversation,
                    "runId" to runtime.run
                )
            )
            insert(
                db,
                "memory_spaces",
                mapOf(
                    "id" to runtime.space,
                    "identityKey" to "old-${runtime.space}",
                    "characterId" to runtime.character,
                    "purpose" to "continuous",
                    "generation" to 3L,
                    "status" to "ACTIVE",
                    "createdAt" to 30L,
                    "updatedAt" to 40L
                )
            )
            insert(
                db, "memory_conversation_bindings", mapOf(
                    "conversationId" to runtime.conversation,
                    "characterId" to runtime.character,
                    "spaceId" to runtime.space,
                    "purpose" to "continuous",
                    "scenarioId" to "scenario-${runtime.conversation}",
                    "runId" to runtime.run,
                    "personaKey" to runtime.persona,
                    "createdAt" to 50L,
                )
            )
            insert(
                db,
                "memory_processing_cursors",
                mapOf(
                    "spaceId" to runtime.space,
                    "committedSequence" to if (runtime.space == SPACE_A) 2L else 0L,
                    "historyBackfilled" to 0,
                    "updatedAt" to 60L
                )
            )
        }
        RECORDS.forEachIndexed { index, record ->
            val body = "旧经历 ${record.id}：保留完整原文与末尾细节。"
            val messageId = "message-${record.id}"
            val sourceId = sourceId(record)
            insert(
                db, "messages", mapOf(
                    "id" to messageId,
                    "conversationId" to record.conversation,
                    "role" to "USER",
                    "content" to body,
                    "status" to "COMPLETED",
                    "createdAt" to 1000L + index,
                    "speakerId" to "user",
                    "memoryRevision" to record.revision,
                )
            )
            insert(
                db, "memory_records", mapOf(
                    "id" to record.id,
                    "spaceId" to record.space,
                    "characterId" to record.character,
                    "kind" to "EPISODE",
                    "category" to "SHARED",
                    "content" to body,
                    "revision" to record.revision,
                    "status" to if (record.id == "r-a1") "ARCHIVED" else "ACTIVE",
                    "importance" to 70 + index,
                    "personaKey" to record.persona,
                    "datePrecision" to "unknown",
                    "createdAt" to 2000L + index,
                    "updatedAt" to 3000L + index,
                )
            )
            for (revision in 1..record.revision) {
                insert(
                    db, "memory_record_revisions", mapOf(
                        "recordId" to record.id, "revision" to revision,
                        "content" to if (revision == record.revision) body else "之前的旧版本 ${record.id}",
                        "origin" to "EXTRACTION", "createdAt" to 2000L + index + revision,
                    )
                )
            }
            insert(
                db, "memory_source_evidence", mapOf(
                    "id" to sourceId,
                    "spaceId" to record.space,
                    "characterId" to record.character,
                    "kind" to "MESSAGE",
                    "conversationId" to record.conversation,
                    "messageId" to messageId,
                    "messageRevision" to record.revision,
                    "speakerId" to "user",
                    "speakerRole" to "USER",
                    "personaKey" to record.persona,
                    "visibleCharacterIdsJson" to AppJson.gson.toJson(listOf(record.character)),
                    "content" to body,
                    "contentLength" to body.length,
                    "status" to "AVAILABLE",
                    "occurredAt" to 1000L + index,
                    "ingestedAt" to 2000L + index,
                    "contentFingerprint" to MemoryArchiveIdentity.fingerprint(body),
                    "contentFormatVersion" to 2,
                )
            )
            insert(
                db, "memory_record_sources", mapOf(
                    "recordId" to record.id,
                    "recordRevision" to record.revision,
                    "sourceId" to sourceId,
                    "sourceRevision" to record.revision,
                    "spanStart" to 0,
                    "spanEnd" to body.length
                )
            )
        }
        BOXES.forEachIndexed { index, box ->
            insert(
                db, "memory_event_boxes", mapOf(
                    "id" to box.id,
                    "spaceId" to box.space,
                    "characterId" to box.character,
                    "title" to "旧事件 ${box.id}",
                    "summary" to "旧事件摘要 ${box.id}",
                    "summaryRevision" to 4L,
                    "summaryStatus" to "READY",
                    "status" to "ACTIVE",
                    "currentState" to "旧阶段-${box.id}",
                    "generation" to 3L,
                    "createdAt" to 4000L + index,
                    "updatedAt" to 5000L + index,
                )
            )
            box.members.forEachIndexed { memberIndex, id ->
                val record = RECORDS.single { it.id == id }
                insert(
                    db, "memory_event_members", mapOf(
                        "eventId" to box.id,
                        "recordId" to id,
                        "coveredRevision" to record.revision,
                        "status" to "ARCHIVED",
                        "addedAt" to 6000L + index * 10 + memberIndex
                    )
                )
            }
        }
        val sourceRecord = RECORDS.first()
        JOBS.forEachIndexed { index, job ->
            insert(
                db, "memory_processing_jobs", mapOf(
                    "id" to job.id,
                    "spaceId" to SPACE_A,
                    "characterId" to "a",
                    "conversationId" to "ca",
                    "scenarioId" to "scenario-ca",
                    "runId" to RUN_A,
                    "personaKey" to PERSONA_A,
                    "generation" to 3L,
                    "idempotencyKey" to "key-${job.id}",
                    "targetRevisionsJson" to "{\"r-a1\":1,\"r-a2\":2}",
                    "status" to job.status,
                    "artifact" to if (job.requestState == "RECEIVED") ARTIFACT else "",
                    "leaseOwner" to "old-owner",
                    "leaseUntil" to 7000L + index,
                    "failureCode" to if (job.status == "FAILED") "OLD_UNCERTAIN" else "",
                    "createdAt" to 8000L + index,
                    "updatedAt" to 9000L + index,
                    "resultRecordIdsJson" to if (job.status == "COMMITTED") "[\"r-a1\"]" else "[]",
                    "cursorAfterCommit" to if (job.status == "COMMITTED") 2L else 0L,
                    "purpose" to "AUTOMATIC_EXTRACTION",
                    "requestJson" to "{\"frozen_source\":\"r-a1\",\"mentioned_event\":\"good-a\"}",
                    "requestState" to job.requestState,
                )
            )
            insert(
                db, "memory_job_sources", mapOf(
                    "jobId" to job.id,
                    "sourceId" to sourceId(sourceRecord),
                    "sourceRevision" to sourceRecord.revision,
                    "spanStart" to 0,
                    "spanEnd" to "旧经历 ${sourceRecord.id}：保留完整原文与末尾细节。".length
                )
            )
        }
        listOf("r-a1", "r-a2").forEachIndexed { index, id ->
            insert(
                db, "memory_ingress_events", mapOf(
                    "sequence" to index + 1L,
                    "spaceId" to SPACE_A,
                    "sourceId" to sourceId(RECORDS.single { it.id == id }),
                    "status" to "COMMITTED",
                    "ingestedAt" to 2000L + index
                )
            )
        }
        insert(
            db,
            "memory_engine_state",
            mapOf("id" to 1, "engineVersion" to 1, "updatedAt" to 10000L)
        )
    }

    private fun sourceId(record: RecordSpec): String = MemoryArchiveIdentity.id(
        "source",
        record.space,
        "message-${record.id}",
        record.revision.toString()
    )

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

    private fun openWithRoom(): ChatDatabase {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 52)
        val db = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
        try {
            assertEquals(ChatDatabase.CURRENT_VERSION, db.openHelper.writableDatabase.version)
            return db
        } catch (failure: Throwable) {
            db.close()
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
        assertTrue("必须由真实SQLite外键拒绝", failed)
    }

    private data class RuntimeSpec(
        val conversation: String,
        val character: String,
        val space: String,
        val persona: String,
        val run: String
    )

    private data class RecordSpec(
        val id: String,
        val character: String,
        val space: String,
        val persona: String,
        val conversation: String,
        val revision: Long = 1L
    )

    private data class BoxSpec(
        val id: String,
        val members: List<String>,
        val expectedPersona: String = "unknown",
        val character: String = "a",
        val space: String = SPACE_A
    )

    private data class JobSpec(val id: String, val status: String, val requestState: String)

    private companion object {
        const val CLOCK = 1_800_000_000_000L
        const val SPACE_A = "space-a"
        const val SPACE_B = "space-b"
        const val SPACE_OTHER = "space-other"
        const val PERSONA_A = "persona-a"
        const val PERSONA_B = "persona-b"
        const val RUN_A = "f9dc6057-3480-4d8b-91ee-02f11c4d48df"
        const val RUN_B = "5610d550-0ee0-4265-a3a3-8e0ad3e274a1"
        const val RUN_OTHER = "b6ac245d-1a96-4b4b-a7f1-df5b82ba691e"
        const val ARTIFACT =
            "{\"version\":1,\"completion\":{\"content\":\"此前已收到good-a相关正文\",\"status\":\"COMPLETED\"}}"
        val RECORDS = listOf(
            RecordSpec("r-a1", "a", SPACE_A, PERSONA_A, "ca"),
            RecordSpec("r-a2", "a", SPACE_A, PERSONA_A, "ca", revision = 2L),
            RecordSpec("r-other-persona", "a", SPACE_A, PERSONA_B, "ca"),
            RecordSpec("r-unknown", "a", SPACE_A, "unknown", "ca"),
            RecordSpec("r-blank", "a", SPACE_A, "", "ca"),
            RecordSpec("r-whitespace", "a", SPACE_A, " \t\n", "ca"),
            RecordSpec("r-cross-character", "b", SPACE_A, PERSONA_A, "cb"),
            RecordSpec("r-cross-space", "a", SPACE_OTHER, PERSONA_A, "co"),
            RecordSpec("r-b1", "b", SPACE_B, PERSONA_B, "cb"),
            RecordSpec("r-b2", "b", SPACE_B, PERSONA_B, "cb"),
        )
        val BOXES = listOf(
            BoxSpec("good-a", listOf("r-a1", "r-a2"), PERSONA_A),
            BoxSpec("good-b", listOf("r-b1", "r-b2"), PERSONA_B, "b", SPACE_B),
            BoxSpec("other-persona", listOf("r-other-persona"), PERSONA_B),
            BoxSpec("empty", emptyList()),
            BoxSpec("mixed", listOf("r-a1", "r-other-persona")),
            BoxSpec("unknown-member", listOf("r-unknown")),
            BoxSpec("blank", listOf("r-blank")),
            BoxSpec("whitespace", listOf("r-whitespace")),
            BoxSpec("cross-character", listOf("r-cross-character")),
            BoxSpec("cross-space", listOf("r-cross-space")),
        )
        val JOBS = listOf(
            JobSpec("job-planned", "PLANNED", "NOT_STARTED"),
            JobSpec("job-received", "EXTRACTED", "RECEIVED"),
            JobSpec("job-started", "FAILED", "STARTED"),
            JobSpec("job-unknown", "FAILED", "UNKNOWN"),
            JobSpec("job-committed", "COMMITTED", "RECEIVED"),
        )
    }
}
