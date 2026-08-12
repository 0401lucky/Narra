package com.example.myapplication.baselineprofile

internal object BenchmarkWireContract {
    const val FIXTURE_VERSION = "narra-fixture-v1"
    const val TRACE_APP_GRAPH = "narra.startup.app_graph"
    const val TRACE_STARTUP_SENSITIVE_MIGRATION = "narra.startup.sensitive_migration"
    const val TRACE_STARTUP_ENSURE_PRESETS = "narra.startup.ensure_presets"
    const val TRACE_STARTUP_CONTEXT_LOG_SETTINGS = "narra.startup.context_log_settings"
    const val TRACE_ROOM_OPEN = "narra.startup.room_open"
    const val TRACE_SETTINGS_DECODE = "narra.settings.decode"
    const val TRACE_SETTINGS_SECURE_READ = "narra.settings.secure_read"
    const val TRACE_SETTINGS_THEME_PROJECT = "narra.settings.project_theme"
    const val TRACE_SETTINGS_VIEW_MODEL_SYNC = "narra.settings.sync_view_model"
    const val TRACE_FIRST_MESSAGE_MAP = "narra.messages.first_map"
    const val TRACE_ROLEPLAY_MAP_MESSAGES = "narra.roleplay.map_messages"
    const val TRACE_ROLEPLAY_APPLY_MAPPED_MESSAGES = "narra.roleplay.apply_mapped"
    const val TRACE_ROLEPLAY_AUTO_SCROLL = "narra.roleplay.auto_scroll"
    const val TRACE_ROLEPLAY_REPLAY_PUMP = "narra.roleplay.replay_pump"
}

internal enum class BenchmarkPhase(val wireId: String) {
    MEASURE("measure"),
    PROFILE("profile"),
}

internal data class BenchmarkTraceSection(
    val name: String,
    val metricLabel: String,
) {
    val presenceMetricName: String
        get() = "${metricLabel}PresenceFirstMs"

    val countMetricName: String
        get() = "${metricLabel}Count"

    val totalDurationMetricName: String
        get() = "${metricLabel}TotalDurationSumMs"
}

