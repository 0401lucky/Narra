package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.preset.PresetDao
import com.example.myapplication.data.local.preset.PresetEntity
import com.example.myapplication.model.BUILTIN_PRESETS
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetInstructConfig
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetRenderConfig
import com.example.myapplication.model.PresetSamplerConfig
import com.example.myapplication.system.json.AppJson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface PresetRepository {
    fun observePresets(): Flow<List<Preset>>

    suspend fun listPresets(): List<Preset>

    suspend fun getPreset(presetId: String): Preset?

    suspend fun upsertPreset(preset: Preset)

    suspend fun deleteCustomPreset(presetId: String)

    suspend fun restoreBuiltInPreset(presetId: String)

    suspend fun ensureBuiltInPresets()
}

class RoomPresetRepository(
    private val presetDao: PresetDao,
    private val builtInPresets: List<Preset> = BUILTIN_PRESETS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : PresetRepository {
    override fun observePresets(): Flow<List<Preset>> {
        return presetDao.observePresets().map { presets -> presets.map(::toDomain) }
    }

    override suspend fun listPresets(): List<Preset> {
        return presetDao.listPresets().map(::toDomain)
    }

    override suspend fun getPreset(presetId: String): Preset? {
        return presetDao.getPreset(presetId.trim())?.let(::toDomain)
    }

    override suspend fun upsertPreset(preset: Preset) {
        val normalized = preset.normalized()
        val existing = presetDao.getPreset(normalized.id)
        if (existing?.builtIn == true) {
            return
        }
        presetDao.upsertPreset(
            toEntity(
                normalized.copy(
                    builtIn = false,
                    userModified = true,
                ),
            ),
        )
    }

    override suspend fun deleteCustomPreset(presetId: String) {
        presetDao.deleteCustomPreset(presetId.trim())
    }

    override suspend fun restoreBuiltInPreset(presetId: String) {
        val normalizedBuiltIn = builtInPresets
            .firstOrNull { it.id == presetId.trim() }
            ?.normalized()
            ?.copy(builtIn = true, userModified = false)
            ?: return
        val existing = presetDao.getPreset(normalizedBuiltIn.id)
        presetDao.upsertPreset(
            toEntity(
                normalizedBuiltIn.copy(
                    createdAt = existing?.createdAt?.takeIf { it > 0L } ?: normalizedBuiltIn.createdAt,
                    updatedAt = nowProvider(),
                ),
            ),
        )
    }

    override suspend fun ensureBuiltInPresets() {
        builtInPresets.forEach { builtIn ->
            val normalizedBuiltIn = builtIn.normalized().copy(builtIn = true, userModified = false)
            val existing = presetDao.getPreset(normalizedBuiltIn.id)
            when {
                existing == null -> {
                    presetDao.upsertPreset(toEntity(normalizedBuiltIn))
                }
                existing.builtIn && !existing.userModified && existing.version < normalizedBuiltIn.version -> {
                    presetDao.upsertPreset(
                        toEntity(
                            normalizedBuiltIn.copy(
                                createdAt = existing.createdAt.takeIf { it > 0L } ?: normalizedBuiltIn.createdAt,
                                updatedAt = nowProvider(),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun toDomain(entity: PresetEntity): Preset {
        return Preset(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            systemPrompt = entity.systemPrompt,
            contextTemplate = entity.contextTemplate,
            sampler = decodeSampler(entity.samplerJson),
            instructMode = decodeInstruct(entity.instructJson),
            stopSequences = decodeStringList(entity.stopSequencesJson),
            entries = decodeEntries(entity.entriesJson),
            renderConfig = decodeRenderConfig(entity.renderConfigJson),
            version = entity.version,
            builtIn = entity.builtIn,
            userModified = entity.userModified,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toEntity(preset: Preset): PresetEntity {
        val normalized = preset.normalized()
        return PresetEntity(
            id = normalized.id,
            name = normalized.name,
            description = normalized.description,
            systemPrompt = normalized.systemPrompt,
            contextTemplate = normalized.contextTemplate,
            samplerJson = AppJson.gson.toJson(normalized.sampler),
            instructJson = normalized.instructMode?.let(AppJson.gson::toJson).orEmpty(),
            stopSequencesJson = AppJson.gson.toJson(normalized.stopSequences),
            entriesJson = AppJson.gson.toJson(normalized.entries),
            renderConfigJson = AppJson.gson.toJson(normalized.renderConfig),
            version = normalized.version,
            builtIn = normalized.builtIn,
            userModified = normalized.userModified,
            createdAt = normalized.createdAt,
            updatedAt = normalized.updatedAt,
        )
    }

    private fun decodeSampler(rawJson: String): PresetSamplerConfig {
        return runCatching {
            AppJson.gson.fromJson(rawJson, PresetSamplerConfig::class.java)
        }.getOrNull() ?: PresetSamplerConfig()
    }

    private fun decodeInstruct(rawJson: String): PresetInstructConfig? {
        if (rawJson.isBlank()) {
            return null
        }
        return runCatching {
            AppJson.gson.fromJson(rawJson, PresetInstructConfig::class.java)
        }.getOrNull()
    }

    private fun decodeStringList(rawJson: String): List<String> {
        if (rawJson.isBlank()) {
            return emptyList()
        }
        return runCatching {
            AppJson.gson.fromJson<List<String>>(rawJson, stringListType).orEmpty()
        }.getOrDefault(emptyList())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun decodeEntries(rawJson: String): List<PresetPromptEntry> {
        if (rawJson.isBlank()) {
            return emptyList()
        }
        return runCatching {
            AppJson.gson.fromJson<List<PresetPromptEntry>>(rawJson, entriesType).orEmpty()
        }.getOrDefault(emptyList())
            .mapIndexed { index, entry -> entry.normalized(index) }
            .distinctBy(PresetPromptEntry::id)
    }

    private fun decodeRenderConfig(rawJson: String): PresetRenderConfig {
        if (rawJson.isBlank()) {
            return PresetRenderConfig()
        }
        return runCatching {
            AppJson.gson.fromJson(rawJson, PresetRenderConfig::class.java)
        }.getOrNull()?.normalized() ?: PresetRenderConfig()
    }

    private companion object {
        val stringListType = object : TypeToken<List<String>>() {}.type
        val entriesType = object : TypeToken<List<PresetPromptEntry>>() {}.type
    }
}

object EmptyPresetRepository : PresetRepository {
    override fun observePresets(): Flow<List<Preset>> = flowOf(emptyList())

    override suspend fun listPresets(): List<Preset> = emptyList()

    override suspend fun getPreset(presetId: String): Preset? = null

    override suspend fun upsertPreset(preset: Preset) = Unit

    override suspend fun deleteCustomPreset(presetId: String) = Unit

    override suspend fun restoreBuiltInPreset(presetId: String) = Unit

    override suspend fun ensureBuiltInPresets() = Unit
}
