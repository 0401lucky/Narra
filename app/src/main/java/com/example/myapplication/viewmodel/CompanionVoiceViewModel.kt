package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanionVoiceUiState(
    val owner: CompanionVoiceOwner,
    val characterName: String = "",
    val characterAvailable: Boolean = true,
    val configurationSupported: Boolean = true,
    val isLoading: Boolean = true,
    val preferences: CompanionVoicePreferences = CompanionVoicePreferences(),
    val draft: CompanionVoicePreferences = CompanionVoicePreferences(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val hasConflict: Boolean = false,
    val settingsError: String? = null,
    val libraryError: String? = null,
    val isClearingCache: Boolean = false,
    val units: List<CompanionSpeechUnit> = emptyList(),
    val selection: CompanionSpeechSelection? = null,
    val voice: CompanionVoiceState = CompanionVoiceState(),
    val favorites: List<CompanionVoiceFavorite> = emptyList(),
    val usage: CompanionVoiceCacheUsage = CompanionVoiceCacheUsage(),
) {
    val editable: Boolean get() = !isLoading && !isSaving && characterAvailable && configurationSupported && draft.schemaVersion == 1
}

/** 页面只提供授权选区与显示 owner；请求、队列及播放器由唯一 Session 持有。 */
class CompanionVoiceViewModel(
    initialOwner: CompanionVoiceOwner,
    private val settingsStore: SettingsStore,
    private val repository: CompanionVoiceRepository,
    sessionFactory: (CoroutineScope) -> CompanionVoiceSession,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val characterId = initialOwner.characterId
    private val mutableState = MutableStateFlow(CompanionVoiceUiState(initialOwner))
    val uiState = mutableState.asStateFlow()
    private val session = sessionFactory(viewModelScope)
    private var settings = AppSettings()
    private var latestCharacter = CompanionCharacterSettings()
    private var baseVoice = CompanionVoicePreferences()
    private var baseRevision = 0L
    private var hostActive = false
    private var automaticUnits: List<CompanionSpeechUnit> = emptyList()
    private var hasBoundContent = false

    init {
        viewModelScope.launch {
            session.state.collect { value -> mutableState.update { it.copy(voice = value) } }
        }
        viewModelScope.launch {
            try {
                settingsStore.settingsFlow.collect { value ->
                    settings = value
                    val current = value.companionSettings(characterId)
                    latestCharacter = current
                    val character = value.resolvedAssistants().firstOrNull { it.id == characterId }
                    if (mutableState.value.isLoading) {
                        val restored = savedState.get<String>(CHARACTER_KEY) == characterId
                        baseRevision = if (restored) savedState[REVISION_KEY]
                            ?: current.revision else current.revision
                        baseVoice =
                            if (restored) savedState[BASE_KEY] ?: current.voice else current.voice
                        val draft =
                            if (restored) savedState.get<CompanionVoicePreferences>(DRAFT_KEY)
                                ?: current.voice else current.voice
                        mutableState.update { it.copy(isLoading = false, draft = draft) }
                    } else if (!mutableState.value.isDirty && !mutableState.value.isSaving) {
                        baseRevision = current.revision
                        baseVoice = current.voice
                        mutableState.update { it.copy(draft = current.voice) }
                    }
                    mutableState.update {
                        it.copy(
                            characterName = character?.name.orEmpty(),
                            characterAvailable = character != null,
                            configurationSupported = current.schemaVersion == 1 && current.voice.schemaVersion == 1,
                            preferences = current.voice,
                            isDirty = it.draft != baseVoice,
                            hasConflict = current.revision != baseRevision,
                            settingsError = if (character == null) "角色已不可用，声音草稿和收藏仍保留。" else it.settingsError,
                        )
                    }
                    session.updateContext(
                        mutableState.value.owner,
                        settings,
                        mutableState.value.units
                    )
                    if (character == null) session.stop()
                    persistDraft()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        settingsError = safeError(failure, "声音设置暂不可读，请重试。")
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                repository.observeFavorites(characterId).collect { values ->
                    mutableState.update { it.copy(favorites = values) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        libraryError = safeError(
                            failure,
                            "声音收藏暂不可读。"
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                repository.observeUsage()
                    .collect { value -> mutableState.update { it.copy(usage = value) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        libraryError = safeError(
                            failure,
                            "音频用量暂不可读。"
                        )
                    )
                }
            }
        }
    }

    fun bind(
        owner: CompanionVoiceOwner,
        units: List<CompanionSpeechUnit>,
        automaticCandidates: List<CompanionSpeechUnit> = emptyList(),
    ) {
        if (owner.characterId != characterId) {
            session.stop()
            return
        }
        val previous = mutableState.value
        val firstBind = !hasBoundContent
        hasBoundContent = true
        val changedOwner = previous.owner != owner
        val selection = previous.selection?.takeIf { CompanionSpeechSupport.isCurrent(it, units) }
        mutableState.update { it.copy(owner = owner, units = units, selection = selection) }
        session.updateContext(owner, settings, units)
        if (selection != previous.selection) session.select(selection)
        automaticUnits = automaticCandidates
        if (hostActive && !changedOwner && !firstBind) session.offerAutomatic(
            automaticCandidates,
            settings
        )
        else if (hostActive) rebaseAutomatic()
    }

    /** 只允许 RESUMED 宿主启动声音；后台回填仅更新内容，不发出自动播请求。 */
    fun setHostActive(active: Boolean) {
        if (active == hostActive) return
        hostActive = active
        if (!active) session.stop()
        else {
            session.updateContext(mutableState.value.owner, settings, mutableState.value.units)
            rebaseAutomatic()
        }
    }

    private fun rebaseAutomatic() {
        if (!hostActive) return
        val current = settings.companionSettings(characterId)
        session.offerAutomatic(
            automaticUnits,
            settings.copy(
                companionCharacterSettings = settings.companionCharacterSettings +
                        (characterId to current.copy(voice = current.voice.copy(autoPlay = false)))
            ),
        )
    }

    fun select(selection: CompanionSpeechSelection?) {
        val valid =
            selection?.takeIf { CompanionSpeechSupport.isCurrent(it, mutableState.value.units) }
        mutableState.update { it.copy(selection = valid) }
        session.select(valid)
    }

    fun playSelection(selection: CompanionSpeechSelection) {
        if (!hostActive) return
        select(selection)
        mutableState.value.selection?.let { session.playSelection(it, settings) }
    }

    fun playSelected() = mutableState.value.selection?.let(::playSelection) ?: Unit

    fun generateAndFavorite(selection: CompanionSpeechSelection) {
        if (!hostActive) return
        select(selection)
        mutableState.value.selection?.let { session.generateAndFavorite(it, settings) }
    }

    fun favoriteCurrent() {
        if (hostActive) session.favoriteCurrent()
    }

    fun playFavorite(id: String) {
        if (hostActive) session.playFavorite(id)
    }

    fun retry() {
        if (hostActive) session.retry()
    }

    fun readOriginal() {
        if (hostActive) session.readOriginal()
    }

    fun regenerateExplicitly() {
        if (hostActive) session.regenerateExplicitly()
    }

    fun stop() = session.stop()

    fun updateDraft(draft: CompanionVoicePreferences) {
        if (!mutableState.value.editable) return
        session.stop()
        mutableState.update {
            it.copy(
                draft = draft,
                isDirty = draft != baseVoice,
                settingsError = null
            )
        }
        persistDraft()
    }

    fun savePreferences() {
        val state = mutableState.value
        if (!state.editable || !state.isDirty) return
        val draft = state.draft
        if (draft.sourceLanguage.isBlank() || draft.targetLanguage.isBlank() ||
            draft.sourceLanguage.length > CompanionVoicePreferences.MAX_LANGUAGE_LENGTH ||
            draft.targetLanguage.length > CompanionVoicePreferences.MAX_LANGUAGE_LENGTH
        ) {
            mutableState.update { it.copy(settingsError = "请填写不超过 ${CompanionVoicePreferences.MAX_LANGUAGE_LENGTH} 字的原文和朗读语言。") }
            return
        }
        val expectedRevision = baseRevision
        mutableState.update { it.copy(isSaving = true, settingsError = null) }
        viewModelScope.launch {
            try {
                when (val result = settingsStore.updateCompanionSettings(
                    characterId,
                    expectedRevision,
                    CompanionSettingsPatch(voice = draft.normalized()),
                )) {
                    is CompanionSettingsWriteResult.Saved -> {
                        baseRevision = result.settings.revision
                        baseVoice = result.settings.voice
                        latestCharacter = result.settings
                        mutableState.update {
                            it.copy(
                                draft = baseVoice,
                                preferences = baseVoice,
                                isDirty = false,
                                hasConflict = false
                            )
                        }
                        persistDraft()
                        try {
                            repository.trimCache(baseVoice.cacheLimitBytes)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            mutableState.update {
                                it.copy(
                                    libraryError = safeError(
                                        failure,
                                        "声音偏好已保存，音频缓存回收未完成。"
                                    )
                                )
                            }
                        }
                    }

                    CompanionSettingsWriteResult.Conflict -> mutableState.update {
                        it.copy(
                            hasConflict = true,
                            settingsError = "设置已在别处更新，草稿保留。可放弃草稿并读取最新设置。"
                        )
                    }

                    CompanionSettingsWriteResult.UnsupportedVersion -> mutableState.update {
                        it.copy(settingsError = "声音设置版本较新，未覆盖已有设置，草稿保留。")
                    }

                    CompanionSettingsWriteResult.CharacterMissing -> mutableState.update {
                        it.copy(
                            characterAvailable = false,
                            settingsError = "角色已不存在，未保存，草稿保留。"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        settingsError = safeError(
                            failure,
                            "声音设置保存未完成，草稿保留。"
                        )
                    )
                }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun reloadDiscardingDraft() {
        if (mutableState.value.isSaving) return
        baseRevision = latestCharacter.revision
        baseVoice = latestCharacter.voice
        mutableState.update {
            it.copy(
                draft = baseVoice,
                isDirty = false,
                hasConflict = false,
                settingsError = null
            )
        }
        persistDraft()
    }

    fun clearCache() {
        if (mutableState.value.isClearingCache) return
        mutableState.update { it.copy(isClearingCache = true, libraryError = null) }
        viewModelScope.launch {
            try {
                val usage = repository.clearCache()
                mutableState.update { it.copy(usage = usage) }
                session.select(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        libraryError = safeError(
                            failure,
                            "缓存清理未完成，收藏保留。"
                        )
                    )
                }
            } finally {
                mutableState.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun unfavorite(id: String) {
        viewModelScope.launch {
            try {
                repository.unfavorite(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        libraryError = safeError(
                            failure,
                            "取消收藏未完成，请重试。"
                        )
                    )
                }
            }
        }
    }

    private fun persistDraft() {
        savedState[CHARACTER_KEY] = characterId
        savedState[REVISION_KEY] = baseRevision
        savedState[BASE_KEY] = baseVoice
        savedState[DRAFT_KEY] = mutableState.value.draft
    }

    override fun onCleared() {
        hostActive = false
        session.close()
    }

    private fun safeError(failure: Exception, fallback: String) =
        SensitiveTextRedactor.throwableMessageForUi(failure, fallback)

    companion object {
        private const val CHARACTER_KEY = "companion.voice.character"
        private const val REVISION_KEY = "companion.voice.revision"
        private const val BASE_KEY = "companion.voice.base"
        private const val DRAFT_KEY = "companion.voice.draft"

        fun factory(
            owner: CompanionVoiceOwner,
            settingsStore: SettingsStore,
            repository: CompanionVoiceRepository,
            sessionFactory: (CoroutineScope) -> CompanionVoiceSession,
            savedState: SavedStateHandle,
        ) = typedViewModelFactory {
            CompanionVoiceViewModel(owner, settingsStore, repository, sessionFactory, savedState)
        }
    }
}
