package com.example.myapplication.ui.screen.moments

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft
import com.example.myapplication.ui.theme.MomentsBackground
import com.example.myapplication.ui.theme.MomentsCardBackground
import com.example.myapplication.ui.theme.MomentsLikeRed
import com.example.myapplication.ui.theme.MomentsMutedText
import com.example.myapplication.viewmodel.MomentsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    uiState: MomentsUiState,
    viewerName: String,
    onNavigateBack: () -> Unit,
    onPublishPost: (String) -> Unit,
    onToggleLikePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onAddComment: (String, String, String) -> Unit,
    onRetryImage: (String) -> Unit,
    onGenerateDueAssistantPosts: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onOpenPhoneCheck: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moments_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NarraIconButton(onClick = {
                        if (selectedPostId != null) {
                            selectedPostId = null
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (selectedPostId == null) {
                        NarraIconButton(onClick = onGenerateDueAssistantPosts) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新朋友圈")
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
                                isPublishing = uiState.isPublishing,
                                retryingImagePostId = uiState.retryingImagePostId,
                                onPublishPost = onPublishPost,
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

@Composable
private fun MomentsTimelineContent(
    posts: List<MomentPost>,
    viewerName: String,
    isPublishing: Boolean,
    retryingImagePostId: String,
    onPublishPost: (String) -> Unit,
    onOpenPost: (MomentPost) -> Unit,
    onToggleLike: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onOpenPhoneCheck: (() -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MomentComposer(
                isPublishing = isPublishing,
                onPublishPost = onPublishPost,
            )
        }
        if (posts.isEmpty()) {
            item {
                EmptyMomentsPanel(onOpenPhoneCheck = onOpenPhoneCheck)
            }
        } else {
            items(posts, key = { it.id }) { post ->
                MomentPostCard(
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

@Composable
private fun MomentComposer(
    isPublishing: Boolean,
    onPublishPost: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MomentsCardBackground(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "发一条朋友圈",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                placeholder = { Text("这一刻想说什么？", color = MomentsMutedText()) },
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val content = draft.trim()
                        if (content.isNotBlank()) {
                            onPublishPost(content)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !isPublishing,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (isPublishing) "发布中" else "发布")
                }
            }
        }
    }
}

@Composable
private fun EmptyMomentsPanel(
    onOpenPhoneCheck: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MomentsCardBackground(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
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
                text = "你可以先发一条，角色会按各自人设来评论互动。",
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
}

@Composable
private fun MomentPostCard(
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPost),
        shape = RoundedCornerShape(18.dp),
        color = MomentsCardBackground(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            MomentMediaContent(
                post = post,
                isRetryingImage = isRetryingImage,
                onRetryImage = onRetryImage,
            )
            if (post.comments.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MomentsBackground(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        post.comments.takeLast(2).forEach { comment ->
                            Text(
                                text = "${comment.authorName}：${comment.text}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (post.comments.size > 2) {
                            Text(
                                text = stringResource(R.string.moments_view_all_comments, post.comments.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MomentsAccent(),
                            )
                        }
                    }
                }
            }
            MomentActionRow(
                post = post,
                viewerName = viewerName,
                onToggleLike = onToggleLike,
            )
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = post.authorName.ifBlank { "朋友圈" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (post.authorLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MomentsAccentSoft(),
                    ) {
                        Text(
                            text = post.authorLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MomentsAccent(),
                        )
                    }
                }
            }
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
                shape = RoundedCornerShape(14.dp),
                color = MomentsAccentSoft(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(14.dp))
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        if (post.comments.isNotEmpty()) {
            Text(
                text = stringResource(R.string.moments_comments_count, post.comments.size),
                style = MaterialTheme.typography.bodySmall,
                color = MomentsMutedText(),
            )
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MomentsCardBackground(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        MomentActionRow(
                            post = post,
                            viewerName = viewerName,
                            onToggleLike = onToggleLike,
                        )
                        if (post.likedByNames.isNotEmpty()) {
                            Text(
                                text = post.likedByNames.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MomentsMutedText(),
                            )
                        }
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
                    shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
private fun MomentAvatar(
    name: String,
    avatarUri: String,
    size: androidx.compose.ui.unit.Dp,
    fallback: String,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
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
