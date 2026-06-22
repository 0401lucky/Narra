package com.example.myapplication.ui.screen.immersive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mail
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CharacterShakeFilters
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.phone.RoleplayPhoneActivityItem
import com.example.myapplication.phone.RoleplayPhoneActivityKind
import com.example.myapplication.phone.RoleplayPhoneEcosystemSnapshot
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.UserProfileAvatar
import com.example.myapplication.viewmodel.CharacterShakeUiState
import com.composables.icons.lucide.BookUser
import com.composables.icons.lucide.CircleUserRound
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircleMore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
    val onOpenAssistantSettings: () -> Unit,
    val onOpenCharacterArtStudio: () -> Unit,
    val onOpenAssistantDetail: (String) -> Unit,
    val onOpenAssistantPrompt: (String) -> Unit,
    val onOpenAssistantMemory: (String) -> Unit,
    val onOpenAssistantWorldBook: (String) -> Unit,
    val onOpenWorldBookSettings: () -> Unit,
    val onOpenMemorySettings: () -> Unit,
    val onOpenContextTransferSettings: () -> Unit,
    val onSetDefaultUserPersonaMask: (String) -> Unit,
    val onOpenAssistantCreate: () -> Unit,
    val onCreateChat: (String, RoleplayInteractionMode, Boolean) -> Unit,
    val onCreateGroupChat: (String, List<String>) -> Unit,
    val onUpdatePinned: (String, Boolean) -> Unit,
    val onUpdateMuted: (String, Boolean) -> Unit,
    val onClearChat: (String) -> Unit,
    val onDeleteChat: (String) -> Unit,
    val onOpenPhoneCheck: (String) -> Unit,
    val onOpenMoments: (String) -> Unit,
    val onOpenDiary: (String) -> Unit,
    val onOpenVideoCall: (String) -> Unit,
    val onOpenMailbox: (String) -> Unit,
    val onOpenWallet: (String) -> Unit,
)

private enum class ImmersiveTab(
    val label: String,
    val icon: ImageVector,
) {
    Messages("消息", Lucide.MessageCircleMore),
    Contacts("通讯录", Lucide.BookUser),
    Discover("发现", Lucide.Compass),
    Profile("我", Lucide.CircleUserRound),
}

private enum class DiscoverTarget(
    val title: String,
    val icon: ImageVector,
) {
    Moments("朋友圈", Icons.Default.Forum),
    PhoneCheck("查手机", Icons.Default.PhoneAndroid),
    Diary("日记本", Icons.Default.Book),
    Mailbox("信箱", Icons.Default.Mail),
    Wallet("钱包", Icons.Default.AccountBalanceWallet),
    VideoCall("视频通话", Icons.Default.Videocam),
}

private data class CharacterShakeFilterGroup(
    val key: String,
    val title: String,
    val options: List<String>,
)

private val CharacterShakeFilterGroups = listOf(
    CharacterShakeFilterGroup("gender", "性别偏好", listOf("", "男生", "女生", "非二元")),
    CharacterShakeFilterGroup("age", "年龄区间", listOf("", "18-22", "23-27", "28-32", "33+")),
    CharacterShakeFilterGroup("personality", "性格特征", listOf("", "温柔", "理性", "活泼", "文艺", "技术")),
    CharacterShakeFilterGroup("identity", "身份特点", listOf("", "学生", "职场新人", "自由职业", "创作者", "互联网从业")),
    CharacterShakeFilterGroup("relationship", "关系定位", listOf("", "恋人", "暧昧对象", "同事", "同学", "邻居", "室友", "师生", "合作伙伴", "前任", "陌生偶遇")),
    CharacterShakeFilterGroup("trait", "个人特征", listOf("", "同城", "高频聊天", "有边界感", "幽默感", "自律")),
)

private data class ModeOption(
    val title: String,
    val subtitle: String,
    val interactionMode: RoleplayInteractionMode,
    val enableNarration: Boolean,
)

