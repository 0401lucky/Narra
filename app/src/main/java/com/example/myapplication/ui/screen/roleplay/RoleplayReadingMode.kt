package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.BuildConfig
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassVariant
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingScrimVariant
import com.example.myapplication.ui.component.roleplay.RoleplayEmotionChip
import com.example.myapplication.ui.component.roleplay.RoleplayLongformCard
import com.example.myapplication.ui.component.roleplay.toLongformParagraphs
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.buildCharacterDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.buildQuotedDialogueAnnotatedString
import com.example.myapplication.ui.component.roleplay.calculateImmersiveBackdropAmbientLuminance
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.resolveImmersiveReadingScrimAlpha
import com.example.myapplication.ui.component.roleplay.roleplayLazyContentType
import com.example.myapplication.ui.component.roleplay.adjustImmersiveReadingContentDarkenAlpha
import com.example.myapplication.ui.component.roleplay.adjustImmersiveReadingOverlayColor
import com.example.myapplication.ui.component.roleplay.resolveImmersiveReadingGlassSpec
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.readableRoleplayTextOnSurfaces
import com.example.myapplication.system.performance.BenchmarkReadyIds
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.ui.performance.LocalBenchmarkRuntimePolicy
import com.example.myapplication.ui.performance.benchmarkReadyTag

