package com.example.myapplication.system.performance

object BenchmarkContract {
    const val SCHEMA_VERSION = 1
    const val FIXTURE_VERSION = "narra-fixture-v1"
    const val BASELINE_CHANNEL = "baseline"
    const val DEFAULT_TTL_MILLIS = 5 * 60 * 1_000L
    // BaselineProfileRule 会执行多轮编译与路径采样，低负载设备上完整收集可能超过 10 分钟。
    const val MAX_TTL_MILLIS = 30 * 60 * 1_000L
    const val MAX_CLOCK_SKEW_MILLIS = 5_000L
    const val SESSION_ID_INTENT_EXTRA = "benchmark_session_id"
}

enum class BenchmarkFixtureId(
    val wireId: String,
    val chatMessageCount: Int,
    val roleplayMessageCount: Int,
    val acceptsCurrentCompliance: Boolean,
    val sourceDatabaseVersion: Int?,
) {
    S0_FIRST_RUN(
        wireId = "s0-first-run",
        chatMessageCount = 0,
        roleplayMessageCount = 0,
        acceptsCurrentCompliance = false,
        sourceDatabaseVersion = null,
    ),
    S1_RETURNING_CORE(
        wireId = "s1-returning-core",
        chatMessageCount = 12,
        roleplayMessageCount = 12,
        acceptsCurrentCompliance = true,
        sourceDatabaseVersion = null,
    ),
    S2_POPULATED(
        wireId = "s2-populated",
        chatMessageCount = 100,
        roleplayMessageCount = 100,
        acceptsCurrentCompliance = true,
        sourceDatabaseVersion = null,
    ),
    S3_SUPPORTED_UPGRADE(
        wireId = "s3-supported-upgrade",
        chatMessageCount = 12,
        roleplayMessageCount = 12,
        acceptsCurrentCompliance = true,
        sourceDatabaseVersion = 47,
    ),
}

enum class BenchmarkScenarioId(
    val wireId: String,
    val fixtureId: BenchmarkFixtureId,
    val initialReadyId: String,
) {
    S0_FIRST_RUN_STARTUP(
        wireId = "s0-first-run-startup",
        fixtureId = BenchmarkFixtureId.S0_FIRST_RUN,
        initialReadyId = BenchmarkReadyIds.FIRST_RUN_CONSENT_READY,
    ),
    S1_RETURNING_CORE_STARTUP(
        wireId = "s1-returning-core-startup",
        fixtureId = BenchmarkFixtureId.S1_RETURNING_CORE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
    ),
    S2_POPULATED_STARTUP(
        wireId = "s2-populated-startup",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
    ),
    S3_UPGRADE_STARTUP(
        wireId = "s3-upgrade-startup",
        fixtureId = BenchmarkFixtureId.S3_SUPPORTED_UPGRADE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
    ),
    F1_CHAT_SCROLL(
        wireId = "f1-chat-scroll",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.CHAT_LIST_BOTTOM_READY,
    ),
    F2_ROLEPLAY_SCROLL(
        wireId = "f2-roleplay-scroll",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.ROLEPLAY_LIST_BOTTOM_READY,
    ),
    F3_ROLEPLAY_STREAM_REPLAY(
        wireId = "f3-roleplay-stream-replay",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.ROLEPLAY_STREAM_READY,
    ),
    N1_HEAVY_NAVIGATION(
        wireId = "n1-heavy-navigation",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
    ),
    P1_BASELINE_PROFILE(
        wireId = "p1-baseline-profile",
        fixtureId = BenchmarkFixtureId.S2_POPULATED,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
    );

    companion object {
        fun fromWireId(value: String): BenchmarkScenarioId? {
            return entries.firstOrNull { it.wireId == value }
        }
    }
}

enum class BenchmarkPhase(val wireId: String) {
    PREPARE("prepare"),
    MEASURE("measure"),
    PROFILE("profile");

    companion object {
        fun fromWireId(value: String): BenchmarkPhase? {
            return entries.firstOrNull { it.wireId == value }
        }
    }
}
