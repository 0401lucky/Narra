package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.model.*
import kotlinx.coroutines.flow.MutableStateFlow

/** 仅合成设置与可观测动作；没有 Provider/TTS 网络实现。 */
internal class CompanionVoiceUiSettingsStore(
    initial: AppSettings = companionD4Settings().let {
        it.copy(
            companionCharacterSettings = mapOf(
                COMPANION_D4_CHARACTER_ID to CompanionCharacterSettings(
                    voice = CompanionVoicePreferences(enabled = true),
                )
            )
        )
    },
) : SettingsStore {
    override val settingsFlow = MutableStateFlow(initial)
    val writes = mutableListOf<CompanionSettingsPatch>()
    override suspend fun updateCompanionSettings(
        assistantId: String,
        expectedRevision: Long,
        patch: CompanionSettingsPatch
    ): CompanionSettingsWriteResult {
        writes += patch
        val current = settingsFlow.value.companionSettings(assistantId)
        if (current.revision != expectedRevision) return CompanionSettingsWriteResult.Conflict
        val saved =
            current.copy(revision = expectedRevision + 1, voice = patch.voice ?: current.voice)
        settingsFlow.value =
            settingsFlow.value.copy(companionCharacterSettings = settingsFlow.value.companionCharacterSettings + (assistantId to saved))
        return CompanionSettingsWriteResult.Saved(saved)
    }

    private fun unexpected(): Nothing = error("声音UI测试不应写其它设置")
    override suspend fun saveSettings(baseUrl: String, apiKey: String, selectedModel: String) =
        unexpected()

    override suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String
    ) = unexpected()

    override suspend fun saveFunctionModelProviderIds(functionModelProviderIds: FunctionModelProviderIds) =
        unexpected()

    override suspend fun saveDisplaySettings(
        themeMode: ThemeMode,
        appColorTheme: AppColorTheme,
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
    ) = unexpected()

    override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) =
        unexpected()

    override suspend fun saveSearchSettings(settings: SearchSettings) = unexpected()
    override suspend fun saveVoiceSynthesisSettings(settings: VoiceSynthesisSettings) = unexpected()
    override suspend fun saveVoiceRecognitionSettings(settings: VoiceRecognitionSettings) =
        unexpected()

    override suspend fun saveMomentsSettings(settings: MomentsSettings) = unexpected()
    override suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String
    ) = unexpected()

    override suspend fun saveUserPersonaMasks(masks: List<UserPersonaMask>, defaultMaskId: String) =
        unexpected()

    override suspend fun saveAssistants(assistants: List<Assistant>, selectedAssistantId: String) =
        unexpected()

    override suspend fun saveDefaultPresetId(presetId: String) = unexpected()
    override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) =
        unexpected()

    override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) =
        unexpected()

    override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = unexpected()
    override suspend fun saveMemoryRecallTokenBudget(tokenBudget: Int) = unexpected()
    override suspend fun saveMemoryResidentLimits(limits: MemoryResidentLimits) = unexpected()
    override suspend fun saveMemoryPromptSettings(
        extractionPrompt: String,
        injectionPrompt: String
    ) = unexpected()

    override suspend fun saveMemoryInjectionPosition(position: MemoryInjectionPosition) =
        unexpected()

    override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = unexpected()
}

internal class CompanionVoiceUiSession : CompanionVoiceSession {
    override val state = MutableStateFlow(CompanionVoiceState())
    val plays = mutableListOf<CompanionSpeechSelection>()
    val generates = mutableListOf<CompanionSpeechSelection>()
    val favoritePlays = mutableListOf<String>()
    val contexts = mutableListOf<CompanionVoiceOwner>()
    val automatic = mutableListOf<Pair<List<CompanionSpeechUnit>, Boolean>>()
    var favorites = 0
    var retries = 0
    var originalReads = 0
    var stops = 0
    var savedAudio: CompanionVoiceAudio? = null
    override fun updateContext(
        owner: CompanionVoiceOwner,
        settings: AppSettings,
        units: List<CompanionSpeechUnit>
    ) {
        contexts += owner
    }

    override fun select(selection: CompanionSpeechSelection?) {
        if (selection != state.value.selection) state.value =
            state.value.copy(selection = selection, currentAudio = null)
    }

    override fun playSelection(
        selection: CompanionSpeechSelection,
        settings: AppSettings,
        automatic: Boolean
    ) {
        plays += selection
        state.value = CompanionVoiceState(
            phase = CompanionVoicePhase.PREPARING,
            selection = selection,
            total = selection.ranges.size,
            originalText = selection.text
        )
    }

