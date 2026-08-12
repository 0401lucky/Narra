package com.example.myapplication.system.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitDiagnosticMapperTest {
    private val version = DiagnosticsAppVersion(
        versionName = "2.2.6-dev",
        versionCode = 20206L,
    )

    @Test
    fun map_abnormalReasonsMapsToExplicitSafeCategories() {
        val cases = listOf(
            ProcessExitReasonCodes.CRASH to ExitReasonCategory.JAVA_CRASH,
            ProcessExitReasonCodes.CRASH_NATIVE to ExitReasonCategory.NATIVE_CRASH,
            ProcessExitReasonCodes.ANR to ExitReasonCategory.ANR,
            ProcessExitReasonCodes.LOW_MEMORY to ExitReasonCategory.LOW_MEMORY,
            ProcessExitReasonCodes.EXCESSIVE_RESOURCE_USAGE to ExitReasonCategory.EXCESSIVE_RESOURCE,
            ProcessExitReasonCodes.INITIALIZATION_FAILURE to ExitReasonCategory.INITIALIZATION_FAILURE,
            ProcessExitReasonCodes.DEPENDENCY_DIED to ExitReasonCategory.DEPENDENCY_DIED,
            ProcessExitReasonCodes.SIGNALED to ExitReasonCategory.UNKNOWN_ABNORMAL,
            ProcessExitReasonCodes.UNKNOWN to ExitReasonCategory.UNKNOWN_ABNORMAL,
        )

        cases.forEachIndexed { index, (reason, expectedCategory) ->
            val event = ExitDiagnosticMapper.map(
                raw = raw(reasonCode = reason, timestamp = 1_000L + index),
                observedAtEpochMillis = 2_000L,
                appVersion = version,
            )

            requireNotNull(event)
            assertEquals(expectedCategory, event.reasonCategory)
            assertEquals("2.2.6-dev", event.appVersionName)
            assertEquals(20206L, event.appVersionCode)
            assertTrue(event.eventId.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun map_userAndPackageLifecycleReasonsAreIgnored() {
        val ignoredReasons = listOf(
            ProcessExitReasonCodes.EXIT_SELF,
            ProcessExitReasonCodes.USER_REQUESTED,
            ProcessExitReasonCodes.USER_STOPPED,
            ProcessExitReasonCodes.PERMISSION_CHANGE,
            ProcessExitReasonCodes.PACKAGE_STATE_CHANGE,
            ProcessExitReasonCodes.PACKAGE_UPDATED,
        )

        ignoredReasons.forEach { reason ->
            assertEquals(
                null,
                ExitDiagnosticMapper.map(
                    raw = raw(reasonCode = reason, timestamp = 1_000L),
                    observedAtEpochMillis = 2_000L,
                    appVersion = version,
                ),
            )
        }
    }

    @Test
    fun map_eventIdIsStableAcrossObservationTimeButChangesWithSafeExitFields() {
        val first = requireNotNull(
            ExitDiagnosticMapper.map(raw(ProcessExitReasonCodes.ANR, 1_000L), 2_000L, version),
        )
        val observedLater = requireNotNull(
            ExitDiagnosticMapper.map(raw(ProcessExitReasonCodes.ANR, 1_000L), 9_000L, version),
        )
        val differentStatus = requireNotNull(
            ExitDiagnosticMapper.map(
                raw(ProcessExitReasonCodes.ANR, 1_000L).copy(status = 9),
                2_000L,
                version,
            ),
        )
        val observedAfterUpgrade = requireNotNull(
            ExitDiagnosticMapper.map(
                raw(ProcessExitReasonCodes.ANR, 1_000L),
                12_000L,
                DiagnosticsAppVersion(versionName = "2.2.7-dev", versionCode = 20207L),
            ),
        )
        val signaledExit = requireNotNull(
            ExitDiagnosticMapper.map(
                raw(ProcessExitReasonCodes.SIGNALED, 1_000L),
                2_000L,
                version,
            ),
        )
        val otherUnknownExit = requireNotNull(
            ExitDiagnosticMapper.map(
                raw(ProcessExitReasonCodes.OTHER, 1_000L),
                2_000L,
                version,
            ),
        )

        assertEquals(first.eventId, observedLater.eventId)
        assertEquals(first.eventId, observedAfterUpgrade.eventId)
        assertNotEquals(first.eventId, differentStatus.eventId)
        assertEquals(signaledExit.reasonCategory, otherUnknownExit.reasonCategory)
        assertNotEquals(signaledExit.eventId, otherUnknownExit.eventId)
        assertFalse(first.appVersionName.contains(" "))
    }

    private fun raw(reasonCode: Int, timestamp: Long) = RawProcessExitRecord(
        reasonCode = reasonCode,
        timestampEpochMillis = timestamp,
        importance = 200,
        status = 1,
        pssKb = 2_048L,
        rssKb = 4_096L,
    )
}
