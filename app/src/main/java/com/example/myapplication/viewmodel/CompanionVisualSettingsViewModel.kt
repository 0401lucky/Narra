package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionResourcesRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CompanionVisualSettingsUiState(
    val assistantId: String,
    val scenarioId: String,
    val characterName: String = "",
    val draft: CompanionVisualSettings? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val isDirty: Boolean = false,
    val hasConflict: Boolean = false,
    val characterAvailable: Boolean = true,
    val settingsVersionSupported: Boolean = true,
    val errorMessage: String? = null,
    val showDiscardConfirmation: Boolean = false,
    val closeRequested: Boolean = false,
) {
    val editable: Boolean
        get() = !isLoading && !isSaving && !isImporting && characterAvailable &&
                settingsVersionSupported && draft?.schemaVersion == 1
}

/** 图像 IO 完成后才加入草稿，提交仍使用首次所见 revision；冲突、失效与取消均不覆盖旧设置。 */
class CompanionVisualSettingsViewModel(
    private val assistantId: String,
    private val scenarioId: String,
    private val settingsStore: SettingsStore,
    private val repository: CompanionResourcesRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(CompanionVisualSettingsUiState(assistantId, scenarioId))
    val uiState = mutableState.asStateFlow()
    private var base: CompanionCharacterSettings? = null
    private var latest: CompanionCharacterSettings? = null
    private val imported = mutableSetOf<String>()
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                settingsStore.settingsFlow.collect { settings ->
                    val current = settings.companionSettings(assistantId)
                    latest = current
                    val character =
                        settings.resolvedAssistants().firstOrNull { it.id == assistantId }
                    if (mutableState.value.isLoading) {
                        val restored = savedState.get<String>(OWNER) == "$assistantId:$scenarioId"
                        base = if (restored) savedState[BASE] ?: current else current
                        val draft = if (restored) savedState.get<CompanionVisualSettings>(DRAFT)
                            ?: current.visuals else current.visuals
                        if (restored) imported += savedState.get<ArrayList<String>>(IMPORTS)
                            .orEmpty()
                        mutableState.update { it.copy(draft = draft, isLoading = false) }
                    } else if (!mutableState.value.isDirty && !mutableState.value.isSaving && !mutableState.value.isImporting) {
                        base = current
                        mutableState.update { it.copy(draft = current.visuals) }
                    }
                    mutableState.update {
                        it.copy(
                            characterName = character?.name.orEmpty(),
                            characterAvailable = character != null,
                            settingsVersionSupported = current.schemaVersion == 1,
                            isDirty = it.draft != base?.visuals,
                            hasConflict = base?.revision != current.revision,
                            errorMessage = if (character == null) "角色已不存在，草稿仍保留。" else it.errorMessage
                        )
                    }
                    persist()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            }
        }
    }

    fun updateDraft(visuals: CompanionVisualSettings) {
        if (!mutableState.value.editable) return
        mutableState.update {
            it.copy(
                draft = visuals,
                isDirty = visuals != base?.visuals,
                errorMessage = null
            )
        }
        persist()
    }

    fun addOutfit() {
        val draft = mutableState.value.draft ?: return
        if (draft.outfits.size >= CompanionVisualValidation.MAX_OUTFITS) return
        val id = newId()
        updateDraft(
            draft.copy(
                outfits = draft.outfits + CompanionOutfit(
                    id,
                    "角色套装 ${draft.outfits.size + 1}"
                ), selectedOutfitId = id
            )
        )
    }

    fun removeResource(id: String) {
        val draft = mutableState.value.draft ?: return
        updateDraft(
            draft.copy(
                resources = draft.resources.filterNot { it.id == id },
                outfits = draft.outfits.map {
                    it.copy(
                        defaultPortraitId = it.defaultPortraitId?.takeUnless { ref -> ref == id },
                        expressions = it.expressions.filterValues { ref -> ref != id })
                },
                selectedBackgroundId = draft.selectedBackgroundId?.takeUnless { it == id },
                decorationIds = draft.decorationIds.filterNot { it == id },
                sceneOverrides = draft.sceneOverrides.mapValues { it.value.copy(selectedBackgroundId = it.value.selectedBackgroundId?.takeUnless { ref -> ref == id }) })
        )
    }

    fun importImage(uri: String, kind: CompanionImageKind, name: String) {
        if (!mutableState.value.editable || mutableState.value.draft?.resources.orEmpty().size >= CompanionVisualValidation.MAX_RESOURCES) return
        mutableState.update { it.copy(isImporting = true, errorMessage = null) }
        importJob = viewModelScope.launch {
            try {
                val resource = repository.importImage(uri, kind, name)
                imported += resource.path
                val draft = mutableState.value.draft ?: return@launch
                val next = draft.copy(resources = draft.resources + resource)
                mutableState.update { it.copy(draft = next, isDirty = true) }
                persist()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            } finally {
                mutableState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun save() {
        val state = mutableState.value
        val draft = state.draft ?: return
        val expected = base ?: return
        if (!state.editable) return
        CompanionVisualValidation.error(draft)
            ?.let { error -> mutableState.update { it.copy(errorMessage = error) }; return }
        if (!state.isDirty) {
            if (imported.isEmpty()) mutableState.update { it.copy(closeRequested = true) } else discard(
                reload = false
            )
            return
        }
        mutableState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (val result =
                    repository.save(assistantId, expected.revision, draft, imported.toSet())) {
                    is CompanionSettingsWriteResult.Saved -> {
                        base = result.settings
                        imported.clear()
                        mutableState.update {
                            it.copy(
                                draft = result.settings.visuals,
                                isDirty = false,
                                hasConflict = false,
                                closeRequested = true
                            )
                        }
                        persist()
                    }

                    CompanionSettingsWriteResult.Conflict -> mutableState.update {
                        it.copy(
                            hasConflict = true,
                            errorMessage = "设置已在别处更新，草稿仍保留。请读取最新后再调整。"
                        )
                    }

                    CompanionSettingsWriteResult.CharacterMissing -> mutableState.update {
                        it.copy(
                            characterAvailable = false,
                            errorMessage = "角色已不存在，草稿仍保留。"
                        )
                    }

                    CompanionSettingsWriteResult.UnsupportedVersion -> mutableState.update {
                        it.copy(
                            errorMessage = "此设置版本暂不能保存，草稿仍保留。"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun requestClose() {
        val state = mutableState.value
        if (state.isSaving) return
        if (state.isDirty || state.isImporting) mutableState.update {
            it.copy(
                showDiscardConfirmation = true
            )
        }
        else if (imported.isNotEmpty()) discard(reload = false)
        else mutableState.update { it.copy(closeRequested = true) }
    }

    fun dismissDiscard() = mutableState.update { it.copy(showDiscardConfirmation = false) }

    fun discardAndClose() = discard(reload = false)
    fun reloadDiscardingDraft() = discard(reload = true)

    private fun discard(reload: Boolean) {
        if (mutableState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                importJob?.cancelAndJoin()
                repository.discardImports(imported.toSet())
                imported.clear()
                val current = latest ?: base ?: return@launch
                base = current
                mutableState.update {
                    it.copy(
                        draft = current.visuals,
                        isDirty = false,
                        hasConflict = false,
                        showDiscardConfirmation = false,
                        closeRequested = !reload,
                        errorMessage = null
                    )
                }
                persist()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun persist() {
        savedState[OWNER] = "$assistantId:$scenarioId"
        savedState[BASE] = base
        savedState[DRAFT] = mutableState.value.draft
        savedState[IMPORTS] = ArrayList(imported)
    }

    private fun fail(failure: Exception) = mutableState.update {
        it.copy(
            isLoading = false, errorMessage =
                SensitiveTextRedactor.throwableMessageForUi(
                    failure,
                    "图片或设置处理失败，草稿仍保留。"
                )
        )
    }

    companion object {
        private const val OWNER = "companion_visual_owner"
        private const val BASE = "companion_visual_base"
        private const val DRAFT = "companion_visual_draft"
        private const val IMPORTS = "companion_visual_imports"
        fun factory(
            assistantId: String,
            scenarioId: String,
            settings: SettingsStore,
            repository: CompanionResourcesRepository,
            savedState: SavedStateHandle
        ) = typedViewModelFactory {
            CompanionVisualSettingsViewModel(
                assistantId,
                scenarioId,
                settings,
                repository,
                savedState
            )
        }
    }
}
