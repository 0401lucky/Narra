package com.example.myapplication.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.example.myapplication.system.performance.BenchmarkActionIds
import com.example.myapplication.system.performance.BenchmarkReadyIds
import com.example.myapplication.system.performance.BenchmarkRuntimePolicy
import com.example.myapplication.BuildConfig
import com.example.myapplication.system.performance.BenchmarkContract

val LocalBenchmarkRuntimePolicy = staticCompositionLocalOf<BenchmarkRuntimePolicy?> { null }

@Composable
fun ProvideBenchmarkRuntimePolicy(
    policy: BenchmarkRuntimePolicy,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBenchmarkRuntimePolicy provides policy,
        content = content,
    )
}

@Composable
fun Modifier.benchmarkReadyTag(
    readyId: String,
    ready: Boolean,
): Modifier {
    if (BuildConfig.APP_CHANNEL != BenchmarkContract.BASELINE_CHANNEL) {
        return this
    }
    val policy = LocalBenchmarkRuntimePolicy.current
    if (policy?.exposeReadyTags != true || !ready) {
        return this
    }
    require(readyId in BenchmarkReadyIds.ALL) { "未登记的 benchmark ready：$readyId" }
    return testTag(readyId)
}

@Composable
fun Modifier.benchmarkActionTag(actionId: String): Modifier {
    if (BuildConfig.APP_CHANNEL != BenchmarkContract.BASELINE_CHANNEL) {
        return this
    }
    val policy = LocalBenchmarkRuntimePolicy.current
    if (policy?.exposeReadyTags != true) {
        return this
    }
    require(actionId in BenchmarkActionIds.ALL) { "未登记的 benchmark action：$actionId" }
    return testTag(actionId)
}

@Composable
fun Modifier.benchmarkResourceIdHost(): Modifier {
    if (BuildConfig.APP_CHANNEL != BenchmarkContract.BASELINE_CHANNEL) {
        return this
    }
    val policy = LocalBenchmarkRuntimePolicy.current
    if (policy?.exposeReadyTags != true) {
        return this
    }
    return semantics {
        testTagsAsResourceId = true
    }
}
