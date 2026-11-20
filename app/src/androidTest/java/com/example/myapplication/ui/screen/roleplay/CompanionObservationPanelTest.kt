package com.example.myapplication.ui.screen.roleplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionMemoryState
import com.example.myapplication.model.CompanionObservationField
import com.example.myapplication.model.CompanionObservationSettings
import com.example.myapplication.model.CompanionPresentationSnapshot
import com.example.myapplication.model.CompanionReplyDiagnostic
import com.example.myapplication.model.CompanionReplyMetadata
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ParsedCompanionReply
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.companionBuiltinObservationFields
import com.example.myapplication.model.companionRawHash
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 面板始终消费真实 parser/projector 的产物，不在测试控件里重写观测解析。 */
@RunWith(AndroidJUnit4::class)
class CompanionObservationPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun historicalReply_displaysSnapshotLabelsAndBackClosesOnlyItsDialog() {
        val fixture = Fixture(
            project(
                observedRaw(),
                snapshot = observationSnapshot(timeLabel = "当时的时刻", coatLabel = "当时的穿着"),
            ),
        )
        render(fixture)
        openPanel()
        composeRule.onNodeWithText("当时的时刻：傍晚\n\n当时的穿着：蓝色外套")
            .assertIsDisplayed().assertTextEquals("当时的时刻：傍晚\n\n当时的穿着：蓝色外套")
        composeRule.onNodeWithText("当前的衣着", substring = true).assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(OPEN_TAG).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, fixture.backCalls) }
        Espresso.pressBack()
        composeRule.runOnIdle { assertEquals(1, fixture.backCalls) }
    }

    @Test
    fun readingHost_currentDisplayGateClosesDialogWithoutChangingHistoricalLabels() {
        val reply = project(
            observedRaw(),
            snapshot = observationSnapshot(timeLabel = "原时刻", coatLabel = "原衣着")
        )
        val enabled = mutableStateOf(true)
        val messages = reply.beats.map { beat ->
            RoleplayMessageUiModel(
                uiItemId = beat.id,
                sourceMessageId = reply.source.messageId,
                contentType = beat.contentType,
                speaker = beat.speaker,
                speakerName = beat.speakerName,
                content = beat.content,
                companionReply = reply,
                companionBeat = beat,
            )
        }
        composeRule.setContent {
            ChatAppTheme {
                RoleplayReadingMode(
                    messages, "旧场记录", "", 1f, false, onDismiss = {},
                    companionObservationEnabledFor = { characterId ->
                        characterId == reply.source.characterId && enabled.value
                    },
                )
            }
        }
        openPanel()
        composeRule.onNodeWithText("原时刻：傍晚\n\n原衣着：蓝色外套").assertIsDisplayed()
        composeRule.runOnIdle { enabled.value = false }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(OPEN_TAG).assertDoesNotExist()
        composeRule.runOnIdle { enabled.value = true }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        openPanel()
        composeRule.onNodeWithText("原时刻：傍晚\n\n原衣着：蓝色外套")
            .assertTextEquals("原时刻：傍晚\n\n原衣着：蓝色外套")
        composeRule.runOnIdle { assertEquals("原衣着", reply.observation!!.fields.last().label) }
    }

    @Test
    fun nextReplyWithoutObservation_removesExpandedOldDialogAndKeepsItsBody() {
        val fixture = Fixture(project(observedRaw()))
        render(fixture)
        openPanel()
        composeRule.runOnIdle {
            fixture.reply.value = project(
                "<dialogue>这一轮只安静地坐一会儿。</dialogue>",
                id = "reply-without-observation",
            )
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(OPEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("蓝色外套", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(BODY_TAG)
            .assertTextContains("这一轮只安静地坐一会儿。", substring = true)
        composeRule.runOnIdle {
            assertEquals(null, fixture.reply.value?.observation)
            assertEquals(0, fixture.backCalls)
        }
    }

    @Test
    fun malformedOrUnknownFields_clearOldObservationWithoutSwallowingFollowingDialogue() {
        val fixture = Fixture(project(observedRaw()))
        render(fixture)
        val invalidReplies = listOf(
            "<observe version=\"1\"><field id=\"time\">傍晚" +
                    "<dialogue>仍保留这句正文。</dialogue>",
            "<observe version=\"1\"><field id=\"obs_removed\">不应显示的旧值</field></observe>" +
                    "<dialogue>仍保留这句正文。</dialogue>",
        )
        invalidReplies.forEachIndexed { index, raw ->
            composeRule.runOnIdle {
                fixture.reply.value = project(observedRaw(), id = "valid-before-$index")
            }
            openPanel()
            composeRule.runOnIdle {
                fixture.reply.value = project(raw, id = "invalid-observation-$index")
            }
            composeRule.onNode(isDialog()).assertDoesNotExist()
            composeRule.onNodeWithTag(OPEN_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag(BODY_TAG)
                .assertTextContains("仍保留这句正文。", substring = true)
            composeRule.runOnIdle {
                val reply = requireNotNull(fixture.reply.value)
                assertEquals(raw, reply.raw)
                assertEquals(null, reply.observation)
                assertTrue(
                    CompanionReplyDiagnostic.MALFORMED_OBSERVATION in reply.diagnostics ||
                            CompanionReplyDiagnostic.UNKNOWN_OBSERVATION_FIELD in reply.diagnostics,
                )
            }
        }
    }

    @Test
    fun changedSourceRevision_closesOldDialogAndUsesEachRepliesOwnLabels() {
        val raw = observedRaw()
        val oldReply =
            project(raw, revision = 1, snapshot = observationSnapshot(coatLabel = "旧称衣着"))
        val newReply =
            project(raw, revision = 2, snapshot = observationSnapshot(coatLabel = "当前穿着"))
        val fixture = Fixture(oldReply)
        render(fixture)
        openPanel()
        composeRule.onNodeWithText("旧称衣着：蓝色外套", substring = true).assertIsDisplayed()

        composeRule.runOnIdle { fixture.reply.value = newReply }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(OPEN_TAG).assertIsDisplayed()
        openPanel()
        composeRule.onNodeWithText("当前穿着：蓝色外套", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("旧称衣着：", substring = true).assertDoesNotExist()
        composeRule.runOnIdle { fixture.reply.value = oldReply }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        openPanel()
        composeRule.onNodeWithText("旧称衣着：蓝色外套", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("当前穿着：", substring = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(raw, oldReply.raw)
            assertEquals(raw, newReply.raw)
            assertEquals(oldReply.source.rawHash, newReply.source.rawHash)
            assertEquals(0, fixture.backCalls)
        }
    }

    @Test
    fun disablingObservation_removesOpenDialogAndReenableRequiresAnotherClick() {
        val fixture = Fixture(project(observedRaw()))
        render(fixture)
        openPanel()
        composeRule.runOnIdle { fixture.enabled.value = false }
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(OPEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(BODY_TAG).assertTextContains("陪你坐一会儿。", substring = true)
        composeRule.runOnIdle { fixture.enabled.value = true }
        composeRule.onNodeWithTag(OPEN_TAG).assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        openPanel()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            fixture.reply.value = project(
                observedRaw(),
                id = "generation-observation-disabled",
                snapshot = observationSnapshot().copy(
                    observation = observationSnapshot().observation.copy(enabled = false),
                ),
            )
        }
        composeRule.onNodeWithTag(OPEN_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, fixture.backCalls) }
    }

    @Test
    fun narrowLargeFont_longObservationScrollsToEndAndKeepsCloseTouchTarget() {
        val longValue = "他仍安静地坐着，手里握着温水，偶尔望向窗外。".repeat(4)
        val raw = "<observe version=\"1\">" +
                "<field id=\"time\">傍晚</field>" +
                "<field id=\"place\">窗边的座位</field>" +
                "<field id=\"state\">$longValue</field>" +
                "<field id=\"detail\">$longValue</field>" +
                "<field id=\"obs_coat\">最后记录：蓝色外套与白色围巾。</field>" +
                "</observe><dialogue>陪你坐一会儿。</dialogue>"
        val fixture = Fixture(project(raw, snapshot = observationSnapshot(coatLabel = "随身物品")))
        withNarrowPlatformWindow(fixture) {
            composeRule.onNodeWithTag(OPEN_TAG)
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            val close = composeRule.onNodeWithText("关闭")
            close.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val content = composeRule.onNodeWithTag("narra_alert_dialog_content")
            val lastFieldText = "随身物品：最后记录：蓝色外套与白色围巾。"
            val observationText = composeRule.onNodeWithText(lastFieldText, substring = true)
            val layouts = mutableListOf<TextLayoutResult>()
            observationText.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            val layout = layouts.single()
            val environment = assertNarrowPlatformEnvironment(observationText, layout)
            val range = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("真实 200% 字体下长观测必须溢出视口", range.maxValue() > 0f)
            content.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                assertTrue(scroll(0f, 100_000f))
            }
            composeRule.waitForIdle()
            assertEquals(range.maxValue(), range.value(), 1f)
            observationText.assertIsDisplayed()
            val textNode = observationText.fetchSemanticsNode()
            val viewport = content.fetchSemanticsNode().boundsInRoot
            val lastFieldStart = layout.layoutInput.text.text.indexOf(lastFieldText)
            assertTrue(lastFieldStart >= 0)
            val lastFieldTop = textNode.positionInRoot.y +
                    layout.getLineTop(layout.getLineForOffset(lastFieldStart))
            val lastFieldBottom = textNode.positionInRoot.y +
                    layout.getLineBottom(layout.getLineForOffset(layout.layoutInput.text.length - 1))
            assertTrue("末项首行必须完整进入滚动视口", lastFieldTop >= viewport.top - 1f)
            assertTrue("末项末行必须完整进入滚动视口", lastFieldBottom <= viewport.bottom + 1f)
            close.assertIsDisplayed()
            val dialogBounds = composeRule.onNode(isDialog()).getUnclippedBoundsInRoot()
            val closeBounds = close.getUnclippedBoundsInRoot()
            assertTrue((dialogBounds.right - dialogBounds.left) <= 320.dp)
            assertTrue(closeBounds.left >= dialogBounds.left && closeBounds.right <= dialogBounds.right)
            assertTrue(closeBounds.top >= dialogBounds.top && closeBounds.bottom <= dialogBounds.bottom)
            captureDialogCandidate(
                "observation-320dp-font200-last-field",
                environment + "\nscroll=${range.value()}/${range.maxValue()}" +
                        "\nlastFieldTop=$lastFieldTop\nlastFieldBottom=$lastFieldBottom\nviewport=$viewport",
            )
            close.performClick()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(0, fixture.backCalls) }
            openPanel()
            Espresso.pressBack()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            composeRule.onNodeWithTag(OPEN_TAG).assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(0, fixture.backCalls) }
            Espresso.pressBack()
            composeRule.runOnIdle {
                assertFalse(fixture.reply.value?.observation?.fields.isNullOrEmpty())
                assertEquals(raw, fixture.reply.value?.raw)
                assertEquals(1, fixture.backCalls)
            }
        }
    }

    private fun render(fixture: Fixture) {
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                fontScale = 1f
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    configuration.fontScale
                ),
            ) {
                FixtureContent(fixture)
            }
        }
    }

    @Composable
    private fun FixtureContent(fixture: Fixture, narrow: Boolean = false) {
        ChatAppTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    (if (narrow) Modifier.width(320.dp).height(600.dp) else Modifier.fillMaxSize())
                        .testTag(HOST_TAG),
                ) {
                    BackHandler { fixture.backCalls++ }
                    CompanionObservationPanel(fixture.reply.value, enabled = fixture.enabled.value)
                    Text(
                        fixture.reply.value?.displayText.orEmpty(),
                        modifier = Modifier.testTag(BODY_TAG),
                    )
                }
            }
        }
    }

    private fun withNarrowPlatformWindow(fixture: Fixture, block: () -> Unit) {
        val activity = composeRule.activity
        val originalConfiguration = Configuration(activity.resources.configuration)
        val originalWidth = activity.window.attributes.width
        val originalHeight = activity.window.attributes.height
        var isolatedView: ComposeView? = null
        try {
            composeRule.runOnUiThread {
                // Dialog 会重建平台 density，必须覆盖宿主 View 的 Android Resources。
                val context = ContextThemeWrapper(activity, activity.theme).apply {
                    applyOverrideConfiguration(Configuration(originalConfiguration).apply {
                        fontScale = 2f
                        screenWidthDp = 320
                        screenHeightDp = 600
                        smallestScreenWidthDp = 320
                    })
                }
                val density = context.resources.displayMetrics.density
                val view = ComposeView(context).apply {
                    isolatedView = this
                    setContent { FixtureContent(fixture, narrow = true) }
                }
                activity.window.setLayout((320f * density).roundToInt(), (600f * density).roundToInt())
                activity.setContentView(view)
            }
            composeRule.waitForIdle()
            block()
        } finally {
            composeRule.runOnUiThread {
                // 先释放 Composition 和独立 Dialog，再恢复窗口；基础资源始终不写入。
                isolatedView?.disposeComposition()
                isolatedView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                activity.window.setLayout(originalWidth, originalHeight)
                val restored = activity.resources.configuration
                File(reviewDirectory(), "observation-platform-restored.txt").writeText(
                    "baseFontScaleBefore=${originalConfiguration.fontScale}" +
                            "\nbaseFontScaleAfter=${restored.fontScale}" +
                            "\nwindowWidthBefore=$originalWidth" +
                            "\nwindowWidthAfter=${activity.window.attributes.width}" +
                            "\nwindowHeightBefore=$originalHeight" +
                            "\nwindowHeightAfter=${activity.window.attributes.height}",
                )
                assertEquals(originalWidth, activity.window.attributes.width)
                assertEquals(originalHeight, activity.window.attributes.height)
                assertEquals(originalConfiguration.fontScale, restored.fontScale, 0.001f)
                assertEquals(originalConfiguration.screenWidthDp, restored.screenWidthDp)
                assertEquals(originalConfiguration.screenHeightDp, restored.screenHeightDp)
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun assertNarrowPlatformEnvironment(
        observationText: SemanticsNodeInteraction,
        layout: TextLayoutResult,
    ): String {
        val host = composeRule.onNodeWithTag(HOST_TAG).fetchSemanticsNode()
        val dialog = composeRule.onNode(isDialog()).fetchSemanticsNode()
        val text = observationText.fetchSemanticsNode()
        val hostOwner = host.root as? ViewRootForTest ?: error("宿主必须拥有真实 Android View")
        val dialogOwner = dialog.root as? ViewRootForTest ?: error("Dialog 必须拥有独立 Android View")
        val hostConfiguration = hostOwner.view.resources.configuration
        val dialogConfiguration = dialogOwner.view.resources.configuration
        val hostDensity = host.layoutInfo.density
        val dialogDensity = dialog.layoutInfo.density
        val content = composeRule.onNodeWithTag("narra_alert_dialog_content").fetchSemanticsNode()
        val range = content.config[SemanticsProperties.VerticalScrollAxisRange]
        val evidence = "hostPlatformFontScale=${hostConfiguration.fontScale}" +
                "\ndialogPlatformFontScale=${dialogConfiguration.fontScale}" +
                "\nhostLayoutFontScale=${hostDensity.fontScale}" +
                "\ndialogLayoutFontScale=${dialogDensity.fontScale}" +
                "\ntextNodeFontScale=${text.layoutInfo.density.fontScale}" +
                "\ntextLayoutFontScale=${layout.layoutInput.density.fontScale}" +
                "\nhostScreenWidthDp=${hostConfiguration.screenWidthDp}" +
                "\ndialogScreenWidthDp=${dialogConfiguration.screenWidthDp}" +
                "\nhostWidthPx=${host.boundsInRoot.width}\ndialogWidthPx=${dialogOwner.view.width}" +
                "\nhostDensity=${hostDensity.density}\ndialogDensity=${dialogDensity.density}" +
                "\nhostHeightPx=${host.boundsInRoot.height}\ndialogHeightPx=${dialogOwner.view.height}" +
                "\ntextHeightPx=${layout.size.height}\nviewport=${content.boundsInRoot}" +
                "\nscroll=${range.value()}/${range.maxValue()}"
        File(reviewDirectory(), "observation-platform-window.txt").writeText(evidence)
        assertFalse(hostOwner.view === dialogOwner.view)
        assertEquals("宿主平台字体", 2f, hostConfiguration.fontScale, 0.001f)
        assertEquals("Dialog 平台字体", 2f, dialogConfiguration.fontScale, 0.001f)
        assertEquals("宿主布局字体", 2f, hostDensity.fontScale, 0.001f)
        assertEquals("Dialog 布局字体", 2f, dialogDensity.fontScale, 0.001f)
        assertEquals("观测节点字体", 2f, text.layoutInfo.density.fontScale, 0.001f)
        assertEquals("观测实际文本字体", 2f, layout.layoutInput.density.fontScale, 0.001f)
        assertEquals(320, hostConfiguration.screenWidthDp)
        assertEquals(320, dialogConfiguration.screenWidthDp)
        assertEquals(600, dialogConfiguration.screenHeightDp)
        assertEquals(hostDensity.density, dialogDensity.density, 0.001f)
        assertEquals("宿主实际宽度", 320f, host.boundsInRoot.width / hostDensity.density, 1f)
        assertTrue("Dialog 实际宽度", dialogOwner.view.width / dialogDensity.density <= 321f)
        return evidence
    }

    private fun openPanel() {
        composeRule.onNodeWithTag(OPEN_TAG).assertIsDisplayed().performClick()
        composeRule.onNode(isDialog()).assertExists()
    }

    private fun project(
        raw: String,
        id: String = "historical-reply",
        revision: Long = 1,
        snapshot: CompanionPresentationSnapshot = observationSnapshot(),
    ): ParsedCompanionReply {
        val message = ChatMessage(
            id = id,
            conversationId = "native-observation-conversation",
            role = MessageRole.ASSISTANT,
            content = raw,
            speakerId = "native-observation-character",
            speakerName = "合成测试角色",
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            memoryRevision = revision,
            companionTurnId = "turn-$id",
            companionMemoryState = CompanionMemoryState.READY,
        )
        val metadata = CompanionReplyMetadata(
            messageId = message.id,
            conversationId = message.conversationId,
            turnId = message.companionTurnId,
            sourceRevision = message.memoryRevision,
            rawHash = companionRawHash(raw),
            speakerId = message.speakerId,
            outputFormat = message.roleplayOutputFormat,
            interactionMode = message.roleplayInteractionMode,
            selectedAttemptId = "attempt-$id-$revision",
            protocolVersion = 1,
            presentation = snapshot,
        )
        return requireNotNull(CompanionReplyProjector.project(message.copy(companionReplyMetadata = metadata)))
    }

    private fun observedRaw(): String =
        "<observe version=\"1\"><field id=\"time\">傍晚</field>" +
                "<field id=\"obs_coat\">蓝色外套</field></observe>" +
                "<narration>角色在窗边停下。</narration><dialogue>陪你坐一会儿。</dialogue>"

    private fun observationSnapshot(
        timeLabel: String = "时间",
        coatLabel: String = "衣着",
    ): CompanionPresentationSnapshot = CompanionPresentationSnapshot(
        sourceSettingsRevision = 7,
        observation = CompanionObservationSettings(
            enabled = true,
            fields = companionBuiltinObservationFields().map {
                if (it.id == "time") it.copy(label = timeLabel) else it
            },
            customFields = listOf(CompanionObservationField("obs_coat", coatLabel)),
        ),
    )

    private fun captureDialogCandidate(name: String, evidence: String) {
        val directory = reviewDirectory()
        val bitmap = composeRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.txt").writeText(evidence)
    }

    private fun reviewDirectory(): File =
        File(composeRule.activity.cacheDir, "companion-d3-ui-review").also {
            check(it.isDirectory || it.mkdirs())
        }

    private class Fixture(reply: ParsedCompanionReply) {
        val reply: MutableState<ParsedCompanionReply?> = mutableStateOf(reply)
        val enabled = mutableStateOf(true)
        var backCalls = 0
    }

    companion object {
        private const val OPEN_TAG = "companion_observation_open"
        private const val BODY_TAG = "companion_observation_native_body"
        private const val HOST_TAG = "companion_observation_native_host"
    }
}
