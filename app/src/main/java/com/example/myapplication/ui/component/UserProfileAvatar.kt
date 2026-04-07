package com.example.myapplication.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UserAvatarLoadState {
    Empty,
    Loading,
    Success,
    Error,
}

enum class UserAvatarSource {
    None,
    Local,
    Remote,
}

data class UserProfileAvatarState(
    val imageBitmap: ImageBitmap?,
    val loadState: UserAvatarLoadState,
    val source: UserAvatarSource,
)

@Composable
fun rememberUserProfileAvatarState(
    avatarUri: String,
    avatarUrl: String,
    requestSize: IntSize? = null,
    allowHardware: Boolean = false,
): UserProfileAvatarState {
    val context = LocalContext.current
    val avatarSource = remember(avatarUri, avatarUrl) {
        resolveAvatarSource(
            avatarUri = avatarUri,
            avatarUrl = avatarUrl,
        )
    }

    return produceState(
        initialValue = UserProfileAvatarState(
            imageBitmap = null,
            loadState = if (avatarSource.value.isBlank()) {
                UserAvatarLoadState.Empty
            } else {
                UserAvatarLoadState.Loading
            },
            source = avatarSource.type,
        ),
        context,
        avatarSource.value,
        avatarSource.type,
        requestSize,
        allowHardware,
    ) {
        if (avatarSource.value.isBlank()) {
            value = UserProfileAvatarState(
                imageBitmap = null,
                loadState = UserAvatarLoadState.Empty,
                source = avatarSource.type,
            )
            return@produceState
        }

        value = UserProfileAvatarState(
            imageBitmap = null,
            loadState = UserAvatarLoadState.Loading,
            source = avatarSource.type,
        )

        val avatarBitmap = withContext(Dispatchers.IO) {
            loadAvatarBitmapWithCoil(
                context = context,
                source = avatarSource.value,
                requestSize = requestSize,
                allowHardware = allowHardware,
            )
        }

        value = if (avatarBitmap != null) {
            UserProfileAvatarState(
                imageBitmap = avatarBitmap,
                loadState = UserAvatarLoadState.Success,
                source = avatarSource.type,
            )
        } else {
            UserProfileAvatarState(
                imageBitmap = null,
                loadState = UserAvatarLoadState.Error,
                source = avatarSource.type,
            )
        }
    }.value
}

@Composable
fun UserProfileAvatar(
    displayName: String,
    avatarUri: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
    requestSize: IntSize? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val avatarState = rememberUserProfileAvatarState(
        avatarUri = avatarUri,
        avatarUrl = avatarUrl,
        requestSize = requestSize,
    )
    val fallbackText = displayName.trim().firstOrNull()?.uppercase()
        ?: stringResource(id = R.string.user_avatar_fallback_letter)
    val avatarDescription = stringResource(
        id = R.string.avatar_content_description,
        displayName.ifBlank {
            stringResource(id = R.string.default_user_name)
        },
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarState.loadState == UserAvatarLoadState.Success &&
                avatarState.imageBitmap != null
            ) {
                Image(
                    bitmap = avatarState.imageBitmap,
                    contentDescription = avatarDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = fallbackText,
                    style = textStyle,
                    color = contentColor,
                )
            }
        }
    }
}

private data class ResolvedAvatarSource(
    val value: String,
    val type: UserAvatarSource,
)

private fun resolveAvatarSource(
    avatarUri: String,
    avatarUrl: String,
): ResolvedAvatarSource {
    val normalizedUrl = avatarUrl.trim()
    val normalizedUri = avatarUri.trim()
    return when {
        normalizedUrl.isNotBlank() -> ResolvedAvatarSource(
            value = normalizedUrl,
            type = UserAvatarSource.Remote,
        )

        normalizedUri.startsWith("http://") || normalizedUri.startsWith("https://") -> {
            ResolvedAvatarSource(
                value = normalizedUri,
                type = UserAvatarSource.Remote,
            )
        }

        normalizedUri.isNotBlank() -> ResolvedAvatarSource(
            value = normalizedUri,
            type = UserAvatarSource.Local,
        )

        else -> ResolvedAvatarSource(
            value = "",
            type = UserAvatarSource.None,
        )
    }
}

private suspend fun loadAvatarBitmapWithCoil(
    context: android.content.Context,
    source: String,
    requestSize: IntSize?,
    allowHardware: Boolean,
): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(source)
        .allowHardware(allowHardware)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .apply {
            if (requestSize != null && requestSize.width > 0 && requestSize.height > 0) {
                size(requestSize.width, requestSize.height)
            }
        }
        .build()
    val result = context.imageLoader.execute(request)
    return (result as? SuccessResult)
        ?.image
        ?.toBitmap()
        ?.asImageBitmap()
}
