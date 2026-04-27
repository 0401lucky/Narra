package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.model.Assistant
import com.example.myapplication.viewmodel.ChatUiState
import com.example.myapplication.ui.component.AppSnackbarHost

@Composable
internal fun ChatScreenChrome(
    uiState: ChatUiState,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    currentProviderName: String,
    currentModel: String,
    userDisplayName: String,
    userAvatarUri: String,
    userAvatarUrl: String,
    currentAssistant: Assistant?,
    onSelectAssistant: (String) -> Unit,
    onOpenAssistantDetail: (String) -> Unit,
    onCreateConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onClearConversation: (String) -> Unit,
    onClearCurrentConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenRoleplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenConversationDrawer: () -> Unit,
    onOpenContextLog: () -> Unit,
    onOpenExportSheet: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                currentProviderName = currentProviderName,
                currentModel = currentModel,
                userDisplayName = userDisplayName,
                userAvatarUri = userAvatarUri,
                userAvatarUrl = userAvatarUrl,
                currentAssistant = currentAssistant,
                assistants = uiState.settings.resolvedAssistants(),
                onSelectAssistant = onSelectAssistant,
                onOpenAssistantDetail = onOpenAssistantDetail,
                onCreateConversation = onCreateConversation,
                onSelectConversation = onSelectConversation,
                onClearConversation = onClearConversation,
                onClearCurrentConversation = onClearCurrentConversation,
                onDeleteConversation = onDeleteConversation,
                onOpenTranslator = onOpenTranslator,
                onOpenRoleplay = onOpenRoleplay,
                onOpenSettings = onOpenSettings,
                onEditProfile = onEditProfile,
            )
        },
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = uiState.currentConversationTitle,
                    onOpenConversationDrawer = onOpenConversationDrawer,
                    onOpenContextLog = onOpenContextLog,
                    onOpenExportSheet = onOpenExportSheet,
                    onCreateConversation = onCreateConversation,
                )
            },
            snackbarHost = {
                AppSnackbarHost(hostState = snackbarHostState)
            },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
            )
        }
    }
}
