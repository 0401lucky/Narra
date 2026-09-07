package com.example.myapplication.ui.screen.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.MessageCircleMore
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ShoppingBag
import com.example.myapplication.R
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.PhoneCheckUiState

@Composable internal fun PhonePageBackground() = MaterialTheme.colorScheme.background
@Composable internal fun PhoneCardBackground() = MaterialTheme.colorScheme.surfaceContainerLow
@Composable internal fun PhoneAccent() = MaterialTheme.colorScheme.primary
@Composable internal fun PhoneAccentSoft() = MaterialTheme.colorScheme.primaryContainer
@Composable internal fun PhoneMutedText() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable internal fun PhoneLikeAccent() = MaterialTheme.colorScheme.error

@Composable
fun PhoneCheckScreen(
    uiState: PhoneCheckUiState,
    onNavigateBack: () -> Unit,
    onGenerateSnapshot: () -> Unit,
    onRefreshSections: (Set<PhoneSnapshotSection>) -> Unit,
    onLoadSearchDetail: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onClearNoticeMessage: () -> Unit,
    onOpenMailbox: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable(uiState.conversationId, uiState.scenarioId, uiState.ownerType) {
        mutableStateOf(PhoneSnapshotSection.MESSAGES)
    }
    var detailState by remember(uiState.conversationId, uiState.scenarioId, uiState.ownerType) {
        mutableStateOf<PhoneDetailState?>(null)
    }
    var showRefreshDialog by rememberSaveable(uiState.conversationId, uiState.scenarioId, uiState.ownerType) {
        mutableStateOf(false)
    }
    var selectedRefreshSections by rememberSaveable(
        uiState.conversationId,
        uiState.scenarioId,
        uiState.ownerType,
        stateSaver = listSaver<Set<PhoneSnapshotSection>, String>(
            save = { sections -> sections.map { it.storageValue } },
            restore = { values -> values.mapNotNull(PhoneSnapshotSection::fromStorageValue).toSet() },
        ),
    ) { mutableStateOf(PhoneSnapshotSection.entries.toSet()) }
    val snapshot = uiState.snapshot
    val hasContent = snapshot?.hasContent() == true

    BackHandler(enabled = detailState != null) { detailState = null }

    LaunchedEffect(snapshot) {
        if (snapshot == null) {
            detailState = null
            return@LaunchedEffect
        }
        if (detailState?.isAvailableIn(snapshot) == false) {
            detailState = null
        }
        val availableSections = snapshot.availableSections()
        if (availableSections.isNotEmpty() && selectedTab !in availableSections) {
            selectedTab = availableSections.first()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearErrorMessage()
        }
    }
    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearNoticeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("phone_screen"),
        containerColor = PhonePageBackground(),
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    PhoneTopBar(
                        ownerTitle = stringResource(
                            R.string.phone_title,
                            uiState.ownerName.ifBlank { stringResource(R.string.phone_default_owner) },
                        ),
                        showRefresh = hasContent,
                        isGenerating = uiState.isGenerating,
                        generationStatusText = if (hasContent) uiState.generationStatusText else "",
                        showingDetail = detailState != null,
                        onNavigateBack = {
                            if (detailState != null) detailState = null else onNavigateBack()
                        },
                        onRefresh = {
                            selectedRefreshSections = PhoneSnapshotSection.entries.toSet()
                            showRefreshDialog = true
                        },
                        onOpenMailbox = onOpenMailbox,
                    )
                    if (detailState == null && hasContent) {
                        PhoneSectionTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when {
                uiState.isLoading -> PhoneStatusPanel(
                    title = "正在读取手机内容",
                    description = "稍等片刻，正在整理上次保存的记录。",
                    isLoading = true,
                )
                snapshot != null && hasContent -> {
                    if (detailState != null) {
                        PhoneDetailContent(
                            snapshot = snapshot,
                            detailState = detailState,
                            loadingSearchEntryId = uiState.loadingSearchEntryId,
                            onRetrySearchDetail = onLoadSearchDetail,
                        )
                    } else if (selectedTab !in snapshot.availableSections()) {
                        PhoneStatusPanel(
                            title = "还没有" + selectedTab.displayName,
                            description = "刷新这个板块，看看新的日常。",
                            actionLabel = "刷新" + selectedTab.displayName,
                            actionEnabled = !uiState.isGenerating,
                            onAction = {
                                selectedRefreshSections = setOf(selectedTab)
                                showRefreshDialog = true
                            },
                        )
                    } else {
                        PhoneSnapshotTabsContent(
                            snapshot = snapshot,
                            selectedTab = selectedTab,
                            loadingSearchEntryId = uiState.loadingSearchEntryId,
                            onOpenMessageThread = { detailState = PhoneDetailState.MessageThread(it.id) },
                            onOpenNote = { detailState = PhoneDetailState.Note(it.id) },
                            onOpenGallery = { detailState = PhoneDetailState.Gallery(it.id) },
                            onOpenShopping = { detailState = PhoneDetailState.Shopping(it.id) },
                            onOpenSearch = { entry ->
                                detailState = PhoneDetailState.Search(entry.id)
                                if (entry.detail == null && uiState.loadingSearchEntryId != entry.id) {
                                    onLoadSearchDetail(entry.id)
                                }
                            },
                            onOpenSocialPost = { detailState = PhoneDetailState.SocialPost(it.id) },
                        )
                    }
                }
                else -> PhoneEmptyState(
                    isGenerating = uiState.isGenerating,
                    generationStatusText = uiState.generationStatusText,
                    onGenerate = onGenerateSnapshot,
                )
            }
        }
    }

    if (showRefreshDialog && hasContent) {
        RefreshSectionsDialog(
            selectedSections = selectedRefreshSections,
            isGenerating = uiState.isGenerating,
            onDismiss = { showRefreshDialog = false },
            onToggleSection = { section ->
                selectedRefreshSections = if (section in selectedRefreshSections) {
                    selectedRefreshSections - section
                } else {
                    selectedRefreshSections + section
                }
            },
            onSelectAll = { selectedRefreshSections = PhoneSnapshotSection.entries.toSet() },
            onConfirm = {
                if (selectedRefreshSections.isNotEmpty() && !uiState.isGenerating) {
                    showRefreshDialog = false
                    onRefreshSections(selectedRefreshSections)
                }
            },
        )
    }
}

