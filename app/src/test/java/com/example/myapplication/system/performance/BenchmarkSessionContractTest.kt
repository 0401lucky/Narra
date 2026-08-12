package com.example.myapplication.system.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkSessionContractTest {

    @Test
    fun ttlConstants_keepExactMeasureAndProfileBoundaries() {
        assertEquals(300_000L, BenchmarkContract.DEFAULT_TTL_MILLIS)
        assertEquals(1_800_000L, BenchmarkContract.MAX_TTL_MILLIS)
    }

    @Test
    fun resolve_baselineExplicitValidSession_activatesIsolationPolicy() {
        val descriptor = validDescriptor()

        val policy = BenchmarkRuntimePolicy.resolve(
            appChannel = BenchmarkContract.BASELINE_CHANNEL,
            descriptor = descriptor,
            requestedSessionId = descriptor.sessionId,
            nowEpochMillis = NOW_EPOCH_MILLIS,
        )

        assertTrue(policy.isBenchmarkSessionActive)
        assertTrue(policy.suppressExternalNetwork)
        assertTrue(policy.suppressPeriodicWorkers)
        assertTrue(policy.skipExitDiagnostics)
        assertTrue(policy.exposeReadyTags)
        assertNull(policy.rejection)
    }

    @Test
    fun resolve_devReleaseMissingOrMismatchedSession_neverActivates() {
        val descriptor = validDescriptor()

        val policies = listOf(
            BenchmarkRuntimePolicy.resolve(
                appChannel = "dev",
                descriptor = descriptor,
                requestedSessionId = descriptor.sessionId,
                nowEpochMillis = NOW_EPOCH_MILLIS,
            ),
            BenchmarkRuntimePolicy.resolve(
                appChannel = "release",
                descriptor = descriptor,
                requestedSessionId = descriptor.sessionId,
                nowEpochMillis = NOW_EPOCH_MILLIS,
            ),
            BenchmarkRuntimePolicy.resolve(
                appChannel = BenchmarkContract.BASELINE_CHANNEL,
                descriptor = descriptor,
                requestedSessionId = null,
                nowEpochMillis = NOW_EPOCH_MILLIS,
            ),
            BenchmarkRuntimePolicy.resolve(
                appChannel = BenchmarkContract.BASELINE_CHANNEL,
                descriptor = descriptor,
                requestedSessionId = "different-session-id",
                nowEpochMillis = NOW_EPOCH_MILLIS,
            ),
        )

        assertTrue(policies.all { !it.isBenchmarkSessionActive })
        assertTrue(policies.all { !it.suppressExternalNetwork })
        assertTrue(policies.all { !it.exposeReadyTags })
    }

    @Test
    fun validate_expiredFutureOrWrongSchema_returnsPreciseRejection() {
        assertEquals(
            BenchmarkSessionRejection.EXPIRED,
            validDescriptor(createdAtEpochMillis = NOW_EPOCH_MILLIS - BenchmarkContract.DEFAULT_TTL_MILLIS - 1L)
                .validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
        assertEquals(
            BenchmarkSessionRejection.CREATED_IN_FUTURE,
            validDescriptor(createdAtEpochMillis = NOW_EPOCH_MILLIS + BenchmarkContract.MAX_CLOCK_SKEW_MILLIS + 1L)
                .validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
        assertEquals(
            BenchmarkSessionRejection.UNSUPPORTED_SCHEMA,
            validDescriptor(schemaVersion = BenchmarkContract.SCHEMA_VERSION + 1)
                .validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
        assertEquals(
            BenchmarkSessionRejection.UNSUPPORTED_FIXTURE,
            validDescriptor(fixtureVersion = "unknown-fixture")
                .validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
    }

    @Test
    fun validate_profileSessionAtMaxTtl_acceptsBoundaryAndRejectsOverflow() {
        val descriptor = validDescriptor(
            scenarioId = BenchmarkScenarioId.P1_BASELINE_PROFILE,
            phase = BenchmarkPhase.PROFILE,
            ttlMillis = BenchmarkContract.MAX_TTL_MILLIS,
        )

        assertNull(
            descriptor.validateForLaunch(
                nowEpochMillis = NOW_EPOCH_MILLIS + BenchmarkContract.MAX_TTL_MILLIS,
                requestedSessionId = SESSION_ID,
            ),
        )
        assertEquals(
            BenchmarkSessionRejection.EXPIRED,
            descriptor.validateForLaunch(
                nowEpochMillis = NOW_EPOCH_MILLIS + BenchmarkContract.MAX_TTL_MILLIS + 1L,
                requestedSessionId = SESSION_ID,
            ),
        )
        assertEquals(
            BenchmarkSessionRejection.INVALID_TTL,
            validDescriptor(
                scenarioId = BenchmarkScenarioId.P1_BASELINE_PROFILE,
                phase = BenchmarkPhase.PROFILE,
                ttlMillis = BenchmarkContract.MAX_TTL_MILLIS + 1L,
            ).validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
    }

    @Test
    fun validate_measureSessionAtDefaultTtl_acceptsBoundaryAndRejectsOverflow() {
        val descriptor = validDescriptor(ttlMillis = BenchmarkContract.DEFAULT_TTL_MILLIS)

        assertNull(
            descriptor.validateForLaunch(
                nowEpochMillis = NOW_EPOCH_MILLIS + BenchmarkContract.DEFAULT_TTL_MILLIS,
                requestedSessionId = SESSION_ID,
            ),
        )
        assertEquals(
            BenchmarkSessionRejection.EXPIRED,
            descriptor.validateForLaunch(
                nowEpochMillis = NOW_EPOCH_MILLIS + BenchmarkContract.DEFAULT_TTL_MILLIS + 1L,
                requestedSessionId = SESSION_ID,
            ),
        )
        assertEquals(
            BenchmarkSessionRejection.INVALID_TTL,
            validDescriptor(
                ttlMillis = BenchmarkContract.DEFAULT_TTL_MILLIS + 1L,
            ).validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
    }

    @Test
    fun validate_scenarioPhaseMismatchOrPrepare_returnsInvalidPhase() {
        assertEquals(
            BenchmarkSessionRejection.INVALID_PHASE,
            validDescriptor(
                scenarioId = BenchmarkScenarioId.P1_BASELINE_PROFILE,
                phase = BenchmarkPhase.MEASURE,
            ).validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
        )
        BenchmarkScenarioId.entries
            .filterNot { it == BenchmarkScenarioId.P1_BASELINE_PROFILE }
            .forEach { scenarioId ->
                assertEquals(
                    scenarioId.wireId,
                    BenchmarkSessionRejection.INVALID_PHASE,
                    validDescriptor(
                        scenarioId = scenarioId,
                        phase = BenchmarkPhase.PROFILE,
                    ).validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
                )
            }
        BenchmarkScenarioId.entries.forEach { scenarioId ->
            assertEquals(
                scenarioId.wireId,
                BenchmarkSessionRejection.INVALID_PHASE,
                validDescriptor(
                    scenarioId = scenarioId,
                    phase = BenchmarkPhase.PREPARE,
                ).validateForLaunch(NOW_EPOCH_MILLIS, SESSION_ID),
            )
        }
    }

    @Test
    fun codec_roundTripPreservesDescriptor_andRejectsUnknownScenario() {
        val descriptor = validDescriptor()
        val encoded = BenchmarkSessionCodec.encode(descriptor)

        assertEquals(descriptor, BenchmarkSessionCodec.decodeOrNull(encoded))
        assertNull(
            BenchmarkSessionCodec.decodeOrNull(
                encoded.replace(
                    "scenario_id=${descriptor.scenarioId.wireId}",
                    "scenario_id=unknown-scenario",
                ),
            ),
        )
    }

    @Test
    fun fixtureProcessPredicate_requiresExactProcessName() {
        val applicationId = "com.narra.app.baseline"

        assertTrue(
            BenchmarkProcessPolicy.isBenchmarkFixtureProcess(
                applicationId = applicationId,
                processName = "$applicationId:benchmark_fixture",
            ),
        )
        assertFalse(
            BenchmarkProcessPolicy.isBenchmarkFixtureProcess(
                applicationId = applicationId,
                processName = "$applicationId:benchmark_fixture_worker",
            ),
        )
        assertFalse(
            BenchmarkProcessPolicy.isBenchmarkFixtureProcess(
                applicationId = applicationId,
                processName = applicationId,
            ),
        )
    }

    @Test
    fun resolver_devAndRelease_doNotReadSessionFile() {
        var readCount = 0
        val sessionReader = {
            readCount += 1
            validDescriptor()
        }

        BenchmarkRuntimePolicyResolver(
            appChannel = "dev",
            sessionReader = sessionReader,
            nowEpochMillis = { NOW_EPOCH_MILLIS },
        ).resolve(SESSION_ID)
        BenchmarkRuntimePolicyResolver(
            appChannel = "release",
            sessionReader = sessionReader,
            nowEpochMillis = { NOW_EPOCH_MILLIS },
        ).resolve(SESSION_ID)

        assertEquals(0, readCount)
    }

    private fun validDescriptor(
        schemaVersion: Int = BenchmarkContract.SCHEMA_VERSION,
        fixtureVersion: String = BenchmarkContract.FIXTURE_VERSION,
        createdAtEpochMillis: Long = NOW_EPOCH_MILLIS,
        scenarioId: BenchmarkScenarioId = BenchmarkScenarioId.S2_POPULATED_STARTUP,
        phase: BenchmarkPhase = BenchmarkPhase.MEASURE,
        ttlMillis: Long = BenchmarkContract.DEFAULT_TTL_MILLIS,
    ): BenchmarkSessionDescriptor {
        return BenchmarkSessionDescriptor(
            schemaVersion = schemaVersion,
            sessionId = SESSION_ID,
            scenarioId = scenarioId,
            fixtureVersion = fixtureVersion,
            phase = phase,
            createdAtEpochMillis = createdAtEpochMillis,
            ttlMillis = ttlMillis,
        )
    }

    private companion object {
        const val NOW_EPOCH_MILLIS = 1_800_000_000_000L
        const val SESSION_ID = "01234567-89ab-cdef-0123-456789abcdef"
    }
}
