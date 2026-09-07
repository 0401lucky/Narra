package com.example.myapplication.ui.screen.roleplay.mailbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.MailboxBox
import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MailboxProactiveFrequency
import com.example.myapplication.model.MailboxSenderType
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.MailboxScreenMode
import com.example.myapplication.viewmodel.MailboxUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MailboxCallbacks(
    val onSelectBox: (MailboxBox) -> Unit,
    val onSelectLetter: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSelectTag: (String) -> Unit,
    val onUnreadOnlyChange: (Boolean) -> Unit,
    val onClearFilters: () -> Unit,
    val onBackToList: () -> Unit,
    val onStartCompose: (String) -> Unit,
    val onEditDraft: (String) -> Unit,
    val onSubjectChange: (String) -> Unit,
    val onContentChange: (String) -> Unit,
    val onIncludeRecentChatChange: (Boolean) -> Unit,
    val onIncludePhoneCluesChange: (Boolean) -> Unit,
    val onAllowMemoryChange: (Boolean) -> Unit,
    val onGenerateReplyAfterSendChange: (Boolean) -> Unit,
    val onOpenSettings: () -> Unit,
    val onCloseSettings: () -> Unit,
    val onAutoReplySettingChange: (Boolean) -> Unit,
    val onDefaultIncludeRecentChatSettingChange: (Boolean) -> Unit,
    val onDefaultIncludePhoneCluesSettingChange: (Boolean) -> Unit,
    val onDefaultAllowMemorySettingChange: (Boolean) -> Unit,
    val onProactiveFrequencyChange: (MailboxProactiveFrequency) -> Unit,
    val onRequestProactiveLetterNow: () -> Unit,
    val onCancelGeneration: () -> Unit,
    val onSaveDraft: () -> Unit,
    val onSend: () -> Unit,
    val onArchive: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onGenerateReply: (String) -> Unit,
    val onCreateMemoryCandidate: (String) -> Unit,
    val onApproveMemory: () -> Unit,
    val onRejectMemory: () -> Unit,
    val onQuoteLetter: (String) -> Unit,
    val onOpenDiary: () -> Unit,
    val onOpenPhoneCheck: () -> Unit,
    val onClearNotice: () -> Unit,
    val onClearError: () -> Unit,
)

private class MailboxColors(scheme: ColorScheme) {
    val background = scheme.background
    val surface = scheme.surfaceContainerLow
    val paper = scheme.surface
    val title = scheme.onSurface
    val body = scheme.onSurface
    val muted = scheme.onSurfaceVariant
    val accent = scheme.primary
    val onAccent = scheme.onPrimary
    val accentSoft = scheme.primaryContainer
    val unread = scheme.error
    val unreadSoft = scheme.errorContainer
    val memory = scheme.tertiary
    val memorySoft = scheme.tertiaryContainer
    val sent = scheme.secondary
    val sentSoft = scheme.secondaryContainer
    val border = scheme.outlineVariant
}

