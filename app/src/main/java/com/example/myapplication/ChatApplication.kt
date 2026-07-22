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
import com.example.myapplication.system.update.AppUpdateDownloadController

class ChatApplication : Application() {
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
        appGraph.launchStartupTasks()
    }
}
