package com.example.myapplication.ui.screen.settings

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import com.example.myapplication.ui.screen.settings.memory.MemoryArchiveScreen
import com.example.myapplication.ui.screen.settings.memory.MemoryManagementScreen
import com.example.myapplication.ui.screenshot.*
import com.example.myapplication.viewmodel.*
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

@Config(qualifiers = ScreenshotFixture.NarrowQualifiers)
class MemoryArchiveUiTest : ScreenshotStageTest() {
    @Test
    fun editor200PercentLight_keepsMultilineDraftAndSaveReachable() = editorProbe(false)
    @Test
    fun editor200PercentDark_keepsMultilineDraftAndSaveReachable() = editorProbe(true)

    private fun editorProbe(dark: Boolean) {
        useNarrowFontScale()
        val editor = mutableStateOf(
            MemoryArchiveEditorUi(
                MemoryMaterialKind.RECORD,
                "00000000-0000-4000-8000-000000000001",
                123456789012345,
                "保留第一段\n\n保留第二段\n\n" + "完整长文内容。".repeat(20),
                error = "内容已有新版本，草稿已保留。"
            )
        )
        var saves = 0
        val callbacks = archiveCallbacksFixture().copy(
            onUpdateEditor = { editor.value = it },
            onSaveEditor = { saves++ })
        setStage(ScreenshotCases.require("E04_reading-narrow-dark"), darkTheme = dark) {
            MemoryArchiveScreen(
                MemoryArchiveUiState("a", isLoading = false, editor = editor.value),
                Assistant(id = "a", name = "合成角色"),
                AppSettings(),
                callbacks,
                {},
                {},
                {},
                {},
                {})
        }
        val save = composeRule.onNodeWithTag("memory_editor_save")
        save.assertIsDisplayed()
        assertTouchTarget(save)
        composeRule.onNodeWithTag("memory_editor_content").performScrollTo()
        composeRule.onNodeWithTag("memory_editor_content")
            .performTextReplacement("修订第一段\n\n修订第二段")
        save.performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves); assertEquals(
            "修订第一段\n\n修订第二段",
            editor.value.content
        )
        }
        probe("editor-${if (dark) "dark" else "light"}", composeRule.onNodeWithTag(StageTag))
    }

    @Test
    fun deleteDialog_cancelHasNoMutationAndConfirmIsExplicit() {
        useNarrowFontScale()
        val record = archiveUiRecord()
        var deletions = 0
        val state = MemoryArchiveUiState(
            "a", isLoading = false, detail = MemoryArchiveDetailUi(
                MemoryArchiveItem.Record(record),
                record.revision,
                record.content,
                listOf(1L, 3L),
                isLoading = false
            )
        )
        val callbacks = archiveCallbacksFixture().copy(onDelete = { deletions++ })
        setStage(ScreenshotCases.require("E04_reading-narrow-dark"), darkTheme = true) {
            MemoryArchiveScreen(
                state,
                Assistant(id = "a"),
                AppSettings(),
                callbacks,
                {},
                {},
                {},
                {},
                {})
        }
        composeRule.onNodeWithTag("memory_detail_list").performScrollToNode(hasText("删除记忆"))
        composeRule.onNodeWithText("删除记忆").performClick()
        composeRule.onNodeWithText("删除这条记忆").assertIsDisplayed()
        probe("delete-dialog-dark", composeRule.onNode(isDialog()))
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deletions) }
        composeRule.onNodeWithText("删除记忆").performClick()
        val confirm = composeRule.onNode(hasText("确认") and hasClickAction())
        assertTouchTarget(confirm)
        confirm.performClick()
        composeRule.runOnIdle { assertEquals(1, deletions) }
    }

    @Test
    fun missingRoundAndHistoricalPersonaControls_useRealCallbacks() {
        useNarrowFontScale()
        var persona: String? = null
        val context = MemoryRuntimeContext("a", "s", "", personaKey = "p")
        val state = mutableStateOf(
            MemoryArchiveUiState(
                "a",
                spaces = listOf(MemorySpace("s", "a", MemoryPurpose.INDEPENDENT)),
                context = context,
                personas = listOf(
                    MemoryArchivePersona("p", "旧身份"),
                    MemoryArchivePersona("knowledge", "仅认知身份")
                ),
                panel = MemoryArchivePanel.RECEIPT,
                isLoading = false
            )
        )
        val callbacks = archiveCallbacksFixture().copy(onSelectPersona = { persona = it })
        setStage(ScreenshotCases.require("E04_reading-narrow-dark")) {
            MemoryArchiveScreen(
                state.value,
                Assistant(id = "a", memoryEnabled = true),
                AppSettings(),
                callbacks,
                {},
                {},
                {},
                {},
                {})
        }
        composeRule.onNodeWithTag("memory_archive_list")
            .performScrollToNode(hasTestTag("memory_persona"))
        composeRule.onNodeWithTag("memory_persona")
            .performScrollToNode(hasTestTag("memory_persona_knowledge"))
        composeRule.onNodeWithTag("memory_persona_knowledge").performClick()
        composeRule.runOnIdle { assertEquals("knowledge", persona) }
        composeRule.onNodeWithTag("memory_archive_list")
            .performScrollToNode(hasTestTag("memory_receipt_missing"))
        composeRule.onNodeWithText("本轮无记录").assertIsDisplayed()
        probe("missing-round-light", composeRule.onNodeWithTag(StageTag))
    }

    @Test
    fun historicalSource200PercentLight_showsCompleteEvidenceAndReadAction() =
        historicalSourceProbe(false)

    @Test
    fun historicalSource200PercentDark_showsCompleteEvidenceAndReadAction() =
        historicalSourceProbe(true)

    private fun historicalSourceProbe(dark: Boolean) {
        useNarrowFontScale()
        val record = archiveUiRecord(revision = 123456789012L)
        val content = "原文尾部证据：星河路182号，约定11月8日。"
        val source = MemorySourceEvidence(
            "historical-source",
            "daily",
            "a",
            MemorySourceKind.MESSAGE,
            "old-conversation",
            "original-message",
            1,
            "a",
            "ASSISTANT",
            "旧身份-旅者",
            listOf("a"),
            content,
            content.length,
            MemorySourceStatus.CHANGED,
            1_700_000_000_000,
            1,
            "synthetic-fingerprint"
        )
        var read: Pair<String, String>? = null
        var selectedRevision: Long? = null
        val detail = MemoryArchiveDetailUi(
            MemoryArchiveItem.Record(record),
            1,
            "原始记录正文",
            listOf(1, record.revision),
            listOf(MemoryArchiveSourceUi(source.fullRef(), source)),
            isLoading = false
        )
        val callbacks = archiveCallbacksFixture().copy(
            onReadOriginal = { conversation, persona -> read = conversation to persona },
            onSelectRevision = { selectedRevision = it })
        setStage(ScreenshotCases.require("E04_reading-narrow-dark"), darkTheme = dark) {
            MemoryArchiveScreen(
                MemoryArchiveUiState("a", detail = detail, isLoading = false),
                Assistant(id = "a"),
                AppSettings(),
                callbacks,
                {},
                {},
                {},
                {},
                {})
        }
        val list = composeRule.onNodeWithTag("memory_detail_list")
        list.performScrollToNode(hasTestTag("memory_revision"))
        composeRule.onNodeWithTag("memory_revision")
            .performScrollToNode(hasTestTag("memory_revision_${record.revision}"))
        val version = composeRule.onNodeWithTag("memory_revision_${record.revision}")
        version.assertIsDisplayed()
        assertTouchTarget(version)
        version.performClick()
        composeRule.runOnIdle { assertEquals(record.revision, selectedRevision) }
        list.performScrollToNode(hasTestTag("memory_source_historical-source"))
        val readAction = composeRule.onNodeWithText("查看只读原文")
        readAction.performScrollTo().assertIsDisplayed()
        assertTouchTarget(readAction)
        assertEntireNodeWithin(composeRule.onNodeWithText(content), list)
        assertEntireNodeWithin(readAction, list)
        composeRule.onNodeWithText("原文已改变（CHANGED）").assertExists()
        probe(
            "history-source-${if (dark) "dark" else "light"}",
            composeRule.onNodeWithTag(StageTag)
        )
        readAction.performClick()
        composeRule.runOnIdle { assertEquals("old-conversation" to "旧身份-旅者", read) }
    }

    @Test
    fun historicalState_doesNotDisplayCurrentExpiryOrEndedStatus() {
        useNarrowFontScale()
        val current = MemoryCurrentState(
            "state", "daily", "a", "p", MemoryStateKey.APPOINTMENT, "新的约定", 2,
            "ENDED", 1_800_000_000_000, 1_810_000_000_000, 1_790_000_000_000, true, 1, 2
        )
        val old = MemoryKnowledgeRevision(
            MemoryMaterialKind.STATE, "state", 1, "旧的约定", "ACTIVE", MemoryChangeOrigin.MANUAL,
            false, 1_700_000_000_000, null, emptyList(), emptyList(), 1
        )
        val detail = MemoryArchiveDetailUi(
            MemoryArchiveItem.State(current), 1, old.content, listOf(1, 2),
            isLoading = false, knowledgeRevision = old
        )
        setStage(ScreenshotCases.require("E04_reading-narrow-dark"), darkTheme = true) {
            MemoryArchiveScreen(
                MemoryArchiveUiState("a", detail = detail, isLoading = false),
                Assistant(id = "a"),
                AppSettings(),
                archiveCallbacksFixture(),
                {},
                {},
                {},
                {},
                {})
        }
        composeRule.onNodeWithTag("memory_detail_list")
            .performScrollToNode(hasText("本版本到期：2023-11-15 06:13"))
        composeRule.onNodeWithText("本版本状态：有效").assertExists()
        composeRule.onNodeWithText("本版本到期：2023-11-15 06:13").assertIsDisplayed()
        composeRule.onNodeWithText("已结束", substring = false).assertDoesNotExist()
        composeRule.onNodeWithText("观察：", substring = true).assertDoesNotExist()
    }

    @Test
    fun legacyDeleteDialog_keepsEntryShownWhenOpened() {
        val original = MemoryEntry(
            id = "legacy", scopeType = MemoryScopeType.ASSISTANT, scopeId = "a", characterId = "a",
            content = "打开确认窗口时看到的旧正文", archiveRevision = 3
        )
        val state = mutableStateOf(MemoryManagementUiState(memories = listOf(original)))
        var deleted: MemoryEntry? = null
        var deletions = 0
        setStage(ScreenshotCases.require("E04_reading-narrow-dark")) {
            MemoryManagementScreen(
                uiState = state.value,
                assistants = listOf(Assistant(id = "a")),
                initialAssistantId = "a",
                memoryAutoSummaryEvery = 0,
                memoryCapacity = 100,
                memoryRecallTokenBudget = 2000,
                memoryExtractionPrompt = "",
                memoryInjectionPrompt = "",
                memoryInjectionPosition = MemoryInjectionPosition.AFTER_WORLD_BOOK,
                onUpdateMemoryAutoSummaryEvery = {},
                onUpdateMemoryCapacity = {},
                onUpdateMemoryRecallTokenBudget = {},
                onUpdateMemoryExtractionPrompt = {},
                onUpdateMemoryInjectionPrompt = {},
                onUpdateMemoryInjectionPosition = {},
                onTogglePinned = { error("此操作需要所见版本") },
                onDeleteMemory = { error("此操作需要所见版本") },
                onDeleteSummary = {},
                onConsumeMessage = {},
                onNavigateBack = {},
                onDeleteMemoryEntry = { deleted = it; deletions++ })
        }
        composeRule.onNodeWithTag("memory_management_list")
            .performScrollToNode(hasText(original.content))
        composeRule.onNodeWithContentDescription("删除").performScrollTo().performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deletions) }
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                memories = listOf(
                    original.copy(
                        content = "后台更新的新正文",
                        archiveRevision = 4
                    )
                )
            )
        }
        composeRule.onNode(hasText("删除", substring = false) and hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(1, deletions); assertEquals(original, deleted) }
    }

    private fun assertEntireNodeWithin(
        node: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction
    ) {
        val bounds = node.getUnclippedBoundsInRoot()
        val visible = viewport.getUnclippedBoundsInRoot()
        assertTrue(
            "内容需完整位于视口：$bounds / $visible",
            bounds.left >= visible.left && bounds.right <= visible.right &&
                    bounds.top >= visible.top && bounds.bottom <= visible.bottom
        )
    }

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("应满足 48dp: $bounds", bounds.width >= minimum && bounds.height >= minimum)
    }

    private fun probe(name: String, root: SemanticsNodeInteraction) {
        val bitmap = root.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("原生画面为空")
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val directory = File("build/outputs/c6-ui-probes").apply { mkdirs() }
        File(directory, "$name.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(directory, "$name-semantics.txt").writeText(root.printToString())
    }
}