@Composable
fun RoleplayReadingMode(
    messages: List<RoleplayMessageUiModel>,
    scenarioTitle: String,
    backgroundUri: String,
    lineHeightScale: Float,
    highContrast: Boolean,
    onDismiss: () -> Unit,
) {
    val effectiveHighContrast = highContrast || rememberSystemHighTextContrastEnabled()
    val benchmarkExpectedMessageCount = if (
        BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL
    ) {
        LocalBenchmarkRuntimePolicy.current
            ?.scenarioId
            ?.fixtureId
            ?.roleplayMessageCount
    } else {
        null
    }
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = backgroundUri,
        highContrast = effectiveHighContrast,
    )
    val palette = backdropState.palette
    val readingColors = remember(backdropState) { resolveReadingModeColors(backdropState) }
    val ambientLuminance = remember(backdropState.imageBitmap, palette) {
        calculateImmersiveBackdropAmbientLuminance(backdropState)
    }
    val scrimAlpha = remember(ambientLuminance) {
        resolveImmersiveReadingScrimAlpha(
            backgroundLuminance = ambientLuminance,
            variant = ImmersiveReadingScrimVariant.READING,
        )
    }
    val scrimBrush = remember(scrimAlpha) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = scrimAlpha * 0.22f),
                0.42f to Color.Black.copy(alpha = scrimAlpha * 0.58f),
                1.0f to Color.Black.copy(alpha = scrimAlpha),
            ),
        )
    }

    // 阅读模式始终显示系统栏并添加安全区 padding，不跟随 immersiveMode 设置。
    // 设计意图：阅读模式的核心目标是"高可读性的全文回顾"，需要稳定的可视区域和易于退出的系统栏。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .benchmarkReadyTag(
                readyId = BenchmarkReadyIds.READING_READY,
                ready = scenarioTitle.isNotBlank() &&
                    messages.size == benchmarkExpectedMessageCount,
            ),
    ) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush),
        )

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ImmersiveReadingGlassSurface(
                backdropState = backdropState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = NarraUi.space12),
                shape = NarraShapes.small,
                variant = ImmersiveReadingGlassVariant.PANEL,
                overlayColor = palette.readingSurface,
                minimumOverlayAlpha = ReadingModePanelMinimumAlpha,
                contentDarkenAlpha = 0f,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("roleplay_reading_header")
                            .padding(horizontal = NarraUi.space24, vertical = NarraUi.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    ) {
                        Text(
                            text = stringResource(id = R.string.roleplay_reading_mode_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = readingColors.primary,
                        )
                        NarraIconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.common_close),
                                tint = readingColors.primary)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("roleplay_reading_list"),
                        contentPadding = PaddingValues(
                            start = NarraUi.space24,
                            end = NarraUi.space24,
                            top = NarraUi.space16,
                            bottom = NarraUi.space32,
                        ),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = NarraUi.space8),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                                ) {
                                    Text(
                                        text = scenarioTitle.ifBlank {
                                            stringResource(id = R.string.roleplay_story_recap)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = readingColors.muted,
                                    )
                                    ReadingModeMetaChip(
                                        text = stringResource(
                                            id = R.string.roleplay_story_count,
                                            messages.size,
                                        ),
                                        backdropState = backdropState,
                                        textColor = readingColors.primary,
                                    )
                                }
                            }
                        }

                        if (messages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = NarraUi.space32),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.roleplay_no_story_content),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = readingColors.muted,
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = messages,
                            key = { _, message -> message.uiItemId },
                            contentType = { _, message -> roleplayLazyContentType(message) },
                        ) { _, message ->
                            when (message.contentType) {
                                RoleplayContentType.NARRATION -> NarrationReadingBlock(
                                    message = message,
                                    lineHeightScale = lineHeightScale,
                                    colors = readingColors,
                                )

                                RoleplayContentType.THOUGHT -> ThoughtReadingBlock(
                                    message = message,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    colors = readingColors,
                                )

                                RoleplayContentType.SYSTEM -> SystemReadingBlock(message, backdropState, readingColors)

                                RoleplayContentType.DIALOGUE -> DialogueReadingBlock(
                                    message = message,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    colors = readingColors,
                                )

                                RoleplayContentType.ACTION -> DialogueReadingBlock(
                                    message = message,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    colors = readingColors,
                                )

                                RoleplayContentType.LONGFORM -> LongformReadingBlock(
                                    message = message,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    colors = readingColors,
                                )

                                RoleplayContentType.SPECIAL_PLAY -> TransferReadingBlock(message)

                                RoleplayContentType.STATUS -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThoughtReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    colors: ReadingModeColors,
) {
    val paragraphs = remember(message.content) {
        message.content.toLongformParagraphs()
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.fillMaxWidth(0.76f),
            shape = NarraShapes.large,
            variant = ImmersiveReadingGlassVariant.THOUGHT,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = NarraUi.space16, vertical = NarraUi.space12),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.thought,
                            dialogueColor = colors.accent,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 26.sp * lineHeightScale,
                            letterSpacing = 0.3.sp,
                        ),
                        color = colors.thought,
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrationReadingBlock(
    message: RoleplayMessageUiModel,
    lineHeightScale: Float,
    colors: ReadingModeColors,
) {
    val paragraphs = remember(message.content) {
        message.content.toLongformParagraphs()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(NarraUi.space4),
        verticalArrangement = Arrangement.spacedBy(NarraUi.space16),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = buildQuotedDialogueAnnotatedString(
                    text = paragraph,
                    narrationColor = colors.muted,
                    dialogueColor = colors.accent,
                ),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 30.sp * lineHeightScale,
                    letterSpacing = 0.3.sp,
                ),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun DialogueReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    colors: ReadingModeColors,
) {
    val isUser = message.speaker == RoleplaySpeaker.USER
    val palette = backdropState.palette
    val nameColor = if (isUser) colors.userAccent else colors.accent
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = Modifier.widthIn(max = maxWidth * if (isUser) 0.82f else 0.9f),
            shape = NarraShapes.large,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
            contentDarkenAlpha = 0f,
            overlayColor = if (isUser) {
                palette.userAccent.copy(alpha = 0.08f)
            } else {
                palette.panelTint.copy(alpha = 0.09f)
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = NarraUi.space16, vertical = NarraUi.space12),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    Text(
                        text = message.speakerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                    )
                    if (message.emotion.isNotBlank()) {
                        RoleplayEmotionChip(
                            text = message.emotion,
                            textColor = nameColor,
                            containerColor = Color.Transparent,
                        )
                    }
                }
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = if (isUser) {
                                buildQuotedDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = colors.primary,
                                    dialogueColor = colors.accent,
                                )
                            } else {
                                buildCharacterDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = colors.primary,
                                    dialogueColor = colors.accent,
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 30.sp * lineHeightScale,
                                letterSpacing = 0.3.sp,
                            ),
                            color = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongformReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    colors: ReadingModeColors,
) {
    RoleplayLongformCard(
        speakerName = message.speakerName,
        content = message.content,
        richTextSource = message.richTextSource,
        backdropState = backdropState,
        useReadingGlassStyle = true,
        containerColor = Color.Transparent,
        titleColor = colors.accent,
        bodyColor = colors.primary,
        accentColor = colors.accent,
        thoughtColor = colors.thought,
        lineHeightScale = lineHeightScale,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NarraUi.space4, horizontal = NarraUi.space8),
    )
}

