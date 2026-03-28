package com.example.myapplication.data.repository.ai

import kotlinx.coroutines.CancellationException
import java.net.UnknownHostException

internal object GatewayNetworkSupport {
    fun toReadableNetworkException(throwable: Exception): Exception {
        if (throwable is CancellationException) {
            return throwable
        }
        if (findUnknownHostException(throwable) != null) {
            return IllegalStateException(
                "无法解析服务地址，请检查设备网络、DNS 设置，并确认 Base URL 是否可访问",
                throwable,
            )
        }
        return throwable
    }

    fun okhttpFailure(
        operation: String,
        response: okhttp3.Response,
    ): IllegalStateException {
        val errorDetail = response.body?.string().orEmpty()
        return IllegalStateException(
            buildString {
                append(operation)
                append('：')
                append(response.code)
                val normalizedErrorDetail = errorDetail.trim()
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    fun <T> retrofitFailure(
        operation: String,
        response: retrofit2.Response<T>,
    ): IllegalStateException {
        val errorDetail = response.errorBody()?.string().orEmpty()
        return IllegalStateException(
            buildString {
                append(operation)
                append('：')
                append(response.code())
                val normalizedErrorDetail = errorDetail.trim()
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    private fun findUnknownHostException(throwable: Throwable): UnknownHostException? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is UnknownHostException) {
                return current
            }
            current = current.cause
        }
        return null
    }
}
