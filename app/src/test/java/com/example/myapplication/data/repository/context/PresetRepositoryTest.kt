package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.preset.PresetDao
import com.example.myapplication.data.local.preset.PresetEntity
import com.example.myapplication.model.BUILTIN_PRESETS
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetSamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRepositoryTest {
    @Test
    fun ensureBuiltInPresets_insertsDefaultPresets() = runTest {
        val dao = FakePresetDao()
        val repository = RoomPresetRepository(dao)

        repository.ensureBuiltInPresets()

        val presets = repository.listPresets()
        assertEquals(BUILTIN_PRESETS.map { it.id }.sorted(), presets.map { it.id }.sorted())
        assertTrue(presets.all { it.builtIn })
        assertNotNull(repository.getPreset(DEFAULT_PRESET_ID))
    }

    @Test
    fun ensureBuiltInPresets_doesNotOverwriteUserModifiedBuiltIn() = runTest {
        val dao = FakePresetDao()
        val repository = RoomPresetRepository(dao)
        repository.ensureBuiltInPresets()
        val original = dao.getPreset(DEFAULT_PRESET_ID)!!
        dao.upsertPreset(
            original.copy(
                name = "用户改过的预设",
                userModified = true,
                version = 1,
            ),
        )

        repository.ensureBuiltInPresets()

        val loaded = repository.getPreset(DEFAULT_PRESET_ID)
        assertEquals("用户改过的预设", loaded?.name)
        assertTrue(loaded?.userModified == true)
    }

    @Test
    fun upsertPreset_doesNotModifyExistingBuiltInPreset() = runTest {
        val dao = FakePresetDao()
        val repository = RoomPresetRepository(dao)
        repository.ensureBuiltInPresets()
        val original = repository.getPreset(DEFAULT_PRESET_ID)!!

        repository.upsertPreset(
            original.copy(
                name = "界面试图改名",
                description = "不应写入内置预设",
                builtIn = true,
            ),
        )

        val loaded = repository.getPreset(DEFAULT_PRESET_ID)
        assertEquals(original.name, loaded?.name)
        assertEquals(original.description, loaded?.description)
        assertTrue(loaded?.builtIn == true)
    }

    @Test
    fun upsertPreset_roundTripsSamplerEntriesAndMarksCustomAsModified() = runTest {
        val dao = FakePresetDao()
        val repository = RoomPresetRepository(dao)
        val custom = Preset(
            id = "custom-1",
            name = "我的预设",
            sampler = PresetSamplerConfig(
                temperature = 0.7f,
                topP = 0.9f,
                maxOutputTokens = 1200,
            ),
            stopSequences = listOf("  STOP  ", "STOP", ""),
            entries = listOf(
                PresetPromptEntry(
                    id = "entry-1",
                    title = "后置指令",
                    kind = PresetPromptEntryKind.POST_HISTORY,
                    content = "最后保持沉浸。",
                    order = 20,
                ),
            ),
            builtIn = false,
        )

        repository.upsertPreset(custom)

        val loaded = repository.getPreset("custom-1")
        assertEquals(0.7f, loaded?.sampler?.temperature)
        assertEquals(1200, loaded?.sampler?.maxOutputTokens)
        assertEquals(listOf("STOP"), loaded?.stopSequences)
        assertEquals("后置指令", loaded?.entries?.firstOrNull()?.title)
        assertEquals(PresetPromptEntryKind.POST_HISTORY, loaded?.entries?.firstOrNull()?.kind)
        assertTrue(loaded?.userModified == true)
        assertFalse(loaded?.builtIn == true)
    }
}

private class FakePresetDao : PresetDao {
    private val state = MutableStateFlow<List<PresetEntity>>(emptyList())

    override fun observePresets(): Flow<List<PresetEntity>> = state

    override suspend fun listPresets(): List<PresetEntity> {
        return state.value.sortedWith(
            compareByDescending<PresetEntity> { it.builtIn }
                .thenBy { it.name.lowercase() }
                .thenByDescending { it.updatedAt },
        )
    }

    override suspend fun getPreset(presetId: String): PresetEntity? {
        return state.value.firstOrNull { it.id == presetId }
    }

    override suspend fun upsertPreset(preset: PresetEntity) {
        state.value = state.value.filterNot { it.id == preset.id } + preset
    }

    override suspend fun deleteCustomPreset(presetId: String) {
        state.value = state.value.filterNot { it.id == presetId && !it.builtIn }
    }
}
