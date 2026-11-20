package com.example.myapplication.ui.screen.roleplay

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.*
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.CompanionSettingsViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用实际 ViewModel 与页面；Fake 仅提供可控的设置读写结果。 */
@RunWith(AndroidJUnit4::class)
class CompanionSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun writingChoices_editDraftAndSaveOnlyAfterExplicitClick() {
        val fixture = Fixture()
        render(fixture)

        CompanionWritingStyle.entries.forEach { style ->
            composeRule.onNodeWithTag("companion_style_" + style.name)
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle { assertEquals(style, fixture.draft.writing.style) }
        }
        composeRule.onNodeWithTag("companion_style_default")
            .performScrollTo().performClick().assertIsSelected()
        CompanionPointOfView.entries.forEach { pointOfView ->
            composeRule.onNodeWithTag("companion_pov_" + pointOfView.name)
                .performScrollTo().performClick().assertIsSelected()
            composeRule.runOnIdle { assertEquals(pointOfView, fixture.draft.writing.pointOfView) }
        }
        composeRule.onNodeWithTag("companion_pov_default")
            .performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(null, fixture.draft.writing.style)
            assertEquals(null, fixture.draft.writing.pointOfView)
            assertTrue(fixture.store.calls.isEmpty())
        }

        composeRule.onNodeWithTag("companion_style_CINEMATIC").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_pov_THIRD_YOU").performScrollTo().performClick()
        saveButton().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            val call = fixture.store.calls.single()
            assertEquals(CHARACTER_ID, call.assistantId)
            assertEquals(7L, call.expectedRevision)
            assertEquals(CompanionWritingStyle.CINEMATIC, call.patch.writing?.style)
            assertEquals(CompanionPointOfView.THIRD_YOU, call.patch.writing?.pointOfView)
            assertTrue(fixture.viewModel.uiState.value.closeRequested)
            assertFalse(fixture.viewModel.uiState.value.isDirty)
        }
    }

    @Test
    fun templates_applyUpdateAndDeletePreserveAppliedText() {
        val originalTemplate =
            CompanionStyleTemplate("template_original", "雨天", "让停顿自然一点。")
        val fixture = Fixture(
            CompanionCharacterSettings(
                revision = 7,
                writing = CompanionWritingSettings(
                    extraStyle = "原来的补充",
                    templates = listOf(originalTemplate),
                ),
            ),
        )
        render(fixture)

        composeRule.onNodeWithText("应用模板").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_extra_style")
            .performScrollTo().assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(originalTemplate.text)
                )
            )
            .performTextReplacement("保留安静的日常，也允许角色不立刻回答。")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("用当前补充更新").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                fixture.draft.writing.extraStyle,
                fixture.draft.writing.templates.single().text
            )
        }
        composeRule.onNodeWithText("删除模板").performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_template_template_original").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(fixture.draft.writing.templates.isEmpty())
            assertEquals("保留安静的日常，也允许角色不立刻回答。", fixture.draft.writing.extraStyle)
        }
        composeRule.onNodeWithTag("companion_template_name")
            .performScrollTo().performTextReplacement("新的日常模板")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("将当前补充存为模板").performScrollTo().performClick()
        composeRule.runOnIdle {
            val template = fixture.draft.writing.templates.single()
            assertEquals("新的日常模板", template.name)
            assertEquals(fixture.draft.writing.extraStyle, template.text)
            assertNotEquals(originalTemplate.id, template.id)
            assertTrue(fixture.store.calls.isEmpty())
        }
        saveButton().performClick()
        composeRule.runOnIdle {
            assertEquals(fixture.draft.writing, fixture.store.calls.single().patch.writing)
        }
    }

    @Test
    fun customObservationFields_keepIdsWhileRenamingAndEnforceSixItemLimit() {
        val fields = (1..5).map { CompanionObservationField("obs_existing_$it", "自定义项目 $it") }
        val fixture = Fixture(
            CompanionCharacterSettings(
                revision = 7,
                writing = CompanionWritingSettings(
                    observation = CompanionObservationSettings(
                        enabled = true,
                        customFields = fields
                    ),
                ),
            ),
        )
        render(fixture)

        composeRule.onNodeWithTag("companion_add_observation").performScrollTo().performClick()
            .assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(6, fixture.draft.writing.observation.customFields.size)
            assertEquals(
                6,
                fixture.draft.writing.observation.customFields.map { it.id }.distinct().size
            )
        }
        composeRule.onNodeWithTag("companion_field_label_obs_existing_1")
            .performScrollTo().performTextReplacement("外套")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("companion_field_delete_obs_existing_2")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("companion_add_observation").performScrollTo().assertIsEnabled()
        composeRule.runOnIdle {
            val current = fixture.draft.writing.observation
            assertEquals("外套", current.customFields.single { it.id == "obs_existing_1" }.label)
            assertFalse(current.customFields.any { it.id == "obs_existing_2" })
            assertEquals(listOf("time", "place", "state", "detail"), current.fields.map { it.id })
            assertTrue(fixture.store.calls.isEmpty())
        }
        saveButton().performClick()
        composeRule.runOnIdle {
            assertEquals(
                fixture.draft.writing.observation.customFields,
                fixture.store.calls.single().patch.writing?.observation?.customFields,
            )
        }
    }

    @Test
    fun duplicateObservationNames_showErrorAtSaveWithoutLosingDraftOrWriting() {
        val fixture = Fixture()
        render(fixture)
        composeRule.onNodeWithTag("companion_field_label_state")
            .performScrollTo().performTextReplacement("地点")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("companion_settings_zone").performScrollTo()

        saveButton().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("companion_settings_error")
            .assertIsDisplayed().assertTextEquals("观测名称不能重复")
        saveButton().assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals(
                "地点",
                fixture.draft.writing.observation.fields.single { it.id == "state" }.label,
            )
            assertTrue(fixture.store.calls.isEmpty())
            assertFalse(fixture.viewModel.uiState.value.closeRequested)
        }
        composeRule.onNodeWithTag("companion_field_label_state")
            .performScrollTo().performTextReplacement("姿态")
        Espresso.closeSoftKeyboard()
        saveButton().performClick()
        composeRule.runOnIdle { assertEquals(1, fixture.store.calls.size) }
    }

    @Test
    fun narrowLargeFont_failedSaveKeepsDraftAndVisibleRetryActions() {
        val fixture = Fixture().apply {
            store.failure = IOException("设置保存失败，草稿已保留，请重试。")
        }
        render(fixture, narrow = true, fontScale = 2f)
        composeRule.onNodeWithTag("companion_settings_zone")
            .performScrollTo().performClick().performTextReplacement("Asia/Tokyo")
        Espresso.closeSoftKeyboard()
        assertFixedActionBounds()

        saveButton().performClick()
        composeRule.onNodeWithTag("companion_settings_error")
            .assertIsDisplayed().assertTextContains("草稿", substring = true)
        assertFixedActionBounds()
        composeRule.runOnIdle {
            assertEquals("Asia/Tokyo", fixture.draft.zoneId)
            assertTrue(fixture.viewModel.uiState.value.isDirty)
            assertFalse(fixture.viewModel.uiState.value.closeRequested)
            assertEquals(1, fixture.store.calls.size)
            assertEquals("", fixture.store.values.value.companionSettings(CHARACTER_ID).zoneId)
        }
        captureCandidate("settings-320dp-font200-save-error")
        composeRule.runOnIdle { fixture.store.failure = null }
        saveButton().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, fixture.store.calls.size)
            assertEquals(fixture.store.calls[0], fixture.store.calls[1])
            assertEquals(
                "Asia/Tokyo",
                fixture.store.values.value.companionSettings(CHARACTER_ID).zoneId
            )
            assertTrue(fixture.viewModel.uiState.value.closeRequested)
        }
    }

    @Test
    fun pendingSave_disablesDuplicateSaveCancelAndSystemBack() {
        val fixture = Fixture()
        val pending = CompletableDeferred<CompanionSettingsWriteResult>()
        fixture.store.pending = pending
        render(fixture)
        composeRule.onNodeWithTag("companion_style_PLAIN").performScrollTo().performClick()
        saveButton().performClick()

        saveButton().assertIsNotEnabled().performTouchInput { click() }
        cancelButton().assertIsNotEnabled().performTouchInput { click() }
        Espresso.pressBack()
        composeRule.runOnIdle {
            assertEquals(1, fixture.store.calls.size)
            assertTrue(fixture.viewModel.uiState.value.isSaving)
            assertFalse(fixture.viewModel.uiState.value.closeRequested)
            assertFalse(fixture.viewModel.uiState.value.showDiscardConfirmation)
            pending.complete(
                CompanionSettingsWriteResult.Saved(fixture.draft.copy(revision = 8)),
            )
        }
        composeRule.waitUntil(5_000) { !fixture.viewModel.uiState.value.isSaving }
        composeRule.runOnIdle {
            assertTrue(fixture.viewModel.uiState.value.closeRequested)
            assertEquals(1, fixture.store.calls.size)
        }
    }

    @Test
    fun conflict_requiresExplicitReloadAndDialogBackPreservesDraft() {
        val fixture = Fixture()
        render(fixture, narrow = true, fontScale = 2f)
        composeRule.onNodeWithTag("companion_settings_zone")
            .performScrollTo().performTextReplacement("Europe/Paris")
        Espresso.closeSoftKeyboard()
        composeRule.runOnIdle {
            fixture.store.result = CompanionSettingsWriteResult.Conflict
            fixture.store.publishSettings(
                CompanionCharacterSettings(
                    revision = 8,
                    zoneId = "Asia/Shanghai",
                    writing = CompanionWritingSettings(style = CompanionWritingStyle.LITERARY),
                ),
            )
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.uiState.value.hasConflict }
        saveButton().performClick()
        composeRule.onNodeWithTag("companion_settings_error").assertIsDisplayed()
        composeRule.onNodeWithText("读取最新设置").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("读取最新设置？").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("读取最新设置？").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals("Europe/Paris", fixture.draft.zoneId)
            assertTrue(fixture.viewModel.uiState.value.isDirty)
            assertFalse(fixture.viewModel.uiState.value.closeRequested)
            assertEquals(7L, fixture.store.calls.single().expectedRevision)
        }
        captureCandidate("settings-320dp-font200-conflict-actions")
        composeRule.onNodeWithText("读取最新设置").performClick()
        composeRule.onNodeWithText("放弃草稿并读取").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(8L, fixture.draft.revision)
            assertEquals("Asia/Shanghai", fixture.draft.zoneId)
            assertEquals(CompanionWritingStyle.LITERARY, fixture.draft.writing.style)
            assertFalse(fixture.viewModel.uiState.value.isDirty)
            assertFalse(fixture.viewModel.uiState.value.hasConflict)
            assertEquals(1, fixture.store.calls.size)
        }
    }

    @Test
    fun keyboardBack_hidesImeThenClosesDiscardDialogWithoutSavingDraft() {
        val fixture = Fixture()
        render(fixture, narrow = true, fontScale = 2f)
        composeRule.onNodeWithTag("companion_settings_zone")
            .performScrollTo().performClick().performTextReplacement("Europe/Paris")
        composeRule.runOnIdle {
            WindowInsetsControllerCompat(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).show(WindowInsetsCompat.Type.ime())
        }
        composeRule.waitUntil(5_000) { imeVisible() }
        saveButton().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        cancelButton().assertIsDisplayed().assertHeightIsAtLeast(48.dp)

        Espresso.pressBack()
        composeRule.waitUntil(5_000) { !imeVisible() }
        composeRule.runOnIdle {
            assertFalse(fixture.viewModel.uiState.value.showDiscardConfirmation)
            assertFalse(fixture.viewModel.uiState.value.closeRequested)
            assertEquals("Europe/Paris", fixture.draft.zoneId)
        }
        Espresso.pressBack()
        composeRule.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("Europe/Paris", fixture.draft.zoneId) }
        cancelButton().performClick()
        composeRule.onNodeWithText("放弃修改").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue(fixture.viewModel.uiState.value.closeRequested)
            assertTrue(fixture.store.calls.isEmpty())
        }
    }

    @Test
    fun newerSettingsAndRemovedCharacter_disableEditsButKeepCancelAvailable() {
        val fixture = Fixture(CompanionCharacterSettings(schemaVersion = 2, revision = 7))
        render(fixture)
        composeRule.onNodeWithTag("companion_style_CINEMATIC")
            .performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        saveButton().assertIsNotEnabled()
        cancelButton().assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals(2, fixture.draft.schemaVersion)
            assertTrue(fixture.store.calls.isEmpty())
            fixture.store.values.value = fixture.store.values.value.copy(assistants = emptyList())
        }
        composeRule.waitUntil(5_000) { !fixture.viewModel.uiState.value.characterAvailable }
        saveButton().assertIsNotEnabled()
        composeRule.onNodeWithTag("companion_settings_error")
            .assertIsDisplayed().assertTextContains("角色已不存在", substring = true)
        cancelButton().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertTrue(fixture.viewModel.uiState.value.closeRequested)
            assertTrue(fixture.store.calls.isEmpty())
        }
    }

    private fun render(fixture: Fixture, narrow: Boolean = false, fontScale: Float = 1f) {
        composeRule.activityRule.scenario.onActivity { activity ->
            fixture.viewModel = ViewModelProvider(
                activity,
                CompanionSettingsViewModel.factory(CHARACTER_ID, fixture.store, SavedStateHandle()),
            )[CompanionSettingsViewModel::class.java]
        }
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                this.fontScale = fontScale
                if (narrow) screenWidthDp = 320
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                ChatAppTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(
                            (if (narrow) Modifier.width(320.dp) else Modifier.fillMaxWidth())
                                .fillMaxHeight().testTag(HOST_TAG),
                        ) {
                            val state by fixture.viewModel.uiState.collectAsStateWithLifecycle()
                            CompanionSettingsScreen(
                                state = state,
                                callbacks = CompanionSettingsCallbacks(
                                    onWritingChange = fixture.viewModel::updateWriting,
                                    onZoneChange = fixture.viewModel::updateZone,
                                    onAddCustomField = fixture.viewModel::addCustomField,
                                    onAddTemplate = fixture.viewModel::addTemplate,
                                    onSave = fixture.viewModel::save,
                                    onBack = fixture.viewModel::requestClose,
                                    onDismissDiscard = fixture.viewModel::dismissDiscard,
                                    onDiscard = fixture.viewModel::discardAndClose,
                                    onReload = fixture.viewModel::reloadDiscardingDraft,
                                ),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { !fixture.viewModel.uiState.value.isLoading }
    }

    private fun saveButton() = composeRule.onNodeWithTag("companion_settings_save")
    private fun cancelButton() = composeRule.onNodeWithTag("companion_settings_cancel")

    private fun assertFixedActionBounds() {
        val host = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        assertEquals(320f, (host.right - host.left).value, 1f)
        listOf(saveButton(), cancelButton()).forEach { button ->
            button.assertIsDisplayed().assertIsEnabled()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            assertInsideHost(button)
        }
    }

    private fun assertInsideHost(node: SemanticsNodeInteraction) {
        val host = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= host.left && bounds.right <= host.right)
        assertTrue(bounds.top >= host.top && bounds.bottom <= host.bottom)
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun captureCandidate(name: String) {
        val directory = File(composeRule.activity.cacheDir, "companion-d3-ui-review")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = composeRule.onNodeWithTag(HOST_TAG).captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private class Fixture(settings: CompanionCharacterSettings = CompanionCharacterSettings(revision = 7)) {
        val store = NativeSettingsStore(settings)
        lateinit var viewModel: CompanionSettingsViewModel
        val draft: CompanionCharacterSettings get() = requireNotNull(viewModel.uiState.value.draft)
    }

    private data class SaveCall(
        val assistantId: String,
        val expectedRevision: Long,
        val patch: CompanionSettingsPatch,
    )

    private class NativeSettingsStore(initial: CompanionCharacterSettings) : SettingsStore {
        val values = MutableStateFlow(
            AppSettings(
                assistants = listOf(Assistant(id = CHARACTER_ID, name = "合成测试角色")),
                companionCharacterSettings = mapOf(CHARACTER_ID to initial),
            ),
        )
        override val settingsFlow = values
        val calls = mutableListOf<SaveCall>()
        var failure: Exception? = null
        var result: CompanionSettingsWriteResult? = null
        var pending: CompletableDeferred<CompanionSettingsWriteResult>? = null

        override suspend fun updateCompanionSettings(
            assistantId: String,
            expectedRevision: Long,
            patch: CompanionSettingsPatch,
        ): CompanionSettingsWriteResult {
            calls += SaveCall(assistantId, expectedRevision, patch)
            failure?.let { throw it }
            val response = pending?.await() ?: result ?: CompanionSettingsWriteResult.Saved(
                values.value.companionSettings(assistantId).copy(
                    revision = expectedRevision + 1,
                    zoneId = patch.zoneId.orEmpty(),
                    writing = requireNotNull(patch.writing),
                ),
            )
            if (response is CompanionSettingsWriteResult.Saved) publishSettings(response.settings)
            return response
        }

        fun publishSettings(settings: CompanionCharacterSettings) {
            values.value = values.value.copy(
                companionCharacterSettings = values.value.companionCharacterSettings + (CHARACTER_ID to settings),
            )
        }

        private fun unexpected(): Nothing = error("本测试不应调用其它设置写入")
        override suspend fun saveSettings(baseUrl: String, apiKey: String, selectedModel: String) =
            unexpected()

        override suspend fun saveProviderSettings(
            providers: List<ProviderSettings>,
            selectedProviderId: String
        ) = unexpected()

        override suspend fun saveFunctionModelProviderIds(functionModelProviderIds: FunctionModelProviderIds) =
            unexpected()

        override suspend fun saveDisplaySettings(
            themeMode: ThemeMode,
            appColorTheme: AppColorTheme,
            messageTextScale: Float,
            reasoningExpandedByDefault: Boolean,
            showThinkingContent: Boolean,
            autoCollapseThinking: Boolean,
            autoPreviewImages: Boolean,
            codeBlockAutoWrap: Boolean,
            codeBlockAutoCollapse: Boolean,
            showRoleplayAiHelper: Boolean,
            roleplayLongformTargetChars: Int,
            showRoleplayPresenceStrip: Boolean,
            showRoleplayStatusStrip: Boolean,
            showOnlineRoleplayNarration: Boolean,
            enableRoleplayNetMeme: Boolean,
            roleplayImmersiveMode: RoleplayImmersiveMode,
            roleplayHighContrast: Boolean,
            roleplayLineHeightScale: RoleplayLineHeightScale,
            roleplayNoBackgroundSkin: RoleplayNoBackgroundSkinSettings,
        ) = unexpected()

        override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) =
            unexpected()

        override suspend fun saveSearchSettings(settings: SearchSettings) = unexpected()
        override suspend fun saveVoiceSynthesisSettings(settings: VoiceSynthesisSettings) =
            unexpected()

        override suspend fun saveVoiceRecognitionSettings(settings: VoiceRecognitionSettings) =
            unexpected()

        override suspend fun saveMomentsSettings(settings: MomentsSettings) = unexpected()
        override suspend fun saveUserProfile(
            displayName: String,
            personaPrompt: String,
            avatarUri: String,
            avatarUrl: String
        ) = unexpected()

        override suspend fun saveUserPersonaMasks(
            masks: List<UserPersonaMask>,
            defaultMaskId: String
        ) = unexpected()

        override suspend fun saveAssistants(
            assistants: List<Assistant>,
            selectedAssistantId: String
        ) = unexpected()

        override suspend fun saveDefaultPresetId(presetId: String) = unexpected()
        override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) =
            unexpected()

        override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) =
            unexpected()

        override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = unexpected()
        override suspend fun saveMemoryRecallTokenBudget(tokenBudget: Int) = unexpected()
        override suspend fun saveMemoryResidentLimits(limits: MemoryResidentLimits) = unexpected()
        override suspend fun saveMemoryPromptSettings(
            extractionPrompt: String,
            injectionPrompt: String
        ) = unexpected()

        override suspend fun saveMemoryInjectionPosition(position: MemoryInjectionPosition) =
            unexpected()

        override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = unexpected()
    }

    companion object {
        private const val CHARACTER_ID = "d3-native-character"
        private const val HOST_TAG = "companion_settings_native_host"
    }
}
