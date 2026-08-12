package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.system.performance.BenchmarkRuntimePolicyResolver
import com.example.myapplication.system.logging.AppLogger
import com.example.myapplication.ui.AppRoot
import com.example.myapplication.ui.performance.ProvideBenchmarkRuntimePolicy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ChatApplication
        val benchmarkRuntimePolicy = if (
            BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL
        ) {
            val requestedBenchmarkSessionId = intent.getStringExtra(
                BenchmarkContract.SESSION_ID_INTENT_EXTRA,
            )
            BenchmarkRuntimePolicyResolver(
                context = this,
                appChannel = BuildConfig.APP_CHANNEL,
            ).resolve(requestedBenchmarkSessionId).also { policy ->
                if (!requestedBenchmarkSessionId.isNullOrBlank()) {
                    if (policy.isBenchmarkSessionActive) {
                        AppLogger.i(
                            TAG,
                            "benchmark session 已启用，scenario=${policy.scenarioId?.wireId.orEmpty()}",
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "benchmark session 被拒绝，reason=${policy.rejection?.name.orEmpty()}",
                        )
                    }
                }
                app.configureDiagnosticsBenchmarkSession(policy.sessionId)
            }
        } else {
            null
        }
        setContent {
            if (benchmarkRuntimePolicy?.exposeReadyTags == true) {
                ProvideBenchmarkRuntimePolicy(benchmarkRuntimePolicy) {
                    Box(
                        modifier = Modifier.semantics {
                            testTagsAsResourceId = true
                        },
                    ) {
                        NarraActivityContent(
                            app = app,
                            collectExitDiagnostics = false,
                            onExit = { finishAndRemoveTask() },
                        )
                    }
                }
            } else {
                NarraActivityContent(
                    app = app,
                    collectExitDiagnostics = true,
                    onExit = { finishAndRemoveTask() },
                )
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@Composable
private fun NarraActivityContent(
    app: ChatApplication,
    collectExitDiagnostics: Boolean,
    onExit: () -> Unit,
) {
    if (collectExitDiagnostics) {
        LaunchedEffect(app) {
            withFrameNanos { }
            app.exitDiagnostics.scheduleCollection()
        }
    }
    AppRoot(
        appGraph = app.appGraph,
        onExit = onExit,
    )
}
