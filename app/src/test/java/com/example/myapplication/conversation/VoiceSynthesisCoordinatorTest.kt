package com.example.myapplication.conversation

import com.example.myapplication.data.local.ConversationStore
import com.example.myapplication.data.repository.AudioFileStorage
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.tts.MimoTtsClient
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.VoiceAudioStatus
import com.example.myapplication.model.VoiceProfile
import com.example.myapplication.model.VoiceProfileMode
import com.example.myapplication.model.VoiceSynthesisSettings
import com.example.myapplication.model.voiceAudioErrorMessage
import com.example.myapplication.model.voiceAudioPath
import com.example.myapplication.model.voiceAudioStatus
import com.example.myapplication.model.voiceMessageActionPart
import com.example.myapplication.model.voiceMessageContent
import com.example.myapplication.model.withVoiceAudioFailure
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class VoiceSynthesisCoordinatorTest {
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
    fun generate_assistantVoiceMessageSuccess_updatesReadyMetadataAndSavesAudio() = runTest {
        val audioBytes = sampleWavBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"${encode(audioBytes)}"}}}]}"""),
        )
        val message = assistantVoiceMessage()
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(message),
        )
        val audioDirectory = temporaryFolder.newFolder("voice")
        val coordinator = coordinator(store, audioDirectory)

        val updatedMessages = coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = enabledSettings(),
            ),
        )

        val updatedPart = requireNotNull(updatedMessages).single().parts.single()
        assertEquals(VoiceAudioStatus.READY, updatedPart.voiceAudioStatus())
        assertTrue(updatedPart.voiceAudioPath().endsWith("voice-message-1-voice-1.wav"))
        assertArrayEquals(audioBytes, File(updatedPart.voiceAudioPath()).readBytes())
        assertEquals("今晚想听你说晚安", updatedPart.voiceMessageContent())
        assertEquals(MessageStatus.COMPLETED, updatedMessages.single().status)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("test-key", recorded.getHeader("api-key"))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals("mimo-v2.5-tts", body.get("model").asString)
        assertEquals("assistant", body.getAsJsonArray("messages")[0].asJsonObject.get("role").asString)
        assertEquals("今晚想听你说晚安", body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        assertEquals("冰糖", body.getAsJsonObject("audio").get("voice").asString)
    }

    @Test
    fun generate_failure_updatesPartFailedAndKeepsMessageText() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"not-valid-base64"}}}]}"""),
        )
        val message = assistantVoiceMessage()
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(message),
        )
        val coordinator = coordinator(store, temporaryFolder.newFolder("voice"))

        val updatedMessages = coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = enabledSettings(),
            ),
        )

        val updatedMessage = requireNotNull(updatedMessages).single()
        val updatedPart = updatedMessage.parts.single()
        assertEquals(VoiceAudioStatus.FAILED, updatedPart.voiceAudioStatus())
        assertTrue(updatedPart.voiceAudioErrorMessage().isNotBlank())
        assertEquals("", updatedPart.voiceAudioPath())
        assertEquals("今晚想听你说晚安", updatedPart.voiceMessageContent())
        assertEquals(MessageStatus.COMPLETED, updatedMessage.status)
    }

    @Test
    fun generate_disabledOrMissingKey_doesNotRequestMimoAndKeepsFallbackText() = runTest {
        val message = assistantVoiceMessage()
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(message),
        )
        val coordinator = coordinator(store, temporaryFolder.newFolder("voice"))

        val disabledResult = coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = AppSettings(
                    voiceSynthesisSettings = VoiceSynthesisSettings(
                        enabled = false,
                        apiKey = "test-key",
                    ),
                ),
            ),
        )
        val missingKeyResult = coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = AppSettings(
                    voiceSynthesisSettings = VoiceSynthesisSettings(
                        enabled = true,
                        apiKey = "",
                    ),
                ),
            ),
        )

        assertNull(disabledResult)
        assertNull(missingKeyResult)
        assertEquals(0, server.requestCount)
        assertNull(store.messages.single().parts.single().voiceAudioStatus())
        assertEquals("今晚想听你说晚安", store.messages.single().parts.single().voiceMessageContent())
    }

    @Test
    fun generate_voiceDesignProfile_sendsPromptAsUserAndTextAsAssistant() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"${encode(sampleWavBytes())}"}}}]}"""),
        )
        val message = assistantVoiceMessage(speakerId = "assistant-special")
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(message),
        )
        val coordinator = coordinator(store, temporaryFolder.newFolder("voice"))

        coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = enabledSettings(
                    assistantProfiles = mapOf(
                        "assistant-special" to VoiceProfile(
                            mode = VoiceProfileMode.VOICE_DESIGN,
                            voiceDesignPrompt = "年轻、温柔、贴近耳边的中文女声。",
                        ),
                    ),
                ),
            ),
        )

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("mimo-v2.5-tts-voicedesign", body.get("model").asString)
        assertEquals("user", body.getAsJsonArray("messages")[0].asJsonObject.get("role").asString)
        assertEquals("年轻、温柔、贴近耳边的中文女声。", body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        assertEquals("assistant", body.getAsJsonArray("messages")[1].asJsonObject.get("role").asString)
        assertEquals("今晚想听你说晚安", body.getAsJsonArray("messages")[1].asJsonObject.get("content").asString)
        assertTrue(!body.getAsJsonObject("audio").has("voice"))
    }

    @Test
    fun generate_voiceCloneProfile_sendsSampleDataUriAsVoice() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"${encode(sampleWavBytes())}"}}}]}"""),
        )
        val sampleFile = temporaryFolder.newFile("clone.wav").apply {
            writeBytes(sampleWavBytes())
        }
        val message = assistantVoiceMessage(speakerId = "assistant-clone")
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(message),
        )
        val coordinator = coordinator(store, temporaryFolder.newFolder("voice"))

        coordinator.generate(
            request = request(
                messages = listOf(message),
                settings = enabledSettings(
                    assistantProfiles = mapOf(
                        "assistant-clone" to VoiceProfile(
                            mode = VoiceProfileMode.VOICE_CLONE,
                            voiceCloneSamplePath = sampleFile.absolutePath,
                            voiceCloneSampleMimeType = "audio/wav",
                            voiceCloneSampleFileName = sampleFile.name,
                            voiceCloneSampleSizeBytes = sampleFile.length(),
                        ),
                    ),
                ),
            ),
        )

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("mimo-v2.5-tts-voiceclone", body.get("model").asString)
        assertEquals("user", body.getAsJsonArray("messages")[0].asJsonObject.get("role").asString)
        assertEquals("", body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        assertEquals("assistant", body.getAsJsonArray("messages")[1].asJsonObject.get("role").asString)
        assertEquals("今晚想听你说晚安", body.getAsJsonArray("messages")[1].asJsonObject.get("content").asString)
        assertEquals(
            "data:audio/wav;base64,${encode(sampleWavBytes())}",
            body.getAsJsonObject("audio").get("voice").asString,
        )
    }

    @Test
    fun generate_failedVoiceMessage_canRetryOnNextGenerationPass() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"${encode(sampleWavBytes())}"}}}]}"""),
        )
        val failedMessage = assistantVoiceMessage().copy(
            parts = listOf(
                voiceMessageActionPart(
                    content = "今晚想听你说晚安",
                    id = "voice-1",
                ).withVoiceAudioFailure("上次网络失败"),
            ),
        )
        val store = InMemoryConversationStore(
            conversation = conversation(),
            initialMessages = listOf(failedMessage),
        )
        val coordinator = coordinator(store, temporaryFolder.newFolder("voice"))

        val updatedMessages = coordinator.generate(
            request = request(
                messages = listOf(failedMessage),
                settings = enabledSettings(),
            ),
        )

        assertEquals(VoiceAudioStatus.READY, requireNotNull(updatedMessages).single().parts.single().voiceAudioStatus())
        assertEquals(1, server.requestCount)
    }

    private fun coordinator(
        store: InMemoryConversationStore,
        audioDirectory: File,
    ): VoiceSynthesisCoordinator {
        val client = MimoTtsClient(
            baseUrl = server.url("/v1").toString(),
        )
        return VoiceSynthesisCoordinator(
            mimoTtsClient = client,
            conversationRepository = ConversationRepository(store),
            audioSaver = { b64Audio, fileNamePrefix ->
                AudioFileStorage.saveBase64AudioToDirectory(
                    directory = audioDirectory,
                    b64Data = b64Audio,
                    fileNamePrefix = fileNamePrefix,
                )
            },
        )
    }

    private fun request(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): VoiceSynthesisRequest {
        return VoiceSynthesisRequest(
            conversationId = "conversation-1",
            selectedModel = "test-model",
            messages = messages,
            settings = settings,
            fallbackAssistantId = "assistant-1",
        )
    }

    private fun enabledSettings(
        assistantProfiles: Map<String, VoiceProfile> = emptyMap(),
    ): AppSettings {
        return AppSettings(
            voiceSynthesisSettings = VoiceSynthesisSettings(
                enabled = true,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                defaultProfile = VoiceProfile(
                    mode = VoiceProfileMode.PRESET,
                    presetVoiceId = "冰糖",
                ),
                assistantProfiles = assistantProfiles,
            ),
        )
    }

    private fun conversation(): Conversation {
        return Conversation(
            id = "conversation-1",
            createdAt = 0L,
            updatedAt = 0L,
            assistantId = "assistant-1",
        )
    }

    private fun assistantVoiceMessage(
        speakerId: String = "assistant-1",
    ): ChatMessage {
        return ChatMessage(
            id = "message-1",
            conversationId = "conversation-1",
            role = MessageRole.ASSISTANT,
            content = "[voice_message]今晚想听你说晚安[/voice_message]",
            status = MessageStatus.COMPLETED,
            parts = listOf(
                voiceMessageActionPart(
                    content = "今晚想听你说晚安",
                    id = "voice-1",
                ),
            ),
            speakerId = speakerId,
        )
    }

    private fun sampleWavBytes(): ByteArray {
        return byteArrayOf(
            'R'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            'F'.code.toByte(),
            0,
            0,
            0,
            0,
            'W'.code.toByte(),
            'A'.code.toByte(),
            'V'.code.toByte(),
            'E'.code.toByte(),
            1,
            2,
            3,
        )
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }
}

private class InMemoryConversationStore(
    private var conversation: Conversation,
    initialMessages: List<ChatMessage>,
) : ConversationStore {
    private val conversationsFlow = MutableStateFlow(listOf(conversation))
    private val messagesFlow = MutableStateFlow(initialMessages)

    var messages: List<ChatMessage>
        get() = messagesFlow.value
        private set(value) {
            messagesFlow.value = value
        }

    override fun observeConversations(): Flow<List<Conversation>> = conversationsFlow

    override fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>> {
        return MutableStateFlow(
            conversationsFlow.value.filter { conversation -> conversation.assistantId == assistantId },
        )
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = messagesFlow

    override suspend fun listConversations(): List<Conversation> = conversationsFlow.value

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversation.takeIf { it.id == conversationId }
    }

    override suspend fun listMessages(conversationId: String): List<ChatMessage> = messages

    override suspend fun upsertConversationMetadata(conversation: Conversation) {
        this.conversation = conversation
        conversationsFlow.value = listOf(conversation)
    }

    override suspend fun replaceConversationSnapshot(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        upsertConversationMetadata(conversation)
        this.messages = messages
    }

    override suspend fun updateConversationMessages(
        conversation: Conversation,
        conversationId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ): List<ChatMessage> {
        upsertConversationMetadata(conversation)
        messages = transform(messages)
        return messages
    }

    override suspend fun upsertConversationWithMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
    ) {
        upsertConversationMetadata(conversation)
        this.messages = messages
    }

    override suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    ) {
        upsertConversationMetadata(conversation)
        messages = emptyList()
    }

    override suspend fun upsertMessages(messages: List<ChatMessage>) {
        this.messages = messages
    }

    override suspend fun deleteConversation(conversationId: String) {
        if (conversation.id == conversationId) {
            conversationsFlow.value = emptyList()
            messages = emptyList()
        }
    }

    override suspend fun clearMessages(conversationId: String) {
        messages = emptyList()
    }
}
