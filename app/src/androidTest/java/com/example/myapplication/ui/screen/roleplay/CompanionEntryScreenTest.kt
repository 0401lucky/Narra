package com.example.myapplication.ui.screen.roleplay

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.CompanionEncounter
import com.example.myapplication.model.CompanionEncounterState
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.CompanionTurn
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.CompanionRecoveryAction
import com.example.myapplication.viewmodel.RoleplayCompanionUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 只验证实际控件与回调；请求数、提交和进程恢复由 Room/HTTP/VM 回归验证。 */
@RunWith(AndroidJUnit4::class)
class CompanionEntryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun preparation_onlyExplicitOpeningClicksChooseTheirOwnMode() {
        val calls = Calls()
        renderEntry(mutableStateOf(preparing()), calls)

        composeRule.runOnIdle { assertTrue(calls.events.isEmpty()) }
        listOf(
            "companion_approach" to CompanionOpeningMode.APPROACH,
            "companion_invite" to CompanionOpeningMode.INVITE,
            "companion_direct" to CompanionOpeningMode.DIRECT,
        ).forEach { (tag, _) ->
            composeRule.onNodeWithTag(tag).performScrollTo()
                .assertIsDisplayed().assertIsEnabled()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                .performClick()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf("opening:APPROACH", "opening:INVITE", "opening:DIRECT"),
                calls.events,
            )
        }
    }

    @Test
    fun missingConversation_offersBackWithoutOpeningOrReadActions() {
        val calls = Calls()
        renderEntry(
            mutableStateOf(
                RoleplayCompanionUiState(
                    phase = CompanionEntryPhase.PREPARE,
                    errorMessage = "角色已不存在，请重新选择。",
                ),
            ),
            calls,
        )

        composeRule.onNodeWithTag("companion_error").assertIsDisplayed()
        composeRule.onNodeWithTag("companion_direct").assertDoesNotExist()
        composeRule.onNodeWithText("查看记录").assertIsNotEnabled()
        composeRule.onNodeWithText("场景设置").assertIsNotEnabled()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertEquals(listOf("back"), calls.events) }
    }

    @Test
    fun acceptingPreview_disablesRepeatedAcceptAndKeepsCancelAvailable() {
        val state = mutableStateOf(preview())
        val calls = Calls().apply {
            afterAccept = {
                state.value = state.value.copy(
                    phase = CompanionEntryPhase.ACCEPTING,
                    isBusy = true,
                )
            }
        }
        renderEntry(state, calls)

        composeRule.onNodeWithTag("companion_preview")
            .assertTextEquals("角色拉开椅子。\n\n请坐一会儿。")
        composeRule.onNodeWithTag("companion_accept").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_accept").assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.onNodeWithTag("companion_direct").performScrollTo()
            .assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithText("停止").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(listOf("accept", "cancel"), calls.events) }
    }

    @Test
    fun failedPreviewSave_exposesStorageRecoveryWithoutRegenerating() {
        val state = mutableStateOf(
            preview().copy(
                recoveryAction = CompanionRecoveryAction.ACCEPT,
                errorMessage = "开场尚未保存，请重试接受。",
            ),
        )
        val calls = Calls()
        renderEntry(state, calls)

        composeRule.onNodeWithText("重新生成开场").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_recover").performScrollTo()
            .assertTextContains("重试接受开场").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("recover"), calls.events)
            state.value = state.value.copy(
                recoveryAction = CompanionRecoveryAction.SAVE,
                errorMessage = "结果尚未写入存储，请重试保存。",
            )
        }

        composeRule.onNodeWithTag("companion_preview").assertExists()
        composeRule.onNodeWithTag("companion_accept").assertDoesNotExist()
        composeRule.onNodeWithText("重新生成开场").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_recover").performScrollTo()
            .assertTextContains("重试保存").performClick()
        composeRule.runOnIdle { assertEquals(listOf("recover", "recover"), calls.events) }
    }

    @Test
    fun generatingPreview_disablesOpeningAndZoneSaveButCanStop() {
        val calls = Calls()
        renderEntry(
            mutableStateOf(
                preparing().copy(phase = CompanionEntryPhase.GENERATING, isBusy = true),
            ),
            calls,
        )

        listOf("companion_approach", "companion_invite", "companion_direct").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
                .performTouchInput { click() }
        }
        composeRule.onNodeWithTag("companion_zone").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("保存时区").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("停止").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("cancel"), calls.events) }
    }

    @Test
    fun preparationNavigationAndSystemBack_doNotRequestAnOpening() {
        val calls = Calls()
        renderEntry(mutableStateOf(preview()), calls)

        composeRule.onNodeWithText("查看记录").performClick()
        composeRule.onNodeWithText("场景设置").performClick()
        composeRule.onNodeWithText("返回").performClick()
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(listOf("read", "settings", "back", "back"), calls.events)
        }
    }

    @Test
    fun activeActions_keepContinueRerollNewEncounterAndRecoveryDistinct() {
        val state = mutableStateOf(active())
        val calls = Calls()
        render { CompanionSceneActions(state.value, calls.sceneCallbacks) }

        composeRule.runOnIdle { assertTrue(calls.events.isEmpty()) }
        composeRule.onNodeWithTag("companion_continue").performClick()
        composeRule.onNodeWithTag("companion_regenerate").performClick()
        composeRule.onNodeWithTag("companion_new_encounter").performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(recoveryAction = CompanionRecoveryAction.SAVE)
        }
        composeRule.onNodeWithTag("companion_continue").assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.onNodeWithTag("companion_regenerate").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_recover").assertTextContains("重试保存").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("continue", "regenerate", "new-encounter", "recover"), calls.events)
            state.value = state.value.copy(isBusy = true)
        }
        composeRule.onNodeWithTag("companion_recover").assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.onNodeWithTag("companion_new_encounter").assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(4, calls.events.size) }
    }

    @Test
    fun incompleteResult_systemBackClosesOnlyTheDialog() {
        val state = active().copy(
            preview = "尚未完成的合成剧情片段",
            completionStatus = PromptCompletionStatus.TRUNCATED,
            recoveryAction = CompanionRecoveryAction.GENERATE,
        )
        val calls = Calls()
        var outerBackCalls = 0
        render {
            BackHandler { outerBackCalls++ }
            CompanionSceneActions(state, calls.sceneCallbacks)
        }

        composeRule.onNodeWithText("查看未完成结果").performClick()
        composeRule.onNodeWithText("尚未完成的合成剧情片段").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("尚未完成的合成剧情片段").assertDoesNotExist()
        composeRule.onNodeWithText("查看未完成结果").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, outerBackCalls)
            assertTrue(calls.events.isEmpty())
        }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(1, outerBackCalls)
            assertTrue(calls.events.isEmpty())
        }
    }

    @Test
    fun narrowLargeFont_longPreviewAndFailedZoneSave_keepActionsAndDraftReachable() {
        val state = mutableStateOf(
            preview().copy(
                preview = "<narration>" +
                        (1..20).joinToString("\n") { "第 $it 段：窗边的光落在桌面，角色安静地等待回应。" } +
                        "</narration><dialogue>请坐一会儿。</dialogue>",
            ),
        )
        val calls = Calls().apply {
            afterSaveZone = {
                state.value = state.value.copy(errorMessage = "保存失败，请保留草稿重试。")
            }
        }
        renderEntry(state, calls, narrow = true, fontScale = 2f)

        val hostBounds = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        assertEquals(
            320f,
            (hostBounds.right - hostBounds.left).value,
            1f,
        )
        listOf("companion_approach", "companion_invite", "companion_direct", "companion_accept")
            .forEach { tag ->
                composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
        captureCandidate("entry-320dp-font200-preview-actions")

        composeRule.onNodeWithTag("companion_zone").performScrollTo()
            .performClick().performTextReplacement("Asia/Tokyo")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("保存时区").performScrollTo()
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("companion_zone").performScrollTo()
            .assertTextContains("Asia/Tokyo")
        composeRule.onNodeWithText("保存时区").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("Asia/Tokyo", "Asia/Tokyo"), calls.savedZones)
            assertEquals(listOf("save-zone", "save-zone"), calls.events)
        }
        captureCandidate("entry-320dp-font200-zone-save-retry")
    }

    @Test
    fun keyboardBack_preservesZoneDraftBeforeLeavingPreparation() {
        val calls = Calls()
        renderEntry(mutableStateOf(preparing()), calls)
        composeRule.onNodeWithTag("companion_zone").performScrollTo()
            .performClick().performTextReplacement("Europe/Paris")
        composeRule.runOnIdle {
            WindowInsetsControllerCompat(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).show(WindowInsetsCompat.Type.ime())
        }
        composeRule.waitUntil(5_000) { imeVisible() }

        Espresso.pressBack()
        composeRule.waitUntil(5_000) { !imeVisible() }
        composeRule.onNodeWithTag("companion_zone").assertTextContains("Europe/Paris")
        composeRule.runOnIdle { assertTrue(calls.events.isEmpty()) }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(listOf("back"), calls.events)
            assertTrue(calls.savedZones.isEmpty())
        }
    }

    private fun renderEntry(
        state: MutableState<RoleplayCompanionUiState>,
        calls: Calls,
        narrow: Boolean = false,
        fontScale: Float = 1f,
    ) {
        render(narrow, fontScale) {
            CompanionEntryScreen(state.value, "合成角色", calls.entryCallbacks)
        }
    }

    private fun render(
        narrow: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                ChatAppTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(
                            (if (narrow) Modifier.width(320.dp) else Modifier.fillMaxWidth())
                                .fillMaxHeight().testTag(HOST_TAG),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun captureCandidate(name: String) {
        val bitmap = composeRule.onNodeWithTag(HOST_TAG).captureToImage().asAndroidBitmap()
        val directory = File(composeRule.activity.cacheDir, "companion-ui-review")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun preparing(): RoleplayCompanionUiState {
        val encounter = CompanionEncounter(
            id = "ui-encounter",
            context = MemoryRuntimeContext(
                characterId = "ui-character",
                spaceId = "ui-space",
                conversationId = "ui-conversation",
                scenarioId = "ui-scenario",
                runId = "ui-run",
                personaKey = "ui-persona",
            ),
            openingMode = CompanionOpeningMode.APPROACH,
            state = CompanionEncounterState.DRAFT,
            revision = 1,
            createdAt = 1_000,
        )
        return RoleplayCompanionUiState(
            scenarioId = encounter.context.scenarioId,
            entryId = "ui-entry",
            encounter = encounter,
            phase = CompanionEntryPhase.PREPARE,
            turn = CompanionTurn(
                id = "ui-turn",
                encounterId = encounter.id,
                ordinal = 0,
                kind = CompanionTurnKind.OPENING,
                inputMessageId = null,
                replyMessageId = null,
                inputRevision = null,
                replyRevision = null,
                mutationRevision = 0,
                committedRevision = 0,
                selectedAttemptId = null,
                activeAttemptId = "ui-attempt",
                createdAt = 1_001,
            ),
            attemptId = "ui-attempt",
        )
    }

    private fun preview(): RoleplayCompanionUiState = preparing().copy(
        phase = CompanionEntryPhase.PREVIEW,
        preview = "<narration>角色拉开椅子。</narration><dialogue>请坐一会儿。</dialogue>",
        completionStatus = PromptCompletionStatus.COMPLETED,
    )

    private fun active(): RoleplayCompanionUiState {
        val state = preparing()
        return state.copy(
            phase = CompanionEntryPhase.ACTIVE,
            encounter = state.encounter?.copy(
                state = CompanionEncounterState.ACTIVE,
                openingMode = CompanionOpeningMode.DIRECT,
            ),
            turn = state.turn?.copy(
                kind = CompanionTurnKind.INPUT,
                inputMessageId = "ui-input",
                replyMessageId = "ui-reply",
                inputRevision = 1,
                replyRevision = 1,
                committedRevision = 1,
                selectedAttemptId = "ui-attempt",
                activeAttemptId = null,
            ),
        )
    }

    private class Calls {
        val events = mutableListOf<String>()
        val savedZones = mutableListOf<String>()
        var afterAccept: (() -> Unit)? = null
        var afterSaveZone: (() -> Unit)? = null
        val entryCallbacks = CompanionEntryCallbacks(
            onChooseOpening = { events += "opening:" + it.name },
            onRegenerate = { events += "regenerate" },
            onAccept = {
                events += "accept"
                afterAccept?.invoke()
            },
            onRecover = { events += "recover" },
            onCancel = { events += "cancel" },
            onBack = { events += "back" },
            onRead = { events += "read" },
            onSettings = { events += "settings" },
            onSaveZone = {
                savedZones += it
                events += "save-zone"
                afterSaveZone?.invoke()
            },
        )
        val sceneCallbacks = CompanionSceneCallbacks(
            onContinue = { events += "continue" },
            onRegenerate = { events += "regenerate" },
            onRecover = { events += "recover" },
            onNewEncounter = { events += "new-encounter" },
        )
    }

    private companion object {
        const val HOST_TAG = "companion_ui_test_host"
    }
}
