package com.example.myapplication.data.local.chat.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 所有 Room schema 迁移的中央注册表。
 *
 * 版本 bump 约定（见 `app/schemas/.../README.md`）：
 * - 一次只 +1 版本号，分多次提交，避免 schema JSON 漏导。
 * - 新增 `MIGRATION_N_N+1` 后必须加入 [ALL] 数组；`ChatDatabaseMigrationRegistryTest` 会断言连续性。
 * - ALTER TABLE 列新增优先用 `hasColumn` 守护，保证中间版本升级链幂等。
 */
internal object ChatDbMigrations {

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
                    bookId TEXT NOT NULL DEFAULT '',
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
                "CREATE INDEX IF NOT EXISTS index_worldbook_entries_bookId ON worldbook_entries (bookId)",
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
                    userPersonaOverride TEXT NOT NULL DEFAULT '',
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

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "conversations", "searchEnabled")) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN searchEnabled INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (!hasColumn(db, "messages", "citationsJson")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN citationsJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "roleplayOutputFormat")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN roleplayOutputFormat TEXT NOT NULL DEFAULT 'UNSPECIFIED'",
                )
            }
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "reasoningStepsJson")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN reasoningStepsJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
            db.execSQL(
                """
                UPDATE messages
                SET reasoningStepsJson = CASE
                    WHEN TRIM(reasoningContent) = '' THEN '[]'
                    ELSE
                        '[{"id":"legacy-' || id || '-0","text":"' ||
                        REPLACE(
                            REPLACE(
                                REPLACE(
                                    REPLACE(reasoningContent, '\', '\\'),
                                    '"',
                                    '\"'
                                ),
                                char(10),
                                '\n'
                            ),
                            char(13),
                            '\r'
                        ) ||
                        '","createdAt":' || createdAt || ',"finishedAt":' || createdAt || '}]'
                END
                WHERE reasoningStepsJson = '[]'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS phone_snapshots (
                    conversationId TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL DEFAULT '',
                    assistantId TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    snapshotJson TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_phone_snapshots_updatedAt ON phone_snapshots (updatedAt)",
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "interactionMode")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN interactionMode TEXT NOT NULL DEFAULT 'offline_dialogue'",
                )
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS phone_snapshots_v2 (
                    conversationId TEXT NOT NULL,
                    ownerType TEXT NOT NULL DEFAULT 'character',
                    scenarioId TEXT NOT NULL DEFAULT '',
                    assistantId TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    snapshotJson TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(conversationId, ownerType)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO phone_snapshots_v2 (
                    conversationId,
                    ownerType,
                    scenarioId,
                    assistantId,
                    updatedAt,
                    snapshotJson
                )
                SELECT
                    conversationId,
                    'character',
                    scenarioId,
                    assistantId,
                    updatedAt,
                    snapshotJson
                FROM phone_snapshots
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE phone_snapshots")
            db.execSQL("ALTER TABLE phone_snapshots_v2 RENAME TO phone_snapshots")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_phone_snapshots_updatedAt ON phone_snapshots (updatedAt)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS phone_observations (
                    conversationId TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL DEFAULT '',
                    ownerType TEXT NOT NULL DEFAULT 'user',
                    viewMode TEXT NOT NULL DEFAULT 'character_looks_user_phone',
                    ownerName TEXT NOT NULL DEFAULT '',
                    viewerName TEXT NOT NULL DEFAULT '',
                    eventText TEXT NOT NULL DEFAULT '',
                    keyFindingsJson TEXT NOT NULL DEFAULT '[]',
                    observedAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_phone_observations_updatedAt ON phone_observations (updatedAt)",
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "replyToMessageId")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN replyToMessageId TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "messages", "replyToPreview")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN replyToPreview TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "messages", "replyToSpeakerName")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN replyToSpeakerName TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "isRecalled")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN isRecalled INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (!hasColumn(db, "messages", "systemEventKind")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN systemEventKind TEXT NOT NULL DEFAULT 'none'",
                )
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS roleplay_online_meta (
                    conversationId TEXT NOT NULL PRIMARY KEY,
                    lastCompensationBucket TEXT NOT NULL DEFAULT '',
                    lastConsumedObservationUpdatedAt INTEGER NOT NULL DEFAULT 0,
                    lastSystemEventToken TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_roleplay_online_meta_updatedAt ON roleplay_online_meta (updatedAt)",
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "phone_observations", "hasVisibleFeedback")) {
                db.execSQL(
                    "ALTER TABLE phone_observations ADD COLUMN hasVisibleFeedback INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (!hasColumn(db, "phone_observations", "feedbackMessageId")) {
                db.execSQL(
                    "ALTER TABLE phone_observations ADD COLUMN feedbackMessageId TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "phone_observations", "usedFindingKeysJson")) {
                db.execSQL(
                    "ALTER TABLE phone_observations ADD COLUMN usedFindingKeysJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_online_meta", "activeVideoCallSessionId")) {
                db.execSQL(
                    "ALTER TABLE roleplay_online_meta ADD COLUMN activeVideoCallSessionId TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "roleplay_online_meta", "activeVideoCallStartedAt")) {
                db.execSQL(
                    "ALTER TABLE roleplay_online_meta ADD COLUMN activeVideoCallStartedAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "userPersonaOverride")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN userPersonaOverride TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "roleplayInteractionMode")) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN roleplayInteractionMode TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "worldbook_entries", "bookId")) {
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN bookId TEXT NOT NULL DEFAULT ''",
                )
            }
            db.execSQL(
                """
                UPDATE worldbook_entries
                SET bookId = CASE
                    WHEN TRIM(bookId) != '' THEN TRIM(bookId)
                    WHEN TRIM(sourceBookName) = '' THEN ''
                    ELSE 'legacy-book:' || LOWER(TRIM(sourceBookName))
                END
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_worldbook_entries_bookId ON worldbook_entries (bookId)",
            )
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "enableDeepImmersion")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN enableDeepImmersion INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "memory_entries", "characterId")) {
                db.execSQL(
                    "ALTER TABLE memory_entries ADD COLUMN characterId TEXT NOT NULL DEFAULT ''",
                )
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_entries_characterId ON memory_entries (characterId)",
            )
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "enableTimeAwareness")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN enableTimeAwareness INTEGER NOT NULL DEFAULT 1",
                )
            }
            if (!hasColumn(db, "roleplay_scenarios", "enableNetMeme")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN enableNetMeme INTEGER NOT NULL DEFAULT 0",
                )
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS roleplay_diary_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL DEFAULT '',
                    scenarioId TEXT NOT NULL DEFAULT '',
                    title TEXT NOT NULL DEFAULT '',
                    content TEXT NOT NULL DEFAULT '',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_roleplay_diary_entries_conversationId ON roleplay_diary_entries (conversationId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_roleplay_diary_entries_scenarioId ON roleplay_diary_entries (scenarioId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_roleplay_diary_entries_conversationId_sortOrder ON roleplay_diary_entries (conversationId, sortOrder)",
            )
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_diary_entries", "mood")) {
                db.execSQL(
                    "ALTER TABLE roleplay_diary_entries ADD COLUMN mood TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "roleplay_diary_entries", "weather")) {
                db.execSQL(
                    "ALTER TABLE roleplay_diary_entries ADD COLUMN weather TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "roleplay_diary_entries", "tagsCsv")) {
                db.execSQL(
                    "ALTER TABLE roleplay_diary_entries ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''",
                )
            }
            if (!hasColumn(db, "roleplay_diary_entries", "dateLabel")) {
                db.execSQL(
                    "ALTER TABLE roleplay_diary_entries ADD COLUMN dateLabel TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "worldbook_entries", "matchMode")) {
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN matchMode TEXT NOT NULL DEFAULT 'word_cjk'",
                )
            }
        }
    }

    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "worldbook_entries", "extrasJson")) {
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN extrasJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "worldbook_entries", "probability")) {
                db.execSQL(
                    "ALTER TABLE worldbook_entries ADD COLUMN probability INTEGER NOT NULL DEFAULT 100",
                )
            }
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "descriptionPromptEnabled")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN descriptionPromptEnabled INTEGER NOT NULL DEFAULT 1",
                )
            }
            if (!hasColumn(db, "roleplay_scenarios", "isPinned")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (!hasColumn(db, "roleplay_scenarios", "isMuted")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "userPersonaMaskId")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN userPersonaMaskId TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mailbox_letters (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    assistantId TEXT NOT NULL,
                    senderType TEXT NOT NULL,
                    box TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    content TEXT NOT NULL,
                    excerpt TEXT NOT NULL,
                    tagsCsv TEXT NOT NULL,
                    mood TEXT NOT NULL,
                    replyToLetterId TEXT NOT NULL,
                    isRead INTEGER NOT NULL,
                    isStarred INTEGER NOT NULL,
                    allowMemory INTEGER NOT NULL,
                    memoryCandidate TEXT NOT NULL,
                    linkedMemoryId TEXT NOT NULL,
                    source TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    sentAt INTEGER NOT NULL,
                    readAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_mailbox_letters_scenarioId_box_updatedAt
                ON mailbox_letters(scenarioId, box, updatedAt)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_mailbox_letters_conversationId
                ON mailbox_letters(conversationId)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_mailbox_letters_assistantId
                ON mailbox_letters(assistantId)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_mailbox_letters_replyToLetterId
                ON mailbox_letters(replyToLetterId)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mailbox_settings (
                    scenarioId TEXT NOT NULL PRIMARY KEY,
                    autoReplyToUserLetters INTEGER NOT NULL,
                    includeRecentChatByDefault INTEGER NOT NULL,
                    includePhoneCluesByDefault INTEGER NOT NULL,
                    allowMemoryByDefault INTEGER NOT NULL,
                    proactiveFrequency TEXT NOT NULL,
                    lastProactiveLetterAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS presets (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    systemPrompt TEXT NOT NULL,
                    contextTemplate TEXT NOT NULL,
                    samplerJson TEXT NOT NULL,
                    instructJson TEXT NOT NULL,
                    stopSequencesJson TEXT NOT NULL,
                    entriesJson TEXT NOT NULL DEFAULT '[]',
                    renderConfigJson TEXT NOT NULL DEFAULT '{}',
                    compatMetadataJson TEXT NOT NULL DEFAULT '{}',
                    version INTEGER NOT NULL,
                    builtIn INTEGER NOT NULL,
                    userModified INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_presets_builtIn ON presets (builtIn)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_presets_updatedAt ON presets (updatedAt)")
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "presets", "entriesJson")) {
                db.execSQL("ALTER TABLE presets ADD COLUMN entriesJson TEXT NOT NULL DEFAULT '[]'")
            }
            if (!hasColumn(db, "presets", "renderConfigJson")) {
                db.execSQL("ALTER TABLE presets ADD COLUMN renderConfigJson TEXT NOT NULL DEFAULT '{}'")
            }
        }
    }

    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "speakerId")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN speakerId TEXT NOT NULL DEFAULT ''")
            }
            if (!hasColumn(db, "messages", "speakerName")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN speakerName TEXT NOT NULL DEFAULT ''")
            }
            if (!hasColumn(db, "messages", "speakerAvatarUri")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN speakerAvatarUri TEXT NOT NULL DEFAULT ''")
            }
            if (!hasColumn(db, "roleplay_scenarios", "chatType")) {
                db.execSQL("ALTER TABLE roleplay_scenarios ADD COLUMN chatType TEXT NOT NULL DEFAULT 'single'")
            }
            if (!hasColumn(db, "roleplay_scenarios", "groupReplyMode")) {
                db.execSQL("ALTER TABLE roleplay_scenarios ADD COLUMN groupReplyMode TEXT NOT NULL DEFAULT 'natural'")
            }
            if (!hasColumn(db, "roleplay_scenarios", "enableGroupMentionAutoReply")) {
                db.execSQL("ALTER TABLE roleplay_scenarios ADD COLUMN enableGroupMentionAutoReply INTEGER NOT NULL DEFAULT 1")
            }
            if (!hasColumn(db, "roleplay_scenarios", "maxGroupAutoReplies")) {
                db.execSQL("ALTER TABLE roleplay_scenarios ADD COLUMN maxGroupAutoReplies INTEGER NOT NULL DEFAULT 3")
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS roleplay_group_participants (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    assistantId TEXT NOT NULL,
                    displayNameOverride TEXT NOT NULL DEFAULT '',
                    avatarUriOverride TEXT NOT NULL DEFAULT '',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    isMuted INTEGER NOT NULL DEFAULT 0,
                    canAutoReply INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(scenarioId) REFERENCES roleplay_scenarios(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_group_participants_scenarioId ON roleplay_group_participants (scenarioId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_roleplay_group_participants_scenarioId_assistantId ON roleplay_group_participants (scenarioId, assistantId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_group_participants_scenarioId_sortOrder ON roleplay_group_participants (scenarioId, sortOrder)")
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_summary_segments (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL,
                    assistantId TEXT NOT NULL,
                    startMessageId TEXT NOT NULL,
                    endMessageId TEXT NOT NULL,
                    startCreatedAt INTEGER NOT NULL,
                    endCreatedAt INTEGER NOT NULL,
                    messageCount INTEGER NOT NULL,
                    summary TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_summary_segments_conversationId ON conversation_summary_segments (conversationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_summary_segments_assistantId ON conversation_summary_segments (assistantId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_summary_segments_conversationId_startCreatedAt ON conversation_summary_segments (conversationId, startCreatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_summary_segments_conversationId_endCreatedAt ON conversation_summary_segments (conversationId, endCreatedAt)")
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_conversation_summary_segments_conversationId_startMessageId_endMessageId
                ON conversation_summary_segments (conversationId, startMessageId, endMessageId)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "onlineReplyMinCount")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN onlineReplyMinCount INTEGER NOT NULL DEFAULT 1",
                )
            }
            if (!hasColumn(db, "roleplay_scenarios", "onlineReplyMaxCount")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN onlineReplyMaxCount INTEGER NOT NULL DEFAULT 3",
                )
            }
        }
    }

    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "enableOnlineProactiveReply")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN enableOnlineProactiveReply INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "presets", "compatMetadataJson")) {
                db.execSQL("ALTER TABLE presets ADD COLUMN compatMetadataJson TEXT NOT NULL DEFAULT '{}'")
            }
        }
    }

    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_messages_conversationId_createdAt_id
                ON messages (conversationId, createdAt, id)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS roleplay_scripts (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    scope TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    source TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    grantedPermissionsJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_scripts_scope_ownerId ON roleplay_scripts (scope, ownerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_scripts_enabled ON roleplay_scripts (enabled)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_scripts_updatedAt ON roleplay_scripts (updatedAt)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS roleplay_script_state (
                    scriptId TEXT NOT NULL,
                    stateKey TEXT NOT NULL,
                    stateValue TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(scriptId, stateKey),
                    FOREIGN KEY(scriptId) REFERENCES roleplay_scripts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_script_state_scriptId ON roleplay_script_state (scriptId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_roleplay_script_state_updatedAt ON roleplay_script_state (updatedAt)")
        }
    }

    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS moment_posts (
                    id TEXT NOT NULL PRIMARY KEY,
                    authorType TEXT NOT NULL,
                    authorId TEXT NOT NULL,
                    authorName TEXT NOT NULL,
                    authorAvatarUri TEXT NOT NULL,
                    authorLabel TEXT NOT NULL,
                    content TEXT NOT NULL,
                    likedByNamesJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_posts_createdAt ON moment_posts (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_posts_authorType_authorId ON moment_posts (authorType, authorId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS moment_comments (
                    id TEXT NOT NULL PRIMARY KEY,
                    postId TEXT NOT NULL,
                    authorType TEXT NOT NULL,
                    authorId TEXT NOT NULL,
                    authorName TEXT NOT NULL,
                    text TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(postId) REFERENCES moment_posts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_comments_postId ON moment_comments (postId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_comments_createdAt ON moment_comments (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_comments_authorType_authorId ON moment_comments (authorType, authorId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS moment_media (
                    id TEXT NOT NULL PRIMARY KEY,
                    postId TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    imageUri TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    status TEXT NOT NULL,
                    errorMessage TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(postId) REFERENCES moment_posts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_moment_media_postId ON moment_media (postId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_media_status ON moment_media (status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_moment_media_updatedAt ON moment_media (updatedAt)")
        }
    }

    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "moment_comments", "authorAvatarUri")) {
                db.execSQL(
                    "ALTER TABLE moment_comments ADD COLUMN authorAvatarUri TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "moment_posts", "location")) {
                db.execSQL(
                    "ALTER TABLE moment_posts ADD COLUMN location TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "roleplay_scenarios", "presetId")) {
                db.execSQL(
                    "ALTER TABLE roleplay_scenarios ADD COLUMN presetId TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }

    val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE roleplay_scenarios ADD COLUMN memoryPurpose TEXT NOT NULL DEFAULT 'legacy_compat'")
            db.execSQL("ALTER TABLE roleplay_sessions ADD COLUMN runId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN memoryRevision INTEGER NOT NULL DEFAULT 1")
            db.query("SELECT id FROM roleplay_sessions").use { cursor ->
                while (cursor.moveToNext()) {
                    db.execSQL(
                        "UPDATE roleplay_sessions SET runId = ? WHERE id = ?",
                        arrayOf(java.util.UUID.randomUUID().toString(), cursor.getString(0))
                    )
                }
            }
            createMemoryFoundationTables(db)
            migrateLegacyMemoryFoundation(db)
        }
    }

    private fun createMemoryFoundationTables(db: SupportSQLiteDatabase) {
        val spaceReference =
            "FOREIGN KEY(spaceId) REFERENCES memory_spaces(id) ON UPDATE NO ACTION ON DELETE NO ACTION"
        val recordReference =
            "FOREIGN KEY(recordId) REFERENCES memory_records(id) ON UPDATE NO ACTION ON DELETE CASCADE"
        createMemoryTable(
            db, "memory_spaces", """
            id TEXT NOT NULL PRIMARY KEY, identityKey TEXT NOT NULL, characterId TEXT NOT NULL, purpose TEXT NOT NULL,
            scenarioId TEXT NOT NULL, runId TEXT NOT NULL, generation INTEGER NOT NULL, status TEXT NOT NULL,
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        """, indexes = listOf("characterId"), uniqueIndexes = listOf("identityKey")
        )
        createMemoryTable(
            db, "memory_conversation_bindings", """
            conversationId TEXT NOT NULL, characterId TEXT NOT NULL, spaceId TEXT NOT NULL, purpose TEXT NOT NULL,
            scenarioId TEXT NOT NULL, runId TEXT NOT NULL, personaKey TEXT NOT NULL, archived INTEGER NOT NULL,
            createdAt INTEGER NOT NULL, PRIMARY KEY(conversationId, characterId), $spaceReference
        """, indexes = listOf("spaceId", "scenarioId")
        )
        createMemoryTable(
            db,
            "memory_records",
            """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, kind TEXT NOT NULL,
            category TEXT NOT NULL, content TEXT NOT NULL, revision INTEGER NOT NULL, status TEXT NOT NULL,
            importance INTEGER NOT NULL, pinned INTEGER NOT NULL, pinnedUntil INTEGER, occurredAt INTEGER,
            datePrecision TEXT NOT NULL, personaKey TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            lastUsedAt INTEGER NOT NULL, jobId TEXT, localItemId TEXT, legacyScopeType TEXT NOT NULL,
            legacyScopeId TEXT NOT NULL, legacySourceMessageId TEXT NOT NULL, $spaceReference
        """,
            indexes = listOf("spaceId,status", "characterId"),
            uniqueIndexes = listOf("jobId,localItemId")
        )
        createMemoryTable(
            db, "memory_record_revisions", """
            recordId TEXT NOT NULL, revision INTEGER NOT NULL, content TEXT NOT NULL, origin TEXT NOT NULL,
            createdAt INTEGER NOT NULL, PRIMARY KEY(recordId, revision), $recordReference
        """
        )
        createMemoryTable(
            db,
            "memory_source_evidence",
            """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, kind TEXT NOT NULL,
            conversationId TEXT NOT NULL, messageId TEXT, messageRevision INTEGER NOT NULL, speakerId TEXT NOT NULL,
            speakerRole TEXT NOT NULL, personaKey TEXT NOT NULL, visibleCharacterIdsJson TEXT NOT NULL,
            content TEXT NOT NULL, contentLength INTEGER NOT NULL, status TEXT NOT NULL, occurredAt INTEGER NOT NULL,
            ingestedAt INTEGER NOT NULL, contentFingerprint TEXT NOT NULL, $spaceReference
        """,
            indexes = listOf("spaceId", "conversationId,messageId"),
            uniqueIndexes = listOf("spaceId,messageId,messageRevision")
        )
        createMemoryTable(
            db, "memory_record_sources", """
            recordId TEXT NOT NULL, recordRevision INTEGER NOT NULL, sourceId TEXT NOT NULL, sourceRevision INTEGER NOT NULL,
            spanStart INTEGER NOT NULL, spanEnd INTEGER NOT NULL, PRIMARY KEY(recordId,recordRevision,sourceId,spanStart,spanEnd),
            FOREIGN KEY(recordId,recordRevision) REFERENCES memory_record_revisions(recordId,revision) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(sourceId) REFERENCES memory_source_evidence(id) ON UPDATE NO ACTION ON DELETE NO ACTION
        """, indexes = listOf("sourceId")
        )
        createMemoryTable(
            db, "memory_ingress_events", """
            sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, spaceId TEXT NOT NULL, sourceId TEXT NOT NULL,
            status TEXT NOT NULL, ingestedAt INTEGER NOT NULL,
            FOREIGN KEY(sourceId) REFERENCES memory_source_evidence(id) ON UPDATE NO ACTION ON DELETE NO ACTION
        """, indexes = listOf("spaceId,sequence"), uniqueIndexes = listOf("sourceId")
        )
        createMemoryTable(
            db, "memory_processing_jobs", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, conversationId TEXT NOT NULL,
            scenarioId TEXT NOT NULL, runId TEXT NOT NULL, personaKey TEXT NOT NULL, generation INTEGER NOT NULL,
            idempotencyKey TEXT NOT NULL, targetRevisionsJson TEXT NOT NULL, status TEXT NOT NULL, artifact TEXT NOT NULL,
            leaseOwner TEXT NOT NULL, leaseUntil INTEGER NOT NULL, failureCode TEXT NOT NULL, createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL, resultRecordIdsJson TEXT NOT NULL, cursorAfterCommit INTEGER NOT NULL, $spaceReference
        """, indexes = listOf("spaceId,status"), uniqueIndexes = listOf("spaceId,idempotencyKey")
        )
        createMemoryTable(
            db, "memory_job_sources", """
            jobId TEXT NOT NULL, sourceId TEXT NOT NULL, sourceRevision INTEGER NOT NULL,
            spanStart INTEGER NOT NULL, spanEnd INTEGER NOT NULL, PRIMARY KEY(jobId,sourceId,spanStart,spanEnd),
            FOREIGN KEY(jobId) REFERENCES memory_processing_jobs(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(sourceId) REFERENCES memory_source_evidence(id) ON UPDATE NO ACTION ON DELETE NO ACTION
        """, indexes = listOf("sourceId")
        )
        createMemoryTable(
            db, "memory_processing_cursors", """
            spaceId TEXT NOT NULL PRIMARY KEY, committedSequence INTEGER NOT NULL, historyBackfilled INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL, $spaceReference
        """
        )
        createMemoryTable(
            db, "memory_relation_intents", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL,
            fromRecordId TEXT NOT NULL, fromRevision INTEGER NOT NULL, targetRecordId TEXT NOT NULL, targetRevision INTEGER NOT NULL,
            label TEXT NOT NULL, eventName TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL,
            FOREIGN KEY(fromRecordId) REFERENCES memory_records(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(targetRecordId) REFERENCES memory_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
        """, indexes = listOf("fromRecordId", "targetRecordId", "spaceId,status")
        )
        createMemoryTable(
            db,
            "memory_engine_state",
            "id INTEGER NOT NULL PRIMARY KEY, engineVersion INTEGER NOT NULL, updatedAt INTEGER NOT NULL"
        )
        createMemoryTable(
            db, "memory_event_boxes", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, title TEXT NOT NULL,
            summary TEXT NOT NULL, summaryRevision INTEGER NOT NULL, summaryStatus TEXT NOT NULL, status TEXT NOT NULL,
            currentState TEXT NOT NULL, generation INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            $spaceReference
        """, indexes = listOf("spaceId,status")
        )
        createMemoryTable(
            db, "memory_event_members", """
            eventId TEXT NOT NULL, recordId TEXT NOT NULL, coveredRevision INTEGER, status TEXT NOT NULL,
            addedAt INTEGER NOT NULL, PRIMARY KEY(eventId,recordId), $recordReference,
            FOREIGN KEY(eventId) REFERENCES memory_event_boxes(id) ON UPDATE NO ACTION ON DELETE CASCADE
        """, indexes = listOf("recordId")
        )
        createMemoryTable(
            db, "memory_resident_entries", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, category TEXT NOT NULL,
            personaKey TEXT NOT NULL, content TEXT NOT NULL, revision INTEGER NOT NULL, status TEXT NOT NULL,
            subjective INTEGER NOT NULL, supersedesId TEXT, editedByUser INTEGER NOT NULL, createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL, $spaceReference
        """, indexes = listOf("spaceId,category,personaKey")
        )
        createMemoryTable(
            db, "memory_current_states", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, stateKey TEXT NOT NULL,
            personaKey TEXT NOT NULL, content TEXT NOT NULL, revision INTEGER NOT NULL, status TEXT NOT NULL,
            expiresAt INTEGER, endedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, $spaceReference
        """, uniqueIndexes = listOf("spaceId,stateKey,personaKey")
        )
        createMemoryTable(
            db, "memory_derived_links", """
            ownerKind TEXT NOT NULL, ownerId TEXT NOT NULL, recordId TEXT NOT NULL, recordRevision INTEGER NOT NULL,
            PRIMARY KEY(ownerKind,ownerId,recordId), $recordReference
        """, indexes = listOf("recordId")
        )
        createMemoryTable(
            db, "memory_embedding_indexes", """
            spaceId TEXT NOT NULL, vectorSpace TEXT NOT NULL, generation INTEGER NOT NULL, providerId TEXT NOT NULL,
            model TEXT NOT NULL, dimensions INTEGER NOT NULL, preprocessingVersion INTEGER NOT NULL, status TEXT NOT NULL,
            active INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(spaceId,vectorSpace,generation), $spaceReference
        """
        )
        createMemoryTable(
            db, "memory_embeddings", """
            recordId TEXT NOT NULL, recordRevision INTEGER NOT NULL, chunkIndex INTEGER NOT NULL, vectorSpace TEXT NOT NULL,
            generation INTEGER NOT NULL, spaceId TEXT NOT NULL, dimensions INTEGER NOT NULL, spanStart INTEGER NOT NULL,
            spanEnd INTEGER NOT NULL, vector BLOB NOT NULL, status TEXT NOT NULL, updatedAt INTEGER NOT NULL,
            PRIMARY KEY(recordId,recordRevision,chunkIndex,vectorSpace,generation), $recordReference
        """, indexes = listOf("spaceId,vectorSpace,generation")
        )
        createMemoryTable(
            db, "memory_recall_receipts", """
            requestId TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, conversationId TEXT NOT NULL,
            generation INTEGER NOT NULL, selectedJson TEXT NOT NULL, sentJson TEXT NOT NULL, omittedJson TEXT NOT NULL,
            payloadFingerprint TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, sentAt INTEGER, $spaceReference
        """, indexes = listOf("spaceId,createdAt")
        )
        createMemoryTable(
            db, "memory_proposals", """
            id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, characterId TEXT NOT NULL, conversationId TEXT NOT NULL,
            generation INTEGER NOT NULL, personaKey TEXT NOT NULL, content TEXT NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL,
            sourceRefsJson TEXT NOT NULL, status TEXT NOT NULL, finalRecordId TEXT, createdAt INTEGER NOT NULL,
            decidedAt INTEGER, $spaceReference
        """, indexes = listOf("spaceId,status")
        )
    }

    private fun createMemoryTable(
        db: SupportSQLiteDatabase,
        name: String,
        columns: String,
        indexes: List<String> = emptyList(),
        uniqueIndexes: List<String> = emptyList(),
    ) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `$name` (${columns.trimIndent()})")
        (indexes.map { it to false } + uniqueIndexes.map { it to true }).forEach { (fields, unique) ->
            val indexName = "index_${name}_${fields.replace(',', '_')}"
            db.execSQL("CREATE ${if (unique) "UNIQUE " else ""}INDEX IF NOT EXISTS `$indexName` ON `$name` ($fields)")
        }
    }

    private fun migrateLegacyMemoryFoundation(db: SupportSQLiteDatabase) {
        // 旧 GLOBAL 无角色记录仍可兼容读取；未知归属使用明确哨兵，不猜测为现实用户事实。
        val oldMemories = """
            SELECT m.*,
                CASE WHEN TRIM(m.characterId) != '' THEN m.characterId
                    WHEN m.scopeType = 'assistant' AND TRIM(m.scopeId) != '' THEN m.scopeId
                    ELSE 'legacy-unassigned' END AS resolvedOwner,
                CASE WHEN m.scopeType = 'conversation' THEN m.scopeId ELSE '' END AS resolvedConversation
            FROM memory_entries m
        """.trimIndent()
        val legacyKey = "'legacy:' || hex(resolvedOwner) || ':' || hex(resolvedConversation)"
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_spaces
                (id,identityKey,characterId,purpose,scenarioId,runId,generation,status,createdAt,updatedAt)
            SELECT $legacyKey, $legacyKey, resolvedOwner, 'legacy_compat', '', '', 1, 'ACTIVE', MIN(createdAt), MAX(updatedAt)
            FROM ($oldMemories) GROUP BY resolvedOwner,resolvedConversation
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_records
                (id,spaceId,characterId,kind,category,content,revision,status,importance,pinned,pinnedUntil,
                 occurredAt,datePrecision,personaKey,createdAt,updatedAt,lastUsedAt,jobId,localItemId,
                 legacyScopeType,legacyScopeId,legacySourceMessageId)
            SELECT id, $legacyKey, resolvedOwner, 'LEGACY_UNCLASSIFIED', 'DAILY', content, 1, 'ACTIVE',
                importance,pinned,NULL,NULL,'unknown','unknown',createdAt,updatedAt,lastUsedAt,NULL,NULL,
                scopeType,scopeId,sourceMessageId FROM ($oldMemories)
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_record_revisions(recordId,revision,content,origin,createdAt)
            SELECT id,1,content,'MIGRATION',updatedAt FROM memory_records
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_source_evidence
                (id,spaceId,characterId,kind,conversationId,messageId,messageRevision,speakerId,speakerRole,
                 personaKey,visibleCharacterIdsJson,content,contentLength,status,occurredAt,ingestedAt,contentFingerprint)
            SELECT 'legacy-source:' || r.id,r.spaceId,r.characterId,'LEGACY_HINT',
                CASE WHEN r.legacyScopeType = 'conversation' THEN r.legacyScopeId ELSE COALESCE(m.conversationId,'') END,
                NULL,0,'','','unknown','[]','',0,
                CASE WHEN m.id IS NULL THEN 'MISSING' ELSE 'UNKNOWN' END,r.createdAt,r.updatedAt,''
            FROM memory_records r LEFT JOIN messages m ON m.id = r.legacySourceMessageId
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_record_sources(recordId,recordRevision,sourceId,sourceRevision,spanStart,spanEnd)
            SELECT id,1,'legacy-source:' || id,0,0,0 FROM memory_records
        """.trimIndent()
        )
        // 仅迁移旧引擎自己生成的固定心境 ID；普通短句不按文本猜成当前状态。
        db.execSQL(
            """
            INSERT INTO memory_current_states
                (id,spaceId,characterId,stateKey,personaKey,content,revision,status,expiresAt,endedAt,createdAt,updatedAt)
            SELECT id,spaceId,characterId,'mood','unknown',SUBSTR(content,5),1,'ACTIVE',NULL,NULL,createdAt,updatedAt
            FROM memory_records WHERE legacyScopeType = 'conversation'
                AND id = 'mental-state:' || legacyScopeId AND SUBSTR(content,1,4) = '【心境】'
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_derived_links(ownerKind,ownerId,recordId,recordRevision)
            SELECT 'STATE',id,id,1 FROM memory_current_states
        """.trimIndent()
        )
        val oldOwners = """
            SELECT rs.conversationId,rs.scenarioId,rs.runId,rs.createdAt,s.assistantId AS characterId
            FROM roleplay_sessions rs JOIN roleplay_scenarios s ON s.id = rs.scenarioId
            WHERE s.chatType != 'group' AND TRIM(s.assistantId) != ''
            UNION
            SELECT rs.conversationId,rs.scenarioId,rs.runId,rs.createdAt,p.assistantId AS characterId
            FROM roleplay_sessions rs JOIN roleplay_scenarios s ON s.id = rs.scenarioId
            JOIN roleplay_group_participants p ON p.scenarioId = s.id
            WHERE s.chatType = 'group' AND TRIM(p.assistantId) != ''
        """.trimIndent()
        val bindingKey = "'legacy:' || hex(o.characterId) || ':' || hex(o.conversationId)"
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_spaces
                (id,identityKey,characterId,purpose,scenarioId,runId,generation,status,createdAt,updatedAt)
            SELECT $bindingKey,$bindingKey,o.characterId,'legacy_compat','','',1,'ACTIVE',o.createdAt,o.createdAt
            FROM ($oldOwners) o
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_conversation_bindings
                (conversationId,characterId,spaceId,purpose,scenarioId,runId,personaKey,archived,createdAt)
            SELECT o.conversationId,o.characterId,$bindingKey,'legacy_compat',o.scenarioId,o.runId,'unknown',
                CASE WHEN c.id IS NULL THEN 1 ELSE 0 END,o.createdAt FROM ($oldOwners) o
            LEFT JOIN conversations c ON c.id = o.conversationId
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memory_processing_cursors(spaceId,committedSequence,historyBackfilled,updatedAt)
            SELECT id,0,0,updatedAt FROM memory_spaces
        """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO memory_engine_state(id,engineVersion,updatedAt) VALUES (1,1,?)",
            arrayOf(System.currentTimeMillis())
        )
    }

    val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS wallet_accounts (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    ownerType TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    balanceCents INTEGER NOT NULL,
                    frozenCents INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_accounts_scenarioId ON wallet_accounts (scenarioId)")
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_wallet_accounts_scenarioId_ownerType_ownerId
                ON wallet_accounts (scenarioId, ownerType, ownerId)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS wallet_ledger_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    amountCents INTEGER NOT NULL,
                    balanceAfterCents INTEGER NOT NULL,
                    frozenAfterCents INTEGER NOT NULL,
                    relatedAccountId TEXT NOT NULL,
                    relatedShopItemId TEXT NOT NULL,
                    relatedInventoryItemId TEXT NOT NULL,
                    referenceId TEXT NOT NULL,
                    note TEXT NOT NULL,
                    failureReason TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_ledger_entries_scenarioId ON wallet_ledger_entries (scenarioId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_ledger_entries_accountId ON wallet_ledger_entries (accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_ledger_entries_createdAt ON wallet_ledger_entries (createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_ledger_entries_referenceId ON wallet_ledger_entries (referenceId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS shop_batches (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    style TEXT NOT NULL,
                    promptContext TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_batches_scenarioId ON shop_batches (scenarioId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_batches_createdAt ON shop_batches (createdAt)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS shop_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    batchId TEXT NOT NULL,
                    scenarioId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    priceCents INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    rarity TEXT NOT NULL,
                    effectPrompt TEXT NOT NULL,
                    imageStyle TEXT NOT NULL,
                    imagePrompt TEXT NOT NULL,
                    imageNegativePrompt TEXT NOT NULL,
                    imageStatus TEXT NOT NULL,
                    imageUri TEXT NOT NULL,
                    imageMimeType TEXT NOT NULL,
                    imageFileName TEXT NOT NULL,
                    imageError TEXT NOT NULL,
                    status TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_items_batchId ON shop_items (batchId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_items_scenarioId ON shop_items (scenarioId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_items_status ON shop_items (status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_items_imageStatus ON shop_items (imageStatus)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS inventory_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    scenarioId TEXT NOT NULL,
                    ownerType TEXT NOT NULL,
                    ownerId TEXT NOT NULL,
                    sourceShopItemId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    effectPrompt TEXT NOT NULL,
                    imageUri TEXT NOT NULL,
                    imageMimeType TEXT NOT NULL,
                    imageFileName TEXT NOT NULL,
                    status TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_scenarioId ON inventory_items (scenarioId)")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_inventory_items_scenarioId_ownerType_ownerId
                ON inventory_items (scenarioId, ownerType, ownerId)
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_sourceShopItemId ON inventory_items (sourceShopItemId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_status ON inventory_items (status)")
        }
    }

    val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE memory_source_evidence ADD COLUMN contentFormatVersion INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE memory_processing_jobs ADD COLUMN purpose TEXT NOT NULL DEFAULT 'AUTOMATIC_EXTRACTION'")
            db.execSQL("ALTER TABLE memory_processing_jobs ADD COLUMN requestJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_processing_jobs ADD COLUMN requestState TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("UPDATE memory_processing_jobs SET requestState = 'NOT_STARTED' WHERE status = 'PLANNED' AND artifact = ''")
            db.execSQL("ALTER TABLE memory_proposals ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_proposals ADD COLUMN importance INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE memory_proposals ADD COLUMN scenarioId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_proposals ADD COLUMN runId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_proposals ADD COLUMN scopeType TEXT NOT NULL DEFAULT 'assistant'")
            listOf("conversation_summaries", "conversation_summary_segments").forEach { table ->
                db.execSQL("ALTER TABLE $table ADD COLUMN sourceSnapshotJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE $table ADD COLUMN completionStatus TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE $table ADD COLUMN memoryContextJson TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("ALTER TABLE conversation_summary_segments ADD COLUMN isAggregate INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DROP INDEX index_conversation_summary_segments_conversationId_startMessageId_endMessageId")
            db.execSQL("CREATE UNIQUE INDEX index_conversation_summary_segments_conversationId_startMessageId_endMessageId ON conversation_summary_segments (conversationId, startMessageId, endMessageId, memoryContextJson, isAggregate)")
        }
    }

    val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN memoryRecallRequestId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_recall_receipts ADD COLUMN personaKey TEXT NOT NULL DEFAULT 'unknown'")
            listOf(
                "scenarioId",
                "runId",
                "anchorMessageId",
                "userMessageId",
                "failureCode"
            ).forEach { column ->
                db.execSQL("ALTER TABLE memory_recall_receipts ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("ALTER TABLE memory_recall_receipts ADD COLUMN transmissionsJson TEXT NOT NULL DEFAULT '[]'")
            // 旧索引保留原向量与元数据；未知身份不能被当成新运行的可用索引。
            db.execSQL(
                """
                CREATE TABLE memory_embedding_indexes_v51 (
                    spaceId TEXT NOT NULL, vectorSpace TEXT NOT NULL, generation INTEGER NOT NULL,
                    providerId TEXT NOT NULL, model TEXT NOT NULL, dimensions INTEGER NOT NULL,
                    preprocessingVersion INTEGER NOT NULL, status TEXT NOT NULL, active INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL, personaKey TEXT NOT NULL DEFAULT 'unknown',
                    configurationKey TEXT NOT NULL DEFAULT '', contextJson TEXT NOT NULL DEFAULT '',
                    recordsJson TEXT NOT NULL DEFAULT '[]', totalChunks INTEGER NOT NULL DEFAULT 0,
                    completedChunks INTEGER NOT NULL DEFAULT 0, pendingBatchJson TEXT NOT NULL DEFAULT '[]',
                    requestToken TEXT NOT NULL DEFAULT '', failureCode TEXT NOT NULL DEFAULT '',
                    fullRebuild INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(spaceId, vectorSpace, generation, personaKey),
                    FOREIGN KEY(spaceId) REFERENCES memory_spaces(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO memory_embedding_indexes_v51
                    (spaceId,vectorSpace,generation,providerId,model,dimensions,preprocessingVersion,status,active,updatedAt)
                SELECT spaceId,vectorSpace,generation,providerId,model,dimensions,preprocessingVersion,status,active,updatedAt
                FROM memory_embedding_indexes
            """.trimIndent()
            )
            db.execSQL("DROP TABLE memory_embedding_indexes")
            db.execSQL("ALTER TABLE memory_embedding_indexes_v51 RENAME TO memory_embedding_indexes")
        }
    }

    val MIGRATION_51_52 = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE memory_event_boxes ADD COLUMN personaKey TEXT NOT NULL DEFAULT 'unknown'")
            // 只有全部成员证明同一角色、空间与身份，才补全旧盒子的身份；不按名称猜测。
            db.execSQL(
                """
                UPDATE memory_event_boxes SET personaKey = (
                    SELECT MIN(r.personaKey) FROM memory_event_members m
                    JOIN memory_records r ON r.id = m.recordId WHERE m.eventId = memory_event_boxes.id
                ) WHERE EXISTS (SELECT 1 FROM memory_event_members m WHERE m.eventId = memory_event_boxes.id)
                AND NOT EXISTS (
                    SELECT 1 FROM memory_event_members m LEFT JOIN memory_records r ON r.id = m.recordId
                    WHERE m.eventId = memory_event_boxes.id AND (r.id IS NULL OR r.spaceId != memory_event_boxes.spaceId
                        OR r.characterId != memory_event_boxes.characterId
                        OR TRIM(r.personaKey, ' ' || CHAR(9) || CHAR(10) || CHAR(11) || CHAR(12) || CHAR(13)) IN ('', 'unknown'))
                ) AND 1 = (
                    SELECT COUNT(DISTINCT r.personaKey) FROM memory_event_members m
                    JOIN memory_records r ON r.id = m.recordId WHERE m.eventId = memory_event_boxes.id
                )
            """.trimIndent()
            )
            db.execSQL("ALTER TABLE memory_processing_jobs ADD COLUMN eventId TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX index_memory_processing_jobs_eventId_status ON memory_processing_jobs (eventId, status)")
        }
    }

    val MIGRATION_52_53 = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE memory_current_states ADD COLUMN editedByUser INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE memory_current_states ADD COLUMN observedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE memory_knowledge_revisions (
                    ownerKind TEXT NOT NULL, ownerId TEXT NOT NULL, revision INTEGER NOT NULL,
                    spaceId TEXT NOT NULL, content TEXT NOT NULL, status TEXT NOT NULL, origin TEXT NOT NULL,
                    subjective INTEGER NOT NULL, expiresAt INTEGER, endedAt INTEGER,
                    recordRefsJson TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerKind, ownerId, revision),
                    FOREIGN KEY(spaceId) REFERENCES memory_spaces(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_memory_knowledge_revisions_spaceId ON memory_knowledge_revisions(spaceId)")
            db.execSQL(
                """
                CREATE TABLE memory_knowledge_sources (
                    ownerKind TEXT NOT NULL, ownerId TEXT NOT NULL, ownerRevision INTEGER NOT NULL,
                    sourceId TEXT NOT NULL, sourceRevision INTEGER NOT NULL, spanStart INTEGER NOT NULL, spanEnd INTEGER NOT NULL,
                    PRIMARY KEY(ownerKind, ownerId, ownerRevision, sourceId, sourceRevision, spanStart, spanEnd),
                    FOREIGN KEY(ownerKind, ownerId, ownerRevision) REFERENCES memory_knowledge_revisions(ownerKind, ownerId, revision) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(sourceId) REFERENCES memory_source_evidence(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_memory_knowledge_sources_sourceId ON memory_knowledge_sources(sourceId)")
            db.execSQL(
                """
                CREATE TABLE memory_knowledge_consumptions (
                    jobId TEXT NOT NULL PRIMARY KEY, residentWrites INTEGER NOT NULL, stateWrites INTEGER NOT NULL,
                    skippedActions INTEGER NOT NULL, reasonCodesJson TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    FOREIGN KEY(jobId) REFERENCES memory_processing_jobs(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent()
            )
            // 原认知/状态保持原样；不替旧来源补造可用的正文或修订证明。
        }
    }

    val MIGRATION_53_54 = object : Migration(53, 54) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE memory_processing_jobs ADD COLUMN controlJobId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE memory_engine_state ADD COLUMN activationVersion INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE memory_processing_controls (
                    spaceId TEXT NOT NULL, jobId TEXT NOT NULL, userPaused INTEGER NOT NULL,
                    controlVersion INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(spaceId, jobId),
                    FOREIGN KEY(spaceId) REFERENCES memory_spaces(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE memory_transfer_origins (
                    id TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, kind TEXT NOT NULL,
                    recordId TEXT, sourceId TEXT, eventId TEXT, residentId TEXT, stateId TEXT,
                    localRevision INTEGER NOT NULL, originJson TEXT NOT NULL,
                    FOREIGN KEY(spaceId) REFERENCES memory_spaces(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(recordId) REFERENCES memory_records(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(sourceId) REFERENCES memory_source_evidence(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(eventId) REFERENCES memory_event_boxes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(residentId) REFERENCES memory_resident_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(stateId) REFERENCES memory_current_states(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
            )
            listOf(
                "spaceId",
                "recordId",
                "sourceId",
                "eventId",
                "residentId",
                "stateId"
            ).forEach { column ->
                db.execSQL("CREATE INDEX index_memory_transfer_origins_$column ON memory_transfer_origins($column)")
            }
            db.execSQL(
                """
                CREATE TABLE memory_transfer_receipts (
                    localPlanId TEXT NOT NULL PRIMARY KEY, bundleFingerprint TEXT NOT NULL,
                    targetsFingerprint TEXT NOT NULL, resultJson TEXT NOT NULL, createdAt INTEGER NOT NULL
                )
            """.trimIndent()
            )
        }
    }

    val MIGRATION_54_55 = object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "messages", "companionTurnId")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN companionTurnId TEXT NOT NULL DEFAULT ''")
            }
            if (!hasColumn(db, "messages", "companionMemoryState")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN companionMemoryState TEXT NOT NULL DEFAULT 'legacy'")
            }
            db.execSQL(
                """
                CREATE TABLE companion_encounters (
                    id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL,
                    scenarioId TEXT NOT NULL, runId TEXT NOT NULL, spaceId TEXT NOT NULL,
                    characterId TEXT NOT NULL, personaKey TEXT NOT NULL, generation INTEGER NOT NULL,
                    openingMode TEXT NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL,
                    activeKey TEXT, createdAt INTEGER NOT NULL, endedAt INTEGER,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_companion_encounters_conversationId_createdAt_id ON companion_encounters(conversationId, createdAt, id)")
            db.execSQL("CREATE UNIQUE INDEX index_companion_encounters_activeKey ON companion_encounters(activeKey)")
            db.execSQL("CREATE INDEX index_companion_encounters_characterId_personaKey_createdAt_id ON companion_encounters(characterId, personaKey, createdAt, id)")
            db.execSQL(
                """
                CREATE TABLE companion_turns (
                    id TEXT NOT NULL PRIMARY KEY, encounterId TEXT NOT NULL, ordinal INTEGER NOT NULL,
                    kind TEXT NOT NULL, inputMessageId TEXT, replyMessageId TEXT,
                    inputRevision INTEGER, replyRevision INTEGER, mutationRevision INTEGER NOT NULL,
                    committedRevision INTEGER NOT NULL, selectedAttemptId TEXT, activeAttemptId TEXT,
                    createdAt INTEGER NOT NULL, editedAt INTEGER,
                    FOREIGN KEY(encounterId) REFERENCES companion_encounters(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX index_companion_turns_encounterId_ordinal ON companion_turns(encounterId, ordinal)")
            db.execSQL("CREATE UNIQUE INDEX index_companion_turns_inputMessageId ON companion_turns(inputMessageId)")
            db.execSQL("CREATE UNIQUE INDEX index_companion_turns_replyMessageId ON companion_turns(replyMessageId)")
            db.execSQL(
                """
                CREATE TABLE companion_attempts (
                    id TEXT NOT NULL PRIMARY KEY, turnId TEXT NOT NULL,
                    expectedTurnRevision INTEGER NOT NULL, expectedInputRevision INTEGER,
                    expectedReplyRevision INTEGER, frozenRuntimeJson TEXT NOT NULL,
                    requestDescriptorJson TEXT NOT NULL, requestFingerprint TEXT NOT NULL,
                    state TEXT NOT NULL, claimToken TEXT, leaseUntil INTEGER, requestedAt INTEGER,
                    receivedAt INTEGER, artifactJson TEXT, artifactHash TEXT,
                    FOREIGN KEY(turnId) REFERENCES companion_turns(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_companion_attempts_turnId ON companion_attempts(turnId)")
            db.execSQL("CREATE INDEX index_companion_attempts_state_leaseUntil ON companion_attempts(state, leaseUntil)")
        }
    }

    val MIGRATION_55_56 = object : Migration(55, 56) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE companion_voice_resources (
                    id TEXT NOT NULL PRIMARY KEY, requestKey TEXT NOT NULL, cacheKey TEXT,
                    state TEXT NOT NULL, claimToken TEXT, leaseUntil INTEGER,
                    path TEXT NOT NULL, mimeType TEXT NOT NULL, sha256 TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL, clipJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, lastUsedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_companion_voice_resources_cacheKey ON companion_voice_resources(cacheKey)")
            db.execSQL("CREATE INDEX index_companion_voice_resources_state_lastUsedAt_id ON companion_voice_resources(state, lastUsedAt, id)")
            db.execSQL("""
                CREATE TABLE companion_voice_favorites (
                    id TEXT NOT NULL PRIMARY KEY, audioId TEXT NOT NULL, characterId TEXT NOT NULL,
                    sourceMessageId TEXT NOT NULL, sourceRevision INTEGER NOT NULL,
                    clipJson TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    FOREIGN KEY(audioId) REFERENCES companion_voice_resources(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_companion_voice_favorites_audioId ON companion_voice_favorites(audioId)")
            db.execSQL("CREATE INDEX index_companion_voice_favorites_characterId_createdAt_id ON companion_voice_favorites(characterId, createdAt, id)")
            db.execSQL("CREATE INDEX index_companion_voice_favorites_sourceMessageId_sourceRevision ON companion_voice_favorites(sourceMessageId, sourceRevision)")
        }
    }

    val MIGRATION_56_57 = object : Migration(56, 57) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE context_transfer_operations (
                    id TEXT NOT NULL PRIMARY KEY, planVersion INTEGER NOT NULL,
                    kind TEXT NOT NULL, stage TEXT NOT NULL, revision INTEGER NOT NULL,
                    planHash TEXT NOT NULL, settingsTargetCount INTEGER NOT NULL,
                    completedSettingsJson TEXT NOT NULL, publishedImagesJson TEXT NOT NULL,
                    memoryResultJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_context_transfer_operations_kind_stage_createdAt_id ON context_transfer_operations(kind, stage, createdAt, id)")
        }
    }

    /** 版本连续性由 `ChatDatabaseMigrationRegistryTest` 保证：`size == CURRENT_VERSION - 1`。 */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45,
        MIGRATION_45_46,
        MIGRATION_46_47,
        MIGRATION_47_48,
        MIGRATION_48_49,
        MIGRATION_49_50,
        MIGRATION_50_51,
        MIGRATION_51_52,
        MIGRATION_52_53,
        MIGRATION_53_54,
        MIGRATION_54_55,
        MIGRATION_55_56,
        MIGRATION_56_57,
    )

    /** 幂等列检查。子迁移在 `ALTER TABLE ADD COLUMN` 之前先探测，允许中间版本重复升级。 */
    internal fun hasColumn(
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
