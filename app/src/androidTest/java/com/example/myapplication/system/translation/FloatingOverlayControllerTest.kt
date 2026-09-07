package com.example.myapplication.system.translation

import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.ui.theme.resolveAppColorScheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class FloatingOverlayControllerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: FloatingOverlayController
    private lateinit var windows: TrackedWindowManager
    private val callback = RecordingCallback()
    private val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.dismissAll()
            producerScope.cancel()
        }
    }

    @Test
    fun permissionMissing_noWindowIsAddedAndNoPermissionIsRequested() {
        createController(permission = false)
        composeRule.runOnIdle {
            controller.showFloatingBall(1f, 0.5f)
            controller.showSelectedTextAction("Hello", "测试应用")
            controller.showLoadingPanel("测试", "Hello", "简体中文", true)
            showResult()
            controller.showErrorPanel("测试", "", "请先授权悬浮窗", "简体中文", false, false)
            assertTrue(windows.views.isEmpty())
            assertEquals(0, callback.clickCount)
        }
    }

    @Test
    fun loading_closeCancelsProducer_andLateResultDoesNotReopenWindow() {
        createController()
        val result = CompletableDeferred<String>()
        lateinit var producer: Job
        composeRule.runOnIdle {
            producer = producerScope.launch {
                controller.showLoadingPanel("测试应用", "Hello", "简体中文", true)
                showResult(result.await())
            }
            callback.dismissAction = { producer.cancel() }
        }
        waitForLayout()
        composeRule.runOnIdle {
            assertFalse(find<Button>("panel-copy").isEnabled)
            assertFalse(find<Button>("panel-language").isEnabled)
            assertTouchTarget(find("panel-close"))
            assertTouchTarget(find("panel-retry"))
            capture("overlay-loading")
            find<View>("panel-close").performClick()
            assertTrue(producer.isCancelled)
            assertTrue(windows.views.isEmpty())
            result.complete("迟到的译文")
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(windows.views.isEmpty())
            assertEquals(1, callback.dismissCount)
        }
    }

    @Test
    fun result_copyUsesTranslation_languageAndRetryDispatchOnce() {
        createController()
        composeRule.runOnIdle { showResult("你好，世界。") }
        waitForLayout()
        composeRule.runOnIdle {
            assertTouchTarget(find("panel-copy"))
            capture("overlay-result-light")
            find<Button>("panel-copy").performClick()
            val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("你好，世界。", clipboard.primaryClip?.getItemAt(0)?.text.toString())
            find<Button>("panel-language").performClick()
        }
        waitForLayout()
        composeRule.runOnIdle {
            val language = find<Button>("panel-language-日语")
            assertTouchTarget(language)
            language.performClick()
            assertEquals("日语", callback.language)
            showResult()
            find<Button>("panel-retry").performClick()
            assertFalse(find<Button>("panel-retry").isEnabled)
            assertEquals(1, callback.retryCount)
        }
    }

    @Test
    fun error_isNotCopyable_andCanBeDismissedOrRetried() {
        createController(dark = true)
        composeRule.runOnIdle {
            controller.showErrorPanel("测试应用", "Hello", "连接暂时不可用，请稍后重试。", "简体中文", true, true)
        }
        waitForLayout()
        composeRule.runOnIdle {
            assertEquals(View.GONE, find<Button>("panel-copy").visibility)
            assertEquals("暂未完成", find<TextView>("panel-title").text.toString())
            assertEquals(View.GONE, find<View>("panel-progress").visibility)
            capture("overlay-error-dark")
            find<Button>("panel-retry").performClick()
            assertEquals(1, callback.retryCount)
            find<View>("panel-close").performClick()
            assertTrue(windows.views.isEmpty())
        }
    }

    @Test
    fun selectedText_confirmOrDismiss_removesOnlySelectionPrompt() {
        createController()
        composeRule.runOnIdle {
            controller.showFloatingBall(1f, 0.8f)
            controller.showSelectedTextAction("This is selected text in another application.", "阅读器")
        }
        waitForLayout()
        composeRule.runOnIdle {
            assertTouchTarget(find("selected-translate"))
            assertTouchTarget(find("selected-close"))
            capture("overlay-selection", "selected-text-action")
            find<View>("selected-close").performClick()
            assertEquals(0, callback.selectionCount)
            assertEquals(1, windows.views.size)
            controller.showSelectedTextAction("Hello", "阅读器")
            find<Button>("selected-translate").performClick()
            assertEquals(1, callback.selectionCount)
            assertEquals("translation-ball", windows.views.single().tag)
        }
    }

    @Test
    fun floatingBall_longPressHidesWithoutAlsoTranslating_dragStaysOnScreen() {
        createController()
        composeRule.runOnIdle { controller.showFloatingBall(0f, 0.5f) }
        waitForLayout()
        var downTime = 0L
        composeRule.runOnIdle {
            val ball = find<View>("translation-ball")
            assertTouchTarget(ball)
            downTime = SystemClock.uptimeMillis()
            dispatch(ball, MotionEvent.ACTION_DOWN, downTime, 24f, 24f)
            dispatch(ball, MotionEvent.ACTION_MOVE, downTime, 4000f, -4000f)
            dispatch(ball, MotionEvent.ACTION_UP, downTime, 4000f, -4000f)
            assertEquals(0, callback.clickCount)
            assertEquals(1f to 0f, callback.position)
        }
        composeRule.runOnIdle {
            downTime = SystemClock.uptimeMillis()
            dispatch(find("translation-ball"), MotionEvent.ACTION_DOWN, downTime, 24f, 24f)
        }
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150L)
        composeRule.runOnIdle {
            assertEquals(1, callback.hideCount)
            assertEquals(0, callback.clickCount)
            assertTrue(windows.views.isEmpty())
        }
    }

    @Test
    fun largeFont_shortWindow_keepsActionsVisibleAndBodyAndHelpScrollable() {
        createController(dark = true, fontScale = 1.6f, shortWindow = true)
        composeRule.runOnIdle { showResult("这是一段很长的翻译，正文应在面板内滚动。\n".repeat(30)) }
        waitForLayout()
        composeRule.runOnIdle {
            assertTouchTarget(find("panel-copy"))
            assertTouchTarget(find("panel-close"))
            val scroll = find<ScrollView>("panel-scroll")
            assertTrue(scroll.canScrollVertically(1))
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            assertTrue(scroll.canScrollVertically(-1))
            assertFalse(scroll.canScrollVertically(1))
            capture("overlay-dark-large-font-compact")
            find<View>("panel-help").performClick()
        }
        waitForLayout()
        composeRule.runOnIdle {
            val scroll = find<ScrollView>("panel-scroll")
            assertEquals("帮助应从第一段开始", 0, scroll.scrollY)
            assertTrue(scroll.canScrollVertically(1))
            assertTouchTarget(find("panel-copy"))
            assertEquals("知道了", find<Button>("panel-copy").text.toString())
            capture("overlay-help-dark-large-font")
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            assertTrue(scroll.canScrollVertically(-1))
            assertFalse(scroll.canScrollVertically(1))
            val help = find<TextView>("panel-help-text")
            val visibleHelp = Rect()
            assertTrue(help.getLocalVisibleRect(visibleHelp))
            assertEquals("帮助最后一段可达", help.height, visibleHelp.bottom)
            assertTouchTarget(find("panel-copy"))
            find<Button>("panel-copy").performClick()
            assertEquals("翻译结果", find<TextView>("panel-title").text.toString())
        }
        waitForLayout()
        composeRule.runOnIdle {
            assertEquals("返回译文从顶部开始", 0, find<ScrollView>("panel-scroll").scrollY)
        }
    }

    @Test
    fun liveThemeChanges_useSharedAppColorsForEveryThemeAndPreserveResult() {
        createController()
        composeRule.runOnIdle { showResult() }
        waitForLayout()
        AppColorTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                composeRule.runOnIdle {
                    val scheme = resolveAppColorScheme(theme, dark)
                    controller.updateTheme(scheme)
                    assertEquals(scheme.onSurface.toArgb(), find<TextView>("panel-result").currentTextColor)
                    assertEquals("你好", find<TextView>("panel-result").text.toString())
                }
                waitForLayout()
                composeRule.runOnIdle {
                    capture("overlay-${theme.name.lowercase()}-${if (dark) "dark" else "light"}")
                }
            }
        }
    }

    private fun createController(permission: Boolean = true, dark: Boolean = false, fontScale: Float = 1f, shortWindow: Boolean = false) {
        composeRule.setContent { ChatAppTheme(darkTheme = dark) { Box(Modifier.fillMaxSize()) } }
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val context = activity.createConfigurationContext(Configuration(activity.resources.configuration).apply {
                this.fontScale = fontScale
            })
            windows = TrackedWindowManager(activity.windowManager, if (shortWindow) context.overlayDp(320) else null)
            controller = FloatingOverlayController(
                context, callback, windows,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                activity.window.decorView.windowToken,
                canShowOverlays = { permission },
            )
            controller.updateTheme(resolveAppColorScheme(AppColorTheme.MATCHA, dark))
        }
    }

    private fun showResult(translation: String = "你好") {
        controller.showTranslationResult(
            appLabel = "阅读器中的测试内容",
            sourceText = "Hello world. Original text remains available.",
            translatedText = translation,
            targetLanguage = "简体中文",
            availableLanguages = listOf("简体中文", "英语", "日语"),
            showSourceText = true,
            allowRetranslate = true,
        )
    }

    private fun waitForLayout() {
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { windows.views.isNotEmpty() && windows.views.all { it.width > 0 && it.height > 0 && !it.isLayoutRequested } }
    }

    private fun <T : View> find(tag: String): T = windows.views.firstNotNullOf { it.findViewWithTag<T>(tag) }

    private fun assertTouchTarget(view: View) {
        val minimum = view.context.overlayDp(48)
        assertTrue("${view.tag} width", view.width >= minimum)
        assertTrue("${view.tag} height", view.height >= minimum)
        val visible = Rect()
        assertTrue("${view.tag} visible", view.getGlobalVisibleRect(visible))
        assertEquals("${view.tag} unclipped height", view.height, visible.height())
    }

    private fun capture(name: String, tag: String = "translation-panel") {
        val view = find<View>(tag)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File(composeRule.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun dispatch(view: View, action: Int, downTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private class TrackedWindowManager(
        private val delegate: WindowManager,
        private val shortHeight: Int?,
    ) : WindowManager by delegate {
        val views = mutableListOf<View>()
        override fun addView(view: View, params: ViewGroup.LayoutParams) {
            delegate.addView(view, params)
            views += view
        }
        override fun removeViewImmediate(view: View) {
            delegate.removeViewImmediate(view)
            views -= view
        }
        override fun getCurrentWindowMetrics(): WindowMetrics {
            val actual = delegate.currentWindowMetrics
            return if (shortHeight == null) actual else WindowMetrics(
                Rect(0, 0, actual.bounds.width(), shortHeight),
                actual.windowInsets,
            )
        }
    }

    private class RecordingCallback : FloatingOverlayController.Callback {
        var clickCount = 0
        var hideCount = 0
        var selectionCount = 0
        var retryCount = 0
        var dismissCount = 0
        var language = ""
        var position: Pair<Float, Float>? = null
        var dismissAction: () -> Unit = {}
        override fun onFloatingBallClicked() { clickCount++ }
        override fun onFloatingBallHidden() { hideCount++ }
        override fun onFloatingBallPositionChanged(normalizedX: Float, normalizedY: Float) { position = normalizedX to normalizedY }
        override fun onSelectedTextTranslationConfirmed() { selectionCount++ }
        override fun onTargetLanguageSelected(language: String) { this.language = language }
        override fun onRetranslateLast() { retryCount++ }
        override fun onTranslationPanelDismissed() { dismissCount++; dismissAction() }
    }
}
