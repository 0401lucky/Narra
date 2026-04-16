package com.example.myapplication.ui.screen.settings

import androidx.activity.compose.BackHandler
import com.example.myapplication.ui.component.*

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.component.ModelIcon
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
                    shape = RoundedCornerShape(32.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border.copy(alpha=0.3f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = "Models", Modifier.size(24.dp), tint = palette.title)
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp),
                                shape = CircleShape,
                                color = palette.accentStrong,
                                contentColor = palette.accentOnStrong
                            ) {
                                Text(
                                    text = modelCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        NarraButton(
                            onClick = { if (canLoadModels && !isFetching) onLoadModels(provider.id) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong, contentColor = palette.accentOnStrong),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp,0.dp,0.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (isFetching) "获取中..." else "添加新模型", 
                                    style = MaterialTheme.typography.titleSmall, 
                                    fontWeight = FontWeight.Bold 
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Crossfade(targetState = selectedTab, label = "tab_crossfade", modifier = Modifier.fillMaxSize()) { tab ->
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
                        onConfirmFetchedModels = onConfirmFetchedModels,
                        onDismissFetchedModels = onDismissFetchedModels,
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
}

