package com.example.myapplication.testutil

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class CompanionD4SettingsStore(
    initial: AppSettings = AppSettings(
        assistants = listOf(
            Assistant(id = "alice"),
            Assistant(id = "bob")
        ), selectedAssistantId = "alice"
    )
) : SettingsStore by FakeSettingsStore(initial) {
    override val settingsFlow = MutableStateFlow(initial)
    val writes = mutableListOf<Triple<String, Long, CompanionSettingsPatch>>()
    var beforeWrite: suspend () -> Unit = {}
    var result: CompanionSettingsWriteResult? = null
    var failure: Exception? = null

    override suspend fun updateCompanionSettings(
        assistantId: String,
        expectedRevision: Long,
        patch: CompanionSettingsPatch
    ): CompanionSettingsWriteResult {
        writes += Triple(assistantId, expectedRevision, patch)
        beforeWrite()
        failure?.let { throw it }
        result?.let { return it }
        val settings = settingsFlow.value
        if (settings.resolvedAssistants()
                .none { it.id == assistantId }
        ) return CompanionSettingsWriteResult.CharacterMissing
        val current = settings.companionSettings(assistantId)
        if (current.revision != expectedRevision) return CompanionSettingsWriteResult.Conflict
        val saved = current.copy(
            revision = current.revision + 1, zoneId = patch.zoneId ?: current.zoneId,
            writing = patch.writing ?: current.writing, visuals = patch.visuals ?: current.visuals
        )
        settingsFlow.value =
            settings.copy(companionCharacterSettings = settings.companionCharacterSettings + (assistantId to saved))
        return CompanionSettingsWriteResult.Saved(saved)
    }

    override suspend fun companionImageReferences(): Set<String> =
        settingsFlow.value.companionCharacterSettings.values
            .flatMap { it.visuals.resources.map { resource -> resource.path } }.toSet()
}
