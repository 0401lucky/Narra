package com.example.myapplication.model

/**
 * 协议结束证据；正文是否为空、结构是否完整仍由调用方校验。
 * rawFinishReason 保存 Chat/Anthropic 的结束原因，或 Responses 的未完成原因/错误码；
 * rawStatus 只保存 Responses 顶层状态，不复制供应商错误正文。
 */
data class PromptCompletionResult(
    val content: String,
    val status: PromptCompletionStatus,
    val rawFinishReason: String? = null,
    val rawStatus: String? = null,
)

enum class PromptCompletionStatus {
    COMPLETED,

    /** 兼容供应商未提供结束字段，不能据此证明正文完整。 */
    UNKNOWN,
    TRUNCATED,
    FILTERED,
    INCOMPLETE,
    FAILED,

    /** 供应商返回的取消状态；协程取消仍通过 CancellationException 传播。 */
    CANCELLED,
}
