package com.example.myapplication.ui.screen.roleplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.CompanionEncounter
import com.example.myapplication.model.CompanionEncounterState
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.CompanionPendingResult
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.ui.navigation.AppRoutes
import com.example.myapplication.ui.navigation.CompanionRouteContent
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.RoleplayCompanionUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用生产 wrapper/bar 与原生 Dialog；持久权限和零 HTTP 由 Room/VM 回归验证。 */
@RunWith(AndroidJUnit4::class)
class CompanionPendingResultsNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun originalOnlineRoute_pendingRowsSaveExactAttemptsAndBackClosesOnlyDialog() {
        val oldA = pending("attempt-a", "ended-a", text = "旧场 A 的完整回复")
        val oldC = pending(
            "attempt-c",
            "ended-c",
            kind = CompanionTurnKind.CONTINUE,
            text = "旧场 C 的继续回复",
        )
        val fixture = Fixture(ordinaryState(listOf(oldA, oldC)), "当前原线上会话")
        render(fixture, AppRoutes.roleplayPlay(SCENARIO_ID))
        enterRoute()

        composeRule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("companion_pending_results")
            .assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("待保存结果").assertIsDisplayed()
        saveButton(oldC.attemptId).performScrollTo().performClick()
        saveButton(oldA.attemptId).performScrollTo().performClick()
        Espresso.pressBack()

