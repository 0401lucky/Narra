package com.example.myapplication.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.theme.resolveAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C01：固定动态 ColorScheme 的浅/深解析（不读取系统动态色或壁纸）。
 *
 * 固定 scheme 由测试提供，直接调用生产 `resolveAppColorScheme`，先断言生产映射结果，
 * 再用同一个 scheme 渲染色板基准；动态色取值不依赖 CI 机器壁纸。
 */
class ThemeScreenshotC01Test : ScreenshotStageTest() {

    @Test
    fun c01_dynamic_light() {
        val case = ScreenshotCases.require("C01_dynamic-light")
        val resolved = resolveAppColorScheme(
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = false,
            dynamicColorScheme = FixedDynamicScheme,
        )
        assertEquals("动态色保留 primary 本体", FixedDynamicScheme.primary, resolved.primary)
        assertEquals("浅色强调文字固定为白色", Color.White, resolved.onPrimary)
        assertEquals(Color(0xFFF5F6F7), resolved.background)
        assertEquals(Color(0xFF212628), resolved.onBackground)
        assertEquals(Color.White, resolved.surface)
        assertEquals("动态色容器色不被覆盖", FixedDynamicScheme.primaryContainer, resolved.primaryContainer)

        setColorSchemeStage(case, resolved) {
            SchemeBoard(roles = schemeRoles(resolved))
        }
        composeRule.onNodeWithText("primary").assertIsDisplayed()
        composeRule.onNodeWithText("background").assertIsDisplayed()
        captureCase(case)
    }

    @Test
    fun c01_dynamic_dark() {
        val case = ScreenshotCases.require("C01_dynamic-dark")
        val resolved = resolveAppColorScheme(
            appColorTheme = AppColorTheme.MATCHA,
            darkTheme = true,
            dynamicColorScheme = FixedDynamicScheme,
        )
        assertEquals("动态色保留 primary 本体", FixedDynamicScheme.primary, resolved.primary)
        assertEquals("深色沿用输入 scheme 的 onPrimary", FixedDynamicScheme.onPrimary, resolved.onPrimary)
        assertEquals(Color(0xFF141617), resolved.background)
        assertEquals(Color(0xFF191C1D), resolved.surface)
        assertEquals(Color(0xFFE6E8E9), resolved.onBackground)

        setColorSchemeStage(case, resolved) {
            SchemeBoard(roles = schemeRoles(resolved))
        }
        composeRule.onNodeWithText("primary").assertIsDisplayed()
        composeRule.onNodeWithText("background").assertIsDisplayed()
        captureCase(case)
    }

    private fun schemeRoles(scheme: ColorScheme): List<Pair<String, Color>> = listOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "secondaryContainer" to scheme.secondaryContainer,
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceContainer" to scheme.surfaceContainer,
        "error" to scheme.error,
    )

    @Composable
    private fun SchemeBoard(roles: List<Pair<String, Color>>) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "固定动态 ColorScheme 解析结果",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            roles.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }

    private companion object {
        /** 合成固定动态配色：不是系统壁纸取色，仅用于验证生产解析与色板渲染。 */
        val FixedDynamicScheme = lightColorScheme(
            primary = Color(0xFF5E6B4F),
            onPrimary = Color(0xFF102030),
            primaryContainer = Color(0xFFCCE8C8),
            secondaryContainer = Color(0xFFD6E4F7),
            surfaceContainer = Color(0xFFEDEDF2),
            error = Color(0xFFBA1A1A),
        )
    }
}
