package com.example.myapplication.ui.screen.roleplay

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayChatType
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RoleplayOnlineBackgroundBubbleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backgroundGlassSwitch_restoresThemeSurfaceAndWidthWhileKeepingBackdropAndChrome() {
        val backdrop = loadedBackdrop()
        var settings by mutableStateOf(testSettings())
        var scenario by mutableStateOf(testScenario)
        val messages = listOf(
            dialogueMessage("user", RoleplaySpeaker.USER, UserContent),
            dialogueMessage("character", RoleplaySpeaker.CHARACTER, CharacterContent),
        )
        composeRule.setContent { OnlinePage(settings, backdrop, messages, scenario) }

        val glassUserWidth = bubble("user").fetchSemanticsNode().boundsInRoot.width
        val glassCharacterWidth = bubble("character").fetchSemanticsNode().boundsInRoot.width
        val backgroundPixel = pageBackgroundPixel()
        assertFalse(colorsMatch(Color(0xFFDDEEDD), bubblePixel("user")))
        assertFalse(colorsMatch(Color.White, bubblePixel("character")))
        assertEquals(backdrop.palette.onGlassMuted, textLayout("线上模式").layoutInput.style.color)
        captureReviewImage("online-background-glass-default")

        composeRule.runOnIdle {
            settings = settings.copy(
                roleplayNoBackgroundSkin = settings.roleplayNoBackgroundSkin.copy(
                    onlineBackgroundGlassEnabled = false,
                ),
            )
        }

        val pageWidth = composeRule.onNodeWithTag(PageTag).fetchSemanticsNode().boundsInRoot.width
        val listPadding = with(composeRule.density) { 24.dp.toPx() }
        val expectedWidth = (pageWidth - listPadding) * 0.64f
        assertEquals(expectedWidth, bubble("user").fetchSemanticsNode().boundsInRoot.width, 2f)
        assertEquals(expectedWidth, bubble("character").fetchSemanticsNode().boundsInRoot.width, 2f)
        assertTrue(expectedWidth < glassUserWidth)
        assertTrue(expectedWidth < glassCharacterWidth)
        assertColorClose(Color(0xFFDDEEDD), bubblePixel("user"))
        assertColorClose(Color.White, bubblePixel("character"))
        assertThemeCorner("user", Color(0xFFDDEEDD))
        assertThemeCorner("character", Color.White)
        assertEquals(Color(0xFF2D4A2B), textLayout(UserContent).layoutInput.style.color)
        assertDialogueColor(CharacterContent, Color(0xFF1A1C19))
        assertColorClose(backgroundPixel, pageBackgroundPixel())
        assertEquals(backdrop.palette.onGlassMuted, textLayout("线上模式").layoutInput.style.color)
        captureReviewImage("online-background-theme-bubbles")

        composeRule.runOnIdle {
            scenario = scenario.copy(id = "another-online-scene", title = "测试群聊", chatType = RoleplayChatType.GROUP)
        }
        assertColorClose(Color(0xFFDDEEDD), bubblePixel("user"))
        assertColorClose(Color.White, bubblePixel("character"))
        assertEquals(expectedWidth, bubble("character").fetchSemanticsNode().boundsInRoot.width, 2f)

        composeRule.runOnIdle {
            settings = settings.copy(
                roleplayNoBackgroundSkin = settings.roleplayNoBackgroundSkin.copy(
                    onlineBackgroundGlassEnabled = true,
                ),
            )
        }
        assertEquals(glassUserWidth, bubble("user").fetchSemanticsNode().boundsInRoot.width, 2f)
        assertEquals(glassCharacterWidth, bubble("character").fetchSemanticsNode().boundsInRoot.width, 2f)
        assertFalse(colorsMatch(Color(0xFFDDEEDD), bubblePixel("user")))
        assertColorClose(backgroundPixel, pageBackgroundPixel())
        composeRule.runOnIdle {
            assertEquals(64, settings.roleplayNoBackgroundSkin.maxWidthPercent)
            assertEquals(4, settings.roleplayNoBackgroundSkin.bubbleRadiusDp)
            assertEquals(testScenario.backgroundUri, scenario.backgroundUri)
        }
    }

    @Test
    fun themeBubbles_quotesStreamingAndErrors_followThemeTextColors() {
        val backdrop = loadedBackdrop()
        var settings by mutableStateOf(
            testSettings().copy(
                roleplayNoBackgroundSkin = testSettings().roleplayNoBackgroundSkin.copy(
                    onlineBackgroundGlassEnabled = false,
                ),
            ),
        )
        var message by mutableStateOf(
            dialogueMessage("character", RoleplaySpeaker.CHARACTER, CharacterContent).copy(
                replyToMessageId = "quoted-message",
                replyToSpeakerName = "被引用的角色",
                replyToPreview = "被引用的消息",
                emotion = "平静",
            ),
        )
        composeRule.setContent { OnlinePage(settings, backdrop, listOf(message)) }

        assertDialogueColor(CharacterContent, Color(0xFF1A1C19))
        assertEquals(Color(0xFF546B50), textLayout("被引用的消息").layoutInput.style.color)
        composeRule.onNodeWithText("平静", useUnmergedTree = true).assertIsDisplayed()

        composeRule.runOnIdle {
            settings = settings.copy(
                roleplayNoBackgroundSkin = settings.roleplayNoBackgroundSkin.copy(
                    preset = RoleplayNoBackgroundSkinPreset.NOIR,
                ),
            )
        }
        assertDialogueColor(CharacterContent, Color(0xFFF4EDFF))
        assertEquals(Color(0xFFC1B3D5), textLayout("被引用的消息").layoutInput.style.color)
        assertEquals(Color(0xFFB7A0D6), textLayout("平静").layoutInput.style.color)
        assertColorClose(Color(0xFF171120), bubblePixel("character"))
        captureReviewImage("online-background-noir-quote")

        composeRule.runOnIdle { message = message.copy(content = StreamingContent, isStreaming = true) }
        assertStreamingBodyColor(StreamingContent, Color(0xFFF4EDFF))

        composeRule.runOnIdle { message = message.copy(messageStatus = MessageStatus.ERROR) }
        composeRule.onNodeWithText("本回合生成失败，可长按重回", useUnmergedTree = true).assertIsDisplayed()
        assertStreamingBodyColor(StreamingContent, Color(0xFFB3261E))
        assertColorClose(Color(0xFFFFE9E8), bubblePixel("character"))
        captureReviewImage("online-background-theme-streaming-error")

        composeRule.runOnIdle { message = message.copy(isStreaming = false) }
        assertDialogueColor(StreamingContent, Color(0xFFB3261E))
    }

    @Test
    fun missingLoadedBackdrop_switchKeepsExistingThemeBubble() {
        var settings by mutableStateOf(testSettings())
        val message = dialogueMessage("user", RoleplaySpeaker.USER, UserContent)
        composeRule.setContent { OnlinePage(settings, roleplayTestBackdropState(), listOf(message)) }

        val initialWidth = bubble("user").fetchSemanticsNode().boundsInRoot.width
        assertColorClose(Color(0xFFDDEEDD), bubblePixel("user"))
        composeRule.runOnIdle {
            settings = settings.copy(
                roleplayNoBackgroundSkin = settings.roleplayNoBackgroundSkin.copy(
                    onlineBackgroundGlassEnabled = false,
                ),
            )
        }

        assertEquals(initialWidth, bubble("user").fetchSemanticsNode().boundsInRoot.width, 0.5f)
        assertColorClose(Color(0xFFDDEEDD), bubblePixel("user"))
        assertEquals(Color(0xFF2D4A2B), textLayout(UserContent).layoutInput.style.color)
    }

    private fun bubble(id: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("roleplay_dialogue_bubble_$id", useUnmergedTree = true).assertIsDisplayed()

    private fun bubblePixel(id: String): Color {
        val pixels = bubble(id).captureToImage().toPixelMap()
        val inset = with(composeRule.density) { 4.dp.roundToPx() }
        return pixels[inset, pixels.height / 2]
    }

    private fun assertThemeCorner(id: String, expected: Color) {
        val pixels = bubble(id).captureToImage().toPixelMap()
        val inset = with(composeRule.density) { 6.dp.roundToPx() }
        assertColorClose(expected, pixels[inset, inset])
    }

    private fun pageBackgroundPixel(): Color {
        val pixels = composeRule.onNodeWithTag(PageTag).captureToImage().toPixelMap()
        return pixels[1, pixels.height / 2]
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun assertDialogueColor(text: String, expected: Color) {
        val spans = textLayout(text).layoutInput.text.spanStyles
        assertTrue("角色正文应保留明确的文字颜色", spans.isNotEmpty())
        assertTrue(spans.all { it.item.color == expected })
    }

    private fun assertStreamingBodyColor(text: String, expected: Color) {
        val pixels = composeRule.onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .captureToImage()
            .toPixelMap()
        var matchingPixels = 0
        // 正文跨多行，只检查上半部，避免最后一行光标掩盖正文用错颜色。
        for (y in 0 until pixels.height / 2) {
            for (x in 0 until pixels.width) {
                if (colorsMatch(expected, pixels[x, y])) matchingPixels += 1
            }
        }
        assertTrue("流式正文应使用 $expected，匹配像素数=$matchingPixels", matchingPixels > 12)
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertTrue("expected=$expected, actual=$actual", colorsMatch(expected, actual))
    }

    private fun captureReviewImage(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val directory = File(composeRule.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = composeRule.onNodeWithTag(PageTag).captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun colorsMatch(expected: Color, actual: Color): Boolean =
        abs(expected.red - actual.red) < 0.03f &&
            abs(expected.green - actual.green) < 0.03f &&
            abs(expected.blue - actual.blue) < 0.03f

    @Composable
    private fun OnlinePage(
        settings: AppSettings,
        backdrop: ImmersiveBackdropState,
        messages: List<RoleplayMessageUiModel>,
        scenario: RoleplayScenario = testScenario,
    ) {
        MaterialTheme {
            Box(Modifier.widthIn(max = 360.dp).fillMaxSize().testTag(PageTag)) {
                RoleplayOnlinePhoneContent(
                    scenario = scenario,
                    assistant = null,
                    settings = settings,
                    backdropState = backdrop,
                    contextStatus = RoleplayContextStatus(),
                    groupParticipants = emptyList(),
                    messages = messages,
                    suggestions = emptyList(),
                    input = "",
                    inputFocusToken = 0L,
                    replyToMessageId = "",
                    replyToPreview = "",
                    replyToSpeakerName = "",
                    isSending = false,
                    isGeneratingSuggestions = false,
                    suggestionErrorMessage = null,
                    pendingMemoryProposal = null,
                    snackbarHostState = remember { SnackbarHostState() },
                    onInputChange = {},
                    onGenerateSuggestions = {},
                    onApplySuggestion = {},
                    onClearSuggestions = {},
                    onRetryTurn = {},
                    onRetryAiPhoto = { _, _ -> },
                    onEditUserMessage = {},
                    onQuoteMessage = { _, _, _ -> },
                    onPokeMessageAvatar = {},
                    onClearQuotedMessage = {},
                    onRecallMessage = {},
                    onScreenshotChat = {},
                    onOpenDiary = {},
                    onOpenVoiceMessage = {},
                    onOpenTransferPlay = {},
                    onOpenInvitePlay = {},
                    onOpenGiftPlay = {},
                    onOpenTaskPlay = {},
                    onOpenPunishPlay = {},
                    onConfirmTransferReceipt = {},
                    onSend = {},
                    onRequestGroupParticipantReply = {},
                    onCancelSending = {},
                    onApprovePendingMemoryProposal = {},
                    onRejectPendingMemoryProposal = {},
                    onOpenPhoneCheck = {},
                    onOpenMoments = {},
                    onOpenVideoCall = {},
                    onOpenMailbox = {},
                    onOpenWallet = {},
                    onOpenSettings = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    private fun loadedBackdrop(): ImmersiveBackdropState {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(90, 35, 150))
        }
        return roleplayTestBackdropState().copy(imageBitmap = bitmap.asImageBitmap())
    }

    private fun testSettings() = AppSettings(
        roleplayImmersiveMode = RoleplayImmersiveMode.NONE,
        showRoleplayAiHelper = false,
        roleplayNoBackgroundSkin = RoleplayNoBackgroundSkinSettings(
            maxWidthPercent = 64,
            bubbleRadiusDp = 4,
            bubblePaddingHorizontalDp = 20,
            bubblePaddingVerticalDp = 6,
        ),
    )

    private fun dialogueMessage(id: String, speaker: RoleplaySpeaker, content: String) = RoleplayMessageUiModel(
        uiItemId = id,
        sourceMessageId = id,
        contentType = RoleplayContentType.DIALOGUE,
        speaker = speaker,
        speakerName = if (speaker == RoleplaySpeaker.USER) "我" else "测试角色",
        content = content,
    )

    private companion object {
        const val PageTag = "online_bubble_test_page"
        const val UserContent = "这是一段足够长的用户消息，用来验证主题气泡的最大宽度。"
        const val CharacterContent = "这是一段足够长的角色回复，用来检查关闭玻璃后的显示。"
        const val StreamingContent = "消息正在继续生成；这是一段足够长、可以跨行验证正文颜色的流式文字。"
        val testScenario = RoleplayScenario(
            id = "online-bubble-test",
            characterDisplayNameOverride = "测试角色",
            backgroundUri = "content://test/background",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableTimeAwareness = false,
        )
    }
}
