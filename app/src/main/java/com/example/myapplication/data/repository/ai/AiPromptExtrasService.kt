package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.conversation.PhoneGenerationContext
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.model.PhoneSnapshotSections
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplaySuggestionUiModel

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
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<RoleplayDiaryDraft> = emptyList()

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

    /**
     * 用户在动态下评论后，AI 生成 1-3 条角色/NPC 回复。
     * 返回 List<Pair<authorName, text>>。
     */
    suspend fun generateSocialCommentReplies(
        context: PhoneGenerationContext,
        postAuthorName: String,
        postAuthorLabel: String,
        postContent: String,
        existingComments: String,
        userComment: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<Pair<String, String>> = emptyList()
}

/**
 * T6.8 — Thin facade：每个 override 只是把入参原样转发给对应的子服务。
 *
 * 主构造器接收 6 个子服务；便利构造器按原有 3 参数方式接入（测试代码和旧 AppGraph 用），
 * 内部自行实例化 [PromptExtrasCore] 与 6 个子服务，保留旧调用现场不破坏。
 *
 * 具体领域实现：
 * - [TitleAndChatSuggestionPromptService]（标题 / 聊天建议）
 * - [ConversationSummaryPromptService]（普通 / 剧情摘要）
 * - [MemoryProposalPromptService]（通用记忆 / RP 结构化记忆 / 记忆精炼）
 * - [RoleplaySuggestionPromptService]（剧情建议）
 * - [RoleplayDiaryPromptService]（角色日记 / 礼物生图提示词）
 * - [PhoneContentPromptService]（手机快照 / 搜索详情 / 动态评论回复）
 */
class DefaultAiPromptExtrasService internal constructor(
    private val titleService: TitleAndChatSuggestionPromptService,
    private val summaryService: ConversationSummaryPromptService,
    private val memoryService: MemoryProposalPromptService,
    private val suggestionService: RoleplaySuggestionPromptService,
    private val diaryService: RoleplayDiaryPromptService,
    private val phoneService: PhoneContentPromptService,
) : AiPromptExtrasService {

    constructor(
        apiServiceFactory: ApiServiceFactory,
        apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
            apiServiceFactory.create(
                baseUrl = baseUrl,
                apiKey = apiKey,
            )
        },
        anthropicApiProvider: (String, String) -> AnthropicApi = { baseUrl, apiKey ->
            apiServiceFactory.createAnthropic(
                baseUrl = baseUrl,
                apiKey = apiKey,
            )
        },
    ) : this(
        core = PromptExtrasCore(
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = apiServiceProvider,
            anthropicApiProvider = anthropicApiProvider,
        ),
    )

    internal constructor(core: PromptExtrasCore) : this(
        titleService = TitleAndChatSuggestionPromptService(core),
        summaryService = ConversationSummaryPromptService(core),
        memoryService = MemoryProposalPromptService(core),
        suggestionService = RoleplaySuggestionPromptService(core),
        diaryService = RoleplayDiaryPromptService(core),
        phoneService = PhoneContentPromptService(core),
    )

    override suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = titleService.generateTitle(
        firstUserMessage = firstUserMessage,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> = titleService.generateChatSuggestions(
        conversationSummary = conversationSummary,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = summaryService.generateConversationSummary(
        conversationText = conversationText,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = summaryService.generateRoleplayConversationSummary(
        conversationText = conversationText,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> = memoryService.generateMemoryEntries(
        conversationExcerpt = conversationExcerpt,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): StructuredMemoryExtractionResult = memoryService.generateRoleplayMemoryEntries(
        conversationExcerpt = conversationExcerpt,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

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
    ): List<RoleplaySuggestionUiModel> = suggestionService.generateRoleplaySuggestions(
        conversationExcerpt = conversationExcerpt,
        systemPrompt = systemPrompt,
        playerStyleReference = playerStyleReference,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
        longformMode = longformMode,
    )

    override suspend fun generateRoleplayDiaries(
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
    ): List<RoleplayDiaryDraft> = diaryService.generateRoleplayDiaries(
        characterContext = characterContext,
        scenarioContext = scenarioContext,
        conversationExcerpt = conversationExcerpt,
        characterName = characterName,
        userName = userName,
        todayLabel = todayLabel,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

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
    ): String = diaryService.generateGiftImagePrompt(
        giftName = giftName,
        recipientName = recipientName,
        userName = userName,
        assistantName = assistantName,
        contextExcerpt = contextExcerpt,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> = memoryService.condenseRoleplayMemories(
        memoryItems = memoryItems,
        mode = mode,
        maxItems = maxItems,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generatePhoneSnapshotSections(
        context: PhoneGenerationContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot?,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSnapshotSections = phoneService.generatePhoneSnapshotSections(
        context = context,
        requestedSections = requestedSections,
        existingSnapshot = existingSnapshot,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generatePhoneSearchDetail(
        context: PhoneGenerationContext,
        query: String,
        relatedContext: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSearchDetail = phoneService.generatePhoneSearchDetail(
        context = context,
        query = query,
        relatedContext = relatedContext,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    override suspend fun generateSocialCommentReplies(
        context: PhoneGenerationContext,
        postAuthorName: String,
        postAuthorLabel: String,
        postContent: String,
        existingComments: String,
        userComment: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<Pair<String, String>> = phoneService.generateSocialCommentReplies(
        context = context,
        postAuthorName = postAuthorName,
        postAuthorLabel = postAuthorLabel,
        postContent = postContent,
        existingComments = existingComments,
        userComment = userComment,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )
}
