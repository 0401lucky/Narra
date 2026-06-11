package com.example.myapplication.system.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateUrlPolicyTest {
    @Test
    fun normalizeAndValidateUpdateMetadataBaseUrl_allowsOfficialMetadataBaseUrl() {
        val normalized = normalizeAndValidateUpdateMetadataBaseUrl(
            "https://0401lucky.github.io/Narra/updates/",
        )

        assertEquals("https://0401lucky.github.io/Narra/updates", normalized)
    }

    @Test
    fun validateUpdateApkUrl_allowsCurrentDevDownloadUrlShape() {
        validateUpdateApkUrl(
            apkUrl = "https://download.lsa1230.dpdns.org/dev/Narra-v1.9.6-dev-10906-dev.apk?v=10906",
            channel = "dev",
        )
    }

    @Test
    fun validateUpdateApkUrl_rejectsCustomPort() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateUpdateApkUrl(
                apkUrl = "https://download.lsa1230.dpdns.org:8443/dev/Narra.apk",
                channel = "dev",
            )
        }

        assertEquals("更新地址不能使用自定义端口", error.message)
    }
}