        composeRule.onNodeWithText("待保存结果").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_pending_results").assertIsDisplayed()
        composeRule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(oldC.attemptId, oldA.attemptId), fixture.savedAttempts)
            assertEquals(CompanionEntryPhase.NONE, fixture.state.value.phase)
            assertNull(fixture.state.value.encounter)
            assertEquals(AppRoutes.ROLEPLAY_PLAY, fixture.navigation.currentDestination?.route)
            assertTrue(fixture.otherActions.isEmpty())
        }
        Espresso.pressBack()
        composeRule.onNodeWithText("打开当前会话").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(fixture.otherActions.isEmpty()) }
    }

    @Test
    fun activeNewScene_savesOldResultOnceWhileBusyAndKeepsItsRecoveryInTheModal() {
        val oldA = pending("old-a-attempt", "ended-a", text = "A 场已经收到的完整结果")
        val fixture = Fixture(activeState(listOf(oldA)), "当前 B 场次", markBusyOnSave = true)
        render(fixture, AppRoutes.roleplayCompanion(SCENARIO_ID, "new-b-entry"))
        enterRoute()
        composeRule.onNodeWithTag("companion_pending_results").performClick()

        saveButton(oldA.attemptId).performScrollTo().performClick()
        saveButton(oldA.attemptId).assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithText("关闭").assertIsDisplayed().assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf(oldA.attemptId), fixture.savedAttempts)
            assertEquals("active-b", fixture.state.value.encounter?.id)
            assertEquals(CompanionEntryPhase.ACTIVE, fixture.state.value.phase)
            assertTrue(fixture.otherActions.isEmpty())
            fixture.state.value = fixture.state.value.copy(
                isBusy = false,
                errorMessage = "补保存失败，原结果仍保留，请重试。",
            )
        }
        composeRule.onNodeWithText("补保存失败，原结果仍保留，请重试。")
            .performScrollTo().assertIsDisplayed()
        saveButton(oldA.attemptId).performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithText("关闭").assertIsEnabled().performClick()
        composeRule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("待保存结果").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(oldA.attemptId, oldA.attemptId), fixture.savedAttempts)
            assertEquals("active-b", fixture.state.value.encounter?.id)
            assertEquals(AppRoutes.ROLEPLAY_COMPANION, fixture.navigation.currentDestination?.route)
            assertTrue(fixture.otherActions.isEmpty())
        }
        Espresso.pressBack()
        composeRule.onNodeWithText("打开当前会话").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("back"), fixture.otherActions) }
    }

    @Test
    fun foreignScenarioWhileDialogIsOpen_hidesOldAndForeignResultActions() {
        val oldA = pending("visible-attempt", "ended-a", text = "原场可见的待保存文字")
        val foreign = pending("foreign-attempt", "foreign-encounter", text = "其它角色的私有结果")
        val fixture = Fixture(ordinaryState(listOf(oldA)), "当前普通会话")
        render(fixture, AppRoutes.roleplayPlay(SCENARIO_ID))
        enterRoute()
        composeRule.onNodeWithTag("companion_pending_results").performClick()
        composeRule.onNodeWithText("原场可见的待保存文字").assertIsDisplayed()

        composeRule.runOnIdle {
            fixture.state.value =
                ordinaryState(listOf(foreign)).copy(scenarioId = "foreign-scenario")
        }
        composeRule.onNodeWithText("待保存结果").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_pending_results").assertDoesNotExist()
        saveButton(oldA.attemptId).assertDoesNotExist()
        saveButton(foreign.attemptId).assertDoesNotExist()
        composeRule.onNodeWithText("原场可见的待保存文字").assertDoesNotExist()
        composeRule.onNodeWithText("其它角色的私有结果").assertDoesNotExist()
        composeRule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("打开当前会话").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fixture.savedAttempts.isEmpty())
            assertTrue(fixture.otherActions.isEmpty())
        }
    }

    @Test
    fun narrowLargeFont_lastResultSaveAndCloseRemainReachableWithFullTouchTargets() {
        val results = (1..6).map { index ->
            pending(
                "narrow-attempt-$index",
                "ended-$index",
                text = "第 $index 份完整结果。" +
                        "角色在窗边回顾当天的片段，停下来等待回应。".repeat(10),
            )
        }
        val fixture = Fixture(ordinaryState(results), "窄屏原线上会话")
        render(fixture, AppRoutes.roleplayPlay(SCENARIO_ID), narrow = true)
        enterRoute()
        val hostBounds = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        assertEquals(320f, (hostBounds.right - hostBounds.left).value, 1f)

        composeRule.onNodeWithTag("companion_pending_results")
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        saveButton(results.last().attemptId).performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val dialogBounds = composeRule.onNode(isDialog()).getUnclippedBoundsInRoot()
        assertTrue((dialogBounds.right - dialogBounds.left).value <= 321f)
        captureDialogCandidate()

        saveButton(results.last().attemptId).performClick()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("待保存结果").assertDoesNotExist()
        composeRule.onNodeWithTag(CONTENT_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(results.last().attemptId), fixture.savedAttempts)
            assertTrue(fixture.otherActions.isEmpty())
        }
    }

    private fun render(fixture: Fixture, destination: String, narrow: Boolean = false) {
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                fontScale = if (narrow) 2f else 1f
                if (narrow) {
                    screenWidthDp = 320
                    screenHeightDp = 600
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    configuration.fontScale
                ),
            ) {
                ChatAppTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(
                            (if (narrow) Modifier.width(320.dp)
                                .height(600.dp) else Modifier.fillMaxSize())
                                .testTag(HOST_TAG),
                        ) {
                            val navigation = rememberNavController()
                            fixture.navigation = navigation
                            NavHost(navigation, startDestination = "pending-results-origin") {
                                composable("pending-results-origin") {
                                    TextButton(onClick = { navigation.navigate(destination) }) {
                                        Text("打开当前会话")
                                    }
                                }
                                listOf(AppRoutes.ROLEPLAY_PLAY, AppRoutes.ROLEPLAY_COMPANION)
                                    .forEach { route ->
                                        composable(route) { entry ->
                                            CompanionRouteContent(
                                                scenarioId = Uri.decode(
                                                    entry.arguments?.getString("scenarioId")
                                                        .orEmpty()
                                                ),
                                                routeEntryId = entry.arguments?.getString("companionEntryId")
                                                    ?.let(Uri::decode),
                                                state = fixture.state.value,
                                                characterName = "合成角色",
                                                callbacks = CompanionEntryCallbacks(
                                                    onChooseOpening = { fixture.otherActions += "opening" },
                                                    onRegenerate = { fixture.otherActions += "regenerate" },
                                                    onAccept = { fixture.otherActions += "accept" },
                                                    onRecover = { fixture.otherActions += "recover" },
                                                    onCancel = { fixture.otherActions += "cancel" },
                                                    onBack = {
                                                        fixture.otherActions += "back"
                                                        navigation.popBackStack()
                                                    },
                                                    onRead = { fixture.otherActions += "read" },
                                                    onSettings = { fixture.otherActions += "settings" },
                                                    onSaveZone = { fixture.otherActions += "save-zone" },
                                                    onSavePendingResult = { attemptId ->
                                                        fixture.savedAttempts += attemptId
                                                        if (fixture.markBusyOnSave) {
                                                            fixture.state.value =
                                                                fixture.state.value.copy(isBusy = true)
                                                        }
                                                    },
                                                ),
                                            ) {
                                                Text(
                                                    fixture.contentLabel,
                                                    modifier = Modifier.testTag(CONTENT_TAG)
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enterRoute() {
        composeRule.onNodeWithText("打开当前会话").performClick()
        composeRule.runOnIdle { }
    }

    private fun saveButton(attemptId: String) =
        composeRule.onNodeWithTag("companion_save_pending_$attemptId")

    private fun ordinaryState(results: List<CompanionPendingResult>) = RoleplayCompanionUiState(
        scenarioId = SCENARIO_ID,
        phase = CompanionEntryPhase.NONE,
        pendingResults = results,
    )

    private fun activeState(results: List<CompanionPendingResult>) = ordinaryState(results).copy(
        entryId = "new-b-entry",
        phase = CompanionEntryPhase.ACTIVE,
        encounter = CompanionEncounter(
            id = "active-b",
            context = MemoryRuntimeContext(
                characterId = "pending-character",
                spaceId = "pending-space",
                conversationId = "current-conversation",
                scenarioId = SCENARIO_ID,
                runId = "current-run",
                personaKey = "current-persona",
            ),
            openingMode = CompanionOpeningMode.DIRECT,
            state = CompanionEncounterState.ACTIVE,
            revision = 2,
            createdAt = 1_800_000_000_000,
        ),
    )

    private fun pending(
        id: String,
        encounterId: String,
        kind: CompanionTurnKind = CompanionTurnKind.INPUT,
        text: String,
    ) = CompanionPendingResult(
        attemptId = id,
        encounterId = encounterId,
        turnKind = kind,
        createdAt = 1_799_000_000_000,
        rawReply = "<dialogue>$text</dialogue>",
    )

    private fun captureDialogCandidate() {
        val bitmap = composeRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val directory = File(composeRule.activity.cacheDir, "companion-ui-review")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "pending-results-320dp-font200-last-save-dialog.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private class Fixture(
        initial: RoleplayCompanionUiState,
        val contentLabel: String,
        val markBusyOnSave: Boolean = false,
    ) {
        val state = mutableStateOf(initial)
        val savedAttempts = mutableListOf<String>()
        val otherActions = mutableListOf<String>()
        lateinit var navigation: NavHostController
    }

    private companion object {
        const val SCENARIO_ID = "pending-scenario"
        const val HOST_TAG = "companion_pending_test_host"
        const val CONTENT_TAG = "companion_pending_current_content"
    }
}
