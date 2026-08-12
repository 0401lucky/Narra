package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.CancellationException

class ExitDiagnosticsCollector(
    private val source: ProcessExitInfoSource,
    private val store: LocalExitDiagnosticsStore,
    private val appVersion: DiagnosticsAppVersion,
    private val clock: () -> Long = System::currentTimeMillis,
    private val shouldSkipCollection: () -> Boolean = { false },
    private val queryLimit: Int = DEFAULT_QUERY_LIMIT,
) {
    init {
        require(queryLimit > 0) { "历史退出查询上限必须大于 0" }
    }

    fun collect(): DiagnosticsCollectionResult {
        return try {
            collectSafely()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "本地退出诊断收集失败，type=${throwable.javaClass.simpleName}")
            DiagnosticsCollectionResult.Failed
        }
    }

    private fun collectSafely(): DiagnosticsCollectionResult {
        if (shouldSkipCollection()) return DiagnosticsCollectionResult.Skipped
        val queryResult = try {
            source.query(queryLimit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "历史退出查询失败，type=${throwable.javaClass.simpleName}")
            return DiagnosticsCollectionResult.Failed
        }
        if (queryResult is ProcessExitQueryResult.Unsupported) {
            return DiagnosticsCollectionResult.Unsupported
        }
        queryResult as ProcessExitQueryResult.Success
        val observedAt = clock()
        val mappedEvents = queryResult.records.mapNotNull { raw ->
            ExitDiagnosticMapper.map(
                raw = raw,
                observedAtEpochMillis = observedAt,
                appVersion = appVersion,
            )
        }
        val update = store.merge(mappedEvents)
        return if (update.persisted) {
            DiagnosticsCollectionResult.Completed(storedCount = update.storedCount)
        } else {
            DiagnosticsCollectionResult.Failed
        }
    }

    private companion object {
        const val TAG = "ExitDiagnostics"
        const val DEFAULT_QUERY_LIMIT = 32
    }
}
