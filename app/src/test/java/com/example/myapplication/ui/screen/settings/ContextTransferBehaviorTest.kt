package com.example.myapplication.ui.screen.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.viewmodel.ContextTransferSection
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/** 全部操作走真实触控、真实 launcher 回调和内存 URI，不创建用户 AppGraph。 */
class ContextTransferBehaviorTest : ContextTransferRenderTest() {
    @Test
    fun allFiveExports_keepFilenamesAndSections_andOnlyWriteAfterResult() {
        renderTransfer(ScreenshotCases.require("E06_transfer-first-narrow-light"))
        assertTrue(registry.launches.isEmpty())
        sections.forEachIndexed { index, (title, section) ->
            revealSection(title)
            val export = sectionButton(title, "导出")
            requireTarget("$title-export", export)
            requireTarget("$title-import", sectionButton(title, "导入"))
            export.performTouchInput { click() }
            assertEquals(index + 1, registry.launches.size)
            val launch = registry.launches.last()
            assertTrue(launch.contract is ActivityResultContracts.CreateDocument)
            assertEquals(section.exportFileName, launch.input)
            assertEquals(index, events.size)
            assertEquals(index, outputOpens.get())
            dispatch(registerOutput("${section.name}.json"))
            composeRule.waitUntil(5_000) { outputCloses.get() == index + 1 }
            assertEquals("export:${section.name}", events.last())
        }
        assertEquals(ExportJson.repeat(5), exported.toString(Charsets.UTF_8.name()))
        assertEquals(0, inputOpens.get())
        assertNoExternalActivity()
    }

    @Test
    fun allFiveImports_keepSectionAndSourceContracts_andCloseDialogBeforeResult() {
        renderTransfer(ScreenshotCases.require("E06_transfer-first-narrow-light"))
        val textUri = registerInput("local.json", "\uFEFF$ExportJson".toByteArray(), "text/plain")
        sections.forEachIndexed { index, (title, section) ->
            revealSection(title)
            sectionButton(title, "导入").performTouchInput { click() }
            if (section in sourceSections) {
                dialogRoot(SourceTitle).assertIsDisplayed()
                assertEquals(index, registry.launches.size)
                val file = windowNode(
                    dialogMatcher(SourceTitle),
                    hasText("从文件（JSON）") and hasClickAction()
                )
                requireTarget("source-file", file, dialogRoot(SourceTitle))
                file.performTouchInput { click() }
                assertThrows(AssertionError::class.java) { dialogRoot(SourceTitle) }
            }
            val launch = registry.launches.last()
            assertTrue(launch.contract is ActivityResultContracts.OpenDocument)
            assertArrayEquals(arrayOf("application/json", "text/plain"), launch.input as Array<*>)
            assertEquals(index, inputOpens.get())
            dispatch(textUri)
            composeRule.waitUntil(5_000) { payloads.size == index + 1 }
            assertEquals(section, payloads.last().first)
            assertEquals(ExportJson, payloads.last().second.textContent)
            assertNull(payloads.last().second.binaryContent)
        }
        assertEquals(5, inputCloses.get())
        assertEquals(0, outputOpens.get())
        assertTrue(events.isEmpty())
        assertNoExternalActivity()
    }

    @Test
    fun threePhotoSources_keepImageOnlyAndPendingSection_andNullLeavesExistingPreview() {
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"), preview = true)
        val imageBytes = byteArrayOf(0x50, 0x4e, 0x47)
        val imageUri = registerInput("constructed.png", imageBytes, "image/png")
        sections.filter { it.second in sourceSections }.forEachIndexed { index, (title, section) ->
            revealSection(title)
            sectionButton(title, "导入").performTouchInput { click() }
            windowNode(dialogMatcher(SourceTitle), hasText("从相册（PNG）") and hasClickAction())
                .performTouchInput { click() }
            val launch = registry.launches.last()
            assertTrue(launch.contract is ActivityResultContracts.PickVisualMedia)
            assertEquals(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
                (launch.input as PickVisualMediaRequest).mediaType
            )
            assertThrows(AssertionError::class.java) { dialogRoot(SourceTitle) }
            dispatch(null)
            assertEquals(index, payloads.size)
            assertEquals(Preview, state.value.importPreview)
            sectionButton(title, "导入").performTouchInput { click() }
            windowNode(
                dialogMatcher(SourceTitle),
                hasText("从相册（PNG）") and hasClickAction()
            ).performTouchInput { click() }
            dispatch(imageUri)
            composeRule.waitUntil(5_000) { payloads.size == index + 1 }
            assertEquals(section, payloads.last().first)
            assertArrayEquals(imageBytes, payloads.last().second.binaryContent)
            assertNull(payloads.last().second.textContent)
        }
        assertEquals(3, inputOpens.get())
        assertEquals(3, inputCloses.get())
        assertTrue(events.isEmpty())
        assertNoExternalActivity()
    }

