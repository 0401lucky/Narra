package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePresetRepository(
    initialPresets: List<Preset> = emptyList(),
) : PresetRepository {
    private val presetsState = MutableStateFlow(initialPresets)

    override fun observePresets(): Flow<List<Preset>> = presetsState

    override suspend fun listPresets(): List<Preset> = presetsState.value

    override suspend fun getPreset(presetId: String): Preset? {
        return presetsState.value.firstOrNull { it.id == presetId }
    }

    override suspend fun upsertPreset(preset: Preset) {
        presetsState.value = presetsState.value.filterNot { it.id == preset.id } + preset
    }

    override suspend fun deleteCustomPreset(presetId: String) {
        presetsState.value = presetsState.value.filterNot { it.id == presetId && !it.builtIn }
    }

    override suspend fun restoreBuiltInPreset(presetId: String) = Unit

    override suspend fun ensureBuiltInPresets() = Unit

    suspend fun currentPresets(): List<Preset> = presetsState.value
}
