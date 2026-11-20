package com.example.myapplication.ui.screen.roleplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionMemoryState
import com.example.myapplication.model.CompanionObservationSettings
import com.example.myapplication.model.CompanionPresentationSnapshot
import com.example.myapplication.model.CompanionReplyMetadata
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ParsedCompanionReply
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.companionRawHash
import com.example.myapplication.roleplay.companion.CompanionBeatPlayer
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal typealias CompanionD4ComposeRule =
        AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

internal const val COMPANION_D4_HOST_TAG = "companion_d4_native_host"
internal const val COMPANION_D4_CHARACTER_ID = "d4-native-character"
internal const val COMPANION_D4_SCENARIO_ID = "d4-native-current-scene"
internal const val COMPANION_D4_CHARACTER_NAME = "合成测试角色"

/** 只用于这三个 D4 原生测试；窗口做法来自已在设备验证的 D3 观测夹具。 */
internal class CompanionD4UiFixture(private val rule: CompanionD4ComposeRule) : AutoCloseable {
    private val ownedImages = mutableListOf<File>()
    private val imageDirectory by lazy {
        File(rule.activity.cacheDir, "d4-ui-fixtures/" + UUID.randomUUID()).also {
            check(it.isDirectory || it.mkdirs())
        }
    }

    fun render(content: @Composable () -> Unit) {
        rule.setContent {
            ChatAppTheme {
                Box(Modifier.fillMaxSize().testTag(COMPANION_D4_HOST_TAG)) { content() }
            }
        }
    }

