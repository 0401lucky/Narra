package com.example.myapplication.data.local.chat

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CompanionFoundationMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "c1-migration-" + UUID.randomUUID().toString().take(8) + ".db"
    private val sourceSchema: JsonObject by lazy {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(SOURCE_SCHEMA_ASSET).use { it.readBytes() }
        assertEquals(SOURCE_SCHEMA_SHA256, bytes.sha256())
        AppJson.gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
            .getAsJsonObject("database")
            .also {
                assertEquals(54, it.get("version").asInt)
                assertEquals(SOURCE_IDENTITY_HASH, it.get("identityHash").asString)
            }
    }
    private val sourceEntities: Map<String, JsonObject> by lazy {
        sourceSchema.getAsJsonArray("entities").associate { element ->
            val entity = element.asJsonObject
            entity.get("tableName").asString to entity
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun fixed54ToLatest_preservesMessagesAndMemoryGraphWithoutInventingEncounters() {
        val before = createLegacyDatabase()

        withDatabase { database ->
            val sql = database.openHelper.writableDatabase
            assertEquals(before, snapshots(sql::query))
            assertEquals(
                listOf("", "", ""),
                rows(sql.query("SELECT companionTurnId FROM messages ORDER BY id"))
                    .map { it.getValue("companionTurnId") },
            )
            assertTrue(
                rows(sql.query("SELECT companionMemoryState FROM messages"))
                    .all { it["companionMemoryState"] == "legacy" },
            )
            assertEquals(
                LONG_RAW_TEXT,
                scalar(sql, "SELECT content FROM messages WHERE id = '$USER_ID'")
            )
            assertEquals(
                "7",
                scalar(sql, "SELECT memoryRevision FROM messages WHERE id = '$USER_ID'")
            )
            assertEquals(
                "STARTED",
                scalar(sql, "SELECT requestState FROM memory_processing_jobs WHERE id = 'paid-job'")
            )
            assertEquals(
                "saved-paid-output",
                scalar(sql, "SELECT artifact FROM memory_processing_jobs WHERE id = 'paid-job'")
            )
            assertEquals("1", scalar(sql, "SELECT userPaused FROM memory_processing_controls"))
            assertEquals(
                """{"note":"source-snapshot-kept"}""",
                scalar(sql, "SELECT originJson FROM memory_transfer_origins")
            )
            assertEquals(
                """{"result":"plan-receipt-kept"}""",
                scalar(sql, "SELECT resultJson FROM memory_transfer_receipts")
            )
            COMPANION_TABLES.forEach { table ->
                assertEquals(
                    "0",
                    scalar(sql, "SELECT COUNT(*) FROM $table")
                )
            }
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }

        withDatabase { database ->
            assertEquals(before, snapshots(database.openHelper.writableDatabase::query))
        }
    }

    @Test
    fun fixed54ToLatest_enforcesUniqueSlotsAndForeignKeysWhileAllowingNullReservations() {
        createLegacyDatabase()

        withDatabase { database ->
            val sql = database.openHelper.writableDatabase
            assertEquals("1", scalar(sql, "PRAGMA foreign_keys"))
            insert(
                sql,
                "companion_encounters",
                encounter("active", activeKey = "run-a:character-a:mask-a")
            )
            assertConstraint {
                insert(
                    sql,
                    "companion_encounters",
                    encounter("duplicate-active", activeKey = "run-a:character-a:mask-a")
                )
            }
            insert(sql, "companion_encounters", encounter("draft-one"))
            insert(sql, "companion_encounters", encounter("draft-two"))
            assertConstraint {
                insert(
                    sql,
                    "companion_encounters",
                    encounter("orphan", conversationId = "missing-conversation")
                )
            }
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM companion_encounters"))
            assertEquals(
                RUN_ID,
                scalar(sql, "SELECT runId FROM companion_encounters WHERE id = 'active'")
            )
            assertEquals(
                PERSONA_KEY,
                scalar(sql, "SELECT personaKey FROM companion_encounters WHERE id = 'active'")
            )
            assertEquals(
                "3",
                scalar(sql, "SELECT generation FROM companion_encounters WHERE id = 'active'")
            )

            insert(sql, "companion_turns", turn("turn-one", "active", 1, USER_ID, REPLY_ID))
            assertConstraint {
                insert(
                    sql,
                    "companion_turns",
                    turn("duplicate-order", "active", 1)
                )
            }
            assertConstraint {
                insert(
                    sql,
                    "companion_turns",
                    turn("duplicate-input", "active", 2, USER_ID)
                )
            }
            assertConstraint {
                insert(
                    sql,
                    "companion_turns",
                    turn("duplicate-reply", "active", 2, replyMessageId = REPLY_ID)
                )
            }
            insert(sql, "companion_turns", turn("null-one", "active", 2))
            insert(sql, "companion_turns", turn("null-two", "active", 3))
            assertConstraint {
                insert(
                    sql,
                    "companion_turns",
                    turn("orphan-turn", "missing-encounter", 1)
                )
            }
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM companion_turns"))

            insert(sql, "companion_attempts", attempt("attempt-one", "turn-one"))
            assertConstraint {
                insert(
                    sql,
                    "companion_attempts",
                    attempt("orphan-attempt", "missing-turn")
                )
            }
            assertConstraint {
                insert(
                    sql,
                    "companion_attempts",
                    attempt("attempt-one", "turn-one")
                )
            }
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM companion_attempts"))
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test
    fun fixed54ToLatest_cascadesOnlyOwnedAttemptTreeAndKeepsUnrelatedHistory() {
        createLegacyDatabase()

        withDatabase { database ->
            val sql = database.openHelper.writableDatabase
            insert(sql, "companion_encounters", encounter("encounter-a"))
            insert(sql, "companion_turns", turn("turn-a", "encounter-a", 1))
            insert(sql, "companion_attempts", attempt("attempt-a", "turn-a"))
            sql.execSQL("DELETE FROM companion_turns WHERE id = 'turn-a'")
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM companion_attempts"))
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM companion_encounters"))

            insert(sql, "companion_turns", turn("turn-a-next", "encounter-a", 2))
            insert(sql, "companion_attempts", attempt("attempt-a-next", "turn-a-next"))
            sql.execSQL("DELETE FROM companion_encounters WHERE id = 'encounter-a'")
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM companion_turns"))
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM companion_attempts"))
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM messages"))

            insert(
                sql,
                "companion_encounters",
                encounter("encounter-b", conversationId = OTHER_CONVERSATION_ID)
            )
            insert(sql, "companion_turns", turn("turn-b", "encounter-b", 1))
            insert(sql, "companion_attempts", attempt("attempt-b", "turn-b"))
            sql.execSQL("DELETE FROM conversations WHERE id = '$OTHER_CONVERSATION_ID'")
            COMPANION_TABLES.forEach { table ->
                assertEquals(
                    "0",
                    scalar(sql, "SELECT COUNT(*) FROM $table")
                )
            }
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM messages"))
            assertEquals(
                LONG_RAW_TEXT,
                scalar(sql, "SELECT content FROM messages WHERE id = '$USER_ID'")
            )
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM memory_records"))
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM memory_record_sources"))
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test
    fun fixed54ToLatest_rollsBackWholeAttemptTreeWhenAConstraintFails() {
        val before = createLegacyDatabase()

        withDatabase { database ->
            val sql = database.openHelper.writableDatabase
            assertConstraint {
                database.runInTransaction(Runnable {
                    insert(
                        sql,
                        "companion_encounters",
                        encounter("transaction", activeKey = "exclusive")
                    )
                    insert(sql, "companion_turns", turn("transaction-turn", "transaction", 1))
                    insert(
                        sql,
                        "companion_attempts",
                        attempt("transaction-attempt", "transaction-turn")
                    )
                    insert(
                        sql,
                        "companion_encounters",
                        encounter("conflict", activeKey = "exclusive")
                    )
                })
            }
            COMPANION_TABLES.forEach { table ->
                assertEquals(
                    "0",
                    scalar(sql, "SELECT COUNT(*) FROM $table")
                )
            }
            assertEquals(before, snapshots(sql::query))
        }
    }

    private fun createLegacyDatabase(): Map<String, List<Map<String, String?>>> {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            sql.setForeignKeyConstraintsEnabled(true)
            sourceEntities.forEach { (table, entity) ->
                sql.execSQL(entity.get("createSql").asString.replace("$" + "{TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    sql.execSQL(
                        index.asJsonObject.get("createSql").asString.replace(
                            "$" + "{TABLE_NAME}",
                            table
                        )
                    )
                }
            }
            sourceSchema.getAsJsonArray("views")?.forEach { item ->
                val view = item.asJsonObject
                sql.execSQL(
                    view.get("createSql").asString.replace(
                        "$" + "{VIEW_NAME}",
                        view.get("viewName").asString
                    )
                )
            }
            sourceSchema.getAsJsonArray("setupQueries").forEach { sql.execSQL(it.asString) }
            seedLegacyData(sql)
            sql.version = 54
            assertEquals(
                SOURCE_IDENTITY_HASH,
                rows(
                    sql.rawQuery(
                        "SELECT identity_hash FROM room_master_table WHERE id = 42",
                        null
                    )
                ).single()["identity_hash"]
            )
            COMPANION_TABLES.forEach { table ->
                sql.rawQuery("SELECT name FROM sqlite_master WHERE name = ?", arrayOf(table)).use {
                    assertFalse(it.moveToFirst())
                }
            }
            sql.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
            snapshots { query -> sql.rawQuery(query, null) }
        }
    }

    private fun seedLegacyData(sql: SQLiteDatabase) {
        listOf(CONVERSATION_ID, OTHER_CONVERSATION_ID).forEachIndexed { index, id ->
            legacyRow(
                sql, "conversations", mapOf(
                    "id" to id,
                    "title" to "迁移原文-$index",
                    "assistantId" to CHARACTER_ID,
                    "model" to "synthetic-model",
                    "createdAt" to 101L + index,
                    "updatedAt" to 201L + index,
                )
            )
        }
        listOf(CONVERSATION_ID, OTHER_CONVERSATION_ID).forEachIndexed { index, conversationId ->
            val scenarioId = if (index == 0) SCENARIO_ID else OTHER_SCENARIO_ID
            val runId = if (index == 0) RUN_ID else OTHER_RUN_ID
            legacyRow(
                sql, "roleplay_scenarios", mapOf(
                    "id" to scenarioId,
                    "title" to "旧日常空间-$index",
                    "assistantId" to CHARACTER_ID,
                    "interactionMode" to "offline_dialogue",
                    "memoryPurpose" to "continuous",
                    "userPersonaMaskId" to "mask-a",
                    "chatType" to "single",
                    "openingNarration" to "原开场不重写",
                    "createdAt" to 301L + index,
                    "updatedAt" to 302L + index,
                )
            )
            legacyRow(
                sql, "roleplay_sessions", mapOf(
                    "id" to "session-$index",
                    "scenarioId" to scenarioId,
                    "conversationId" to conversationId,
                    "runId" to runId,
                    "createdAt" to 303L + index,
                    "updatedAt" to 304L + index,
                )
            )
        }
        listOf(
            Triple(USER_ID, "USER", LONG_RAW_TEXT),
            Triple(REPLY_ID, "ASSISTANT", "<dialogue emotion=\"平静\">保留原答。</dialogue>"),
            Triple("other-pending", "ASSISTANT", "旧未完成片段"),
        ).forEachIndexed { index, (id, role, content) ->
            legacyRow(
                sql, "messages", mapOf(
                    "id" to id,
                    "conversationId" to if (index == 2) OTHER_CONVERSATION_ID else CONVERSATION_ID,
                    "role" to role,
                    "content" to content,
                    "status" to if (index == 2) "LOADING" else "COMPLETED",
                    "createdAt" to 401L + index,
                    "modelName" to "synthetic-model",
                    "speakerId" to if (role == "ASSISTANT") CHARACTER_ID else "",
                    "speakerName" to if (role == "ASSISTANT") "旧角色" else "旧用户",
                    "reasoningContent" to "独立推理保留-$index",
                    "partsJson" to """[{"type":"text","text":"完整部件-$index"}]""",
                    "replyToMessageId" to if (index == 1) USER_ID else "",
                    "replyToPreview" to "旧引用-$index",
                    "isRecalled" to if (index == 2) 1 else 0,
                    "roleplayOutputFormat" to if (index == 1) "PROTOCOL" else "PLAIN",
                    "roleplayInteractionMode" to if (index == 2) "online_phone" else "offline_dialogue",
                    "memoryRevision" to 7L + index,
                    "memoryRecallRequestId" to "recall-$index",
                )
            )
        }
        legacyRow(
            sql, "memory_spaces", mapOf(
                "id" to SPACE_ID,
                "identityKey" to "synthetic-space-key",
                "characterId" to CHARACTER_ID,
                "purpose" to "continuous",
                "generation" to 3L,
                "status" to "ACTIVE",
                "createdAt" to 501L,
                "updatedAt" to 502L,
            )
        )
        listOf(CONVERSATION_ID, OTHER_CONVERSATION_ID).forEachIndexed { index, conversationId ->
            legacyRow(
                sql, "memory_conversation_bindings", mapOf(
                    "conversationId" to conversationId,
                    "characterId" to CHARACTER_ID,
                    "spaceId" to SPACE_ID,
                    "purpose" to "continuous",
                    "scenarioId" to if (index == 0) SCENARIO_ID else OTHER_SCENARIO_ID,
                    "runId" to if (index == 0) RUN_ID else OTHER_RUN_ID,
                    "personaKey" to PERSONA_KEY,
                    "createdAt" to 503L + index,
                )
            )
        }
        legacyRow(
            sql, "memory_source_evidence", mapOf(
                "id" to "source-a",
                "spaceId" to SPACE_ID,
                "characterId" to CHARACTER_ID,
                "kind" to "MESSAGE",
                "conversationId" to CONVERSATION_ID,
                "messageId" to USER_ID,
                "messageRevision" to 7L,
                "speakerRole" to "USER",
                "personaKey" to PERSONA_KEY,
                "visibleCharacterIdsJson" to """["character-a"]""",
                "content" to LONG_RAW_TEXT,
                "contentLength" to LONG_RAW_TEXT.length,
                "status" to "AVAILABLE",
                "occurredAt" to 401L,
                "ingestedAt" to 601L,
                "contentFingerprint" to LONG_RAW_TEXT.toByteArray(Charsets.UTF_8).sha256(),
                "contentFormatVersion" to 2,
            )
        )
        legacyRow(
            sql, "memory_records", mapOf(
                "id" to "record-a",
                "spaceId" to SPACE_ID,
                "characterId" to CHARACTER_ID,
                "kind" to "EXPERIENCE",
                "category" to "event",
                "content" to "当前已修订经历",
                "revision" to 2L,
                "status" to "ACTIVE",
                "importance" to 83,
                "pinned" to 1,
                "occurredAt" to 401L,
                "personaKey" to PERSONA_KEY,
                "createdAt" to 602L,
                "updatedAt" to 603L,
                "lastUsedAt" to 604L,
            )
        )
        (1L..2L).forEach { revision ->
            legacyRow(
                sql, "memory_record_revisions", mapOf(
                    "recordId" to "record-a", "revision" to revision,
                    "content" to if (revision == 1L) "旧版经历" else "当前已修订经历",
                    "origin" to "EXTRACTION", "createdAt" to 602L + revision,
                )
            )
            legacyRow(
                sql, "memory_record_sources", mapOf(
                    "recordId" to "record-a",
                    "recordRevision" to revision,
                    "sourceId" to "source-a",
                    "sourceRevision" to 7L,
                    "spanEnd" to LONG_RAW_TEXT.length,
                )
            )
        }
        legacyRow(
            sql, "memory_processing_jobs", mapOf(
                "id" to "paid-job",
                "spaceId" to SPACE_ID,
                "characterId" to CHARACTER_ID,
                "conversationId" to CONVERSATION_ID,
                "scenarioId" to SCENARIO_ID,
                "runId" to RUN_ID,
                "personaKey" to PERSONA_KEY,
                "generation" to 3L,
                "idempotencyKey" to "paid-once",
                "targetRevisionsJson" to """{"record-a":2}""",
                "status" to "EXTRACTED",
                "artifact" to "saved-paid-output",
                "requestState" to "STARTED",
                "requestJson" to """{"prompt":"frozen-request"}""",
                "purpose" to "AUTOMATIC_EXTRACTION",
                "leaseOwner" to "previous-owner",
                "leaseUntil" to 990L,
                "createdAt" to 701L,
                "updatedAt" to 702L,
                "controlJobId" to "paid-job",
            )
        )
        legacyRow(
            sql, "memory_job_sources", mapOf(
                "jobId" to "paid-job", "sourceId" to "source-a", "sourceRevision" to 7L,
                "spanEnd" to LONG_RAW_TEXT.length,
            )
        )
        legacyRow(
            sql, "memory_processing_controls", mapOf(
                "spaceId" to SPACE_ID,
                "jobId" to "paid-job",
                "userPaused" to 1,
                "controlVersion" to 9L,
                "updatedAt" to 703L,
            )
        )
        legacyRow(
            sql, "memory_engine_state", mapOf(
                "id" to 1, "engineVersion" to 1, "activationVersion" to 1, "updatedAt" to 704L,
            )
        )
        legacyRow(
            sql, "memory_event_boxes", mapOf(
                "id" to "event-a",
                "spaceId" to SPACE_ID,
                "characterId" to CHARACTER_ID,
                "title" to "旧事件盒",
                "summary" to "已保存前情",
                "summaryRevision" to 2L,
                "summaryStatus" to "CURRENT",
                "status" to "ACTIVE",
                "currentState" to "进行中",
                "generation" to 3L,
                "personaKey" to PERSONA_KEY,
                "createdAt" to 801L,
                "updatedAt" to 802L,
            )
        )
        legacyRow(
            sql, "memory_event_members", mapOf(
                "eventId" to "event-a", "recordId" to "record-a", "coveredRevision" to 2L,
                "status" to "ARCHIVED", "addedAt" to 803L,
            )
        )
        legacyRow(
            sql, "memory_resident_entries", mapOf(
                "id" to "resident-a",
                "spaceId" to SPACE_ID,
                "characterId" to CHARACTER_ID,
                "category" to "relationship",
                "personaKey" to PERSONA_KEY,
                "content" to "用户独立编辑的认知",
                "revision" to 4L,
                "status" to "ACTIVE",
                "subjective" to 1,
                "editedByUser" to 1,
                "createdAt" to 901L,
                "updatedAt" to 902L,
            )
        )
        legacyRow(
            sql, "memory_knowledge_revisions", mapOf(
                "ownerKind" to "RESIDENT",
                "ownerId" to "resident-a",
                "revision" to 4L,
                "spaceId" to SPACE_ID,
                "content" to "用户独立编辑的认知",
                "status" to "ACTIVE",
                "origin" to "MANUAL",
                "subjective" to 1,
                "recordRefsJson" to """[{"id":"record-a","revision":2}]""",
                "createdAt" to 902L,
            )
        )
        legacyRow(
            sql, "memory_knowledge_sources", mapOf(
                "ownerKind" to "RESIDENT", "ownerId" to "resident-a", "ownerRevision" to 4L,
                "sourceId" to "source-a", "sourceRevision" to 7L, "spanEnd" to LONG_RAW_TEXT.length,
            )
        )
        legacyRow(
            sql, "memory_transfer_origins", mapOf(
                "id" to "origin-a",
                "spaceId" to SPACE_ID,
                "kind" to "SOURCE",
                "sourceId" to "source-a",
                "localRevision" to 7L,
                "originJson" to """{"note":"source-snapshot-kept"}""",
            )
        )
        legacyRow(
            sql, "memory_transfer_receipts", mapOf(
                "localPlanId" to "plan-a",
                "bundleFingerprint" to "bundle-a",
                "targetsFingerprint" to "targets-a",
                "resultJson" to """{"result":"plan-receipt-kept"}""",
                "createdAt" to 903L,
            )
        )
    }

    private fun legacyRow(sql: SQLiteDatabase, table: String, overrides: Map<String, Any?>) {
        val fields = sourceEntities.getValue(table).getAsJsonArray("fields").map { it.asJsonObject }
        assertTrue(overrides.keys.all { key -> fields.any { it.get("columnName").asString == key } })
        val values = ContentValues()
        fields.forEach { field ->
            val column = field.get("columnName").asString
            val value = when {
                overrides.containsKey(column) -> overrides[column]
                field.get("notNull")?.asBoolean != true -> null
                field.get("affinity").asString == "INTEGER" -> 0L
                field.get("affinity").asString == "TEXT" -> if (column.endsWith("Json")) "[]" else ""
                else -> error("旧迁移夹具没有定义列类型：" + field.get("affinity").asString)
            }
            when (value) {
                null -> values.putNull(column)
                is String -> values.put(column, value)
                is Int -> values.put(column, value)
                is Long -> values.put(column, value)
                else -> error("旧迁移夹具没有定义值类型")
            }
        }
        assertNotEquals(-1L, sql.insertOrThrow(table, null, values))
    }

    private fun encounter(
        id: String,
        conversationId: String = CONVERSATION_ID,
        activeKey: String? = null,
    ): ContentValues = ContentValues().apply {
        put("id", id)
        put("conversationId", conversationId)
        put(
            "scenarioId",
            if (conversationId == OTHER_CONVERSATION_ID) OTHER_SCENARIO_ID else SCENARIO_ID
        )
        put("runId", if (conversationId == OTHER_CONVERSATION_ID) OTHER_RUN_ID else RUN_ID)
        put("spaceId", SPACE_ID)
        put("characterId", CHARACTER_ID)
        put("personaKey", PERSONA_KEY)
        put("generation", 3L)
        put("openingMode", "DIRECT")
        put("state", if (activeKey == null) "DRAFT" else "ACTIVE")
        put("revision", 1L)
        put("activeKey", activeKey)
        put("createdAt", 1_001L)
        putNull("endedAt")
    }

    private fun turn(
        id: String,
        encounterId: String,
        ordinal: Long,
        inputMessageId: String? = null,
        replyMessageId: String? = null,
    ): ContentValues = ContentValues().apply {
        put("id", id)
        put("encounterId", encounterId)
        put("ordinal", ordinal)
        put("kind", "INPUT")
        put("inputMessageId", inputMessageId)
        put("replyMessageId", replyMessageId)
        if (inputMessageId == null) putNull("inputRevision") else put("inputRevision", 7L)
        if (replyMessageId == null) putNull("replyRevision") else put("replyRevision", 8L)
        put("mutationRevision", 1L)
        put("committedRevision", 0L)
        putNull("selectedAttemptId")
        putNull("activeAttemptId")
        put("createdAt", 1_002L)
        putNull("editedAt")
    }

    private fun attempt(id: String, turnId: String): ContentValues = ContentValues().apply {
        put("id", id)
        put("turnId", turnId)
        put("expectedTurnRevision", 1L)
        putNull("expectedInputRevision")
        putNull("expectedReplyRevision")
        put("frozenRuntimeJson", "{}")
        put("requestDescriptorJson", "{}")
        put("requestFingerprint", "synthetic-request")
        put("state", "NOT_STARTED")
        putNull("claimToken")
        putNull("leaseUntil")
        putNull("requestedAt")
        putNull("receivedAt")
        putNull("artifactJson")
        putNull("artifactHash")
    }

    private fun insert(sql: SupportSQLiteDatabase, table: String, values: ContentValues) {
        assertNotEquals(-1L, sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values))
    }

    private fun assertConstraint(block: () -> Unit) {
        try {
            block()
            fail("数据库应拒绝违反唯一键或外键的写入")
        } catch (_: SQLiteConstraintException) {
            // 只接受数据库约束失败，其它错误不能替代这个断言。
        }
    }

    private fun withDatabase(block: (ChatDatabase) -> Unit) {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 55)
        val database = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(ChatDatabase.CURRENT_VERSION, database.openHelper.writableDatabase.version)
            block(database)
        } finally {
            database.close()
        }
    }

    private fun snapshots(query: (String) -> Cursor): Map<String, List<Map<String, String?>>> =
        sourceEntities.mapValues { (table, entity) ->
            val columns = entity.getAsJsonArray("fields")
                .joinToString { "\"" + it.asJsonObject.get("columnName").asString + "\"" }
            rows(query("SELECT $columns FROM \"$table\" ORDER BY $columns"))
        }

    private fun rows(cursor: Cursor): List<Map<String, String?>> = cursor.use {
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

    private fun scalar(sql: SupportSQLiteDatabase, query: String): String =
        requireNotNull(rows(sql.query(query)).single().values.single())

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val SOURCE_SCHEMA_ASSET =
            "com.example.myapplication.data.local.chat.ChatDatabase/54.json"
        const val SOURCE_SCHEMA_SHA256 =
            "c6be6663d9fbaba9cb1403bdf2551f01be24352f0230449bc7e563c3cf069ad1"
        const val SOURCE_IDENTITY_HASH = "e9527aaf628fa664ca1c1f4a92af1b52"
        const val CONVERSATION_ID = "conversation-a"
        const val OTHER_CONVERSATION_ID = "conversation-b"
        const val SCENARIO_ID = "scenario-a"
        const val OTHER_SCENARIO_ID = "scenario-b"
        const val RUN_ID = "run-a"
        const val OTHER_RUN_ID = "run-b"
        const val SPACE_ID = "space-a"
        const val CHARACTER_ID = "character-a"
        const val PERSONA_KEY = "mask:mask-a"
        const val USER_ID = "message-user"
        const val REPLY_ID = "message-reply"
        val LONG_RAW_TEXT = "旧原文完整保留🍀。\n".repeat(400) + "尾部约定：2032年8月17日，编号72931。"
        val COMPANION_TABLES =
            listOf("companion_encounters", "companion_turns", "companion_attempts")
    }
}