    override fun offerAutomatic(units: List<CompanionSpeechUnit>, settings: AppSettings) {
        val id = units.firstOrNull()?.source?.characterId ?: COMPANION_D4_CHARACTER_ID
        automatic += units to settings.companionSettings(id).voice.autoPlay
    }

    override fun generateAndFavorite(selection: CompanionSpeechSelection, settings: AppSettings) {
        generates += selection
    }

    override fun favoriteCurrent() {
        favorites++
    }

    override fun playFavorite(favoriteId: String) {
        favoritePlays += favoriteId
        state.value = CompanionVoiceState(
            phase = CompanionVoicePhase.PLAYING,
            currentAudio = savedAudio,
            originalText = savedAudio?.clip?.segment?.originalText.orEmpty(),
            spokenText = savedAudio?.clip?.spokenText.orEmpty(),
            languageTag = savedAudio?.clip?.languageTag.orEmpty()
        )
    }

    override fun retry() {
        retries++
    }

    override fun readOriginal() {
        originalReads++
    }

    override fun regenerateExplicitly() = Unit
    override fun stop() {
        stops++; state.value = state.value.copy(phase = CompanionVoicePhase.IDLE)
    }

    override fun close() = stop()
}

internal class CompanionVoiceUiRepository(initialFavorites: List<CompanionVoiceFavorite> = emptyList()) :
    CompanionVoiceRepository {
    val favorites = MutableStateFlow(initialFavorites)
    val values = MutableStateFlow(
        CompanionVoiceCacheUsage(
            cacheBytes = 4096,
            cacheCount = 1,
            favoriteBytes = initialFavorites.sumOf { it.audio.receipt.sizeBytes },
            favoriteCount = initialFavorites.size
        )
    )
    var clears = 0
    val removed = mutableListOf<String>()
    override fun observeFavorites(characterId: String) = favorites
    override fun observeUsage() = values
    override suspend fun usage() = values.value
    override suspend fun clearCache(): CompanionVoiceCacheUsage {
        clears++; return values.value.copy(cacheBytes = 0, cacheCount = 0)
            .also { values.value = it }
    }

    override suspend fun trimCache(maxBytes: Long) = values.value
    override suspend fun unfavorite(favoriteId: String) {
        removed += favoriteId; favorites.value = favorites.value.filterNot { it.id == favoriteId }
    }

    override suspend fun isSourceCurrent(source: CompanionReplySource) = true
    override fun observeSourceChanges() = MutableStateFlow(Unit)
    override suspend fun lookup(key: SpeechRequestKey, ownerToken: String): CompanionAudioReceipt? =
        error("UI不应查询TTS缓存")

    override suspend fun claim(
        key: SpeechRequestKey,
        ownerToken: String,
        explicitRegeneration: Boolean
    ): CompanionAudioClaimResult = error("UI不应claim")

    override suspend fun publish(
        claim: CompanionAudioClaim,
        b64Audio: String,
        clip: CompanionVoiceClip,
        isCurrent: suspend () -> Boolean
    ): CompanionVoiceAudio = error("UI不应发布音频")

    override suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio =
        error("补存由Session负责")

    override suspend fun recoverPending() = emptyList<CompanionVoiceAudio>()
    override suspend fun releaseOwner(ownerToken: String) = Unit
    override suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite =
        error("收藏由Session负责")

    override suspend fun favoriteAudio(
        favoriteId: String,
        ownerToken: String
    ): CompanionVoiceAudio? = error("播放由Session负责")
}

internal fun companionVoiceUiAudio(unit: CompanionSpeechUnit): CompanionVoiceAudio =
    CompanionVoiceAudio(
        CompanionAudioReceipt(
            "synthetic-audio",
            SpeechRequestKey("a".repeat(64)),
            "/synthetic/voice.wav",
            "audio/wav",
            "b".repeat(64),
            16044,
            1,
            CompanionAudioAttachmentState.ATTACHED
        ),
        CompanionVoiceClip(
            CompanionVoiceSegment(
                unit,
                CompanionSpeechRange(unit.id, 0, unit.text.length),
                unit.text
            ), unit.text,
            "简体中文", "简体中文",
            CompanionSpeechSynthesisIdentity(
                "https://synthetic.example/tts",
                MIMO_TTS_MODEL_PRESET,
                unit.source.characterId,
                VoiceProfileMode.PRESET,
                mapOf("format" to "wav", "voiceId" to "Mia")
            ),
        ),
    )
