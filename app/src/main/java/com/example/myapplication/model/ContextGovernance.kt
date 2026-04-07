package com.example.myapplication.model

enum class ContextPressureLevel(
    val label: String,
) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
}

enum class ContextSummaryState(
    val label: String,
) {
    DISABLED("未启用"),
    READY_IDLE("已就绪"),
    APPLIED("已接管"),
    STALE("待刷新"),
}

data class ContextGovernanceItem(
    val title: String,
    val content: String,
)

data class ContextGovernanceSnapshot(
    val pressureLevel: ContextPressureLevel = ContextPressureLevel.LOW,
    val summaryState: ContextSummaryState = ContextSummaryState.DISABLED,
    val recentWindow: Int = 0,
    val summaryRefreshAvailable: Boolean = false,
    val sentMessageCount: Int = 0,
    val requestMessageCountBeforeTrim: Int = 0,
    val summaryCoveredMessageCount: Int = 0,
    val completedMessageCount: Int = 0,
    val memoryCount: Int = 0,
    val worldBookHitCount: Int = 0,
    val enabledTools: List<String> = emptyList(),
    val summaryPreview: String = "",
    val memoryItems: List<ContextGovernanceItem> = emptyList(),
    val worldBookItems: List<ContextGovernanceItem> = emptyList(),
    val rawDebugDump: String = "",
) {
    val hasActionableSummaryRefresh: Boolean
        get() = summaryRefreshAvailable

    val summaryLabel: String
        get() = summaryState.label

    val summarySupportingText: String
        get() = when (summaryState) {
            ContextSummaryState.DISABLED -> "当前提供商未配置可用的摘要模型。"
            ContextSummaryState.READY_IDLE -> {
                if (summaryCoveredMessageCount > 0) {
                    "已有摘要可用，当前窗口内的消息仍会原样发送。"
                } else {
                    "消息量还没有达到摘要接管区间。"
                }
            }

            ContextSummaryState.APPLIED -> {
                "当前仅发送最近 $recentWindow 条消息，旧消息由摘要承接。"
            }

            ContextSummaryState.STALE -> {
                if (summaryCoveredMessageCount > 0) {
                    "当前上下文已经超出摘要覆盖范围，建议刷新摘要。"
                } else {
                    "上下文已经变长，建议先生成摘要以控制发送体积。"
                }
            }
        }
}
