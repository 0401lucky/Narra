package com.example.myapplication.ui.component

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
        key1 = context,
        key2 = avatarSource.value,
        key3 = avatarSource.type,
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
            loadAvatarBitmap(
                context = context,
                source = avatarSource.value,
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
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val avatarState = rememberUserProfileAvatarState(
        avatarUri = avatarUri,
        avatarUrl = avatarUrl,
    )
    val fallbackText = displayName.trim().firstOrNull()?.uppercase() ?: "用"

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
                    contentDescription = "${displayName.ifBlank { "用户" }}头像",
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

private fun loadAvatarBitmap(
    context: Context,
    source: String,
): ImageBitmap? {
    return runCatching {
        when {
            source.startsWith("content://") -> {
                context.contentResolver.openInputStream(source.toUri())?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }

            source.startsWith("http://") || source.startsWith("https://") -> {
                val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        return@runCatching null
                    }
                    connection.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } finally {
                    connection.disconnect()
                }
            }

            source.startsWith("file://") -> {
                BitmapFactory.decodeFile(source.toUri().path)?.asImageBitmap()
            }

            File(source).exists() -> {
                BitmapFactory.decodeFile(source)?.asImageBitmap()
            }

            else -> null
        }
    }.getOrNull()
}
