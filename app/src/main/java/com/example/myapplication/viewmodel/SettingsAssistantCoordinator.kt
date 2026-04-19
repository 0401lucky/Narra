package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import java.util.UUID

class SettingsAssistantCoordinator(
    private val settingsEditor: AiSettingsEditor,
    private val imageFileCleaner: suspend (String?) -> Boolean = { false },
) {
    suspend fun addAssistant(
        settings: AppSettings,
        assistant: Assistant,
    ) {
        val currentAssistants = settings.assistants.toMutableList()
        currentAssistants.add(assistant)
        settingsEditor.saveAssistants(currentAssistants, settings.selectedAssistantId)
    }

    suspend fun updateAssistant(
        settings: AppSettings,
        assistant: Assistant,
    ) {
        val currentAssistants = settings.assistants.toMutableList()
        val index = currentAssistants.indexOfFirst { it.id == assistant.id }
        if (index >= 0) {
            currentAssistants[index] = assistant
        } else {
            currentAssistants.add(assistant)
        }
        settingsEditor.saveAssistants(currentAssistants, settings.selectedAssistantId)
    }

    suspend fun removeAssistant(
        settings: AppSettings,
        assistantId: String,
    ) {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        if (assistantId in builtinIds) {
            return
        }
        val target = settings.assistants.firstOrNull { it.id == assistantId }
        val updatedAssistants = settings.assistants.filter { it.id != assistantId }
        val selectedId = if (settings.selectedAssistantId == assistantId) {
            DEFAULT_ASSISTANT_ID
        } else {
            settings.selectedAssistantId
        }
        settingsEditor.saveAssistants(updatedAssistants, selectedId)
        target?.let { imageFileCleaner(it.avatarUri) }
    }

    suspend fun duplicateAssistant(
        settings: AppSettings,
        assistantId: String,
    ) {
        val source = settings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (副本)",
            isBuiltin = false,
        )
        addAssistant(settings, copy)
    }

    suspend fun selectAssistant(
        settings: AppSettings,
        assistantId: String,
    ) {
        settingsEditor.saveAssistants(settings.assistants, assistantId)
    }
}
