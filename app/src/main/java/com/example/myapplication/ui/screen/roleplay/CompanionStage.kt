package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionBeatPlayer
import com.example.myapplication.roleplay.companion.CompanionBeatPlayerPhase
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.roleplay.CharacterDialogueBubbleContent
import com.example.myapplication.ui.component.roleplay.UserDialogueBubbleContent
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDialogueBubbleStyle
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionDisplayUiState

data class CompanionDisplayBinding(
    val state: CompanionDisplayUiState,
    val onModeChange: (CompanionDisplayMode) -> Unit,
    val onOpenReading: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onSpeechSelection: (CompanionSpeechSelection?) -> Unit = {},
    val replyMessageId: String? = null,
    val voice: CompanionVoiceBinding? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompanionDisplayToolbar(binding: CompanionDisplayBinding) {
    Column(Modifier.fillMaxWidth().testTag("companion_display_toolbar")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
            CompanionDisplayMode.entries.forEach { mode ->
                NarraTextButton(
                    onClick = { binding.onModeChange(mode) },
                    enabled = !binding.state.isSavingPreference,
                    modifier = Modifier.testTag("companion_display_${mode.name}")
                ) {
                    Text(
                        if (mode == CompanionDisplayMode.STAGE) "演出" else "列表",
                        fontWeight = if (binding.state.mode == mode) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                }
            }
            NarraTextButton(
                onClick = binding.onOpenReading,
                modifier = Modifier.testTag("companion_open_reading")
            ) { Text("连续阅读") }
            NarraTextButton(
                onClick = binding.onOpenAppearance,
                modifier = Modifier.testTag("companion_open_visuals")
            ) { Text("素材与布局") }
            binding.voice?.let { CompanionVoiceToolbar(it) }
        }
        binding.state.errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun CompanionStageViewport(
    reply: ParsedCompanionReply?, binding: CompanionDisplayBinding,
    characterName: String, scenarioId: String, settings: AppSettings, modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val player = remember(scope) { CompanionBeatPlayer(scope) }
    DisposableEffect(player) { onDispose { player.close() } }
    CompanionStage(
        reply,
        player,
        binding.state.visuals.forScene(scenarioId),
        binding.state.resources,
        characterName,
        settings.roleplayLineHeightScale.scaleFactor,
        modifier,
        settings.roleplayNoBackgroundSkin,
        settings.roleplayHighContrast,
        binding.onSpeechSelection,
        observationEnabled = reply?.source?.characterId?.let { settings.companionSettings(it).writing.observation.enabled } == true,
        voiceBinding = binding.voice)
}

/** 播放原文的正式投影；定时器只在 RESUMED 宿主工作，菜单、输入和滚动不推进。 */
@Composable
fun CompanionStage(
    reply: ParsedCompanionReply?,
    player: CompanionBeatPlayer,
    visuals: CompanionVisualSettings,
    resources: CompanionResolvedResources,
    characterName: String,
    lineHeightScale: Float,
    modifier: Modifier = Modifier,
    noBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
    highContrast: Boolean = false,
    onSpeechSelection: (CompanionSpeechSelection?) -> Unit = {},
    observationEnabled: Boolean = false,
    voiceBinding: CompanionVoiceBinding? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentReply by rememberUpdatedState(reply)
    val selectionCallback by rememberUpdatedState(onSpeechSelection)
    val playback by player.state.collectAsStateWithLifecycle()
    val visible = playback.takeIf { it.source == reply?.source }
    var menuOpen by remember(reply?.source) { mutableStateOf(false) }
    var selected by remember(reply?.source) { mutableStateOf<CompanionSpeechSelection?>(null) }
    LaunchedEffect(player, reply) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.setSource(
            reply
        )
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.stop()
            if (event == Lifecycle.Event.ON_RESUME) player.setSource(currentReply)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer); player.stop(); selectionCallback(
            null
        )
        }
    }
    DisposableEffect(reply?.source) { onDispose { selectionCallback(null) } }
    val background =
        resources.backgrounds.firstOrNull()?.let { it.uri.ifBlank { it.url } }.orEmpty()
    val backdrop =
        rememberImmersiveBackdropState(backgroundUri = background, highContrast = highContrast)
    val colors = rememberImmersiveRoleplayColors(backdrop, noBackgroundSkin)
    val bubbleStyle = rememberRoleplayDialogueBubbleStyle(
        backdrop,
        noBackgroundSkin,
        RoleplayMessageBubbleMode.DEFAULT
    )
    val beat = visible?.beat
    val scroll = rememberScrollState()
    LaunchedEffect(beat?.id) { scroll.scrollTo(0) }

    BoxWithConstraints(modifier.fillMaxSize().testTag("companion_stage")) {
        val stageHeight = maxHeight
        val fontScale = LocalDensity.current.fontScale
        val textBudget = NarraMinimumTouchTarget * maxOf(1f, fontScale)
        val controlsBudget = NarraMinimumTouchTarget * maxOf(1f, fontScale * 0.6f)
        val requestedPortraitHeight = stageHeight * visuals.layout.normalized().portraitHeight
        val portraitHeight = minOf(
            requestedPortraitHeight,
            (stageHeight - textBudget - controlsBudget).coerceAtLeast(0.dp)
        )
            .takeIf { it >= NarraMinimumTouchTarget } ?: 0.dp
        val showSpeaker =
            stageHeight - portraitHeight - controlsBudget >= textBudget + NarraMinimumTouchTarget
        val textPadding =
            if (stageHeight - portraitHeight - controlsBudget < textBudget) NarraUi.space4 else NarraUi.space8
        CompanionStageImage(
            resources.backgrounds,
            "演出背景",
            Modifier.matchParentSize(),
            ContentScale.Crop
        )
        resources.decorations.forEach { decoration ->
            CompanionStageImage(
                listOf(decoration),
                "场景装饰",
                Modifier.matchParentSize(),
                ContentScale.Fit
            )
        }
        Column(Modifier.fillMaxSize()) {
            if (portraitHeight > 0.dp) {
                val alignment = when (visuals.layout.portraitPosition) {
                    CompanionPortraitPosition.LEFT -> Alignment.CenterStart
                    CompanionPortraitPosition.CENTER -> Alignment.Center
                    CompanionPortraitPosition.RIGHT -> Alignment.CenterEnd
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(portraitHeight),
                    contentAlignment = alignment
                ) {
                    CompanionStageImage(
                        resources.portraits(beat?.spriteKey), characterName,
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(visuals.layout.normalized().portraitWidth)
                            .testTag("companion_stage_portrait"), ContentScale.Fit, characterName
                    )
                }
            }
            Surface(
                color = colors.panelBackgroundStrong.copy(alpha = 1f), shape = NarraShapes.medium,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .testTag("companion_stage_text_viewport")
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll)
                        .companionTextGestures(
                            Triple(reply?.source, beat?.id, visible?.generation),
                            onClick = player::advanceOrReveal,
                            onLongClick = { menuOpen = true },
                            onCancel = { menuOpen = false })
                        .padding(textPadding).testTag("companion_stage_surface")
                ) {
                    if (reply == null || beat == null) Text(
                        "尚无已提交回复。发送消息后即可演出。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.panelText
                    )
                    else {
                        val message = RoleplayMessageUiModel(
                            beat.id,
                            reply.source.messageId,
                            beat.contentType,
                            beat.speaker,
                            beat.speakerName,
                            content = visible?.visibleText.orEmpty(),
                            copyText = beat.content,
                            richTextSource = visible?.visibleText.orEmpty(),
                            companionReply = reply,
                            companionBeat = beat
                        )
                        Column(Modifier.fillMaxWidth().testTag("companion_stage_beat")) {
                            if (showSpeaker) Text(
                                beat.speakerName,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.panelText
                            )
                            when {
                                beat.contentType != RoleplayContentType.DIALOGUE -> RoleplayReadingMessage(
                                    message,
                                    backdrop,
                                    lineHeightScale
                                )

                                beat.speaker == RoleplaySpeaker.USER -> UserDialogueBubbleContent(
                                    message, colors, backdrop,
                                    lineHeightScale, null, true, bubbleStyle
                                )

                                else -> CharacterDialogueBubbleContent(
                                    message,
                                    colors,
                                    backdrop,
                                    lineHeightScale,
                                    null,
                                    false,
                                    true,
                                    bubbleStyle
                                )
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NarraTextButton(
                            onClick = player::advanceOrReveal, enabled = beat != null,
                            modifier = Modifier.testTag("companion_stage_advance")
                        ) {
                            Text(
                                when (visible?.phase) {
                                    CompanionBeatPlayerPhase.TYPING -> "补全"
                                    CompanionBeatPlayerPhase.END -> "再演一次"
                                    else -> "下一拍"
                                }
                            )
                        }
                        NarraTextButton(
                            onClick = player::replay, enabled = beat != null,
                            modifier = Modifier.testTag("companion_stage_replay")
                        ) { Text("重播") }
                        Box {
                            NarraTextButton(
                                onClick = { menuOpen = true }, enabled = beat != null,
                                modifier = Modifier.testTag("companion_stage_menu")
                            ) { Text("片段") }
                            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("选择本拍") },
                                    modifier = Modifier.testTag("companion_stage_select"),
                                    onClick = {
                                        val units =
                                            reply?.let(CompanionSpeechSupport::units).orEmpty()
                                        units.firstOrNull { it.beatId == beat?.id }?.let { unit ->
                                            selected = CompanionSpeechSupport.select(units, unit.id)
                                            selectionCallback(selected)
                                        }
                                        menuOpen = false
                                    })
                                if (voiceBinding != null) DropdownMenuItem(
                                    text = { Text("朗读本段") },
                                    modifier = Modifier.testTag("companion_stage_speak"),
                                    onClick = {
                                        val units = reply?.let(CompanionSpeechSupport::units).orEmpty()
                                        units.firstOrNull { it.beatId == beat?.id }?.let { unit ->
                                            selected = CompanionSpeechSupport.select(units, unit.id)
                                            selectionCallback(selected)
                                            selected?.let(voiceBinding.callbacks.onPlay)
                                        }
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                        CompanionObservationPanel(reply, enabled = observationEnabled)
                    }
                    Text(
                        "${(visible?.beatIndex ?: -1) + 1}/${visible?.beatCount ?: 0}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = NarraUi.space8)
                            .testTag("companion_stage_progress")
                    )
                }
            }
        }
    }
    selected?.let { selection ->
        val dismiss = { selected = null; selectionCallback(null) }
        if (voiceBinding == null) CompanionSpeechSelectionPanel(selection, dismiss)
        else CompanionSpeechSelectionPanel(selection, voiceBinding, dismiss)
    }
}

/** 有序候选在真实加载失败后继续回退，上一拍的图不冒充新表情。 */
@Composable
internal fun CompanionStageImage(
    candidates: List<CompanionResolvedImage>, description: String, modifier: Modifier,
    scale: ContentScale, fallback: String? = null
) {
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val candidate = candidates.getOrNull(candidateIndex)
        val density = LocalDensity.current
        val size = with(density) {
            IntSize(
                maxWidth.roundToPx().coerceAtLeast(1),
                maxHeight.roundToPx().coerceAtLeast(1)
            )
        }
        val imageState = key(candidate) {
            rememberUserProfileAvatarState(candidate?.uri.orEmpty(), candidate?.url.orEmpty(), size)
        }
        LaunchedEffect(candidate, imageState.loadState) {
            if (candidate != null && (imageState.loadState == UserAvatarLoadState.Error || imageState.loadState == UserAvatarLoadState.Empty)) candidateIndex++
        }
        if (candidate != null && imageState.loadState == UserAvatarLoadState.Success && imageState.imageBitmap != null) {
            Image(
                imageState.imageBitmap,
                description,
                Modifier.fillMaxSize()
                    .testTag("companion_image_${candidate.resourceId ?: "legacy"}"),
                contentScale = scale
            )
        } else if (fallback != null) Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = NarraShapes.medium
        ) {
            Text(
                fallback.ifBlank { "角色" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(NarraUi.space8)
            )
        }
    }
}

@Composable
fun CompanionSpeechSelectionPanel(selection: CompanionSpeechSelection, onDismiss: () -> Unit) {
    NarraAlertDialog(
        title = "已选择 ${selection.units.size} 个片段", message = selection.text,
        onDismiss = onDismiss, onConfirm = onDismiss, confirmLabel = "关闭", dismissLabel = null,
        modifier = Modifier.testTag("companion_speech_selection")
    )
}

@Composable
fun CompanionSpeechSelectionPanel(selection: CompanionSpeechSelection, voiceBinding: CompanionVoiceBinding, onDismiss: () -> Unit) {
    NarraAlertDialog(
        title = "已选择 ${selection.units.size} 个片段",
        onDismiss = onDismiss, onConfirm = onDismiss, confirmLabel = "关闭", dismissLabel = null,
        modifier = Modifier.testTag("companion_speech_selection"),
        content = { CompanionVoiceActions(voiceBinding, selection) },
    )
}
