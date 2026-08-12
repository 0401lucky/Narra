package com.example.myapplication.system.diagnostics

import android.content.Context
import android.os.Trace
import com.example.myapplication.system.logging.AppLogger
import com.example.myapplication.system.performance.PerformanceTraceContract
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DiagnosticsTraceRunner {
    fun <T> trace(sectionName: String, block: () -> T): T
}

object AndroidDiagnosticsTraceRunner : DiagnosticsTraceRunner {
    override fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }
}

class LocalExitDiagnostics internal constructor(
    override val supportStatus: HistoricalExitQuerySupport,
    private val store: LocalExitDiagnosticsStore,
    private val collector: ExitDiagnosticsCollector,
    private val exporter: ExitDiagnosticsExporter,
    private val ioDispatcher: CoroutineDispatcher,
    private val collectionScope: CoroutineScope,
    private val traceRunner: DiagnosticsTraceRunner,
) : ExitDiagnosticsOperations {
    private val collectionScheduled = AtomicBoolean(false)

    override fun scheduleCollection() {
        if (!collectionScheduled.compareAndSet(false, true)) return
        collectionScope.launch {
            try {
                val result = traceRunner.trace(PerformanceTraceContract.EXIT_COLLECT) {
                    collector.collect()
                }
                if (result is DiagnosticsCollectionResult.Skipped) {
                    collectionScheduled.set(false)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AppLogger.w(TAG, "首帧后诊断收集失败，type=${throwable.javaClass.simpleName}")
            }
        }
    }

    override suspend fun snapshot(): DiagnosticsSnapshot {
        return withContext(ioDispatcher) {
            DiagnosticsSnapshot(
                supportStatus = supportStatus,
                events = store.readEvents(),
            )
        }
    }

    override suspend fun export(
        openOutputStream: () -> OutputStream?,
    ): DiagnosticsExportResult {
        return withContext(ioDispatcher) {
            traceRunner.trace(PerformanceTraceContract.DIAGNOSTICS_EXPORT) {
                exporter.export(openOutputStream)
            }
        }
    }

    companion object {
        internal fun createOrNoOp(
            context: Context,
            appVersion: DiagnosticsAppVersion,
            shouldSkipCollection: () -> Boolean = { false },
            traceRunner: DiagnosticsTraceRunner = AndroidDiagnosticsTraceRunner,
            clock: () -> Long = System::currentTimeMillis,
        ): ExitDiagnosticsOperations {
            return createOrNoOp {
                create(
                    context = context,
                    appVersion = appVersion,
                    shouldSkipCollection = shouldSkipCollection,
                    traceRunner = traceRunner,
                    clock = clock,
                )
            }
        }

        internal fun createOrNoOp(
            factory: () -> ExitDiagnosticsOperations,
        ): ExitDiagnosticsOperations {
            return try {
                factory()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                AppLogger.w(
                    TAG,
                    "本地退出诊断初始化失败，已降级，type=${throwable.javaClass.simpleName}",
                )
                NoOpExitDiagnostics
            }
        }

        fun create(
            context: Context,
            appVersion: DiagnosticsAppVersion,
            shouldSkipCollection: () -> Boolean = { false },
            traceRunner: DiagnosticsTraceRunner = AndroidDiagnosticsTraceRunner,
            clock: () -> Long = System::currentTimeMillis,
        ): LocalExitDiagnostics {
            val appContext = context.applicationContext
            val source = AndroidProcessExitInfoSource(appContext)
            val store = LocalExitDiagnosticsStore(
                directory = File(appContext.noBackupFilesDir, DIAGNOSTICS_DIRECTORY_NAME),
            )
            val ioDispatcher = Dispatchers.IO
            return LocalExitDiagnostics(
                supportStatus = source.supportStatus,
                store = store,
                collector = ExitDiagnosticsCollector(
                    source = source,
                    store = store,
                    appVersion = appVersion,
                    clock = clock,
                    shouldSkipCollection = shouldSkipCollection,
                ),
                exporter = ExitDiagnosticsExporter(
                    store = store,
                    appVersion = appVersion,
                    clock = clock,
                ),
                ioDispatcher = ioDispatcher,
                collectionScope = CoroutineScope(SupervisorJob() + ioDispatcher),
                traceRunner = traceRunner,
            )
        }

        private const val DIAGNOSTICS_DIRECTORY_NAME = "diagnostics"
        private const val TAG = "ExitDiagnostics"
    }
}

private object NoOpExitDiagnostics : ExitDiagnosticsOperations {
    override val supportStatus: HistoricalExitQuerySupport = HistoricalExitQuerySupport.UNSUPPORTED

    override fun scheduleCollection() = Unit

    override suspend fun snapshot(): DiagnosticsSnapshot {
        return DiagnosticsSnapshot(
            supportStatus = supportStatus,
            events = emptyList(),
        )
    }

    override suspend fun export(
        openOutputStream: () -> OutputStream?,
    ): DiagnosticsExportResult = DiagnosticsExportResult.Failure
}
