package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiSettingsRepository
import com.example.myapplication.data.repository.moments.MomentsGenerationCoordinator
import com.example.myapplication.data.repository.moments.MomentsRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun publishUserPost_usesScenarioBoundMaskSnapshot() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository()
        val viewModel = createViewModel(momentsRepository)

        advanceUntilIdle()
        viewModel.publishUserPost("今天也在角色会话里")
        advanceUntilIdle()

        val post = momentsRepository.posts.single()
        assertEquals("纪念", post.authorName)
        assertEquals("mask-b-avatar", post.authorAvatarUri)
        assertEquals("user-mask-mask-b", post.authorId)
        assertEquals("纪念", viewModel.uiState.value.viewerName)
    }

    @Test
    fun addCommentAndLike_usesScenarioBoundMaskSnapshot() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository(
            initialPosts = listOf(
                MomentPost(
                    id = "post-1",
                    authorType = MomentAuthorType.ASSISTANT,
                    authorId = "assistant-1",
                    authorName = "角色",
                    content = "看到了新的风景",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val viewModel = createViewModel(momentsRepository)

        advanceUntilIdle()
        viewModel.addCommentToPost("post-1", "我也看到了")
        advanceUntilIdle()
        viewModel.toggleLikePost("post-1")
        advanceUntilIdle()

        val post = momentsRepository.getPost("post-1")!!
        val comment = post.comments.single()
        assertEquals("纪念", comment.authorName)
        assertEquals("mask-b-avatar", comment.authorAvatarUri)
        assertEquals("user-mask-mask-b", comment.authorId)
        assertEquals(listOf("纪念"), post.likedByNames)
    }

    @Test
    fun publishUserPost_acceptsComposerImageLocationAndSelectedMask() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository()
        val viewModel = createViewModel(momentsRepository)

        advanceUntilIdle()
        viewModel.publishUserPost(
            content = "今天在学校门口看到了晚霞",
            imageUri = "content://picked/image",
            location = "深圳大学",
            userPersonaMaskId = "mask-a",
        )
        advanceUntilIdle()

        val post = momentsRepository.posts.single()
        assertEquals("纪善", post.authorName)
        assertEquals("user-mask-mask-a", post.authorId)
        assertEquals("深圳大学", post.location)
        assertEquals("content://picked/image", post.media?.imageUri)
    }

    @Test
    fun updateCoverImage_savesMomentsCoverUri() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository()
        val viewModel = createViewModel(momentsRepository)

        advanceUntilIdle()
        viewModel.updateCoverImage(" content://cover/image ")
        advanceUntilIdle()

        assertEquals("content://cover/image", viewModel.uiState.value.momentsSettings.coverImageUri)
    }

    @Test
    fun refreshWithRandomPost_withoutEnabledAssistantShowsHint() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository()
        val viewModel = createViewModel(momentsRepository)

        advanceUntilIdle()
        viewModel.refreshWithRandomPost()
        advanceUntilIdle()

        assertEquals("还没有角色开启发朋友圈", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun init_doesNotWarmUpAssistantPostUntilManualRefresh() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val momentsRepository = FakeMomentsRepository()
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "moments-model",
        )
        val viewModel = createViewModel(
            momentsRepository = momentsRepository,
            settings = defaultMomentsTestSettings().copy(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "陆骁",
                        momentAutoPostEnabled = true,
                    ),
                ),
            ),
        )

        advanceUntilIdle()

        assertTrue(momentsRepository.posts.isEmpty())

        viewModel.refreshWithRandomPost()
        advanceUntilIdle()

        assertEquals(1, momentsRepository.posts.size)
        assertEquals("陆骁", momentsRepository.posts.single().authorName)
    }

    private fun createViewModel(
        momentsRepository: FakeMomentsRepository,
        settings: AppSettings = defaultMomentsTestSettings(),
    ): MomentsViewModel {
        val settingsStore = FakeSettingsStore(
            settings = settings,
        )
        val settingsRepository = DefaultAiSettingsRepository(settingsStore)
        val coordinator = MomentsGenerationCoordinator(
            momentsRepository = momentsRepository,
            settingsRepository = settingsRepository,
            aiPromptExtrasService = NoOpMomentsPromptExtrasService,
            aiGateway = NoOpMomentsAiGateway,
            imageSaver = { error("测试不生成图片") },
            nowProvider = { 100L },
        )
        return MomentsViewModel(
            scenarioId = "scene-1",
            settingsRepository = settingsRepository,
            settingsStore = settingsStore,
            momentsRepository = momentsRepository,
            momentsGenerationCoordinator = coordinator,
            roleplayRepository = FakeMomentsRoleplayRepository(
                RoleplayScenario(
                    id = "scene-1",
                    title = "测试会话",
                    userPersonaMaskId = "mask-b",
                ),
            ),
            nowProvider = { 100L },
        )
    }
}

private fun defaultMomentsTestSettings(): AppSettings {
    return AppSettings(
        userDisplayName = "全局用户",
        userPersonaMasks = listOf(
            UserPersonaMask(
                id = "mask-a",
                name = "纪善",
                avatarUri = "mask-a-avatar",
            ),
            UserPersonaMask(
                id = "mask-b",
                name = "纪念",
                avatarUri = "mask-b-avatar",
            ),
        ),
        defaultUserPersonaMaskId = "mask-a",
    )
}

