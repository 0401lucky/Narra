package com.example.myapplication.ui.screen.roleplay

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotFixture
import com.example.myapplication.ui.screenshot.SessionSidebarRenderTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 驱动真实侧栏导航和控件；不写入或接纳截图基准。 */
@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class SessionSidebarAccessibilityTest : SessionSidebarRenderTest() {
    @Test
    fun allEightPages_narrowLargeFont_keepLastContentAndHeaderBackReachable() {
        renderNarrowSidebar(group = true)
        assertToolbarTitle("会话设定")
        val pages = listOf(
            "情景设定" to (hasText("最多条数") and hasSetTextAction()),
            "用户身份" to (hasText("人设提示词") and hasClickAction()),
            "群聊设置" to (hasText(RoleplayGroupReplyMode.entries.last().displayName) and role(Role.RadioButton)),
            "主题" to hasTestTag("roleplay_line_height_relaxed"),
            "快捷切换" to (hasText("上下文治理") and hasClickAction()),
            "背景与立绘" to hasText("角色立绘"),
            "正则" to hasText("正则功能预留"),
        )
        pages.forEach { (title, lastContent) ->
            if (title == "正则") open("高级选项")
            open(title)
            assertToolbarTitle(title)
            scrollTo(lastContent).assertIsDisplayed()
            if (title == "背景与立绘") {
                composeRule.onAllNodes(hasSetTextAction()).onLast().performScrollTo().assertIsDisplayed()
            } else if (title == "正则") {
                val list = composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST)
                list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
                composeRule.onNodeWithText("当前项目里还没有", substring = true).assertIsDisplayed()
                val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                assertEquals("预留说明末尾应可达", range.maxValue(), range.value(), 1f)
            }
            val back = composeRule.onNodeWithContentDescription("返回")
            assertTouchTarget(back)
            back.performTouchInput { click() }
            composeRule.waitForIdle()
            assertToolbarTitle("会话设定")
        }
        scrollTo(hasText("重置")).assertIsDisplayed()
        assertTouchTarget(composeRule.onNodeWithText("重启"))
        assertTouchTarget(composeRule.onNodeWithText("重置"))
        composeRule.runOnIdle { assertTrue("子页返回不能触发退出回调", events.isEmpty()) }
    }

    @Test
    fun mainAdvancedGroup_narrowLargeFont_exposesExpandedStateAndNamedMemorySwitch() {
        renderNarrowSidebar()
        val advanced = scrollTo(hasText("高级选项"))
        advanced.assert(role(Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已折叠"))
        assertTouchTarget(advanced)
        advanced.performTouchInput { click() }
        composeRule.waitForIdle()
        scrollTo(hasText("高级选项"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        val memory = scrollTo(hasContentDescription("长记忆") and role(Role.Switch))
        assertTouchTarget(memory)
        scrollTo(hasText("高级选项")).performTouchInput { click() }
        composeRule.waitForIdle()
        scrollTo(hasText("高级选项"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已折叠"))
        composeRule.onNodeWithContentDescription("长记忆").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test
    fun interactionTabs_narrowLargeFont_exposeSelectionAndSendOneExactModeCallback() {
        renderNarrowSidebar()
        open("情景设定")
        scrollTo(role(Role.Tab) and hasText(RoleplayInteractionMode.ONLINE_PHONE.displayName))
        RoleplayInteractionMode.entries.forEach { mode ->
            val tab = composeRule.onNode(role(Role.Tab) and hasText(mode.displayName))
            if (mode == ScreenshotFixture.SuwanScenario.interactionMode) tab.assertIsSelected()
            else tab.assertIsNotSelected()
            assertTouchTarget(tab)
            assertTabLabelFits(mode.displayName)
            tab.performTouchInput { click() }
        }
        composeRule.runOnIdle {
            assertEquals(RoleplayInteractionMode.entries.map { "mode:$it" }, events)
        }
    }

    @Test
    fun readabilityTabs_narrowLargeFont_updateSelectionAndCallbacksForEveryOption() {
        renderNarrowSidebar()
        open("主题")
        RoleplayImmersiveMode.entries.forEach { mode ->
            val tab = scrollTo(hasTestTag("roleplay_immersive_${mode.storageValue}"))
            tab.assert(role(Role.Tab))
            assertTouchTarget(tab)
            tab.performTouchInput { click() }
            composeRule.waitForIdle()
            RoleplayImmersiveMode.entries.forEach { candidate ->
                val option = composeRule.onNodeWithTag("roleplay_immersive_${candidate.storageValue}")
                if (candidate == mode) option.assertIsSelected() else option.assertIsNotSelected()
            }
        }
        listOf("边到边", "全屏", "标准").forEach { label ->
            assertTabLabelFits(label, "roleplay_immersive_")
        }
        RoleplayLineHeightScale.entries.forEach { scale ->
            val tab = scrollTo(hasTestTag("roleplay_line_height_${scale.storageValue}"))
            tab.assert(role(Role.Tab))
            assertTouchTarget(tab)
            tab.performTouchInput { click() }
            composeRule.waitForIdle()
            RoleplayLineHeightScale.entries.forEach { candidate ->
                val option = composeRule.onNodeWithTag("roleplay_line_height_${candidate.storageValue}")
                if (candidate == scale) option.assertIsSelected() else option.assertIsNotSelected()
            }
        }
        listOf("紧凑", "标准", "宽松").forEach { label ->
            assertTabLabelFits(label, "roleplay_line_height_")
        }
        composeRule.runOnIdle {
            assertEquals(
                RoleplayImmersiveMode.entries.map { "immersive:$it" } +
                    RoleplayLineHeightScale.entries.map { "line:$it" },
                events,
            )
        }
    }

    @Test
    fun sceneSwitches_narrowLargeFont_exposeTheirNamesRolesAndTouchTargets() {
        renderNarrowSidebar()
        open("情景设定")
        listOf(
            "显示身份条", "显示 AI 帮写", "显示心声与状态提示", "启用心声生成",
            "深度沉浸", "时间感知", "主动回复", "网络热梗",
        ).forEach { label ->
            val control = scrollTo(hasContentDescription(label) and role(Role.Switch))
            assertTouchTarget(control)
            control.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        }
    }

    @Test
    fun visualFields_existingImages_narrowLargeFont_keepNamesAndSeparateActionsReachable() {
        renderSidebar(
            ScreenshotCases.require("E05_main-narrow-light"),
            scenarioOverride = ScreenshotFixture.SuwanScenario.copy(
                userPortraitUrl = "https://fixture.invalid/user.png",
                characterPortraitUrl = "https://fixture.invalid/character.png",
            ),
        )
        open("背景与立绘")
        listOf("用户立绘", "角色立绘").forEach { title ->
            val label = scrollTo(hasText(title))
            val layouts = mutableListOf<TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("已有图片的字段名称应完整显示：$title", layouts.single().hasVisualOverflow)
            val pick = composeRule.onNodeWithContentDescription("$title：选择图片").performScrollTo()
            val clear = composeRule.onNodeWithContentDescription("$title：清除图片").performScrollTo()
            assertTouchTarget(pick)
            assertTouchTarget(clear)
            assertFalse(
                "选择与清除的命中区不能重叠：$title",
                pick.fetchSemanticsNode().touchBoundsInRoot.overlaps(clear.fetchSemanticsNode().touchBoundsInRoot),
            )
            clear.performTouchInput { click() }
        }
        composeRule.runOnIdle {
            assertEquals(listOf("visual:||||https://fixture.invalid/character.png", "visual:||||"), events)
            assertEquals(skin, settings.value.roleplayNoBackgroundSkin)
        }
    }

    @Test
    fun themeSwitchAndHelp_narrowLargeFont_keepTextHelpAndSwitchClicksIndependent() {
        renderNarrowSidebar()
        open("主题")
        val control = scrollTo(hasTestTag("roleplay_online_background_glass_switch"))
        control.assert(role(Role.Switch)).assertContentDescriptionEquals("线上背景玻璃气泡").assertIsOff()
        assertTouchTarget(control)
        val label = composeRule.onNodeWithText("线上背景玻璃气泡")
        label.performScrollTo().performTouchInput { click() }
        composeRule.runOnIdle { assertTrue("点标题不能切换开关", events.isEmpty()) }
        val help = composeRule.onNodeWithContentDescription("线上背景玻璃气泡：查看说明")
        help.performScrollTo()
        assertTouchTarget(help)
        val helpBounds = help.fetchSemanticsNode().touchBoundsInRoot
        val switchBounds = control.fetchSemanticsNode().touchBoundsInRoot
        assertFalse("相邻说明与开关命中区不能重叠", helpBounds.overlaps(switchBounds))
        help.performTouchInput { click() }
        windowRoot("线上背景玻璃气泡").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue("查看说明不能切换开关", events.isEmpty()) }
        composeRule.onNodeWithText("知道了").performTouchInput { click() }
        control.performScrollTo().assertIsOff().performTouchInput { click() }
        control.assertIsOn()
        val tail = scrollTo(hasTestTag("roleplay_skin_tail_switch"))
        tail.assert(role(Role.Switch)).assertContentDescriptionEquals("显示气泡尾巴").assertIsOn()
        assertTouchTarget(tail)
        composeRule.runOnIdle {
            assertEquals(listOf("skin"), events)
            assertTrue(settings.value.roleplayNoBackgroundSkin.onlineBackgroundGlassEnabled)
            assertTrue(settings.value.roleplayNoBackgroundSkin.showBubbleTail)
        }
        val contrast = scrollTo(hasTestTag("roleplay_high_contrast_switch"))
        contrast.assert(role(Role.Switch)).assertContentDescriptionEquals("高对比度模式").assertIsOff()
        assertTouchTarget(contrast)
        contrast.performTouchInput { click() }
        contrast.assertIsOn()
        composeRule.runOnIdle { assertEquals(listOf("skin", "contrast:true"), events) }
    }

    @Test
    fun syntheticLongHelp_narrowLargeFont_scrollsToEndWithFixedCloseAndIndependentRow() {
        RuntimeEnvironment.setQualifiers(ScreenshotFixture.NarrowQualifiers)
        useNarrowFontScale()
        val title = "合成长规则"
        val description = (1..40).joinToString("\n") { "第 $it 条合成规则：保留完整说明并允许滚动阅读。" }
        var rowClicks = 0
        setStage(ScreenshotCases.require("E05_main-narrow-light")) {
            SettingsListRow(
                title = title,
                supportingText = "合成说明入口",
                onClick = { rowClicks++ },
                trailingContent = { SettingsHelpButton(title, description) },
            )
        }
        val help = composeRule.onNodeWithContentDescription("$title：查看说明")
        assertTouchTarget(help)
        help.performTouchInput { click() }
        windowRoot(title).assertIsDisplayed()
        assertEquals(2f, composeRule.activity.resources.configuration.fontScale, 0.001f)
        val close = composeRule.onNodeWithText("知道了")
        assertTouchTarget(close)
        val initialCloseBounds = close.fetchSemanticsNode().touchBoundsInRoot
        val scroll = composeRule.onNode(
            hasScrollAction() and hasAnyAncestor(windowMatcher(title)),
            useUnmergedTree = true,
        )
        val initialRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("合成长文必须产生真实滚动范围", initialRange.maxValue() > 0f)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val finalRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("说明应能滚动到末尾", finalRange.maxValue(), finalRange.value(), 1f)
        close.assertIsDisplayed()
        assertEquals("关闭操作不能随正文滚走", initialCloseBounds, close.fetchSemanticsNode().touchBoundsInRoot)
        composeRule.runOnIdle { assertEquals("说明不能触发所在行", 0, rowClicks) }
        close.performTouchInput { click() }
        composeRule.onNode(windowMatcher(title), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(title).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, rowClicks) }
    }

    private fun assertTabLabelFits(label: String, tagPrefix: String? = null) {
        val parent = role(Role.Tab) and SemanticsMatcher("选项组 $tagPrefix") { node ->
            tagPrefix == null || (SemanticsProperties.TestTag in node.config &&
                node.config[SemanticsProperties.TestTag].startsWith(tagPrefix))
        }
        val text = composeRule.onNode(hasText(label) and hasAnyAncestor(parent), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("选项标签应完整换行：$label", layouts.single().hasVisualOverflow)
    }

    private fun renderNarrowSidebar(group: Boolean = false) {
        renderSidebar(ScreenshotCases.require("E05_main-narrow-light"), group = group)
        val bounds = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode().boundsInRoot
        assertEquals(with(composeRule.density) { 320.dp.toPx() }, bounds.width, 1f)
        assertEquals(with(composeRule.density) { 640.dp.toPx() }, bounds.height, 1f)
        assertEquals(2f, composeRule.activity.resources.configuration.fontScale, 0.001f)
    }

    private fun scrollTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeRule.onNodeWithTag(TAG_ROLEPLAY_SETTINGS_LIST).performScrollToNode(matcher)
        return composeRule.onNode(matcher).performScrollTo()
    }

    private fun assertToolbarTitle(title: String) {
        val heading = composeRule.onNode(
            hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
            useUnmergedTree = true,
        )
        heading.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("页面标题不能被纵向裁切：$title", layout.didOverflowHeight)
        assertEquals(
            "页面标题应保留全部文字：$title",
            title.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        )
        repeat(layout.lineCount) { line ->
            assertFalse("页面标题不能省略：$title", layout.isLineEllipsized(line))
            // Compose 的浮点行宽与整数布局宽度可能相差不足 1px。
            assertTrue(
                "页面标题应完整显示：$title，line=$line，" +
                    "bounds=${layout.getLineLeft(line)}..${layout.getLineRight(line)}，width=${layout.size.width}",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f,
            )
        }
    }

    private fun role(value: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, value)

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("点击宽度不足 48dp：$bounds", bounds.width >= minimum - 0.5f)
        assertTrue("点击高度不足 48dp：$bounds", bounds.height >= minimum - 0.5f)
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "点击范围应完整位于宿主内：$bounds / $stage",
            bounds.left >= stage.left - 0.5f && bounds.top >= stage.top - 0.5f &&
                bounds.right <= stage.right + 0.5f && bounds.bottom <= stage.bottom + 0.5f,
        )
    }
}