    @Test
    fun sixHelpEntrances_andSourceCancel_areIndependent48dpActionsAtTwoHundredPercent() {
        renderTransfer(ScreenshotCases.require("E06_transfer-first-narrow-light"), preview = true)
        (listOf(IntroTitle) + sections.map { it.first }).forEach { title ->
            val help = reveal(hasContentDescription("$title：查看说明"))
            requireTarget("help-$title", help)
            help.performTouchInput { click() }
            val root = dialogRoot(title)
            val close = windowNode(dialogMatcher(title), hasText("知道了") and hasClickAction())
            requireTarget("help-close", close, root)
            val titleNode = windowNode(dialogMatcher(title), hasText(title), unmerged = true)
            assertEquals(2f, textLayout(titleNode).layoutInput.density.fontScale)
            close.performTouchInput { click() }
            assertThrows(AssertionError::class.java) { dialogRoot(title) }
        }
        sections.filter { it.second in sourceSections }.forEach { (title, _) ->
            revealSection(title)
            sectionButton(title, "导入").performTouchInput { click() }
            listOf("从相册（PNG）", "从文件（JSON）", "取消").forEach { label ->
                requireTarget(
                    label,
                    windowNode(dialogMatcher(SourceTitle), hasText(label) and hasClickAction()),
                    dialogRoot(SourceTitle)
                )
            }
            windowNode(
                dialogMatcher(SourceTitle),
                hasText("取消") and hasClickAction()
            ).performTouchInput { click() }
        }
        assertEquals(Preview, state.value.importPreview)
        assertCaptureIsolation()
    }

    @Test
    fun preview_showsPositiveCountsAllNoticesAndOnlyFirstFiveConflicts_thenCancelOnlyDismisses() {
        state.value = OverviewState.copy(
            importPreview = Preview.copy(
                assistantCount = 0,
                memoryCount = 0,
                presetCount = 0
            )
        )
        renderTransfer(ScreenshotCases.require("E06_transfer-preview-actions-narrow-dark"))
        reveal(hasText("来源：上下文数据包"))
        pageNode(hasText("角色卡")).assertDoesNotExist()
        pageNode(hasText("记忆")).assertDoesNotExist()
        pageNode(hasText("预设")).assertDoesNotExist()
        val notice = reveal(hasText(Preview.noticeMessages.joinToString("\n") { "• $it" }))
        notice.assert(hasAnyAncestor(hasClickAction()).not())
        scrollToEnd()
        pageNode(hasText("检测到 7 条冲突")).assertExists()
        pageNode(
            hasText(
                Preview.conflicts.take(5).joinToString("\n") { "• ${it.typeLabel} / ${it.title}" })
        ).assertExists()
        pageNode(hasText(Preview.conflicts[5].title, substring = true)).assertDoesNotExist()
        val confirm = pageNode(hasText("确认导入") and hasClickAction())
        val cancel = pageNode(hasText("取消") and hasClickAction())
        requireTarget("preview-confirm", confirm)
        requireTarget("preview-cancel", cancel)
        confirm.performTouchInput { click() }
        assertEquals(listOf("confirm"), events)
        assertNotNull(state.value.importPreview)
        cancel.performTouchInput { click() }
        assertEquals(listOf("confirm", "dismiss-preview"), events)
        assertNull(state.value.importPreview)
        assertEquals(0, inputOpens.get())
        assertEquals(0, outputOpens.get())
        assertTrue(registry.launches.isEmpty())
    }

    @Test
    fun busy_disablesAllTransferAndPreviewActions_withoutBlockingReturnOrChangingPreview() {
        state.value = OverviewState.copy(isBusy = true, importPreview = Preview)
        renderTransfer(ScreenshotCases.require("E06_transfer-last-narrow-dark"))
        sections.forEach { (title, _) ->
            pageList().performScrollToNode(hasContentDescription("$title：查看说明"))
            val controls = composeRule.onAllNodes(
                hasText("处理中…") and hasClickAction() and hasAnyAncestor(groupMatcher(title))
            )
            controls.assertCountEquals(2)
            repeat(2) { index ->
                val button = controls[index].performScrollTo().assertIsNotEnabled()
                requireTarget("busy-$title-$index", button)
                button.performTouchInput { click() }
            }
        }
        scrollToEnd()
        pageNode(hasText("处理中…") and hasClickAction() and hasAnyAncestor(previewCardMatcher()))
            .assertIsNotEnabled().performTouchInput { click() }
        pageNode(hasText("取消") and hasClickAction()).assertIsNotEnabled()
            .performTouchInput { click() }
        assertTrue(events.isEmpty())
        assertTrue(registry.launches.isEmpty())
        assertEquals(Preview, state.value.importPreview)
        pageNode(hasContentDescription("返回")).performTouchInput { click() }
        assertEquals(listOf("back"), events)
        assertEquals(Preview, state.value.importPreview)
    }

