package com.example.myapplication.ui.screen.settings

import android.media.MediaPlayer

internal interface VoicePreviewPlayback {
    fun prepareAndPlay(
        path: String,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable?) -> Unit,
    )

    fun release()
}

internal fun interface VoicePreviewPlaybackFactory {
    fun create(): VoicePreviewPlayback
}

internal object AndroidVoicePreviewPlaybackFactory : VoicePreviewPlaybackFactory {
    override fun create(): VoicePreviewPlayback {
        return AndroidVoicePreviewPlayback(MediaPlayer())
    }
}

private class AndroidVoicePreviewPlayback(
    private val mediaPlayer: MediaPlayer,
) : VoicePreviewPlayback {
    private var released = false

    override fun prepareAndPlay(
        path: String,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable?) -> Unit,
    ) {
        if (released) {
            onError(IllegalStateException("播放器已释放"))
            return
        }
        try {
            mediaPlayer.setOnPreparedListener { preparedPlayer ->
                if (released) return@setOnPreparedListener
                try {
                    preparedPlayer.start()
                    if (!released) {
                        onStarted()
                    }
                } catch (throwable: Throwable) {
                    fail(throwable, onError)
                }
            }
            mediaPlayer.setOnCompletionListener {
                if (released) return@setOnCompletionListener
                release()
                onCompleted()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                if (!released) {
                    release()
                    onError(null)
                }
                true
            }
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepareAsync()
        } catch (throwable: Throwable) {
            fail(throwable, onError)
        }
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { mediaPlayer.setOnPreparedListener(null) }
        runCatching { mediaPlayer.setOnCompletionListener(null) }
        runCatching { mediaPlayer.setOnErrorListener(null) }
        runCatching { mediaPlayer.release() }
    }

    private fun fail(
        throwable: Throwable,
        onError: (Throwable?) -> Unit,
    ) {
        if (released) return
        release()
        onError(throwable)
    }
}
