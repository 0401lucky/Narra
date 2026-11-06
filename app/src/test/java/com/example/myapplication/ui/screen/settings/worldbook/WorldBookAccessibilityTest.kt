package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ScreenshotCases
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WorldBookAccessibilityTest : P06ColorTestSupport() {
    @Test
    fun list_narrowHeaderFiltersAndLastEntryRemainReachable() {
        renderList(ScreenshotCases.require("E06_list-filters-narrow-light"))
        val stage = composeRule.onNodeWithTag(StageTag)
        val title = pageNode(hasText("世界书"), unmerged = true)
        if (textLayout(title).hasVisualOverflow) {
            val directory = File("build/outputs/p06-accessibility-diagnostics").apply { mkdirs() }
            val image = nativeBitmap()
            File(directory, "list-header.png").outputStream()
                .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            File(directory, "list-header-semantics.txt").writeText(stage.printToString())
        }
        assertCompleteText(title, "世界书")
        pageNode(hasText("世界书"), unmerged = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertActionBounds("返回", pageNode(hasContentDescription("返回")), stage)
        assertActionBounds("新增", pageNode(hasText("新增") and hasClickAction()), stage)
        assertActionBounds("导入世界书", reveal(hasText("导入世界书") and hasClickAction()))
        val filter = pageNode(hasText("筛选") and hasClickAction())
        assertActionBounds("筛选", filter, stage)
        touch(filter)
        selectListFilter(0, "全局")
        assertActionBounds(
            "全局筛选",
            pageNode(hasText("全局") and isSelectable()).performScrollTo()
        )
        entryAtEnd(detail = false)
        val last = pageNode(hasText(LastStandaloneTitle) and hasClickAction())
        last.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertActionBounds("末条", last)
        touch(last)
        assertEquals(listOf("entry:${StandaloneEntries.last().id}"), events)
    }

    @Test
    fun list_helpIsIndependentAndCloseIsReachableAt200Percent() {
        renderList(ScreenshotCases.require("E06_list-help-dialog-light"))
        val help = pageNode(hasContentDescription("世界书：查看说明"))
        assertActionBounds("列表帮助", help, composeRule.onNodeWithTag(StageTag))
        touch(help)
        val root = dialogRoot("世界书")
        val close = windowNode(dialogMatcher("世界书"), hasText("知道了") and hasClickAction())
        assertActionBounds("知道了", close, root)
        touch(close)
        assertTrue(events.isEmpty())
    }

    @Test
    fun detail_longTitleAndBothManagementActionsCanBeReadAndReached() {
        val name = "海风档案：群岛旅人和守灯人在漫长旅途之后重逢的约定"
        renderP06(narrowCase) {
            WorldBookBookDetailScreen(
                BookId, name, BookEntries, false,
                onRenameBook = { id, title -> events += "rename:$id:$title" },
                onDeleteBook = { events += "delete:$it" }, onAddEntry = {},
                onOpenEntryEdit = {}, onNavigateBack = {})
        }
        assertCompleteText(pageNode(hasText(name), unmerged = true), name)
        touch(reveal(hasText("管理世界书") and hasClickAction()))
        input("书名").performTextReplacement("新的海风档案")
        val rename = reveal(hasText("保存书名") and hasClickAction())
        assertActionBounds("保存书名", rename)
        touch(rename)
        val delete = reveal(hasText("删除整本世界书") and hasClickAction())
        assertActionBounds("删除整本", delete)
        touch(delete)
        val root = dialogRoot("删除整本世界书")
        for (label in listOf("确认删除", "取消")) {
            assertActionBounds(
                label,
                windowNode(dialogMatcher("删除整本世界书"), hasText(label) and hasClickAction()),
                root
            )
        }
        touch(windowNode(dialogMatcher("删除整本世界书"), hasText("取消") and hasClickAction()))
        assertEquals(listOf("rename:$BookId:新的海风档案"), events)
    }

    @Test
    fun editor_allElevenHelpEntrypointsOpenCorrectWindowsWithoutEditingState() {
        renderEditor(narrowCase, EditorEntry.copy(content = "正文"))
        val sections = listOf("基本信息", "命中规则", "作用域", "状态与高级", "试命中（预览）")
        sections.forEach { title ->
            val section = reveal(hasText(title) and hasClickAction())
            val before = section.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            checkHelp(title)
            reveal(hasText(title) and hasClickAction())
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, before))
        }
        setSection("基本信息", false)
        setSection("命中规则", true)
        checkHelp("匹配模式")
        listOf("次级匹配", "区分大小写").forEach { title -> checkToggleHelp(title) }
        setSection("命中规则", false)
        setSection("状态与高级", true)
        listOf("启用条目", "常驻注入").forEach { title -> checkToggleHelp(title) }
        touch(reveal(hasText("高级参数") and hasClickAction()))
        checkHelp("注入控制")
        assertTrue(events.isEmpty())
        assertTrue(savedEntries.isEmpty())
    }

    @Test
    fun editor_switchNamesAndHelpHaveIndependent48dpTouchTargets() {
        renderEditor(narrowCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        val target = toggle("次级匹配")
        target.assertIsOn()
        assertActionBounds("次级匹配开关", target)
        val help = pageNode(hasContentDescription("次级匹配：查看说明"))
        assertActionBounds("次级匹配帮助", help)
        val switchBounds = target.fetchSemanticsNode().touchBoundsInRoot
        val helpBounds = help.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("相邻帮助和开关触控区不得重叠", helpBounds.right <= switchBounds.left + 0.5f)
        touch(target)
        toggle("次级匹配").assertIsOff()
        checkHelp("次级匹配")
        toggle("次级匹配").assertIsOff()
        saveDraft()
        assertFalse(savedEntries.single().selective)
        assertEquals(EditorEntry.secondaryKeywords, savedEntries.single().secondaryKeywords)
    }

    @Test
    fun editor_longKeywordHasFullTextAndRemovesExactlyOneToken() {
        renderEditor(narrowCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        val keyword = EditorEntry.keywords[1]
        val chip = reveal(hasText(keyword) and hasClickAction())
        assertActionBounds("长关键词", chip)
        assertCompleteText(pageNode(hasText(keyword), unmerged = true), keyword)
        touch(chip)
        saveDraft()
        assertEquals(
            EditorEntry.keywords.filterNot { it == keyword },
            savedEntries.single().keywords
        )
        assertEquals(EditorEntry.aliases, savedEntries.single().aliases)
    }

    @Test
    fun editor_longBodyInternalTailAndFixedActionsFitAt200Percent() {
        renderEditor(narrowCase)
        openBodySheet()
        bodyToEnd()
        val root = dialogRoot("编辑正文")
        windowNode(dialogMatcher("编辑正文"), hasTestTag("worldbook_fullscreen_content"))
            .assertContentDescriptionEquals("正文")
        assertCompleteText(
            windowNode(
                dialogMatcher("编辑正文"),
                hasText("编辑正文"),
                unmerged = true
            ), "编辑正文"
        )
        assertActionBounds(
            "取消正文",
            windowNode(dialogMatcher("编辑正文"), hasContentDescription("取消正文编辑")),
            root
        )
        assertActionBounds(
            "确认正文",
            windowNode(dialogMatcher("编辑正文"), hasText("保存并返回") and hasClickAction()),
            root
        )
        touch(windowNode(dialogMatcher("编辑正文"), hasContentDescription("取消正文编辑")))
        assertTrue(savedEntries.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun editor_bothPopupLastChoicesHaveNativePixelsAndCompleteTouchBounds() {
        renderEditor(narrowCase, EditorEntry.copy(content = "正文"))
        openBookPopup()
        popupToEnd(LastBookName)
        val book =
            windowNode(popupMatcher(LastBookName), hasText(LastBookName) and hasClickAction())
        assertActionBounds("末本世界书", book, popupRoot(LastBookName))
        val bitmap = renderPopupBitmap(popupRoot(LastBookName))
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
        touch(book)
        openConversationPopup()
        popupToEnd(LastConversationTitle)
        val conversation = windowNode(
            popupMatcher(LastConversationTitle),
            hasText(LastConversationTitle) and hasClickAction()
        )
        assertActionBounds("末次会话", conversation, popupRoot(LastConversationTitle))
        touch(conversation)
        saveDraft()
        assertEquals(Conversations.last().id, savedEntries.single().scopeId)
        assertEquals(LastBookName, savedEntries.single().sourceBookName)
    }

    private fun checkHelp(title: String) {
        val help = reveal(hasContentDescription("$title：查看说明"))
        assertActionBounds("帮助:$title", help)
        touch(help)
        val root = dialogRoot(title)
        val body = windowNode(dialogMatcher(title), hasScrollAction(), unmerged = true)
        scrollToEnd(body, "helpBody")
        val close = windowNode(dialogMatcher(title), hasText("知道了") and hasClickAction())
        assertActionBounds("关闭:$title", close, root)
        touch(close)
        assertThrows(AssertionError::class.java) { dialogRoot(title) }
    }

    private fun checkToggleHelp(title: String) {
        val before = toggle(title).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        checkHelp(title)
        toggle(title).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ToggleableState,
                before
            )
        )
    }
}
