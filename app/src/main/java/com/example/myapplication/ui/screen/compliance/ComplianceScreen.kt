package com.example.myapplication.ui.screen.compliance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.compliance.CompliancePolicy
import com.example.myapplication.viewmodel.ComplianceUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect

enum class ComplianceScreenMode {
    GATE,
    VIEW_ONLY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(
    uiState: ComplianceUiState,
    mode: ComplianceScreenMode,
    onAccept: () -> Unit = {},
    onExit: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onClearErrorMessage: () -> Unit = {},
) {
    val isGate = mode == ComplianceScreenMode.GATE
    val scrollState = rememberScrollState()
    var hasReadToEnd by rememberSaveable { mutableStateOf(false) }
    var agreeToTerms by rememberSaveable { mutableStateOf(false) }
    var confirmAdult by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, maxValue) ->
            if (maxValue == 0 || value >= maxValue) {
                hasReadToEnd = true
            }
        }
    }

    if (isGate) {
        BackHandler { onExit() }
    } else {
        BackHandler { onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = CompliancePolicy.TITLE,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "条款版本 ${CompliancePolicy.CURRENT_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = if (isGate) {
                    {}
                } else {
                    {
                        androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = if (isGate) {
            {
                ComplianceConfirmationBar(
                    hasReadToEnd = hasReadToEnd,
                    agreeToTerms = agreeToTerms,
                    confirmAdult = confirmAdult,
                    isSaving = uiState.isSaving,
                    onAgreeToTermsChange = { agreeToTerms = it },
                    onConfirmAdultChange = { confirmAdult = it },
                    onAccept = onAccept,
                    onExit = onExit,
                )
            }
        } else {
            {}
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.errorMessage != null) {
                ComplianceErrorBanner(
                    message = uiState.errorMessage,
                    onRetry = onRetry,
                    onDismiss = onClearErrorMessage,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .testTag(COMPLIANCE_CONTENT_TAG)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (isGate) {
                                "请完整阅读以下内容。Narra 仅向年满 18 周岁的用户开放。"
                            } else {
                                "以下内容与首次启动时展示的使用须知一致。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                CompliancePolicy.sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }

                if (isGate) {
                    Text(
                        text = if (hasReadToEnd) {
                            "你已阅读到条款末尾，请完成下方确认。"
                        } else {
                            "请继续向下滑动，阅读到条款末尾后再进行确认。"
                        },
                        modifier = Modifier.testTag(COMPLIANCE_READ_STATUS_TAG),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    ComplianceAcceptanceInfo(uiState = uiState)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ComplianceConfirmationBar(
    hasReadToEnd: Boolean,
    agreeToTerms: Boolean,
    confirmAdult: Boolean,
    isSaving: Boolean,
    onAgreeToTermsChange: (Boolean) -> Unit,
    onConfirmAdultChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    val canConfirm = hasReadToEnd && agreeToTerms && confirmAdult && !isSaving
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ComplianceCheckRow(
                text = "我已阅读并同意以上使用条款与风险提示",
                checked = agreeToTerms,
                enabled = hasReadToEnd && !isSaving,
                testTag = COMPLIANCE_TERMS_CHECKBOX_TAG,
                onCheckedChange = onAgreeToTermsChange,
            )
            ComplianceCheckRow(
                text = "本人确认已年满 18 周岁",
                checked = confirmAdult,
                enabled = hasReadToEnd && !isSaving,
                testTag = COMPLIANCE_ADULT_CHECKBOX_TAG,
                onCheckedChange = onConfirmAdultChange,
            )
            Button(
                onClick = onAccept,
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(COMPLIANCE_ACCEPT_BUTTON_TAG),
            ) {
                Text(if (isSaving) "正在保存确认..." else "同意并进入 Narra")
            }
            OutlinedButton(
                onClick = onExit,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(COMPLIANCE_EXIT_BUTTON_TAG),
            ) {
                Text("不同意并退出")
            }
        }
    }
}

@Composable
private fun ComplianceCheckRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) },
            )
            .testTag(testTag)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ComplianceAcceptanceInfo(uiState: ComplianceUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(COMPLIANCE_ACCEPTANCE_INFO_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "当前条款版本：${CompliancePolicy.CURRENT_VERSION}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (uiState.acceptedPolicyVersion.isBlank()) {
                "当前设备尚未记录确认"
            } else {
                "已接受版本：${uiState.acceptedPolicyVersion}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.acceptedAtEpochMillis > 0L) {
            Text(
                text = "确认时间：${formatAcceptedAt(uiState.acceptedAtEpochMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComplianceErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    }
}

private fun formatAcceptedAt(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE)
        .format(Date(epochMillis))
}

const val COMPLIANCE_CONTENT_TAG = "compliance_content"
const val COMPLIANCE_READ_STATUS_TAG = "compliance_read_status"
const val COMPLIANCE_TERMS_CHECKBOX_TAG = "compliance_terms_checkbox"
const val COMPLIANCE_ADULT_CHECKBOX_TAG = "compliance_adult_checkbox"
const val COMPLIANCE_ACCEPT_BUTTON_TAG = "compliance_accept_button"
const val COMPLIANCE_EXIT_BUTTON_TAG = "compliance_exit_button"
const val COMPLIANCE_ACCEPTANCE_INFO_TAG = "compliance_acceptance_info"
