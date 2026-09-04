package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.asr.AudioRecording
import com.example.myapplication.data.repository.asr.RecognitionEvent
import com.example.myapplication.data.repository.asr.SpeechRecognitionService
import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun updateSettings_disabledHidesButtonAndMissingCredentialDisablesIt() = runTest {
        val controller = createController()

        controller.updateSettings(VoiceRecognitionSettings(enabled = false))
        assertFalse(controller.uiState.value.visible)
        assertFalse(controller.uiState.value.enabled)

        controller.updateSettings(
            VoiceRecognitionSettings(
                enabled = true,
                engine = VoiceRecognitionEngine.REMOTE_MODEL,
            ),
        )
        assertTrue(controller.uiState.value.visible)
        assertFalse("凭据不全时按钮应置灰", controller.uiState.value.enabled)

        controller.updateSettings(remoteSettings())
        assertTrue(controller.uiState.value.enabled)

        // 系统通道不需要任何凭据
        controller.updateSettings(VoiceRecognitionSettings(enabled = true))
        assertTrue(controller.uiState.value.enabled)
    }

    @Test
    fun startRecording_whenDisabledDoesNothing() = runTest {
        val recorder = FakeAudioRecorder(temporaryFolder.newFile("a.m4a"))
        val controller = createController(recorder = recorder)
        controller.updateSettings(VoiceRecognitionSettings(enabled = false))

        controller.startRecording()
        runCurrent()

        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals(0, recorder.startCount)
    }

    @Test
    fun startRecording_remoteWithoutCredentialShowsGuidanceAndDoesNotRecord() = runTest {
        val recorder = FakeAudioRecorder(temporaryFolder.newFile("a.m4a"))
        val controller = createController(recorder = recorder)
        controller.updateSettings(
            VoiceRecognitionSettings(
                enabled = true,
                engine = VoiceRecognitionEngine.REMOTE_MODEL,
            ),
        )

        controller.startRecording()
        runCurrent()

        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals(0, recorder.startCount)
        assertTrue(controller.uiState.value.message.contains("语音识别 API"))
    }

    @Test
    fun remoteChannel_happyPathAppendsTextAndDeletesAudioFile() = runTest {
        val audioFile = temporaryFolder.newFile("happy.m4a").apply { writeBytes(ByteArray(16)) }
        val recorder = FakeAudioRecorder(audioFile)
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = recorder,
            remoteService = FakeService(listOf(RecognitionEvent.Final("  今天天气不错  "))),
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        assertEquals(VoiceInputPhase.RECORDING, controller.uiState.value.phase)

        advanceTimeBy(1_000)
        controller.stopAndTranscribe()
        advanceUntilIdle()

        assertEquals(listOf("今天天气不错"), recognized)
        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals("", controller.uiState.value.message)
        assertFalse("识别结束后音频文件必须删除", audioFile.exists())
    }

    @Test
    fun remoteChannel_tooShortRecordingIsDiscardedWithoutTranscribing() = runTest {
        val audioFile = temporaryFolder.newFile("short.m4a").apply { writeBytes(ByteArray(16)) }
        val recorder = FakeAudioRecorder(audioFile)
        val service = FakeService(listOf(RecognitionEvent.Final("不该出现")))
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = recorder,
            remoteService = service,
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        advanceTimeBy(100)
        controller.stopAndTranscribe()
        advanceUntilIdle()

        assertTrue(recognized.isEmpty())
        assertEquals(0, service.recognizeCount)
        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals("说话时间太短", controller.uiState.value.message)
        assertTrue("过短录音应走 cancel 而非 stop", recorder.cancelCount > 0)
    }

    @Test
    fun cancel_producesNoTextAndLeavesNoAudioFile() = runTest {
        val audioFile = temporaryFolder.newFile("cancel.m4a").apply { writeBytes(ByteArray(16)) }
        val recorder = FakeAudioRecorder(audioFile)
        val service = FakeService(listOf(RecognitionEvent.Final("不该出现")))
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = recorder,
            remoteService = service,
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        advanceTimeBy(2_000)
        controller.cancel()
        advanceUntilIdle()

        assertTrue(recognized.isEmpty())
        assertEquals(0, service.recognizeCount)
        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertFalse(audioFile.exists())
    }

    @Test
    fun remoteChannel_failureLandsInFailedPhaseAndDismissReturnsToIdle() = runTest {
        val audioFile = temporaryFolder.newFile("fail.m4a").apply { writeBytes(ByteArray(16)) }
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = FakeAudioRecorder(audioFile),
            remoteService = FakeService(listOf(RecognitionEvent.Failed("语音识别鉴权失败，请检查 API Key"))),
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        advanceTimeBy(1_000)
        controller.stopAndTranscribe()
        advanceUntilIdle()

        assertTrue(recognized.isEmpty())
        assertEquals(VoiceInputPhase.FAILED, controller.uiState.value.phase)
        assertEquals("语音识别鉴权失败，请检查 API Key", controller.uiState.value.message)
        assertFalse(audioFile.exists())

        controller.dismissMessage()
        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals("", controller.uiState.value.message)
    }

    @Test
    fun blankFinalTextIsTreatedAsFailureInsteadOfEmptyAppend() = runTest {
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = FakeAudioRecorder(temporaryFolder.newFile("blank.m4a")),
            remoteService = FakeService(listOf(RecognitionEvent.Final("   "))),
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        advanceTimeBy(1_000)
        controller.stopAndTranscribe()
        advanceUntilIdle()

        assertTrue(recognized.isEmpty())
        assertEquals(VoiceInputPhase.FAILED, controller.uiState.value.phase)
        assertEquals("没听清，再说一次？", controller.uiState.value.message)
    }

    @Test
    fun remoteChannel_recorderFailureShowsMicrophoneGuidance() = runTest {
        val controller = createController(
            recorder = FailingAudioRecorder(),
            remoteService = FakeService(listOf(RecognitionEvent.Final("不该出现"))),
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()

        assertEquals(VoiceInputPhase.FAILED, controller.uiState.value.phase)
        assertEquals("无法开始录音，请检查麦克风权限", controller.uiState.value.message)
    }

    @Test
    fun systemChannel_partialsPreviewLevelsDriveAmplitudeAndReleaseYieldsFinalText() = runTest {
        val service = ReleaseAwareService(
            beforeRelease = listOf(
                RecognitionEvent.Level(0.5f),
                RecognitionEvent.Partial("今天"),
            ),
            afterRelease = listOf(RecognitionEvent.Final("今天天气不错")),
        )
        val recorder = FakeAudioRecorder(temporaryFolder.newFile("system.m4a"))
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = recorder,
            systemService = service,
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(VoiceRecognitionSettings(enabled = true))

        controller.startRecording()
        advanceTimeBy(600)
        runCurrent()

        // 系统通道自己采音，不该动 AudioRecorder
        assertEquals(0, recorder.startCount)
        assertEquals("今天", controller.uiState.value.partialText)
        assertEquals(0.5f, controller.uiState.value.amplitude, 0.001f)

        controller.stopAndTranscribe()
        advanceUntilIdle()

        assertTrue("松手必须通知识别器收音", service.releaseObserved)
        assertEquals(listOf("今天天气不错"), recognized)
        assertEquals(VoiceInputPhase.IDLE, controller.uiState.value.phase)
        assertEquals("", controller.uiState.value.partialText)
    }

    @Test
    fun recordingLongerThanLimitStopsAutomaticallyWithNotice() = runTest {
        val audioFile = temporaryFolder.newFile("long.m4a").apply { writeBytes(ByteArray(16)) }
        val recognized = mutableListOf<String>()
        val controller = createController(
            recorder = FakeAudioRecorder(audioFile),
            remoteService = FakeService(listOf(RecognitionEvent.Final("很长的一段话"))),
            onTextRecognized = { recognized += it },
        )
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        advanceUntilIdle()

        assertEquals(listOf("很长的一段话"), recognized)
        assertTrue(controller.uiState.value.message.contains("60 秒"))
    }

    @Test
    fun startRecording_whileBusyIsIgnored() = runTest {
        val recorder = FakeAudioRecorder(temporaryFolder.newFile("busy.m4a"))
        val controller = createController(recorder = recorder)
        controller.updateSettings(remoteSettings())

        controller.startRecording()
        runCurrent()
        controller.startRecording()
        runCurrent()

        assertEquals(1, recorder.startCount)
    }

    @Test
    fun appendRecognizedText_appendsWithoutSeparatorForChinese() {
        assertEquals("今天天气不错", appendRecognizedText("今天", "天气不错"))
        assertEquals("今天天气不错", appendRecognizedText("今天", "  天气不错  "))
    }

    @Test
    fun appendRecognizedText_insertsSpaceOnlyBetweenAsciiWords() {
        assertEquals("hello world", appendRecognizedText("hello", "world"))
        assertEquals("abc 123", appendRecognizedText("abc", "123"))
        // 标点或中文相邻时不该补空格
        assertEquals("hello,world", appendRecognizedText("hello,", "world"))
        assertEquals("你好world", appendRecognizedText("你好", "world"))
        assertEquals("hello你好", appendRecognizedText("hello", "你好"))
    }

    @Test
    fun appendRecognizedText_respectsExistingTrailingWhitespaceAndEmptyInputs() {
        assertEquals("今天 天气", appendRecognizedText("今天 ", "天气"))
        assertEquals("天气", appendRecognizedText("", "天气"))
        assertEquals("今天", appendRecognizedText("今天", "   "))
    }

    private fun kotlinx.coroutines.test.TestScope.createController(
        recorder: AudioRecording = FakeAudioRecorder(temporaryFolder.newFile("default.m4a")),
        systemService: SpeechRecognitionService = FakeService(emptyList()),
        remoteService: SpeechRecognitionService = FakeService(emptyList()),
        onTextRecognized: (String) -> Unit = {},
    ): VoiceInputController {
        // backgroundScope 的 launch 在本项目的 coroutines-test 版本下不被
        // advanceUntilIdle 驱动，因此显式用挂在 testScheduler 上的作用域。
        return VoiceInputController(
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            audioRecorder = recorder,
            systemService = systemService,
            remoteService = remoteService,
            onTextRecognized = onTextRecognized,
            nowProvider = { testScheduler.currentTime },
        )
    }

    private fun remoteSettings(): VoiceRecognitionSettings {
        return VoiceRecognitionSettings(
            enabled = true,
            engine = VoiceRecognitionEngine.REMOTE_MODEL,
            apiKey = "sk-test",
            baseUrl = "https://api.example.com/v1",
        )
    }
}

