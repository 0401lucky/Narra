package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.brandColor
import com.example.myapplication.model.sortedForModelListDisplay
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.viewmodel.SettingsUiState

@Composable
internal fun ModelTabContent(
    provider: ProviderSettings,
    uiState: SettingsUiState,
    onUpdateProviderSelectedModel: (String, String) -> Unit,
    onUpdateProviderModelAbilities: (String, String, Set<ModelAbility>?) -> Unit,
    onRemoveModel: (String, String) -> Unit,
    onConfirmFetchedModels: (String, Set<String>) -> Unit,
    onDismissFetchedModels: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val modelInfos = provider.resolvedModels().sortedForModelListDisplay()
    var editingModelInfo by remember(provider.id) { mutableStateOf<ModelInfo?>(null) }
    val showFetchedDialog = uiState.pendingFetchedModels.isNotEmpty() &&
        uiState.pendingFetchProviderId == provider.id

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (modelInfos.isEmpty()) {
                item {
                    SettingsPlaceholderRow(
                        title = "暂无模型",
                        subtitle = if (provider.hasBaseCredentials()) "点击右下角浮动按钮获取模型，然后选择要添加的。" else "先在配置页补全信息，再来获取。",
                    )
                }
            } else {
                items(modelInfos, key = { it.modelId }) { modelInfo ->
                    PremiumModelCard(
                        modelInfo = modelInfo,
                        isSelected = modelInfo.modelId == provider.selectedModel,
                        brandColor = provider.resolvedType().brandColor(isSystemInDarkTheme()),
                        onClick = { onUpdateProviderSelectedModel(provider.id, modelInfo.modelId) },
                        onEditAbilities = { editingModelInfo = modelInfo },
                        onRemove = { onRemoveModel(provider.id, modelInfo.modelId) },
                    )
                }
            }
        }
    }

    editingModelInfo?.let { modelInfo ->
        ModelAbilityOverrideDialog(
            modelInfo = modelInfo,
            onDismissRequest = { editingModelInfo = null },
            onSave = { abilities ->
                onUpdateProviderModelAbilities(provider.id, modelInfo.modelId, abilities)
                editingModelInfo = null
            },
        )
    }

    if (showFetchedDialog) {
        FetchedModelSelectionBottomSheet(
            fetchedModels = uiState.pendingFetchedModels,
            existingModelIds = modelInfos.map { it.modelId }.toSet(),
            onConfirm = { selectedIds ->
                onConfirmFetchedModels(provider.id, selectedIds)
            },
            onDismiss = onDismissFetchedModels,
        )
    }
}

