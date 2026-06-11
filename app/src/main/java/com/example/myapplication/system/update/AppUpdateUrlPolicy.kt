package com.example.myapplication.system.update

import java.net.URI

private val AllowedMetadataHosts = setOf("0401lucky.github.io")
private val AllowedApkHosts = setOf("download.lsa1230.dpdns.org")
private const val MetadataPathPrefix = "/Narra/updates"

fun normalizeAndValidateUpdateMetadataBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().removeSuffix("/")
    val uri = parseHttpsUri(trimmed, "更新元数据地址必须使用 https://")
    require(uri.host.lowercase() in AllowedMetadataHosts) {
        "更新元数据地址不在可信来源中"
    }
    require(uri.path.removeSuffix("/") == MetadataPathPrefix) {
        "更新元数据路径不在可信范围内"
    }
    return trimmed
}

fun validateUpdateApkUrl(
    apkUrl: String,
    channel: String,
) {
    val uri = parseHttpsUri(apkUrl.trim(), "APK 下载地址必须使用 https://")
    require(uri.host.lowercase() in AllowedApkHosts) {
        "APK 下载地址不在可信来源中"
    }
    val normalizedChannel = channel.trim().trim('/')
    require(normalizedChannel.isNotBlank()) { "更新渠道为空" }
    require(uri.path.startsWith("/$normalizedChannel/")) {
        "APK 下载路径与当前渠道不匹配"
    }
    require(uri.path.endsWith(".apk", ignoreCase = true)) {
        "APK 下载地址必须指向安装包"
    }
}

private fun parseHttpsUri(
    rawUrl: String,
    schemeMessage: String,
): URI {
    val uri = runCatching { URI(rawUrl) }.getOrElse {
        throw IllegalArgumentException("更新地址格式无效")
    }
    require(uri.scheme.equals("https", ignoreCase = true)) { schemeMessage }
    require(!uri.host.isNullOrBlank()) { "更新地址缺少可信域名" }
    require(uri.userInfo.isNullOrBlank()) { "更新地址不能包含用户信息" }
    require(uri.fragment.isNullOrBlank()) { "更新地址不能包含片段标识" }
    require(uri.port == -1 || uri.port == 443) { "更新地址不能使用自定义端口" }
    return uri
}
