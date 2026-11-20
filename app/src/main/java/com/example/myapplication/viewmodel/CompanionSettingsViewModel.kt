package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

data class CompanionSettingsUiState(
    val assistantId: String,
    val characterName: String = "",
    val draft: CompanionCharacterSettings? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val characterAvailable: Boolean = true,
    val isDirty: Boolean = false,
    val hasConflict: Boolean = false,
    val errorMessage: String? = null,
    val showDiscardConfirmation: Boolean = false,
    val closeRequested: Boolean = false,
) {
    val editable: Boolean get() = !isLoading && !isSaving && characterAvailable && draft?.schemaVersion == 1
}

/** 独立角色小配置草稿；保存失败或 CAS 冲突不清草稿，重新读取需由页面明确选择。 */
class CompanionSettingsViewModel(
    private val assistantId: String,
    private val settingsStore: SettingsStore,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanionSettingsUiState(assistantId))
    val uiState = mutableState.asStateFlow()
    private var base: CompanionCharacterSettings? = null
    private var latest: CompanionCharacterSettings? = null

    init {
        viewModelScope.launch {
            try {
                settingsStore.settingsFlow.collect { settings ->
                    val character =
                        settings.resolvedAssistants().firstOrNull { it.id == assistantId }
                    val current = settings.companionSettings(assistantId)
                    latest = current
                    if (mutableState.value.isLoading) {
                        val restored = savedState.get<String>(CHARACTER_KEY) == assistantId
                        base = if (restored) savedState[BASE_KEY] ?: current else current
                        val draft =
                            if (restored) savedState.get<CompanionCharacterSettings>(DRAFT_KEY)
                                ?: current else current
                        mutableState.update { it.copy(draft = draft, isLoading = false) }
                    } else if (!mutableState.value.isDirty && !mutableState.value.isSaving) {
                        base = current
                        mutableState.update { it.copy(draft = current) }
                    }
                    mutableState.update { state ->
                        state.copy(
                            characterName = character?.name.orEmpty(),
                            characterAvailable = character != null,
                            isDirty = state.draft != base,
                            hasConflict = base?.revision != current.revision,
                            errorMessage = if (character == null) "角色已不存在，草稿仍保留。" else state.errorMessage,
                        )
                    }
                    persistDraft()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = safeError(failure)
                    )
                }
            }
        }
    }

    fun updateWriting(writing: CompanionWritingSettings) = edit { it.copy(writing = writing) }
    fun updateZone(zoneId: String) = edit { it.copy(zoneId = zoneId) }

    fun addCustomField() {
        val draft = mutableState.value.draft ?: return
        val observation = draft.writing.observation
        if (observation.customFields.size >= CompanionWritingValidation.MAX_CUSTOM_FIELDS) return
        val used = (observation.fields + observation.customFields).map { it.label }.toSet()
        val label = generateSequence(1) { it + 1 }.map { "自定义观测 $it" }.first { it !in used }
        updateWriting(
            draft.writing.copy(
                observation = observation.copy(
                    customFields = observation.customFields + CompanionObservationField(
                        "obs_${newId()}",
                        label
                    ),
                )
            )
        )
    }

    fun addTemplate(name: String) {
        val draft = mutableState.value.draft ?: return
        val writing = draft.writing
        val next = writing.copy(
            templates = writing.templates + CompanionStyleTemplate(
                newId(),
                name.trim(),
                writing.extraStyle
            )
        )
        val error = CompanionWritingValidation.error(next)
        if (error != null) mutableState.update { it.copy(errorMessage = error) } else updateWriting(
            next
        )
    }

    private fun edit(transform: (CompanionCharacterSettings) -> CompanionCharacterSettings) {
        val state = mutableState.value
        if (!state.editable) return
        val draft = state.draft ?: return
        val next = transform(draft)
        mutableState.update { it.copy(draft = next, isDirty = next != base, errorMessage = null) }
        persistDraft()
    }

    fun save() {
        val state = mutableState.value
        val draft = state.draft ?: return
        val expected = base ?: return
        if (!state.editable) return
        val validation = CompanionWritingValidation.error(draft.writing)
            ?: if (draft.zoneId.isNotBlank() && runCatching { ZoneId.of(draft.zoneId.trim()) }.isFailure)
                "时区无效，请使用 Asia/Shanghai 等地区名称，或留空。" else null
        if (validation != null) {
            mutableState.update { it.copy(errorMessage = validation) }
            return
        }
        if (!state.isDirty) {
            mutableState.update { it.copy(closeRequested = true) }
            return
        }
        mutableState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (val result = settingsStore.updateCompanionSettings(
                    assistantId,
                    expected.revision,
                    CompanionSettingsPatch(draft.zoneId, draft.writing),
                )) {
                    is CompanionSettingsWriteResult.Saved -> {
                        base = result.settings
                        latest = result.settings
                        mutableState.update {
                            it.copy(
                                draft = result.settings, isDirty = false, hasConflict = false,
                                errorMessage = null, closeRequested = true,
                            )
                        }
                        persistDraft()
                    }

                    CompanionSettingsWriteResult.Conflict -> mutableState.update {
                        it.copy(
                            hasConflict = true,
                            errorMessage = "设置已在别处更新，草稿已保留。可继续编辑，或放弃草稿并读取最新设置。",
                        )
                    }

                    CompanionSettingsWriteResult.UnsupportedVersion -> mutableState.update {
                        it.copy(
                            errorMessage = "此角色的陪伴设置版本较新，未覆盖已有设置，草稿已保留。",
                        )
                    }

                    CompanionSettingsWriteResult.CharacterMissing -> mutableState.update {
                        it.copy(
                            characterAvailable = false,
                            errorMessage = "角色已不存在，未保存，草稿仍保留。",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(errorMessage = safeError(failure)) }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun requestClose() {
        if (mutableState.value.isSaving) return
        mutableState.update {
            if (it.isDirty) it.copy(showDiscardConfirmation = true) else it.copy(
                closeRequested = true
            )
        }
    }

    fun dismissDiscard() = mutableState.update { it.copy(showDiscardConfirmation = false) }

    fun discardAndClose() {
        if (mutableState.value.isSaving) return
        savedState.remove<CompanionCharacterSettings>(DRAFT_KEY)
        savedState.remove<CompanionCharacterSettings>(BASE_KEY)
        mutableState.update { it.copy(showDiscardConfirmation = false, closeRequested = true) }
    }

    fun reloadDiscardingDraft() {
        if (mutableState.value.isSaving) return
        val current = latest ?: return
        base = current
        mutableState.update {
            it.copy(
                draft = current,
                isDirty = false,
                hasConflict = false,
                errorMessage = null
            )
        }
        persistDraft()
    }

    private fun persistDraft() {
        savedState[CHARACTER_KEY] = assistantId
        savedState[BASE_KEY] = base
        savedState[DRAFT_KEY] = mutableState.value.draft
    }

    private fun safeError(failure: Exception): String = SensitiveTextRedactor.throwableMessageForUi(
        failure, "陪伴设置保存未完成，草稿已保留，请重试。",
    )

    companion object {
        private const val CHARACTER_KEY = "companion.settings.character"
        private const val BASE_KEY = "companion.settings.base"
        private const val DRAFT_KEY = "companion.settings.draft"

        fun factory(
            assistantId: String,
            settingsStore: SettingsStore,
            savedState: SavedStateHandle
        ) =
            typedViewModelFactory {
                CompanionSettingsViewModel(
                    assistantId,
                    settingsStore,
                    savedState
                )
            }
    }
}
