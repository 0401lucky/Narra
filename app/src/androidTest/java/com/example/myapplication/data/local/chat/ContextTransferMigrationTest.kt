package com.example.myapplication.data.local.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ContextTransferMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "d6-migration-${UUID.randomUUID().toString().take(8)}.db"

    @After
    fun cleanup() {
        context.deleteDatabase(name)
    }

    @Test
    fun fixed56To57_preservesEveryOldTableAndIndependentAudioAndNewReceipts() {
        val bytes =
            InstrumentationRegistry.getInstrumentation().context.assets.open("com.example.myapplication.data.local.chat.ChatDatabase/56.json")
                .use { it.readBytes() }
        assertEquals(
            "d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7",
            sha256(bytes)
        )
        val schema = AppJson.gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
            .getAsJsonObject("database")
        assertEquals(56, schema["version"].asInt)
        assertEquals("37d3992a3b6f8544d71ead1e129bccef", schema["identityHash"].asString)
        val entities = schema.getAsJsonArray("entities").map { it.asJsonObject }
        assertEquals(58, entities.size)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        lateinit var before: Map<String, List<List<String?>>>
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            entities.forEach { entity ->
                val table = entity["tableName"].asString
                db.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach {
                    db.execSQL(
                        it.asJsonObject["createSql"].asString.replace(
                            "\${TABLE_NAME}",
                            table
                        )
                    )
                }
            }
            schema.getAsJsonArray("views")?.forEach {
                db.execSQL(
                    it.asJsonObject["createSql"].asString.replace(
                        "\${VIEW_NAME}",
                        it.asJsonObject["viewName"].asString
                    )
                )
            }
            schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            fun row(table: String, values: Map<String, Any?>) =
                insert(db, entities.single { it["tableName"].asString == table }, values)
            row(
                "conversations",
                mapOf("id" to "c56", "assistantId" to "alice", "title" to "合成旧资料")
            )
            row(
                "messages",
                mapOf(
                    "id" to "legacy56",
                    "conversationId" to "c56",
                    "role" to "USER",
                    "content" to " 保留全部\r\n旧原文🙂 ".repeat(140),
                    "memoryRevision" to 9L,
                    "companionMemoryState" to "legacy"
                )
            )
            row(
                "messages",
                mapOf(
                    "id" to "reply56",
                    "conversationId" to "c56",
                    "role" to "ASSISTANT",
                    "content" to "<dialogue sprite=\"happy\">旧台词</dialogue>",
                    "memoryRevision" to 17L,
                    "companionTurnId" to "t56",
                    "companionMemoryState" to "ready"
                )
            )
            row(
                "companion_encounters",
                mapOf(
                    "id" to "e56",
                    "conversationId" to "c56",
                    "characterId" to "alice",
                    "personaKey" to "persona56",
                    "state" to "ENDED"
                )
            )
            row(
                "companion_turns",
                mapOf(
                    "id" to "t56",
                    "encounterId" to "e56",
                    "ordinal" to 1L,
                    "kind" to "INPUT",
                    "replyMessageId" to "reply56",
                    "replyRevision" to 17L,
                    "selectedAttemptId" to "a56"
                )
            )
            row(
                "companion_attempts",
                mapOf(
                    "id" to "a56",
                    "turnId" to "t56",
                    "state" to "COMMITTED",
                    "artifactJson" to "{\"rawReply\":\"旧产物\"}",
                    "artifactHash" to "synthetic-preserved"
                )
            )
            row(
                "memory_spaces",
                mapOf(
                    "id" to "space56",
                    "identityKey" to "fixed56-space",
                    "characterId" to "alice",
                    "status" to "ACTIVE"
                )
            )
            row(
                "memory_records",
                mapOf(
                    "id" to "manual56",
                    "spaceId" to "space56",
                    "characterId" to "alice",
                    "content" to "独立人工记忆",
                    "revision" to 3L,
                    "status" to "ACTIVE"
                )
            )
            row(
                "companion_voice_resources",
                mapOf(
                    "id" to "audio56",
                    "requestKey" to "synthetic-only",
                    "cacheKey" to "audio-key",
                    "state" to "READY",
                    "path" to "synthetic-local.wav",
                    "sha256" to "f".repeat(64),
                    "clipJson" to "{\"source\":\"kept\"}"
                )
            )
            row(
                "companion_voice_favorites",
                mapOf(
                    "id" to "favorite56",
                    "audioId" to "audio56",
                    "characterId" to "alice",
                    "sourceMessageId" to "reply56",
                    "sourceRevision" to 17L,
                    "clipJson" to "{\"rawText\":\"独立声音原文\"}"
                )
            )
            db.version = 56
            before = snapshot(db, entities)
        }
        openRoom()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertEquals(57L, scalar(db, "PRAGMA user_version"))
            assertEquals(before, snapshot(db, entities))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM context_transfer_operations"))
            assertEquals(
                1L,
                scalar(
                    db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_context_transfer_operations_kind_stage_createdAt_id'"
                )
            )
            db.execSQL(
                "INSERT INTO context_transfer_operations VALUES ('op56', 1, 'PRIVATE_LIBRARY', 'ROOM_COMMITTED', 2, ?, 1, '[]', '[]', '', 13, 14)",
                arrayOf("a".repeat(64))
            )
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM messages WHERE id = 'reply56'")
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM companion_voice_favorites"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM memory_records"))
            try {
                db.execSQL("DELETE FROM companion_voice_resources WHERE id = 'audio56'"); fail("收藏仍引用的音频不得删除")
            } catch (_: SQLiteConstraintException) {
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
        openRoom()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(
                1L,
                scalar(
                    db,
                    "SELECT COUNT(*) FROM context_transfer_operations WHERE id='op56' AND stage='ROOM_COMMITTED' AND revision=2"
                )
            )
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM companion_voice_favorites"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM memory_records"))
        }
    }

    private fun openRoom() = Room.databaseBuilder(context, ChatDatabase::class.java, name)
        .addMigrations(*ChatDatabase.ALL_MIGRATIONS).build().let { db ->
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun scalar(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use { it.moveToFirst(); it.getLong(0) }

    private fun insert(db: SQLiteDatabase, entity: JsonObject, overrides: Map<String, Any?>) {
        val values = ContentValues()
        entity.getAsJsonArray("fields").forEach { element ->
            val field = element.asJsonObject
            val column = field["columnName"].asString
            val value =
                if (column in overrides) overrides[column] else if (field.get("notNull")?.asBoolean != true) null else if (field["affinity"].asString == "TEXT") "" else 0L
            when (value) {
                null -> values.putNull(column); is String -> values.put(
                column,
                value
            ); is Long -> values.put(column, value); else -> error("合成固定56字段类型无效")
            }
        }
        assertTrue(db.insertOrThrow(entity["tableName"].asString, null, values) != -1L)
    }

    private fun snapshot(
        db: SQLiteDatabase,
        entities: List<JsonObject>
    ): Map<String, List<List<String?>>> = entities.associate { entity ->
        val table = entity["tableName"].asString
        val columns = entity.getAsJsonArray("fields").map { it.asJsonObject["columnName"].asString }
        val select = columns.joinToString(",") { "`$it`" }
        table to db.rawQuery("SELECT $select FROM `$table` ORDER BY $select", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(columns.indices.map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                })
            }
        }
    }
}
