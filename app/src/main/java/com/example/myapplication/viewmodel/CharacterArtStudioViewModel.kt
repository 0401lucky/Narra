package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CHARACTER_ART_STYLES
import com.example.myapplication.model.CharacterArtPromptDraft
import com.example.myapplication.model.CharacterArtStyle
import com.example.myapplication.model.DEFAULT_ASSISTANT_ICON
import com.example.myapplication.model.ImagePromptPolishRequest
import com.example.myapplication.model.ImagePromptPurpose
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.characterArtStyleById
import com.example.myapplication.model.fallbackPolishResult
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class CharacterArtGeneratedImage(
    val uri: String,
    val mimeType: String,
    val fileName: String,
    val prompt: String,
    val revisedPrompt: String = "",
)

data class CharacterArtStudioUiState(
    val assistants: List<Assistant> = emptyList(),
    val selectedAssistantId: String = "",
    val styles: List<CharacterArtStyle> = CHARACTER_ART_STYLES,
    val selectedStyleId: String = CHARACTER_ART_STYLES.first().id,
    val promptDraft: CharacterArtPromptDraft = CharacterArtPromptDraft(),
    val editablePrompt: String = "",
    val editableNegativePrompt: String = "",
    val revisionInstruction: String = "",
    val generatedImage: CharacterArtGeneratedImage? = null,
    val isExtractingPrompt: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val isApplyingAvatar: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val selectedStyle: CharacterArtStyle
        get() = characterArtStyleById(selectedStyleId)

    val selectedAssistant: Assistant?
        get() = assistants.firstOrNull { it.id == selectedAssistantId }
}

