package com.example.myapplication.model

import androidx.compose.ui.graphics.Color

/** 提供商品牌主色（用于卡片竖条等强调元素）。 */
fun ProviderType.brandColor(isDark: Boolean): Color = when (this) {
    ProviderType.OPENAI -> if (isDark) Color(0xFF74D7A8) else Color(0xFF10A37F)
    ProviderType.DEEPSEEK -> if (isDark) Color(0xFF8BA3FE) else Color(0xFF4D6BFE)
    ProviderType.GOOGLE -> if (isDark) Color(0xFF8AB4F8) else Color(0xFF4285F4)
    ProviderType.ANTHROPIC -> if (isDark) Color(0xFFD4956A) else Color(0xFFD97706)
    ProviderType.MISTRAL -> if (isDark) Color(0xFFFF9E4D) else Color(0xFFFF7000)
    ProviderType.GROK -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    ProviderType.MOONSHOT -> if (isDark) Color(0xFFB48EF0) else Color(0xFF7C3AED)
    ProviderType.ZHIPU -> if (isDark) Color(0xFF7EABFA) else Color(0xFF4C89F8)
    ProviderType.QWEN -> if (isDark) Color(0xFF9B9DF7) else Color(0xFF6366F1)
    ProviderType.META_LLAMA -> if (isDark) Color(0xFF5A9CF5) else Color(0xFF0668E1)
    ProviderType.CUSTOM -> if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
}

/** 提供商品牌容器色（用于卡片背景、图标背景等）。 */
fun ProviderType.brandContainerColor(isDark: Boolean): Color = when (this) {
    ProviderType.OPENAI -> if (isDark) Color(0xFF1A3D2E) else Color(0xFFE6F7EF)
    ProviderType.DEEPSEEK -> if (isDark) Color(0xFF1C2350) else Color(0xFFE8EDFE)
    ProviderType.GOOGLE -> if (isDark) Color(0xFF1B2A45) else Color(0xFFE8F0FE)
    ProviderType.ANTHROPIC -> if (isDark) Color(0xFF3D2810) else Color(0xFFFEF3E2)
    ProviderType.MISTRAL -> if (isDark) Color(0xFF3D2210) else Color(0xFFFFF0E5)
    ProviderType.GROK -> if (isDark) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
    ProviderType.MOONSHOT -> if (isDark) Color(0xFF2A1845) else Color(0xFFF0E8FE)
    ProviderType.ZHIPU -> if (isDark) Color(0xFF1B2845) else Color(0xFFE8F0FE)
    ProviderType.QWEN -> if (isDark) Color(0xFF1E1E50) else Color(0xFFEAEAFE)
    ProviderType.META_LLAMA -> if (isDark) Color(0xFF0D1E3D) else Color(0xFFE3EFFC)
    ProviderType.CUSTOM -> if (isDark) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
}
