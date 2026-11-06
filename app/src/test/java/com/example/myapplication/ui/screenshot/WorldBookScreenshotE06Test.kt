package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import org.junit.Test

/** E06 世界书 47 个独立捕获；进入状态走真实语义回调，不把探针混入图片计数。 */
class WorldBookScreenshotE06Test : WorldBookRenderTest() {
    @Test
    fun e06_list_matcha_light() = captureScene("E06_list-matcha-light")

    @Test
    fun e06_list_matcha_dark() = captureScene("E06_list-matcha-dark")

    @Test
    fun e06_list_vanilla_light() = captureScene("E06_list-vanilla-light")

    @Test
    fun e06_list_vanilla_dark() = captureScene("E06_list-vanilla-dark")

    @Test
    fun e06_list_ocean_light() = captureScene("E06_list-ocean-light")

    @Test
    fun e06_list_ocean_dark() = captureScene("E06_list-ocean-dark")

    @Test
    fun e06_detail_light() = captureScene("E06_detail-light")

    @Test
    fun e06_detail_dark() = captureScene("E06_detail-dark")

    @Test
    fun e06_list_dynamic_light() = captureScene("E06_list-dynamic-light")

    @Test
    fun e06_list_dynamic_dark() = captureScene("E06_list-dynamic-dark")

    @Test
    fun e06_detail_dynamic_light() = captureScene("E06_detail-dynamic-light")

    @Test
    fun e06_detail_dynamic_dark() = captureScene("E06_detail-dynamic-dark")

    @Test
    fun e06_list_filters_narrow_light() = captureScene("E06_list-filters-narrow-light")

    @Test
    fun e06_list_filters_narrow_dark() = captureScene("E06_list-filters-narrow-dark")

    @Test
    fun e06_list_tail_narrow_light() = captureScene("E06_list-tail-narrow-light")

    @Test
    fun e06_list_tail_narrow_dark() = captureScene("E06_list-tail-narrow-dark")

    @Test
    fun e06_detail_management_narrow_light() = captureScene("E06_detail-management-narrow-light")

    @Test
    fun e06_detail_management_narrow_dark() = captureScene("E06_detail-management-narrow-dark")

    @Test
    fun e06_detail_tail_narrow_light() = captureScene("E06_detail-tail-narrow-light")

    @Test
    fun e06_detail_tail_narrow_dark() = captureScene("E06_detail-tail-narrow-dark")

    @Test
    fun e06_list_help_dialog_light() = captureScene("E06_list-help-dialog-light")

    @Test
    fun e06_list_help_dialog_dark() = captureScene("E06_list-help-dialog-dark")

    @Test
    fun e06_book_delete_dialog_light() = captureScene("E06_book-delete-dialog-light")

    @Test
    fun e06_book_delete_dialog_dark() = captureScene("E06_book-delete-dialog-dark")

    @Test
    fun e06_list_empty_light() = captureScene("E06_list-empty-light")

    @Test
    fun e06_list_search_empty_dark() = captureScene("E06_list-search-empty-dark")

    @Test
    fun e06_list_filter_empty_light() = captureScene("E06_list-filter-empty-light")

    @Test
    fun e06_editor_new_required_narrow_light() =
        captureScene("E06_editor-new-required-narrow-light")

    @Test
    fun e06_editor_new_required_narrow_dark() = captureScene("E06_editor-new-required-narrow-dark")

    @Test
    fun e06_editor_existing_light() = captureScene("E06_editor-existing-light")

    @Test
    fun e06_editor_existing_dark() = captureScene("E06_editor-existing-dark")

    @Test
    fun e06_editor_rules_narrow_light() = captureScene("E06_editor-rules-narrow-light")

    @Test
    fun e06_editor_rules_narrow_dark() = captureScene("E06_editor-rules-narrow-dark")

    @Test
    fun e06_editor_scope_narrow_light() = captureScene("E06_editor-scope-narrow-light")

    @Test
    fun e06_editor_scope_narrow_dark() = captureScene("E06_editor-scope-narrow-dark")

