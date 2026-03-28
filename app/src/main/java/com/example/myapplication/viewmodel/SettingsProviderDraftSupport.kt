package com.example.myapplication.viewmodel

import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.createDefaultProvider
import com.example.myapplication.model.inferredModelInfo
import com.example.myapplication.model.withAbilityOverride

data class ProviderModelSelectionUpdate(
    val providers: List<ProviderSettings>,
    val message: String,
)

data class ProviderDraftMutationResult(
    val providers: List<ProviderSettings>,
    val selectedProviderId: String = "",
)

object SettingsProviderDraftSupport {
    fun ensureProviders(providers: List<ProviderSettings>): List<ProviderSettings> {
        return if (providers.isEmpty()) {
            listOf(createDefaultProvider())
        } else {
            providers
        }
    }

    fun resolveSelectedProviderId(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ): String {
        return providers.firstOrNull { it.id == selectedProviderId }?.id
            ?: providers.firstOrNull()?.id
            ?: ""
    }

    fun normalizeProviders(providers: List<ProviderSettings>): List<ProviderSettings> {
        return ensureProviders(providers).mapIndexed { index, provider ->
            provider.copy(
                name = provider.name.trim().ifBlank { "提供商 ${index + 1}" },
                baseUrl = provider.baseUrl.trim(),
                apiKey = provider.apiKey.trim(),
                selectedModel = provider.selectedModel.trim(),
                chatCompletionsPath = provider.resolvedChatCompletionsPath(),
            )
        }
    }

    fun mergeModels(
        currentModels: List<String>,
        selectedModel: String,
    ): List<String> {
        return buildList {
            addAll(currentModels)
            if (selectedModel.isNotBlank() && selectedModel !in currentModels) {
                add(selectedModel)
            }
        }
    }

    fun updateProviderModelAbilities(
        provider: ProviderSettings,
        modelId: String,
        abilities: Set<ModelAbility>?,
    ): ProviderSettings {
        val currentModels = provider.models.orEmpty()
        val existingModel = currentModels.firstOrNull { it.modelId == modelId }
        val updatedModel = (existingModel ?: inferredModelInfo(modelId)).withAbilityOverride(abilities)
        return provider.copy(
            availableModels = mergeModels(
                currentModels = provider.availableModels,
                selectedModel = modelId,
            ),
            models = if (existingModel != null) {
                currentModels.map { model ->
                    if (model.modelId == modelId) {
                        updatedModel
                    } else {
                        model
                    }
                }
            } else {
                currentModels + updatedModel
            },
        )
    }

    fun applyFetchedModelSelection(
        currentProviders: List<ProviderSettings>,
        providerId: String,
        fetchedModels: List<ModelInfo>,
        selectedModelIds: Set<String>,
    ): ProviderModelSelectionUpdate {
        val updatedProviders = currentProviders.map { provider ->
            if (provider.id == providerId) {
                val existingModels = provider.models.orEmpty()
                val existingById = existingModels.associateBy { it.modelId }
                val fetchedById = fetchedModels.associateBy { it.modelId }

                val finalModels = selectedModelIds.mapNotNull { modelId ->
                    val existing = existingById[modelId]
                    val fetched = fetchedById[modelId]
                    when {
                        existing != null -> existing
                        fetched != null -> fetched
                        else -> null
                    }
                }

                val finalIds = finalModels.map { it.modelId }
                val resolvedSelected = when {
                    provider.selectedModel in finalIds -> provider.selectedModel
                    finalIds.isNotEmpty() -> finalIds.first()
                    else -> ""
                }

                provider.copy(
                    availableModels = finalIds,
                    models = finalModels.ifEmpty { null },
                    selectedModel = resolvedSelected,
                )
            } else {
                provider
            }
        }

        val existingIds = currentProviders
            .firstOrNull { it.id == providerId }
            ?.models.orEmpty()
            .map { it.modelId }
            .toSet()
        val added = selectedModelIds.count { it !in existingIds }
        val removed = existingIds.count { it !in selectedModelIds }
        val message = buildString {
            append("模型已更新")
            if (added > 0) append("，新增 $added")
            if (removed > 0) append("，移除 $removed")
        }

        return ProviderModelSelectionUpdate(
            providers = updatedProviders,
            message = message,
        )
    }

    fun toggleProviderEnabled(
        providers: List<ProviderSettings>,
        providerId: String,
    ): List<ProviderSettings> {
        return providers.map { provider ->
            if (provider.id == providerId) {
                provider.copy(enabled = !provider.enabled)
            } else {
                provider
            }
        }
    }

    fun deleteProvider(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
        providerId: String,
    ): ProviderDraftMutationResult {
        val remainingProviders = ensureProviders(providers.filterNot { it.id == providerId })
        val resolvedSelectedProviderId = when {
            selectedProviderId == providerId -> remainingProviders.first().id
            else -> resolveSelectedProviderId(
                providers = remainingProviders,
                selectedProviderId = selectedProviderId,
            )
        }
        return ProviderDraftMutationResult(
            providers = remainingProviders,
            selectedProviderId = resolvedSelectedProviderId,
        )
    }

    fun removeModelFromProvider(
        providers: List<ProviderSettings>,
        providerId: String,
        modelId: String,
    ): List<ProviderSettings> {
        return providers.map { provider ->
            if (provider.id == providerId) {
                val updatedModels = provider.models.orEmpty().filter { it.modelId != modelId }
                val updatedAvailable = provider.availableModels.filter { it != modelId }
                val resolvedSelected = when {
                    provider.selectedModel != modelId -> provider.selectedModel
                    updatedModels.isNotEmpty() -> updatedModels.first().modelId
                    else -> ""
                }
                provider.copy(
                    availableModels = updatedAvailable,
                    models = updatedModels.ifEmpty { null },
                    selectedModel = resolvedSelected,
                )
            } else {
                provider
            }
        }
    }
}
