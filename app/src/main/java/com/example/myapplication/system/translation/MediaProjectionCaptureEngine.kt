package com.example.myapplication.system.translation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import androidx.core.graphics.createBitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ScreenCaptureEngine {
    suspend fun startSession(
        resultCode: Int,
        dataIntent: Intent,
    )

    suspend fun capture(): Bitmap

    fun hasActiveSession(): Boolean

    fun release()
}

class MediaProjectionCaptureEngine(
    private val context: Context,
) : ScreenCaptureEngine {
    private val lock = Any()
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var width = 0
    private var height = 0

    override suspend fun startSession(
        resultCode: Int,
        dataIntent: Intent,
    ) {
        synchronized(lock) {
            if (mediaProjection != null) {
                return
            }
        }

        val metrics = context.resources.displayMetrics
        val sessionWidth = metrics.widthPixels
        val sessionHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val mediaProjectionManager = context.getSystemService(MediaProjectionManager::class.java)
        val projection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent)
            ?: throw IllegalStateException("无法创建屏幕投影实例")
        val reader = ImageReader.newInstance(
            sessionWidth,
            sessionHeight,
            PixelFormat.RGBA_8888,
            3,
        )
        val thread = HandlerThread("screen-capture").apply { start() }
        val threadHandler = Handler(thread.looper)

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }
        projection.registerCallback(callback, threadHandler)

        val display = projection.createVirtualDisplay(
            "screen-translation-capture",
            sessionWidth,
            sessionHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            threadHandler,
        )

        synchronized(lock) {
            mediaProjection = projection
            imageReader = reader
            handlerThread = thread
            handler = threadHandler
            virtualDisplay = display
            projectionCallback = callback
            width = sessionWidth
            height = sessionHeight
        }

        // 给虚拟显示一点时间产出首帧，避免刚授权完立即抓取拿到空图像。
        delay(120)
    }

    override suspend fun capture(): Bitmap {
        if (!hasActiveSession()) {
            throw IllegalStateException("当前没有可用的屏幕共享会话")
        }

        return suspendCancellableCoroutine { continuation ->
            val currentReader = synchronized(lock) { imageReader }
                ?: run {
                    continuation.resumeWithException(IllegalStateException("截图读取器不可用"))
                    return@suspendCancellableCoroutine
                }
            val currentHandler = synchronized(lock) { handler }
                ?: run {
                    continuation.resumeWithException(IllegalStateException("截图线程不可用"))
                    return@suspendCancellableCoroutine
                }

            fun tryResumeWithImage(imageReader: ImageReader): Boolean {
                val image = imageReader.acquireLatestImage() ?: return false
                try {
                    val plane = image.planes.firstOrNull()
                        ?: throw IllegalStateException("截图数据为空")
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val rawBitmap = createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val croppedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                    rawBitmap.recycle()
                    if (continuation.isActive) {
                        continuation.resume(croppedBitmap)
                    }
                } catch (throwable: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                } finally {
                    image.close()
                }
                return true
            }

            if (tryResumeWithImage(currentReader)) {
                return@suspendCancellableCoroutine
            }

            currentReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    reader.setOnImageAvailableListener(null, null)
                    tryResumeWithImage(reader)
                }
            }, currentHandler)

            continuation.invokeOnCancellation {
                currentReader.setOnImageAvailableListener(null, null)
            }
        }
    }

    override fun hasActiveSession(): Boolean {
        return synchronized(lock) {
            mediaProjection != null && imageReader != null && virtualDisplay != null
        }
    }

    override fun release() {
        val projectionToStop: MediaProjection?
        val callbackToUnregister: MediaProjection.Callback?
        val readerToClose: ImageReader?
        val displayToRelease: VirtualDisplay?
        val threadToQuit: HandlerThread?

        synchronized(lock) {
            projectionToStop = mediaProjection
            callbackToUnregister = projectionCallback
            readerToClose = imageReader
            displayToRelease = virtualDisplay
            threadToQuit = handlerThread

            mediaProjection = null
            projectionCallback = null
            imageReader = null
            virtualDisplay = null
            handlerThread = null
            handler = null
            width = 0
            height = 0
        }

        runCatching {
            if (projectionToStop != null && callbackToUnregister != null) {
                projectionToStop.unregisterCallback(callbackToUnregister)
            }
        }
        runCatching { displayToRelease?.release() }
        runCatching { readerToClose?.close() }
        runCatching { projectionToStop?.stop() }
        runCatching { threadToQuit?.quitSafely() }
    }
}
