package com.example.myapplication.ui.screen.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.myapplication.R
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.viewmodel.PhoneCheckUiState

// ── Phone 模块共用调色板（internal，同包其他文件共享） ──

@Composable internal fun PhonePageBackground() = if (isSystemInDarkTheme()) Color(0xFF1A1D24) else Color(0xFFF3F6FB)
@Composable internal fun PhoneCardBackground() = if (isSystemInDarkTheme()) Color(0xFF242830) else Color.White
@Composable internal fun PhoneAccent() = if (isSystemInDarkTheme()) Color(0xFF7DB1E8) else Color(0xFF5D94D8)
@Composable internal fun PhoneAccentSoft() = if (isSystemInDarkTheme()) Color(0xFF1E2A3E) else Color(0xFFE8F1FF)
@Composable internal fun PhoneMutedText() = if (isSystemInDarkTheme()) Color(0xFF8A94A3) else Color(0xFF7B8797)
@Composable internal fun PhoneLikeAccent() = if (isSystemInDarkTheme()) Color(0xFFEF6B85) else Color(0xFFE8456B)
@Composable
internal fun PhoneGalleryPlaceholderBrush(): Brush = Brush.linearGradient(
    colors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF253142), Color(0xFF1C2635))
    } else {
        listOf(Color(0xFFE7EDF8), Color(0xFFDCE7F9))
    },
)

@Composable
internal fun PhonePageGradientBrush(): Brush = Brush.verticalGradient(
    colors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF141B27), PhonePageBackground())
    } else {
        listOf(Color(0xFFF7FAFF), PhonePageBackground())
    },
)

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
        containerColor = PhonePageBackground(),
        snackbarHost = {
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        topBar = {
            PhoneTopBar(
                title = stringResource(
                    R.string.phone_title,
                    uiState.ownerName.ifBlank { stringResource(R.string.phone_default_owner) },
                ),
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
                onOpenMailbox = onOpenMailbox,
            )
        },
        bottomBar = {
            if (detailState == null && uiState.snapshot?.hasContent() == true) {
                NavigationBar(containerColor = PhoneCardBackground()) {
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
                    PhoneTabNavigationItem(
                        section = PhoneSnapshotSection.SOCIAL_POSTS,
                        selectedTab = selectedTab,
                        icon = Icons.Default.Forum,
                        onSelect = { selectedTab = it },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PhonePageGradientBrush())
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = PhoneAccent())
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.phone_loading_snapshot), color = PhoneMutedText())
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
                            onOpenSocialPost = { post ->
                                detailState = PhoneDetailState.SocialPost(post.id)
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
    onOpenMailbox: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NarraIconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (showingDetail) stringResource(R.string.phone_detail_title) else title,
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
                    color = PhoneMutedText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onOpenMailbox != null && !showingDetail) {
                NarraIconButton(onClick = onOpenMailbox) {
                    Icon(
                        imageVector = Icons.Default.Mail,
                        contentDescription = "信箱",
                        tint = PhoneAccent(),
                    )
                }
            }
        }
        if (showRefresh) {
            NarraIconButton(onClick = onRefresh, enabled = !isGenerating) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PhoneAccent(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.phone_refresh_content),
                        tint = PhoneAccent(),
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
    val isSelected = selectedTab == section
    val accent = PhoneAccent()
    val muted = PhoneMutedText()
    NavigationBarItem(
        selected = isSelected,
        onClick = { onSelect(section) },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = section.displayName,
            )
        },
        label = {
            Text(
                text = section.displayName,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = accent,
            selectedTextColor = accent,
            unselectedIconColor = muted,
            unselectedTextColor = muted,
            indicatorColor = accent.copy(alpha = 0.12f),
        ),
    )
}

internal fun PhoneSnapshot.availableSections(): List<PhoneSnapshotSection> {
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
        if (socialPosts.isNotEmpty()) {
            add(PhoneSnapshotSection.SOCIAL_POSTS)
        }
    }
}
