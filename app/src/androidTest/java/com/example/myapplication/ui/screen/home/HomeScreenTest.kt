package com.example.myapplication.ui.screen.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.ui.theme.ChatAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingConfig_primaryActionOpensSettings() {
        var openSettingsCalls = 0
        var openRoleplayCalls = 0
        showHome(
            settings = AppSettings(),
            onOpenSettings = { openSettingsCalls++ },
            onOpenRoleplay = { openRoleplayCalls++ },
        )

        composeRule.onNodeWithText("配置提供商")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, openSettingsCalls)
            assertEquals(0, openRoleplayCalls)
        }
    }

    @Test
    fun completedConfig_primaryActionOpensRoleplay() {
        var openSettingsCalls = 0
        var openRoleplayCalls = 0
        showHome(
            settings = AppSettings(
                baseUrl = "https://example.com",
                apiKey = "test-key",
                selectedModel = "test-model",
            ),
            onOpenSettings = { openSettingsCalls++ },
            onOpenRoleplay = { openRoleplayCalls++ },
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_open_roleplay))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        captureReviewImage("home-light-ready")
        composeRule.runOnIdle {
            assertEquals(1, openRoleplayCalls)
            assertEquals(0, openSettingsCalls)
        }
    }

    @Test
    fun missingConfig_largeFont_bothSettingsActionsRemainReachable() {
        var settingsCalls = 0
        var roleplayCalls = 0
        showHome(
            settings = AppSettings(),
            fontScale = 1.6f,
            compact = true,
            darkTheme = { true },
            onOpenSettings = { settingsCalls++ },
            onOpenRoleplay = { roleplayCalls++ },
        )

        composeRule.onNodeWithText("配置提供商")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_open_settings))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        captureReviewImage("home-dark-large-font")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_config_hint))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, settingsCalls)
            assertEquals(0, roleplayCalls)
        }
    }

    @Test
    fun longConnectionNames_allThemes_keepFullTextAndPrimaryNavigation() {
        var colorTheme by mutableStateOf(AppColorTheme.MATCHA)
        var dark by mutableStateOf(false)
        var roleplayCalls = 0
        val provider = ProviderSettings(
            id = "test-provider",
            name = "用于长文本回归的提供商名称".repeat(3),
            selectedModel = "model-with-a-long-readable-identifier-".repeat(3),
            baseUrl = "https://example.com",
            apiKey = "test-key",
        )
        showHome(
            settings = AppSettings(providers = listOf(provider), selectedProviderId = provider.id),
            fontScale = 1.6f,
            compact = true,
            appColorTheme = { colorTheme },
            darkTheme = { dark },
            onOpenRoleplay = { roleplayCalls++ },
        )

        for (theme in AppColorTheme.entries) {
            for (isDark in listOf(false, true)) {
                composeRule.runOnIdle {
                    colorTheme = theme
                    dark = isDark
                }
                assertFullTextWraps(provider.name)
                assertFullTextWraps(provider.selectedModel)
                composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_open_roleplay))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
                    .performClick()
            }
        }
        composeRule.runOnIdle { assertEquals(AppColorTheme.entries.size * 2, roleplayCalls) }
    }

    private fun showHome(
        settings: AppSettings,
        fontScale: Float = 1f,
        compact: Boolean = false,
        appColorTheme: () -> AppColorTheme = { AppColorTheme.MATCHA },
        darkTheme: () -> Boolean = { false },
        onOpenSettings: () -> Unit = {},
        onOpenRoleplay: () -> Unit = {},
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ChatAppTheme(appColorTheme = appColorTheme(), darkTheme = darkTheme()) {
                    Box(if (compact) Modifier.width(320.dp).height(560.dp) else Modifier.fillMaxSize()) {
                        HomeScreen(
                            storedSettings = settings,
                            onOpenSettings = onOpenSettings,
                            onOpenRoleplay = onOpenRoleplay,
                        )
                    }
                }
            }
        }
    }

    private fun assertFullTextWraps(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        assertTrue(results.single().lineCount > 1)
        assertFalse(results.single().hasVisualOverflow)
    }

    private fun captureReviewImage(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiScreenshots") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val bitmap = composeRule.onNodeWithTag("home_screen").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
