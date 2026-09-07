package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.repository.SavedAudioFile
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.VoiceProfile
import com.example.myapplication.model.VoiceProfileMode
import com.example.myapplication.model.VoiceSynthesisSettings
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceSynthesisSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun voiceDesignLongContent_switchesModeAndSavesLatestDraft() {
        val initialPrompt = List(32) { index ->
            "初始音色描述第 ${index + 1} 行，保持温柔、清晰且富有层次。"
        }.joinToString("\n")
        val updatedPrompt = List(28) { index ->
            "更新后的音色描述第 ${index + 1} 行，语速舒缓，情绪自然。"
        }.joinToString("\n")
        var savedProfile: VoiceProfile? = null

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = initialPrompt,
            ),
            onSaveProfile = { savedProfile = it },
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_PROMPT_TAG)
            .performScrollTo()
            .performTextReplacement(updatedPrompt)
        composeRule.onNodeWithTag("assistant_voice_profile_mode_disabled")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("assistant_voice_profile_mode_voice_design")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ASSISTANT_VOICE_PROFILE_SAVE_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            val saved = checkNotNull(savedProfile)
            check(saved.mode == VoiceProfileMode.VOICE_DESIGN)
            check(saved.voiceDesignPrompt == updatedPrompt)
        }
    }

    @Test
    fun voiceCloneImportError_scrollsToSaveAndKeepsCloneDraft() {
        val importError = List(12) { index ->
            "声音样本导入失败，第 ${index + 1} 项校验信息需要用户处理。"
        }.joinToString("\n")
        val initialProfile = VoiceProfile(
            mode = VoiceProfileMode.VOICE_CLONE,
            voiceCloneSamplePath = "voice-clone/sample.mp3",
            voiceCloneSampleMimeType = "audio/mpeg",
            voiceCloneSampleFileName = "用于设备测试的长名称声音克隆样本.mp3",
            voiceCloneSampleSizeBytes = 4_096L,
        )
        var savedProfile: VoiceProfile? = null

        renderAssistantVoiceProfileSheet(
            initialProfile = initialProfile,
            initialVoiceCloneImportError = importError,
            onSaveProfile = { savedProfile = it },
        )

        composeRule.onNodeWithText(importError)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ASSISTANT_VOICE_PROFILE_SAVE_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            val saved = checkNotNull(savedProfile)
            check(saved.mode == VoiceProfileMode.VOICE_CLONE)
            check(saved.voiceCloneSamplePath == initialProfile.voiceCloneSamplePath)
            check(saved.voiceCloneSampleFileName == initialProfile.voiceCloneSampleFileName)
        }
    }

    @Test
    fun presetVoiceSelector_selectsVoiceAndSavesFromScrollableSheet() {
        var savedProfile: VoiceProfile? = null

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.PRESET,
                presetVoiceId = "茉莉",
            ),
            onSaveProfile = { savedProfile = it },
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_PRESET_SELECTOR_TAG)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("冰糖")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ASSISTANT_VOICE_PROFILE_SAVE_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            val saved = checkNotNull(savedProfile)
            check(saved.mode == VoiceProfileMode.PRESET)
            check(saved.presetVoiceId == "冰糖")
        }
    }

    @Test
    fun voiceDesignTest_usesLatestPromptAndShowsSuccessDialogWhileSheetRemainsOpen() {
        var previewedProfile: VoiceProfile? = null
        val previewFile = createPreviewFile("voice-design-preview-test.wav")
        val playback = FakeVoicePreviewPlayback()

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "低沉、清晰的中文男声。",
            ),
            onPreviewProfile = { _, profile, _ ->
                previewedProfile = profile
                SavedAudioFile(
                    path = previewFile.absolutePath,
                    mimeType = "audio/wav",
                    fileName = previewFile.name,
                )
            },
            previewPlaybackFactory = VoicePreviewPlaybackFactory { playback },
            onSaveProfile = {},
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_PROMPT_TAG)
            .performScrollTo()
            .performTextReplacement("清晰、克制、温暖的中文男声，语速舒缓。")
        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            previewedProfile != null
        }
        check(composeRule.onAllNodesWithText("音色测试成功").fetchSemanticsNodes().isEmpty())
        composeRule.runOnIdle { playback.dispatchStarted() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("音色测试成功").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle {
            check(previewedProfile?.voiceDesignPrompt == "清晰、克制、温暖的中文男声，语速舒缓。")
            check(playback.startedPath == previewFile.absolutePath)
            previewFile.delete()
        }
    }

    @Test
    fun voiceDesignTest_withoutApiKeyShowsFailureInsideSheet() {
        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "自然、清晰的中文声音。",
            ),
            voiceSynthesisSettings = VoiceSynthesisSettings(apiKey = ""),
            onSaveProfile = {},
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("音色测试失败")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("请先填写 MiMo API Key 后再测试音色")
            .get(1)
            .assertIsDisplayed()
    }

    @Test
    fun voiceDesignPreview_canBeSavedAsCloneDraft() {
        var savedProfile: VoiceProfile? = null
        val previewFile = createPreviewFile("voice-design-fixed-test.wav")
        val playback = FakeVoicePreviewPlayback()

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "清晰、自然的中文女声。",
            ),
            onPreviewProfile = { _, _, _ ->
                SavedAudioFile(
                    path = previewFile.absolutePath,
                    mimeType = "audio/wav",
                    fileName = previewFile.name,
                )
            },
            previewPlaybackFactory = VoicePreviewPlaybackFactory { playback },
            onSaveProfile = { savedProfile = it },
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.startedPath.isNotBlank() }
        composeRule.runOnIdle { playback.dispatchStarted() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("音色测试成功").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("保存为固定音色")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("固定音色已保存").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithTag(ASSISTANT_VOICE_PROFILE_SAVE_TAG)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            val saved = checkNotNull(savedProfile)
            check(saved.mode == VoiceProfileMode.VOICE_CLONE)
            check(saved.voiceCloneSamplePath.isNotBlank())
            check(File(saved.voiceCloneSamplePath).isFile)
            File(saved.voiceCloneSamplePath).delete()
            previewFile.delete()
        }
    }

    @Test
    fun voiceDesignPreview_releasesPlaybackWhenSheetIsDismissed() {
        val previewFile = createPreviewFile("voice-design-release-test.wav")
        val playback = FakeVoicePreviewPlayback()

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "自然、平静的中文声音。",
            ),
            onPreviewProfile = { _, _, _ ->
                SavedAudioFile(
                    path = previewFile.absolutePath,
                    mimeType = "audio/wav",
                    fileName = previewFile.name,
                )
            },
            previewPlaybackFactory = VoicePreviewPlaybackFactory { playback },
            onSaveProfile = {},
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.startedPath.isNotBlank() }
        composeRule.runOnIdle { playback.dispatchStarted() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("音色测试成功").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("稍后").performClick()
        composeRule.onNodeWithText("取消")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.releaseCount == 1 }
        previewFile.delete()
    }

    @Test
    fun voiceDesignPreview_completionReleasesPlayerAndKeepsSuccessResult() {
        val previewFile = createPreviewFile("voice-design-complete-test.wav")
        val playback = FakeVoicePreviewPlayback()

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "自然、平静的中文声音。",
            ),
            onPreviewProfile = { _, _, _ ->
                SavedAudioFile(
                    path = previewFile.absolutePath,
                    mimeType = "audio/wav",
                    fileName = previewFile.name,
                )
            },
            previewPlaybackFactory = VoicePreviewPlaybackFactory { playback },
            onSaveProfile = {},
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.startedPath.isNotBlank() }
        composeRule.runOnIdle { playback.dispatchStarted() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("音色测试成功").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { playback.dispatchCompleted() }

        composeRule.onNodeWithText("音色测试成功").assertIsDisplayed()
        composeRule.onNodeWithText("保存为固定音色").assertIsDisplayed()
        composeRule.runOnIdle {
            check(playback.releaseCount == 1)
            previewFile.delete()
        }
    }

    @Test
    fun voiceDesignPreview_deletesAudioAfterPlaybackFailure() {
        val previewFile = createPreviewFile("voice-design-playback-error-test.wav")
        val playback = FakeVoicePreviewPlayback()

        renderAssistantVoiceProfileSheet(
            initialProfile = VoiceProfile(
                mode = VoiceProfileMode.VOICE_DESIGN,
                voiceDesignPrompt = "自然、平静的中文声音。",
            ),
            onPreviewProfile = { _, _, _ ->
                SavedAudioFile(
                    path = previewFile.absolutePath,
                    mimeType = "audio/wav",
                    fileName = previewFile.name,
                )
            },
            previewPlaybackFactory = VoicePreviewPlaybackFactory { playback },
            onSaveProfile = {},
        )

        composeRule.onNodeWithTag(ASSISTANT_VOICE_DESIGN_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.startedPath.isNotBlank() }
        composeRule.runOnIdle { playback.dispatchError() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("音色测试失败").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            check(playback.releaseCount == 1)
            check(!previewFile.exists())
        }
    }

    private fun renderAssistantVoiceProfileSheet(
        initialProfile: VoiceProfile,
        initialVoiceCloneImportError: String = "",
        voiceSynthesisSettings: VoiceSynthesisSettings = VoiceSynthesisSettings(
            apiKey = "test-mimo-key",
        ),
        onPreviewProfile: suspend (VoiceSynthesisSettings, VoiceProfile, String) -> SavedAudioFile = { _, _, _ ->
            error("当前测试不应调用试听回调")
        },
        previewPlaybackFactory: VoicePreviewPlaybackFactory = VoicePreviewPlaybackFactory {
            error("当前测试不应创建试听播放器")
        },
        onSaveProfile: (VoiceProfile) -> Unit,
    ) {
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            ChatAppTheme {
                if (visible) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AssistantVoiceProfileSheet(
                            assistant = TEST_ASSISTANT,
                            voiceSynthesisSettings = voiceSynthesisSettings,
                            initialProfile = initialProfile,
                            initialVoiceCloneImportError = initialVoiceCloneImportError,
                            isTesting = false,
                            onDismissRequest = { visible = false },
                            onTestProfile = {},
                            onPreviewProfile = onPreviewProfile,
                            previewPlaybackFactory = previewPlaybackFactory,
                            onSaveProfile = { profile ->
                                onSaveProfile(profile)
                                visible = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun createPreviewFile(name: String): File {
        return File(
            composeRule.activity.cacheDir,
            "$MIMO_VOICE_DESIGN_PREVIEW_CACHE_DIR/$name",
        ).apply {
            parentFile?.mkdirs()
            writeBytes(validWavBytes())
        }
    }

    private companion object {
        val TEST_ASSISTANT = Assistant(
            id = "voice-profile-sheet-test-assistant",
            name = "设备测试角色",
        )

        fun validWavBytes(): ByteArray {
            val sampleRate = 8_000
            val sampleCount = 800
            val dataSize = sampleCount * 2
            val bytes = ByteArray(44 + dataSize)
            fun writeAscii(offset: Int, value: String) {
                value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
            }
            fun writeInt(offset: Int, value: Int) {
                bytes[offset] = value.toByte()
                bytes[offset + 1] = (value shr 8).toByte()
                bytes[offset + 2] = (value shr 16).toByte()
                bytes[offset + 3] = (value shr 24).toByte()
            }
            fun writeShort(offset: Int, value: Int) {
                bytes[offset] = value.toByte()
                bytes[offset + 1] = (value shr 8).toByte()
            }
            writeAscii(0, "RIFF")
            writeInt(4, 36 + dataSize)
            writeAscii(8, "WAVE")
            writeAscii(12, "fmt ")
            writeInt(16, 16)
            writeShort(20, 1)
            writeShort(22, 1)
            writeInt(24, sampleRate)
            writeInt(28, sampleRate * 2)
            writeShort(32, 2)
            writeShort(34, 16)
            writeAscii(36, "data")
            writeInt(40, dataSize)
            return bytes
        }

    }

    private class FakeVoicePreviewPlayback : VoicePreviewPlayback {
        var startedPath: String = ""
            private set
        var releaseCount: Int = 0
            private set

        private var onStarted: (() -> Unit)? = null
        private var onCompleted: (() -> Unit)? = null
        private var onError: ((Throwable?) -> Unit)? = null
        private var released = false

        override fun prepareAndPlay(
            path: String,
            onStarted: () -> Unit,
            onCompleted: () -> Unit,
            onError: (Throwable?) -> Unit,
        ) {
            if (released) {
                onError(IllegalStateException("测试播放器已释放"))
                return
            }
            startedPath = path
            this.onStarted = onStarted
            this.onCompleted = onCompleted
            this.onError = onError
        }

        override fun release() {
            if (released) return
            released = true
            releaseCount += 1
            onStarted = null
            onCompleted = null
            onError = null
        }

        fun dispatchStarted() {
            onStarted?.invoke()
        }

        fun dispatchCompleted() {
            val callback = onCompleted
            release()
            callback?.invoke()
        }

        fun dispatchError() {
            val callback = onError
            release()
            callback?.invoke(IllegalStateException("测试播放器失败"))
        }
    }
}
