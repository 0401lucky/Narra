package com.example.myapplication.ui.screen.immersive

import androidx.compose.animation.AnimatedVisibility
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.UserProfileAvatar
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ImmersivePhoneCallbacks(
    val onNavigateBack: () -> Unit,
    val onOpenChat: (String) -> Unit,
    val onOpenChatManage: () -> Unit,
    val onOpenChatEdit: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenUserMasks: () -> Unit,
    val onSetDefaultUserPersonaMask: (String) -> Unit,
    val onOpenAssistantCreate: () -> Unit,
    val onCreateChat: (String, RoleplayInteractionMode, Boolean) -> Unit,
    val onUpdatePinned: (String, Boolean) -> Unit,
    val onUpdateMuted: (String, Boolean) -> Unit,
    val onClearChat: (String) -> Unit,
    val onDeleteChat: (String) -> Unit,
    val onOpenPhoneCheck: (String) -> Unit,
    val onOpenMoments: (String) -> Unit,
    val onOpenDiary: (String) -> Unit,
    val onOpenVideoCall: (String) -> Unit,
)

private enum class ImmersiveTab(
    val label: String,
    val icon: ImageVector,
) {
    Messages("消息", Icons.Default.Chat),
    Contacts("通讯录", Icons.Default.Person),
    Discover("广场", Icons.Default.Forum),
    Profile("我", Icons.Default.Person),
}

private enum class DiscoverTarget(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Moments("朋友圈", "按聊天查看动态；无内容先查手机", Icons.Default.Forum),
    PhoneCheck("查手机", "选择聊天后生成或查看手机内容", Icons.Default.PhoneAndroid),
    Diary("日记本", "按聊天生成或查看日记", Icons.Default.Book),
    VideoCall("视频通话", "选择聊天后进入对应角色通话", Icons.Default.Videocam),
}

private data class ModeOption(
    val title: String,
    val subtitle: String,
    val interactionMode: RoleplayInteractionMode,
    val enableNarration: Boolean,
)

private val ModeOptions = listOf(
    ModeOption("线上", "手机聊天，无心声", RoleplayInteractionMode.ONLINE_PHONE, false),
    ModeOption("线上-心声", "手机聊天，允许心声", RoleplayInteractionMode.ONLINE_PHONE, true),
    ModeOption("线下", "对白推进，无旁白", RoleplayInteractionMode.OFFLINE_DIALOGUE, false),
    ModeOption("线下-心声", "对白推进，允许旁白", RoleplayInteractionMode.OFFLINE_DIALOGUE, true),
    ModeOption("剧情模式", "长文叙事体验", RoleplayInteractionMode.OFFLINE_LONGFORM, true),
)