@Composable
private fun PhoneTopBar(
    ownerTitle: String,
    showRefresh: Boolean,
    isGenerating: Boolean,
    generationStatusText: String,
    showingDetail: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMailbox: (() -> Unit)?,
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.compactGap, vertical = NarraUi.compactGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NarraIconButton(onClick = onNavigateBack) {
                Icon(Lucide.ArrowLeft, stringResource(R.string.common_back), Modifier.size(24.dp))
            }
            Text(
                text = if (showingDetail) stringResource(R.string.phone_detail_title) else "查手机",
                modifier = Modifier.weight(1f).testTag("phone_top_title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onOpenMailbox != null && !showingDetail) {
                NarraIconButton(onClick = onOpenMailbox) {
                    Icon(Lucide.Mail, "信箱", Modifier.size(24.dp))
                }
            }
            if (showRefresh) {
                NarraIconButton(onClick = onRefresh, enabled = !isGenerating) {
                    Icon(Lucide.RefreshCw, stringResource(R.string.phone_refresh_content), Modifier.size(24.dp))
                }
            }
        }
        if (!showingDetail) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ownerTitle,
                    modifier = Modifier.weight(1f).testTag("phone_owner"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SettingsHelpButton(
                    title = "手机内容",
                    description = stringResource(R.string.phone_empty_body) +
                        "\n\n可分别查看消息、备忘录、相册、购物、搜索和动态。点开记录查看详情；搜索详情会在首次打开时生成。" +
                        "\n\n" + stringResource(R.string.phone_refresh_dialog_body),
                )
            }
        }
        if (generationStatusText.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = NarraUi.pageHorizontal,
                    vertical = NarraUi.compactGap,
                ),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = generationStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = PhoneMutedText(),
                )
            }
        }
    }
}

@Composable
private fun PhoneSectionTabs(
    selectedTab: PhoneSnapshotSection,
    onSelect: (PhoneSnapshotSection) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab) {
        listState.animateScrollToItem(selectedTab.ordinal)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag("phone_sections"),
        contentPadding = PaddingValues(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.compactGap),
        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
    ) {
        items(PhoneSnapshotSection.entries, key = { it.storageValue }) { section ->
            val selected = selectedTab == section
            Surface(
                modifier = Modifier
                    .heightIn(min = NarraMinimumTouchTarget)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(section) })
                    .testTag("phone_tab_" + section.storageValue),
                shape = RoundedCornerShape(NarraUi.smallRadius),
                color = if (selected) PhoneAccentSoft() else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else PhoneMutedText(),
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = NarraUi.pageVertical, vertical = NarraUi.compactGap),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(section.icon(), null, Modifier.size(20.dp))
                    Text(section.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

internal fun PhoneSnapshotSection.icon(): ImageVector = when (this) {
    PhoneSnapshotSection.MESSAGES -> Lucide.MessageCircleMore
    PhoneSnapshotSection.NOTES -> Lucide.NotebookPen
    PhoneSnapshotSection.GALLERY -> Lucide.Image
    PhoneSnapshotSection.SHOPPING -> Lucide.ShoppingBag
    PhoneSnapshotSection.SEARCH -> Lucide.Search
    PhoneSnapshotSection.SOCIAL_POSTS -> Lucide.MessageCircleMore
}

internal fun PhoneSnapshot.availableSections(): List<PhoneSnapshotSection> = buildList {
    if (relationshipHighlights.isNotEmpty() || messageThreads.isNotEmpty()) add(PhoneSnapshotSection.MESSAGES)
    if (notes.isNotEmpty()) add(PhoneSnapshotSection.NOTES)
    if (gallery.isNotEmpty()) add(PhoneSnapshotSection.GALLERY)
    if (shoppingRecords.isNotEmpty()) add(PhoneSnapshotSection.SHOPPING)
    if (searchHistory.isNotEmpty()) add(PhoneSnapshotSection.SEARCH)
    if (socialPosts.isNotEmpty()) add(PhoneSnapshotSection.SOCIAL_POSTS)
}
