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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.ui.theme.MomentsAccent
import com.example.myapplication.ui.theme.MomentsAccentSoft
import com.example.myapplication.ui.theme.MomentsBackground
import com.example.myapplication.ui.theme.MomentsCardBackground
import com.example.myapplication.ui.theme.MomentsLikeRed
import com.example.myapplication.ui.theme.MomentsMutedText
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.viewmodel.MomentsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    uiState: MomentsUiState,
    viewerName: String,
    onNavigateBack: () -> Unit,
    onToggleLikePost: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
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

                uiState.posts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.moments_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MomentsMutedText(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.moments_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MomentsMutedText(),
                        )
                        if (onOpenPhoneCheck != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onOpenPhoneCheck) {
                                Text("去查手机")
                            }
                        }
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
                            MomentsListContent(
                                posts = uiState.posts,
                                viewerName = viewerName,
                                onOpenPost = { selectedPostId = it.id },
                                onToggleLike = onToggleLikePost,
                            )
                        } else {
                            MomentsDetailContent(
                                post = post,
                                viewerName = viewerName,
                                isGeneratingReplies = uiState.isGeneratingReplies && uiState.replyingPostId == post.id,
                                onToggleLike = { onToggleLikePost(post.id) },
                                onAddComment = { text -> onAddComment(post.id, text) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentsListContent(
    posts: List<PhoneSocialPost>,
    viewerName: String,
    onOpenPost: (PhoneSocialPost) -> Unit,
    onToggleLike: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(posts, key = { it.id }) { post ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPost(post) },
                shape = RoundedCornerShape(22.dp),
                color = MomentsCardBackground(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 作者行
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MomentsAccentSoft()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = post.authorName.take(1),
                                color = MomentsAccent(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = post.authorName,
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
                                text = post.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MomentsMutedText(),
                            )
                        }
                    }
                    // 正文
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 评论预览
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
                                        text = stringResource(
                                            R.string.moments_preview_comment,
                                            comment.authorName,
                                            comment.text,
                                        ),
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
                    // 底栏
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.clickable { onToggleLike(post.id) },
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
            }
        }
    }
}

@Composable
private fun MomentsDetailContent(
    post: PhoneSocialPost,
    viewerName: String,
    isGeneratingReplies: Boolean,
    onToggleLike: () -> Unit,
    onAddComment: (String) -> Unit,
) {
    var commentInput by remember(post.id) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 正文卡片
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MomentsCardBackground(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MomentsAccentSoft()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = post.authorName.take(1),
                                    color = MomentsAccent(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = post.authorName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (post.authorLabel.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MomentsAccentSoft(),
                                        ) {
                                            Text(
                                                text = post.authorLabel,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MomentsAccent(),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = post.timeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MomentsMutedText(),
                                )
                            }
                        }
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // 点赞区
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MomentsCardBackground(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (post.likeCount > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (post.likeCount > 0) MomentsLikeRed() else MomentsMutedText(),
                                )
                                Text(
                                    text = stringResource(R.string.moments_likes_count, post.likeCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MomentsMutedText(),
                                )
                            }
                            Surface(
                                modifier = Modifier.clickable(onClick = onToggleLike),
                                shape = RoundedCornerShape(999.dp),
                                color = MomentsAccentSoft(),
                            ) {
                                Text(
                                    text = if (viewerName in post.likedByNames) {
                                        stringResource(R.string.moments_unlike_action)
                                    } else {
                                        stringResource(R.string.moments_like_action)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MomentsAccent(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
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

            // 评论标题
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

            // 评论列表
            items(post.comments, key = { it.id }) { comment ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MomentsCardBackground(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MomentsAccentSoft()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = comment.authorName.take(1),
                                color = MomentsAccent(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // AI 正在回复提示
            if (isGeneratingReplies) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
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

        // 评论输入栏
        Surface(
            modifier = Modifier.imePadding(),
            color = MomentsCardBackground(),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.moments_comment_placeholder), color = MomentsMutedText()) },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                )
                NarraIconButton(
                    onClick = {
                        if (commentInput.isNotBlank()) {
                            onAddComment(commentInput.trim())
                            commentInput = ""
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
