package com.example.myapplication.data.repository.asr

import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteSpeechRecognitionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recognize_successReturnsTrimmedTextAndSendsExpectedMultipartFields() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"  今天天气不错  "}"""),
        )
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Final("今天天气不错"), event)

        val recorded = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("whisper-1"))
        assertTrue(body.contains("name=\"response_format\""))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("name=\"file\""))
    }

    @Test
    fun recognize_blankLanguageOmitsLanguageField() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"hi"}"""))
        val service = createService()

        service.recognize(createAudioFile(), remoteSettings(language = "")).first()

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun recognize_missingTextFieldFailsWithChineseMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"duration":1.2}"""))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("识别服务没有返回文本内容"), event)
    }

    @Test
    fun recognize_malformedJsonFailsInsteadOfCrashing() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("这不是 JSON"))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("识别服务没有返回文本内容"), event)
    }

    @Test
    fun recognize_unauthorizedReportsCredentialProblem() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid api key"}}"""),
        )
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        val failed = event as RecognitionEvent.Failed
        assertTrue(failed.message.startsWith("语音识别鉴权失败，请检查 API Key"))
    }

    @Test
    fun recognize_notFoundReportsBaseUrlProblem() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(""))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(
            RecognitionEvent.Failed("语音识别地址不存在，请检查 Base URL"),
            event,
        )
    }

    @Test
    fun recognize_serverErrorReportsServiceProblem() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        val failed = event as RecognitionEvent.Failed
        assertTrue(failed.message.startsWith("语音识别服务异常（HTTP 500）"))
    }

    @Test
    fun recognize_readTimeoutFailsWithoutThrowing() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"太慢了"}""")
                .setBodyDelay(2, TimeUnit.SECONDS),
        )
        val service = createService(
            httpClient = OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .build(),
        )

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertTrue(event is RecognitionEvent.Failed)
    }

    @Test
    fun recognize_missingAudioFileFailsBeforeAnyRequest() = runTest {
        val service = createService()

        val event = service.recognize(null, remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("没有可上传的录音文件"), event)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun recognize_blankApiKeyFailsBeforeAnyRequest() = runTest {
        val service = createService()

        val event = service
            .recognize(createAudioFile(), remoteSettings(apiKey = "   "))
            .first()

        assertEquals(RecognitionEvent.Failed("请先填写语音识别 API Key"), event)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun recognize_insecureBaseUrlFailsWithChineseMessage() = runTest {
        val service = createService()

        val event = service
            .recognize(createAudioFile(), remoteSettings(baseUrl = "http://api.example.com/v1"))
            .first()

        assertEquals(
            RecognitionEvent.Failed("语音识别 Base URL 必须使用 https://，本机调试地址除外"),
            event,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun isAvailable_followsCredentialCompleteness() {
        val service = createService()

        assertTrue(service.isAvailable(remoteSettings()))
        assertFalse(service.isAvailable(remoteSettings(apiKey = "")))
    }

    private fun createService(
        httpClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build(),
    ): RemoteSpeechRecognitionService {
        return RemoteSpeechRecognitionService(httpClient = httpClient)
    }

    private fun remoteSettings(
        apiKey: String = "sk-test-key",
        baseUrl: String = server.url("/v1").toString(),
        language: String = "zh",
    ): VoiceRecognitionSettings {
        return VoiceRecognitionSettings(
            enabled = true,
            engine = VoiceRecognitionEngine.REMOTE_MODEL,
            apiKey = apiKey,
            baseUrl = baseUrl,
            language = language,
        )
    }

    private fun createAudioFile(): File {
        return temporaryFolder.newFile("asr-sample.m4a").apply {
            writeBytes(ByteArray(64) { it.toByte() })
        }
    }
}
