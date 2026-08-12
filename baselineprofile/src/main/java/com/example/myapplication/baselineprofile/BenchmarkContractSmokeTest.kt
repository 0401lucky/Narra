package com.example.myapplication.baselineprofile

import android.content.Intent
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
class BenchmarkContractSmokeTest {

    @Test(timeout = 60_000L)
    fun s0Fixture_sessionAndReadyContractCompletes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = InstrumentationRegistry.getArguments().getString(ARG_TARGET_APP_ID)
            ?.takeIf(String::isNotBlank)
            ?: error("缺少 $ARG_TARGET_APP_ID instrumentation 参数")
        val assertions = ReadyAssertions.create()
        val device = UiDevice.getInstance(instrumentation)

        assertions.runWithSessionCleanup {
            val session = assertions.prepareProfileFixtureOrThrow(BenchmarkScenario.S0_FIRST_RUN_STARTUP)
            val launchIntent = instrumentation.context.packageManager
                .getLaunchIntentForPackage(targetPackage)
                ?: error("找不到 benchmark 目标应用启动入口")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            launchIntent.putExtra(ARG_SESSION_ID, session.sessionId)
            instrumentation.context.startActivity(launchIntent)
            val targetWindowShown = device.wait(
                Until.hasObject(By.pkg(targetPackage).depth(0)),
                TARGET_WINDOW_TIMEOUT_MILLIS,
            )
            if (!targetWindowShown) {
                throw AssertionError("benchmark 目标窗口未在超时内显示")
            }

            try {
                assertions.waitForReadyOrThrow(BenchmarkReadyIds.FIRST_RUN_CONSENT_READY)
            } catch (throwable: AssertionError) {
                val visibleNodes = device.findObjects(By.pkg(targetPackage))
                    .asSequence()
                    .mapNotNull { node ->
                        listOfNotNull(
                            node.resourceName?.let { "res=${it.take(MAX_NODE_VALUE_LENGTH)}" },
                            node.text?.let { "text=${it.take(MAX_NODE_VALUE_LENGTH)}" },
                            node.contentDescription?.let { "desc=${it.take(MAX_NODE_VALUE_LENGTH)}" },
                        ).takeIf(List<String>::isNotEmpty)?.joinToString(",")
                    }
                    .take(MAX_VISIBLE_NODE_COUNT)
                    .joinToString(" | ")
                throw AssertionError("S0 ready 未出现，可见节点：$visibleNodes").also { error ->
                    error.initCause(throwable)
                }
            }
        }
    }

    private companion object {
        const val ARG_TARGET_APP_ID = "targetAppId"
        const val ARG_SESSION_ID = "benchmark_session_id"
        const val TARGET_WINDOW_TIMEOUT_MILLIS = 30_000L
        const val MAX_VISIBLE_NODE_COUNT = 24
        const val MAX_NODE_VALUE_LENGTH = 80
    }
}
