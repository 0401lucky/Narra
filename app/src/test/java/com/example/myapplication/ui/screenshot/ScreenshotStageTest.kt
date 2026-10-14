package com.example.myapplication.ui.screenshot

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.SimpleImageComparator
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.ui.theme.Typography
import androidx.compose.material3.ColorScheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fetchImage
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 截图安全网的公共宿主：固定 Robolectric SDK/locale/时区/fontScale，统一舞台尺寸断言与
 * 截图前守卫（节点存在、关键内容、固定像素尺寸、非单色空白）。
 *
 * 与冒烟用例相同：Compose 官方 `captureToImage()` 在 Robolectric 下会等待重绘超时，
 * 守卫改用 Roborazzi `fetchImage`（同一原生渲染路径）。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    application = Application::class,
    qualifiers = ScreenshotFixture.HostQualifiers,
)
abstract class ScreenshotStageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    private var observedDensity = 0f
    private var observedFontScale = 0f

    @Before
    fun configureDeterministicHost() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        Locale.setDefault(Locale.CHINA)
        RuntimeEnvironment.setFontScale(ScreenshotFixture.NormalFontScale)
    }

    @After
    fun restoreDeterministicHost() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    /** 窄屏 + 200% 字体宿主；必须在 [setStage] 之前调用。 */
    protected fun useNarrowFontScale() {
        RuntimeEnvironment.setFontScale(ScreenshotFixture.NarrowFontScale)
    }

    /** 关闭自动推进的测试动画时钟，用于固定流式/动画中间帧。 */
    protected fun stopAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    protected fun advanceAnimationClock(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
    }

    /** Robolectric 暂停 looper 时把主线程任务跑完，供真实 ViewModel 状态刷新生效。 */
    protected fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected fun setStage(
        case: ScreenshotCase,
        appColorTheme: AppColorTheme = AppColorTheme.MATCHA,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            ChatAppTheme(
                appColorTheme = appColorTheme,
                darkTheme = darkTheme,
                dynamicColor = false,
            ) {
                StageBox(case = case, backgroundColor = MaterialTheme.colorScheme.background, content = content)
            }
        }
    }

    /**
     * 直接以给定 ColorScheme 搭舞台，用于 C01 的固定动态配色用例：
     * 颜色来自生产解析函数 resolveAppColorScheme，不读取系统动态色或壁纸。
     */
    protected fun setColorSchemeStage(
        case: ScreenshotCase,
        colorScheme: ColorScheme,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
            ) {
                StageBox(case = case, backgroundColor = colorScheme.background, content = content)
            }
        }
    }

    @Composable
    private fun StageBox(
        case: ScreenshotCase,
        backgroundColor: androidx.compose.ui.graphics.Color,
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        observedDensity = density.density
        observedFontScale = density.fontScale
        Box(
            modifier = Modifier
                .size(case.stageWidthDp.dp, case.stageHeightDp.dp)
                .background(backgroundColor)
                .testTag(StageTag),
        ) {
            content()
        }
    }

    /** 截图前守卫 + 真实写入/比对；用例 ID 与测试类必须与登记表一致。 */
    protected fun captureCase(case: ScreenshotCase) {
        assertEquals(
            "用例 ${case.id} 登记在 ${case.testClass}，当前测试类不一致",
            case.testClass,
            this::class.java.simpleName,
        )
        check(capturedCaseIds.add(case.id)) {
            "同一次运行中用例 ${case.id} 被重复捕获，请检查测试方法是否复制粘贴出错"
        }
        assertStageReady(case)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StageTag).captureRoboImage(
            filePath = case.relativePath,
            roborazziOptions = StrictRoborazziOptions,
        )
    }

    private fun assertStageReady(case: ScreenshotCase) {
        val node = composeRule.onNodeWithTag(StageTag)
        node.assertIsDisplayed()

        assertEquals(
            "宿主 density 必须固定为 ${ScreenshotFixture.HostDensity}",
            ScreenshotFixture.HostDensity,
            observedDensity,
            0.001f,
        )
        assertEquals(
            "字体缩放必须与用例登记一致",
            case.fontScale,
            observedFontScale,
            0.001f,
        )

        val expectedWidthPx = (case.stageWidthDp * ScreenshotFixture.HostDensity).toInt()
        val expectedHeightPx = (case.stageHeightDp * ScreenshotFixture.HostDensity).toInt()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals("宿主宽度必须固定为 ${expectedWidthPx}px", expectedWidthPx.toFloat(), bounds.width, 1f)
        assertEquals("宿主高度必须固定为 ${expectedHeightPx}px", expectedHeightPx.toFloat(), bounds.height, 1f)

        val rendered = node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("截图守卫失败：节点渲染结果为 null")
        assertEquals("截图宽度与固定宿主不一致", expectedWidthPx, rendered.width)
        assertEquals("截图高度与固定宿主不一致", expectedHeightPx, rendered.height)
        assertImageHasRealContent(rendered.asImageBitmap())
    }

    private fun assertImageHasRealContent(image: ImageBitmap) {
        val pixelMap = image.toPixelMap()
        val distinctColors = mutableSetOf<Long>()
        var sampledPixels = 0
        var x = 0
        while (x < image.width) {
            var y = 0
            while (y < image.height) {
                distinctColors += pixelMap[x, y].value.toLong()
                sampledPixels++
                y += PixelSampleStep
            }
            x += PixelSampleStep
        }
        assertTrue("图像采样点过少：$sampledPixels", sampledPixels >= 1000)
        assertTrue("图像疑似单色空白，仅采样到 ${distinctColors.size} 种颜色", distinctColors.size >= 8)
    }

    companion object {
        const val StageTag = "screenshot_stage"
        private const val PixelSampleStep = 16

        private val capturedCaseIds = mutableSetOf<String>()

        /** 零色差容忍（SimpleImageComparator 默认 0.007）与零变化像素比例。 */
        val StrictRoborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = 0f),
                resultValidator = ThresholdValidator(0f),
            ),
        )
    }
}
