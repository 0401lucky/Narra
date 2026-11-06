package com.example.myapplication.ui.screenshot

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.core.app.ActivityOptionsCompat
import com.example.myapplication.ui.screen.settings.ContextTransferScreen
import com.example.myapplication.viewmodel.ContextImportConflict
import com.example.myapplication.viewmodel.ContextImportPayload
import com.example.myapplication.viewmodel.ContextImportPreview
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.viewmodel.ContextTransferUiState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

/** 真实 launcher 与私有 URI helper 使用内存 provider；从不启动 chooser、AppGraph 或用户库。 */
abstract class ContextTransferRenderTest : P06RenderTest() {
    protected val state = mutableStateOf(OverviewState)
    protected val registry = RecordingTransferRegistry()
    protected val provider = MemoryTransferProvider()
    protected val events = mutableListOf<String>()
    protected val payloads = mutableListOf<Pair<ContextTransferSection, ContextImportPayload>>()
    protected val inputOpens = AtomicInteger()
    protected val inputCloses = AtomicInteger()
    protected val outputOpens = AtomicInteger()
    protected val outputCloses = AtomicInteger()
    protected val exported = ByteArrayOutputStream()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    @Before
    fun configureMemoryProvider() {
        provider.attachInfo(
            RuntimeEnvironment.getApplication(),
            ProviderInfo().apply { authority = MemoryAuthority })
        ShadowContentResolver.registerProviderInternal(MemoryAuthority, provider)
    }

