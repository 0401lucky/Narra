package com.example.myapplication.system.diagnostics

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExitDiagnosticsCollectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun collect_supportedQueryPersistsOnlyAbnormalEventsAndDeduplicates() {
        val rawAbnormal = raw(ProcessExitReasonCodes.CRASH, 100L)
        val source = FakeProcessExitInfoSource(
            result = ProcessExitQueryResult.Success(
                listOf(
                    rawAbnormal,
                    rawAbnormal,
                    raw(ProcessExitReasonCodes.USER_REQUESTED, 200L),
                ),
            ),
        )
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("collect"))
        val collector = collector(source = source, store = store)

        val result = collector.collect()

        assertEquals(DiagnosticsCollectionResult.Completed(storedCount = 1), result)
        assertEquals(1, store.readEvents().size)
    }

    @Test
    fun collect_unsupportedPlatformDoesNotTouchStore() {
        val source = FakeProcessExitInfoSource(result = ProcessExitQueryResult.Unsupported)
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("unsupported"))

        val result = collector(source = source, store = store).collect()

        assertEquals(DiagnosticsCollectionResult.Unsupported, result)
        assertTrue(store.readEvents().isEmpty())
    }

    @Test
    fun collect_benchmarkSuppressionSkipsQueryAndStore() {
        val source = FakeProcessExitInfoSource(
            result = ProcessExitQueryResult.Success(listOf(raw(ProcessExitReasonCodes.ANR, 100L))),
        )
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("benchmark"))
        val collector = collector(
            source = source,
            store = store,
            shouldSkipCollection = { true },
        )

        val result = collector.collect()

        assertEquals(DiagnosticsCollectionResult.Skipped, result)
        assertEquals(0, source.queryCalls)
        assertTrue(store.readEvents().isEmpty())
    }

    @Test
    fun collect_queryFailureFailsOpenAndKeepsExistingRecords() {
        val store = LocalExitDiagnosticsStore(temporaryFolder.newFolder("query-failure"))
        store.merge(
            listOf(
                requireNotNull(
                    ExitDiagnosticMapper.map(
                        raw(ProcessExitReasonCodes.ANR, 50L),
                        observedAtEpochMillis = 60L,
                        appVersion = VERSION,
                    ),
                ),
            ),
        )
        val source = FakeProcessExitInfoSource(failure = IOException("query failed"))

        val result = collector(source = source, store = store).collect()

        assertEquals(DiagnosticsCollectionResult.Failed, result)
        assertEquals(1, store.readEvents().size)
    }

    private fun collector(
        source: ProcessExitInfoSource,
        store: LocalExitDiagnosticsStore,
        shouldSkipCollection: () -> Boolean = { false },
    ) = ExitDiagnosticsCollector(
        source = source,
        store = store,
        appVersion = VERSION,
        clock = { 1_000L },
        shouldSkipCollection = shouldSkipCollection,
    )

    private fun raw(reasonCode: Int, timestamp: Long) = RawProcessExitRecord(
        reasonCode = reasonCode,
        timestampEpochMillis = timestamp,
        importance = 200,
        status = 1,
        pssKb = 2_048L,
        rssKb = 4_096L,
    )

    private class FakeProcessExitInfoSource(
        private val result: ProcessExitQueryResult = ProcessExitQueryResult.Unsupported,
        private val failure: Throwable? = null,
    ) : ProcessExitInfoSource {
        var queryCalls = 0

        override val supportStatus: HistoricalExitQuerySupport =
            if (result is ProcessExitQueryResult.Unsupported) {
                HistoricalExitQuerySupport.UNSUPPORTED
            } else {
                HistoricalExitQuerySupport.SUPPORTED
            }

        override fun query(maxRecords: Int): ProcessExitQueryResult {
            queryCalls++
            failure?.let { throw it }
            return result
        }
    }

    private companion object {
        val VERSION = DiagnosticsAppVersion("2.2.6-dev", 20206L)
    }
}
