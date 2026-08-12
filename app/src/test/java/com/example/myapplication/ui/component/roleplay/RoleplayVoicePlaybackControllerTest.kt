package com.example.myapplication.ui.component.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayVoicePlaybackControllerTest {
    @Test
    fun toggle_idle_registersListenersBeforeAsyncPrepareAndStartsOnlyAfterPrepared() {
        val factory = FakeRoleplayVoicePlayerFactory()
        val states = mutableListOf<RoleplayVoicePlaybackState>()
        val controller = RoleplayVoicePlaybackController(factory, states::add)

        controller.toggle("voice.mp3")

        val player = factory.singlePlayer()
        assertEquals(
            listOf(
                "setOnPreparedListener",
                "setOnCompletionListener",
                "setOnErrorListener",
                "setDataSource:voice.mp3",
                "prepareAsync",
            ),
            player.events,
        )
        assertEquals(RoleplayVoicePlaybackState.Preparing, controller.state)
        assertEquals(0, player.startCount)

        player.dispatchPrepared()

        assertEquals(RoleplayVoicePlaybackState.Playing, controller.state)
        assertEquals(1, player.startCount)
        assertEquals(
            listOf(RoleplayVoicePlaybackState.Preparing, RoleplayVoicePlaybackState.Playing),
            states,
        )
    }

    @Test
    fun toggle_preparingAgain_cancelsWithoutCreatingSecondPlayer() {
        val factory = FakeRoleplayVoicePlayerFactory()
        val states = mutableListOf<RoleplayVoicePlaybackState>()
        val controller = RoleplayVoicePlaybackController(factory, states::add)

        controller.toggle("voice.mp3")
        val player = factory.singlePlayer()
        controller.toggle("voice.mp3")

        assertEquals(RoleplayVoicePlaybackState.Idle, controller.state)
        assertEquals(1, factory.players.size)
        assertEquals(1, player.releaseCount)
        assertEquals(1, player.clearListenersCount)

        player.dispatchPrepared()

        assertEquals(0, player.startCount)
        assertEquals(RoleplayVoicePlaybackState.Idle, controller.state)
        assertEquals(1, player.releaseCount)
        assertEquals(
            listOf(RoleplayVoicePlaybackState.Preparing, RoleplayVoicePlaybackState.Idle),
            states,
        )
    }

    @Test
    fun completion_releasesOnceAndReturnsToIdle() {
        val factory = FakeRoleplayVoicePlayerFactory()
        val states = mutableListOf<RoleplayVoicePlaybackState>()
        val controller = RoleplayVoicePlaybackController(factory, states::add)

        controller.toggle("voice.mp3")
        val player = factory.singlePlayer()
        player.dispatchPrepared()
        player.dispatchCompletion()
        player.dispatchCompletion()
        player.dispatchError()

        assertEquals(RoleplayVoicePlaybackState.Idle, controller.state)
        assertEquals(1, player.releaseCount)
        assertEquals(
            listOf(
                RoleplayVoicePlaybackState.Preparing,
                RoleplayVoicePlaybackState.Playing,
                RoleplayVoicePlaybackState.Idle,
            ),
            states,
        )
    }

    @Test
    fun playerError_releasesOnceAndIgnoresLaterCallbacks() {
        val factory = FakeRoleplayVoicePlayerFactory()
        val states = mutableListOf<RoleplayVoicePlaybackState>()
        val controller = RoleplayVoicePlaybackController(factory, states::add)

        controller.toggle("voice.mp3")
        val player = factory.singlePlayer()

        assertTrue(player.dispatchError())
        val errorState = controller.state
        assertTrue(errorState is RoleplayVoicePlaybackState.Error)
        assertEquals(1, player.releaseCount)

        player.dispatchPrepared()
        player.dispatchCompletion()
        player.dispatchError()

        assertSame(errorState, controller.state)
        assertEquals(0, player.startCount)
        assertEquals(1, player.releaseCount)
        assertEquals(2, states.size)
    }

    @Test
    fun prepareFailure_releasesOnceAndExposesCause() {
        val expected = IllegalStateException("prepare failed")
        val factory = FakeRoleplayVoicePlayerFactory().apply {
            onCreate = { FakeRoleplayVoicePlayer(prepareFailure = expected) }
        }
        val controller = RoleplayVoicePlaybackController(factory)

        controller.toggle("voice.mp3")

        val player = factory.singlePlayer()
        val errorState = controller.state as RoleplayVoicePlaybackState.Error
        assertSame(expected, errorState.cause)
        assertEquals(1, player.releaseCount)
        assertEquals(0, player.startCount)
    }

    @Test
    fun startFailure_releasesOnceAndExposesCause() {
        val expected = IllegalStateException("start failed")
        val factory = FakeRoleplayVoicePlayerFactory().apply {
            onCreate = { FakeRoleplayVoicePlayer(startFailure = expected) }
        }
        val controller = RoleplayVoicePlaybackController(factory)

        controller.toggle("voice.mp3")
        val player = factory.singlePlayer()
        player.dispatchPrepared()

        val errorState = controller.state as RoleplayVoicePlaybackState.Error
        assertSame(expected, errorState.cause)
        assertEquals(1, player.startCount)
        assertEquals(1, player.releaseCount)
    }

    @Test
    fun dispose_isIdempotentAndReleasedCallbacksCannotWriteState() {
        val factory = FakeRoleplayVoicePlayerFactory()
        val states = mutableListOf<RoleplayVoicePlaybackState>()
        val controller = RoleplayVoicePlaybackController(factory, states::add)

        controller.toggle("voice.mp3")
        val player = factory.singlePlayer()
        player.dispatchPrepared()
        controller.dispose()
        controller.dispose()

        assertEquals(RoleplayVoicePlaybackState.Idle, controller.state)
        assertEquals(1, player.releaseCount)

        player.dispatchCompletion()
        player.dispatchError()
        player.dispatchPrepared()
        controller.toggle("voice-2.mp3")

        assertEquals(1, factory.players.size)
        assertEquals(1, player.releaseCount)
        assertEquals(1, player.startCount)
        assertEquals(
            listOf(RoleplayVoicePlaybackState.Preparing, RoleplayVoicePlaybackState.Playing),
            states,
        )
    }

    @Test
    fun keyChange_disposedControllerCannotOverwriteReplacementState() {
        val oldFactory = FakeRoleplayVoicePlayerFactory()
        var renderedState: RoleplayVoicePlaybackState = RoleplayVoicePlaybackState.Idle
        val oldController = RoleplayVoicePlaybackController(oldFactory) { renderedState = it }
        oldController.toggle("old.mp3")
        val oldPlayer = oldFactory.singlePlayer()

        oldController.dispose()
        renderedState = RoleplayVoicePlaybackState.Idle
        val newFactory = FakeRoleplayVoicePlayerFactory()
        val newController = RoleplayVoicePlaybackController(newFactory) { renderedState = it }
        oldPlayer.dispatchPrepared()

        assertEquals(RoleplayVoicePlaybackState.Idle, renderedState)
        assertEquals(1, oldPlayer.releaseCount)

        newController.toggle("new.mp3")
        newFactory.singlePlayer().dispatchPrepared()

        assertEquals(RoleplayVoicePlaybackState.Playing, renderedState)
    }

    private class FakeRoleplayVoicePlayerFactory : RoleplayVoicePlayerFactory {
        val players = mutableListOf<FakeRoleplayVoicePlayer>()
        var onCreate: () -> FakeRoleplayVoicePlayer = { FakeRoleplayVoicePlayer() }

        override fun create(): RoleplayVoicePlayer {
            return onCreate().also(players::add)
        }

        fun singlePlayer(): FakeRoleplayVoicePlayer = players.single()
    }

    private class FakeRoleplayVoicePlayer(
        private val prepareFailure: Throwable? = null,
        private val startFailure: Throwable? = null,
    ) : RoleplayVoicePlayer {
        val events = mutableListOf<String>()
        var startCount: Int = 0
            private set
        var clearListenersCount: Int = 0
            private set
        var releaseCount: Int = 0
            private set

        private var retainedPreparedListener: (() -> Unit)? = null
        private var retainedCompletionListener: (() -> Unit)? = null
        private var retainedErrorListener: (() -> Boolean)? = null

        override fun setDataSource(path: String) {
            events += "setDataSource:$path"
        }

        override fun setOnPreparedListener(listener: () -> Unit) {
            events += "setOnPreparedListener"
            retainedPreparedListener = listener
        }

        override fun setOnCompletionListener(listener: () -> Unit) {
            events += "setOnCompletionListener"
            retainedCompletionListener = listener
        }

        override fun setOnErrorListener(listener: () -> Boolean) {
            events += "setOnErrorListener"
            retainedErrorListener = listener
        }

        override fun prepareAsync() {
            events += "prepareAsync"
            prepareFailure?.let { throw it }
        }

        override fun start() {
            events += "start"
            startCount += 1
            startFailure?.let { throw it }
        }

        override fun clearListeners() {
            events += "clearListeners"
            clearListenersCount += 1
        }

        override fun release() {
            events += "release"
            releaseCount += 1
        }

        fun dispatchPrepared() {
            retainedPreparedListener?.invoke()
        }

        fun dispatchCompletion() {
            retainedCompletionListener?.invoke()
        }

        fun dispatchError(): Boolean {
            return retainedErrorListener?.invoke() ?: false
        }
    }
}
