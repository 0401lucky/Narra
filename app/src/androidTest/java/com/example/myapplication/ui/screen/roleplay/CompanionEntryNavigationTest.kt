package com.example.myapplication.ui.screen.roleplay

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.CompanionEncounter
import com.example.myapplication.model.CompanionEncounterState
import com.example.myapplication.model.CompanionEntryOrigin
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.ui.navigation.AppRoutes
import com.example.myapplication.ui.navigation.CompanionRouteContent
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.CompanionRecoveryAction
import com.example.myapplication.viewmodel.RoleplayCompanionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 在原生 NavHost 中消费生产路由 wrapper；不复制其准备页/Back 判定，也不初始化 AppGraph。 */
@RunWith(AndroidJUnit4::class)
class CompanionEntryNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oldRoute_restoresPreviewAndReturnsFromReadingAndSettingsWithoutRequests() {
        val scenarioId = "场景/甲?x&y#见面"
        val fixture = NavigationFixture(preview(scenarioId))
        renderNavigation(fixture, AppRoutes.roleplayPlay(scenarioId))

        composeRule.onNodeWithText("进入合成会话").performClick()
        composeRule.onNodeWithTag("companion_preview").assertIsDisplayed()
            .assertTextContains("本场待接受预览")
        composeRule.onNodeWithTag("ordinary_content").assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(fixture.navigation.currentBackStackEntry?.arguments?.getString("companionEntryId"))
            assertTrue(fixture.events.isEmpty())
        }

        composeRule.onNodeWithText("查看记录").performClick()
        composeRule.onNodeWithTag("reading_destination").assertIsDisplayed()
            .assertTextEquals("阅读原会话：$scenarioId")
        Espresso.pressBack()
        composeRule.onNodeWithTag("companion_preview").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("read"), fixture.events) }

        composeRule.onNodeWithText("场景设置").performClick()
        composeRule.onNodeWithTag("settings_destination").assertIsDisplayed()
            .assertTextEquals("设置原会话：$scenarioId")
        composeRule.onNodeWithText("返回原会话").performClick()
        composeRule.onNodeWithTag("companion_preview").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("prepared-entry", fixture.state.value.entryId)
            assertEquals("origin-conversation", fixture.state.value.origin?.conversationId)
            assertEquals(
                RoleplayInteractionMode.ONLINE_PHONE,
                fixture.state.value.origin?.interactionMode
            )
            assertEquals(listOf("read", "settings"), fixture.events)
        }

        Espresso.pressBack()
        composeRule.onNodeWithText("进入合成会话").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("read", "settings", "back"), fixture.events)
            assertEquals(listOf("origin-conversation"), fixture.leftOrigins)
        }
    }

    @Test
    fun explicitEntry_hidesForeignPreviewAndDialogBackDoesNotLeaveActiveScene() {
        val scenarioId = "新场景/乙"
        val routeEntryId = "入口/第二次?选择"
        val fixture = NavigationFixture(
            preview("其它场景").copy(preview = "其它角色的预览不得出现"),
        )
        renderNavigation(
            fixture,
            AppRoutes.roleplayCompanion(scenarioId, routeEntryId),
            includeSceneActions = true,
        )

        composeRule.onNodeWithText("进入合成会话").performClick()
        composeRule.onNodeWithTag("companion_preview").assertDoesNotExist()
        composeRule.onNodeWithText("其它角色的预览不得出现").assertDoesNotExist()
        composeRule.onNodeWithTag("ordinary_content").assertDoesNotExist()
        composeRule.onNodeWithText("查看记录").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(
                routeEntryId,
                fixture.navigation.currentBackStackEntry?.arguments
                    ?.getString("companionEntryId")?.let(Uri::decode),
            )
            fixture.state.value =
                preview(scenarioId).copy(entryId = "旧入口", preview = "同会话旧入口的候选")
        }
        composeRule.onNodeWithTag("companion_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("ordinary_content").assertDoesNotExist()
        composeRule.onNodeWithText("同会话旧入口的候选").assertDoesNotExist()
        composeRule.runOnIdle {
            val prepared = preview(scenarioId)
            fixture.state.value = prepared.copy(
                entryId = routeEntryId,
                phase = CompanionEntryPhase.ACTIVE,
                encounter = prepared.encounter?.copy(state = CompanionEncounterState.ACTIVE),
                preview = "仅供本场查看的未完成片段",
                completionStatus = PromptCompletionStatus.TRUNCATED,
                recoveryAction = CompanionRecoveryAction.GENERATE,
            )
        }
        composeRule.onNodeWithTag("ordinary_content").assertIsDisplayed()
        composeRule.onNodeWithTag("companion_approach").assertDoesNotExist()
        composeRule.onNodeWithText("查看未完成结果").performClick()
        composeRule.onNodeWithText("仅供本场查看的未完成片段").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("仅供本场查看的未完成片段").assertDoesNotExist()
        composeRule.onNodeWithTag("ordinary_content").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(fixture.events.isEmpty()) }

        Espresso.pressBack()
        composeRule.onNodeWithText("进入合成会话").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("back"), fixture.events) }
    }

    @Test
    fun foreignState_onOldRouteKeepsOrdinaryContentAndDoesNotInterceptItsBack() {
        val fixture = NavigationFixture(
            preview("先前场景").copy(preview = "先前场景的草稿"),
        )
        renderNavigation(fixture, AppRoutes.roleplayPlay("当前普通会话"))

        composeRule.onNodeWithText("进入合成会话").performClick()
        composeRule.onNodeWithTag("ordinary_content").assertIsDisplayed()
        composeRule.onNodeWithTag("companion_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_direct").assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithText("进入合成会话").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fixture.events.isEmpty())
            assertTrue(fixture.leftOrigins.isEmpty())
        }
    }

    private fun renderNavigation(
        fixture: NavigationFixture,
        destination: String,
        includeSceneActions: Boolean = false,
    ) {
        composeRule.setContent {
            ChatAppTheme {
                val navigation = rememberNavController()
                fixture.navigation = navigation
                NavHost(navigation, startDestination = "companion-test-origin") {
                    composable("companion-test-origin") {
                        TextButton(onClick = { navigation.navigate(destination) }) {
                            Text("进入合成会话")
                        }
                    }
                    listOf(AppRoutes.ROLEPLAY_PLAY, AppRoutes.ROLEPLAY_COMPANION).forEach { route ->
                        composable(route) { entry ->
                            val scenarioId =
                                Uri.decode(entry.arguments?.getString("scenarioId").orEmpty())
                            CompanionRouteContent(
                                scenarioId = scenarioId,
                                routeEntryId = entry.arguments?.getString("companionEntryId")
                                    ?.let(Uri::decode),
                                state = fixture.state.value,
                                characterName = "合成角色",
                                callbacks = CompanionEntryCallbacks(
                                    onChooseOpening = { fixture.events += "opening:" + it.name },
                                    onRegenerate = { fixture.events += "regenerate" },
                                    onAccept = { fixture.events += "accept" },
                                    onRecover = { fixture.events += "recover" },
                                    onCancel = { fixture.events += "cancel" },
                                    onBack = {
                                        fixture.events += "back"
                                        fixture.leftOrigins += fixture.state.value.origin?.conversationId
                                        navigation.popBackStack()
                                    },
                                    onRead = {
                                        fixture.events += "read"
                                        navigation.navigate(AppRoutes.roleplayReading(scenarioId))
                                    },
                                    onSettings = {
                                        fixture.events += "settings"
                                        navigation.navigate(AppRoutes.roleplaySettings(scenarioId))
                                    },
                                    onSaveZone = { fixture.events += "save-zone" },
                                ),
                            ) {
                                Column {
                                    Text(
                                        "普通会话内容",
                                        modifier = Modifier.testTag("ordinary_content")
                                    )
                                    if (includeSceneActions) {
                                        CompanionSceneActions(
                                            fixture.state.value,
                                            CompanionSceneCallbacks(
                                                onContinue = { fixture.events += "continue" },
                                                onRegenerate = { fixture.events += "regenerate" },
                                                onRecover = { fixture.events += "recover" },
                                                onNewEncounter = { fixture.events += "new-encounter" },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    composable(AppRoutes.ROLEPLAY_READING) { entry ->
                        val scenarioId =
                            Uri.decode(entry.arguments?.getString("scenarioId").orEmpty())
                        Text(
                            "阅读原会话：" + scenarioId,
                            modifier = Modifier.testTag("reading_destination")
                        )
                    }
                    composable(AppRoutes.ROLEPLAY_SETTINGS) { entry ->
                        val scenarioId =
                            Uri.decode(entry.arguments?.getString("scenarioId").orEmpty())
                        Column {
                            Text(
                                "设置原会话：" + scenarioId,
                                modifier = Modifier.testTag("settings_destination")
                            )
                            TextButton(onClick = { navigation.popBackStack() }) { Text("返回原会话") }
                        }
                    }
                }
            }
        }
    }

    private fun preview(scenarioId: String): RoleplayCompanionUiState {
        val context = MemoryRuntimeContext(
            characterId = "navigation-character",
            spaceId = "navigation-space",
            conversationId = "origin-conversation",
            scenarioId = scenarioId,
            runId = "origin-run",
            personaKey = "origin-persona",
        )
        return RoleplayCompanionUiState(
            scenarioId = scenarioId,
            entryId = "prepared-entry",
            encounter = CompanionEncounter(
                id = "selected-encounter",
                context = context,
                openingMode = CompanionOpeningMode.INVITE,
                state = CompanionEncounterState.DRAFT,
                revision = 2,
                createdAt = 1_000,
            ),
            origin = CompanionEntryOrigin(
                scenarioId = scenarioId,
                conversationId = context.conversationId,
                runId = context.runId,
                characterId = context.characterId,
                personaKey = context.personaKey,
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
            phase = CompanionEntryPhase.PREVIEW,
            attemptId = "navigation-attempt",
            preview = "<dialogue>本场待接受预览</dialogue>",
            completionStatus = PromptCompletionStatus.COMPLETED,
        )
    }

    private class NavigationFixture(initial: RoleplayCompanionUiState) {
        val state = mutableStateOf(initial)
        val events = mutableListOf<String>()
        val leftOrigins = mutableListOf<String?>()
        lateinit var navigation: NavHostController
    }
}
