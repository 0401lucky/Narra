package com.example.myapplication.ui.screen.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.viewmodel.PhoneCheckUiState

private val PhonePageBackground = Color(0xFFF3F6FB)
private val PhoneCardBackground = Color.White
private val PhoneAccent = Color(0xFF5D94D8)
private val PhoneAccentSoft = Color(0xFFE8F1FF)
private val PhoneMutedText = Color(0xFF7B8797)

@Composable
fun PhoneCheckScreen(
    uiState: PhoneCheckUiState,
    onNavigateBack: () -> Unit,
    onGenerateSnapshot: () -> Unit,
    onRefreshSections: (Set<PhoneSnapshotSection>) -> Unit,
    onLoadSearchDetail: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onClearNoticeMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(PhoneSnapshotSection.MESSAGES) }
    var detailState by remember { mutableStateOf<PhoneDetailState?>(null) }
    var showRefreshDialog by remember { mutableStateOf(false) }
    var selectedRefreshSections by remember { mutableStateOf(PhoneSnapshotSection.entries.toSet()) }

    LaunchedEffect(uiState.snapshot, detailState) {
        val snapshot = uiState.snapshot ?: return@LaunchedEffect
        if (detailState != null || !snapshot.hasContent()) {
            return@LaunchedEffect
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
        containerColor = PhonePageBackground,
        snackbarHost = {
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        topBar = {
            PhoneTopBar(
                title = uiState.ownerName.ifBlank { "角色" } + "的手机",
                showRefresh = uiState.snapshot?.hasContent() == true,
                isGenerating = uiState.isGenerating,
                generationStatusText = uiState.generationStatusText,
                showingDetail = detailState != null,
                onNavigateBack = {
                    if (detailState != null) {
                        detailState = null
                    } else {
                        onNavigateBack()
                    }
                },
                onRefresh = {
                    selectedRefreshSections = PhoneSnapshotSection.entries.toSet()
                    showRefreshDialog = true
                },
            )
        },
        bottomBar = {
            if (detailState == null && uiState.snapshot?.hasContent() == true) {
                NavigationBar(containerColor = Color.White) {
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.MESSAGES,
                        selectedTab = selectedTab,
                        icon = Icons.Default.Textsms,
                        onSelect = { selectedTab = it },
                    )
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.NOTES,
                        selectedTab = selectedTab,
                        icon = Icons.Default.Description,
                        onSelect = { selectedTab = it },
                    )
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.GALLERY,
                        selectedTab = selectedTab,
                        icon = Icons.Default.Image,
                        onSelect = { selectedTab = it },
                    )
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.SHOPPING,
                        selectedTab = selectedTab,
                        icon = Icons.Default.ShoppingCart,
                        onSelect = { selectedTab = it },
                    )
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.SEARCH,
                        selectedTab = selectedTab,
                        icon = Icons.Default.Search,
                        onSelect = { selectedTab = it },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF7FAFF), PhonePageBackground),
                    ),
                )
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = PhoneAccent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在读取手机快照…", color = PhoneMutedText)
                    }
                }

                uiState.snapshot?.hasContent() == true -> {
                    if (detailState == null) {
                        PhoneSnapshotTabsContent(
                            snapshot = uiState.snapshot,
                            selectedTab = selectedTab,
                            loadingSearchEntryId = uiState.loadingSearchEntryId,
                            onOpenMessageThread = { thread ->
                                detailState = PhoneDetailState.MessageThread(thread.id)
                            },
                            onOpenNote = { entry ->
                                detailState = PhoneDetailState.Note(entry.id)
                            },
                            onOpenGallery = { entry ->
                                detailState = PhoneDetailState.Gallery(entry.id)
                            },
                            onOpenShopping = { entry ->
                                detailState = PhoneDetailState.Shopping(entry.id)
                            },
                            onOpenSearch = { entry ->
                                detailState = PhoneDetailState.Search(entry.id)
                                if (entry.detail == null) {
                                    onLoadSearchDetail(entry.id)
                                }
                            },
                        )
                    } else {
                        PhoneDetailContent(
                            snapshot = uiState.snapshot,
                            detailState = detailState,
                            loadingSearchEntryId = uiState.loadingSearchEntryId,
                        )
                    }
                }

                else -> {
                    PhoneEmptyState(
                        isGenerating = uiState.isGenerating,
                        generationStatusText = uiState.generationStatusText,
                        onGenerate = onGenerateSnapshot,
                    )
                }
            }
        }
    }

    if (showRefreshDialog && uiState.snapshot?.hasContent() == true) {
        RefreshSectionsDialog(
            selectedSections = selectedRefreshSections,
            onDismiss = { showRefreshDialog = false },
            onToggleSection = { section ->
                selectedRefreshSections = if (section in selectedRefreshSections) {
                    selectedRefreshSections - section
                } else {
                    selectedRefreshSections + section
                }
            },
            onSelectAll = {
                selectedRefreshSections = PhoneSnapshotSection.entries.toSet()
            },
            onConfirm = {
                showRefreshDialog = false
                onRefreshSections(selectedRefreshSections)
            },
        )
    }
}

