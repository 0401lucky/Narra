package com.example.myapplication.roleplay.companion

import com.example.myapplication.data.repository.ai.*
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.data.repository.tts.*
import com.example.myapplication.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionVoiceControllerTest {
    @Test
    fun disabledAndMissingConfiguration_neverTranslateOrSynthesize() = runTest {
        val cases = listOf(
            settings().copy(companionCharacterSettings = mapOf("alice" to CompanionCharacterSettings())),
            settings().copy(voiceSynthesisSettings = VoiceSynthesisSettings(apiKey = "")),
            settings().copy(
                voiceSynthesisSettings = VoiceSynthesisSettings(
                    apiKey = "fake-key",
                    assistantProfiles = mapOf("alice" to VoiceProfile(mode = VoiceProfileMode.DISABLED))
                )
            ),
            settings().copy(
                voiceSynthesisSettings = VoiceSynthesisSettings(
                    apiKey = "fake-key",
                    assistantProfiles = mapOf("alice" to VoiceProfile(mode = VoiceProfileMode.VOICE_DESIGN))
                )
            ),
        )
        cases.forEach { value ->
            val f = Fixture(this, value)
            f.play(); runCurrent()
            assertEquals(0, f.ttsTexts.size)
            assertEquals(0, f.translation.calls)
            assertEquals(CompanionVoicePhase.ERROR, f.controller.state.value.phase)
            assertEquals(
                CompanionVoiceFailureStage.CONFIGURATION,
                f.controller.state.value.failureStage
            )
            assertEquals(f.selection, f.controller.state.value.selection)
            f.close(); runCurrent()
        }
    }

    @Test
    fun oldBackgroundSwitchOff_explicitCompanionSpeechStillWorks() = runTest {
        val f = Fixture(this, settings())
        assertFalse(f.settings.voiceSynthesisSettings.enabled)
        f.play(); runCurrent()
        assertEquals(listOf("第一句"), f.ttsTexts)
        assertEquals(CompanionVoicePhase.PREPARING, f.controller.state.value.phase)
        assertEquals(0, f.players.single().startCalls)
        f.players.single().prepared(); runCurrent()
        assertEquals(CompanionVoicePhase.PLAYING, f.controller.state.value.phase)
        f.players.single().completed(); runCurrent()
        assertEquals(CompanionVoicePhase.COMPLETED, f.controller.state.value.phase)
        f.close(); runCurrent()
    }

    @Test
    fun selectedDisplayOffsets_preserveRawSourceAndSpokenText() = runTest {
        val unit = unit("first", "0123456789").copy(
            rawSpan = CompanionRawSpan(123, 456),
            rawText = "完整协议原文"
        )
        val f = Fixture(this, units = listOf(unit))
        f.selection =
            CompanionSpeechSelection(listOf(unit), listOf(CompanionSpeechRange(unit.id, 3, 7)))
        f.play(); runCurrent()
        assertEquals(listOf("3456"), f.ttsTexts)
        val clip = f.controller.state.value.currentAudio!!.clip
        assertEquals("3456", clip.segment.originalText)
        assertEquals(CompanionRawSpan(123, 456), clip.segment.unit.rawSpan)
        assertEquals(CompanionSpeechRange(unit.id, 3, 7), clip.segment.range)
        assertEquals("完整协议原文", clip.segment.unit.rawText)
        f.close(); runCurrent()
    }

    @Test
    fun crossEncounterNarrationQueue_isSerialAndStopDoesNotStartNext() = runTest {
        val units = listOf(
            unit("old", "旧场旁白", RoleplayContentType.NARRATION),
            unit("current", "本场对白"),
            unit("last", "最后一句")
        )
        val f = Fixture(this, units = units)
        f.play(); runCurrent()
        assertEquals(listOf("旧场旁白"), f.ttsTexts)
        f.players[0].prepared(); runCurrent(); f.players[0].completed(); runCurrent()
        assertEquals(listOf("旧场旁白", "本场对白"), f.ttsTexts)
        assertEquals(
            units[1].source,
            f.controller.state.value.currentAudio!!.clip.segment.unit.source
        )
        f.controller.stop(); runCurrent()
        f.players[1].prepared(); f.players[1].completed(); runCurrent()
        assertEquals(0, f.players[1].startCalls)
        assertEquals(2, f.ttsTexts.size)
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.repo.releasedOwners.isNotEmpty())
        f.close(); runCurrent()
    }

    @Test
    fun translationFailure_retryKeepsOriginalAndOriginalFallbackRequiresAction() = runTest {
        val f = Fixture(this, settings(translated = true))
        f.translation.behavior = { throw IllegalStateException("翻译失败") }
        f.play(); runCurrent()
        assertEquals(CompanionVoiceFailureStage.TRANSLATION, f.controller.state.value.failureStage)
        assertEquals("第一句", f.controller.state.value.originalText)
        assertEquals(0, f.ttsTexts.size)
        assertEquals("第一句", f.units.single().text)
        f.translation.behavior = { "English sentence" }
        f.controller.retry(); runCurrent()
        assertEquals(2, f.translation.calls)
        assertEquals(listOf("English sentence"), f.ttsTexts)
        assertEquals("第一句", f.controller.state.value.currentAudio!!.clip.segment.originalText)
        assertEquals("英语", f.controller.state.value.currentAudio!!.clip.languageTag)
        f.close(); runCurrent()
    }

    @Test
    fun explicitReadOriginal_afterTranslationFailureUsesOriginalOnly() = runTest {
        val f = Fixture(this, settings(translated = true))
        f.translation.behavior = { throw IllegalStateException("翻译失败") }
        f.play(); runCurrent()
        assertTrue(f.ttsTexts.isEmpty())
        f.controller.readOriginal(); runCurrent()
        assertEquals(listOf("第一句"), f.ttsTexts)
        assertEquals(1, f.translation.calls)
        assertNull(f.controller.state.value.currentAudio!!.clip.translationKey)
        assertEquals(
            CompanionVoiceReadMode.TRANSLATED,
            f.settings.companionSettings("alice").voice.readMode
        )
        f.close(); runCurrent()
    }

    @Test
    fun lateUncancellableTranslation_afterNewSelectionCannotSynthesize() = runTest {
        val f = Fixture(
            this,
            settings(translated = true),
            listOf(unit("old", "旧句"), unit("new", "新句"))
        )
        val gate = CompletableDeferred<Unit>()
        f.translation.behavior =
            { text -> if (text == "旧句") withContext(NonCancellable) { gate.await(); "late translation" } else "new translation" }
        f.selection = selection(listOf(f.units[0])); f.play(); runCurrent()
        f.selection = selection(listOf(f.units[1])); f.play(); runCurrent()
        assertEquals(listOf("new translation"), f.ttsTexts)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("new translation"), f.ttsTexts)
        assertEquals(1, f.players.size)
        assertEquals("新句", f.controller.state.value.originalText)
        f.close(); runCurrent()
    }

    @Test
    fun lateUncancellableTts_afterLeavingCannotPublishOrPrepare() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        f.ttsBehavior =
            { withContext(NonCancellable) { gate.await(); MimoTtsResult("fake-audio") } }
        f.play(); runCurrent()
        f.controller.close(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(1, f.ttsTexts.size)
        assertEquals(0, f.repo.publicationCalls)
        assertTrue(f.players.isEmpty())
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
    }

    @Test
    fun parentScopeCancellation_invalidatesBeforeUncancellableResponseReturns() = runTest {
        val parent = SupervisorJob()
        val ownedScope = CoroutineScope(parent + StandardTestDispatcher(testScheduler))
        val f = Fixture(this, sessionScope = ownedScope)
        val gate = CompletableDeferred<Unit>()
        f.ttsBehavior =
            { withContext(NonCancellable) { gate.await(); MimoTtsResult("fake-audio") } }
        f.play(); runCurrent()
        parent.cancel(); runCurrent()
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        gate.complete(Unit); runCurrent()
        assertEquals(0, f.repo.publicationCalls)
        assertTrue(f.players.isEmpty())
        parent.join()
    }

    @Test
    fun preparedAfterNewOwnerAndStartFailure_neverReportPlaying() = runTest {
        val f = Fixture(this)
        f.play(); runCurrent()
        val old = f.players.single()
        f.controller.updateContext(
            CompanionVoiceOwner("scene", "alice", "READER"),
            f.settings,
            f.units
        )
        old.prepared(); runCurrent()
        assertEquals(0, old.startCalls)
        assertEquals(1, old.releaseCalls)
        f.play(); runCurrent()
        val current = f.players.last()
        current.startFailure = IllegalStateException("播放启动失败")
        current.prepared(); runCurrent()
        assertEquals(CompanionVoicePhase.ERROR, f.controller.state.value.phase)
        assertEquals(CompanionVoiceFailureStage.PLAYBACK, f.controller.state.value.failureStage)
        assertEquals(1, current.releaseCalls)
        f.close(); runCurrent()
    }

    @Test
    fun synchronousStopDuringStart_oldCallbackCannotRestorePlaying() = runTest {
        val f = Fixture(this)
        f.play(); runCurrent()
        val player = f.players.single()
        player.onStart = f.controller::stop
        player.prepared(); runCurrent()
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        assertEquals(1, player.releaseCalls)
        f.close(); runCurrent()
    }

    @Test
    fun sourceDeletedDuringPrepare_stopsAndRejectsPrepared() = runTest {
        val f = Fixture(this)
        f.play(); runCurrent()
        f.repo.sourcesAvailable = false
        f.repo.changes.emit(Unit); runCurrent()
        f.players.single().prepared(); runCurrent()
        assertEquals(0, f.players.single().startCalls)
        assertEquals(CompanionVoiceFailureStage.SOURCE, f.controller.state.value.failureStage)
        assertNull(f.controller.state.value.currentAudio)
        f.close(); runCurrent()
    }

    @Test
    fun sourceObserverFailure_explicitPlaybackRestoresObservationAndDeletionStopsCachedAudio() =
        runTest {
            val failureGate = CompletableDeferred<Unit>()
            val f = Fixture(this, settings(auto = true), sourceFailureGate = failureGate)
            f.controller.offerAutomatic(f.units, f.settings)
            runCurrent()
            assertEquals(1, f.repo.sourceSubscriptions)
            assertTrue(f.ttsTexts.isEmpty())

            f.play(); runCurrent()
            val firstPlayer = f.players.single()
            firstPlayer.prepared(); runCurrent()
            assertEquals(CompanionVoicePhase.PLAYING, f.controller.state.value.phase)
            assertEquals(1, f.ttsTexts.size)
            failureGate.complete(Unit); runCurrent()
            assertEquals(CompanionVoiceFailureStage.SOURCE, f.controller.state.value.failureStage)
            assertEquals(1, firstPlayer.releaseCalls)
            assertEquals(0, f.repo.changes.subscriptionCount.value)

            f.updateUnits(f.units + unit("new-auto", "监听失效时的新句"))
            f.controller.offerAutomatic(f.units, f.settings); runCurrent()
            assertEquals(1, f.repo.sourceSubscriptions)
            assertEquals(1, f.ttsTexts.size)

            f.play(); runCurrent()
            assertEquals(2, f.repo.sourceSubscriptions)
            assertEquals(1, f.repo.changes.subscriptionCount.value)
            val restoredPlayer = f.players.last()
            restoredPlayer.prepared(); runCurrent()
            assertEquals(CompanionVoicePhase.PLAYING, f.controller.state.value.phase)
            assertEquals(1, f.ttsTexts.size)
            assertEquals(1, f.repo.publicationCalls)
            f.repo.sourcesAvailable = false
            f.repo.changes.emit(Unit); runCurrent()
            assertEquals(CompanionVoicePhase.ERROR, f.controller.state.value.phase)
            assertEquals(CompanionVoiceFailureStage.SOURCE, f.controller.state.value.failureStage)
            assertEquals(1, restoredPlayer.releaseCalls)
            assertNull(f.controller.state.value.currentAudio)
            assertEquals(1, f.ttsTexts.size)
            f.close(); runCurrent()
        }

    @Test
    fun changingVoiceConfiguration_cancelsCurrentOwner() = runTest {
        val f = Fixture(this)
        f.play(); runCurrent()
        val changed = f.settings.copy(
            voiceSynthesisSettings = f.settings.voiceSynthesisSettings.copy(
                defaultProfile = VoiceProfile(
                    mode = VoiceProfileMode.PRESET,
                    presetVoiceId = "Mia"
                )
            )
        )
        f.controller.updateContext(f.owner, changed, f.units)
        f.players.single().prepared(); runCurrent()
        assertEquals(0, f.players.single().startCalls)
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        f.close(); runCurrent()
    }

    @Test
    fun attachmentFailure_retryAndFavoriteUsePersistedAudioWithoutTts() = runTest {
        val f = Fixture(this)
        f.repo.returnPendingAttachment = true
        f.play(); runCurrent()
        assertEquals(CompanionVoiceFailureStage.STORAGE, f.controller.state.value.failureStage)
        assertEquals(
            CompanionAudioAttachmentState.PENDING,
            f.controller.state.value.currentAudio!!.receipt.attachmentState
        )
        assertTrue(f.players.isEmpty())
        f.repo.returnPendingAttachment = false
        f.controller.retry(); runCurrent()
        assertEquals(1, f.ttsTexts.size)
        assertEquals(1, f.repo.publicationCalls)
        assertEquals(1, f.repo.attachmentRetries)
        val recoveredPlayer = f.players.single()
        assertEquals(CompanionVoicePhase.PREPARING, f.controller.state.value.phase)
        recoveredPlayer.prepared(); runCurrent()
        assertEquals(CompanionVoicePhase.PLAYING, f.controller.state.value.phase)
        recoveredPlayer.completed(); runCurrent()
        assertEquals(CompanionVoicePhase.COMPLETED, f.controller.state.value.phase)
        f.controller.favoriteCurrent(); runCurrent()
        assertEquals(1, f.repo.favoriteCalls)
        assertEquals(1, f.ttsTexts.size)

        val stoppedUnit = unit("pending-stop", "停止后保留的音频")
        f.updateUnits(f.units + stoppedUnit)
        f.selection = selection(listOf(stoppedUnit))
        f.repo.returnPendingAttachment = true
        f.play(); runCurrent()
        val pending = f.controller.state.value.currentAudio!!
        assertEquals(CompanionAudioAttachmentState.PENDING, pending.receipt.attachmentState)
        f.controller.stop(); runCurrent()
        assertEquals(pending, f.controller.state.value.currentAudio)
        val savingGate = CompletableDeferred<Unit>()
        f.repo.attachmentGate = savingGate
        f.controller.retry(); runCurrent()
        assertEquals(CompanionVoicePhase.PUBLISHING, f.controller.state.value.phase)
        f.controller.stop(); runCurrent()
        f.repo.attachmentGate = null
        f.controller.retry(); runCurrent()
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        assertEquals(
            CompanionAudioAttachmentState.ATTACHED,
            f.controller.state.value.currentAudio!!.receipt.attachmentState
        )
        assertEquals(
            pending.receipt.audioId,
            f.controller.state.value.currentAudio!!.receipt.audioId
        )
        assertEquals(2, f.ttsTexts.size)
        assertEquals(2, f.repo.publicationCalls)
        assertEquals(0, f.translation.calls)
        assertEquals(1, f.players.size)
        val newSelection = selection(listOf(f.units.first()))
        f.controller.select(newSelection)
        savingGate.complete(Unit); runCurrent()
        assertEquals(newSelection, f.controller.state.value.selection)
        assertNull(f.controller.state.value.currentAudio)
        assertEquals(CompanionVoicePhase.IDLE, f.controller.state.value.phase)
        assertEquals(4, f.repo.attachmentRetries)
        assertEquals(2, f.ttsTexts.size)
        assertEquals(1, f.players.size)
        assertEquals(1, recoveredPlayer.prepareCalls)
        assertEquals(1, recoveredPlayer.startCalls)
        assertEquals(1, recoveredPlayer.releaseCalls)
        f.close(); runCurrent()
    }

    @Test
    fun firstFileSaveFailure_memoryRetryDoesNotSynthesizeAgain() = runTest {
        val f = Fixture(this)
        f.repo.failPublications = 1
        f.play(); runCurrent()
        assertEquals(CompanionVoiceFailureStage.STORAGE, f.controller.state.value.failureStage)
        f.controller.retry(); runCurrent()
        assertEquals(1, f.ttsTexts.size)
        assertEquals(2, f.repo.publicationCalls)
        assertEquals(CompanionVoicePhase.PREPARING, f.controller.state.value.phase)
        f.close(); runCurrent()
    }

    @Test
    fun unknownPendingWithoutAudio_requiresExplicitRegeneration() = runTest {
        val f = Fixture(this)
        f.repo.requiresExplicit = true
        f.play(); runCurrent()
        assertTrue(f.controller.state.value.needsExplicitRegeneration)
        assertTrue(f.ttsTexts.isEmpty())
        f.controller.retry(); runCurrent()
        assertTrue(f.ttsTexts.isEmpty())
        f.controller.regenerateExplicitly(); runCurrent()
        assertEquals(listOf("第一句"), f.ttsTexts)
        f.close(); runCurrent()
    }

    @Test
    fun generateAndFavorite_entireSelectionSavesActualAudioWithoutPlayback() = runTest {
        val f = Fixture(
            this,
            units = listOf(
                unit("old", "旧场旁白", RoleplayContentType.NARRATION),
                unit("new", "新场对白")
            )
        )
        f.controller.generateAndFavorite(f.selection, f.settings); runCurrent()
        assertEquals(listOf("旧场旁白", "新场对白"), f.ttsTexts)
        assertEquals(2, f.repo.favoriteCalls)
        assertTrue(f.players.isEmpty())
        assertEquals(CompanionVoicePhase.COMPLETED, f.controller.state.value.phase)
        f.controller.generateAndFavorite(f.selection, f.settings); runCurrent()
        assertEquals(2, f.ttsTexts.size)
        assertEquals(4, f.repo.favoriteCalls)
        f.close(); runCurrent()
    }

    @Test
    fun storedFavoriteWithDeletedSource_playsSnapshotWithoutTranslationOrTts() = runTest {
        val f = Fixture(this)
        f.controller.generateAndFavorite(f.selection, f.settings); runCurrent()
        val favorite = f.repo.favorites.values.single()
        f.repo.sourcesAvailable = false
        f.controller.playFavorite(favorite.id); runCurrent()
        f.players.single().prepared(); runCurrent()
        assertEquals(CompanionVoicePhase.PLAYING, f.controller.state.value.phase)
        assertEquals(1, f.ttsTexts.size)
        assertEquals(0, f.translation.calls)
        assertEquals(favorite.audio.clip, f.controller.state.value.currentAudio!!.clip)
        f.close(); runCurrent()
    }

    @Test
    fun automaticSpeech_skipsInitialHistoryOpeningAndNarration() = runTest {
        val f = Fixture(this, settings(auto = true), listOf(unit("history", "历史句")))
        f.controller.offerAutomatic(f.units, f.settings); runCurrent()
        assertTrue(f.ttsTexts.isEmpty())
        val opening = unit("opening", "开场台词")
        f.turnKinds[opening.source.turnId] = CompanionTurnKind.OPENING
        f.updateUnits(f.units + opening)
        f.controller.offerAutomatic(f.units, f.settings); runCurrent()
        assertTrue(f.ttsTexts.isEmpty())
        val next = unit("next", "真正的新对白")
        val narration = unit("narration", "新旁白", RoleplayContentType.NARRATION)
        f.updateUnits(f.units + next + narration)
        f.controller.offerAutomatic(f.units, f.settings); runCurrent()
        assertEquals(listOf("真正的新对白"), f.ttsTexts)
        f.close(); runCurrent()
    }

    @Test
    fun automaticMutedResumeBaseline_doesNotReadExistingReplyWhenReenabled() = runTest {
        val f = Fixture(this, settings(auto = true))
        f.controller.offerAutomatic(f.units, f.settings)
        val background = unit("background", "后台完成的答复")
        f.updateUnits(f.units + background)
        val silent = f.settings.copy(
            companionCharacterSettings = mapOf(
                "alice" to f.settings.companionSettings("alice")
                    .copy(voice = f.settings.companionSettings("alice").voice.copy(autoPlay = false))
            )
        )
        f.controller.offerAutomatic(f.units, silent); runCurrent()
        f.controller.offerAutomatic(f.units, f.settings); runCurrent()
        assertTrue(f.ttsTexts.isEmpty())
        f.close(); runCurrent()
    }

    @Test
    fun completedQueue_releasesAudioBeforeTrimmingToLimit() = runTest {
        val value = settings().let {
            it.copy(
                companionCharacterSettings = mapOf(
                    "alice" to it.companionSettings("alice")
                        .copy(voice = it.companionSettings("alice").voice.copy(cacheLimitBytes = CompanionVoicePreferences.MIN_CACHE_LIMIT_BYTES))
                )
            )
        }
        val f = Fixture(this, value, listOf(unit("one", "一句"), unit("two", "二句")))
        f.repo.receiptSize = 700_000
        f.play(); runCurrent()
        f.players[0].prepared(); runCurrent(); f.players[0].completed(); runCurrent()
        assertEquals(2, f.repo.audio.size)
        f.players[1].prepared(); runCurrent(); f.players[1].completed(); runCurrent()
        assertEquals(CompanionVoicePhase.COMPLETED, f.controller.state.value.phase)
        assertEquals(1, f.repo.audio.size)
        assertTrue(f.repo.audio.values.sumOf { it.receipt.sizeBytes } <= CompanionVoicePreferences.MIN_CACHE_LIMIT_BYTES)
        assertTrue(f.repo.pins.isEmpty())
        f.close(); runCurrent()
    }

    private class Fixture(
        val scope: TestScope,
        val settings: AppSettings = settings(),
        units: List<CompanionSpeechUnit> = listOf(unit("first", "第一句")),
        sessionScope: CoroutineScope = scope.backgroundScope,
        sourceFailureGate: CompletableDeferred<Unit>? = null,
    ) {
        val owner = CompanionVoiceOwner("scene", "alice", "STAGE")
        var units = units
        var selection = selection(units)
        val repo = FakeVoiceRepository(sourceFailureGate)
        val translation = FakeTranslation()
        val players = mutableListOf<FakePlayer>()
        val ttsTexts = mutableListOf<String>()
        var ttsBehavior: suspend () -> MimoTtsResult = { MimoTtsResult("fake-audio") }
        val turnKinds = mutableMapOf<String, CompanionTurnKind>()
        val controller = CompanionVoiceController(
            sessionScope, repo, translation,
            CompanionVoicePlayerFactory { FakePlayer().also(players::add) },
            getTurn = { id ->
                CompanionTurn(
                    id,
                    "encounter-$id",
                    1,
                    turnKinds[id] ?: CompanionTurnKind.INPUT,
                    null,
                    "reply-$id",
                    null,
                    1,
                    0,
                    0,
                    "attempt-$id",
                    null,
                    1
                )
            },
            synthesize = { _, request, _ -> ttsTexts += request.messages.last().content; ttsBehavior() },
            requestFactory = CompanionTtsRequestFactory(StandardTestDispatcher(scope.testScheduler)),
        )

        init {
            controller.updateContext(owner, settings, units)
        }

        fun play() = controller.playSelection(selection, settings)
        fun updateUnits(value: List<CompanionSpeechUnit>) {
            units = value; controller.updateContext(owner, settings, value)
        }

        fun close() = controller.close()
    }

    private class FakePlayer : CompanionVoicePlayer {
        var prepared: () -> Unit = {}
        var completed: () -> Unit = {}
        var error: (Throwable?) -> Unit = {}
        var prepareCalls = 0
        var startCalls = 0
        var releaseCalls = 0
        var startFailure: Throwable? = null
        var onStart: () -> Unit = {}
        override fun setCallbacks(
            onPrepared: () -> Unit,
            onCompleted: () -> Unit,
            onError: (Throwable?) -> Unit
        ) {
            prepared = onPrepared; completed = onCompleted; error = onError
        }

        override fun prepare(path: String) {
            prepareCalls++
        }

        override fun start() {
            startCalls++; startFailure?.let { throw it }; onStart()
        }

        override fun stop() = Unit
        override fun release() {
            releaseCalls++
        }
    }

    private class FakeTranslation : SnapshotAiTranslationService {
        var calls = 0
        var behavior: suspend (String) -> String = { "English: $it" }
        override fun freeze(settingsSnapshot: AppSettings) = TranslationRequestSnapshot(
            TranslationRequestIdentity(
                "fake",
                "https://example.test/v1/",
                "https://example.test/v1/chat/completions",
                "fake-model",
                ProviderApiProtocol.OPENAI_COMPATIBLE,
                OpenAiTextApiMode.CHAT_COMPLETIONS
            ),
            "fake-key", null,
        )

        override suspend fun translateText(
            snapshot: TranslationRequestSnapshot,
            text: String,
            targetLanguage: String,
            sourceLanguage: String
        ): String {
            calls++; return behavior(text)
        }
    }

    private class FakeVoiceRepository(private val sourceFailureGate: CompletableDeferred<Unit>? = null) :
        CompanionVoiceRepository {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val audio = mutableMapOf<SpeechRequestKey, CompanionVoiceAudio>()
        val favorites = mutableMapOf<String, CompanionVoiceFavorite>()
        val releasedOwners = mutableListOf<String>()
        var sourcesAvailable = true
        var publicationCalls = 0
        var favoriteCalls = 0
        var attachmentRetries = 0
        var attachmentGate: CompletableDeferred<Unit>? = null
        var failPublications = 0
        var returnPendingAttachment = false
        var requiresExplicit = false
        var receiptSize = 100L
        val pins = mutableMapOf<SpeechRequestKey, String>()
        var sourceSubscriptions = 0
        override suspend fun lookup(key: SpeechRequestKey, ownerToken: String) =
            audio[key]?.receipt?.also { pins[key] = ownerToken }

        override suspend fun claim(
            key: SpeechRequestKey,
            ownerToken: String,
            explicitRegeneration: Boolean
        ): CompanionAudioClaimResult =
            audio[key]?.let { CompanionAudioClaimResult.Existing(it.receipt) }
                ?: if (requiresExplicit && !explicitRegeneration) CompanionAudioClaimResult.NeedsExplicitRegeneration
                else CompanionAudioClaimResult.Granted(
                    CompanionAudioClaim(
                        key.value,
                        key,
                        ownerToken
                    )
                )

        override suspend fun publish(
            claim: CompanionAudioClaim,
            b64Audio: String,
            clip: CompanionVoiceClip,
            isCurrent: suspend () -> Boolean
        ): CompanionVoiceAudio {
            publicationCalls++
            if (!isCurrent()) throw CancellationException()
            if (failPublications-- > 0) throw IllegalStateException("私有文件保存失败")
            return CompanionVoiceAudio(
                CompanionAudioReceipt(
                    claim.audioId,
                    claim.key,
                    "/fake/audio.wav",
                    "audio/wav",
                    "0".repeat(64),
                    receiptSize,
                    1,
                    if (returnPendingAttachment) CompanionAudioAttachmentState.PENDING else CompanionAudioAttachmentState.ATTACHED
                ), clip
            ).also { audio[claim.key] = it; pins[claim.key] = claim.ownerToken }
        }

        override suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio {
            attachmentRetries++
            attachmentGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return audio.copy(receipt = audio.receipt.copy(attachmentState = CompanionAudioAttachmentState.ATTACHED))
                .also { this.audio[audio.receipt.key] = it }
        }

        override suspend fun recoverPending() = emptyList<CompanionVoiceAudio>()
        override suspend fun releaseOwner(ownerToken: String) {
            releasedOwners += ownerToken; pins.entries.removeAll { it.value == ownerToken }
        }

        override suspend fun isSourceCurrent(source: CompanionReplySource) = sourcesAvailable
        override fun observeSourceChanges(): Flow<Unit> = flow {
            val subscription = ++sourceSubscriptions
            if (subscription == 1 && sourceFailureGate != null) {
                sourceFailureGate.await()
                throw IllegalStateException("原文状态监听暂时不可用")
            }
            emitAll(changes)
        }

        override fun observeFavorites(characterId: String): Flow<List<CompanionVoiceFavorite>> =
            flowOf(favorites.values.toList())

        override fun observeUsage(): Flow<CompanionVoiceCacheUsage> =
            flowOf(CompanionVoiceCacheUsage())

        override suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite {
            favoriteCalls++
            return CompanionVoiceFavorite(
                audio.receipt.audioId,
                audio,
                if (sourcesAvailable) CompanionVoiceSourceState.AVAILABLE else CompanionVoiceSourceState.UNAVAILABLE,
                1
            ).also { favorites[it.id] = it }
        }

        override suspend fun unfavorite(favoriteId: String) {
            favorites.remove(favoriteId)
        }

        override suspend fun favoriteAudio(favoriteId: String, ownerToken: String) =
            favorites[favoriteId]?.audio

        override suspend fun usage() = CompanionVoiceCacheUsage()
        override suspend fun trimCache(maxBytes: Long): CompanionVoiceCacheUsage {
            audio.keys.toList().forEach { key ->
                if (audio.values.sumOf { it.receipt.sizeBytes } > maxBytes && key !in pins && favorites.values.none { it.audio.receipt.key == key }) audio.remove(
                    key
                )
            }
            return usage()
        }

        override suspend fun clearCache() = usage()
    }

    companion object {
        private fun settings(translated: Boolean = false, auto: Boolean = false) = AppSettings(
            voiceSynthesisSettings = VoiceSynthesisSettings(apiKey = "fake-key"),
            companionCharacterSettings = mapOf(
                "alice" to CompanionCharacterSettings(
                    voice = CompanionVoicePreferences(
                        enabled = true,
                        autoPlay = auto,
                        readMode = if (translated) CompanionVoiceReadMode.TRANSLATED else CompanionVoiceReadMode.ORIGINAL
                    )
                )
            ),
        )

        private fun unit(
            id: String,
            text: String,
            type: RoleplayContentType = RoleplayContentType.DIALOGUE
        ) = CompanionSpeechUnit(
            id,
            CompanionReplySource(
                id,
                "conversation-$id",
                "turn-$id",
                1,
                1,
                1,
                companionRawHash(text),
                "attempt-$id",
                characterId = "alice"
            ),
            "beat-$id",
            text,
            CompanionRawSpan(0, text.length),
            if (type == RoleplayContentType.NARRATION) RoleplaySpeaker.NARRATOR else RoleplaySpeaker.CHARACTER,
            "Alice",
            CompanionVoiceEmotion.CALM,
            type,
            text,
        )

        private fun selection(units: List<CompanionSpeechUnit>) = CompanionSpeechSelection(
            units,
            units.map { CompanionSpeechRange(it.id, 0, it.text.length) })
    }
}
