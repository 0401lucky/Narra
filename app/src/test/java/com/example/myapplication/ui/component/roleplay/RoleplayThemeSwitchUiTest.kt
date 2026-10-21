package com.example.myapplication.ui.component.roleplay

import android.graphics.Bitmap
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.ui.screenshot.ScreenshotCases
import com.example.myapplication.ui.screenshot.ScreenshotFixture
import com.example.myapplication.ui.screenshot.ScreenshotRenderTest
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.example.myapplication.ui.theme.readableRoleplayTextOnSurfaces
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fetchImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayThemeSwitchUiTest : ScreenshotRenderTest() {
    @Test
    fun liquidSkinWithGlassDisabled_darkWhiteImage_readsActualScrimmedTransparentSurfaces() {
        val image = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        val user = ScreenshotFixture.userMessage("liquid-user", "透明用户气泡")
        val character = ScreenshotFixture.characterMessage("liquid-character", "透明角色气泡")
        val narration = ScreenshotFixture.narrationMessage("liquid-narration", "透明旁白阅读面")
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H01_p03-online-roleplay-matcha-dark"), AppColorTheme.MATCHA, true,
            messages = listOf(user, character, narration), backdropImage = image,
            skin = RoleplayNoBackgroundSkinSettings(
                preset = com.example.myapplication.model.RoleplayNoBackgroundSkinPreset.LIQUID_GLASS,
                onlineBackgroundGlassEnabled = false,
            ),
        )

        listOf(user, character).forEach { message ->
            val pixels = composeRule.onNodeWithTag("roleplay_dialogue_bubble_${message.uiItemId}", useUnmergedTree = true)
                .fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
            val surface = pixels[8, pixels.height / 2]
            val layout = textLayout(message.content)
            val text = if (message == user) layout.layoutInput.style.color else layout.layoutInput.text.spanStyles.first().item.color
            assertTrue("actual Liquid text=$text surface=$surface", roleplayTextContrast(text, surface, surface) >= 4.5f)
        }

        val narrationNode = composeRule.onNodeWithText(narration.content, useUnmergedTree = true).fetchSemanticsNode()
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode()
        val pixels = stage.fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        val bounds = narrationNode.boundsInRoot
        val surface = pixels[(bounds.left - stage.boundsInRoot.left - 8).toInt(),
            (bounds.center.y - stage.boundsInRoot.top).toInt()]
        val text = textLayout(narration.content).layoutInput.style.color
        assertTrue("actual Liquid narration=$text surface=$surface", roleplayTextContrast(text, surface, surface) >= 4.5f)
    }

    @Test
    fun noirMentionPanelOnLightTheme_usesMaterialContentAndSelectionCallback() {
        val scheme = resolveAppColorScheme(AppColorTheme.MATCHA, false)
        var input = "@"
        setStage(ScreenshotCases.require("H01_p03-online-roleplay-matcha-light")) {
            val backdrop = ScreenshotFixture.backdropState(image = null)
            val colors = rememberImmersiveRoleplayColors(backdrop,
                RoleplayNoBackgroundSkinSettings(preset = com.example.myapplication.model.RoleplayNoBackgroundSkinPreset.NOIR))
            RoleplayInputBar(
                colors = colors, backdropState = backdrop, input = input, inputFocusToken = 0,
                isSending = false, onInputChange = { input = it }, onSend = {}, onCancel = null,
                onOpenSpecialPlay = {}, mentionCandidates = listOf(RoleplayMentionCandidate("sample", "小安")),
            )
        }
        assertEquals(scheme.onSurface, textLayout("@ 群成员").layoutInput.style.color)
        assertEquals(scheme.onSurfaceVariant, textLayout("收起").layoutInput.style.color)
        assertEquals(scheme.onPrimaryContainer, textLayout("小安").layoutInput.style.color)
        composeRule.onNodeWithText("小安").performClick()
        composeRule.runOnIdle { assertEquals("@小安 ", input) }
    }

    @Test
    fun realGlassPage_whiteImage_renderedBubbleSurfaceAndTextPassContrast_includingOpaqueError() {
        val image = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        val scheme = resolveAppColorScheme(AppColorTheme.MATCHA, true)
        val user = ScreenshotFixture.userMessage("contrast-user", "玻璃阅读对比")
        val error = ScreenshotFixture.characterMessage("contrast-error", "生成失败了，请重试。")
            .copy(messageStatus = com.example.myapplication.model.MessageStatus.ERROR)
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H01_p03-online-roleplay-matcha-dark"), AppColorTheme.MATCHA, true,
            messages = listOf(user, error), backdropImage = image, colorScheme = scheme,
        )
        listOf(user, error).forEach { message ->
            val bubble = composeRule.onNodeWithTag("roleplay_dialogue_bubble_${message.uiItemId}", useUnmergedTree = true)
                .fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
            val surface = bubble[8, bubble.height / 2]
            val layout = textLayout(message.content)
            val text = if (message == error) layout.layoutInput.text.spanStyles.first().item.color else layout.layoutInput.style.color
            assertTrue("rendered text=$text surface=$surface", roleplayTextContrast(text, surface, surface) >= 4.5f)
            if (message == error) assertEquals(scheme.errorContainer, surface)
        }
    }

    @Test
    fun realOnlinePage_recomposesBubbleTextOnSameCompositionThemeSwitch() {
        val initial = resolveAppColorScheme(AppColorTheme.MATCHA, false)
        val next = resolveAppColorScheme(AppColorTheme.OCEAN, true)
        val scheme = mutableStateOf(initial)
        val text = "主题切换中的合成消息"
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H01_p03-online-roleplay-matcha-light"), AppColorTheme.MATCHA, false,
            messages = listOf(ScreenshotFixture.userMessage("theme-switch", text)), colorSchemeState = scheme,
        )
        assertEquals(initial.onPrimaryContainer, textLayout(text).layoutInput.style.color)
        val bounds = composeRule.onNodeWithTag("roleplay_dialogue_bubble_shot-theme-switch", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { scheme.value = next }
        assertEquals(next.onPrimaryContainer, textLayout(text).layoutInput.style.color)
        assertNotEquals(initial.onPrimaryContainer, next.onPrimaryContainer)
        assertEquals(bounds.width, composeRule.onNodeWithTag("roleplay_dialogue_bubble_shot-theme-switch", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun realOnlinePage_fixedDynamicSchemeControlsUserBubbleText() {
        val dynamic = resolveAppColorScheme(AppColorTheme.VANILLA, false, lightColorScheme(
            primary = Color(0xFF315DA8), primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A42),
        ))
        val text = "固定动态主题消息"
        renderOnlineRoleplayPage(
            ScreenshotCases.require("H01_p03-online-roleplay-matcha-light"), AppColorTheme.VANILLA, false,
            messages = listOf(ScreenshotFixture.userMessage("dynamic", text)), colorScheme = dynamic,
        )
        assertEquals(dynamic.onPrimaryContainer, textLayout(text).layoutInput.style.color)
    }

    @Test
    fun paletteCacheKey_sameUriAndDimensions_invalidatesImageGenerationAndActualThemeWithoutRetainingBitmap() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val image = bitmap.asImageBitmap()
        val light = resolveAppColorScheme(AppColorTheme.MATCHA, false)
        val dark = resolveAppColorScheme(AppColorTheme.MATCHA, true)
        val before = immersivePaletteCacheKey("fixture://same", image, light, false)
        bitmap.eraseColor(android.graphics.Color.BLACK)
        val changed = immersivePaletteCacheKey("fixture://same", image, light, false)
        assertNotEquals(before, changed)
        assertNotEquals(changed, immersivePaletteCacheKey("fixture://same", image, dark, false))
        val another = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()
        assertNotEquals(changed, immersivePaletteCacheKey("fixture://same", another, light, false))
        assertTrue(ImmersivePaletteCacheKey::class.java.declaredFields.none {
            Bitmap::class.java.isAssignableFrom(it.type) || androidx.compose.ui.graphics.ImageBitmap::class.java.isAssignableFrom(it.type)
        })
    }

    @Test
    fun glassDialogueText_meetsContrastOnActualScrimSheenAndDarkenedSurface_forWhiteGrayBlackImages() {
        listOf(android.graphics.Color.WHITE, android.graphics.Color.GRAY, android.graphics.Color.BLACK).forEach { pixel ->
            val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(pixel) }.asImageBitmap()
            listOf(false, true).forEach { dark ->
                val scheme = resolveAppColorScheme(AppColorTheme.MATCHA, dark)
                val skin = resolveRoleplayNoBackgroundSkinSpec(RoleplayNoBackgroundSkinSettings(), scheme)
                val backdrop = ImmersiveBackdropState(bitmap, IntSize(720, 1600), deriveImmersiveGlassPalette(bitmap, scheme))
                val colors = resolveImmersiveRoleplayColors(backdrop, skin, scheme, true)
                listOf(colors.userText to colors.userBubbleBackground, colors.textPrimary to colors.characterBubbleBackground,
                    colors.characterAccent to colors.characterBubbleBackground).forEach { (text, surface) ->
                    roleplayDialogueReadingSurfaces(backdrop, surface).forEach { renderedSurface ->
                        assertTrue("pixel=$pixel dark=$dark text=$text surface=$renderedSurface", roleplayTextContrast(text, renderedSurface, renderedSurface) >= 4.5f)
                    }
                }
                assertEquals(scheme.errorContainer, colors.errorBackground)
                assertTrue(roleplayTextContrast(colors.errorText, colors.errorBackground, skin.background) >= 4.5f)
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
