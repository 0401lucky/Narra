package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import com.example.myapplication.model.*
import com.example.myapplication.ui.screenshot.ContextTransferRenderTest
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.viewmodel.ContextImportPreview
import com.example.myapplication.viewmodel.ContextTransferSection
import org.junit.Assert.*
import org.junit.Test

/** 真实窄屏 200% 字体与独立 Dialog；不创建/更新任何截图基准。 */
class ContextTransferMemoryTargetTest : ContextTransferRenderTest() {
    @Test
    fun explicitTargetRemainsReachableAtLargeFont() {
        val target = MemoryImportTarget(
            MemoryImportTargetMode.EXISTING,
            "local",
            "local-space",
            expectedGeneration = 1L
        )
        val label = "本机角色 · 日常相处 · 未设置扮演身份"
        state.value = state.value.copy(importPreview = preview(target, label))
        var selection: Pair<String, MemoryImportTarget?>? = null
        renderMemory(
            onTarget = { key, selected ->
                selection = key to selected
                state.value = state.value.copy(
                    importPreview = state.value.importPreview!!.copy(
                        memoryTargets = mapOf(key to requireNotNull(selected)),
                        canConfirmMemory = true
                    )
                )
            },
        )
        scrollToEnd()
        pageNode(hasText("确认导入") and hasClickAction()).assertIsNotEnabled()
        pageList().performScrollToNode(hasTestTag("memory_import_target_group"))
        val choose = pageNode(hasTestTag("memory_import_target_group")).performScrollTo()
        requireTarget("memory-target", choose)
        choose.performTouchInput { click() }
        val root = dialogRoot("选择本机记忆目标")
        root.assertIsDisplayed()
        val option = windowNode(
            dialogMatcher("选择本机记忆目标"),
            hasText(label) and hasClickAction()
        ).performScrollTo()
        requireTarget("memory-target-option", option, root)
        val layout = textLayout(
            windowNode(
                dialogMatcher("选择本机记忆目标"),
                hasText(label),
                unmerged = true
            )
        )
        assertTrue(layout.lineCount > 1)
        assertFalse(layout.didOverflowHeight)
        option.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals("group" to target, selection) }
        scrollToEnd()
        val confirm = pageNode(hasText("确认导入") and hasClickAction())
        confirm.assertIsEnabled()
        requireTarget("memory-confirm", confirm)
        confirm.performTouchInput { click() }
        assertEquals(listOf("confirm"), events)
        assertNoExternalActivity()
    }

    @Test
    fun busyAndStateConflictCannotImplicitlyReplace() {
        val target = MemoryImportTarget(
            MemoryImportTargetMode.EXISTING,
            "local",
            "local-space",
            expectedGeneration = 1L
        )
        state.value = state.value.copy(
            importPreview = preview(target, "本机角色 · 日常相处").copy(
                memoryTargets = mapOf("group" to target),
                memoryStateConflicts = listOf(
                    MemoryImportStateConflict(
                        "state",
                        "local-space",
                        "unknown",
                        MemoryStateKey.MOOD,
                        "local-state",
                        3L,
                        false
                    )
                ),
            )
        )
        var replace: Boolean? = null
        renderMemory(onReplace = { replace = it })
        scrollToEnd()
        pageNode(hasText("确认导入") and hasClickAction()).assertIsNotEnabled()
        val button = pageNode(hasTestTag("memory_import_replace_states")).performScrollTo()
        requireTarget("replace-states", button)
        button.performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(true, replace); state.value = state.value.copy(isBusy = true)
        }
        pageNode(hasTestTag("memory_import_replace_states")).assertIsNotEnabled()
        pageList().performScrollToNode(hasTestTag("memory_import_target_group"))
        pageNode(hasTestTag("memory_import_target_group")).assertIsNotEnabled()
        assertTrue(events.isEmpty())
        assertNoExternalActivity()
    }

    private fun preview(target: MemoryImportTarget, label: String) = ContextImportPreview(
        "上下文数据包",
        ContextTransferSection.MEMORY,
        0,
        0,
        1,
        0,
        0,
        emptyList(),
        memoryGroups = listOf(
            MemoryImportSourceGroup(
                "group",
                "original-space",
                "原角色",
                "unknown",
                MemoryPurpose.CONTINUOUS,
                1
            )
        ),
        memoryTargetOptions = listOf(MemoryImportTargetOption(label, target)),
        canConfirmMemory = false,
        memorySourceCount = 1,
    )

    private fun requireTarget(
        name: String,
        node: SemanticsNodeInteraction,
        viewport: SemanticsNodeInteraction = pageList()
    ) {
        recordBounds(name, node, viewport)
        requireFullyVisible(name)
        assertEquals("实际触控范围至少 48dp：$name", "true", measurements["${name}Touch48dp"])
    }

    private fun renderMemory(
        onTarget: (String, MemoryImportTarget?) -> Unit = { _, _ -> },
        onReplace: (Boolean) -> Unit = {},
    ) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        renderP06(ScreenshotCases.require("E06_transfer-preview-actions-narrow-dark")) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ContextTransferScreen(
                    state.value,
                    { _, _ -> error("不应导出") },
                    { _, _ -> error("不应打开文件") },
                    { events += "confirm" },
                    { events += "dismiss" },
                    {},
                    {},
                    onTarget,
                    onReplace
                )
            }
        }
    }
}
