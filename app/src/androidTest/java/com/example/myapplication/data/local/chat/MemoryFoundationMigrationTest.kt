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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryFoundationMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "memory-foundation-migration-${UUID.randomUUID()}.db"
    private val databaseFile = context.getDatabasePath(databaseName)
    private val sourceSchema: JsonObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open(SOURCE_SCHEMA_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { reader ->
                AppJson.gson.fromJson(reader, JsonObject::class.java).getAsJsonObject("database")
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
    fun migrateFrom48ToLatest_preservesEveryLegacyFieldAndCreatesCompatibleRecordRevisions() {
        val before = createLegacyDatabase()

        openWithRoom()

        openReadableDatabase().use { db ->
            before.forEach { snapshot ->
                assertEquals(snapshot.table, snapshot.rows, readSnapshot(db, snapshot.table).rows)
            }
            val legacy =
                before.single { it.table == "memory_entries" }.rows.associateBy { it["id"] }
            val records =
                queryRows(db, "SELECT * FROM memory_records ORDER BY id").associateBy { it["id"] }
            assertEquals(legacy.keys, records.keys)
            LEGACY_MEMORIES.forEach { fixture ->
                val old = legacy.getValue(fixture.id)
                val record = records.getValue(fixture.id)
                assertEquals(fixture.owner, record["characterId"])
                assertEquals("LEGACY_UNCLASSIFIED", record["kind"])
                assertEquals("ACTIVE", record["status"])
                assertEquals("1", record["revision"])
                assertEquals("unknown", record["personaKey"])
                listOf("content", "importance", "pinned", "createdAt", "updatedAt", "lastUsedAt")
                    .forEach { field ->
                        assertEquals(
                            "${fixture.id}.$field",
                            old[field],
                            record[field]
                        )
                    }
                assertEquals(old["scopeType"], record["legacyScopeType"])
                assertEquals(old["scopeId"], record["legacyScopeId"])
                assertEquals(old["sourceMessageId"], record["legacySourceMessageId"])
                assertNull(record["jobId"])
                assertNull(record["localItemId"])
                assertNull(record["pinnedUntil"])
                assertNull(record["occurredAt"])
                assertEquals("unknown", record["datePrecision"])

                val revision = queryRows(
                    db,
                    "SELECT * FROM memory_record_revisions WHERE recordId = ?",
                    arrayOf(fixture.id),
                ).single()
                assertEquals("1", revision["revision"])
                assertEquals("MIGRATION", revision["origin"])
                assertEquals(old["content"], revision["content"])
                assertEquals(old["updatedAt"], revision["createdAt"])
            }

            fun space(id: String): String = requireNotNull(records.getValue(id)["spaceId"])
            assertEquals(space("global-a"), space("global-a-second"))
            assertEquals(space("global-b"), space("assistant-b"))
            assertNotEquals(space("global-a"), space("global-b"))
            assertNotEquals(space("global-a"), space("conversation-a"))
            assertNotEquals(space(MOOD_A), space(MOOD_B))
            assertNotEquals(space("unassigned-global"), space("global-a"))
            assertTrue(queryRows(db, "PRAGMA foreign_key_check").isEmpty())
        }
    }

    @Test
    fun migrateFrom48ToLatest_keepsExistingMessagePointersUnverifiedAndMissingSourcesExplicit() {
        createLegacyDatabase()

        openWithRoom()

        openReadableDatabase().use { db ->
            assertEquals(
                LEGACY_MEMORIES.size.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_source_evidence")
            )
            LEGACY_MEMORIES.forEach { fixture ->
                val sourceId = "legacy-source:${fixture.id}"
                val source = queryRows(
                    db,
                    "SELECT * FROM memory_source_evidence WHERE id = ?",
                    arrayOf(sourceId)
                )
                    .single()
                val expectedStatus =
                    if (fixture.sourceMessageId in SOURCE_MESSAGE_IDS) "UNKNOWN" else "MISSING"
                assertEquals(expectedStatus, source["status"])
                assertEquals("LEGACY_HINT", source["kind"])
                assertEquals(fixture.owner, source["characterId"])
                assertEquals("unknown", source["personaKey"])
                assertEquals(
                    scalar(
                        db,
                        "SELECT spaceId FROM memory_records WHERE id = ?",
                        arrayOf(fixture.id)
                    ),
                    source["spaceId"],
                )
                // 旧指针只能作为提示，不能冒充经过角色可见性和消息版本校验的来源。
                assertNull(source["messageId"])
                assertEquals("0", source["messageRevision"])
                assertEquals("", source["content"])
                assertEquals("0", source["contentLength"])
                assertEquals("[]", source["visibleCharacterIdsJson"])
                assertEquals("", source["contentFingerprint"])

                val link = queryRows(
                    db,
                    "SELECT * FROM memory_record_sources WHERE recordId = ?",
                    arrayOf(fixture.id)
                )
                    .single()
                assertEquals(sourceId, link["sourceId"])
                assertEquals("1", link["recordRevision"])
                assertEquals("0", link["sourceRevision"])
                assertEquals("0", link["spanStart"])
                assertEquals("0", link["spanEnd"])
            }
            assertEquals(
                "0",
                scalar(db, "SELECT COUNT(*) FROM memory_source_evidence WHERE status = 'AVAILABLE'")
            )
            assertEquals(
                LONG_RAW_MESSAGE,
                scalar(db, "SELECT content FROM messages WHERE id = ?", arrayOf(MESSAGE_A_USER))
            )
            assertEquals(
                "1",
                scalar(
                    db,
                    "SELECT memoryRevision FROM messages WHERE id = ?",
                    arrayOf(MESSAGE_A_USER)
                )
            )
        }
    }

    @Test
    fun migrateFrom48ToLatest_createsStableIndependentRunIdsAndBindsOnlyLegacyParticipants() {
        createLegacyDatabase()

        openWithRoom()

        val firstRuns = openReadableDatabase().use { db ->
            val sessions = queryRows(db, "SELECT * FROM roleplay_sessions ORDER BY id")
            val runIds = sessions.map { requireNotNull(it["runId"]) }
            assertEquals(LEGACY_SCENARIOS.size, runIds.toSet().size)
            sessions.forEach { session ->
                val runId = requireNotNull(session["runId"])
                assertEquals(runId, UUID.fromString(runId).toString())
                assertNotEquals(session["id"], runId)
                assertNotEquals(session["conversationId"], runId)
            }
            val bindings = queryRows(
                db,
                "SELECT * FROM memory_conversation_bindings ORDER BY conversationId, characterId"
            )
            assertEquals(
                setOf(
                    CONVERSATION_A to CHARACTER_A, CONVERSATION_B to CHARACTER_A,
                    CONVERSATION_GROUP to CHARACTER_A, CONVERSATION_GROUP to CHARACTER_B,
                    MISSING_CONVERSATION to CHARACTER_B
                ),
                bindings.map { it["conversationId"] to it["characterId"] }.toSet(),
            )
            bindings.forEach { binding ->
                val session = sessions.single { it["conversationId"] == binding["conversationId"] }
                assertEquals(session["scenarioId"], binding["scenarioId"])
                assertEquals(session["runId"], binding["runId"])
                assertEquals("legacy_compat", binding["purpose"])
                assertEquals("unknown", binding["personaKey"])
                assertEquals(
                    if (binding["conversationId"] == MISSING_CONVERSATION) "1" else "0",
                    binding["archived"]
                )
                val space = queryRows(
                    db,
                    "SELECT * FROM memory_spaces WHERE id = ?",
                    arrayOf(requireNotNull(binding["spaceId"]))
                )
                    .single()
                assertEquals(binding["characterId"], space["characterId"])
                assertEquals("legacy_compat", space["purpose"])
                assertEquals("1", space["generation"])
            }
            assertTrue(
                queryRows(
                    db,
                    "SELECT * FROM roleplay_scenarios"
                ).all { it["memoryPurpose"] == "legacy_compat" })
            assertEquals(
                scalar(db, "SELECT spaceId FROM memory_records WHERE id = 'conversation-a'"),
                scalar(
                    db,
                    "SELECT spaceId FROM memory_conversation_bindings WHERE conversationId = ? AND characterId = ?",
                    arrayOf(CONVERSATION_A, CHARACTER_A)
                ),
            )
            sessions.associate { requireNotNull(it["id"]) to requireNotNull(it["runId"]) }
        }

        openWithRoom()

        openReadableDatabase().use { db ->
            assertEquals(
                firstRuns,
                queryRows(
                    db,
                    "SELECT * FROM roleplay_sessions"
                ).associate { it["id"] to it["runId"] })
            assertEquals("5", scalar(db, "SELECT COUNT(*) FROM memory_conversation_bindings"))
        }
    }

    @Test
    fun migrateFrom48ToLatest_copiesOnlyDedicatedMoodsAndLeavesHistoryUnprocessed() {
        createLegacyDatabase()

        openWithRoom()

        openReadableDatabase().use { db ->
            val states = queryRows(db, "SELECT * FROM memory_current_states ORDER BY id")
            assertEquals(setOf(MOOD_A, MOOD_B), states.map { it["id"] }.toSet())
            states.forEach { state ->
                val fixture = LEGACY_MEMORIES.single { it.id == state["id"] }
                assertEquals(fixture.content.removePrefix("【心境】"), state["content"])
                assertEquals(fixture.owner, state["characterId"])
                assertEquals("mood", state["stateKey"])
                assertEquals("unknown", state["personaKey"])
                assertEquals("1", state["revision"])
                assertEquals("ACTIVE", state["status"])
                assertNull(state["expiresAt"])
                assertNull(state["endedAt"])
                assertEquals(
                    fixture.content,
                    scalar(
                        db,
                        "SELECT content FROM memory_records WHERE id = ?",
                        arrayOf(fixture.id)
                    )
                )
                val link = queryRows(
                    db,
                    "SELECT * FROM memory_derived_links WHERE ownerId = ?",
                    arrayOf(fixture.id)
                ).single()
                assertEquals("STATE", link["ownerKind"])
                assertEquals(fixture.id, link["recordId"])
                assertEquals("1", link["recordRevision"])
            }
            assertEquals(
                "1",
                scalar(db, "SELECT engineVersion FROM memory_engine_state WHERE id = 1")
            )
            assertEquals("0", scalar(db, "SELECT COUNT(*) FROM memory_ingress_events"))
            assertEquals("0", scalar(db, "SELECT COUNT(*) FROM memory_processing_jobs"))
            assertEquals(
                "0",
                scalar(
                    db,
                    "SELECT COUNT(*) FROM memory_processing_cursors WHERE committedSequence != 0 OR historyBackfilled != 0"
                )
            )
            assertEquals(
                scalar(db, "SELECT COUNT(*) FROM memory_spaces"),
                scalar(db, "SELECT COUNT(*) FROM memory_processing_cursors")
            )
            assertEquals(
                LEGACY_MEMORIES.size.toString(),
                scalar(db, "SELECT COUNT(*) FROM memory_records")
            )
        }
    }

    private fun createLegacyDatabase(): List<LegacySnapshot> {
        assertEquals(48, sourceSchema.get("version").asInt)
        assertEquals(SOURCE_IDENTITY_HASH, sourceSchema.get("identityHash").asString)
        databaseFile.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            sourceEntities.forEach { (table, entity) ->
                db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                // Room 会省略无显式索引的 indices，例如 v48 的 mailbox_settings。
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
            seedLegacyData(db)
            db.version = 48
            assertEquals(
                SOURCE_IDENTITY_HASH,
                scalar(db, "SELECT identity_hash FROM room_master_table WHERE id = 42")
            )
            assertEquals(
                "0",
                scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'memory_records'")
            )
            assertTrue(queryRows(db, "PRAGMA foreign_key_check").isEmpty())
            SNAPSHOT_TABLES.map { readSnapshot(db, it) }
        }
    }

    private fun seedLegacyData(db: SQLiteDatabase) {
        LEGACY_SCENARIOS.forEachIndexed { index, fixture ->
            if (fixture.conversationId != MISSING_CONVERSATION) {
                insertLegacyRow(
                    db, "conversations", mapOf(
                        "id" to fixture.conversationId,
                        "title" to "旧会话-$index",
                        "model" to "fixture-model",
                        "assistantId" to fixture.characterId,
                        "createdAt" to 101L + index,
                        "updatedAt" to 201L + index,
                        "searchEnabled" to 1,
                    )
                )
            }
            insertLegacyRow(
                db, "roleplay_scenarios", mapOf(
                    "id" to fixture.id,
                    "title" to "旧剧情-$index",
                    "description" to "保留旧场景和配置",
                    "assistantId" to fixture.characterId,
                    "chatType" to fixture.chatType,
                    "presetId" to "legacy-preset",
                    "userPersonaMaskId" to "legacy-mask-$index",
                    "userPersonaOverride" to "剧情中的虚构身份",
                    "interactionMode" to if (index % 2 == 0) "online_chat" else "offline_dialogue",
                    "createdAt" to 301L + index,
                    "updatedAt" to 401L + index,
                )
            )
            insertLegacyRow(
                db, "roleplay_sessions", mapOf(
                    "id" to "session-${fixture.id}",
                    "scenarioId" to fixture.id,
                    "conversationId" to fixture.conversationId,
                    "createdAt" to 501L + index,
                    "updatedAt" to 601L + index,
                )
            )
        }
        listOf(CHARACTER_A, CHARACTER_B).forEachIndexed { index, character ->
            insertLegacyRow(
                db, "roleplay_group_participants", mapOf(
                    "id" to "participant-$index",
                    "scenarioId" to "scenario-group",
                    "assistantId" to character,
                    "displayNameOverride" to "群角色-$index",
                    "sortOrder" to index,
                    "canAutoReply" to 1,
                    "createdAt" to 701L + index,
                    "updatedAt" to 801L + index,
                )
            )
        }
        val rawMessages = listOf(
            arrayOf(MESSAGE_A_USER, CONVERSATION_A, "USER", LONG_RAW_MESSAGE, ""),
            arrayOf(
                MESSAGE_A_REPLY,
                CONVERSATION_A,
                "ASSISTANT",
                "旧角色回复：记下这次经历。",
                CHARACTER_A
            ),
            arrayOf(MESSAGE_B, CONVERSATION_B, "USER", "另一次独立旧会话的原文。", ""),
            arrayOf(
                MESSAGE_GROUP_B,
                CONVERSATION_GROUP,
                "ASSISTANT",
                "群成员乙的旧发言。",
                CHARACTER_B
            ),
        )
        rawMessages.forEachIndexed { index, values ->
            insertLegacyRow(
                db, "messages", mapOf(
                    "id" to values[0],
                    "conversationId" to values[1],
                    "role" to values[2],
                    "content" to values[3],
                    "speakerId" to values[4],
                    "speakerName" to "旧说话者-$index",
                    "status" to "COMPLETED",
                    "createdAt" to 901L + index,
                    "modelName" to "fixture-model",
                    "reasoningContent" to "旧推理文本-$index",
                    "replyToMessageId" to if (index == 1) MESSAGE_A_USER else "",
                    "replyToPreview" to "原引用预览-$index",
                    "replyToSpeakerName" to "原引用人",
                    "systemEventKind" to "none",
                    "roleplayOutputFormat" to "UNSPECIFIED",
                    "roleplayInteractionMode" to "offline_dialogue",
                )
            )
        }
        insertLegacyRow(
            db, "conversation_summaries", mapOf(
                "conversationId" to CONVERSATION_A,
                "assistantId" to CHARACTER_A,
                "summary" to "旧会话摘要：约好了下一次出游。",
                "coveredMessageCount" to 2,
                "updatedAt" to 1_010L,
            )
        )
        insertLegacyRow(
            db, "conversation_summary_segments", mapOf(
                "id" to "legacy-segment",
                "conversationId" to CONVERSATION_A,
                "assistantId" to CHARACTER_A,
                "startMessageId" to MESSAGE_A_USER,
                "endMessageId" to MESSAGE_A_REPLY,
                "startCreatedAt" to 901L,
                "endCreatedAt" to 902L,
                "messageCount" to 2,
                "summary" to "旧分段摘要，保持覆盖指针。",
                "createdAt" to 1_011L,
                "updatedAt" to 1_012L,
            )
        )
        LEGACY_MEMORIES.forEachIndexed { index, fixture ->
            insertLegacyRow(
                db, "memory_entries", mapOf(
                    "id" to fixture.id,
                    "scopeType" to fixture.scopeType,
                    "scopeId" to fixture.scopeId,
                    "characterId" to fixture.characterId,
                    "content" to fixture.content,
                    "sourceMessageId" to fixture.sourceMessageId,
                    "importance" to 11 + index * 7,
                    "pinned" to index % 2,
                    "createdAt" to 2_001L + index,
                    "updatedAt" to 3_001L + index,
                    "lastUsedAt" to 4_001L + index,
                )
            )
        }
    }

    private fun insertLegacyRow(db: SQLiteDatabase, table: String, overrides: Map<String, Any>) {
        val fields = sourceEntities.getValue(table).getAsJsonArray("fields").map { it.asJsonObject }
        assertTrue(overrides.keys.all { name -> fields.any { it.get("columnName").asString == name } })
        val values = ContentValues().apply {
            fields.forEach { field ->
                val column = field.get("columnName").asString
                val value = overrides[column] ?: when (field.get("affinity").asString) {
                    "INTEGER" -> 0L
                    "TEXT" -> if (column.endsWith("Json")) "[]" else ""
                    else -> error("旧 fixture 出现未定义的列类型")
                }
                when (value) {
                    is String -> put(column, value)
                    is Int -> put(column, value)
                    is Long -> put(column, value)
                    else -> error("旧 fixture 出现未定义的值类型")
                }
            }
        }
        assertNotEquals(-1L, db.insertOrThrow(table, null, values))
    }

    private fun openWithRoom() {
        assertTrue(ChatDatabase.CURRENT_VERSION >= 49)
        val database = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .addMigrations(*ChatDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 此处真实执行迁移及 Room 生成的整库 schema 校验。
            assertEquals(ChatDatabase.CURRENT_VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private fun openReadableDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun readSnapshot(db: SQLiteDatabase, table: String): LegacySnapshot {
        val columns = sourceEntities.getValue(table).getAsJsonArray("fields")
            .joinToString { "`${it.asJsonObject.get("columnName").asString}`" }
        return LegacySnapshot(table, queryRows(db, "SELECT $columns FROM `$table` ORDER BY 1"))
    }

    private fun queryRows(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String> = emptyArray()
    ): List<Map<String, String?>> =
        db.rawQuery(sql, args).use { cursor ->
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

    private fun scalar(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String> = emptyArray()
    ): String =
        requireNotNull(queryRows(db, sql, args).single().values.single())

    private data class LegacySnapshot(val table: String, val rows: List<Map<String, String?>>)

    private data class LegacyScenario(
        val id: String,
        val conversationId: String,
        val characterId: String,
        val chatType: String = "single"
    )

    private data class LegacyMemory(
        val id: String,
        val scopeType: String,
        val scopeId: String,
        val characterId: String,
        val content: String,
        val sourceMessageId: String,
        val owner: String = characterId,
    )

    private companion object {
        const val SOURCE_SCHEMA_ASSET =
            "com.example.myapplication.data.local.chat.ChatDatabase/48.json"
        const val SOURCE_IDENTITY_HASH = "453b8f924854031d443ced52b2abb8bb"
        const val CHARACTER_A = "legacy-character-a"
        const val CHARACTER_B = "legacy-character-b"
        const val CONVERSATION_A = "legacy-conversation-a"
        const val CONVERSATION_B = "legacy-conversation-b"
        const val CONVERSATION_GROUP = "legacy-conversation-group"
        const val MISSING_CONVERSATION = "legacy-missing-conversation"
        const val MESSAGE_A_USER = "legacy-message-a-user"
        const val MESSAGE_A_REPLY = "legacy-message-a-reply"
        const val MESSAGE_B = "legacy-message-b"
        const val MESSAGE_GROUP_B = "legacy-message-group-b"
        const val MOOD_A = "mental-state:$CONVERSATION_A"
        const val MOOD_B = "mental-state:$CONVERSATION_B"
        val SOURCE_MESSAGE_IDS = setOf(MESSAGE_A_USER, MESSAGE_A_REPLY, MESSAGE_B, MESSAGE_GROUP_B)
        val LONG_RAW_MESSAGE =
            "历史长段落🍀。\n".repeat(600) + "尾部约定：2031 年 8 月 17 日去看展览，编号 72819。"
        val SNAPSHOT_TABLES = listOf(
            "memory_entries",
            "conversations",
            "messages",
            "conversation_summaries",
            "conversation_summary_segments",
            "roleplay_scenarios",
            "roleplay_sessions",
            "roleplay_group_participants",
        )
        val LEGACY_SCENARIOS = listOf(
            LegacyScenario("scenario-a", CONVERSATION_A, CHARACTER_A),
            LegacyScenario("scenario-b", CONVERSATION_B, CHARACTER_A),
            LegacyScenario(
                "scenario-group",
                CONVERSATION_GROUP,
                "not-a-group-participant",
                "group"
            ),
            LegacyScenario("scenario-missing", MISSING_CONVERSATION, CHARACTER_B),
        )
        val LEGACY_MEMORIES = listOf(
            LegacyMemory("global-a", "global", "", CHARACTER_A, "甲的旧全局记录。", MESSAGE_A_USER),
            LegacyMemory(
                "global-a-second",
                "global",
                "",
                CHARACTER_A,
                "同一旧来源的另一条记录。",
                MESSAGE_A_USER
            ),
            LegacyMemory(
                "global-b",
                "global",
                "",
                CHARACTER_B,
                "乙的旧全局记录。",
                "missing-message"
            ),
            LegacyMemory(
                "assistant-b",
                "assistant",
                CHARACTER_B,
                "",
                "角色范围的历史记录。",
                MESSAGE_GROUP_B,
                CHARACTER_B
            ),
            LegacyMemory(
                "conversation-a",
                "conversation",
                CONVERSATION_A,
                CHARACTER_A,
                "会话范围的历史记录。",
                MESSAGE_A_REPLY
            ),
            LegacyMemory(
                "unassigned-global",
                "global",
                "",
                "",
                "归属未知的旧记录。",
                "",
                "legacy-unassigned"
            ),
            LegacyMemory(
                MOOD_A,
                "conversation",
                CONVERSATION_A,
                CHARACTER_A,
                "【心境】平静，准备散步。",
                MESSAGE_A_REPLY
            ),
            LegacyMemory(
                MOOD_B,
                "conversation",
                CONVERSATION_B,
                CHARACTER_A,
                "【心境】担心明天演出。",
                MESSAGE_B
            ),
            LegacyMemory(
                "ordinary-prefixed",
                "conversation",
                CONVERSATION_A,
                CHARACTER_A,
                "【心境】普通文本不能变成当前状态。",
                MESSAGE_A_USER
            ),
            LegacyMemory(
                "mental-state:global-looking",
                "global",
                "",
                CHARACTER_A,
                "【心境】全局文本也不能变成当前状态。",
                MESSAGE_A_USER
            ),
            LegacyMemory(
                "mental-state:unbound-legacy",
                "conversation",
                "unbound-legacy",
                CHARACTER_A,
                "没有专用前缀的旧记录。",
                ""
            ),
        )
    }
}
