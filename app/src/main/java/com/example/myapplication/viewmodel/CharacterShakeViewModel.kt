package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CharacterShakeFilters
import com.example.myapplication.model.DEFAULT_ASSISTANT_ICON
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CharacterShakeUiState(
    val filters: CharacterShakeFilters = CharacterShakeFilters(),
    val isGenerating: Boolean = false,
    val generatedAssistant: Assistant? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

class CharacterShakeViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val aiPromptExtrasService: AiPromptExtrasService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterShakeUiState())
    val uiState: StateFlow<CharacterShakeUiState> = _uiState.asStateFlow()

    fun updateFilters(filters: CharacterShakeFilters) {
        _uiState.update {
            it.copy(
                filters = filters,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun resetFilters() {
        updateFilters(CharacterShakeFilters())
    }

    fun generateAssistant(filters: CharacterShakeFilters = _uiState.value.filters) {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    filters = filters,
                    isGenerating = true,
                    generatedAssistant = null,
                    message = null,
                    errorMessage = null,
                )
            }
            runCatching {
                val settings = settingsRepository.settingsFlow.first()
                val provider = settings.activeProvider()
                    ?: throw IllegalStateException("请先在设置里配置可用模型")
                if (!provider.hasRequiredConfig()) {
                    throw IllegalStateException("请先在设置里配置 Base URL、API Key 和模型")
                }
                val generated = aiPromptExtrasService.generateShakeAssistantCard(
                    filters = filters,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelId = provider.selectedModel,
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                )
                val assistant = generated.withShakeDefaults(settings)
                settingsEditor.saveAssistants(
                    assistants = settings.assistants + assistant,
                    selectedAssistantId = assistant.id,
                )
                assistant
            }.onSuccess { assistant ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedAssistant = assistant,
                        message = "已创建角色：${assistant.name.ifBlank { "未命名角色" }}",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                val safeMessage = SensitiveTextRedactor.redact(
                    throwable.message ?: "角色生成失败",
                    maxLength = 180,
                )
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = safeMessage,
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                message = null,
                errorMessage = null,
            )
        }
    }

    fun dismissGeneratedAssistant() {
        _uiState.update { it.copy(generatedAssistant = null) }
    }

    private fun Assistant.withShakeDefaults(settings: AppSettings): Assistant {
        val normalizedTags = (tags + "摇一摇")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
        return copy(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "摇出的角色" },
            iconName = iconName.trim().ifBlank { DEFAULT_ASSISTANT_ICON },
            avatarUri = "",
            description = description.trim(),
            systemPrompt = systemPrompt.trim().ifBlank {
                "你正在参与角色扮演。请保持${name.ifBlank { "当前角色" }}的人格、边界与说话方式，自然回应用户。"
            },
            scenario = scenario.trim(),
            greeting = greeting.trim(),
            exampleDialogues = exampleDialogues.map { it.trim() }.filter { it.isNotBlank() }.take(4),
            creatorNotes = creatorNotes.trim(),
            tags = normalizedTags,
            memoryEnabled = true,
            defaultPresetId = settings.defaultPresetId,
            isBuiltin = false,
        )
    }

    companion object {
        fun factory(
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            aiPromptExtrasService: AiPromptExtrasService,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                CharacterShakeViewModel(
                    settingsRepository = settingsRepository,
                    settingsEditor = settingsEditor,
                    aiPromptExtrasService = aiPromptExtrasService,
                )
            }
        }
    }
}
