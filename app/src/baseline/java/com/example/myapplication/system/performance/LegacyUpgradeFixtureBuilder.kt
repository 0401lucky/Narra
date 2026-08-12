package com.example.myapplication.system.performance

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.myapplication.data.local.chat.ChatDatabase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class LegacyUpgradeFixtureBuilder(
    private val context: Context,
) {
    fun create(dataset: FixtureDataset): FixtureSafeSummary {
        require(dataset.fixtureId == BenchmarkFixtureId.S3_SUPPORTED_UPGRADE) {
            "旧版本 fixture 仅允许 S3"
        }
        verifyUpgradeContract()
        context.deleteDatabase(BaselineFixturePreparer.CHAT_DATABASE_NAME)
        val schemaStatements = verifiedSchemaStatements()
        val callback = object : SupportSQLiteOpenHelper.Callback(MEASURED_SOURCE_SCHEMA_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schemaStatements.forEach(db::execSQL)
                writeDataset(db, dataset)
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) {
                error("旧版本 fixture 不应通过 SQLiteOpenHelper onUpgrade 构造")
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(BaselineFixturePreparer.CHAT_DATABASE_NAME)
                .callback(callback)
                .build(),
        )
        return try {
            val database = helper.writableDatabase
            check(database.version == MEASURED_SOURCE_SCHEMA_VERSION) {
                "旧版本 fixture 数据库版本不一致"
            }
            check(readRoomIdentityHash(database) == SOURCE_SCHEMA_IDENTITY_HASH) {
                "旧版本 fixture Room identity hash 不一致"
            }
            val actual = dataset.copy(
                chatMessages = readMessages(database, BenchmarkFixtureIds.CHAT_CONVERSATION_ID),
                roleplayMessages = readMessages(database, BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID),
            )
            val expectedSummary = dataset.safeSummary()
            val actualSummary = actual.safeSummary()
            check(actualSummary == expectedSummary) { "旧版本 fixture 数据摘要不一致" }
            actualSummary
        } finally {
            helper.close()
        }
    }

    private fun verifyUpgradeContract() {
        check(
            BenchmarkFixtureId.S3_SUPPORTED_UPGRADE.sourceDatabaseVersion ==
                MEASURED_SOURCE_SCHEMA_VERSION,
        ) { "S3 fixture 来源版本与固定快照不一致" }
        check(ChatDatabase.CURRENT_VERSION == MEASURED_TARGET_SCHEMA_VERSION) {
            "S3 fixture 目标版本已变化，必须更新固定快照与升级区间"
        }
        check(MEASURED_TARGET_SCHEMA_VERSION == MEASURED_SOURCE_SCHEMA_VERSION + 1) {
            "S3 fixture 仅允许测量一个明确的相邻版本升级区间"
        }
    }

    private fun verifiedSchemaStatements(): List<String> {
        val bytes = context.assets.open(SCHEMA_ASSET_PATH).use { it.readBytes() }
        val actualSha256 = bytes.sha256()
        check(actualSha256 == SCHEMA_ASSET_SHA256) { "旧版本 fixture schema 校验失败" }
        val statements = bytes.toString(StandardCharsets.UTF_8)
            .lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("--") }
            .map { line ->
                check(line.endsWith(';')) { "旧版本 fixture schema 存在不完整语句" }
                line.removeSuffix(";")
            }
            .toList()
        check(statements.isNotEmpty()) { "旧版本 fixture schema 为空" }
        return statements
    }

    private fun writeDataset(
        db: SupportSQLiteDatabase,
        dataset: FixtureDataset,
    ) {
        insertOrThrow(
            db = db,
            table = "conversations",
            values = conversationValues(
                id = BenchmarkFixtureIds.CHAT_CONVERSATION_ID,
                title = "普通聊天升级基准",
                updatedAt = dataset.chatMessages.lastOrNull()?.createdAtEpochMillis
                    ?.plus(CHAT_CONVERSATION_RECENCY_OFFSET_MILLIS),
            ),
        )
        insertOrThrow(
            db = db,
            table = "conversations",
            values = conversationValues(
                id = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                title = "角色扮演升级基准",
                updatedAt = dataset.roleplayMessages.lastOrNull()?.createdAtEpochMillis,
            ),
        )
        dataset.chatMessages.forEach { message ->
            insertOrThrow(db, "messages", messageValues(message))
        }
        dataset.roleplayMessages.forEach { message ->
            insertOrThrow(db, "messages", messageValues(message))
        }
        insertOrThrow(db, "roleplay_scenarios", roleplayScenarioValues())
        insertOrThrow(db, "roleplay_group_participants", roleplayParticipantValues())
        insertOrThrow(db, "roleplay_sessions", roleplaySessionValues())
    }

    private fun conversationValues(
        id: String,
        title: String,
        updatedAt: Long?,
    ): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("title", title)
            put("model", "")
            put("createdAt", BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS)
            put("updatedAt", updatedAt ?: BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS)
            put("assistantId", BenchmarkFixtureIds.ASSISTANT_ID)
            put("searchEnabled", 0)
        }
    }

    private fun messageValues(message: FixtureMessageRecord): ContentValues {
        return ContentValues().apply {
            put("id", message.id)
            put("conversationId", message.conversationId)
            put("role", message.role)
            put("content", message.content)
            put("status", message.status)
            put("createdAt", message.createdAtEpochMillis)
            put("modelName", "")
            put("reasoningContent", "")
            put("reasoningStepsJson", "[]")
            put("attachmentsJson", message.attachmentsJson)
            put("partsJson", message.partsJson)
            put("replyToMessageId", "")
            put("replyToPreview", "")
            put("replyToSpeakerName", "")
            put("speakerId", message.speakerId)
            put("speakerName", message.speakerName)
            put("speakerAvatarUri", message.speakerAvatarUri)
            put("isRecalled", 0)
            put("systemEventKind", "none")
            put("citationsJson", "[]")
            put("roleplayOutputFormat", "UNSPECIFIED")
            put("roleplayInteractionMode", message.roleplayInteractionMode)
        }
    }

    private fun roleplayScenarioValues(): ContentValues {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return ContentValues().apply {
            put("id", BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID)
            put("title", "角色扮演升级基准场景")
            put("description", "固定离线升级性能验证场景")
            put("descriptionPromptEnabled", 0)
            put("assistantId", BenchmarkFixtureIds.ASSISTANT_ID)
            put("backgroundUri", "")
            put("userDisplayNameOverride", "基准用户")
            put("userPersonaMaskId", "")
            put("userPersonaOverride", "")
            put("userPortraitUri", "")
            put("userPortraitUrl", "")
            put("characterDisplayNameOverride", "基准角色")
            put("characterPortraitUri", "")
            put("characterPortraitUrl", "")
            put("openingNarration", "")
            put("interactionMode", "offline_dialogue")
            put("enableNarration", 1)
            put("enableRoleplayProtocol", 1)
            put("longformModeEnabled", 0)
            put("autoHighlightSpeaker", 1)
            put("enableDeepImmersion", 0)
            put("enableTimeAwareness", 1)
            put("enableNetMeme", 0)
            put("enableOnlineProactiveReply", 0)
            put("chatType", "single")
            put("groupReplyMode", "natural")
            put("enableGroupMentionAutoReply", 1)
            put("maxGroupAutoReplies", 3)
            put("onlineReplyMinCount", 1)
            put("onlineReplyMaxCount", 3)
            put("isPinned", 0)
            put("isMuted", 0)
            put("createdAt", timestamp)
            put("updatedAt", timestamp)
        }
    }

    private fun roleplayParticipantValues(): ContentValues {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return ContentValues().apply {
            put("id", "benchmark-roleplay-participant-v1")
            put("scenarioId", BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID)
            put("assistantId", BenchmarkFixtureIds.ASSISTANT_ID)
            put("displayNameOverride", "基准角色")
            put("avatarUriOverride", "")
            put("sortOrder", 0)
            put("isMuted", 0)
            put("canAutoReply", 1)
            put("createdAt", timestamp)
            put("updatedAt", timestamp)
        }
    }

    private fun roleplaySessionValues(): ContentValues {
        val timestamp = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS
        return ContentValues().apply {
            put("id", BenchmarkFixtureIds.ROLEPLAY_SESSION_ID)
            put("scenarioId", BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID)
            put("conversationId", BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID)
            put("createdAt", timestamp)
            put("updatedAt", timestamp)
        }
    }

    private fun insertOrThrow(
        db: SupportSQLiteDatabase,
        table: String,
        values: ContentValues,
    ) {
        val rowId = db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
        check(rowId != -1L) { "旧版本 fixture 写入失败：$table" }
    }

    private fun readMessages(
        db: SupportSQLiteDatabase,
        conversationId: String,
    ): List<FixtureMessageRecord> {
        return db.query(
            """
            SELECT id, conversationId, role, content, status, createdAt,
                   attachmentsJson, partsJson, speakerId, speakerName,
                   speakerAvatarUri, roleplayInteractionMode
            FROM messages
            WHERE conversationId = ?
            ORDER BY createdAt ASC, id ASC
            """.trimIndent(),
            arrayOf(conversationId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        FixtureMessageRecord(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            conversationId = cursor.getString(cursor.getColumnIndexOrThrow("conversationId")),
                            role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                            createdAtEpochMillis = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                            attachmentsJson = cursor.getString(cursor.getColumnIndexOrThrow("attachmentsJson")),
                            partsJson = cursor.getString(cursor.getColumnIndexOrThrow("partsJson")),
                            speakerId = cursor.getString(cursor.getColumnIndexOrThrow("speakerId")),
                            speakerName = cursor.getString(cursor.getColumnIndexOrThrow("speakerName")),
                            speakerAvatarUri = cursor.getString(cursor.getColumnIndexOrThrow("speakerAvatarUri")),
                            roleplayInteractionMode = cursor.getString(
                                cursor.getColumnIndexOrThrow("roleplayInteractionMode"),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun readRoomIdentityHash(db: SupportSQLiteDatabase): String? {
        return db.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val MEASURED_SOURCE_SCHEMA_VERSION = 47
        const val MEASURED_TARGET_SCHEMA_VERSION = 48
        const val SCHEMA_ASSET_PATH = "benchmark/s3-schema-v47.sql"
        const val SCHEMA_ASSET_SHA256 = "cc4a60c82d5a3ae33949937622704b1fa3c4223b6782e67cc6ae9a64a05f109b"
        const val SOURCE_SCHEMA_IDENTITY_HASH = "d9407f25f48657b4ed1e52c84c1c8b12"
        private const val CHAT_CONVERSATION_RECENCY_OFFSET_MILLIS = 1_000L
    }
}
