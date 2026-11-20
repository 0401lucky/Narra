package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveReadingGlassVariant
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.viewmodel.CompanionReadingUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CompanionReadingCallbacks(
    val onLoadOlder: () -> Unit,
    val onRefresh: () -> Unit,
    val onAnchorChange: (CompanionReadingAnchor) -> Unit,
    val onSelectionChange: (CompanionSpeechSelection?) -> Unit,
    val onDismiss: () -> Unit,
    val onRetryOpen: () -> Unit = onRefresh,
    val onOpenHistory: (() -> Unit)? = null,
)

private class CompanionReaderViewport {
    var anchor: CompanionReadingAnchor? = null
    var followingBottom = true
    var restoring by mutableStateOf(false)
    var previousItems: List<CompanionReadingItem> = emptyList()
    var reconcilePending by mutableStateOf(false)
    var userScrollRevision = 0L
}

/** 独立只读页面。列表 identity 属于原文/段落，读取和选区不会改变当前场次。 */
@Composable
fun CompanionReadingScreen(
    state: CompanionReadingUiState,
    settings: AppSettings,
    callbacks: CompanionReadingCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    voiceBinding: CompanionVoiceBinding? = null,
) {
    val viewport = remember(state.scope?.context) {
        CompanionReaderViewport().apply {
            anchor = state.anchor; followingBottom = state.anchor?.followingBottom ?: true
        }
    }
    val currentItems by rememberUpdatedState(state.items)
    val currentCallbacks by rememberUpdatedState(callbacks)
    SideEffect { if (viewport.previousItems != state.items) viewport.reconcilePending = true }
    val units = remember(state.items) { CompanionSpeechSupport.units(state.items) }
    var startUnitId by remember(state.scope?.context) { mutableStateOf<String?>(null) }
    var menuItemId by remember { mutableStateOf<String?>(null) }
    var showSelection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val scene =
        state.scope?.let { reading -> reading.bindings.firstOrNull { it.scenarioId == reading.context.scenarioId }?.scenario }
    val appearance =
        state.scope?.let { settings.companionSettings(it.context.characterId).visuals.forScene(it.context.scenarioId) }
    val backgroundId = appearance?.selectedBackgroundId
    val selectedBackground = appearance?.takeIf { it.schemaVersion == 1 }?.resources
        ?.firstOrNull { it.id == backgroundId && it.kind == CompanionImageKind.BACKGROUND }
    val backgroundCandidates = listOfNotNull(
        selectedBackground?.let {
            CompanionResolvedImage(
                it.id,
                it.path,
                kind = CompanionImageKind.BACKGROUND
            )
        },
        scene?.backgroundUri?.takeIf(String::isNotBlank)
            ?.let { CompanionResolvedImage(null, it, kind = CompanionImageKind.BACKGROUND) })
        .distinctBy { it.uri }
    val background = backgroundCandidates.firstOrNull()?.uri.orEmpty()
    val backdrop = rememberImmersiveBackdropState(
        backgroundUri = background,
        highContrast = settings.roleplayHighContrast
    )

    ApplyRoleplaySurfaceSystemBars(
        MaterialTheme.colorScheme.surface,
        settings.roleplayImmersiveMode
    )
    BackHandler {
        when {
            menuItemId != null -> menuItemId = null
            showSelection -> showSelection = false
            startUnitId != null || state.selection != null -> {
                startUnitId = null; callbacks.onSelectionChange(null)
            }

            else -> callbacks.onDismiss()
        }
    }
    LaunchedEffect(units) {
        if (startUnitId != null && units.none { it.id == startUnitId }) startUnitId = null
        if (state.selection == null) showSelection = false
    }

    fun hasCurrentWindowLayout(): Boolean {
        val layout = listState.layoutInfo
        val items = currentItems
        return layout.totalItemsCount == items.size + 1 && layout.visibleItemsInfo.all { info ->
            info.index == 0 || items.getOrNull(info.index - 1)?.id == info.key
        }
    }

    suspend fun restore() {
        if (viewport.restoring || listState.isScrollInProgress || currentItems.isEmpty()) return
        viewport.restoring = true
        try {
            if (viewport.followingBottom) listState.scrollToItem(currentItems.size, Int.MAX_VALUE)
            else viewport.anchor?.let { anchor ->
                val index = CompanionReadingSupport.anchorIndex(anchor, currentItems) ?: return@let
                val sameSource = currentItems[index].record.message.id == anchor.messageId
                val size =
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentItems[index].id }?.size
                val offset = if (!sameSource) 0 else anchor.offset.coerceAtMost(
                    (size?.minus(1) ?: anchor.offset).coerceAtLeast(0)
                )
                listState.scrollToItem(index + 1, offset)
            }
        } finally {
            viewport.restoring = false
        }
    }

    val gestures = remember(viewport) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    viewport.followingBottom = false
                    viewport.userScrollRevision++
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState, viewport) {
        snapshotFlow {
            val layout = listState.layoutInfo
            // LazyColumn 会预布置 padding 区的前项；锚点只取实际正文视口中的记录。
            val contentStart = maxOf(0, layout.viewportStartOffset)
            layout.visibleItemsInfo.filter {
                it.offset + it.size > contentStart && it.offset < layout.viewportEndOffset
            }.map {
                Triple(
                    it.key,
                    it.offset,
                    it.size
                )
            }.let {
                Triple(
                    it,
                    listState.isScrollInProgress,
                    viewport.reconcilePending || viewport.restoring || !hasCurrentWindowLayout()
                )
            }
        }
            .collect { (visible, moving, reconciling) ->
                if (!viewport.restoring && !reconciling) {
                    val first =
                        visible.firstOrNull { info -> currentItems.any { it.id == info.first } }
                    val item =
                        first?.let { info -> currentItems.firstOrNull { it.id == info.first } }
                    if (first != null && item != null) {
                        if (!moving && !listState.canScrollForward) viewport.followingBottom = true
                        val anchor = CompanionReadingAnchor(
                            item.record.message.id,
                            item.id,
                            item.record.cursor,
                            (-first.second).coerceAtLeast(0),
                            viewport.followingBottom,
                            item.ui.companionBeat?.rawSpan?.start
                        )
                        viewport.anchor = anchor
                        currentCallbacks.onAnchorChange(anchor)
                    }
                }
            }
    }
    LaunchedEffect(viewport, state.items, density.fontScale, imeBottom) {
        val userRevisionAtStart = viewport.userScrollRevision
        val userWasMoving = listState.isScrollInProgress && !viewport.restoring
        viewport.reconcilePending = true
        withFrameNanos { }
        // 旧 layout 与新窗口的 key/index 尚未对应时，不能把旧偏移写到新来源。
        snapshotFlow { hasCurrentWindowLayout() && !viewport.restoring }.first { it }
        if (!userWasMoving && viewport.userScrollRevision == userRevisionAtStart) {
            snapshotFlow { listState.isScrollInProgress || viewport.restoring }.first { !it }
            if (viewport.userScrollRevision == userRevisionAtStart) restore()
        }
        viewport.previousItems = state.items
        viewport.reconcilePending = false
    }
    LaunchedEffect(listState, viewport) {
        var wasMoving = false
        snapshotFlow {
            Triple(
                listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset,
                listState.layoutInfo.visibleItemsInfo.map { it.key to it.size },
                listState.isScrollInProgress
            )
        }
            .distinctUntilChanged().collect {
                val endedMovement = wasMoving && !it.third
                wasMoving = it.third
                if (!it.third && !endedMovement && !viewport.reconcilePending) restore()
            }
    }

    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .testTag("companion_reader")
    ) {
        CompanionStageImage(
            backgroundCandidates,
            "阅读背景",
            Modifier.matchParentSize().safeDrawingPadding(),
            ContentScale.Crop
        )
        Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = NarraUi.space12),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("连续阅读", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "已载入 ${state.records.size} 条记录",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    NarraIconButton(
                        onClick = callbacks.onDismiss,
                        modifier = Modifier.testTag("companion_reader_close")
                    ) {
                        Icon(Icons.Default.Close, "关闭阅读")
                    }
                }
                callbacks.onOpenHistory?.let { open ->
                    NarraTextButton(
                        onClick = open,
                        modifier = Modifier.fillMaxWidth().testTag("companion_reader_history"),
                    ) { Text(if (state.scope?.readOnly == true) "只读旧档 · 返回历史与导出" else "历史与导出") }
                }
                state.errorMessage?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12)) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        NarraTextButton(onClick = if (state.scope == null) callbacks.onRetryOpen else callbacks.onRefresh) {
                            Text(
                                "重试"
                            )
                        }
                    }
                }
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ImmersiveReadingGlassSurface(
                    backdropState = backdrop,
                    shape = NarraShapes.medium,
                    variant = ImmersiveReadingGlassVariant.PANEL,
                    contentDarkenAlpha = 0f,
                    overlayColor = backdrop.palette.readingSurface.copy(alpha = 1f),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .padding(horizontal = NarraUi.space12)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().nestedScroll(gestures)
                            .testTag("companion_reader_list"),
                        contentPadding = PaddingValues(NarraUi.space12),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space12)
                    ) {
                        item(key = "companion_reader_older") {
                            if (state.hasOlder) NarraTextButton(
                                onClick = callbacks.onLoadOlder, enabled = !state.isLoadingOlder,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("companion_reader_load_older")
                            ) {
                                Text(if (state.isLoadingOlder) "正在读取…" else "加载更早记录")
                            } else if (!state.isLoading) Text(
                                if (state.items.isEmpty()) "此范围暂无可读记录" else "已到最早记录",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                            Column(
                                Modifier.fillMaxWidth().companionTextGestures(
                                    item.id,
                                    onClick = null,
                                    onLongClick = { menuItemId = item.id },
                                    onCancel = { menuItemId = null })
                                    .testTag("companion_reader_item_${item.id}")
                            ) {
                                if (index == 0 || state.items[index - 1].record.message.id != item.record.message.id) {
                                    Text(
                                        item.record.binding.title.ifBlank { "陪伴记录" },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    item.ui.companionReply?.let { reply ->
                                        CompanionObservationPanel(
                                            reply,
                                            enabled = settings.companionSettings(reply.source.characterId).writing.observation.enabled
                                        )
                                    }
                                    CompanionReadingImages(item.record.message)
                                }
                                RoleplayReadingMessage(
                                    item.ui,
                                    backdrop,
                                    settings.roleplayLineHeightScale.scaleFactor
                                )
                                DropdownMenu(
                                    expanded = menuItemId == item.id,
                                    onDismissRequest = { menuItemId = null }) {
                                    val unit =
                                        units.firstOrNull { it.source.messageId == item.record.message.id && it.beatId == item.ui.companionBeat?.id }
                                    if (unit != null) {
                                        DropdownMenuItem(
                                            text = { Text("选择本段") },
                                            modifier = Modifier.testTag("companion_reader_select_unit"),
                                            onClick = {
                                                callbacks.onSelectionChange(
                                                    CompanionSpeechSupport.select(
                                                        units,
                                                        unit.id
                                                    )
                                                ); startUnitId = null
                                                showSelection = true; menuItemId = null
                                            })
                                        DropdownMenuItem(
                                            text = { Text("从这里开始选择") },
                                            modifier = Modifier.testTag("companion_reader_select_start"),
                                            onClick = {
                                                startUnitId =
                                                    unit.id; callbacks.onSelectionChange(null); menuItemId =
                                                null
                                            })
                                        if (startUnitId != null) DropdownMenuItem(
                                            text = { Text("选择到这里") },
                                            modifier = Modifier.testTag("companion_reader_select_end"),
                                            onClick = {
                                                val selected = CompanionSpeechSupport.select(
                                                    units,
                                                    startUnitId!!,
                                                    unit.id
                                                )
                                                if (selected != null) {
                                                    callbacks.onSelectionChange(selected); showSelection =
                                                        true; startUnitId = null
                                                }
                                                menuItemId = null
                                            })
                                    }
                                    DropdownMenuItem(
                                        text = { Text("关闭菜单") },
                                        onClick = { menuItemId = null })
                                }
                            }
                        }
                    }
                }
                if (startUnitId != null) Text(
                    "已设置起点，长按另一段选择到那里。", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = NarraUi.space12)
                )
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(rememberScrollState())
                ) {
                    NarraTextButton(
                        onClick = callbacks.onRefresh, enabled = !state.isRefreshing,
                        modifier = Modifier.testTag("companion_reader_refresh")
                    ) { Text("刷新") }
                    NarraTextButton(
                        onClick = { viewport.followingBottom = true; scope.launch { restore() } },
                        modifier = Modifier.testTag("companion_reader_bottom")
                    ) { Text("到最新") }
                    voiceBinding?.let { CompanionVoiceToolbar(it) }
                    if (state.selection != null) {
                        NarraTextButton(
                            onClick = { showSelection = true },
                            modifier = Modifier.testTag("companion_reader_selection")
                        ) { Text("已选 ${state.selection.units.size} 段") }
                        NarraTextButton(onClick = {
                            callbacks.onSelectionChange(null); startUnitId = null
                        }) { Text("清除选区") }
                    }
                }
            }
        }
    }
    if (showSelection && state.selection != null) {
        val dismiss: () -> Unit = { showSelection = false; voiceBinding?.callbacks?.onStop?.invoke() }
        if (voiceBinding == null) CompanionSpeechSelectionPanel(state.selection, dismiss)
        else CompanionSpeechSelectionPanel(state.selection, voiceBinding, dismiss)
    }
}

