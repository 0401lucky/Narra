package com.example.myapplication

import android.app.Application
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.di.AppGraph
import com.example.myapplication.system.diagnostics.DiagnosticsAppVersion
import com.example.myapplication.system.diagnostics.ExitDiagnosticsOperations
import com.example.myapplication.system.diagnostics.LocalExitDiagnostics
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.system.performance.BenchmarkProcessNameResolver
import com.example.myapplication.system.performance.BenchmarkProcessPolicy
import com.example.myapplication.system.performance.BenchmarkRuntimePolicyResolver
import com.example.myapplication.system.update.AppUpdateDownloadController

class ChatApplication : Application() {
    @Volatile
    private var requestedBenchmarkSessionId: String? = null

    private val benchmarkRuntimePolicyResolver by lazy {
        BenchmarkRuntimePolicyResolver(
            context = this,
            appChannel = BuildConfig.APP_CHANNEL,
        )
    }

    val exitDiagnostics: ExitDiagnosticsOperations by lazy {
        LocalExitDiagnostics.createOrNoOp(
            context = this,
            appVersion = DiagnosticsAppVersion(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
            ),
            shouldSkipCollection = {
                BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL &&
                    benchmarkRuntimePolicyResolver
                        .resolve(requestedBenchmarkSessionId)
                        .skipExitDiagnostics
            },
        )
    }
    val appGraph: AppGraph by lazy { AppGraph(this) }
    val conversationRepository: ConversationRepository
        get() = appGraph.conversationRepository
    val aiSettingsRepository: AiSettingsRepository
        get() = appGraph.aiSettingsRepository
    val aiSettingsEditor: AiSettingsEditor
        get() = appGraph.aiSettingsEditor
    val aiTranslationService: AiTranslationService
        get() = appGraph.aiTranslationService
    val worldBookRepository: WorldBookRepository
        get() = appGraph.worldBookRepository
    val memoryRepository: MemoryRepository
        get() = appGraph.memoryRepository
    val conversationSummaryRepository: ConversationSummaryRepository
        get() = appGraph.conversationSummaryRepository
    val presetRepository: PresetRepository
        get() = appGraph.presetRepository
    val promptContextAssembler: PromptContextAssembler
        get() = appGraph.promptContextAssembler
    val roleplayRepository: RoleplayRepository
        get() = appGraph.roleplayRepository
    val appUpdateRepository: AppUpdateRepository
        get() = appGraph.appUpdateRepository
    val appUpdateDownloadController: AppUpdateDownloadController
        get() = appGraph.appUpdateDownloadController

    override fun onCreate() {
        super.onCreate()
        if (
            BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL &&
            BenchmarkProcessPolicy.isBenchmarkFixtureProcess(
                applicationId = packageName,
                processName = BenchmarkProcessNameResolver.current(this),
            )
        ) {
            return
        }
        appGraph.launchStartupTasks()
    }

    fun configureDiagnosticsBenchmarkSession(sessionId: String?) {
        requestedBenchmarkSessionId = sessionId
    }
}
