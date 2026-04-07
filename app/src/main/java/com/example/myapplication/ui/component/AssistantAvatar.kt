package com.example.myapplication.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

/**
 * 助手头像组件。
 * 优先显示用户上传头像（avatarUri），其次显示预设图标（iconName），
 * 最后回退为名称首字母。
 */
@Composable
fun AssistantAvatar(
    name: String,
    iconName: String,
    avatarUri: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    cornerRadius: Dp = 14.dp,
) {
    val density = LocalDensity.current
    val requestSize = with(density) {
        val sizePx = size.roundToPx().coerceAtLeast(1)
        IntSize(width = sizePx, height = sizePx)
    }
    val avatarState = rememberUserProfileAvatarState(
        avatarUri = avatarUri,
        avatarUrl = "",
        requestSize = requestSize,
    )
    val hasUploadedAvatar = avatarUri.isNotBlank() &&
        avatarState.loadState == UserAvatarLoadState.Success &&
        avatarState.imageBitmap != null
    val resolvedAssistantName = name.ifBlank { stringResource(id = R.string.default_assistant_name) }
    val avatarDescription = stringResource(
        id = R.string.avatar_content_description,
        resolvedAssistantName,
    )

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasUploadedAvatar -> {
                    Image(
                        bitmap = avatarState.imageBitmap!!,
                        contentDescription = avatarDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                iconName.isNotBlank() -> {
                    val icon = resolveAssistantIcon(iconName)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = resolvedAssistantName,
                            modifier = Modifier.size(size * 0.52f),
                            tint = contentColor,
                        )
                    } else {
                        FallbackLetter(name, contentColor)
                    }
                }

                else -> {
                    FallbackLetter(name, contentColor)
                }
            }
        }
    }
}

@Composable
private fun FallbackLetter(name: String, color: Color) {
    Text(
        text = name.trim().firstOrNull()?.uppercase() ?: "A",
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

fun resolveAssistantIcon(iconName: String): ImageVector? {
    return assistantIconMap[iconName]
}

private val assistantIconMap: Map<String, ImageVector> = mapOf(
    "smart_toy" to Icons.Default.SmartToy,
    "psychology" to Icons.Default.Psychology,
    "translate" to Icons.Default.Translate,
    "code" to Icons.Default.Code,
    "edit_note" to Icons.Default.EditNote,
    "school" to Icons.Default.School,
    "science" to Icons.Default.Science,
    "calculate" to Icons.Default.Calculate,
    "palette" to Icons.Default.Palette,
    "music_note" to Icons.Default.MusicNote,
    "restaurant" to Icons.Default.Restaurant,
    "fitness_center" to Icons.Default.FitnessCenter,
    "travel_explore" to Icons.Default.TravelExplore,
    "local_hospital" to Icons.Default.LocalHospital,
    "gavel" to Icons.Default.Gavel,
    "auto_stories" to Icons.Default.AutoStories,
)