    @Test
    fun e06_editor_advanced_tail_narrow_light() =
        captureScene("E06_editor-advanced-tail-narrow-light")

    @Test
    fun e06_editor_advanced_tail_narrow_dark() =
        captureScene("E06_editor-advanced-tail-narrow-dark")

    @Test
    fun e06_editor_body_sheet_light() = captureScene("E06_editor-body-sheet-light")

    @Test
    fun e06_editor_body_sheet_dark() = captureScene("E06_editor-body-sheet-dark")

    @Test
    fun e06_entry_delete_dialog_light() = captureScene("E06_entry-delete-dialog-light")

    @Test
    fun e06_entry_delete_dialog_dark() = captureScene("E06_entry-delete-dialog-dark")

    @Test
    fun e06_editor_rule_help_dialog_light() = captureScene("E06_editor-rule-help-dialog-light")

    @Test
    fun e06_editor_rule_help_dialog_dark() = captureScene("E06_editor-rule-help-dialog-dark")

    @Test
    fun e06_editor_book_popup_light() = captureScene("E06_editor-book-popup-light")

    @Test
    fun e06_editor_book_popup_dark() = captureScene("E06_editor-book-popup-dark")

    @Test
    fun e06_editor_conversation_popup_light() = captureScene("E06_editor-conversation-popup-light")

    @Test
    fun e06_editor_conversation_popup_dark() = captureScene("E06_editor-conversation-popup-dark")

    private fun captureScene(id: String) {
        val case = ScreenshotCases.require(id)
        when {
            id.startsWith("E06_list-") -> listScene(case)
            id.startsWith("E06_detail-") || id.startsWith("E06_book-delete-") -> detailScene(case)
            else -> editorScene(case)
        }
        assertTrue("仅访问页面草稿，不能触发保存/删除/导航", events.isEmpty())
        assertTrue(savedEntries.isEmpty())
    }

    private fun listScene(case: ScreenshotCase) {
        renderList(case, if ("list-empty-" in case.id) emptyList() else Entries)
        when {
            "list-help-dialog" in case.id -> {
                captureClick(pageNode(hasContentDescription("世界书：查看说明")), "help:worldbook")
                scrollHelpToEnd("世界书")
                captureWindow(case, "世界书", listOf("知道了"))
            }

            "list-tail-narrow" in case.id -> {
                entryAtEnd(detail = false)
                capturePage(case, hasText(LastStandaloneTitle) and hasClickAction())
            }

            "list-search-empty" in case.id -> {
                pageNode(hasSetTextAction()).performTextReplacement("不存在的合成岛屿")
                actions += "search:no-match"
                capturePage(case, hasText("没有匹配结果"))
            }

            "list-empty-" in case.id -> capturePage(case, hasText("还没有世界书"))
            else -> {
                if ("filters" in case.id || "dynamic" in case.id || "filter-empty" in case.id) {
                    captureClick(pageNode(hasText("筛选") and hasClickAction()), "filters:expand")
                    if ("dynamic" in case.id || "filter-empty" in case.id) {
                        val scope = if ("filter-empty" in case.id) "会话" else "全局"
                        val row = pageNode(
                            hasScrollToIndexAction() and hasAnyDescendant(hasText("全局")) and
                                    SemanticsMatcher("作用域横向列表") {
                                        it.config.contains(
                                            SemanticsProperties.HorizontalScrollAxisRange
                                        )
                                    })
                        row.performScrollToNode(hasText(scope) and isSelectable())
                        captureClick(pageNode(hasText(scope) and isSelectable()), "filter:$scope")
                    }
                }
                when {
                    "filter-empty" in case.id -> {
                        reveal(hasText("当前筛选下没有条目"))
                        capturePage(case, hasText("当前筛选下没有条目"))
                    }

                    "filters" in case.id -> {
                        reveal(hasText("作用域"))
                        capturePage(case, hasText("作用域"))
                    }

                    "dynamic" in case.id -> capturePage(case, hasText("全局") and isSelectable())
                    else -> capturePage(case, hasText(BookName) and hasClickAction())
                }
            }
        }
    }

