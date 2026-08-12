package com.example.myapplication.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class F3BenchmarkContractSmokeTest {

    @Test(timeout = 60_000L)
    fun f3Session_initialReadyAndReplayCompletionContractCompletes() {
        assertions.runWithSessionCleanup {
            val session = assertions.prepareProfileFixtureOrThrow(
                BenchmarkScenario.F3_ROLEPLAY_STREAM_REPLAY,
            )
            launchTargetOrThrow(session.sessionId)

            assertions.waitForReadyOrThrow(BenchmarkReadyIds.ROLEPLAY_STREAM_READY)
            assertNodePresent(BenchmarkActionIds.START_OFFLINE_REPLAY)
            assertNodesRemainAbsent(
                nodeIds = listOf(BenchmarkReadyIds.ROLEPLAY_STREAM_COMPLETE_READY),
                timeoutMillis = PRE_CLICK_ABSENCE_TIMEOUT_MILLIS,
            )

            assertions.clickAndWaitForReadyOrThrow(
                actionId = BenchmarkActionIds.START_OFFLINE_REPLAY,
                readyId = BenchmarkReadyIds.ROLEPLAY_STREAM_COMPLETE_READY,
            )
        }
    }

    @Test(timeout = 60_000L)
    fun nonF3Session_doesNotExposeF3ActionOrReadies() {
        assertions.runWithSessionCleanup {
            val session = assertions.prepareProfileFixtureOrThrow(
                BenchmarkScenario.F2_ROLEPLAY_SCROLL,
            )
            launchTargetOrThrow(session.sessionId)
            assertions.waitForReadyOrThrow(BenchmarkScenario.F2_ROLEPLAY_SCROLL.initialReadyId)

            assertNodesRemainAbsent(F3_CONTROL_IDS, FORBIDDEN_EXPOSURE_TIMEOUT_MILLIS)
        }
    }

    @Test(timeout = 60_000L)
    fun mismatchedSession_doesNotExposeF3ActionOrReadies() {
        assertions.runWithSessionCleanup {
            val session = assertions.prepareProfileFixtureOrThrow(
                BenchmarkScenario.F3_ROLEPLAY_STREAM_REPLAY,
            )
            launchTargetOrThrow("${session.sessionId}-mismatch")

            assertNodesRemainAbsent(F3_CONTROL_IDS, FORBIDDEN_EXPOSURE_TIMEOUT_MILLIS)
        }
    }

    private val instrumentation by lazy { InstrumentationRegistry.getInstrumentation() }
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val assertions by lazy { ReadyAssertions.create() }
    private val targetPackage by lazy {
        InstrumentationRegistry.getArguments().getString(ARG_TARGET_APP_ID)
            ?.takeIf(String::isNotBlank)
            ?: error("缺少 $ARG_TARGET_APP_ID instrumentation 参数")
    }

    private fun launchTargetOrThrow(sessionId: String) {
        val launchIntent = instrumentation.context.packageManager
            .getLaunchIntentForPackage(targetPackage)
            ?: error("找不到 benchmark 目标应用启动入口")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launchIntent.putExtra(ARG_SESSION_ID, sessionId)
        instrumentation.context.startActivity(launchIntent)

        val targetWindowShown = device.wait(
            Until.hasObject(By.pkg(targetPackage).depth(0)),
            TARGET_WINDOW_TIMEOUT_MILLIS,
        )
        if (!targetWindowShown) {
            throw AssertionError("benchmark 目标窗口未在超时内显示")
        }
        device.waitForIdle(TARGET_IDLE_TIMEOUT_MILLIS)
    }

    private fun assertNodePresent(nodeId: String) {
        if (!device.hasObject(By.res(nodeId))) {
            throw AssertionError("F3 benchmark 节点未暴露：$nodeId")
        }
    }

    private fun assertNodesRemainAbsent(
        nodeIds: Collection<String>,
        timeoutMillis: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            val visibleIds = nodeIds.filter { nodeId -> device.hasObject(By.res(nodeId)) }
            if (visibleIds.isNotEmpty()) {
                throw AssertionError("不应暴露 F3 benchmark 节点：${visibleIds.joinToString()}")
            }
            val remainingMillis = deadline - SystemClock.uptimeMillis()
            if (remainingMillis <= 0L) {
                return
            }
            SystemClock.sleep(minOf(NEGATIVE_ASSERTION_POLL_MILLIS, remainingMillis))
        }
    }

    private companion object {
        const val ARG_TARGET_APP_ID = "targetAppId"
        const val ARG_SESSION_ID = "benchmark_session_id"
        const val TARGET_WINDOW_TIMEOUT_MILLIS = 30_000L
        const val TARGET_IDLE_TIMEOUT_MILLIS = 2_000L
        const val PRE_CLICK_ABSENCE_TIMEOUT_MILLIS = 750L
        const val FORBIDDEN_EXPOSURE_TIMEOUT_MILLIS = 1_500L
        const val NEGATIVE_ASSERTION_POLL_MILLIS = 100L

        val F3_CONTROL_IDS = listOf(
            BenchmarkActionIds.START_OFFLINE_REPLAY,
            BenchmarkReadyIds.ROLEPLAY_STREAM_READY,
            BenchmarkReadyIds.ROLEPLAY_STREAM_COMPLETE_READY,
        )
    }
}
