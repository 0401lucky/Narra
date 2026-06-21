package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.moments.MomentsGenerationCoordinator
import com.example.myapplication.data.repository.moments.MomentsRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.MomentsSettings
import com.example.myapplication.model.ResolvedUserPersona
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.model.sanitizeMomentDisplayName
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MomentsUiState(
    val posts: List<MomentPost> = emptyList(),
    val viewerName: String = "",
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val isRefreshing: Boolean = false,
    val isGeneratingReplies: Boolean = false,
    val replyingPostId: String = "",
    val retryingImagePostId: String = "",
    val momentsSettings: MomentsSettings = MomentsSettings(),
    val userPersonaMasks: List<UserPersonaMask> = emptyList(),
    val selectedUserPersonaMaskId: String = "",
    val viewerAvatarUri: String = "",
    val errorMessage: String? = null,
)

class MomentsViewModel(
    private val scenarioId: String = "",
    private val settingsRepository: AiSettingsRepository,
    private val settingsStore: SettingsStore,
    private val momentsRepository: MomentsRepository,
    private val momentsGenerationCoordinator: MomentsGenerationCoordinator,
    private val roleplayRepository: RoleplayRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(MomentsUiState(isLoading = true))
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()

    init {
        observeTimeline()
    }

    private fun observeTimeline() {
        viewModelScope.launch {
            combine(
                settingsRepository.settingsFlow,
                momentsRepository.observeTimeline(),
                observeCurrentScenario(),
            ) { settings, posts, scenario ->
                val userPersona = resolveMomentUserPersona(settings, scenario)
                MomentsTimelineProjection(
                    posts = posts.withResolvedAvatars(settings, userPersona),
                    viewerName = userPersona.displayName,
                    viewerAvatarUri = userPersona.resolvedMomentAvatar(),
                    momentsSettings = settings.momentsSettings,
                    userPersonaMasks = settings.normalizedUserPersonaMasks(),
                    selectedUserPersonaMaskId = userPersona.sourceMaskId,
                )
            }.collect { projection ->
                _uiState.update {
                    it.copy(
                        posts = projection.posts,
                        viewerName = projection.viewerName,
                        viewerAvatarUri = projection.viewerAvatarUri,
                        momentsSettings = projection.momentsSettings,
                        userPersonaMasks = projection.userPersonaMasks,
                        selectedUserPersonaMaskId = projection.selectedUserPersonaMaskId,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun publishUserPost(
        content: String,
        imageUri: String = "",
        location: String = "",
        userPersonaMaskId: String = "",
    ) {
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank() || _uiState.value.isPublishing) return
        _uiState.update { it.copy(isPublishing = true) }
        viewModelScope.launch {
            var publishedUserPersona: ResolvedUserPersona? = null
            val createdPost = runCatching {
                val userPersona = resolveCurrentMomentUserPersona(maskId = userPersonaMaskId)
                publishedUserPersona = userPersona
                momentsGenerationCoordinator.publishUserPost(
                    content = normalizedContent,
                    imageUri = imageUri,
                    location = location,
                    userPersona = userPersona,
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("朋友圈发布失败"))
                }
            }.getOrNull()
            _uiState.update { it.copy(isPublishing = false) }
            if (createdPost != null) {
                _uiState.update {
                    it.copy(isGeneratingReplies = true, replyingPostId = createdPost.id)
                }
                runCatching {
                    momentsGenerationCoordinator.generateRepliesForPost(
                        postId = createdPost.id,
                        triggerText = "用户刚发布了这条朋友圈。",
                        isUserCommentTrigger = false,
                        userPersona = publishedUserPersona ?: resolveCurrentMomentUserPersona(),
                    )
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorMessage = throwable.toMomentsUiError("回复生成失败"))
                    }
                }
                _uiState.update { it.copy(isGeneratingReplies = false, replyingPostId = "") }
            }
        }
    }

    fun toggleLikePost(postId: String) {
        viewModelScope.launch {
            val viewerName = resolveCurrentMomentUserPersona().displayName
            val post = momentsRepository.getPost(postId) ?: return@launch
            val updated = post.toggleLike(viewerName)
            momentsRepository.updatePostLikes(
                postId = post.id,
                likedByNames = updated.likedByNames,
                updatedAt = nowProvider(),
            )
        }
    }

    fun deletePost(postId: String) {
        if (postId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                momentsRepository.deletePost(postId)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("朋友圈删除失败"))
                }
            }
        }
    }

    fun addCommentToPost(
        postId: String,
        commentText: String,
        replyToCommentId: String = "",
    ) {
        val normalizedText = commentText.trim()
        if (normalizedText.isBlank()) return
        if (_uiState.value.isGeneratingReplies) return
        _uiState.update { it.copy(isGeneratingReplies = true, replyingPostId = postId) }
        viewModelScope.launch {
            runCatching {
                val userPersona = resolveCurrentMomentUserPersona()
                val post = momentsRepository.getPost(postId)
                val replyTarget = post?.comments?.firstOrNull { it.id == replyToCommentId }
                val displayText = if (replyTarget != null) {
                    "回复 ${replyTarget.authorName}：$normalizedText"
                } else {
                    normalizedText
                }
                momentsRepository.addComment(
                    MomentComment(
                        id = "user-comment-${UUID.randomUUID()}",
                        postId = postId,
                        authorType = MomentAuthorType.USER,
                        authorId = userPersona.toMomentUserAuthorId(),
                        authorName = userPersona.displayName,
                        authorAvatarUri = userPersona.resolvedMomentAvatar(),
                        text = displayText,
                        createdAt = nowProvider(),
                    ),
                )
                momentsGenerationCoordinator.generateRepliesForPost(
                    postId = postId,
                    triggerText = replyTarget?.let { target ->
                        "用户回复了 ${target.authorName} 的评论「${target.text.take(80)}」：$normalizedText"
                    } ?: normalizedText,
                    userPersona = userPersona,
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("回复生成失败"))
                }
            }
            _uiState.update { it.copy(isGeneratingReplies = false, replyingPostId = "") }
        }
    }

    fun retryImage(postId: String) {
        if (_uiState.value.retryingImagePostId.isNotBlank()) return
        _uiState.update { it.copy(retryingImagePostId = postId) }
        viewModelScope.launch {
            runCatching {
                momentsGenerationCoordinator.retryImage(postId)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("图片重试失败"))
                }
            }
            _uiState.update { it.copy(retryingImagePostId = "") }
        }
    }

    fun generateDueAssistantPosts() {
        viewModelScope.launch {
            runCatching {
                momentsGenerationCoordinator.generateDueAssistantPosts(maxPosts = 2)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("朋友圈刷新失败"))
                }
            }
        }
    }

    fun refreshWithRandomPost() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching {
                momentsGenerationCoordinator.generateRandomAssistantPost()
            }.onSuccess { post ->
                if (post == null) {
                    _uiState.update {
                        it.copy(errorMessage = "还没有角色开启发朋友圈")
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("朋友圈刷新失败"))
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun updateCoverImage(uri: String) {
        viewModelScope.launch {
            val normalizedUri = uri.trim()
            val currentSettings = settingsRepository.settingsFlow.first().momentsSettings
            runCatching {
                settingsStore.saveMomentsSettings(
                    currentSettings.copy(coverImageUri = normalizedUri),
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toMomentsUiError("封面保存失败"))
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeCurrentScenario(): Flow<RoleplayScenario?> {
        val normalizedScenarioId = scenarioId.trim()
        if (normalizedScenarioId.isBlank()) {
            return flowOf(null)
        }
        return roleplayRepository.observeScenario(normalizedScenarioId)
    }

    private suspend fun resolveCurrentMomentUserPersona(maskId: String = ""): ResolvedUserPersona {
        val settings = settingsRepository.settingsFlow.first()
        if (maskId.isNotBlank()) {
            return settings.resolveUserPersona(maskId = maskId)
        }
        val scenario = scenarioId.trim()
            .takeIf(String::isNotBlank)
            ?.let { roleplayRepository.getScenario(it) }
        return resolveMomentUserPersona(settings, scenario)
    }

    private fun resolveMomentUserPersona(
        settings: AppSettings,
        scenario: RoleplayScenario?,
    ): ResolvedUserPersona {
        return settings.resolveUserPersona(maskId = scenario?.userPersonaMaskId.orEmpty())
    }

    private fun List<MomentPost>.withResolvedAvatars(
        settings: AppSettings,
        userPersona: ResolvedUserPersona,
    ): List<MomentPost> {
        val userAvatar = userPersona.resolvedMomentAvatar()
        val assistantAvatars = settings.resolvedAssistants().associate { assistant ->
            assistant.id to assistant.avatarUri
        }
        return map { post ->
            post.copy(
                authorName = post.authorName.sanitizedForMoment(post.authorId),
                authorAvatarUri = post.authorAvatarUri.ifBlank {
                    when (post.authorType) {
                        MomentAuthorType.USER -> userAvatar
                        MomentAuthorType.ASSISTANT -> assistantAvatars[post.authorId].orEmpty()
                        MomentAuthorType.NPC,
                        MomentAuthorType.SYSTEM -> ""
                    }
                },
                comments = post.comments.map { comment ->
                    comment.copy(
                        authorName = comment.authorName.sanitizedForMoment(comment.authorId),
                        authorAvatarUri = comment.authorAvatarUri.ifBlank {
                            when (comment.authorType) {
                                MomentAuthorType.USER -> userAvatar
                                MomentAuthorType.ASSISTANT -> assistantAvatars[comment.authorId].orEmpty()
                                MomentAuthorType.NPC,
                                MomentAuthorType.SYSTEM -> ""
                            }
                        },
                    )
                },
            )
        }
    }

    private fun Throwable.toMomentsUiError(fallback: String): String {
        return SensitiveTextRedactor.throwableMessageForUi(
            throwable = this,
            fallback = fallback,
        )
    }

    private fun String.sanitizedForMoment(stableKey: String): String {
        return sanitizeMomentDisplayName(
            name = this,
            stableKey = stableKey,
        )
    }

    private fun ResolvedUserPersona.toMomentUserAuthorId(): String {
        val maskId = sourceMaskId.trim()
        return if (maskId.isBlank()) "user" else "user-mask-$maskId"
    }

    private fun ResolvedUserPersona.resolvedMomentAvatar(): String {
        return avatarUrl.trim().ifBlank { avatarUri.trim() }
    }

    companion object {
        fun factory(
            scenarioId: String = "",
            settingsRepository: AiSettingsRepository,
            settingsStore: SettingsStore,
            momentsRepository: MomentsRepository,
            momentsGenerationCoordinator: MomentsGenerationCoordinator,
            roleplayRepository: RoleplayRepository,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                MomentsViewModel(
                    scenarioId = scenarioId,
                    settingsRepository = settingsRepository,
                    settingsStore = settingsStore,
                    momentsRepository = momentsRepository,
                    momentsGenerationCoordinator = momentsGenerationCoordinator,
                    roleplayRepository = roleplayRepository,
                )
            }
        }
    }
}

private data class MomentsTimelineProjection(
    val posts: List<MomentPost>,
    val viewerName: String,
    val viewerAvatarUri: String,
    val momentsSettings: MomentsSettings,
    val userPersonaMasks: List<UserPersonaMask>,
    val selectedUserPersonaMaskId: String,
)
