package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonParser

/**
 * 负责三种记忆类提取 / 归并任务：
 * - [generateMemoryEntries]：通用对话长期记忆，JSON 数组。
 * - [generateRoleplayMemoryEntries]：沉浸剧情三段式记忆（persistent / scene / mental state）。
 * - [condenseRoleplayMemories]：将零散的角色/场景记忆精炼为 maxItems 条以内。
 *
 * T6.4 从 DefaultAiPromptExtrasService 抽离。不使用 roleplay 采样（保留原语义）。
 */
internal class MemoryProposalPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val prompt = buildString {
            append("你是对话长期记忆提取器。")
            append("请从下面的最近对话中提取适合长期保存的内容，例如：用户偏好、稳定设定、人物关系、长期目标、持续约束。")
            append("忽略寒暄、一次性任务、临时情绪和重复信息。")
            append("如果没有值得记忆的内容，返回 []。")
            append("只输出 JSON 数组，每项都是一条简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
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
            append("3. 关系类记忆必须保留变化方向和强度（如\u201c关系升温\u201d\u201c产生芥蒂\u201d等）。\n")
            append("4. mental_state 应该是一句话概括角色当下心境，如\"表面冷淡但内心在意，因为昨天的吵架还在别扭\"。\n")
            append("只输出 JSON 对象：")
            append("{\"persistent_memories\":[...],\"scene_state_memories\":[...],\"mental_state\":\"...\"}。")
            append("每项都必须是简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
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
}
