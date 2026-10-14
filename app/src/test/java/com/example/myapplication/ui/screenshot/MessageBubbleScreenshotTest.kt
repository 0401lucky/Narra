package com.example.myapplication.ui.screenshot

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.SimpleImageComparator
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.MessageBubble
import com.example.myapplication.ui.component.roleplay.CharacterDialogueBubbleContent
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import com.example.myapplication.ui.theme.ChatAppTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fetchImage
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 视觉回归安全网的最小冒烟用例。
 *
 * 目标是证明 record / verify 链路在固定宿主上真实可跑：
 * - 渲染真实生产组件（聊天用户气泡、线上扮演角色玻璃气泡），不复制业务 UI；
 * - 使用纯合成 fixture、固定时间戳与本地合成背景图，不读取用户数据、网络或数据库；
 * - 截图前断言根节点、关键文本、尺寸与图像内容，空白图或错页不能通过；
 * - 比较使用零色差容忍与零变化比例，基线写入 app/src/test/snapshots。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    application = Application::class,
    qualifiers = "zh-rCN-w360dp-h800dp-xhdpi",
)
class MessageBubbleScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUpDeterministicEnvironment() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        RuntimeEnvironment.setFontScale(1f)
    }

    @After
    fun restoreEnvironment() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun chatUserMessageBubble_matchaLight_rendersContentAndMatchesBaseline() {
        composeRule.setContent {
            ChatAppTheme(
                appColorTheme = AppColorTheme.MATCHA,
                darkTheme = false,
                dynamicColor = false,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = StageWidth, height = StageHeight)
                        .background(MaterialTheme.colorScheme.background)
                        .testTag(StageTag),
                    contentAlignment = Alignment.Center,
                ) {
                    MessageBubble(message = userMessageFixture())
                }
            }
        }

        composeRule.onNodeWithText(UserMessageTextSubstring, substring = true).assertIsDisplayed()
        assertStageContentReady()
        captureStage(UserMessageBaselineFileName)
    }

    @Test
    fun roleplayCharacterBubble_glassWithLoadedBackdrop_rendersContentAndMatchesBaseline() {
        val backdropImage = synthesizeBackdropImage()
        val backdropState = backdropState(backdropImage)
        composeRule.setContent {
            ChatAppTheme(
                appColorTheme = AppColorTheme.MATCHA,
                darkTheme = false,
                dynamicColor = false,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = StageWidth, height = StageHeight)
                        .testTag(StageTag),
                ) {
                    Image(
                        bitmap = backdropImage,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CharacterDialogueBubbleContent(
                            message = characterMessageFixture(),
                            colors = rememberImmersiveRoleplayColors(backdropState),
                            backdropState = backdropState,
                            lineHeightScale = 1f,
                            onOpenQuotedMessage = null,
                            isError = false,
                            fillWidth = false,
                            useGlassBubble = true,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(RoleplayBubbleTag).assertIsDisplayed()
        composeRule.onNodeWithText(CharacterDialogueSubstring, substring = true).assertIsDisplayed()
        assertStageContentReady()
        captureStage(RoleplayBubbleBaselineFileName)
    }

    /** 截图前守卫：根节点可见、尺寸符合固定宿主、图像不是单色空白。 */
    private fun assertStageContentReady() {
        val node = composeRule.onNodeWithTag(StageTag)
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals("宿主宽度必须固定为 ${StageWidthPx}px", StageWidthPx.toFloat(), bounds.width, 1f)
        assertEquals("宿主高度必须固定为 ${StageHeightPx}px", StageHeightPx.toFloat(), bounds.height, 1f)

        // Compose 官方 captureToImage 在 Robolectric 下会等待重绘超时，这里改用与截图完全相同的
        // Roborazzi 渲染路径取一次像素，先确认画面真实存在再写入或比对基准。
        val rendered = node.fetchSemanticsNode().fetchImage(RoborazziOptions.RecordOptions())
            ?: error("截图守卫失败：节点渲染结果为 null")
        assertEquals("截图宽度与固定宿主不一致", StageWidthPx, rendered.width)
        assertEquals("截图高度与固定宿主不一致", StageHeightPx, rendered.height)
        assertImageHasRealContent(rendered.asImageBitmap())
    }

    private fun captureStage(baselineFileName: String) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StageTag).captureRoboImage(
            filePath = "$BaselineDirectory/$baselineFileName",
            roborazziOptions = StrictRoborazziOptions,
        )
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

    /** 合成背景图：固定的双色块，供玻璃气泡计算真实背景亮度。 */
    private fun synthesizeBackdropImage(): ImageBitmap {
        val size = BackdropSizePx.toFloat()
        val bitmap = Bitmap.createBitmap(BackdropSizePx, BackdropSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = android.graphics.Color.rgb(36, 58, 92)
        canvas.drawRect(0f, 0f, size, size * 0.6f, paint)
        paint.color = android.graphics.Color.rgb(210, 150, 90)
        canvas.drawRect(0f, size * 0.6f, size, size, paint)
        return bitmap.asImageBitmap()
    }

    private fun backdropState(image: ImageBitmap) = ImmersiveBackdropState(
        imageBitmap = image,
        screenSize = IntSize(StageWidthPx, StageHeightPx),
        palette = ImmersiveGlassPalette(
            panelTint = Color(0xFF28415B),
            panelTintStrong = Color(0xFF20344A),
            panelHighlight = Color.White.copy(alpha = 0.18f),
            panelBorder = Color.White.copy(alpha = 0.18f),
            shadowColor = Color.Black.copy(alpha = 0.32f),
            scrimTop = Color.Black.copy(alpha = 0.20f),
            scrimBottom = Color.Black.copy(alpha = 0.40f),
            onGlass = Color.White,
            onGlassMuted = Color.White.copy(alpha = 0.72f),
            chipTint = Color(0xFF5B8FD9),
            chipText = Color.White,
            characterAccent = Color(0xFF9FD3FF),
            userAccent = Color(0xFFFFD79A),
            thoughtText = Color(0xFFF2E9FF),
            readingSurface = Color(0xFF1F2F42),
            readingBorder = Color(0xFF7FB8F4),
        ),
    )

    private fun userMessageFixture() = ChatMessage(
        id = "screenshot-smoke-user-1",
        role = MessageRole.USER,
        content = UserMessageText,
        createdAt = FixedEpochMillis,
    )

    private fun characterMessageFixture() = RoleplayMessageUiModel(
        uiItemId = RoleplayBubbleMessageId,
        sourceMessageId = RoleplayBubbleMessageId,
        contentType = RoleplayContentType.DIALOGUE,
        speaker = RoleplaySpeaker.CHARACTER,
        speakerName = CharacterName,
        content = CharacterDialogueText,
        createdAt = FixedEpochMillis,
    )

    private companion object {
        const val StageTag = "screenshot_smoke_stage"
        val StageWidth = 360.dp
        val StageHeight = 800.dp
        const val StageWidthPx = 720
        const val StageHeightPx = 1600
        const val PixelSampleStep = 16
        const val BackdropSizePx = 64
        const val BaselineDirectory = "MessageBubbleScreenshotTest"
        const val UserMessageBaselineFileName = "chat-user-message-matcha-light.png"
        const val RoleplayBubbleBaselineFileName = "roleplay-character-bubble-glass.png"
        const val RoleplayBubbleMessageId = "smoke-rp-1"
        const val RoleplayBubbleTag = "roleplay_dialogue_bubble_$RoleplayBubbleMessageId"

        // 2026-10-14 12:00:00 Asia/Shanghai，避免使用 System.currentTimeMillis()。
        const val FixedEpochMillis = 1_791_950_400_000L
        const val UserMessageText = "你好，这是用于截图冒烟测试的固定用户消息。"
        const val UserMessageTextSubstring = "截图冒烟测试"
        const val CharacterName = "苏晚"
        const val CharacterDialogueText = "「晚上好。」她抬起头，看向窗外的霓虹。"
        const val CharacterDialogueSubstring = "她抬起头"

        /** 零色差容忍（SimpleImageComparator 默认 0.007），零变化像素比例。 */
        val StrictRoborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = 0f),
                resultValidator = ThresholdValidator(0f),
            ),
        )
    }
}
