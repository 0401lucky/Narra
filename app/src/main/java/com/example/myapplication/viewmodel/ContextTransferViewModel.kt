package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.TavernCharacterAdapter
import com.example.myapplication.data.repository.context.TavernCharacterImageAdapter
import com.example.myapplication.data.repository.context.TavernWorldBookAdapter
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContextTransferSection(
    val label: String,
    val exportFileName: String,
) {
    ALL("全部资料", "chat-context-export.json"),
    ASSISTANTS("角色卡", "assistants-export.json"),
    WORLD_BOOK("世界书", "worldbook-export.json"),
    MEMORY("记忆", "memory-export.json"),
}

data class ContextImportConflict(
    val typeLabel: String,
    val title: String,
    val id: String,
)

data class ContextImportPreview(
    val sourceLabel: String,
    val section: ContextTransferSection,
    val assistantCount: Int,
    val worldBookCount: Int,
    val memoryCount: Int,
    val summaryCount: Int,
    val conflicts: List<ContextImportConflict>,
)

data class ContextImportPayload(
    val fileName: String = "",
    val mimeType: String = "",
    val textContent: String? = null,
    val binaryContent: ByteArray? = null,
)

data class AssistantAvatarImport(
    val assistantId: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ContextTransferUiState(
    val customAssistantCount: Int = 0,
    val worldBookCount: Int = 0,
    val memoryCount: Int = 0,
    val summaryCount: Int = 0,
    val isBusy: Boolean = false,
    val importPreview: ContextImportPreview? = null,
    val message: String? = null,
)

class ContextTransferViewModel(
    private val repository: AiRepository,
    private val worldBookRepository: WorldBookRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val codec: ContextTransferCodec = ContextTransferCodec(),
    private val tavernCharacterAdapter: TavernCharacterAdapter = TavernCharacterAdapter(),
    private val tavernCharacterImageAdapter: TavernCharacterImageAdapter = TavernCharacterImageAdapter(),
    private val tavernWorldBookAdapter: TavernWorldBookAdapter = TavernWorldBookAdapter(),
    private val importedAssistantAvatarSaver: suspend (AssistantAvatarImport) -> String? = { null },
) : ViewModel() {
    val settings = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = com.example.myapplication.model.AppSettings(),
    )

    private val _uiState = MutableStateFlow(ContextTransferUiState())
    val uiState: StateFlow<ContextTransferUiState> = _uiState.asStateFlow()
    private var pendingImportBundle: ContextDataBundle? = null
    private var pendingAssistantAvatarImport: AssistantAvatarImport? = null

    init {
        viewModelScope.launch {
            refreshCounts()
        }
    }

    fun exportBundleJson(
        section: ContextTransferSection,
        onSuccess: (String, String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            val bundle = filterBundleBySection(
                section = section,
                bundle = buildCurrentBundle(),
            )
            onSuccess(codec.encode(bundle), section.exportFileName)
            _uiState.update {
                it.copy(
                    isBusy = false,
                )
            }
        }
    }

    fun previewImportJson(
        rawJson: String,
        section: ContextTransferSection,
    ) {
        previewImportPayload(
            payload = ContextImportPayload(
                fileName = "import.json",
                mimeType = "application/json",
                textContent = rawJson,
            ),
            section = section,
        )
    }

    fun previewImportPayload(
        payload: ContextImportPayload,
        section: ContextTransferSection,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            runCatching {
                val decodedImport = decodeImportBundle(payload, section)
                val filteredBundle = filterBundleBySection(
                    section = section,
                    bundle = decodedImport.bundle,
                    sourceType = decodedImport.sourceType,
                )
                if (isBundleEmpty(filteredBundle)) {
                    error("导入文件中没有可用于 ${section.label} 的内容")
                }
                val preview = buildImportPreview(
                    sourceLabel = decodedImport.sourceType.sourceLabel,
                    section = section,
                    bundle = filteredBundle,
                )
                Triple(
                    filteredBundle,
                    preview,
                    resolveAssistantAvatarImport(
                        payload = payload,
                        bundle = filteredBundle,
                    ),
                )
            }.onSuccess { (bundle, preview, assistantAvatarImport) ->
                pendingImportBundle = bundle
                pendingAssistantAvatarImport = assistantAvatarImport
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = preview,
                    )
                }
            }.onFailure { throwable ->
                pendingImportBundle = null
                pendingAssistantAvatarImport = null
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = null,
                        message = throwable.message ?: "导入失败",
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val bundle = pendingImportBundle ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            runCatching {
                mergeImportedData(applyImportedAssistantAvatar(bundle))
            }.onSuccess {
                pendingImportBundle = null
                pendingAssistantAvatarImport = null
                refreshCounts()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = null,
                        message = "上下文数据已合并导入",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = throwable.message ?: "导入失败",
                    )
                }
            }
        }
    }

    fun dismissImportPreview() {
        pendingImportBundle = null
        pendingAssistantAvatarImport = null
        _uiState.update { it.copy(importPreview = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun buildCurrentBundle(): ContextDataBundle {
        val currentSettings = settings.value
        return ContextDataBundle(
            assistants = currentSettings.assistants,
            worldBookEntries = worldBookRepository.listEntries(),
            memoryEntries = memoryRepository.listEntries(),
            conversationSummaries = conversationSummaryRepository.listSummaries(),
        )
    }

    private suspend fun mergeImportedData(bundle: ContextDataBundle) {
        val currentSettings = settings.value
        val mergedAssistants = mergeAssistants(
            currentAssistants = currentSettings.assistants,
            importedAssistants = bundle.assistants,
        )
        repository.saveAssistants(
            assistants = mergedAssistants,
            selectedAssistantId = resolveSelectedAssistantId(
                currentSelectedAssistantId = currentSettings.selectedAssistantId,
                assistants = mergedAssistants,
            ),
        )

        bundle.worldBookEntries.forEach { entry ->
            worldBookRepository.upsertEntry(entry)
        }
        bundle.memoryEntries.forEach { entry ->
            memoryRepository.upsertEntry(entry)
        }
        bundle.conversationSummaries.forEach { summary ->
            conversationSummaryRepository.upsertSummary(summary)
        }
    }

    private suspend fun refreshCounts() {
        val customAssistantCount = settings.value.assistants.size
        val worldBookCount = worldBookRepository.listEntries().size
        val memoryCount = memoryRepository.listEntries().size
        val summaryCount = conversationSummaryRepository.listSummaries().size
        _uiState.update {
            it.copy(
                customAssistantCount = customAssistantCount,
                worldBookCount = worldBookCount,
                memoryCount = memoryCount,
                summaryCount = summaryCount,
            )
        }
    }

    private suspend fun applyImportedAssistantAvatar(bundle: ContextDataBundle): ContextDataBundle {
        val assistantAvatarImport = pendingAssistantAvatarImport ?: return bundle
        if (bundle.assistants.size != 1) {
            return bundle
        }
        val avatarUri = importedAssistantAvatarSaver(assistantAvatarImport)
            ?.trim()
            .orEmpty()
        if (avatarUri.isBlank()) {
            return bundle
        }
        return bundle.copy(
            assistants = listOf(
                bundle.assistants.single().copy(avatarUri = avatarUri),
            ),
        )
    }

    private fun mergeAssistants(
        currentAssistants: List<Assistant>,
        importedAssistants: List<Assistant>,
    ): List<Assistant> {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        val importedCustomAssistants = importedAssistants.filter { it.id !in builtinIds }
        val merged = linkedMapOf<String, Assistant>()
        currentAssistants.forEach { assistant ->
            merged[assistant.id] = assistant
        }
        importedCustomAssistants.forEach { assistant ->
            merged[assistant.id] = assistant
        }
        return merged.values.toList()
    }

    private fun resolveSelectedAssistantId(
        currentSelectedAssistantId: String,
        assistants: List<Assistant>,
    ): String {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        return when {
            currentSelectedAssistantId in builtinIds -> currentSelectedAssistantId
            assistants.any { it.id == currentSelectedAssistantId } -> currentSelectedAssistantId
            else -> DEFAULT_ASSISTANT_ID
        }
    }

    private suspend fun buildImportPreview(
        sourceLabel: String,
        section: ContextTransferSection,
        bundle: ContextDataBundle,
    ): ContextImportPreview {
        val currentSettings = settings.value
        val currentAssistantIds = currentSettings.assistants.map { it.id }.toSet()
        val currentWorldBookIds = worldBookRepository.listEntries().map { it.id }.toSet()
        val currentMemoryIds = memoryRepository.listEntries().map { it.id }.toSet()
        val currentSummaryIds = conversationSummaryRepository.listSummaries().map { it.conversationId }.toSet()

        val conflicts = buildList {
            bundle.assistants
                .filter { it.id in currentAssistantIds }
                .forEach { assistant ->
                    add(ContextImportConflict("角色卡", assistant.name.ifBlank { assistant.id }, assistant.id))
                }
            bundle.worldBookEntries
                .filter { it.id in currentWorldBookIds }
                .forEach { entry ->
                    add(ContextImportConflict("世界书", entry.title.ifBlank { entry.id }, entry.id))
                }
            bundle.memoryEntries
                .filter { it.id in currentMemoryIds }
                .forEach { entry ->
                    add(ContextImportConflict("记忆", entry.content.take(30).ifBlank { entry.id }, entry.id))
                }
            bundle.conversationSummaries
                .filter { it.conversationId in currentSummaryIds }
                .forEach { summary ->
                    add(ContextImportConflict("摘要", summary.conversationId, summary.conversationId))
                }
        }

        return ContextImportPreview(
            sourceLabel = sourceLabel,
            section = section,
            assistantCount = bundle.assistants.size,
            worldBookCount = bundle.worldBookEntries.size,
            memoryCount = bundle.memoryEntries.size,
            summaryCount = bundle.conversationSummaries.size,
            conflicts = conflicts,
        )
    }

    private fun resolveAssistantAvatarImport(
        payload: ContextImportPayload,
        bundle: ContextDataBundle,
    ): AssistantAvatarImport? {
        val bytes = payload.binaryContent ?: return null
        val assistant = bundle.assistants.singleOrNull() ?: return null
        if (!looksLikePngImage(bytes, payload.fileName, payload.mimeType)) {
            return null
        }
        return AssistantAvatarImport(
            assistantId = assistant.id,
            fileName = payload.fileName,
            mimeType = payload.mimeType,
            bytes = bytes,
        )
    }

    private fun decodeImportBundle(
        payload: ContextImportPayload,
        section: ContextTransferSection,
    ): DecodedImportBundle {
        payload.textContent?.let { rawJson ->
            val decodedBundle = runCatching {
                codec.decode(rawJson)
            }.getOrNull()?.let { bundle ->
                DecodedImportBundle(
                    sourceType = ImportSourceType.CONTEXT_BUNDLE,
                    bundle = bundle,
                )
            }

            if (decodedBundle != null && !isBundleEmpty(decodedBundle.bundle)) {
                return decodedBundle
            }

            tavernWorldBookAdapter.decodeAsBundle(
                rawJson = rawJson,
                fileName = payload.fileName,
            )?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_WORLD_BOOK,
                    bundle = bundle,
                )
            }

            tavernCharacterAdapter.decodeAsBundle(rawJson)?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_JSON,
                    bundle = attachAssistantScopedWorldBookLinks(bundle),
                )
            }
        }

        payload.binaryContent?.let { bytes ->
            tavernCharacterImageAdapter.decodeAsBundle(
                bytes = bytes,
                fileName = payload.fileName,
                mimeType = payload.mimeType,
            )?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_PNG,
                    bundle = attachAssistantScopedWorldBookLinks(bundle),
                )
            }
            throw IllegalArgumentException(
                when {
                    looksLikePngImage(bytes, payload.fileName, payload.mimeType) ->
                        "未在 PNG 图片中识别到 Tavern 角色卡数据"
                    looksLikeImageFile(payload.fileName, payload.mimeType) ->
                        "暂仅支持 PNG Tavern 图片角色卡"
                    else -> "导入文件格式无效"
                },
            )
        }

        throw IllegalArgumentException("导入文件格式无效")
    }

    private fun looksLikePngImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.equals("image/png", ignoreCase = true)) {
            return true
        }
        if (fileName.endsWith(".png", ignoreCase = true)) {
            return true
        }
        return bytes.size >= PNG_SIGNATURE.size &&
            bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
    }

    private fun looksLikeImageFile(
        fileName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) {
            return true
        }
        return IMAGE_FILE_REGEX.containsMatchIn(fileName)
    }

    private fun filterBundleBySection(
        section: ContextTransferSection,
        bundle: ContextDataBundle,
        sourceType: ImportSourceType = ImportSourceType.CONTEXT_BUNDLE,
    ): ContextDataBundle {
        return when (section) {
            ContextTransferSection.ALL -> bundle
            ContextTransferSection.ASSISTANTS -> bundle.copy(
                worldBookEntries = if (sourceType.includesAssistantScopedWorldBooks) {
                    assistantScopedWorldBookEntries(bundle)
                } else {
                    emptyList()
                },
                memoryEntries = emptyList(),
                conversationSummaries = emptyList(),
            )

            ContextTransferSection.WORLD_BOOK -> bundle.copy(
                assistants = emptyList(),
                memoryEntries = emptyList(),
                conversationSummaries = emptyList(),
            )

            ContextTransferSection.MEMORY -> bundle.copy(
                assistants = emptyList(),
                worldBookEntries = emptyList(),
            )
        }
    }

    private fun isBundleEmpty(bundle: ContextDataBundle): Boolean {
        return bundle.assistants.isEmpty() &&
            bundle.worldBookEntries.isEmpty() &&
            bundle.memoryEntries.isEmpty() &&
            bundle.conversationSummaries.isEmpty()
    }

    private fun attachAssistantScopedWorldBookLinks(bundle: ContextDataBundle): ContextDataBundle {
        if (bundle.assistants.isEmpty() || bundle.worldBookEntries.isEmpty()) {
            return bundle
        }

        val assistantScopedEntryIds = bundle.worldBookEntries
            .filter { entry ->
                entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT
            }
            .groupBy { entry -> entry.scopeId }
            .mapValues { (_, entries) -> entries.map { it.id } }

        return bundle.copy(
            assistants = bundle.assistants.map { assistant ->
                val scopedEntryIds = assistantScopedEntryIds[assistant.id].orEmpty()
                if (scopedEntryIds.isEmpty()) {
                    assistant
                } else {
                    assistant.copy(
                        linkedWorldBookIds = (assistant.linkedWorldBookIds + scopedEntryIds).distinct(),
                    )
                }
            },
        )
    }

    private fun assistantScopedWorldBookEntries(bundle: ContextDataBundle): List<com.example.myapplication.model.WorldBookEntry> {
        val assistantIds = bundle.assistants.map { it.id }.toSet()
        if (assistantIds.isEmpty()) {
            return emptyList()
        }
        return bundle.worldBookEntries.filter { entry ->
            entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT &&
                entry.scopeId in assistantIds
        }
    }

    companion object {
        private val IMAGE_FILE_REGEX = Regex("""\.(png|apng|jpg|jpeg|webp|gif|bmp)$""", RegexOption.IGNORE_CASE)
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )

        fun factory(
            repository: AiRepository,
            worldBookRepository: WorldBookRepository,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            importedAssistantAvatarSaver: suspend (AssistantAvatarImport) -> String? = { null },
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ContextTransferViewModel(
                        repository = repository,
                        worldBookRepository = worldBookRepository,
                        memoryRepository = memoryRepository,
                        conversationSummaryRepository = conversationSummaryRepository,
                        importedAssistantAvatarSaver = importedAssistantAvatarSaver,
                    ) as T
                }
            }
        }
    }
}

private data class DecodedImportBundle(
    val sourceType: ImportSourceType,
    val bundle: ContextDataBundle,
)

private enum class ImportSourceType(
    val sourceLabel: String,
    val includesAssistantScopedWorldBooks: Boolean,
) {
    CONTEXT_BUNDLE(
        sourceLabel = "上下文数据包",
        includesAssistantScopedWorldBooks = false,
    ),
    TAVERN_JSON(
        sourceLabel = "Tavern 角色卡",
        includesAssistantScopedWorldBooks = true,
    ),
    TAVERN_PNG(
        sourceLabel = "Tavern 图片角色卡",
        includesAssistantScopedWorldBooks = true,
    ),
    TAVERN_WORLD_BOOK(
        sourceLabel = "独立世界书",
        includesAssistantScopedWorldBooks = false,
    ),
}
