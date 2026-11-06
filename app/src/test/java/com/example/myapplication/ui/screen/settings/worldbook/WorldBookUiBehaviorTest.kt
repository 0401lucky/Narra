package com.example.myapplication.ui.screen.settings.worldbook

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.screenshot.ScreenshotCases
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Duration

class WorldBookUiBehaviorTest : P06WorldBookTestSupport() {
    @Test
    fun list_realActionsKeepBookAndEntryIdsAndDoNotToggleData() {
        renderList(ScreenshotCases.require("E06_list-matcha-light"))
        touch(pageNode(hasText("新增") and hasClickAction()))
        touch(reveal(hasText("导入世界书") and hasClickAction()))
        touch(reveal(hasText(BookName) and hasClickAction()))
        entryAtEnd(detail = false)
        touch(pageNode(hasText(LastStandaloneTitle) and hasClickAction()))
        touch(pageNode(hasContentDescription("返回")))
        assertEquals(
            listOf(
                "add",
                "import",
                "book:$BookId",
                "entry:${StandaloneEntries.last().id}",
                "back"
            ), events
        )
        assertTrue(savedEntries.isEmpty())
    }

    @Test
    fun list_threeFiltersAreAndedAndClearingThemKeepsSearch() {
        val entries = listOf(
            BookEntries[0].copy(
                id = "wanted",
                sourceBookName = "甲书",
                title = "海风目标",
                enabled = true
            ),
            BookEntries[1].copy(
                id = "disabled",
                sourceBookName = "甲书",
                title = "海风停用",
                enabled = false
            ),
            BookEntries[2].copy(
                id = "other-book",
                bookId = "other",
                sourceBookName = "乙书",
                title = "海风他书",
                enabled = true
            ),
            StandaloneEntries[0].copy(title = "海风独立"),
        )
        renderList(ScreenshotCases.require("E06_list-matcha-light"), entries)
        pageNode(hasSetTextAction()).performTextReplacement("海风")
        touch(pageNode(hasText("筛选") and hasClickAction()))
        selectListFilter(0, "可挂载")
        selectListFilter(1, "启用")
        selectListFilter(2, "甲书")
        reveal(hasText("甲书") and hasClickAction() and !isSelectable()).assertIsDisplayed()
        pageNode(hasText("甲书") and hasClickAction() and !isSelectable()).assert(hasText("1 条条目"))
        pageNode(hasText("乙书") and hasClickAction() and !isSelectable()).assertDoesNotExist()
        touch(reveal(hasText("清除筛选") and hasClickAction()))
        pageNode(hasText("海风") and hasSetTextAction()).assertExists()
        pageNode(hasText("筛选") and hasClickAction()).assertExists()
        reveal(hasText("乙书") and hasClickAction() and !isSelectable()).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun list_searchEmptyClearOnlyClearsQuery() {
        renderList(ScreenshotCases.require("E06_list-matcha-light"))
        touch(pageNode(hasText("筛选") and hasClickAction()))
        selectListFilter(0, "全局")
        pageNode(hasSetTextAction()).performTextReplacement("不存在的合成条目")
        reveal(hasText("没有匹配结果")).assertIsDisplayed()
        touch(reveal(hasText("清空搜索") and hasClickAction()))
        pageNode(hasText("筛选 (1)") and hasClickAction()).assertExists()
        pageNode(hasText("全局") and isSelectable()).assertIsSelected()
        assertTrue(events.isEmpty())
    }

    @Test
    fun list_filterEmptyClearRestoresResultsWithoutWriting() {
        renderList(ScreenshotCases.require("E06_list-matcha-light"))
        touch(pageNode(hasText("筛选") and hasClickAction()))
        selectListFilter(0, "会话")
        reveal(hasText("当前筛选下没有条目")).assertIsDisplayed()
        touch(reveal(hasText("清筛选") and hasClickAction()))
        reveal(hasText(BookName) and hasClickAction() and !isSelectable()).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun list_globalEmptyKeepsTheAddEntryAction() {
        renderList(ScreenshotCases.require("E06_list-empty-light"), emptyList())
        reveal(hasText("还没有世界书")).assertIsDisplayed()
        pageNode(hasText("清筛选") and hasClickAction()).assertDoesNotExist()
        touch(pageNode(hasText("新增") and hasClickAction()))
        assertEquals(listOf("add"), events)
    }

    @Test
    fun detail_renameChecksTrimmedNameBusyStateAndImeUsesSameCallback() {
        val busy = mutableStateOf(false)
        val name = mutableStateOf(BookName)
        renderP06(normalCase) {
            WorldBookBookDetailScreen(
                BookId,
                name.value,
                BookEntries,
                busy.value,
                onRenameBook = { id, title -> events += "rename:$id:$title" },
                onDeleteBook = { events += "delete:$it" },
                onAddEntry = {},
                onOpenEntryEdit = {},
                onNavigateBack = { events += "back" })
        }
        touch(reveal(hasText("管理世界书") and hasClickAction()))
        reveal(hasText("保存书名") and hasClickAction()).assertIsNotEnabled()
        input("书名").performTextReplacement("  ")
        reveal(hasText("保存书名") and hasClickAction()).assertIsNotEnabled()
        input("书名").performTextReplacement("  新海岸  ")
        touch(reveal(hasText("保存书名") and hasClickAction()))
        input("书名").performImeAction()
        assertEquals(listOf("rename:$BookId:新海岸", "rename:$BookId:新海岸"), events)
        composeRule.runOnIdle { busy.value = true }
        reveal(hasText("处理中…") and hasClickAction()).assertIsNotEnabled()
        reveal(hasText("删除整本世界书") and hasClickAction()).assertIsNotEnabled()
        composeRule.runOnIdle { busy.value = false; name.value = "服务器回传书名" }
        input("书名").assertTextContains("服务器回传书名")
    }

    @Test
    fun detail_deleteCancelDoesNotWriteAndConfirmDoesNotPrematurelyNavigate() {
        renderDetail(normalCase)
        touch(reveal(hasText("管理世界书") and hasClickAction()))
        touch(reveal(hasText("删除整本世界书") and hasClickAction()))
        assertTrue(events.isEmpty())
        touch(windowNode(dialogMatcher("删除整本世界书"), hasText("取消") and hasClickAction()))
        assertTrue(events.isEmpty())
        touch(reveal(hasText("删除整本世界书") and hasClickAction()))
        touch(windowNode(dialogMatcher("删除整本世界书"), hasText("确认删除") and hasClickAction()))
        assertEquals(listOf("delete-book:$BookId"), events)
        pageNode(hasText("新增") and hasClickAction()).assertExists()
    }

    @Test
    fun editor_saveRetainsIdentityExtrasAndUneditedFieldsWithOriginalConversions() {
        val original = EditorEntry.copy(content = "旧正文\n")
        renderEditor(normalCase, original)
        input("标题").performTextReplacement("新标题")
        input("内容").performTextReplacement("新的正文\n保留末尾换行\n")
        input("所属世界书").performTextReplacement("  改名但保留书ID  ")
        setSection("基本信息", false)
        input("优先级").performTextReplacement("-4")
        touch(toggle("区分大小写"))
        setSection("命中规则", false)
        selectScope(WorldBookScopeType.GLOBAL)
        setSection("作用域", false)
        setSection("状态与高级", true)
        touch(toggle("启用条目"))
        touch(toggle("常驻注入"))
        touch(reveal(hasText("高级参数") and hasClickAction()))
        input("插入顺序").performTextReplacement("-7")
        input("触发概率 (%)").performTextReplacement("180")
        saveDraft()
        val saved = savedEntries.single()
        assertEquals(
            original.copy(
                title = "新标题",
                content = "新的正文\n保留末尾换行\n",
                sourceBookName = "改名但保留书ID",
                priority = -4,
                insertionOrder = -7,
                probability = 100,
                enabled = false,
                alwaysActive = true,
                caseSensitive = true,
                scopeType = WorldBookScopeType.GLOBAL,
                scopeId = "",
                updatedAt = saved.updatedAt
            ), saved
        )
        assertTrue(saved.updatedAt >= FixedEpochMillis)
        assertEquals(listOf("save:${original.id}", "back"), events)
    }

    @Test
    fun editor_invalidNumbersFallBackWithoutIntroducingNewSaveGates() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        input("优先级").performTextReplacement("999999999999999999999999")
        setSection("命中规则", false)
        setSection("状态与高级", true)
        touch(reveal(hasText("高级参数") and hasClickAction()))
        input("插入顺序").performTextReplacement("")
        input("触发概率 (%)").performTextReplacement("-")
        saveDraft()
        assertEquals(0, savedEntries.single().priority)
        assertEquals(0, savedEntries.single().insertionOrder)
        assertEquals(100, savedEntries.single().probability)
    }

    @Test
    fun editor_negativeProbabilityClampsToZero() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        setSection("命中规则", false)
        setSection("状态与高级", true)
        touch(reveal(hasText("高级参数") and hasClickAction()))
        input("触发概率 (%)").performTextReplacement("-1")
        saveDraft()
        assertEquals(0, savedEntries.single().probability)
    }