internal enum class BenchmarkScenario(
    val wireId: String,
    val fixtureId: String,
    val expectedChatMessageCount: Int,
    val expectedRoleplayMessageCount: Int,
    val phase: BenchmarkPhase,
    val initialReadyId: String,
    val requiredTraceSections: List<BenchmarkTraceSection>,
) {
    S0_FIRST_RUN_STARTUP(
        wireId = "s0-first-run-startup",
        fixtureId = "s0-first-run",
        expectedChatMessageCount = 0,
        expectedRoleplayMessageCount = 0,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.FIRST_RUN_CONSENT_READY,
        requiredTraceSections = listOf(
            BenchmarkTraceSections.APP_GRAPH,
            BenchmarkTraceSections.STARTUP_SENSITIVE_MIGRATION,
            BenchmarkTraceSections.STARTUP_ENSURE_PRESETS,
            BenchmarkTraceSections.STARTUP_CONTEXT_LOG_SETTINGS,
            BenchmarkTraceSections.ROOM_OPEN,
            BenchmarkTraceSections.SETTINGS_DECODE,
            BenchmarkTraceSections.SETTINGS_SECURE_READ,
        ),
    ),
    S1_RETURNING_CORE_STARTUP(
        wireId = "s1-returning-core-startup",
        fixtureId = "s1-returning-core",
        expectedChatMessageCount = 12,
        expectedRoleplayMessageCount = 12,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
        requiredTraceSections = BenchmarkTraceSections.RETURNING_STARTUP,
    ),
    S2_POPULATED_STARTUP(
        wireId = "s2-populated-startup",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
        requiredTraceSections = BenchmarkTraceSections.RETURNING_STARTUP,
    ),
    S3_UPGRADE_STARTUP(
        wireId = "s3-upgrade-startup",
        fixtureId = "s3-supported-upgrade",
        expectedChatMessageCount = 12,
        expectedRoleplayMessageCount = 12,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
        requiredTraceSections = BenchmarkTraceSections.RETURNING_STARTUP,
    ),
    F1_CHAT_SCROLL(
        wireId = "f1-chat-scroll",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.CHAT_LIST_BOTTOM_READY,
        requiredTraceSections = emptyList(),
    ),
    F2_ROLEPLAY_SCROLL(
        wireId = "f2-roleplay-scroll",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.ROLEPLAY_LIST_BOTTOM_READY,
        requiredTraceSections = emptyList(),
    ),
    F3_ROLEPLAY_STREAM_REPLAY(
        wireId = "f3-roleplay-stream-replay",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.ROLEPLAY_STREAM_READY,
        requiredTraceSections = listOf(
            BenchmarkTraceSections.ROLEPLAY_MAP_MESSAGES,
            BenchmarkTraceSections.ROLEPLAY_APPLY_MAPPED_MESSAGES,
            BenchmarkTraceSections.ROLEPLAY_AUTO_SCROLL,
            BenchmarkTraceSections.ROLEPLAY_REPLAY_PUMP,
        ),
    ),
    N1_HEAVY_NAVIGATION(
        wireId = "n1-heavy-navigation",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.MEASURE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
        requiredTraceSections = listOf(
            BenchmarkTraceSections.SETTINGS_DECODE,
            BenchmarkTraceSections.SETTINGS_THEME_PROJECT,
            BenchmarkTraceSections.SETTINGS_VIEW_MODEL_SYNC,
            BenchmarkTraceSections.ROLEPLAY_MAP_MESSAGES,
        ),
    ),
    P1_BASELINE_PROFILE(
        wireId = "p1-baseline-profile",
        fixtureId = "s2-populated",
        expectedChatMessageCount = 100,
        expectedRoleplayMessageCount = 100,
        phase = BenchmarkPhase.PROFILE,
        initialReadyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
        requiredTraceSections = listOf(
            BenchmarkTraceSections.SETTINGS_DECODE,
            BenchmarkTraceSections.FIRST_MESSAGE_MAP,
            BenchmarkTraceSections.ROLEPLAY_MAP_MESSAGES,
        ),
    ),
}

internal object BenchmarkTraceSections {
    val APP_GRAPH = BenchmarkTraceSection(BenchmarkWireContract.TRACE_APP_GRAPH, "traceStartupAppGraph")
    val STARTUP_SENSITIVE_MIGRATION = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_STARTUP_SENSITIVE_MIGRATION,
        "traceStartupSensitiveMigration",
    )
    val STARTUP_ENSURE_PRESETS = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_STARTUP_ENSURE_PRESETS,
        "traceStartupEnsurePresets",
    )
    val STARTUP_CONTEXT_LOG_SETTINGS = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_STARTUP_CONTEXT_LOG_SETTINGS,
        "traceStartupContextLogSettings",
    )
    val ROOM_OPEN = BenchmarkTraceSection(BenchmarkWireContract.TRACE_ROOM_OPEN, "traceStartupRoomOpen")
    val SETTINGS_DECODE = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_SETTINGS_DECODE,
        "traceSettingsDecode",
    )
    val SETTINGS_SECURE_READ = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_SETTINGS_SECURE_READ,
        "traceSettingsSecureRead",
    )
    val SETTINGS_THEME_PROJECT = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_SETTINGS_THEME_PROJECT,
        "traceSettingsProjectTheme",
    )
    val SETTINGS_VIEW_MODEL_SYNC = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_SETTINGS_VIEW_MODEL_SYNC,
        "traceSettingsSyncViewModel",
    )
    val FIRST_MESSAGE_MAP = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_FIRST_MESSAGE_MAP,
        "traceMessagesFirstMap",
    )
    val ROLEPLAY_MAP_MESSAGES = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_ROLEPLAY_MAP_MESSAGES,
        "traceRoleplayMapMessages",
    )
    val ROLEPLAY_APPLY_MAPPED_MESSAGES = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_ROLEPLAY_APPLY_MAPPED_MESSAGES,
        "traceRoleplayApplyMappedMessages",
    )
    val ROLEPLAY_AUTO_SCROLL = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_ROLEPLAY_AUTO_SCROLL,
        "traceRoleplayAutoScroll",
    )
    val ROLEPLAY_REPLAY_PUMP = BenchmarkTraceSection(
        BenchmarkWireContract.TRACE_ROLEPLAY_REPLAY_PUMP,
        "traceRoleplayReplayPump",
    )

    val RETURNING_STARTUP = listOf(
        APP_GRAPH,
        STARTUP_SENSITIVE_MIGRATION,
        STARTUP_ENSURE_PRESETS,
        STARTUP_CONTEXT_LOG_SETTINGS,
        ROOM_OPEN,
        SETTINGS_DECODE,
        SETTINGS_SECURE_READ,
        FIRST_MESSAGE_MAP,
        ROLEPLAY_MAP_MESSAGES,
    )

}

