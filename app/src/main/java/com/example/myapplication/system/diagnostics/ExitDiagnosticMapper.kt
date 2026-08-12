package com.example.myapplication.system.diagnostics

import com.example.myapplication.system.security.SensitiveTextRedactor
import java.security.MessageDigest

object ExitDiagnosticMapper {
    fun map(
        raw: RawProcessExitRecord,
        observedAtEpochMillis: Long,
        appVersion: DiagnosticsAppVersion,
    ): LocalExitDiagnosticEvent? {
        if (raw.timestampEpochMillis <= 0L) return null
        val category = raw.reasonCode.toSafeCategory() ?: return null
        val normalizedVersion = normalizeDiagnosticsAppVersion(appVersion)
        val normalizedObservedAt = observedAtEpochMillis.coerceAtLeast(raw.timestampEpochMillis)
        val normalizedImportance = raw.importance.coerceAtLeast(0)
        val normalizedPssKb = raw.pssKb.coerceAtLeast(0L)
        val normalizedRssKb = raw.rssKb.coerceAtLeast(0L)
        val eventId = sha256Hex(
            listOf(
                raw.timestampEpochMillis,
                raw.reasonCode,
                normalizedImportance,
                raw.status,
                normalizedPssKb,
                normalizedRssKb,
            ).joinToString(separator = "|"),
        )
        return LocalExitDiagnosticEvent(
            eventId = eventId,
            exitTimestampEpochMillis = raw.timestampEpochMillis,
            observedTimestampEpochMillis = normalizedObservedAt,
            reasonCategory = category,
            importance = normalizedImportance,
            status = raw.status,
            appVersionName = normalizedVersion.versionName,
            appVersionCode = normalizedVersion.versionCode,
            pssKb = normalizedPssKb,
            rssKb = normalizedRssKb,
        )
    }

    private fun Int.toSafeCategory(): ExitReasonCategory? {
        return when (this) {
            ProcessExitReasonCodes.CRASH -> ExitReasonCategory.JAVA_CRASH
            ProcessExitReasonCodes.CRASH_NATIVE -> ExitReasonCategory.NATIVE_CRASH
            ProcessExitReasonCodes.ANR -> ExitReasonCategory.ANR
            ProcessExitReasonCodes.LOW_MEMORY -> ExitReasonCategory.LOW_MEMORY
            ProcessExitReasonCodes.EXCESSIVE_RESOURCE_USAGE -> ExitReasonCategory.EXCESSIVE_RESOURCE
            ProcessExitReasonCodes.INITIALIZATION_FAILURE -> ExitReasonCategory.INITIALIZATION_FAILURE
            ProcessExitReasonCodes.DEPENDENCY_DIED -> ExitReasonCategory.DEPENDENCY_DIED
            ProcessExitReasonCodes.UNKNOWN,
            ProcessExitReasonCodes.SIGNALED,
            ProcessExitReasonCodes.OTHER,
            ProcessExitReasonCodes.FREEZER,
            -> ExitReasonCategory.UNKNOWN_ABNORMAL
            else -> null
        }
    }
}

internal fun normalizeDiagnosticsAppVersion(
    version: DiagnosticsAppVersion,
): DiagnosticsAppVersion {
    val normalizedName = SensitiveTextRedactor.redact(
        text = version.versionName.take(MAX_UNTRUSTED_IDENTIFIER_LENGTH),
        maxLength = MAX_UNTRUSTED_IDENTIFIER_LENGTH,
    ).trim()
        .takeIf { it.matches(DIAGNOSTICS_VERSION_NAME_PATTERN) }
        ?: "unknown"
    return DiagnosticsAppVersion(
        versionName = normalizedName,
        versionCode = version.versionCode.coerceAtLeast(0L),
    )
}

internal fun normalizeDiagnosticEvent(
    event: LocalExitDiagnosticEvent,
): LocalExitDiagnosticEvent? {
    if (event.exitTimestampEpochMillis <= 0L) return null
    val normalizedVersion = normalizeDiagnosticsAppVersion(
        DiagnosticsAppVersion(event.appVersionName, event.appVersionCode),
    )
    val safeEventIdCandidate = SensitiveTextRedactor.redact(
        text = event.eventId.take(MAX_UNTRUSTED_IDENTIFIER_LENGTH),
        maxLength = MAX_UNTRUSTED_IDENTIFIER_LENGTH,
    ).trim()
    val normalizedEventId = safeEventIdCandidate
        .takeIf { it.matches(DIAGNOSTICS_EVENT_ID_PATTERN) }
        ?: sha256Hex(safeEventIdCandidate)
    return event.copy(
        schemaVersion = DIAGNOSTICS_SCHEMA_VERSION,
        eventId = normalizedEventId,
        observedTimestampEpochMillis = event.observedTimestampEpochMillis
            .coerceAtLeast(event.exitTimestampEpochMillis),
        importance = event.importance.coerceAtLeast(0),
        appVersionName = normalizedVersion.versionName,
        appVersionCode = normalizedVersion.versionCode,
        pssKb = event.pssKb.coerceAtLeast(0L),
        rssKb = event.rssKb.coerceAtLeast(0L),
    )
}

private fun sha256Hex(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private val DIAGNOSTICS_EVENT_ID_PATTERN = Regex("[0-9a-f]{64}")
private val DIAGNOSTICS_VERSION_NAME_PATTERN = Regex(
    pattern = "[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}(?:-(?:dev|baseline))?",
)
private const val MAX_UNTRUSTED_IDENTIFIER_LENGTH = 512
