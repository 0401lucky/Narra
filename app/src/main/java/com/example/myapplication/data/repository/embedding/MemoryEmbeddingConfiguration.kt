package com.example.myapplication.data.repository.embedding

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.system.json.AppJson

/** 索引构建、查询和资料恢复共享配置身份；密钥从不参与序列化。 */
internal object MemoryEmbeddingConfiguration {
    fun endpoint(settings: ProviderSettings, factory: ApiServiceFactory): String =
        factory.normalizeBaseUrl(settings.baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)

    fun key(settings: ProviderSettings, factory: ApiServiceFactory): String =
        MemoryArchiveIdentity.fingerprint(
            AppJson.gson.toJson(
                listOf(
                    settings.id,
                    endpoint(settings, factory),
                    settings.resolveFunctionModel(ProviderFunction.EMBEDDING),
                    MemoryEmbeddingChunker.PREPROCESSING_VERSION.toString()
                )
            ),
        )

    fun vectorSpace(configuration: String, dimensions: Int): String =
        if (dimensions == 0) "pending:$configuration" else MemoryArchiveIdentity.fingerprint("$configuration:$dimensions")
}
