package com.example.myapplication.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.ui.theme.NarraShapes
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProviderSharedDialogCompatibilityTest : ProviderSettingsRenderTest() {
    @Test
    fun optionalActionColor_keepsDefaultPrimary_andDestructiveConfirmationKeepsErrorPriority() {
        val customized = mutableStateOf(false)
        val destructive = mutableStateOf(false)
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        var onSurface = androidx.compose.ui.graphics.Color.Unspecified
        var error = androidx.compose.ui.graphics.Color.Unspecified
        renderProvider(content = {
            primary = MaterialTheme.colorScheme.primary
            onSurface = MaterialTheme.colorScheme.onSurface
            error = MaterialTheme.colorScheme.error
            if (customized.value) {
                NarraAlertDialog(
                    "动作颜色兼容", onDismiss = {}, onConfirm = {},
                    actionContentColor = onSurface, isDestructive = destructive.value
                )
            } else {
                NarraAlertDialog("动作颜色兼容", onDismiss = {}, onConfirm = {})
            }
        })
        fun foreground(label: String) =
            textLayout(inWindow("动作颜色兼容", hasText(label))).layoutInput.style.color
        assertEquals(primary, foreground("取消"))
        assertEquals(primary, foreground("确认"))
        composeRule.runOnIdle { customized.value = true }
        assertEquals(onSurface, foreground("取消"))
        assertEquals(onSurface, foreground("确认"))
        composeRule.runOnIdle { destructive.value = true }
        assertEquals(onSurface, foreground("取消"))
        assertEquals(error, foreground("确认"))
    }

    @Test
    fun optionalShape_changesOnlyCorners_oldDefaultAndCancelContractRemainIntact() {
        val large = mutableStateOf(false)
        val visible = mutableStateOf(true)
        var dismissed = 0
        var confirmed = 0
        var surface = androidx.compose.ui.graphics.Color.Unspecified
        renderProvider(content = {
            surface = MaterialTheme.colorScheme.surfaceContainerHigh
            if (visible.value) {
                val dismiss = { dismissed++; visible.value = false }
                if (large.value) {
                    NarraAlertDialog(
                        "共享兼容窗口", onDismiss = dismiss, onConfirm = { confirmed++ },
                        modifier = Modifier.testTag("provider_dialog_shape"), message = "合成正文",
                        shape = NarraShapes.large
                    )
                } else {
                    NarraAlertDialog(
                        "共享兼容窗口", onDismiss = dismiss, onConfirm = { confirmed++ },
                        modifier = Modifier.testTag("provider_dialog_shape"), message = "合成正文"
                    )
                }
            }
        })
        val node = composeRule.onNodeWithTag("provider_dialog_shape", useUnmergedTree = true)
        val beforeBounds = unclippedBounds(node)
        val before =
            node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap()
                .toPixelMap()
        assertEquals(surface, before[8, 8])
        composeRule.runOnIdle { large.value = true }
        composeRule.waitForIdle()
        val after =
            node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap()
                .toPixelMap()
        assertEquals(beforeBounds, unclippedBounds(node))
        assertNotEquals(surface, after[8, 8])
        val cancel = windowControl("共享兼容窗口", hasText("取消") and hasClickAction())
        assertTouchTarget(cancel, windowRoot("共享兼容窗口"))
        cancel.performTouchInput { click() }
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        composeRule.onNodeWithTag("provider_dialog_shape", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
