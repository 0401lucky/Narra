package com.example.myapplication.baselineprofile

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.benchmark.Outputs
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在测量区间外写入可复现实验环境与 Trace 消费契约。
 * 原始 benchmark JSON 和 Perfetto 由 AndroidX Benchmark 生成，宿主侧归档任务再统一校验与哈希。
 */
@SuppressLint("RestrictedApi")
internal object BenchmarkRunManifestWriter {
    private const val SCHEMA_VERSION = 1
    private const val ANDROIDX_BENCHMARK_VERSION = "1.5.0-alpha03"
    private val runIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{7,95}")

    fun recordScenario(
        benchmarkName: String,
        scenario: BenchmarkScenario,
        compilationMode: String,
        iterations: Int,
    ) {
        require(iterations > 0) { "benchmark 迭代次数必须大于 0" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val runId = arguments.getString(ARG_RUN_ID)
            ?.takeIf(runIdPattern::matches)
            ?: error("缺少有效的 $ARG_RUN_ID instrumentation 参数")
        val targetPackage = arguments.getString(ARG_TARGET_APP_ID)
            ?.takeIf(String::isNotBlank)
            ?: error("缺少 $ARG_TARGET_APP_ID instrumentation 参数")
        val context = instrumentation.targetContext
        val packageInfo = context.packageManager.getPackageInfo(targetPackage, 0)

        val manifest = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("runId", runId)
            put("benchmarkName", benchmarkName)
            put("scenarioId", scenario.wireId)
            put("phase", scenario.phase.wireId)
            put("fixtureVersion", BenchmarkWireContract.FIXTURE_VERSION)
            put("iterations", iterations)
            put("compilationMode", compilationMode)
            put("buildMode", "baseline")
            put("createdAtEpochMillis", System.currentTimeMillis())
            put("appVersionName", packageInfo.versionName.orEmpty())
            put("appVersionCode", packageInfo.longVersionCode)
            put("commit", arguments.valueOrUnknown(ARG_COMMIT))
            put("androidxBenchmarkVersion", ANDROIDX_BENCHMARK_VERSION)
            put("agpVersion", arguments.valueOrUnknown(ARG_AGP_VERSION))
            put("gradleVersion", arguments.valueOrUnknown(ARG_GRADLE_VERSION))
            put("jdkVersion", arguments.valueOrUnknown(ARG_JDK_VERSION))
            put("environment", environmentJson(context))
            put("requiredTraceSections", traceSectionsJson(scenario))
        }

        val safeBenchmarkName = benchmarkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        Outputs.writeFile(
            fileName = "narra-environment-$runId-$safeBenchmarkName.json",
            reportOnRunEndOnly = true,
        ) { file ->
            file.writeText(manifest.toString(2), Charsets.UTF_8)
        }
    }

    private fun environmentJson(context: Context): JSONObject {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val windowManager = context.getSystemService(WindowManager::class.java)
        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("buildFingerprint", Build.FINGERPRINT)
            put("refreshRateHz", windowManager?.defaultDisplay?.refreshRate ?: JSONObject.NULL)
            put("windowAnimationScale", globalScale(context, Settings.Global.WINDOW_ANIMATION_SCALE))
            put("transitionAnimationScale", globalScale(context, Settings.Global.TRANSITION_ANIMATION_SCALE))
            put("animatorDurationScale", globalScale(context, Settings.Global.ANIMATOR_DURATION_SCALE))
            put(
                "batteryPercent",
                batteryManager
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    ?.takeUnless { it == Int.MIN_VALUE }
                    ?: JSONObject.NULL,
            )
            put(
                "thermalStatus",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager?.currentThermalStatus ?: JSONObject.NULL
                } else {
                    JSONObject.NULL
                },
            )
        }
    }

    private fun traceSectionsJson(scenario: BenchmarkScenario): JSONArray {
        return JSONArray().apply {
            scenario.requiredTraceSections.forEach { section ->
                put(
                    JSONObject().apply {
                        put("sectionName", section.name)
                        put("presenceMetric", section.presenceMetricName)
                        put("countMetric", section.countMetricName)
                        put("totalDurationMetric", section.totalDurationMetricName)
                    },
                )
            }
        }
    }

    private fun globalScale(
        context: Context,
        key: String,
    ): Any {
        return runCatching { Settings.Global.getFloat(context.contentResolver, key) }
            .getOrNull()
            ?: JSONObject.NULL
    }

    private fun android.os.Bundle.valueOrUnknown(key: String): String {
        return getString(key).orEmpty().ifBlank { "unknown" }
    }

    private const val ARG_RUN_ID = "narraBenchmarkRunId"
    private const val ARG_TARGET_APP_ID = "targetAppId"
    private const val ARG_COMMIT = "narraBenchmarkCommit"
    private const val ARG_AGP_VERSION = "narraBenchmarkAgpVersion"
    private const val ARG_GRADLE_VERSION = "narraBenchmarkGradleVersion"
    private const val ARG_JDK_VERSION = "narraBenchmarkJdkVersion"
}
