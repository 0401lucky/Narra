package com.example.myapplication.ui.screen.roleplay

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 真实 Screen/PickVisualMedia 回调测试；Room 成功前不得确认输入已保存。 */
@RunWith(AndroidJUnit4::class)
class CompanionInputPersistenceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val createdPhotos = mutableListOf<File>()

    @After
    fun removeSyntheticPhotos() {
        createdPhotos.forEach { file ->
            file.delete()
            file.parentFile?.delete()
        }
    }

    @Test
    fun sendingWithoutInputSavedAck_keepsPhotoUntilMatchingAckArrives() {
        val fixture = Fixture()
        val photo = createPhoto(Color.RED)
        render(fixture)
        pickPhoto(fixture, photo)
        sendButton().assertIsEnabled().performClick()

        composeRule.onNodeWithTag("roleplay_input_editor").assertIsNotEnabled()
        pendingPhoto().assertIsDisplayed().assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, fixture.submissions.size)
            assertImage(fixture.submissions.single().parts, photo)
            assertTrue(fixture.legacySubmissions.isEmpty())
            fixture.submissions.single().onInputSaved()
        }

        pendingPhoto().assertDoesNotExist()
        composeRule.onNodeWithTag("roleplay_input_editor").assertIsNotEnabled()
        composeRule.runOnIdle { fixture.isSending.value = false }
        sendButton().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, fixture.submissions.size) }
    }

    @Test
    fun inputSaveFailure_preservesExactPhotoForRetryAndOnlySuccessfulAckClearsIt() {
        val fixture = Fixture()
        val photo = createPhoto(Color.BLUE)
        render(fixture)
        pickPhoto(fixture, photo)
        sendButton().performClick()
        pendingPhoto().assertIsDisplayed()

        composeRule.runOnIdle {
            fixture.isSending.value = false
            fixture.errorMessage.value = "输入保存失败，照片仍保留，请重试。"
        }
        pendingPhoto().assertIsDisplayed().assertIsEnabled()
        sendButton().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, fixture.submissions.size)
            fixture.submissions.forEach { assertImage(it.parts, photo) }
            assertEquals(fixture.submissions.first().parts, fixture.submissions.last().parts)
            assertTrue(fixture.legacySubmissions.isEmpty())
            fixture.submissions.last().onInputSaved()
            fixture.isSending.value = false
        }
        pendingPhoto().assertDoesNotExist()
        sendButton().assertIsNotEnabled()
    }

    @Test
    fun staleInputSavedAck_doesNotClearNewlySelectedPhotoWithTheSameFileName() {
        val fixture = Fixture()
        val firstPhoto = createPhoto(Color.GREEN)
        val nextPhoto = createPhoto(Color.YELLOW)
        assertNotEquals(firstPhoto, nextPhoto)
        render(fixture)
        pickPhoto(fixture, firstPhoto)
        sendButton().performClick()
        composeRule.runOnIdle {
            assertImage(fixture.submissions.single().parts, firstPhoto)
            fixture.isSending.value = false
        }
        pickPhoto(fixture, nextPhoto)
        composeRule.runOnIdle { fixture.submissions.first().onInputSaved() }

        pendingPhoto().assertIsDisplayed()
        sendButton().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, fixture.submissions.size)
            assertImage(fixture.submissions.last().parts, nextPhoto)
            assertNotEquals(
                fixture.submissions.first().parts.single().uri,
                fixture.submissions.last().parts.single().uri,
            )
            assertEquals(
                fixture.submissions.first().parts.single().fileName,
                fixture.submissions.last().parts.single().fileName,
            )
            fixture.submissions.last().onInputSaved()
            fixture.isSending.value = false
        }
        pendingPhoto().assertDoesNotExist()
        sendButton().assertIsNotEnabled()
    }

    @Test
    fun legacyCallbackWithoutInputSavedAck_preservesOriginalSendStartClearing() {
        val fixture = Fixture(useInputSavedAck = false)
        val photo = createPhoto(Color.MAGENTA)
        render(fixture)
        pickPhoto(fixture, photo)
        sendButton().performClick()

        pendingPhoto().assertDoesNotExist()
        composeRule.onNodeWithTag("roleplay_input_editor").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertTrue(fixture.submissions.isEmpty())
            assertEquals(1, fixture.legacySubmissions.size)
            assertImage(fixture.legacySubmissions.single(), photo)
            fixture.isSending.value = false
        }
        sendButton().assertIsNotEnabled()
    }

    private fun render(fixture: Fixture) {
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = fixture.registry
        }
        val callbacks = callbacks(fixture)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                ChatAppTheme(darkTheme = false) {
                    RoleplayScreen(
                        scenario = RoleplayScenario(
                            id = "companion-photo-scenario",
                            assistantId = "companion-photo-character",
                            title = "合成照片输入场景",
                            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                            autoHighlightSpeaker = false,
                        ),
                        assistant = Assistant(id = "companion-photo-character", name = "合成角色"),
                        settings = AppSettings(
                            roleplayImmersiveMode = RoleplayImmersiveMode.NONE,
                            showRoleplayPresenceStrip = false,
                            showRoleplayAiHelper = false,
                        ),
                        contextStatus = RoleplayContextStatus(),
                        groupParticipants = emptyList(),
                        messages = emptyList(),
                        suggestions = emptyList(),
                        input = fixture.input.value,
                        inputFocusToken = 0L,
                        replyToMessageId = "",
                        replyToPreview = "",
                        replyToSpeakerName = "",
                        isSending = fixture.isSending.value,
                        isGeneratingSuggestions = false,
                        isScenarioLoading = false,
                        hasActiveSession = true,
                        showAssistantMismatchDialog = false,
                        previousAssistantName = "",
                        currentAssistantName = "",
                        noticeMessage = null,
                        errorMessage = fixture.errorMessage.value,
                        suggestionErrorMessage = null,
                        pendingMemoryProposal = null,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    private fun callbacks(fixture: Fixture): RoleplayScreenCallbacks {
        val sendWithAck: ((List<ChatMessagePart>, () -> Unit) -> Unit)? =
            if (fixture.useInputSavedAck) {
                { parts, onInputSaved ->
                    fixture.submissions += Submission(parts.toList(), onInputSaved)
                    fixture.isSending.value = true
                }
            } else null
        return RoleplayScreenCallbacks(
            message = RoleplayMessageCallbacks(
                onInputChange = { fixture.input.value = it },
                onSend = {
                    fixture.legacySubmissions += it.toList()
                    fixture.isSending.value = true
                },
                onRequestGroupParticipantReply = {},
                onCancelSending = { fixture.isSending.value = false },
                onRetryTurn = {},
                onRetryAiPhoto = { _, _ -> },
                onEditUserMessage = {},
                onQuoteMessage = { _, _, _ -> },
                onPokeMessageAvatar = {},
                onClearQuotedMessage = {},
                onDeleteMessage = {},
                onRecallMessage = {},
                onCaptureOnlineChat = {},
                onSendSpecialPlay = {},
                onSendVoiceMessage = {},
                onConfirmTransferReceipt = {},
                onSendWithInputSaved = sendWithAck,
            ),
            suggestion = RoleplaySuggestionCallbacks(
                onGenerateSuggestions = {},
                onApplySuggestion = {},
                onClearSuggestions = {},
            ),
            navigation = RoleplayNavigationCallbacks(
                onOpenDiary = {},
                onOpenPhoneCheck = {},
                onOpenMoments = {},
                onOpenVideoCall = {},
                onOpenMailbox = {},
                onOpenWallet = {},
                onOpenReadingMode = {},
                onOpenSettings = {},
                onNavigateBack = {},
            ),
            session = RoleplaySessionCallbacks(
                onRestartSession = {},
                onDismissAssistantMismatch = {},
                onApprovePendingMemoryProposal = {},
                onRejectPendingMemoryProposal = {},
            ),
            group = RoleplayGroupCallbacks(
                onToggleParticipantMuted = {},
                onRemoveParticipant = {},
                onUpdateReplyMode = {},
            ),
            ui = RoleplayUiCallbacks(
                onClearNoticeMessage = {},
                onClearErrorMessage = { fixture.errorMessage.value = null },
            ),
        )
    }

    private fun pickPhoto(fixture: Fixture, uri: Uri) {
        val previousLaunches = fixture.registry.launches.size
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.roleplay_more_actions),
        ).performClick()
        composeRule.onNodeWithText("添加图片").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(previousLaunches + 1, fixture.registry.launches.size)
            val launch = fixture.registry.launches.last()
            assertTrue(launch.contract is ActivityResultContracts.PickVisualMedia)
            assertEquals(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
                (launch.input as PickVisualMediaRequest).mediaType,
            )
            assertTrue(fixture.registry.dispatchResult(launch.requestCode, uri))
        }
        pendingPhoto().assertIsDisplayed()
    }

    private fun sendButton() =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.common_send))

    private fun pendingPhoto() = composeRule.onNodeWithContentDescription("移除图片")

    private fun createPhoto(color: Int): Uri {
        val directory = File(composeRule.activity.cacheDir, "companion-input-" + UUID.randomUUID())
        check(directory.mkdirs())
        // 同名文件放在不同私有目录，避免仅比较展示名称便误认原图和新图。
        val file = File(directory, "photo.png")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        createdPhotos += file
        return Uri.fromFile(file)
    }

    private fun assertImage(parts: List<ChatMessagePart>, expectedUri: Uri) {
        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.IMAGE, parts.single().type)
        assertEquals(expectedUri.toString(), parts.single().uri)
        assertTrue(parts.single().mimeType.startsWith("image/"))
    }

    private data class Submission(
        val parts: List<ChatMessagePart>,
        val onInputSaved: () -> Unit,
    )

    private class Fixture(val useInputSavedAck: Boolean = true) {
        val registry = PhotoPickerRegistry()
        val input = mutableStateOf("")
        val isSending = mutableStateOf(false)
        val errorMessage = mutableStateOf<String?>(null)
        val submissions = mutableListOf<Submission>()
        val legacySubmissions = mutableListOf<List<ChatMessagePart>>()
    }

    private class PhotoPickerRegistry : ActivityResultRegistry() {
        data class Launch(
            val requestCode: Int,
            val contract: ActivityResultContract<*, *>,
            val input: Any?,
        )

        val launches = mutableListOf<Launch>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += Launch(requestCode, contract, input)
        }
    }
}
