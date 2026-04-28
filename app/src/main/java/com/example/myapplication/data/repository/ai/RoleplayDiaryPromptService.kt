package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayDiaryDraft
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 负责剧情模式的私域文本生成：
 * - [generateRoleplayDiaries]：基于人设 / 场景 / 最近对话虚构 5~8 篇角色日记。
 * - [generateGiftImagePrompt]：为礼物卡片生成一条文生图 Prompt。
 *
 * T6.6 从 DefaultAiPromptExtrasService 抽离。日记走 roleplay 采样 + 回退；礼物走常规采样。
 */
internal class RoleplayDiaryPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateRoleplayDiaries(
        characterContext: String,
        scenarioContext: String,
        conversationExcerpt: String,
        characterName: String,
        userName: String,
        todayLabel: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<RoleplayDiaryDraft> {
        val safeCharacterName = sanitizeDiaryIdentifier(characterName).ifBlank { "角色" }
        val safeUserName = sanitizeDiaryIdentifier(userName).ifBlank { "聊天对象" }
        val safeTodayLabel = sanitizeDiaryIdentifier(todayLabel)
        val prompt = buildString {
            appendLine("# 你的任务")
            appendLine("你是一个虚拟生活模拟器和故事作家。")
            appendLine("你的任务是扮演角色“$safeCharacterName”，并根据其人设、记忆、世界设定和最近的互动，虚构出【5到8篇】TA最近可能会写的日记。")
            appendLine()
            appendLine("# 核心规则")
            appendLine("1. 【时间（最高优先级）】")
            appendLine("- 今天的日期是 $safeTodayLabel。")
            appendLine("- 你生成的所有日记标题日期，必须是今天或今天以前的日期。")
            appendLine("- 绝对禁止生成任何未来日期。")
            appendLine("2. 【沉浸感】")
            appendLine("- 每篇日记都必须使用第一人称视角“我”来写。")
            appendLine("- 绝对禁止把自己写成第三人称“他/她/TA”。")
            appendLine("- 内容必须像角色私下写给自己的秘密记录，而不是剧情总结。")
            appendLine("3. 【长度】")
            appendLine("- 每篇正文不少于 300 字。")
            appendLine("4. 【格式铁律（最高优先级）】")
            appendLine("- 你的回复必须且只能是一个 JSON 数组字符串。")
            appendLine("- 回复必须以 `[` 开始，并以 `]` 结束。")
            appendLine("- 绝对禁止在 JSON 前后添加解释、标题、Markdown 代码块或额外文字。")
            appendLine("- 数组元素格式固定为：{\"title\":\"...\",\"content\":\"...\",\"mood\":\"...\",\"weather\":\"...\",\"tags\":[\"...\"],\"dateLabel\":\"...\"}。")
            appendLine("- `mood` 是 2~4 字心情短语（例如 平静 / 烦躁 / 窃喜 / 心虚 / 怀念）。")
            appendLine("- `weather` 是 2~4 字天气或环境氛围（例如 晴 / 阴 / 夜雨 / 清晨 / 霓虹街），可留空但最好有。")
            appendLine("- `tags` 是 2~4 个短标签数组（例如 [\"失眠\",\"学校\",\"新朋友\"]），每个不超过 6 字。")
            appendLine("- `dateLabel` 是这一篇日记对应的日期口语化标签（例如 \"3 月 14 日 周五\" 或 \"$safeTodayLabel\"），必须不晚于今天。")
            appendLine("5. 【占位符替换（最高优先级）】")
            appendLine("- 日记内容里绝对不能出现 `{{user}}`。")
            appendLine("- 你必须使用“$safeUserName”来指代聊天对象。")
            appendLine("6. 【日记专属标记语法（必须使用）】")
            appendLine("- `**加粗文字**`：强调。")
            appendLine("- `~~划掉的文字~~`：改变主意或自我否定。")
            appendLine("- `!h{黄色高亮}`：标记关键词或重要信息。")
            appendLine("- `!u{粉色下划线}`：标注人名、地名或特殊名词。")
            appendLine("- `!e{粉色强调}`：表达强烈情绪。")
            appendLine("- `!w{手写体}`：写下引言、歌词或特殊笔记。")
            appendLine("- `!m{凌乱手写体}`：表达激动、慌乱或潦草记录。")
            appendLine("- `||涂黑||`：隐藏秘密或敏感词汇，每次涂黑 2 到 5 个字。")
            appendLine("7. 【内容约束】")
            appendLine("- 所有内容必须基于给定的人设、记忆、关系、剧情和最近对话自然推导。")
            appendLine("- 可以适度扩展，但不能脱离上下文乱编。")
            appendLine("- 多篇日记之间要体现时间推进和情绪变化，不要写成同一件事的重复改写。")
            appendLine()
            if (characterContext.isNotBlank()) {
                appendLine("# 角色与长期上下文")
                appendLine(characterContext.trim())
                appendLine()
            }
            if (scenarioContext.isNotBlank()) {
                appendLine("# 当前场景")
                appendLine(scenarioContext.trim())
                appendLine()
            }
            if (conversationExcerpt.isNotBlank()) {
                appendLine("# 最近互动记录")
                appendLine(conversationExcerpt.trim())
                appendLine()
            }
            appendLine("现在，请开始输出这组充满真情实感、并熟练运用了日记标记语法的角色日记。")
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "角色日记生成失败",
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        if (content.isBlank()) {
            return emptyList()
        }
        val parsedArray = extractDiaryArray(content)
            ?: error("角色日记生成失败：模型返回格式不符合要求，未提取到合法 JSON 数组")
        val drafts = parsedArray.mapIndexedNotNull { index, element ->
            parseDiaryDraft(element, index)
        }
        if (parsedArray.size() > 0 && drafts.isEmpty()) {
            error("角色日记生成失败：模型返回了 JSON，但未包含可保存的日记标题和正文")
        }
        return drafts
    }

    suspend fun generateGiftImagePrompt(
        giftName: String,
        recipientName: String,
        userName: String,
        assistantName: String,
        contextExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = buildString {
            append("你是礼物生图提示词优化器。")
            append("请根据礼物信息、人物关系和最近上下文，输出一条适合文生图模型的最终提示词。")
            append("只输出最终 Prompt，不要标题、解释、编号或 Markdown。")
            append("默认突出单个礼物主体的特写或近景，强调材质、光影、氛围和构图稳定。")
            append("除非上下文明确需要，否则不要出现人物正脸、对话框、文字、水印、品牌 logo、边框或界面元素。")
            append("风格偏好：电影感、精致细节、真实材质、柔和光影、高质量构图。")
            append("\n礼物：").append(giftName.trim())
            append("\n送礼对象：").append(recipientName.trim().ifBlank { "对方" })
            append("\n送礼人：").append(userName.trim().ifBlank { "用户" })
            append("\n相关角色：").append(assistantName.trim().ifBlank { "对方" })
            if (contextExcerpt.isNotBlank()) {
                append("\n最近上下文：").append(contextExcerpt.trim())
            }
        }
        return core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "礼物生图提示词生成失败",
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
    }

    private fun sanitizeDiaryIdentifier(value: String): String {
        return value
            .replace("\r", "")
            .replace("\n", " ")
            .replace("\"", "'")
            .replace("“", "'")
            .replace("”", "'")
            .replace("{", "〈")
            .replace("}", "〉")
            .trim()
    }

    private fun extractDiaryArray(rawContent: String): JsonArray? {
        val cleaned = core.stripMarkdownCodeFence(rawContent)
        parseDiaryArrayFromJson(cleaned)?.let { return it }
        findFirstCompleteJsonArray(cleaned)?.let { candidate ->
            parseDiaryArrayFromJson(candidate)?.let { return it }
        }
        core.extractStructuredJsonObject(cleaned)
            ?.let(::findDiaryArrayInObject)
            ?.let { return it }
        return null
    }

    private fun parseDiaryArrayFromJson(candidate: String): JsonArray? {
        val element = runCatching { JsonParser.parseString(candidate) }.getOrNull()
            ?: return null
        return when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> findDiaryArrayInObject(element.asJsonObject)
            else -> null
        }
    }

    private fun findDiaryArrayInObject(obj: JsonObject): JsonArray? {
        DiaryArrayKeys.forEach { key ->
            val value = obj.get(key) ?: return@forEach
            when {
                value.isJsonArray -> return value.asJsonArray
                value.isJsonObject -> findDiaryArrayInObject(value.asJsonObject)?.let { return it }
            }
        }
        return obj.entrySet()
            .map { it.value }
            .filter { it.isJsonArray }
            .singleOrNull()
            ?.asJsonArray
    }

    private fun parseDiaryDraft(element: JsonElement, index: Int): RoleplayDiaryDraft? {
        val item = element.asJsonObjectOrNull() ?: return null
        val normalizedItem = findNestedDiaryObject(item) ?: item
        val content = normalizedItem.firstStringValue(DiaryContentKeys)
        if (content.isBlank()) {
            return null
        }
        val dateLabel = normalizedItem.firstStringValue(DiaryDateKeys)
        val title = normalizedItem.firstStringValue(DiaryTitleKeys)
            .ifBlank { dateLabel }
            .ifBlank { "日记 ${index + 1}" }
        return RoleplayDiaryDraft(
            title = title,
            content = content,
            mood = normalizedItem.firstStringValue(DiaryMoodKeys),
            weather = normalizedItem.firstStringValue(DiaryWeatherKeys),
            tags = normalizedItem.diaryTags(),
            dateLabel = dateLabel,
        )
    }

    private fun findNestedDiaryObject(item: JsonObject): JsonObject? {
        return DiaryNestedObjectKeys
            .asSequence()
            .mapNotNull { key -> item.get(key) }
            .firstOrNull { it.isJsonObject }
            ?.asJsonObject
    }

    private fun JsonObject.firstStringValue(keys: List<String>): String {
        return keys
            .asSequence()
            .mapNotNull { key -> get(key) }
            .mapNotNull(::jsonElementStringValue)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun JsonObject.diaryTags(): List<String> {
        val value = DiaryTagKeys
            .asSequence()
            .mapNotNull { key -> get(key) }
            .firstOrNull { !it.isJsonNull }
            ?: return emptyList()
        return when {
            value.isJsonArray -> value.asJsonArray
                .mapNotNull(::jsonElementStringValue)
                .flatMap(::splitDiaryTags)
            value.isJsonPrimitive -> splitDiaryTags(value.asString)
            else -> emptyList()
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    private fun jsonElementStringValue(element: JsonElement): String? {
        if (element.isJsonNull || !element.isJsonPrimitive) {
            return null
        }
        return runCatching { element.asString }.getOrNull()
    }

    private fun splitDiaryTags(value: String): List<String> {
        return value.split(',', '，', '、', ';', '；', '\n')
    }

    private fun findFirstCompleteJsonArray(rawContent: String): String? {
        var searchStart = 0
        while (searchStart < rawContent.length) {
            val startIndex = rawContent.indexOf('[', startIndex = searchStart)
            if (startIndex == -1) {
                return null
            }
            var inString = false
            var escaped = false
            var depth = 0
            for (index in startIndex until rawContent.length) {
                val char = rawContent[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    '[' -> depth += 1
                    ']' -> {
                        depth -= 1
                        if (depth == 0) {
                            return rawContent.substring(startIndex, index + 1)
                        }
                    }
                }
            }
            searchStart = startIndex + 1
        }
        return null
    }

    private companion object {
        val DiaryArrayKeys = listOf(
            "entries",
            "diaries",
            "items",
            "data",
            "diaryEntries",
            "journals",
            "日记",
            "日记列表",
            "条目",
        )
        val DiaryNestedObjectKeys = listOf("diary", "entry", "item", "journal", "日记", "条目")
        val DiaryTitleKeys = listOf("title", "name", "heading", "标题", "日记标题")
        val DiaryContentKeys = listOf("content", "body", "text", "diary", "entry", "正文", "内容", "日记内容")
        val DiaryMoodKeys = listOf("mood", "emotion", "feeling", "心情", "情绪")
        val DiaryWeatherKeys = listOf("weather", "atmosphere", "environment", "天气", "氛围", "环境")
        val DiaryDateKeys = listOf("dateLabel", "date_label", "date", "time", "日期", "时间")
        val DiaryTagKeys = listOf("tags", "tag", "labels", "keywords", "标签", "关键词")
    }
}
