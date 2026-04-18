package com.example.myapplication.ui.screen.phone

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.PhoneSnapshot

// 详情页内部导航状态
internal sealed interface PhoneDetailState {
    data class MessageThread(val threadId: String) : PhoneDetailState
    data class Note(val entryId: String) : PhoneDetailState
    data class Gallery(val entryId: String) : PhoneDetailState
    data class Shopping(val entryId: String) : PhoneDetailState
    data class Search(val entryId: String) : PhoneDetailState
    data class SocialPost(val postId: String) : PhoneDetailState
}

@Composable
internal fun PhoneDetailContent(
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
                        color = if (message.isOwner) PhoneAccentSoft() else PhoneCardBackground(),
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
                                color = PhoneMutedText(),
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
                        color = PhoneCardBackground(),
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .background(PhoneGalleryPlaceholderBrush()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = PhoneMutedText(),
                                    modifier = Modifier.size(54.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
                                entry.summary.takeIf { it.isNotBlank() }?.let { summary ->
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PhoneMutedText(),
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
                    CircularProgressIndicator(color = PhoneAccent())
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.phone_search_detail_loading), color = PhoneMutedText())
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

        is PhoneDetailState.SocialPost -> {
            val post = snapshot.socialPosts.firstOrNull { it.id == detailState.postId } ?: return
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = PhoneCardBackground(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PhoneAccentSoft()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = post.authorName.take(1),
                                        fontWeight = FontWeight.Bold,
                                        color = PhoneAccent(),
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = post.authorName,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        post.authorLabel.takeIf { it.isNotBlank() }?.let { authorLabel ->
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = PhoneAccentSoft(),
                                            ) {
                                                Text(
                                                    text = authorLabel,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PhoneAccent(),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = post.timeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PhoneMutedText(),
                                    )
                                }
                            }
                            Text(
                                text = post.content,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (post.likeCount > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (post.likeCount > 0) PhoneLikeAccent() else PhoneMutedText(),
                                    )
                                    Text(
                                        text = "${post.likeCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PhoneMutedText(),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.phone_comments_count, post.comments.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PhoneMutedText(),
                                )
                            }
                        }
                    }
                }
                if (post.comments.isNotEmpty()) {
                    items(post.comments, key = { it.id }) { comment ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PhoneCardBackground(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = comment.authorName,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(comment.text)
                            }
                        }
                    }
                }
            }
        }

        null -> Unit
    }
}

@Composable
internal fun PhoneLongContentCard(
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
                color = PhoneCardBackground(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
                    Text(content)
                }
            }
        }
    }
}
