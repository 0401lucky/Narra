package com.example.myapplication.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@OptIn(ExperimentalMetricApi::class)
class FrameBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @get:Rule
    val testName = TestName()

    @Test
    fun f1ChatScroll() {
        benchmark(BenchmarkScenario.F1_CHAT_SCROLL) { assertions ->
            assertions.performFixedScrollRoundTripOrThrow(
                topReadyId = BenchmarkReadyIds.CHAT_LIST_TOP_READY,
                bottomReadyId = BenchmarkReadyIds.CHAT_LIST_BOTTOM_READY,
            )
        }
    }

    @Test
    fun f2RoleplayScroll() {
        benchmark(BenchmarkScenario.F2_ROLEPLAY_SCROLL) { assertions ->
            assertions.performFixedScrollRoundTripOrThrow(
                topReadyId = BenchmarkReadyIds.ROLEPLAY_LIST_TOP_READY,
                bottomReadyId = BenchmarkReadyIds.ROLEPLAY_LIST_BOTTOM_READY,
            )
        }
    }

    @Test
    fun f3RoleplayOfflineStreamReplay() {
        benchmark(BenchmarkScenario.F3_ROLEPLAY_STREAM_REPLAY) { assertions ->
            assertions.clickAndWaitForReadyOrThrow(
                actionId = BenchmarkActionIds.START_OFFLINE_REPLAY,
                readyId = BenchmarkReadyIds.ROLEPLAY_STREAM_COMPLETE_READY,
            )
        }
    }

    @Test
    fun n1SettingsAndReadingNavigation() {
        benchmark(BenchmarkScenario.N1_HEAVY_NAVIGATION) { assertions ->
            assertions.clickAndWaitForReadyOrThrow(
                actionId = BenchmarkActionIds.OPEN_SETTINGS,
                readyId = BenchmarkReadyIds.SETTINGS_READY,
            )
            assertions.clickAndWaitForReadyOrThrow(
                actionId = BenchmarkActionIds.OPEN_ROLEPLAY_QUICK_SETTINGS,
                readyId = BenchmarkReadyIds.ROLEPLAY_QUICK_SETTINGS_READY,
            )
            assertions.clickAndWaitForReadyOrThrow(
                actionId = BenchmarkActionIds.OPEN_READING,
                readyId = BenchmarkReadyIds.READING_READY,
            )
            assertions.pressBackAndWaitForReadyOrThrow(BenchmarkReadyIds.ROLEPLAY_QUICK_SETTINGS_READY)
            assertions.pressBackAndWaitForReadyOrThrow(BenchmarkReadyIds.RETURNING_ROLEPLAY_READY)
        }
    }

    private fun benchmark(
        scenario: BenchmarkScenario,
        measure: (ReadyAssertions) -> Unit,
    ) {
        val assertions = ReadyAssertions.create()
        lateinit var session: BenchmarkSession
        BenchmarkRunManifestWriter.recordScenario(
            benchmarkName = testName.methodName,
            scenario = scenario,
            compilationMode = "partial-require-baseline",
            iterations = FRAME_ITERATIONS,
        )
        assertions.runWithSessionCleanup {
            rule.measureRepeated(
                packageName = targetPackage(),
                metrics = listOf(FrameTimingMetric()) + scenario.traceMetrics(),
                compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
                iterations = FRAME_ITERATIONS,
                setupBlock = {
                    session = assertions.prepareFixtureOrThrow(this, scenario)
                    assertions.startScenarioOrThrow(this, session)
                },
                measureBlock = {
                    measure(assertions)
                },
            )
        }
    }

    private fun targetPackage(): String {
        return InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")
    }

    private companion object {
        const val FRAME_ITERATIONS = 5
    }
}