private val ChatSwipeActionWidth = 256.dp
private val ChatSwipeActionButtonWidth = 62.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersivePhoneShell(
    settings: AppSettings,
    assistants: List<Assistant>,
    chatSummaries: List<RoleplayChatSummary>,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    callbacks: ImmersivePhoneCallbacks,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(ImmersiveTab.Messages) }
    var plusMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showNewChatSheet by rememberSaveable { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Assistant?>(null) }
    var discoverTarget by remember { mutableStateOf<DiscoverTarget?>(null) }

    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    val sortedSummaries = remember(chatSummaries) {
        chatSummaries.sortedWith(
            compareByDescending<RoleplayChatSummary> { it.scenario.isPinned }
                .thenByDescending { it.lastActiveAt },
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ImmersivePhoneTopBar(
                title = currentTab.label,
                onNavigateBack = callbacks.onNavigateBack,
                showSearch = false,
                showAdd = currentTab == ImmersiveTab.Messages || currentTab == ImmersiveTab.Contacts,
                onAdd = {
                    if (currentTab == ImmersiveTab.Messages) {
                        plusMenuExpanded = true
                    } else {
                        callbacks.onOpenAssistantCreate()
                    }
                },
                plusMenuExpanded = plusMenuExpanded,
                onDismissPlusMenu = { plusMenuExpanded = false },
                onOpenNewChat = {
                    plusMenuExpanded = false
                    showNewChatSheet = true
                },
                onOpenChatManage = {
                    plusMenuExpanded = false
                    callbacks.onOpenChatManage()
                },
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                ImmersiveTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        ) {
            when (currentTab) {
                ImmersiveTab.Messages -> ImmersiveMessagesPage(
                    summaries = sortedSummaries,
                    assistants = assistants,
                    callbacks = callbacks,
                    onOpenNewChat = { showNewChatSheet = true },
                )

                ImmersiveTab.Contacts -> ImmersiveContactsPage(
                    assistants = assistants,
                    summaries = sortedSummaries,
                    onOpenContact = { selectedContact = it },
                    onOpenAssistantCreate = callbacks.onOpenAssistantCreate,
                    onOpenChatManage = callbacks.onOpenChatManage,
                )

                ImmersiveTab.Discover -> ImmersiveDiscoverPage(
                    onOpenTarget = { discoverTarget = it },
                )

                ImmersiveTab.Profile -> ImmersiveProfilePage(
                    settings = settings,
                    chatCount = sortedSummaries.size,
                    assistantCount = assistants.size,
                    onOpenChatManage = callbacks.onOpenChatManage,
                    onOpenUserMasks = callbacks.onOpenUserMasks,
                    onSetDefaultUserPersonaMask = callbacks.onSetDefaultUserPersonaMask,
                    onOpenSettings = callbacks.onOpenSettings,
                )
            }
        }
    }

    if (showNewChatSheet) {
        NewChatSheet(
            assistants = assistants,
            initialAssistant = null,
            onDismiss = { showNewChatSheet = false },
            onCreateChat = { assistantId, mode, narration ->
                showNewChatSheet = false
                callbacks.onCreateChat(assistantId, mode, narration)
            },
        )
    }

    selectedContact?.let { contact ->
        ContactCardSheet(
            assistant = contact,
            summaries = sortedSummaries.filter { it.scenario.assistantId == contact.id },
            onDismiss = { selectedContact = null },
            onOpenChat = { scenarioId ->
                selectedContact = null
                callbacks.onOpenChat(scenarioId)
            },
            onCreateChat = { assistantId, mode, narration ->
                selectedContact = null
                callbacks.onCreateChat(assistantId, mode, narration)
            },
        )
    }

    discoverTarget?.let { target ->
        ChatPickerSheet(
            title = target.title,
            summaries = sortedSummaries,
            assistants = assistants,
            onDismiss = { discoverTarget = null },
            onSelect = { scenarioId ->
                discoverTarget = null
                when (target) {
                    DiscoverTarget.Moments -> callbacks.onOpenMoments(scenarioId)
                    DiscoverTarget.PhoneCheck -> callbacks.onOpenPhoneCheck(scenarioId)
                    DiscoverTarget.Diary -> callbacks.onOpenDiary(scenarioId)
                    DiscoverTarget.VideoCall -> callbacks.onOpenVideoCall(scenarioId)
                }
            },
        )
    }
}

