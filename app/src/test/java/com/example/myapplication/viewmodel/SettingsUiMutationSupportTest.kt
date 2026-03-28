package com.example.myapplication.viewmodel

import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiMutationSupportTest {
    @Test
    fun beginSaving_setsSavingAndClearsMessage() {
        val updated = SettingsUiMutationSupport.beginSaving(
            SettingsUiState(
                isSaving = false,
                message = "旧消息",
            ),
        )

        assertTrue(updated.isSaving)
        assertEquals(null, updated.message)
    }

    @Test
    fun applyLoadModelsSuccess_updatesPendingModelsAndMessage() {
        val updated = SettingsUiMutationSupport.applyLoadModelsSuccess(
            current = SettingsUiState(isLoadingModels = true, loadingProviderId = "provider-1"),
            result = SettingsModelLoadResult(
                message = "已获取 2 个模型",
                pendingFetchedModels = listOf(
                    com.example.myapplication.model.ModelInfo(modelId = "model-a"),
                    com.example.myapplication.model.ModelInfo(modelId = "model-b"),
                ),
                pendingFetchProviderId = "provider-1",
            ),
        )

        assertFalse(updated.isLoadingModels)
        assertEquals("", updated.loadingProviderId)
        assertEquals(2, updated.pendingFetchedModels.size)
        assertEquals("provider-1", updated.pendingFetchProviderId)
        assertEquals("已获取 2 个模型", updated.message)
    }

    @Test
    fun deleteProvider_updatesSelectionAndClearsMessage() {
        val providers = listOf(
            ProviderSettings(id = "p1", name = "A"),
            ProviderSettings(id = "p2", name = "B"),
        )
        val updated = SettingsUiMutationSupport.deleteProvider(
            current = SettingsUiState(
                providers = providers,
                selectedProviderId = "p2",
                message = "旧消息",
            ),
            providerId = "p2",
        )

        assertEquals(1, updated.providers.size)
        assertEquals("p1", updated.selectedProviderId)
        assertEquals(null, updated.message)
    }

    @Test
    fun applyMessageError_setsFailureMessage() {
        val updated = SettingsUiMutationSupport.applyMessageError(
            current = SettingsUiState(message = "旧消息"),
            errorMessage = "保存失败",
        )

        assertEquals("保存失败", updated.message)
    }

    @Test
    fun applyPersistenceSuccess_updatesProvidersAndSelectionWhenPresent() {
        val result = SettingsPersistenceResult(
            message = "模型已切换",
            providers = listOf(ProviderSettings(id = "p1", name = "A", selectedModel = "model-new")),
            selectedProviderId = "p1",
        )
        val updated = SettingsUiMutationSupport.applyPersistenceSuccess(
            current = SettingsUiState(
                providers = listOf(ProviderSettings(id = "p1", name = "A", selectedModel = "model-old")),
            ),
            result = result,
        )

        assertEquals("模型已切换", updated.message)
        assertEquals("p1", updated.selectedProviderId)
        assertEquals("model-new", updated.providers.single().selectedModel)
    }
}