@Composable
fun MailboxScreen(
    uiState: MailboxUiState,
    callbacks: MailboxCallbacks,
    onNavigateBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) { MailboxColors(scheme) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            callbacks.onClearNotice()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            callbacks.onClearError()
        }
    }

    if (uiState.pendingMemoryProposal != null) {
        AlertDialog(
            onDismissRequest = callbacks.onRejectMemory,
            shape = RoundedCornerShape(NarraUi.cardRadius),
            title = { Text("建议记住这封信") },
            text = {
                Text(
                    text = uiState.pendingMemoryProposal.content,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = callbacks.onApproveMemory) { Text("记住") }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onRejectMemory) { Text("取消") }
            },
        )
    }
    if (uiState.showSettings) {
        MailboxSettingsDialog(
            uiState = uiState,
            colors = colors,
            callbacks = callbacks,
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.activeMode == MailboxScreenMode.LIST && !uiState.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { callbacks.onStartCompose("") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("写信") },
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    shape = RoundedCornerShape(NarraUi.cardRadius),
                )
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            when {
                uiState.isLoading -> MailboxLoading(colors)
                uiState.activeMode == MailboxScreenMode.COMPOSE -> MailboxComposer(
                    uiState = uiState,
                    colors = colors,
                    callbacks = callbacks,
                    onBack = callbacks.onBackToList,
                )
                uiState.activeMode == MailboxScreenMode.DETAIL -> MailboxDetail(
                    uiState = uiState,
                    colors = colors,
                    callbacks = callbacks,
                    onBack = callbacks.onBackToList,
                )
                else -> MailboxList(
                    uiState = uiState,
                    colors = colors,
                    callbacks = callbacks,
                    onNavigateBack = onNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun MailboxLoading(colors: MailboxColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colors.accent)
    }
}

@Composable
private fun MailboxList(
    uiState: MailboxUiState,
    colors: MailboxColors,
    callbacks: MailboxCallbacks,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MailboxTopBar(
            title = "信箱",
            subtitle = "与 ${uiState.characterName} 的书信往来",
            colors = colors,
            onBack = onNavigateBack,
            trailingIcon = Icons.Default.MoreHoriz,
            onTrailingClick = callbacks.onOpenSettings,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MailboxHeroCard(
                    uiState = uiState,
                    colors = colors,
                    onCancelGeneration = callbacks.onCancelGeneration,
                )
            }
            item {
                MailboxBridgeCard(
                    uiState = uiState,
                    colors = colors,
                    onOpenDiary = callbacks.onOpenDiary,
                    onOpenPhoneCheck = callbacks.onOpenPhoneCheck,
                )
            }
            item {
                MailboxTabs(
                    selectedBox = uiState.selectedBox,
                    unreadCount = uiState.unreadCount,
                    colors = colors,
                    onSelect = callbacks.onSelectBox,
                )
            }
            item {
                MailboxFilterPanel(
                    uiState = uiState,
                    colors = colors,
                    callbacks = callbacks,
                )
            }
            if (uiState.letters.isEmpty()) {
                item { MailboxEmptyState(uiState.selectedBox, colors) }
            } else if (uiState.visibleLetters.isEmpty()) {
                item { MailboxNoMatchesState(colors) }
            } else {
                items(uiState.visibleLetters, key = { it.id }) { letter ->
                    MailboxLetterCard(
                        letter = letter,
                        colors = colors,
                        characterName = uiState.characterName,
                        userName = uiState.userName,
                        onClick = {
                            if (letter.box == MailboxBox.DRAFT) {
                                callbacks.onEditDraft(letter.id)
                            } else {
                                callbacks.onSelectLetter(letter.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MailboxBridgeCard(
    uiState: MailboxUiState,
    colors: MailboxColors,
    onOpenDiary: () -> Unit,
    onOpenPhoneCheck: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MailboxLinkPill(
                    label = if (uiState.diaryEntryCount > 0) "日记 ${uiState.diaryEntryCount}" else "日记",
                    icon = Icons.Default.Book,
                    color = colors.sent,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDiary,
                )
                MailboxLinkPill(
                    label = if (uiState.phoneClueCount > 0) "手机 ${uiState.phoneClueCount}" else "查手机",
                    icon = Icons.Default.PhoneAndroid,
                    color = colors.accent,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPhoneCheck,
                )
            }
            val diaryText = uiState.latestDiaryTitle.takeIf { it.isNotBlank() }?.let { "日记：$it" }
            val phoneText = uiState.phoneClueSummary.takeIf { it.isNotBlank() }?.let { "手机：$it" }
            val summary = listOfNotNull(diaryText, phoneText).joinToString(" · ")
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    color = colors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MailboxLinkPill(
    label: String,
    icon: ImageVector,
    color: Color,
    colors: MailboxColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = NarraMinimumTouchTarget)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        color = color.copy(alpha = 0.13f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = colors.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailboxFilterPanel(
    uiState: MailboxUiState,
    colors: MailboxColors,
    callbacks: MailboxCallbacks,
) {
    val hasFilters = uiState.searchQuery.isNotBlank() || uiState.selectedTag.isNotBlank() || uiState.unreadOnly
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = callbacks.onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = colors.muted)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { callbacks.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清空搜索", tint = colors.muted)
                        }
                    }
                },
                placeholder = { Text("搜索信件") },
                singleLine = true,
                shape = RoundedCornerShape(NarraUi.smallRadius),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (uiState.selectedBox == MailboxBox.INBOX) {
                    MailboxSelectableChip(
                        label = "未读",
                        selected = uiState.unreadOnly,
                        colors = colors,
                        selectedColor = colors.unread,
                        onClick = { callbacks.onUnreadOnlyChange(!uiState.unreadOnly) },
                    )
                }
                uiState.availableTags.forEach { tag ->
                    MailboxSelectableChip(
                        label = tag,
                        selected = uiState.selectedTag == tag,
                        colors = colors,
                        selectedColor = colors.accent,
                        onClick = { callbacks.onSelectTag(tag) },
                    )
                }
                if (hasFilters) {
                    MailboxSelectableChip(
                        label = "清除",
                        selected = false,
                        colors = colors,
                        selectedColor = colors.title,
                        onClick = callbacks.onClearFilters,
                    )
                }
            }
        }
    }
}

@Composable
private fun MailboxSelectableChip(
    label: String,
    selected: Boolean,
    colors: MailboxColors,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColor.copy(alpha = 0.14f),
            selectedLabelColor = selectedColor,
            labelColor = colors.title,
        ),
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailboxSettingsDialog(
    uiState: MailboxUiState,
    colors: MailboxColors,
    callbacks: MailboxCallbacks,
) {
    AlertDialog(
        onDismissRequest = callbacks.onCloseSettings,
        title = { Text("信箱设置") },
        shape = RoundedCornerShape(NarraUi.cardRadius),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "主动来信",
                        color = colors.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MailboxProactiveFrequency.entries.forEach { frequency ->
                            MailboxFrequencyChip(
                                frequency = frequency,
                                selected = uiState.settings.proactiveFrequency == frequency,
                                colors = colors,
                                onClick = { callbacks.onProactiveFrequencyChange(frequency) },
                            )
                        }
                    }
                }
                MailboxSwitchRow(
                    label = "寄信后自动回信",
                    checked = uiState.settings.autoReplyToUserLetters,
                    onCheckedChange = callbacks.onAutoReplySettingChange,
                    colors = colors,
                )
                MailboxSwitchRow(
                    label = "默认带入最近会话",
                    checked = uiState.settings.includeRecentChatByDefault,
                    onCheckedChange = callbacks.onDefaultIncludeRecentChatSettingChange,
                    colors = colors,
                )
                MailboxSwitchRow(
                    label = "默认带入手机线索",
                    checked = uiState.settings.includePhoneCluesByDefault,
                    onCheckedChange = callbacks.onDefaultIncludePhoneCluesSettingChange,
                    colors = colors,
                )
                MailboxSwitchRow(
                    label = "默认允许沉淀记忆",
                    checked = uiState.settings.allowMemoryByDefault,
                    onCheckedChange = callbacks.onDefaultAllowMemorySettingChange,
                    colors = colors,
                )
                Button(
                    onClick = {
                        if (uiState.isGeneratingProactiveLetter || uiState.isGeneratingReply) {
                            callbacks.onCancelGeneration()
                        } else {
                            callbacks.onRequestProactiveLetterNow()
                        }
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget),
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGeneratingProactiveLetter || uiState.isGeneratingReply) {
                            colors.unread
                        } else {
                            colors.accent
                        },
                        contentColor = if (uiState.isGeneratingProactiveLetter || uiState.isGeneratingReply) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            colors.onAccent
                        },
                    ),
                ) {
                    if (uiState.isGeneratingProactiveLetter || uiState.isGeneratingReply) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isGeneratingProactiveLetter) "停止写信" else "停止回信")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("现在写一封")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = callbacks.onCloseSettings) { Text("完成") }
        },
    )
}