@Composable
private fun PhoneTopBar(
    title: String,
    showRefresh: Boolean,
    isGenerating: Boolean,
    generationStatusText: String,
    showingDetail: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (showingDetail) "手机详情" else title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!showingDetail && generationStatusText.isNotBlank()) {
                Text(
                    text = generationStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = PhoneMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (showRefresh) {
            IconButton(onClick = onRefresh, enabled = !isGenerating) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PhoneAccent,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新手机内容",
                        tint = PhoneAccent,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun RowScope.PhoneTabNavigationItem(
    section: PhoneSnapshotSection,
    selectedTab: PhoneSnapshotSection,
    icon: ImageVector,
    onSelect: (PhoneSnapshotSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onSelect(section) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = section.displayName,
            tint = if (selectedTab == section) PhoneAccent else PhoneMutedText,
        )
        Text(
            text = section.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selectedTab == section) PhoneAccent else PhoneMutedText,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneSnapshotTabsContent(
    snapshot: PhoneSnapshot,
    selectedTab: PhoneSnapshotSection,
    loadingSearchEntryId: String,
    onOpenMessageThread: (PhoneMessageThread) -> Unit,
    onOpenNote: (PhoneNoteEntry) -> Unit,
    onOpenGallery: (PhoneGalleryEntry) -> Unit,
    onOpenShopping: (PhoneShoppingEntry) -> Unit,
    onOpenSearch: (PhoneSearchEntry) -> Unit,
) {
    when (selectedTab) {
        PhoneSnapshotSection.MESSAGES -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (snapshot.relationshipHighlights.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = PhoneCardBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "关系速览",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                snapshot.relationshipHighlights.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = PhoneAccentSoft,
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${item.relationLabel} · ${item.stance}".trim().trim('.'),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PhoneMutedText,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(snapshot.messageThreads, key = { it.id }) { thread ->
                PhoneListCard(
                    title = thread.contactName,
                    subtitle = thread.preview,
                    meta = thread.timeLabel,
                    accent = thread.relationLabel,
                    onClick = { onOpenMessageThread(thread) },
                )
            }
        }

        PhoneSnapshotSection.NOTES -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(snapshot.notes, key = { it.id }) { note ->
                PhoneListCard(
                    title = buildString {
                        if (note.icon.isNotBlank()) {
                            append(note.icon)
                            append(' ')
                        }
                        append(note.title)
                    },
                    subtitle = note.summary,
                    meta = note.timeLabel,
                    onClick = { onOpenNote(note) },
                )
            }
        }

        PhoneSnapshotSection.GALLERY -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(snapshot.gallery, key = { it.id }) { entry ->
                Surface(
                    modifier = Modifier.clickable { onOpenGallery(entry) },
                    shape = RoundedCornerShape(22.dp),
                    color = PhoneCardBackground,
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFE7EDF8), Color(0xFFDCE7F9)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = PhoneMutedText,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = entry.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = PhoneMutedText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        PhoneSnapshotSection.SHOPPING -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(snapshot.shoppingRecords, key = { it.id }) { record ->
                PhoneListCard(
                    title = record.title,
                    subtitle = record.note,
                    meta = "${record.priceLabel} · ${record.status}",
                    onClick = { onOpenShopping(record) },
                )
            }
        }

        PhoneSnapshotSection.SEARCH -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(snapshot.searchHistory, key = { it.id }) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = PhoneCardBackground,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSearch(entry) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(PhoneAccentSoft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingSearchEntryId == entry.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PhoneAccent,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = PhoneAccent,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.query, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = entry.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = PhoneMutedText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneListCard(
    title: String,
    subtitle: String,
    meta: String,
    accent: String = "",
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = PhoneCardBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PhoneAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1),
                    color = PhoneAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (accent.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PhoneAccentSoft,
                        ) {
                            Text(
                                text = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PhoneAccent,
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = PhoneMutedText,
            )
        }
    }
}

@Composable
private fun PhoneEmptyState(
    isGenerating: Boolean,
    generationStatusText: String,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(PhoneAccentSoft, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PhoneAccent,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "暂无手机内容",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "这里会保留你上次生成的手机快照。只有点击生成或刷新时，内容才会变化。",
            color = PhoneMutedText,
            textAlign = TextAlign.Center,
        )
        if (generationStatusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = generationStatusText,
                color = PhoneMutedText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        NarraButton(
            onClick = onGenerate,
            enabled = !isGenerating,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneAccent,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("生成手机内容", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PhoneDetailContent(
    snapshot: PhoneSnapshot,
    detailState: PhoneDetailState?,
    loadingSearchEntryId: String,
) {
    when (detailState) {
        is PhoneDetailState.MessageThread -> {
            val thread = snapshot.messageThreads.firstOrNull { it.id == detailState.threadId } ?: return
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(thread.contactName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                items(thread.messages, key = { it.id }) { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (message.isOwner) PhoneAccentSoft else PhoneCardBackground,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(message.senderName, fontWeight = FontWeight.SemiBold)
                            Text(message.text)
                            Text(
                                message.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = PhoneMutedText,
                            )
                        }
                    }
                }
            }
        }

        is PhoneDetailState.Note -> {
            val entry = snapshot.notes.firstOrNull { it.id == detailState.entryId } ?: return
            PhoneLongContentCard(
                title = entry.title,
                meta = entry.timeLabel,
                content = entry.content,
            )
        }

        is PhoneDetailState.Gallery -> {
            val entry = snapshot.gallery.firstOrNull { it.id == detailState.entryId } ?: return
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = PhoneCardBackground,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFE7EDF8), Color(0xFFDCE7F9)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = PhoneMutedText,
                                    modifier = Modifier.size(54.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText)
                                entry.summary.takeIf { it.isNotBlank() }?.let { summary ->
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PhoneMutedText,
                                    )
                                }
                                Text(entry.description)
                            }
                        }
                    }
                }
            }
        }

        is PhoneDetailState.Shopping -> {
            val entry = snapshot.shoppingRecords.firstOrNull { it.id == detailState.entryId } ?: return
            PhoneLongContentCard(
                title = entry.title,
                meta = "${entry.priceLabel} · ${entry.status} · ${entry.timeLabel}",
                content = buildString {
                    append(entry.note)
                    if (entry.detail.isNotBlank()) {
                        append("\n\n")
                        append(entry.detail)
                    }
                },
            )
        }

        is PhoneDetailState.Search -> {
            val entry = snapshot.searchHistory.firstOrNull { it.id == detailState.entryId } ?: return
            if (loadingSearchEntryId == entry.id && entry.detail == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = PhoneAccent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在生成搜索详情…", color = PhoneMutedText)
                }
            } else {
                val detail = entry.detail ?: return
                PhoneLongContentCard(
                    title = detail.title,
                    meta = entry.timeLabel,
                    content = buildString {
                        if (detail.summary.isNotBlank()) {
                            append(detail.summary)
                            append("\n\n")
                        }
                        append(detail.content)
                    },
                )
            }
        }

        null -> Unit
    }
}