    fun withNarrowPlatformWindow(
        name: String,
        content: @Composable () -> Unit,
        block: () -> Unit,
    ) {
        val activity = rule.activity
        val originalConfiguration = Configuration(activity.resources.configuration)
        val originalWidth = activity.window.attributes.width
        val originalHeight = activity.window.attributes.height
        val originalSoftInputMode = activity.window.attributes.softInputMode
        var isolatedView: ComposeView? = null
        try {
            rule.runOnUiThread {
                // Dialog/Popup 从 Android Context 重建 density，不能只覆盖 LocalDensity。
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
                    setContent {
                        ChatAppTheme {
                            Box(
                                Modifier.width(320.dp).height(600.dp)
                                    .testTag(COMPANION_D4_HOST_TAG),
                            ) { content() }
                        }
                    }
                }
                activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                activity.window.setLayout(
                    (320f * density).roundToInt(),
                    (600f * density).roundToInt(),
                )
                activity.setContentView(view)
            }
            rule.waitForIdle()
            block()
        } finally {
            rule.runOnUiThread {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
                // 先释放 Composition 和独立窗口，再恢复宿主；基础 Resources 始终不写入。
                isolatedView?.disposeComposition()
                isolatedView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                activity.window.setLayout(originalWidth, originalHeight)
                // Window.setSoftInputMode(0) 只清除显式设置标记，不会将 attrs 的 16 写回 0。
                // 先恢复原始 LayoutParams，再调用 setter 恢复 UNSPECIFIED 的平台语义。
                activity.window.attributes = activity.window.attributes.apply {
                    softInputMode = originalSoftInputMode
                }
                activity.window.setSoftInputMode(originalSoftInputMode)
                val restored = activity.resources.configuration
                File(candidateDirectory(), name + "-platform-restored.txt").writeText(
                    "baseFontScaleBefore=" + originalConfiguration.fontScale +
                            "\nbaseFontScaleAfter=" + restored.fontScale +
                            "\nwindowWidthBefore=" + originalWidth +
                            "\nwindowWidthAfter=" + activity.window.attributes.width +
                            "\nwindowHeightBefore=" + originalHeight +
                            "\nwindowHeightAfter=" + activity.window.attributes.height +
                            "\nsoftInputModeBefore=" + originalSoftInputMode +
                            "\nsoftInputModeAfter=" + activity.window.attributes.softInputMode,
                )
                assertEquals(originalWidth, activity.window.attributes.width)
                assertEquals(originalHeight, activity.window.attributes.height)
                assertEquals(originalSoftInputMode, activity.window.attributes.softInputMode)
                assertEquals(originalConfiguration.fontScale, restored.fontScale, 0.001f)
                assertEquals(originalConfiguration.screenWidthDp, restored.screenWidthDp)
                assertEquals(originalConfiguration.screenHeightDp, restored.screenHeightDp)
            }
        }
    }

    fun textLayout(text: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue("必须读取生产 Text 的实际布局", it(layouts))
        }
        return layouts.single()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun assertNarrowPlatformText(
        text: SemanticsNodeInteraction,
        window: SemanticsNodeInteraction? = null,
    ): String {
        val host = rule.onNodeWithTag(COMPANION_D4_HOST_TAG).fetchSemanticsNode()
        val textNode = text.fetchSemanticsNode()
        val hostOwner = host.root as? ViewRootForTest ?: error("宿主必须拥有 Android View")
        val textOwner = textNode.root as? ViewRootForTest ?: error("文字必须拥有 Android View")
        val layout = textLayout(text)
        val hostConfiguration = hostOwner.view.resources.configuration
        val textConfiguration = textOwner.view.resources.configuration
        val evidence = "hostPlatformFontScale=" + hostConfiguration.fontScale +
                "\ntextPlatformFontScale=" + textConfiguration.fontScale +
                "\nhostLayoutFontScale=" + host.layoutInfo.density.fontScale +
                "\ntextNodeFontScale=" + textNode.layoutInfo.density.fontScale +
                "\ntextLayoutFontScale=" + layout.layoutInput.density.fontScale +
                "\nhostScreenWidthDp=" + hostConfiguration.screenWidthDp +
                "\ntextScreenWidthDp=" + textConfiguration.screenWidthDp +
                "\nhostWidthPx=" + host.boundsInRoot.width +
                "\nhostDensity=" + host.layoutInfo.density.density +
                "\ntextHeightPx=" + layout.size.height +
                "\ntextOwner=" + textOwner.view.javaClass.name
        assertEquals("宿主平台字体", 2f, hostConfiguration.fontScale, 0.001f)
        assertEquals("文字平台字体", 2f, textConfiguration.fontScale, 0.001f)
        assertEquals(2f, host.layoutInfo.density.fontScale, 0.001f)
        assertEquals(2f, textNode.layoutInfo.density.fontScale, 0.001f)
        assertEquals(2f, layout.layoutInput.density.fontScale, 0.001f)
        assertEquals(320, hostConfiguration.screenWidthDp)
        assertEquals(320, textConfiguration.screenWidthDp)
        assertEquals(320f, host.boundsInRoot.width / host.layoutInfo.density.density, 1f)
        window?.let {
            val windowNode = it.fetchSemanticsNode()
            val windowOwner = windowNode.root as? ViewRootForTest
                ?: error("覆盖层必须拥有 Android View")
            assertFalse("覆盖层不能冒用 Activity 像素", hostOwner.view === windowOwner.view)
            assertTrue(windowOwner.view === textOwner.view)
            assertEquals(2f, windowOwner.view.resources.configuration.fontScale, 0.001f)
        }
        return evidence
    }

    fun assertTouchTarget(
        target: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction? = null,
    ) {
        val node = target.fetchSemanticsNode()
        val touch = node.touchBoundsInRoot
        val density = node.layoutInfo.density.density
        assertTrue("真实触控宽度至少 48dp：" + touch, touch.width / density >= 48f - 0.5f)
        assertTrue("真实触控高度至少 48dp：" + touch, touch.height / density >= 48f - 0.5f)
        viewport?.let {
            val bounds = it.fetchSemanticsNode().boundsInRoot
            assertTrue("操作完整位于视口内", touch.left >= bounds.left - 1f)
            assertTrue("操作完整位于视口内", touch.right <= bounds.right + 1f)
            assertTrue("操作完整位于视口内", touch.top >= bounds.top - 1f)
            assertTrue("操作完整位于视口内", touch.bottom <= bounds.bottom + 1f)
        }
    }

    fun scrollToEnd(scrollable: SemanticsNodeInteraction): String {
        val range =
            scrollable.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("长内容必须真实溢出视口", range.maxValue() > 0f)
        scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
            assertTrue(it(0f, 100_000f))
        }
        rule.waitForIdle()
        assertEquals(range.maxValue(), range.value(), 1f)
        return "scroll=" + range.value() + "/" + range.maxValue()
    }

    fun assertCompleteTextInViewport(
        text: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction,
    ): String {
        val node = text.fetchSemanticsNode()
        val layout = textLayout(text)
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val firstLineTop = node.positionInRoot.y + layout.getLineTop(0)
        val lastLineBottom = node.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1)
        assertFalse("末项不允许省略", layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue("末项首行完整进入视口", firstLineTop >= bounds.top - 1f)
        assertTrue("末项末行完整进入视口", lastLineBottom <= bounds.bottom + 1f)
        return "firstLineTop=" + firstLineTop + "\nlastLineBottom=" + lastLineBottom +
                "\nviewport=" + bounds
    }

    fun assertTextSuffixInViewport(
        text: SemanticsNodeInteraction,
        suffix: String,
        viewport: SemanticsNodeInteraction,
    ): String {
        val node = text.fetchSemanticsNode()
        val layout = textLayout(text)
        val start = layout.layoutInput.text.text.indexOf(suffix)
        assertTrue("末段必须来自原始测试正文", start >= 0)
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val top = node.positionInRoot.y + layout.getLineTop(layout.getLineForOffset(start))
        val bottom = node.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1)
        assertTrue("末段首行完整进入视口", top >= bounds.top - 1f)
        assertTrue("末段末行完整进入视口", bottom <= bounds.bottom + 1f)
        return "lastParagraphTop=" + top + "\nlastParagraphBottom=" + bottom + "\nviewport=" + bounds
    }

    fun assertOneCompleteTextLine(
        text: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction,
    ): String {
        val node = text.fetchSemanticsNode()
        val layout = textLayout(text)
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val line = (0 until layout.lineCount).firstOrNull {
            !layout.isLineEllipsized(it) &&
                    node.positionInRoot.y + layout.getLineTop(it) >= bounds.top - 1f &&
                    node.positionInRoot.y + layout.getLineBottom(it) <= bounds.bottom + 1f
        }
        assertTrue("必须至少有一整行实际正文进入视口，不能仅以正高度通过：" + bounds, line != null)
        val index = requireNotNull(line)
        return "completeLine=" + index +
                "\nlineTop=" + (node.positionInRoot.y + layout.getLineTop(index)) +
                "\nlineBottom=" + (node.positionInRoot.y + layout.getLineBottom(index)) +
                "\nviewport=" + bounds
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun captureCandidate(name: String, root: SemanticsNodeInteraction, evidence: String) {
        val node = root.fetchSemanticsNode()
        val owner = node.root as? ViewRootForTest ?: error("候选必须来自真实 Android View")
        val view = owner.view
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        try {
            File(candidateDirectory(), name + ".png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(candidateDirectory(), name + ".txt").writeText(
                evidence + "\nowner=" + view.javaClass.name +
                        "\nwidthPx=" + view.width + "\nheightPx=" + view.height,
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun image(name: String, color: Int, width: Int = 64, height: Int = 64): File {
        val file = File(imageDirectory, name + ".png")
        ownedImages += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream()
                .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun showIme() {
        rule.runOnUiThread {
            WindowInsetsControllerCompat(rule.activity.window, rule.activity.window.decorView)
                .show(WindowInsetsCompat.Type.ime())
        }
        rule.waitUntil(5_000) { imeVisible() }
    }

    override fun close() {
        ownedImages.forEach { if (it.exists()) assertTrue(it.delete()) }
    }

    private fun candidateDirectory(): File =
        File(rule.activity.cacheDir, "d4-candidates").also { check(it.isDirectory || it.mkdirs()) }
}

/** 生成合法的 D3 来源快照；不是 Room/授权或真实模型的替代证据。 */
internal fun companionD4Message(
    raw: String,
    id: String = "d4-native-reply",
    revision: Long = 1L,
    conversationId: String = "d4-native-current-conversation",
    createdAt: Long = 1_700_000_000_000L,
    observation: Boolean = true,
    presentationSnapshot: CompanionPresentationSnapshot? = null,
): ChatMessage {
    val message = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = MessageRole.ASSISTANT,
        content = raw,
        speakerId = COMPANION_D4_CHARACTER_ID,
        speakerName = COMPANION_D4_CHARACTER_NAME,
        roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
        roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        createdAt = createdAt,
        memoryRevision = revision,
        companionTurnId = "turn-" + id,
        companionMemoryState = CompanionMemoryState.READY,
    )
    return message.copy(
        companionReplyMetadata = CompanionReplyMetadata(
            messageId = message.id,
            conversationId = message.conversationId,
            turnId = message.companionTurnId,
            sourceRevision = message.memoryRevision,
            rawHash = companionRawHash(raw),
            speakerId = message.speakerId,
            outputFormat = message.roleplayOutputFormat,
            interactionMode = message.roleplayInteractionMode,
            selectedAttemptId = "attempt-" + id + "-" + revision,
            protocolVersion = 1,
            presentation = presentationSnapshot ?: CompanionPresentationSnapshot(
                sourceSettingsRevision = 7,
                observation = CompanionObservationSettings(enabled = observation),
            ),
        ),
    )
}

internal fun companionD4Reply(message: ChatMessage): ParsedCompanionReply =
    requireNotNull(CompanionReplyProjector.project(message))

internal fun companionD4Settings(): AppSettings = AppSettings(
    assistants = listOf(
        Assistant(
            id = COMPANION_D4_CHARACTER_ID,
            name = COMPANION_D4_CHARACTER_NAME
        )
    ),
    selectedAssistantId = COMPANION_D4_CHARACTER_ID,
)

/** 保留旧回调直到显式放行，验证已经取消的 timer 到达时仍不能修改新状态。 */
internal class CompanionD4Player : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ticks = ArrayDeque<Continuation<Unit>>()
    val player = CompanionBeatPlayer(scope, delayCharacter = {
        suspendCoroutine { continuation -> synchronized(ticks) { ticks.addLast(continuation) } }
    })

    fun tick() {
        val continuation = synchronized(ticks) { ticks.pollFirst() }
            ?: error("播放器尚未等待下一次打字时钟")
        continuation.resume(Unit)
    }

    override fun close() {
        player.close()
        scope.cancel()
        while (true) {
            val continuation = synchronized(ticks) { ticks.pollFirst() } ?: break
            continuation.resume(Unit)
        }
    }
}

internal class CompanionD4PickerRegistry : ActivityResultRegistry() {
    data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    val launches = mutableListOf<Launch>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += Launch(requestCode, contract, input)
    }
}
