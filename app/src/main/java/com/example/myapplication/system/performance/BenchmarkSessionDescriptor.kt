package com.example.myapplication.system.performance

data class BenchmarkSessionDescriptor(
    val schemaVersion: Int,
    val sessionId: String,
    val scenarioId: BenchmarkScenarioId,
    val fixtureVersion: String,
    val phase: BenchmarkPhase,
    val createdAtEpochMillis: Long,
    val ttlMillis: Long,
) {
    fun validateForLaunch(
        nowEpochMillis: Long,
        requestedSessionId: String?,
    ): BenchmarkSessionRejection? {
        if (schemaVersion != BenchmarkContract.SCHEMA_VERSION) {
            return BenchmarkSessionRejection.UNSUPPORTED_SCHEMA
        }
        if (fixtureVersion != BenchmarkContract.FIXTURE_VERSION) {
            return BenchmarkSessionRejection.UNSUPPORTED_FIXTURE
        }
        if (!SESSION_ID_PATTERN.matches(sessionId)) {
            return BenchmarkSessionRejection.INVALID_SESSION_ID
        }
        val expectedPhase = if (scenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE) {
            BenchmarkPhase.PROFILE
        } else {
            BenchmarkPhase.MEASURE
        }
        if (phase != expectedPhase) {
            return BenchmarkSessionRejection.INVALID_PHASE
        }
        val maxTtlMillis = if (phase == BenchmarkPhase.PROFILE) {
            BenchmarkContract.MAX_TTL_MILLIS
        } else {
            BenchmarkContract.DEFAULT_TTL_MILLIS
        }
        if (ttlMillis !in 1L..maxTtlMillis) {
            return BenchmarkSessionRejection.INVALID_TTL
        }
        if (createdAtEpochMillis <= 0L) {
            return BenchmarkSessionRejection.INVALID_CREATED_AT
        }
        if (createdAtEpochMillis - nowEpochMillis > BenchmarkContract.MAX_CLOCK_SKEW_MILLIS) {
            return BenchmarkSessionRejection.CREATED_IN_FUTURE
        }
        if (nowEpochMillis - createdAtEpochMillis > ttlMillis) {
            return BenchmarkSessionRejection.EXPIRED
        }
        if (requestedSessionId.isNullOrBlank()) {
            return BenchmarkSessionRejection.MISSING_REQUESTED_SESSION
        }
        if (requestedSessionId != sessionId) {
            return BenchmarkSessionRejection.SESSION_MISMATCH
        }
        return null
    }

    companion object {
        private val SESSION_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{15,63}")
    }
}

enum class BenchmarkSessionRejection {
    WRONG_CHANNEL,
    MISSING_DESCRIPTOR,
    UNSUPPORTED_SCHEMA,
    UNSUPPORTED_FIXTURE,
    INVALID_SESSION_ID,
    INVALID_TTL,
    INVALID_CREATED_AT,
    CREATED_IN_FUTURE,
    EXPIRED,
    INVALID_PHASE,
    MISSING_REQUESTED_SESSION,
    SESSION_MISMATCH,
}

@ConsistentCopyVisibility
data class BenchmarkRuntimePolicy private constructor(
    val isBenchmarkSessionActive: Boolean,
    val scenarioId: BenchmarkScenarioId?,
    val sessionId: String?,
    val rejection: BenchmarkSessionRejection?,
) {
    val suppressExternalNetwork: Boolean
        get() = isBenchmarkSessionActive

    val suppressPeriodicWorkers: Boolean
        get() = isBenchmarkSessionActive

    val skipExitDiagnostics: Boolean
        get() = isBenchmarkSessionActive

    val exposeReadyTags: Boolean
        get() = isBenchmarkSessionActive

    val useOfflineRoleplayReplay: Boolean
        get() = isBenchmarkSessionActive && scenarioId == BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY

    companion object {
        fun resolve(
            appChannel: String,
            descriptor: BenchmarkSessionDescriptor?,
            requestedSessionId: String?,
            nowEpochMillis: Long,
        ): BenchmarkRuntimePolicy {
            if (appChannel != BenchmarkContract.BASELINE_CHANNEL) {
                return inactive(BenchmarkSessionRejection.WRONG_CHANNEL)
            }
            if (descriptor == null) {
                return inactive(BenchmarkSessionRejection.MISSING_DESCRIPTOR)
            }
            val rejection = descriptor.validateForLaunch(nowEpochMillis, requestedSessionId)
            if (rejection != null) {
                return inactive(rejection)
            }
            return BenchmarkRuntimePolicy(
                isBenchmarkSessionActive = true,
                scenarioId = descriptor.scenarioId,
                sessionId = descriptor.sessionId,
                rejection = null,
            )
        }

        private fun inactive(rejection: BenchmarkSessionRejection): BenchmarkRuntimePolicy {
            return BenchmarkRuntimePolicy(
                isBenchmarkSessionActive = false,
                scenarioId = null,
                sessionId = null,
                rejection = rejection,
            )
        }
    }
}

object BenchmarkProcessPolicy {
    const val FIXTURE_PROCESS_SUFFIX = ":benchmark_fixture"

    fun isBenchmarkFixtureProcess(
        applicationId: String,
        processName: String,
    ): Boolean {
        return applicationId.isNotBlank() && processName == applicationId + FIXTURE_PROCESS_SUFFIX
    }
}
