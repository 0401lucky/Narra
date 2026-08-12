package com.example.myapplication.system.diagnostics

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExitDiagnosticsExporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun export_containsOnlyAllowlistedEventFieldsAndNoSensitiveSentinels() {
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("export"))
        val sensitiveSentinels = listOf(
            "chat-secret sentinel",
            "Prompt",
            "sk-1234567890",
            "base64,AAAA",
            "SELECT token",
            "secret.txt",
        )
        store.merge(
            listOf(
                event(
                    eventId = "chat-secret sentinel Prompt data:image/png;base64,AAAA " +
                        "SELECT token C:\\Users\\lucky\\secret.txt",
                    versionName = "sk-1234567890 /data/user/0/narra/private.db",
                ),
            ),
        )
        val output = ByteArrayOutputStream()
        val exporter = ExitDiagnosticsExporter(
            store = store,
            appVersion = DiagnosticsAppVersion("2.2.6-dev", 20206L),
            clock = { 9_999L },
        )

        val result = exporter.export { output }
        val json = output.toString(Charsets.UTF_8.name())
        val root = JsonParser.parseString(json).asJsonObject
        val exportedEvent = root.getAsJsonArray("events").single().asJsonObject

        assertEquals(DiagnosticsExportResult.Success(recordCount = 1), result)
        assertEquals(
            setOf(
                "schemaVersion",
                "eventId",
                "exitTimestampEpochMillis",
                "observedTimestampEpochMillis",
                "reasonCategory",
                "importance",
                "status",
                "appVersionName",
                "appVersionCode",
                "pssKb",
                "rssKb",
            ),
            exportedEvent.keySet(),
        )
        val rawFile = store.diagnosticsFile.readText()
        sensitiveSentinels.forEach { sentinel ->
            assertFalse("落盘内容泄漏敏感哨兵：$sentinel", rawFile.contains(sentinel, ignoreCase = true))
            assertFalse("导出内容泄漏敏感哨兵：$sentinel", json.contains(sentinel, ignoreCase = true))
        }
    }

    @Test
    fun export_writeFailureReturnsSafeFailureAndDoesNotClearStore() {
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("export-failure"))
        store.merge(listOf(event(eventId = "safe-event", versionName = "2.2.6-dev")))
        val exporter = ExitDiagnosticsExporter(
            store = store,
            appVersion = DiagnosticsAppVersion("2.2.6-dev", 20206L),
            clock = { 9_999L },
        )

        val result = exporter.export {
            object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("C:\\Users\\lucky\\secret.txt")
                }
            }
        }

        assertEquals(DiagnosticsExportResult.Failure, result)
        assertEquals(1, store.readEvents().size)
        assertTrue(store.diagnosticsFile.exists())
    }

    private fun event(eventId: String, versionName: String) = LocalExitDiagnosticEvent(
        eventId = eventId,
        exitTimestampEpochMillis = 100L,
        observedTimestampEpochMillis = 101L,
        reasonCategory = ExitReasonCategory.JAVA_CRASH,
        importance = 200,
        status = 1,
        appVersionName = versionName,
        appVersionCode = 20206L,
        pssKb = 2_048L,
        rssKb = 4_096L,
    )
}
