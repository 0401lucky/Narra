package com.example.myapplication.system.diagnostics

import java.io.OutputStream

internal const val DIAGNOSTICS_SCHEMA_VERSION = 1
internal const val DEFAULT_DIAGNOSTICS_MAX_RECORDS = 20
internal const val DEFAULT_DIAGNOSTICS_MAX_BYTES = 256 * 1024

enum class HistoricalExitQuerySupport {
    SUPPORTED,
    UNSUPPORTED,
}

enum class ExitReasonCategory {
    JAVA_CRASH,
    NATIVE_CRASH,
    ANR,
    LOW_MEMORY,
    EXCESSIVE_RESOURCE,
    INITIALIZATION_FAILURE,
    DEPENDENCY_DIED,
    UNKNOWN_ABNORMAL,
}

data class DiagnosticsAppVersion(
    val versionName: String,
    val versionCode: Long,
)

data class RawProcessExitRecord(
    val reasonCode: Int,
    val timestampEpochMillis: Long,
    val importance: Int,
    val status: Int,
    val pssKb: Long,
    val rssKb: Long,
)

data class LocalExitDiagnosticEvent(
    val schemaVersion: Int = DIAGNOSTICS_SCHEMA_VERSION,
    val eventId: String,
    val exitTimestampEpochMillis: Long,
    val observedTimestampEpochMillis: Long,
    val reasonCategory: ExitReasonCategory,
    val importance: Int,
    val status: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val pssKb: Long,
    val rssKb: Long,
)

data class DiagnosticsSnapshot(
    val supportStatus: HistoricalExitQuerySupport,
    val events: List<LocalExitDiagnosticEvent>,
)

sealed interface ProcessExitQueryResult {
    data object Unsupported : ProcessExitQueryResult

    data class Success(
        val records: List<RawProcessExitRecord>,
    ) : ProcessExitQueryResult
}

sealed interface DiagnosticsCollectionResult {
    data object Unsupported : DiagnosticsCollectionResult
    data object Skipped : DiagnosticsCollectionResult
    data object Failed : DiagnosticsCollectionResult

    data class Completed(
        val storedCount: Int,
    ) : DiagnosticsCollectionResult
}

sealed interface DiagnosticsExportResult {
    data class Success(
        val recordCount: Int,
    ) : DiagnosticsExportResult

    data object Failure : DiagnosticsExportResult
}

interface ExitDiagnosticsOperations {
    val supportStatus: HistoricalExitQuerySupport

    fun scheduleCollection()

    suspend fun snapshot(): DiagnosticsSnapshot

    suspend fun export(
        openOutputStream: () -> OutputStream?,
    ): DiagnosticsExportResult
}

object ProcessExitReasonCodes {
    const val UNKNOWN = 0
    const val EXIT_SELF = 1
    const val SIGNALED = 2
    const val LOW_MEMORY = 3
    const val CRASH = 4
    const val CRASH_NATIVE = 5
    const val ANR = 6
    const val INITIALIZATION_FAILURE = 7
    const val PERMISSION_CHANGE = 8
    const val EXCESSIVE_RESOURCE_USAGE = 9
    const val USER_REQUESTED = 10
    const val USER_STOPPED = 11
    const val DEPENDENCY_DIED = 12
    const val OTHER = 13
    const val FREEZER = 14
    const val PACKAGE_STATE_CHANGE = 15
    const val PACKAGE_UPDATED = 16
}