private val ModeOptions = listOf(
    ModeOption("线上", "手机会话，无心声", RoleplayInteractionMode.ONLINE_PHONE, false),
    ModeOption("线上-心声", "手机会话，允许心声", RoleplayInteractionMode.ONLINE_PHONE, true),
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
    phoneEcosystem: RoleplayPhoneEcosystemSnapshot = RoleplayPhoneEcosystemSnapshot(),
    characterShakeState: CharacterShakeUiState = CharacterShakeUiState(),
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onUpdateCharacterShakeFilters: (CharacterShakeFilters) -> Unit = {},
    onResetCharacterShakeFilters: () -> Unit = {},
    onGenerateCharacterShake: (CharacterShakeFilters) -> Unit = {},
    onDismissGeneratedCharacter: () -> Unit = {},
    onClearCharacterShakeMessages: () -> Unit = {},
    callbacks: ImmersivePhoneCallbacks,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    var currentTab by rememberSaveable { mutableStateOf(ImmersiveTab.Messages) }
    var plusMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showNewChatSheet by rememberSaveable { mutableStateOf(false) }
    var showNewGroupChatSheet by rememberSaveable { mutableStateOf(false) }
    var showPhoneEcosystemSheet by rememberSaveable { mutableStateOf(false) }
    var showCharacterShakeSheet by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(characterShakeState.message) {
        characterShakeState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearCharacterShakeMessages()
        }
    }
    LaunchedEffect(characterShakeState.errorMessage) {
        characterShakeState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearCharacterShakeMessages()
        }
    }

    val sortedSummaries = remember(chatSummaries) {
        chatSummaries.sortedWith(
            compareByDescending<RoleplayChatSummary> { it.scenario.isPinned }
                .thenByDescending { it.lastActiveAt },
        )
    }
    fun openPhoneActivity(activity: RoleplayPhoneActivityItem) {
        when (activity.kind) {
            RoleplayPhoneActivityKind.MOMENT -> callbacks.onOpenMoments(activity.scenarioId)
            RoleplayPhoneActivityKind.MAILBOX -> {
                if (activity.scenarioId.isBlank()) {
                    discoverTarget = DiscoverTarget.Mailbox
                } else {
                    callbacks.onOpenMailbox(activity.scenarioId)
                }
            }
            RoleplayPhoneActivityKind.DIARY -> {
                if (activity.scenarioId.isBlank()) {
                    discoverTarget = DiscoverTarget.Diary
                } else {
                    callbacks.onOpenDiary(activity.scenarioId)
                }
            }
            RoleplayPhoneActivityKind.VIDEO_CALL -> {
                if (activity.scenarioId.isBlank()) {
                    discoverTarget = DiscoverTarget.VideoCall
                } else {
                    callbacks.onOpenVideoCall(activity.scenarioId)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (currentTab != ImmersiveTab.Profile) {
                ImmersivePhoneTopBar(
                    title = currentTab.label,
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
                    onOpenNewGroupChat = {
                        plusMenuExpanded = false
                        showNewGroupChatSheet = true
                    },
                    onOpenChatManage = {
                        plusMenuExpanded = false
                        callbacks.onOpenChatManage()
                    },
                )
            }
        },
        bottomBar = {
            ImmersiveFloatingBottomBar(
                currentTab = currentTab,
                hazeState = hazeState,
                onTabSelected = { currentTab = it },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        // 只消费顶部 padding，让内容延伸到底部栏下方穿过毛玻璃；
        // 底部空隙交给各页面的列表 contentPadding 预留
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .haze(hazeState)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        ) {
            AnimatedContent(
                targetState = currentTab,
                label = "immersive_phone_tab_content",
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.92f,
                                stiffness = 380f,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ) { width -> direction * width / 4 } +
                            fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing))
                        ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(200, easing = FastOutLinearInEasing),
                            ) { width -> -direction * width / 5 } +
                                fadeOut(animationSpec = tween(150)),
                        ).using(SizeTransform(clip = false))
                },
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    ImmersiveTab.Messages -> ImmersiveMessagesPage(
                        summaries = sortedSummaries,
                        assistants = assistants,
                        callbacks = callbacks,
                        onOpenNewChat = { showNewChatSheet = true },
                        bottomPadding = bottomPadding,
                    )

                    ImmersiveTab.Contacts -> ImmersiveContactsPage(
                        assistants = assistants,
                        summaries = sortedSummaries,
                        onOpenContact = { selectedContact = it },
                        onOpenAssistantCreate = callbacks.onOpenAssistantCreate,
                        onOpenChatManage = callbacks.onOpenChatManage,
                        bottomPadding = bottomPadding,
                    )

                    ImmersiveTab.Discover -> ImmersiveDiscoverPage(
                        phoneEcosystem = phoneEcosystem,
                        onOpenEcosystem = { showPhoneEcosystemSheet = true },
                        onOpenCharacterShake = { showCharacterShakeSheet = true },
                        onOpenCharacterArtStudio = callbacks.onOpenCharacterArtStudio,
                        onOpenTarget = { target ->
                            if (target == DiscoverTarget.Moments) {
                                callbacks.onOpenMoments("")
                            } else {
                                discoverTarget = target
                            }
                        },
                        bottomPadding = bottomPadding,
                    )

                    ImmersiveTab.Profile -> ImmersiveProfilePage(
                        settings = settings,
                        chatCount = sortedSummaries.size,
                        assistantCount = assistants.size,
                        onOpenChatManage = callbacks.onOpenChatManage,
                        onOpenUserMasks = callbacks.onOpenUserMasks,
                        onOpenAssistantSettings = callbacks.onOpenAssistantSettings,
                        onOpenWorldBookSettings = callbacks.onOpenWorldBookSettings,
                        onOpenMemorySettings = callbacks.onOpenMemorySettings,
                        onOpenContextTransferSettings = callbacks.onOpenContextTransferSettings,
                        onSetDefaultUserPersonaMask = callbacks.onSetDefaultUserPersonaMask,
                        onOpenSettings = callbacks.onOpenSettings,
                        bottomPadding = bottomPadding,
                    )
                }
            }
        }
    }

    if (showPhoneEcosystemSheet) {
        PhoneEcosystemSheet(
            snapshot = phoneEcosystem,
            onDismiss = { showPhoneEcosystemSheet = false },
            onOpenActivity = { activity ->
                showPhoneEcosystemSheet = false
                openPhoneActivity(activity)
            },
        )
    }

    if (showCharacterShakeSheet) {
        CharacterShakeSheet(
            uiState = characterShakeState,
            onDismiss = { showCharacterShakeSheet = false },
            onFiltersChange = onUpdateCharacterShakeFilters,
            onResetFilters = onResetCharacterShakeFilters,
            onGenerate = onGenerateCharacterShake,
            onOpenContacts = {
                showCharacterShakeSheet = false
                currentTab = ImmersiveTab.Contacts
                onDismissGeneratedCharacter()
            },
        )
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

    if (showNewGroupChatSheet) {
        NewGroupChatSheet(
            assistants = assistants,
            onDismiss = { showNewGroupChatSheet = false },
            onCreateGroupChat = { title, assistantIds ->
                showNewGroupChatSheet = false
                callbacks.onCreateGroupChat(title, assistantIds)
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
            onOpenAssistantDetail = { assistantId ->
                selectedContact = null
                callbacks.onOpenAssistantDetail(assistantId)
            },
            onOpenAssistantPrompt = { assistantId ->
                selectedContact = null
                callbacks.onOpenAssistantPrompt(assistantId)
            },
            onOpenAssistantMemory = { assistantId ->
                selectedContact = null
                callbacks.onOpenAssistantMemory(assistantId)
            },
            onOpenAssistantWorldBook = { assistantId ->
                selectedContact = null
                callbacks.onOpenAssistantWorldBook(assistantId)
            },
            onOpenPhoneCheck = { scenarioId ->
                selectedContact = null
                callbacks.onOpenPhoneCheck(scenarioId)
            },
            onOpenMoments = { scenarioId ->
                selectedContact = null
                callbacks.onOpenMoments(scenarioId)
            },
            onOpenDiary = { scenarioId ->
                selectedContact = null
                callbacks.onOpenDiary(scenarioId)
            },
            onOpenMailbox = { scenarioId ->
                selectedContact = null
                callbacks.onOpenMailbox(scenarioId)
            },
            onOpenWallet = { scenarioId ->
                selectedContact = null
                callbacks.onOpenWallet(scenarioId)
            },
            onOpenVideoCall = { scenarioId ->
                selectedContact = null
                callbacks.onOpenVideoCall(scenarioId)
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
                    DiscoverTarget.PhoneCheck -> callbacks.onOpenPhoneCheck(scenarioId)
                    DiscoverTarget.Diary -> callbacks.onOpenDiary(scenarioId)
                    DiscoverTarget.Mailbox -> callbacks.onOpenMailbox(scenarioId)
                    DiscoverTarget.Wallet -> callbacks.onOpenWallet(scenarioId)
                    DiscoverTarget.VideoCall -> callbacks.onOpenVideoCall(scenarioId)
                    DiscoverTarget.Moments -> callbacks.onOpenMoments("")
                }
            },
        )
    }
}

