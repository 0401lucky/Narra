package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.example.myapplication.model.WorldBookEntry
import org.junit.Test

/** 八种生产解析 scheme 下读取真实 TextLayout 和当前 View 像素，包含禁用 alpha。 */
class WorldBookColorsTest : P06ColorTestSupport() {
    @Test
    fun list_titlesStatusPillsAndFocusedSearchUseReadableRealSurfaces() {
        val current = mutableStateOf(schemes.first())
        renderColors(current) {
            WorldBookListScreen(
                Entries,
                onOpenBook = {},
                onOpenEntryEdit = {},
                onAddEntry = {},
                onOpenImport = {},
                onNavigateBack = {})
        }
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            reveal(hasText(BookName) and hasClickAction())
            assertReadable(
                pageNode(hasText(BookName), unmerged = true),
                expected = scheme.onSurface
            )
            assertReadable(
                pageNode(hasText("启用 5"), unmerged = true),
                expected = scheme.onPrimaryContainer
            )
            touch(pageNode(hasSetTextAction()))
            assertReadable(
                pageNode(hasText("搜索世界书"), unmerged = true),
                expected = scheme.onSurface
            )
        }
    }

    @Test
    fun list_selectedFilterAndReadonlyStoppedStatusRemainReadable() {
        val current = mutableStateOf(schemes.first())
        renderColors(current) {
            WorldBookListScreen(
                Entries,
                onOpenBook = {},
                onOpenEntryEdit = {},
                onAddEntry = {},
                onOpenImport = {},
                onNavigateBack = {})
        }
        touch(pageNode(hasText("筛选") and hasClickAction()))
        selectListFilter(0, "全局")
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val filter = reveal(hasText("全局") and isSelectable())
            assertReadable(
                pageNode(
                    hasText("全局") and hasAnyAncestor(isSelectable()),
                    unmerged = true
                ), controlSurface(filter), expected = scheme.onPrimaryContainer
            )
            reveal(hasText("已停用"))
            assertReadable(
                pageNode(hasText("已停用"), unmerged = true),
                expected = scheme.onSurfaceVariant
            )
        }
    }

    @Test
    fun editor_selectedModeRegexAndFocusedLabelUseTheirActualContainers() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val mode = reveal(hasText("整词") and isSelectable())
            assertReadable(
                pageNode(hasText("整词"), unmerged = true),
                controlSurface(mode),
                expected = scheme.onPrimaryContainer
            )
            val regex = "正则 /islands?,harbor/i"
            val chip = reveal(hasText(regex) and hasClickAction())
            assertReadable(
                pageNode(hasText(regex), unmerged = true),
                controlSurface(chip),
                expected = scheme.onPrimaryContainer
            )
            touch(input("优先级"))
            assertReadable(
                pageNode(hasText("优先级"), unmerged = true),
                expected = scheme.onSurface
            )
        }
    }

    @Test
    fun editor_disabledSecondaryKeywordRemainsReadableWithoutBecomingEditable() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry.copy(content = "正文", selective = false))
        setSection("基本信息", false)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            reveal(hasContentDescription("次级关键词")).assertIsNotEnabled()
            val chip = reveal(hasText("旅人") and hasClickAction()).assertIsNotEnabled()
            assertReadable(
                pageNode(hasText("旅人"), unmerged = true),
                controlSurface(chip),
                expected = scheme.onSecondaryContainer
            )
        }
    }

    @Test
    fun editor_requiredMessageAndDisabledSaveKeepReadablePixels() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, entry = null)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            assertReadable(
                pageNode(hasText("标题和正文为必填项"), unmerged = true),
                expected = scheme.error
            )
            val save = pageNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
            assertReadable(pageNode(hasText("保存"), unmerged = true), controlSurface(save))
        }
    }

    @Test
    fun detail_busySaveAndDeleteHaveReadableActualPixels() {
        val current = mutableStateOf(schemes.first())
        renderColors(current) {
            WorldBookBookDetailScreen(
                BookId,
                BookName,
                BookEntries,
                true,
                onRenameBook = { _, _ -> },
                onDeleteBook = {},
                onAddEntry = {},
                onOpenEntryEdit = {},
                onNavigateBack = {})
        }
        touch(reveal(hasText("管理世界书") and hasClickAction()))
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            for (label in listOf("处理中…", "删除整本世界书")) {
                val control = reveal(hasText(label) and hasClickAction()).assertIsNotEnabled()
                assertReadable(pageNode(hasText(label), unmerged = true), controlSurface(control))
            }
        }
    }

    @Test
    fun helpDialog_readsItsOwnViewPixelsAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderColors(current) {
            WorldBookListScreen(
                Entries,
                onOpenBook = {},
                onOpenEntryEdit = {},
                onAddEntry = {},
                onOpenImport = {},
                onNavigateBack = {})
        }
        touch(pageNode(hasContentDescription("世界书：查看说明")))
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = dialogRoot("世界书")
            for (label in listOf("世界书", "知道了")) {
                val text = windowNode(dialogMatcher("世界书"), hasText(label), unmerged = true)
                assertReadable(text, besideText(text, root), root, scheme.onSurface)
            }
        }
    }

    @Test
    fun bookPopup_namesUseNativePopupPixelsAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry.copy(content = "正文"))
        openBookPopup()
        scrollToEnd(
            windowNode(popupMatcher(LastBookName), hasScrollAction(), unmerged = true),
            "bookColor"
        )
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = popupRoot(LastBookName)
            val text =
                windowNode(popupMatcher(LastBookName), hasText(LastBookName), unmerged = true)
            assertReadable(text, besideText(text, root), root, scheme.onSurface)
        }
    }

    @Test
    fun conversationPopup_titleAndRelativeTimeUseActualPopupSurface() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry.copy(content = "正文"))
        openConversationPopup()
        scrollToEnd(
            windowNode(
                popupMatcher(LastConversationTitle),
                hasScrollAction(),
                unmerged = true
            ), "conversationColor"
        )
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = popupRoot(LastConversationTitle)
            val title = windowNode(
                popupMatcher(LastConversationTitle),
                hasText(LastConversationTitle),
                unmerged = true
            )
            val time = windowNode(
                popupMatcher(LastConversationTitle),
                hasText("50分钟前"),
                unmerged = true
            )
            assertReadable(title, besideText(title, root), root, scheme.onSurface)
            assertReadable(time, besideText(time, root), root, scheme.onSurfaceVariant)
        }
    }

    @Test
    fun editor_selectedRoleAndPreviewTextStayReadableAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry.copy(content = "正文"))
        setSection("基本信息", false)
        setSection("命中规则", false)
        setSection("作用域", true)
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val role = reveal(hasText(Assistants.first().name) and isSelectable())
            assertReadable(
                pageNode(hasText(Assistants.first().name), unmerged = true),
                controlSurface(role),
                expected = scheme.onPrimaryContainer
            )
        }
        setSection("作用域", false)
        setSection("试命中（预览）", true)
        input("待测文本").performTextReplacement("白塔城和旅人")
        touch(reveal(hasText("试命中") and hasClickAction()))
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            reveal(hasText("命中：会被自动注入"))
            assertReadable(
                pageNode(hasText("命中：会被自动注入"), unmerged = true),
                expected = scheme.onSurface
            )
        }
    }

    @Test
    fun editor_bodySheetTextAndSaveUseTheRealSheetSurfaceAcrossEightSchemes() {
        val current = mutableStateOf(schemes.first())
        renderEditorColors(current, EditorEntry)
        openBodySheet()
        bodyToEnd()
        schemes.forEach { scheme ->
            composeRule.runOnIdle { current.value = scheme }
            val root = dialogRoot("编辑正文")
            val field =
                windowNode(dialogMatcher("编辑正文"), hasTestTag("worldbook_fullscreen_content"))
            assertReadable(field, controlSurface(field, root), root, scheme.onSurface)
            val save =
                windowNode(dialogMatcher("编辑正文"), hasText("保存并返回") and hasClickAction())
            val text = windowNode(dialogMatcher("编辑正文"), hasText("保存并返回"), unmerged = true)
            assertReadable(text, controlSurface(save, root), root, scheme.onPrimary)
        }
    }

    private fun renderEditorColors(
        current: androidx.compose.runtime.State<androidx.compose.material3.ColorScheme>,
        entry: WorldBookEntry?
    ) {
        renderColors(current) {
            WorldBookEditScreen(
                entry,
                entry == null,
                Assistants,
                Conversations,
                existingBookNames = BookNames,
                onSave = { savedEntries += it },
                onDelete = {},
                onNavigateBack = {})
        }
    }
}
