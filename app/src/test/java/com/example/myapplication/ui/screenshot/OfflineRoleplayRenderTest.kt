package com.example.myapplication.ui.screenshot

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.roleplay.RoleplayInputQuickAction
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.deriveImmersiveGlassPalette
import androidx.compose.material3.MaterialTheme
import com.example.myapplication.ui.screen.roleplay.RoleplayReadingMode
import com.example.myapplication.ui.screen.roleplay.RoleplaySceneContent
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.theme.roleplayTextContrast
import com.github.takahirom.roborazzi.fetchImage
import com.github.takahirom.roborazzi.RoborazziOptions
import org.junit.After
import org.junit.Assert.assertTrue

/** 使用生产页面；测试不复制布局，也不读写用户图片。 */
@OptIn(coil3.annotation.DelicateCoilApi::class)
abstract class OfflineRoleplayRenderTest : ScreenshotRenderTest() {
    private var installedSyntheticLoader = false

    @After fun resetSyntheticImageLoader() {
        if (installedSyntheticLoader) SingletonImageLoader.reset()
    }
    internal fun renderOfflinePage(
        case: ScreenshotCase,
        theme: AppColorTheme = AppColorTheme.MATCHA,
        dark: Boolean = false,
        scenario: RoleplayScenario = OfflineRoleplayFixture.DialogueScenario,
        messages: List<RoleplayMessageUiModel> = ScreenshotFixture.stageMessages(),
        suggestions: List<RoleplaySuggestionUiModel> = emptyList(),
        image: ImageBitmap? = null,
        sending: Boolean = false,
        generating: Boolean = false,
        error: String? = null,
        presence: Boolean = false,
        showHelper: Boolean = true,
        skin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
        proposal: PendingMemoryProposal? = null,
        schemeState: State<ColorScheme>? = null,
        input: State<String>? = null,
        quickActions: List<RoleplayInputQuickAction> = emptyList(),
        onInput: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onCancel: () -> Unit = {},
        onGenerate: () -> Unit = {},
        onApply: (String) -> Unit = {},
        onClear: () -> Unit = {},
        onBack: () -> Unit = {},
        onSettings: () -> Unit = {},
        onApprove: () -> Unit = {},
        onReject: () -> Unit = {},
    ) {
        val page: @Composable () -> Unit = {
            RoleplaySceneContent(
                scenario = scenario,
                assistant = ScreenshotFixture.SuwanAssistant,
                settings = AppSettings(appColorTheme = theme, roleplayImmersiveMode = RoleplayImmersiveMode.NONE,
                    showRoleplayPresenceStrip = presence, showRoleplayAiHelper = showHelper, roleplayNoBackgroundSkin = skin),
                messages = messages, suggestions = suggestions, input = input?.value.orEmpty(), inputFocusToken = 0L,
                isSending = sending, isGeneratingSuggestions = generating, suggestionErrorMessage = error,
                pendingMemoryProposal = proposal, snackbarHostState = remember { SnackbarHostState() },
                backdropState = ScreenshotFixture.backdropState(image, case.stageWidthDp * 2, case.stageHeightDp * 2),
                onInputChange = onInput, onGenerateSuggestions = onGenerate, onApplySuggestion = onApply,
                onClearSuggestions = onClear, onRetryTurn = {}, onRetryAiPhoto = { _, _ -> }, onEditUserMessage = {},
                onOpenSpecialPlay = {}, quickActions = quickActions, pendingImageParts = emptyList(),
                onPickImage = {}, onRemovePendingImagePart = {}, onOpenPhoneCheck = {}, onOpenWallet = {},
                onConfirmTransferReceipt = {}, onSend = onSend, onCancelSending = onCancel,
                onApprovePendingMemoryProposal = onApprove, onRejectPendingMemoryProposal = onReject,
                onOpenSettings = onSettings, onNavigateBack = onBack, showSpecialPlaySheet = false,
                activeSpecialPlayDraft = null, onDismissSpecialPlay = {}, onOpenSpecialPlayEditor = {},
                onDismissSpecialPlayEditor = {}, onSpecialPlayDraftChange = {}, onSpecialPlayConfirm = {},
            )
        }
        if (schemeState == null) setStage(case, theme, dark, page) else setColorSchemeStateStage(case, schemeState, page)
    }

