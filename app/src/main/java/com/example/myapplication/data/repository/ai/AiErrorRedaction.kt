package com.example.myapplication.data.repository.ai

import com.example.myapplication.system.security.SensitiveTextRedactor

internal object AiErrorRedaction {
    fun redact(errorDetail: String): String {
        return SensitiveTextRedactor.redact(errorDetail.trim(), maxLength = 160)
    }
}
