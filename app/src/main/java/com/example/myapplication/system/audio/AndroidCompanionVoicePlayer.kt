package com.example.myapplication.system.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.myapplication.model.CompanionVoicePlayer
import com.example.myapplication.model.CompanionVoicePlayerFactory

class AndroidCompanionVoicePlayerFactory(context: Context) : CompanionVoicePlayerFactory {
    private val applicationContext = context.applicationContext
    override fun create(): CompanionVoicePlayer = AndroidCompanionVoicePlayer(applicationContext)
}

/** 仅系统适配；会话代次与队列由 CompanionVoiceController 持有。 */
private class AndroidCompanionVoicePlayer(context: Context) : CompanionVoicePlayer {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val mediaPlayer = MediaPlayer().apply { setAudioAttributes(attributes) }
    private var released = false
    private var focusHeld = false
    private var errorCallback: (Throwable?) -> Unit = {}
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (!released && focusHeld && change in listOf(
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                )
            ) {
                errorCallback(IllegalStateException("系统音频焦点已切换，朗读已停止，可重新点播。"))
            }
        }
        .build()

    override fun setCallbacks(
        onPrepared: () -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable?) -> Unit
    ) {
        check(!released) { "播放器已释放" }
        errorCallback = onError
        mediaPlayer.setOnPreparedListener { if (!released) onPrepared() }
        mediaPlayer.setOnCompletionListener { if (!released) onCompleted() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            if (!released) onError(IllegalStateException("声音文件无法播放，请重试。"))
            true
        }
    }

    override fun prepare(path: String) {
        check(!released) { "播放器已释放" }
        mediaPlayer.setDataSource(path)
        mediaPlayer.prepareAsync()
    }

    override fun start() {
        check(!released) { "播放器已释放" }
        check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "当前无法取得系统音频焦点，请稍后重试。"
        }
        focusHeld = true
        mediaPlayer.start()
    }

    override fun stop() {
        if (!released) runCatching { mediaPlayer.stop() }
        releaseFocus()
    }

    override fun release() {
        if (released) return
        released = true
        mediaPlayer.setOnPreparedListener(null)
        mediaPlayer.setOnCompletionListener(null)
        mediaPlayer.setOnErrorListener(null)
        mediaPlayer.release()
        releaseFocus()
        errorCallback = {}
    }

    private fun releaseFocus() {
        if (focusHeld) {
            focusHeld = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