@Composable
private fun ImmersivePhoneTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    showSearch: Boolean,
    showAdd: Boolean,
    onAdd: () -> Unit,
    plusMenuExpanded: Boolean,
    onDismissPlusMenu: () -> Unit,
    onOpenNewChat: () -> Unit,
    onOpenChatManage: () -> Unit,
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (showSearch) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Box {
                if (showAdd) {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "新增")
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                DropdownMenu(
                    expanded = plusMenuExpanded,
                    onDismissRequest = onDismissPlusMenu,
                ) {
                    DropdownMenuItem(
                        text = { Text("新建聊天") },
                        leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        onClick = onOpenNewChat,
                    )
                    DropdownMenuItem(
                        text = { Text("聊天管理") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = onOpenChatManage,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveMessagesPage(
    summaries: List<RoleplayChatSummary>,
    assistants: List<Assistant>,
    callbacks: ImmersivePhoneCallbacks,
    onOpenNewChat: () -> Unit,
) {
    if (summaries.isEmpty()) {
        EmptyState(
            title = "还没有聊天",
            subtitle = "先选择一个角色，开一段新的沉浸聊天。",
            actionText = "新建聊天",
            onAction = onOpenNewChat,
        )
        return
    }
    var revealedScenarioId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(summaries, key = { it.scenario.id }) { summary ->
            val scenarioId = summary.scenario.id
            val actionWidthPx = with(LocalDensity.current) { ChatSwipeActionWidth.toPx() }
            var offsetX by remember(summary.scenario.id) { mutableFloatStateOf(0f) }
            var dragStartOffsetX by remember(summary.scenario.id) { mutableFloatStateOf(0f) }
            val isRevealed = offsetX < 0f
            fun closeSwipe() {
                offsetX = 0f
                if (revealedScenarioId == scenarioId) {
                    revealedScenarioId = null
                }
            }
            fun settleSwipe() {
                val startedRevealed = dragStartOffsetX <= -actionWidthPx * 0.9f
                val draggedRightFromOpen = startedRevealed &&
                    offsetX - dragStartOffsetX >= actionWidthPx * 0.12f
                if (draggedRightFromOpen) {
                    closeSwipe()
                } else if (offsetX <= -actionWidthPx * 0.35f) {
                    offsetX = -actionWidthPx
                    revealedScenarioId = scenarioId
                } else {
                    closeSwipe()
                }
            }
            fun runSwipeAction(action: () -> Unit) {
                action()
                closeSwipe()
            }
            LaunchedEffect(revealedScenarioId) {
                if (revealedScenarioId != scenarioId && offsetX != 0f) {
                    offsetX = 0f
                }
            }
            val dragState = rememberDraggableState { dragAmount ->
                offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { dragStartOffsetX = offsetX },
                        onDragStopped = { settleSwipe() },
                    ),
            ) {
                SwipeActions(
                    modifier = Modifier.matchParentSize(),
                    scenario = summary.scenario,
                    onPin = {
                        runSwipeAction {
                            callbacks.onUpdatePinned(summary.scenario.id, !summary.scenario.isPinned)
                        }
                    },
                    onMute = {
                        runSwipeAction {
                            callbacks.onUpdateMuted(summary.scenario.id, !summary.scenario.isMuted)
                        }
                    },
                    onClear = {
                        runSwipeAction {
                            callbacks.onClearChat(summary.scenario.id)
                        }
                    },
                    onDelete = {
                        runSwipeAction {
                            callbacks.onDeleteChat(summary.scenario.id)
                        }
                    },
                )
                ChatSummaryRow(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), 0) },
                    summary = summary,
                    assistant = assistants.firstOrNull { it.id == summary.scenario.assistantId },
                    onOpen = {
                        if (isRevealed) {
                            closeSwipe()
                        } else {
                            callbacks.onOpenChat(summary.scenario.id)
                        }
                    },
                    onEdit = {
                        if (isRevealed) {
                            closeSwipe()
                        } else {
                            callbacks.onOpenChatEdit(summary.scenario.id)
                        }
                    },
                    dragged = isRevealed,
                )
            }
        }
    }
}