@Composable
private fun SystemReadingBlock(
    message: RoleplayMessageUiModel,
    backdropState: ImmersiveBackdropState,
    colors: ReadingModeColors,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NarraUi.space8),
        contentAlignment = Alignment.Center,
    ) {
        ReadingModeMetaChip(
            text = message.content,
            backdropState = backdropState,
            textColor = colors.primary,
        )
    }
}

@Composable
private fun TransferReadingBlock(message: RoleplayMessageUiModel) {
    val specialPart = message.specialPart ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NarraUi.space8),
        contentAlignment = if (message.speaker == RoleplaySpeaker.USER) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        SpecialPlayCard(
            part = specialPart,
            isUserMessage = message.speaker == RoleplaySpeaker.USER,
            onConfirmTransferReceipt = null,
            modifier = if (message.speaker == RoleplaySpeaker.USER) {
                Modifier.fillMaxWidth(0.78f)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

@Composable
private fun ReadingModeMetaChip(
    text: String,
    backdropState: ImmersiveBackdropState,
    textColor: Color,
) {
    ImmersiveReadingGlassSurface(
        backdropState = backdropState,
        shape = NarraShapes.pill,
        variant = ImmersiveReadingGlassVariant.PILL,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

private const val ReadingModePanelMinimumAlpha = 0.94f

private data class ReadingModeColors(
    val primary: Color,
    val muted: Color,
    val accent: Color,
    val userAccent: Color,
    val thought: Color,
)

/** 阅读页实际玻璃层的保守承载面，包含场景最亮/最暗区域和所有正文卡片。 */
private fun resolveReadingModeColors(backdropState: ImmersiveBackdropState): ReadingModeColors {
    val palette = backdropState.palette
    val luminance = calculateImmersiveBackdropAmbientLuminance(backdropState)
    fun surfaces(backings: List<Color>, variant: ImmersiveReadingGlassVariant, tint: Color): List<Color> {
        val spec = resolveImmersiveReadingGlassSpec(variant)
        val adjusted = adjustImmersiveReadingOverlayColor(tint, luminance, variant)
        val overlay = if (variant == ImmersiveReadingGlassVariant.PANEL) {
            adjusted.copy(alpha = maxOf(adjusted.alpha, ReadingModePanelMinimumAlpha))
        } else adjusted
        val darken = if (variant == ImmersiveReadingGlassVariant.PANEL || variant == ImmersiveReadingGlassVariant.DIALOGUE) {
            0f
        } else adjustImmersiveReadingContentDarkenAlpha(spec.contentDarkenAlpha, luminance, variant)
        return backings.flatMap { backing ->
            val underlay = palette.panelHighlight.copy(alpha = 0.06f).compositeOver(backing)
            listOf(palette.panelHighlight.copy(alpha = spec.highlightAlpha), Color.Transparent,
                palette.shadowColor.copy(alpha = spec.highlightAlpha * 0.35f)).map { sheen ->
                Color.Black.copy(alpha = darken).compositeOver(sheen.compositeOver(overlay.compositeOver(underlay)))
            }
        }
    }
    // scrim与背景的所有合成值都在黑白端点内；无需依赖某个固定背景采样点。
    val panel = surfaces(listOf(Color.Black, Color.White), ImmersiveReadingGlassVariant.PANEL, palette.readingSurface)
    val cardVariants = listOf(ImmersiveReadingGlassVariant.CARD, ImmersiveReadingGlassVariant.THOUGHT,
        ImmersiveReadingGlassVariant.PILL)
    val all = panel + cardVariants.flatMap { variant ->
        val spec = resolveImmersiveReadingGlassSpec(variant)
        val tint = if (variant == ImmersiveReadingGlassVariant.PILL) {
            lerp(palette.panelTintStrong, palette.panelTint, 0.46f).copy(alpha = spec.overlayAlpha)
        } else palette.panelTint.copy(alpha = spec.overlayAlpha)
        surfaces(panel, variant, tint)
    } + listOf(palette.userAccent.copy(alpha = 0.08f), palette.panelTint.copy(alpha = 0.09f)).flatMap {
        surfaces(panel, ImmersiveReadingGlassVariant.DIALOGUE, it)
    }
    fun readable(preferred: Color) = readableRoleplayTextOnSurfaces(preferred, all)
    return ReadingModeColors(readable(palette.onGlass), readable(palette.onGlassMuted),
        readable(palette.characterAccent), readable(palette.userAccent), readable(palette.thoughtText))
}
