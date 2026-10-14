package com.example.myapplication.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ROLEPLAY_SKIN_MAX_MAX_WIDTH_PERCENT
import com.example.myapplication.model.ROLEPLAY_SKIN_MAX_PADDING_HORIZONTAL_DP
import com.example.myapplication.model.ROLEPLAY_SKIN_MAX_PADDING_VERTICAL_DP
import com.example.myapplication.model.ROLEPLAY_SKIN_MAX_RADIUS_DP
import com.example.myapplication.model.ROLEPLAY_SKIN_MIN_MAX_WIDTH_PERCENT
import com.example.myapplication.model.ROLEPLAY_SKIN_MIN_PADDING_HORIZONTAL_DP
import com.example.myapplication.model.ROLEPLAY_SKIN_MIN_PADDING_VERTICAL_DP
import com.example.myapplication.model.ROLEPLAY_SKIN_MIN_RADIUS_DP
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B03：自定义尺寸边界，按维度分别覆盖，不做全维度笛卡尔积。
 *
 * 每个用例只把一个维度推到边界（值取自生产常量），其余保持默认皮肤，
 * 组件级走真实无背景皮肤链路。
 */
class BubbleScreenshotB03Test : ScreenshotRenderTest() {

    @Test
    fun b03_radius_min() = renderDimensionCase(
        "B03_radius-min",
        RoleplayNoBackgroundSkinSettings(bubbleRadiusDp = ROLEPLAY_SKIN_MIN_RADIUS_DP),
    )

    @Test
    fun b03_radius_max() = renderDimensionCase(
        "B03_radius-max",
        RoleplayNoBackgroundSkinSettings(bubbleRadiusDp = ROLEPLAY_SKIN_MAX_RADIUS_DP),
    )

    @Test
    fun b03_width_min() = renderDimensionCase(
        "B03_width-min", RoleplayNoBackgroundSkinSettings(maxWidthPercent = ROLEPLAY_SKIN_MIN_MAX_WIDTH_PERCENT),
    )

    @Test
    fun b03_width_max() = renderDimensionCase(
        "B03_width-max", RoleplayNoBackgroundSkinSettings(maxWidthPercent = ROLEPLAY_SKIN_MAX_MAX_WIDTH_PERCENT),
    )

    @Test
    fun b03_padding_horizontal_min() = renderDimensionCase(
        "B03_padding-horizontal-min",
        RoleplayNoBackgroundSkinSettings(bubblePaddingHorizontalDp = ROLEPLAY_SKIN_MIN_PADDING_HORIZONTAL_DP),
    )

    @Test
    fun b03_padding_horizontal_max() = renderDimensionCase(
        "B03_padding-horizontal-max",
        RoleplayNoBackgroundSkinSettings(bubblePaddingHorizontalDp = ROLEPLAY_SKIN_MAX_PADDING_HORIZONTAL_DP),
    )

    @Test
    fun b03_padding_vertical_min() = renderDimensionCase(
        "B03_padding-vertical-min",
        RoleplayNoBackgroundSkinSettings(bubblePaddingVerticalDp = ROLEPLAY_SKIN_MIN_PADDING_VERTICAL_DP),
    )

    @Test
    fun b03_padding_vertical_max() = renderDimensionCase(
        "B03_padding-vertical-max",
        RoleplayNoBackgroundSkinSettings(bubblePaddingVerticalDp = ROLEPLAY_SKIN_MAX_PADDING_VERTICAL_DP),
    )

    @Test
    fun b03_tail_on() = renderDimensionCase(
        "B03_tail-on",
        RoleplayNoBackgroundSkinSettings(showBubbleTail = true),
    )

    @Test
    fun b03_tail_off() = renderDimensionCase(
        "B03_tail-off",
        RoleplayNoBackgroundSkinSettings(showBubbleTail = false),
    )

    private fun renderDimensionCase(caseId: String, skin: RoleplayNoBackgroundSkinSettings) {
        val case = ScreenshotCases.require(caseId)
        assertEquals("边界值不应被归一化裁剪", skin, skin.normalized())
        renderBubbleStage(
            case = case,
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            messages = ScreenshotFixture.stageMessages(),
            skin = skin,
        )
        composeRule.onNodeWithText(ScreenshotFixture.UserLineText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ScreenshotFixture.CharacterLineText, substring = true).assertIsDisplayed()
        captureCase(case)
    }
}
