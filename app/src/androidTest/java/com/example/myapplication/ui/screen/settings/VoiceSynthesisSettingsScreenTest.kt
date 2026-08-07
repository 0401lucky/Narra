package com.example.myapplication.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.VoiceProfile
import com.example.myapplication.model.VoiceProfileMode
import com.example.myapplication.model.VoiceSynthesisSettings
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun renderAssistantVoiceProfileSheet(
        initialProfile: VoiceProfile,
        initialVoiceCloneImportError: String = "",
        onSaveProfile: (VoiceProfile) -> Unit,
    ) {
        composeRule.setContent {
            ChatAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AssistantVoiceProfileSheet(
                        assistant = TEST_ASSISTANT,
                        voiceSynthesisSettings = VoiceSynthesisSettings(),
                        initialProfile = initialProfile,
                        initialVoiceCloneImportError = initialVoiceCloneImportError,
                        isTesting = false,
                        onDismissRequest = {},
                        onTestProfile = {},
                        onSaveProfile = onSaveProfile,
                    )
                }
            }
        }
    }

    private companion object {
        val TEST_ASSISTANT = Assistant(
            id = "voice-profile-sheet-test-assistant",
            name = "设备测试角色",
        )
    }
}
