package com.example.myapplication.ui.component.roleplay

internal sealed interface RoleplayVoicePlaybackState {
    data object Idle : RoleplayVoicePlaybackState

    data object Preparing : RoleplayVoicePlaybackState

    data object Playing : RoleplayVoicePlaybackState

    data class Error(
        val cause: Throwable? = null,
    ) : RoleplayVoicePlaybackState
}

internal fun interface RoleplayVoicePlayerFactory {
    fun create(): RoleplayVoicePlayer
}

internal interface RoleplayVoicePlayer {
    fun setDataSource(path: String)

    fun setOnPreparedListener(listener: () -> Unit)

    fun setOnCompletionListener(listener: () -> Unit)

    fun setOnErrorListener(listener: () -> Boolean)

    fun prepareAsync()

    fun start()

    fun clearListeners()

    fun release()
}

internal class RoleplayVoicePlaybackController(
    private val playerFactory: RoleplayVoicePlayerFactory,
    private val onStateChanged: (RoleplayVoicePlaybackState) -> Unit = {},
) {
    @Volatile
    var state: RoleplayVoicePlaybackState = RoleplayVoicePlaybackState.Idle
        private set

    private var activeSession: PlaybackSession? = null
    private var isDisposed: Boolean = false

    @Synchronized
    fun toggle(audioPath: String) {
        if (isDisposed) return
        when (state) {
            RoleplayVoicePlaybackState.Preparing,
            RoleplayVoicePlaybackState.Playing,
            -> stopLocked()

            RoleplayVoicePlaybackState.Idle,
            is RoleplayVoicePlaybackState.Error,
            -> startPreparingLocked(audioPath)
        }
    }

    @Synchronized
    fun stop() {
        if (isDisposed) return
        stopLocked()
    }

    @Synchronized
    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        activeSession?.let(::releaseSessionLocked)
        state = RoleplayVoicePlaybackState.Idle
    }

    private fun startPreparingLocked(audioPath: String) {
        if (audioPath.isBlank()) {
            transitionTo(RoleplayVoicePlaybackState.Error(IllegalArgumentException("语音文件路径为空")))
            return
        }
        val player = try {
            playerFactory.create()
        } catch (throwable: Throwable) {
            transitionTo(RoleplayVoicePlaybackState.Error(throwable))
            return
        }
        val session = PlaybackSession(player)
        activeSession = session
        try {
            player.setOnPreparedListener { handlePrepared(session) }
            player.setOnCompletionListener { handleCompletion(session) }
            player.setOnErrorListener { handleError(session) }
            player.setDataSource(audioPath)
            transitionTo(RoleplayVoicePlaybackState.Preparing)
            if (isActive(session)) {
                player.prepareAsync()
            }
        } catch (throwable: Throwable) {
            failSessionLocked(session, throwable)
        }
    }

    @Synchronized
    private fun handlePrepared(session: PlaybackSession) {
        if (!isActive(session) || state != RoleplayVoicePlaybackState.Preparing) return
        try {
            session.player.start()
            if (isActive(session)) {
                transitionTo(RoleplayVoicePlaybackState.Playing)
            }
        } catch (throwable: Throwable) {
            failSessionLocked(session, throwable)
        }
    }

    @Synchronized
    private fun handleCompletion(session: PlaybackSession) {
        if (!isActive(session)) return
        releaseSessionLocked(session)
        transitionTo(RoleplayVoicePlaybackState.Idle)
    }

    @Synchronized
    private fun handleError(session: PlaybackSession): Boolean {
        if (isActive(session)) {
            releaseSessionLocked(session)
            transitionTo(RoleplayVoicePlaybackState.Error())
        }
        return true
    }

    private fun stopLocked() {
        activeSession?.let(::releaseSessionLocked)
        if (state != RoleplayVoicePlaybackState.Idle) {
            transitionTo(RoleplayVoicePlaybackState.Idle)
        }
    }

    private fun failSessionLocked(
        session: PlaybackSession,
        throwable: Throwable,
    ) {
        if (!isActive(session)) return
        releaseSessionLocked(session)
        transitionTo(RoleplayVoicePlaybackState.Error(throwable))
    }

    private fun releaseSessionLocked(session: PlaybackSession) {
        if (activeSession === session) {
            activeSession = null
        }
        session.releaseOnce()
    }

    private fun isActive(session: PlaybackSession): Boolean {
        return !isDisposed && activeSession === session && !session.isReleased
    }

    private fun transitionTo(nextState: RoleplayVoicePlaybackState) {
        if (isDisposed) return
        state = nextState
        onStateChanged(nextState)
    }

    private class PlaybackSession(
        val player: RoleplayVoicePlayer,
    ) {
        var isReleased: Boolean = false
            private set

        fun releaseOnce() {
            if (isReleased) return
            isReleased = true
            runCatching { player.clearListeners() }
            runCatching { player.release() }
        }
    }
}
