package com.example.myapplication.data.remote

import com.example.myapplication.model.ProviderApiProtocol
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiServiceFactory {
    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_READ_TIMEOUT_SECONDS = 120L
        // 非流式 extras（日记 / 摘要 / 记忆 / 标题 / 手机等）使用的长超时：
        // 思考模型 + 长上下文的首字节返回常常 >120s，120s 会触发 SocketTimeoutException
        // 而服务端仍在完成生成，API 后台看得到完整输出但客户端拿不到。
        const val LONG_READ_TIMEOUT_SECONDS = 600L
    }
    private var cachedApiBaseUrl: String? = null
    private var cachedApiKey: String? = null
    private var cachedApi: OpenAiCompatibleApi? = null

    private var cachedAnthropicApiBaseUrl: String? = null
    private var cachedAnthropicApiKey: String? = null
    private var cachedAnthropicApi: AnthropicApi? = null

    private var cachedLongRunningApiBaseUrl: String? = null
    private var cachedLongRunningApiKey: String? = null
    private var cachedLongRunningApi: OpenAiCompatibleApi? = null

    private var cachedLongRunningAnthropicApiBaseUrl: String? = null
    private var cachedLongRunningAnthropicApiKey: String? = null
    private var cachedLongRunningAnthropicApi: AnthropicApi? = null

    private var cachedStreamBaseUrl: String? = null
    private var cachedStreamApiKey: String? = null
    private var cachedStreamClient: OkHttpClient? = null

    private var cachedRequestBaseUrl: String? = null
    private var cachedRequestApiKey: String? = null
    private var cachedRequestClient: OkHttpClient? = null

    private var cachedAnthropicStreamBaseUrl: String? = null
    private var cachedAnthropicStreamApiKey: String? = null
    private var cachedAnthropicStreamClient: OkHttpClient? = null

    @Synchronized
    fun create(
        baseUrl: String,
        apiKey: String,
    ): OpenAiCompatibleApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val trimmedKey = apiKey.trim()

        cachedApi?.let { api ->
            if (cachedApiBaseUrl == normalizedBaseUrl && cachedApiKey == trimmedKey) {
                return api
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        val api = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiCompatibleApi::class.java)

        cachedApiBaseUrl = normalizedBaseUrl
        cachedApiKey = trimmedKey
        cachedApi = api
        return api
    }

    @Synchronized
    fun createAnthropic(
        baseUrl: String,
        apiKey: String,
    ): AnthropicApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.ANTHROPIC)
        val trimmedKey = apiKey.trim()

        cachedAnthropicApi?.let { api ->
            if (cachedAnthropicApiBaseUrl == normalizedBaseUrl && cachedAnthropicApiKey == trimmedKey) {
                return api
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", trimmedKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        val api = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnthropicApi::class.java)

        cachedAnthropicApiBaseUrl = normalizedBaseUrl
        cachedAnthropicApiKey = trimmedKey
        cachedAnthropicApi = api
        return api
    }

    /**
     * 长超时版本的 OpenAI 兼容客户端。仅用于 PromptExtrasCore 等非流式长任务（日记 / 摘要 /
     * 记忆提议 / 手机内容）。readTimeout = 600s，避免思考模型首字节返回 >120s 触发 timeout
     * 而服务端实际已经生成完的误报。
     */
    @Synchronized
    fun createLongRunning(
        baseUrl: String,
        apiKey: String,
    ): OpenAiCompatibleApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val trimmedKey = apiKey.trim()

        cachedLongRunningApi?.let { api ->
            if (cachedLongRunningApiBaseUrl == normalizedBaseUrl &&
                cachedLongRunningApiKey == trimmedKey
            ) {
                return api
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(LONG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        val api = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiCompatibleApi::class.java)

        cachedLongRunningApiBaseUrl = normalizedBaseUrl
        cachedLongRunningApiKey = trimmedKey
        cachedLongRunningApi = api
        return api
    }

    /** 长超时版本的 Anthropic 客户端，语义同 [createLongRunning]。 */
    @Synchronized
    fun createLongRunningAnthropic(
        baseUrl: String,
        apiKey: String,
    ): AnthropicApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.ANTHROPIC)
        val trimmedKey = apiKey.trim()

        cachedLongRunningAnthropicApi?.let { api ->
            if (cachedLongRunningAnthropicApiBaseUrl == normalizedBaseUrl &&
                cachedLongRunningAnthropicApiKey == trimmedKey
            ) {
                return api
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(LONG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", trimmedKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        val api = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnthropicApi::class.java)

        cachedLongRunningAnthropicApiBaseUrl = normalizedBaseUrl
        cachedLongRunningAnthropicApiKey = trimmedKey
        cachedLongRunningAnthropicApi = api
        return api
    }

    @Synchronized
    fun createStreamingClient(
        baseUrl: String,
        apiKey: String,
    ): OkHttpClient {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val trimmedKey = apiKey.trim()

        cachedStreamClient?.let { client ->
            if (cachedStreamBaseUrl == normalizedBaseUrl && cachedStreamApiKey == trimmedKey) {
                return client
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .addHeader("Accept", "text/event-stream")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        cachedStreamBaseUrl = normalizedBaseUrl
        cachedStreamApiKey = trimmedKey
        cachedStreamClient = client
        return client
    }

    @Synchronized
    fun createRequestClient(
        baseUrl: String,
        apiKey: String,
    ): OkHttpClient {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val trimmedKey = apiKey.trim()

        cachedRequestClient?.let { client ->
            if (cachedRequestBaseUrl == normalizedBaseUrl && cachedRequestApiKey == trimmedKey) {
                return client
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $trimmedKey")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        cachedRequestBaseUrl = normalizedBaseUrl
        cachedRequestApiKey = trimmedKey
        cachedRequestClient = client
        return client
    }

    @Synchronized
    fun createAnthropicStreamingClient(
        baseUrl: String,
        apiKey: String,
    ): OkHttpClient {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl, ProviderApiProtocol.ANTHROPIC)
        val trimmedKey = apiKey.trim()

        cachedAnthropicStreamClient?.let { client ->
            if (cachedAnthropicStreamBaseUrl == normalizedBaseUrl && cachedAnthropicStreamApiKey == trimmedKey) {
                return client
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", trimmedKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("Accept", "text/event-stream")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(RateLimitRetryInterceptor())
            .build()

        cachedAnthropicStreamBaseUrl = normalizedBaseUrl
        cachedAnthropicStreamApiKey = trimmedKey
        cachedAnthropicStreamClient = client
        return client
    }

    fun normalizeBaseUrl(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
    ): String {
        val trimmed = baseUrl.trim()
        require(trimmed.isNotBlank()) { "请先填写 Base URL" }
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "Base URL 必须以 http:// 或 https:// 开头"
        }
        val normalizedBaseUrl = if (trimmed.endsWith('/')) trimmed else "$trimmed/"
        return when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> normalizeGeminiBaseUrl(normalizedBaseUrl)
            ProviderApiProtocol.ANTHROPIC -> normalizedBaseUrl
        }
    }

    private fun normalizeGeminiBaseUrl(baseUrl: String): String {
        val lower = baseUrl.lowercase()
        if (!lower.contains("generativelanguage.googleapis.com")) {
            return baseUrl
        }
        if (lower.contains("/openai/")) {
            return baseUrl
        }
        return if (baseUrl.endsWith('/')) {
            "${baseUrl}openai/"
        } else {
            "$baseUrl/openai/"
        }
    }
}
