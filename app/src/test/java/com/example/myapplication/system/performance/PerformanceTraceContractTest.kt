package com.example.myapplication.system.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTraceContractTest {

    @Test
    fun sectionNames_areUniqueStaticShortAndPrivacySafe() {
        val sections = PerformanceTraceContract.ALL_SECTIONS
        val forbiddenTokens = listOf("session", "message_id", "content", "title", "prompt", "api_key", "{")

        assertEquals(sections.size, sections.map { it.name }.toSet().size)
        assertTrue(sections.all { it.name.length <= PerformanceTraceContract.MAX_SECTION_NAME_LENGTH })
        assertTrue(sections.all { it.name.matches(Regex("[a-z0-9_.]+")) })
        assertTrue(sections.none { section -> forbiddenTokens.any(section.name::contains) })
    }

    @Test
    fun evaluate_zeroDurationEmission_isPresent_butMissingEmissionIsReported() {
        val required = PerformanceTraceContract.requiredSections(BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY)
        val presentWithZeroDuration = required.first()
        val observations = listOf(
            TraceSectionObservation(
                sectionName = presentWithZeroDuration,
                count = 1,
                totalDurationNanos = 0L,
                p50DurationNanos = 0L,
                p90DurationNanos = 0L,
                p95DurationNanos = 0L,
            ),
        )

        val result = PerformanceTraceContract.evaluate(
            scenarioId = BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY,
            observations = observations,
        )

        assertTrue(result.sections.getValue(presentWithZeroDuration).isPresent)
        assertEquals(0L, result.sections.getValue(presentWithZeroDuration).totalDurationNanos)
        assertFalse(result.isDecidable)
        assertEquals(required - presentWithZeroDuration, result.missingSections)
    }

    @Test
    fun catalog_declaresEmitterOwnerForEverySection() {
        assertTrue(PerformanceTraceContract.ALL_SECTIONS.all { it.owner != TraceEmitterOwner.UNASSIGNED })
        listOf(
            PerformanceTraceContract.APP_GRAPH,
            PerformanceTraceContract.STARTUP_SENSITIVE_MIGRATION,
            PerformanceTraceContract.STARTUP_ENSURE_PRESETS,
            PerformanceTraceContract.STARTUP_CONTEXT_LOG_SETTINGS,
            PerformanceTraceContract.SETTINGS_THEME_PROJECT,
            PerformanceTraceContract.SETTINGS_VIEW_MODEL_SYNC,
        ).forEach { sectionName ->
            assertEquals(
                TraceEmitterOwner.STARTUP_SETTINGS,
                PerformanceTraceContract.section(sectionName)?.owner,
            )
        }
        assertEquals(
            TraceEmitterOwner.BENCHMARK_FIXTURE,
            PerformanceTraceContract.section(PerformanceTraceContract.FIXTURE_PREPARE)?.owner,
        )
        assertEquals(
            TraceEmitterOwner.ROLEPLAY,
            PerformanceTraceContract.section(PerformanceTraceContract.ROLEPLAY_MAP_MESSAGES)?.owner,
        )
    }

    @Test
    fun p1Profile_requiresSettingsAndMessageMappingTraces() {
        assertEquals(
            setOf(
                PerformanceTraceContract.SETTINGS_DECODE,
                PerformanceTraceContract.FIRST_MESSAGE_MAP,
                PerformanceTraceContract.ROLEPLAY_MAP_MESSAGES,
            ),
            PerformanceTraceContract.requiredSections(BenchmarkScenarioId.P1_BASELINE_PROFILE),
        )
    }

    @Test
    fun f3Replay_requiresPumpMappingStatePublicationAndAutoScrollTraces() {
        assertEquals(
            setOf(
                PerformanceTraceContract.ROLEPLAY_MAP_MESSAGES,
                PerformanceTraceContract.ROLEPLAY_APPLY_MAPPED_MESSAGES,
                PerformanceTraceContract.ROLEPLAY_AUTO_SCROLL,
                PerformanceTraceContract.ROLEPLAY_REPLAY_PUMP,
            ),
            PerformanceTraceContract.requiredSections(BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY),
        )
    }

    @Test
    fun startupScenarios_keepAggregateAndRequireIndependentStartupTasks() {
        val startupTaskSections = setOf(
            PerformanceTraceContract.STARTUP_SENSITIVE_MIGRATION,
            PerformanceTraceContract.STARTUP_ENSURE_PRESETS,
            PerformanceTraceContract.STARTUP_CONTEXT_LOG_SETTINGS,
        )

        listOf(
            BenchmarkScenarioId.S0_FIRST_RUN_STARTUP,
            BenchmarkScenarioId.S1_RETURNING_CORE_STARTUP,
            BenchmarkScenarioId.S2_POPULATED_STARTUP,
            BenchmarkScenarioId.S3_UPGRADE_STARTUP,
        ).forEach { scenarioId ->
            val required = PerformanceTraceContract.requiredSections(scenarioId)

            assertTrue(required.contains(PerformanceTraceContract.APP_GRAPH))
            assertTrue(required.containsAll(startupTaskSections))
        }
    }

    @Test
    fun startupAndSettingsSectionNames_keepStableNarraContract() {
        assertEquals("narra.startup.app_graph", PerformanceTraceContract.APP_GRAPH)
        assertEquals(
            "narra.startup.sensitive_migration",
            PerformanceTraceContract.STARTUP_SENSITIVE_MIGRATION,
        )
        assertEquals(
            "narra.startup.ensure_presets",
            PerformanceTraceContract.STARTUP_ENSURE_PRESETS,
        )
        assertEquals(
            "narra.startup.context_log_settings",
            PerformanceTraceContract.STARTUP_CONTEXT_LOG_SETTINGS,
        )
        assertEquals("narra.settings.project_theme", PerformanceTraceContract.SETTINGS_THEME_PROJECT)
        assertEquals("narra.settings.sync_view_model", PerformanceTraceContract.SETTINGS_VIEW_MODEL_SYNC)
    }

    @Test
    fun n1Navigation_requiresThemeProjectionAndSettingsViewModelSync() {
        assertEquals(
            setOf(
                PerformanceTraceContract.SETTINGS_DECODE,
                PerformanceTraceContract.SETTINGS_THEME_PROJECT,
                PerformanceTraceContract.SETTINGS_VIEW_MODEL_SYNC,
                PerformanceTraceContract.ROLEPLAY_MAP_MESSAGES,
            ),
            PerformanceTraceContract.requiredSections(BenchmarkScenarioId.N1_HEAVY_NAVIGATION),
        )
    }
}
