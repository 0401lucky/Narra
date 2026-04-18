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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraTextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneSnapshotTabsContent(
    snapshot: PhoneSnapshot,
    selectedTab: PhoneSnapshotSection,
    loadingSearchEntryId: String,
    onOpenMessageThread: (PhoneMessageThread) -> Unit,
    onOpenNote: (PhoneNoteEntry) -> Unit,
    onOpenGallery: (PhoneGalleryEntry) -> Unit,
    onOpenShopping: (PhoneShoppingEntry) -> Unit,
    onOpenSearch: (PhoneSearchEntry) -> Unit,
    onOpenSocialPost: (PhoneSocialPost) -> Unit,
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
                        color = PhoneCardBackground(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.phone_relationship_overview),
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
                                        color = PhoneAccentSoft(),
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${item.relationLabel} · ${item.stance}".trim().trim('.'),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PhoneMutedText(),
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
                    color = PhoneCardBackground(),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(PhoneGalleryPlaceholderBrush()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = PhoneMutedText(),
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
                                color = PhoneMutedText(),
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
                    color = PhoneCardBackground(),
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
                                .background(PhoneAccentSoft(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingSearchEntryId == entry.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PhoneAccent(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = PhoneAccent(),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.query, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = entry.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = PhoneMutedText(),
                            )
                        }
                    }
                }
            }
        }

        PhoneSnapshotSection.SOCIAL_POSTS -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(snapshot.socialPosts, key = { it.id }) { post ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSocialPost(post) },
                    shape = RoundedCornerShape(22.dp),
                    color = PhoneCardBackground(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PhoneAccentSoft()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = post.authorName.take(1),
                                    color = PhoneAccent(),
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
                                            color = PhoneAccentSoft(),
                                        ) {
                                            Text(
                                                text = post.authorLabel,
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
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
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
                                if (post.likeCount > 0) {
                                    Text(
                                        text = "${post.likeCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PhoneMutedText(),
                                    )
                                }
                            }
                            if (post.comments.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.phone_comments_count, post.comments.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PhoneMutedText(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneListCard(
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
        color = PhoneCardBackground(),
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
                    .background(PhoneAccentSoft()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1),
                    color = PhoneAccent(),
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
                            color = PhoneAccentSoft(),
                        ) {
                            Text(
                                text = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PhoneAccent(),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneMutedText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = PhoneMutedText(),
            )
        }
    }
}

@Composable
internal fun PhoneEmptyState(
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
                .background(PhoneAccentSoft(), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PhoneAccent(),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.phone_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.phone_empty_body),
            color = PhoneMutedText(),
            textAlign = TextAlign.Center,
        )
        if (generationStatusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = generationStatusText,
                color = PhoneMutedText(),
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
                containerColor = PhoneAccent(),
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
                Text(stringResource(R.string.phone_generate_content), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun RefreshSectionsDialog(
    selectedSections: Set<PhoneSnapshotSection>,
    onDismiss: () -> Unit,
    onToggleSection: (PhoneSnapshotSection) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.phone_refresh_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NarraTextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.phone_select_all))
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
                    text = stringResource(R.string.phone_refresh_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = PhoneMutedText(),
                )
            }
        },
        confirmButton = {
            NarraTextButton(
                onClick = onConfirm,
                enabled = selectedSections.isNotEmpty(),
            ) {
                Text(stringResource(R.string.phone_refresh_confirm))
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
