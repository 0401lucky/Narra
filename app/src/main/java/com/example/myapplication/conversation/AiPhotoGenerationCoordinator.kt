package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AiPhotoImageStatus
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ImagePromptPolishRequest
import com.example.myapplication.model.ImagePromptPurpose
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.aiPhotoDuplicateKey
import com.example.myapplication.model.aiPhotoDescription
import com.example.myapplication.model.aiPhotoImageStatus
import com.example.myapplication.model.fallbackPolishResult
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.withAiPhotoImageFailure
import com.example.myapplication.model.withAiPhotoImageGenerating
import com.example.myapplication.model.withAiPhotoImageSuccess
import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal fun sanitizeAiPhotoPromptTextForImageModel(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isBlank() || !AiPhotoUnsafeContentRegex.containsMatchIn(trimmed)) {
        return trimmed
    }
    val safeSegments = trimmed
        .split(AiPhotoPromptSegmentSplitRegex)
        .map { it.trim() }
        .filter { it.isNotBlank() && !AiPhotoUnsafeContentRegex.containsMatchIn(it) }
    return safeSegments
        .joinToString(separator = ", ")
        .ifBlank { SafeAiPhotoFallbackDescription }
}

data class AiPhotoGenerationRequest(
    val conversationId: String,
    val selectedModel: String,
    val messages: List<ChatMessage>,
    val settings: AppSettings,
    val targetMessageId: String = "",
    val targetActionId: String = "",
    val assistant: Assistant? = null,
    val scenario: RoleplayScenario? = null,
)

class AiPhotoGenerationCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiGateway: AiGateway,
    private val conversationRepository: ConversationRepository,
    private val imageSaver: suspend (String, String) -> SavedImageFile,
) {
    suspend fun generate(
        request: AiPhotoGenerationRequest,
        onUpdated: (List<ChatMessage>) -> Unit = {},
    ): List<ChatMessage>? {
        val provider = request.settings.resolveFunctionProvider(ProviderFunction.GIFT_IMAGE)
            ?: return null
        val imageModelId = request.settings.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
            .trim()
        if (request.conversationId.isBlank() || imageModelId.isBlank()) {
            return null
        }

        var latestMessages: List<ChatMessage>? = null
        val generatedKeysByMessageId = mutableMapOf<String, MutableSet<String>>()
        request.messages
            .filter { message ->
                message.conversationId == request.conversationId &&
                    message.role == MessageRole.ASSISTANT &&
                    message.status == MessageStatus.COMPLETED &&
                    (request.targetMessageId.isBlank() || message.id == request.targetMessageId)
            }
            .forEach { message ->
                message.parts.forEach { part ->
                    if (request.targetActionId.isNotBlank() && part.actionId != request.targetActionId) {
                        return@forEach
                    }
                    if (!part.shouldGenerateAiPhoto()) {
                        return@forEach
                    }
                    val duplicateKey = part.aiPhotoDuplicateKey()
                    if (
                        request.targetActionId.isBlank() &&
                        duplicateKey.isNotBlank() &&
                        !generatedKeysByMessageId
                            .getOrPut(message.id) { mutableSetOf() }
                            .add(duplicateKey)
                    ) {
                        return@forEach
                    }
                    latestMessages = generatePart(
                        request = request,
                        message = message,
                        part = part,
                        imageModelId = imageModelId,
                        provider = provider,
                        onUpdated = onUpdated,
                    ) ?: latestMessages
                }
            }
        return latestMessages
    }

    private suspend fun generatePart(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
        part: ChatMessagePart,
        imageModelId: String,
        provider: com.example.myapplication.model.ProviderSettings,
        onUpdated: (List<ChatMessage>) -> Unit,
    ): List<ChatMessage>? {
        val description = part.aiPhotoDescription()
        var latestMessages = conversationRepository.updateAiPhotoPart(
            conversationId = request.conversationId,
            messageId = message.id,
            actionId = part.actionId,
            selectedModel = request.selectedModel,
        ) { currentPart ->
            currentPart.withAiPhotoImageGenerating(imageModelId)
        }
        onUpdated(latestMessages)

        val result = runCatching {
            val finalPrompt = buildOptimizedPrompt(
                request = request,
                message = message,
                part = part,
                provider = provider,
            )
            val imageResult = withTimeout(ImageGenerationTimeoutMs) {
                aiGateway.generateImageWithProvider(
                    prompt = finalPrompt,
                    provider = provider,
                    modelId = imageModelId,
                ).firstOrNull() ?: error("生图接口未返回图片")
            }
            val savedImage = persistImage(
                imageResult = imageResult,
                messageId = message.id,
                actionId = part.actionId,
            )
            conversationRepository.updateAiPhotoPart(
                conversationId = request.conversationId,
                messageId = message.id,
                actionId = part.actionId,
                selectedModel = request.selectedModel,
            ) { currentPart ->
                currentPart.withAiPhotoImageSuccess(
                    imageUri = savedImage.path,
                    mimeType = savedImage.mimeType,
                    fileName = savedImage.fileName,
                )
            }
        }.getOrElse { throwable ->
            AppLogger.w(
                tag = TAG,
                message = "AI 照片生成失败 messageId=${message.id}, actionId=${part.actionId}, prompt=${description.take(48)}",
                throwable = throwable,
            )
            conversationRepository.updateAiPhotoPart(
                conversationId = request.conversationId,
                messageId = message.id,
                actionId = part.actionId,
                selectedModel = request.selectedModel,
            ) { currentPart ->
                currentPart.withAiPhotoImageFailure(buildErrorMessage(throwable))
            }
        }
        latestMessages = result
        onUpdated(latestMessages)
        return latestMessages
    }

    private suspend fun buildOptimizedPrompt(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
        part: ChatMessagePart,
        provider: com.example.myapplication.model.ProviderSettings,
    ): String {
        val promptModelId = provider.resolveFunctionModel(ProviderFunction.CHAT)
            .ifBlank { request.selectedModel }
            .trim()
        val optimizedPrompt = if (promptModelId.isBlank()) {
            null
        } else runCatching {
            withTimeout(PromptOptimizationTimeoutMs) {
                aiPromptExtrasService.generateAiPhotoImagePrompt(
                    photoDescription = part.aiPhotoDescription(),
                    assistantName = resolveAssistantName(request, message),
                    assistantPersona = buildAssistantPersona(request),
                    scenarioContext = buildScenarioContext(request),
                    conversationExcerpt = buildConversationExcerpt(request, message),
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelId = promptModelId,
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                )
            }
        }.getOrNull()
        val basePrompt = optimizedPrompt
            ?.trim()
            .orEmpty()
            .let(::sanitizeAiPhotoPromptTextForImageModel)
            .ifBlank { buildFallbackPrompt(request, message, part) }
        return polishPrompt(
            request = request,
            message = message,
            part = part,
            provider = provider,
            basePrompt = basePrompt,
            promptModelId = promptModelId,
        )
    }

    private suspend fun polishPrompt(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
        part: ChatMessagePart,
        provider: com.example.myapplication.model.ProviderSettings,
        basePrompt: String,
        promptModelId: String,
    ): String {
        val persona = sanitizeAiPhotoPromptTextForImageModel(buildAssistantPersona(request))
        val scenario = sanitizeAiPhotoPromptTextForImageModel(buildScenarioContext(request))
        val polishRequest = ImagePromptPolishRequest(
            purpose = ImagePromptPurpose.AI_PHOTO,
            basePrompt = basePrompt,
            subject = sanitizeAiPhotoPromptTextForImageModel(part.aiPhotoDescription()),
            styleHint = "realistic in-chat phone photo, natural camera framing, believable daily-life lighting",
            roleContext = persona,
            sceneContext = listOf(
                scenario,
                buildConversationExcerpt(request, message),
            ).filter(String::isNotBlank).joinToString(separator = "\n").take(MaxConversationExcerptLength),
        )
        if (promptModelId.isBlank()) {
            return polishRequest.fallbackPolishResult().finalPrompt()
        }
        return runCatching {
            withTimeout(PromptOptimizationTimeoutMs) {
                aiPromptExtrasService.polishImagePrompt(
                    request = polishRequest,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelId = promptModelId,
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                ).finalPrompt()
            }
        }.getOrElse {
            polishRequest.fallbackPolishResult().finalPrompt()
        }
    }

    private suspend fun persistImage(
        imageResult: ImageGenerationResult,
        messageId: String,
        actionId: String,
    ): SavedImageFile {
        if (imageResult.b64Data.isNotBlank()) {
            return imageSaver(
                imageResult.b64Data,
                "ai-photo-$messageId-$actionId",
            )
        }
        val remoteUrl = imageResult.url.trim()
        if (remoteUrl.isBlank()) {
            error("生图结果为空")
        }
        return SavedImageFile(
            path = remoteUrl,
            mimeType = "image/*",
            fileName = "ai-photo-$messageId-$actionId",
        )
    }

    private fun buildFallbackPrompt(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
        part: ChatMessagePart,
    ): String {
        val description = sanitizeAiPhotoPromptTextForImageModel(part.aiPhotoDescription())
            .ifBlank { SafeAiPhotoFallbackDescription }
        return buildString {
            append(description)
            append("\n\n")
            append("Render as a realistic in-chat photo that the character could genuinely send. ")
            append("Natural phone-camera composition, believable lighting, no text, no watermark, no logo, no UI frame. ")
            append("If a person, selfie, body, clothing, hand or face is visible, match the sender's character profile exactly and do not change gender. ")
            append("Keep the image safe, ordinary and suitable for general audiences: modest everyday clothing, neutral pose, daily-life framing. ")
            resolveAssistantName(request, message).takeIf { it.isNotBlank() }?.let { speakerName ->
                append("Sender: ")
                append(speakerName)
                append(". ")
            }
            sanitizeAiPhotoPromptTextForImageModel(buildAssistantPersona(request)).takeIf { it.isNotBlank() }?.let { persona ->
                append("Sender profile: ")
                append(persona.take(MaxPersonaLength))
                append(". ")
            }
            sanitizeAiPhotoPromptTextForImageModel(buildScenarioContext(request)).takeIf { it.isNotBlank() }?.let { scenario ->
                append("Scene context: ")
                append(scenario.take(MaxScenarioLength))
                append(".")
            }
        }.trim()
    }

    private fun resolveAssistantName(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
    ): String {
        return request.scenario?.characterDisplayNameOverride
            ?.trim()
            .orEmpty()
            .ifBlank { request.assistant?.name?.trim().orEmpty() }
            .ifBlank { message.speakerName.trim() }
            .ifBlank { "角色" }
    }

    private fun buildAssistantPersona(request: AiPhotoGenerationRequest): String {
        val assistant = request.assistant ?: return ""
        return listOf(
            assistant.description,
            assistant.systemPrompt,
            assistant.scenario,
            assistant.creatorNotes,
            assistant.tags.joinToString(separator = "、"),
        )
            .map { it.replace("\r\n", "\n").trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .take(MaxPersonaLength)
    }

    private fun buildScenarioContext(request: AiPhotoGenerationRequest): String {
        val scenario = request.scenario ?: return ""
        return buildString {
            scenario.title.trim().takeIf { it.isNotBlank() }?.let { title ->
                append("标题：").append(title)
            }
            scenario.description.trim().takeIf { it.isNotBlank() }?.let { description ->
                if (isNotBlank()) append('\n')
                append(description)
            }
        }.take(MaxScenarioLength)
    }

    private fun buildConversationExcerpt(
        request: AiPhotoGenerationRequest,
        message: ChatMessage,
    ): String {
        val targetIndex = request.messages.indexOfLast { it.id == message.id }
            .takeIf { it >= 0 }
            ?: request.messages.lastIndex
        return request.messages
            .take(targetIndex + 1)
            .takeLast(RecentMessageCount)
            .mapNotNull { item ->
                val text = item.parts.toContentMirror(
                    imageFallback = "图片",
                    specialFallback = "特殊玩法",
                ).ifBlank { item.content }.trim()
                if (text.isBlank()) {
                    null
                } else {
                    val role = when (item.role) {
                        MessageRole.USER -> "用户"
                        MessageRole.ASSISTANT -> item.speakerName.ifBlank { resolveAssistantName(request, message) }
                    }
                    "$role：${text.take(MaxExcerptMessageLength)}"
                }
            }
            .joinToString(separator = "\n")
            .take(MaxConversationExcerptLength)
    }

    private fun ChatMessagePart.shouldGenerateAiPhoto(): Boolean {
        return actionType == ChatActionType.AI_PHOTO &&
            actionId.isNotBlank() &&
            aiPhotoDescription().isNotBlank() &&
            aiPhotoImageStatus() != AiPhotoImageStatus.READY &&
            aiPhotoImageStatus() != AiPhotoImageStatus.GENERATING
    }

    private fun buildErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is TimeoutCancellationException -> "照片生成超时"
            else -> throwable.message
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(160)
                ?: "照片生成失败"
        }
    }

    private companion object {
        const val TAG = "AiPhotoGeneration"
        const val ImageGenerationTimeoutMs = 240_000L
        const val PromptOptimizationTimeoutMs = 15_000L
        const val RecentMessageCount = 8
        const val MaxExcerptMessageLength = 180
        const val MaxConversationExcerptLength = 900
        const val MaxPersonaLength = 900
        const val MaxScenarioLength = 500
    }
}

private val AiPhotoUnsafeContentRegex = Regex(
    pattern = "nsfw|nude|naked|sex|sexual|erotic|porn|pornographic|seductive|sexy|" +
        "cleavage|lingerie|underwear|nipple|nipples|genital|genitals|vagina|penis|boob|boobs|breast|breasts|" +
        "裸体|裸露|露点|色情|性爱|性行为|性交|做爱|性器官|下体|私处|乳头|乳房|胸部|胸罩|内衣|情趣|挑逗|诱惑|媚态|成人内容|擦边",
    option = RegexOption.IGNORE_CASE,
)

private val AiPhotoPromptSegmentSplitRegex = Regex("""[，,。.!！?？;；\n\r]+""")

private const val SafeAiPhotoFallbackDescription =
    "safe everyday phone-camera photo of the sender in the current scene"
