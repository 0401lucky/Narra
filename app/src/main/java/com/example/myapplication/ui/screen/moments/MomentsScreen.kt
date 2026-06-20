package com.example.myapplication.ui.screen.moments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentMediaStatus
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft
import com.example.myapplication.ui.theme.MomentsBackground
import com.example.myapplication.ui.theme.MomentsCardBackground
import com.example.myapplication.ui.theme.MomentsLikeRed
import com.example.myapplication.ui.theme.MomentsMutedText
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
    onAddComment: (String, String, String) -> Unit,
    onRetryImage: (String) -> Unit,
    onRefreshMoments: () -> Unit,
    onUpdateCoverImage: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onOpenPhoneCheck: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var composerSubmitPending by remember { mutableStateOf(false) }
    var composerSawPublishing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    LaunchedEffect(showComposer, uiState.isPublishing, uiState.errorMessage) {
        if (!showComposer) {
            composerSubmitPending = false
            composerSawPublishing = false
            return@LaunchedEffect
        }
        if (composerSubmitPending && uiState.isPublishing) {
            composerSawPublishing = true
        }
        if (composerSubmitPending && composerSawPublishing && !uiState.isPublishing) {
            if (uiState.errorMessage == null) {
                showComposer = false
            }
            composerSubmitPending = false
            composerSawPublishing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showComposer) "发表朋友圈" else stringResource(R.string.moments_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    NarraIconButton(
                        onClick = {
                            when {
                                showComposer -> showComposer = false
                                selectedPostId != null -> selectedPostId = null
                                else -> onNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (!showComposer && selectedPostId == null) {
                        NarraIconButton(onClick = { showComposer = true }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "发表朋友圈")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MomentsBackground(),
                ),
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        containerColor = MomentsBackground(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MomentsAccent())
                    }
                }

                showComposer -> {
                    MomentComposerPage(
                        isPublishing = uiState.isPublishing,
                        masks = uiState.userPersonaMasks,
                        selectedMaskId = uiState.selectedUserPersonaMaskId,
                        onPublishPost = { content, imageUri, location, maskId ->
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
                                retryingImagePostId = uiState.retryingImagePostId,
                                onRefresh = onRefreshMoments,
                                onUpdateCoverImage = onUpdateCoverImage,
                                onOpenPost = { selectedPostId = it.id },
                                onToggleLike = onToggleLikePost,
                                onDeletePost = onDeletePost,
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
    retryingImagePostId: String,
    onRefresh: () -> Unit,
    onUpdateCoverImage: (String) -> Unit,
    onOpenPost: (MomentPost) -> Unit,
    onToggleLike: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onRetryImage: (String) -> Unit,
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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
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
                    EmptyMomentsPanel(onOpenPhoneCheck = onOpenPhoneCheck)
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    MomentPostTimelineItem(
                        post = post,
                        viewerName = viewerName,
                        isRetryingImage = retryingImagePostId == post.id,
                        onOpenPost = { onOpenPost(post) },
                        onToggleLike = { onToggleLike(post.id) },
                        onDeletePost = { onDeletePost(post.id) },
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onPickCover),
    ) {
        if (coverImageUri.isNotBlank()) {
            AsyncImage(
                model = coverImageUri,
                contentDescription = "朋友圈封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF88A79E),
                                Color(0xFF33413F),
                                Color(0xFF161C1B),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.58f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = viewerName.ifBlank { "我" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
            ) {
                MomentAvatar(
                    name = viewerName,
                    avatarUri = viewerAvatarUri,
                    size = 62.dp,
                    fallback = "我",
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.32f),
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.padding(9.dp).size(18.dp),
                tint = Color.White,
            )
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
    var draft by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedPersonaMaskId by remember(selectedMaskId, masks) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(MomentsCardBackground())
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            Button(
                onClick = {
                    onPublishPost(
                        draft.trim(),
                        imageUri,
                        location.trim(),
                        selectedPersonaMaskId,
                    )
                },
                enabled = draft.isNotBlank() && !isPublishing,
                shape = RoundedCornerShape(999.dp),
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isPublishing) "发表中" else "发表")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(300) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                    minLines = 6,
                    maxLines = 8,
                    placeholder = { Text("这一刻的想法…", color = MomentsMutedText()) },
                    shape = RoundedCornerShape(8.dp),
                )
            }

            item {
                ComposerImagePicker(
                    imageUri = imageUri,
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClearImage = { imageUri = "" },
                )
            }

            item {
                ComposerSectionRow(
                    icon = { Icon(Icons.Default.Place, contentDescription = null) },
                    title = "所在位置",
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("不显示位置", color = MomentsMutedText()) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }

            item {
                ComposerSectionRow(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = "选择身份",
                ) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMaskMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MomentsBackground(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedMask?.name.orEmpty().ifBlank { "当前身份" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MomentsMutedText(),
                                )
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
                                        text = { Text(mask.name.ifBlank { "未命名身份" }) },
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
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MomentsBackground())
            .clickable(onClick = onPickImage),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri.isBlank()) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "添加配图",
                modifier = Modifier.size(30.dp),
                tint = MomentsMutedText(),
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = "已选配图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.46f),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除配图",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onClearImage)
                        .padding(5.dp),
                    tint = Color.White,
                )
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
                modifier = Modifier.size(22.dp),
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
    onOpenPhoneCheck: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MomentsAccent(),
        )
        Text(
            text = "还没有朋友圈",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "发一条动态，角色会按各自人设来评论互动。",
            style = MaterialTheme.typography.bodyMedium,
            color = MomentsMutedText(),
        )
        if (onOpenPhoneCheck != null) {
            TextButton(onClick = onOpenPhoneCheck) {
                Text("去查手机")
            }
        }
    }
}

@Composable
private fun MomentPostTimelineItem(
    post: MomentPost,
    viewerName: String,
    isRetryingImage: Boolean,
    onOpenPost: () -> Unit,
    onToggleLike: () -> Unit,
    onDeletePost: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MomentsCardBackground())
            .clickable(onClick = onOpenPost)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MomentAvatar(
            name = post.authorName,
            avatarUri = post.authorAvatarUri,
            size = 44.dp,
            fallback = "朋",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = post.authorName.ifBlank { "朋友圈" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MomentsAccent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MomentMoreMenuButton(
                    expanded = showMoreMenu,
                    onExpandedChange = { showMoreMenu = it },
                    onDeleteClick = {
                        showMoreMenu = false
                        showDeleteDialog = true
                    },
                )
            }
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            MomentMediaContent(
                post = post,
                isRetryingImage = isRetryingImage,
                onRetryImage = onRetryImage,
            )
            if (post.location.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MomentsMutedText(),
                    )
                    Text(
                        text = post.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MomentsMutedText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMomentTime(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MomentsMutedText(),
                )
                MomentActionRow(
                    post = post,
                    viewerName = viewerName,
                    onToggleLike = onToggleLike,
                )
            }
            MomentEngagementBlock(post = post)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(MomentsBackground()),
    )
}

