package com.example.myapplication.system.network

import java.net.URI

fun normalizeSecureHttpBaseUrl(
    baseUrl: String,
    blankMessage: String,
    schemeMessage: String,
    secureMessage: String,
): String {
    val trimmed = baseUrl.trim()
    require(trimmed.isNotBlank()) { blankMessage }
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        schemeMessage
    }
    require(trimmed.startsWith("https://") || isLoopbackHttpBaseUrl(trimmed)) {
        secureMessage
    }
    return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
}

fun isLoopbackHttpBaseUrl(baseUrl: String): Boolean {
    if (!baseUrl.startsWith("http://", ignoreCase = true)) {
        return false
    }
    val host = runCatching { URI(baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "::1" ||
        host == "[::1]" ||
        host == "10.0.2.2" ||
        host == "10.0.3.2" ||
        host.endsWith(".localhost")
}
