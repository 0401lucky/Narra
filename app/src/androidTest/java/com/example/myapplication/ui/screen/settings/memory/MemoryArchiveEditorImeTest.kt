package com.example.myapplication.ui.screen.settings.memory

import android.graphics.Bitmap
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.MemoryArchiveEditorUi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 隔离纯页面 + 真实系统 IME，不创建 AppGraph、用户数据库或模型。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class MemoryArchiveEditorImeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longId200PercentWithKeyboard_keepsEditableBodyAndSaveAboveIme() {
        val editor = mutableStateOf(
            MemoryArchiveEditorUi(
                MemoryMaterialKind.RECORD,
                "00000000-0000-4000-8000-000000000001",
                123456789012345,
                "第一段\n\n" + "完整原文保留。".repeat(30)
            )
        )
        var saves = 0
        val callbacks = editorCallbacks(onUpdate = { editor.value = it }, onSave = { saves++ })
        composeRule.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            ChatAppTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        2f
                    )
                ) {
                    Box(Modifier.fillMaxHeight().widthIn(max = 320.dp)) {
                        MemoryArchiveEditorScreen(
                            editor.value,
                            false,
                            callbacks,
                            remember { SnackbarHostState() })
                    }
                }
            }
        }
        val field = composeRule.onNodeWithTag("memory_editor_content")
        field.performScrollTo().performClick()
        val window = composeRule.activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        try {
            composeRule.runOnIdle { controller.show(WindowInsetsCompat.Type.ime()) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                window.decorView.rootWindowInsets?.isVisible(
                    WindowInsets.Type.ime()
                ) == true
            }
            field.performTextReplacement("第一段已修改\n\n第二段仍保留")
            field.assertIsDisplayed()
            val save = composeRule.onNodeWithTag("memory_editor_save")
            save.assertIsDisplayed()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                val ime =
                    window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom
                        ?: 0
                val imeTop =
                    composeRule.activity.windowManager.currentWindowMetrics.bounds.height() - ime
                ime > 0 && save.fetchSemanticsNode().boundsInWindow.bottom <= imeTop + 1f
            }
            val node = save.fetchSemanticsNode()
            val minimum = with(composeRule.density) { 48.dp.toPx() }
            assertTrue(node.touchBoundsInRoot.width >= minimum && node.touchBoundsInRoot.height >= minimum)
            composeRule.runOnIdle {
                val ime =
                    window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
                assertTrue("必须实际显示系统键盘", ime > 0)
                val imeTop =
                    composeRule.activity.windowManager.currentWindowMetrics.bounds.height() - ime
                assertTrue(
                    "保存按钮不可被键盘遮挡：${node.boundsInWindow} / $imeTop",
                    node.boundsInWindow.bottom <= imeTop + 1f
                )
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = File(
                requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
                "c6-ui-probes"
            ).apply { mkdirs() }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory, "editor-long-id-200-ime.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "editor-long-id-200-ime-semantics.txt").writeText(
                composeRule.onRoot().printToString()
            )
            save.performClick()
            composeRule.runOnIdle {
                assertEquals(
                    1,
                    saves
                ); assertEquals("第一段已修改\n\n第二段仍保留", editor.value.content)
            }
        } finally {
            composeRule.runOnIdle { controller.hide(WindowInsetsCompat.Type.ime()) }
        }
    }

    private fun editorCallbacks(onUpdate: (MemoryArchiveEditorUi) -> Unit, onSave: () -> Unit) =
        MemoryArchiveCallbacks(
            onSelectSpace = {},
            onSelectPersona = {},
            onSetPanel = {},
            onSetQuery = {},
            onReload = {},
            onLoadMore = {},
            onOpenItem = {},
            onCloseDetail = {},
            onSelectRevision = {},
            onAdd = {},
            onCreateDailyArchive = {},
            onEdit = {},
            onUpdateEditor = onUpdate,
            onCloseEditor = {},
            onSaveEditor = onSave,
            onTogglePinned = {},
            onSetArchived = {},
            onDelete = {},
            onEndState = {},
            onLinkRecords = {},
            onLoadLinkCandidates = { _, _ -> },
            onUnlinkMember = {},
            onRebuildEvent = {},
            onRebuildIndex = {},
            onSetSpaceArchived = {},
            onDeleteSpace = {},
            onSelectConversation = {},
            onSelectMessage = {},
            onLoadOlderMessages = {},
            onToggleSelection = {},
            onInspectSelection = {},
            onProcessSelection = { _, _ -> },
            onResumeJob = {},
            onSetPaused = { _, _ -> },
            onLoadMoreJobs = {},
            onCancelWork = {},
            onConsumeMessage = {},
            onReadOriginal = { _, _ -> },
        )
}