private class FakeAudioRecorder(private val file: File) : AudioRecording {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var cancelCount = 0
        private set
    private var recording = false

    override val isRecording: Boolean get() = recording

    override fun start(): File {
        startCount += 1
        recording = true
        return file
    }

    override fun stop(): File? {
        stopCount += 1
        recording = false
        return file.takeIf { it.exists() }
    }

    override fun cancel() {
        cancelCount += 1
        recording = false
        file.delete()
    }

    override fun amplitude(): Float = 0.3f
}

private class FailingAudioRecorder : AudioRecording {
    override val isRecording: Boolean = false
    override fun start(): File = error("麦克风被占用")
    override fun stop(): File? = null
    override fun cancel() = Unit
    override fun amplitude(): Float = 0f
}

private class FakeService(
    private val events: List<RecognitionEvent>,
) : SpeechRecognitionService {
    var recognizeCount = 0
        private set

    override fun isAvailable(settings: VoiceRecognitionSettings): Boolean = true

    override fun recognize(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
        releaseSignal: Flow<Unit>?,
    ): Flow<RecognitionEvent> {
        recognizeCount += 1
        return flow { events.forEach { emit(it) } }
    }
}

/** 模拟系统通道：先发中间结果，等到松手信号才给最终结果。 */
private class ReleaseAwareService(
    private val beforeRelease: List<RecognitionEvent>,
    private val afterRelease: List<RecognitionEvent>,
) : SpeechRecognitionService {
    var releaseObserved = false
        private set

    override fun isAvailable(settings: VoiceRecognitionSettings): Boolean = true

    override fun recognize(
        audioFile: File?,
        settings: VoiceRecognitionSettings,
        releaseSignal: Flow<Unit>?,
    ): Flow<RecognitionEvent> = flow {
        beforeRelease.forEach { emit(it) }
        releaseSignal?.first()
        releaseObserved = true
        afterRelease.forEach { emit(it) }
    }
}
