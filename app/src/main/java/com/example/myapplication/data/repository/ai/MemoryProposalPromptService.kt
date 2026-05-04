package com.example.myapplication.data.repository.ai

import com.example.myapplication.context.ContextPlaceholderResolver
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.MemoryPromptDefaults
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.estimateContextTokenCount
import com.google.gson.JsonParser

/**
 * 负责三种记忆类提取 / 归并任务：
 * - [generateMemoryEntries]：通用对话长期记忆，JSON 数组。
 * - [generateRoleplayMemoryEntries]：沉浸剧情三段式记忆（persistent / scene / mental state）。
 * - [condenseRoleplayMemories]：将零散的角色/场景记忆精炼为 maxItems 条以内。
 *
 * T6.4 从 DefaultAiPromptExtrasService 抽离。不使用 roleplay 采样（保留原语义）。
 *
 * tavo 对标 A4：每次提取记忆的请求都会把完整 prompt 推入 [ContextLogStore]，
 * 与聊天日志混排同列展示，详情页根据 promptModeLabel="长记忆" 渲染单 system 段精简布局。
 */
internal class MemoryProposalPromptService(
    private val core: PromptExtrasCore,
    private val contextLogStore: ContextLogStore? = null,
) {
    suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        existingMemories: List<String> = emptyList(),
        userName: String = "用户",
        characterName: String = "角色",
        extractionPromptOverride: String = "",
    ): List<String> {
        val prompt = buildExtractionPrompt(
            conversationExcerpt = conversationExcerpt,
            existingMemories = existingMemories,
            userName = userName,
            characterName = characterName,
            override = extractionPromptOverride,
        )
        pushContextLog(
            prompt = prompt,
            modelId = modelId,
            provider = provider,
            sectionTitle = "通用记忆提取",
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "记忆提取失败",
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
            return emptyList()
        }
        val parsedArray = runCatching {
            JsonParser.parseString(content).asJsonArray.mapNotNull { element ->
                runCatching { element.asString.trim() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
        if (parsedArray != null) {
            return parsedArray.distinct().take(3)
        }
        return content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)
    }

    suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        existingMemories: List<String> = emptyList(),
    ): StructuredMemoryExtractionResult {
        val prompt = buildString {
            append("你是沉浸式剧情记忆提取器。")
            append("请从下面的剧情记录中提取三类记忆。")
            append("persistent_memories 用来保存长期稳定事实、关系、偏好和设定。")
            append("scene_state_memories 用来保存当前剧情线的地点、任务进度、关键事件、关系阶段和线索。")
            append("mental_state 用来保存角色此刻的心境快照——对方处于什么情绪基调、对用户是什么态度、有没有未说出口的心结。")
            append("忽略寒暄、重复信息、一次性情绪和无长期价值的细节。")
            append("若某一类没有内容，返回空数组或空字符串。\n")
            append("记忆质量约束：\n")
            append("1. 严禁编造对话中不存在的内容，只提取实际发生的事实。\n")
            append("2. 记忆条目应包含近似时间标记（如果对话中有提及）。\n")
            append("3. 关系类记忆必须保留变化方向和强度（如“关系升温”“产生芥蒂”等）。\n")
            append("4. mental_state 应该是一句话概括角色当下心境，如\"表面冷淡但内心在意，因为昨天的吵架还在别扭\"。\n")
            append("只输出 JSON 对象：")
            append("{\"persistent_memories\":[...],\"scene_state_memories\":[...],\"mental_state\":\"...\"}。")
            append("每项都必须是简体中文短句，不要输出额外解释：\n")
            appendKnownMemoriesSection(existingMemories)
            append(conversationExcerpt)
        }
        pushContextLog(
            prompt = prompt,
            modelId = modelId,
            provider = provider,
            sectionTitle = "沉浸剧情记忆提取",
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 记忆提取失败",
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
            return StructuredMemoryExtractionResult()
        }

        val parsedJson = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
        if (parsedJson != null) {
            return StructuredMemoryExtractionResult(
                persistentMemories = parsedJson["persistent_memories"]
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { element -> runCatching { element.asString.trim() }.getOrNull() }
                    .orEmpty()
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(4),
                sceneStateMemories = parsedJson["scene_state_memories"]
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { element -> runCatching { element.asString.trim() }.getOrNull() }
                    .orEmpty()
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(6),
                mentalStateSnapshot = parsedJson["mental_state"]
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    .orEmpty(),
            )
        }

        val fallbackItems = content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
        return StructuredMemoryExtractionResult(
            persistentMemories = fallbackItems.take(2),
            sceneStateMemories = fallbackItems.drop(2).take(4),
        )
    }

    suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val normalizedItems = memoryItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedItems.size <= maxItems.coerceAtLeast(1)) {
            return normalizedItems
        }
        val prompt = buildString {
            append(
                when (mode) {
                    RoleplayMemoryCondenseMode.CHARACTER -> {
                        "你是角色长期记忆整理器。请把下面多条零散记忆整理为更稳定、更少量的角色事实。"
                    }
                    RoleplayMemoryCondenseMode.SCENE -> {
                        "你是剧情状态记忆整理器。请把下面多条零散剧情状态整理为更稳定、更少量的场景事实。"
                    }
                },
            )
            append("去掉重复、近义改写和一次性废话，保留必须被后续对话遵守的信息。")
            append("必须进行实际内容精炼，禁止偷懒输出条目数或占位符，每条输出必须包含实质性信息。")
            append("输出不超过 ")
            append(maxItems.coerceAtLeast(1))
            append(" 条简体中文短句。只输出 JSON 数组，不要解释：\n")
            normalizedItems.forEach { item ->
                append("- ")
                append(item)
                append('\n')
            }
        }
        pushContextLog(
            prompt = prompt,
            modelId = modelId,
            provider = provider,
            sectionTitle = when (mode) {
                RoleplayMemoryCondenseMode.CHARACTER -> "角色记忆精炼"
                RoleplayMemoryCondenseMode.SCENE -> "场景记忆精炼"
            },
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "记忆汇总失败",
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
            return normalizedItems.take(maxItems.coerceAtLeast(1))
        }
        val parsedArray = runCatching {
            JsonParser.parseString(content).asJsonArray.mapNotNull { element ->
                runCatching { element.asString.trim() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
        return (parsedArray ?: content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() })
            .distinct()
            .take(maxItems.coerceAtLeast(1))
    }

    private fun buildExtractionPrompt(
        conversationExcerpt: String,
        existingMemories: List<String>,
        userName: String,
        characterName: String,
        override: String,
    ): String {
        val knownMemoriesBlock = buildKnownMemoriesBlock(existingMemories)
        val template = override.trim().ifEmpty { MemoryPromptDefaults.EXTRACTION_PROMPT_TEMPLATE }
        return renderExtractionTemplate(
            template = template,
            conversationExcerpt = conversationExcerpt,
            knownMemoriesBlock = knownMemoriesBlock,
            userName = userName,
            characterName = characterName,
        )
    }

    private fun renderExtractionTemplate(
        template: String,
        conversationExcerpt: String,
        knownMemoriesBlock: String,
        userName: String,
        characterName: String,
    ): String {
        val containsConversationToken = CONVERSATION_PLACEHOLDER_REGEX.containsMatchIn(template)
        val containsKnownMemoriesToken = KNOWN_MEMORIES_PLACEHOLDER_REGEX.containsMatchIn(template)
        var rendered = template
            .let { CONVERSATION_PLACEHOLDER_REGEX.replace(it) { conversationExcerpt } }
            .let { KNOWN_MEMORIES_PLACEHOLDER_REGEX.replace(it) { knownMemoriesBlock } }
        rendered = ContextPlaceholderResolver.resolve(
            text = rendered,
            userName = userName,
            characterName = characterName,
        )
        // 用户模板未声明 conversation 占位符时尾部自动追加，避免没把对话喂给模型。
        if (!containsConversationToken && conversationExcerpt.isNotBlank()) {
            rendered = buildString {
                append(rendered.trimEnd())
                append('\n')
                append(conversationExcerpt)
            }
        }
        // 用户模板未声明 known_memories 占位符且确有已知记忆时尾部追加，避免重复输出。
        if (!containsKnownMemoriesToken && knownMemoriesBlock.isNotEmpty()) {
            rendered = buildString {
                append(rendered.trimEnd())
                append('\n')
                append(knownMemoriesBlock)
            }
        }
        return rendered
    }

    private fun buildKnownMemoriesBlock(existingMemories: List<String>): String {
        val cleaned = existingMemories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleaned.isEmpty()) return ""
        return buildString {
            append("# 已知信息处理 [重要]\n")
            append("以下 <已知信息> 是当前已经存在的长期记忆，请把新信息与已知信息逐条比对，")
            append("如果信息相同或冲突，必须忽略，不要重复输出。\n")
            append("<已知信息>\n")
            cleaned.forEach { item ->
                append("- ")
                append(item)
                append('\n')
            }
            append("</已知信息>\n")
        }
    }

    private fun StringBuilder.appendKnownMemoriesSection(existingMemories: List<String>) {
        val cleaned = existingMemories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleaned.isEmpty()) return
        append("# 已知信息处理 [重要]\n")
        append("以下 <已知信息> 是当前已经存在的长期记忆，请把新信息与已知信息逐条比对，")
        append("如果信息相同或冲突，必须忽略，不要重复输出。\n")
        append("<已知信息>\n")
        cleaned.forEach { item ->
            append("- ")
            append(item)
            append('\n')
        }
        append("</已知信息>\n")
    }

    private fun pushContextLog(
        prompt: String,
        modelId: String,
        provider: ProviderSettings?,
        sectionTitle: String,
    ) {
        val store = contextLogStore ?: return
        val tokens = estimateContextTokenCount(prompt)
        store.push(
            ContextGovernanceSnapshot(
                providerLabel = provider?.name.orEmpty(),
                modelLabel = modelId,
                promptModeLabel = "长记忆",
                generatedAt = System.currentTimeMillis(),
                rawDebugDump = prompt,
                estimatedContextTokens = tokens,
                contextSections = listOf(
                    ContextLogSection(
                        sourceType = ContextLogSourceType.SYSTEM_RULE,
                        title = sectionTitle,
                        content = prompt,
                        tokenEstimate = tokens,
                    ),
                ),
            ),
        )
    }

    companion object {
        private val CONVERSATION_PLACEHOLDER_REGEX =
            Regex("""\{\{\s*conversation\s*\}\}""", RegexOption.IGNORE_CASE)
        private val KNOWN_MEMORIES_PLACEHOLDER_REGEX =
            Regex("""\{\{\s*known_memories\s*\}\}""", RegexOption.IGNORE_CASE)
    }
}
