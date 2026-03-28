package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.home.HomeScreen
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel

@Composable
fun AppNavHost(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(AppRoutes.HOME) {
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                storedSettings = settingsUiState.savedSettings,
                onOpenChat = {
                    if (settingsUiState.savedSettings.hasRequiredConfig()) {
                        navController.navigate(AppRoutes.CHAT) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                onOpenRoleplay = {
                    navController.navigate(AppRoutes.ROLEPLAY) {
                        launchSingleTop = true
                    }
                },
            )
        }

        registerRoleplayGraph(
            appGraph = appGraph,
            navController = navController,
            settingsViewModel = settingsViewModel,
        )

        registerSettingsNavGraph(
            appGraph = appGraph,
            navController = navController,
            settingsViewModel = settingsViewModel,
            appUpdateViewModel = appUpdateViewModel,
        )

        registerChatNavGraph(
            appGraph = appGraph,
            navController = navController,
            settingsViewModel = settingsViewModel,
        )
    }
}
