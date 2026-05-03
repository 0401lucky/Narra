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
