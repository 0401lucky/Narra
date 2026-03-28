package com.example.myapplication.viewmodel

import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProviderDraftSupportTest {
    @Test
    fun normalizeProviders_trimsFieldsAndEnsuresFallbackName() {
        val normalized = SettingsProviderDraftSupport.normalizeProviders(
            listOf(
                ProviderSettings(
                    id = "provider-1",
                    name = "   ",
                    baseUrl = " https://example.com/v1/ ",
                    apiKey = " key ",
                    selectedModel = " model-a ",
                ),
            ),
        )

        assertEquals("提供商 1", normalized.single().name)
        assertEquals("https://example.com/v1/", normalized.single().baseUrl)
        assertEquals("key", normalized.single().apiKey)
        assertEquals("model-a", normalized.single().selectedModel)
    }

    @Test
    fun updateProviderModelAbilities_preservesOverride() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            selectedModel = "gpt-4o",
            availableModels = listOf("gpt-4o"),
            models = listOf(
                ModelInfo(
                    modelId = "gpt-4o",
                    abilities = setOf(ModelAbility.VISION),
                ),
            ),
        )

        val updated = SettingsProviderDraftSupport.updateProviderModelAbilities(
            provider = provider,
            modelId = "gpt-4o",
            abilities = setOf(ModelAbility.REASONING),
        )

        assertEquals(setOf(ModelAbility.REASONING), updated.models?.single()?.abilities)
        assertTrue(updated.models?.single()?.abilitiesCustomized == true)
    }

    @Test
    fun applyFetchedModelSelection_keepsExistingOverridesAndBuildsMessage() {
        val currentProviders = listOf(
            ProviderSettings(
                id = "provider-1",
                name = "Provider",
                selectedModel = "model-a",
                availableModels = listOf("model-a"),
                models = listOf(
                    ModelInfo(
                        modelId = "model-a",
                        abilities = setOf(ModelAbility.TOOL),
                        abilitiesCustomized = true,
                    ),
                ),
            ),
        )
        val fetchedModels = listOf(
            ModelInfo(modelId = "model-a"),
            ModelInfo(modelId = "model-b"),
        )

        val update = SettingsProviderDraftSupport.applyFetchedModelSelection(
            currentProviders = currentProviders,
            providerId = "provider-1",
            fetchedModels = fetchedModels,
            selectedModelIds = linkedSetOf("model-a", "model-b"),
        )

        assertEquals(listOf("model-a", "model-b"), update.providers.single().availableModels)
        assertEquals(setOf(ModelAbility.TOOL), update.providers.single().models?.first { it.modelId == "model-a" }?.abilities)
        assertTrue(update.message.contains("新增 1"))
    }

    @Test
    fun toggleProviderEnabled_flipsEnabledFlag() {
        val providers = listOf(
            ProviderSettings(
                id = "provider-1",
                name = "Provider",
                enabled = true,
            ),
        )

        val updated = SettingsProviderDraftSupport.toggleProviderEnabled(
            providers = providers,
            providerId = "provider-1",
        )

        assertEquals(false, updated.single().enabled)
    }

    @Test
    fun deleteProvider_removesTargetAndResolvesNewSelection() {
        val update = SettingsProviderDraftSupport.deleteProvider(
            providers = listOf(
                ProviderSettings(id = "provider-1", name = "Provider 1"),
                ProviderSettings(id = "provider-2", name = "Provider 2"),
            ),
            selectedProviderId = "provider-1",
            providerId = "provider-1",
        )

        assertEquals(listOf("provider-2"), update.providers.map { it.id })
        assertEquals("provider-2", update.selectedProviderId)
    }

    @Test
    fun removeModelFromProvider_dropsModelAndFallsBackSelection() {
        val updated = SettingsProviderDraftSupport.removeModelFromProvider(
            providers = listOf(
                ProviderSettings(
                    id = "provider-1",
                    name = "Provider",
                    selectedModel = "model-a",
                    availableModels = listOf("model-a", "model-b"),
                    models = listOf(
                        ModelInfo(modelId = "model-a"),
                        ModelInfo(modelId = "model-b"),
                    ),
                ),
            ),
            providerId = "provider-1",
            modelId = "model-a",
        )

        assertEquals(listOf("model-b"), updated.single().availableModels)
        assertEquals("model-b", updated.single().selectedModel)
        assertEquals(listOf("model-b"), updated.single().models?.map { it.modelId })
    }
}