    @Test
    fun editor_blankRequiredFieldsAndMissingScopeKeepSaveDisabled() {
        renderEditor(normalCase, entry = null)
        pageNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
        input("标题").performTextReplacement("标题")
        input("内容").performTextReplacement("正文")
        pageNode(hasText("保存") and hasClickAction()).assertIsEnabled()
        setSection("基本信息", false)
        setSection("命中规则", false)
        selectScope(WorldBookScopeType.ASSISTANT)
        pageNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
        touch(reveal(hasText(Assistants.first().name) and isSelectable()))
        pageNode(hasText("保存") and hasClickAction()).assertIsEnabled()
        selectScope(WorldBookScopeType.ATTACHABLE)
        touch(reveal(hasText("去角色页挂载") and hasClickAction()))
        assertEquals(listOf("mount"), events)
        assertTrue(savedEntries.isEmpty())
        saveDraft()
        assertEquals(WorldBookScopeType.ATTACHABLE, savedEntries.single().scopeType)
        assertEquals("", savedEntries.single().scopeId)
    }

    @Test
    fun editor_keywordCommitAndRemovalKeepUnsubmittedDraftOutOfSave() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文", keywords = emptyList()))
        setSection("基本信息", false)
        keywordInput("主关键词").performTextInput("白塔，/sea,wind/i，白塔，")
        keywordInput("主关键词").performTextInput("灯塔")
        keywordInput("主关键词").performImeAction()
        touch(reveal(hasText("白塔") and hasClickAction()))
        keywordInput("主关键词").performTextInput("未提交的尾部")
        saveDraft()
        assertEquals(listOf("/sea,wind/i", "灯塔"), savedEntries.single().keywords)
        assertEquals(EditorEntry.secondaryKeywords, savedEntries.single().secondaryKeywords)
    }

    @Test
    fun editor_secondarySwitchDisablesInputAndPreservesExistingKeywords() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        touch(toggle("次级匹配"))
        reveal(hasContentDescription("次级关键词")).assertIsNotEnabled()
        pageNode(hasText(EditorEntry.secondaryKeywords.first()) and hasClickAction()).assertIsNotEnabled()
        saveDraft()
        assertFalse(savedEntries.single().selective)
        assertEquals(EditorEntry.secondaryKeywords, savedEntries.single().secondaryKeywords)
    }

    @Test
    fun editor_bodyCancelAndConfirmOnlyUpdateSecondLevelDraft() {
        renderEditor(normalCase, EditorEntry.copy(content = "原正文\n"))
        openBodySheet()
        windowNode(
            dialogMatcher("编辑正文"),
            hasTestTag("worldbook_fullscreen_content")
        ).performTextReplacement("取消的内容")
        touch(windowNode(dialogMatcher("编辑正文"), hasContentDescription("取消正文编辑")))
        input("内容").assertTextContains("原正文\n")
        assertTrue(events.isEmpty())
        openBodySheet()
        windowNode(
            dialogMatcher("编辑正文"),
            hasTestTag("worldbook_fullscreen_content")
        ).performTextReplacement("已确认正文\n")
        touch(windowNode(dialogMatcher("编辑正文"), hasText("保存并返回") and hasClickAction()))
        input("内容").assertTextContains("已确认正文\n")
        assertTrue(events.isEmpty())
        assertTrue(savedEntries.isEmpty())
        saveDraft()
        assertEquals("已确认正文\n", savedEntries.single().content)
    }

    @Test
    fun editor_deleteUsesOriginalIdentityAndCancelDoesNotNavigate() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文"))
        input("标题").performTextReplacement("未保存的新标题")
        touch(reveal(hasText("删除条目") and hasClickAction()))
        windowNode(
            dialogMatcher("删除世界书条目"),
            hasText("将删除《${EditorEntry.title}》，这个操作不可撤销。")
        ).assertExists()
        touch(windowNode(dialogMatcher("删除世界书条目"), hasText("取消") and hasClickAction()))
        assertTrue(events.isEmpty())
        touch(reveal(hasText("删除条目") and hasClickAction()))
        touch(windowNode(dialogMatcher("删除世界书条目"), hasText("确认删除") and hasClickAction()))
        assertEquals(listOf("delete:${EditorEntry.id}", "back"), events)
        assertTrue(savedEntries.isEmpty())
    }

    @Test
    fun editor_backDoesNotSaveAndPreviewDoesNotPersist() {
        renderEditor(normalCase, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        setSection("命中规则", false)
        setSection("试命中（预览）", true)
        input("待测文本").performTextReplacement("白塔城的旅人寻找灯塔")
        touch(reveal(hasText("试命中") and hasClickAction()))
        pageNode(hasText("命中：会被自动注入")).assertExists()
        setSection("命中规则", true)
        touch(reveal(hasText("正则") and isSelectable()))
        setSection("命中规则", false)
        reveal(hasText("命中：会被自动注入")).assertExists()
        input("待测文本").performTextReplacement("完全无关")
        pageNode(hasText("命中：会被自动注入")).assertDoesNotExist()
        touch(reveal(hasText("试命中") and hasClickAction()))
        pageNode(hasText("未命中：不会被自动注入")).assertExists()
        touch(pageNode(hasContentDescription("返回")))
        assertEquals(listOf("back"), events)
        assertTrue(savedEntries.isEmpty())
    }

    @Test
    fun list_preservesDeleteOnlyMessageFilter() = assertMessageFilter(detail = false)

    @Test
    fun detail_preservesRenameOnlyMessageFilter() = assertMessageFilter(detail = true)

    private fun assertMessageFilter(detail: Boolean) {
        val message = mutableStateOf<String?>("世界书已保存")
        var consumed = 0
        val consume = { consumed++; message.value = null }
        renderP06(normalCase) {
            if (detail) {
                WorldBookBookDetailScreen(
                    BookId, BookName, BookEntries, false,
                    onRenameBook = { _, _ -> }, onDeleteBook = {}, onAddEntry = {},
                    onOpenEntryEdit = {}, onNavigateBack = {}, uiMessage = message.value,
                    onConsumeMessage = consume
                )
            } else {
                WorldBookListScreen(
                    Entries, onOpenBook = {}, onOpenEntryEdit = {},
                    onAddEntry = {}, onOpenImport = {}, onNavigateBack = {},
                    uiMessage = message.value, onConsumeMessage = consume
                )
            }
        }
        pageNode(hasText("世界书已保存")).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(3_200)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3_200))
        composeRule.waitForIdle()
        assertEquals(1, consumed)
        val shown = if (detail) "世界书已重命名" else "世界书已删除"
        composeRule.runOnIdle { message.value = shown }
        pageNode(hasText(shown)).assertExists()
    }
}
