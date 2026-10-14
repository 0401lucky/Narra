package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B01：13 套无背景皮肤 × 浅/深，固定用户/角色/旁白消息。
 *
 * 组件级用例，走真实无背景皮肤链路（RoleplayMessageItem + rememberImmersiveRoleplayColors）。
 * 浅/深只改变应用舞台底色，皮肤预设色本身由产品固定，现状如实记录。
 */
class SkinScreenshotB01Test : ScreenshotRenderTest() {

    @Test
    fun b01_narra_bubbles_light() = renderSkin("narra", "light")

    @Test
    fun b01_narra_bubbles_dark() = renderSkin("narra", "dark")

    @Test
    fun b01_wechat_bubbles_light() = renderSkin("wechat", "light")

    @Test
    fun b01_wechat_bubbles_dark() = renderSkin("wechat", "dark")

    @Test
    fun b01_qq_bubbles_light() = renderSkin("qq", "light")

    @Test
    fun b01_qq_bubbles_dark() = renderSkin("qq", "dark")

    @Test
    fun b01_telegram_bubbles_light() = renderSkin("telegram", "light")

    @Test
    fun b01_telegram_bubbles_dark() = renderSkin("telegram", "dark")

    @Test
    fun b01_kakao_bubbles_light() = renderSkin("kakao", "light")

    @Test
    fun b01_kakao_bubbles_dark() = renderSkin("kakao", "dark")

    @Test
    fun b01_retro_bubbles_light() = renderSkin("retro", "light")

    @Test
    fun b01_retro_bubbles_dark() = renderSkin("retro", "dark")

    @Test
    fun b01_polkadot_bubbles_light() = renderSkin("polkadot", "light")

    @Test
    fun b01_polkadot_bubbles_dark() = renderSkin("polkadot", "dark")

    @Test
    fun b01_pixel_bubbles_light() = renderSkin("pixel", "light")

    @Test
    fun b01_pixel_bubbles_dark() = renderSkin("pixel", "dark")

    @Test
    fun b01_rose_bubbles_light() = renderSkin("rose", "light")

    @Test
    fun b01_rose_bubbles_dark() = renderSkin("rose", "dark")

    @Test
    fun b01_noir_bubbles_light() = renderSkin("noir", "light")

    @Test
    fun b01_noir_bubbles_dark() = renderSkin("noir", "dark")

    @Test
    fun b01_y2k_bubbles_light() = renderSkin("y2k", "light")

    @Test
    fun b01_y2k_bubbles_dark() = renderSkin("y2k", "dark")

    @Test
    fun b01_imessage_bubbles_light() = renderSkin("imessage", "light")

    @Test
    fun b01_imessage_bubbles_dark() = renderSkin("imessage", "dark")

    @Test
    fun b01_liquidglass_bubbles_light() = renderSkin("liquidglass", "light")

    @Test
    fun b01_liquidglass_bubbles_dark() = renderSkin("liquidglass", "dark")

    private fun renderSkin(skinToken: String, shadeToken: String) {
        val case = ScreenshotCases.require("B01_$skinToken-bubbles-$shadeToken")
        val preset = RoleplayNoBackgroundSkinPreset.fromStorageValue(skinToken)
        assertEquals("皮肤 token 必须对应真实预设", skinToken, preset.storageValue)
        renderBubbleStage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = shadeToken == "dark",
            messages = ScreenshotFixture.stageMessages(),
            skin = RoleplayNoBackgroundSkinSettings(preset = preset),
        )
        composeRule.onNodeWithText(ScreenshotFixture.UserLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.NarrationLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }
}
