package com.example.myapplication.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val assertions = ReadyAssertions.create()
        val scenario = BenchmarkScenario.P1_BASELINE_PROFILE
        assertions.runWithSessionCleanup {
            val session = assertions.prepareProfileFixtureOrThrow(scenario)

            rule.collect(
                packageName = targetPackage(),
                includeInStartupProfile = true,
                strictStability = true,
                filterPredicate = { profileRule ->
                    profileRule.contains(APPLICATION_RULE_MARKER)
                },
            ) {
                killProcess()
                pressHome()
                assertions.startScenarioOrThrow(this, session)

                assertions.clickAndWaitForReadyOrThrow(
                    actionId = BenchmarkActionIds.OPEN_CHAT_HISTORY,
                    readyId = BenchmarkReadyIds.CHAT_LIST_READY,
                )
                assertions.pressBackAndWaitForReadyOrThrow(BenchmarkReadyIds.RETURNING_ROLEPLAY_READY)

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

                assertions.clickAndWaitForReadyOrThrow(
                    actionId = BenchmarkActionIds.OPEN_VOICE_SYNTHESIS,
                    readyId = BenchmarkReadyIds.VOICE_SYNTHESIS_READY,
                )
                assertions.clickAndWaitForReadyOrThrow(
                    actionId = BenchmarkActionIds.OPEN_VOICE_PROFILE,
                    readyId = BenchmarkReadyIds.VOICE_PROFILE_READY,
                )
                assertions.clickAndWaitForReadyOrThrow(
                    actionId = BenchmarkActionIds.CLOSE_VOICE_PROFILE,
                    readyId = BenchmarkReadyIds.VOICE_SYNTHESIS_READY,
                )
                assertions.pressBackAndWaitForReadyOrThrow(BenchmarkReadyIds.SETTINGS_READY)
                assertions.pressBackAndWaitForReadyOrThrow(BenchmarkReadyIds.RETURNING_ROLEPLAY_READY)
            }
        }
    }

    private fun targetPackage(): String {
        return InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")
    }

    private companion object {
        const val APPLICATION_RULE_MARKER = "Lcom/example/myapplication/"
    }
}