@Composable
private fun ImmersiveFloatingBottomBar(
    currentTab: ImmersiveTab,
    hazeState: HazeState,
    onTabSelected: (ImmersiveTab) -> Unit,
) {
    val barShape = RoundedCornerShape(999.dp)
    val selectedShape = RoundedCornerShape(999.dp)
    val tabCount = ImmersiveTab.entries.size
    // 用 ordinal 而非像素驱动动画，宽度变化（旋转/状态恢复）时指示器不会漂移
    val indicatorPosition by animateFloatAsState(
        targetValue = currentTab.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "bottom_bar_indicator_position",
    )
    var indicatorWidthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(elevation = 8.dp, shape = barShape, clip = false)
                .clip(barShape)
                .hazeChild(
                    state = hazeState,
                    shape = barShape,
                    style = HazeStyle(
                        blurRadius = 24.dp,
                        backgroundColor = Color.White,
                        tint = HazeTint(Color.White.copy(alpha = 0.7f)),
                    ),
                )
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)), barShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((indicatorPosition * indicatorWidthPx).roundToInt(), 0) }
                    .fillMaxWidth(1f / tabCount)
                    .fillMaxHeight()
                    .onSizeChanged { indicatorWidthPx = it.width }
                    .clip(selectedShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImmersiveTab.entries.forEach { tab ->
                    ImmersiveFloatingBottomBarItem(
                        modifier = Modifier.weight(1f),
                        tab = tab,
                        selected = currentTab == tab,
                        selectedShape = selectedShape,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveFloatingBottomBarItem(
    modifier: Modifier = Modifier,
    tab: ImmersiveTab,
    selected: Boolean,
    selectedShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val inactiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else inactiveContentColor,
        animationSpec = tween(220),
        label = "bottom_bar_item_content",
    )
    // 低阻尼 spring 让图标落位时先过冲再回弹，产生轻弹一下的手感
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "bottom_bar_item_icon_scale",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(selectedShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                tint = contentColor,
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImmersivePhoneTopBar(
    title: String,
    showSearch: Boolean,
    showAdd: Boolean,
    onAdd: () -> Unit,
    plusMenuExpanded: Boolean,
    onDismissPlusMenu: () -> Unit,
    onOpenNewChat: () -> Unit,
    onOpenNewGroupChat: () -> Unit,
    onOpenChatManage: () -> Unit,
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSearch) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
                Box {
                    if (showAdd) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = "新增")
                        }
                    }
                    DropdownMenu(
                        expanded = plusMenuExpanded,
                        onDismissRequest = onDismissPlusMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建会话") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                            onClick = onOpenNewChat,
                        )
                        DropdownMenuItem(
                            text = { Text("新建群聊") },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                            onClick = onOpenNewGroupChat,
                        )
                        DropdownMenuItem(
                            text = { Text("会话管理") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = onOpenChatManage,
                        )
                    }
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
    bottomPadding: Dp,
) {
    if (summaries.isEmpty()) {
        EmptyState(
            title = "还没有会话",
            subtitle = "先选择一个角色，开一段新的沉浸剧情。",
            actionText = "新建会话",
            onAction = onOpenNewChat,
            modifier = Modifier.padding(bottom = bottomPadding),
        )
        return
    }
    var revealedScenarioId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomPadding),
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
    val isGroupChat = scenario.isGroupChat
    val name = if (isGroupChat) {
        scenario.title.trim().ifBlank { "群聊" }
    } else {
        scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name.orEmpty() }
            .ifBlank { "角色" }
    }
    val title = if (isGroupChat) "" else scenario.title.trim()
    val displayName = if (title.isNotBlank()) "$name / $title" else name
    val latestContent = sanitizeChatSummaryPreview(summary.lastMessageText).ifBlank {
        if (summary.hasSession) "最近没有消息" else "还没有开始会话"
    }
    val latest = if (isGroupChat) "群聊 · $latestContent" else latestContent
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
            if (isGroupChat) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Group, contentDescription = null)
                    }
                }
            } else {
                AssistantAvatar(
                    name = name,
                    iconName = assistant?.iconName.orEmpty(),
                    avatarUri = scenario.characterPortraitUri.ifBlank { assistant?.avatarUri.orEmpty() },
                    size = 52.dp,
                    cornerRadius = 12.dp,
                )
            }
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
                Icon(Icons.Default.MoreVert, contentDescription = "会话资料")
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
    bottomPadding: Dp,
) {
    val grouped = remember(assistants) {
        assistants.sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.name.ifBlank { "角色" } })
            .groupBy { pinyinInitial(it.name) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + bottomPadding),
    ) {
        item {
            ContactShortcutRow("新的角色", Icons.Default.PersonAdd, onOpenAssistantCreate)
            ContactShortcutRow("会话管理", Icons.Default.Group, onOpenChatManage)
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
                text = if (chatCount > 0) "$chatCount 个会话" else "还没有会话",
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
    phoneEcosystem: RoleplayPhoneEcosystemSnapshot,
    onOpenEcosystem: () -> Unit,
    onOpenCharacterShake: () -> Unit,
    onOpenCharacterArtStudio: () -> Unit,
    onOpenTarget: (DiscoverTarget) -> Unit,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 20.dp + bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PhoneEcosystemEntry(
                snapshot = phoneEcosystem,
                onClick = onOpenEcosystem,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiscoverSectionHeader(icon = Icons.Default.AutoAwesome, title = "AI 创作")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverAiTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        title = "摇一摇",
                        subtitle = "摇出新角色，自动入通讯录",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenCharacterShake,
                    )
                    DiscoverAiTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Image,
                        title = "角色图工作台",
                        subtitle = "生成非真人风格头像图",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenCharacterArtStudio,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiscoverSectionHeader(icon = Icons.Default.Smartphone, title = "角色空间")
                DiscoverSpaceGrid(
                    targets = DiscoverTarget.entries,
                    onOpenTarget = onOpenTarget,
                )
            }
        }
    }
}

