package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.*
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CompanionHistoryUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionHistoryScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun multiEditAndDelete_requireExplicitConfirmAndKeepBackLayers() {
        val fixture = HistoryUiFixture()
        compose.setContent {
            ChatAppTheme {
                CompanionHistoryScreen(
                    fixture.state.value,
                    "合成角色",
                    fixture.callbacks
                )
            }
        }
        reveal("companion_history_locate_one")
        compose.onNodeWithTag("companion_history_locate_one").assertIsNotEnabled()
        compose.onNodeWithText("时间未知").assertIsDisplayed()
        reveal("companion_history_edit_selected")
        compose.onNodeWithTag("companion_history_edit_selected").performClick()
        compose.onNodeWithTag("companion_history_edit_one").performClick()
            .performTextReplacement("<dialogue>第一条人工正文 &amp; 👩‍💻</dialogue>")
        assertCompanionDialogActionAboveIme(compose, compose.activity, "保存编辑")
        Espresso.pressBack()
        compose.runOnIdle {
            assertEquals(2, fixture.state.value.editingDrafts.size); assertTrue(
            fixture.saved.isEmpty()
        )
        }
        compose.onNodeWithText("保存编辑").performClick()
        compose.runOnIdle {
            assertEquals(setOf("one", "two"), fixture.saved.keys)
            assertEquals("<dialogue>第一条人工正文 &amp; 👩‍💻</dialogue>", fixture.saved["one"])
            assertTrue(fixture.state.value.editingDrafts.isEmpty())
        }
        reveal("companion_history_edit_selected")
        compose.onNodeWithTag("companion_history_edit_selected").performClick()
        Espresso.pressBack()
        compose.runOnIdle {
            assertTrue(fixture.state.value.editingDrafts.isEmpty()); assertEquals(
            0,
            fixture.left
        )
        }
        reveal("companion_history_delete_selected")
        compose.onNodeWithTag("companion_history_delete_selected").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertTrue(fixture.deleted.isEmpty()); assertEquals(
            3,
            fixture.state.value.records.size
        )
        }
        compose.onNodeWithTag("companion_history_delete_selected").performClick()
        compose.onNodeWithText("删除原文").performClick()
        compose.runOnIdle {
            assertEquals(setOf("one", "two"), fixture.deleted); assertEquals(
            1,
            fixture.state.value.records.size
        )
        }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, fixture.left) }
    }

    @Test
    fun readOnlyArchive_at320dpAnd200PercentDisablesWritesAndClearsSelectionBeforeExit() {
        val fixture = HistoryUiFixture(readOnly = true)
        compose.setContent {
            val baseDensity = LocalDensity.current
            ChatAppTheme {
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 2f)) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        CompanionHistoryScreen(fixture.state.value, "合成角色", fixture.callbacks)
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("companion_history_count").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0f)
        val density = compose.activity.resources.displayMetrics.density
        val width =
            compose.onNodeWithTag("companion_history").fetchSemanticsNode().boundsInRoot.width
        assertEquals(320f * density, width, 0.5f)
        reveal("companion_history_edit_selected")
        compose.onNodeWithTag("companion_history_edit_selected").assertIsNotEnabled()
        compose.onNodeWithTag("companion_history_delete_selected").assertIsNotEnabled()
        Espresso.pressBack()
        compose.runOnIdle {
            assertTrue(fixture.state.value.selectedIds.isEmpty())
            assertEquals(0, fixture.left)
            assertTrue(fixture.saved.isEmpty()); assertTrue(fixture.deleted.isEmpty())
        }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, fixture.left) }
    }

    @Test
    fun mixedReadOnlyBinding_disablesBatchWritesAndKeepsPendingOriginalEditable() {
        val fixture = HistoryUiFixture(readOnlyMessageIds = setOf("two"))
        compose.setContent {
            ChatAppTheme {
                CompanionHistoryScreen(
                    fixture.state.value,
                    "合成角色",
                    fixture.callbacks
                )
            }
        }
        reveal("companion_history_edit_selected")
        compose.onNodeWithTag("companion_history_edit_selected").assertIsNotEnabled()
        compose.onNodeWithTag("companion_history_delete_selected").assertIsNotEnabled()
        reveal("companion_history_edit_one_two")
        compose.onNodeWithTag("companion_history_edit_one_two").assertIsNotEnabled()
        reveal("companion_history_locate_one")
        compose.onNodeWithTag("companion_history_locate_one").assertIsNotEnabled()
        compose.onNodeWithTag("companion_history_edit_one_one").performClick()
        compose.onNodeWithTag("companion_history_edit_one").assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle {
            assertTrue(fixture.saved.isEmpty()); assertTrue(fixture.deleted.isEmpty()); assertEquals(
            0,
            fixture.left
        )
        }
    }

    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun editorAt320dpAnd200Percent_usesRealDialogFontAndKeepsImeSaveAndBackLayers() {
        val longOriginal =
            "<dialogue>合成原文保留标签、&amp;、连续空白  与 👩‍💻。</dialogue>".repeat(30)
        val lastOriginal = "最后原文。"
        val fixture =
            HistoryUiFixture(contents = mapOf("one" to longOriginal, "two" to lastOriginal))
        withNarrowCompanionWindow(
            compose,
            compose.activity,
            content = {
                ChatAppTheme {
                    CompanionHistoryScreen(
                        fixture.state.value,
                        "合成角色",
                        fixture.callbacks
                    )
                }
            },
        ) {
            reveal("companion_history_edit_selected")
            compose.onNodeWithTag("companion_history_edit_selected").performClick()
            val host = compose.onNodeWithTag("companion_history").fetchSemanticsNode()
            val editor = compose.onNodeWithTag("companion_history_editor").fetchSemanticsNode()
            val hostOwner = host.root as? ViewRootForTest ?: error("宿主必须拥有真实 Android View")
            val editorOwner =
                editor.root as? ViewRootForTest ?: error("编辑器必须拥有真实 Android View")
            assertFalse(hostOwner.view === editorOwner.view)
            assertEquals(2f, hostOwner.view.resources.configuration.fontScale, 0.001f)
            assertEquals(2f, editorOwner.view.resources.configuration.fontScale, 0.001f)
            assertEquals(320, editorOwner.view.resources.configuration.screenWidthDp)
            assertEquals(2f, editor.layoutInfo.density.fontScale, 0.001f)
            assertEquals(320f, host.boundsInRoot.width / host.layoutInfo.density.density, 1f)
            assertTrue(editorOwner.view.width / editor.layoutInfo.density.density <= 321f)
            assertEquals(
                longOriginal,
                compose.onNodeWithTag("companion_history_edit_one")
                    .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            )
            val lastField = compose.onNodeWithTag("companion_history_edit_two")
            val layouts = mutableListOf<TextLayoutResult>()
            lastField.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(
                    it(
                        layouts
                    )
                )
            }
            val layout = layouts.single { it.layoutInput.text.text == lastOriginal }
            assertEquals(2f, layout.layoutInput.density.fontScale, 0.001f)
            assertFalse(layout.hasVisualOverflow)
            val content = compose.onNodeWithTag("narra_alert_dialog_content")
            val range =
                content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("完整长原文必须产生真实滚动范围", range.maxValue() > 0f)
            content.performSemanticsAction(SemanticsActions.ScrollBy) {
                assertTrue(
                    it(
                        0f,
                        100_000f
                    )
                )
            }
            compose.waitForIdle()
            assertEquals(range.maxValue(), range.value(), 1f)
            lastField.assertIsDisplayed()
            val viewport = content.getUnclippedBoundsInRoot()
            val fieldBounds = lastField.getUnclippedBoundsInRoot()
            assertTrue("最后字段顶部必须完整可见", fieldBounds.top >= viewport.top - 1.dp)
            assertTrue("最后字段底部必须完整可见", fieldBounds.bottom <= viewport.bottom + 1.dp)
            lastField.performClick()
            assertCompanionDialogActionAboveIme(compose, compose.activity, "保存编辑")
            Espresso.pressBack()
            compose.runOnIdle {
                assertEquals(longOriginal, fixture.state.value.editingDrafts["one"])
                assertEquals(lastOriginal, fixture.state.value.editingDrafts["two"])
                assertEquals(0, fixture.left)
            }
            compose.onNodeWithTag("companion_history_editor").assertIsDisplayed()
            Espresso.pressBack()
            compose.onNodeWithTag("companion_history_editor").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(
                    setOf("one", "two"),
                    fixture.state.value.selectedIds
                ); assertEquals(0, fixture.left)
            }
            Espresso.pressBack()
            compose.runOnIdle {
                assertTrue(fixture.state.value.selectedIds.isEmpty()); assertEquals(
                0,
                fixture.left
            )
            }
            Espresso.pressBack()
            compose.runOnIdle {
                assertEquals(
                    1,
                    fixture.left
                ); assertTrue(fixture.saved.isEmpty()); assertTrue(fixture.deleted.isEmpty())
            }
        }
    }

    private fun reveal(tag: String) {
        compose.onNodeWithTag("companion_history_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private class HistoryUiFixture(
        readOnly: Boolean = false,
        contents: Map<String, String> = emptyMap(),
        readOnlyMessageIds: Set<String> = emptySet(),
    ) {
        val context =
            MemoryRuntimeContext("alice", "space", "conversation", "scene", "run", "persona")
        val binding = CompanionReadingBinding("conversation", "scene", "run", "合成旧记录", "alice")
        val records = listOf("one", "two", "three").mapIndexed { index, id ->
            CompanionReadingRecord(
                ChatMessage(
                    id,
                    "conversation",
                    if (id == "one") MessageRole.USER else MessageRole.ASSISTANT,
                    contents[id] ?: "<dialogue>合成原文 $id</dialogue>",
                    createdAt = if (id == "one") 0L else 1_000L + index
                ),
                binding.copy(readOnly = id in readOnlyMessageIds), readerEligible = id != "one",
            )
        }
        val state = mutableStateOf(
            CompanionHistoryUiState(
                scope = CompanionReadingScope(context, listOf(binding), emptySet(), readOnly),
                records = records,
                totalCount = records.size,
                isLoading = false,
                selectedIds = setOf("one", "two"),
            )
        )
        var saved = emptyMap<String, String>()
        var deleted = emptySet<String>()
        var left = 0
        val callbacks = CompanionHistoryCallbacks(
            onSetQuery = { state.value = state.value.copy(query = it) },
            onSelectArchive = {}, onLoadOlder = {},
            onToggleSelection = { id ->
                state.value =
                    state.value.copy(selectedIds = state.value.selectedIds.let { if (id in it) it - id else it + id })
            },
            onClearSelection = { state.value = state.value.copy(selectedIds = emptySet()) },
            onBeginEdit = { ids ->
                state.value =
                    state.value.copy(editingDrafts = records.filter { it.message.id in ids }
                        .associate { it.message.id to it.message.content })
            },
            onUpdateDraft = { id, value ->
                state.value =
                    state.value.copy(editingDrafts = state.value.editingDrafts + (id to value))
            },
            onCancelEdit = { state.value = state.value.copy(editingDrafts = emptyMap()) },
            onSaveEdits = {
                saved = state.value.editingDrafts; state.value =
                state.value.copy(editingDrafts = emptyMap())
            },
            onDeleteSelected = {
                deleted = state.value.selectedIds; state.value = state.value.copy(
                records = state.value.records.filterNot { it.message.id in deleted },
                selectedIds = emptySet()
            )
            },
            onRequestExport = {}, onRefresh = {}, onDismiss = { left++ }, onReadMessage = {},
            onOpenReader = {}, onOpenVoice = {}, onOpenAppearance = {},
        )
    }
}
