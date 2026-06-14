package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.moments.MomentsGenerationCoordinator
import com.example.myapplication.data.repository.moments.MomentsRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.sanitizeMomentDisplayName
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MomentsUiState(
    val posts: List<MomentPost> = emptyList(),
    val viewerName: String = "",
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val isGeneratingReplies: Boolean = false,
    val replyingPostId: String = "",
    val retryingImagePostId: String = "",
    val errorMessage: String? = null,
)

class MomentsViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val momentsRepository: MomentsRepository,
    private val momentsGenerationCoordinator: MomentsGenerationCoordinator,
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
        warmUpAutoPosts()
    }

    private fun observeTimeline() {
        viewModelScope.launch {
            combine(
                settingsRepository.settingsFlow,
                momentsRepository.observeTimeline(),
            ) { settings, posts ->
                settings to posts.withResolvedAvatars(settings)
            }.collect { (settings, posts) ->
                _uiState.update {
                    it.copy(
                        posts = posts,
                        viewerName = settings.resolvedUserDisplayName(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun warmUpAutoPosts() {
        viewModelScope.launch {
            runCatching {
                momentsGenerationCoordinator.generateDueAssistantPosts(maxPosts = 1)
            }
        }
    }

    fun publishUserPost(content: String) {
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank() || _uiState.value.isPublishing) return
        _uiState.update { it.copy(isPublishing = true) }
        viewModelScope.launch {
            val createdPost = runCatching {
                momentsGenerationCoordinator.publishUserPost(normalizedContent)
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
            val viewerName = _uiState.value.viewerName.ifBlank {
                settingsRepository.settingsFlow.first().resolvedUserDisplayName()
            }
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
                val settings = settingsRepository.settingsFlow.first()
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
                        authorId = "user",
                        authorName = settings.resolvedUserDisplayName(),
                        authorAvatarUri = settings.resolvedUserAvatar(),
                        text = displayText,
                        createdAt = nowProvider(),
                    ),
                )
                momentsGenerationCoordinator.generateRepliesForPost(
                    postId = postId,
                    triggerText = replyTarget?.let { target ->
                        "用户回复了 ${target.authorName} 的评论「${target.text.take(80)}」：$normalizedText"
                    } ?: normalizedText,
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

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun List<MomentPost>.withResolvedAvatars(settings: AppSettings): List<MomentPost> {
        val userAvatar = settings.resolvedUserAvatar()
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

    companion object {
        fun factory(
            settingsRepository: AiSettingsRepository,
            momentsRepository: MomentsRepository,
            momentsGenerationCoordinator: MomentsGenerationCoordinator,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                MomentsViewModel(
                    settingsRepository = settingsRepository,
                    momentsRepository = momentsRepository,
                    momentsGenerationCoordinator = momentsGenerationCoordinator,
                )
            }
        }
    }
}
