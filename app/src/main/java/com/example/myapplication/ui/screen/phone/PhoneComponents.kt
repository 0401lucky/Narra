package com.example.myapplication.ui.screen.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.Smartphone
import com.example.myapplication.R
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi

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
        PhoneSnapshotSection.MESSAGES -> PhoneSectionList {
            if (snapshot.relationshipHighlights.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(NarraUi.cardRadius), color = PhoneCardBackground()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                        ) {
                            Text(
                                text = stringResource(R.string.phone_relationship_overview),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            snapshot.relationshipHighlights.forEach { relationship ->
                                Column(verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap)) {
                                    Text(
                                        text = relationship.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = listOf(relationship.relationLabel, relationship.stance)
                                            .filter(String::isNotBlank).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PhoneAccent(),
                                    )
                                    if (relationship.note.isNotBlank()) {
                                        Text(
                                            text = relationship.note,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PhoneMutedText(),
                                        )
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
        PhoneSnapshotSection.NOTES -> PhoneSectionList {
            items(snapshot.notes, key = { it.id }) { note ->
                PhoneListCard(
                    title = listOf(note.icon, note.title).filter(String::isNotBlank).joinToString(" "),
                    subtitle = note.summary,
                    meta = note.timeLabel,
                    leadingIcon = Lucide.NotebookPen,
                    onClick = { onOpenNote(note) },
                )
            }
        }
        PhoneSnapshotSection.GALLERY -> {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = (144f * fontScale).dp),
                modifier = Modifier.fillMaxSize().testTag("phone_list"),
                contentPadding = PaddingValues(NarraUi.pageVertical),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
            ) {
                items(snapshot.gallery, key = { it.id }) { entry ->
                    Surface(
                        modifier = Modifier.clickable(onClickLabel = "查看照片详情") { onOpenGallery(entry) },
                        shape = RoundedCornerShape(NarraUi.cardRadius),
                        color = PhoneCardBackground(),
                    ) {
                        Column {
                            PhoneGalleryPlaceholder(modifier = Modifier.fillMaxWidth().height(144.dp))
                            Column(
                                modifier = Modifier.padding(NarraUi.pageVertical),
                                verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PhoneMutedText(),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(entry.timeLabel, style = MaterialTheme.typography.labelSmall, color = PhoneMutedText())
                            }
                        }
                    }
                }
            }
        }
        PhoneSnapshotSection.SHOPPING -> PhoneSectionList {
            items(snapshot.shoppingRecords, key = { it.id }) { record ->
                PhoneListCard(
                    title = record.title,
                    subtitle = record.note,
                    meta = listOf(record.priceLabel, record.status).filter(String::isNotBlank).joinToString(" · "),
                    leadingIcon = Lucide.ShoppingBag,
                    onClick = { onOpenShopping(record) },
                )
            }
        }
        PhoneSnapshotSection.SEARCH -> PhoneSectionList {
            items(snapshot.searchHistory, key = { it.id }) { entry ->
                PhoneListCard(
                    title = entry.query,
                    subtitle = if (loadingSearchEntryId == entry.id) "正在生成详情…" else entry.detail?.summary.orEmpty(),
                    meta = entry.timeLabel,
                    leadingIcon = Lucide.Search,
                    onClick = { onOpenSearch(entry) },
                )
            }
        }
        PhoneSnapshotSection.SOCIAL_POSTS -> PhoneSectionList {
            items(snapshot.socialPosts, key = { it.id }) { post ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "查看动态详情") { onOpenSocialPost(post) },
                    shape = RoundedCornerShape(NarraUi.cardRadius),
                    color = PhoneCardBackground(),
                ) {
                    Column(
                        modifier = Modifier.padding(NarraUi.pageVertical),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
                    ) {
                        PhoneSocialHeader(post)
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PhoneSocialStats(post)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhoneSectionList(
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("phone_list"),
        contentPadding = PaddingValues(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.pageVertical),
        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        content = content,
    )
}

@Composable
internal fun PhoneGalleryPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
internal fun PhoneSocialHeader(post: PhoneSocialPost) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(PhoneAccentSoft()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = post.authorName.take(1).ifBlank { "友" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Text(
                text = post.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.authorLabel.isNotBlank()) {
                Text(post.authorLabel, style = MaterialTheme.typography.labelMedium, color = PhoneAccent())
            }
            Text(post.timeLabel, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneSocialStats(post: PhoneSocialPost) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Heart,
                contentDescription = "赞",
                modifier = Modifier.size(16.dp),
                tint = if (post.likeCount > 0) PhoneLikeAccent() else PhoneMutedText(),
            )
            Text(post.likeCount.toString(), style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
        }
        Text(
            text = stringResource(R.string.phone_comments_count, post.comments.size),
            style = MaterialTheme.typography.bodySmall,
            color = PhoneMutedText(),
        )
    }
}

@Composable
internal fun PhoneListCard(
    title: String,
    subtitle: String,
    meta: String,
    accent: String = "",
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "查看详情", onClick = onClick),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = PhoneCardBackground(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(NarraUi.smallRadius)).background(PhoneAccentSoft()),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(
                        text = title.take(1),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (accent.isNotBlank()) {
                    Text(accent, style = MaterialTheme.typography.labelMedium, color = PhoneAccent())
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneMutedText(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = PhoneMutedText())
                }
            }
            Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = PhoneMutedText())
        }
    }
}

