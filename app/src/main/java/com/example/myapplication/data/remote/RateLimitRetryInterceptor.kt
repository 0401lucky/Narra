package com.example.myapplication.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 429 (Too Many Requests) 自动重试拦截器。
 * 收到 429 时按指数退避策略重试，最多重试 [maxRetries] 次。
 * 如果响应包含 Retry-After 头，以该值为基准（上限为 30 秒）。
 */
class RateLimitRetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialDelayMs: Long = 1_500L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)

        var attempt = 0
        while (response.code == 429 && attempt < maxRetries) {
            response.close()
            val delay = resolveDelay(response, attempt)
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("重试等待被中断")
            }
            attempt++
            response = chain.proceed(request)
        }
        return response
    }

    private fun resolveDelay(response: Response, attempt: Int): Long {
        val retryAfterHeader = response.header("retry-after")
        val retryAfterSeconds = retryAfterHeader?.trim()?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds in 1..30) {
            return retryAfterSeconds * 1_000L
        }
        return initialDelayMs * (1L shl attempt)
    }
}
