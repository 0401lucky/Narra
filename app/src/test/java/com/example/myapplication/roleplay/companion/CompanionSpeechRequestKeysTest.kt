package com.example.myapplication.roleplay.companion

import com.example.myapplication.data.repository.ai.*
import com.example.myapplication.data.repository.tts.MimoTtsClient
import com.example.myapplication.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionSpeechRequestKeysTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun effectiveTextLanguageEndpointModelVoiceAndTranslationKeys_doNotCollide() {
        val identity = CompanionSpeechSynthesisIdentity(
            "https://example.test/v1/chat/completions",
            "mimo-v2.5-tts",
            "alice",
            VoiceProfileMode.PRESET,
            mapOf("format" to "wav", "voiceId" to "Mia")
        )
        val base = CompanionSpeechRequestKeys.speech(" Hello ", "中文", "英语", identity)
        assertEquals(
            base,
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(parameters = mapOf("voiceId" to "Mia", "format" to "wav"))
            )
        )
        val variants = listOf(
            CompanionSpeechRequestKeys.speech("other", "中文", "英语", identity),
            CompanionSpeechRequestKeys.speech("Hello", "日语", "英语", identity),
            CompanionSpeechRequestKeys.speech("Hello", "中文", "日语", identity),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(endpoint = "https://example.test/v2/chat/completions")
            ),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(model = "other-model")
            ),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(characterId = "bob")
            ),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(parameters = identity.parameters + ("voiceId" to "Chloe"))
            ),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity.copy(builderVersion = "next-builder")
            ),
            CompanionSpeechRequestKeys.speech(
                "Hello",
                "中文",
                "英语",
                identity,
                TranslationRequestKey("a".repeat(64))
            ),
        )
        assertEquals(variants.size, variants.distinct().size)
        assertTrue(variants.none { it == base })
        assertTrue((variants + base).all { Regex("[a-f0-9]{64}").matches(it.value) })
    }

    @Test
    fun cloneSamePathAndSize_differentActualBytesHaveDifferentKeyAndFrozenPayload() = runTest {
        val sample = files.newFile("sample.wav")
        val oldBytes = wav(1)
        val newBytes = wav(2)
        assertEquals(oldBytes.size, newBytes.size)
        val settings = VoiceSynthesisSettings(
            apiKey = "synthetic-secret",
            assistantProfiles = mapOf(
                "alice" to VoiceProfile(
                    mode = VoiceProfileMode.VOICE_CLONE,
                    voiceCloneSamplePath = sample.path,
                    voiceCloneSampleMimeType = "audio/mpeg",
                    voiceCloneSampleSizeBytes = oldBytes.size.toLong()
                )
            )
        )
        val factory = CompanionTtsRequestFactory(StandardTestDispatcher(testScheduler))
        sample.writeBytes(oldBytes)
        val first = factory.freeze(settings, "alice")
        sample.writeBytes(newBytes)
        val second = factory.freeze(settings, "alice")
        val firstKey =
            CompanionSpeechRequestKeys.speech("Hello", "自动检测", "英语", first.identity)
        val secondKey =
            CompanionSpeechRequestKeys.speech("Hello", "自动检测", "英语", second.identity)
        assertNotEquals(firstKey, secondKey)
        assertEquals("audio/wav", first.identity.parameters["sampleMime"])
        assertEquals(
            CompanionSpeechRequestKeys.bytesHash(oldBytes),
            first.identity.parameters["sampleHash"]
        )
        assertEquals(
            CompanionSpeechRequestKeys.bytesHash(newBytes),
            second.identity.parameters["sampleHash"]
        )
        val oldPayload = first.request("Hello").audio.voice!!.substringAfter(";base64,")
        assertArrayEquals(oldBytes, java.util.Base64.getDecoder().decode(oldPayload))
        assertFalse(firstKey.value.contains("synthetic-secret"))
        assertFalse(first.toString().contains("synthetic-secret"))
        assertFalse(first.toString().contains(sample.path))
        assertFalse(first.toString().contains(oldPayload))
    }

    @Test
    fun presetDesignAndClone_identityMatchActualRequestsAndNoUnsupportedEmotionParameters() =
        runTest {
            val sample = files.newFile("sample.wav").apply { writeBytes(wav(7)) }
            val profiles = listOf(
                VoiceProfile(mode = VoiceProfileMode.PRESET, presetVoiceId = " Mia "),
                VoiceProfile(
                    mode = VoiceProfileMode.VOICE_DESIGN,
                    voiceDesignPrompt = " soft voice "
                ),
                VoiceProfile(
                    mode = VoiceProfileMode.VOICE_CLONE,
                    voiceCloneSamplePath = sample.path
                ),
            )
            val factory = CompanionTtsRequestFactory(StandardTestDispatcher(testScheduler))
            val keys = profiles.map { profile ->
                val frozen = factory.freeze(
                    VoiceSynthesisSettings(
                        apiKey = "fake",
                        assistantProfiles = mapOf("alice" to profile)
                    ), "alice"
                )
                val request = frozen.request(" Text ")
                assertEquals(frozen.identity.model, request.model)
                assertEquals("Text", request.messages.last().content)
                assertEquals("wav", request.audio.format)
                when (profile.mode) {
                    VoiceProfileMode.PRESET -> assertEquals("Mia", request.audio.voice)
                    VoiceProfileMode.VOICE_DESIGN -> {
                        assertNull(request.audio.voice); assertEquals(
                            "soft voice",
                            request.messages.first().content
                        )
                    }

                    VoiceProfileMode.VOICE_CLONE -> assertTrue(request.audio.voice!!.startsWith("data:audio/wav;base64,"))
                    else -> fail()
                }
                assertFalse(frozen.identity.parameters.keys.any {
                    it in setOf(
                        "emotion",
                        "speed",
                        "pitch"
                    )
                })
                CompanionSpeechRequestKeys.speech("Text", "中文", "中文", frozen.identity)
            }
            assertEquals(3, keys.distinct().size)
            val pathKey = "synthetic-secret-token"
            listOf(
                "https://proxy.test/$pathKey/v1",
                "https://proxy.test/synthetic%2Dsecret%2Dtoken/v1",
            ).forEach { baseUrl ->
                val failure = runCatching {
                    factory.freeze(VoiceSynthesisSettings(apiKey = pathKey, baseUrl = baseUrl), "alice")
                }.exceptionOrNull()!!
                assertTrue(failure is IllegalArgumentException)
                assertFalse(failure.message.orEmpty().contains(pathKey))
            }
            val loopback = factory.freeze(
                VoiceSynthesisSettings(apiKey = pathKey, baseUrl = "http://127.0.0.1:43210/v1"), "alice",
            )
            assertEquals("http://127.0.0.1:43210/v1/chat/completions", loopback.identity.endpoint)
        }

    @Test
    fun translationKeys_coverFrozenConfigurationAndBuilderAndIgnoreCredential() {
        val identity = TranslationRequestIdentity(
            "provider",
            "https://example.test/v1/",
            "https://example.test/v1/chat/completions",
            "translator",
            ProviderApiProtocol.OPENAI_COMPATIBLE,
            OpenAiTextApiMode.CHAT_COMPLETIONS
        )

        fun snapshot(id: TranslationRequestIdentity = identity, key: String = "secret") =
            TranslationRequestSnapshot(id, key, null)

        val base = CompanionSpeechRequestKeys.translation(snapshot(), "原文", "英语", "中文")
        assertEquals(
            base,
            CompanionSpeechRequestKeys.translation(
                snapshot(key = "new-secret"),
                "原文",
                "英语",
                "中文"
            )
        )
        val changed = listOf(
            identity.copy(endpoint = "https://example.test/v2/chat/completions"),
            identity.copy(modelId = "next-translator"),
            identity.copy(providerId = "other"),
            identity.copy(apiProtocol = ProviderApiProtocol.ANTHROPIC),
            identity.copy(textApiMode = OpenAiTextApiMode.RESPONSES),
            identity.copy(builderVersion = "translation-text-v2"),
        ).map { CompanionSpeechRequestKeys.translation(snapshot(it), "原文", "英语", "中文") }
        assertTrue(changed.none { it == base })
        assertNotEquals(
            base,
            CompanionSpeechRequestKeys.translation(snapshot(), "原文", "日语", "中文")
        )
        assertNotEquals(
            base,
            CompanionSpeechRequestKeys.translation(snapshot(), "另文", "英语", "中文")
        )
    }

    @Test
    fun longSelectedText_usesCompleteGraphemesAndPreservesExactSourceOrder() {
        val text = "a".repeat(798) + "👩🏽‍🚀" + "b".repeat(810) + "。" + "c".repeat(9)
        val source = CompanionReplySource(
            "id",
            "conversation",
            "turn",
            1,
            1,
            1,
            companionRawHash(text),
            characterId = "alice"
        )
        val unit = CompanionSpeechUnit(
            "unit",
            source,
            "beat",
            text,
            CompanionRawSpan(31, 42),
            RoleplaySpeaker.NARRATOR,
            "旁白",
            null,
            RoleplayContentType.NARRATION,
            "完整raw"
        )
        val selection = CompanionSpeechSelection(
            listOf(unit),
            listOf(CompanionSpeechRange(unit.id, 0, text.length))
        )
        val parts = CompanionVoiceSegments.from(selection)
        assertTrue(parts.size > 1)
        assertEquals(text, parts.joinToString("") { it.originalText })
        assertTrue(parts.all { it.originalText.length <= CompanionVoiceSegments.MAX_SEGMENT_CHARS })
        val boundaries = companionGraphemeEnds(text).toSet() + 0
        assertTrue(parts.all { it.range.start in boundaries && it.range.endExclusive in boundaries })
        assertTrue(parts.all { it.unit == unit && it.unit.rawSpan == CompanionRawSpan(31, 42) })
        assertEquals("完整raw", parts.first().unit.rawText)
    }

    @Test
    fun boundedTranslationCache_evictionRetainsMostRecentlyUsedOnly() {
        val cache = CompanionTranslationCache(maxEntries = 2, maxCharacters = 12)
        val first = TranslationRequestKey("first")
        val second = TranslationRequestKey("second")
        val third = TranslationRequestKey("third")
        cache.put(first, "first"); cache.put(second, "second")
        assertEquals("first", cache.get(first))
        cache.put(third, "third")
        assertNull(cache.get(second))
        assertEquals("first", cache.get(first))
        assertEquals("third", cache.get(third))
    }

    @Test
    fun actualCloneHttp_afterSampleReplacementUsesSameFrozenBytesAsCacheKey() = runBlocking<Unit> {
        val server = MockWebServer()
        server.start()
        try {
            val old = wav(3)
            val sample = files.newFile("http-clone.wav").apply { writeBytes(old) }
            val settings = VoiceSynthesisSettings(
                apiKey = "fake-http-key",
                baseUrl = server.url("/mimo/v1").toString(),
                assistantProfiles = mapOf(
                    "alice" to VoiceProfile(
                        mode = VoiceProfileMode.VOICE_CLONE,
                        voiceCloneSamplePath = sample.path
                    )
                )
            )
            val frozen =
                CompanionTtsRequestFactory(Dispatchers.Unconfined).freeze(settings, "alice")
            val key = CompanionSpeechRequestKeys.speech(" Text ", "中文", "中文", frozen.identity)
            sample.writeBytes(wav(4))
            val returned = java.util.Base64.getEncoder().encodeToString(wav(5))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"audio":{"data":"$returned"}}}]}"""))
            val result =
                MimoTtsClient().synthesize(frozen.apiKey, frozen.request(" Text "), frozen.baseUrl)
            assertEquals(returned, result.b64Audio)
            val actual = server.takeRequest()
            assertEquals("/mimo/v1/chat/completions", actual.path)
            assertEquals(server.url(actual.path!!).toString(), frozen.identity.endpoint)
            assertEquals("fake-http-key", actual.getHeader("api-key"))
            val body = com.google.gson.JsonParser.parseString(actual.body.readUtf8()).asJsonObject
            assertEquals(frozen.identity.model, body["model"].asString)
            assertEquals(
                "Text",
                body["messages"].asJsonArray.last().asJsonObject["content"].asString
            )
            val payload = body["audio"].asJsonObject["voice"].asString.substringAfter(";base64,")
            assertArrayEquals(old, java.util.Base64.getDecoder().decode(payload))
            assertEquals(
                CompanionSpeechRequestKeys.bytesHash(old),
                frozen.identity.parameters["sampleHash"]
            )
            assertEquals(
                key,
                CompanionSpeechRequestKeys.speech("Text", "中文", "中文", frozen.identity)
            )
            assertFalse(key.value.contains("fake-http-key"))
        } finally {
            server.shutdown()
        }
    }

    private fun wav(value: Byte): ByteArray =
        ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(52); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(16); put(ByteArray(16) { value })
        }.array()
}
