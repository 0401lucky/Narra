package com.example.myapplication.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.BuildConfig
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.system.update.AppUpdateDownloadController
import com.example.myapplication.ui.component.AppUpdateDialogHost
import com.example.myapplication.ui.navigation.AppNavHost
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MemoryManagementViewModel
import com.example.myapplication.viewmodel.RoleplayViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.TranslationViewModel
import com.example.myapplication.viewmodel.WorldBookViewModel

@Composable
fun AppRoot(
    repository: AiRepository,
    conversationRepository: ConversationRepository,
    worldBookRepository: WorldBookRepository,
    memoryRepository: MemoryRepository,
    conversationSummaryRepository: ConversationSummaryRepository,
    promptContextAssembler: PromptContextAssembler,
    roleplayRepository: RoleplayRepository,
    appUpdateRepository: AppUpdateRepository,
    appUpdateDownloadController: AppUpdateDownloadController,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appSettings by produceState<com.example.myapplication.model.AppSettings?>(
        initialValue = null,
        repository,
    ) {
        repository.settingsFlow.collect { value = it }
    }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(repository),
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            repository = repository,
            conversationRepository = conversationRepository,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            promptContextAssembler = promptContextAssembler,
            imageSaver = { b64Data ->
                ImageFileStorage.saveBase64Image(context, b64Data)
            },
        ),
    )
    val translationViewModel: TranslationViewModel = viewModel(
        factory = TranslationViewModel.factory(repository),
    )
    val worldBookViewModel: WorldBookViewModel = viewModel(
        factory = WorldBookViewModel.factory(worldBookRepository),
    )
    val contextTransferViewModel: ContextTransferViewModel = viewModel(
        factory = ContextTransferViewModel.factory(
            repository = repository,
            worldBookRepository = worldBookRepository,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            importedAssistantAvatarSaver = { importedAvatar ->
                ImageFileStorage.saveImageBytes(
                    context = context,
                    bytes = importedAvatar.bytes,
                    fileNamePrefix = "assistant-avatar-${importedAvatar.assistantId}",
                ).path
            },
        ),
    )
    val memoryManagementViewModel: MemoryManagementViewModel = viewModel(
        factory = MemoryManagementViewModel.factory(
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
        ),
    )
    val roleplayViewModel: RoleplayViewModel = viewModel(
        factory = RoleplayViewModel.factory(
            repository = repository,
            conversationRepository = conversationRepository,
            roleplayRepository = roleplayRepository,
            promptContextAssembler = promptContextAssembler,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
        ),
    )
    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        factory = AppUpdateViewModel.factory(
            repository = appUpdateRepository,
            downloadController = appUpdateDownloadController,
            environment = AppUpdateEnvironment(
                appId = BuildConfig.APPLICATION_ID,
                channel = BuildConfig.APP_CHANNEL,
                versionName = BuildConfig.APP_VERSION_NAME_VALUE,
                versionCode = BuildConfig.APP_VERSION_CODE_VALUE,
                metadataBaseUrl = BuildConfig.APP_UPDATE_METADATA_BASE_URL,
            ),
        ),
    )
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()

    val resolvedDarkTheme = when (appSettings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.onAppStarted()
    }

    ChatAppTheme(
        darkTheme = resolvedDarkTheme ?: androidx.compose.foundation.isSystemInDarkTheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (appSettings != null) {
                AppNavHost(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    chatViewModel = chatViewModel,
                    translationViewModel = translationViewModel,
                    worldBookViewModel = worldBookViewModel,
                    contextTransferViewModel = contextTransferViewModel,
                    memoryManagementViewModel = memoryManagementViewModel,
                    roleplayViewModel = roleplayViewModel,
                    appUpdateViewModel = appUpdateViewModel,
                    startDestination = com.example.myapplication.ui.navigation.AppRoutes.CHAT,
                )
                AppUpdateDialogHost(
                    uiState = appUpdateState,
                    onDismiss = appUpdateViewModel::dismissDialog,
                    onDownload = appUpdateViewModel::startUpdateDownload,
                    onInstall = appUpdateViewModel::installDownloadedUpdate,
                )
            }
        }
    }
}
