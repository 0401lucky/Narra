package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings

data class TranslationRequestIdentity(
    val providerId: String,
    val baseUrl: String,
    val endpoint: String,
    val modelId: String,
    val apiProtocol: ProviderApiProtocol,
    val textApiMode: OpenAiTextApiMode,
    val builderVersion: String = "translation-text-v1",
)

/** 只驻内存，不是数据类或持久 DTO；缓存身份没有 API Key。 */
class TranslationRequestSnapshot internal constructor(
    val identity: TranslationRequestIdentity,
    internal val apiKey: String,
    internal val provider: ProviderSettings?,
) {
    override fun toString(): String = "TranslationRequestSnapshot(identity=$identity)"
}

/** 附加接口保留旧 AiTranslationService 的 JVM 方法和已有 fake 实现。 */
interface SnapshotAiTranslationService {
    fun freeze(settingsSnapshot: AppSettings): TranslationRequestSnapshot
    suspend fun translateText(
        snapshot: TranslationRequestSnapshot,
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): String
}
