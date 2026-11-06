package com.example.myapplication.ui.screenshot

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookBookDetailScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookEditScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookListScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 真正的四类 Screen 输入都是合成状态；没有 ViewModel/AppGraph、文件或网络依赖。 */
abstract class WorldBookRenderTest : P06RenderTest() {
    protected val events = mutableListOf<String>()
    protected val savedEntries = mutableListOf<WorldBookEntry>()

    override fun assertCaptureIsolation() {
        assertTrue("捕获不能触发保存/删除/导航", events.isEmpty())
        assertTrue(savedEntries.isEmpty())
        measurements["isolation"] =
            "Application; local Screen callbacks; no AppGraph/repository/API/file IO"
    }

    protected fun renderList(case: ScreenshotCase, entries: List<WorldBookEntry> = Entries) {
        renderP06(case) {
            WorldBookListScreen(
                entries = entries,
                onOpenBook = { events += "book:$it" },
                onOpenEntryEdit = { events += "entry:$it" },
                onAddEntry = { events += "add" },
                onOpenImport = { events += "import" },
                onNavigateBack = { events += "back" },
            )
        }
    }

    protected fun renderDetail(case: ScreenshotCase) {
        renderP06(case) {
            WorldBookBookDetailScreen(
                bookId = BookId,
                bookName = BookName,
                entries = BookEntries,
                isSaving = false,
                onRenameBook = { id, name -> events += "rename:$id:$name" },
                onDeleteBook = { events += "delete-book:$it" },
                onAddEntry = { events += "add" },
                onOpenEntryEdit = { events += "entry:$it" },
                onNavigateBack = { events += "back" },
            )
        }
    }

    protected fun renderEditor(
        case: ScreenshotCase,
        entry: WorldBookEntry? = EditorEntry,
        bookNames: List<String> = BookNames,
        conversations: List<Conversation> = Conversations,
    ) {
        renderP06(case) {
            WorldBookEditScreen(
                entry = entry,
                isNew = entry == null,
                assistants = Assistants,
                conversations = conversations,
                existingBookNames = bookNames,
                onSave = { savedEntries += it; events += "save:${it.id}" },
                onDelete = { events += "delete:${it}" },
                onNavigateBack = { events += "back" },
                onOpenAssistantMount = { events += "mount" },
            )
        }
    }

    protected fun toggleSection(title: String) {
        captureClick(reveal(hasText(title) and hasClickAction()), "section:$title")
    }

    protected fun openManagement() {
        captureClick(reveal(hasText("管理世界书") and hasClickAction()), "management")
    }

    protected fun openBookPopup() {
        val field = reveal(hasText("所属世界书") and hasSetTextAction())
        captureClick(field, "open-book-popup")
        popupRoot(LastBookName)
    }

    protected fun openConversationPopup() {
        toggleSection("基本信息")
        toggleSection("命中规则")
        toggleSection("作用域")
        captureClick(reveal(hasText("会话") and isSelectable()), "scope:conversation")
        captureClick(reveal(hasText("所属会话") and hasClickAction()), "open-conversation-popup")
        popupRoot(LastConversationTitle)
    }

    protected fun popupToEnd(choice: String) {
        val matcher = popupMatcher(choice)
        val scroll =
            windowNode(matcher, hasScrollAction() and verticalScrollMatcher, unmerged = true)
        scrollToEnd(scroll, "popup")
        assertTrue(
            "长选项矩阵必须发生真实滚动",
            scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0
        )
        val item = windowNode(matcher, hasText(choice) and hasClickAction())
        item.assertIsDisplayed()
        recordBounds("lastChoice", item, popupRoot(choice))
        requireFullyVisible("lastChoice")
        measurements["lastChoiceIdentity"] = when (choice) {
            LastBookName -> "sourceBookName=$LastBookName"
            LastConversationTitle -> "conversationId=${Conversations.last().id}"
            else -> error("未登记的末项")
        }
    }

    protected fun openBodySheet() {
        captureClick(reveal(hasContentDescription("全屏编辑正文")), "open-body-sheet")
        dialogRoot("编辑正文")
    }

