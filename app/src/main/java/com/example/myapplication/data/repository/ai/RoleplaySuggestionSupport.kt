package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.RoleplaySuggestionUiModel

internal object RoleplaySuggestionSupport {
    private const val roleplaySuggestionCount = 3

    fun buildRequestMessages(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        longformMode: Boolean,
        rejectedSuggestions: List<RoleplaySuggestionUiModel> = emptyList(),
    ): List<ChatMessageDto> {
        return listOf(
            ChatMessageDto(
                role = "system",
                content = buildString {
                    append("你是沉浸式剧情输入建议生成器。")
                    append("你的任务不是继续扮演角色，而是基于当前剧情上下文，为玩家/用户这一侧生成 3 条可直接发送的下一步输入建议。")
                    append("所有建议必须站在用户视角书写，不能替角色发言，不能输出剧情总结或解释。")
                    append("必须尽量保持玩家最近几轮的说话口吻、人设和情绪状态一致。")
                    append("3 条建议是 3 个独立的可选写作方向，用户只会从中挑一个使用，所以每条建议都必须是完整、可独立发送的草稿。")
                    append("建议风格要有差异，至少覆盖：推进剧情、探索信息、推动情绪/关系。")
                    if (longformMode) {
                        append("当前场景处于长文小说模式。")
                        append("每条建议都要更像可直接接到小说流中的用户段落草稿。")
                        append("建议内容可以同时包含动作描写、心理描写和对白。")
                        append("如果出现心理描写，优先使用全角括号（……）；如果出现对白，优先使用中文引号。")
                        append("每条建议控制在 2~4 句，允许写成较完整的一小段。")
                    } else {
                        append("建议内容可以同时包含动作描写和对话；如果包含动作描写，优先使用 *动作* 这种写法。")
                        append("每条建议控制在 1~3 句。")
                    }
                    append("不要输出 Markdown，不要输出代码块，不要输出额外解释。")
                    append("3 条建议的 axis 必须分别覆盖：plot、info、emotion，且各出现一次。")
                    append("label 由你自由生成，为 2 到 6 个字的短标签，不要复用固定模板。")
                    append("首选且默认只输出 JSON 数组。")
                    append("如果提供方强制要求对象包装，唯一允许的包装形式是 {\"suggestions\":[...]}。")
                    append("严禁输出 {\"plot\":{...},\"info\":{...},\"emotion\":{...}} 这类按轴分组的外层对象。")
                    append("每条建议的完整正文必须全部写进 text 字段，不要再拆成 content、dialogue、action 或其他子字段。")
                    append("格式固定为：")
                    append("[{\"axis\":\"plot\",\"label\":\"推进试探\",\"text\":\"...\"},{\"axis\":\"info\",\"label\":\"追问细节\",\"text\":\"...\"},{\"axis\":\"emotion\",\"label\":\"情绪逼近\",\"text\":\"...\"}]")
                },
            ),
            ChatMessageDto(
                role = "user",
                content = buildString {
                    append("【剧情设定与上下文】\n")
                    append(systemPrompt.trim().ifBlank { "无" })
                    append("\n\n【玩家口吻参考】\n")
                    append(playerStyleReference.trim().ifBlank { "暂无可参考输入，请保持自然克制的第一人称口吻。" })
                    append("\n\n【最近剧情】\n")
                    append(conversationExcerpt.trim().ifBlank { "无" })
                    if (rejectedSuggestions.isNotEmpty()) {
                        append("\n\n【上一批建议（不要沿用这些句式）】\n")
                        rejectedSuggestions.forEach { suggestion ->
                            append("- [")
                            append(suggestion.axis.name.lowercase())
                            append("] ")
                            append(suggestion.text)
                            append('\n')
                        }
                    }
                    append("\n\n请基于以上内容生成 3 条“用户下一步输入建议”。")
                },
            ),
        )
    }

    fun shouldRetry(
        suggestions: List<RoleplaySuggestionUiModel>,
    ): Boolean {
        if (suggestions.isEmpty()) {
            return false
        }
        if (suggestions.size < roleplaySuggestionCount) {
            return true
        }
        if (suggestions.map { it.axis }.distinct().size < roleplaySuggestionCount) {
            return true
        }
        val normalizedSuggestions = suggestions.map { suggestion ->
            normalizeSuggestionTextForDiversity(suggestion.text)
        }
        if (normalizedSuggestions.distinct().size < suggestions.size) {
            return true
        }
        val firstClauses = normalizedSuggestions.map { suggestion ->
            suggestion.split('，', '。', '！', '？')
                .firstOrNull()
                .orEmpty()
        }
        return firstClauses.distinct().size < suggestions.size
    }

    private fun normalizeSuggestionTextForDiversity(text: String): String {
        return text
            .replace(Regex("""\*[^*]+\*"""), "")
            .replace(Regex("""\s+"""), "")
            .trim()
    }
}
