package com.example.myapplication.ui.screen.roleplay

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.CompanionReadingAnchor
import com.example.myapplication.model.CompanionReadingBinding
import com.example.myapplication.model.CompanionReadingRecord
import com.example.myapplication.model.CompanionReadingScope
import com.example.myapplication.model.CompanionSpeechSelection
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import com.example.myapplication.viewmodel.CompanionReadingUiState
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionReadingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ui = CompanionD4UiFixture(composeRule)

    @After
    fun release() {
        ui.close()
    }

    @Test
    fun parsedTagsAndLongJoinedCharacters_remainFaithfulInActualReadingTexts() {
        val literal = "字面 <narration>内容应保留</narration> 与 &#65;，不再次解析。"
        val clusters = listOf("😀", "e\u0301", "👩🏽‍💻")
        val longBody = "中".repeat(143) + clusters[0] + "后".repeat(143) +
                clusters[1] + "文".repeat(142) + clusters[2] + "完".repeat(180)
        val spacedBody = "前" + " ".repeat(300) + "后".repeat(100)
        val spacedXml = "前" + "&#32;".repeat(300) + "后".repeat(100)

        fun xmlText(text: String) =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val raw = "<thought>" + xmlText(literal) + "</thought><narration>" + xmlText(literal) +
                "</narration><dialogue>" + longBody + "</dialogue><thought>" + spacedXml +
                "</thought><narration>" + spacedXml + "</narration><dialogue>" + spacedXml + "</dialogue>"
        val template = record(1, "准备数据")
        val message = companionD4Message(
            raw, id = template.message.id, conversationId = template.message.conversationId,
            createdAt = template.message.createdAt,
        )
        val fixture = Fixture(listOf(template.copy(message = message)))
        assertEquals(6, fixture.state.value.items.size)
        ui.render { Reader(fixture) }
        // 与既有回看用例一致，先实际触控退出跟底，不能把程序跳转视作用户滚动。
        scrollIntoHistory(fixture, 0)

        fixture.state.value.items.forEachIndexed { index, item ->
            composeRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
            composeRule.onNodeWithTag(LIST).performScrollToIndex(index + 1)
            composeRule.onNodeWithTag(LIST).performTouchInput {
                swipeUp(startY = height * 0.55f, endY = height * 0.45f, durationMillis = 500)
            }
            composeRule.waitForIdle()
            val visible = composeRule.runOnIdle {
                fixture.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
            }
            if (visible == null) {
                val diagnostic = captureParsedReadingDiagnostic(
                    fixture,
                    index,
                    emptyList(),
                    emptyList(),
                    emptyList()
                )
                assertTrue("当前源item必须仍在真实layout中：$diagnostic", false)
            }
            assertEquals(
                "同一canonical消息须按实际段itemId定位",
                index + 1,
                requireNotNull(visible).index
            )
            composeRule.onNodeWithTag("companion_reader_item_" + item.id, useUnmergedTree = true)
                .assertIsDisplayed()
            val expected = requireNotNull(item.ui.companionBeat).content
            val scope = hasAnyAncestor(hasTestTag("companion_reader_item_" + item.id))
            val nodes = composeRule.onAllNodes(
                scope and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            val rawLayouts = nodes.flatMap { node ->
                val result = mutableListOf<TextLayoutResult>()
                composeRule.runOnUiThread {
                    assertTrue(
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(
                            result
                        ) == true
                    )
                }
                result
            }
            val layouts = rawLayouts.filter { layout ->
                val text = layout.layoutInput.text.text
                text.isNotEmpty() && text != item.ui.speakerName && expected.contains(text)
            }
            if (layouts.isEmpty()) {
                val diagnostic =
                    captureParsedReadingDiagnostic(fixture, index, nodes, rawLayouts, layouts)
                assertTrue("实际阅读项必须绘制正文Text：$diagnostic", false)
            }
            val renderedTexts = layouts.map { it.layoutInput.text.text }
            assertEquals("阅读显示不可再次清洗正文", expected, renderedTexts.joinToString(""))
            layouts.forEach { layout ->
                (0 until layout.lineCount).forEach { line ->
                    assertFalse("阅读原文不得省略", layout.isLineEllipsized(line))
                }
            }
            renderedTexts.forEach { text ->
                var offset = 0
                while (offset < text.length) {
                    val char = text[offset]
                    assertFalse("独立阅读Text不能含孤立低代理", char.isLowSurrogate())
                    if (char.isHighSurrogate()) {
                        assertTrue("独立阅读Text内代理对须完整", offset + 1 < text.length)
                        assertTrue(text[offset + 1].isLowSurrogate())
                        offset += 2
                    } else offset++
                }
            }
            if (expected == longBody) {
                assertTrue("长阅读正文须有实际独立分段", renderedTexts.size > 1)
                clusters.forEach { cluster ->
                    assertTrue("整个grapheme须绘在同一Text", renderedTexts.any { cluster in it })
                }
            } else if (expected == spacedBody) {
                assertTrue(
                    "每种实际阅读支路须保留全空白Text",
                    renderedTexts.any { it.isNotEmpty() && it.isBlank() })
                assertEquals(spacedBody, renderedTexts.joinToString(""))
            } else {
                assertTrue(renderedTexts.any { "<narration>" in it && "</narration>" in it })
                assertTrue(renderedTexts.any { "&#65;" in it })
            }
            assertEquals(raw, item.ui.companionReply?.raw)
            assertEquals(message.memoryRevision, item.ui.companionReply?.source?.revision)
        }
        composeRule.runOnIdle {
            assertEquals(raw, message.content)
            assertEquals(0, fixture.refreshCalls)
            assertEquals(0, fixture.loadOlderCalls)
            assertEquals(0, fixture.dismissCalls)
            assertEquals(0, fixture.retryOpenCalls)
            assertTrue(fixture.selections.none { it != null })
        }
    }

    @Test
    fun userScrolledUp_prependRefreshAndNewReply_keepActualSourceAndPixelOffset() {
        val fixture = Fixture(records(20..49))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 8)
        val before = actualAnchor(fixture)
        assertFalse(before.followingBottom)
        assertTrue("保锚用例必须具有真实像素偏移", before.offset > 0)
        composeRule.runOnIdle { fixture.replace(records(0..19) + fixture.state.value.records) }
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records.toList()) }
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records + records(50..50)) }
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle {
            assertEquals(51, fixture.state.value.records.size)
            assertEquals(51, fixture.state.value.records.map { it.message.id }.distinct().size)
            assertEquals(0, fixture.dismissCalls)
            assertEquals(0, fixture.refreshCalls)
            assertEquals(COMPANION_D4_SCENARIO_ID, fixture.state.value.scope?.context?.scenarioId)
        }
    }

    @Test
    fun onePointerDrag_windowUpdateAndContinuedDrag_keepTheLastUserPositionAfterRelease() {
        val fixture = Fixture(records(20..79))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 18)
        var pointerDown = false
        try {
            composeRule.onNodeWithTag(LIST).performTouchInput {
                val start = Offset(width * 0.5f, height * 0.75f)
                down(start)
                pointerDown = true
                advanceEventTime(100)
                moveTo(start.copy(y = height * 0.60f))
            }
            val atUpdate = actualAnchor(fixture)
            composeRule.runOnIdle {
                assertTrue(
                    "更新必须发生在同一指针尚未松开的实际拖动中",
                    fixture.list.isScrollInProgress
                )
                fixture.replace(records(0..19) + fixture.state.value.records)
            }
            // 不创建第二次 down；同一根手指在窗口更新后继续拖动。
            composeRule.onNodeWithTag(LIST).performTouchInput {
                advanceEventTime(300)
                moveTo(Offset(width * 0.5f, height * 0.30f))
            }
            val beforeRelease = actualAnchor(fixture)
            assertTrue(
                "窗口更新之后必须继续产生实际用户滚动",
                beforeRelease.messageId != atUpdate.messageId || beforeRelease.offset != atUpdate.offset
            )
            composeRule.onNodeWithTag(LIST).performTouchInput {
                // 用户在当前位置停住后松手，避免惯性使精确最终位置比较失去参照。
                advanceEventTime(300)
                up()
                pointerDown = false
            }
            assertSameAnchor(fixture, beforeRelease)
            val released = actualAnchor(fixture)
            assertTrue(
                "松手后不能恢复到窗口更新瞬间的旧锚点",
                released.messageId != atUpdate.messageId || released.offset != atUpdate.offset
            )
            composeRule.runOnIdle {
                assertEquals(80, fixture.state.value.records.size)
                assertEquals(80, fixture.state.value.records.map { it.message.id }.distinct().size)
                assertEquals(0, fixture.loadOlderCalls)
                assertEquals(0, fixture.refreshCalls)
                assertEquals(0, fixture.dismissCalls)
            }
        } finally {
            if (pointerDown) composeRule.onNodeWithTag(LIST).performTouchInput { up() }
        }
    }

    @Test
    fun deletedFirstVisibleRecord_continuesAtItsSurvivingNeighbourWithoutJumpingToBottom() {
        val fixture = Fixture(records(0..39))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 12)
        val before = actualAnchor(fixture)
        val originalItems = fixture.state.value.items
        val index = originalItems.indexOfFirst { it.id == before.itemId }
        val next = originalItems.drop(index + 1).first { it.record.message.id != before.messageId }
        composeRule.runOnIdle {
            fixture.replace(fixture.state.value.records.filterNot { it.message.id == before.messageId })
        }
        try {
            composeRule.waitUntil(5_000) { actualAnchorOrNull(fixture)?.messageId == next.record.message.id }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val observed = composeRule.runOnIdle {
                val layout = fixture.list.layoutInfo
                "anchor=" + fixture.anchors.lastOrNull() +
                        "\nviewport=" + layout.viewportStartOffset + ".." + layout.viewportEndOffset +
                        "\nvisible=" + layout.visibleItemsInfo.joinToString {
                    "${it.key}@${it.offset}+${it.size}"
                }
            }
            throw AssertionError(
                "删除后的首条可见记录应为 ${next.record.message.id}：\n$observed",
                timeout
            )
        }
        val after = actualAnchor(fixture)
        assertEquals(next.id, after.itemId)
        assertEquals(0, after.offset)
        assertFalse(after.followingBottom)
        composeRule.runOnIdle {
            assertTrue(fixture.list.canScrollForward)
            assertEquals(39, fixture.state.value.records.size)
        }
        composeRule.onNodeWithTag(LIST).performTouchInput {
            swipeUp(startY = height * 0.55f, endY = height * 0.45f, durationMillis = 500)
        }
        composeRule.waitForIdle()
        val scrolled = actualAnchor(fixture)
        assertTrue(
            "恢复后应继续捕获用户实际滚动",
            scrolled.messageId != after.messageId || scrolled.offset != after.offset
        )
        assertFalse(scrolled.followingBottom)
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records + records(40..40)) }
        assertSameAnchor(fixture, scrolled)
    }

    @Test
    fun onlyAtBottom_newRecordsFollow_andExplicitLatestRestoresFollowing() {
        val fixture = Fixture(records(0..29))
        ui.render { Reader(fixture) }
        composeRule.waitUntil(5_000) { !fixture.list.canScrollForward }
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records + records(30..30)) }
        composeRule.waitUntil(5_000) {
            !fixture.list.canScrollForward && fixture.list.layoutInfo.visibleItemsInfo.any {
                it.key == fixture.state.value.items.last().id
            }
        }
        scrollIntoHistory(fixture, 9)
        val historical = actualAnchor(fixture)
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records + records(31..31)) }
        assertSameAnchor(fixture, historical)
        composeRule.onNodeWithTag("companion_reader_bottom").performClick()
        composeRule.waitUntil(5_000) { !fixture.list.canScrollForward }
        composeRule.runOnIdle { fixture.replace(fixture.state.value.records + records(32..32)) }
        composeRule.waitUntil(5_000) {
            !fixture.list.canScrollForward && fixture.list.layoutInfo.visibleItemsInfo.any {
                it.key == fixture.state.value.items.last().id
            }
        }
        composeRule.runOnIdle {
            assertTrue(fixture.anchors.last().followingBottom)
            assertEquals(0, fixture.dismissCalls)
        }
    }

    @Test
    fun fontReflowAndRealIme_preserveTheScrolledSourceAndOffset() {
        val fixture = Fixture(records(0..39))
        val fontScale = mutableStateOf(1f)
        ui.render {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    fontScale.value
                )
            ) {
                Reader(fixture, imeSource = true)
            }
        }
        scrollIntoHistory(fixture, 11)
        val before = actualAnchor(fixture)
        // 此步骤测试字体重排；真正 200% 平台配置在下方独立窄屏用例核验。
        composeRule.runOnIdle { fontScale.value = 2f }
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle {
            fixture.settings.value = fixture.settings.value.copy(
                roleplayLineHeightScale = RoleplayLineHeightScale.entries.last(),
            )
            fixture.replace(fixture.state.value.records)
        }
        assertSameAnchor(fixture, before)
        composeRule.onNodeWithTag(IME_SOURCE).performClick().performTextInput("真实键盘")
        ui.showIme()
        assertSameAnchor(fixture, before)
        Espresso.pressBack()
        composeRule.waitUntil(5_000) { !ui.imeVisible() }
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle { assertEquals(0, fixture.dismissCalls) }
    }

    @Test
    fun actualImageDecodeAndAspectRatioReflow_preserveTheHistoricalSourceAndOffset() {
        val broken = ui.image("reader-unreadable", Color.BLACK).apply { writeText("invalid image") }
        val color = Color.rgb(28, 157, 181)
        val valid = ui.image("reader-tall", color, width = 64, height = 256)
        val fixture = Fixture(records(0..39))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 12)
        val before = actualAnchor(fixture)
        fun imageSnapshot(path: String) = fixture.state.value.records.map { record ->
            if (record.message.id != before.messageId) record else record.copy(
                message = record.message.copy(
                    parts = listOf(
                        ChatMessagePart(
                            type = ChatMessagePartType.TEXT,
                            text = record.message.content
                        ),
                        ChatMessagePart(type = ChatMessagePartType.IMAGE, uri = path),
                    )
                ),
            )
        }
        composeRule.runOnIdle { fixture.replace(imageSnapshot(broken.absolutePath)) }
        composeRule.onNode(
            hasText("图片暂不可读") and
                    hasAnyAncestor(hasTestTag("companion_reader_item_" + before.itemId)),
        ).assertExists()
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle { fixture.replace(imageSnapshot(valid.absolutePath)) }
        val imageTag = "companion_reader_image_" + before.messageId + "_0"
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(imageTag).fetchSemanticsNodes().isNotEmpty()
        }
        val image = composeRule.onNodeWithTag(imageTag).assertIsDisplayed()
        val bounds = image.getUnclippedBoundsInRoot()
        assertEquals(
            4f,
            (bounds.bottom - bounds.top).value / (bounds.right - bounds.left).value,
            0.05f
        )
        val bitmap = image.captureToImage().asAndroidBitmap()
        assertEquals(color, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertSameAnchor(fixture, before)
        composeRule.runOnIdle {
            val item = fixture.state.value.items.first { it.id == before.itemId }
            assertEquals(1L, item.ui.companionReply?.source?.revision)
            assertEquals(0, fixture.dismissCalls)
            assertEquals(0, fixture.refreshCalls)
        }
        ui.captureCandidate(
            "reader-image-loaded-historical-anchor", composeRule.onNodeWithTag("companion_reader"),
            "messageId=" + before.messageId + "\nitemId=" + before.itemId +
                    "\noffsetBefore=" + before.offset + "\noffsetAfter=" + actualAnchor(fixture).offset +
                    "\nimageAspectRatio=4.0\npixelColor=" + color
        )
    }

    @Test
    fun expandedOlderPage_crossSceneSpeechSelectionAndBackConsumeTheirOwnLayers() {
        val fixture = Fixture(records(10..19), older = records(0..9))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 1)
        openMenu(fixture, "d4-record-10")
        composeRule.onNodeWithTag("companion_reader_select_start").performClick()
        composeRule.onNodeWithTag(LIST).performScrollToIndex(0)
        composeRule.onNodeWithTag("companion_reader_load_older").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.loadOlderCalls)
            assertEquals(20, fixture.state.value.records.size)
        }
        openMenu(fixture, "d4-record-5")
        composeRule.onNodeWithTag("companion_reader_select_start").performClick()
        openMenu(fixture, "d4-record-19")
        composeRule.onNodeWithTag("companion_reader_select_end").performClick()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.runOnIdle {
            val selected = requireNotNull(fixture.state.value.selection)
            assertEquals(15, selected.units.size)
            assertEquals("d4-record-5", selected.units.first().source.messageId)
            assertEquals("d4-record-19", selected.units.last().source.messageId)
            assertEquals(2, selected.units.map { it.source.conversationId }.distinct().size)
            selected.units.forEach {
                assertTrue(it.rawSpan.endExclusive > it.rawSpan.start)
                assertTrue(it.rawText.isNotBlank())
                assertFalse(it.text.contains("<observe"))
            }
            assertEquals(0, fixture.dismissCalls)
            assertEquals(COMPANION_D4_SCENARIO_ID, fixture.state.value.scope?.context?.scenarioId)
        }
        Espresso.pressBack()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertNotNull(fixture.state.value.selection)
            assertEquals(0, fixture.dismissCalls)
        }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(null, fixture.state.value.selection)
            assertEquals(0, fixture.dismissCalls)
        }
        Espresso.pressBack()
        composeRule.runOnIdle { assertEquals(1, fixture.dismissCalls) }
    }

    @Test
    fun oneLongPressThenDrag_cancelsReadingMenuWithoutSelectingOrNavigating() {
        val fixture = Fixture(records(0..29))
        ui.render { Reader(fixture) }
        scrollIntoHistory(fixture, 9)
        val before = actualAnchor(fixture)
        composeRule.onNodeWithTag("companion_reader_item_" + before.itemId).performTouchInput {
            val start = Offset(width * 0.03f, height * 0.6f)
            down(start)
            advanceEventTime(800)
            moveTo(start.copy(y = start.y - 90f))
            advanceEventTime(100)
            up()
        }
        composeRule.onNodeWithTag("companion_reader_select_unit").assertDoesNotExist()
        composeRule.onNodeWithText("复制").assertDoesNotExist()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(null, fixture.state.value.selection)
            assertTrue(fixture.selections.filterNotNull().isEmpty())
            assertEquals(0, fixture.dismissCalls)
            assertEquals(0, fixture.loadOlderCalls)
            assertEquals(0, fixture.refreshCalls)
        }
    }

    @Test
    fun changedSource_clearsPendingSelectionStartAndClosesInvalidatedSelection() {
        val fixture = Fixture(records(0..12))
        ui.render { Reader(fixture) }
        openMenu(fixture, "d4-record-4")
        composeRule.onNodeWithTag("companion_reader_select_start").performClick()
        composeRule.onNodeWithText("已设置起点，长按另一段选择到那里。").assertIsDisplayed()
        composeRule.runOnIdle {
            fixture.replace(fixture.state.value.records.map {
                if (it.message.id == "d4-record-4") it.copy(
                    message = companionD4Message(
                        "<narration>此记录已修订。</narration>", id = it.message.id,
                        revision = 2L, conversationId = it.message.conversationId,
                        createdAt = it.message.createdAt,
                    ),
                ) else it
            })
        }
        composeRule.onNodeWithText("已设置起点，长按另一段选择到那里。").assertDoesNotExist()
        openMenu(fixture, "d4-record-6")
        composeRule.onNodeWithTag("companion_reader_select_end").assertDoesNotExist()
        composeRule.onNodeWithTag("companion_reader_select_unit").performClick()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        // VM 的来源失效信号进入真实 Screen 后，旧 Dialog 也必须退出。
        composeRule.runOnIdle { fixture.state.value = fixture.state.value.copy(selection = null) }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, fixture.dismissCalls) }
    }

    @Test
    fun loadingAndUnavailableScope_disableDuplicateActionsAndUseTheCorrectRetryCallback() {
        val fixture = Fixture(records(0..8), older = records(-5..-1))
        ui.render { Reader(fixture) }
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
        composeRule.onNodeWithTag(LIST).performScrollToIndex(0)
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(isLoadingOlder = true)
        }
        composeRule.onNodeWithTag("companion_reader_load_older").assertIsNotEnabled()
        composeRule.runOnIdle {
            fixture.state.value =
                fixture.state.value.copy(isLoadingOlder = false, isRefreshing = true)
        }
        composeRule.onNodeWithTag("companion_reader_refresh").assertIsNotEnabled()
        composeRule.runOnIdle {
            fixture.state.value = CompanionReadingUiState(
                isLoading = false, errorMessage = "阅读范围已改变，请重新打开。",
            )
        }
        composeRule.onNodeWithText("阅读范围已改变，请重新打开。").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.retryOpenCalls)
            assertEquals(0, fixture.loadOlderCalls)
            assertEquals(0, fixture.refreshCalls)
        }
    }

    @Test
    fun narrowPlatformFont200_reachesTheLastRecordAndKeepsCloseOutsideTheList() {
        val lastText = "最后记录：完整可读。"
        val data = records(0..23) + record(24, lastText)
        val fixture = Fixture(data)
        ui.withNarrowPlatformWindow(
            "reader-320dp-font200",
            content = { Reader(fixture, imeSource = true) }) {
            composeRule.onNodeWithTag("companion_reader_bottom").performClick()
            composeRule.waitUntil(5_000) { !fixture.list.canScrollForward }
            val last = composeRule.onNodeWithText(lastText, substring = true)
            val viewport = composeRule.onNodeWithTag(LIST)
            val environment = ui.assertNarrowPlatformText(last)
            val lastBounds = ui.assertCompleteTextInViewport(last, viewport)
            val close = composeRule.onNodeWithTag("companion_reader_close").assertIsDisplayed()
            ui.assertTouchTarget(close, composeRule.onNodeWithTag("companion_reader"))
            ui.assertTouchTarget(
                composeRule.onNodeWithTag("companion_reader_bottom"),
                composeRule.onNodeWithTag("companion_reader")
            )
            ui.captureCandidate(
                "reader-320dp-font200-last-record", composeRule.onNodeWithTag("companion_reader"),
                environment + "\n" + lastBounds + "\ncanScrollForward=" + fixture.list.canScrollForward
            )
            scrollIntoHistory(fixture, 6)
            val before = actualAnchor(fixture)
            composeRule.onNodeWithTag(IME_SOURCE).performClick().performTextInput("窄屏键盘")
            ui.showIme()
            assertSameAnchor(fixture, before)
            close.assertIsDisplayed()
            ui.assertTouchTarget(close, composeRule.onNodeWithTag("companion_reader"))
            Espresso.pressBack()
            composeRule.waitUntil(5_000) { !ui.imeVisible() }
            assertSameAnchor(fixture, before)
            composeRule.runOnIdle { assertEquals(0, fixture.dismissCalls) }
            close.performClick()
            composeRule.runOnIdle { assertEquals(1, fixture.dismissCalls) }
        }
    }

    private fun captureParsedReadingDiagnostic(
        fixture: Fixture,
        index: Int,
        scopedNodes: List<SemanticsNode>,
        rawLayouts: List<TextLayoutResult>,
        filteredLayouts: List<TextLayoutResult>,
    ): String {
        val item = fixture.state.value.items[index]
        val itemTag = "companion_reader_item_" + item.id
        val summary = "index=$index requestedLazyIndex=${index + 1} item=${item.id}" +
                " type=${item.ui.contentType} expectedLength=${item.ui.companionBeat?.content?.length}" +
                " scopedNodes=${scopedNodes.size} rawLayouts=${rawLayouts.size}" +
                " filteredLayouts=${filteredLayouts.size}"
        val evidence = StringBuilder(summary)
        val listEvidence = composeRule.runOnIdle {
            val info = fixture.list.layoutInfo
            "\nfirstVisibleIndex=${fixture.list.firstVisibleItemIndex}" +
                    " offset=${fixture.list.firstVisibleItemScrollOffset}" +
                    " isScrollInProgress=${fixture.list.isScrollInProgress}" +
                    " totalItems=${info.totalItemsCount}" +
                    " viewportStart=${info.viewportStartOffset} viewportEnd=${info.viewportEndOffset}" +
                    "\nlastAnchor=${fixture.anchors.lastOrNull()}" +
                    "\nfollowingBottom=${fixture.anchors.lastOrNull()?.followingBottom}" +
                    "\nvisibleItems=" + info.visibleItemsInfo.joinToString("\n") {
                "index=${it.index} key=${it.key} offset=${it.offset} size=${it.size}"
            } + "\nsourceItems=" + fixture.state.value.items.mapIndexed { sourceIndex, sourceItem ->
                "index=$sourceIndex key=${sourceItem.id} type=${sourceItem.ui.contentType}"
            }.joinToString("\n")
        }
        evidence.append(listEvidence)
        evidence.append("\nitemTag=$itemTag")
        composeRule.onAllNodes(hasTestTag(itemTag), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                evidence.append(
                    "\nitemNode id=${node.id} bounds=${node.boundsInRoot}" +
                            " position=${node.positionInRoot} size=${node.size}"
                )
            }
        evidence.append("\nscopedRawTextNodes:")
        scopedNodes.forEach { node ->
            evidence.append(
                "\nnode id=${node.id} bounds=${node.boundsInRoot}" +
                        " position=${node.positionInRoot} size=${node.size}" +
                        " text=" + node.config[SemanticsProperties.Text].joinToString(" | ") {
                    it.text.replace(" ", "·").replace("\n", "\\n")
                })
        }
        fun appendLayouts(label: String, layouts: List<TextLayoutResult>) {
            evidence.append("\n$label:")
            layouts.forEachIndexed { layoutIndex, layout ->
                evidence.append(
                    "\nlayout=$layoutIndex size=${layout.size} lineCount=${layout.lineCount}" +
                            " length=${layout.layoutInput.text.length} text=" +
                            layout.layoutInput.text.text.replace(" ", "·").replace("\n", "\\n")
                )
            }
        }
        appendLayouts("allTextLayouts", rawLayouts)
        appendLayouts("filteredTextLayouts", filteredLayouts)
        evidence.append("\ncurrentUnmergedSemantics:")
        composeRule.onAllNodes(
            SemanticsMatcher("所有合成Reader节点") { true },
            useUnmergedTree = true
        )
            .fetchSemanticsNodes().forEach { node ->
                evidence.append(
                    "\nnode id=${node.id} bounds=${node.boundsInRoot}" +
                            " position=${node.positionInRoot} size=${node.size} config=${node.config}"
                )
            }
        ui.captureCandidate(
            "reader-parsed-before-item-$index",
            composeRule.onNodeWithTag("companion_reader"),
            evidence.toString(),
        )
        return summary + " visible=" + composeRule.runOnIdle {
            fixture.list.layoutInfo.visibleItemsInfo.joinToString { "${it.index}:${it.key}" }
        }
    }

    @Composable
    private fun Reader(fixture: Fixture, imeSource: Boolean = false) {
        if (imeSource) {
            Column {
                Box(Modifier.weight(1f)) { Content(fixture) }
                // 阅读页没有编辑功能，此独立宿主控件只提供真实 IME/Insets。
                OutlinedTextField(
                    fixture.input.value, { fixture.input.value = it },
                    modifier = Modifier.fillMaxWidth().testTag(IME_SOURCE), singleLine = true
                )
            }
        } else Content(fixture)
    }

    @Composable
    private fun Content(fixture: Fixture) {
        CompanionReadingScreen(
            fixture.state.value,
            fixture.settings.value,
            fixture.callbacks,
            listState = fixture.list
        )
    }

    private fun scrollIntoHistory(fixture: Fixture, itemIndex: Int) {
        // 先走真实触控解除跟底，再定向滚动；不用手写 anchor 驱动被测列表。
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
        composeRule.onNodeWithTag(LIST).performScrollToIndex(itemIndex + 1)
        composeRule.onNodeWithTag(LIST).performTouchInput {
            swipeUp(startY = height * 0.55f, endY = height * 0.45f, durationMillis = 500)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(fixture.list.canScrollForward)
            assertFalse(fixture.anchors.last().followingBottom)
        }
    }

    private fun openMenu(fixture: Fixture, messageId: String) {
        val index = fixture.state.value.items.indexOfFirst { it.record.message.id == messageId }
        assertTrue(index >= 0)
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
        composeRule.onNodeWithTag(LIST).performScrollToIndex(index + 1)
        composeRule.onNodeWithTag("companion_reader_item_" + fixture.state.value.items[index].id)
            .performTouchInput {
                down(Offset(width * 0.03f, height * 0.35f))
                advanceEventTime(800)
                up()
            }
        composeRule.onNodeWithTag("companion_reader_select_unit").assertIsDisplayed()
    }

    private fun actualAnchor(fixture: Fixture): CompanionReadingAnchor =
        requireNotNull(actualAnchorOrNull(fixture))

    private fun actualAnchorOrNull(fixture: Fixture): CompanionReadingAnchor? =
        composeRule.runOnIdle {
            val layout = fixture.list.layoutInfo
            val contentStart = maxOf(0, layout.viewportStartOffset)
            val first = layout.visibleItemsInfo.firstOrNull { info ->
                info.offset + info.size > contentStart && info.offset < layout.viewportEndOffset &&
                        fixture.state.value.items.any { it.id == info.key }
            } ?: return@runOnIdle null
            val item = fixture.state.value.items.first { it.id == first.key }
            val callback = fixture.anchors.lastOrNull() ?: return@runOnIdle null
            assertEquals(item.record.message.id, callback.messageId)
            assertEquals(item.id, callback.itemId)
            assertEquals((-first.offset).coerceAtLeast(0), callback.offset)
            callback
        }

    private fun assertSameAnchor(fixture: Fixture, expected: CompanionReadingAnchor) {
        composeRule.waitForIdle()
        val actual = actualAnchor(fixture)
        assertEquals(expected.messageId, actual.messageId)
        assertEquals(expected.itemId, actual.itemId)
        assertTrue(
            "像素偏移必须保留：" + expected.offset + " -> " + actual.offset,
            abs(expected.offset - actual.offset) <= 2
        )
        assertFalse(actual.followingBottom)
    }

    private fun records(indices: IntRange): List<CompanionReadingRecord> = indices.map {
        record(it, "记录 " + it + "：他慢慢讲述这一场的事情，保留原来的上下文。".repeat(4))
    }

    private fun record(index: Int, text: String): CompanionReadingRecord {
        val conversation = if (index % 2 == 0) "d4-native-history-a" else "d4-native-history-b"
        val scenario = if (index % 2 == 0) "d4-native-old-scene" else COMPANION_D4_SCENARIO_ID
        val message = companionD4Message(
            "<narration>" + text + "</narration>", id = "d4-record-" + index,
            conversationId = conversation, createdAt = 1_700_000_000_000L + index,
        )
        return CompanionReadingRecord(
            message, CompanionReadingBinding(
                conversationId = conversation,
                scenarioId = scenario,
                runId = "d4-native-run",
                title = if (index % 2 == 0) "旧场次" else "当前场次",
                characterId = COMPANION_D4_CHARACTER_ID,
                scenario = RoleplayScenario(
                    id = scenario,
                    title = "阅读合成场次",
                    assistantId = COMPANION_D4_CHARACTER_ID,
                    interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE
                ),
            ), encounterId = if (index % 2 == 0) "d4-old-encounter" else "d4-current-encounter"
        )
    }

    private class Fixture(
        records: List<CompanionReadingRecord>,
        private var older: List<CompanionReadingRecord> = emptyList()
    ) {
        val settings = mutableStateOf(companionD4Settings())
        val list = LazyListState()
        val input = mutableStateOf("")
        val anchors = mutableListOf<CompanionReadingAnchor>()
        val selections = mutableListOf<CompanionSpeechSelection?>()
        var loadOlderCalls = 0
        var refreshCalls = 0
        var dismissCalls = 0
        var retryOpenCalls = 0
        val state = mutableStateOf(
            CompanionReadingUiState(
                scope = CompanionReadingScope(
                    MemoryRuntimeContext(
                        COMPANION_D4_CHARACTER_ID, "d4-native-space",
                        "d4-native-history-b", COMPANION_D4_SCENARIO_ID, "d4-native-run"
                    ),
                    records.map { it.binding }.distinctBy { it.conversationId },
                    records.mapNotNull { it.encounterId }.toSet(),
                ),
                records = records,
                items = CompanionReadingSupport.items(records, settings.value),
                isLoading = false,
                hasOlder = older.isNotEmpty(),
                nextBefore = records.firstOrNull()?.cursor,
            )
        )
        val callbacks = CompanionReadingCallbacks(
            onLoadOlder = {
                loadOlderCalls++
                replace(older + state.value.records)
                older = emptyList()
                state.value = state.value.copy(hasOlder = false)
            },
            onRefresh = { refreshCalls++ },
            onAnchorChange = { anchors += it; state.value = state.value.copy(anchor = it) },
            onSelectionChange = {
                selections += it; state.value = state.value.copy(selection = it)
            },
            onDismiss = { dismissCalls++ },
            onRetryOpen = { retryOpenCalls++ },
        )

        fun replace(records: List<CompanionReadingRecord>) {
            val sorted = CompanionReadingSupport.refreshedWindow(records)
            state.value = state.value.copy(
                records = sorted, items = CompanionReadingSupport.items(sorted, settings.value),
            )
        }
    }

    companion object {
        private const val LIST = "companion_reader_list"
        private const val IME_SOURCE = "d4_reader_ime_source"
    }
}
