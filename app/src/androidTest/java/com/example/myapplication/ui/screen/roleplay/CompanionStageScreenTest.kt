package com.example.myapplication.ui.screen.roleplay

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.CompanionCharacterSettings
import com.example.myapplication.model.CompanionDisplayMode
import com.example.myapplication.model.CompanionImageKind
import com.example.myapplication.model.CompanionObservationSettings
import com.example.myapplication.model.CompanionPresentationSnapshot
import com.example.myapplication.model.CompanionResolvedImage
import com.example.myapplication.model.CompanionResolvedResources
import com.example.myapplication.model.CompanionSpeechSelection
import com.example.myapplication.model.CompanionVisualSettings
import com.example.myapplication.model.ParsedCompanionReply
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.companionBuiltinObservationFields
import com.example.myapplication.roleplay.companion.CompanionBeatPlayerPhase
import com.example.myapplication.viewmodel.CompanionDisplayUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionStageScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ui = CompanionD4UiFixture(composeRule)
    private val players = mutableListOf<CompanionD4Player>()

    @After
    fun release() {
        players.forEach { it.close() }
        ui.close()
    }

    @Test
    fun parsedLiteralTagsAndHardBoundaryClusters_keepEveryRenderedTextFaithful() {
        val literal = "字面 <narration>标签应保留</narration> 与 &#65;，不再次解释。"
        val clusters = listOf("😀", "e\u0301", "👩🏽‍💻")
        val longBody = "中".repeat(143) + clusters[0] + "后".repeat(143) +
                clusters[1] + "文".repeat(142) + clusters[2] + "完".repeat(180)
        val spacedBody = "前" + " ".repeat(300) + "后".repeat(100)
        val spacedXml = "前" + "&#32;".repeat(300) + "后".repeat(100)

        fun xmlText(text: String) =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val raw =
            "<dialogue>" + xmlText(literal) + "</dialogue><narration>" + longBody +
                    "</narration><dialogue>" + spacedXml + "</dialogue>"
        val message = companionD4Message(raw)
        val reply = companionD4Reply(message)
        val selections = mutableListOf<CompanionSpeechSelection?>()
        val clock = player()
        assertEquals(literal, reply.beats[0].content)
        assertEquals(longBody, reply.beats[1].content)
        assertEquals(spacedBody, reply.beats[2].content)
        ui.render { Stage(reply, clock, onSelection = { selections += it }) }

        composeRule.onNodeWithTag(ADVANCE).performClick()
        val literalParts = stageBodyParagraphs(literal, reply.beats[0].speakerName)
        literalParts.first().assertIsDisplayed()
        assertTrue(ui.textLayout(literalParts.first()).layoutInput.text.text.contains("<narration>"))
        assertTrue(ui.textLayout(literalParts.first()).layoutInput.text.text.contains("&#65;"))
        composeRule.onNodeWithTag(ADVANCE).performClick()
        composeRule.onNodeWithTag(ADVANCE).performClick()
        val paragraphs = stageBodyParagraphs(longBody, reply.beats[1].speakerName)
        val renderedTexts = paragraphs.map { ui.textLayout(it).layoutInput.text.text }
        assertTrue("长文必须实际绘制为多个独立Text", renderedTexts.size > 1)
        assertIndependentRenderedTexts(renderedTexts, clusters)
        assertVisibleStageBodyLine(
            paragraphs,
            composeRule.onNodeWithTag("companion_stage_text_viewport")
        )
        composeRule.runOnIdle {
            assertEquals(reply.source, clock.player.state.value.source)
            assertEquals(longBody, clock.player.state.value.visibleText)
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, clock.player.state.value.phase)
            assertEquals(raw, reply.raw)
            assertEquals(raw, message.content)
            assertEquals(message.memoryRevision, reply.source.revision)
            assertTrue(selections.none { it != null })
        }
        composeRule.onNodeWithTag(ADVANCE).performClick()
        composeRule.onNodeWithTag(ADVANCE).performClick()
        val spacedParts = stageBodyParagraphs(spacedBody, reply.beats[2].speakerName)
        val spacedTexts = spacedParts.map { ui.textLayout(it).layoutInput.text.text }
        assertTrue(
            "实际角色气泡必须保留全空白中间Text",
            spacedTexts.any { it.isNotEmpty() && it.isBlank() })
        assertEquals(spacedBody, spacedTexts.joinToString(""))
        composeRule.runOnIdle {
            assertEquals(reply.source, clock.player.state.value.source)
            assertEquals(spacedBody, clock.player.state.value.visibleText)
            assertEquals(raw, reply.raw)
            assertEquals(raw, message.content)
            assertEquals(message.memoryRevision, reply.source.revision)
            assertTrue(selections.none { it != null })
        }
    }

    @Test
    fun reveal_lateTypingCallback_keepsWholeBeatUntilAnotherTap() {
        val first = "中👩🏽‍🚀e\u0301，完整地留在这一拍。"
        val reply = companionD4Reply(
            companionD4Message("<narration>" + first + "</narration><dialogue>下一拍。</dialogue>"),
        )
        val clock = player()
        ui.render { Stage(reply, clock) }
        composeRule.runOnIdle {
            assertEquals(CompanionBeatPlayerPhase.TYPING, clock.player.state.value.phase)
            clock.tick()
        }
        composeRule.runOnIdle {
            assertEquals("中", clock.player.state.value.visibleText)
            clock.tick()
        }
        composeRule.runOnIdle {
            assertEquals("中👩🏽‍🚀", clock.player.state.value.visibleText)
        }
        composeRule.onNodeWithTag(ADVANCE).performClick()
        val completed = composeRule.runOnIdle { clock.player.state.value }
        assertEquals(first, completed.visibleText)
        assertEquals(0, completed.beatIndex)
        assertEquals(CompanionBeatPlayerPhase.COMPLETE, completed.phase)
        composeRule.onNodeWithText(first, substring = true).assertIsDisplayed()

        composeRule.runOnIdle { clock.tick() }
        composeRule.runOnIdle { assertEquals(completed, clock.player.state.value) }
        composeRule.onNodeWithTag(ADVANCE).performClick()
        composeRule.runOnIdle {
            assertEquals(1, clock.player.state.value.beatIndex)
            assertEquals(reply.beats[1].id, clock.player.state.value.beat?.id)
            assertEquals("", clock.player.state.value.visibleText)
            assertEquals(reply.source, clock.player.state.value.source)
        }
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("2/2")
    }

    @Test
    fun changedRevision_andLeavingStage_cancelOldTypingAndSpeechSelection() {
        val raw = "<dialogue>旧来源的本地正文。</dialogue><narration>第二拍。</narration>"
        val source = mutableStateOf(companionD4Reply(companionD4Message(raw)))
        val mounted = mutableStateOf(true)
        val selections = mutableListOf<CompanionSpeechSelection?>()
        val clock = player()
        ui.render {
            if (mounted.value) Stage(source.value, clock, onSelection = { selections += it })
        }
        composeRule.onNodeWithTag(MENU).performClick()
        composeRule.onNodeWithTag(SELECT).performClick()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                source.value.source,
                selections.last()?.units?.single()?.source
            )
        }
        composeRule.runOnIdle {
            source.value = companionD4Reply(companionD4Message(raw, revision = 2L))
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(null, selections.last())
            assertEquals(2L, clock.player.state.value.source?.revision)
            clock.tick()
        }
        composeRule.runOnIdle {
            assertEquals("", clock.player.state.value.visibleText)
            assertEquals(0, clock.player.state.value.beatIndex)
            mounted.value = false
        }
        composeRule.onNodeWithTag(STAGE).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(CompanionBeatPlayerPhase.IDLE, clock.player.state.value.phase)
            assertEquals(null, selections.last())
            clock.tick()
        }
        composeRule.runOnIdle {
            assertEquals(null, clock.player.state.value.source)
            assertEquals(CompanionBeatPlayerPhase.IDLE, clock.player.state.value.phase)
        }
    }

    @Test
    fun observation_menuAndLocalReplay_keepTheSameCanonicalReply() {
        val raw = "<observe version=\"1\"><field id=\"time\">旧时刻：傍晚</field></observe>" +
                "<narration>仍是这一条已提交记录。</narration><dialogue>只在本地重播。</dialogue>"
        val message = companionD4Message(raw)
        val reply = companionD4Reply(message)
        val clock = player()
        ui.render {
            Column {
                CompanionObservationPanel(reply, enabled = true)
                Box(Modifier.weight(1f)) { Stage(reply, clock) }
            }
        }
        composeRule.onNodeWithTag(ADVANCE).performClick()
        val complete = composeRule.runOnIdle { clock.player.state.value }
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("旧时刻：傍晚", substring = true).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.runOnIdle { assertEquals(complete, clock.player.state.value) }
        composeRule.onNodeWithTag(MENU).performClick()
        Espresso.pressBack()
        composeRule.onNodeWithTag(SELECT).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(complete, clock.player.state.value) }
        composeRule.onNodeWithTag(REPLAY).performClick()
        composeRule.runOnIdle {
            assertEquals(0, clock.player.state.value.beatIndex)
            assertEquals(CompanionBeatPlayerPhase.TYPING, clock.player.state.value.phase)
            assertEquals(reply.source, clock.player.state.value.source)
            assertEquals(raw, message.content)
            assertEquals(1L, message.memoryRevision)
            assertEquals(raw, reply.raw)
        }
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
    }

    @Test
    fun stageHud_gateRevisionAndMalformedObservation_keepLocalPlaybackAndBackOwnership() {
        val raw = "<observe version=\"1\"><field id=\"time\">傍晚</field></observe>" +
                "<narration>历史正文一。</narration><dialogue>历史正文二。</dialogue>"
        val reply = mutableStateOf(
            companionD4Reply(
                companionD4Message(
                    raw, presentationSnapshot = observationSnapshot("当时的时刻"),
                )
            )
        )
        val enabled = mutableStateOf(true)
        val selections = mutableListOf<CompanionSpeechSelection?>()
        var hostBackCalls = 0
        val clock = player()
        ui.render {
            BackHandler { hostBackCalls++ }
            CompanionStage(
                reply.value, clock.player, CompanionVisualSettings(), CompanionResolvedResources(),
                COMPANION_D4_CHARACTER_NAME, 1f, Modifier.fillMaxSize(),
                onSpeechSelection = { selections += it }, observationEnabled = enabled.value,
            )
        }
        val originalPlayback = composeRule.runOnIdle { clock.player.state.value }
        assertEquals(CompanionBeatPlayerPhase.TYPING, originalPlayback.phase)
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("当时的时刻：傍晚").assertTextEquals("当时的时刻：傍晚")
        Espresso.pressBack()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(originalPlayback, clock.player.state.value)
            assertEquals(0, hostBackCalls)
        }
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.runOnIdle { enabled.value = false }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_observation_open").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(originalPlayback, clock.player.state.value)
            enabled.value = true
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("当时的时刻：傍晚").assertIsDisplayed()
        composeRule.runOnIdle {
            reply.value = companionD4Reply(
                companionD4Message(
                    raw, revision = 2L, presentationSnapshot = observationSnapshot("本次时刻"),
                )
            )
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, clock.player.state.value.beatIndex)
            assertEquals(2L, clock.player.state.value.source?.revision)
            clock.tick()
        }
        composeRule.runOnIdle { assertEquals("", clock.player.state.value.visibleText) }
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("本次时刻：傍晚").assertIsDisplayed()
        composeRule.onNodeWithText("当时的时刻：傍晚").assertDoesNotExist()
        composeRule.runOnIdle {
            reply.value = companionD4Reply(
                companionD4Message(
                    "<observe version=\"1\"><field id=\"unknown\">旧值</field></observe>" +
                            "<narration>坏观测后的正文仍可演出。</narration>",
                    revision = 3L, presentationSnapshot = observationSnapshot("第三次时刻"),
                )
            )
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_observation_open").assertDoesNotExist()
        val malformedPlayback = composeRule.runOnIdle { clock.player.state.value }
        assertEquals(0, malformedPlayback.beatIndex)
        assertEquals(3L, malformedPlayback.source?.revision)
        assertEquals(null, reply.value.observation)
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(1, hostBackCalls)
            assertEquals(malformedPlayback, clock.player.state.value)
            assertTrue(selections.filterNotNull().isEmpty())
        }
    }

    @Test
    fun roleplayScreen_stageHud_usesCurrentGateAndOldLabels_andRejectsMissingSourceMetadata() {
        val raw = "<observe version=\"1\"><field id=\"time\">傍晚</field></observe>" +
                "<narration>历史正文一。</narration><dialogue>历史正文二。</dialogue>"
        val message = mutableStateOf(
            companionD4Message(
                raw, presentationSnapshot = observationSnapshot("当时的时刻"),
            )
        )
        val current = CompanionCharacterSettings(
            revision = 31L,
            writing = CompanionCharacterSettings().writing.copy(
                observation = observationSnapshot("当前配置的时间").observation,
            ),
        )
        val settings = mutableStateOf(
            companionD4Settings().copy(
                companionCharacterSettings = mapOf(COMPANION_D4_CHARACTER_ID to current),
            )
        )
        val display = mutableStateOf(CompanionDisplayUiState(mode = CompanionDisplayMode.STAGE))
        var sendCalls = 0
        var retryCalls = 0
        var generateCalls = 0
        var hostBackCalls = 0
        val callbacks = screenCallbacks(
            {}, { sendCalls++ }, { retryCalls++ },
            { generateCalls++ }, { hostBackCalls++ }, {})
        ui.render {
            // 单独渲染 Screen 时补上生产 CompanionRouteContent 的宿主返回所有者。
            BackHandler { callbacks.navigation.onNavigateBack() }
            val parsed = companionD4Reply(message.value)
            val messages = parsed.beats.map { beat ->
                RoleplayMessageUiModel(
                    uiItemId = beat.id,
                    sourceMessageId = parsed.source.messageId,
                    contentType = beat.contentType,
                    speaker = beat.speaker,
                    speakerName = beat.speakerName,
                    content = beat.content,
                    companionReply = parsed,
                    companionBeat = beat,
                )
            }
            RoleplayScreen(
                scenario = RoleplayScenario(
                    id = COMPANION_D4_SCENARIO_ID,
                    title = "合成当前场次",
                    assistantId = COMPANION_D4_CHARACTER_ID,
                    interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE
                ),
                assistant = settings.value.assistants.single(),
                settings = settings.value,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList(),
                messages = messages,
                suggestions = emptyList(),
                input = "",
                inputFocusToken = 0L,
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
                isSending = false,
                isGeneratingSuggestions = false,
                isScenarioLoading = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                noticeMessage = null,
                errorMessage = null,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                callbacks = callbacks,
                companionDisplay = CompanionDisplayBinding(
                    display.value, onModeChange = { display.value = display.value.copy(mode = it) },
                    onOpenReading = {}, onOpenAppearance = {}, replyMessageId = message.value.id,
                ),
            )
        }
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag("companion_observation_open"))
                .fetchSemanticsNodes().size
        )
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("当时的时刻：傍晚").assertIsDisplayed()
        composeRule.onNodeWithText("当前配置的时间：", substring = true).assertDoesNotExist()
        composeRule.runOnIdle {
            settings.value = settings.value.copy(
                companionCharacterSettings = mapOf(
                    COMPANION_D4_CHARACTER_ID to current.copy(
                        writing = current.writing.copy(
                            observation = current.writing.observation.copy(enabled = false),
                        )
                    ),
                )
            )
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_observation_open").assertDoesNotExist()
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
        composeRule.runOnIdle {
            settings.value = settings.value.copy(
                companionCharacterSettings = mapOf(COMPANION_D4_CHARACTER_ID to current),
            )
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_observation_open").performClick()
        composeRule.onNodeWithText("当时的时刻：傍晚").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.runOnIdle { assertEquals(0, hostBackCalls) }
        composeRule.runOnIdle { message.value = message.value.copy(companionReplyMetadata = null) }
        composeRule.onNodeWithTag("companion_observation_open").assertDoesNotExist()
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("0/0")
        composeRule.onNodeWithTag("companion_display_LIST").performScrollTo().performClick()
        composeRule.onNodeWithText("历史正文一。", substring = true).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(1, hostBackCalls)
            assertEquals(0, sendCalls)
            assertEquals(0, retryCalls)
            assertEquals(0, generateCalls)
            assertEquals(raw, message.value.content)
            assertEquals(1L, message.value.memoryRevision)
        }
    }

    @Test
    fun oneLongPressThenDrag_cancelsMenusAndSelectionWithoutAdvancing() {
        val reply = companionD4Reply(
            companionD4Message("<narration>长按后拖动仍留在本拍。</narration><dialogue>不要推进。</dialogue>"),
        )
        val selections = mutableListOf<CompanionSpeechSelection?>()
        val clock = player()
        ui.render { Stage(reply, clock, onSelection = { selections += it }) }
        composeRule.onNodeWithTag(ADVANCE).performClick()
        val complete = composeRule.runOnIdle { clock.player.state.value }
        // 同一根指针从 down 一直保持到拖动后的 up，不能用第二次 swipe 冒充取消。
        composeRule.onNodeWithTag(SURFACE).performTouchInput {
            val start = Offset(width * 0.08f, height * 0.85f)
            down(start)
            advanceEventTime(800)
            moveTo(start.copy(y = height * 0.35f))
            advanceEventTime(100)
            up()
        }
        composeRule.onNodeWithTag(SELECT).assertDoesNotExist()
        composeRule.onNodeWithText("复制").assertDoesNotExist()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(complete, clock.player.state.value)
            assertTrue(selections.filterNotNull().isEmpty())
        }
    }

    @Test
    fun selectedBeat_systemBackClosesOverlayBeforeHost() {
        val reply = companionD4Reply(companionD4Message("<dialogue>选区保留完整正文。</dialogue>"))
        val clock = player()
        var hostBackCalls = 0
        val selections = mutableListOf<CompanionSpeechSelection?>()
        ui.render {
            BackHandler { hostBackCalls++ }
            Stage(reply, clock, onSelection = { selections += it })
        }
        composeRule.onNodeWithTag(MENU).performClick()
        composeRule.onNodeWithTag(SELECT).performClick()
        composeRule.onNodeWithText("选区保留完整正文。").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, hostBackCalls)
            assertEquals(null, selections.last())
            assertEquals(0, clock.player.state.value.beatIndex)
        }
        Espresso.pressBack()
        composeRule.runOnIdle { assertEquals(1, hostBackCalls) }
    }

    @Test
    fun roleplayScreen_inputDisplayAndOldQuickActions_neverSendOrRetry() {
        val raw = "<narration>" + "本地显示不改变生成目标。".repeat(12) +
                "</narration><dialogue>第二拍。</dialogue>"
        val message = companionD4Message(raw)
        val reply = companionD4Reply(message)
        val input = mutableStateOf("")
        val display = mutableStateOf(CompanionDisplayUiState())
        var sendCalls = 0
        var retryCalls = 0
        var generateCalls = 0
        var readingCalls = 0
        var appearanceCalls = 0
        var hostBackCalls = 0
        var phoneOwner: PhoneSnapshotOwnerType? = null
        val callbacks = screenCallbacks(
            onInput = { input.value = it },
            onSend = { sendCalls++ },
            onRetry = { retryCalls++ },
            onGenerate = { generateCalls++ },
            onBack = { hostBackCalls++ },
            onPhone = { phoneOwner = it },
        )
        val scenario = RoleplayScenario(
            id = COMPANION_D4_SCENARIO_ID,
            title = "合成当前场次",
            assistantId = COMPANION_D4_CHARACTER_ID,
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        )
        val settings = companionD4Settings()
        val messages = reply.beats.map { beat ->
            RoleplayMessageUiModel(
                uiItemId = beat.id,
                sourceMessageId = reply.source.messageId,
                contentType = beat.contentType,
                speaker = beat.speaker,
                speakerName = beat.speakerName,
                content = beat.content,
                companionReply = reply,
                companionBeat = beat,
            )
        }
        ui.render {
            RoleplayScreen(
                scenario = scenario,
                assistant = settings.assistants.single(),
                settings = settings,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList(),
                messages = messages,
                suggestions = emptyList(),
                input = input.value,
                inputFocusToken = 0L,
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
                isSending = false,
                isGeneratingSuggestions = false,
                isScenarioLoading = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                noticeMessage = null,
                errorMessage = null,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                callbacks = callbacks,
                companionDisplay = CompanionDisplayBinding(
                    display.value, onModeChange = { display.value = display.value.copy(mode = it) },
                    onOpenReading = { readingCalls++ }, onOpenAppearance = { appearanceCalls++ },
                    replyMessageId = reply.source.messageId,
                ),
            )
        }
        composeRule.onNodeWithTag(STAGE).assertDoesNotExist()
        composeRule.onNodeWithTag("companion_display_STAGE").performScrollTo().performClick()
        composeRule.onNodeWithTag(ADVANCE).performClick()
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
        composeRule.onNodeWithTag("roleplay_input_editor").performClick()
            .performTextInput("仍在输入，尚未发送。")
        ui.showIme()
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
        Espresso.pressBack()
        composeRule.waitUntil(5_000) { !ui.imeVisible() }
        composeRule.runOnIdle { assertEquals(0, hostBackCalls) }
        composeRule.onNodeWithTag("companion_display_LIST").performScrollTo().performClick()
        composeRule.onNodeWithTag(STAGE).assertDoesNotExist()
        val messageList = composeRule.onNodeWithTag("roleplay_scene_message_list")
        messageList.performTouchInput { swipeDown() }
        val expectedBody = reply.beats.first().content
        captureRoleplayLayoutDiagnostic("roleplay-list-before-body-query", expectedBody)
        val listBody = actualBodyText(expectedBody)
            .performScrollTo().assertIsDisplayed()
        val textLayout = ui.textLayout(listBody)
        val listBounds = messageList.fetchSemanticsNode().boundsInRoot
        val bodyNode = listBody.fetchSemanticsNode()
        val firstLineTop =
            bodyNode.layoutInfo.coordinates.positionInRoot().y + textLayout.getLineTop(0)
        messageList.performSemanticsAction(SemanticsActions.ScrollBy) {
            assertTrue(it(0f, firstLineTop - listBounds.top))
        }
        captureRoleplayLayoutDiagnostic("roleplay-list-before-complete-assertion", expectedBody)
        val completeFirstLine = assertCompleteBodyLine(listBody, messageList, line = 0)
        ui.captureCandidate("roleplay-list-complete-first-line", messageList, completeFirstLine)
        // 长段落高于视口时分别真实滚动展示首、尾，原文与所有行仍完整保留。
        messageList.performTouchInput { swipeUp() }
        val tailLayout = ui.textLayout(listBody)
        val lastLine = tailLayout.lineCount - 1
        val lastLineBottom =
            listBody.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y +
                    tailLayout.getLineBottom(lastLine)
        val tailViewportBottom = messageList.fetchSemanticsNode().boundsInRoot.bottom
        messageList.performSemanticsAction(SemanticsActions.ScrollBy) {
            assertTrue(it(0f, lastLineBottom - tailViewportBottom))
        }
        captureRoleplayLayoutDiagnostic("roleplay-list-before-tail-assertion", expectedBody)
        val completeLastLine = assertCompleteBodyLine(listBody, messageList, line = lastLine)
        ui.captureCandidate("roleplay-list-complete-last-line", messageList, completeLastLine)
        composeRule.onNodeWithTag("companion_display_STAGE").performScrollTo().performClick()
        composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
        composeRule.onNodeWithTag("companion_open_reading").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_open_visuals").performScrollTo().performClick()

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.roleplay_more_actions),
        ).performClick()
        listOf("日记", "语音", "查手机", "转账", "邀约", "礼物", "委托", "惩罚", "动态", "视频")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("查手机").performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.roleplay_phone_owner_character),
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(PhoneSnapshotOwnerType.CHARACTER, phoneOwner)
            assertEquals("仍在输入，尚未发送。", input.value)
            assertEquals(1, readingCalls)
            assertEquals(1, appearanceCalls)
            assertEquals(0, sendCalls)
            assertEquals(0, retryCalls)
            assertEquals(0, generateCalls)
            assertEquals(CompanionDisplayMode.STAGE, display.value.mode)
            assertEquals(null, display.value.visuals.preferredMode)
            assertEquals(raw, message.content)
            assertEquals(1L, message.memoryRevision)
            assertEquals(
                message.id,
                messages.single { it.companionBeat?.id == reply.beats.first().id }.sourceMessageId
            )
        }
    }

    @Test
    fun unreadableExpression_fallsBackToRealDefaultPixelsWithoutChangingSource() {
        val broken = ui.image("broken-expression", Color.RED).apply { writeText("not an image") }
        val fallback = ui.image("valid-default", Color.rgb(17, 109, 201))
        val resources = CompanionResolvedResources(
            defaultPortraits = listOf(CompanionResolvedImage("default", fallback.absolutePath)),
            expressionPortraits = mapOf(
                "happy" to listOf(
                    CompanionResolvedImage("broken", broken.absolutePath),
                    CompanionResolvedImage("default", fallback.absolutePath),
                ),
            ),
        )
        val reply =
            companionD4Reply(companionD4Message("<dialogue sprite=\"happy\">表情使用真实图像。</dialogue>"))
        val clock = player()
        ui.render { Stage(reply, clock, resources) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("companion_image_default"))
                .fetchSemanticsNodes().size == 1
        }
        val bitmap =
            composeRule.onNodeWithTag("companion_image_default").captureToImage().asAndroidBitmap()
        assertEquals(Color.rgb(17, 109, 201), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        composeRule.onNodeWithTag("companion_image_broken").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals("happy", reply.beats.single().spriteKey)
            assertEquals(reply.source, clock.player.state.value.source)
            assertEquals(1L, clock.player.state.value.source?.revision)
        }
    }

    @Test
    fun decorationOnly_keepsNameFallbackOutsideTheDecorativeImage() {
        val decor = ui.image("only-decor", Color.rgb(212, 167, 23))
        val reply = companionD4Reply(companionD4Message("<dialogue>主体仍是这个角色。</dialogue>"))
        val clock = player()
        ui.render {
            Stage(
                reply, clock, CompanionResolvedResources(
                    decorations = listOf(
                        CompanionResolvedImage(
                            "decor",
                            decor.absolutePath,
                            kind = CompanionImageKind.DECOR
                        )
                    ),
                )
            )
        }
        composeRule.onNode(
            hasText(COMPANION_D4_CHARACTER_NAME) and hasAnyAncestor(hasTestTag("companion_stage_portrait")),
        ).assertIsDisplayed()
        composeRule.onAllNodes(
            hasTestTag("companion_image_decor") and hasAnyAncestor(hasTestTag("companion_stage_portrait")),
        ).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(reply.source, clock.player.state.value.source) }
    }

    @Test
    fun narrowPlatformFont200_longBeatScrollsToItsEndWithReachableControls() {
        val last = "末段：这句话必须完整可读。"
        val body = "他慢慢讲述今天发生的事情，仍然坐在原来的窗边。".repeat(14) + "\n\n" + last
        val reply = companionD4Reply(
            companionD4Message("<narration>" + body + "</narration><dialogue>下一拍。</dialogue>"),
        )
        val clock = player()
        ui.withNarrowPlatformWindow("stage-320dp-font200", content = { Stage(reply, clock) }) {
            val advance = composeRule.onNodeWithTag(ADVANCE)
            advance.performClick()
            val text = composeRule.onNodeWithText(last, substring = true)
            val viewport = composeRule.onNodeWithTag("companion_stage_text_viewport")
            val environment = ui.assertNarrowPlatformText(text)
            val scroll = ui.scrollToEnd(composeRule.onNodeWithTag(SURFACE))
            val lastParagraph = ui.assertTextSuffixInViewport(text, last, viewport)
            listOf(ADVANCE, REPLAY, MENU).forEach {
                val action = composeRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
                ui.assertTouchTarget(action, composeRule.onNodeWithTag(STAGE))
            }
            assertCompleteProgress("1/2")
            ui.captureCandidate(
                "stage-320dp-font200-last-paragraph", composeRule.onNodeWithTag(STAGE),
                environment + "\n" + scroll + "\n" + lastParagraph
            )
            composeRule.runOnIdle {
                assertEquals(body, clock.player.state.value.visibleText)
                assertEquals(0, clock.player.state.value.beatIndex)
            }
            advance.performScrollTo().performClick()
            composeRule.onNodeWithTag(PROGRESS).assertTextEquals("2/2")
        }
    }

    @Test
    fun narrowRoleplayScreenFont200_stageInputQuickActionsAndIme_keepAWholeBodyLineAndNeverSend() {
        val body = "正文始终完整，键盘不会触发下一拍。".repeat(12)
        val reply = companionD4Reply(
            companionD4Message(
                "<narration>" + body + "</narration><dialogue>下一拍。</dialogue>",
            )
        )
        val input = mutableStateOf("")
        val display = mutableStateOf(CompanionDisplayUiState(mode = CompanionDisplayMode.STAGE))
        val settings = companionD4Settings()
        var sendCalls = 0
        var retryCalls = 0
        var generateCalls = 0
        var hostBackCalls = 0
        var phoneCalls = 0
        val callbacks = screenCallbacks(
            { input.value = it }, { sendCalls++ }, { retryCalls++ }, { generateCalls++ },
            { hostBackCalls++ }, { phoneCalls++ },
        )
        val messages = reply.beats.map { beat ->
            RoleplayMessageUiModel(
                uiItemId = beat.id,
                sourceMessageId = reply.source.messageId,
                contentType = beat.contentType,
                speaker = beat.speaker,
                speakerName = beat.speakerName,
                content = beat.content,
                companionReply = reply,
                companionBeat = beat,
            )
        }
        ui.withNarrowPlatformWindow("roleplay-stage-320dp-font200", content = {
            BackHandler { callbacks.navigation.onNavigateBack() }
            RoleplayScreen(
                scenario = RoleplayScenario(
                    id = COMPANION_D4_SCENARIO_ID,
                    title = "合成当前场次",
                    assistantId = COMPANION_D4_CHARACTER_ID,
                    interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE
                ),
                assistant = settings.assistants.single(),
                settings = settings,
                contextStatus = RoleplayContextStatus(),
                groupParticipants = emptyList(),
                messages = messages,
                suggestions = emptyList(),
                input = input.value,
                inputFocusToken = 0L,
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
                isSending = false,
                isGeneratingSuggestions = false,
                isScenarioLoading = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                noticeMessage = null,
                errorMessage = null,
                suggestionErrorMessage = null,
                pendingMemoryProposal = null,
                callbacks = callbacks,
                companionDisplay = CompanionDisplayBinding(
                    display.value,
                    onModeChange = { display.value = display.value.copy(mode = it) },
                    onOpenReading = {},
                    onOpenAppearance = {},
                    replyMessageId = reply.source.messageId,
                ),
            )
        }) {
            composeRule.onNodeWithTag(ADVANCE).performClick()
            composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
            captureRoleplayLayoutDiagnostic("roleplay-stage-before-display-assertion", body)
            val paragraphs = stageBodyParagraphs(body, reply.beats.first().speakerName)
            val text = paragraphs.first()
            text.assertIsDisplayed()
            val viewport = composeRule.onNodeWithTag("companion_stage_text_viewport")
            val environment = ui.assertNarrowPlatformText(text)
            val beforeIme = assertVisibleStageBodyLine(paragraphs, viewport)
            val beforeImeProgress = assertCompleteProgress("1/2")
            val editor = composeRule.onNodeWithTag("roleplay_input_editor").assertIsDisplayed()
            ui.assertTouchTarget(editor, composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG))
            editor.performClick().performTextInput("窄屏输入")
            ui.showIme()
            captureRoleplayLayoutDiagnostic("roleplay-stage-ime-before-line-assertion", body)
            val withIme = assertVisibleStageBodyLine(
                stageBodyParagraphs(body, reply.beats.first().speakerName), viewport,
            )
            val withImeProgress = assertCompleteProgress("1/2")
            editor.assertIsDisplayed()
            ui.assertTouchTarget(editor, composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG))
            ui.assertTouchTarget(
                composeRule.onNodeWithTag(ADVANCE),
                composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG)
            )
            val moreDescription = composeRule.activity.getString(R.string.roleplay_more_actions)
            ui.assertTouchTarget(
                composeRule.onNodeWithContentDescription(moreDescription),
                composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG)
            )
            composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
            ui.captureCandidate(
                "roleplay-stage-320dp-font200-ime",
                composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG),
                environment + "\nbeforeIme:\n" + beforeIme + "\nwithIme:\n" + withIme +
                        "\nbeforeImeProgress:\n" + beforeImeProgress +
                        "\nwithImeProgress:\n" + withImeProgress +
                        "\nimeVisible=" + ui.imeVisible()
            )
            Espresso.pressBack()
            composeRule.waitUntil(5_000) { !ui.imeVisible() }
            composeRule.runOnIdle { assertEquals(0, hostBackCalls) }
            val helper = composeRule.onNodeWithContentDescription("AI 帮写")
                .assertIsDisplayed()
            ui.assertTouchTarget(helper, composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG))
            helper.performClick()
            composeRule.onNodeWithTag("companion_stage_suggestions_dialog").assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNodeWithTag("companion_stage_suggestions_dialog").assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(0, hostBackCalls) }
            composeRule.onNodeWithContentDescription(moreDescription).performClick()
            listOf("日记", "语音", "查手机", "转账", "邀约", "礼物", "委托", "惩罚", "动态", "视频")
                .forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
            captureRoleplayLayoutDiagnostic("roleplay-stage-actions-before-line-assertion", body)
            val withActions = assertVisibleStageBodyLine(
                stageBodyParagraphs(body, reply.beats.first().speakerName), viewport,
            )
            val withActionsProgress = assertCompleteProgress("1/2")
            editor.assertIsDisplayed()
            ui.captureCandidate(
                "roleplay-stage-320dp-font200-quick-actions",
                composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG),
                environment + "\n" + withActions + "\nprogress:\n" + withActionsProgress
            )
            composeRule.onNodeWithText("查手机").performScrollTo().performClick()
            composeRule.onNode(isDialog()).assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(0, hostBackCalls) }
            composeRule.onNodeWithText(
                composeRule.activity.getString(R.string.roleplay_quick_actions_title),
            ).assertDoesNotExist()
            composeRule.onNodeWithTag(PROGRESS).assertTextEquals("1/2")
            Espresso.pressBack()
            composeRule.runOnIdle {
                assertEquals(1, hostBackCalls)
                assertEquals("窄屏输入", input.value)
                assertEquals(0, phoneCalls)
                assertEquals(0, sendCalls)
                assertEquals(0, retryCalls)
                assertEquals(0, generateCalls)
                assertEquals(reply.source.messageId, messages.first().sourceMessageId)
            }
        }
    }

    private fun assertIndependentRenderedTexts(texts: List<String>, clusters: List<String>) {
        texts.forEach { text ->
            var index = 0
            while (index < text.length) {
                val char = text[index]
                assertTrue("独立Text不能含孤立低代理", !char.isLowSurrogate())
                if (char.isHighSurrogate()) {
                    assertTrue("代理对必须存在于同一个Text", index + 1 < text.length)
                    assertTrue(text[index + 1].isLowSurrogate())
                    index += 2
                } else index++
            }
        }
        clusters.forEach { cluster ->
            assertTrue("完整grapheme须存在于一个实际Text：$cluster", texts.any { cluster in it })
        }
    }

    private fun stageBodyParagraphs(
        expectedBody: String,
        speakerLabel: String,
    ): List<SemanticsNodeInteraction> {
        val scope = hasAnyAncestor(hasTestTag("companion_stage_beat"))
        val nodes = composeRule.onAllNodes(
            scope and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().filter { node ->
            val content = node.config[SemanticsProperties.Text].joinToString("") { it.text }
            content.isNotEmpty() && content != speakerLabel
        }
        // 同一段文字可重复，按 Beat 内实际段序与节点身份定位，不用文本唯一匹配。
        val paragraphs = nodes.mapIndexed { index, node ->
            composeRule.onNode(
                scope and SemanticsMatcher("陪伴正文段 " + index) { it.id == node.id },
                useUnmergedTree = true,
            )
        }
        assertTrue("当前 Beat 必须绘制实际正文段", paragraphs.isNotEmpty())
        val layouts = paragraphs.map(ui::textLayout)
        assertEquals(
            "所有真实段拼接须等于完整原正文", expectedBody,
            layouts.joinToString("") { it.layoutInput.text.text })
        layouts.forEach { layout ->
            (0 until layout.lineCount).forEach {
                assertTrue("每段原文不能省略", !layout.isLineEllipsized(it))
            }
        }
        return paragraphs
    }

    private fun assertVisibleStageBodyLine(
        paragraphs: List<SemanticsNodeInteraction>,
        viewport: SemanticsNodeInteraction,
    ): String {
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val visible = paragraphs.withIndex().firstOrNull { (_, paragraph) ->
            val origin = paragraph.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot()
            val layout = ui.textLayout(paragraph)
            (0 until layout.lineCount).any {
                origin.y + layout.getLineTop(it) >= bounds.top - 1f &&
                        origin.y + layout.getLineBottom(it) <= bounds.bottom + 1f
            }
        }
        assertTrue("实际分段正文至少一整行可读", visible != null)
        val paragraph = requireNotNull(visible)
        return "paragraphIndex=" + paragraph.index + " paragraphCount=" + paragraphs.size +
                "\n" + assertCompleteBodyLine(paragraph.value, viewport)
    }

    private fun assertCompleteProgress(expected: String): String {
        val progress = composeRule.onNodeWithTag(PROGRESS, useUnmergedTree = true)
            .assertTextEquals(expected).assertIsDisplayed()
        val layout = ui.textLayout(progress)
        val origin = progress.fetchSemanticsNode().layoutInfo.coordinates.positionInRoot()
        val bounds = composeRule.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        (0 until layout.lineCount).forEach { line ->
            assertTrue("完整拍数不允许省略", !layout.isLineEllipsized(line))
            assertTrue(
                "拍数首字符完整可见",
                origin.x + layout.getLineLeft(line) >= bounds.left - 1f
            )
            assertTrue(
                "拍数末字符完整可见",
                origin.x + layout.getLineRight(line) <= bounds.right + 1f
            )
            assertTrue("拍数行顶完整可见", origin.y + layout.getLineTop(line) >= bounds.top - 1f)
            assertTrue(
                "拍数行底完整可见",
                origin.y + layout.getLineBottom(line) <= bounds.bottom + 1f
            )
        }
        return "progress=" + expected + " origin=" + origin + " layout=" + layout.size +
                " stage=" + bounds
    }

    private fun actualBodyText(expected: String): SemanticsNodeInteraction = composeRule.onNode(
        hasText(expected, substring = true) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    )

    private fun assertCompleteBodyLine(
        text: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction,
        line: Int? = null,
    ): String {
        val node = text.fetchSemanticsNode()
        val layout = ui.textLayout(text)
        val origin = node.layoutInfo.coordinates.positionInRoot()
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        assertTrue("正文必须包含真实行", layout.lineCount > 0)
        (0 until layout.lineCount).forEach {
            assertTrue("原正文不能省略", !layout.isLineEllipsized(it))
        }
        val candidates = line?.let { listOf(it) } ?: (0 until layout.lineCount).toList()
        val completeLine = candidates.firstOrNull {
            origin.y + layout.getLineTop(it) >= bounds.top - 1f &&
                    origin.y + layout.getLineBottom(it) <= bounds.bottom + 1f
        }
        assertTrue(
            "真实正文指定行须完整位于视口：line=" + line + " viewport=" + bounds,
            completeLine != null
        )
        val actualLine = requireNotNull(completeLine)
        return "completeLine=" + actualLine + "\ntextOrigin=" + origin +
                "\ntextLayoutSize=" + layout.size +
                "\nlineTop=" + (origin.y + layout.getLineTop(actualLine)) +
                "\nlineBottom=" + (origin.y + layout.getLineBottom(actualLine)) +
                "\nviewport=" + bounds
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun captureRoleplayLayoutDiagnostic(name: String, expectedText: String) {
        val host = composeRule.onNodeWithTag(COMPANION_D4_HOST_TAG)
        val hostNode = host.fetchSemanticsNode()
        val hostOwner = hostNode.root as? ViewRootForTest ?: error("主屏必须拥有 Android View")
        val evidence = StringBuilder()
        try {
            evidence.append("imeVisible=" + ui.imeVisible())
            evidence.append("\nhostPlatformFontScale=" + hostOwner.view.resources.configuration.fontScale)
            evidence.append("\nhostLayoutFontScale=" + hostNode.layoutInfo.density.fontScale)
            evidence.append("\nhostScreenWidthDp=" + hostOwner.view.resources.configuration.screenWidthDp)
            evidence.append("\nhostDensity=" + hostNode.layoutInfo.density.density)
            evidence.append("\nhostBounds=" + hostNode.boundsInRoot)
            evidence.append("\nexpectedTextLength=" + expectedText.length)
            listOf(
                ADVANCE, PROGRESS, "companion_stage_beat", "companion_stage_text_viewport", STAGE,
                "roleplay_scene_header", "roleplay_scene_title", "companion_scene_controls",
                "companion_display_toolbar", "roleplay_scene_suggestions", "roleplay_scene_input",
                "roleplay_input_editor", SURFACE,
                "roleplay_scene_message_viewport", "roleplay_scene_message_list",
            ).forEach { tag ->
                val unmerged = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                evidence.append("\n" + tag + ".count=" + unmerged.size)
                unmerged.forEachIndexed { index, node ->
                    appendNodeEvidence(evidence, tag + "[" + index + "]", node)
                }
                composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = false)
                    .fetchSemanticsNodes().forEachIndexed { index, node ->
                        appendNodeEvidence(evidence, tag + ".merged[" + index + "]", node)
                    }
            }
            val stageTexts = composeRule.onAllNodes(
                hasAnyAncestor(hasTestTag("companion_stage_beat")) and
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            evidence.append("\nstageText.count=" + stageTexts.size)
            stageTexts.forEachIndexed { index, node ->
                appendNodeEvidence(evidence, "stageText[" + index + "]", node)
            }
            val textNodes = composeRule.onAllNodes(
                hasText(expectedText, substring = true) and
                        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            evidence.append("\ntext.count=" + textNodes.size)
            textNodes.forEachIndexed { index, node ->
                appendNodeEvidence(evidence, "text[" + index + "]", node)
            }
            val visibleTexts = composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true,
            ).fetchSemanticsNodes()
            evidence.append("\nallText.count=" + visibleTexts.size)
            visibleTexts.forEachIndexed { index, node ->
                appendNodeEvidence(evidence, "allText[" + index + "]", node)
            }
            val helpers =
                composeRule.onAllNodes(hasContentDescription("AI 帮写"), useUnmergedTree = true)
                    .fetchSemanticsNodes()
            evidence.append("\naiHelper.count=" + helpers.size)
            helpers.forEachIndexed { index, node ->
                appendNodeEvidence(evidence, "aiHelper[" + index + "]", node)
            }
        } finally {
            // 全文节点缺失是诊断结果；取图不依赖正文存在或后续断言成功。
            ui.captureCandidate(name, host, evidence.toString())
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun appendNodeEvidence(evidence: StringBuilder, label: String, node: SemanticsNode) {
        val coordinates = node.layoutInfo.coordinates
        evidence.append("\n" + label + ".bounds=" + node.boundsInRoot)
        evidence.append(" semanticPosition=" + node.positionInRoot + " semanticSize=" + node.size)
        evidence.append(" density=" + node.layoutInfo.density.density)
        evidence.append(" fontScale=" + node.layoutInfo.density.fontScale)
        evidence.append(" disabled=" + node.config.contains(SemanticsProperties.Disabled))
        evidence.append(" onClick=" + node.config.contains(SemanticsActions.OnClick))
        if (coordinates.isAttached) {
            evidence.append(" ownerPosition=" + coordinates.positionInRoot() + " ownerSize=" + coordinates.size)
        } else evidence.append(" ownerDetached=true")
        (node.root as? ViewRootForTest)?.let {
            evidence.append(" platformFontScale=" + it.view.resources.configuration.fontScale)
        }
        if (node.config.contains(SemanticsProperties.Text)) {
            evidence.append(" text=" + node.config[SemanticsProperties.Text].joinToString(" | ") { it.text })
        }
        if (node.config.contains(SemanticsProperties.EditableText)) {
            evidence.append(" editableText=" + node.config[SemanticsProperties.EditableText].text)
        }
        if (node.config.contains(SemanticsActions.GetTextLayoutResult)) {
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.runOnUiThread {
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            }
            evidence.append("\n" + label + ".textLayoutCount=" + layouts.size)
            if (coordinates.isAttached) layouts.forEachIndexed { layoutIndex, layout ->
                val origin = coordinates.positionInRoot()
                evidence.append("\n" + label + ".layout[" + layoutIndex + "].size=" + layout.size)
                evidence.append(" fontScale=" + layout.layoutInput.density.fontScale)
                evidence.append(" textLength=" + layout.layoutInput.text.length + " lineCount=" + layout.lineCount)
                for (line in 0 until layout.lineCount) {
                    evidence.append(
                        "\n" + label + ".line[" + line + "].top=" + (origin.y + layout.getLineTop(
                            line
                        ))
                    )
                    evidence.append(" bottom=" + (origin.y + layout.getLineBottom(line)))
                    evidence.append(" ellipsized=" + layout.isLineEllipsized(line))
                }
            }
        }
    }

    private fun player(): CompanionD4Player = CompanionD4Player().also { players += it }

    private fun observationSnapshot(timeLabel: String): CompanionPresentationSnapshot =
        CompanionPresentationSnapshot(
            sourceSettingsRevision = 7L,
            observation = CompanionObservationSettings(
                enabled = true,
                fields = companionBuiltinObservationFields().map {
                    if (it.id == "time") it.copy(label = timeLabel) else it
                },
            ),
        )

    @Composable
    private fun Stage(
        reply: ParsedCompanionReply,
        clock: CompanionD4Player,
        resources: CompanionResolvedResources = CompanionResolvedResources(),
        onSelection: (CompanionSpeechSelection?) -> Unit = {},
    ) {
        CompanionStage(
            reply, clock.player, CompanionVisualSettings(), resources,
            COMPANION_D4_CHARACTER_NAME, 1f, Modifier.fillMaxSize(), onSpeechSelection = onSelection
        )
    }

    private fun screenCallbacks(
        onInput: (String) -> Unit,
        onSend: () -> Unit,
        onRetry: () -> Unit,
        onGenerate: () -> Unit,
        onBack: () -> Unit,
        onPhone: (PhoneSnapshotOwnerType) -> Unit,
    ): RoleplayScreenCallbacks = RoleplayScreenCallbacks(
        message = RoleplayMessageCallbacks(
            onInputChange = onInput, onSend = { onSend() },
            onRequestGroupParticipantReply = { onSend() }, onCancelSending = {},
            onRetryTurn = { onRetry() }, onRetryAiPhoto = { _, _ -> onRetry() },
            onEditUserMessage = {}, onQuoteMessage = { _, _, _ -> }, onPokeMessageAvatar = {},
            onClearQuotedMessage = {}, onDeleteMessage = {}, onRecallMessage = {},
            onCaptureOnlineChat = {}, onSendSpecialPlay = { onSend() },
            onSendVoiceMessage = { onSend() }, onConfirmTransferReceipt = {},
        ),
        suggestion = RoleplaySuggestionCallbacks(onGenerate, {}, {}),
        navigation = RoleplayNavigationCallbacks(
            onOpenDiary = {}, onOpenPhoneCheck = onPhone, onOpenMoments = {}, onOpenVideoCall = {},
            onOpenMailbox = {}, onOpenWallet = {}, onOpenReadingMode = {}, onOpenSettings = {},
            onNavigateBack = onBack,
        ),
        session = RoleplaySessionCallbacks({}, {}, {}, {}),
        group = RoleplayGroupCallbacks({}, {}, {}),
        ui = RoleplayUiCallbacks({}, {}),
    )

    companion object {
        private const val STAGE = "companion_stage"
        private const val SURFACE = "companion_stage_surface"
        private const val ADVANCE = "companion_stage_advance"
        private const val REPLAY = "companion_stage_replay"
        private const val MENU = "companion_stage_menu"
        private const val SELECT = "companion_stage_select"
        private const val PROGRESS = "companion_stage_progress"
    }
}