@Composable
private fun PhoneEcosystemEntry(
    snapshot: RoleplayPhoneEcosystemSnapshot,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        Color.Transparent,
                    ),
                ),
            ),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("今日动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = phoneEcosystemSummaryText(snapshot),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "查看",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhoneEcosystemStat(
                        label = "未读来信",
                        value = snapshot.unreadMailboxCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    PhoneEcosystemStat(
                        label = "角色动态",
                        value = snapshot.latestMomentCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    PhoneEcosystemStat(
                        label = "通话中",
                        value = snapshot.activeVideoCallCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DiscoverAiTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    onContainer: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        container.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                ),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = container,
                    contentColor = onContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSpaceGrid(
    targets: List<DiscoverTarget>,
    onOpenTarget: (DiscoverTarget) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        targets.chunked(3).forEach { rowTargets ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTargets.forEach { target ->
                    DiscoverSpaceTile(
                        modifier = Modifier.weight(1f),
                        target = target,
                        onClick = { onOpenTarget(target) },
                    )
                }
                repeat(3 - rowTargets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverSpaceTile(
    target: DiscoverTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(target.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = target.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterShakeSheet(
    uiState: CharacterShakeUiState,
    onDismiss: () -> Unit,
    onFiltersChange: (CharacterShakeFilters) -> Unit,
    onResetFilters: () -> Unit,
    onGenerate: (CharacterShakeFilters) -> Unit,
    onOpenContacts: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    var preferencesExpanded by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetHeight)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ShakeHeroButton(
                        uiState = uiState,
                        onShake = { onGenerate(uiState.filters) },
                    )
                }
                uiState.generatedAssistant?.let { assistant ->
                    item {
                        GeneratedAssistantResultCard(
                            assistant = assistant,
                            onOpenContacts = onOpenContacts,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShakePreferenceToggle(
                            expanded = preferencesExpanded,
                            summary = uiState.filters.selectedSummary(),
                            hasSelection = uiState.filters.hasSelectedOption(),
                            enabled = !uiState.isGenerating,
                            onToggle = { preferencesExpanded = !preferencesExpanded },
                            onReset = onResetFilters,
                        )
                        AnimatedVisibility(visible = preferencesExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CharacterShakeFilterGroups.forEach { group ->
                                    CharacterShakeFilterGroupCard(
                                        group = group,
                                        filters = uiState.filters,
                                        enabled = !uiState.isGenerating,
                                        onFiltersChange = onFiltersChange,
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
private fun ShakeHeroButton(
    uiState: CharacterShakeUiState,
    onShake: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "shake_dice")
    val wobble by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 160, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shake_wobble",
    )
    val angle = if (uiState.isGenerating) wobble * 14f else 0f
    val statusText = when {
        uiState.isGenerating -> "正在摇出角色…"
        uiState.filters.hasSelectedOption() -> "点击按偏好摇出角色"
        else -> "点击随机摇出角色"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !uiState.isGenerating) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onShake()
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        Color.Transparent,
                    ),
                ),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer { rotationZ = angle },
                        )
                    }
                }
                Text("摇一摇", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShakePreferenceToggle(
    expanded: Boolean,
    summary: String,
    hasSelection: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("偏好设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (hasSelection) summary else "默认完全随机，点击展开可精挑细选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasSelection) {
            TextButton(onClick = onReset, enabled = enabled) {
                Text("重置")
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "收起" else "展开",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterShakeFilterGroupCard(
    group: CharacterShakeFilterGroup,
    filters: CharacterShakeFilters,
    enabled: Boolean,
    onFiltersChange: (CharacterShakeFilters) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = filters.valueForShakeGroup(group.key).ifBlank { "不限" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.options.forEach { option ->
                    val label = option.ifBlank { "不限" }
                    val selected = filters.valueForShakeGroup(group.key) == option
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        modifier = Modifier
                            .height(38.dp)
                            .widthIn(min = 86.dp),
                        onClick = {
                            onFiltersChange(filters.copyForShakeGroup(group.key, option))
                        },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedAssistantResultCard(
    assistant: Assistant,
    onOpenContacts: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssistantAvatar(
                    name = assistant.name,
                    iconName = assistant.iconName,
                    avatarUri = assistant.avatarUri,
                    size = 52.dp,
                    cornerRadius = 8.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name.ifBlank { "未命名角色" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "已加入通讯录，可直接开启会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            if (assistant.description.isNotBlank()) {
                Text(
                    text = assistant.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onOpenContacts,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("去通讯录")
            }
        }
    }
}

private fun CharacterShakeFilters.valueForShakeGroup(key: String): String {
    return when (key) {
        "gender" -> gender
        "age" -> ageRange
        "personality" -> personality
        "identity" -> identity
        "relationship" -> relationship
        "trait" -> personalTrait
        else -> ""
    }
}

private fun CharacterShakeFilters.copyForShakeGroup(
    key: String,
    value: String,
): CharacterShakeFilters {
    return when (key) {
        "gender" -> copy(gender = value)
        "age" -> copy(ageRange = value)
        "personality" -> copy(personality = value)
        "identity" -> copy(identity = value)
        "relationship" -> copy(relationship = value)
        "trait" -> copy(personalTrait = value)
        else -> this
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneEcosystemSheet(
    snapshot: RoleplayPhoneEcosystemSnapshot,
    onDismiss: () -> Unit,
    onOpenActivity: (RoleplayPhoneActivityItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("今日动态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = phoneEcosystemSummaryText(snapshot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhoneEcosystemStat(
                    label = "未读来信",
                    value = snapshot.unreadMailboxCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                PhoneEcosystemStat(
                    label = "角色动态",
                    value = snapshot.latestMomentCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                PhoneEcosystemStat(
                    label = "通话中",
                    value = snapshot.activeVideoCallCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (snapshot.items.isEmpty()) {
                Text(
                    text = "等角色写信、发朋友圈或留下日记后，这里会自动汇总。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(snapshot.items, key = { it.id }) { item ->
                        PhoneEcosystemActivityRow(
                            item = item,
                            onClick = { onOpenActivity(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneEcosystemStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhoneEcosystemActivityRow(
    item: RoleplayPhoneActivityItem,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = item.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(activityIcon(item.kind), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = {
            val timeText = formatMessageTime(item.timestamp)
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun activityIcon(kind: RoleplayPhoneActivityKind): ImageVector {
    return when (kind) {
        RoleplayPhoneActivityKind.MOMENT -> Icons.Default.Forum
        RoleplayPhoneActivityKind.MAILBOX -> Icons.Default.Mail
        RoleplayPhoneActivityKind.DIARY -> Icons.Default.Book
        RoleplayPhoneActivityKind.VIDEO_CALL -> Icons.Default.Videocam
    }
}

private fun phoneEcosystemSummaryText(snapshot: RoleplayPhoneEcosystemSnapshot): String {
    val parts = buildList {
        if (snapshot.unreadMailboxCount > 0) add("${snapshot.unreadMailboxCount} 封未读来信")
        if (snapshot.latestMomentCount > 0) add("${snapshot.latestMomentCount} 条角色动态")
        if (snapshot.activeVideoCallCount > 0) add("${snapshot.activeVideoCallCount} 个通话中")
    }
    return parts.joinToString(" · ").ifBlank { "暂时没有新的手机动态" }
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
    onOpenAssistantSettings: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenMemorySettings: () -> Unit,
    onOpenContextTransferSettings: () -> Unit,
    onSetDefaultUserPersonaMask: (String) -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
) {
    val masks = settings.normalizedUserPersonaMasks()
    val defaultMask = settings.resolvedDefaultUserPersonaMask()
    val profileDisplayName = defaultMask?.name ?: settings.resolvedUserDisplayName()
    val profileAvatarUri = defaultMask?.avatarUri ?: settings.userAvatarUri
    val profileAvatarUrl = defaultMask?.avatarUrl ?: settings.userAvatarUrl
    val profileScopeText = if (defaultMask != null) {
        "默认面具 · $chatCount 个会话 · $assistantCount 位角色"
    } else {
        "$chatCount 个会话 · $assistantCount 位角色"
    }
    val maskSummary = when {
        masks.isEmpty() -> "还没有默认面具，未单独绑定的会话会使用全局个人资料"
        defaultMask != null -> "未单独绑定的会话会使用这个身份"
        else -> "${masks.size} 个身份，未设置默认"
    }
    var masksExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp + bottomPadding),
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
                            displayName = profileDisplayName,
                            avatarUri = profileAvatarUri,
                            avatarUrl = profileAvatarUrl,
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profileDisplayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = profileScopeText,
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
        item { FeatureRow("会话管理", "查看、编辑和删除会话资料", Icons.AutoMirrored.Filled.Chat, onOpenChatManage) }
        item { FeatureRow("角色资料", "管理角色卡、提示词和扩展能力", Icons.Default.Person, onOpenAssistantSettings) }
        item { FeatureRow("世界书", "维护角色关系会用到的设定资料", Icons.AutoMirrored.Filled.LibraryBooks, onOpenWorldBookSettings) }
        item { FeatureRow("记忆档案", "查看长期记忆、摘要和关系线索", Icons.Default.AutoStories, onOpenMemorySettings) }
        item { FeatureRow("资料导入导出", "备份和迁移会话、角色与上下文资料", Icons.Default.CloudSync, onOpenContextTransferSettings) }
        item { FeatureRow("设置", "模型、显示、工具和应用更新", Icons.Default.Settings, onOpenSettings) }
        item { FeatureRow("关于", "Narra 角色手机", Icons.Default.Info) {} }
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
        masks.isEmpty() -> "还没有默认面具，先创建不同对话里的“我”"
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
                    Text("默认面具", fontWeight = FontWeight.SemiBold)
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
            Text("新建会话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    Text("已选择角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAssistant = null },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AssistantAvatar(
                                name = assistant.name,
                                iconName = assistant.iconName,
                                avatarUri = assistant.avatarUri,
                                size = 40.dp,
                                cornerRadius = 12.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = assistant.name.ifBlank { "未命名角色" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "点击这里重新选择角色",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "重新选择角色",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Text("选择会话模式", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun NewGroupChatSheet(
    assistants: List<Assistant>,
    onDismiss: () -> Unit,
    onCreateGroupChat: (String, List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf("") }
    var selectedAssistantIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedAssistants = assistants.filter { assistant -> assistant.id in selectedAssistantIds }
    val generatedTitle = selectedAssistants
        .take(3)
        .joinToString("、") { it.name.ifBlank { "角色" } }
        .ifBlank { "群聊" }
    val finalTitle = title.trim().ifBlank { "$generatedTitle 的群聊" }

    fun toggleAssistant(assistantId: String) {
        selectedAssistantIds = if (assistantId in selectedAssistantIds) {
            selectedAssistantIds - assistantId
        } else {
            selectedAssistantIds + assistantId
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("新建群聊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "选择至少 2 位角色，默认使用线上手机和自然会话。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("群聊名称") },
                placeholder = { Text(finalTitle) },
                singleLine = true,
            )
            Text(
                text = "群成员（${selectedAssistantIds.size}）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            if (assistants.isEmpty()) {
                Text("还没有角色，先去通讯录创建角色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(assistants, key = { it.id }) { assistant ->
                        val selected = assistant.id in selectedAssistantIds
                        GroupAssistantPickRow(
                            assistant = assistant,
                            selected = selected,
                            onClick = { toggleAssistant(assistant.id) },
                        )
                    }
                }
            }
            Button(
                onClick = { onCreateGroupChat(finalTitle, selectedAssistantIds) },
                enabled = selectedAssistantIds.size >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selectedAssistantIds.size >= 2) "创建群聊" else "至少选择 2 位角色")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GroupAssistantPickRow(
    assistant: Assistant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        leadingContent = {
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 46.dp,
                cornerRadius = 12.dp,
            )
        },
        headlineContent = {
            Text(
                text = assistant.name.ifBlank { "未命名角色" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = assistant.description.ifBlank { "自然会话候选成员" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = "已选择", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCardSheet(
    assistant: Assistant,
    summaries: List<RoleplayChatSummary>,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onCreateChat: (String, RoleplayInteractionMode, Boolean) -> Unit,
    onOpenAssistantDetail: (String) -> Unit,
    onOpenAssistantPrompt: (String) -> Unit,
    onOpenAssistantMemory: (String) -> Unit,
    onOpenAssistantWorldBook: (String) -> Unit,
    onOpenPhoneCheck: (String) -> Unit,
    onOpenMoments: (String) -> Unit,
    onOpenDiary: (String) -> Unit,
    onOpenMailbox: (String) -> Unit,
    onOpenWallet: (String) -> Unit,
    onOpenVideoCall: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showModePicker by remember { mutableStateOf(false) }
    val primarySummary = summaries.firstOrNull()
    val worldBookCount = assistant.linkedWorldBookIds.size + assistant.linkedWorldBookBookIds.size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistantAvatar(
                        name = assistant.name,
                        iconName = assistant.iconName,
                        avatarUri = assistant.avatarUri,
                        size = 68.dp,
                        cornerRadius = 18.dp,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(assistant.name.ifBlank { "未命名角色" }, style = MaterialTheme.typography.titleLarge)
                        Text(
                            primarySummary?.let { "最近互动 ${formatMessageTime(it.lastActiveAt)}" }
                                ?: "还没有会话",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContactMetricPill("${summaries.size}", "会话")
                    ContactMetricPill(if (assistant.memoryEnabled) "开" else "关", "长记忆")
                    ContactMetricPill("$worldBookCount", "世界书")
                    ContactMetricPill("${assistant.tags.size}", "标签")
                }
            }

            assistant.description.trim().takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (assistant.tags.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        assistant.tags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            primarySummary?.let { onOpenChat(it.scenario.id) } ?: run {
                                showModePicker = true
                            }
                        },
                    ) {
                        Text("发消息")
                    }
                    TextButton(onClick = { showModePicker = !showModePicker }) {
                        Text("新建会话")
                    }
                    TextButton(onClick = { onOpenAssistantDetail(assistant.id) }) {
                        Text("编辑资料")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContactActionChip("人设提示词", Icons.Default.AutoStories) {
                        onOpenAssistantPrompt(assistant.id)
                    }
                    ContactActionChip("记忆", Icons.Default.ManageAccounts) {
                        onOpenAssistantMemory(assistant.id)
                    }
                    ContactActionChip("世界书", Icons.AutoMirrored.Filled.LibraryBooks) {
                        onOpenAssistantWorldBook(assistant.id)
                    }
                }
            }

            primarySummary?.let { summary ->
                item {
                    Text("关系玩法", fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContactActionChip("写信", Icons.Default.Mail) { onOpenMailbox(summary.scenario.id) }
                        ContactActionChip("查手机", Icons.Default.PhoneAndroid) { onOpenPhoneCheck(summary.scenario.id) }
                        ContactActionChip("朋友圈", Icons.Default.Forum) { onOpenMoments(summary.scenario.id) }
                        ContactActionChip("日记", Icons.Default.Book) { onOpenDiary(summary.scenario.id) }
                        ContactActionChip("钱包", Icons.Default.AccountBalanceWallet) { onOpenWallet(summary.scenario.id) }
                        ContactActionChip("视频通话", Icons.Default.Videocam) { onOpenVideoCall(summary.scenario.id) }
                    }
                }
            }

            if (showModePicker) {
                items(ModeOptions, key = { it.title }) { option ->
                    ModeOptionCard(
                        option = option,
                        onClick = { onCreateChat(assistant.id, option.interactionMode, option.enableNarration) },
                    )
                }
            }
            if (summaries.isNotEmpty()) {
                item {
                    Text("最近互动", fontWeight = FontWeight.SemiBold)
                }
                items(summaries, key = { it.scenario.id }) { summary ->
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
                        trailingContent = {
                            Text(
                                text = summary.scenario.interactionMode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactMetricPill(
    value: String,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContactActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
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
            Text("选择会话 · $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summaries.isEmpty()) {
                Text("还没有可用会话。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        summary.hasHistory -> "最近有会话记录"
        summary.hasSession -> "最近没有消息"
        else -> "还没有开始会话"
    }
}

private fun sanitizeChatSummaryPreview(rawContent: String): String {
    return rawContent
        .replace(Regex("""(?is)<\s*(?:script|style)\b[^>]*>.*?<\s*/\s*(?:script|style)\s*>"""), " ")
        .replace(Regex("""(?is)<\s*br\s*/?\s*>"""), " ")
        .replace(Regex("""(?is)</?\s*(?:p|div|span|content)\b[^>]*>"""), " ")
        .replace(Regex("""(?is)<[^>]+>"""), " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
