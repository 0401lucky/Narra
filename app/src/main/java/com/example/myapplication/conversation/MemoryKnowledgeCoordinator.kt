package com.example.myapplication.conversation

import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MEMORY_ENGINE_CURRENT
import com.example.myapplication.model.MemoryKnowledgeProgress
import com.example.myapplication.model.MemoryRuntimeContext

/** 启动或提交后免费恢复已保存的候选；读取记忆不会触发模型维护请求。 */
class MemoryKnowledgeCoordinator(
    private val archive: MemoryArchiveRepository,
    private val currentSettings: suspend () -> AppSettings,
    private val hasCurrentConsent: suspend () -> Boolean,
) {
    suspend fun resume(context: MemoryRuntimeContext): MemoryKnowledgeProgress {
        if (context.conversationId.isBlank() || !hasCurrentConsent() || archive.getEngineVersion() < MEMORY_ENGINE_CURRENT ||
            currentSettings().resolvedAssistants()
                .none { it.id == context.characterId && it.memoryEnabled }
        ) {
            return MemoryKnowledgeProgress(failureCode = "MEMORY_DISABLED")
        }
        return archive.consumeCommittedKnowledge(context)
    }
}
