package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.mergeModelInfosPreservingOverrides

data class SettingsModelLoadResult(
    val message: String,
    val pendingFetchedModels: List<ModelInfo> = emptyList(),
    val pendingFetchProviderId: String = "",
)

class SettingsModelLoadCoordinator(
    private val modelCatalogRepository: AiModelCatalogRepository,
    private val settingsEditor: AiSettingsEditor,
) {
    suspend fun loadModelsForProvider(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        persistResult: Boolean = false,
        persistedProviders: List<ProviderSettings> = emptyList(),
        persistedSelectedProviderId: String = "",
    ): SettingsModelLoadResult {
        val providerProtocol = persistedProviders.firstOrNull { it.id == providerId }
            ?.resolvedApiProtocol()
            ?: apiProtocol
        val modelInfos = modelCatalogRepository.fetchModelInfos(
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiProtocol = providerProtocol,
        )
        if (!persistResult) {
            return SettingsModelLoadResult(
                pendingFetchedModels = modelInfos,
                pendingFetchProviderId = providerId,
                message = "已获取 ${modelInfos.size} 个模型，请选择要添加的",
            )
        }

        val mergedModelInfos = mergeModelInfosPreservingOverrides(
            fetchedModels = modelInfos,
            previousModels = persistedProviders
                .firstOrNull { it.id == providerId }
                ?.models
                .orEmpty(),
        )
        val models = mergedModelInfos.map { it.modelId }
        val resolvedSelectedModel = when {
            selectedModel in models -> selectedModel
            models.isNotEmpty() -> models.first()
            else -> ""
        }
        val providersToPersist = persistedProviders.map { provider ->
            if (provider.id == providerId) {
                provider.copy(
                    availableModels = models,
                    models = mergedModelInfos,
                    selectedModel = resolvedSelectedModel,
                )
            } else {
                provider
            }
        }
        settingsEditor.saveProviderSettings(
            providers = providersToPersist,
            selectedProviderId = persistedSelectedProviderId.ifBlank { providerId },
        )
        return SettingsModelLoadResult(
            message = "模型已同步",
        )
    }
}