@Composable
private fun SwipeActions(
    modifier: Modifier = Modifier,
    scenario: RoleplayScenario,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(ChatSwipeActionWidth)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeActionButton(
                label = if (scenario.isPinned) "取消置顶" else "置顶",
                onClick = onPin,
            )
            SwipeActionButton(
                label = if (scenario.isMuted) "取消免扰" else "免打扰",
                onClick = onMute,
            )
            SwipeActionButton(
                label = "清空",
                onClick = onClear,
            )
            SwipeActionButton(
                label = "删除",
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(ChatSwipeActionButtonWidth)
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatSummaryRow(
    modifier: Modifier = Modifier,
    summary: RoleplayChatSummary,
    assistant: Assistant?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    dragged: Boolean,
) {
    val scenario = summary.scenario
    val name = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name.orEmpty() }
        .ifBlank { "角色" }
    val title = scenario.title.trim()
    val displayName = if (title.isNotBlank()) "$name / $title" else name
    val latest = summary.lastMessageText.ifBlank {
        if (summary.hasSession) "最近没有消息" else "还没有开始聊天"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = if (dragged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantAvatar(
                name = name,
                iconName = assistant?.iconName.orEmpty(),
                avatarUri = scenario.characterPortraitUri.ifBlank { assistant?.avatarUri.orEmpty() },
                size = 52.dp,
                cornerRadius = 12.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMessageTime(summary.lastActiveAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = latest,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (scenario.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = "已置顶", modifier = Modifier.size(16.dp))
                    }
                    if (scenario.isMuted) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = "免打扰", modifier = Modifier.size(16.dp))
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.MoreVert, contentDescription = "聊天资料")
            }
        }
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ImmersiveContactsPage(
    assistants: List<Assistant>,
    summaries: List<RoleplayChatSummary>,
    onOpenContact: (Assistant) -> Unit,
    onOpenAssistantCreate: () -> Unit,
    onOpenChatManage: () -> Unit,
) {
    val grouped = remember(assistants) {
        assistants.sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.name.ifBlank { "角色" } })
            .groupBy { pinyinInitial(it.name) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ContactShortcutRow("新的角色", Icons.Default.PersonAdd, onOpenAssistantCreate)
            ContactShortcutRow("聊天管理", Icons.Default.Group, onOpenChatManage)
            Divider()
        }
        grouped.forEach { (initial, items) ->
            item(key = "header-$initial") {
                Text(
                    text = initial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items, key = { it.id }) { assistant ->
                val chatCount = summaries.count { it.scenario.assistantId == assistant.id }
                ContactRow(assistant, chatCount, onOpenContact)
            }
        }
        item {
            Text(
                text = "${assistants.size} 位角色",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactShortcutRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
        },
    )
}

@Composable
private fun ContactRow(
    assistant: Assistant,
    chatCount: Int,
    onOpen: (Assistant) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onOpen(assistant) },
        headlineContent = { Text(assistant.name.ifBlank { "未命名角色" }) },
        supportingContent = {
            Text(
                text = if (chatCount > 0) "$chatCount 个聊天" else "还没有聊天",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 46.dp,
                cornerRadius = 12.dp,
            )
        },
    )
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ImmersiveDiscoverPage(
    onOpenTarget: (DiscoverTarget) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        items(DiscoverTarget.entries, key = { it.name }) { target ->
            FeatureRow(
                title = target.title,
                subtitle = target.subtitle,
                icon = target.icon,
                onClick = { onOpenTarget(target) },
            )
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
        },
    )
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ImmersiveProfilePage(
    settings: AppSettings,
    chatCount: Int,
    assistantCount: Int,
    onOpenChatManage: () -> Unit,
    onOpenUserMasks: () -> Unit,
    onSetDefaultUserPersonaMask: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val masks = settings.normalizedUserPersonaMasks()
    val defaultMask = settings.resolvedDefaultUserPersonaMask()
    val maskSummary = when {
        masks.isEmpty() -> "还没有面具，先用全局个人资料"
        defaultMask != null -> "默认：${defaultMask.name} · 共 ${masks.size} 个身份"
        else -> "${masks.size} 个身份，未设置默认"
    }
    var masksExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 18.dp),
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        UserProfileAvatar(
                            displayName = settings.resolvedUserDisplayName(),
                            avatarUri = settings.userAvatarUri,
                            avatarUrl = settings.userAvatarUrl,
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(settings.resolvedUserDisplayName(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "$chatCount 个聊天 · $assistantCount 位角色",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = maskSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            ImmersiveMaskSwitcher(
                masks = masks,
                defaultMask = defaultMask,
                expanded = masksExpanded,
                onToggleExpanded = { masksExpanded = !masksExpanded },
                onSetDefaultMask = onSetDefaultUserPersonaMask,
                onOpenUserMasks = onOpenUserMasks,
            )
        }
        item { FeatureRow("聊天管理", "查看、编辑和删除聊天资料", Icons.Default.Chat, onOpenChatManage) }
        item { FeatureRow("设置", "模型、显示、世界书与记忆", Icons.Default.Settings, onOpenSettings) }
        item { FeatureRow("关于", "Narra 沉浸世界", Icons.Default.Info) {} }
    }
}

