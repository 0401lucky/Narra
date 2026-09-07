package com.example.myapplication.ui.screen.settings.contextlog

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextLogScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportDoesNotOpenDetails_andDeleteRequiresExplicitConfirmation() {
        val snapshot = sampleSnapshot()
        val deletedIds = mutableListOf<String>()
        var exported: ContextGovernanceSnapshot? = null
        showLogs(
            snapshots = listOf(snapshot),
            onDelete = { deletedIds += it.id },
            onExport = { exported = it },
        )

        scrollToLog(snapshot.id)
        captureReviewImage("context-log-light-list")
        composeRule.onNodeWithTag("context_log_menu_${snapshot.id}").performClick()
        composeRule.onNodeWithText("导出").performClick()
        composeRule.onNodeWithTag("context_log_detail").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(snapshot, exported)
            assertEquals(emptyList<String>(), deletedIds)
        }

        composeRule.onNodeWithTag("context_log_menu_${snapshot.id}").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("删除上下文日志").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), deletedIds) }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), deletedIds) }
        composeRule.onNodeWithTag("context_log_menu_${snapshot.id}").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("删除日志").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(listOf(snapshot.id), deletedIds) }
        composeRule.onNodeWithText("删除上下文日志").assertDoesNotExist()
    }

    @Test
    fun largeFont_helpAndSettingsScroll_capacityOnlyUpdatesWhenEnabled() {
        var enabled by mutableStateOf(false)
        var capacity by mutableIntStateOf(15)
        var capacityCalls = 0
        val snapshot = sampleSnapshot()
        showLogs(
            snapshots = listOf(snapshot),
            fontScale = 1.6f,
            compact = true,
            darkTheme = true,
            enabled = { enabled },
            capacity = { capacity },
            onEnabledChange = { enabled = it },
            onCapacityChange = {
                capacity = it
                capacityCalls++
            },
        )

        composeRule.onNodeWithText("每次发送请求到模型时记录一条上下文日志。", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("记录设置：查看说明")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("每次发送请求到模型时记录一条上下文日志。", substring = true).assertExists()
        composeRule.onNodeWithText("知道了").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("context_log_list")
            .performScrollToNode(hasTestTag("context_log_capacity"))
        composeRule.onNodeWithTag("context_log_capacity").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, capacityCalls) }
        composeRule.onNodeWithTag("context_log_list")
            .performScrollToNode(hasTestTag("context_log_enabled"))
        composeRule.onNodeWithTag("context_log_enabled").performClick().assertIsOn()
        composeRule.onNodeWithTag("context_log_list")
            .performScrollToNode(hasTestTag("context_log_capacity"))
        composeRule.onNodeWithTag("context_log_capacity")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
        composeRule.runOnIdle {
            assertEquals(24, capacity)
            assertEquals(1, capacityCalls)
        }
        captureReviewImage("context-log-dark-large-font")
        scrollToLog(snapshot.id)
        composeRule.onNodeWithTag("context_log_${snapshot.id}").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("context_log_detail").assertIsDisplayed()
    }

    @Test
    fun detailCopyMemoryAndBack_keepExistingCallbackBoundaries() {
        val snapshot = sampleSnapshot()
        var copied: ContextGovernanceSnapshot? = null
        var memoryCalls = 0
        var navigateBackCalls = 0
        showLogs(
            snapshots = listOf(snapshot),
            onCopy = { copied = it },
            onOpenMemoryManagement = { memoryCalls++ },
            onNavigateBack = { navigateBackCalls++ },
        )

        openLog(snapshot.id)
        captureReviewImage("context-log-light-detail")
        composeRule.onNodeWithContentDescription("复制原文").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("打开长记忆管理").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("context_log_detail").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(snapshot, copied)
            assertEquals(1, memoryCalls)
            assertEquals(0, navigateBackCalls)
        }
        openLog(snapshot.id)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("context_log_detail").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle { assertEquals(1, navigateBackCalls) }
    }

    @Test
    fun allSourceTypes_largeFont_lastFilterIsReachableAndCanRestoreAllSections() {
        val sections = ContextLogSourceType.entries.map { type ->
            ContextLogSection(
                id = "section-${type.name}",
                sourceType = type,
                title = "${type.label}的详细内容",
                content = "${type.label}正文：完整保留这次请求中的长文本与中文标点。\n".repeat(20),
            )
        }
        val snapshot = sampleSnapshot().copy(contextSections = sections)
        showLogs(snapshots = listOf(snapshot), fontScale = 1.6f, compact = true, darkTheme = true)
        openLog(snapshot.id)
        composeRule.onNodeWithText("tokens 仅为预估值，请以模型实际消耗为准。", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("日志信息：查看说明").performClick()
        composeRule.onNodeWithText("tokens 仅为预估值，请以模型实际消耗为准。", substring = true).assertExists()
        composeRule.onNodeWithText("知道了").assertIsDisplayed().performClick()

        val finalType = ContextLogSourceType.entries.last()
        val finalFilterTag = "context_log_filter_${finalType.name}"
        composeRule.onNodeWithTag("context_log_filters").performScrollToNode(hasTestTag(finalFilterTag))
        composeRule.onNodeWithTag(finalFilterTag)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("context_log_detail_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("context_log_content_section-${finalType.name}").assertIsDisplayed()
        captureReviewImage("context-log-dark-filtered-detail")

        composeRule.onNodeWithTag(finalFilterTag).performClick()
        composeRule.onNodeWithTag("context_log_filters")
            .performScrollToNode(hasTestTag("context_log_filter_all"))
        composeRule.onNodeWithTag("context_log_filter_all").assertIsSelected()
        composeRule.onNodeWithTag("context_log_detail_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("context_log_content_section-${ContextLogSourceType.entries.first().name}")
            .assertIsDisplayed()
    }

    @Test
    fun legacySnapshotWithoutSections_keepsRawTextReadable() {
        val snapshot = sampleSnapshot().copy(
            contextSections = emptyList(),
            rawDebugDump = "兼容旧日志的完整原文。\n".repeat(30),
        )
        showLogs(snapshots = listOf(snapshot), fontScale = 1.6f, compact = true)
        openLog(snapshot.id)
        composeRule.onNodeWithTag("context_log_detail_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("context_log_raw_content").assertIsDisplayed()
        composeRule.onNodeWithText(snapshot.rawDebugDump).assertIsDisplayed()
    }

    @Test
    fun blankRawText_disablesCopyWithoutDiscardingStructuredSections() {
        val snapshot = sampleSnapshot().copy(rawDebugDump = "")
        var copied: ContextGovernanceSnapshot? = null
        showLogs(snapshots = listOf(snapshot), onCopy = { copied = it })
        openLog(snapshot.id)
        composeRule.onNodeWithContentDescription("复制原文").assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithTag("context_log_detail_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("context_log_content_sample-section").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(copied) }
    }

    private fun sampleSnapshot() = ContextGovernanceSnapshot(
        id = "sample-log",
        generatedAt = 1_783_296_000_000L,
        providerLabel = "回归测试提供商",
        modelLabel = "example-model",
        promptModeLabel = "角色扮演",
        estimatedContextTokens = 128,
        rawDebugDump = "一次用于回归的上下文请求\n完整原文不会在选择、复制或导出时被改写。",
        contextSections = listOf(
            ContextLogSection(
                id = "sample-section",
                sourceType = ContextLogSourceType.WORLD_BOOK,
                title = "场景设定",
                content = "场景中的街道、信箱和日记。\n这是一段完整保留的测试上下文。",
            ),
        ),
    )

    private fun showLogs(
        snapshots: List<ContextGovernanceSnapshot>,
        enabled: () -> Boolean = { true },
        capacity: () -> Int = { 15 },
        fontScale: Float = 1f,
        compact: Boolean = false,
        darkTheme: Boolean = false,
        onEnabledChange: (Boolean) -> Unit = {},
        onCapacityChange: (Int) -> Unit = {},
        onNavigateBack: () -> Unit = {},
        onOpenMemoryManagement: () -> Unit = {},
        onDelete: (ContextGovernanceSnapshot) -> Unit = {},
        onExport: (ContextGovernanceSnapshot) -> Unit = {},
        onCopy: (ContextGovernanceSnapshot) -> Unit = {},
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ChatAppTheme(darkTheme = darkTheme) {
                    Box(if (compact) Modifier.width(320.dp).height(560.dp) else Modifier.fillMaxSize()) {
                        ContextLogContent(
                            snapshots = snapshots,
                            contextLogEnabled = enabled(),
                            contextLogCapacity = capacity(),
                            onUpdateContextLogEnabled = onEnabledChange,
                            onUpdateContextLogCapacity = onCapacityChange,
                            onNavigateBack = onNavigateBack,
                            onOpenMemoryManagement = onOpenMemoryManagement,
                            onDelete = onDelete,
                            onExport = onExport,
                            onCopy = onCopy,
                        )
                    }
                }
            }
        }
    }

    private fun scrollToLog(id: String) {
        composeRule.onNodeWithTag("context_log_list").performScrollToNode(hasTestTag("context_log_$id"))
    }

    private fun openLog(id: String) {
        scrollToLog(id)
        composeRule.onNodeWithTag("context_log_$id").performClick()
        composeRule.onNodeWithTag("context_log_detail").assertIsDisplayed()
    }

    private fun captureReviewImage(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = composeRule.onNodeWithTag("context_log_screen").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
