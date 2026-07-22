package com.example.myapplication.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.BuildConfig
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.component.AppUpdateDialogHost
import com.example.myapplication.ui.navigation.AppNavHost
import com.example.myapplication.ui.navigation.AppRoutes
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.ComplianceViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.ui.screen.compliance.ComplianceScreen
import com.example.myapplication.ui.screen.compliance.ComplianceScreenMode

@Composable
fun AppRoot(
    appGraph: AppGraph,
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val complianceViewModel: ComplianceViewModel = viewModel(
        factory = ComplianceViewModel.factory(
            consentRepository = appGraph.complianceConsentStore,
        ),
    )
    val complianceUiState by complianceViewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by produceState<com.example.myapplication.model.AppSettings?>(
        initialValue = null,
        appGraph.aiSettingsRepository,
    ) {
        appGraph.aiSettingsRepository.settingsFlow.collect { value = it }
    }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = appGraph.aiSettingsRepository,
            settingsEditor = appGraph.aiSettingsEditor,
            modelCatalogRepository = appGraph.aiModelCatalogRepository,
            imageFileCleaner = appGraph.localImageStore::deleteIfLocalAsync,
            mimoTtsClient = appGraph.mimoTtsClient,
        ),
    )
    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        factory = AppUpdateViewModel.factory(
            repository = appGraph.appUpdateRepository,
            downloadController = appGraph.appUpdateDownloadController,
            environment = AppUpdateEnvironment(
                appId = BuildConfig.APPLICATION_ID,
                channel = BuildConfig.APP_CHANNEL,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                metadataBaseUrl = BuildConfig.APP_UPDATE_METADATA_BASE_URL,
            ),
        ),
    )
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val resolvedDarkTheme = when ((appSettings?.let { settingsUiState.themeMode } ?: ThemeMode.SYSTEM)) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(complianceUiState.isAccepted) {
        if (complianceUiState.isAccepted) {
            appUpdateViewModel.onAppStarted()
            appGraph.scheduleConsentDependentTasks()
        }
    }

    ChatAppTheme(
        appColorTheme = appSettings?.let { settingsUiState.appColorTheme } ?: AppColorTheme.MATCHA,
        darkTheme = resolvedDarkTheme ?: androidx.compose.foundation.isSystemInDarkTheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                complianceUiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                !complianceUiState.isAccepted -> {
                    ComplianceScreen(
                        uiState = complianceUiState,
                        mode = ComplianceScreenMode.GATE,
                        onAccept = complianceViewModel::acceptCurrentPolicy,
                        onExit = onExit,
                        onRetry = complianceViewModel::retryObservation,
                        onClearErrorMessage = complianceViewModel::clearErrorMessage,
                    )
                }

                appSettings == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                else -> {
                    CompositionLocalProvider(LocalImagePersister provides appGraph.localImageStore) {
                        AppNavHost(
                            appGraph = appGraph,
                            navController = navController,
                            settingsViewModel = settingsViewModel,
                            appUpdateViewModel = appUpdateViewModel,
                            complianceViewModel = complianceViewModel,
                            startDestination = AppRoutes.ROLEPLAY_GRAPH,
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
    }
}
