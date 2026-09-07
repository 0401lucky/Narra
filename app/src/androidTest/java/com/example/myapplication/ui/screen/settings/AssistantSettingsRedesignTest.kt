package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantSettingsRedesignTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun roleMenu_largeTextKeepsTouchTargetAndDoesNotSelectRole() {
        var selectCalls = 0
        var editCalls = 0
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                ChatAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        AssistantCardMinimal(
                            assistant = Assistant(
                                id = "long-role",
                                name = "一个名字很长也应当保留操作空间的角色",
                                tags = listOf("长篇叙事", "日常陪伴"),
                            ),
                            isSelected = true,
                            palette = rememberSettingsPalette(),
                            onClick = { selectCalls++ },
                            onEdit = { editCalls++ },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("更多操作")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("进入设置").performClick()
        composeRule.runOnIdle {
            assertEquals(1, editCalls)
            assertEquals(0, selectCalls)
        }
    }

    @Test
    fun personaMenu_defaultAndDeleteKeepIndependentCallbacksAndConfirmation() {
        val primary = UserPersonaMask(id = "mask-primary", name = "日常身份")
        val secondary = UserPersonaMask(id = "mask-secondary", name = "旅行身份")
        var defaultId = ""
        var deletedId = ""
        composeRule.setContent {
            ChatAppTheme {
                UserPersonaMasksScreen(
                    settings = AppSettings(
                        userPersonaMasks = listOf(primary, secondary),
                        defaultUserPersonaMaskId = primary.id,
                    ),
                    onUpsertMask = {},
                    onDeleteMask = { deletedId = it },
                    onSetDefaultMask = { defaultId = it },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("旅行身份：更多操作")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("设为全局默认").performClick()
        composeRule.runOnIdle {
            assertEquals(secondary.id, defaultId)
            assertEquals("", deletedId)
        }
        composeRule.onNodeWithContentDescription("旅行身份：更多操作").performClick()
        composeRule.onNodeWithText("删除面具").performClick()
        composeRule.onNodeWithText("删除后，已绑定这个面具的会话会回退到默认面具或全局个人资料。")
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("", deletedId) }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle { assertEquals(secondary.id, deletedId) }
    }

    @Test
    fun promptHelp_opensWithoutSavingUnchangedDraft() {
        var saveCalls = 0
        val description = "描述默认关系、时间线、相遇背景和互动起点。"
        composeRule.setContent {
            ChatAppTheme {
                AssistantPromptScreen(
                    assistant = Assistant(id = "prompt-role", name = "角色"),
                    onSave = { saveCalls++ },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText(description).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("关系与场景：查看说明")
            .performScrollTo().performClick()
        composeRule.onNodeWithText(description).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, saveCalls) }
    }

    @Test
    fun extensionHelp_keepsAdvancedFieldsVisibleAndDoesNotSave() {
        var saveCalls = 0
        composeRule.setContent {
            ChatAppTheme(darkTheme = true) {
                AssistantExtensionsScreen(
                    assistant = Assistant(id = "extensions-role", name = "角色"),
                    worldBookEntries = emptyList(),
                    onSave = { saveCalls++ },
                    onOpenWorldBookSettings = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("扫描消息条数").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("世界书联动：查看说明")
            .performScrollTo().performClick()
        composeRule.onNodeWithText(
            "条数上限决定每次最多引用多少条世界书内容。扫描深度（scanDepth）表示参与关键词匹配的最近消息条数，默认 2 条。",
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, saveCalls) }
    }
}
