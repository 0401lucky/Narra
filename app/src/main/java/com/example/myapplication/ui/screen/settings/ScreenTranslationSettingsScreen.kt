package com.example.myapplication.ui.screen.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.composables.icons.lucide.Battery
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.example.myapplication.system.translation.SelectionAccessibilityService
import com.example.myapplication.system.translation.resolveVendorBackgroundGuide
import com.example.myapplication.viewmodel.DefaultAutoDetectLanguage
import com.example.myapplication.viewmodel.SettingsUiState
import com.example.myapplication.viewmodel.TranslationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTranslationSettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onUpdateServiceEnabled: (Boolean) -> Unit,
    onUpdateOverlayEnabled: (Boolean) -> Unit,
    onUpdateSelectedTextEnabled: (Boolean) -> Unit,
    onUpdateShowSourceText: (Boolean) -> Unit,
    onUpdateTargetLanguage: (String) -> Unit,
    onUpdateVendorGuideDismissed: (Boolean) -> Unit,
    onSaveChanges: () -> Unit,
    onSaveAndStartService: () -> Unit,
    onSaveAndStopService: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = uiState.screenTranslationSettings
    val palette = rememberSettingsPalette()
    val vendorGuide = remember { resolveVendorBackgroundGuide(context) }
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    val overlayGranted = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val notificationGranted = remember { mutableStateOf(areNotificationsEnabled(context)) }
    val batteryOptimizationIgnored = remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val accessibilityGranted = remember { mutableStateOf(isSelectionAccessibilityEnabled(context)) }
    val targetLanguages = TranslationViewModel.SupportedLanguages.filterNot { it == DefaultAutoDetectLanguage }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted.value = Settings.canDrawOverlays(context)
                notificationGranted.value = areNotificationsEnabled(context)
                batteryOptimizationIgnored.value = isIgnoringBatteryOptimizations(context)
                accessibilityGranted.value = isSelectionAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "悬浮翻译",
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("screen_translation_settings_list")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 8.dp,
                end = SettingsScreenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = "随处翻译",
                    summary = "轻点悬浮球翻译当前屏幕，拖动可调整位置，长按可隐藏。也可选择其他应用中的文字后翻译。权限仅在你主动前往系统设置时开启；保存后应用当前配置。",
                )
            }

            // Permissions Section
            item { SettingsSectionHeader("系统权限", "") }
            item {
                SettingsGroup {
                    PermissionRow(
                        title = "通知权限",
                        granted = notificationGranted.value,
                        description = "前台服务常驻时需要通知栏入口",
                        actionLabel = "打开通知设置",
                        palette = palette,
                        onAction = {
                            context.startActivity(notificationSettingsIntent(context))
                        },
                        leadingIcon = Lucide.Bell,
                    )
                    SettingsGroupDivider()
                    PermissionRow(
                        title = "悬浮窗权限",
                        granted = overlayGranted.value,
                        description = "用于显示悬浮球和翻译结果面板",
                        actionLabel = "授权悬浮窗",
                        palette = palette,
                        onAction = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri(),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        leadingIcon = Lucide.Languages,
                    )
                    SettingsGroupDivider()
                    PermissionRow(
                        title = "无障碍选中文本",
                        granted = accessibilityGranted.value,
                        description = "读取系统外文本选区并弹出翻译入口",
                        actionLabel = "打开无障碍设置",
                        palette = palette,
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        leadingIcon = Lucide.Eye,
                    )
                    SettingsGroupDivider()
                    PermissionRow(
                        title = "后台运行优化",
                        granted = batteryOptimizationIgnored.value,
                        description = "如后台常被系统回收，可手动在系统电池优化设置中放宽限制",
                        actionLabel = "打开电池优化设置",
                        palette = palette,
                        onAction = {
                            context.startActivity(batteryOptimizationSettingsIntent(context))
                        },
                        leadingIcon = Lucide.Battery,
                    )
                }
            }

            // Behavior Section
            item { SettingsSectionHeader("翻译行为", "") }
            item {
                SettingsGroup {
                    TranslationSwitchRow(
                        title = "启用后台翻译服务",
                        description = "开启前台服务常驻，响应悬浮球与选中文本翻译",
                        checked = settings.serviceEnabled,
                        palette = palette,
                        onCheckedChange = onUpdateServiceEnabled,
                    )
                    SettingsGroupDivider()
                    TranslationSwitchRow(
                        title = "显示悬浮球",
                        description = "关闭后仅保留通知栏和选中文本翻译",
                        checked = settings.overlayEnabled,
                        palette = palette,
                        onCheckedChange = onUpdateOverlayEnabled,
                    )
                    SettingsGroupDivider()
                    TranslationSwitchRow(
                        title = "启用选中文本翻译",
                        description = "依赖无障碍服务读取选区",
                        checked = settings.selectedTextEnabled,
                        palette = palette,
                        onCheckedChange = onUpdateSelectedTextEnabled,
                    )
                    SettingsGroupDivider()
                    TranslationSwitchRow(
                        title = "结果面板显示原文",
                        description = "关闭后仅展示译文，减少面板占用",
                        checked = settings.showSourceText,
                        palette = palette,
                        onCheckedChange = onUpdateShowSourceText,
                    )
                }
            }

            // Target Language
            item { SettingsSectionHeader("默认目标语言", "") }
            item {
                SettingsGroup {
                    SettingsListRow(
                        title = settings.targetLanguage,
                        supportingText = "点击以切换目标语言",
                        onClick = { showLanguageSheet = true },
                    )
                }
            }

            // Vendor Guide
            if (vendorGuide != null && !settings.vendorGuideDismissed) {
                item {
                    SettingsSectionHeader(
                        title = vendorGuide.title,
                        description = vendorGuide.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"),
                    )
                }
                item {
                    SettingsHintCard(
                        title = vendorGuide.title,
                        body = vendorGuide.summary,
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        vendorGuide.deepLinkIntent?.let { intent ->
                            AnimatedSettingButton(
                                text = "打开厂商设置",
                                onClick = { runCatching { context.startActivity(intent) } },
                                enabled = true,
                                isPrimary = true,
                            )
                        }
                    }
                    AnimatedSettingButton(
                        text = "不再提示",
                        onClick = { onUpdateVendorGuideDismissed(true) },
                        enabled = true,
                        isPrimary = false,
                    )
                }
            }

            // Quick Actions
            item { SettingsSectionHeader("快捷操作", "") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!overlayGranted.value) {
                        Text(
                            text = "先允许悬浮窗权限，再启动翻译。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.body,
                        )
                    }
                    AnimatedSettingButton(
                        text = "保存并启动后台服务",
                        onClick = onSaveAndStartService,
                        enabled = overlayGranted.value && !uiState.isSaving,
                        isPrimary = true,
                    )
                    AnimatedSettingButton(
                        text = "保存并停止后台服务",
                        onClick = onSaveAndStopService,
                        enabled = !uiState.isSaving,
                        isPrimary = false,
                    )
                    AnimatedSettingButton(
                        text = if (uiState.isSaving) "保存中…" else "保存设置",
                        onClick = onSaveChanges,
                        enabled = !uiState.isSaving,
                        isPrimary = false,
                    )
                }
            }
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().testTag("screen_translation_languages"),
                contentPadding = PaddingValues(horizontal = SettingsScreenPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(targetLanguages, key = { it }) { language ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = palette.surface,
                        border = BorderStroke(
                            0.5.dp,
                            if (language == settings.targetLanguage) palette.accent.copy(alpha = 0.5f)
                            else palette.border.copy(alpha = 0.3f)
                        ),
                        onClick = {
                            onUpdateTargetLanguage(language)
                            showLanguageSheet = false
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = language,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.title,
                                modifier = Modifier.weight(1f),
                            )
                            if (language == settings.targetLanguage) {
                                SettingsStatusPill(
                                    text = "当前",
                                    containerColor = palette.accentSoft,
                                    contentColor = palette.accent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    palette: SettingsPalette,
    onAction: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    SettingsListRow(
        leadingContent = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (granted) palette.accent else MaterialTheme.colorScheme.error,
            )
        },
        title = title,
        supportingText = if (granted) "已就绪 · 管理设置" else actionLabel,
        onClick = onAction,
        trailingContent = {
            SettingsHelpButton(title = title, description = description)
        },
    )
}

@Composable
private fun TranslationSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    palette: SettingsPalette,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.title,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body.copy(alpha = 0.8f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

private fun areNotificationsEnabled(context: Context): Boolean {
    return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
}

private fun notificationSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

private fun batteryOptimizationSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isSelectionAccessibilityEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC,
    )
    val serviceId = "${context.packageName}/${SelectionAccessibilityService::class.java.name}"
    return enabledServices.any { it.resolveInfo.serviceInfo?.let { info ->
        "${info.packageName}/${info.name}" == serviceId
    } == true }
}