@Composable
private fun ImmersiveMaskSwitcher(
    masks: List<UserPersonaMask>,
    defaultMask: UserPersonaMask?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetDefaultMask: (String) -> Unit,
    onOpenUserMasks: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val summary = when {
        masks.isEmpty() -> "还没有面具，先创建不同对话里的“我”"
        defaultMask != null -> "默认：${defaultMask.name} · 共 ${masks.size} 个身份"
        else -> "${masks.size} 个身份，未设置默认"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("我的面具", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (masks.isEmpty()) {
                        Text(
                            text = "暂无面具。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    } else {
                        masks.forEach { mask ->
                            ImmersiveMaskOptionRow(
                                mask = mask,
                                selected = mask.id == defaultMask?.id,
                                onClick = { onSetDefaultMask(mask.id) },
                            )
                        }
                    }
                    TextButton(onClick = onOpenUserMasks, modifier = Modifier.fillMaxWidth()) {
                        Text("管理面具")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveMaskOptionRow(
    mask: UserPersonaMask,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserProfileAvatar(
                displayName = mask.name,
                avatarUri = mask.avatarUri,
                avatarUrl = mask.avatarUrl,
                modifier = Modifier.size(34.dp),
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(mask.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = mask.personaPrompt.ifBlank { mask.note }.ifBlank { "未填写人设" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前默认面具",
                    tint = colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    assistants: List<Assistant>,
    initialAssistant: Assistant?,
    onDismiss: () -> Unit,
    onCreateChat: (String, RoleplayInteractionMode, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedAssistant by remember { mutableStateOf(initialAssistant) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("新建聊天", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (selectedAssistant == null) {
                Text("选择一个角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(assistants, key = { it.id }) { assistant ->
                        ContactRow(
                            assistant = assistant,
                            chatCount = 0,
                            onOpen = { selectedAssistant = it },
                        )
                    }
                }
            } else {
                selectedAssistant?.let { assistant ->
                    AssistChip(
                        onClick = { selectedAssistant = null },
                        label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                Text("选择聊天模式", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ModeOptions.forEach { option ->
                    ModeOptionCard(
                        option = option,
                        onClick = {
                            selectedAssistant?.let { assistant ->
                                onCreateChat(assistant.id, option.interactionMode, option.enableNarration)
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCardSheet(
    assistant: Assistant,
    summaries: List<RoleplayChatSummary>,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onCreateChat: (String, RoleplayInteractionMode, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showModePicker by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistantAvatar(
                    name = assistant.name,
                    iconName = assistant.iconName,
                    avatarUri = assistant.avatarUri,
                    size = 64.dp,
                    cornerRadius = 16.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(assistant.name.ifBlank { "未命名角色" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (summaries.isEmpty()) "还没有聊天" else "${summaries.size} 个聊天",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        summaries.firstOrNull()?.let { onOpenChat(it.scenario.id) } ?: run {
                            showModePicker = true
                        }
                    },
                ) {
                    Text("发消息")
                }
                TextButton(onClick = { showModePicker = !showModePicker }) {
                    Text("新建聊天")
                }
            }
            if (showModePicker) {
                ModeOptions.forEach { option ->
                    ModeOptionCard(
                        option = option,
                        onClick = { onCreateChat(assistant.id, option.interactionMode, option.enableNarration) },
                    )
                }
            }
            if (summaries.isNotEmpty()) {
                Text("这个角色的聊天", fontWeight = FontWeight.SemiBold)
                summaries.forEach { summary ->
                    ListItem(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenChat(summary.scenario.id) },
                        headlineContent = {
                            Text(
                                chatPickerSummaryText(summary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(formatMessageTime(summary.lastActiveAt)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeOptionCard(
    option: ModeOption,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(option.title, fontWeight = FontWeight.SemiBold)
                Text(option.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPickerSheet(
    title: String,
    summaries: List<RoleplayChatSummary>,
    assistants: List<Assistant>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择聊天 · $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summaries.isEmpty()) {
                Text("还没有可用聊天。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(summaries, key = { it.scenario.id }) { summary ->
                        val assistant = assistants.firstOrNull { it.id == summary.scenario.assistantId }
                        ChatPickerRow(summary, assistant) { onSelect(summary.scenario.id) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChatPickerRow(
    summary: RoleplayChatSummary,
    assistant: Assistant?,
    onClick: () -> Unit,
) {
    val scenario = summary.scenario
    val name = scenario.characterDisplayNameOverride.ifBlank { assistant?.name.orEmpty() }.ifBlank { "角色" }
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                text = chatPickerSummaryText(summary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AssistantAvatar(
                name = name,
                iconName = assistant?.iconName.orEmpty(),
                avatarUri = scenario.characterPortraitUri.ifBlank { assistant?.avatarUri.orEmpty() },
                size = 44.dp,
                cornerRadius = 12.dp,
            )
        },
    )
}

private fun chatPickerSummaryText(summary: RoleplayChatSummary): String {
    val title = summary.scenario.title.trim()
    return when {
        title.isNotBlank() -> title
        summary.hasHistory -> "最近有聊天记录"
        summary.hasSession -> "最近没有消息"
        else -> "还没有开始聊天"
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(34.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun pinyinInitial(name: String): String {
    val source = name.trim().ifBlank { "#" }
    val latin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { transliterateHanToLatin(source) }.getOrDefault(source)
    } else {
        source
    }
    val first = latin.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
    return if (first in 'A'..'Z') first.toString() else "#"
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun transliterateHanToLatin(source: String): String {
    return Transliterator.getInstance("Han-Latin; Latin-ASCII").transliterate(source)
}
