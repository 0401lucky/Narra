package com.example.myapplication.data.repository.moments

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MomentAssistantContext
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentComment
import com.example.myapplication.model.MomentCommentDraft
import com.example.myapplication.model.MomentMedia
import com.example.myapplication.model.MomentMediaStatus
import com.example.myapplication.model.MomentNpcFallbackNames
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ResolvedUserPersona
import com.example.myapplication.model.sanitizeMomentDisplayName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.UUID

class MomentsGenerationCoordinator(
    private val momentsRepository: MomentsRepository,
    private val settingsRepository: AiSettingsRepository,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiGateway: AiGateway,
    private val imageSaver: suspend (String) -> SavedImageFile,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun publishUserPost(
        content: String,
        userPersona: ResolvedUserPersona? = null,
    ): MomentPost {
        val settings = settingsRepository.settingsFlow.first()
        val resolvedUserPersona = userPersona ?: settings.resolveUserPersona()
        val now = nowProvider()
        val post = MomentPost(
            id = "user-moment-${UUID.randomUUID()}",
            authorType = MomentAuthorType.USER,
            authorId = resolvedUserPersona.toMomentUserAuthorId(),
            authorName = resolvedUserPersona.displayName,
            authorAvatarUri = resolvedUserPersona.resolvedMomentAvatar(),
            authorLabel = "我",
            content = content.trim(),
            createdAt = now,
            updatedAt = now,
        )
        momentsRepository.upsertPost(post)
        return post
    }

    suspend fun generateRepliesForPost(
        postId: String,
        triggerText: String,
        isUserCommentTrigger: Boolean = true,
        excludeAssistantIds: Set<String> = emptySet(),
        userPersona: ResolvedUserPersona? = null,
    ): List<MomentComment> {
        val settings = settingsRepository.settingsFlow.first()
        val resolvedUserPersona = userPersona ?: settings.resolveUserPersona()
        val post = momentsRepository.getPost(postId) ?: return emptyList()
        val provider = settings.resolveFunctionProvider(ProviderFunction.MOMENTS) ?: return emptyList()
        val modelId = settings.resolveFunctionModel(ProviderFunction.MOMENTS)
            .ifBlank { provider.selectedModel.trim() }
        if (modelId.isBlank()) return emptyList()

        val assistants = settings.resolvedAssistants()
            .filter { it.momentAutoCommentEnabled }
            .filterNot { it.id in excludeAssistantIds }
            .take(MaxReplyAssistantCount)
        val assistantAvatarUris = assistants.associate { assistant ->
            assistant.id to assistant.avatarUri
        }
        val assistantContexts = assistants
            .map { assistant ->
                MomentAssistantContext(
                    id = assistant.id,
                    name = assistant.momentDisplayName(),
                    persona = assistant.toMomentPersona(),
                    commentStyle = assistant.momentCommentStyle,
                )
            }
        if (assistantContexts.isEmpty()) return emptyList()

        val existingComments = post.comments.joinToString("\n") { comment ->
            "${comment.authorName.sanitizedForMoment(comment.authorId)}：${comment.text}"
        }
        val drafts = aiPromptExtrasService.generateMomentCommentReplies(
            assistants = assistantContexts,
            npcNames = buildMomentNpcCandidates(
                post = post,
                settings = settings,
                assistants = assistants,
                triggerText = triggerText,
                userName = resolvedUserPersona.displayName,
            ),
            postAuthorName = post.authorName.sanitizedForMoment(post.authorId),
            postAuthorType = post.authorType,
            postContent = post.content,
            existingComments = existingComments,
            userName = resolvedUserPersona.displayName,
            userComment = triggerText,
            isUserCommentTrigger = isUserCommentTrigger,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelId = modelId,
            apiProtocol = provider.resolvedApiProtocol(),
            provider = provider,
        )
        val now = nowProvider()
        val comments = drafts
            .dedupeByAuthorAndText()
            .take(3)
            .mapIndexed { index, draft ->
                MomentComment(
                    id = "moment-comment-${UUID.randomUUID()}",
                    postId = post.id,
                    authorType = draft.authorType,
                    authorId = draft.authorId,
                    authorName = draft.authorName.sanitizedForMoment(draft.authorId),
                    authorAvatarUri = if (draft.authorType == MomentAuthorType.ASSISTANT) {
                        assistantAvatarUris[draft.authorId].orEmpty()
                    } else {
                        ""
                    },
                    text = draft.text,
                    createdAt = now + index,
                )
            }
        momentsRepository.addComments(comments)
        return comments
    }

    suspend fun generateDueAssistantPosts(maxPosts: Int = 2): Int {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.momentsSettings.backgroundGenerationEnabled) {
            return 0
        }
        val dueAssistants = settings.resolvedAssistants()
            .filter { it.momentAutoPostEnabled }
            .filter { assistant -> isAssistantDueToPost(assistant) }
            .take(maxPosts.coerceAtLeast(1))
        var generatedCount = 0
        dueAssistants.forEach { assistant ->
            val post = runCatching {
                publishAssistantPost(settings, assistant)
            }.getOrNull()
            if (post != null) {
                generatedCount += 1
                generateRepliesForPost(
                    postId = post.id,
                    triggerText = "其他角色看到了这条朋友圈。",
                    isUserCommentTrigger = false,
                    excludeAssistantIds = setOf(assistant.id),
                )
            }
        }
        return generatedCount
    }

    suspend fun retryImage(postId: String): MomentMedia? {
        val settings = settingsRepository.settingsFlow.first()
        val post = momentsRepository.getPost(postId) ?: return null
        val assistant = settings.resolvedAssistants().firstOrNull { it.id == post.authorId }
            ?: return null
        if (!assistant.momentAutoImageEnabled) return null
        val existingPrompt = post.media?.prompt.orEmpty()
        val prompt = existingPrompt.ifBlank {
            buildFallbackImagePrompt(
                assistantName = post.authorName,
                content = post.content,
                persona = assistant.toMomentPersona(),
            )
        }
        val now = nowProvider()
        val media = MomentMedia(
            id = post.media?.id.orEmpty().ifBlank { "moment-media-${UUID.randomUUID()}" },
            postId = post.id,
            prompt = prompt,
            status = MomentMediaStatus.GENERATING,
            createdAt = post.media?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
        momentsRepository.upsertMedia(media)
        return generateImageForMedia(media)
    }

    private suspend fun publishAssistantPost(
        settings: AppSettings,
        assistant: Assistant,
    ): MomentPost {
        val provider = settings.resolveFunctionProvider(ProviderFunction.MOMENTS)
            ?: error("当前未配置朋友圈模型提供商")
        val modelId = settings.resolveFunctionModel(ProviderFunction.MOMENTS)
            .ifBlank { provider.selectedModel.trim() }
        if (modelId.isBlank()) error("当前未配置朋友圈模型")

        val recentMoments = momentsRepository.listTimeline(8).joinToString("\n") { post ->
            "${post.authorName.sanitizedForMoment(post.authorId)}：${post.content.take(60)}"
        }
        val assistantDisplayName = assistant.momentDisplayName()
        val resolvedUserPersona = settings.resolveUserPersona()
        val draft = aiPromptExtrasService.generateMomentPost(
            assistantName = assistantDisplayName,
            assistantPersona = assistant.toMomentPersona(),
            userName = resolvedUserPersona.displayName,
            recentMoments = recentMoments,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelId = modelId,
            apiProtocol = provider.resolvedApiProtocol(),
            provider = provider,
        )
        val now = nowProvider()
        val postId = "assistant-moment-${UUID.randomUUID()}"
        val media = if (assistant.momentAutoImageEnabled) {
            MomentMedia(
                id = "moment-media-${UUID.randomUUID()}",
                postId = postId,
                prompt = draft.imagePrompt.ifBlank {
                    buildFallbackImagePrompt(
                        assistantName = assistantDisplayName,
                        content = draft.content,
                        persona = assistant.toMomentPersona(),
                    )
                },
                status = MomentMediaStatus.GENERATING,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            null
        }
        val post = MomentPost(
            id = postId,
            authorType = MomentAuthorType.ASSISTANT,
            authorId = assistant.id,
            authorName = assistantDisplayName,
            authorAvatarUri = assistant.avatarUri,
            authorLabel = "角色",
            content = draft.content.ifBlank { "今天也想留下一点生活的痕迹。" }.take(MaxMomentContentLength),
            media = media,
            createdAt = now,
            updatedAt = now,
        )
        momentsRepository.upsertPost(post)
        media?.let { generateImageForMedia(it) }
        return post
    }

    private suspend fun generateImageForMedia(media: MomentMedia): MomentMedia {
        val settings = settingsRepository.settingsFlow.first()
        val provider = settings.resolveFunctionProvider(ProviderFunction.GIFT_IMAGE)
            ?: return media.markFailed("默认生图模型未配置")
        val imageModelId = settings.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
            .ifBlank { return media.markFailed("默认生图模型未配置") }
        return runCatching {
            val result = withTimeout(ImageGenerationTimeoutMs) {
                aiGateway.generateImage(
                    prompt = media.prompt,
                    modelId = imageModelId,
                ).firstOrNull() ?: error("生图接口未返回图片")
            }
            val savedImage = persistImage(result, media.id)
            val succeeded = media.copy(
                imageUri = savedImage.path,
                mimeType = savedImage.mimeType,
                fileName = savedImage.fileName,
                status = MomentMediaStatus.SUCCEEDED,
                errorMessage = "",
                updatedAt = nowProvider(),
            )
            momentsRepository.upsertMedia(succeeded)
            succeeded
        }.getOrElse { throwable ->
            media.markFailed(buildImageErrorMessage(throwable))
        }
    }

    private suspend fun MomentMedia.markFailed(message: String): MomentMedia {
        val failed = copy(
            imageUri = "",
            mimeType = "",
            fileName = "",
            status = MomentMediaStatus.FAILED,
            errorMessage = message,
            updatedAt = nowProvider(),
        )
        momentsRepository.upsertMedia(failed)
        return failed
    }

    private suspend fun isAssistantDueToPost(assistant: Assistant): Boolean {
        val latestCreatedAt = momentsRepository.latestAssistantPostCreatedAt(assistant.id)
            ?: return true
        return nowProvider() - latestCreatedAt >= assistant.momentAutoPostFrequency.minIntervalMillis
    }

    private suspend fun persistImage(
        imageResult: ImageGenerationResult,
        mediaId: String,
    ): SavedImageFile {
        if (imageResult.b64Data.isNotBlank()) {
            return imageSaver(imageResult.b64Data)
        }
        val remoteUrl = imageResult.url.trim()
        if (remoteUrl.isBlank()) {
            error("生图结果为空")
        }
        return SavedImageFile(
            path = remoteUrl,
            mimeType = "image/*",
            fileName = "moment-$mediaId",
        )
    }

    private fun Assistant.toMomentPersona(): String {
        return buildString {
            appendLine("姓名：${momentDisplayName()}")
            if (description.isNotBlank()) appendLine("简介：$description")
            if (systemPrompt.isNotBlank()) appendLine("人设：${systemPrompt.take(1200)}")
            if (scenario.isNotBlank()) appendLine("场景：${scenario.take(800)}")
            if (creatorNotes.isNotBlank()) appendLine("备注：${creatorNotes.take(500)}")
            if (tags.isNotEmpty()) appendLine("标签：${tags.joinToString("、")}")
        }.trim()
    }

    private fun buildFallbackImagePrompt(
        assistantName: String,
        content: String,
        persona: String,
    ): String {
        return buildString {
            append("真实手机随手拍风格，微信朋友圈配图。")
            append("发布者：")
            append(assistantName.ifBlank { "角色" })
            append("。朋友圈文案：")
            append(content.take(120))
            append("。")
            if (persona.isNotBlank()) {
                append("角色气质参考：")
                append(persona.take(180))
                append("。")
            }
            append("自然光，生活化构图，不要文字、水印、logo、边框、界面元素。")
        }
    }

    private fun buildImageErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is TimeoutCancellationException -> "图片生成超时"
            else -> throwable.message
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.take(40)
                ?: "图片生成失败"
        }
    }

    private fun List<MomentCommentDraft>.dedupeByAuthorAndText(): List<MomentCommentDraft> {
        return distinctBy { draft -> Triple(draft.authorType, draft.authorId, draft.text.trim()) }
    }

    private fun buildMomentNpcCandidates(
        post: MomentPost,
        settings: AppSettings,
        assistants: List<Assistant>,
        triggerText: String,
        userName: String,
    ): List<String> {
        val usedNames = buildSet {
            add(userName)
            add(post.authorName)
            post.comments.forEach { comment -> add(comment.authorName) }
            assistants.forEach { assistant -> add(assistant.momentDisplayName()) }
        }.map { name -> name.trim() }.filter { it.isNotBlank() }.toSet()
        val seed = "${post.id}|${triggerText.take(80)}|${post.comments.size}"
        return MomentNpcFallbackNames
            .filterNot { it in usedNames }
            .sortedBy { name -> "$seed|$name".hashCode() }
            .take(4)
    }

    private fun Assistant.momentDisplayName(): String {
        return sanitizeMomentDisplayName(
            name = name,
            stableKey = id,
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

    private companion object {
        const val MaxReplyAssistantCount = 6
        const val MaxMomentContentLength = 300
        const val ImageGenerationTimeoutMs = 240_000L
    }
}
