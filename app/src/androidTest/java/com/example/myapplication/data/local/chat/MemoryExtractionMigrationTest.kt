package com.example.myapplication.data.local.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryExtractionMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "mx-${UUID.randomUUID().toString().take(8)}.db"
    private val databaseFile = context.getDatabasePath(databaseName)
    private val sourceSchema: JsonObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/49.json")
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
    fun migrateFrom49ToLatest_preservesEveryOldColumnIncludingReceivedArtifactsAndPendingApproval() {
        val original = createVersion49Database()

        openWithRoom()

        readDatabase().use { db ->
            assertEquals(original, snapshot(db))
            assertEquals(RAW_TEXT, scalar(db, "SELECT content FROM messages WHERE id = 'm1'"))
            assertEquals(
                ARTIFACT,
                scalar(db, "SELECT artifact FROM memory_processing_jobs WHERE id = 'j-extracted'")
            )
            assertEquals(
                "EXTRACTED",
                scalar(db, "SELECT status FROM memory_processing_jobs WHERE id = 'j-extracted'")
            )
            assertEquals(
                "FAILED",
                scalar(db, "SELECT status FROM memory_processing_jobs WHERE id = 'j-failed'")
            )
            assertEquals(
                "provider-timeout",
                scalar(db, "SELECT failureCode FROM memory_processing_jobs WHERE id = 'j-failed'")
            )
            assertEquals(
                "COMMITTED",
                scalar(db, "SELECT status FROM memory_processing_jobs WHERE id = 'j-committed'")
            )
            assertEquals(
                "[\"r1\"]",
                scalar(
                    db,
                    "SELECT resultRecordIdsJson FROM memory_processing_jobs WHERE id = 'j-committed'"
                )
            )
            assertEquals(
                "1",
                scalar(
                    db,
                    "SELECT committedSequence FROM memory_processing_cursors WHERE spaceId = 'space'"
                )
            )
            assertEquals("2", scalar(db, "SELECT COUNT(*) FROM memory_ingress_events"))
            val proposal = rows(db, "SELECT * FROM memory_proposals WHERE id = 'proposal'").single()
            assertEquals("PENDING", proposal["status"])
            assertEquals("待用户确认的旧建议。", proposal["content"])
            assertNull(proposal["finalRecordId"])
            assertNull(proposal["decidedAt"])
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
        }
    }

    @Test
    fun migrateFrom49ToLatest_setsCompatibilityDefaultsWithoutCertifyingOrReprocessingOldData() {
        createVersion49Database()

        openWithRoom()

        val upgraded = readDatabase().use { db ->
            rows(db, "SELECT * FROM memory_processing_jobs").forEach { job ->
                assertEquals("AUTOMATIC_EXTRACTION", job["purpose"])
                assertEquals("", job["requestJson"])
                assertEquals(
                    if (job["status"] == "PLANNED") "NOT_STARTED" else "UNKNOWN",
                    job["requestState"]
                )
            }
            rows(db, "SELECT * FROM memory_source_evidence").forEach { source ->
                assertEquals("1", source["contentFormatVersion"])
            }
            val hint = rows(db, "SELECT * FROM memory_source_evidence WHERE id = 'hint'").single()
            assertEquals("LEGACY_HINT", hint["kind"])
            assertEquals("UNKNOWN", hint["status"])
            assertNull(hint["messageId"])
            assertEquals("0", hint["messageRevision"])
            assertEquals("", hint["content"])
            assertEquals("0", hint["contentLength"])
            val proposal = rows(db, "SELECT * FROM memory_proposals WHERE id = 'proposal'").single()
            assertEquals("", proposal["reason"])
            assertEquals("0", proposal["importance"])
            assertEquals("", proposal["scenarioId"])
            assertEquals("", proposal["runId"])
            assertEquals("assistant", proposal["scopeType"])
            listOf("conversation_summaries", "conversation_summary_segments").forEach { table ->
                val summary = rows(db, "SELECT * FROM $table").single()
                assertEquals("[]", summary["sourceSnapshotJson"])
                assertEquals("unknown", summary["completionStatus"])
                assertEquals("", summary["memoryContextJson"])
                if (table == "conversation_summary_segments") assertEquals(
                    "0",
                    summary["isAggregate"]
                )
                assertTrue(requireNotNull(summary["summary"]).startsWith("旧摘要"))
            }
            assertEquals(
                listOf(
                    "conversationId",
                    "startMessageId",
                    "endMessageId",
                    "memoryContextJson",
                    "isAggregate"
                ),
                rows(
                    db,
                    "PRAGMA index_info(index_conversation_summary_segments_conversationId_startMessageId_endMessageId)"
                ).map { it["name"] })
            assertEquals(
                "1",
                scalar(db, "SELECT engineVersion FROM memory_engine_state WHERE id = 1")
            )
            assertEquals(
                "0",
                scalar(
                    db,
                    "SELECT historyBackfilled FROM memory_processing_cursors WHERE spaceId = 'space'"
                )
            )
            assertEquals(
                "0",
                scalar(db, "SELECT COUNT(*) FROM memory_ingress_events WHERE sourceId = 'hint'")
            )
            snapshot(db, includeNewColumns = true)
        }

        openWithRoom()

        readDatabase().use { db ->
            assertEquals(upgraded, snapshot(db, includeNewColumns = true))
        }
    }

    private fun createVersion49Database(): Map<String, List<Map<String, String?>>> {
        assertEquals(49, sourceSchema.get("version").asInt)
        assertEquals("42e909cd9b374f6bcb5da09cef0b6e79", sourceSchema.get("identityHash").asString)
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
            seedVersion49Rows(db)
            db.version = 49
            mapOf(
                "memory_processing_jobs" to setOf("purpose", "requestJson", "requestState"),
                "memory_source_evidence" to setOf("contentFormatVersion"),
                "memory_proposals" to setOf(
                    "reason",
                    "importance",
                    "scenarioId",
                    "runId",
                    "scopeType"
                ),
                "conversation_summaries" to setOf("sourceSnapshotJson", "completionStatus"),
                "conversation_summary_segments" to setOf("sourceSnapshotJson", "completionStatus"),
            ).forEach { (table, added) ->
                val columns =
                    rows(db, "PRAGMA table_info(`$table`)").map { requireNotNull(it["name"]) }
                        .toSet()
                assertFalse("旧库不能提前包含 $table 的 v50 列", columns.any { it in added })
            }
            assertEquals("49", scalar(db, "PRAGMA user_version"))
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
            snapshot(db)
        }
    }

    private fun seedVersion49Rows(db: SQLiteDatabase) {
        insert(
            db,
            "conversations",
            mapOf(
                "id" to "c",
                "assistantId" to "a",
                "title" to "旧原文",
                "createdAt" to 10L,
                "updatedAt" to 20L
            )
        )
        insert(
            db,
            "roleplay_scenarios",
            mapOf(
                "id" to "scenario",
                "assistantId" to "a",
                "chatType" to "single",
                "memoryPurpose" to "legacy_compat"
            )
        )
        insert(
            db,
            "roleplay_sessions",
            mapOf(
                "id" to "session",
                "scenarioId" to "scenario",
                "conversationId" to "c",
                "runId" to RUN_ID
            )
        )
        insert(
            db, "memory_spaces", mapOf(
                "id" to "space",
                "identityKey" to "legacy:61:63",
                "characterId" to "a",
                "purpose" to "legacy_compat",
                "generation" to 3L,
                "status" to "ACTIVE",
                "createdAt" to 11L,
                "updatedAt" to 21L,
            )
        )
        insert(
            db, "memory_conversation_bindings", mapOf(
                "conversationId" to "c",
                "characterId" to "a",
                "spaceId" to "space",
                "purpose" to "legacy_compat",
                "scenarioId" to "scenario",
                "runId" to RUN_ID,
                "personaKey" to "unknown",
                "createdAt" to 12L,
            )
        )
        listOf(RAW_TEXT, PENDING_TEXT).forEachIndexed { index, text ->
            val number = index + 1
            insert(
                db, "messages", mapOf(
                    "id" to "m$number",
                    "conversationId" to "c",
                    "role" to "USER",
                    "content" to text,
                    "status" to "COMPLETED",
                    "createdAt" to 100L + index,
                    "memoryRevision" to number.toLong(),
                )
            )
            insert(
                db, "memory_source_evidence", mapOf(
                    "id" to "s$number",
                    "spaceId" to "space",
                    "characterId" to "a",
                    "kind" to "MESSAGE",
                    "conversationId" to "c",
                    "messageId" to "m$number",
                    "messageRevision" to number.toLong(),
                    "speakerRole" to "USER",
                    "personaKey" to "unknown",
                    "visibleCharacterIdsJson" to "[\"a\"]",
                    "content" to text,
                    "contentLength" to text.length,
                    "status" to "AVAILABLE",
                    "occurredAt" to 100L + index,
                    "ingestedAt" to 200L + index,
                    "contentFingerprint" to fingerprint(text),
                )
            )
            insert(
                db, "memory_ingress_events", mapOf(
                    "sequence" to number.toLong(),
                    "spaceId" to "space",
                    "sourceId" to "s$number",
                    "status" to if (index == 0) "COMMITTED" else "PENDING",
                    "ingestedAt" to 200L + index,
                )
            )
        }
        insert(
            db, "memory_source_evidence", mapOf(
                "id" to "hint",
                "spaceId" to "space",
                "characterId" to "a",
                "kind" to "LEGACY_HINT",
                "conversationId" to "c",
                "personaKey" to "unknown",
                "status" to "UNKNOWN",
                "ingestedAt" to 222L,
            )
        )
        insert(
            db, "memory_records", mapOf(
                "id" to "r1",
                "spaceId" to "space",
                "characterId" to "a",
                "kind" to "EPISODE",
                "category" to "DAILY",
                "content" to "已修正的旧经历。",
                "revision" to 2L,
                "status" to "ACTIVE",
                "importance" to 73,
                "pinned" to 1,
                "personaKey" to "unknown",
                "datePrecision" to "unknown",
                "createdAt" to 301L,
                "updatedAt" to 302L,
                "lastUsedAt" to 401L,
            )
        )
        listOf("原先的旧经历。", "已修正的旧经历。").forEachIndexed { index, text ->
            insert(
                db, "memory_record_revisions", mapOf(
                    "recordId" to "r1",
                    "revision" to index + 1L,
                    "content" to text,
                    "origin" to if (index == 0) "EXTRACTION" else "CORRECTION",
                    "createdAt" to 301L + index,
                )
            )
        }
        insert(
            db, "memory_record_sources", mapOf(
                "recordId" to "r1",
                "recordRevision" to 2L,
                "sourceId" to "s1",
                "sourceRevision" to 1L,
                "spanStart" to 0,
                "spanEnd" to RAW_TEXT.length,
            )
        )
        listOf("EXTRACTED", "FAILED", "COMMITTED", "PLANNED").forEachIndexed { index, status ->
            val jobId = "j-${status.lowercase()}"
            val committed = status == "COMMITTED"
            insert(
                db, "memory_processing_jobs", mapOf(
                    "id" to jobId,
                    "spaceId" to "space",
                    "characterId" to "a",
                    "conversationId" to "c",
                    "scenarioId" to "scenario",
                    "runId" to RUN_ID,
                    "personaKey" to "unknown",
                    "generation" to 3L,
                    "idempotencyKey" to "key-$status",
                    "targetRevisionsJson" to "{\"r1\":2}",
                    "status" to status,
                    "artifact" to if (status == "FAILED" || status == "PLANNED") "" else ARTIFACT,
                    "leaseOwner" to if (status == "EXTRACTED") "old-owner" else "",
                    "leaseUntil" to 456L,
                    "failureCode" to if (status == "FAILED") "provider-timeout" else "",
                    "createdAt" to 501L + index,
                    "updatedAt" to 601L + index,
                    "resultRecordIdsJson" to if (committed) "[\"r1\"]" else "[]",
                    "cursorAfterCommit" to if (committed) 1L else 0L,
                )
            )
            insert(
                db, "memory_job_sources", mapOf(
                    "jobId" to jobId, "sourceId" to if (committed) "s1" else "s2",
                    "sourceRevision" to if (committed) 1L else 2L, "spanStart" to 0,
                    "spanEnd" to if (committed) RAW_TEXT.length else PENDING_TEXT.length,
                )
            )
        }
        insert(
            db,
            "memory_processing_cursors",
            mapOf(
                "spaceId" to "space",
                "committedSequence" to 1L,
                "historyBackfilled" to 0,
                "updatedAt" to 702L
            )
        )
        insert(
            db, "memory_proposals", mapOf(
                "id" to "proposal",
                "spaceId" to "space",
                "characterId" to "a",
                "conversationId" to "c",
                "generation" to 3L,
                "personaKey" to "unknown",
                "content" to "待用户确认的旧建议。",
                "kind" to "FACT",
                "category" to "USER_SOCIAL",
                "sourceRefsJson" to "[{\"sourceId\":\"s2\",\"sourceRevision\":2,\"start\":0,\"end\":${PENDING_TEXT.length}}]",
                "status" to "PENDING",
                "createdAt" to 801L,
            )
        )
        insert(
            db, "conversation_summaries", mapOf(
                "conversationId" to "c",
                "assistantId" to "a",
                "summary" to "旧摘要：完整保留历史。",
                "coveredMessageCount" to 1,
                "updatedAt" to 901L,
            )
        )
        insert(
            db, "conversation_summary_segments", mapOf(
                "id" to "segment",
                "conversationId" to "c",
                "assistantId" to "a",
                "startMessageId" to "m1",
                "endMessageId" to "m2",
                "startCreatedAt" to 100L,
                "endCreatedAt" to 101L,
                "messageCount" to 2,
                "summary" to "旧摘要分段：不能伪造已验证覆盖。",
                "createdAt" to 902L,
                "updatedAt" to 903L,
            )
        )
        insert(
            db,
            "memory_engine_state",
            mapOf("id" to 1, "engineVersion" to 1, "updatedAt" to 999L)
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
                    else -> error("旧 fixture 出现未定义的字段类型")
                }
                when (value) {
                    null -> putNull(column)
                    is String -> put(column, value)
                    is Int -> put(column, value)
                    is Long -> put(column, value)
                    else -> error("旧 fixture 出现未定义的值类型")
                }
            }
        }
        db.insertOrThrow(table, null, values)
    }

    private fun openWithRoom() {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 50)
        val db = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
        try {
            assertEquals(ChatDatabase.CURRENT_VERSION, db.openHelper.writableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun readDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun snapshot(
        db: SQLiteDatabase,
        includeNewColumns: Boolean = false
    ): Map<String, List<Map<String, String?>>> =
        entities.mapValues { (table, entity) ->
            val columns = if (includeNewColumns) {
                rows(db, "PRAGMA table_info(`$table`)").map { requireNotNull(it["name"]) }
            } else {
                entity.getAsJsonArray("fields").map { it.asJsonObject.get("columnName").asString }
            }
            val projection = columns.joinToString { "`$it`" }
            rows(db, "SELECT $projection FROM `$table` ORDER BY $projection")
        }

    private fun rows(db: SQLiteDatabase, sql: String): List<Map<String, String?>> =
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.columnNames.indices.associate { index ->
                        cursor.getColumnName(index) to if (cursor.isNull(index)) null else cursor.getString(
                            index
                        )
                    })
                }
            }
        }

    private fun scalar(db: SQLiteDatabase, sql: String): String =
        requireNotNull(rows(db, sql).single().values.single())

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val RUN_ID = "50a00d83-0a5b-4ff9-9077-a2269722e4c9"
        const val ARTIFACT =
            "{\"schema_version\":2,\"memories\":[],\"corrections\":[],\"state_updates\":[],\"resident_candidates\":[]}"
        const val PENDING_TEXT = "第二条尚待处理的原文。"
        val RAW_TEXT = "旧原文🍀。\n".repeat(700) + "尾部编号：98271，日期：2032年9月18日。"
    }
}
