package com.example.myapplication.data.local.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.memory.MemoryProcessingControlEntity
import com.example.myapplication.data.local.memory.MemoryTransferOriginEntity
import com.example.myapplication.data.local.memory.MemoryTransferReceiptEntity
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemoryManagementMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "m6-${UUID.randomUUID().toString().take(8)}.db"

    @After
    fun cleanup() {
        context.deleteDatabase(name)
    }

    @Test
    fun fixed53ToLatest_preservesPaidJobAndEnforcesNewTables() = runBlocking {
        create53()
        withDatabase { db ->
            assertTrue(ChatDatabase.CURRENT_VERSION >= 54)
            assertEquals(ChatDatabase.CURRENT_VERSION, db.openHelper.writableDatabase.version)
            val job = requireNotNull(db.memoryArchiveDao().getJob("paid"))
            assertEquals("saved-completion", job.artifact)
            assertEquals("frozen-request", job.requestJson)
            assertEquals("STARTED", job.requestState)
            assertEquals("", job.controlJobId)
            assertNull(db.memoryArchiveDao().getProcessingControl("space", ""))
            db.memoryArchiveDao()
                .upsertProcessingControl(MemoryProcessingControlEntity("space", "", true, 1, 100))
            assertTrue(db.memoryArchiveDao().isProcessingPaused("space", "paid"))
            assertTrue(runCatching {
                db.memoryArchiveDao().upsertProcessingControl(
                    MemoryProcessingControlEntity(
                        "missing",
                        "",
                        true,
                        1,
                        100
                    )
                )
            }.isFailure)
            val origin = MemoryTransferOriginEntity(
                "origin",
                "space",
                "SPACE",
                null,
                null,
                null,
                null,
                null,
                0,
                "{}"
            )
            db.memoryTransferDao().insertOrigins(listOf(origin))
            assertTrue(runCatching {
                db.memoryTransferDao()
                    .insertOrigins(listOf(origin.copy(id = "bad", recordId = "missing-record")))
            }.isFailure)
            db.memoryTransferDao().insertReceipt(
                MemoryTransferReceiptEntity(
                    "plan",
                    "bundle-hash",
                    "target-hash",
                    "{}",
                    100
                )
            )
            assertEquals(
                "target-hash",
                db.memoryTransferDao().getReceipt("plan")?.targetsFingerprint
            )
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
                .use { assertFalse(it.moveToFirst()) }
        }
        withDatabase { db ->
            assertTrue(
                requireNotNull(
                    db.memoryArchiveDao().getProcessingControl("space", "")
                ).userPaused
            )
            assertEquals("saved-completion", db.memoryArchiveDao().getJob("paid")?.artifact)
        }
    }

    @Test
    fun activation_doesNotRestartMigratedJobsOrInventHistory() = runBlocking {
        create53()
        withDatabase { db ->
            val archive = RoomMemoryArchiveRepository(db, { 200 }, { it == "character" })
            assertEquals(2, archive.getEngineVersion())
            assertEquals(2, archive.getEngineVersion())
            val job = requireNotNull(db.memoryArchiveDao().getJob("paid"))
            assertEquals("saved-completion", job.artifact)
            assertEquals("STARTED", job.requestState)
            assertEquals("EXTRACTED", job.status)
            assertEquals(1, db.memoryArchiveDao().listSpaceJobs("space").size)
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM memory_ingress_events")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
                }
        }
    }

    private fun create53() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.myapplication.data.local.chat.ChatDatabase/53.json")
            .use { it.readBytes() }
        assertEquals(
            "f48f8a4cccb022a367a047b63625e3c81bfb2e6cecb220eb73e6899596b864a8",
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) })
        val schema = AppJson.gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
            .getAsJsonObject("database")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getAsJsonArray("entities").forEach { item ->
                val entity = item.asJsonObject
                val table = entity.get("tableName").asString
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
            schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            db.execSQL("INSERT INTO memory_spaces VALUES ('space','synthetic-identity','character','continuous','','',1,'ACTIVE',100,100)")
            val values = ContentValues().apply {
                put("id", "paid"); put("spaceId", "space"); put("characterId", "character")
                put("conversationId", "old-conversation"); put(
                "scenarioId",
                "old-scenario"
            ); put("runId", "old-run")
                put("personaKey", "unknown"); put("generation", 1); put(
                "idempotencyKey",
                "paid-once"
            )
                put("targetRevisionsJson", "{}"); put("status", "EXTRACTED"); put(
                "artifact",
                "saved-completion"
            )
                put("leaseOwner", "old-owner"); put("leaseUntil", 150); put("failureCode", "")
                put("createdAt", 100); put("updatedAt", 100); put("resultRecordIdsJson", "[]"); put(
                "cursorAfterCommit",
                0
            )
                put("purpose", "AUTOMATIC_EXTRACTION"); put(
                "requestJson",
                "frozen-request"
            ); put("requestState", "STARTED"); put("eventId", "")
            }
            db.insertOrThrow("memory_processing_jobs", null, values)
            db.execSQL("INSERT INTO memory_engine_state VALUES (1,1,100)")
            db.version = 53
        }
    }

    private fun open(): ChatDatabase = Room.databaseBuilder(context, ChatDatabase::class.java, name)
        .addMigrations(*ChatDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        .also { it.openHelper.writableDatabase }

    private suspend fun withDatabase(block: suspend (ChatDatabase) -> Unit) {
        val database = open()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
