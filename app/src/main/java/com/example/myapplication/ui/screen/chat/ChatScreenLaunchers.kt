package com.example.myapplication.ui.screen.chat

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.example.myapplication.R
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.viewmodel.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ChatScreen launcher 聚合：把 5 个 rememberLauncherForActivityResult + 3 个本地 helper
// (handlePickedAttachment / saveProfileDraft / primeSpecialPlayDraft / resetSpecialPlayDraft) 全部搬进
// 同包的 remember 函数。对外暴露一个 @Immutable 持有者，包装成 `() -> Unit` / `(T) -> Unit` 形态。
// 说明：data class 字段含 Compose 识别为 stable 的函数引用，与既有 ChatScreenLocalState 同模式。
@Immutable
internal data class ChatScreenLaunchers(
    val pickImages: () -> Unit,
    val pickAvatar: () -> Unit,
    val pickFile: () -> Unit,
    val exportMarkdown: () -> Unit,
    val exportMessageMarkdown: (String) -> Unit,
    val saveProfileDraft: () -> Unit,
    val primeSpecialPlayDraft: (ChatSpecialType) -> Unit,
    val resetSpecialPlayDraft: (ChatSpecialType) -> Unit,
)

@Composable
internal fun rememberChatScreenLaunchers(
    context: Context,
    scope: CoroutineScope,
    resources: Resources,
    localState: ChatScreenLocalState,
    uiState: ChatUiState,
    snackbarHostState: SnackbarHostState,
    currentAssistantName: String,
    onAddPendingParts: (List<ChatMessagePart>) -> Unit,
    onSaveUserProfile: (String, String, String, String) -> Unit,
): ChatScreenLaunchers {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val attachments = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            runCatching {
                resolveSelectedAttachment(context, uri, AttachmentType.IMAGE)
            }.getOrNull()
        }
        if (attachments.isNotEmpty()) {
            onAddPendingParts(attachments.map(MessageAttachment::toChatMessagePart))
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        localState.setDraftUserAvatarUri(uri.toString())
        localState.setDraftUserAvatarUrl("")
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        handlePickedAttachment(
            context = context,
            scope = scope,
            resources = resources,
            snackbarHostState = snackbarHostState,
            onAddPendingParts = onAddPendingParts,
            uri = uri,
            type = AttachmentType.FILE,
        )
    }

    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val markdown = buildConversationMarkdown(
            title = uiState.currentConversationTitle,
            messages = uiState.messages,
            options = localState.exportOptions,
        )
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(markdown.toByteArray(Charsets.UTF_8))
            } ?: error(resources.getString(R.string.chat_error_write_export))
        }.onSuccess {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.chat_export_msg_md_success)) }
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    throwable.message ?: resources.getString(R.string.chat_error_export_md_fail),
                )
            }
        }
    }

    val messageMarkdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val payload = localState.pendingMessageExport
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.markdown.toByteArray(Charsets.UTF_8))
                } ?: error(resources.getString(R.string.chat_error_write_export))
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.chat_export_msg_md_success)) }
            }.onFailure { throwable ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        throwable.message ?: resources.getString(R.string.chat_error_export_msg_md_fail),
                    )
                }
            }
        }
        localState.setPendingMessageExport(null)
    }

    return ChatScreenLaunchers(
        pickImages = { imagePickerLauncher.launch(arrayOf("image/*")) },
        pickAvatar = { avatarPickerLauncher.launch(arrayOf("image/*")) },
        pickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        exportMarkdown = {
            exportMarkdownLauncher.launch(buildExportFileName(uiState.currentConversationTitle))
        },
        exportMessageMarkdown = { fileName -> messageMarkdownExportLauncher.launch(fileName) },
        saveProfileDraft = {
            runSaveProfileDraft(
                scope = scope,
                resources = resources,
                localState = localState,
                snackbarHostState = snackbarHostState,
                onSaveUserProfile = onSaveUserProfile,
            )
        },
        primeSpecialPlayDraft = { type ->
            primeSpecialPlayDraftInternal(type, localState, currentAssistantName)
        },
        resetSpecialPlayDraft = { type ->
            resetSpecialPlayDraftInternal(type, localState, currentAssistantName)
        },
    )
}

private fun handlePickedAttachment(
    context: Context,
    scope: CoroutineScope,
    resources: Resources,
    snackbarHostState: SnackbarHostState,
    onAddPendingParts: (List<ChatMessagePart>) -> Unit,
    uri: Uri,
    type: AttachmentType,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    runCatching {
        resolveSelectedAttachment(context, uri, type)
    }.onSuccess { attachment ->
        onAddPendingParts(listOf(attachment.toChatMessagePart()))
    }.onFailure { throwable ->
        scope.launch {
            snackbarHostState.showSnackbar(
                throwable.message ?: resources.getString(R.string.chat_error_read_attachment),
            )
        }
    }
}

private fun runSaveProfileDraft(
    scope: CoroutineScope,
    resources: Resources,
    localState: ChatScreenLocalState,
    snackbarHostState: SnackbarHostState,
    onSaveUserProfile: (String, String, String, String) -> Unit,
) {
    val normalizedName = localState.draftUserDisplayName.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME }
    val normalizedPersonaPrompt = localState.draftUserPersonaPrompt
        .replace("\r\n", "\n")
        .trim()
    val normalizedUrl = localState.draftUserAvatarUrl.trim()
    if (normalizedUrl.isNotBlank() && !isSupportedAvatarUrl(normalizedUrl)) {
        scope.launch {
            snackbarHostState.showSnackbar(resources.getString(R.string.chat_error_avatar_url_http))
        }
        return
    }
    val normalizedUri = if (normalizedUrl.isBlank()) localState.draftUserAvatarUri.trim() else ""
    onSaveUserProfile(normalizedName, normalizedPersonaPrompt, normalizedUri, normalizedUrl)
    localState.setShowProfileSheet(false)
}

private fun primeSpecialPlayDraftInternal(
    type: ChatSpecialType,
    localState: ChatScreenLocalState,
    currentAssistantName: String,
) {
    when (type) {
        ChatSpecialType.TRANSFER -> {
            if (localState.transferDraft.counterparty.isBlank()) {
                localState.setTransferDraft(localState.transferDraft.copy(counterparty = currentAssistantName))
            }
        }
        ChatSpecialType.INVITE -> {
            if (localState.inviteDraft.target.isBlank()) {
                localState.setInviteDraft(localState.inviteDraft.copy(target = currentAssistantName))
            }
        }
        ChatSpecialType.GIFT -> {
            if (localState.giftDraft.target.isBlank()) {
                localState.setGiftDraft(localState.giftDraft.copy(target = currentAssistantName))
            }
        }
        ChatSpecialType.TASK,
        ChatSpecialType.PUNISH,
        -> Unit
    }
}

private fun resetSpecialPlayDraftInternal(
    type: ChatSpecialType,
    localState: ChatScreenLocalState,
    currentAssistantName: String,
) {
    when (type) {
        ChatSpecialType.TRANSFER -> localState.setTransferDraft(
            TransferPlayDraft(counterparty = currentAssistantName),
        )
        ChatSpecialType.INVITE -> localState.setInviteDraft(
            InvitePlayDraft(target = currentAssistantName),
        )
        ChatSpecialType.GIFT -> localState.setGiftDraft(
            GiftPlayDraft(target = currentAssistantName),
        )
        ChatSpecialType.TASK -> localState.setTaskDraft(TaskPlayDraft())
        ChatSpecialType.PUNISH -> localState.setPunishDraft(PunishPlayDraft())
    }
}