internal object BenchmarkReadyIds {
    const val FIRST_RUN_CONSENT_READY = "benchmark_ready_first_run_consent"
    const val RETURNING_ROLEPLAY_READY = "benchmark_ready_returning_roleplay"
    const val CHAT_LIST_READY = "benchmark_ready_chat_list"
    const val CHAT_LIST_TOP_READY = "benchmark_ready_chat_list_top"
    const val CHAT_LIST_BOTTOM_READY = "benchmark_ready_chat_list_bottom"
    const val ROLEPLAY_LIST_READY = "benchmark_ready_roleplay_list"
    const val ROLEPLAY_LIST_TOP_READY = "benchmark_ready_roleplay_list_top"
    const val ROLEPLAY_LIST_BOTTOM_READY = "benchmark_ready_roleplay_list_bottom"
    const val ROLEPLAY_STREAM_READY = "benchmark_ready_roleplay_stream"
    const val ROLEPLAY_STREAM_COMPLETE_READY = "benchmark_ready_roleplay_stream_complete"
    const val SETTINGS_READY = "benchmark_ready_settings"
    const val ROLEPLAY_QUICK_SETTINGS_READY = "benchmark_ready_roleplay_quick_settings"
    const val READING_READY = "benchmark_ready_reading"
    const val VOICE_SYNTHESIS_READY = "benchmark_ready_voice_synthesis"
    const val VOICE_PROFILE_READY = "benchmark_ready_voice_profile"

    val ALL = setOf(
        FIRST_RUN_CONSENT_READY,
        RETURNING_ROLEPLAY_READY,
        CHAT_LIST_READY,
        CHAT_LIST_TOP_READY,
        CHAT_LIST_BOTTOM_READY,
        ROLEPLAY_LIST_READY,
        ROLEPLAY_LIST_TOP_READY,
        ROLEPLAY_LIST_BOTTOM_READY,
        ROLEPLAY_STREAM_READY,
        ROLEPLAY_STREAM_COMPLETE_READY,
        SETTINGS_READY,
        ROLEPLAY_QUICK_SETTINGS_READY,
        READING_READY,
        VOICE_SYNTHESIS_READY,
        VOICE_PROFILE_READY,
    )
}

internal object BenchmarkActionIds {
    const val OPEN_CHAT_HISTORY = "benchmark_action_open_chat_history"
    const val OPEN_SETTINGS = "benchmark_action_open_settings"
    const val OPEN_ROLEPLAY_QUICK_SETTINGS = "benchmark_action_open_roleplay_quick_settings"
    const val OPEN_READING = "benchmark_action_open_reading"
    const val OPEN_VOICE_SYNTHESIS = "benchmark_action_open_voice_synthesis"
    const val OPEN_VOICE_PROFILE = "benchmark_action_open_voice_profile"
    const val CLOSE_VOICE_PROFILE = "benchmark_action_close_voice_profile"
    const val START_OFFLINE_REPLAY = "benchmark_action_start_offline_replay"

    val ALL = setOf(
        OPEN_CHAT_HISTORY,
        OPEN_SETTINGS,
        OPEN_ROLEPLAY_QUICK_SETTINGS,
        OPEN_READING,
        OPEN_VOICE_SYNTHESIS,
        OPEN_VOICE_PROFILE,
        CLOSE_VOICE_PROFILE,
        START_OFFLINE_REPLAY,
    )
}
