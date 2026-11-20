package com.example.myapplication.ui.screen.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.*
import com.example.myapplication.ui.screen.immersive.NewChatSheet
import com.example.myapplication.ui.screen.immersive.NewGroupChatSheet
import com.example.myapplication.ui.screen.immersive.ContactCardSheet
import com.example.myapplication.ui.screenshot.*
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class MemoryPurposeUiTest : ScreenshotStageTest() {
    private val assistant = Assistant(id = "a", name = "合成角色甲")

    @Test
    fun newChat200Percent_selectedPurposeReachesModeCallback() {
        useNarrowFontScale()
        var created: MemoryPurpose? = null
        var mode: RoleplayInteractionMode? = null
        setStage(ScreenshotCases.require("E04_reading-narrow-dark")) {
            NewChatSheet(
                listOf(assistant),
                assistant,
                {},
                { _, selectedMode, _, purpose -> mode = selectedMode; created = purpose })
        }
        composeRule.onNodeWithTag("memory_purpose_continuous").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("memory_purpose_independent").performScrollTo().performClick()
        val action = composeRule.onNode(hasText("剧情模式") and hasClickAction())
        action.performScrollTo().assertIsDisplayed()
        assertTouch(action)
        action.performClick()
        composeRule.runOnIdle {
            assertEquals(MemoryPurpose.INDEPENDENT, created); assertEquals(
            RoleplayInteractionMode.OFFLINE_LONGFORM,
            mode
        )
        }
    }

    @Test
    fun contactCard200Percent_selectedPurposeReachesPrivateCallback() {
        useNarrowFontScale()
        var purpose: MemoryPurpose? = null
        setStage(ScreenshotCases.require("E04_reading-narrow-dark"), darkTheme = true) {
            ContactCardSheet(
                assistant,
                emptyList(),
                {},
                {},
                { _, _, _, selected -> purpose = selected },
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {})
        }
        composeRule.onNodeWithTag("contact_card_list").performScrollToNode(hasText("发消息"))
        composeRule.onNodeWithText("发消息").performClick()
        composeRule.onNodeWithTag("contact_card_list")
            .performScrollToNode(hasTestTag("memory_purpose_independent"))
        composeRule.onNodeWithTag("memory_purpose_independent").performClick()
        composeRule.onNodeWithTag("contact_card_list")
            .performScrollToNode(hasText("线下", substring = false))
        composeRule.onNode(hasText("线下") and hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(MemoryPurpose.INDEPENDENT, purpose) }
    }

    @Test
    fun newGroup200Percent_keepsCreateReachableAndDefaultContinuous() {
        useNarrowFontScale()
        var created: Pair<List<String>, MemoryPurpose>? = null
        setStage(ScreenshotCases.require("E04_reading-narrow-dark")) {
            NewGroupChatSheet(
                listOf(assistant, Assistant(id = "b", name = "合成角色乙")),
                {},
                { _, members, purpose -> created = members to purpose })
        }
        composeRule.onNodeWithTag("memory_purpose_continuous").performScrollTo().assertIsSelected()
        // 先把整个嵌套列表滚进 Sheet，再滚动成员；只滚内层无法保证真实点击落在窗口内。
        composeRule.onNodeWithTag("new_group_members").performScrollTo()
        composeRule.onNodeWithTag("new_group_members")
            .performScrollToNode(hasTestTag("new_group_member_a"))
        val first = composeRule.onNodeWithTag("new_group_member_a")
        first.assertIsDisplayed()
        assertTouch(first)
        first.performClick()
        composeRule.onNodeWithText("群成员（1）").assertExists()
        composeRule.onNodeWithTag("new_group_members")
            .performScrollToNode(hasTestTag("new_group_member_b"))
        val second = composeRule.onNodeWithTag("new_group_member_b")
        second.assertIsDisplayed()
        assertTouch(second)
        second.performClick()
        composeRule.onNodeWithText("群成员（2）").assertExists()
        val create = composeRule.onNodeWithTag("new_group_create")
        create.performScrollTo().assertIsDisplayed()
        create.assertIsEnabled()
        assertTouch(create)
        create.performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf("a", "b") to MemoryPurpose.CONTINUOUS,
                created
            )
        }
    }

    private fun assertTouch(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= minimum && bounds.height >= minimum)
    }
}
