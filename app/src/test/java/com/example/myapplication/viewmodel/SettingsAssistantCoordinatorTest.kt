package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.UserPersonaMask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAssistantCoordinatorTest {
    @Test
    fun addAssistant_appendsAssistantAndPreservesSelection() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsAssistantCoordinator(editor)

        coordinator.addAssistant(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "旧助手"),
                ),
                selectedAssistantId = "assistant-1",
            ),
            assistant = Assistant(id = "assistant-2", name = "新助手"),
        )

        assertEquals(listOf("assistant-1", "assistant-2"), editor.savedAssistants.map { it.id })
        assertEquals("assistant-1", editor.savedSelectedAssistantId)
    }

    @Test
    fun updateAssistant_replacesExistingAssistant() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsAssistantCoordinator(editor)

        coordinator.updateAssistant(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "旧名字"),
                ),
                selectedAssistantId = "assistant-1",
            ),
            assistant = Assistant(id = "assistant-1", name = "新名字"),
        )

        assertEquals("新名字", editor.savedAssistants.single().name)
    }

    @Test
    fun removeAssistant_fallsBackToDefaultSelection() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsAssistantCoordinator(editor)

        coordinator.removeAssistant(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "自定义助手"),
                ),
                selectedAssistantId = "assistant-1",
            ),
            assistantId = "assistant-1",
        )

        assertTrue(editor.savedAssistants.isEmpty())
        assertEquals(DEFAULT_ASSISTANT_ID, editor.savedSelectedAssistantId)
    }

    @Test
    fun duplicateAssistant_createsNonBuiltinCopy() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsAssistantCoordinator(editor)

        coordinator.duplicateAssistant(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "原助手"),
                ),
            ),
            assistantId = "assistant-1",
        )

        assertEquals(2, editor.savedAssistants.size)
        assertTrue(editor.savedAssistants.last().name.contains("(副本)"))
        assertEquals(false, editor.savedAssistants.last().isBuiltin)
    }

    @Test
    fun selectAssistant_persistsSelectionOnly() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsAssistantCoordinator(editor)

        coordinator.selectAssistant(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "原助手"),
                ),
            ),
            assistantId = "assistant-1",
        )

        assertEquals("assistant-1", editor.savedSelectedAssistantId)
        assertEquals(1, editor.savedAssistants.size)
    }

    private class RecordingSettingsEditor : AiSettingsEditor {
        var savedAssistants: List<Assistant> = emptyList()
        var savedSelectedAssistantId: String = ""

        override suspend fun saveProviderSettings(providers: List<ProviderSettings>, selectedProviderId: String) = Unit

        override suspend fun saveFunctionModelProviderIds(
            functionModelProviderIds: com.example.myapplication.model.FunctionModelProviderIds,
        ) = Unit

        override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) = Unit

        override suspend fun saveAssistants(assistants: List<Assistant>, selectedAssistantId: String) {
            savedAssistants = assistants
            savedSelectedAssistantId = selectedAssistantId
        }

        override suspend fun saveDisplaySettings(
            themeMode: ThemeMode,
            messageTextScale: Float,
            reasoningExpandedByDefault: Boolean,
            showThinkingContent: Boolean,
            autoCollapseThinking: Boolean,
            autoPreviewImages: Boolean,
            codeBlockAutoWrap: Boolean,
            codeBlockAutoCollapse: Boolean,
            showRoleplayAiHelper: Boolean,
            roleplayLongformTargetChars: Int,
            showRoleplayPresenceStrip: Boolean,
            showRoleplayStatusStrip: Boolean,
            showOnlineRoleplayNarration: Boolean,
            enableRoleplayNetMeme: Boolean,
            roleplayImmersiveMode: RoleplayImmersiveMode,
            roleplayHighContrast: Boolean,
            roleplayLineHeightScale: RoleplayLineHeightScale,
            roleplayNoBackgroundSkin: RoleplayNoBackgroundSkinSettings,
        ) = Unit

        override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) = Unit

        override suspend fun saveSearchSettings(settings: SearchSettings) = Unit

        override suspend fun saveUserProfile(displayName: String, personaPrompt: String, avatarUri: String, avatarUrl: String) = Unit
        override suspend fun saveUserPersonaMasks(masks: List<UserPersonaMask>, defaultMaskId: String) = Unit
        override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) = Unit
        override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = Unit
        override suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String) = Unit
        override suspend fun saveMemoryInjectionPosition(position: com.example.myapplication.model.MemoryInjectionPosition) = Unit
        override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = Unit
    }
}
