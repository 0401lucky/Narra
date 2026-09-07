package com.example.myapplication.ui.screen.moments

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.MessageCircleMore
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.X
import com.example.myapplication.R
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentMediaStatus
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.voice.VoiceInputAnchor
import com.example.myapplication.viewmodel.appendRecognizedText
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft
import com.example.myapplication.ui.theme.MomentsBackground
import com.example.myapplication.ui.theme.MomentsCardBackground
import com.example.myapplication.ui.theme.MomentsLikeRed
import com.example.myapplication.ui.theme.MomentsMutedText
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.MomentsUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    uiState: MomentsUiState,
    viewerName: String,
    onNavigateBack: () -> Unit,
    onPublishPost: (String, String, String, String) -> Unit,
    onToggleLikePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onDeletePosts: (List<String>) -> Unit,
    onClearMoments: () -> Unit,
    onAddComment: (String, String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onRefreshMoments: () -> Unit,
    onUpdateCoverImage: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onOpenPhoneCheck: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var showTimelineMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showClearMomentsDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var composerSubmitPending by rememberSaveable { mutableStateOf(false) }
    var composerPreviousPublishedId by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = showComposer || selectionMode || selectedPostId != null) {
        when {
            showComposer -> showComposer = false
            selectionMode -> {
                selectionMode = false
                selectedPostIds = emptySet()
            }
            else -> selectedPostId = null
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    LaunchedEffect(showComposer, composerSubmitPending, uiState.lastPublishedPostId) {
        if (!showComposer) {
            composerSubmitPending = false
            return@LaunchedEffect
        }
        // 快速本地写入可能合并掉 loading 帧，发布成功也不能被后续的回复失败覆盖。
        if (composerSubmitPending && uiState.lastPublishedPostId.isNotBlank() &&
            uiState.lastPublishedPostId != composerPreviousPublishedId
        ) {
            showComposer = false
            composerSubmitPending = false
        }
    }

    LaunchedEffect(uiState.posts) {
        val existingIds = uiState.posts.map(MomentPost::id).toSet()
        selectedPostIds = selectedPostIds.intersect(existingIds)
        if (selectedPostId !in existingIds) {
            selectedPostId = null
        }
        if (uiState.posts.isEmpty()) {
            selectionMode = false
            showTimelineMenu = false
        }
    }

    if (showClearMomentsDialog) {
        MomentsClearConfirmDialog(
            postCount = uiState.posts.size,
            onConfirm = {
                showClearMomentsDialog = false
                selectionMode = false
                selectedPostIds = emptySet()
                onClearMoments()
            },
            onDismiss = { showClearMomentsDialog = false },
        )
    }

    if (showDeleteSelectedDialog) {
        MomentsDeleteSelectedConfirmDialog(
            selectedCount = selectedPostIds.size,
            onConfirm = {
                val ids = selectedPostIds.toList()
                showDeleteSelectedDialog = false
                selectionMode = false
                selectedPostIds = emptySet()
                onDeletePosts(ids)
            },
            onDismiss = { showDeleteSelectedDialog = false },
        )
    }

    Scaffold(
        modifier = Modifier.testTag("moments_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            showComposer -> "发表朋友圈"
                            selectionMode -> "已选 ${selectedPostIds.size} 条"
                            else -> stringResource(R.string.moments_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    NarraIconButton(
                        onClick = {
                            when {
                                showComposer -> showComposer = false
                                selectionMode -> {
                                    selectionMode = false
                                    selectedPostIds = emptySet()
                                }
                                selectedPostId != null -> selectedPostId = null
                                else -> onNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        val allSelected = uiState.posts.isNotEmpty() &&
                            selectedPostIds.size == uiState.posts.size
                        TextButton(
                            onClick = {
                                selectedPostIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    uiState.posts.map(MomentPost::id).toSet()
                                }
                            },
                            enabled = uiState.posts.isNotEmpty() && !uiState.isDeletingMoments,
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        NarraIconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedPostIds.isNotEmpty() && !uiState.isDeletingMoments,
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = "删除选中朋友圈",
                                tint = if (selectedPostIds.isNotEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MomentsMutedText()
                                },
                            )
                        }
                    } else if (!showComposer && selectedPostId == null) {
                        NarraIconButton(
                            onClick = { showComposer = true },
                            enabled = !uiState.isDeletingMoments,
                        ) {
                            Icon(Lucide.Camera, contentDescription = "发表朋友圈")
                        }
                        if (uiState.posts.isNotEmpty()) {
                            Box {
                                NarraIconButton(
                                    onClick = { showTimelineMenu = true },
                                    enabled = !uiState.isDeletingMoments,
                                ) {
                                    Icon(Lucide.Ellipsis, contentDescription = "朋友圈更多操作")
                                }
                                DropdownMenu(
                                    expanded = showTimelineMenu,
                                    onDismissRequest = { showTimelineMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("选择删除") },
                                        leadingIcon = {
                                            Icon(Lucide.Trash2, contentDescription = null)
                                        },
                                        onClick = {
                                            showTimelineMenu = false
                                            selectionMode = true
                                            selectedPostIds = emptySet()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("清空朋友圈") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Lucide.Trash2,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showTimelineMenu = false
                                            showClearMomentsDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = MomentsBackground(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(color = MomentsAccent())
                        Text("正在读取朋友圈", color = MomentsMutedText())
                    }
                }

                showComposer -> {
                    MomentComposerPage(
                        isPublishing = uiState.isPublishing,
                        masks = uiState.userPersonaMasks,
                        selectedMaskId = uiState.selectedUserPersonaMaskId,
                        onPublishPost = { content, imageUri, location, maskId ->
                            composerPreviousPublishedId = uiState.lastPublishedPostId
                            composerSubmitPending = true
                            onPublishPost(content, imageUri, location, maskId)
                        },
                        onCancel = { showComposer = false },
                    )
                }

                else -> {
                    val selectedPost = selectedPostId?.let { id ->
                        uiState.posts.firstOrNull { it.id == id }
                    }
                    Crossfade(
                        targetState = selectedPost,
                        label = "moments_content",
                    ) { post ->
                        if (post == null) {
                            MomentsTimelineContent(
                                posts = uiState.posts,
                                viewerName = viewerName,
                                viewerAvatarUri = uiState.viewerAvatarUri,
                                coverImageUri = uiState.momentsSettings.coverImageUri,
                                isRefreshing = uiState.isRefreshing,
                                isDeletingMoments = uiState.isDeletingMoments,
                                selectionMode = selectionMode,
                                selectedPostIds = selectedPostIds,
                                retryingImagePostId = uiState.retryingImagePostId,
                                onRefresh = onRefreshMoments,
                                onUpdateCoverImage = onUpdateCoverImage,
                                onOpenPost = { selectedPostId = it.id },
                                onCompose = { showComposer = true },
                                onTogglePostSelected = { postId ->
                                    selectedPostIds = selectedPostIds.toggle(postId)
                                },
                                onToggleLike = onToggleLikePost,
                                onDeletePost = onDeletePost,
                                onDeleteComment = onDeleteComment,
                                onRetryImage = onRetryImage,
                                onOpenPhoneCheck = onOpenPhoneCheck,
                            )
                        } else {
                            MomentsDetailContent(
                                post = post,
                                viewerName = viewerName,
                                isGeneratingReplies = uiState.isGeneratingReplies && uiState.replyingPostId == post.id,
                                isRetryingImage = uiState.retryingImagePostId == post.id,
                                onToggleLike = { onToggleLikePost(post.id) },
                                onDeletePost = {
                                    onDeletePost(post.id)
                                    selectedPostId = null
                                },
                                onAddComment = { text, replyToCommentId ->
                                    onAddComment(post.id, text, replyToCommentId)
                                },
                                onDeleteComment = onDeleteComment,
                                onRetryImage = { onRetryImage(post.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentsTimelineContent(
    posts: List<MomentPost>,
    viewerName: String,
    viewerAvatarUri: String,
    coverImageUri: String,
    isRefreshing: Boolean,
    isDeletingMoments: Boolean,
    selectionMode: Boolean,
    selectedPostIds: Set<String>,
    retryingImagePostId: String,
    onRefresh: () -> Unit,
    onUpdateCoverImage: (String) -> Unit,
    onOpenPost: (MomentPost) -> Unit,
    onTogglePostSelected: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onCompose: () -> Unit,
    onOpenPhoneCheck: (() -> Unit)?,
) {
    val coroutineScope = rememberCoroutineScope()
    val localImageStore = LocalImagePersister.current
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val localPath = localImageStore.copyToAppStorage(uri, MOMENT_COVER_IMAGE_SCOPE)
                onUpdateCoverImage(localPath ?: uri.toString())
            }
        },
    )
    // 评论预览行分散在各卡片里，确认框状态统一由时间线持有，避免每张卡片重复一份
    var pendingDeleteComment by remember { mutableStateOf<MomentComment?>(null) }

    pendingDeleteComment?.let { comment ->
        MomentCommentDeleteConfirmDialog(
            comment = comment,
            onConfirm = {
                pendingDeleteComment = null
                onDeleteComment(comment.id)
            },
            onDismiss = { pendingDeleteComment = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!selectionMode && !isDeletingMoments) {
                onRefresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("moments_timeline"),
            contentPadding = PaddingValues(bottom = NarraUi.pageVertical),
            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        ) {
            item {
                MomentsCoverHeader(
                    coverImageUri = coverImageUri,
                    viewerName = viewerName,
                    viewerAvatarUri = viewerAvatarUri,
                    onPickCover = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            if (posts.isEmpty()) {
                item {
                    EmptyMomentsPanel(onCompose = onCompose, onOpenPhoneCheck = onOpenPhoneCheck)
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    MomentPostTimelineItem(
                        post = post,
                        viewerName = viewerName,
                        selectionMode = selectionMode,
                        selected = post.id in selectedPostIds,
                        enabled = !isDeletingMoments,
                        isRetryingImage = retryingImagePostId == post.id,
                        onOpenPost = { onOpenPost(post) },
                        onToggleSelection = { onTogglePostSelected(post.id) },
                        onToggleLike = { onToggleLike(post.id) },
                        onDeletePost = { onDeletePost(post.id) },
                        onRequestDeleteComment = { pendingDeleteComment = it },
                        onRetryImage = { onRetryImage(post.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentsCoverHeader(
    coverImageUri: String,
    viewerName: String,
    viewerAvatarUri: String,
    onPickCover: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = NarraUi.pageHorizontal,
            vertical = NarraUi.pageVertical,
        ),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = MomentsCardBackground(),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(144.dp).testTag("moments_cover")
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClickLabel = "更换朋友圈封面", onClick = onPickCover),
                contentAlignment = Alignment.Center,
            ) {
                if (coverImageUri.isNotBlank()) {
                    AsyncImage(
                        model = coverImageUri,
                        contentDescription = "朋友圈封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Lucide.Image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(NarraUi.compactGap),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                ) {
                    NarraIconButton(onClick = onPickCover) {
                        Icon(Lucide.Camera, "更换朋友圈封面", Modifier.size(20.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MomentAvatar(
                    name = viewerName,
                    avatarUri = viewerAvatarUri,
                    size = 48.dp,
                    fallback = "我",
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                ) {
                    Text(
                        text = viewerName.ifBlank { "我" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("我的朋友圈", style = MaterialTheme.typography.bodySmall, color = MomentsMutedText())
                }
                SettingsHelpButton(
                    title = "朋友圈",
                    description = "发表动态时可以添加配图、位置并选择身份，角色会按各自人设参与互动。" +
                        "\n\n下拉可刷新动态。点开动态可查看全部评论和回复；长按评论可删除，删除前会再次确认。" +
                        "\n\n点封面可更换图片，右上角菜单可选择删除或清空朋友圈。",
                )
            }
        }
    }
}

@Composable
private fun MomentComposerPage(
    isPublishing: Boolean,
    masks: List<UserPersonaMask>,
    selectedMaskId: String,
    onPublishPost: (String, String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val localImageStore = LocalImagePersister.current
    var draft by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var selectedPersonaMaskId by rememberSaveable(selectedMaskId) {
        mutableStateOf(
            selectedMaskId.takeIf { id -> masks.any { it.id == id } }
                ?: masks.firstOrNull()?.id.orEmpty(),
        )
    }
    var showMaskMenu by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val localPath = localImageStore.copyToAppStorage(uri, MOMENT_POST_IMAGE_SCOPE)
                imageUri = localPath ?: uri.toString()
            }
        },
    )
    val selectedMask = masks.firstOrNull { it.id == selectedPersonaMaskId }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = NarraUi.pageHorizontal,
                vertical = NarraUi.compactGap,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
            ) { Text("取消") }
            Button(
                onClick = {
                    onPublishPost(draft.trim(), imageUri, location.trim(), selectedPersonaMaskId)
                },
                enabled = draft.isNotBlank() && !isPublishing,
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag("moment_publish"),
                shape = RoundedCornerShape(NarraUi.smallRadius),
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(NarraUi.compactGap))
                }
                Text(if (isPublishing) "发表中" else "发表")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("moment_composer"),
            contentPadding = PaddingValues(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.compactGap),
            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        ) {
            item {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(300) },
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("moment_draft"),
                    minLines = 5,
                    maxLines = 8,
                    placeholder = { Text("这一刻的想法…") },
                    supportingText = { Text(draft.length.toString() + " / 300") },
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                )
            }
            if (!isPublishing) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VoiceInputAnchor(onTextRecognized = { recognized ->
                            draft = appendRecognizedText(draft, recognized).take(300)
                        })
                    }
                }
            }
            item {
                ComposerImagePicker(
                    imageUri = imageUri,
                    enabled = !isPublishing,
                    onPickImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClearImage = { imageUri = "" },
                )
            }
            item {
                ComposerSectionRow(
                    icon = { Icon(Lucide.MapPin, contentDescription = null) },
                    title = "所在位置",
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it.take(40) },
                        enabled = !isPublishing,
                        modifier = Modifier.fillMaxWidth().testTag("moment_location"),
                        singleLine = true,
                        placeholder = { Text("不显示位置") },
                        shape = RoundedCornerShape(NarraUi.smallRadius),
                    )
                }
            }
            item {
                ComposerSectionRow(
                    icon = { Icon(Lucide.UserRound, contentDescription = null) },
                    title = "选择身份",
                ) {
                    Box {
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = NarraMinimumTouchTarget)
                                .clickable(enabled = !isPublishing) { showMaskMenu = true }
                                .testTag("moment_identity"),
                            shape = RoundedCornerShape(NarraUi.smallRadius),
                            color = MomentsCardBackground(),
                        ) {
                            Row(
                                modifier = Modifier.padding(NarraUi.pageVertical),
                                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedMask?.name.orEmpty().ifBlank { "当前身份" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(Lucide.ChevronDown, null, Modifier.size(20.dp), tint = MomentsMutedText())
                            }
                        }
                        DropdownMenu(
                            expanded = showMaskMenu,
                            onDismissRequest = { showMaskMenu = false },
                        ) {
                            if (masks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("默认身份") },
                                    onClick = {
                                        selectedPersonaMaskId = ""
                                        showMaskMenu = false
                                    },
                                )
                            } else {
                                masks.forEach { mask ->
                                    DropdownMenuItem(
                                        text = { Text(mask.name.ifBlank { "未命名身份" }, maxLines = 3) },
                                        onClick = {
                                            selectedPersonaMaskId = mask.id
                                            showMaskMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerImagePicker(
    imageUri: String,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    Box(
        modifier = Modifier.size(112.dp)
            .clip(RoundedCornerShape(NarraUi.smallRadius))
            .background(MomentsCardBackground())
            .clickable(enabled = enabled, onClickLabel = "添加配图", onClick = onPickImage),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri.isBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            ) {
                Icon(Lucide.Image, null, Modifier.size(24.dp), tint = MomentsMutedText())
                Text("添加配图", style = MaterialTheme.typography.labelMedium, color = MomentsMutedText())
            }
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = "已选配图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(NarraUi.compactGap),
                shape = RoundedCornerShape(NarraUi.smallRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                NarraIconButton(onClick = onClearImage, enabled = enabled) {
                    Icon(Lucide.X, "移除配图", Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ComposerSectionRow(
    icon: @Composable () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        content()
    }
}

@Composable
private fun EmptyMomentsPanel(
    onCompose: () -> Unit,
    onOpenPhoneCheck: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.pageHorizontal, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
    ) {
        Icon(Lucide.MessageCircleMore, null, Modifier.size(32.dp), tint = MomentsAccent())
        Text("还没有朋友圈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "分享这一刻，和朋友聊聊天。",
            style = MaterialTheme.typography.bodyMedium,
            color = MomentsMutedText(),
        )
        Button(
            onClick = onCompose,
            modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
            shape = RoundedCornerShape(NarraUi.smallRadius),
        ) { Text("发一条动态") }
        if (onOpenPhoneCheck != null) {
            TextButton(
                onClick = onOpenPhoneCheck,
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
            ) { Text("去查手机") }
        }
    }
}

@Composable
private fun MomentPostTimelineItem(
    post: MomentPost,
    viewerName: String,
    selectionMode: Boolean,
    selected: Boolean,
    enabled: Boolean,
    isRetryingImage: Boolean,
    onOpenPost: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleLike: () -> Unit,
    onDeletePost: () -> Unit,
    onRequestDeleteComment: (MomentComment) -> Unit,
    onRetryImage: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        MomentDeleteConfirmDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeletePost()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.pageHorizontal)
            .clip(RoundedCornerShape(NarraUi.cardRadius))
            .clickable(enabled = enabled) {
                if (selectionMode) onToggleSelection() else onOpenPost()
            }
            .testTag("moment_post_" + post.id),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = MomentsCardBackground(),
    ) {
        Column(
            modifier = Modifier.padding(NarraUi.pageVertical),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.Top,
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection() },
                        enabled = enabled,
                        modifier = Modifier.testTag("moment_select_" + post.id),
                    )
                }
                MomentAvatar(post.authorName, post.authorAvatarUri, 40.dp, "朋")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap)) {
                    Text(
                        text = post.authorName.ifBlank { "朋友圈" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatMomentTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MomentsMutedText())
                }
                if (!selectionMode) {
                    MomentMoreMenuButton(
                        expanded = showMoreMenu,
                        enabled = enabled,
                        onExpandedChange = { showMoreMenu = it },
                        onDeleteClick = {
                            showMoreMenu = false
                            showDeleteDialog = true
                        },
                    )
                }
            }
            Text(post.content, style = MaterialTheme.typography.bodyLarge)
            MomentMediaContent(
                post = post,
                isRetryingImage = isRetryingImage,
                enabled = enabled && !selectionMode,
                onRetryImage = onRetryImage,
            )
            if (post.location.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.MapPin, null, Modifier.size(16.dp), tint = MomentsMutedText())
                    Text(
                        post.location,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MomentsMutedText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MomentActionRow(
                    post = post,
                    viewerName = viewerName,
                    enabled = enabled,
                    onToggleLike = { if (selectionMode) onToggleSelection() else onToggleLike() },
                )
                TextButton(
                    onClick = { if (selectionMode) onToggleSelection() else onOpenPost() },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                ) {
                    Icon(Lucide.MessageCircleMore, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(NarraUi.compactGap))
                    Text(if (post.comments.isEmpty()) "评论" else post.comments.size.toString())
                }
            }
            MomentEngagementBlock(
                post = post,
                onCommentPreviewClick = if (enabled) {
                    { if (selectionMode) onToggleSelection() else onOpenPost() }
                } else null,
                onCommentPreviewLongClick = if (selectionMode || !enabled) null else onRequestDeleteComment,
            )
        }
    }
}

@Composable
private fun MomentMoreMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box {
        NarraIconButton(onClick = { onExpandedChange(true) }, enabled = enabled) {
            Icon(Lucide.Ellipsis, "更多操作", Modifier.size(24.dp), tint = MomentsMutedText())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Lucide.Trash2, null, tint = MaterialTheme.colorScheme.error) },
                onClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun MomentDeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NarraAlertDialog(
        title = "删除这条朋友圈？",
        message = "删除后，这条动态下的评论和配图也会一起移除。",
        confirmLabel = "删除",
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MomentCommentDeleteConfirmDialog(
    comment: MomentComment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NarraAlertDialog(
        title = "删除这条回复？",
        message = "将删除 " + comment.authorName + " 的这条回复，删除后无法恢复。",
        confirmLabel = "删除",
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MomentsClearConfirmDialog(
    postCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NarraAlertDialog(
        title = "清空朋友圈？",
        message = "将删除当前朋友圈里的 $postCount 条动态，评论和配图也会一起移除。",
        confirmLabel = "清空",
        confirmEnabled = postCount > 0,
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MomentsDeleteSelectedConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NarraAlertDialog(
        title = "删除选中的朋友圈？",
        message = "将删除已选中的 $selectedCount 条动态，评论和配图也会一起移除。",
        confirmLabel = "删除选中",
        confirmEnabled = selectedCount > 0,
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MomentMediaContent(
    post: MomentPost,
    isRetryingImage: Boolean,
    onRetryImage: () -> Unit,
    enabled: Boolean = true,
) {
    val media = post.media ?: return
    when (media.status) {
        MomentMediaStatus.GENERATING -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(NarraUi.smallRadius),
            color = MomentsAccentSoft(),
        ) {
            Row(
                modifier = Modifier.padding(NarraUi.pageVertical),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("图片生成中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        MomentMediaStatus.SUCCEEDED -> {
            if (media.imageUri.isNotBlank()) {
                AsyncImage(
                    model = media.imageUri,
                    contentDescription = media.fileName.ifBlank { "朋友圈配图" },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.25f)
                        .clip(RoundedCornerShape(NarraUi.smallRadius)).background(MomentsAccentSoft()),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        MomentMediaStatus.FAILED -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(NarraUi.smallRadius),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(NarraUi.compactGap)) {
                Text(
                    "配图生成失败",
                    modifier = Modifier.padding(horizontal = NarraUi.compactGap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MomentsMutedText(),
                )
                TextButton(
                    onClick = onRetryImage,
                    enabled = enabled && !isRetryingImage,
                    modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag("moment_retry_" + post.id),
                ) { Text(if (isRetryingImage) "重试中" else "重试配图") }
            }
        }
    }
}

@Composable
private fun MomentActionRow(
    post: MomentPost,
    viewerName: String,
    onToggleLike: () -> Unit,
    enabled: Boolean = true,
) {
    val isLikedByViewer = viewerName in post.likedByNames
    TextButton(
        onClick = onToggleLike,
        enabled = enabled,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag("moment_like_" + post.id),
        contentPadding = PaddingValues(horizontal = NarraUi.compactGap, vertical = NarraUi.compactGap),
    ) {
        Icon(
            imageVector = Lucide.Heart,
            contentDescription = if (isLikedByViewer) "取消赞" else "赞",
            modifier = Modifier.size(20.dp),
            tint = if (isLikedByViewer) MomentsLikeRed() else MomentsMutedText(),
        )
        Spacer(Modifier.width(NarraUi.compactGap))
        Text(
            text = if (post.likeCount > 0) post.likeCount.toString() else "赞",
            color = if (isLikedByViewer) MomentsLikeRed() else MomentsMutedText(),
        )
    }
}

@Composable
private fun MomentEngagementBlock(
    post: MomentPost,
    showCommentPreview: Boolean = true,
    onCommentPreviewClick: (() -> Unit)? = null,
    onCommentPreviewLongClick: ((MomentComment) -> Unit)? = null,
) {
    if (post.likedByNames.isEmpty() && (!showCommentPreview || post.comments.isEmpty())) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(NarraUi.compactGap),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            if (post.likedByNames.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Lucide.Heart, null, Modifier.size(16.dp), tint = MomentsAccent())
                    Text(
                        text = post.likedByNames.joinToString("、"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MomentsAccent(),
                    )
                }
            }
            if (showCommentPreview) {
                val haptic = LocalHapticFeedback.current
                post.comments.take(4).forEach { comment ->
                    val previewModifier = if (onCommentPreviewClick == null && onCommentPreviewLongClick == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                            .combinedClickable(
                                onClick = { onCommentPreviewClick?.invoke() },
                                onLongClickLabel = "删除回复",
                                onLongClick = onCommentPreviewLongClick?.let { requestDelete ->
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        requestDelete(comment)
                                    }
                                },
                            )
                    }
                    Text(
                        text = comment.authorName + "：" + comment.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = previewModifier.padding(vertical = NarraUi.compactGap)
                            .testTag("moment_comment_preview_" + comment.id),
                    )
                }
                if (post.comments.size > 4) {
                    TextButton(
                        onClick = { onCommentPreviewClick?.invoke() },
                        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                    ) { Text(stringResource(R.string.moments_view_all_comments, post.comments.size)) }
                }
            }
        }
    }
}

@Composable
private fun MomentsDetailContent(
    post: MomentPost,
    viewerName: String,
    isGeneratingReplies: Boolean,
    isRetryingImage: Boolean,
    onToggleLike: () -> Unit,
    onDeletePost: () -> Unit,
    onAddComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onRetryImage: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var commentInput by rememberSaveable(post.id) { mutableStateOf("") }
    var replyTargetId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    val replyTarget = post.comments.firstOrNull { it.id == replyTargetId }
    var pendingDeleteComment by remember(post.id) { mutableStateOf<MomentComment?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        MomentDeleteConfirmDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeletePost()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    pendingDeleteComment?.let { comment ->
        MomentCommentDeleteConfirmDialog(
            comment = comment,
            onConfirm = {
                pendingDeleteComment = null
                if (replyTargetId == comment.id) replyTargetId = null
                onDeleteComment(comment.id)
            },
            onDismiss = { pendingDeleteComment = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("moment_detail_list"),
            contentPadding = PaddingValues(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.pageVertical),
            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        ) {
            item {
                Surface(shape = RoundedCornerShape(NarraUi.cardRadius), color = MomentsCardBackground()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                    ) {
                        MomentAuthorRow(
                            post = post,
                            trailingContent = {
                                MomentMoreMenuButton(
                                    expanded = showMoreMenu,
                                    onExpandedChange = { showMoreMenu = it },
                                    onDeleteClick = {
                                        showMoreMenu = false
                                        showDeleteDialog = true
                                    },
                                )
                            },
                        )
                        Text(post.content, style = MaterialTheme.typography.bodyLarge)
                        MomentMediaContent(post, isRetryingImage, onRetryImage)
                        if (post.location.isNotBlank()) {
                            Text(post.location, style = MaterialTheme.typography.bodySmall, color = MomentsMutedText())
                        }
                        MomentActionRow(post, viewerName, onToggleLike)
                        MomentEngagementBlock(post = post, showCommentPreview = false)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.moments_comments_title, post.comments.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SettingsHelpButton(
                        title = "评论互动",
                        description = "点按一条评论可回复对方。长按评论可删除，确认后无法恢复。\n\n评论发送后，角色会按各自人设回应。",
                    )
                }
            }
            if (post.comments.isEmpty() && !isGeneratingReplies) {
                item { Text("还没有评论，聊聊这一刻吧。", color = MomentsMutedText()) }
            }
            items(post.comments, key = { it.id }) { comment ->
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget)
                        .combinedClickable(
                            onClick = { replyTargetId = comment.id },
                            onLongClickLabel = "删除回复",
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pendingDeleteComment = comment
                            },
                        )
                        .testTag("moment_comment_" + comment.id),
                    shape = RoundedCornerShape(NarraUi.cardRadius),
                    color = MomentsCardBackground(),
                ) {
                    Column(
                        modifier = Modifier.padding(NarraUi.pageVertical),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MomentAvatar(comment.authorName, comment.authorAvatarUri, 32.dp, "评")
                            Text(
                                text = comment.authorName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { replyTargetId = comment.id },
                                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                            ) { Text("回复") }
                        }
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (isGeneratingReplies) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.moments_replying_hint), color = MomentsMutedText())
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = NarraUi.pageHorizontal,
                    vertical = NarraUi.compactGap,
                ),
                verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            ) {
                replyTarget?.let { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "回复 " + target.authorName,
                            modifier = Modifier.weight(1f).testTag("moment_reply_target"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MomentsAccent(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        NarraIconButton(onClick = { replyTargetId = null }) {
                            Icon(Lucide.X, "取消回复", Modifier.size(20.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it.take(160) },
                    modifier = Modifier.fillMaxWidth().testTag("moment_comment_input"),
                    placeholder = { Text(stringResource(R.string.moments_comment_placeholder)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceInputAnchor(
                        modifier = Modifier.weight(1f, fill = false),
                        onTextRecognized = { recognized ->
                            commentInput = appendRecognizedText(commentInput, recognized).take(160)
                        },
                    )
                    Button(
                        onClick = {
                            if (commentInput.isNotBlank() && !isGeneratingReplies) {
                                onAddComment(commentInput.trim(), replyTarget?.id.orEmpty())
                                commentInput = ""
                                replyTargetId = null
                            }
                        },
                        enabled = commentInput.isNotBlank() && !isGeneratingReplies,
                        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag("moment_send_comment"),
                        shape = RoundedCornerShape(NarraUi.smallRadius),
                    ) {
                        Icon(Lucide.Send, stringResource(R.string.moments_send_comment), Modifier.size(20.dp))
                        Spacer(Modifier.width(NarraUi.compactGap))
                        Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentAuthorRow(
    post: MomentPost,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentAvatar(
            name = post.authorName,
            avatarUri = post.authorAvatarUri,
            size = 40.dp,
            fallback = "朋",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Text(
                text = post.authorName.ifBlank { "朋友圈" },
                fontWeight = FontWeight.SemiBold,
                color = MomentsAccent(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMomentTime(post.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MomentsMutedText(),
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun MomentAvatar(
    name: String,
    avatarUri: String,
    size: androidx.compose.ui.unit.Dp,
    fallback: String,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MomentsAccentSoft()),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUri.isNotBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).ifBlank { fallback },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatMomentTime(timestamp: Long): String {
    if (timestamp <= 0L) return "刚刚"
    return SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) {
        this - value
    } else {
        this + value
    }
}

private const val MOMENT_COVER_IMAGE_SCOPE = "moments_cover"
private const val MOMENT_POST_IMAGE_SCOPE = "moments_post"