@Composable
private fun PhoneLongContentCard(
    title: String,
    meta: String,
    content: String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = PhoneCardBackground,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText)
                    Text(content)
                }
            }
        }
    }
}

@Composable
private fun RefreshSectionsDialog(
    selectedSections: Set<PhoneSnapshotSection>,
    onDismiss: () -> Unit,
    onToggleSection: (PhoneSnapshotSection) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择刷新内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NarraTextButton(onClick = onSelectAll) {
                    Text("全选")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhoneSnapshotSection.entries.forEach { section ->
                        FilterChip(
                            selected = section in selectedSections,
                            onClick = { onToggleSection(section) },
                            label = { Text(section.displayName) },
                        )
                    }
                }
                Text(
                    text = "只会替换你勾选的板块，其他内容会保持上次快照不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhoneMutedText,
                )
            }
        },
        confirmButton = {
            NarraTextButton(
                onClick = onConfirm,
                enabled = selectedSections.isNotEmpty(),
            ) {
                Text("确认刷新")
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private sealed interface PhoneDetailState {
    data class MessageThread(val threadId: String) : PhoneDetailState
    data class Note(val entryId: String) : PhoneDetailState
    data class Gallery(val entryId: String) : PhoneDetailState
    data class Shopping(val entryId: String) : PhoneDetailState
    data class Search(val entryId: String) : PhoneDetailState
}

private fun PhoneSnapshot.availableSections(): List<PhoneSnapshotSection> {
    return buildList {
        if (relationshipHighlights.isNotEmpty() || messageThreads.isNotEmpty()) {
            add(PhoneSnapshotSection.MESSAGES)
        }
        if (notes.isNotEmpty()) {
            add(PhoneSnapshotSection.NOTES)
        }
        if (gallery.isNotEmpty()) {
            add(PhoneSnapshotSection.GALLERY)
        }
        if (shoppingRecords.isNotEmpty()) {
            add(PhoneSnapshotSection.SHOPPING)
        }
        if (searchHistory.isNotEmpty()) {
            add(PhoneSnapshotSection.SEARCH)
        }
    }
}
