package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.normalizeChatMessageParts

/**
 * 清理模型偶发泄漏的格式提醒、自检步骤或提示词片段。
 *
 * 这类内容通常来自长上下文下模型把运行时 reminder 当成正文续写。
 * 清洗只作用于角色扮演输出，不参与普通聊天请求体构造。
 */
internal object RoleplayOutputLeakSanitizer {
    private val formatReminderBlockRegex = Regex("""(?is)<format_reminder\b[^>]*>.*?</format_reminder>\s*""")
    private val reasoningBlockRegex = Regex("""(?is)<think\b[^>]*>.*?</think>\s*|<thinking\b[^>]*>.*?</thinking>\s*""")
    private val danglingReasoningBlockRegex = Regex("""(?is)<think\b[^>]*>.*$|<thinking\b[^>]*>.*$""")
    private val bracketedPromptHeadingRegex = Regex(
        """^\s*[`'"“”‘’\s]*(?:[【\[]\s*)?(?:避坑|格式保持|格式要求|输出格式|输出前自检|自检|正例|反例|推进点|长文质量扫描|筋骨扫描|叙事之眼|禁词扫描|行文标点)(?:\s*[】\]])?""",
    )
    private val promptInstructionCueRegex = Regex(
        """(?i)(?:必须用|必须是|禁止|不得|不要输出|格式|自检|正例|反例|Markdown|JSON|代码块|客户端内部渲染|规则|要求|标签|包裹|裸对白|纯文本|额外说明)""",
    )
    private val roleplayProtocolCueRegex = Regex("""(?i)(?:<char>|</char>|<thought>|</thought>|<dialogue>|</dialogue>|<narration>|</narration>)""")
    private val promptListItemRegex = Regex("""^\s*(?:[-*•]|[0-9]+[.、]|[A-Z][.)、]|[❌✅→])\s+""")

    fun sanitize(
        rawContent: String,
        interactionMode: RoleplayInteractionMode?,
    ): String {
        if (rawContent.isBlank()) {
            return rawContent
        }
        if (interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
            return rawContent
        }
        val withoutExplicitReminder = rawContent
            .replace("\r\n", "\n")
            .replace(formatReminderBlockRegex, "")
            .replace(reasoningBlockRegex, "")
            .replace(danglingReasoningBlockRegex, "")
        return sanitizePromptLeakLines(withoutExplicitReminder)
    }

    fun sanitizeParts(
        parts: List<ChatMessagePart>,
        interactionMode: RoleplayInteractionMode?,
    ): List<ChatMessagePart> {
        if (parts.isEmpty() || interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
            return parts
        }
        return normalizeChatMessageParts(
            parts.mapNotNull { part ->
                if (part.type != ChatMessagePartType.TEXT) {
                    part
                } else {
                    val sanitized = sanitize(part.text, interactionMode)
                    sanitized.takeIf { it.isNotBlank() }?.let { part.copy(text = it) }
                }
            },
        )
    }

    private fun sanitizePromptLeakLines(content: String): String {
        val lines = content.lines()
        val kept = mutableListOf<String>()
        lines.forEach { line ->
            if (line.isPromptLeakLine()) {
                if (kept.lastOrNull()?.isBlank() != true) {
                    kept += ""
                }
                return@forEach
            }
            kept += line
        }
        return kept
            .joinToString(separator = "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun String.isPromptLeakLine(): Boolean {
        val normalized = trim()
            .trimStart('`', '\'', '"', '“', '”', '‘', '’')
            .trim()
        if (normalized.isBlank()) {
            return false
        }
        if (formatReminderBlockRegex.containsMatchIn(normalized)) {
            return true
        }
        if (bracketedPromptHeadingRegex.containsMatchIn(normalized)) {
            return true
        }
        val withoutListPrefix = normalized.replace(promptListItemRegex, "").trim()
        val hasInstructionCue = promptInstructionCueRegex.containsMatchIn(withoutListPrefix)
        val hasProtocolCue = roleplayProtocolCueRegex.containsMatchIn(withoutListPrefix)
        if (hasProtocolCue && hasInstructionCue) {
            return true
        }
        return hasInstructionCue && looksLikeMetaInstruction(withoutListPrefix)
    }

    private fun looksLikeMetaInstruction(line: String): Boolean {
        return line.contains("台词") ||
            line.contains("心声") ||
            line.contains("叙述") ||
            line.contains("对白") ||
            line.contains("输出") ||
            line.contains("模式") ||
            line.contains("段落") ||
            line.contains("每句") ||
            line.contains("每段") ||
            line.contains("最外层") ||
            line.contains("数组") ||
            line.contains("标签") ||
            line.contains("Markdown") ||
            line.contains("JSON") ||
            line.contains("代码块") ||
            line.contains("<char") ||
            line.contains("<thought") ||
            line.contains("<dialogue") ||
            line.contains("<narration")
    }
}
