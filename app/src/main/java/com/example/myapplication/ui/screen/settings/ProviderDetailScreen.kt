package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
fun ProviderDetailScreen(
    providerId: String,
    uiState: SettingsUiState,
    onUpdateProviderName: (String, String) -> Unit,
    onUpdateProviderBaseUrl: (String, String) -> Unit,
    onUpdateProviderApiKey: (String, String) -> Unit,
    onUpdateProviderApiProtocol: (String, ProviderApiProtocol) -> Unit,
    onUpdateProviderOpenAiTextApiMode: (String, com.example.myapplication.model.OpenAiTextApiMode) -> Unit,
    onUpdateProviderChatCompletionsPath: (String, String) -> Unit,
    onUpdateProviderSelectedModel: (String, String) -> Unit,
    onUpdateProviderModelAbilities: (String, String, Set<ModelAbility>?) -> Unit,
    onLoadModels: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onToggleProviderEnabled: (String) -> Unit,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmFetchedModels: (String, Set<String>) -> Unit,
    onDismissFetchedModels: () -> Unit,
    onRemoveModel: (String, String) -> Unit,
    onClearAllModels: (String) -> Unit,
    onAddManualModel: (String, String, String) -> Unit,
) {
    BackHandler {
        onNavigateBack()
    }

    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val provider = uiState.providers.firstOrNull { it.id == providerId }

    if (provider == null) {
        Scaffold(
            topBar = { SettingsTopBar(title = "未找到", onNavigateBack = onNavigateBack) },
            containerColor = palette.background,
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
                SettingsNoticeCard(
                    title = "没有找到这个提供商",
                    body = "这个草稿可能已经被删除，返回上一页后重新选择一个可用的提供商。",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        return
    }

    var selectedTab by rememberSaveable(providerId) { mutableIntStateOf(0) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var modelActionsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            ProviderDetailTopBar(provider = provider, onNavigateBack = onNavigateBack)
        },
        bottomBar = {
            SleekBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab == 1,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                val canLoadModels = !uiState.isLoadingModels && provider.hasBaseCredentials()
                val isFetching = uiState.isLoadingModels && uiState.loadingProviderId == provider.id
                val modelCount = provider.resolvedModels().size
                Surface(
                    shape = NarraShapes.medium,
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.3f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = NarraUi.space8)
                        .onSizeChanged { modelActionsHeightPx = it.height },
                ) {
                    Row(
                        modifier = Modifier.padding(NarraUi.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(NarraShapes.small)
                                .sizeIn(
                                    minWidth = NarraMinimumTouchTarget,
                                    minHeight = NarraMinimumTouchTarget
                                )
                                .semantics {
                                    contentDescription = "获取模型"
                                    stateDescription = when {
                                        isFetching -> "正在获取模型"
                                        !provider.hasBaseCredentials() -> "请先填写连接信息"
                                        uiState.isLoadingModels -> "正在获取其他提供商的模型"
                                        else -> "已有 $modelCount 个模型"
                                    }
                                    if (isFetching) progressBarRangeInfo =
                                        ProgressBarRangeInfo.Indeterminate
                                }
                                .clickable(
                                    enabled = canLoadModels && !isFetching,
                                    role = Role.Button,
                                    onClick = { onLoadModels(provider.id) },
                                )
                                .padding(horizontal = NarraUi.space8, vertical = NarraUi.space8),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = palette.accentStrong,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (canLoadModels) palette.title else palette.body,
                                )
                            }
                            Text(
                                text = modelCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.body
                            )
                        }

                        NarraButton(
                            onClick = { showManualAddDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = NarraShapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentStrong,
                                contentColor = palette.accentOnStrong
                            ),
                            contentPadding = PaddingValues(
                                horizontal = NarraUi.space12,
                                vertical = NarraUi.space12
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "添加新模型",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Crossfade(
                targetState = selectedTab,
                label = "tab_crossfade",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> ConfigTabContent(
                        provider = provider,
                        isSaving = uiState.isSaving,
                        providerCount = uiState.providers.size,
                        onUpdateProviderName = onUpdateProviderName,
                        onUpdateProviderBaseUrl = onUpdateProviderBaseUrl,
                        onUpdateProviderApiKey = onUpdateProviderApiKey,
                        onUpdateProviderApiProtocol = onUpdateProviderApiProtocol,
                        onUpdateProviderOpenAiTextApiMode = onUpdateProviderOpenAiTextApiMode,
                        onUpdateProviderChatCompletionsPath = onUpdateProviderChatCompletionsPath,
                        onToggleProviderEnabled = onToggleProviderEnabled,
                        onSave = onSave,
                        onDeleteProvider = onDeleteProvider,
                        onNavigateBack = onNavigateBack,
                    )

                    1 -> ModelTabContent(
                        provider = provider,
                        uiState = uiState,
                        onUpdateProviderSelectedModel = onUpdateProviderSelectedModel,
                        onUpdateProviderModelAbilities = onUpdateProviderModelAbilities,
                        onRemoveModel = onRemoveModel,
                        onClearAllModels = onClearAllModels,
                        onConfirmFetchedModels = onConfirmFetchedModels,
                        onDismissFetchedModels = onDismissFetchedModels,
                        bottomContentPadding = with(density) { modelActionsHeightPx.toDp() } + NarraUi.space24,
                    )
                }
            }
            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }

    if (showManualAddDialog) {
        ManualAddModelDialog(
            onDismissRequest = { showManualAddDialog = false },
            onConfirm = { modelId, displayName ->
                onAddManualModel(provider.id, modelId, displayName)
            }
        )
    }
}
