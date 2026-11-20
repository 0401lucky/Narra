package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.ui.navigation.rememberCompanionVoiceBinding
import com.example.myapplication.viewmodel.CompanionReadingUiState
import com.example.myapplication.viewmodel.CompanionVoiceViewModel
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 实际 Compose→生产 binding→VM→Fake Session，音效质量不由本类证明。 */
@RunWith(AndroidJUnit4::class)
class CompanionVoiceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val ui = CompanionD4UiFixture(composeRule)
    private val models = mutableListOf<CompanionVoiceViewModel>()
    private val players = mutableListOf<CompanionD4Player>()
    private val owner =
        CompanionVoiceOwner(COMPANION_D4_SCENARIO_ID, COMPANION_D4_CHARACTER_ID, "STAGE")

    @After
    fun close() {
        players.forEach { it.close() }
        ui.close()
        composeRule.runOnUiThread { models.forEach { it.viewModelScope.cancel() } }
    }

    @Test
    fun stagePointPlay_reachesSessionAndReportsPreparingBeforeStarted() {
        val message =
            companionD4Message("<narration>旧场旁白原样保留。</narration><dialogue>你好。</dialogue>")
        val reply = companionD4Reply(message)
        val units = CompanionSpeechSupport.units(reply)
        val session = CompanionVoiceUiSession()
        val model = model(session)
        val player = CompanionD4Player().also(players::add)
        ui.render {
            val binding = rememberCompanionVoiceBinding(model, owner, units)
            CompanionStage(
                reply,
                player.player,
                CompanionVisualSettings(),
                CompanionResolvedResources(),
                COMPANION_D4_CHARACTER_NAME,
                1f,
                onSpeechSelection = model::select,
                voiceBinding = binding
            )
        }
        composeRule.onNodeWithTag("companion_stage_menu").performClick()
        composeRule.onNodeWithTag("companion_stage_speak").performClick()
        composeRule.onNodeWithTag("companion_voice_phase")
            .assertTextContains("正在准备播放", substring = true)
        composeRule.runOnIdle {
            assertEquals(1, session.plays.size)
            assertEquals(units.first(), session.plays.single().units.single())
            assertEquals(
                RoleplayContentType.NARRATION,
                session.plays.single().units.single().contentType
            )
            assertEquals(CompanionVoicePhase.PREPARING, session.state.value.phase)
            assertEquals(message.content, reply.raw)
            session.state.value = session.state.value.copy(phase = CompanionVoicePhase.PLAYING)
        }
        composeRule.onNodeWithTag("companion_voice_phase")
            .assertTextContains("正在播放", substring = true)
        composeRule.onNodeWithTag("companion_voice_stop").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(session.stops > 0); assertTrue(session.generates.isEmpty()) }
        composeRule.onNodeWithTag("companion_voice_generate_favorite").performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, session.generates.size); assertEquals(
            units.first(),
            session.generates.single().units.single()
        )
        }
    }

    @Test
    fun readerCrossSceneSelection_keepsNarrationAndOrderThroughVoiceBinding() {
        val oldMessage = companionD4Message(
            "<narration>旧场所选旁白。</narration>",
            id = "d5-old",
            conversationId = "old-conversation",
            createdAt = 1
        )
        val currentMessage = companionD4Message(
            "<dialogue>当前场所选台词。</dialogue>",
            id = "d5-new",
            conversationId = "new-conversation",
            createdAt = 2
        )

        fun record(message: ChatMessage, scene: String) = CompanionReadingRecord(
            message,
            CompanionReadingBinding(
                message.conversationId,
                scene,
                "synthetic-run",
                scene,
                COMPANION_D4_CHARACTER_ID
            )
        )

        val records =
            listOf(record(oldMessage, "旧场次"), record(currentMessage, COMPANION_D4_SCENARIO_ID))
        val settings = companionD4Settings()
        val items = CompanionReadingSupport.items(records, settings)
        val units = CompanionSpeechSupport.units(items)
        val reading = mutableStateOf(
            CompanionReadingUiState(
                scope = CompanionReadingScope(
                    MemoryRuntimeContext(
                        COMPANION_D4_CHARACTER_ID,
                        "synthetic-space",
                        "new-conversation",
                        COMPANION_D4_SCENARIO_ID,
                        "synthetic-run"
                    ), records.map { it.binding }, emptySet()
                ),
                records = records, items = items, isLoading = false,
            )
        )
        val readerOwner = owner.copy(mode = "READER")
        val session = CompanionVoiceUiSession()
        val model = model(session, initialOwner = readerOwner)
        ui.render {
            val binding = rememberCompanionVoiceBinding(
                model,
                readerOwner,
                units,
                selection = reading.value.selection,
                synchronizeSelection = true
            )
            CompanionReadingScreen(
                reading.value, settings,
                CompanionReadingCallbacks(
                    {},
                    {},
                    {},
                    { selected ->
                        reading.value = reading.value.copy(selection = selected); model.select(
                        selected
                    )
                    },
                    {}),
                voiceBinding = binding
            )
        }
        composeRule.onNodeWithTag("companion_reader_item_${items.first().id}")
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("companion_reader_select_start").performClick()
        composeRule.onNodeWithTag("companion_reader_item_${items.last().id}")
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("companion_reader_select_end").performClick()
        composeRule.onNodeWithTag("companion_voice_play_selection").performScrollTo().performClick()
        composeRule.runOnIdle {
            val played = session.plays.single()
            assertEquals(
                listOf("old-conversation", "new-conversation"),
                played.units.map { it.source.conversationId })
            assertEquals("旧场所选旁白。\n\n当前场所选台词。", played.text)
            assertEquals(RoleplayContentType.NARRATION, played.units.first().contentType)
            assertEquals(oldMessage.content, records.first().message.content)
            assertEquals(currentMessage.content, records.last().message.content)
            assertTrue(session.generates.isEmpty())
        }
    }

    @Test
    fun translationFailure_requiresExplicitRetryOrOriginalChoice() {
        val message = companionD4Message("<dialogue>原文不能被译文改写。</dialogue>")
        val units = CompanionSpeechSupport.units(companionD4Reply(message))
        val selection = requireNotNull(CompanionSpeechSupport.select(units, units.first().id))
        val session = CompanionVoiceUiSession().apply {
            state.value = CompanionVoiceState(
                phase = CompanionVoicePhase.ERROR,
                failureStage = CompanionVoiceFailureStage.TRANSLATION,
                errorMessage = "翻译未完成",
                originalText = units.first().text
            )
        }
        val model = model(session)
        ui.render {
            val binding = rememberCompanionVoiceBinding(model, owner, units)
            CompanionSpeechSelectionPanel(selection, binding, {})
        }
        composeRule.onNodeWithTag("companion_voice_retry_translation").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_voice_read_original").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, session.retries)
            assertEquals(1, session.originalReads)
            assertTrue(session.plays.isEmpty())
            assertTrue(session.generates.isEmpty())
            assertEquals("<dialogue>原文不能被译文改写。</dialogue>", message.content)
        }
    }

    @Test
    fun librarySavedAudio_andClearCache_areSeparateFromGenerationAndSource() {
        val message = companionD4Message("<dialogue>独立收藏原文。</dialogue>")
        val audio =
            companionVoiceUiAudio(CompanionSpeechSupport.units(companionD4Reply(message)).single())
        val favorite =
            CompanionVoiceFavorite("favorite-1", audio, CompanionVoiceSourceState.UNAVAILABLE, 1)
        val repository = CompanionVoiceUiRepository(listOf(favorite))
        val session = CompanionVoiceUiSession().apply { savedAudio = audio }
        val libraryOwner =
            owner.copy(scenarioId = "voice-library:${owner.characterId}", mode = "LIBRARY")
        val model = model(session, repository, initialOwner = libraryOwner)
        ui.render {
            val binding = rememberCompanionVoiceBinding(model, libraryOwner, emptyList())
            CompanionVoiceScreen(binding.state, binding.callbacks, {}, {})
        }
        composeRule.onNodeWithTag("companion_voice_source_unavailable_favorite-1").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("companion_voice_play_favorite_favorite-1").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_voice_favorite_existing").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_voice_clear_cache").performScrollTo().performClick()
        composeRule.onNodeWithText("清理缓存").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("favorite-1"), session.favoritePlays)
            assertEquals(1, session.favorites)
            assertTrue(session.plays.isEmpty())
            assertTrue(session.generates.isEmpty())
            assertEquals(1, repository.clears)
            assertEquals(listOf(favorite), repository.favorites.value)
        }
        composeRule.onNodeWithTag("companion_voice_unfavorite_favorite-1").performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("favorite-1"), repository.removed)
            assertTrue(repository.favorites.value.isEmpty())
            assertEquals("<dialogue>独立收藏原文。</dialogue>", message.content)
        }
    }

    @Test
    fun preferenceSave_isIndependentFromOldVoiceBackgroundSwitch() {
        val store = CompanionVoiceUiSettingsStore(companionD4Settings())
        val session = CompanionVoiceUiSession()
        val libraryOwner =
            owner.copy(scenarioId = "voice-library:${owner.characterId}", mode = "LIBRARY")
        val model = model(session, store = store, initialOwner = libraryOwner)
        ui.render {
            val binding = rememberCompanionVoiceBinding(model, libraryOwner, emptyList())
            CompanionVoiceScreen(binding.state, binding.callbacks, {}, {})
        }
        composeRule.onNodeWithTag("companion_voice_enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_voice_auto").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_voice_mode_TRANSLATED").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_voice_target_language").performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("companion_voice_target_language").performTextInput("日语")
        composeRule.onNodeWithTag("companion_voice_save").performClick()
        composeRule.runOnIdle {
            val saved = requireNotNull(store.writes.single().voice)
            assertTrue(saved.enabled)
            assertTrue(saved.autoPlay)
            assertEquals("日语", saved.targetLanguage)
            assertFalse(store.settingsFlow.value.voiceSynthesisSettings.enabled)
            assertTrue(session.plays.isEmpty())
            assertTrue(session.generates.isEmpty())
        }
    }

    @Test
    fun pausedHost_blocksLateContentAndResumedHostOnlyRebasesAutomatic() {
        val message = companionD4Message("<dialogue>已显示的旧答。</dialogue>")
        val units = CompanionSpeechSupport.units(companionD4Reply(message))
        val session = CompanionVoiceUiSession()
        val model = model(session)
        ui.render {
            val binding = rememberCompanionVoiceBinding(model, owner, units, units)
            CompanionSpeechSelectionPanel(
                requireNotNull(
                    CompanionSpeechSupport.select(
                        units,
                        units.first().id
                    )
                ), binding, {})
        }
        composeRule.runOnIdle { assertTrue(session.automatic.all { !it.second }) }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        var calls = 0
        composeRule.runOnUiThread {
            calls = session.automatic.size
            val revised = units.first().copy(source = units.first().source.copy(revision = 2))
            model.bind(owner.copy(mode = "READER"), listOf(revised), listOf(revised))
            model.playSelection(
                requireNotNull(
                    CompanionSpeechSupport.select(
                        listOf(revised),
                        revised.id
                    )
                )
            )
            assertEquals(calls, session.automatic.size)
            assertTrue(session.plays.isEmpty())
            assertTrue(session.stops > 0)
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.runOnIdle {
            assertTrue(session.automatic.size > calls)
            assertFalse(session.automatic.last().second)
            assertTrue(session.plays.isEmpty())
            assertEquals("READER", session.contexts.last().mode)
        }
    }

    private fun model(
        session: CompanionVoiceUiSession,
        repository: CompanionVoiceUiRepository = CompanionVoiceUiRepository(),
        store: CompanionVoiceUiSettingsStore = CompanionVoiceUiSettingsStore(),
        initialOwner: CompanionVoiceOwner = owner,
    ): CompanionVoiceViewModel {
        lateinit var model: CompanionVoiceViewModel
        composeRule.runOnUiThread {
            model = CompanionVoiceViewModel(initialOwner, store, repository, { session })
        }
        models += model
        return model
    }
}
