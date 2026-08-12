package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalExitDiagnosticsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun merge_deduplicatesAndEvictsOldestByRecordLimit() {
        val store = LocalExitDiagnosticsStore(
            directory = temporaryFolder.newFolder("diagnostics"),
            maxRecords = 2,
        )

        store.merge(listOf(event(safeId('a'), 100L), event(safeId('b'), 200L)))
        val result = store.merge(listOf(event(safeId('b'), 200L), event(safeId('c'), 300L)))

        assertTrue(result.persisted)
        assertEquals(listOf(safeId('b'), safeId('c')), store.readEvents().map { it.eventId })
    }

    @Test
    fun merge_duplicateEventKeepsFirstObservationMetadata() {
        val store = LocalExitDiagnosticsStore(
            directory = temporaryFolder.newFolder("first-observation"),
        )
        val firstObservation = event(safeId('a'), 100L).copy(
            observedTimestampEpochMillis = 150L,
            appVersionName = "2.2.5-dev",
            appVersionCode = 20205L,
        )
        val observedAgainAfterUpgrade = firstObservation.copy(
            observedTimestampEpochMillis = 9_000L,
            appVersionName = "2.2.6-dev",
            appVersionCode = 20206L,
        )

        store.merge(listOf(firstObservation))
        store.merge(listOf(observedAgainAfterUpgrade))

        assertEquals(firstObservation, store.readEvents().single())
    }

    @Test
    fun merge_evictsOldestUntilSerializedFileFitsByteLimit() {
        val directory = temporaryFolder.newFolder("byte-limit")
        val store = LocalExitDiagnosticsStore(
            directory = directory,
            maxRecords = 20,
            maxBytes = 720,
        )

        store.merge((1..8).map { index -> event(index.toString().repeat(64), index * 100L) })

        val stored = store.readEvents()
        assertTrue(stored.isNotEmpty())
        assertEquals(800L, stored.last().exitTimestampEpochMillis)
        assertTrue(store.diagnosticsFile.length() <= 720L)
    }

    @Test
    fun readEvents_discardsOneMalformedEntryAndKeepsValidEntries() {
        val directory = temporaryFolder.newFolder("malformed-entry")
        val store = LocalExitDiagnosticsStore(directory = directory)
        store.merge(listOf(event(safeId('a'), 100L)))
        val root = JsonParser.parseString(store.diagnosticsFile.readText()).asJsonObject
        root.getAsJsonArray("events").add(
            JsonObject().apply {
                addProperty("schemaVersion", 1)
                addProperty("eventId", "invalid")
            },
        )
        store.diagnosticsFile.writeText(AppJson.gson.toJson(root))

        val events = store.readEvents()

        assertEquals(listOf(safeId('a')), events.map { it.eventId })
    }

    @Test
    fun readEvents_validFileOverRecordLimitIsDeduplicatedTrimmedAndRewritten() {
        val directory = temporaryFolder.newFolder("recovery-record-limit")
        val store = LocalExitDiagnosticsStore(directory = directory)
        val root = JsonParser.parseString(
            ExitDiagnosticsCodec().encodeStore(
                (1..25).map { index -> event(index.toString().padStart(64, '0'), index * 100L) },
            ).toString(Charsets.UTF_8),
        ).asJsonObject
        val eventElements = root.getAsJsonArray("events")
        eventElements.add(JsonParser.parseString(eventElements.last().toString()))
        store.diagnosticsFile.writeText(AppJson.gson.toJson(root))

        val events = store.readEvents()

        assertEquals(DEFAULT_DIAGNOSTICS_MAX_RECORDS, events.size)
        assertEquals(events.size, events.distinctBy { it.eventId }.size)
        assertEquals(600L, events.first().exitTimestampEpochMillis)
        assertEquals(2_500L, events.last().exitTimestampEpochMillis)
        assertTrue(
            store.diagnosticsFile.readBytes().contentEquals(
                ExitDiagnosticsCodec().encodeStore(events),
            ),
        )
    }

    @Test
    fun readEvents_validFileOverByteLimitIsTrimmedAndRewritten() {
        val directory = temporaryFolder.newFolder("recovery-byte-limit")
        val store = LocalExitDiagnosticsStore(
            directory = directory,
            maxRecords = 20,
            maxBytes = 720,
        )
        store.diagnosticsFile.writeBytes(
            ExitDiagnosticsCodec().encodeStore(
                (1..8).map { index -> event(index.toString().repeat(64), index * 100L) },
            ),
        )

        val events = store.readEvents()

        assertTrue(events.isNotEmpty())
        assertEquals(800L, events.last().exitTimestampEpochMillis)
        assertTrue(store.diagnosticsFile.length() <= 720L)
        assertTrue(
            store.diagnosticsFile.readBytes().contentEquals(
                ExitDiagnosticsCodec().encodeStore(events),
            ),
        )
    }

    @Test
    fun readEvents_recoveryRewriteFailureReturnsTrimmedEventsAndKeepsOriginalFile() {
        val directory = temporaryFolder.newFolder("recovery-write-failure")
        val originalBytes = ExitDiagnosticsCodec().encodeStore(
            (1..25).map { index -> event(index.toString().padStart(64, '0'), index * 100L) },
        )
        val seedStore = LocalExitDiagnosticsStore(directory = directory)
        seedStore.diagnosticsFile.writeBytes(originalBytes)
        val failingStore = LocalExitDiagnosticsStore(
            directory = directory,
            fileWriter = DiagnosticsFileWriter { _, _ -> throw IOException("write failed") },
        )

        val events = failingStore.readEvents()

        assertEquals(DEFAULT_DIAGNOSTICS_MAX_RECORDS, events.size)
        assertTrue(failingStore.diagnosticsFile.exists())
        assertTrue(failingStore.diagnosticsFile.readBytes().contentEquals(originalBytes))
    }

    @Test
    fun readEvents_wholeFileCorruptionIsQuarantinedAndReturnsEmpty() {
        val directory = temporaryFolder.newFolder("whole-corrupt")
        val store = LocalExitDiagnosticsStore(directory = directory)
        val sensitiveCorruption = "not-json Bearer secret-token C:\\Users\\lucky\\secret.txt"
        store.diagnosticsFile.writeText(sensitiveCorruption)

        val events = store.readEvents()

        assertTrue(events.isEmpty())
        assertFalse(store.diagnosticsFile.exists())
        assertTrue(store.quarantineFile.exists())
        assertFalse(store.quarantineFile.readText().contains(sensitiveCorruption))
        assertTrue(store.quarantineFile.readText().contains("discarded_corrupt_diagnostics"))
    }

    @Test
    fun merge_atomicWriteFailurePreservesPreviousFile() {
        val directory = temporaryFolder.newFolder("write-failure")
        val initialStore = LocalExitDiagnosticsStore(directory = directory)
        initialStore.merge(listOf(event(safeId('a'), 100L)))
        val failingStore = LocalExitDiagnosticsStore(
            directory = directory,
            fileWriter = DiagnosticsFileWriter { _, _ -> throw IOException("write failed") },
        )

        val result = failingStore.merge(listOf(event(safeId('b'), 200L)))

        assertFalse(result.persisted)
        assertEquals(listOf(safeId('a')), initialStore.readEvents().map { it.eventId })
    }

    @Test
    fun merge_sanitizesIdentifiersAndVersionBeforeWritingNoBackupFile() {
        val store = LocalExitDiagnosticsStore(
            directory = temporaryFolder.newFolder("write-boundary-redaction"),
        )
        val unsafe = event(
            eventId = "Bearer secret-token Prompt data:image/png;base64,AAAA " +
                "SELECT token FROM secrets C:\\Users\\lucky\\secret.txt",
            timestamp = 100L,
        ).copy(
            appVersionName = "sk-1234567890 /data/user/0/narra/private.db",
        )

        store.merge(listOf(unsafe))

        val rawFile = store.diagnosticsFile.readText()
        listOf(
            "Bearer secret-token",
            "Prompt",
            "base64,AAAA",
            "SELECT token",
            "secret.txt",
            "sk-1234567890",
            "private.db",
        ).forEach { sentinel ->
            assertFalse("本地诊断文件泄漏敏感哨兵：$sentinel", rawFile.contains(sentinel, ignoreCase = true))
        }
        val stored = store.readEvents().single()
        assertTrue(stored.eventId.matches(Regex("[0-9a-f]{64}")))
        assertEquals("unknown", stored.appVersionName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsByteLimitSmallerThanEmptyDocument() {
        LocalExitDiagnosticsStore(
            directory = temporaryFolder.newFolder("too-small"),
            maxBytes = 1,
        )
    }

    private fun event(eventId: String, timestamp: Long) = LocalExitDiagnosticEvent(
        eventId = eventId,
        exitTimestampEpochMillis = timestamp,
        observedTimestampEpochMillis = timestamp + 1,
        reasonCategory = ExitReasonCategory.ANR,
        importance = 200,
        status = 1,
        appVersionName = "2.2.6-dev",
        appVersionCode = 20206L,
        pssKb = 2_048L,
        rssKb = 4_096L,
    )

    private fun safeId(character: Char): String = character.toString().repeat(64)
}