@Composable
internal fun PhoneEmptyState(
    isGenerating: Boolean,
    generationStatusText: String,
    onGenerate: () -> Unit,
) {
    PhoneStatusPanel(
        title = if (isGenerating) "正在准备手机内容" else stringResource(R.string.phone_empty_title),
        description = if (isGenerating) {
            generationStatusText.ifBlank { "消息、备忘与生活片段正在生成。" }
        } else {
            "生成后，手机内容会保存在这里，随时都能回来看看。"
        },
        isLoading = isGenerating,
        actionLabel = if (isGenerating) "正在生成…" else stringResource(R.string.phone_generate_content),
        actionEnabled = !isGenerating,
        actionTag = "phone_generate",
        onAction = onGenerate,
    )
}

@Composable
internal fun PhoneStatusPanel(
    title: String,
    description: String,
    isLoading: Boolean = false,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    actionTag: String = "phone_state_action",
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = NarraUi.pageHorizontal, vertical = 32.dp)
            .testTag("phone_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical, Alignment.CenterVertically),
    ) {
        Surface(shape = RoundedCornerShape(NarraUi.cardRadius), color = PhoneAccentSoft()) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Lucide.Smartphone, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneMutedText(),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag(actionTag),
                shape = RoundedCornerShape(NarraUi.smallRadius),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun RefreshSectionsDialog(
    selectedSections: Set<PhoneSnapshotSection>,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onToggleSection: (PhoneSnapshotSection) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
) {
    NarraAlertDialog(
        title = stringResource(R.string.phone_refresh_dialog_title),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = stringResource(R.string.phone_refresh_confirm),
        confirmEnabled = selectedSections.isNotEmpty() && !isGenerating,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap)) {
                Text(
                    text = stringResource(R.string.phone_refresh_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneMutedText(),
                )
                TextButton(
                    onClick = onSelectAll,
                    enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                ) {
                    Text(stringResource(R.string.phone_select_all))
                }
                PhoneSnapshotSection.entries.forEach { section ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = NarraMinimumTouchTarget)
                            .toggleable(
                                value = section in selectedSections,
                                enabled = !isGenerating,
                                role = Role.Checkbox,
                                onValueChange = { onToggleSection(section) },
                            )
                            .testTag("phone_refresh_" + section.storageValue),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    ) {
                        Checkbox(checked = section in selectedSections, onCheckedChange = null, enabled = !isGenerating)
                        Icon(section.icon(), null, Modifier.size(20.dp), tint = PhoneMutedText())
                        Text(section.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}
