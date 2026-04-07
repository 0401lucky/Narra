package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.withGiftImageFailure
import com.example.myapplication.model.withGiftImageSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class GiftImageGenerationRequest(
    val conversationId: String,
    val selectedModel: String,
    val provider: ProviderSettings,
    val specialId: String,
    val giftName: String,
    val recipientName: String,
    val userName: String,
    val assistantName: String,
    val contextExcerpt: String,
)

class GiftImageGenerationCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiGateway: AiGateway,
    private val conversationRepository: ConversationRepository,
    private val imageSaver: suspend (String) -> SavedImageFile,
) {
    suspend fun generate(request: GiftImageGenerationRequest): List<ChatMessage>? {
        val giftModelId = request.provider.resolveFunctionModel(ProviderFunction.GIFT_IMAGE).trim()
        if (request.conversationId.isBlank() || request.specialId.isBlank() || giftModelId.isBlank()) {
            return null
        }

        val finalPrompt = buildOptimizedPrompt(request)
        return runCatching {
            val imageResult = withTimeout(ImageGenerationTimeoutMs) {
                aiGateway.generateImage(
                    prompt = finalPrompt,
                    modelId = giftModelId,
                ).firstOrNull() ?: error("生图接口未返回图片")
            }
            val savedImage = persistImage(imageResult, request.specialId)
            conversationRepository.updateGiftImagePart(
                conversationId = request.conversationId,
                specialId = request.specialId,
                selectedModel = request.selectedModel,
            ) { part ->
                part.withGiftImageSuccess(
                    imageUri = savedImage.path,
                    mimeType = savedImage.mimeType,
                    fileName = savedImage.fileName,
                )
            }
        }.getOrElse { throwable ->
            conversationRepository.updateGiftImagePart(
                conversationId = request.conversationId,
                specialId = request.specialId,
                selectedModel = request.selectedModel,
            ) { part ->
                part.withGiftImageFailure(
                    buildGiftImageErrorMessage(throwable),
                )
            }
        }
    }

    private suspend fun buildOptimizedPrompt(request: GiftImageGenerationRequest): String {
        val promptModelId = request.provider.resolveFunctionModel(ProviderFunction.CHAT)
            .ifBlank { request.selectedModel }
            .trim()
        if (promptModelId.isBlank()) {
            return buildFallbackPrompt(request)
        }

        val optimizedPrompt = runCatching {
            withTimeout(PromptOptimizationTimeoutMs) {
                aiPromptExtrasService.generateGiftImagePrompt(
                    giftName = request.giftName,
                    recipientName = request.recipientName,
                    userName = request.userName,
                    assistantName = request.assistantName,
                    contextExcerpt = request.contextExcerpt.take(MaxContextLength),
                    baseUrl = request.provider.baseUrl,
                    apiKey = request.provider.apiKey,
                    modelId = promptModelId,
                    apiProtocol = request.provider.resolvedApiProtocol(),
                    provider = request.provider,
                )
            }
        }.getOrNull()

        return optimizedPrompt.takeIf { !it.isNullOrBlank() }
            ?.trim()
            .orEmpty()
            .ifBlank { buildFallbackPrompt(request) }
    }

    private suspend fun persistImage(
        imageResult: ImageGenerationResult,
        specialId: String,
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
            fileName = "gift-$specialId",
        )
    }

    private fun buildFallbackPrompt(request: GiftImageGenerationRequest): String {
        return buildString {
            append("单个礼物主体特写，")
            append(request.giftName.trim())
            append("，送给 ")
            append(request.recipientName.trim().ifBlank { "重要的人" })
            append(" 的礼物。")
            if (request.contextExcerpt.isNotBlank()) {
                append("氛围参考：")
                append(request.contextExcerpt.trim().take(120))
                append("。")
            }
            append("电影感构图，柔和光影，真实材质，细节丰富，高质量，背景干净，不要文字、水印、logo、边框、界面元素，不要人物正脸。")
        }
    }

    private fun buildGiftImageErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is TimeoutCancellationException -> "礼物图生成超时"
            else -> throwable.message
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(32)
                ?: "礼物图生成失败"
        }
    }

    private companion object {
        const val PromptOptimizationTimeoutMs = 10_000L
        const val ImageGenerationTimeoutMs = 30_000L
        const val MaxContextLength = 600
    }
}