    private fun detailScene(case: ScreenshotCase) {
        renderDetail(case)
        when {
            "book-delete" in case.id -> {
                openManagement()
                captureClick(
                    reveal(hasText("删除整本世界书") and hasClickAction()),
                    "delete-book:open"
                )
                scrollHelpToEnd("删除整本世界书")
                captureWindow(case, "删除整本世界书", listOf("确认删除", "取消"))
            }

            "detail-tail" in case.id -> {
                entryAtEnd(detail = true)
                capturePage(case, hasText(LastEntryTitle) and hasClickAction())
            }

            "management" in case.id || "dynamic" in case.id -> {
                openManagement()
                reveal(hasText("删除整本世界书") and hasClickAction())
                recordBounds("rename", pageNode(hasText("保存书名") and hasClickAction()))
                recordBounds("deleteBook", pageNode(hasText("删除整本世界书") and hasClickAction()))
                capturePage(case, hasText("删除整本世界书") and hasClickAction())
            }

            else -> capturePage(case, hasText(BookName))
        }
    }

    private fun editorScene(case: ScreenshotCase) {
        renderEditor(case, entry = if ("new-required" in case.id) null else EditorEntry)
        when {
            "new-required" in case.id -> {
                pageNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
                capturePage(case, hasText("标题和正文为必填项"))
            }

            "editor-existing" in case.id -> capturePage(
                case,
                hasText("内容") and hasSetTextAction()
            )

            "editor-rules" in case.id -> {
                toggleSection("基本信息")
                val keyword = hasText(EditorEntry.keywords[1]) and hasClickAction()
                recordBounds("longKeyword", reveal(keyword))
                requireFullyVisible("longKeyword")
                capturePage(case, keyword)
            }

            "editor-scope" in case.id -> {
                toggleSection("基本信息")
                toggleSection("命中规则")
                toggleSection("作用域")
                reveal(hasText(Assistants.first().name) and isSelectable())
                recordBounds(
                    "assistantChoice",
                    pageNode(hasText(Assistants.first().name) and isSelectable())
                )
                capturePage(case, hasText(Assistants.first().name) and isSelectable())
            }

            "advanced-tail" in case.id -> {
                showAdvancedPreview()
                capturePage(case, hasText("删除条目") and hasClickAction())
            }

            "body-sheet" in case.id -> {
                openBodySheet()
                bodyToEnd()
                recordBounds(
                    "cancelBody",
                    windowNode(dialogMatcher("编辑正文"), hasContentDescription("取消正文编辑")),
                    dialogRoot("编辑正文")
                )
                captureWindow(case, "编辑正文", listOf("保存并返回"))
            }

            "entry-delete" in case.id -> {
                scrollToEnd()
                captureClick(
                    pageNode(hasText("删除条目") and hasClickAction()),
                    "delete-entry:open"
                )
                scrollHelpToEnd("删除世界书条目")
                captureWindow(case, "删除世界书条目", listOf("确认删除", "取消"))
            }

            "rule-help" in case.id -> {
                captureClick(reveal(hasContentDescription("命中规则：查看说明")), "help:rules")
                scrollHelpToEnd("命中规则")
                captureWindow(case, "命中规则", listOf("知道了"))
            }

            "book-popup" in case.id -> {
                openBookPopup()
                popupToEnd(LastBookName)
                captureWindow(case, LastBookName, listOf(LastBookName), popup = true)
            }

            "conversation-popup" in case.id -> {
                openConversationPopup()
                popupToEnd(LastConversationTitle)
                captureWindow(
                    case,
                    LastConversationTitle,
                    listOf(LastConversationTitle),
                    popup = true
                )
            }

            else -> error("未处理的世界书截图：" + case.id)
        }
    }

    private fun scrollHelpToEnd(title: String) {
        val scroll = windowNode(
            dialogMatcher(title),
            hasScrollAction() and verticalScrollMatcher,
            unmerged = true
        )
        scrollToEnd(scroll, "windowBody")
    }
}
