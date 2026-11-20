package com.example.myapplication.ui.screen.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import com.example.myapplication.ui.navigation.*
import com.example.myapplication.ui.screenshot.*
import com.example.myapplication.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class MemoryArchiveNavigationTest : ScreenshotStageTest() {
    @Test
    fun realRoutes_simpleToAdvancedKeepsConversationAndOriginalIsReadOnly() {
        val fixture = MemoryArchiveUiTestFixture()
        fixture.messages += MemoryArchiveMessage(
            ChatMessage(
                "m",
                "conv",
                MessageRole.ASSISTANT,
                "历史原文合成内容",
                speakerName = "原来的署名"
            ), "p", null
        )
        val models = mutableListOf<MemoryArchiveViewModel>()
        val arguments = mutableListOf<Pair<String, String?>>()
        lateinit var navController: NavHostController
        setStage(ScreenshotCases.require("E11_config-light")) {
            navController = rememberNavController()
            NavHost(navController, startDestination = AppRoutes.SETTINGS_GRAPH) {
                navigation(startDestination = "origin", route = AppRoutes.SETTINGS_GRAPH) {
                    composable("origin") {
                        TextButton(onClick = {
                            navController.navigate(
                                AppRoutes.settingsAssistantMemorySimple(
                                    "a",
                                    "conv"
                                )
                            )
                        }) { Text("会话中的记忆入口") }
                    }
                    registerMemoryArchiveRoutes(
                        navController,
                        settingsProvider = {
                            AppSettings(
                                assistants = listOf(
                                    Assistant(
                                        id = "a",
                                        name = "角色"
                                    )
                                )
                            )
                        },
                        archiveFactory = { id, conversationId, saved ->
                            typedViewModelFactory {
                                arguments += id to conversationId
                                MemoryArchiveViewModel(
                                    fixture.repository,
                                    id,
                                    conversationId,
                                    rejectingArchiveTasks(),
                                    saved
                                ).also { models += it }
                            }
                        },
                        originalFactory = { id, space, persona, conversation ->
                            MemoryOriginalViewModel.factory(
                                fixture.repository,
                                id,
                                space,
                                persona,
                                conversation
                            )
                        },
                        onUpdateAssistant = { error("导航读取不应写入角色") })
                }
            }
        }
        composeRule.onNodeWithText("会话中的记忆入口").performClick()
        drainMainLooper()
        composeRule.onNodeWithText("简洁记忆编辑").assertIsDisplayed()
        composeRule.onNodeWithText("高级管理").performClick()
        drainMainLooper()
        composeRule.onNodeWithText("记忆档案").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("a" to "conv"), arguments)
            assertEquals(
                "conv",
                navController.currentBackStackEntry?.arguments?.getString("conversationId")
            )
        }
        composeRule.onNodeWithTag("memory_archive_list")
            .performScrollToNode(hasTestTag("memory_panel"))
        composeRule.onNodeWithTag("memory_panel")
            .performScrollToNode(hasTestTag("memory_panel_RECEIPT"))
        composeRule.onNodeWithTag("memory_panel_RECEIPT").performClick()
        composeRule.onNodeWithTag("memory_archive_list")
            .performScrollToNode(hasText("只读查看整段原文"))
        composeRule.onNodeWithText("只读查看整段原文").performClick()
        drainMainLooper()
        composeRule.onNodeWithText("只读历史原文").assertIsDisplayed()
        composeRule.onNodeWithTag("memory_original_list")
            .performScrollToNode(hasText("历史原文合成内容"))
        composeRule.onNodeWithText("原来的署名").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        drainMainLooper()
        composeRule.runOnIdle {
            assertEquals(
                1,
                models.size
            ); assertTrue(fixture.writeCalls.isEmpty())
        }
    }

    @Test
    fun archiveRouteEncodesEveryIdentityAndKeepsViewModelKeysDistinct() {
        val id = "角色/a?b#c"
        val conversation = "旧/会话?x&y"
        val route = AppRoutes.settingsAssistantMemory(id, conversation)
        assertTrue(route.contains(android.net.Uri.encode(id)))
        assertTrue(route.contains(android.net.Uri.encode(conversation)))
        assertNotEquals(
            memoryArchiveViewModelKey("a-b", "c"),
            memoryArchiveViewModelKey("a", "b-c")
        )
        val original =
            AppRoutes.settingsMemoryOriginal(id, "space/?", conversation, "persona/旧?身份")
        assertTrue(original.contains(android.net.Uri.encode("persona/旧?身份")))
    }
}