    protected fun bodyToEnd() {
        val root = dialogMatcher("编辑正文")
        val field = windowNode(root, hasTestTag("worldbook_fullscreen_content"))
        field.performSemanticsAction(SemanticsActions.ScrollBy) { assertTrue(it(0f, 100_000f)) }
        composeRule.waitForIdle()
        clearFocusForCapture(dialogRoot("编辑正文"))
        field.assertIsNotFocused()
        val scroll = LegacyTextFieldScrollProbe.read(field)
        assertTrue("长正文必须确实有内部滚动范围", scroll.maximum > 0f)
        assertEquals("正文内部必须达到真实滚动末尾", scroll.maximum, scroll.offset, 1f)
        measurements["bodyScroll"] = "${scroll.offset}/${scroll.maximum}"
        measurements["bodyViewport"] = scroll.viewport.toString()
        measurements["bodyViewportSize"] = scroll.viewportSize.toString()
        actions += "body-scrollBy:end"
        measurements["bodyFocused"] =
            field.fetchSemanticsNode().config[SemanticsProperties.Focused].toString()
        val layout = textLayout(field)
        assertEquals(LongContent, layout.layoutInput.text.text)
        val lastLine = layout.lineCount - 1
        val visibleTop = layout.getLineTop(lastLine) - scroll.offset
        val visibleBottom = layout.getLineBottom(lastLine) - scroll.offset
        assertTrue(
            "正文末行必须真实滚入内部视口：$visibleTop/$visibleBottom height=${scroll.viewport.height}",
            visibleTop >= -1f && visibleBottom <= scroll.viewport.height + 1f
        )
        measurements["bodyLastMarker"] = BodyLastMarker
        measurements["bodyLineCount"] = layout.lineCount.toString()
        measurements["bodyLastLine"] =
            "${layout.getLineTop(layout.lineCount - 1)}/${layout.getLineBottom(layout.lineCount - 1)}"
        measurements["bodyLastLineInViewport"] =
            "$visibleTop/$visibleBottom within 0/${scroll.viewport.height}"
        measurements["bodyLastLineText"] = layout.layoutInput.text.substring(
            layout.getLineStart(lastLine),
            layout.getLineEnd(lastLine)
        )
        measurements["bodyLastLineVisible"] = "true"
        recordBounds("bodyField", field, dialogRoot("编辑正文"))
    }

    protected fun showAdvancedPreview() {
        toggleSection("基本信息")
        toggleSection("命中规则")
        toggleSection("状态与高级")
        captureClick(reveal(hasText("高级参数") and hasClickAction()), "advanced")
        reveal(hasText("触发概率 (%)") and hasSetTextAction()).assertIsDisplayed()
        toggleSection("试命中（预览）")
        reveal(hasText("待测文本") and hasSetTextAction()).performTextReplacement("旅人在白塔城寻找灯塔")
        captureClick(reveal(hasText("试命中") and hasClickAction()), "preview")
        pageNode(hasText("命中：会被自动注入")).assertExists()
        measurements["preview"] = "production-previewHit:matched"
        scrollToEnd()
        pageNode(hasText("删除后无法恢复")).assertIsDisplayed()
        recordBounds("deleteEntry", pageNode(hasText("删除条目") and hasClickAction()))
    }

    protected fun entryAtEnd(detail: Boolean) {
        val entry = if (detail) BookEntries.last() else StandaloneEntries.last()
        val key = if (detail) entry.id else "entry-${entry.id}"
        pageList().performScrollToKey(key)
        scrollToEnd()
        val card = pageNode(hasText(entry.title) and hasClickAction())
        card.assertIsDisplayed()
        measurements["lastEntryId"] = entry.id
        measurements["lastLazyKey"] = key
        recordBounds("lastEntry", card)
        requireFullyVisible("lastEntry")
    }

