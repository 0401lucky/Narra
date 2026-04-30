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

enum class ContextLogSourceType(
    val label: String,
) {
    ROLE_CARD("角色卡"),
    CHAT_HISTORY("聊天历史"),
    ROLE_EXTRAS("角色补充"),
    WORLD_BOOK("世界书"),
    LONG_MEMORY("长记忆"),
    SUMMARY("摘要"),
    USER_PERSONA("用户身份"),
    PHONE_CONTEXT("手机线索"),
    PROMPT_PRESET("预设"),
    SYSTEM_RULE("系统规则"),
}

data class ContextLogSection(
    val sourceType: ContextLogSourceType,
    val title: String,
    val content: String,
    val tokenEstimate: Int = estimateContextTokenCount(content),
    val id: String = "${sourceType.name}:${title}:${content.hashCode()}",
)

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
    val providerLabel: String = "",
    val modelLabel: String = "",
    val promptModeLabel: String = "",
    val activePresetId: String = "",
    val activePresetName: String = "",
    val generatedAt: Long = 0L,
    val estimatedContextTokens: Int = 0,
    val contextSections: List<ContextLogSection> = emptyList(),
    val id: String = java.util.UUID.randomUUID().toString(),
) {
    val hasActionableSummaryRefresh: Boolean
        get() = summaryRefreshAvailable

    val summaryLabel: String
        get() = summaryState.label

    val activeSourceTypes: List<ContextLogSourceType>
        get() = contextSections.map { it.sourceType }.distinct()

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

fun estimateContextTokenCount(content: String): Int {
    val normalized = content.trim()
    if (normalized.isBlank()) {
        return 0
    }
    return (normalized.length + 3) / 4
}
