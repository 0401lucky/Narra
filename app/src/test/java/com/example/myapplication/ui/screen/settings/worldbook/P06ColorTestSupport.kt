package com.example.myapplication.ui.screen.settings.worldbook

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.screenshot.ScreenshotCase
import com.example.myapplication.ui.screenshot.ScreenshotFixture
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.ui.theme.roleplayTextContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** 直接绘制当前窗口的 AndroidComposeView，避免把 Dialog/Popup 的局部坐标用于 Activity 像素。 */
abstract class P06ColorTestSupport : P06WorldBookTestSupport() {
    protected val schemes: List<ColorScheme> = AppColorTheme.entries.flatMap { theme ->
        listOf(false, true).map { dark -> resolveAppColorScheme(theme, dark) }
    } + listOf(false, true).map { dark ->
        resolveAppColorScheme(
            AppColorTheme.MATCHA,
            dark,
            lightColorScheme()
        )
    }

    protected val stageRoot get() = composeRule.onNodeWithTag(StageTag)

    protected fun renderColors(
        current: State<ColorScheme>,
        case: ScreenshotCase = normalCase,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers(if (case.fontScale == 2f) ScreenshotFixture.NarrowQualifiers else ScreenshotFixture.HostQualifiers)
        RuntimeEnvironment.setFontScale(case.fontScale)
        setColorSchemeStateStage(case, current, content)
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    protected fun nativeBitmap(root: SemanticsNodeInteraction = stageRoot): Bitmap {
        val owner = root.fetchSemanticsNode().root as? ViewRootForTest
            ?: error("颜色证据必须来自真实 Android View owner")
        return composeRule.runOnIdle {
            val view = owner.view
            assertTrue(view.width > 0 && view.height > 0)
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                .also { view.draw(Canvas(it)) }
        }
    }

    protected fun besideText(
        text: SemanticsNodeInteraction,
        root: SemanticsNodeInteraction = stageRoot
    ): Color {
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val image = nativeBitmap(root)
        return try {
            Color(
                image.getPixel(
                    (bounds.left - 2).roundToInt().coerceIn(0, image.width - 1),
                    bounds.center.y.roundToInt().coerceIn(0, image.height - 1)
                )
            )
        } finally {
            image.recycle()
        }
    }

    protected fun controlSurface(
        control: SemanticsNodeInteraction,
        root: SemanticsNodeInteraction = stageRoot
    ): Color {
        val bounds = control.fetchSemanticsNode().boundsInRoot
        val image = nativeBitmap(root)
        return try {
            Color(
                image.getPixel(
                    (bounds.left + 8).roundToInt().coerceIn(0, image.width - 1),
                    bounds.center.y.roundToInt().coerceIn(0, image.height - 1)
                )
            )
        } finally {
            image.recycle()
        }
    }

    protected fun maximumInkContrast(
        text: SemanticsNodeInteraction,
        surface: Color,
        root: SemanticsNodeInteraction = stageRoot,
    ): Float {
        val bounds = text.fetchSemanticsNode().boundsInRoot
        val image = nativeBitmap(root)
        return try {
            val left = ceil(bounds.left).toInt().coerceIn(0, image.width - 1)
            val right = floor(bounds.right).toInt().coerceIn(left + 1, image.width)
            val top = ceil(bounds.top).toInt().coerceIn(0, image.height - 1)
            val bottom = floor(bounds.bottom).toInt().coerceIn(top + 1, image.height)
            var maximum = 1f
            for (y in top until bottom) for (x in left until right) {
                maximum = maxOf(
                    maximum,
                    roleplayTextContrast(Color(image.getPixel(x, y)), surface, surface)
                )
            }
            maximum
        } finally {
            image.recycle()
        }
    }

    protected fun assertReadable(
        text: SemanticsNodeInteraction,
        surface: Color = besideText(text),
        root: SemanticsNodeInteraction = stageRoot,
        expected: Color? = null,
    ) {
        val layout = textLayout(text)
        val foreground = layout.layoutInput.style.color
        if (expected != null) assertEquals(expected, foreground)
        assertTrue(
            "实际文字配对不足4.5：${layout.layoutInput.text} $foreground / $surface",
            roleplayTextContrast(foreground, surface, surface) >= 4.5f
        )
        val rendered = maximumInkContrast(text, surface, root)
        assertTrue(
            "实际文字像素（含外层alpha）不足4.5：${layout.layoutInput.text} contrast=$rendered",
            rendered >= 4.5f
        )
    }
}
