package com.example.myapplication.system.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalExitDiagnosticsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createOrNoOp_initializationFailureReturnsSafeOperations() = runTest {
        val diagnostics = LocalExitDiagnostics.createOrNoOp {
            throw IllegalStateException("C:\\Users\\lucky\\secret.txt")
        }

        diagnostics.scheduleCollection()

        assertEquals(HistoricalExitQuerySupport.UNSUPPORTED, diagnostics.supportStatus)
        assertTrue(diagnostics.snapshot().events.isEmpty())
        assertEquals(
            DiagnosticsExportResult.Failure,
            diagnostics.export { error("no-op 不应打开输出流") },
        )
    }

    @Test(expected = CancellationException::class)
    fun createOrNoOp_cancellationIsRethrown() {
        LocalExitDiagnostics.createOrNoOp {
            throw CancellationException("cancelled")
        }
    }

    @Test
    fun scheduleCollection_skippedRunCanBeScheduledAgainAfterSuppressionEnds() = runTest {
        var shouldSkip = true
        val source = CountingExitInfoSource()
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("schedule-reset"))
        val traceRunner = RecordingTraceRunner()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val diagnostics = LocalExitDiagnostics(
            supportStatus = source.supportStatus,
            store = store,
            collector = ExitDiagnosticsCollector(
                source = source,
                store = store,
                appVersion = DiagnosticsAppVersion("2.2.6-dev", 20206L),
                shouldSkipCollection = { shouldSkip },
            ),
            exporter = ExitDiagnosticsExporter(
                store = store,
                appVersion = DiagnosticsAppVersion("2.2.6-dev", 20206L),
            ),
            ioDispatcher = dispatcher,
            collectionScope = this,
            traceRunner = traceRunner,
        )

        diagnostics.scheduleCollection()
        advanceUntilIdle()
        assertEquals(0, source.queryCalls)

        shouldSkip = false
        diagnostics.scheduleCollection()
        advanceUntilIdle()

        assertEquals(1, source.queryCalls)
        assertEquals(2, traceRunner.sections.size)
    }

    private class CountingExitInfoSource : ProcessExitInfoSource {
        var queryCalls = 0

        override val supportStatus: HistoricalExitQuerySupport = HistoricalExitQuerySupport.SUPPORTED

        override fun query(maxRecords: Int): ProcessExitQueryResult {
            queryCalls++
            return ProcessExitQueryResult.Success(emptyList())
        }
    }

    private class RecordingTraceRunner : DiagnosticsTraceRunner {
        val sections = mutableListOf<String>()

        override fun <T> trace(sectionName: String, block: () -> T): T {
            sections += sectionName
            return block()
        }
    }
}
