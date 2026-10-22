package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayContentType
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class OfflineRoleplayScreenshotE04Test : OfflineRoleplayRenderTest() {
    @Test fun e04_scene_matcha_light() = sceneTheme(AppColorTheme.MATCHA, false)
    @Test fun e04_scene_matcha_dark() = sceneTheme(AppColorTheme.MATCHA, true)
    @Test fun e04_scene_vanilla_light() = sceneTheme(AppColorTheme.VANILLA, false)
    @Test fun e04_scene_vanilla_dark() = sceneTheme(AppColorTheme.VANILLA, true)
    @Test fun e04_scene_ocean_light() = sceneTheme(AppColorTheme.OCEAN, false)
    @Test fun e04_scene_ocean_dark() = sceneTheme(AppColorTheme.OCEAN, true)

    @Test fun e04_scene_empty() {
        stopAnimationClock()
        val case = ScreenshotCases.require("E04_scene-empty")
        renderOfflinePage(case, messages = emptyList())
        advanceAnimationClock(160)
        captureCase(case)
    }

    @Test fun e04_scene_narrow_light() {
        narrow()
        val case = ScreenshotCases.require("E04_scene-narrow-light")
        renderOfflinePage(case, scenario = OfflineRoleplayFixture.DialogueScenario.copy(characterDisplayNameOverride = OfflineRoleplayFixture.LongName),
            suggestions = OfflineRoleplayFixture.Suggestions)
        captureCase(case)
    }

    @Test fun e04_scene_narrow_dark() {
        narrow()
        stopAnimationClock()
        val case = ScreenshotCases.require("E04_scene-narrow-dark")
        renderOfflinePage(case, dark = true, scenario = OfflineRoleplayFixture.LongformScenario,
            messages = listOf(ScreenshotFixture.characterMessage("offline-sending", ScreenshotFixture.StreamingPartialText,
                isStreaming = true).copy(contentType = RoleplayContentType.LONGFORM)), sending = true, presence = true)
        advanceAnimationClock(160)
        captureCase(case)
    }

    @Test fun e04_scene_background() {
        val case = ScreenshotCases.require("E04_scene-background")
        renderOfflinePage(case, image = ScreenshotFixture.syntheticBackdrop())
        captureCase(case)
    }

    @Test fun e04_scene_suggestions() {
        val case = ScreenshotCases.require("E04_scene-suggestions")
        renderOfflinePage(case, suggestions = OfflineRoleplayFixture.Suggestions)
        captureCase(case)
    }

    @Test fun e04_reading_mixed_background() {
        val case = ScreenshotCases.require("E04_reading-mixed-background")
        renderReadingPage(case, withBackground = true)
        composeRule.onNodeWithContentDescription("关闭").assertIsDisplayed()
        captureCase(case)
    }

    @Test fun e04_reading_empty_dark() {
        val case = ScreenshotCases.require("E04_reading-empty-dark")
        renderReadingPage(case, dark = true, messages = emptyList())
        captureCase(case)
    }

    @Test fun e04_reading_narrow_dark() {
        narrow()
        val case = ScreenshotCases.require("E04_reading-narrow-dark")
        renderReadingPage(case, dark = true, title = OfflineRoleplayFixture.LongName,
            messages = listOf(ScreenshotFixture.characterMessage("reading-long", ScreenshotFixture.LongReplyText,
                emotion = "温柔又有些期待").copy(speakerName = OfflineRoleplayFixture.LongName)))
        captureCase(case)
    }

    private fun sceneTheme(theme: AppColorTheme, dark: Boolean) {
        val case = ScreenshotCases.require("E04_scene-${theme.storageValue}-${if (dark) "dark" else "light"}")
        renderOfflinePage(case, theme, dark)
        composeRule.onNodeWithTag("roleplay_scene_title").assertIsDisplayed()
        captureCase(case)
    }

    private fun narrow() {
        RuntimeEnvironment.setQualifiers(ScreenshotFixture.NarrowQualifiers)
        useNarrowFontScale()
    }
}
