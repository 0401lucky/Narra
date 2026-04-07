package com.example.myapplication.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ContextGovernanceItem
import com.example.myapplication.model.ContextGovernanceSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextGovernanceSheet(
    snapshot: ContextGovernanceSnapshot?,
    rawDebugDump: String,
    onRefreshSummary: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    val resolvedRawDump = snapshot?.rawDebugDump?.ifBlank { rawDebugDump } ?: rawDebugDump
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "上下文治理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                if (resolvedRawDump.isNotBlank()) {
                                    clipboardScope.copyPlainTextToClipboard(
                                        clipboard = clipboard,
                                        label = "context-governance",
                                        text = resolvedRawDump,
                                    )
                                    Toast.makeText(context, "原始上下文已复制", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = resolvedRawDump.isNotBlank(),
                        ) {
                            Text("复制原文")
                        }
                        TextButton(
                            onClick = onRefreshSummary,
                            enabled = snapshot?.hasActionableSummaryRefresh == true,
                        ) {
                            Text("刷新摘要")
                        }
                    }
                }
            }

            snapshot?.let { governance ->
                item {
                    GovernanceCard(title = "概览") {
                        GovernanceMetric("摘要状态", governance.summaryLabel)
                        GovernanceMetric("上下文压力", governance.pressureLevel.label)
                        GovernanceMetric("最近窗口", "${governance.recentWindow} 条")
                        GovernanceMetric(
                            "实际发送",
                            "${governance.sentMessageCount} / ${governance.requestMessageCountBeforeTrim} 条消息",
                        )
                        GovernanceMetric("摘要覆盖", "${governance.summaryCoveredMessageCount} 条")
                        Text(
                            text = governance.summarySupportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (governance.summaryPreview.isNotBlank()) {
                    item {
                        GovernanceCard(title = "摘要预览") {
                            Text(
                                text = governance.summaryPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (governance.worldBookItems.isNotEmpty()) {
                    item {
                        GovernanceCard(title = "世界书") {
                            for (item in governance.worldBookItems) {
                                GovernanceItemBlock(item)
                            }
                        }
                    }
                }

                if (governance.memoryItems.isNotEmpty()) {
                    item {
                        GovernanceCard(title = "记忆") {
                            for (item in governance.memoryItems) {
                                GovernanceItemBlock(item)
                            }
                        }
                    }
                }

                item {
                    GovernanceCard(title = "可用工具") {
                        Text(
                            text = governance.enabledTools.joinToString("、").ifBlank { "无" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                GovernanceCard(title = "原始上下文") {
                    Text(
                        text = resolvedRawDump.ifBlank { "当前还没有可展示的上下文信息。发送一条消息后再查看这里。" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GovernanceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun GovernanceMetric(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun GovernanceItemBlock(
    item: ContextGovernanceItem,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
