package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.TranslationSourceType
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.detectLanguageLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

const val DefaultAutoDetectLanguage = "自动检测"

data class TranslationPageUiState(
    val inputText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = DefaultAutoDetectLanguage,
    val targetLanguage: String = "简体中文",
    val isTranslating: Boolean = false,
    val activeModelName: String = "",
    val availableModels: List<String> = emptyList(),
    val history: List<TranslationHistoryEntry> = emptyList(),
    val detectedSourceLanguageLabel: String = "待检测",
    val settings: AppSettings = AppSettings(),
    val errorMessage: String? = null,
)

class TranslationViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val aiTranslationService: AiTranslationService,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(TranslationPageUiState())
    val uiState: StateFlow<TranslationPageUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    init {
        viewModelScope.launch {
            settings.collect { appSettings ->
                _uiState.update { current ->
                    current.copy(
                        settings = appSettings,
                        activeModelName = appSettings.activeProvider()
                            ?.resolveFunctionModel(ProviderFunction.TRANSLATION)
                            .orEmpty()
                            .ifBlank { appSettings.selectedModel },
                        availableModels = appSettings.activeProvider()?.resolvedModelIds().orEmpty(),
                        history = appSettings.translationHistory.sortedByDescending(TranslationHistoryEntry::createdAt),
                        detectedSourceLanguageLabel = detectLanguageLabel(current.inputText),
                    )
                }
            }
        }
    }

    fun updateInputText(value: String) {
        _uiState.update {
            it.copy(
                inputText = value,
                detectedSourceLanguageLabel = detectLanguageLabel(value),
                errorMessage = null,
            )
        }
    }

    fun updateSourceLanguage(value: String) {
        _uiState.update { it.copy(sourceLanguage = value, errorMessage = null) }
    }

    fun updateTargetLanguage(value: String) {
        _uiState.update { it.copy(targetLanguage = value, errorMessage = null) }
    }

    fun swapLanguages() {
        _uiState.update { current ->
            val nextInput = current.translatedText.ifBlank { current.inputText }
            val nextTranslated = current.inputText
            if (current.sourceLanguage == AUTO_DETECT_LANGUAGE) {
                current.copy(
                    sourceLanguage = current.targetLanguage,
                    targetLanguage = "简体中文",
                    inputText = nextInput,
                    translatedText = nextTranslated,
                    detectedSourceLanguageLabel = detectLanguageLabel(nextInput),
                )
            } else {
                current.copy(
                    sourceLanguage = current.targetLanguage,
                    targetLanguage = current.sourceLanguage,
                    inputText = nextInput,
                    translatedText = nextTranslated,
                    detectedSourceLanguageLabel = detectLanguageLabel(nextInput),
                )
            }
        }
    }

    fun updateTranslationModel(modelId: String) {
        val state = _uiState.value
        val providers = state.settings.resolvedProviders()
        val selectedProviderId = state.settings.activeProvider()?.id
            ?: state.settings.selectedProviderId
        if (providers.isEmpty() || selectedProviderId.isBlank()) {
            return
        }
        val updatedProviders = providers.map { provider ->
            if (provider.id == selectedProviderId) {
                provider.copy(translationModel = modelId)
            } else {
                provider
            }
        }
        viewModelScope.launch {
            settingsEditor.saveProviderSettings(
                providers = updatedProviders,
                selectedProviderId = selectedProviderId,
            )
        }
    }

    fun useHistoryItem(entry: TranslationHistoryEntry) {
        _uiState.update {
            it.copy(
                inputText = entry.sourceText,
                translatedText = entry.translatedText,
                sourceLanguage = entry.sourceLanguage,
                targetLanguage = entry.targetLanguage,
                detectedSourceLanguageLabel = detectLanguageLabel(entry.sourceText),
                errorMessage = null,
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            settingsEditor.saveTranslationHistory(emptyList())
        }
    }

    fun clearInput() {
        _uiState.update {
            it.copy(
                inputText = "",
                translatedText = "",
                detectedSourceLanguageLabel = "待检测",
                errorMessage = null,
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun translate() {
        val state = _uiState.value
        val inputText = state.inputText.trim()
        if (inputText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先输入要翻译的内容") }
            return
        }

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTranslating = true,
                    translatedText = "",
                    errorMessage = null,
                )
            }
            runCatching {
                aiTranslationService.translateText(
                    text = inputText,
                    targetLanguage = _uiState.value.targetLanguage,
                    sourceLanguage = _uiState.value.sourceLanguage,
                )
            }.onSuccess { translated ->
                val updatedHistory = listOf(
                    TranslationHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        sourceText = inputText,
                        translatedText = translated,
                        sourceLanguage = _uiState.value.sourceLanguage,
                        targetLanguage = _uiState.value.targetLanguage,
                        modelName = _uiState.value.activeModelName,
                        createdAt = nowProvider(),
                        sourceType = TranslationSourceType.MANUAL,
                    ),
                ) + _uiState.value.history.filterNot {
                    it.sourceText == inputText &&
                        it.targetLanguage == _uiState.value.targetLanguage &&
                        it.sourceLanguage == _uiState.value.sourceLanguage
                }

                settingsEditor.saveTranslationHistory(updatedHistory)
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        translatedText = translated,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        errorMessage = throwable.message ?: "翻译失败",
                    )
                }
            }
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        translationJob = null
        _uiState.update { it.copy(isTranslating = false) }
    }

    companion object {
        const val AUTO_DETECT_LANGUAGE = DefaultAutoDetectLanguage

        val SupportedLanguages = listOf(
            AUTO_DETECT_LANGUAGE,
            "简体中文",
            "繁体中文",
            "英语",
            "日语",
            "韩语",
            "法语",
            "德语",
            "西班牙语",
        )

        fun factory(
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            aiTranslationService: AiTranslationService,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TranslationViewModel(
                        settingsRepository = settingsRepository,
                        settingsEditor = settingsEditor,
                        aiTranslationService = aiTranslationService,
                    ) as T
                }
            }
        }
    }
}
