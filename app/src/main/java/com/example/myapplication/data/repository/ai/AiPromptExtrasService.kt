package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.conversation.PhoneGenerationContext
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageItem
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneRelationshipHighlight
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.model.PhoneSnapshotSections
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Collections

private const val ROLEPLAY_TEMPERATURE = 0.9f
private const val ROLEPLAY_TOP_P = 0.92f
private val UnsupportedSamplingMessageHints = listOf(
    "temperature",
    "top_p",
    "top p",
    "unknown parameter",
    "unknown field",
    "unrecognized field",
    "not permitted",
    "not allowed",
)

private data class PromptExtrasRoleplaySamplingConfig(
    val temperature: Float,
    val topP: Float,
)

interface AiPromptExtrasService {
    suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>

    suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>

    suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): StructuredMemoryExtractionResult

    suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
        longformMode: Boolean = false,
    ): List<RoleplaySuggestionUiModel>

    suspend fun generateGiftImagePrompt(
        giftName: String,
        recipientName: String,
        userName: String,
        assistantName: String,
        contextExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String = ""

    suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>

    suspend fun generatePhoneSnapshotSections(
        context: PhoneGenerationContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot?,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): PhoneSnapshotSections = PhoneSnapshotSections()

    suspend fun generatePhoneSearchDetail(
        context: PhoneGenerationContext,
        query: String,
        relatedContext: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): PhoneSearchDetail = PhoneSearchDetail(
        title = query.trim(),
        summary = "",
        content = "",
    )
}

