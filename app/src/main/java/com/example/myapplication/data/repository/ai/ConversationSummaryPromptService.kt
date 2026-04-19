package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings

/**
 * 负责普通会话摘要与剧情会话摘要两种风格。
 * T6.3 从 DefaultAiPromptExtrasService 抽离。
 *
 * - 普通摘要走常规采样，风格简洁、控制在 300 字。
 * - 剧情摘要使用 [PromptExtrasCore.buildRequestWithRoleplaySampling]，按 5 小节模板输出，
 *   允许 roleplay 采样回退。
 */
internal class ConversationSummaryPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = buildString {
            append("请把下面的对话压缩成一段简洁摘要，保留关键事实、人物关系、目标、进度和未完成事项。")
            append("输出使用简体中文，控制在 300 字以内，不要添加标题：\n")
            append(conversationText)
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "摘要生成失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("摘要模型未返回有效内容")
        }
        return content.take(500)
    }

    suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = buildString {
            append("你是沉浸式剧情摘要整理器。")
            append("请根据下面的剧情记录输出结构化摘要，帮助下一轮扮演保持连续性。")
            append("严格使用以下 5 个小节，每节 1 到 3 句：")
            append("【剧情进展】、【当前状态】、【关系变化】、【未解问题】、【近期触发点】。")
            append("保留明确人物关系、地点、任务进度、情绪转折和悬念。\n")
            append("摘要质量约束：\n")
            append("1. 使用角色第一人称视角进行总结。\n")
            append("2. 必须使用精确时间（如 15:20），禁止使用模糊时间（如\u201c上午\u201d\u201c下午\u201d\u201c晚上\u201d）。\n")
            append("3. 保留核心事件链条：对方处于什么状态 → 我做了什么 → 对方如何反应 → 结果如何。\n")
            append("4. 按话题自动划分，一个话题视为一件事，不要全部堆在一起。单个事件叙述控制在 150 字以内。\n")
            append("5. 如果对话中涉及图片，必须说明图片的具体内容，禁止只写\u201c发了一张图片/照片\u201d。\n")
            append("6. 严禁编造对话中不存在的内容，如实直述。\n")
            append("不要输出 XML、不要解释规则、不要省略小节标题：\n")
            append(conversationText)
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 摘要生成失败",
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("RP 摘要模型未返回有效内容")
        }
        return content.take(800)
    }
}
