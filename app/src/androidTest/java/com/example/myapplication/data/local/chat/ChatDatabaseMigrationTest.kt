package com.example.myapplication.data.local.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseFile: File = context.getDatabasePath(TEST_DATABASE_NAME)

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migrateFrom1To13_preservesLegacyConversationData() {
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
            assertEquals("default-assistant", queryString(db, "SELECT assistantId FROM conversations WHERE id = 'c1'"))
            assertEquals("[]", queryString(db, "SELECT partsJson FROM messages WHERE id = 'm1'"))
            assertEquals("[]", queryString(db, "SELECT citationsJson FROM messages WHERE id = 'm1'"))
            assertEquals(
                "UNSPECIFIED",
                queryString(db, "SELECT roleplayOutputFormat FROM messages WHERE id = 'm1'"),
            )
            assertEquals(0L, queryLong(db, "SELECT searchEnabled FROM conversations WHERE id = 'c1'"))
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
            assertTrue(hasIndex(db, "conversations", "index_conversations_updatedAt"))
        }
    }

    @Test
    fun migrateFrom9To13_createsRoleplayTablesWithLongformColumn() {
        createLegacyDatabase(version = 9) { db ->
            createVersion9Schema(db)
        }

        migrateToLatest()

        openReadableDatabase().use { db ->
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
            assertTrue(hasIndex(db, "roleplay_scenarios", "index_roleplay_scenarios_updatedAt"))
            assertTrue(hasIndex(db, "roleplay_sessions", "index_roleplay_sessions_conversationId"))
        }
    }

    @Test
    fun migrateFrom10To12_whenLongformAlreadyExists_keepsDataReadable() {
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
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
        }
    }

    @Test
    fun migrateFrom10To12_whenLongformMissing_addsDefaultColumn() {
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
            assertTrue(hasColumn(db, "roleplay_scenarios", "longformModeEnabled"))
        }
    }

    @Test
    fun migrateFrom11To12_addsSearchAndCitationColumns() {
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
            assertEquals(0L, queryLong(db, "SELECT searchEnabled FROM conversations WHERE id = 'c11'"))
            assertEquals("[]", queryString(db, "SELECT citationsJson FROM messages WHERE id = 'm11'"))
            assertEquals(
                "UNSPECIFIED",
                queryString(db, "SELECT roleplayOutputFormat FROM messages WHERE id = 'm11'"),
            )
        }
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
        private val ALL_MIGRATIONS = arrayOf(
            ChatDatabase.MIGRATION_1_2,
            ChatDatabase.MIGRATION_2_3,
            ChatDatabase.MIGRATION_3_4,
            ChatDatabase.MIGRATION_4_5,
            ChatDatabase.MIGRATION_5_6,
            ChatDatabase.MIGRATION_6_7,
            ChatDatabase.MIGRATION_7_8,
            ChatDatabase.MIGRATION_8_9,
            ChatDatabase.MIGRATION_9_10,
            ChatDatabase.MIGRATION_10_11,
            ChatDatabase.MIGRATION_11_12,
            ChatDatabase.MIGRATION_12_13,
        )
    }
}
