package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayDiaryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun narrowList_largeFont_keepsHeaderReadableAndSearchActionsWork() {
        var openedEntry = ""
        var generateCalls = 0
        composeRule.setContent {
            DiaryTestTheme {
                RoleplayDiaryScreen(
                    scenario = testScenario,
                    assistant = null,
                    settings = AppSettings(),
                    diaryEntries = testEntries,
                    isGeneratingDiary = false,
                    noticeMessage = null,
                    errorMessage = null,
                    onClearNoticeMessage = {},
                    onClearErrorMessage = {},
                    onGenerateDiary = { generateCalls++ },
                    onOpenEntry = { openedEntry = it },
                    onOpenMailbox = {},
                    onNavigateBack = {},
                )
            }
        }

        assertSingleLine("diary_top_title")
        assertSingleLine("diary_count")
        composeRule.onNodeWithTag("diary_count").assertTextEquals("共 5 篇")
        composeRule.onNodeWithContentDescription("重新生成").performClick()
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("diary_search").performTextInput("不存在的关键词")
        composeRule.onNodeWithText("没有找到相关日记").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("清除搜索").performClick()
        composeRule.onNodeWithTag("diary_count").assertTextEquals("共 5 篇")
        composeRule.onNodeWithTag("diary_search").performTextInput("归途")
        composeRule.onNodeWithTag("diary_count").assertTextEquals("找到 1 篇")
        composeRule.onNodeWithText("这是秘密", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("diary_list").performScrollToNode(hasText("显示涂黑"))
        composeRule.onNodeWithText("显示涂黑").performClick()
        composeRule.onNodeWithText("隐藏涂黑").assertIsDisplayed()
        composeRule.onNodeWithText("阅读全文").performClick()
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("diary_search").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(testEntries.first().id, openedEntry)
            assertEquals(1, generateCalls)
        }
    }

    @Test
    fun reader_turningPages_resetsScrollAndPreservesNavigation() {
        var mailboxCalls = 0
        var backCalls = 0
        composeRule.setContent {
            DiaryTestTheme {
                RoleplayDiaryDetailScreen(
                    scenario = testScenario,
                    settings = AppSettings(),
                    diaryEntries = testEntries,
                    entryId = testEntries.first().id,
                    onOpenMailbox = { mailboxCalls++ },
                    onNavigateBack = { backCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("上一篇").assertIsNotEnabled()
        composeRule.onNodeWithTag("diary_reader").performScrollToNode(hasText("显示涂黑"))
        composeRule.onNodeWithText("显示涂黑").performClick()
        composeRule.onNodeWithTag("diary_entry_body")
            .assertContentDescriptionContains("这是秘密", substring = true)
        composeRule.onNodeWithTag("diary_reader").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("diary_entry_title").assertIsNotDisplayed()
        composeRule.onNodeWithText("下一篇").performClick()
        composeRule.onNodeWithTag("diary_entry_title").assertIsDisplayed()
            .assertTextEquals(testEntries[1].title)
        composeRule.onNodeWithTag("diary_page_index").assertTextEquals("2 / 5")
        composeRule.onNodeWithText("上一篇").performClick()
        composeRule.onNodeWithTag("diary_entry_title").assertIsDisplayed()
            .assertTextEquals(testEntries.first().title)
        composeRule.onNodeWithContentDescription("信箱").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle {
            assertEquals(1, mailboxCalls)
            assertEquals(1, backCalls)
        }
    }

    @Test
    fun reader_entriesReplaced_keepsPageIndexWithinBounds() {
        var entries by mutableStateOf(testEntries)
        composeRule.setContent {
            DiaryTestTheme {
                RoleplayDiaryDetailScreen(
                    scenario = testScenario,
                    settings = AppSettings(),
                    diaryEntries = entries,
                    entryId = testEntries.last().id,
                    onOpenMailbox = {},
                    onNavigateBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("diary_page_index").assertTextEquals("5 / 5")
        composeRule.onNodeWithText("下一篇").assertIsNotEnabled()
        composeRule.runOnIdle { entries = testEntries.take(2) }
        composeRule.onNodeWithTag("diary_page_index").assertTextEquals("1 / 2")
        composeRule.onNodeWithText("上一篇").assertIsNotEnabled()
        composeRule.onNodeWithText("下一篇").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("diary_entry_title").assertTextEquals(testEntries[1].title)
    }

    @Test
    fun reader_allColorThemes_useOpaqueThemeSurface() {
        var colorTheme by mutableStateOf(AppColorTheme.MATCHA)
        var darkTheme by mutableStateOf(false)
        var expectedSurface = Color.Unspecified
        composeRule.setContent {
            DiaryTestTheme(colorTheme, darkTheme) {
                expectedSurface = MaterialTheme.colorScheme.surface
                RoleplayDiaryDetailScreen(
                    scenario = testScenario,
                    settings = AppSettings(),
                    diaryEntries = testEntries,
                    entryId = testEntries.first().id,
                    onOpenMailbox = {},
                    onNavigateBack = {},
                )
            }
        }
        AppColorTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                composeRule.runOnIdle {
                    colorTheme = theme
                    darkTheme = dark
                }
                val pixels = composeRule.onNodeWithTag("diary_reader").captureToImage().toPixelMap()
                val actual = pixels[2, 2]
                assertEquals(expectedSurface.red, actual.red, 0.01f)
                assertEquals(expectedSurface.green, actual.green, 0.01f)
                assertEquals(expectedSurface.blue, actual.blue, 0.01f)
                assertEquals(1f, actual.alpha, 0.01f)
            }
        }
    }

    private fun assertSingleLine(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        val diagnostic = "$tag: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "lineCount=${layout.lineCount}, maxWidth=${layout.layoutInput.constraints.maxWidth}, " +
            "fontSize=${layout.layoutInput.style.fontSize}, lineHeight=${layout.layoutInput.style.lineHeight}, " +
            "overflowWidth=${layout.didOverflowWidth}, overflowHeight=${layout.didOverflowHeight}"
        assertEquals(diagnostic, 1, layout.lineCount)
        assertFalse(diagnostic, layout.isLineEllipsized(0) || layout.didOverflowHeight)
        org.junit.Assert.assertTrue(diagnostic, layout.getLineRight(0) <= layout.size.width + 1f)
    }
}

@Composable
private fun DiaryTestTheme(
    colorTheme: AppColorTheme = AppColorTheme.MATCHA,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
        ChatAppTheme(appColorTheme = colorTheme, darkTheme = darkTheme) {
            Box(Modifier.width(360.dp).fillMaxHeight()) { content() }
        }
    }
}

private val testScenario = RoleplayScenario(
    id = "diary-ui-test",
    characterDisplayNameOverride = "陆星野和一个用于验证超长角色姓名的名字",
)

private val testEntries = List(5) { index ->
    RoleplayDiaryEntry(
        id = "entry-$index",
        scenarioId = testScenario.id,
        conversationId = "conversation-ui-test",
        title = if (index == 0) "归途：终于决定在这座城市留下来，记录一整个秋天的故事" else "第 ${index + 1} 天的日常",
        content = "今天见到了 !u{lucky}，有一件**很重要的事**，||这是秘密||。\n\n" +
            "走过熟悉的街道，拍下沿途的风景。晚风吹过窗边，我把今天的故事写在这里。\n\n".repeat(18),
        mood = "期待下一次见面，也有一点难以言说的紧张",
        weather = "晴，傍晚有风",
        tags = listOf("生活记录", "这是一个需要自动换行的很长很长的中文标签"),
        dateLabel = "2026/9/${index + 1} 周二",
        sortOrder = index,
        createdAt = 1_788_220_800_000L + index * 86_400_000L,
        updatedAt = 1_788_220_800_000L + index * 86_400_000L,
    )
}
