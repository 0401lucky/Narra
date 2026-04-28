package com.example.myapplication.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.USER_PROFILE_PERSONA_MASK_ID
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.model.normalized
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.ui.component.UserProfileAvatar
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPersonaMasksScreen(
    settings: AppSettings,
    onUpsertMask: (UserPersonaMask) -> Unit,
    onDeleteMask: (String) -> Unit,
    onSetDefaultMask: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val masks = settings.normalizedUserPersonaMasks()
    val defaultMaskId = settings.resolvedDefaultUserPersonaMask()?.id.orEmpty()
    var editingMask by remember { mutableStateOf<UserPersonaMask?>(null) }
    var deletingMask by remember { mutableStateOf<UserPersonaMask?>(null) }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "我的面具",
                subtitle = "管理不同对话里的“我”",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = {
                    val now = System.currentTimeMillis()
                    editingMask = UserPersonaMask(
                        id = UUID.randomUUID().toString(),
                        name = settings.resolvedUserDisplayName(),
                        avatarUri = settings.userAvatarUri,
                        avatarUrl = settings.userAvatarUrl,
                        personaPrompt = settings.userPersonaPrompt,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = "身份库",
                    summary = "每个面具都包含昵称、头像和用户人设。角色场景绑定面具后，聊天展示、提示词、日记和手机内容都会使用这套身份。",
                )
            }
            item {
                SettingsSectionHeader(
                    title = "面具列表",
                    description = "默认面具会在没有单独绑定时自动生效。",
                )
            }
            item {
                SettingsGroup {
                    if (masks.isEmpty()) {
                        SettingsPlaceholderRow(
                            title = "还没有面具",
                            subtitle = "点右上角新增，会先用当前全局个人资料生成一张面具草稿。",
                        )
                    } else {
                        masks.forEachIndexed { index, mask ->
                            PersonaMaskRow(
                                mask = mask,
                                isDefault = mask.id == defaultMaskId,
                                onEdit = { editingMask = mask },
                                canDelete = mask.id != USER_PROFILE_PERSONA_MASK_ID,
                                onDelete = { deletingMask = mask },
                                onSetDefault = { onSetDefaultMask(mask.id) },
                            )
                            if (index != masks.lastIndex) {
                                SettingsGroupDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    editingMask?.let { mask ->
        PersonaMaskEditorSheet(
            initialMask = mask,
            onDismiss = { editingMask = null },
            onSave = { updated ->
                editingMask = null
                onUpsertMask(updated)
            },
        )
    }

    deletingMask?.let { mask ->
        AlertDialog(
            onDismissRequest = { deletingMask = null },
            title = { Text("删除面具") },
            text = { Text("删除后，已绑定这个面具的聊天会回退到默认面具或全局个人资料。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMask = null
                        onDeleteMask(mask.id)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMask = null }) {
                    Text("取消")
                }
            },
            containerColor = palette.surface,
            titleContentColor = palette.title,
            textContentColor = palette.body,
        )
    }
}

@Composable
private fun PersonaMaskRow(
    mask: UserPersonaMask,
    isDefault: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit),
        color = if (isDefault) palette.accentSoft.copy(alpha = 0.24f) else palette.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UserProfileAvatar(
                displayName = mask.name,
                avatarUri = mask.avatarUri,
                avatarUrl = mask.avatarUrl,
                modifier = Modifier.size(48.dp),
                containerColor = palette.subtleChip,
                contentColor = palette.subtleChipContent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = mask.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isDefault) {
                        SettingsStatusPill(
                            text = "默认",
                            containerColor = palette.accentSoft,
                            contentColor = palette.accent,
                        )
                    }
                    if (mask.id == USER_PROFILE_PERSONA_MASK_ID) {
                        SettingsStatusPill(
                            text = "基础",
                            containerColor = palette.subtleChip,
                            contentColor = palette.body,
                        )
                    }
                }
                Text(
                    text = mask.personaPrompt.ifBlank { mask.note }.ifBlank { "未填写人设" },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = palette.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑面具", tint = palette.title)
                }
                Row {
                    IconButton(onClick = onSetDefault, enabled = !isDefault) {
                        Icon(Icons.Default.Check, contentDescription = "设为默认", tint = palette.body)
                    }
                    IconButton(onClick = onDelete, enabled = canDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除面具", tint = palette.body)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaMaskEditorSheet(
    initialMask: UserPersonaMask,
    onDismiss: () -> Unit,
    onSave: (UserPersonaMask) -> Unit,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val localImageStore = LocalImagePersister.current
    val coroutineScope = rememberCoroutineScope()
    var name by rememberSaveable(initialMask.id) { mutableStateOf(initialMask.name) }
    var avatarUri by rememberSaveable(initialMask.id) { mutableStateOf(initialMask.avatarUri) }
    var avatarUrl by rememberSaveable(initialMask.id) { mutableStateOf(initialMask.avatarUrl) }
    var personaPrompt by rememberSaveable(initialMask.id) { mutableStateOf(initialMask.personaPrompt) }
    var note by rememberSaveable(initialMask.id) { mutableStateOf(initialMask.note) }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            localImageStore.copyToAppStorage(uri, PERSONA_MASK_AVATAR_SCOPE)?.let {
                avatarUri = it
                avatarUrl = ""
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                end = SettingsScreenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "编辑面具",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = palette.surface,
                    border = BorderStroke(0.75.dp, palette.border),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        UserProfileAvatar(
                            displayName = name,
                            avatarUri = avatarUri,
                            avatarUrl = avatarUrl,
                            modifier = Modifier.size(72.dp),
                            containerColor = palette.subtleChip,
                            contentColor = palette.subtleChipContent,
                        )
                        NarraFilledTonalButton(
                            onClick = {
                                avatarLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("选择头像")
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("面具昵称") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = outlineColors,
                )
            }
            item {
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = {
                        avatarUrl = it
                        if (it.isNotBlank()) avatarUri = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("头像链接") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = outlineColors,
                )
            }
            item {
                OutlinedTextField(
                    value = personaPrompt,
                    onValueChange = { personaPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户人设") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(18.dp),
                    colors = outlineColors,
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = outlineColors,
                )
            }
            item {
                NarraFilledTonalButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onSave(
                            initialMask.copy(
                                name = name,
                                avatarUri = avatarUri,
                                avatarUrl = avatarUrl,
                                personaPrompt = personaPrompt,
                                note = note,
                                updatedAt = now,
                            ).normalized(now),
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("保存面具")
                }
            }
        }
    }
}

private const val PERSONA_MASK_AVATAR_SCOPE = "personaMaskAvatar"