    protected fun renderTransfer(case: ScreenshotCase, preview: Boolean = false) {
        if (preview) state.value = state.value.copy(importPreview = Preview)
        renderP06(case) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                ContextTransferScreen(
                    uiState = state.value,
                    onExportJson = { section, success ->
                        events += "export:${section.name}"
                        success(ExportJson, section.exportFileName)
                    },
                    onPreviewImportPayload = { payload, section -> payloads += section to payload },
                    onConfirmImport = { events += "confirm" },
                    onDismissImportPreview = {
                        events += "dismiss-preview"; state.value =
                        state.value.copy(importPreview = null)
                    },
                    onConsumeMessage = {
                        events += "consume"; state.value = state.value.copy(message = null)
                    },
                    onNavigateBack = { events += "back" },
                )
            }
        }
    }

    protected fun groupMatcher(title: String): SemanticsMatcher =
        hasAnyDescendant(hasContentDescription("$title：查看说明")) and
                !hasScrollToIndexAction() and !hasAnyDescendant(hasScrollToIndexAction()) and
                !hasTestTag(StageTag) and !isRoot()

    protected fun sectionButton(title: String, label: String): SemanticsNodeInteraction =
        pageNode(hasText(label) and hasClickAction() and hasAnyAncestor(groupMatcher(title)))

    protected fun revealSection(title: String) {
        pageList().performScrollToNode(hasContentDescription("$title：查看说明"))
        sectionButton(title, "导入").performScrollTo().assertIsDisplayed()
        actions += "section:$title"
        measurements["section"] = title
    }

    protected fun openSource(title: String = "资料库备份") {
        revealSection(title)
        captureClick(sectionButton(title, "导入"), "import:$title")
        dialogRoot(SourceTitle).assertIsDisplayed()
    }

    protected fun registerInput(name: String, bytes: ByteArray, mime: String): Uri {
        val uri = Uri.parse("content://$MemoryAuthority/$name")
        provider.documents[uri] = name to mime
        shadowOf(composeRule.activity.contentResolver).registerInputStreamSupplier(uri) {
            inputOpens.incrementAndGet()
            object : ByteArrayInputStream(bytes) {
                override fun close() {
                    super.close()
                    inputCloses.incrementAndGet()
                }
            }
        }
        return uri
    }

    protected fun registerOutput(name: String): Uri {
        val uri = Uri.parse("content://$MemoryAuthority/$name")
        provider.documents[uri] = name to "application/json"
        shadowOf(composeRule.activity.contentResolver).registerOutputStreamSupplier(uri) {
            outputOpens.incrementAndGet()
            object : ByteArrayOutputStream() {
                override fun close() {
                    exported.write(toByteArray())
                    super.close()
                    outputCloses.incrementAndGet()
                }
            }
        }
        return uri
    }

    protected fun dispatch(uri: Uri?) {
        composeRule.runOnIdle { registry.dispatchResult(registry.launches.last().requestCode, uri) }
        composeRule.waitForIdle()
    }

    protected fun assertNoExternalActivity() {
        assertNull(
            "测试 registry 不得启动真实 Activity",
            shadowOf(composeRule.activity).nextStartedActivity
        )
    }

    override fun assertCaptureIsolation() {
        assertNoExternalActivity()
        assertEquals(0, registry.launches.size)
        assertEquals(0, inputOpens.get())
        assertEquals(0, outputOpens.get())
        assertEquals(0, provider.queries.get())
        assertEquals(0, provider.typeQueries.get())
        assertEquals(emptyList<String>(), events)
        assertEquals(emptyList<Pair<ContextTransferSection, ContextImportPayload>>(), payloads)
        measurements["isolation"] =
            "Application; local UiState; intercepted registry; 0 launches; 0 reads; 0 writes; no AppGraph/API"
    }

    protected fun recordSection(title: String) {
        recordBounds("${title}Export", sectionButton(title, "导出"))
        recordBounds("${title}Import", sectionButton(title, "导入"))
        measurements["section"] = title
    }

    /** 只用于三个预览候选；按真实节点位置滚动，不改变其他场景的捕获路径。 */
    protected fun alignPreviewForCapture(noticeOnly: Boolean) {
        pageList().performScrollToNode(hasText("安全上限提示"))
        val anchor =
            previewNode(hasText(if (noticeOnly) "安全上限提示" else "来源：${Preview.sourceLabel}"))
                .onParent()
        anchor.assert(hasAnyDescendant(hasText(if (noticeOnly) noticeText() else "范围：${Preview.section.label}")))
        val list = pageList()
        repeat(3) {
            val viewport = list.fetchSemanticsNode().boundsInRoot
            val top = anchor.getUnclippedBoundsInRoot().top.value * ScreenshotFixture.HostDensity
            val delta = top - viewport.top
            if (kotlin.math.abs(delta) > 0.5f) {
                list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    scroll(
                        0f,
                        delta
                    )
                }
                composeRule.waitForIdle()
            }
        }
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val anchorTop = anchor.getUnclippedBoundsInRoot().top.value * ScreenshotFixture.HostDensity
        assertEquals("预览锚点应对齐实际列表视口", viewport.top, anchorTop, 1f)
        val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        measurements["previewPageScroll"] = "${range.value()}/${range.maxValue()}"
        measurements["previewViewport"] = viewport.toString()
        recordBounds("previewAnchor", anchor)
        measurements["previewAnchorKind"] =
            if (noticeOnly) "notice-card" else "source-and-section-row"
        actions += "align-preview:${measurements["previewAnchorKind"]}"
        requirePreviewVisible(noticeOnly)
    }

    protected fun requirePreviewVisible(noticeOnly: Boolean) {
        val noticeTitle = previewNode(hasText("安全上限提示"))
        val noticeBody = previewNode(hasText(noticeText()))
        requireVisibleText("previewNoticeTitle", noticeTitle)
        requireVisibleText("previewNoticeBody", noticeBody)
        if (!noticeOnly) {
            requireVisibleText("previewSource", previewNode(hasText("来源：${Preview.sourceLabel}")))
            requireVisibleText(
                "previewSection",
                previewNode(hasText("范围：${Preview.section.label}"))
            )
            listOf(
                "角色卡" to "2 条",
                "世界书" to "6 条",
                "记忆" to "3 条",
                "摘要" to "2 条",
                "预设" to "1 条"
            )
                .forEachIndexed { index, (title, count) ->
                    val titleNode = previewNode(hasText(title))
                    val row = titleNode.onParent()
                    recordBounds("previewCountRow$index", row)
                    requireFullyVisible("previewCountRow$index")
                    requireVisibleText("previewCountTitle$index", titleNode)
                    val countNodes = row.onChildren().filter(hasText(count))
                    countNodes.assertCountEquals(1)
                    requireVisibleText("previewCountValue$index", countNodes[0])
                }
            requireVisibleText(
                "previewConflictTitle",
                previewNode(hasText("检测到 ${Preview.conflicts.size} 条冲突"))
            )
            val conflictText =
                Preview.conflicts.take(5).joinToString("\n") { "• ${it.typeLabel} / ${it.title}" }
            val node = previewNode(hasText(conflictText))
            val layout = textLayout(node)
            val firstConflict = conflictText.substringBefore('\n')
            assertEquals(conflictText, layout.layoutInput.text.text)
            val lastLine = layout.getLineForOffset(firstConflict.length - 1)
            val dp = node.getUnclippedBoundsInRoot()
            val originX = dp.left.value * ScreenshotFixture.HostDensity
            val originY = dp.top.value * ScreenshotFixture.HostDensity
            val lines = (0..lastLine).map { line ->
                assertTrue("第一条冲突不可省略", !layout.isLineEllipsized(line))
                Rect(
                    originX + layout.getLineLeft(line), originY + layout.getLineTop(line),
                    originX + layout.getLineRight(line), originY + layout.getLineBottom(line)
                )
            }
            val viewport = pageList().fetchSemanticsNode().boundsInRoot
            assertTrue(
                "第一条冲突的真实文本行必须全部可见：$lines / $viewport",
                lines.all { bounds ->
                    bounds.left >= viewport.left - 0.5f && bounds.top >= viewport.top - 0.5f &&
                            bounds.right <= viewport.right + 0.5f && bounds.bottom <= viewport.bottom + 0.5f
                })
            measurements["previewFirstConflictText"] =
                layout.layoutInput.text.text.substringBefore('\n')
            measurements["previewFirstConflictLines"] = lines.joinToString(" / ")
            measurements["previewFirstConflictFullyVisible"] = "true"
        }
    }

    private fun requireVisibleText(name: String, node: SemanticsNodeInteraction) {
        recordBounds(name, node)
        requireFullyVisible(name)
        val layout = textLayout(node)
        assertTrue("预览实际文字不可省略：$name", !layout.multiParagraph.didExceedMaxLines)
        (0 until layout.lineCount).forEach { line ->
            assertTrue("预览实际文字不可省略：$name", !layout.isLineEllipsized(line))
        }
        measurements["${name}Text"] = layout.layoutInput.text.text.replace("\n", " / ")
        measurements["${name}TextLines"] = layout.lineCount.toString()
    }

    private fun noticeText() = Preview.noticeMessages.joinToString("\n") { "• $it" }

    private fun previewNode(matcher: SemanticsMatcher): SemanticsNodeInteraction = pageNode(
        matcher and hasAnyAncestor(
            hasAnyDescendant(hasText("来源：${Preview.sourceLabel}")) and
                    hasAnyDescendant(hasText("确认导入")) and !hasScrollToIndexAction() and
                    !hasAnyDescendant(hasScrollToIndexAction()) and !hasTestTag(StageTag) and !isRoot()
        ),
        unmerged = true,
    )

    companion object {
        const val MemoryAuthority = "p06.fixture.memory"
        const val SourceTitle = "选择导入来源"
        const val IntroTitle = "管理 Narra 本地资源包"
        const val ExportJson = "{\"version\":1,\"worldBookEntries\":[]}"
        val OverviewState = ContextTransferUiState(
            customAssistantCount = 3,
            worldBookCount = 18,
            memoryCount = 12,
            summaryCount = 7,
            presetCount = 4
        )
        val Preview = ContextImportPreview(
            sourceLabel = "上下文数据包",
            section = ContextTransferSection.ALL,
            assistantCount = 2,
            worldBookCount = 6,
            memoryCount = 3,
            summaryCount = 2,
            presetCount = 1,
            conflicts = (1..7).map { index ->
                ContextImportConflict(
                    typeLabel = if (index % 2 == 0) "世界书" else "角色",
                    title = "群岛档案 $index：旅人与守灯人的旧日约定",
                    id = "p06-conflict-$index",
                )
            },
            noticeMessages = listOf(
                "部分内容已按安全上限截断。",
                "有 2 条摘要分段缺少对应会话，导入时将跳过。"
            ),
        )
    }
}

class RecordingTransferRegistry : ActivityResultRegistry() {
    data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?
    )

    val launches = mutableListOf<Launch>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        launches += Launch(requestCode, contract, input)
    }
}

class MemoryTransferProvider : ContentProvider() {
    val documents = linkedMapOf<Uri, Pair<String, String>>()
    val queries = AtomicInteger()
    val typeQueries = AtomicInteger()

    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String {
        typeQueries.incrementAndGet()
        return documents.getValue(uri).second
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        queries.incrementAndGet()
        val document = documents.getValue(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(document.first)) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        error("内存捕获 provider 不允许 insert")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = error("内存捕获 provider 不允许 update")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        error("内存捕获 provider 不允许 delete")
}
