package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.ui.screen.home.HomeScreen
import com.example.myapplication.ui.screen.moments.MomentsScreen
import com.example.myapplication.ui.screen.phone.PhoneCheckScreen
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

        composable(AppRoutes.PHONE_CHECK) { backStackEntry ->
            val phoneCheckViewModel = rememberPhoneCheckViewModel(
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val uiState by phoneCheckViewModel.uiState.collectAsStateWithLifecycle()
            PhoneCheckScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onGenerateSnapshot = phoneCheckViewModel::generateSnapshot,
                onRefreshSections = phoneCheckViewModel::refreshSections,
                onLoadSearchDetail = phoneCheckViewModel::loadSearchDetail,
                onClearErrorMessage = phoneCheckViewModel::clearErrorMessage,
                onClearNoticeMessage = phoneCheckViewModel::clearNoticeMessage,
            )
        }

        composable(AppRoutes.MOMENTS) { backStackEntry ->
            val momentsViewModel = rememberMomentsViewModel(
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val uiState by momentsViewModel.uiState.collectAsStateWithLifecycle()
            val conversationId = Uri.decode(backStackEntry.arguments?.getString("conversationId").orEmpty())
            val scenarioId = Uri.decode(backStackEntry.arguments?.getString("scenarioId").orEmpty())
            val ownerType = PhoneSnapshotOwnerType.fromStorageValue(
                backStackEntry.arguments?.getString("ownerType").orEmpty(),
            )
            MomentsScreen(
                uiState = uiState,
                viewerName = uiState.viewerName,
                onNavigateBack = { navController.popBackStack() },
                onToggleLikePost = momentsViewModel::toggleLikePost,
                onAddComment = momentsViewModel::addCommentToPost,
                onClearErrorMessage = momentsViewModel::clearErrorMessage,
                onOpenPhoneCheck = {
                    navController.navigate(
                        AppRoutes.phoneCheck(
                            conversationId = conversationId,
                            scenarioId = scenarioId,
                            ownerType = ownerType,
                        ),
                    ) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
