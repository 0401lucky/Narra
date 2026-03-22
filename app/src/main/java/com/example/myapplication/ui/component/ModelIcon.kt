package com.example.myapplication.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

/**
 * 根据模型名称字符串启发式匹配对应的品牌图标资源。
 * 图标来源：lobehub/lobe-icons (MIT)。
 */
fun resolveModelIconRes(modelName: String): Int? {
    val lower = modelName.lowercase()
    return when {
        "claude" in lower -> R.drawable.ic_model_claude
        "gemini" in lower -> R.drawable.ic_model_gemini
        "deepseek" in lower -> R.drawable.ic_model_deepseek
        "qwen" in lower -> R.drawable.ic_model_qwen
        "mistral" in lower || "mixtral" in lower -> R.drawable.ic_model_mistral
        "grok" in lower -> R.drawable.ic_model_grok
        "kimi" in lower || "moonshot" in lower -> R.drawable.ic_model_kimi
        "glm" in lower || "zhipu" in lower || "chatglm" in lower -> R.drawable.ic_model_zhipu
        "llama" in lower || "meta" in lower -> R.drawable.ic_model_meta
        "gpt" in lower || "openai" in lower
            || lower.startsWith("o1") || lower.startsWith("o3")
            || lower.startsWith("o4") || lower.startsWith("chatgpt") -> R.drawable.ic_model_openai
        else -> null
    }
}

/**
 * 模型品牌图标组件。已识别的模型显示对应 PNG 图标，
 * 未识别的模型显示首字母圆形徽标作为回退。
 */
@Composable
fun ModelIcon(
    modelName: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val iconRes = resolveModelIconRes(modelName)
    if (iconRes != null) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = modelName,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Fit,
        )
    } else {
        val initial = modelName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = if (size <= 20.dp) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                )
            }
        }
    }
}
