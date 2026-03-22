package com.example.myapplication.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.example.myapplication.model.MessageAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ImageAttachmentResolver(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun resolveDataUrl(attachment: MessageAttachment): String = withContext(dispatcher) {
        val uri = attachment.uri.toUri()
        val mimeType = attachment.mimeType.ifBlank {
            context.contentResolver.getType(uri).orEmpty()
        }.ifBlank {
            DEFAULT_MIME_TYPE
        }

        val bitmap = decodeScaledBitmap(uri)
            ?: throw IllegalStateException("无法读取所选图片")
        val output = ByteArrayOutputStream()
        val format = if (mimeType.contains("png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 88
        bitmap.compress(format, quality, output)
        bitmap.recycle()

        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        "data:$mimeType;base64,$base64"
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val maxSide = maxOf(options.outWidth, options.outHeight)
        val sampleSize = when {
            maxSide <= MAX_IMAGE_SIDE -> 1
            else -> {
                var candidate = 1
                while (maxSide / candidate > MAX_IMAGE_SIDE) {
                    candidate *= 2
                }
                candidate
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 1_536
        const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}