private class FakeMomentsRepository(
    initialPosts: List<MomentPost> = emptyList(),
) : MomentsRepository {
    private val postStore = linkedMapOf<String, MomentPost>()
    private val timeline = MutableStateFlow<List<MomentPost>>(emptyList())

    init {
        initialPosts.forEach { postStore[it.id] = it }
        emitTimeline()
    }

    val posts: List<MomentPost>
        get() = timeline.value

    override fun observeTimeline(): Flow<List<MomentPost>> = timeline

    override suspend fun listTimeline(limit: Int): List<MomentPost> {
        return timeline.value.take(limit.coerceAtLeast(1))
    }

    override suspend fun getPost(postId: String): MomentPost? = postStore[postId]

    override suspend fun upsertPost(post: MomentPost) {
        postStore[post.id] = post
        emitTimeline()
    }

    override suspend fun deletePost(postId: String) {
        postStore.remove(postId)
        emitTimeline()
    }

    override suspend fun updatePostLikes(
        postId: String,
        likedByNames: List<String>,
        updatedAt: Long,
    ) {
        val post = postStore[postId] ?: return
        postStore[postId] = post.copy(
            likedByNames = likedByNames,
            updatedAt = updatedAt,
        )
        emitTimeline()
    }

    override suspend fun addComment(comment: MomentComment) {
        val post = postStore[comment.postId] ?: return
        postStore[post.id] = post.copy(
            comments = (post.comments.filterNot { it.id == comment.id } + comment)
                .sortedBy(MomentComment::createdAt),
        )
        emitTimeline()
    }

    override suspend fun addComments(comments: List<MomentComment>) {
        comments.forEach { addComment(it) }
    }

    override suspend fun upsertMedia(media: com.example.myapplication.model.MomentMedia) = Unit

    override suspend fun getMediaByPostId(postId: String): com.example.myapplication.model.MomentMedia? = null

    override suspend fun latestAssistantPostCreatedAt(assistantId: String): Long? = null

    private fun emitTimeline() {
        timeline.value = postStore.values.sortedByDescending(MomentPost::createdAt)
    }
}

private class FakeMomentsRoleplayRepository(
    private val scenario: RoleplayScenario?,
) : RoleplayRepository {
    override fun observeScenarios(): Flow<List<RoleplayScenario>> = flowOf(scenario?.let(::listOf).orEmpty())

    override fun observeChatSummaries(): Flow<List<RoleplayChatSummary>> {
        return flowOf(scenario?.let { RoleplayChatSummary(scenario = it) }?.let(::listOf).orEmpty())
    }

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> {
        return flowOf(scenario?.takeIf { it.id == scenarioId })
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> = flowOf(null)

    override fun observeSessions(): Flow<List<RoleplaySession>> = flowOf(emptyList())

    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> = flowOf(emptyList())

    override fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>> = flowOf(emptyList())

    override suspend fun listScenarios(): List<RoleplayScenario> = scenario?.let(::listOf).orEmpty()

    override suspend fun getScenario(scenarioId: String): RoleplayScenario? {
        return scenario?.takeIf { it.id == scenarioId }
    }

    override suspend fun upsertScenario(scenario: RoleplayScenario) = Unit

    override suspend fun deleteScenario(scenarioId: String) = Unit

    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult {
        throw UnsupportedOperationException()
    }

    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult {
        throw UnsupportedOperationException()
    }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? = null

    override suspend fun getSession(sessionId: String): RoleplaySession? = null

    override suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry> = emptyList()

    override suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<RoleplayDiaryDraft>,
    ): List<RoleplayDiaryEntry> = emptyList()

    override suspend fun getOnlineMeta(conversationId: String): RoleplayOnlineMeta? = null

    override suspend fun upsertOnlineMeta(meta: RoleplayOnlineMeta) = Unit

    override suspend fun deleteOnlineMeta(conversationId: String) = Unit

    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) = Unit
}

private object NoOpMomentsPromptExtrasService : AiPromptExtrasService {
    override suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = ""

    override suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> = emptyList()

    override suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = ""

    override suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String = ""

    override suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        existingMemories: List<String>,
        userName: String,
        characterName: String,
        extractionPromptOverride: String,
    ): List<String> = emptyList()

    override suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        existingMemories: List<String>,
    ): StructuredMemoryExtractionResult = StructuredMemoryExtractionResult()

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
    ): List<RoleplaySuggestionUiModel> = emptyList()

    override suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> = emptyList()
}

private object NoOpMomentsAiGateway : AiGateway {
    override suspend fun generateImage(
        prompt: String,
        modelId: String,
    ): List<ImageGenerationResult> = emptyList()

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): AssistantReply = AssistantReply("")

    override fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptMode: PromptMode,
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): Flow<ChatStreamEvent> = emptyFlow()

    override fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
        statusCardsEnabled: Boolean,
        hideStatusBlocksInBubble: Boolean,
    ): ParsedAssistantSpecialOutput {
        return ParsedAssistantSpecialOutput(content = content, parts = existingParts)
    }
}
