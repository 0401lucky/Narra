package com.example.myapplication.data.local.chat

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.local.roleplay.RoleplayDao
import com.example.myapplication.data.local.roleplay.RoleplayScenarioEntity
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        WorldBookEntryEntity::class,
        MemoryEntryEntity::class,
        ConversationSummaryEntity::class,
        RoleplayScenarioEntity::class,
        RoleplaySessionEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun worldBookDao(): WorldBookDao
    abstract fun memoryDao(): MemoryDao
    abstract fun roleplayDao(): RoleplayDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN modelName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_updatedAt ON conversations (updatedAt)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN assistantId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE conversations SET assistantId = 'default-assistant' WHERE assistantId = ''",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
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
                    "CREATE INDEX IF NOT EXISTS index_worldbook_entries_scopeType_scopeId ON worldbook_entries (scopeType, scopeId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_worldbook_entries_updatedAt ON worldbook_entries (updatedAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_entries_scopeType_scopeId ON memory_entries (scopeType, scopeId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_entries_pinned ON memory_entries (pinned)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries (updatedAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conversation_summaries_assistantId ON conversation_summaries (assistantId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conversation_summaries_updatedAt ON conversation_summaries (updatedAt)",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN secondaryKeywordsJson TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN selective INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN caseSensitive INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN insertionOrder INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN sourceBookName TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        longformModeEnabled INTEGER NOT NULL DEFAULT 0,
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_roleplay_scenarios_updatedAt ON roleplay_scenarios (updatedAt)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_roleplay_sessions_scenarioId ON roleplay_sessions (scenarioId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_roleplay_sessions_conversationId ON roleplay_sessions (conversationId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_roleplay_sessions_updatedAt ON roleplay_sessions (updatedAt)",
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "roleplay_scenarios", "longformModeEnabled")) {
                    db.execSQL(
                        "ALTER TABLE roleplay_scenarios ADD COLUMN longformModeEnabled INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
        }

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
        ): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameColumnIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameColumnIndex != -1 && cursor.getString(nameColumnIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
