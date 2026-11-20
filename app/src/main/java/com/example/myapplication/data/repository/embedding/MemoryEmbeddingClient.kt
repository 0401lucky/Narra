package com.example.myapplication.data.repository.embedding

import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.context.MemoryVectorMath
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.MemoryEmbeddingBatch
import com.example.myapplication.model.MemoryEmbeddingPurpose
import com.example.myapplication.model.MemoryEmbeddingRequest
import com.example.myapplication.model.MemoryEmbeddingResponse
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

fun interface MemoryEmbeddingClient {
    suspend fun embed(
        provider: ProviderSettings,
        inputs: List<String>,
        purpose: MemoryEmbeddingPurpose,
        expectedDimensions: Int?,
    ): MemoryEmbeddingBatch
}

enum class MemoryEmbeddingError(val messageForUser: String) {
    CONFIGURATION("请配置独立的向量模型和提供商"),
    INVALID_INPUT("向量输入为空或超过单次处理范围"),
    PROTOCOL("向量服务返回的数据不完整或格式错误"),
    HTTP("向量服务请求失败，请检查提供商设置"),
    TIMEOUT("向量服务响应超时，已保留本地检索"),
    NETWORK("向量服务连接失败，已保留本地检索"),
}

class MemoryEmbeddingException(
    val code: MemoryEmbeddingError,
    val httpStatus: Int? = null,
) : IOException(code.messageForUser + (httpStatus?.let { "（HTTP $it）" } ?: ""))

class OpenAiMemoryEmbeddingClient(
    private val apiServiceFactory: ApiServiceFactory,
) : MemoryEmbeddingClient {
    override suspend fun embed(
        provider: ProviderSettings,
        inputs: List<String>,
        purpose: MemoryEmbeddingPurpose,
        expectedDimensions: Int?,
    ): MemoryEmbeddingBatch {
        val model = provider.resolveFunctionModel(ProviderFunction.EMBEDDING)
        if (!provider.enabled || !provider.hasBaseCredentials() || provider.id.isBlank() ||
            provider.resolvedApiProtocol() != ProviderApiProtocol.OPENAI_COMPATIBLE || model.isBlank()
        ) throw MemoryEmbeddingException(MemoryEmbeddingError.CONFIGURATION)
        if (inputs.isEmpty() || inputs.size > MemoryEmbeddingChunker.BATCH_SIZE ||
            inputs.any { it.isBlank() || it.length > MemoryEmbeddingChunker.MAX_INPUT_CHARS } ||
            expectedDimensions != null && expectedDimensions !in 1..MemoryVectorMath.MAX_DIMENSIONS
        ) throw MemoryEmbeddingException(MemoryEmbeddingError.INVALID_INPUT)
        val api = try {
            apiServiceFactory.createEmbedding(
                provider.baseUrl,
                provider.apiKey,
                longRunning = purpose == MemoryEmbeddingPurpose.INDEX,
            )
        } catch (_: IllegalArgumentException) {
            throw MemoryEmbeddingException(MemoryEmbeddingError.CONFIGURATION)
        }
        try {
            val response = api.createEmbeddings(MemoryEmbeddingRequest(model, inputs))
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw MemoryEmbeddingException(MemoryEmbeddingError.HTTP, response.code())
            }
            return validate(response.body(), inputs.size, expectedDimensions)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: MemoryEmbeddingException) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw MemoryEmbeddingException(MemoryEmbeddingError.TIMEOUT)
        } catch (_: MalformedJsonException) {
            throw MemoryEmbeddingException(MemoryEmbeddingError.PROTOCOL)
        } catch (_: EOFException) {
            throw MemoryEmbeddingException(MemoryEmbeddingError.PROTOCOL)
        } catch (_: IOException) {
            throw MemoryEmbeddingException(MemoryEmbeddingError.NETWORK)
        } catch (_: Exception) {
            // 不把供应商错误体、原始请求或 Gson 的正文片段放进异常链。
            throw MemoryEmbeddingException(MemoryEmbeddingError.PROTOCOL)
        }
    }

    private fun validate(
        response: MemoryEmbeddingResponse?,
        count: Int,
        expectedDimensions: Int?,
    ): MemoryEmbeddingBatch {
        fun invalid(): Nothing = throw MemoryEmbeddingException(MemoryEmbeddingError.PROTOCOL)
        val items = response?.data ?: invalid()
        if (items.size != count) invalid()
        val vectors = arrayOfNulls<FloatArray>(count)
        var dimensions = expectedDimensions
        for (item in items) {
            val indexElement = item?.index ?: invalid()
            if (!indexElement.isJsonPrimitive || !indexElement.asJsonPrimitive.isNumber) invalid()
            val index = try {
                indexElement.asBigDecimal.intValueExact()
            } catch (_: ArithmeticException) {
                invalid()
            } catch (_: NumberFormatException) {
                invalid()
            }
            if (index !in vectors.indices || vectors[index] != null) invalid()
            val embedding = item.embedding ?: invalid()
            if (!embedding.isJsonArray) invalid()
            val values = embedding.asJsonArray
            if (values.size() !in 1..MemoryVectorMath.MAX_DIMENSIONS) invalid()
            if (dimensions == null) dimensions = values.size()
            if (values.size() != dimensions) invalid()
            val vector = FloatArray(values.size()) { position ->
                val value = values[position]
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) invalid()
                val number = value.asDouble
                if (!number.isFinite() || !number.toFloat().isFinite()) invalid()
                number.toFloat()
            }
            vectors[index] = MemoryVectorMath.normalized(vector) ?: invalid()
        }
        return MemoryEmbeddingBatch(vectors.map { it ?: invalid() }, dimensions ?: invalid())
    }
}