@Composable
private fun MailboxFrequencyChip(
    frequency: MailboxProactiveFrequency,
    selected: Boolean,
    colors: MailboxColors,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        label = {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = frequency.label,
                    color = colors.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = frequency.description,
                    color = colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        },
    )
}

@Composable
private fun MailboxTopBar(
    title: String,
    subtitle: String,
    colors: MailboxColors,
    onBack: () -> Unit,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(NarraMinimumTouchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.title)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.title,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(onClick = onTrailingClick, modifier = Modifier.size(NarraMinimumTouchTarget)) {
                Icon(trailingIcon, contentDescription = "信箱设置", tint = colors.title)
            }
        }
    }
}

@Composable
private fun MailboxHeroCard(
    uiState: MailboxUiState,
    colors: MailboxColors,
    onCancelGeneration: () -> Unit,
) {
    val latest = uiState.letters.firstOrNull()
    val isGenerating = uiState.isGeneratingProactiveLetter || uiState.isGeneratingReply
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MailboxAvatar(
                label = uiState.characterName,
                colors = colors,
                tint = colors.accent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        uiState.isGeneratingProactiveLetter -> "${uiState.characterName} 正在写信"
                        uiState.isGeneratingReply -> "${uiState.characterName} 正在回信"
                        uiState.unreadCount > 0 -> "${uiState.unreadCount} 封未读来信"
                        else -> "信箱很安静"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.title,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (uiState.isGeneratingProactiveLetter) {
                        "纸页翻动了一下，新来信快到了。"
                    } else {
                        latest?.excerpt?.ifBlank { latest.subject }
                            ?: "慢一点写，重要的话会在这里沉下来。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isGenerating) {
                MailboxActionButton(
                    label = "停止",
                    icon = Icons.Default.Close,
                    tint = colors.unread,
                    colors = colors,
                    onClick = onCancelGeneration,
                )
            }
        }
    }
}