@Composable
private fun MomentMoreMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
) {
    Box {
        NarraIconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多操作",
                tint = MomentsMutedText(),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                    )
                },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条朋友圈？") },
        text = { Text("删除后，这条动态下的评论和配图也会一起移除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MomentMediaContent(
    post: MomentPost,
    isRetryingImage: Boolean,
    onRetryImage: () -> Unit,
) {
    val media = post.media ?: return
    when (media.status) {
        MomentMediaStatus.GENERATING -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MomentsAccentSoft(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MomentsAccent(),
                    )
                    Text(
                        text = "图片生成中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MomentsAccent(),
                    )
                }
            }
        }

        MomentMediaStatus.SUCCEEDED -> {
            if (media.imageUri.isNotBlank()) {
                AsyncImage(
                    model = media.imageUri,
                    contentDescription = media.fileName.ifBlank { "朋友圈配图" },
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MomentsAccentSoft()),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        MomentMediaStatus.FAILED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "配图生成失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MomentsMutedText(),
                )
                TextButton(
                    onClick = onRetryImage,
                    enabled = !isRetryingImage,
                ) {
                    Text(if (isRetryingImage) "重试中" else "重试配图")
                }
            }
        }
    }
}

@Composable
private fun MomentActionRow(
    post: MomentPost,
    viewerName: String,
    onToggleLike: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onToggleLike),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isLikedByViewer = viewerName in post.likedByNames
        Icon(
            imageVector = if (isLikedByViewer) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isLikedByViewer) MomentsLikeRed() else MomentsMutedText(),
        )
        if (post.likeCount > 0) {
            Text(
                text = "${post.likeCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MomentsMutedText(),
            )
        }
    }
}