@Composable
private fun CompanionReadingImages(message: ChatMessage) {
    val images = remember(message) {
        (message.parts.mapNotNull { part ->
            when {
                part.type == ChatMessagePartType.IMAGE -> part.uri
                part.actionType == ChatActionType.AI_PHOTO -> part.aiPhotoImageUri()
                part.specialType == ChatSpecialType.GIFT -> part.giftImageUri()
                else -> null
            }
        } + message.attachments.filter { it.type == AttachmentType.IMAGE }.map { it.uri })
            .filter(String::isNotBlank).distinct()
    }
    images.forEachIndexed { index, uri ->
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
            val state = rememberUserProfileAvatarState(uri, "", IntSize(width, width * 4))
            val image = state.imageBitmap
            if (state.loadState == UserAvatarLoadState.Success && image != null) {
                Image(
                    image,
                    "记录中的图片",
                    Modifier.fillMaxWidth()
                        .aspectRatio((image.width.toFloat() / image.height).coerceIn(0.125f, 8f))
                        .testTag("companion_reader_image_${message.id}_$index"),
                    contentScale = ContentScale.Fit
                )
            } else Box(Modifier.fillMaxWidth().height(NarraMinimumTouchTarget)) {
                Text(
                    if (state.loadState == UserAvatarLoadState.Loading) "正在读取图片…" else "图片暂不可读",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