@Composable
private fun MailboxTabs(
    selectedBox: MailboxBox,
    unreadCount: Int,
    colors: MailboxColors,
    onSelect: (MailboxBox) -> Unit,
) {
    val boxes = listOf(MailboxBox.INBOX, MailboxBox.DRAFT, MailboxBox.SENT, MailboxBox.ARCHIVE)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            boxes.forEach { box ->
                val selected = selectedBox == box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = NarraMinimumTouchTarget)
                        .clickable { onSelect(box) },
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                    color = if (selected) colors.accentSoft else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (box == MailboxBox.INBOX && unreadCount > 0) {
                                "${box.label} $unreadCount"
                            } else {
                                box.label
                            },
                            color = if (selected) colors.accent else colors.body,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailboxLetterCard(
    letter: MailboxLetter,
    colors: MailboxColors,
    characterName: String,
    userName: String,
    onClick: () -> Unit,
) {
    val unread = !letter.isRead && letter.box == MailboxBox.INBOX
    val cardColor = animateColorAsState(
        targetValue = if (unread) colors.accentSoft.copy(alpha = 0.38f) else colors.surface,
        animationSpec = tween(durationMillis = 260),
        label = "mailboxCardColor",
    )
    val borderColor = animateColorAsState(
        targetValue = if (unread) colors.accent.copy(alpha = 0.30f) else colors.border,
        animationSpec = tween(durationMillis = 260),
        label = "mailboxBorderColor",
    )
    val tint = when {
        letter.box == MailboxBox.SENT -> colors.sent
        letter.box == MailboxBox.DRAFT -> colors.memory
        letter.isRead -> colors.accent
        else -> colors.unread
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = cardColor.value,
        border = BorderStroke(1.dp, borderColor.value),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MailboxAvatar(
                    label = if (letter.senderType == MailboxSenderType.USER) userName else characterName,
                    colors = colors,
                    tint = tint,
                    small = true,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = letter.subject.ifBlank { "未命名的信" },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${senderLabel(letter, characterName, userName)} · ${formatLetterTime(letter.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                        maxLines = 1,
                    )
                }
                if (unread) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = colors.unread,
                    ) {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Text(
                text = letter.excerpt.ifBlank { letter.content },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val chips = buildList {
                    if (!letter.isRead && letter.box == MailboxBox.INBOX) add("未读" to colors.unreadSoft)
                    addAll(letter.tags.map { it to colors.accentSoft })
                    if (letter.allowMemory) add("可记忆" to colors.memorySoft)
                    if (letter.box == MailboxBox.DRAFT) add("草稿" to colors.memorySoft)
                }
                chips.take(4).forEach { (label, color) ->
                    MailboxChip(label = label, color = color, textColor = colors.title)
                }
            }
        }
    }
}

@Composable
private fun MailboxDetail(
    uiState: MailboxUiState,
    colors: MailboxColors,
    callbacks: MailboxCallbacks,
    onBack: () -> Unit,
) {
    val letter = uiState.selectedLetter
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MailboxTopBar(
            title = when (letter?.box) {
                MailboxBox.SENT -> "已寄信件"
                MailboxBox.DRAFT -> "草稿"
                else -> "来信详情"
            },
            subtitle = "${uiState.characterName} · ${formatLetterTime(letter?.updatedAt ?: 0L)}",
            colors = colors,
            onBack = onBack,
        )
        if (letter == null) {
            MailboxEmptyState(MailboxBox.INBOX, colors)
            return
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item {
                MailboxLetterPaper(
                    letter = letter,
                    colors = colors,
                    characterName = uiState.characterName,
                    userName = uiState.userName,
                )
            }
            item {
                MailboxBridgeCard(
                    uiState = uiState,
                    colors = colors,
                    onOpenDiary = callbacks.onOpenDiary,
                    onOpenPhoneCheck = callbacks.onOpenPhoneCheck,
                )
            }
            item {
                MailboxActionBar(
                    letter = letter,
                    colors = colors,
                    isGeneratingReply = uiState.isGeneratingReply,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailboxLetterPaper(
    letter: MailboxLetter,
    colors: MailboxColors,
    characterName: String,
    userName: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("来自 ${senderLabel(letter, characterName, userName)}", color = colors.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(if (letter.allowMemory) "可沉淀为记忆" else "普通信件", color = colors.muted, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = letter.subject.ifBlank { "未命名的信" },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = letter.content,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.body,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.25,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                letter.tags.forEach { tag ->
                    MailboxChip(tag, colors.accentSoft, colors.accent)
                }
                if (letter.mood.isNotBlank()) {
                    MailboxChip(letter.mood, colors.unreadSoft, colors.unread)
                }
                if (letter.allowMemory) {
                    MailboxChip("记忆候选", colors.memorySoft, colors.memory)
                }
            }
        }
    }
}

@Composable
private fun MailboxActionBar(
    letter: MailboxLetter,
    colors: MailboxColors,
    isGeneratingReply: Boolean,
    callbacks: MailboxCallbacks,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        FlowRow(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (letter.box == MailboxBox.INBOX) {
                MailboxActionButton("回信", Icons.AutoMirrored.Filled.Reply, colors.accent, colors) {
                    callbacks.onStartCompose(letter.id)
                }
            }
            if (letter.box == MailboxBox.SENT) {
                if (isGeneratingReply) {
                    MailboxActionButton("停止回信", Icons.Default.Close, colors.unread, colors) {
                        callbacks.onCancelGeneration()
                    }
                } else {
                    MailboxActionButton("生成回信", Icons.Default.AutoAwesome, colors.accent, colors) {
                        callbacks.onGenerateReply(letter.id)
                    }
                }
            }
            if (letter.box == MailboxBox.DRAFT) {
                MailboxActionButton("继续写", Icons.Default.Edit, colors.accent, colors) {
                    callbacks.onEditDraft(letter.id)
                }
            }
            MailboxActionButton("记住", Icons.Default.PushPin, colors.memory, colors, enabled = letter.allowMemory) {
                callbacks.onCreateMemoryCandidate(letter.id)
            }
            MailboxActionButton("引用", Icons.Default.MarkEmailRead, colors.sent, colors) {
                callbacks.onQuoteLetter(letter.id)
            }
            if (letter.box != MailboxBox.ARCHIVE) {
                MailboxActionButton("归档", Icons.Default.Archive, colors.title, colors) {
                    callbacks.onArchive(letter.id)
                }
            }
            MailboxActionButton("删除", Icons.Default.Delete, colors.unread, colors) {
                callbacks.onDelete(letter.id)
            }
        }
    }
}

@Composable
private fun MailboxComposer(
    uiState: MailboxUiState,
    colors: MailboxColors,
    callbacks: MailboxCallbacks,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MailboxTopBar(
            title = "写信",
            subtitle = uiState.characterName,
            colors = colors,
            onBack = onBack,
            trailingIcon = Icons.Default.Check,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MailboxAvatar(uiState.characterName, colors, colors.accent, small = true)
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color.Transparent,
                            ) {
                                Text(
                                    text = "${uiState.characterName} · 当前会话",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = colors.title,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.draftSubject,
                            onValueChange = callbacks.onSubjectChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("主题") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = uiState.draftContent,
                            onValueChange = callbacks.onContentChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 260.dp),
                            label = { Text("正文") },
                            minLines = 10,
                        )
                        MailboxSwitchRow("带入最近会话", uiState.includeRecentChat, callbacks.onIncludeRecentChatChange, colors)
                        MailboxSwitchRow("带入手机线索", uiState.includePhoneClues, callbacks.onIncludePhoneCluesChange, colors)
                        MailboxSwitchRow("允许沉淀为记忆", uiState.allowMemory, callbacks.onAllowMemoryChange, colors)
                        MailboxSwitchRow("生成角色回信", uiState.generateReplyAfterSend, callbacks.onGenerateReplyAfterSendChange, colors)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = callbacks.onSaveDraft,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.title),
                shape = RoundedCornerShape(NarraUi.smallRadius),
                enabled = !uiState.isSaving,
            ) {
                Text("存草稿")
            }
            Button(
                onClick = callbacks.onSend,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                shape = RoundedCornerShape(NarraUi.smallRadius),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onAccent)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("寄出")
                }
            }
        }
    }
}