@Composable
private fun MomentEngagementBlock(
    post: MomentPost,
) {
    if (post.likedByNames.isEmpty() && post.comments.isEmpty()) {
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MomentsBackground(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (post.likedByNames.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MomentsAccent(),
                    )
                    Text(
                        text = post.likedByNames.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MomentsAccent(),
                    )
                }
            }
            post.comments.take(4).forEach { comment ->
                Text(
                    text = "${comment.authorName}：${comment.text}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (post.comments.size > 4) {
                Text(
                    text = stringResource(R.string.moments_view_all_comments, post.comments.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MomentsAccent(),
                )
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
    onRetryImage: () -> Unit,
) {
    var commentInput by remember(post.id) { mutableStateOf("") }
    var replyTarget by remember(post.id) { mutableStateOf<MomentComment?>(null) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MomentsCardBackground(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        MomentMediaContent(
                            post = post,
                            isRetryingImage = isRetryingImage,
                            onRetryImage = onRetryImage,
                        )
                        if (post.location.isNotBlank()) {
                            Text(
                                text = post.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MomentsMutedText(),
                            )
                        }
                        MomentActionRow(
                            post = post,
                            viewerName = viewerName,
                            onToggleLike = onToggleLike,
                        )
                        MomentEngagementBlock(post)
                    }
                }
            }

            if (post.comments.isNotEmpty() || isGeneratingReplies) {
                item {
                    Text(
                        text = stringResource(R.string.moments_comments_title, post.comments.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            items(post.comments, key = { it.id }) { comment ->
                Surface(
                    modifier = Modifier.clickable { replyTarget = comment },
                    shape = RoundedCornerShape(8.dp),
                    color = MomentsCardBackground(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MomentAvatar(
                            name = comment.authorName,
                            avatarUri = comment.authorAvatarUri,
                            size = 32.dp,
                            fallback = "评",
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = comment.authorName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { replyTarget = comment }) {
                                    Text("回复")
                                }
                            }
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (isGeneratingReplies) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MomentsCardBackground(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MomentsAccent(),
                            )
                            Text(
                                text = stringResource(R.string.moments_replying_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MomentsMutedText(),
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.imePadding(),
            color = MomentsCardBackground(),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                replyTarget?.let { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "回复 ${target.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MomentsAccent(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { replyTarget = null }) {
                            Text("取消")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it.take(160) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = replyTarget?.let { "回复 ${it.authorName}" }
                                    ?: stringResource(R.string.moments_comment_placeholder),
                                color = MomentsMutedText(),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(999.dp),
                    )
                    NarraIconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                onAddComment(commentInput.trim(), replyTarget?.id.orEmpty())
                                commentInput = ""
                                replyTarget = null
                            }
                        },
                        enabled = commentInput.isNotBlank() && !isGeneratingReplies,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.moments_send_comment),
                            tint = if (commentInput.isNotBlank() && !isGeneratingReplies) MomentsAccent() else MomentsMutedText(),
                        )
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentAvatar(
            name = post.authorName,
            avatarUri = post.authorAvatarUri,
            size = 42.dp,
            fallback = "朋",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.authorName.ifBlank { "朋友圈" },
                fontWeight = FontWeight.SemiBold,
                color = MomentsAccent(),
                maxLines = 1,
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
                color = MomentsAccent(),
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

private const val MOMENT_COVER_IMAGE_SCOPE = "moments_cover"
private const val MOMENT_POST_IMAGE_SCOPE = "moments_post"