    internal fun renderReadingPage(
        case: ScreenshotCase,
        dark: Boolean = false,
        messages: List<RoleplayMessageUiModel> = OfflineRoleplayFixture.MixedMessages,
        title: String = ScreenshotFixture.SuwanScenarioTitle,
        withBackground: Boolean = false,
        onDismiss: () -> Unit = {},
        schemeState: State<ColorScheme>? = null,
        lineHeightScale: Float = 1f,
        highContrast: Boolean = false,
    ) {
        val background = if (withBackground) {
            val synthetic = ScreenshotFixture.syntheticBackdrop().asAndroidBitmap().asImage()
            SingletonImageLoader.setUnsafe(ImageLoader.Builder(composeRule.activity).components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        check(chain.request.data == "fixture://e04-reading-backdrop")
                        return SuccessResult(image = synthetic, request = chain.request, dataSource = DataSource.MEMORY)
                    }
                })
            }.build())
            installedSyntheticLoader = true
            "fixture://e04-reading-backdrop"
        } else ""
        var backgroundReady = !withBackground
        val page: @Composable () -> Unit = {
            if (withBackground) {
                // 同 URI / 同解析路径的只读探针，等图片及异步调色板均已就绪。
                val observed = rememberImmersiveBackdropState(background, false)
                backgroundReady = observed.hasImage && observed.palette == deriveImmersiveGlassPalette(
                    observed.imageBitmap, MaterialTheme.colorScheme, false,
                )
            }
            RoleplayReadingMode(messages, title, background, lineHeightScale, highContrast, onDismiss)
        }
        if (schemeState == null) setStage(case, AppColorTheme.MATCHA, dark, page)
        else setColorSchemeStateStage(case, schemeState, page)
        if (withBackground) {
            composeRule.waitUntil(timeoutMillis = 10_000) { backgroundReady }
            // 生产阅读页自身的露出背景带必须确实呈现合成图片；只读旁路成功不算页面加载成功。
            val pixels = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode()
                .fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
            val exposedBackdrop = pixels[pixels.width / 2, 8]
            assertTrue("生产阅读页未绘制蓝色合成背景：$exposedBackdrop",
                exposedBackdrop.blue > exposedBackdrop.red + 0.1f)
        }
        drainMainLooper()
        composeRule.waitForIdle()
    }

    internal fun assertRenderedTextContrast(text: String, substring: Boolean = false) {
        val node = composeRule.onNodeWithText(text, substring = substring, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val stage = composeRule.onNodeWithTag(StageTag).fetchSemanticsNode()
        val pixels = stage.fetchImage(RoborazziOptions.RecordOptions())!!.asImageBitmap().toPixelMap()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val x = (bounds.left - stage.boundsInRoot.left - 4).toInt().coerceIn(0, pixels.width - 1)
        val y = (bounds.center.y - stage.boundsInRoot.top).toInt().coerceIn(0, pixels.height - 1)
        val surface = pixels[x, y]
        val annotated = layout.layoutInput.text
        val foregrounds = annotated.indices.filterNot { annotated[it].isWhitespace() }.map { index ->
            annotated.spanStyles.lastOrNull { index in it.start until it.end && it.item.color != Color.Unspecified }
                ?.item?.color ?: layout.layoutInput.style.color
        }.filter { it != Color.Unspecified }.distinct()
        assertTrue("必须取得生产文字颜色", foregrounds.isNotEmpty())
        foregrounds.forEach { foreground ->
            assertTrue("$text: text=$foreground actualSurface=$surface", roleplayTextContrast(foreground, surface, surface) >= 4.5f)
        }
    }
}