class CharacterArtStudioViewModel(
    private val initialAssistantId: String,
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiGateway: AiGateway,
    private val imageSaver: suspend (String) -> SavedImageFile,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CharacterArtStudioUiState(selectedAssistantId = initialAssistantId),
    )
    val uiState: StateFlow<CharacterArtStudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val assistants = settings.resolvedAssistants()
                _uiState.update { current ->
                    val selectedId = when {
                        current.selectedAssistantId.isNotBlank() &&
                            assistants.any { it.id == current.selectedAssistantId } -> current.selectedAssistantId

                        initialAssistantId.isNotBlank() &&
                            assistants.any { it.id == initialAssistantId } -> initialAssistantId

                        else -> assistants.firstOrNull()?.id.orEmpty()
                    }
                    current.copy(
                        assistants = assistants,
                        selectedAssistantId = selectedId,
                    )
                }
            }
        }
    }

    fun selectAssistant(assistantId: String) {
        if (_uiState.value.isExtractingPrompt || _uiState.value.isGeneratingImage) return
        _uiState.update {
            it.copy(
                selectedAssistantId = assistantId,
                promptDraft = CharacterArtPromptDraft(),
                editablePrompt = "",
                editableNegativePrompt = "",
                revisionInstruction = "",
                generatedImage = null,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun selectStyle(styleId: String) {
        if (_uiState.value.isGeneratingImage) return
        _uiState.update {
            it.copy(
                selectedStyleId = characterArtStyleById(styleId).id,
                generatedImage = null,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun updateEditablePrompt(value: String) {
        _uiState.update {
            it.copy(
                editablePrompt = value,
                generatedImage = null,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun updateEditableNegativePrompt(value: String) {
        _uiState.update {
            it.copy(
                editableNegativePrompt = value,
                generatedImage = null,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun updateRevisionInstruction(value: String) {
        _uiState.update { it.copy(revisionInstruction = value, message = null, errorMessage = null) }
    }

    fun extractPrompt() {
        val state = _uiState.value
        if (state.isExtractingPrompt || state.isGeneratingImage) return
        val assistant = state.selectedAssistant ?: return fail("请选择一个角色")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExtractingPrompt = true,
                    generatedImage = null,
                    message = null,
                    errorMessage = null,
                )
            }
            runCatching {
                val settings = settingsRepository.settingsFlow.first()
                val provider = settings.activeProvider()
                    ?: throw IllegalStateException("请先在设置里配置可用聊天模型")
                if (!provider.hasRequiredConfig()) {
                    throw IllegalStateException("请先在设置里配置 Base URL、API Key 和聊天模型")
                }
                withTimeout(PromptTimeoutMs) {
                    aiPromptExtrasService.generateCharacterArtPrompt(
                        assistant = assistant,
                        style = state.selectedStyle,
                        revisionInstruction = state.revisionInstruction,
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        modelId = provider.selectedModel,
                        apiProtocol = provider.resolvedApiProtocol(),
                        provider = provider,
                    )
                }
            }.onSuccess { draft ->
                _uiState.update {
                    it.copy(
                        isExtractingPrompt = false,
                        promptDraft = draft,
                        editablePrompt = draft.visualPrompt,
                        editableNegativePrompt = draft.negativePrompt,
                        message = "角色视觉提示词已生成",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isExtractingPrompt = false,
                        errorMessage = buildErrorMessage(throwable, "提示词生成失败"),
                    )
                }
            }
        }
    }

    fun generateImage() {
        val state = _uiState.value
        if (state.isExtractingPrompt || state.isGeneratingImage) return
        if (state.selectedAssistant == null) {
            fail("请选择一个角色")
            return
        }
        val prompt = currentPrompt(state)
        if (prompt.isBlank()) {
            fail("请先生成或填写角色图提示词")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingImage = true,
                    generatedImage = null,
                    message = null,
                    errorMessage = null,
                )
            }
            runCatching {
                val settings = settingsRepository.settingsFlow.first()
                val provider = settings.resolveFunctionProvider(ProviderFunction.GIFT_IMAGE)
                    ?: throw IllegalStateException("请先在模型设置里配置默认生图模型")
                val imageModelId = settings.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
                    .ifBlank { throw IllegalStateException("请先在模型设置里配置默认生图模型") }
                val finalPrompt = buildPolishedCharacterPrompt(
                    state = state,
                    basePrompt = prompt,
                    imageProvider = provider,
                )
                val result = withTimeout(ImageTimeoutMs) {
                    aiGateway.generateImageWithProvider(
                        prompt = finalPrompt,
                        provider = provider,
                        modelId = imageModelId,
                    ).firstOrNull() ?: error("生图接口未返回图片")
                }
                persistImage(result, finalPrompt)
            }.onSuccess { image ->
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        generatedImage = image,
                        message = "角色图已生成",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        errorMessage = buildErrorMessage(throwable, "角色图生成失败"),
                    )
                }
            }
        }
    }

    fun applyGeneratedImageAsAvatar() {
        val state = _uiState.value
        val image = state.generatedImage ?: return fail("请先生成一张角色图")
        val assistant = state.selectedAssistant ?: return fail("请选择一个角色")
        if (state.isApplyingAvatar) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isApplyingAvatar = true, message = null, errorMessage = null)
            }
            runCatching {
                val settings = settingsRepository.settingsFlow.first()
                val currentAssistants = settings.assistants.toMutableList()
                val storedIndex = currentAssistants.indexOfFirst { it.id == assistant.id }
                val updatedAssistant = assistant.copy(
                    iconName = DEFAULT_ASSISTANT_ICON,
                    avatarUri = image.uri,
                    isBuiltin = false,
                )
                if (storedIndex >= 0) {
                    currentAssistants[storedIndex] = updatedAssistant
                } else {
                    currentAssistants += updatedAssistant
                }
                settingsEditor.saveAssistants(
                    assistants = currentAssistants,
                    selectedAssistantId = settings.selectedAssistantId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isApplyingAvatar = false,
                        message = "已设为角色头像",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isApplyingAvatar = false,
                        errorMessage = buildErrorMessage(throwable, "头像应用失败"),
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }

    private fun currentPrompt(state: CharacterArtStudioUiState): String {
        val draft = state.promptDraft.copy(
            visualPrompt = state.editablePrompt,
            negativePrompt = state.editableNegativePrompt,
        )
        return draft.finalPrompt(state.selectedStyle)
    }

    private suspend fun buildPolishedCharacterPrompt(
        state: CharacterArtStudioUiState,
        basePrompt: String,
        imageProvider: ProviderSettings,
    ): String {
        val assistant = state.selectedAssistant
        val promptProvider = settingsRepository.settingsFlow.first().activeProvider()
            ?: imageProvider
        val promptModelId = promptProvider.resolveFunctionModel(ProviderFunction.CHAT)
            .ifBlank { promptProvider.selectedModel }
            .trim()
        val polishRequest = ImagePromptPolishRequest(
            purpose = ImagePromptPurpose.CHARACTER_ART,
            basePrompt = basePrompt,
            subject = assistant?.name.orEmpty(),
            styleHint = state.selectedStyle.promptHint,
            roleContext = assistant?.let(::buildAssistantVisualContext).orEmpty(),
            negativePrompt = state.editableNegativePrompt,
        )
        if (promptModelId.isBlank()) {
            return polishRequest.fallbackPolishResult().finalPrompt()
        }
        return runCatching {
            withTimeout(PromptTimeoutMs) {
                aiPromptExtrasService.polishImagePrompt(
                    request = polishRequest,
                    baseUrl = promptProvider.baseUrl,
                    apiKey = promptProvider.apiKey,
                    modelId = promptModelId,
                    apiProtocol = promptProvider.resolvedApiProtocol(),
                    provider = promptProvider,
                ).finalPrompt()
            }
        }.getOrElse {
            polishRequest.fallbackPolishResult().finalPrompt()
        }
    }

    private fun buildAssistantVisualContext(assistant: Assistant): String {
        return listOf(
            assistant.description,
            assistant.systemPrompt,
            assistant.scenario,
            assistant.creatorNotes,
            assistant.tags.joinToString(separator = "、"),
        )
            .map { it.replace("\r\n", "\n").trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .take(1200)
    }

    private suspend fun persistImage(
        imageResult: ImageGenerationResult,
        prompt: String,
    ): CharacterArtGeneratedImage {
        val savedImage = if (imageResult.b64Data.isNotBlank()) {
            imageSaver(imageResult.b64Data)
        } else {
            val remoteUrl = imageResult.url.trim()
            if (remoteUrl.isBlank()) error("生图结果为空")
            SavedImageFile(
                path = remoteUrl,
                mimeType = "image/*",
                fileName = "character-art-remote",
            )
        }
        return CharacterArtGeneratedImage(
            uri = savedImage.path,
            mimeType = savedImage.mimeType,
            fileName = savedImage.fileName,
            prompt = prompt,
            revisedPrompt = imageResult.revisedPrompt,
        )
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(errorMessage = message, message = null) }
    }

    private fun buildErrorMessage(throwable: Throwable, fallback: String): String {
        val message = when (throwable) {
            is TimeoutCancellationException -> "$fallback：请求超时"
            else -> throwable.message?.takeIf { it.isNotBlank() } ?: fallback
        }
        return SensitiveTextRedactor.redact(message, maxLength = 180)
    }

    companion object {
        private const val PromptTimeoutMs = 45_000L
        private const val ImageTimeoutMs = 240_000L

        fun factory(
            initialAssistantId: String,
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            aiPromptExtrasService: AiPromptExtrasService,
            aiGateway: AiGateway,
            imageSaver: suspend (String) -> SavedImageFile,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                CharacterArtStudioViewModel(
                    initialAssistantId = initialAssistantId,
                    settingsRepository = settingsRepository,
                    settingsEditor = settingsEditor,
                    aiPromptExtrasService = aiPromptExtrasService,
                    aiGateway = aiGateway,
                    imageSaver = imageSaver,
                )
            }
        }
    }
}
