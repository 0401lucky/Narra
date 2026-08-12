package com.example.myapplication.system.diagnostics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalExitDiagnosticsStoreInstrumentedTest {
    @Test
    fun noBackupDirectory_writeAndReopenReadsSameSafeRecord() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(
            context.noBackupFilesDir,
            "diagnostics-test-${System.nanoTime()}",
        )
        try {
            val store = LocalExitDiagnosticsStore(directory)
            val expected = requireNotNull(
                ExitDiagnosticMapper.map(
                    raw = RawProcessExitRecord(
                        reasonCode = ProcessExitReasonCodes.ANR,
                        timestampEpochMillis = 100L,
                        importance = 200,
                        status = 1,
                        pssKb = 2_048L,
                        rssKb = 4_096L,
                    ),
                    observedAtEpochMillis = 200L,
                    appVersion = DiagnosticsAppVersion("2.2.6-dev", 20206L),
                ),
            )

            assertTrue(store.merge(listOf(expected)).persisted)
            val reopened = LocalExitDiagnosticsStore(directory)

            assertEquals(listOf(expected), reopened.readEvents())
        } finally {
            directory.deleteRecursively()
        }
    }
}
