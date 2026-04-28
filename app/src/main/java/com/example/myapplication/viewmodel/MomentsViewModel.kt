package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.conversation.PhoneContextBuilder
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneSocialComment
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MomentsUiState(
    val posts: List<PhoneSocialPost> = emptyList(),
    val ownerName: String = "",
    val viewerName: String = "",
    val isLoading: Boolean = true,
    val isGeneratingReplies: Boolean = false,
    /** 正在为哪个帖子生成回复 */
    val replyingPostId: String = "",
    val errorMessage: String? = null,
)

class MomentsViewModel(
    private val initialConversationId: String,
    private val initialScenarioId: String,
    private val initialOwnerType: PhoneSnapshotOwnerType,
    private val settingsRepository: AiSettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val phoneContextBuilder: PhoneContextBuilder,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(MomentsUiState(isLoading = true))
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()

    /** 公共依赖：settings / conversation / scenario / assistant */
    private data class ContextDependencies(
        val settings: AppSettings,
        val conversation: Conversation,
        val scenario: RoleplayScenario?,
        val assistant: Assistant?,
    )

    private suspend fun resolveContextDependencies(): ContextDependencies {
        val currentSettings = settingsRepository.settingsFlow.first()
        val conversation = conversationRepository.getConversation(initialConversationId)
            ?: error("会话不存在")
        val scenario = initialScenarioId.takeIf { it.isNotBlank() }
            ?.let { roleplayRepository.getScenario(it) }
        val assistant = run {
            val scenarioAssistantId = scenario?.assistantId.orEmpty()
            val assistantId = scenarioAssistantId.ifBlank { conversation.assistantId }
            currentSettings.resolvedAssistants().firstOrNull { it.id == assistantId }
        } ?: currentSettings.activeAssistant()
        return ContextDependencies(
            settings = currentSettings,
            conversation = conversation,
            scenario = scenario,
            assistant = assistant,
        )
    }

    init {
        loadPosts()
    }

    private fun loadPosts() {
        if (initialConversationId.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            runCatching {
                val snapshot = phoneSnapshotRepository.getSnapshot(
                    conversationId = initialConversationId,
                    ownerType = initialOwnerType,
                )
                val deps = resolveContextDependencies()

                val ownerName = deps.scenario?.characterDisplayNameOverride?.trim()
                    .orEmpty()
                    .ifBlank { deps.assistant?.name?.trim().orEmpty() }
                    .ifBlank { "角色" }
                val viewerName = deps.settings.resolvedUserDisplayName()
                val resolvedOwnerName = when (initialOwnerType) {
                    PhoneSnapshotOwnerType.CHARACTER -> ownerName
                    PhoneSnapshotOwnerType.USER -> viewerName
                }
                _uiState.update { current ->
                    current.copy(
                        posts = snapshot?.socialPosts.orEmpty(),
                        ownerName = resolvedOwnerName,
                        viewerName = if (initialOwnerType == PhoneSnapshotOwnerType.USER) ownerName else viewerName,
                        isLoading = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message) }
            }
        }
    }

    fun toggleLikePost(postId: String) {
        val viewerName = _uiState.value.viewerName.ifBlank { "我" }
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id != postId) return@map post
            post.toggleLike(viewerName)
        }
        _uiState.update { it.copy(posts = updatedPosts) }
        persistPosts(updatedPosts)
    }

    fun addCommentToPost(postId: String, commentText: String) {
        if (commentText.isBlank()) return
        if (_uiState.value.isGeneratingReplies) return
        val viewerName = _uiState.value.viewerName.ifBlank { "我" }
        val comment = PhoneSocialComment(
            id = "user-comment-${System.currentTimeMillis()}",
            authorName = viewerName,
            text = commentText.trim(),
        )
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id != postId) return@map post
            post.copy(comments = post.comments + comment)
        }
        _uiState.update { it.copy(posts = updatedPosts) }
        persistPosts(updatedPosts)

        // 触发 AI 回复
        val post = updatedPosts.firstOrNull { it.id == postId } ?: return
        generateAiReplies(post, commentText)
    }

    private fun generateAiReplies(post: PhoneSocialPost, userComment: String) {
        _uiState.update { it.copy(isGeneratingReplies = true, replyingPostId = post.id) }
        viewModelScope.launch {
            runCatching {
                val deps = resolveContextDependencies()

                val activeProvider = deps.settings.resolveFunctionProvider(ProviderFunction.PHONE_SNAPSHOT)
                    ?: error("当前未配置可用提供商")
                val modelId = deps.settings.resolveFunctionModel(ProviderFunction.PHONE_SNAPSHOT)
                    .ifBlank { activeProvider.selectedModel.trim() }
                if (modelId.isBlank()) error("当前未配置可用模型")

                val messages = conversationRepository.listMessages(initialConversationId)
                val context = phoneContextBuilder.build(
                    settings = deps.settings,
                    assistant = deps.assistant,
                    conversation = deps.conversation,
                    recentMessages = messages,
                    scenario = deps.scenario,
                    ownerType = initialOwnerType,
                    nowProvider = nowProvider,
                )

                val existingComments = post.comments
                    .joinToString("\n") { "${it.authorName}：${it.text}" }

                val replies = aiPromptExtrasService.generateSocialCommentReplies(
                    context = context,
                    postAuthorName = post.authorName,
                    postAuthorLabel = post.authorLabel,
                    postContent = post.content,
                    existingComments = existingComments,
                    userComment = userComment,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = modelId,
                    apiProtocol = activeProvider.resolvedApiProtocol(),
                    provider = activeProvider,
                )

                if (replies.isNotEmpty()) {
                    val replyComments = replies.mapIndexed { index, (authorName, text) ->
                        PhoneSocialComment(
                            id = "ai-reply-${System.currentTimeMillis()}-$index",
                            authorName = authorName,
                            text = text,
                        )
                    }
                    val finalPosts = _uiState.value.posts.map { p ->
                        if (p.id != post.id) return@map p
                        p.copy(comments = p.comments + replyComments)
                    }
                    _uiState.update { it.copy(posts = finalPosts) }
                    persistPosts(finalPosts)
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = "回复生成失败：${throwable.message}") }
            }
            _uiState.update { it.copy(isGeneratingReplies = false, replyingPostId = "") }
        }
    }

    private fun persistPosts(posts: List<PhoneSocialPost>) {
        viewModelScope.launch {
            runCatching {
                val snapshot = phoneSnapshotRepository.getSnapshot(
                    conversationId = initialConversationId,
                    ownerType = initialOwnerType,
                ) ?: return@launch
                phoneSnapshotRepository.upsertSnapshot(
                    snapshot.copy(
                        socialPosts = posts,
                        updatedAt = nowProvider(),
                    ),
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(
            conversationId: String,
            scenarioId: String,
            ownerType: PhoneSnapshotOwnerType,
            settingsRepository: AiSettingsRepository,
            conversationRepository: ConversationRepository,
            roleplayRepository: RoleplayRepository,
            phoneSnapshotRepository: PhoneSnapshotRepository,
            aiPromptExtrasService: AiPromptExtrasService,
            phoneContextBuilder: PhoneContextBuilder,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                MomentsViewModel(
                    initialConversationId = conversationId,
                    initialScenarioId = scenarioId,
                    initialOwnerType = ownerType,
                    settingsRepository = settingsRepository,
                    conversationRepository = conversationRepository,
                    roleplayRepository = roleplayRepository,
                    phoneSnapshotRepository = phoneSnapshotRepository,
                    aiPromptExtrasService = aiPromptExtrasService,
                    phoneContextBuilder = phoneContextBuilder,
                )
            }
        }
    }
}
