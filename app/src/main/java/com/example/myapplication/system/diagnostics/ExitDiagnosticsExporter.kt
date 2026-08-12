package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.logging.AppLogger
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

class ExitDiagnosticsExporter(
    private val store: LocalExitDiagnosticsStore,
    appVersion: DiagnosticsAppVersion,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appVersion = normalizeDiagnosticsAppVersion(appVersion)
    private val codec = ExitDiagnosticsCodec()

    fun export(
        openOutputStream: () -> OutputStream?,
    ): DiagnosticsExportResult {
        return try {
            val events = store.readEvents()
            val bytes = codec.encodeExport(
                events = events,
                generatedAtEpochMillis = clock(),
                appVersion = appVersion,
            )
            val output = openOutputStream() ?: error("输出流不可用")
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            DiagnosticsExportResult.Success(recordCount = events.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "诊断导出失败，type=${throwable.javaClass.simpleName}")
            DiagnosticsExportResult.Failure
        }
    }

    private companion object {
        const val TAG = "ExitDiagnostics"
    }
}
