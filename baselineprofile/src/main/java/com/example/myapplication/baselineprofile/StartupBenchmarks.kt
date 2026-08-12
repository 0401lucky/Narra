package com.example.myapplication.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
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
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @get:Rule
    val testName = TestName()

    @Test
    fun s0FirstRun_compilationNone() = benchmark(
        BenchmarkScenario.S0_FIRST_RUN_STARTUP,
        CompilationMode.None(),
    )

    @Test
    fun s0FirstRun_requireBaselineProfile() = benchmark(
        BenchmarkScenario.S0_FIRST_RUN_STARTUP,
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun s1ReturningCore_compilationNone() = benchmark(
        BenchmarkScenario.S1_RETURNING_CORE_STARTUP,
        CompilationMode.None(),
    )

    @Test
    fun s1ReturningCore_requireBaselineProfile() = benchmark(
        BenchmarkScenario.S1_RETURNING_CORE_STARTUP,
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun s2Populated_compilationNone() = benchmark(
        BenchmarkScenario.S2_POPULATED_STARTUP,
        CompilationMode.None(),
    )

    @Test
    fun s2Populated_requireBaselineProfile() = benchmark(
        BenchmarkScenario.S2_POPULATED_STARTUP,
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun s3SupportedUpgrade_compilationNone() = benchmark(
        BenchmarkScenario.S3_UPGRADE_STARTUP,
        CompilationMode.None(),
    )

    @Test
    fun s3SupportedUpgrade_requireBaselineProfile() = benchmark(
        BenchmarkScenario.S3_UPGRADE_STARTUP,
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    private fun benchmark(
        scenario: BenchmarkScenario,
        compilationMode: CompilationMode,
    ) {
        val assertions = ReadyAssertions.create()
        lateinit var session: BenchmarkSession
        BenchmarkRunManifestWriter.recordScenario(
            benchmarkName = testName.methodName,
            scenario = scenario,
            compilationMode = compilationMode.manifestName(),
            iterations = STARTUP_ITERATIONS,
        )
        assertions.runWithSessionCleanup {
            rule.measureRepeated(
                packageName = targetPackage(),
                metrics = listOf(StartupTimingMetric()) + scenario.traceMetrics(),
                compilationMode = compilationMode,
                startupMode = StartupMode.COLD,
                iterations = STARTUP_ITERATIONS,
                setupBlock = {
                    session = assertions.prepareFixtureOrThrow(this, scenario)
                },
                measureBlock = {
                    assertions.startScenarioOrThrow(this, session)
                },
            )
        }
    }

    private fun targetPackage(): String {
        return InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")
    }

    private fun CompilationMode.manifestName(): String {
        return when (this) {
            is CompilationMode.None -> "none"
            is CompilationMode.Partial -> "partial-require-baseline"
            else -> javaClass.simpleName
        }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 10
    }
}