@Composable
private fun MailboxSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: MailboxColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = colors.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MailboxActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    colors: MailboxColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
        shape = RoundedCornerShape(NarraUi.smallRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint.copy(alpha = 0.14f),
            contentColor = tint,
            disabledContainerColor = colors.border,
            disabledContentColor = colors.muted,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun MailboxAvatar(
    label: String,
    colors: MailboxColors,
    tint: Color,
    small: Boolean = false,
) {
    val size = if (small) 42.dp else 56.dp
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(if (small) 14.dp else 18.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label.trim().take(1).ifBlank { "信" },
                color = tint,
                style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun MailboxChip(
    label: String,
    color: Color,
    textColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(NarraUi.smallRadius),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MailboxEmptyState(
    box: MailboxBox,
    colors: MailboxColors,
) {
    val (title, subtitle) = when (box) {
        MailboxBox.INBOX -> "还没有来信" to "等关系推进后，角色写来的信会出现在这里。"
        MailboxBox.DRAFT -> "还没有未写完的信" to "写给角色的话会保存成草稿。"
        MailboxBox.SENT -> "你还没有寄出过信" to "写一封慢一点的信，把没说完的话留下来。"
        MailboxBox.ARCHIVE -> "还没有归档信件" to "重要的来信和回信可以收进这里。"
        MailboxBox.TRASH -> "没有已删除信件" to ""
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Mail, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text(title, color = colors.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MailboxNoMatchesState(colors: MailboxColors) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(34.dp))
            Text("没有匹配的信", color = colors.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("换个关键词或标签试试。", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun senderLabel(
    letter: MailboxLetter,
    characterName: String,
    userName: String,
): String {
    return when (letter.senderType) {
        MailboxSenderType.USER -> userName.ifBlank { "我" }
        MailboxSenderType.CHARACTER -> characterName.ifBlank { "角色" }
        MailboxSenderType.SYSTEM -> "系统"
    }
}

private fun formatLetterTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "刚刚"
    }
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
