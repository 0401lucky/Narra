package com.example.myapplication.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.BuildConfig
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.ui.component.AppUpdateDialogHost
import com.example.myapplication.ui.navigation.AppNavHost
import com.example.myapplication.ui.theme.ChatAppTheme
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel

@Composable
fun AppRoot(
    appGraph: AppGraph,
) {
    val navController = rememberNavController()
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
                    appGraph = appGraph,
                    navController = navController,
                    settingsViewModel = settingsViewModel,
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
