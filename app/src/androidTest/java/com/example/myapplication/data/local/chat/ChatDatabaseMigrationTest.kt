package com.example.myapplication.data.local.chat

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ChatDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseFile: File = context.getDatabasePath(TEST_DATABASE_NAME)

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migrateFrom1ToLatest_preservesLegacyConversationData() {
        createLegacyDatabase(version = 1) { db ->
            createVersion1Schema(db)
            db.execSQL(
                """
                INSERT INTO conversations (id, title, model, createdAt, updatedAt)
                VALUES ('c1', '旧会话', 'legacy-model', 10, 20)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO messages (id, conversationId, role, content, status, createdAt)
                VALUES ('m1', 'c1', 'USER', '你好', 'COMPLETED', 30)
                """.trimIndent(),
            )
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertLatestSchemaShape(db)
            assertEquals("default-assistant", queryString(db, "SELECT assistantId FROM conversations WHERE id = 'c1'"))
            assertEquals("[]", queryString(db, "SELECT partsJson FROM messages WHERE id = 'm1'"))
            assertEquals("[]", queryString(db, "SELECT citationsJson FROM messages WHERE id = 'm1'"))
            assertEquals("[]", queryString(db, "SELECT reasoningStepsJson FROM messages WHERE id = 'm1'"))
            assertEquals(
                "UNSPECIFIED",
                queryString(db, "SELECT roleplayOutputFormat FROM messages WHERE id = 'm1'"),
            )
            assertEquals(
                "",
                queryString(db, "SELECT roleplayInteractionMode FROM messages WHERE id = 'm1'"),
            )
            assertEquals(0L, queryLong(db, "SELECT searchEnabled FROM conversations WHERE id = 'c1'"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "descriptionPromptEnabled"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "isPinned"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "isMuted"))
            assertTrue(hasColumn(db, "messages", "roleplayInteractionMode"))
            assertTrue(hasIndex(db, "conversations", "index_conversations_updatedAt"))
        }
    }

    @Test
    fun migrateFrom9ToLatest_createsRoleplayTablesWithLongformColumn() {
        createLegacyDatabase(version = 9) { db ->
            createVersion9Schema(db)
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "descriptionPromptEnabled"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "isPinned"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "isMuted"))
            assertTrue(hasIndex(db, "roleplay_scenarios", "index_roleplay_scenarios_updatedAt"))
            assertTrue(hasIndex(db, "roleplay_sessions", "index_roleplay_sessions_conversationId"))
        }
    }

    @Test
    fun migrateFrom10ToLatest_whenLongformAlreadyExists_keepsDataReadable() {
        createLegacyDatabase(version = 10) { db ->
            createVersion10Schema(db, includeLongformColumn = true)
            db.execSQL(
                """
                INSERT INTO roleplay_scenarios (
                    id, title, description, assistantId, backgroundUri, userDisplayNameOverride,
                    userPortraitUri, userPortraitUrl, characterDisplayNameOverride, characterPortraitUri,
                    characterPortraitUrl, openingNarration, enableNarration, enableRoleplayProtocol,
                    longformModeEnabled, autoHighlightSpeaker, createdAt, updatedAt
                ) VALUES (
                    'scene-1', '测试场景', '', 'assistant-1', '', '', '', '', '', '', '',
                    '', 1, 1, 1, 1, 10, 20
                )
                """.trimIndent(),
            )
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertEquals(1L, queryLong(db, "SELECT longformModeEnabled FROM roleplay_scenarios WHERE id = 'scene-1'"))
            assertEquals(
                1L,
                queryLong(db, "SELECT descriptionPromptEnabled FROM roleplay_scenarios WHERE id = 'scene-1'"),
            )
            assertEquals(0L, queryLong(db, "SELECT isPinned FROM roleplay_scenarios WHERE id = 'scene-1'"))
            assertEquals(0L, queryLong(db, "SELECT isMuted FROM roleplay_scenarios WHERE id = 'scene-1'"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
        }
    }

    @Test
    fun migrateFrom10ToLatest_whenLongformMissing_addsDefaultColumn() {
        createLegacyDatabase(version = 10) { db ->
            createVersion10Schema(db, includeLongformColumn = false)
            db.execSQL(
                """
                INSERT INTO roleplay_scenarios (
                    id, title, description, assistantId, backgroundUri, userDisplayNameOverride,
                    userPortraitUri, userPortraitUrl, characterDisplayNameOverride, characterPortraitUri,
                    characterPortraitUrl, openingNarration, enableNarration, enableRoleplayProtocol,
                    autoHighlightSpeaker, createdAt, updatedAt
                ) VALUES (
                    'scene-2', '缺列场景', '', 'assistant-2', '', '', '', '', '', '', '',
                    '', 1, 1, 1, 30, 40
                )
                """.trimIndent(),
            )
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertEquals(0L, queryLong(db, "SELECT longformModeEnabled FROM roleplay_scenarios WHERE id = 'scene-2'"))
            assertEquals(
                1L,
                queryLong(db, "SELECT descriptionPromptEnabled FROM roleplay_scenarios WHERE id = 'scene-2'"),
            )
            assertEquals(0L, queryLong(db, "SELECT isPinned FROM roleplay_scenarios WHERE id = 'scene-2'"))
            assertEquals(0L, queryLong(db, "SELECT isMuted FROM roleplay_scenarios WHERE id = 'scene-2'"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
        }
    }

    @Test
    fun migrateFrom11ToLatest_addsSearchCitationAndReasoningStepColumns() {
        createLegacyDatabase(version = 11) { db ->
            createVersion11Schema(db)
            db.execSQL(
                """
                INSERT INTO conversations (id, title, model, createdAt, updatedAt, assistantId)
                VALUES ('c11', '旧会话11', 'model-11', 1, 2, 'assistant-11')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO messages (
                    id, conversationId, role, content, status, createdAt,
                    modelName, reasoningContent, attachmentsJson, partsJson
                ) VALUES (
                    'm11', 'c11', 'ASSISTANT', '旧消息', 'COMPLETED', 3,
                    'model-11', '', '[]', '[]'
                )
                """.trimIndent(),
            )
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertTrue(hasColumn(db, "conversations", "searchEnabled"))
            assertTrue(hasColumn(db, "messages", "citationsJson"))
            assertTrue(hasColumn(db, "messages", "roleplayOutputFormat"))
            assertTrue(hasColumn(db, "messages", "reasoningStepsJson"))
            assertEquals(0L, queryLong(db, "SELECT searchEnabled FROM conversations WHERE id = 'c11'"))
            assertEquals("[]", queryString(db, "SELECT citationsJson FROM messages WHERE id = 'm11'"))
            assertEquals("[]", queryString(db, "SELECT reasoningStepsJson FROM messages WHERE id = 'm11'"))
            assertEquals(
                "UNSPECIFIED",
                queryString(db, "SELECT roleplayOutputFormat FROM messages WHERE id = 'm11'"),
            )
            assertEquals(
                "",
                queryString(db, "SELECT roleplayInteractionMode FROM messages WHERE id = 'm11'"),
            )
        }
    }

    @Test
    fun migrateFrom13ToLatest_backfillsLegacyReasoningSteps() {
        createLegacyDatabase(version = 13) { db ->
            createVersion13Schema(db)
            db.execSQL(
                """
                INSERT INTO conversations (
                    id, title, model, createdAt, updatedAt, assistantId, searchEnabled
                ) VALUES (
                    'c13', '旧会话13', 'model-13', 1, 2, 'assistant-13', 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO messages (
                    id, conversationId, role, content, status, createdAt,
                    modelName, reasoningContent, attachmentsJson, partsJson, citationsJson, roleplayOutputFormat
                ) VALUES (
                    'm13', 'c13', 'ASSISTANT', '最终回复', 'COMPLETED', 42,
                    'model-13', '**分析目标**\n先检查输入。', '[]', '[]', '[]', 'UNSPECIFIED'
                )
                """.trimIndent(),
            )
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertLatestSchemaShape(db)
            val reasoningStepsJson = queryString(db, "SELECT reasoningStepsJson FROM messages WHERE id = 'm13'")
            assertNotNull(reasoningStepsJson)
            assertTrue(reasoningStepsJson.contains("\"id\":\"legacy-m13-0\""))
            assertTrue(reasoningStepsJson.contains("\"createdAt\":42"))
            assertTrue(reasoningStepsJson.contains("\"finishedAt\":42"))
            assertTrue(reasoningStepsJson.contains("分析目标"))
            assertEquals(
                "",
                queryString(db, "SELECT roleplayInteractionMode FROM messages WHERE id = 'm13'"),
            )
        }
    }

    @Test
    fun fixed55To56_preservesAllLegacyRowsAndCreatesIndependentVoiceTables() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/55.json").use { it.readBytes() }
        assertEquals("5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033", voiceSha256(bytes))
        val schema = AppJson.gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
            .getAsJsonObject("database")
        assertEquals(55, schema.get("version").asInt)
        assertEquals("949d159c39b12196f82d50b2efb5748a", schema.get("identityHash").asString)
        val entities = schema.getAsJsonArray("entities").map { it.asJsonObject }
        var before: Map<String, List<List<String?>>> = emptyMap()
        createLegacyDatabase(55) { db ->
            entities.forEach { entity ->
                val table = entity.get("tableName").asString
                db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    db.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getAsJsonArray("views")?.forEach { element ->
                val view = element.asJsonObject
                db.execSQL(view.get("createSql").asString.replace("\${VIEW_NAME}", view.get("viewName").asString))
            }
            schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            fun row(table: String, values: Map<String, Any?>) = voiceLegacyRow(db, entities.single { it.get("tableName").asString == table }, values)
            row("conversations", mapOf("id" to "voice-c55", "title" to "保留旧会话", "assistantId" to "alice", "createdAt" to 11L))
            row("messages", mapOf("id" to "legacy-m55", "conversationId" to "voice-c55", "role" to "USER",
                "content" to " 保留 CRLF\r\n".repeat(180), "status" to "COMPLETED", "createdAt" to 12L,
                "memoryRevision" to 9L, "companionTurnId" to "", "companionMemoryState" to "legacy"))
            row("messages", mapOf("id" to "linked-m55", "conversationId" to "voice-c55", "role" to "ASSISTANT",
                "content" to "<dialogue voice_emotion=\"calm\">旧原文🙂。</dialogue>", "status" to "COMPLETED",
                "createdAt" to 13L, "speakerId" to "alice", "memoryRevision" to 17L,
                "companionTurnId" to "turn55", "companionMemoryState" to "ready"))
            row("companion_encounters", mapOf("id" to "enc55", "conversationId" to "voice-c55", "characterId" to "alice",
                "scenarioId" to "scene55", "runId" to "run55", "spaceId" to "space55", "state" to "ACTIVE", "revision" to 1L))
            row("companion_turns", mapOf("id" to "turn55", "encounterId" to "enc55", "ordinal" to 1L, "kind" to "INPUT",
                "replyMessageId" to "linked-m55", "replyRevision" to 17L, "committedRevision" to 1L, "selectedAttemptId" to "attempt55"))
            row("companion_attempts", mapOf("id" to "attempt55", "turnId" to "turn55", "state" to "COMMITTED",
                "frozenRuntimeJson" to "{\"version\":\"preserved\"}", "requestDescriptorJson" to "{\"protocolVersion\":1}",
                "artifactJson" to "{\"rawReply\":\"旧产物\"}", "artifactHash" to "old-artifact-hash"))
            row("memory_spaces", mapOf("id" to "space55", "identityKey" to "fixed55-space", "characterId" to "alice", "status" to "ACTIVE"))
            row("memory_records", mapOf("id" to "memory55", "spaceId" to "space55", "characterId" to "alice",
                "content" to "旧记忆保持", "revision" to 3L, "status" to "ACTIVE"))
            before = voiceLegacySnapshot(db, entities)
        }
        migrateToLatest()
        openReadableDatabase().use { db ->
            assertEquals(ChatDatabase.CURRENT_VERSION.toLong(), queryLong(db, "PRAGMA user_version"))
            assertEquals(before, voiceLegacySnapshot(db, entities))
            assertEquals(0L, queryLong(db, "SELECT COUNT(*) FROM companion_voice_resources"))
            assertEquals(0L, queryLong(db, "SELECT COUNT(*) FROM companion_voice_favorites"))
            assertTrue(hasIndex(db, "companion_voice_resources", "index_companion_voice_resources_cacheKey"))
            assertTrue(hasIndex(db, "companion_voice_favorites", "index_companion_voice_favorites_sourceMessageId_sourceRevision"))
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
        // 再次打开验证 Room identity/schema，旧库数据仍逐字段不变。
        migrateToLatest()
        openReadableDatabase().use { assertEquals(before, voiceLegacySnapshot(it, entities)) }
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            fun audio(id: String, key: String?) = db.execSQL("""
                INSERT INTO companion_voice_resources(id, requestKey, cacheKey, state, path, mimeType, sha256,
                    sizeBytes, clipJson, createdAt, lastUsedAt) VALUES (?, 'synthetic', ?, 'READY', '', '', '', 0, '{}', 1, 1)
            """.trimIndent(), arrayOf(id, key))
            audio("audio55", "same-key")
            audio("nullable-a", null)
            audio("nullable-b", null)
            try {
                audio("duplicate", "same-key")
                fail("同一 cache key 应只有一个当前资源")
            } catch (_: SQLiteConstraintException) { }
            db.execSQL("""
                INSERT INTO companion_voice_favorites(id, audioId, characterId, sourceMessageId, sourceRevision, clipJson, createdAt)
                VALUES ('favorite55', 'audio55', 'alice', 'linked-m55', 17, '{}', 1)
            """.trimIndent())
            db.execSQL("DELETE FROM messages WHERE id = 'linked-m55'")
            assertEquals(1L, queryLong(db, "SELECT COUNT(*) FROM companion_voice_favorites"))
            try {
                db.execSQL("DELETE FROM companion_voice_resources WHERE id = 'audio55'")
                fail("收藏仍引用的音频不可被删除")
            } catch (_: SQLiteConstraintException) { }
        }
    }

    private fun voiceLegacyRow(db: SQLiteDatabase, entity: JsonObject, overrides: Map<String, Any?>) {
        val values = ContentValues()
        entity.getAsJsonArray("fields").forEach { fieldElement ->
            val field = fieldElement.asJsonObject
            val column = field.get("columnName").asString
            val value = if (column in overrides) overrides[column]
                else if (field.get("notNull")?.asBoolean != true) null
                else if (field.get("affinity").asString == "TEXT") "" else 0L
            when (value) {
                null -> values.putNull(column)
                is String -> values.put(column, value)
                is Long -> values.put(column, value)
                else -> error("固定55夹具字段类型无效")
            }
        }
        assertTrue(db.insertOrThrow(entity.get("tableName").asString, null, values) != -1L)
    }

    private fun voiceLegacySnapshot(db: SQLiteDatabase, entities: List<JsonObject>): Map<String, List<List<String?>>> =
        entities.associate { entity ->
            val table = entity.get("tableName").asString
            val columns = entity.getAsJsonArray("fields").map { it.asJsonObject.get("columnName").asString }
            val select = columns.joinToString(",") { "`$it`" }
            val rows = db.rawQuery("SELECT $select FROM `$table` ORDER BY $select", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(columns.indices.map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
            }
            table to rows
        }

    private fun voiceSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun assertLatestSchemaShape(db: SQLiteDatabase) {
        assertEquals(ChatDatabase.CURRENT_VERSION.toLong(), queryLong(db, "PRAGMA user_version"))

        assertTrue(hasTable(db, "roleplay_diary_entries"))
        assertTrue(hasTable(db, "mailbox_letters"))
        assertTrue(hasTable(db, "mailbox_settings"))
        assertTrue(hasTable(db, "presets"))
        assertTrue(hasTable(db, "roleplay_group_participants"))
        assertTrue(hasTable(db, "conversation_summary_segments"))
        assertTrue(hasTable(db, "roleplay_scripts"))
        assertTrue(hasTable(db, "roleplay_script_state"))
        assertTrue(hasTable(db, "moment_posts"))
        assertTrue(hasTable(db, "moment_comments"))
        assertTrue(hasTable(db, "moment_media"))
        assertTrue(hasTable(db, "wallet_accounts"))
        assertTrue(hasTable(db, "wallet_ledger_entries"))
        assertTrue(hasTable(db, "shop_batches"))
        assertTrue(hasTable(db, "shop_items"))
        assertTrue(hasTable(db, "inventory_items"))

        assertTrue(hasColumn(db, "worldbook_entries", "bookId"))
        assertTrue(hasColumn(db, "worldbook_entries", "matchMode"))
        assertTrue(hasColumn(db, "worldbook_entries", "extrasJson"))
        assertTrue(hasColumn(db, "worldbook_entries", "probability"))
        assertTrue(hasColumn(db, "memory_entries", "characterId"))
        assertTrue(hasColumn(db, "messages", "speakerId"))
        assertTrue(hasColumn(db, "messages", "speakerName"))
        assertTrue(hasColumn(db, "roleplay_scenarios", "userPersonaMaskId"))
        assertTrue(hasColumn(db, "roleplay_scenarios", "chatType"))
        assertTrue(hasColumn(db, "roleplay_scenarios", "onlineReplyMinCount"))
        assertTrue(hasColumn(db, "roleplay_scenarios", "enableOnlineProactiveReply"))
        assertTrue(hasColumn(db, "roleplay_scripts", "grantedPermissionsJson"))
        assertTrue(hasColumn(db, "roleplay_script_state", "stateValue"))
        assertTrue(hasColumn(db, "moment_comments", "authorAvatarUri"))

        assertTrue(hasIndex(db, "messages", "index_messages_conversationId_createdAt_id"))
        assertTrue(hasIndex(db, "roleplay_scripts", "index_roleplay_scripts_scope_ownerId"))
        assertTrue(hasIndex(db, "moment_media", "index_moment_media_postId"))
        assertTrue(hasIndex(db, "wallet_accounts", "index_wallet_accounts_scenarioId_ownerType_ownerId"))
        assertTrue(hasIndex(db, "shop_items", "index_shop_items_scenarioId"))
        assertTrue(hasIndex(db, "inventory_items", "index_inventory_items_scenarioId_ownerType_ownerId"))
    }

    private fun migrateToLatest() {
        Room.databaseBuilder(context, ChatDatabase::class.java, TEST_DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
            .apply {
                openHelper.writableDatabase.close()
                close()
            }
    }

    private fun createLegacyDatabase(
        version: Int,
        createSchema: (SQLiteDatabase) -> Unit,
    ) {
        context.deleteDatabase(TEST_DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            createSchema(db)
            db.version = version
        }
    }

    private fun createVersion1Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                model TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT NOT NULL PRIMARY KEY,
                conversationId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId ON messages (conversationId)")
    }

    private fun createVersion9Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                model TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                assistantId TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_updatedAt ON conversations (updatedAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT NOT NULL PRIMARY KEY,
                conversationId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                modelName TEXT NOT NULL DEFAULT '',
                reasoningContent TEXT NOT NULL DEFAULT '',
                attachmentsJson TEXT NOT NULL DEFAULT '[]',
                partsJson TEXT NOT NULL DEFAULT '[]',
                FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId ON messages (conversationId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS worldbook_entries (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                keywordsJson TEXT NOT NULL DEFAULT '[]',
                aliasesJson TEXT NOT NULL DEFAULT '[]',
                enabled INTEGER NOT NULL DEFAULT 1,
                alwaysActive INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                scopeType TEXT NOT NULL DEFAULT 'global',
                scopeId TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                secondaryKeywordsJson TEXT NOT NULL DEFAULT '[]',
                selective INTEGER NOT NULL DEFAULT 0,
                caseSensitive INTEGER NOT NULL DEFAULT 0,
                insertionOrder INTEGER NOT NULL DEFAULT 0,
                sourceBookName TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_worldbook_entries_scopeType_scopeId ON worldbook_entries (scopeType, scopeId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_worldbook_entries_updatedAt ON worldbook_entries (updatedAt)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_entries (
                id TEXT NOT NULL PRIMARY KEY,
                scopeType TEXT NOT NULL DEFAULT 'global',
                scopeId TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                importance INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                sourceMessageId TEXT NOT NULL DEFAULT '',
                lastUsedAt INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memory_entries_scopeType_scopeId ON memory_entries (scopeType, scopeId)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_pinned ON memory_entries (pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries (updatedAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversation_summaries (
                conversationId TEXT NOT NULL PRIMARY KEY,
                assistantId TEXT NOT NULL DEFAULT '',
                summary TEXT NOT NULL DEFAULT '',
                coveredMessageCount INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversation_summaries_assistantId ON conversation_summaries (assistantId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversation_summaries_updatedAt ON conversation_summaries (updatedAt)",
        )
    }

    private fun createVersion10Schema(
        db: SQLiteDatabase,
        includeLongformColumn: Boolean,
    ) {
        createVersion9Schema(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS roleplay_scenarios (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                assistantId TEXT NOT NULL DEFAULT 'default-assistant',
                backgroundUri TEXT NOT NULL DEFAULT '',
                userDisplayNameOverride TEXT NOT NULL DEFAULT '',
                userPortraitUri TEXT NOT NULL DEFAULT '',
                userPortraitUrl TEXT NOT NULL DEFAULT '',
                characterDisplayNameOverride TEXT NOT NULL DEFAULT '',
                characterPortraitUri TEXT NOT NULL DEFAULT '',
                characterPortraitUrl TEXT NOT NULL DEFAULT '',
                openingNarration TEXT NOT NULL DEFAULT '',
                enableNarration INTEGER NOT NULL DEFAULT 1,
                enableRoleplayProtocol INTEGER NOT NULL DEFAULT 1,
                ${if (includeLongformColumn) "longformModeEnabled INTEGER NOT NULL DEFAULT 0," else ""}
                autoHighlightSpeaker INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS roleplay_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                scenarioId TEXT NOT NULL,
                conversationId TEXT NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(scenarioId) REFERENCES roleplay_scenarios(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_scenarios_updatedAt ON roleplay_scenarios (updatedAt)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_roleplay_sessions_scenarioId ON roleplay_sessions (scenarioId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_sessions_conversationId ON roleplay_sessions (conversationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_sessions_updatedAt ON roleplay_sessions (updatedAt)")
    }

    private fun createVersion11Schema(db: SQLiteDatabase) {
        createVersion10Schema(db, includeLongformColumn = true)
    }

    private fun createVersion13Schema(db: SQLiteDatabase) {
        createVersion11Schema(db)
        db.execSQL(
            "ALTER TABLE conversations ADD COLUMN searchEnabled INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN citationsJson TEXT NOT NULL DEFAULT '[]'",
        )
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN roleplayOutputFormat TEXT NOT NULL DEFAULT 'UNSPECIFIED'",
        )
    }

    private fun openReadableDatabase(): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    private fun queryString(
        db: SQLiteDatabase,
        sql: String,
    ): String {
        db.rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun queryLong(
        db: SQLiteDatabase,
        sql: String,
    ): Long {
        db.rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun hasColumn(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasTable(
        db: SQLiteDatabase,
        tableName: String,
    ): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasIndex(
        db: SQLiteDatabase,
        tableName: String,
        indexName: String,
    ): Boolean {
        db.rawQuery("PRAGMA index_list($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == indexName) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        private const val TEST_DATABASE_NAME = "chat-migration-test.db"
        private val ALL_MIGRATIONS = ChatDatabase.ALL_MIGRATIONS
    }
}
