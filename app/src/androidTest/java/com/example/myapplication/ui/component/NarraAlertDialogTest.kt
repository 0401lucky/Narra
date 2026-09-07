package com.example.myapplication.ui.component

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NarraAlertDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longContent_largeFont_keepsActionsVisibleAndAllowsLastOption() {
        var selectedOption by mutableStateOf<Int?>(null)
        var confirmedOption: Int? = null
        var confirmCalls = 0
        var dismissCalls = 0
        val confirmLabel = "确认并保留所选内容"
        val dismissLabel = "取消并返回"
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                ChatAppTheme(darkTheme = true) {
                    NarraAlertDialog(
                        title = "选择要重新生成的内容",
                        message = "请确认需要更新的内容范围。\n".repeat(30),
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .heightIn(max = 520.dp)
                            .testTag("review_dialog"),
                        confirmLabel = confirmLabel,
                        dismissLabel = dismissLabel,
                        onConfirm = {
                            confirmCalls++
                            confirmedOption = selectedOption
                        },
                        onDismiss = { dismissCalls++ },
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..6).forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("dialog_option_$option")
                                            .toggleable(
                                                value = selectedOption == option,
                                                role = Role.Checkbox,
                                                onValueChange = { selectedOption = if (it) option else null },
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = selectedOption == option, onCheckedChange = null)
                                        Text("选项 $option · 保留指定范围内的所有记录", modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText(confirmLabel).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(dismissLabel).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("dialog_option_6").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("dialog_option_6").assertIsOn()
        captureReviewImage("dialog-dark-long-content")
        composeRule.onNodeWithText(confirmLabel).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(6, confirmedOption)
            assertEquals(1, confirmCalls)
            assertEquals(0, dismissCalls)
        }
        composeRule.onNodeWithTag("review_dialog").assertExists()
    }

    @Test
    fun disabledDestructiveConfirm_cannotSubmit_andCancelOnlyDismisses() {
        var visible by mutableStateOf(true)
        var confirmCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            ChatAppTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize())
                if (visible) {
                    NarraAlertDialog(
                        title = "删除上下文日志",
                        confirmLabel = "删除日志",
                        confirmEnabled = false,
                        isDestructive = true,
                        onConfirm = { confirmCalls++ },
                        onDismiss = {
                            dismissCalls++
                            visible = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("删除日志").assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, confirmCalls) }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("删除上下文日志").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, confirmCalls)
            assertEquals(1, dismissCalls)
        }
    }

    @Test
    fun systemBack_onlyDismissesDialog() {
        var visible by mutableStateOf(true)
        var confirmCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                Box(Modifier.fillMaxSize())
                if (visible) {
                    NarraAlertDialog(
                        title = "返回确认",
                        onConfirm = { confirmCalls++ },
                        onDismiss = {
                            dismissCalls++
                            visible = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("返回确认").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("返回确认").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, confirmCalls)
            assertEquals(1, dismissCalls)
        }
    }

    @Test
    fun outsideTap_onlyDismissesDialog() {
        var visible by mutableStateOf(true)
        var confirmCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            ChatAppTheme {
                Box(Modifier.fillMaxSize())
                if (visible) {
                    NarraAlertDialog(
                        title = "外部点击确认",
                        onConfirm = { confirmCalls++ },
                        onDismiss = {
                            dismissCalls++
                            visible = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("外部点击确认").assertIsDisplayed()
        val point = composeRule.runOnIdle {
            val frame = Rect()
            composeRule.activity.window.decorView.getWindowVisibleDisplayFrame(frame)
            val inset = 8 * composeRule.activity.resources.displayMetrics.density
            frame.left + inset to frame.exactCenterY()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(downTime, downTime + index * 32L, action, point.first, point.second, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        composeRule.onNodeWithText("外部点击确认").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, confirmCalls)
            assertEquals(1, dismissCalls)
        }
    }

    private fun captureReviewImage(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = composeRule.onNodeWithTag("review_dialog").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