    companion object {
        const val BookId = "p06-book-islands"
        const val BookName = "海风档案：群岛旅人与旧日传说"
        const val LastEntryTitle = "末条：灯塔"
        const val LastStandaloneTitle = "末项：归航"
        const val BodyLastMarker = "正文末尾：灯塔一直亮着。"
        const val LastBookName = "远行笔记：最后一卷的灯塔与归途"
        const val LastConversationTitle = "末次会话：暴雨后的灯塔与归途"
        val LongContent =
            (1..16).joinToString("\n") { "第 $it 段：旅人沿海岸寻找白塔城，守灯人记下潮汐与归船。" } + "\n$BodyLastMarker"
        val BookNames = listOf(
            BookName,
            "群岛植物志：春天的海岸",
            "潮汐书信与旅人的约定",
            "旧港城的街巷与回忆",
            "灯塔值班簿：漫长冬夜里的回声",
            "海岸航海图：迷雾之后的群山",
            "远海来信：夏天尚未完成的旅程",
            LastBookName
        )
        val Assistants = listOf(
            Assistant(id = "p06-assistant-tower", name = "守灯人：来自群岛北端的林澈"),
            Assistant(id = "p06-assistant-traveler", name = "远行的旅人"),
        )
        val Conversations = (1..5).map { index ->
            Conversation(
                id = "p06-conversation-$index",
                title = when (index) {
                    1 -> ""; 5 -> LastConversationTitle; else -> "群岛会话 $index：关于潮汐与归航"
                },
                createdAt = FixedEpochMillis - index * 86_400_000L,
                updatedAt = FixedEpochMillis - index * 600_000L,
            )
        }
        val BookEntries = (1..6).map { index ->
            WorldBookEntry(
                id = "p06-book-entry-$index",
                bookId = BookId,
                sourceBookName = BookName,
                title = if (index == 6) LastEntryTitle else "群岛记事 $index：海风与旧日约定",
                content = if (index == 6) "守灯人点亮灯塔。" else "群岛由古老灯塔连接，潮汐带来旅人的消息。",
                keywords = if (index == 6) emptyList() else listOf("白塔城", "/islands?,harbor/i"),
                enabled = index != 2,
                alwaysActive = index == 3,
                scopeType = WorldBookScopeType.ATTACHABLE,
                insertionOrder = index,
                createdAt = FixedEpochMillis - 10 * 86_400_000L + index,
                updatedAt = FixedEpochMillis - 600_000L,
                extrasJson = "{\"fixture_extension\":\"preserve\"}",
            )
        }
        val StandaloneEntries = (1..3).map { index ->
            WorldBookEntry(
                id = "p06-standalone-$index",
                title = if (index == 3) LastStandaloneTitle else "独立规则 $index：归船的约定",
                content = if (index == 3) "潮退后归航。" else "旅人遵循海岸的约定，在天黑之前返回旧港。",
                keywords = if (index == 3) emptyList() else listOf("归航"),
                alwaysActive = index == 1,
                enabled = index != 2,
                scopeType = WorldBookScopeType.GLOBAL,
                insertionOrder = index,
                createdAt = FixedEpochMillis - 86_400_000L + index,
                updatedAt = FixedEpochMillis - 3_600_000L,
            )
        }
        val Entries = BookEntries + StandaloneEntries
        val EditorEntry = BookEntries.first().copy(
            title = "白塔城的潮汐：旅人与守灯人的约定",
            content = LongContent,
            keywords = listOf("白塔城", "远方海岸的守灯人与古老约定", "/islands?,harbor/i"),
            aliases = listOf("灯塔", "旧港"),
            secondaryKeywords = listOf("旅人", "潮汐"),
            selective = true,
            caseSensitive = false,
            matchMode = WorldBookMatchMode.WORD_CJK,
            priority = 8,
            insertionOrder = -2,
            probability = 75,
            scopeType = WorldBookScopeType.ASSISTANT,
            scopeId = Assistants.first().id,
            extrasJson = "{\"depth\":4,\"position_override\":\"before\",\"fixture_extension\":{\"preserve\":true}}",
        )
        val verticalScrollMatcher =
            SemanticsMatcher("有真实纵向滚动范围") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
    }
}
