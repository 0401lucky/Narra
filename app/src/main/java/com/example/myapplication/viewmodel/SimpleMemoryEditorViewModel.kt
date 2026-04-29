package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Tavo 简洁编辑器 UI 状态
data class SimpleMemoryEditorUiState(
    val coreDraft: String = "",
    val coreOriginalEntries: List<MemoryEntry> = emptyList(),
    val sceneDraft: String = "",
    val sceneOriginalEntries: List<MemoryEntry> = emptyList(),
    val showSceneSection: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val initialized: Boolean = false,
)

// Tavo 风格简洁长记忆编辑器：把当前助手作用域内的"核心记忆"和（可选）当前会话的"剧情记忆"
// 各自展开成多行文本；用户编辑后保存时按 content 做 diff，新增/删除条目；
// 保留同内容条目的 id 与 pinned 状态。
class SimpleMemoryEditorViewModel(
    private val assistantId: String,
    private val conversationId: String?,
    private val memoryRepository: MemoryRepository,
    private val assistantsProvider: () -> List<Assistant>,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SimpleMemoryEditorUiState(showSceneSection = !conversationId.isNullOrBlank()),
    )
    val uiState: StateFlow<SimpleMemoryEditorUiState> = _state.asStateFlow()

    private var coreDraftTouched = false
    private var sceneDraftTouched = false

    init {
        // 首次真实仓库快照用于填充草稿；之后若用户尚未手动编辑，也跟随外部记忆写入刷新。
        viewModelScope.launch {
            memoryRepository.observeEntries().collect { snapshot ->
                val assistant = assistantsProvider().firstOrNull { it.id == assistantId }
                val useGlobalScope = assistant?.useGlobalMemory == true
                val core = snapshot.filterAssistantCore(assistantId, useGlobalScope)
                val scene = snapshot.filterConversationScene(conversationId)
                _state.update { current ->
                    if (!current.initialized) {
                        current.copy(
                            coreDraft = composeDraft(core),
                            coreOriginalEntries = core,
                            sceneDraft = composeDraft(scene),
                            sceneOriginalEntries = scene,
                            initialized = true,
                        )
                    } else {
                        current.copy(
                            coreDraft = if (coreDraftTouched) current.coreDraft else composeDraft(core),
                            coreOriginalEntries = core,
                            sceneDraft = if (sceneDraftTouched) current.sceneDraft else composeDraft(scene),
                            sceneOriginalEntries = scene,
                        )
                    }
                }
            }
        }
    }

    fun updateCoreDraft(text: String) {
        coreDraftTouched = true
        _state.update { it.copy(coreDraft = text) }
    }

    fun updateSceneDraft(text: String) {
        sceneDraftTouched = true
        _state.update { it.copy(sceneDraft = text) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val assistant = assistantsProvider().firstOrNull { it.id == assistantId }
        val useGlobalScope = assistant?.useGlobalMemory == true
        val current = _state.value
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                val coreDelta = applyDraftDelta(
                    draft = current.coreDraft,
                    originalEntries = current.coreOriginalEntries,
                    scopeType = if (useGlobalScope) MemoryScopeType.GLOBAL else MemoryScopeType.ASSISTANT,
                    scopeId = if (useGlobalScope) "" else assistantId,
                    characterId = assistantId,
                    timestamp = now,
                )

                val sceneDelta = if (current.showSceneSection && !conversationId.isNullOrBlank()) {
                    applyDraftDelta(
                        draft = current.sceneDraft,
                        originalEntries = current.sceneOriginalEntries,
                        scopeType = MemoryScopeType.CONVERSATION,
                        scopeId = conversationId,
                        characterId = assistantId,
                        timestamp = now,
                    )
                } else {
                    DraftDelta(0, 0)
                }

                val totalInsert = coreDelta.inserted + sceneDelta.inserted
                val totalDelete = coreDelta.deleted + sceneDelta.deleted

                coreDraftTouched = false
                sceneDraftTouched = false
                _state.update { state ->
                    state.copy(
                        isBusy = false,
                        message = when {
                            totalInsert == 0 && totalDelete == 0 -> "记忆未变化"
                            else -> "已保存：新增 $totalInsert 条，删除 $totalDelete 条"
                        },
                    )
                }
            } catch (t: Throwable) {
                _state.update { state ->
                    state.copy(
                        isBusy = false,
                        message = "保存失败：${t.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    private data class DraftDelta(val inserted: Int, val deleted: Int)

    private suspend fun applyDraftDelta(
        draft: String,
        originalEntries: List<MemoryEntry>,
        scopeType: MemoryScopeType,
        scopeId: String,
        characterId: String,
        timestamp: Long,
    ): DraftDelta {
        val parsedItems = parseDraft(draft)
        val originalByContent = originalEntries.groupBy { it.content }
        val parsedSet = parsedItems.toSet()

        val toDelete = originalEntries.filter { it.content !in parsedSet }
        val toInsert = parsedItems.filter { content -> originalByContent[content] == null }

        toDelete.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
        }
        toInsert.forEach { content ->
            memoryRepository.upsertEntry(
                MemoryEntry(
                    scopeType = scopeType,
                    scopeId = scopeId,
                    characterId = characterId,
                    content = content,
                    importance = 60,
                    pinned = false,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
        return DraftDelta(inserted = toInsert.size, deleted = toDelete.size)
    }

    companion object {
        fun factory(
            assistantId: String,
            conversationId: String?,
            memoryRepository: MemoryRepository,
            assistantsProvider: () -> List<Assistant>,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                SimpleMemoryEditorViewModel(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    memoryRepository = memoryRepository,
                    assistantsProvider = assistantsProvider,
                )
            }
        }

        // 拼接草稿：每行一条，pinned 项前置；不带任何额外格式以匹配 Tavo 简洁视觉
        internal fun composeDraft(entries: List<MemoryEntry>): String {
            val sorted = entries.sortedWith(
                compareByDescending<MemoryEntry> { it.pinned }
                    .thenByDescending { it.updatedAt.takeIf { ts -> ts > 0L } ?: it.createdAt }
                    .thenByDescending { it.createdAt },
            )
            return sorted.joinToString(separator = "\n") { entry ->
                "- ${entry.content}"
            }
        }

        // 解析草稿：去掉行首列表符号；空行跳过；同内容自动去重保序
        internal fun parseDraft(text: String): List<String> {
            val seen = LinkedHashSet<String>()
            text.split('\n').forEach { rawLine ->
                val cleaned = rawLine.trimStart()
                    .removePrefix("-")
                    .removePrefix("*")
                    .removePrefix("•")
                    .removePrefix("·")
                    .trim()
                if (cleaned.isNotEmpty()) {
                    seen.add(cleaned)
                }
            }
            return seen.toList()
        }

        internal fun List<MemoryEntry>.filterAssistantCore(
            assistantId: String,
            useGlobalScope: Boolean,
        ): List<MemoryEntry> {
            if (assistantId.isBlank()) return emptyList()
            return filter { entry ->
                when (entry.scopeType) {
                    MemoryScopeType.CONVERSATION -> false
                    MemoryScopeType.ASSISTANT -> !useGlobalScope && (
                        entry.scopeId == assistantId ||
                        (entry.scopeId.isBlank() && entry.characterId == assistantId)
                    )
                    MemoryScopeType.GLOBAL -> useGlobalScope &&
                        (entry.characterId.isBlank() || entry.characterId == assistantId)
                }
            }
        }

        internal fun List<MemoryEntry>.filterConversationScene(
            conversationId: String?,
        ): List<MemoryEntry> {
            if (conversationId.isNullOrBlank()) return emptyList()
            return filter { entry ->
                entry.scopeType == MemoryScopeType.CONVERSATION && entry.scopeId == conversationId
            }
        }
    }
}
