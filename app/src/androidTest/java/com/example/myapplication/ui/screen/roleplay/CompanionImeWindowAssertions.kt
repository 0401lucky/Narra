package com.example.myapplication.ui.screen.roleplay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt

/** 取真实编辑 Dialog 的原生 IME/可见区域，不能把底层 Activity 当成键盘窗口证据。 */
@SuppressLint("NewApi")
@OptIn(ExperimentalComposeUiApi::class)
internal fun assertCompanionDialogActionAboveIme(
    rule: ComposeContentTestRule,
    activity: Activity,
    actionText: String,
) {
    val initialAction = rule.onNodeWithText(actionText).fetchSemanticsNode()
    val initialOwner =
        initialAction.root as? ViewRootForTest ?: error("编辑操作必须拥有真实 Android View")
    val root = initialOwner.view.rootView
    assertTrue(
        "编辑操作必须属于独立 Dialog",
        root !== activity.window.decorView && root.hasCompanionDialogLayout()
    )
    assertTrue(
        "编辑窗口必须已挂接",
        root.isAttachedToWindow && root in WindowInspector.getGlobalWindowViews()
    )
    // IME 可见先于原生窗口 resize/Compose 重排完成，必须等待同一操作的实际区域。
    rule.waitUntil(5_000) {
        if (root.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true) return@waitUntil false
        val currentAction = rule.onNodeWithText(actionText).fetchSemanticsNode()
        val currentOwner = currentAction.root as? ViewRootForTest ?: return@waitUntil false
        val currentVisible = Rect()
        root.getWindowVisibleDisplayFrame(currentVisible)
        val currentRootPosition = IntArray(2)
        root.getLocationOnScreen(currentRootPosition)
        val currentOwnerPosition = IntArray(2)
        currentOwner.view.getLocationOnScreen(currentOwnerPosition)
        val currentBounds =
            currentAction.layoutInfo.coordinates?.boundsInWindow() ?: return@waitUntil false
        currentRootPosition[1] + currentBounds.bottom <= currentVisible.bottom + 1f &&
                currentOwnerPosition[1] + currentAction.touchBoundsInRoot.bottom <= currentVisible.bottom + 1f
    }
    assertTrue(requireNotNull(root.rootWindowInsets).isVisible(WindowInsets.Type.ime()))
    val action = rule.onNodeWithText(actionText).fetchSemanticsNode()
    val owner = action.root as? ViewRootForTest ?: error("编辑操作必须拥有真实 Android View")
    val visible = Rect()
    root.getWindowVisibleDisplayFrame(visible)
    val rootPosition = IntArray(2)
    root.getLocationOnScreen(rootPosition)
    val ownerPosition = IntArray(2)
    owner.view.getLocationOnScreen(ownerPosition)
    val bounds = requireNotNull(action.layoutInfo.coordinates).boundsInWindow()
    assertTrue(
        "编辑保存操作必须高于原生键盘",
        rootPosition[1] + bounds.bottom <= visible.bottom + 1f
    )
    assertTrue(
        "编辑保存完整触控区域必须高于原生键盘",
        ownerPosition[1] + action.touchBoundsInRoot.bottom <= visible.bottom + 1f
    )
    assertTrue(
        "编辑保存触控高度至少 48dp",
        action.touchBoundsInRoot.height + 0.5f >= 48f * root.resources.displayMetrics.density
    )
    assertTrue(
        "编辑保存触控宽度至少 48dp",
        action.touchBoundsInRoot.width + 0.5f >= 48f * root.resources.displayMetrics.density
    )
}

/** 沿用 D3 的隔离配置宿主；Dialog 从真实 View Context 获得 200% 字体。 */
internal fun withNarrowCompanionWindow(
    rule: ComposeContentTestRule,
    activity: Activity,
    content: @Composable () -> Unit,
    block: () -> Unit,
) {
    val originalConfiguration = Configuration(activity.resources.configuration)
    val originalWidth = activity.window.attributes.width
    val originalHeight = activity.window.attributes.height
    var isolatedView: ComposeView? = null
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    try {
        instrumentation.runOnMainSync {
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
                setContent(content)
            }
            activity.window.setLayout((320f * density).roundToInt(), (600f * density).roundToInt())
            activity.setContentView(view)
        }
        rule.waitForIdle()
        block()
    } finally {
        instrumentation.runOnMainSync {
            isolatedView?.disposeComposition()
            isolatedView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            activity.window.setLayout(originalWidth, originalHeight)
            val restored = activity.resources.configuration
            assertEquals(originalConfiguration.fontScale, restored.fontScale, 0.001f)
            assertEquals(originalConfiguration.screenWidthDp, restored.screenWidthDp)
            assertEquals(originalConfiguration.screenHeightDp, restored.screenHeightDp)
            assertEquals(originalWidth, activity.window.attributes.width)
            assertEquals(originalHeight, activity.window.attributes.height)
        }
    }
}

private fun View.hasCompanionDialogLayout(): Boolean {
    if (javaClass.simpleName == "DialogLayout") return true
    return this is ViewGroup && (0 until childCount).any { getChildAt(it).hasCompanionDialogLayout() }
}