class DefaultAiPromptExtrasService(
    private val apiServiceFactory: ApiServiceFactory,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        apiServiceFactory.create(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val anthropicApiProvider: (String, String) -> AnthropicApi = { baseUrl, apiKey ->
        apiServiceFactory.createAnthropic(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
) : AiPromptExtrasService {
    private val roleplaySamplingDisabledBaseUrls = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = "请用不超过15个字总结以下对话的主题，只输出标题文字，不要带引号或标点：\n$firstUserMessage"
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "标题生成失败",
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
        )
        if (content.isBlank()) {
            throw IllegalStateException("标题模型未返回有效内容")
        }
        return content.take(20)
    }

    override suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val prompt = "基于以下对话，生成3个简短的后续问题建议，每个建议不超过20个字，用换行分隔，只输出建议文字：\n$conversationSummary"
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "建议生成失败",
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
        )
        return content.lines()
            .map { it.trim().removePrefix("-").removePrefix("·").trim() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(3)
    }

    override suspend fun generateConversationSummary(
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
        val content = requestCompletionContent(
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

    override suspend fun generateRoleplayConversationSummary(
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
            append("保留明确人物关系、地点、任务进度、情绪转折和悬念。")
            append("不要输出 XML、不要解释规则、不要省略小节标题：\n")
            append(conversationText)
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 摘要生成失败",
            request = buildRequestWithRoleplaySampling(
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

    override suspend fun generateMemoryEntries(
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
        val content = requestCompletionContent(
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

    override suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): StructuredMemoryExtractionResult {
        val prompt = buildString {
            append("你是沉浸式剧情记忆提取器。")
            append("请从下面的剧情记录中提取两类记忆。")
            append("persistent_memories 用来保存长期稳定事实、关系、偏好和设定。")
            append("scene_state_memories 用来保存当前剧情线的地点、任务进度、关键事件、关系阶段和线索。")
            append("忽略寒暄、重复信息、一次性情绪和无长期价值的细节。")
            append("若某一类没有内容，返回空数组。")
            append("只输出 JSON 对象：")
            append("{\"persistent_memories\":[...],\"scene_state_memories\":[...]}。")
            append("每项都必须是简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
        val content = requestCompletionContent(
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

    override suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        longformMode: Boolean,
    ): List<RoleplaySuggestionUiModel> {
        val requestMessages = RoleplaySuggestionSupport.buildRequestMessages(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = systemPrompt,
            playerStyleReference = playerStyleReference,
            longformMode = longformMode,
        )
        val initialSuggestions = requestRoleplaySuggestions(
            requestMessages = requestMessages,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            provider = provider,
        )
        if (!RoleplaySuggestionSupport.shouldRetry(initialSuggestions)) {
            return initialSuggestions
        }
        val retryMessages = RoleplaySuggestionSupport.buildRequestMessages(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = systemPrompt,
            playerStyleReference = playerStyleReference,
            longformMode = longformMode,
            rejectedSuggestions = initialSuggestions,
        )
        val retriedSuggestions = requestRoleplaySuggestions(
            requestMessages = retryMessages,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            provider = provider,
        )
        return retriedSuggestions.ifEmpty { initialSuggestions }
    }

    override suspend fun generateGiftImagePrompt(
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
        return requestCompletionContent(
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

    override suspend fun condenseRoleplayMemories(
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
            append("输出不超过 ")
            append(maxItems.coerceAtLeast(1))
            append(" 条简体中文短句。只输出 JSON 数组，不要解释：\n")
            normalizedItems.forEach { item ->
                append("- ")
                append(item)
                append('\n')
            }
        }
        val content = requestCompletionContent(
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

    override suspend fun generatePhoneSnapshotSections(
        context: PhoneGenerationContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot?,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSnapshotSections {
        val normalizedSections = requestedSections.ifEmpty { PhoneSnapshotSection.entries.toSet() }
        val prompt = buildString {
            append("你是“查手机玩法”的内容生成器。")
            append(phoneSnapshotOwnerInstruction(context))
            append("所有内容必须基于给定的人设、记忆、关系、剧情和最近对话自然推导，可以适度扩展，但不能脱离上下文乱编。")
            append(phoneSnapshotAuthenticityInstruction(context))
            append("手机主人：").append(context.ownerName).append("。")
            append("查看者：").append(context.viewerName).append("。")
            append("关系方向：").append(context.relationshipDirection).append("。")
            if (context.timeGapContext.isNotBlank()) {
                append("\n时间间隔：").append(context.timeGapContext)
            }
            append("\n本次只重建这些板块：")
            append(normalizedSections.joinToString("、") { it.displayName })
            append("。")
            append("\n如果重建“消息”板块，必须同时输出 relationship_highlights 和 message_threads。")
            append("\n严格输出 JSON 对象，不要 Markdown，不要解释。")
            append("""JSON 键固定为：relationship_highlights、message_threads、notes、gallery、shopping_records、search_history。""")
            append("\n每个数组项都必须包含 id 字段。")
            append("\n字段要求：")
            append("\n1. relationship_highlights: [{id,name,relation_label,stance,note}]")
            append("\n2. message_threads: [{id,contact_name,relation_label,preview,time_label,avatar_label,messages:[{id,sender_name,text,time_label,is_owner}]}]")
            append("\n3. notes: [{id,title,summary,content,time_label,icon}]")
            append("\n4. gallery: [{id,title,summary,description,time_label}]")
            append("\n5. shopping_records: [{id,title,status,price_label,note,detail,time_label}]")
            append("\n6. search_history: [{id,query,time_label}]")
            append("\n其中相册需要额外遵守这些规则：")
            append("\n- title 要像手机相册里真正会起的照片名，短、具体、带对象或场景。")
            append("\n- summary 是一句简短的画面摘要，突出镜头主体、场景或拍摄瞬间，控制在 12 到 30 字。")
            append("\n- description 不是重复 summary，而是要写成“照片里具体拍到了什么 + 为什么拍下这张照片/这张照片背后的故事或情绪”。")
            append("\n- description 默认使用第一人称，更像手机主人回看照片时对这张图的私人解释。")
            append("\n- description 要有明确画面细节，例如姿势、构图、光线、衣着、地点、视角、当时的小动作。")
            append("\n- description 允许带轻微私密感和暧昧感，但必须仍然像相册说明，不要写成露骨对白或纯欲望宣泄。")
            append("\n- 优先生成值得收藏、带明显关系痕迹或个人执念的照片，不要生成泛泛的风景照。")
            append("\n- messages[].is_owner 表示该消息是否由手机主人本人发出。")
            append(phoneSnapshotOwnerRules(context))
            append("\n除非本次重建该板块，否则对应键返回 []。")
            if (context.systemContext.isNotBlank()) {
                append("\n\n【基础设定】\n")
                append(context.systemContext)
            }
            if (context.scenarioContext.isNotBlank()) {
                append("\n\n【场景补充】\n")
                append(context.scenarioContext)
            }
            if (context.conversationExcerpt.isNotBlank()) {
                append("\n\n【最近上下文】\n")
                append(context.conversationExcerpt)
            }
            existingSnapshot?.takeIf { it.hasContent() }?.let { snapshot ->
                append("\n\n【当前已存在的手机内容，仅供保持一致】\n")
                append(buildPhoneSnapshotReference(snapshot, excludeSections = normalizedSections))
            }
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "手机内容生成失败",
            request = buildRequestWithRoleplaySampling(
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
            return PhoneSnapshotSections()
        }
        val parsedJson = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
            ?: return PhoneSnapshotSections()
        return parsePhoneSnapshotSections(parsedJson)
    }

    override suspend fun generatePhoneSearchDetail(
        context: PhoneGenerationContext,
        query: String,
        relatedContext: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSearchDetail {
        val prompt = buildString {
            append("你是“查手机玩法”的搜索详情生成器。")
            append(phoneSearchDetailOwnerInstruction(context))
            append("详情必须像搜索结果页、百科摘要、帖子整理或经验总结，不要写成第一人称自白。")
            append("手机主人：").append(context.ownerName).append("。")
            append("查看者：").append(context.viewerName).append("。")
            append("\n搜索词：").append(query.trim())
            append("\n严格输出 JSON 对象：{")
            append("\"title\":\"...\",\"summary\":\"...\",\"content\":\"...\"}")
            append("。不要输出额外解释。")
            if (context.systemContext.isNotBlank()) {
                append("\n\n【基础设定】\n")
                append(context.systemContext)
            }
            if (context.scenarioContext.isNotBlank()) {
                append("\n\n【场景补充】\n")
                append(context.scenarioContext)
            }
            if (context.conversationExcerpt.isNotBlank()) {
                append("\n\n【最近上下文】\n")
                append(context.conversationExcerpt)
            }
            if (relatedContext.isNotBlank()) {
                append("\n\n【与该搜索词直接相关的手机线索】\n")
                append(relatedContext)
            }
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "搜索详情生成失败",
            request = buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        val parsedJson = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
        return PhoneSearchDetail(
            title = parsedJson?.get("title")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                .ifBlank { query.trim() },
            summary = parsedJson?.get("summary")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty(),
            content = parsedJson?.get("content")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                .ifBlank { content },
        )
    }

    private suspend fun requestCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        allowRoleplaySamplingFallback: Boolean = false,
    ): String {
        return when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> requestOpenAiCompletionContent(
                baseUrl = baseUrl,
                apiKey = apiKey,
                operation = operation,
                request = request,
                provider = provider,
                allowRoleplaySamplingFallback = allowRoleplaySamplingFallback,
            )
            ProviderApiProtocol.ANTHROPIC -> requestAnthropicCompletionContent(
                baseUrl = baseUrl,
                apiKey = apiKey,
                operation = operation,
                request = request,
            )
        }
    }

    private suspend fun requestOpenAiCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
        provider: ProviderSettings?,
        allowRoleplaySamplingFallback: Boolean,
    ): String {
        val normalizedApiKey = apiKey.trim()
        var currentRequest = request
        val apiMode = provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        var response = if (apiMode == OpenAiTextApiMode.RESPONSES) {
            apiServiceProvider(baseUrl, normalizedApiKey).createResponseAt(
                buildOpenAiTextUrl(baseUrl, provider),
                ResponseApiSupport.buildRequest(currentRequest),
            )
        } else {
            apiServiceProvider(baseUrl, normalizedApiKey).createChatCompletionAt(
                buildOpenAiTextUrl(baseUrl, provider),
                currentRequest,
            )
        }
        var latestErrorDetail = if (!response.isSuccessful) {
            response.errorBody()?.string().orEmpty()
        } else {
            ""
        }
        if (apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS &&
            allowRoleplaySamplingFallback &&
            response.code() == 400 &&
            shouldRetryWithoutRoleplaySampling(
                request = currentRequest,
                errorDetail = latestErrorDetail,
            )
        ) {
            markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
            currentRequest = currentRequest.copy(
                temperature = null,
                topP = null,
            )
            response = apiServiceProvider(baseUrl, normalizedApiKey).createChatCompletionAt(
                buildOpenAiTextUrl(baseUrl, provider),
                currentRequest,
            )
            latestErrorDetail = if (!response.isSuccessful) {
                response.errorBody()?.string().orEmpty()
            } else {
                ""
            }
        }
        if (!response.isSuccessful) {
            throw PromptExtrasResponseSupport.buildHttpFailure(
                operation = operation,
                code = response.code(),
                errorDetail = latestErrorDetail,
                headers = response.headers(),
            )
        }
        return if (apiMode == OpenAiTextApiMode.RESPONSES) {
            val body = response.body() as? com.example.myapplication.model.ResponseApiResponse
                ?: throw IllegalStateException("$operation：响应体为空")
            ResponseApiSupport.parseResponse(body).content.trim()
        } else {
            val body = response.body() as? com.example.myapplication.model.ChatCompletionResponse
                ?: throw IllegalStateException("$operation：响应体为空")
            PromptExtrasResponseSupport.extractContentText(
                body.choices.firstOrNull()?.message?.content,
            ).trim()
        }
    }

    private suspend fun requestAnthropicCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
    ): String {
        val response = anthropicApiProvider(baseUrl, apiKey.trim()).createMessage(
            AnthropicProtocolSupport.buildMessageRequest(request),
        )
        if (!response.isSuccessful) {
            throw PromptExtrasResponseSupport.buildHttpFailure(
                operation = operation,
                code = response.code(),
                errorDetail = response.errorBody()?.string().orEmpty(),
                headers = response.headers(),
            )
        }
        val body = response.body() ?: throw IllegalStateException("$operation：响应体为空")
        return AnthropicProtocolSupport.extractContentText(body).trim()
    }

    private fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(baseUrl, apiProtocol)
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
        )
    }

    private fun resolveRoleplaySampling(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ): PromptExtrasRoleplaySamplingConfig? {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
        if (roleplaySamplingDisabledBaseUrls.contains(normalizedBaseUrl)) {
            return null
        }
        return PromptExtrasRoleplaySamplingConfig(
            temperature = ROLEPLAY_TEMPERATURE,
            topP = ROLEPLAY_TOP_P,
        )
    }

    private fun shouldRetryWithoutRoleplaySampling(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean {
        if (request.temperature == null && request.topP == null) {
            return false
        }
        val normalizedError = errorDetail.trim().lowercase()
        if (normalizedError.isBlank()) {
            return false
        }
        return UnsupportedSamplingMessageHints.any { hint ->
            normalizedError.contains(hint)
        }
    }

    private fun markRoleplaySamplingUnsupported(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ) {
        roleplaySamplingDisabledBaseUrls += apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
    }

    private fun buildOpenAiTextUrl(
        baseUrl: String,
        provider: ProviderSettings?,
    ): String {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val path = when (provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> provider?.resolvedChatCompletionsPath() ?: DEFAULT_CHAT_COMPLETIONS_PATH
            OpenAiTextApiMode.RESPONSES -> "/responses"
        }
        return normalizedBaseUrl.removeSuffix("/") + path
    }

    private suspend fun requestRoleplaySuggestions(
        requestMessages: List<ChatMessageDto>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<RoleplaySuggestionUiModel> {
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 建议生成失败",
            request = buildRequestWithRoleplaySampling(
                model = modelId,
                messages = requestMessages,
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        return RoleplaySuggestionParser.parse(content)
    }

    private fun buildPhoneSnapshotReference(
        snapshot: PhoneSnapshot,
        excludeSections: Set<PhoneSnapshotSection>,
    ): String {
        return buildString {
            if (PhoneSnapshotSection.MESSAGES !in excludeSections) {
                if (snapshot.relationshipHighlights.isNotEmpty()) {
                    appendLine("关系速览：")
                    snapshot.relationshipHighlights.take(4).forEach { item ->
                        append("- ")
                        append(item.name)
                        if (item.relationLabel.isNotBlank()) {
                            append("（")
                            append(item.relationLabel)
                            append("）")
                        }
                        if (item.note.isNotBlank()) {
                            append("：")
                            append(item.note)
                        }
                        appendLine()
                    }
                }
                if (snapshot.messageThreads.isNotEmpty()) {
                    appendLine("消息概览：")
                    snapshot.messageThreads.take(4).forEach { thread ->
                        append("- ")
                        append(thread.contactName)
                        append("：")
                        append(thread.preview)
                        appendLine()
                    }
                }
            }
            if (PhoneSnapshotSection.NOTES !in excludeSections && snapshot.notes.isNotEmpty()) {
                appendLine("备忘录概览：")
                snapshot.notes.take(4).forEach { note ->
                    append("- ")
                    append(note.title)
                    append("：")
                    append(note.summary)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.GALLERY !in excludeSections && snapshot.gallery.isNotEmpty()) {
                appendLine("相册概览：")
                snapshot.gallery.take(4).forEach { item ->
                    append("- ")
                    append(item.title)
                    append("：")
                    append(item.summary)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.SHOPPING !in excludeSections && snapshot.shoppingRecords.isNotEmpty()) {
                appendLine("购物概览：")
                snapshot.shoppingRecords.take(4).forEach { item ->
                    append("- ")
                    append(item.title)
                    append("：")
                    append(item.note)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.SEARCH !in excludeSections && snapshot.searchHistory.isNotEmpty()) {
                appendLine("搜索概览：")
                snapshot.searchHistory.take(6).forEach { item ->
                    append("- ")
                    append(item.query)
                    appendLine()
                }
            }
        }.trim()
    }

    private fun phoneSnapshotOwnerInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER ->
                "现在要为一个虚构角色生成他的手机内容快照。"
            com.example.myapplication.model.PhoneSnapshotOwnerType.USER ->
                "现在要为当前用户生成他的手机内容快照，这些内容会被角色翻看到，用来触发后续反应。"
        }
    }

    private fun phoneSnapshotAuthenticityInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER ->
                "输出必须像已经真实存在于这个角色手机里的内容，而不是总结报告。"
            com.example.myapplication.model.PhoneSnapshotOwnerType.USER ->
                "输出必须像已经真实存在于用户手机里的内容，而不是总结报告；可以优先挑选最能触发角色反应的线索，但不能写成角色自己的手机内容。"
        }
    }

    private fun phoneSnapshotOwnerRules(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER -> ""
            com.example.myapplication.model.PhoneSnapshotOwnerType.USER -> buildString {
                append("\n- 当手机主人是用户时，关系速览、消息、备忘录、相册、购物和搜索都必须从用户侧出发。")
                append("\n- 可以让角色本人、朋友、家人、同事成为用户的联系人，但不要把主体写成角色自己的社交圈和日常手机。")
                append("\n- 如果出现角色本人，也只能作为“用户手机里与角色有关的线索”出现。")
                append("\n- 可以增强戏剧触发点，但必须先保证这些内容像用户真的会留下的痕迹。")
            }
        }
    }

    private fun phoneSearchDetailOwnerInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            com.example.myapplication.model.PhoneSnapshotOwnerType.CHARACTER ->
                "请为角色手机里的一个搜索词生成点开后的详情内容。"
            com.example.myapplication.model.PhoneSnapshotOwnerType.USER ->
                "请为用户手机里的一个搜索词生成点开后的详情内容；搜索方向可以优先保留最能触发角色反应的线索，但主体必须仍是用户自己会搜的内容。"
        }
    }

    private fun parsePhoneSnapshotSections(
        jsonObject: JsonObject,
    ): PhoneSnapshotSections {
        return PhoneSnapshotSections(
            relationshipHighlights = jsonObject.getAsJsonArrayOrNull("relationship_highlights")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneRelationshipHighlight(
                        id = item.stringValue("id").ifBlank { "relationship-${index + 1}" },
                        name = item.stringValue("name"),
                        relationLabel = item.stringValue("relation_label"),
                        stance = item.stringValue("stance"),
                        note = item.stringValue("note"),
                    )
                }
                ?.filter { it.name.isNotBlank() },
            messageThreads = jsonObject.getAsJsonArrayOrNull("message_threads")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneMessageThread(
                        id = item.stringValue("id").ifBlank { "thread-${index + 1}" },
                        contactName = item.stringValue("contact_name"),
                        relationLabel = item.stringValue("relation_label"),
                        preview = item.stringValue("preview"),
                        timeLabel = item.stringValue("time_label"),
                        avatarLabel = item.stringValue("avatar_label"),
                        messages = item?.getAsJsonArrayOrNull("messages")
                            ?.mapIndexed { msgIndex, msgElement ->
                                val messageItem = msgElement.asJsonObjectOrNull()
                                PhoneMessageItem(
                                    id = messageItem.stringValue("id").ifBlank { "message-${index + 1}-${msgIndex + 1}" },
                                    senderName = messageItem.stringValue("sender_name"),
                                    text = messageItem.stringValue("text"),
                                    timeLabel = messageItem.stringValue("time_label"),
                                    isOwner = messageItem.booleanValue("is_owner"),
                                )
                            }
                            ?.filter { it.text.isNotBlank() }
                            .orEmpty(),
                    )
                }
                ?.filter { it.contactName.isNotBlank() },
            notes = jsonObject.getAsJsonArrayOrNull("notes")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneNoteEntry(
                        id = item.stringValue("id").ifBlank { "note-${index + 1}" },
                        title = item.stringValue("title"),
                        summary = item.stringValue("summary"),
                        content = item.stringValue("content"),
                        timeLabel = item.stringValue("time_label"),
                        icon = item.stringValue("icon"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            gallery = jsonObject.getAsJsonArrayOrNull("gallery")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneGalleryEntry(
                        id = item.stringValue("id").ifBlank { "gallery-${index + 1}" },
                        title = item.stringValue("title"),
                        summary = item.stringValue("summary"),
                        description = item.stringValue("description"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            shoppingRecords = jsonObject.getAsJsonArrayOrNull("shopping_records")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneShoppingEntry(
                        id = item.stringValue("id").ifBlank { "shopping-${index + 1}" },
                        title = item.stringValue("title"),
                        status = item.stringValue("status"),
                        priceLabel = item.stringValue("price_label"),
                        note = item.stringValue("note"),
                        detail = item.stringValue("detail"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            searchHistory = jsonObject.getAsJsonArrayOrNull("search_history")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneSearchEntry(
                        id = item.stringValue("id").ifBlank { "search-${index + 1}" },
                        query = item.stringValue("query"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.query.isNotBlank() },
        )
    }

    private fun JsonObject?.stringValue(key: String): String {
        if (this == null) {
            return ""
        }
        return this.get(key)
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.trim()
            .orEmpty()
    }

    private fun JsonObject?.booleanValue(key: String): Boolean {
        if (this == null) {
            return false
        }
        return this.get(key)
            ?.takeIf { !it.isJsonNull }
            ?.asBoolean
            ?: false
    }

    private fun JsonObject.getAsJsonArrayOrNull(key: String): JsonArray? {
        val value = get(key) ?: return null
        return if (value.isJsonArray) value.asJsonArray else null
    }

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
        return runCatching { asJsonObject }.getOrNull()
    }

}