    @Test
    fun readOnlyOverview_keepsAllZeroCountsWithoutClickableAncestors() {
        state.value = OverviewState.copy(
            customAssistantCount = 0,
            worldBookCount = 0,
            memoryCount = 0,
            summaryCount = 0,
            presetCount = 0
        )
        renderTransfer(ScreenshotCases.require("E06_transfer-first-narrow-light"))
        listOf("角色包", "世界书条目", "记忆档案", "对话摘要", "自定义预设").forEach { title ->
            val node = reveal(hasText(title) and hasClickAction().not())
            node.assertHasNoClickAction().assert(hasAnyAncestor(hasClickAction()).not())
        }
        pageList().performScrollToIndex(2)
        composeRule.onAllNodesWithText("当前共有 0 条", useUnmergedTree = true).assertCountEquals(5)
        assertCaptureIsolation()
    }

    @Test
    fun readPermissionFailure_isRedacted_andDoesNotPreviewOrConsumeVmMessage() {
        val uri = registerInput("denied.json", byteArrayOf(), "application/json")
        shadowOf(composeRule.activity.contentResolver).registerInputStreamSupplier(uri) {
            throw SecurityException("Authorization: Bearer synthetic-secret /data/user/0/fake/denied.json")
        }
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"), preview = true)
        revealSection("记忆档案包")
        sectionButton("记忆档案包", "导入").performTouchInput { click() }
        dispatch(uri)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("导入失败").fetchSemanticsNodes().isNotEmpty()
        }
        pageNode(hasText("导入失败")).assertIsDisplayed()
        pageNode(hasText("synthetic-secret", substring = true)).assertDoesNotExist()
        assertTrue(payloads.isEmpty())
        assertTrue(events.isEmpty())
        assertEquals(Preview, state.value.importPreview)
        assertEquals(0, outputOpens.get())
        assertNoExternalActivity()
    }

    @Test
    fun writePermissionFailure_isRedacted_withoutImportOrVmMessageSideEffects() {
        val uri = registerOutput("denied-export.json")
        shadowOf(composeRule.activity.contentResolver).registerOutputStreamSupplier(uri) {
            throw SecurityException("/data/user/0/fake/denied-export.json")
        }
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"))
        revealSection("世界书")
        sectionButton("世界书", "导出").performTouchInput { click() }
        dispatch(uri)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("导出失败").fetchSemanticsNodes().isNotEmpty()
        }
        pageNode(hasText("导出失败")).assertIsDisplayed()
        assertEquals(listOf("export:WORLD_BOOK"), events)
        assertEquals(0, outputOpens.get())
        assertTrue(payloads.isEmpty())
        assertNoExternalActivity()
    }

    @Test
    fun vmNotice_isShownAndConsumedOnce_withoutFileOrPreviewSideEffects() {
        state.value = OverviewState.copy(message = "合成资料库提示", importPreview = Preview)
        renderTransfer(ScreenshotCases.require("E06_transfer-matcha-light"))
        pageNode(hasText("合成资料库提示")).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(3_000)
        drainMainLooper()
        composeRule.waitForIdle()
        assertEquals(listOf("consume"), events)
        assertNull(state.value.message)
        assertEquals(Preview, state.value.importPreview)
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        assertEquals(listOf("consume"), events)
        assertTrue(registry.launches.isEmpty())
        assertEquals(0, inputOpens.get())
        assertEquals(0, outputOpens.get())
    }

    private fun requireTarget(
        name: String,
        node: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction = pageList()
    ) {
        recordBounds(name, node, viewport)
        requireFullyVisible(name)
        assertEquals("实际触控宽高都至少48dp：$name", "true", measurements["${name}Touch48dp"])
    }

    private fun previewCardMatcher() = hasAnyDescendant(hasText("来源：上下文数据包")) and
            hasScrollToIndexAction().not() and hasAnyDescendant(hasScrollToIndexAction()).not() and
            hasTestTag(StageTag).not() and isRoot().not()

    companion object {
        private val sections = listOf(
            "资料库备份" to ContextTransferSection.ALL,
            "角色包" to ContextTransferSection.ASSISTANTS,
            "世界书" to ContextTransferSection.WORLD_BOOK,
            "记忆档案包" to ContextTransferSection.MEMORY,
            "预设包" to ContextTransferSection.PRESETS,
        )
        private val sourceSections = setOf(
            ContextTransferSection.ALL,
            ContextTransferSection.ASSISTANTS,
            ContextTransferSection.WORLD_BOOK
        )
    }
}
