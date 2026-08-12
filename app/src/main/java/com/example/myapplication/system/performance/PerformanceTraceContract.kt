package com.example.myapplication.system.performance

enum class TraceEmitterOwner {
    STARTUP_SETTINGS,
    ROLEPLAY,
    BENCHMARK_FIXTURE,
    DIAGNOSTICS,
    UNASSIGNED,
}

data class PerformanceTraceSection(
    val name: String,
    val owner: TraceEmitterOwner,
)

data class TraceSectionObservation(
    val sectionName: String,
    val count: Int,
    val totalDurationNanos: Long,
    val p50DurationNanos: Long,
    val p90DurationNanos: Long,
    val p95DurationNanos: Long,
) {
    init {
        require(count >= 0) { "Trace 次数不能为负数" }
        require(totalDurationNanos >= 0L) { "Trace 总耗时不能为负数" }
    }

    val isPresent: Boolean
        get() = count > 0
}

data class PerformanceTraceResult(
    val scenarioId: BenchmarkScenarioId,
    val sections: Map<String, TraceSectionObservation>,
    val missingSections: Set<String>,
) {
    val isDecidable: Boolean
        get() = missingSections.isEmpty()
}

object PerformanceTraceContract {
    const val MAX_SECTION_NAME_LENGTH = 64

    const val APP_GRAPH = "narra.startup.app_graph"
    const val STARTUP_SENSITIVE_MIGRATION = "narra.startup.sensitive_migration"
    const val STARTUP_ENSURE_PRESETS = "narra.startup.ensure_presets"
    const val STARTUP_CONTEXT_LOG_SETTINGS = "narra.startup.context_log_settings"
    const val ROOM_OPEN = "narra.startup.room_open"
    const val SETTINGS_DECODE = "narra.settings.decode"
    const val SETTINGS_SECURE_READ = "narra.settings.secure_read"
    const val SETTINGS_THEME_PROJECT = "narra.settings.project_theme"
    const val SETTINGS_VIEW_MODEL_SYNC = "narra.settings.sync_view_model"
    const val FIRST_MESSAGE_MAP = "narra.messages.first_map"
    const val ROLEPLAY_MAP_MESSAGES = "narra.roleplay.map_messages"
    const val ROLEPLAY_APPLY_MAPPED_MESSAGES = "narra.roleplay.apply_mapped"
    const val ROLEPLAY_AUTO_SCROLL = "narra.roleplay.auto_scroll"
    const val ROLEPLAY_REPLAY_PUMP = "narra.roleplay.replay_pump"
    const val FIXTURE_PREPARE = "narra.benchmark.fixture_prepare"
    const val EXIT_COLLECT = "narra.diagnostics.exit_collect"
    const val DIAGNOSTICS_EXPORT = "narra.diagnostics.export"

    val ALL_SECTIONS: List<PerformanceTraceSection> = listOf(
        PerformanceTraceSection(APP_GRAPH, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(STARTUP_SENSITIVE_MIGRATION, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(STARTUP_ENSURE_PRESETS, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(STARTUP_CONTEXT_LOG_SETTINGS, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(ROOM_OPEN, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(SETTINGS_DECODE, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(SETTINGS_SECURE_READ, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(SETTINGS_THEME_PROJECT, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(SETTINGS_VIEW_MODEL_SYNC, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(FIRST_MESSAGE_MAP, TraceEmitterOwner.STARTUP_SETTINGS),
        PerformanceTraceSection(ROLEPLAY_MAP_MESSAGES, TraceEmitterOwner.ROLEPLAY),
        PerformanceTraceSection(ROLEPLAY_APPLY_MAPPED_MESSAGES, TraceEmitterOwner.ROLEPLAY),
        PerformanceTraceSection(ROLEPLAY_AUTO_SCROLL, TraceEmitterOwner.ROLEPLAY),
        PerformanceTraceSection(ROLEPLAY_REPLAY_PUMP, TraceEmitterOwner.ROLEPLAY),
        PerformanceTraceSection(FIXTURE_PREPARE, TraceEmitterOwner.BENCHMARK_FIXTURE),
        PerformanceTraceSection(EXIT_COLLECT, TraceEmitterOwner.DIAGNOSTICS),
        PerformanceTraceSection(DIAGNOSTICS_EXPORT, TraceEmitterOwner.DIAGNOSTICS),
    )

    private val sectionsByName = ALL_SECTIONS.associateBy(PerformanceTraceSection::name)

    fun section(name: String): PerformanceTraceSection? = sectionsByName[name]

    fun requiredSections(scenarioId: BenchmarkScenarioId): Set<String> {
        return when (scenarioId) {
            BenchmarkScenarioId.S0_FIRST_RUN_STARTUP -> setOf(
                APP_GRAPH,
                STARTUP_SENSITIVE_MIGRATION,
                STARTUP_ENSURE_PRESETS,
                STARTUP_CONTEXT_LOG_SETTINGS,
                ROOM_OPEN,
                SETTINGS_DECODE,
                SETTINGS_SECURE_READ,
            )

            BenchmarkScenarioId.S1_RETURNING_CORE_STARTUP,
            BenchmarkScenarioId.S2_POPULATED_STARTUP,
            BenchmarkScenarioId.S3_UPGRADE_STARTUP,
            -> setOf(
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

            BenchmarkScenarioId.F1_CHAT_SCROLL,
            BenchmarkScenarioId.F2_ROLEPLAY_SCROLL,
            -> emptySet()
            BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY -> setOf(
                ROLEPLAY_MAP_MESSAGES,
                ROLEPLAY_APPLY_MAPPED_MESSAGES,
                ROLEPLAY_AUTO_SCROLL,
                ROLEPLAY_REPLAY_PUMP,
            )

            BenchmarkScenarioId.N1_HEAVY_NAVIGATION -> setOf(
                SETTINGS_DECODE,
                SETTINGS_THEME_PROJECT,
                SETTINGS_VIEW_MODEL_SYNC,
                ROLEPLAY_MAP_MESSAGES,
            )

            BenchmarkScenarioId.P1_BASELINE_PROFILE -> setOf(
                SETTINGS_DECODE,
                FIRST_MESSAGE_MAP,
                ROLEPLAY_MAP_MESSAGES,
            )
        }
    }

    fun evaluate(
        scenarioId: BenchmarkScenarioId,
        observations: List<TraceSectionObservation>,
    ): PerformanceTraceResult {
        val observationsByName = observations.associateBy(TraceSectionObservation::sectionName)
        val required = requiredSections(scenarioId)
        val sections = required.associateWith { name ->
            observationsByName[name] ?: TraceSectionObservation(
                sectionName = name,
                count = 0,
                totalDurationNanos = 0L,
                p50DurationNanos = 0L,
                p90DurationNanos = 0L,
                p95DurationNanos = 0L,
            )
        }
        return PerformanceTraceResult(
            scenarioId = scenarioId,
            sections = sections,
            missingSections = sections.values
                .filterNot(TraceSectionObservation::isPresent)
                .mapTo(linkedSetOf(), TraceSectionObservation::sectionName),
        )
    }
}
