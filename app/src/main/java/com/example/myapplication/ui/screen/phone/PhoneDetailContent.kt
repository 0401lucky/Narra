package com.example.myapplication.ui.screen.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.ui.theme.NarraUi

internal sealed interface PhoneDetailState {
    data class MessageThread(val threadId: String) : PhoneDetailState
    data class Note(val entryId: String) : PhoneDetailState
    data class Gallery(val entryId: String) : PhoneDetailState
    data class Shopping(val entryId: String) : PhoneDetailState
    data class Search(val entryId: String) : PhoneDetailState
    data class SocialPost(val postId: String) : PhoneDetailState
}

internal fun PhoneDetailState.isAvailableIn(snapshot: PhoneSnapshot): Boolean = when (this) {
    is PhoneDetailState.MessageThread -> snapshot.messageThreads.any { it.id == threadId }
    is PhoneDetailState.Note -> snapshot.notes.any { it.id == entryId }
    is PhoneDetailState.Gallery -> snapshot.gallery.any { it.id == entryId }
    is PhoneDetailState.Shopping -> snapshot.shoppingRecords.any { it.id == entryId }
    is PhoneDetailState.Search -> snapshot.searchHistory.any { it.id == entryId }
    is PhoneDetailState.SocialPost -> snapshot.socialPosts.any { it.id == postId }
}

@Composable
internal fun PhoneDetailContent(
    snapshot: PhoneSnapshot,
    detailState: PhoneDetailState?,
    loadingSearchEntryId: String,
    onRetrySearchDetail: (String) -> Unit,
) {
    when (detailState) {
        is PhoneDetailState.MessageThread -> {
            val thread = snapshot.messageThreads.firstOrNull { it.id == detailState.threadId } ?: return
            PhoneSectionList {
                item {
                    Text(
                        text = thread.contactName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(thread.messages, key = { it.id }) { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(NarraUi.cardRadius),
                        color = if (message.isOwner) PhoneAccentSoft() else PhoneCardBackground(),
                        contentColor = if (message.isOwner) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(NarraUi.pageVertical),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                        ) {
                            Text(
                                text = message.senderName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                            Text(message.timeLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (thread.messages.isEmpty()) {
                    item {
                        Text("这段会话还没有消息内容。", color = PhoneMutedText())
                    }
                }
            }
        }
        is PhoneDetailState.Note -> {
            val entry = snapshot.notes.firstOrNull { it.id == detailState.entryId } ?: return
            PhoneLongContentCard(title = entry.title, meta = entry.timeLabel, content = entry.content)
        }
        is PhoneDetailState.Gallery -> {
            val entry = snapshot.gallery.firstOrNull { it.id == detailState.entryId } ?: return
            PhoneSectionList {
                item {
                    Surface(shape = RoundedCornerShape(NarraUi.cardRadius), color = PhoneCardBackground()) {
                        Column {
                            PhoneGalleryPlaceholder(Modifier.fillMaxWidth().height(192.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                                verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
                                if (entry.summary.isNotBlank()) {
                                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium, color = PhoneMutedText())
                                }
                                Text(entry.description, style = MaterialTheme.typography.bodyLarge)
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
                meta = listOf(entry.priceLabel, entry.status, entry.timeLabel).filter(String::isNotBlank).joinToString(" · "),
                content = listOf(entry.note, entry.detail).filter(String::isNotBlank).joinToString("\n\n"),
            )
        }
        is PhoneDetailState.Search -> {
            val entry = snapshot.searchHistory.firstOrNull { it.id == detailState.entryId } ?: return
            when {
                entry.detail != null -> PhoneLongContentCard(
                    title = entry.detail.title,
                    meta = entry.timeLabel,
                    content = listOf(entry.detail.summary, entry.detail.content)
                        .filter(String::isNotBlank).joinToString("\n\n"),
                )
                loadingSearchEntryId == entry.id -> PhoneStatusPanel(
                    title = "正在生成搜索详情",
                    description = entry.query,
                    isLoading = true,
                )
                else -> PhoneStatusPanel(
                    title = "搜索详情尚未加载",
                    description = "可以再次尝试生成，搜索记录会保留。",
                    actionLabel = "重试生成详情",
                    actionEnabled = loadingSearchEntryId.isBlank(),
                    onAction = { onRetrySearchDetail(entry.id) },
                )
            }
        }
        is PhoneDetailState.SocialPost -> {
            val post = snapshot.socialPosts.firstOrNull { it.id == detailState.postId } ?: return
            PhoneSectionList {
                item {
                    Surface(shape = RoundedCornerShape(NarraUi.cardRadius), color = PhoneCardBackground()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                        ) {
                            PhoneSocialHeader(post)
                            Text(post.content, style = MaterialTheme.typography.bodyLarge)
                            PhoneSocialStats(post)
                        }
                    }
                }
                items(post.comments, key = { it.id }) { comment ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(NarraUi.cardRadius),
                        color = PhoneCardBackground(),
                    ) {
                        Column(
                            modifier = Modifier.padding(NarraUi.pageVertical),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                        ) {
                            Text(comment.authorName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(comment.text, style = MaterialTheme.typography.bodyLarge)
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
    PhoneSectionList {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("phone_detail_card"),
                shape = RoundedCornerShape(NarraUi.cardRadius),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(NarraUi.pageVertical),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
                    Text(content, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
