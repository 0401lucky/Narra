package com.example.myapplication.ui.screen.roleplay

import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.CompanionDisplayMode
import com.example.myapplication.model.CompanionImageKind
import com.example.myapplication.model.CompanionImageResource
import com.example.myapplication.model.CompanionOutfit
import com.example.myapplication.model.CompanionPortraitPosition
import com.example.myapplication.model.CompanionVisualSettings
import com.example.myapplication.viewmodel.CompanionVisualSettingsUiState
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证生产 Screen 的回调与反馈；真实 CAS、文件 IO 和共享引用保护由仓储测试覆盖。 */
@RunWith(AndroidJUnit4::class)
class CompanionVisualSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ui = CompanionD4UiFixture(composeRule)

    @After
    fun release() {
        ui.close()
    }

    @Test
    fun saveFailureAndConflict_showFeedbackKeepTheEditedFieldAndNeverClose() {
        val fixture = Fixture(visuals())
        ui.render { Screen(fixture) }
        val name = composeRule.onNodeWithTag("companion_visual_resource_name_portrait")
        name.performScrollTo().performTextReplacement("未保存的立绘草稿")
        composeRule.onNodeWithTag(SAVE).performClick()
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, fixture.saveCalls)
            assertEquals("未保存的立绘草稿", fixture.state.value.draft?.resources?.first()?.name)
            fixture.state.value = fixture.state.value.copy(
                isSaving = false, errorMessage = "保存失败，草稿仍保留。",
            )
        }
        composeRule.onNodeWithTag(ERROR).assertIsDisplayed()
            .assertTextContains("保存失败，草稿仍保留。", substring = true)
        name.performScrollTo().assertTextContains("未保存的立绘草稿", substring = true)
        composeRule.onNodeWithTag(SAVE).assertIsEnabled().performClick()
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(
                isSaving = false, hasConflict = true, errorMessage = "设置已在别处更新，草稿仍保留。",
            )
        }
        composeRule.onNodeWithTag(ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag("companion_visual_reload").assertIsDisplayed()
        name.performScrollTo().assertTextContains("未保存的立绘草稿", substring = true)
        composeRule.runOnIdle {
            assertEquals(2, fixture.saveCalls)
            assertEquals(0, fixture.backCalls)
            assertEquals(0, fixture.reloadCalls)
            assertFalse(fixture.state.value.closeRequested)
        }
    }

    @Test
    fun reloadConflict_requiresTheExplicitConfirmationAndBackKeepsTheDraft() {
        val fixture = Fixture(visuals())
        ui.render { Screen(fixture) }
        composeRule.onNodeWithTag("companion_visual_outfit_name_outfit").performScrollTo()
            .performTextReplacement("尚未保存的套装")
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(
                hasConflict = true, errorMessage = "设置冲突，当前草稿仍保留。",
            )
        }
        composeRule.onNodeWithTag("companion_visual_reload").performClick()
        composeRule.onNodeWithText("读取最新设置？").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, fixture.reloadCalls)
            assertEquals("尚未保存的套装", fixture.state.value.draft?.outfits?.single()?.name)
        }
        composeRule.onNodeWithTag("companion_visual_reload").performClick()
        composeRule.onNodeWithText("读取最新").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.reloadCalls)
            assertEquals(0, fixture.discardCalls)
            assertEquals(0, fixture.backCalls)
        }
    }

    @Test
    fun referencePickers_filterDecorAndBackground_andSceneLayoutDoesNotOverwriteCharacterDefaults() {
        val fixture = Fixture(visuals())
        ui.render { Screen(fixture) }
        composeRule.onNodeWithTag("companion_visual_outfit_default_outfit")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_visual_outfit_default_outfit_option_portrait")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("companion_visual_outfit_default_outfit_option_background")
            .assertDoesNotExist()
        composeRule.onNodeWithTag("companion_visual_outfit_default_outfit_option_decor")
            .assertDoesNotExist()
        composeRule.onNodeWithTag("companion_visual_outfit_default_outfit_option_none")
            .performClick()
        composeRule.onNodeWithTag("companion_visual_expression_key_outfit").performScrollTo()
            .performTextReplacement("不合法表情")
        composeRule.onNodeWithTag("companion_visual_add_expression_outfit").assertIsNotEnabled()
        composeRule.onNodeWithTag("companion_visual_expression_key_outfit")
            .performTextReplacement("happy")
        composeRule.onNodeWithTag("companion_visual_expression_image_outfit")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_visual_expression_image_outfit_option_portrait")
            .performClick()
        composeRule.onNodeWithTag("companion_visual_add_expression_outfit").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_mode_STAGE").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_visual_scene_override").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_scene_layout_RIGHT").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_scene_layout_width_50").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_scene_layout_height_25").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_scene_outfit").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_visual_scene_outfit_option_none").performClick()
        composeRule.runOnIdle {
            val draft = requireNotNull(fixture.state.value.draft)
            assertEquals(CompanionDisplayMode.STAGE, draft.preferredMode)
            assertEquals(null, draft.outfits.single().defaultPortraitId)
            assertEquals("portrait", draft.outfits.single().expressions["happy"])
            val scene = requireNotNull(draft.sceneOverrides[COMPANION_D4_SCENARIO_ID])
            assertEquals(null, scene.selectedOutfitId)
            assertEquals(CompanionPortraitPosition.RIGHT, scene.layout.portraitPosition)
            assertEquals(0.5f, scene.layout.portraitWidth, 0.001f)
            assertEquals(0.25f, scene.layout.portraitHeight, 0.001f)
            assertEquals(fixture.original.layout, draft.layout)
            assertEquals(fixture.original.selectedOutfitId, draft.selectedOutfitId)
            assertEquals(0, fixture.saveCalls)
        }
        composeRule.onNodeWithTag("companion_visual_mode_default").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(null, fixture.state.value.draft?.preferredMode) }
    }

    @Test
    fun realGetContentLauncher_nullSelectionDoesNothing_andImportFailureKeepsOldPreview() {
        val fixture = Fixture(visuals())
        ui.render { Screen(fixture) }
        composeRule.onNodeWithTag("companion_visual_import_PORTRAIT").performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            val launch = fixture.registry.launches.single()
            assertTrue(launch.contract is ActivityResultContracts.GetContent)
            assertEquals("image/*", launch.input)
            fixture.registry.dispatchResult<Uri?>(launch.requestCode, null)
        }
        composeRule.runOnIdle {
            assertTrue(fixture.imports.isEmpty())
            assertEquals(fixture.original, fixture.state.value.draft)
        }
        val imported =
            ui.image("picker-background", Color.rgb(35, 141, 70), width = 128, height = 64)
        composeRule.onNodeWithTag("companion_visual_import_BACKGROUND").performClick()
        composeRule.runOnIdle {
            fixture.registry.dispatchResult(
                fixture.registry.launches.last().requestCode,
                Uri.fromFile(imported)
            )
        }
        composeRule.runOnIdle {
            val request = fixture.imports.single()
            assertEquals(Uri.fromFile(imported).toString(), request.uri)
            assertEquals(CompanionImageKind.BACKGROUND, request.kind)
            assertTrue(request.name.startsWith("场景背景"))
            fixture.state.value = fixture.state.value.copy(
                isImporting = false, errorMessage = "导入失败，原外观仍保留。",
            )
        }
        composeRule.onNodeWithTag(ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag("companion_visual_resource_name_portrait").performScrollTo()
            .assertTextContains("原立绘", substring = true)
        composeRule.runOnIdle {
            assertEquals(fixture.original, fixture.state.value.draft)
            assertEquals(0, fixture.saveCalls)
            assertEquals(0, fixture.backCalls)
        }
    }

    @Test
    fun validImportedSnapshot_usesRealPixels_andUnreadableFileShowsTheFallback() {
        val valid = ui.image("preview-real", Color.rgb(211, 54, 88))
        val invalid = ui.image("preview-invalid", Color.BLACK).apply { writeText("invalid bitmap") }
        val draft = CompanionVisualSettings(
            resources = listOf(
                CompanionImageResource(
                    "good",
                    "有效图片",
                    valid.absolutePath,
                    CompanionImageKind.PORTRAIT,
                    64,
                    64
                ),
                CompanionImageResource(
                    "bad",
                    "失效图片",
                    invalid.absolutePath,
                    CompanionImageKind.PORTRAIT,
                    64,
                    64
                ),
            )
        )
        val fixture = Fixture(draft)
        ui.render { Screen(fixture) }
        composeRule.onNodeWithTag("companion_visual_resource_good").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("companion_image_good").fetchSemanticsNodes().isNotEmpty()
        }
        val bitmap =
            composeRule.onNodeWithTag("companion_image_good").captureToImage().asAndroidBitmap()
        assertEquals(Color.rgb(211, 54, 88), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        composeRule.onNodeWithTag("companion_visual_resource_bad").performScrollTo()
        composeRule.onNode(
            hasText("图片暂不可读") and hasAnyAncestor(hasTestTag("companion_visual_resource_bad")),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("companion_image_bad").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(valid.absolutePath, fixture.state.value.draft?.resources?.first()?.path)
            assertEquals(0, fixture.saveCalls)
        }
    }

    @Test
    fun removeSharedReference_emitsItsStableId_andCancelKeepsTheProvidedSavedOutfit() {
        val fixture = Fixture(visuals().let {
            it.copy(
                outfits = it.outfits + CompanionOutfit(
                    "shared",
                    "另一套装",
                    defaultPortraitId = "portrait"
                )
            )
        })
        ui.render { Screen(fixture) }
        composeRule.onNodeWithTag("companion_visual_remove_resource_portrait").performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("portrait"), fixture.removedReferences)
            assertEquals(0, fixture.saveCalls)
            assertEquals(fixture.original, fixture.state.value.draft)
        }
        composeRule.onNodeWithTag("companion_visual_default_outfit").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("companion_visual_default_outfit_option_none").performClick()
        composeRule.onNodeWithTag("companion_visual_cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.backCalls)
            fixture.state.value = fixture.state.value.copy(showDiscardConfirmation = true)
        }
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.dismissDiscardCalls)
            assertEquals(null, fixture.state.value.draft?.selectedOutfitId)
            assertEquals(0, fixture.discardCalls)
        }
        composeRule.onNodeWithTag("companion_visual_cancel").performClick()
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(showDiscardConfirmation = true)
        }
        composeRule.onNodeWithText("放弃草稿").performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.discardCalls)
            // 仓储完成撤稿后提供旧快照，Screen 不自行删除共享图片或猜造新快照。
            fixture.state.value = fixture.state.value.copy(
                draft = fixture.original, isDirty = false, showDiscardConfirmation = false,
            )
        }
        composeRule.onNodeWithTag("companion_visual_default_outfit").performScrollTo()
            .assertTextContains("原套装", substring = true)
        composeRule.runOnIdle {
            assertEquals(
                "portrait",
                fixture.state.value.draft?.outfits?.first()?.defaultPortraitId
            )
        }
    }

    @Test
    fun busyDeletedCharacterAndFutureVersion_disableEditingWhileKeepingDraftFeedback() {
        val fixture = Fixture(visuals())
        ui.render { Screen(fixture) }
        composeRule.runOnIdle { fixture.state.value = fixture.state.value.copy(isImporting = true) }
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag("companion_visual_import_PORTRAIT").assertIsNotEnabled()
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(
                isImporting = false,
                characterAvailable = false,
                errorMessage = "角色已不存在，草稿仍保留。",
            )
        }
        composeRule.onNodeWithTag(ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag("companion_visual_resource_name_portrait").performScrollTo()
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            fixture.state.value = fixture.state.value.copy(
                characterAvailable = true, settingsVersionSupported = false,
                draft = fixture.original.copy(schemaVersion = 2),
            )
        }
        composeRule.onNodeWithText("此设置需要较新版本，当前只读。").assertIsDisplayed()
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(0, fixture.saveCalls)
            assertEquals(0, fixture.imports.size)
            assertEquals(fixture.original.resources, fixture.state.value.draft?.resources)
        }
    }

    @Test
    fun narrowPlatformFont200_scrollsToLastControl_andImeThenDiscardBackUseSeparateLayers() {
        val lastName = "最后装饰：蓝色窗边小灯。"
        val base = visuals().let { draft ->
            draft.copy(
                resources = draft.resources + (1..5).map {
                    CompanionImageResource(
                        "extra-" + it, "合成图片 " + it,
                        "d4-native-missing-extra-" + it, CompanionImageKind.PORTRAIT, 64, 64
                    )
                },
                outfits = draft.outfits + (1..3).map {
                    CompanionOutfit(
                        "extra-outfit-" + it,
                        "合成套装 " + it
                    )
                },
            )
        }
        val fixture = Fixture(base.copy(resources = base.resources.map {
            if (it.id == "decor") it.copy(name = lastName) else it
        }))
        val nameStateTrace = mutableListOf<String>()
        fun observeNameState(stage: String) {
            val fields = composeRule.onAllNodesWithTag(
                "companion_visual_resource_name_portrait", useUnmergedTree = true
            ).fetchSemanticsNodes().joinToString { node ->
                val editable = if (node.config.contains(SemanticsProperties.EditableText))
                    node.config[SemanticsProperties.EditableText].text else null
                val focused = if (node.config.contains(SemanticsProperties.Focused))
                    node.config[SemanticsProperties.Focused] else null
                "id=${node.id} editable=$editable focused=$focused"
            }
            val state = composeRule.runOnIdle {
                "draft=" + fixture.state.value.draft?.resources?.firstOrNull { it.id == "portrait" }?.name +
                        " dirty=" + fixture.state.value.isDirty +
                        " back=" + fixture.backCalls +
                        " dismissDiscard=" + fixture.dismissDiscardCalls +
                        " discard=" + fixture.discardCalls +
                        " showDiscard=" + fixture.state.value.showDiscardConfirmation +
                        " draftTraceCount=" + fixture.draftChangeTrace.size
            }
            nameStateTrace += "$stage ime=${ui.imeVisible()} fields=[$fields] $state"
        }
        ui.withNarrowPlatformWindow("visuals-320dp-font200", content = { Screen(fixture) }) {
            try {
                val form = composeRule.onNodeWithTag("companion_visual_scroll")
                val scroll = ui.scrollToEnd(form)
                val last = composeRule.onNode(
                    hasText(lastName) and hasAnyAncestor(hasTestTag("companion_visual_decor_decor")),
                    useUnmergedTree = true,
                )
                val environment = ui.assertNarrowPlatformText(last)
                val lastBounds = ui.assertCompleteTextInViewport(last, form)
                val lastControl = composeRule.onNodeWithTag("companion_visual_decor_decor")
                lastControl.assertIsDisplayed()
                ui.assertTouchTarget(lastControl, form)
                val screen = composeRule.onNodeWithTag("companion_visual_settings")
                listOf(
                    SAVE,
                    "companion_visual_cancel"
                ).forEach { ui.assertTouchTarget(composeRule.onNodeWithTag(it), screen) }
                ui.captureCandidate(
                    "visuals-320dp-font200-last-control", screen,
                    environment + "\n" + scroll + "\n" + lastBounds
                )
                observeNameState("before-name-edit")
                composeRule.onNodeWithTag("companion_visual_resource_name_portrait")
                    .performScrollTo()
                    .performClick().performTextReplacement("窄屏未保存草稿")
                observeNameState("after-text-replacement")
                // 先确认真实编辑已往返到受控状态，再打开 IME，避免输入连接抢先使用旧值。
                composeRule.runOnIdle {
                    assertEquals(
                        "窄屏未保存草稿",
                        fixture.state.value.draft?.resources?.first()?.name
                    )
                    assertTrue(fixture.state.value.isDirty)
                }
                ui.showIme()
                observeNameState("after-ime-shown")
                composeRule.runOnIdle {
                    assertEquals(
                        "窄屏未保存草稿",
                        fixture.state.value.draft?.resources?.first()?.name
                    )
                }
                composeRule.onNodeWithTag(SAVE).assertIsDisplayed()
                ui.assertTouchTarget(composeRule.onNodeWithTag(SAVE), screen)
                Espresso.pressBack()
                composeRule.waitUntil(5_000) { !ui.imeVisible() }
                composeRule.runOnIdle { assertEquals(0, fixture.backCalls) }
                observeNameState("after-ime-back")
                Espresso.pressBack()
                composeRule.runOnIdle {
                    assertEquals(1, fixture.backCalls)
                    fixture.state.value = fixture.state.value.copy(showDiscardConfirmation = true)
                }
                observeNameState("after-host-back-and-dialog-shown")
                val dialog = composeRule.onNode(isDialog())
                val message = composeRule.onNodeWithText("已保存的外观与共享素材会保留。")
                val dialogEnvironment = ui.assertNarrowPlatformText(message, dialog)
                ui.assertTouchTarget(composeRule.onNodeWithText("放弃草稿"), dialog)
                ui.assertTouchTarget(composeRule.onNodeWithText("继续编辑"), dialog)
                ui.captureCandidate(
                    "visuals-320dp-font200-discard-dialog",
                    dialog,
                    dialogEnvironment
                )
                Espresso.pressBack()
                composeRule.onNode(isDialog()).assertDoesNotExist()
                observeNameState("after-discard-dialog-back")
                composeRule.runOnIdle {
                    assertEquals(1, fixture.dismissDiscardCalls)
                    assertEquals(0, fixture.discardCalls)
                    assertEquals(1, fixture.backCalls)
                    assertEquals(
                        "窄屏未保存草稿",
                        fixture.state.value.draft?.resources?.first()?.name
                    )
                }
            } catch (failure: Throwable) {
                runCatching { observeNameState("failure") }.onFailure {
                    nameStateTrace += "failure-state-unavailable=" + it.javaClass.name
                }
                val draftChanges = runCatching {
                    composeRule.runOnIdle { fixture.draftChangeTrace.toList() }
                }.getOrElse {
                    nameStateTrace += "draft-trace-unavailable=" + it.javaClass.name
                    emptyList()
                }
                val evidence = "nameStateTrace:\n" + nameStateTrace.joinToString("\n") +
                        "\ndraftChangeTrace:\n" + draftChanges.joinToString("\n")
                runCatching {
                    val directory = File(composeRule.activity.cacheDir, "d4-candidates")
                    check(directory.isDirectory || directory.mkdirs())
                    File(directory, "visuals-name-state-failure.txt").writeText(
                        evidence,
                        Charsets.UTF_8
                    )
                }.onFailure { failure.addSuppressed(it) }
                runCatching {
                    ui.captureCandidate(
                        "visuals-name-state-failure",
                        composeRule.onNodeWithTag("companion_visual_settings"),
                        evidence
                    )
                }.onFailure { failure.addSuppressed(it) }
                failure.addSuppressed(AssertionError(evidence))
                throw failure
            }
        }
    }

    @Composable
    private fun Screen(fixture: Fixture) {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides fixture.registryOwner) {
            CompanionVisualSettingsScreen(fixture.state.value, fixture.callbacks)
        }
    }

    private fun visuals(): CompanionVisualSettings = CompanionVisualSettings(
        resources = listOf(
            CompanionImageResource(
                "portrait",
                "原立绘",
                "d4-native-missing-portrait",
                CompanionImageKind.PORTRAIT,
                64,
                64
            ),
            CompanionImageResource(
                "background",
                "原背景",
                "d4-native-missing-background",
                CompanionImageKind.BACKGROUND,
                128,
                64
            ),
            CompanionImageResource(
                "decor",
                "装饰小灯",
                "d4-native-missing-decor",
                CompanionImageKind.DECOR,
                64,
                64
            ),
        ),
        outfits = listOf(CompanionOutfit("outfit", "原套装", defaultPortraitId = "portrait")),
        selectedOutfitId = "outfit", selectedBackgroundId = "background",
    )

    private class Fixture(val original: CompanionVisualSettings) {
        data class Import(val uri: String, val kind: CompanionImageKind, val name: String)

        val registry = CompanionD4PickerRegistry()
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val state = mutableStateOf(
            CompanionVisualSettingsUiState(
                assistantId = COMPANION_D4_CHARACTER_ID, scenarioId = COMPANION_D4_SCENARIO_ID,
                characterName = COMPANION_D4_CHARACTER_NAME, draft = original, isLoading = false,
            )
        )
        val imports = mutableListOf<Import>()
        val removedReferences = mutableListOf<String>()
        var saveCalls = 0
        var backCalls = 0
        var dismissDiscardCalls = 0
        var discardCalls = 0
        var reloadCalls = 0
        val draftChangeTrace = mutableListOf<String>()
        val callbacks = CompanionVisualSettingsCallbacks(
            onDraftChange = {
                draftChangeTrace += "before=" + state.value.draft?.resources?.firstOrNull { resource -> resource.id == "portrait" }?.name +
                        " dirtyBefore=" + state.value.isDirty +
                        " incoming=" + it.resources.firstOrNull { resource -> resource.id == "portrait" }?.name
                state.value = state.value.copy(draft = it, isDirty = true)
                draftChangeTrace += "after=" + state.value.draft?.resources?.firstOrNull { resource -> resource.id == "portrait" }?.name +
                        " dirtyAfter=" + state.value.isDirty
            },
            onImportImage = { uri, kind, name ->
                imports += Import(uri, kind, name)
                state.value = state.value.copy(isImporting = true)
            },
            onAddOutfit = {},
            onRemoveResource = { removedReferences += it },
            onSave = { saveCalls++; state.value = state.value.copy(isSaving = true) },
            onBack = { backCalls++ },
            onDismissDiscard = {
                dismissDiscardCalls++
                state.value = state.value.copy(showDiscardConfirmation = false)
            },
            onDiscard = { discardCalls++ },
            onReload = { reloadCalls++ },
        )
    }

    companion object {
        private const val SAVE = "companion_visual_save"
        private const val ERROR = "companion_visual_error"
    }
}
