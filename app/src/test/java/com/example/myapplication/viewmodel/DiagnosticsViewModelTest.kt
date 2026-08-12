package com.example.myapplication.viewmodel

import com.example.myapplication.system.diagnostics.DiagnosticsExportResult
import com.example.myapplication.system.diagnostics.DiagnosticsSnapshot
import com.example.myapplication.system.diagnostics.ExitDiagnosticsOperations
import com.example.myapplication.system.diagnostics.HistoricalExitQuerySupport
import com.example.myapplication.system.diagnostics.LocalExitDiagnosticEvent
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsSupportCountAndLatestTimestamp() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val operations = FakeDiagnosticsOperations(
            snapshot = DiagnosticsSnapshot(
                supportStatus = HistoricalExitQuerySupport.SUPPORTED,
                events = listOf(event(100L), event(300L)),
            ),
        )

        val viewModel = DiagnosticsViewModel(operations)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(HistoricalExitQuerySupport.SUPPORTED, viewModel.uiState.value.supportStatus)
        assertEquals(2, viewModel.uiState.value.recordCount)
        assertEquals(300L, viewModel.uiState.value.latestExitTimestampEpochMillis)
    }

    @Test
    fun onExportDocumentSelected_nullTargetIsNormalCancellation() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val operations = FakeDiagnosticsOperations()
            val viewModel = DiagnosticsViewModel(operations)
            advanceUntilIdle()

            viewModel.onExportDocumentSelected(null)
            advanceUntilIdle()

            assertEquals(0, operations.exportCalls)
            assertNull(viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isExporting)
        }

    @Test
    fun onExportDocumentSelected_failureShowsSafeMessageAndRestoresButton() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val operations = FakeDiagnosticsOperations(exportResult = DiagnosticsExportResult.Failure)
            val viewModel = DiagnosticsViewModel(operations)
            advanceUntilIdle()

            viewModel.onExportDocumentSelected { ByteArrayOutputStream() }
            advanceUntilIdle()

            assertEquals(1, operations.exportCalls)
            assertFalse(viewModel.uiState.value.isExporting)
            assertEquals("诊断信息导出失败，请稍后重试", viewModel.uiState.value.message)
        }

    private class FakeDiagnosticsOperations(
        var snapshot: DiagnosticsSnapshot = DiagnosticsSnapshot(
            supportStatus = HistoricalExitQuerySupport.UNSUPPORTED,
            events = emptyList(),
        ),
        var exportResult: DiagnosticsExportResult = DiagnosticsExportResult.Success(0),
    ) : ExitDiagnosticsOperations {
        var exportCalls = 0

        override val supportStatus: HistoricalExitQuerySupport
            get() = snapshot.supportStatus

        override fun scheduleCollection() = Unit

        override suspend fun snapshot(): DiagnosticsSnapshot = snapshot

        override suspend fun export(openOutputStream: () -> OutputStream?): DiagnosticsExportResult {
            exportCalls++
            return exportResult
        }
    }

    private fun event(timestamp: Long): LocalExitDiagnosticEvent {
        return LocalExitDiagnosticEvent(
            eventId = "event-$timestamp",
            exitTimestampEpochMillis = timestamp,
            observedTimestampEpochMillis = timestamp + 1,
            reasonCategory = com.example.myapplication.system.diagnostics.ExitReasonCategory.ANR,
            importance = 200,
            status = 1,
            appVersionName = "2.2.6-dev",
            appVersionCode = 20206L,
            pssKb = 2_048L,
            rssKb = 4_096L,
        )
    }
}
