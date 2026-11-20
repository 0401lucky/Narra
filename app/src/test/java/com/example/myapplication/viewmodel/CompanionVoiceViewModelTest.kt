package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionVoiceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionVoiceViewModel>()
    private val owner = CompanionVoiceOwner("scene", "alice", "STAGE")

    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun inactiveHost_andResumeBaseline_neverOfferEnabledAutomaticOrPlay() =
        runTest(main.dispatcher.scheduler) {
            val store = Store(initial(CompanionVoicePreferences(enabled = true, autoPlay = true)))
            val session = Session()
            val model = model(store, session)
            advanceUntilIdle()
            val first = unit("first")
            model.bind(owner, listOf(first), listOf(first))
            assertTrue(session.automatic.isEmpty())
            model.setHostActive(true)
            assertEquals(1, session.automatic.size)
            assertFalse(session.automatic.last().second.companionSettings("alice").voice.autoPlay)
            model.setHostActive(false)
            val calls = session.automatic.size
            val second = unit("second")
            model.bind(owner, listOf(first, second), listOf(second))
            model.playSelection(
                requireNotNull(
                    CompanionSpeechSupport.select(
                        listOf(second),
                        second.id
                    )
                )
            )
            assertEquals(calls, session.automatic.size)
            assertTrue(session.plays.isEmpty())
            model.setHostActive(true)
            assertFalse(session.automatic.last().second.companionSettings("alice").voice.autoPlay)
            assertEquals(listOf(second), session.automatic.last().first)
            assertTrue(session.stops > 0)
        }

    @Test
    fun activeBeforeFirstBind_keepsHistoricalCandidatesSilentThenAllowsNewReply() =
        runTest(main.dispatcher.scheduler) {
            val session = Session()
            val model = model(
                Store(initial(CompanionVoicePreferences(enabled = true, autoPlay = true))),
                session
            )
            advanceUntilIdle()
            model.setHostActive(true)
            val historical = unit("historical")
            model.bind(owner, listOf(historical), listOf(historical))
            assertFalse(session.automatic.last().second.companionSettings("alice").voice.autoPlay)
            val fresh = unit("fresh")
            model.bind(owner, listOf(historical, fresh), listOf(fresh))
            assertTrue(session.automatic.last().second.companionSettings("alice").voice.autoPlay)
            assertEquals(listOf(fresh), session.automatic.last().first)
            assertTrue(session.plays.isEmpty())
        }

    @Test
    fun crossSceneNarrationSelection_preservesFullSnapshotAndDisplayRange() =
        runTest(main.dispatcher.scheduler) {
            val session = Session()
            val model = model(Store(initial()), session)
            advanceUntilIdle()
            val old = unit("old", "old-conversation", RoleplayContentType.NARRATION)
            val current = unit("current")
            val units = listOf(old, current)
            val selection =
                requireNotNull(CompanionSpeechSupport.select(units, old.id, current.id, start = 1))
            model.bind(owner, units)
            model.setHostActive(true)
            model.playSelection(selection)
            assertEquals(selection, session.plays.single())
            assertEquals(old.rawSpan, session.plays.single().units.first().rawSpan)
            assertEquals("旧场旁白", selection.text.substringBefore("\n\n"))
            assertEquals(
                listOf("old-conversation", "conversation"),
                selection.units.map { it.source.conversationId })
            assertTrue(session.generates.isEmpty())
        }

    @Test
    fun saveVoicePatch_usesBaseRevisionAndKeepsOldBackgroundVoiceDisabled() =
        runTest(main.dispatcher.scheduler) {
            val store = Store(initial())
            val repository = Repository()
            val model = model(store, Session(), repository)
            advanceUntilIdle()
            model.updateDraft(
                model.uiState.value.draft.copy(
                    enabled = true,
                    autoPlay = true,
                    readMode = CompanionVoiceReadMode.TRANSLATED,
                    targetLanguage = "日语"
                )
            )
            model.savePreferences()
            advanceUntilIdle()
            val call = store.writes.single()
            assertEquals(7L, call.second)
            assertNotNull(call.third.voice)
            assertNull(call.third.writing)
            assertNull(call.third.visuals)
            assertNull(call.third.zoneId)
            assertFalse(store.settingsFlow.value.voiceSynthesisSettings.enabled)
            assertEquals(
                "日语",
                store.settingsFlow.value.companionSettings("alice").voice.targetLanguage
            )
            assertEquals(
                listOf(CompanionVoicePreferences.DEFAULT_CACHE_LIMIT_BYTES),
                repository.trims
            )
            assertFalse(model.uiState.value.isDirty)
        }

    @Test
    fun conflictAndRestoredDraft_doNotOverwriteLatestPreferences() =
        runTest(main.dispatcher.scheduler) {
            val store = Store(initial())
            val saved = SavedStateHandle()
            val model = model(store, Session(), savedState = saved)
            advanceUntilIdle()
            val draft = model.uiState.value.draft.copy(enabled = true, targetLanguage = "法语")
            model.updateDraft(draft)
            val current = store.settingsFlow.value.companionSettings("alice")
            store.settingsFlow.value = store.settingsFlow.value.copy(
                companionCharacterSettings = mapOf(
                    "alice" to current.copy(
                        revision = 8,
                        voice = current.voice.copy(targetLanguage = "韩语")
                    )
                )
            )
            advanceUntilIdle()
            model.savePreferences()
            advanceUntilIdle()
            assertTrue(model.uiState.value.hasConflict)
            assertEquals(draft, model.uiState.value.draft)
            assertEquals(
                "韩语",
                store.settingsFlow.value.companionSettings("alice").voice.targetLanguage
            )
            val restored =
                this@CompanionVoiceViewModelTest.model(store, Session(), savedState = saved)
            advanceUntilIdle()
            assertEquals(draft, restored.uiState.value.draft)
            restored.reloadDiscardingDraft()
            assertEquals("韩语", restored.uiState.value.draft.targetLanguage)
            assertFalse(restored.uiState.value.isDirty)
        }

    @Test
    fun revisedSource_clearsOldSelectionAndPassesNewModeOwner() =
        runTest(main.dispatcher.scheduler) {
            val session = Session()
            val model = model(Store(initial()), session)
            advanceUntilIdle()
            val old = unit("source")
            model.bind(owner, listOf(old))
            model.select(CompanionSpeechSupport.select(listOf(old), old.id))
            val revised = old.copy(source = old.source.copy(revision = 2), text = "新正文")
            model.bind(owner.copy(mode = "READER"), listOf(revised))
            assertNull(model.uiState.value.selection)
            assertNull(session.selections.last())
            assertEquals("READER", session.contexts.last().first.mode)
            assertTrue(session.plays.isEmpty())
        }

    @Test
    fun futureConfigurationAndDeletedCharacter_preserveDraftAndStopVoice() =
        runTest(main.dispatcher.scheduler) {
            val store = Store(initial().let {
                it.copy(
                    companionCharacterSettings = mapOf(
                        "alice" to it.companionSettings("alice").copy(schemaVersion = 9)
                    )
                )
            })
            val session = Session()
            val model = model(store, session)
            advanceUntilIdle()
            assertFalse(model.uiState.value.editable)
            model.updateDraft(CompanionVoicePreferences(enabled = true))
            model.savePreferences()
            assertTrue(store.writes.isEmpty())
            store.settingsFlow.value = store.settingsFlow.value.copy(
                assistants = listOf(Assistant(id = "other")),
                selectedAssistantId = "other"
            )
            advanceUntilIdle()
            assertFalse(model.uiState.value.characterAvailable)
            assertTrue(session.stops > 0)
            assertFalse(model.uiState.value.draft.enabled)
        }

    @Test
    fun clearFailure_keepsLibraryUsageAndDoesNotGenerate() = runTest(main.dispatcher.scheduler) {
        val repository = Repository().apply { clearFailure = IllegalStateException("cache failed") }
        val session = Session()
        val model = model(Store(initial()), session, repository)
        advanceUntilIdle()
        val before = model.uiState.value.usage
        model.clearCache()
        advanceUntilIdle()
        assertEquals(before, model.uiState.value.usage)
        assertNotNull(model.uiState.value.libraryError)
        assertFalse(model.uiState.value.isClearingCache)
        assertTrue(session.plays.isEmpty())
        assertTrue(session.generates.isEmpty())
    }

    @Test
    fun clearSuccess_invalidatesOrdinaryAudioButKeepsUserSelectionForExplicitReplay() =
        runTest(main.dispatcher.scheduler) {
            val source = unit("saved")
            val selection = requireNotNull(CompanionSpeechSupport.select(listOf(source), source.id))
            val audio = CompanionVoiceAudio(
                CompanionAudioReceipt(
                    "saved-audio",
                    SpeechRequestKey("a".repeat(64)),
                    "/synthetic/voice.wav",
                    "audio/wav",
                    "b".repeat(64),
                    1024,
                    1,
                    CompanionAudioAttachmentState.ATTACHED
                ),
                CompanionVoiceClip(
                    CompanionVoiceSegment(source, selection.ranges.single(), source.text),
                    source.text,
                    "简体中文",
                    "简体中文",
                    CompanionSpeechSynthesisIdentity(
                        "https://synthetic.example/tts", MIMO_TTS_MODEL_PRESET,
                        "alice", VoiceProfileMode.PRESET, mapOf("format" to "wav")
                    )
                ),
            )
            val session = Session().apply {
                state.value = CompanionVoiceState(selection = selection, currentAudio = audio)
            }
            val model = model(Store(initial()), session)
            advanceUntilIdle()
            model.bind(owner, listOf(source))
            model.select(selection)
            assertNotNull(model.uiState.value.voice.currentAudio)
            model.clearCache()
            advanceUntilIdle()
            assertNull(session.selections.last())
            assertNull(model.uiState.value.voice.currentAudio)
            assertEquals(selection, model.uiState.value.selection)
            assertTrue(session.generates.isEmpty())
            assertTrue(session.plays.isEmpty())
        }

    private fun model(
        store: Store,
        session: Session,
        repository: Repository = Repository(),
        savedState: SavedStateHandle = SavedStateHandle()
    ) =
        CompanionVoiceViewModel(owner, store, repository, { session }, savedState).also(models::add)

    private fun initial(voice: CompanionVoicePreferences = CompanionVoicePreferences()) =
        AppSettings(
            assistants = listOf(Assistant(id = "alice", name = "合成角色")),
            selectedAssistantId = "alice",
            companionCharacterSettings = mapOf(
                "alice" to CompanionCharacterSettings(
                    revision = 7,
                    voice = voice
                )
            ),
            voiceSynthesisSettings = VoiceSynthesisSettings(enabled = false),
        )

    private fun unit(
        id: String,
        conversation: String = "conversation",
        type: RoleplayContentType = RoleplayContentType.DIALOGUE
    ) = CompanionSpeechUnit(
        id,
        CompanionReplySource(
            id,
            conversation,
            "turn-$id",
            1,
            1,
            1,
            "hash-$id",
            "attempt-$id",
            characterId = "alice"
        ),
        "beat-$id",
        "甲旧场旁白",
        CompanionRawSpan(40, 65),
        RoleplaySpeaker.CHARACTER,
        "合成角色",
        null,
        type,
        "<narration>甲旧场旁白</narration>",
    )

    private class Store(initial: AppSettings) : SettingsStore by FakeSettingsStore(initial) {
        override val settingsFlow = MutableStateFlow(initial)
        val writes = mutableListOf<Triple<String, Long, CompanionSettingsPatch>>()
        override suspend fun updateCompanionSettings(
            assistantId: String,
            expectedRevision: Long,
            patch: CompanionSettingsPatch
        ): CompanionSettingsWriteResult {
            writes += Triple(assistantId, expectedRevision, patch)
            val current = settingsFlow.value.companionSettings(assistantId)
            if (current.revision != expectedRevision) return CompanionSettingsWriteResult.Conflict
            val saved =
                current.copy(revision = expectedRevision + 1, voice = patch.voice ?: current.voice)
            settingsFlow.value =
                settingsFlow.value.copy(companionCharacterSettings = settingsFlow.value.companionCharacterSettings + (assistantId to saved))
            return CompanionSettingsWriteResult.Saved(saved)
        }
    }

    private class Session : CompanionVoiceSession {
        override val state = MutableStateFlow(CompanionVoiceState())
        val contexts =
            mutableListOf<Triple<CompanionVoiceOwner, AppSettings, List<CompanionSpeechUnit>>>()
        val automatic = mutableListOf<Pair<List<CompanionSpeechUnit>, AppSettings>>()
        val selections = mutableListOf<CompanionSpeechSelection?>()
        val plays = mutableListOf<CompanionSpeechSelection>()
        val generates = mutableListOf<CompanionSpeechSelection>()
        var stops = 0
        override fun updateContext(
            owner: CompanionVoiceOwner,
            settings: AppSettings,
            units: List<CompanionSpeechUnit>
        ) {
            contexts += Triple(owner, settings, units)
        }

        override fun select(selection: CompanionSpeechSelection?) {
            selections += selection
            if (selection != state.value.selection) state.value =
                state.value.copy(selection = selection, currentAudio = null)
        }

        override fun playSelection(
            selection: CompanionSpeechSelection,
            settings: AppSettings,
            automatic: Boolean
        ) {
            plays += selection
        }

        override fun offerAutomatic(units: List<CompanionSpeechUnit>, settings: AppSettings) {
            automatic += units to settings
        }

        override fun generateAndFavorite(
            selection: CompanionSpeechSelection,
            settings: AppSettings
        ) {
            generates += selection
        }

        override fun favoriteCurrent() = Unit
        override fun playFavorite(favoriteId: String) = Unit
        override fun retry() = Unit
        override fun readOriginal() = Unit
        override fun regenerateExplicitly() = Unit
        override fun stop() {
            stops++
        }

        override fun close() = Unit
    }

    private class Repository : CompanionVoiceRepository {
        private val values = MutableStateFlow(
            CompanionVoiceCacheUsage(
                cacheBytes = 1024,
                cacheCount = 1,
                favoriteBytes = 2048,
                favoriteCount = 1
            )
        )
        val trims = mutableListOf<Long>()
        var clearFailure: Exception? = null
        override fun observeFavorites(characterId: String) =
            MutableStateFlow(emptyList<CompanionVoiceFavorite>())

        override fun observeUsage() = values
        override suspend fun usage() = values.value
        override suspend fun clearCache(): CompanionVoiceCacheUsage {
            clearFailure?.let { throw it }; return values.value.copy(cacheBytes = 0, cacheCount = 0)
                .also { values.value = it }
        }

        override suspend fun trimCache(maxBytes: Long): CompanionVoiceCacheUsage {
            trims += maxBytes; return values.value
        }

        override suspend fun unfavorite(favoriteId: String) = Unit
        override suspend fun isSourceCurrent(source: CompanionReplySource) = true
        override fun observeSourceChanges() = MutableStateFlow(Unit)
        override suspend fun lookup(
            key: SpeechRequestKey,
            ownerToken: String
        ): CompanionAudioReceipt? = error("UI不应直接查询合成缓存")

        override suspend fun claim(
            key: SpeechRequestKey,
            ownerToken: String,
            explicitRegeneration: Boolean
        ): CompanionAudioClaimResult = error("UI不应直接claim")

        override suspend fun publish(
            claim: CompanionAudioClaim,
            b64Audio: String,
            clip: CompanionVoiceClip,
            isCurrent: suspend () -> Boolean
        ): CompanionVoiceAudio = error("UI不应直接发布")

        override suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio =
            error("UI不应直接补保存")

        override suspend fun recoverPending() = emptyList<CompanionVoiceAudio>()
        override suspend fun releaseOwner(ownerToken: String) = Unit
        override suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite =
            error("收藏由Session处理")

        override suspend fun favoriteAudio(
            favoriteId: String,
            ownerToken: String
        ): CompanionVoiceAudio? = error("收藏播放由Session处理")
    }
}
