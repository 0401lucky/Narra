package com.example.myapplication.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Smartphone
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    storedSettings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenRoleplay: () -> Unit,
) {
    val hasRequiredConfig = storedSettings.hasRequiredConfig()
    val activeProvider = storedSettings.activeProvider()
    val assistantCount = storedSettings.resolvedAssistants().size
    val providerLabel = activeProvider?.name?.ifBlank { "未命名提供商" }
        ?: if (storedSettings.hasBaseCredentials()) "旧版连接兜底" else "还没有提供商"
    val modelLabel = activeProvider?.selectedModel?.takeIf { it.isNotBlank() }
        ?: storedSettings.selectedModel.ifBlank { "未选择模型" }

    Scaffold(
        modifier = Modifier.testTag("home_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.home_top_bar_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HomeContentMaxWidth)
                    .fillMaxSize()
                    .testTag("home_content")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.pageVertical),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = stringResource(
                        id = if (hasRequiredConfig) {
                            R.string.home_ready_subtitle
                        } else {
                            R.string.home_missing_config_subtitle
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HomePhoneEntryCard(
                    assistantCount = assistantCount,
                    hasRequiredConfig = hasRequiredConfig,
                    providerLabel = providerLabel,
                    modelLabel = modelLabel,
                    onOpenRoleplay = onOpenRoleplay,
                    onOpenSettings = onOpenSettings,
                )
                NarraOutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget),
                ) {
                    Icon(Lucide.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(NarraUi.compactGap))
                    Text(stringResource(id = R.string.home_open_settings))
                }
                if (!hasRequiredConfig) {
                    Text(
                        text = stringResource(id = R.string.home_config_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HomePhoneEntryCard(
    assistantCount: Int,
    hasRequiredConfig: Boolean,
    providerLabel: String,
    modelLabel: String,
    onOpenRoleplay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Lucide.Smartphone, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                ) {
                    Text(
                        text = stringResource(id = R.string.home_private_phone_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    ) {
                        Text(
                            text = "$assistantCount 位角色",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = if (hasRequiredConfig) "已就绪" else "需要配置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasRequiredConfig) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            HomeConnectionField(label = "提供商", value = providerLabel)
            HomeConnectionField(label = "模型", value = modelLabel)
            NarraButton(
                onClick = if (hasRequiredConfig) onOpenRoleplay else onOpenSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = NarraMinimumTouchTarget),
                elevation = null,
            ) {
                Icon(
                    imageVector = if (hasRequiredConfig) Lucide.Smartphone else Lucide.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(NarraUi.compactGap))
                Text(
                    text = if (hasRequiredConfig) {
                        stringResource(id = R.string.home_open_roleplay)
                    } else {
                        "配置提供商"
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeConnectionField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
