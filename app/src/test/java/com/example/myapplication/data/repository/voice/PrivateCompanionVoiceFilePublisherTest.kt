package com.example.myapplication.data.repository.voice

import com.example.myapplication.model.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateCompanionVoiceFilePublisherTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun publishCompleteWave_preservesBytesAndDurablePendingReceipt() = runTest {
        val directory = temporary.newFolder("voice")
        val publisher =
            PrivateCompanionVoiceFilePublisher(directory, UnconfinedTestDispatcher(testScheduler))
        val bytes = syntheticVoiceWave()
        val audio =
            publisher.publish(claim(), bytes.toByteString().base64(), syntheticVoiceClip()) { true }
        assertTrue(File(audio.receipt.path).readBytes().contentEquals(bytes))
        assertEquals(CompanionAudioIntegrity.sha256(bytes), audio.receipt.sha256)
        assertEquals(bytes.size.toLong(), audio.receipt.sizeBytes)
        assertEquals(CompanionAudioAttachmentState.PENDING, audio.receipt.attachmentState)
        assertEquals(listOf(audio), publisher.publishedReceipts())
        assertTrue(publisher.validate(audio.receipt))
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        publisher.markAttached(audio)
        assertEquals(
            CompanionAudioAttachmentState.ATTACHED,
            publisher.publishedReceipts().single().receipt.attachmentState
        )
    }

    @Test
    fun declaredWaveLengthOrPartialFrame_isRejectedWithoutPublishedFile() = runTest {
        val directory = temporary.newFolder("invalid")
        val publisher =
            PrivateCompanionVoiceFilePublisher(directory, UnconfinedTestDispatcher(testScheduler))
        val invalid = listOf(
            syntheticVoiceWave().dropLast(1).toByteArray(),
            syntheticVoiceWave().apply { this[32] = 3 })
        invalid.forEach { bytes ->
            try {
                publisher.publish(
                    claim(),
                    bytes.toByteString().base64(),
                    syntheticVoiceClip()
                ) { true }
                fail("残缺采样帧不能成为持久音频")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(publisher.publishedReceipts().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun mp3RequiresCompleteFrames_notOnlyId3OrSyncHeader() = runTest {
        val directory = temporary.newFolder("mp3")
        val publisher =
            PrivateCompanionVoiceFilePublisher(directory, UnconfinedTestDispatcher(testScheduler))
        val frames = ByteArray(417 * 2).apply {
            listOf(0, 417).forEach { offset ->
                this[offset] = 0xff.toByte(); this[offset + 1] = 0xfb.toByte(); this[offset + 2] =
                0x90.toByte()
            }
        }
        val audio = publisher.publish(
            claim(),
            frames.toByteString().base64(),
            syntheticVoiceClip()
        ) { true }
        assertEquals("audio/mpeg", audio.receipt.mimeType)
        listOf(
            byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 0),
            frames.copyOf(frames.size - 1)
        ).forEach { invalid ->
            assertTrue(runCatching { CompanionAudioIntegrity.format(invalid) }.isFailure)
        }
    }

    @Test
    fun cancelledAfterAudioWrite_deletesActualOwnedTargetAndPropagatesCancellation() = runTest {
        val directory = temporary.newFolder("cancel")
        val written = CompletableDeferred<Unit>()
        val publisher = PrivateCompanionVoiceFilePublisher(
            directory, UnconfinedTestDispatcher(testScheduler),
            afterAudioPublished = { written.complete(Unit); awaitCancellation() })
        val job = launch {
            publisher.publish(
                claim(),
                syntheticVoiceWave().toByteString().base64(),
                syntheticVoiceClip()
            ) { true }
        }
        written.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertTrue(publisher.publishedReceipts().isEmpty())
    }

    @Test
    fun staleGuardAndHashMismatch_neverBecomeUsableCache() = runTest {
        val directory = temporary.newFolder("guard")
        val publisher =
            PrivateCompanionVoiceFilePublisher(directory, UnconfinedTestDispatcher(testScheduler))
        val result = runCatching {
            publisher.publish(
                claim(),
                syntheticVoiceWave().toByteString().base64(),
                syntheticVoiceClip()
            ) { false }
        }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val audio = publisher.publish(
            claim(),
            syntheticVoiceWave().toByteString().base64(),
            syntheticVoiceClip()
        ) { true }
        File(audio.receipt.path).writeBytes(syntheticVoiceWave().apply { this[lastIndex] = 1 })
        assertFalse(publisher.validate(audio.receipt))
        assertTrue(publisher.publishedReceipts().isEmpty())
    }

    @Test
    fun externalReceiptPath_isRejectedAndDeleteCannotEscapePrivateDirectory() = runTest {
        val directory = temporary.newFolder("private")
        val outside = temporary.newFile("outside.wav").apply { writeBytes(syntheticVoiceWave()) }
        val publisher =
            PrivateCompanionVoiceFilePublisher(directory, UnconfinedTestDispatcher(testScheduler))
        val receipt = CompanionAudioReceipt(
            "owned",
            SpeechRequestKey(companionRawHash("fixture")),
            outside.path,
            "audio/wav",
            CompanionAudioIntegrity.sha256(outside.readBytes()),
            outside.length(),
            1,
            CompanionAudioAttachmentState.PENDING
        )
        assertFalse(publisher.validate(receipt))
        assertTrue(runCatching { publisher.delete("../outside") }.isFailure)
        assertTrue(outside.exists())
    }

    private fun claim() =
        CompanionAudioClaim("owned", SpeechRequestKey(companionRawHash("fixture")), "owner")
}

internal fun syntheticVoiceWave(): ByteArray {
    val payload = ByteArray(32)
    return ByteBuffer.allocate(44 + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + payload.size); put("WAVEfmt ".toByteArray()); putInt(
        16
    )
        putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(payload.size); put(payload)
    }.array()
}

internal fun syntheticVoiceClip(
    text: String = "所选旁白🙂。",
    unit: CompanionSpeechUnit? = null
): CompanionVoiceClip {
    val speech = unit ?: CompanionSpeechUnit(
        "unit",
        CompanionReplySource(
            "message", "conversation", "turn", 1, 1, 1,
            companionRawHash(text), "attempt", characterId = "alice"
        ),
        "beat",
        text,
        CompanionRawSpan(0, text.length),
        RoleplaySpeaker.NARRATOR,
        "旁白",
        CompanionVoiceEmotion.CALM,
        RoleplayContentType.NARRATION,
        text
    )
    return CompanionVoiceClip(
        CompanionVoiceSegment(
            speech,
            CompanionSpeechRange(speech.id, 0, speech.text.length),
            speech.text
        ),
        speech.text.trim(), "自动检测", "原文", CompanionSpeechSynthesisIdentity(
            "https://example.test/v1/chat/completions",
            MIMO_TTS_MODEL_PRESET,
            "alice",
            VoiceProfileMode.PRESET,
            mapOf("format" to "wav", "voiceId" to MIMO_DEFAULT_VOICE_ID)
        )
    )
}
