package com.example.myapplication.ui.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fetchImage
import com.github.takahirom.roborazzi.RoborazziOptions
import androidx.lifecycle.Lifecycle
import java.io.File
import java.time.Duration
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPausedSystemClock
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers

/** P06 共用的真实页面/窗口宿主；时钟只推进 Robolectric shadow，不访问宿主系统时间设置。 */
@Config(instrumentedPackages = ["com.example.myapplication.ui.screen.settings.worldbook"])
abstract class P06RenderTest : ScreenshotStageTest() {
    protected val measurements = linkedMapOf<String, String>()
    protected val actions = mutableListOf<String>()
    private var originalUptimeNanos = 0L
    private var originalRealtimeNanos = 0L
    private lateinit var originalQualifiers: String
    private lateinit var hostActivity: ComponentActivity

    @get:Rule(order = -2)
    val restoreP06Environment = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    if (::hostActivity.isInitialized) {
                        assertEquals(
                            "只能在 Compose 与 Activity 完全释放后恢复虚拟时钟",
                            Lifecycle.State.DESTROYED,
                            hostActivity.lifecycle.currentState
                        )
                        restoreP06Clock()
                    }
                }
            }
        }
    }

    @Before
    fun configureP06Clock() {
        assertEquals(Application::class.java, RuntimeEnvironment.getApplication().javaClass)
        hostActivity = composeRule.activity
        originalQualifiers = RuntimeEnvironment.getQualifiers()
        originalUptimeNanos =
            ReflectionHelpers.getStaticField(ShadowPausedSystemClock::class.java, "currentUptimeNs")
        originalRealtimeNanos = ReflectionHelpers.getStaticField(
            ShadowPausedSystemClock::class.java,
            "currentRealtimeNs"
        )
        ShadowSystemClock.advanceBy(Duration.ofMillis(FixedEpochMillis - SystemClock.uptimeMillis()))
        assertEquals(FixedEpochMillis, SystemClock.uptimeMillis())
        measurements["clockStartMillis"] = SystemClock.uptimeMillis().toString()
    }

    private fun restoreP06Clock() {
        RuntimeEnvironment.setQualifiers(originalQualifiers)
        RuntimeEnvironment.setFontScale(ScreenshotFixture.NormalFontScale)
        // 只恢复测试 shadow 字段，允许回到测试开始前；公开 advanceBy 只接受向前推进。
        ReflectionHelpers.setStaticField(
            ShadowPausedSystemClock::class.java,
            "currentUptimeNs",
            originalUptimeNanos
        )
        ReflectionHelpers.setStaticField(
            ShadowPausedSystemClock::class.java,
            "currentRealtimeNs",
            originalRealtimeNanos
        )
        assertEquals(originalUptimeNanos / 1_000_000, SystemClock.uptimeMillis())
    }

    protected fun renderP06(case: ScreenshotCase, content: @Composable () -> Unit) {
        val narrow = case.fontScale == ScreenshotFixture.NarrowFontScale
        RuntimeEnvironment.setQualifiers(if (narrow) ScreenshotFixture.NarrowQualifiers else ScreenshotFixture.HostQualifiers)
        RuntimeEnvironment.setFontScale(case.fontScale)
        val theme = when {
            "vanilla" in case.id -> AppColorTheme.VANILLA
            "ocean" in case.id -> AppColorTheme.OCEAN
            else -> AppColorTheme.MATCHA
        }
        val dark = case.id.endsWith("dark")
        if ("dynamic" in case.id) {
            setColorSchemeStage(
                case,
                resolveAppColorScheme(theme, dark, lightColorScheme()),
                content
            )
        } else {
            setStage(case, theme, dark, content)
        }
        composeRule.waitForIdle()
    }

    protected fun pageNode(
        matcher: SemanticsMatcher,
        unmerged: Boolean = false
    ): SemanticsNodeInteraction =
        composeRule.onNode(
            matcher and hasAnyAncestor(hasTestTag(StageTag)),
            useUnmergedTree = unmerged
        )

    protected fun pageList(): SemanticsNodeInteraction = pageNode(
        hasScrollToIndexAction() and
                SemanticsMatcher("页面纵向 LazyColumn") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) })

    @OptIn(ExperimentalComposeUiApi::class)
    protected fun clearFocusForCapture(root: SemanticsNodeInteraction) {
        val owner =
            root.fetchSemanticsNode().root as? ViewRootForTest ?: error("窗口必须有独立 View owner")
        composeRule.runOnIdle {
            val manager =
                owner.view.javaClass.getMethod("getFocusOwner").invoke(owner.view) as FocusManager
            manager.clearFocus(force = true)
        }
        composeRule.waitForIdle()
        actions += "clear-window-owner-focus"
    }

    protected fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        pageList().performScrollToNode(matcher)
        return pageNode(matcher).performScrollTo().assertIsDisplayed()
    }

    /** 捕获导航使用生产语义回调；真实触控测试不调用此入口。 */
    protected fun captureClick(node: SemanticsNodeInteraction, action: String) {
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        actions += action
        composeRule.waitForIdle()
    }

    protected fun scrollToEnd(
        scroll: SemanticsNodeInteraction = pageList(),
        name: String = "page"
    ) {
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("实际滚动范围必须有限", range.maxValue().isFinite())
        assertEquals("必须达到真实滚动末尾", range.maxValue(), range.value(), 1f)
        measurements["${name}Scroll"] = "${range.value()}/${range.maxValue()}"
        measurements["${name}Viewport"] = scroll.fetchSemanticsNode().boundsInRoot.toString()
        actions += "scroll-end:$name"
    }

    protected fun dialogMatcher(title: String): SemanticsMatcher =
        isRoot() and hasAnyDescendant(isDialog()) and hasAnyDescendant(hasText(title)) and
                !hasAnyDescendant(hasTestTag(StageTag))

    protected fun popupMatcher(choice: String): SemanticsMatcher =
        isRoot() and hasAnyDescendant(isPopup()) and hasAnyDescendant(hasText(choice)) and
                !hasAnyDescendant(hasTestTag(StageTag))

    protected fun dialogRoot(title: String): SemanticsNodeInteraction =
        composeRule.onNode(dialogMatcher(title), useUnmergedTree = true)
            .assertExists("必须真实打开 $title Dialog，不允许回退到底页")

    protected fun popupRoot(choice: String): SemanticsNodeInteraction =
        composeRule.onNode(popupMatcher(choice), useUnmergedTree = true)
            .assertExists("必须真实打开包含 $choice 的 Popup，不允许回退到底页")

    protected fun windowNode(
        root: SemanticsMatcher,
        matcher: SemanticsMatcher,
        unmerged: Boolean = false
    ): SemanticsNodeInteraction =
        composeRule.onNode(matcher and hasAnyAncestor(root), useUnmergedTree = unmerged)

    protected fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    protected fun recordBounds(
        name: String,
        node: SemanticsNodeInteraction,
        container: SemanticsNodeInteraction = pageList()
    ) {
        val semantics = node.fetchSemanticsNode()
        val dp = node.getUnclippedBoundsInRoot()
        val d = ScreenshotFixture.HostDensity
        val unclipped =
            Rect(dp.left.value * d, dp.top.value * d, dp.right.value * d, dp.bottom.value * d)
        val touch = semantics.touchBoundsInRoot
        val limit = container.fetchSemanticsNode().boundsInRoot
        measurements[name] = touch.toString()
        measurements["${name}Unclipped"] = unclipped.toString()
        measurements["${name}Viewport"] = limit.toString()
        measurements["${name}FullyVisible"] =
            (contains(limit, unclipped) && contains(limit, touch)).toString()
        measurements["${name}Touch48dp"] =
            (touch.width >= 96f - 0.5f && touch.height >= 96f - 0.5f).toString()
    }

    protected fun requireFullyVisible(name: String) {
        assertEquals(
            "末项的未裁切布局和真实触控边界都应完整可达：$name $measurements",
            "true",
            measurements["${name}FullyVisible"]
        )
    }

    protected open fun assertCaptureIsolation() = Unit

    @OptIn(ExperimentalComposeUiApi::class)
    protected fun renderPopupBitmap(root: SemanticsNodeInteraction): Bitmap {
        val semantics = root.fetchSemanticsNode()
        val owner =
            semantics.root as? ViewRootForTest ?: error("Popup 必须拥有真实 Android View root")
        return composeRule.runOnIdle {
            val view = owner.view
            assertTrue(
                "Popup 不能回退到主 Activity owner",
                view.rootView !== hostActivity.window.decorView.rootView
            )
            assertTrue(
                "必须是实际 PopupLayout",
                view.rootView.javaClass.simpleName.contains("PopupLayout")
            )
            assertEquals(semantics.boundsInRoot.width, view.width.toFloat(), 1f)
            assertEquals(semantics.boundsInRoot.height, view.height.toFloat(), 1f)
            measurements["popupViewOwner"] = view.rootView.javaClass.name
            measurements["popupRenderer"] =
                "native Canvas drawing the independent Popup AndroidComposeView"
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                view.draw(Canvas(bitmap))
            }
        }
    }

    protected fun probeRenderedRoot(
        name: String,
        root: SemanticsNodeInteraction,
        popup: Boolean = false
    ) {
        val bitmap = if (popup) renderPopupBitmap(root) else root.fetchSemanticsNode()
            .fetchImage(RoborazziOptions.RecordOptions())
            ?: error("独立窗口原生渲染返回 null：$name")
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val colors = mutableSetOf<Int>()
        for (x in 0 until bitmap.width step 8) {
            for (y in 0 until bitmap.height step 8) colors += bitmap.getPixel(x, y)
        }
        assertTrue("真实窗口像素不能是空白色块：$name", colors.size >= 8)
        val directory = File("build/outputs/p06-capture-probes").apply { mkdirs() }
        File(directory, "$name.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(directory, "$name-semantics.txt").writeText(root.printToString())
        File(
            directory,
            "$name-measurements.txt"
        ).writeText(
            "image=${bitmap.width}x${bitmap.height}\ncolors=${colors.size}\n" + measurements.entries.joinToString(
                "\n"
            ) { "${it.key}=${it.value}" })
    }

    protected fun capturePage(case: ScreenshotCase, visible: SemanticsMatcher) {
        assertCaptureIsolation()
        clearFocusForCapture(composeRule.onNodeWithTag(StageTag))
        pageNode(visible).assertIsDisplayed()
        writeEvidence(case, composeRule.onNodeWithTag(StageTag), "screenshot_stage")
        captureCase(case)
    }

    protected fun captureWindow(
        case: ScreenshotCase,
        titleOrChoice: String,
        operations: List<String>,
        popup: Boolean = false
    ) {
        assertCaptureIsolation()
        assertEquals(case.testClass, javaClass.simpleName)
        val matcher = if (popup) popupMatcher(titleOrChoice) else dialogMatcher(titleOrChoice)
        val root = if (popup) popupRoot(titleOrChoice) else dialogRoot(titleOrChoice)
        root.assertIsDisplayed()
        val bounds = root.fetchSemanticsNode().boundsInRoot
        assertTrue("真实窗口必须有可绘制面积", bounds.width > 0 && bounds.height > 0)
        assertTrue(bounds.width <= case.stageWidthDp * ScreenshotFixture.HostDensity + 1)
        assertTrue(bounds.height <= case.stageHeightDp * ScreenshotFixture.HostDensity + 1)
        operations.forEach { operation ->
            val node = windowNode(matcher, hasText(operation) and hasClickAction())
            node.assertIsDisplayed()
            recordBounds(operation, node, root)
            val layout = textLayout(windowNode(matcher, hasText(operation), unmerged = true))
            assertEquals(case.fontScale, layout.layoutInput.density.fontScale, 0.001f)
            assertEquals(ScreenshotFixture.HostDensity, layout.layoutInput.density.density, 0.001f)
        }
        val target = if (popup) "independent-popup-root" else "independent-dialog-root"
        val popupBitmap = if (popup) renderPopupBitmap(root) else null
        evidenceFile(
            case,
            "window.txt"
        ).writeText("title=$titleOrChoice\ntarget=$target\nfontScale=${case.fontScale}\nbounds=$bounds\n" + root.printToString())
        writeEvidence(case, root, target)
        if (popupBitmap != null) {
            popupBitmap.captureRoboImage(case.relativePath, StrictRoborazziOptions)
        } else {
            root.captureRoboImage(case.relativePath, StrictRoborazziOptions)
        }
    }

    private fun writeEvidence(
        case: ScreenshotCase,
        root: SemanticsNodeInteraction,
        target: String
    ) {
        measurements["captureClockMillis"] = SystemClock.uptimeMillis().toString()
        measurements["locale"] = Locale.getDefault().toLanguageTag()
        measurements["timeZone"] = TimeZone.getDefault().id
        measurements["qualifiers"] = RuntimeEnvironment.getQualifiers()
        measurements["density"] = ScreenshotFixture.HostDensity.toString()
        measurements["fontScale"] = case.fontScale.toString()
        measurements["actions"] = actions.joinToString(" -> ")
        evidenceFile(
            case,
            "semantics.xml"
        ).writeText("<semantics id=\"${case.id}\" fontScale=\"${case.fontScale}\" target=\"$target\"><![CDATA[${root.printToString()}]]></semantics>")
        evidenceFile(
            case,
            "measurements.txt"
        ).writeText(measurements.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun evidenceFile(case: ScreenshotCase, suffix: String): File =
        File("build/outputs/roborazzi/${case.id}-$suffix").apply { parentFile?.mkdirs() }

    private fun contains(limit: Rect, bounds: Rect): Boolean =
        bounds.left >= limit.left - 0.5f && bounds.top >= limit.top - 0.5f &&
                bounds.right <= limit.right + 0.5f && bounds.bottom <= limit.bottom + 0.5f

    companion object {
        const val FixedEpochMillis = ScreenshotFixture.FixedEpochMillis
    }
}
