package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayVideoCallScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noPortrait_latestMessageAndCallControlsRemainVisible() {
        showCall()

        composeRule.onNodeWithTag("video_call_character_avatar").assertIsDisplayed()
        composeRule.onNodeWithText("你好呀").assertIsDisplayed()
        composeRule.onNodeWithText("刚刚散步回来。").assertIsDisplayed()
        composeRule.onNodeWithTag("video_call_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("video_call_hangup")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        captureReviewImage("video-light-messages")
    }

    @Test
    fun largeFontAndLongName_sendCancelAndHangupKeepWorking() {
        var input by mutableStateOf("")
        var isSending by mutableStateOf(false)
        var sentInput = ""
        var cancelCount = 0
        var hangupCount = 0
        showCall(
            fontScale = 1.5f,
            dark = true,
            characterName = "陆星野和他的很长很长的显示名称",
            input = { input },
            isSending = { isSending },
            onInputChange = { input = it },
            onSend = {
                sentInput = input
                isSending = true
            },
            onCancelSending = {
                cancelCount += 1
                isSending = false
            },
            onHangup = { hangupCount += 1 },
        )

        composeRule.onNodeWithTag("video_call_character_name").assertIsDisplayed()
        captureReviewImage("video-dark-large-font")
        composeRule.onNodeWithTag("video_call_input").performTextInput("今天过得怎么样")
        composeRule.onNodeWithContentDescription("发送消息").performClick()
        composeRule.onNodeWithContentDescription("取消发送").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("挂断视频通话").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("今天过得怎么样", sentInput)
            assertEquals(1, cancelCount)
            assertEquals(1, hangupCount)
        }
    }

    @Test
    fun focusTokenRequestsInputFocus_andBackHangsUp() {
        var focusToken by mutableStateOf(0L)
        var hangupCount = 0
        showCall(
            focusToken = { focusToken },
            onHangup = { hangupCount += 1 },
        )

        composeRule.runOnIdle { focusToken = 1L }
        composeRule.onNodeWithTag("video_call_input").assertIsFocused()
        composeRule.onNodeWithTag("video_call_hangup").assertIsDisplayed()
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1, hangupCount)
        }
    }

    private fun showCall(
        fontScale: Float = 1f,
        dark: Boolean = false,
        characterName: String = "陆星野",
        input: () -> String = { "" },
        focusToken: () -> Long = { 0L },
        isSending: () -> Boolean = { false },
        onInputChange: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onCancelSending: () -> Unit = {},
        onHangup: () -> Unit = {},
    ) {
        val startedAt = System.currentTimeMillis() - 21_000L
        val messages = listOf(
            RoleplayMessageUiModel(
                uiItemId = "user-1",
                sourceMessageId = "user-1",
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = "lucky",
                content = "你好呀",
                createdAt = startedAt + 1_000L,
            ),
            RoleplayMessageUiModel(
                uiItemId = "character-1",
                sourceMessageId = "character-1",
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.CHARACTER,
                speakerName = characterName,
                content = "刚刚散步回来。",
                createdAt = startedAt + 2_000L,
            ),
        )
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                ChatAppTheme(darkTheme = dark) {
                    Box(modifier = Modifier.widthIn(max = 360.dp).fillMaxSize()) {
                        RoleplayVideoCallScreen(
                            scenario = RoleplayScenario(
                                characterDisplayNameOverride = characterName,
                                userDisplayNameOverride = "lucky",
                            ),
                            assistant = Assistant(name = characterName),
                            settings = AppSettings(roleplayImmersiveMode = RoleplayImmersiveMode.NONE),
                            messages = messages,
                            input = input(),
                            inputFocusToken = focusToken(),
                            isSending = isSending(),
                            activeVideoCallStartedAt = startedAt,
                            isVideoCallActive = true,
                            noticeMessage = null,
                            errorMessage = null,
                            onClearNoticeMessage = {},
                            onClearErrorMessage = {},
                            onInputChange = onInputChange,
                            onSend = onSend,
                            onCancelSending = onCancelSending,
                            onHangup = onHangup,
                        )
                    }
                }
            }
        }
    }

    private fun captureReviewImage(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val directory = File(composeRule.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = composeRule.onNodeWithTag("video_call_screen").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
