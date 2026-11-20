package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.data.repository.voice.PrivateCompanionVoiceFilePublisher
import com.example.myapplication.data.repository.voice.syntheticVoiceClip
import com.example.myapplication.data.repository.voice.syntheticVoiceWave
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.testutil.CompanionTestFixture
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomCompanionVoiceRepositoryTest : CompanionTestFixture() {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun publishedAudio_reopensAndAttachesWithoutAnotherPublication() = runBlocking<Unit> {
        useFileDatabase()
        val clip = committedClip()
        val directory = temporary.newFolder("reopen")
        val publisher = CountingPublisher(PrivateCompanionVoiceFilePublisher(directory))
        var failAttachment = true
        val voice = RoomCompanionVoiceRepository(database, publisher, { clock++ }) {
            if (failAttachment) throw IOException("合成挂接故障")
        }
        val key = key("reopen")
        val claim = (voice.claim(key, "owner") as CompanionAudioClaimResult.Granted).claim
        val audio = voice.publish(claim, encodedWave(), clip) { true }
        assertEquals(CompanionAudioAttachmentState.PENDING, audio.receipt.attachmentState)
        assertEquals(
            "PENDING",
            database.companionVoiceDao().resource(audio.receipt.audioId)!!.state
        )
        assertTrue(File(audio.receipt.path).exists())
        assertEquals(1, publisher.publications)
        reopen()
        failAttachment = false
        val recovered = RoomCompanionVoiceRepository(database, publisher, { clock++ })
        val receipt = requireNotNull(recovered.lookup(key, "restored-owner"))
        assertEquals(audio.receipt.audioId, receipt.audioId)
        assertEquals(audio.receipt.sha256, receipt.sha256)
        assertEquals(CompanionAudioAttachmentState.ATTACHED, receipt.attachmentState)
        assertEquals("READY", database.companionVoiceDao().resource(receipt.audioId)!!.state)
        assertEquals(1, publisher.publications)
        assertTrue(recovered.isSourceCurrent(clip.segment.unit.source))
        assertEquals(
            clip.segment.unit.text,
            store.listMessages(clip.segment.unit.source.conversationId)
                .first { it.id == clip.segment.unit.source.messageId }.let {
                    CompanionSpeechSupport.units(CompanionReplyProjector.project(it)!!).first().text
                })
    }

    @Test
    fun realSourceRevisionAndDeletion_keepFavoriteSnapshotAndNotifyObserver() = runBlocking<Unit> {
        val clip = committedClip()
        val directory = temporary.newFolder("source")
        val voice = RoomCompanionVoiceRepository(
            database,
            PrivateCompanionVoiceFilePublisher(directory),
            { clock++ })
        val audio = publish(voice, key("source"), "owner", clip)
        val favorite = voice.favorite(audio)
        assertEquals(favorite.id, voice.favorite(audio).id)
        assertEquals(1, voice.usage().favoriteCount)
        val available = CompletableDeferred<Unit>()
        val invalidated = CompletableDeferred<CompanionVoiceFavorite>()
        val observer = launch {
            voice.observeFavorites("alice").collect { favorites ->
                favorites.firstOrNull()?.let {
                    if (it.sourceState == CompanionVoiceSourceState.AVAILABLE) available.complete(
                        Unit
                    )
                    else invalidated.complete(it)
                }
            }
        }
        try {
            withTimeout(5_000) { available.await() }
            val source = clip.segment.unit.source
            assertTrue(
                repository.editMessage(
                    source.messageId,
                    source.revision,
                    "<dialogue>修订后的原文。</dialogue>"
                )
            )
            val changedFavorite = withTimeout(5_000) { invalidated.await() }
            assertEquals(clip, changedFavorite.audio.clip)
            assertEquals(audio.receipt.sha256, changedFavorite.audio.receipt.sha256)
            assertFalse(voice.isSourceCurrent(source))
            val edited =
                store.listMessages(source.conversationId).single { it.id == source.messageId }
            assertTrue(repository.deleteMessage(edited.id, edited.memoryRevision))
            assertEquals(
                CompanionVoiceSourceState.UNAVAILABLE,
                voice.observeFavorites("alice").first().single().sourceState
            )
            voice.releaseOwner("owner")
            voice.clearCache()
            assertTrue(File(audio.receipt.path).exists())
            assertEquals(audio.clip, voice.favoriteAudio(favorite.id, "favorite-owner")!!.clip)
            assertEquals(1, voice.usage().favoriteCount)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun lruAndManualClear_skipFavoritesAndCurrentOwnerPins() = runBlocking<Unit> {
        val clip = committedClip()
        val voice = RoomCompanionVoiceRepository(
            database,
            PrivateCompanionVoiceFilePublisher(temporary.newFolder("lru")),
            { clock++ })
        val first = publish(voice, key("first"), "first-owner", clip)
        val second = publish(voice, key("second"), "second-owner", clip)
        val saved = publish(voice, key("favorite"), "favorite-owner", clip)
        voice.favorite(saved)
        listOf("first-owner", "second-owner", "favorite-owner").forEach { voice.releaseOwner(it) }
        assertNotNull(voice.lookup(first.receipt.key, "active-owner"))
        voice.trimCache(first.receipt.sizeBytes)
        assertTrue(File(first.receipt.path).exists())
        assertFalse(File(second.receipt.path).exists())
        assertTrue(File(saved.receipt.path).exists())
        val pinned = voice.clearCache()
        assertEquals(1, pinned.cacheCount)
        assertEquals(1, pinned.favoriteCount)
        voice.releaseOwner("active-owner")
        val cleared = voice.clearCache()
        assertEquals(0, cleared.cacheCount)
        assertEquals(1, cleared.favoriteCount)
        assertFalse(File(first.receipt.path).exists())
        assertTrue(File(saved.receipt.path).exists())
        assertTrue(voice.isSourceCurrent(clip.segment.unit.source))
    }

    @Test
    fun parallelClaimsAndReleasedUnknown_requireExplicitRegeneration() = runBlocking<Unit> {
        val voice = RoomCompanionVoiceRepository(
            database,
            PrivateCompanionVoiceFilePublisher(temporary.newFolder("claim")),
            { clock++ })
        val key = key("claim")
        val results = coroutineScope {
            listOf(
                async { voice.claim(key, "one") },
                async { voice.claim(key, "two") }).awaitAll()
        }
        val claim = results.filterIsInstance<CompanionAudioClaimResult.Granted>().single().claim
        assertEquals(1, results.count { it == CompanionAudioClaimResult.Busy })
        voice.releaseOwner(claim.ownerToken)
        assertEquals(
            CompanionAudioClaimResult.NeedsExplicitRegeneration,
            voice.claim(key, "new-owner")
        )
        assertNull(voice.lookup(key, "new-owner"))
        val replacement = voice.claim(
            key,
            "new-owner",
            explicitRegeneration = true
        ) as CompanionAudioClaimResult.Granted
        assertNotEquals(claim.audioId, replacement.claim.audioId)
        assertEquals(1, database.companionVoiceDao().resources().size)
        assertEquals(0, voice.usage().cacheCount)
    }

    @Test
    fun cancellationAfterRoomAttachment_removesSuccessCacheAndPreservesCanonical() =
        runBlocking<Unit> {
            val clip = committedClip()
            val publisher = PrivateCompanionVoiceFilePublisher(temporary.newFolder("cancel"))
            var current = true
            val voice =
                RoomCompanionVoiceRepository(database, publisher, { clock++ }) { current = false }
            val claim =
                (voice.claim(key("cancel"), "owner") as CompanionAudioClaimResult.Granted).claim
            val result = runCatching { voice.publish(claim, encodedWave(), clip) { current } }
            assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertEquals("CANCELLED", database.companionVoiceDao().resource(claim.audioId)!!.state)
            assertEquals(0, voice.usage().cacheCount)
            assertTrue(publisher.publishedReceipts().isEmpty())
            assertTrue(voice.isSourceCurrent(clip.segment.unit.source))
        }

    @Test
    fun corruptSidecar_doesNotDeleteFavoriteAndUnfavoriteDoesNotDeleteOriginal() =
        runBlocking<Unit> {
            val clip = committedClip()
            val directory = temporary.newFolder("orphan")
            val voice = RoomCompanionVoiceRepository(
                database,
                PrivateCompanionVoiceFilePublisher(directory),
                { clock++ })
            val audio = publish(voice, key("orphan"), "owner", clip)
            val favorite = voice.favorite(audio)
            voice.releaseOwner("owner")
            val sidecar =
                File(directory, "${audio.receipt.audioId}.json").apply { writeText("{broken") }
            val orphan = File(directory, "orphan.wav").apply { writeBytes(syntheticVoiceWave()) }
            assertTrue(sidecar.setLastModified(1))
            assertTrue(orphan.setLastModified(1))
            assertTrue(File(audio.receipt.path).setLastModified(1))
            voice.clearCache()
            assertTrue(File(audio.receipt.path).exists())
            assertFalse(orphan.exists())
            assertNotNull(voice.favoriteAudio(favorite.id, "play-owner"))
            voice.releaseOwner("play-owner")
            voice.unfavorite(favorite.id)
            assertEquals(0, voice.usage().favoriteCount)
            assertTrue(File(audio.receipt.path).exists())
            assertTrue(voice.isSourceCurrent(clip.segment.unit.source))
            voice.clearCache()
            assertFalse(File(audio.receipt.path).exists())
            assertTrue(voice.isSourceCurrent(clip.segment.unit.source))
        }

    @Test
    fun cancelDelete_reopenRejectsAudioAndKeepsFavorite() =
        runBlocking<Unit> {
            useFileDatabase()
            val clip = committedClip()
            val source = clip.segment.unit.source
            val canonicalBefore = database.conversationDao().getMessage(source.messageId)
            val directory = temporary.newFolder("cancel-delete")
            val files = PrivateCompanionVoiceFilePublisher(directory)
            val publisher = DeleteFailurePublisher(files)
            val originalVoice = RoomCompanionVoiceRepository(database, publisher, { clock++ })
            val saved = publish(originalVoice, key("kept-favorite"), "kept-owner", clip)
            val favorite = originalVoice.favorite(saved)
            originalVoice.releaseOwner("kept-owner")

            for (stage in listOf("PENDING", "READY")) {
                val cancelledKey = key("cancel-delete-$stage")
                val owner = "cancel-delete-$stage"
                var current = true
                publisher.afterPublication = { audio ->
                    publisher.failedAudioIds += audio.receipt.audioId
                    if (stage == "PENDING") current = false
                }
                val voice =
                    RoomCompanionVoiceRepository(database, publisher, { clock++ }) { audio ->
                        if (audio.receipt.key == cancelledKey && stage == "READY") current = false
                    }
                val claim =
                    (voice.claim(cancelledKey, owner) as CompanionAudioClaimResult.Granted).claim
                val result = runCatching { voice.publish(claim, encodedWave(), clip) { current } }
                val cancellation = result.exceptionOrNull()
                assertTrue(cancellation is kotlinx.coroutines.CancellationException)
                assertTrue(cancellation!!.suppressed.any { it is IOException })
                val cancelled = requireNotNull(publisher.lastPublished)
                assertEquals(claim.audioId, cancelled.receipt.audioId)
                assertTrue(File(cancelled.receipt.path).exists())
                assertTrue(files.validate(cancelled.receipt))
                assertEquals(
                    "CANCELLED",
                    database.companionVoiceDao().resource(claim.audioId)!!.state
                )
                voice.releaseOwner(owner)

                reopen()
                val reopened = RoomCompanionVoiceRepository(database, publisher, { clock++ })
                assertNull(reopened.lookup(cancelledKey, "lookup-$stage"))
                assertTrue(reopened.recoverPending().none { it.receipt.audioId == claim.audioId })
                assertTrue(runCatching { reopened.retryAttachment(cancelled) }.isFailure)
                assertTrue(runCatching { reopened.favorite(cancelled) }.isFailure)
                val publications = publisher.publications
                assertTrue(runCatching {
                    reopened.publish(
                        claim,
                        encodedWave(),
                        clip
                    ) { true }
                }.isFailure)
                assertEquals(publications, publisher.publications)
                assertEquals(
                    CompanionAudioClaimResult.NeedsExplicitRegeneration,
                    reopened.claim(cancelledKey, "new-$stage")
                )
                assertTrue(runCatching { reopened.clearCache() }.exceptionOrNull() is IOException)
                assertEquals(
                    "CANCELLED",
                    database.companionVoiceDao().resource(claim.audioId)!!.state
                )
                assertNull(reopened.lookup(cancelledKey, "after-clear-$stage"))
                assertEquals(0, reopened.usage().cacheCount)
                assertEquals(1, reopened.usage().favoriteCount)
                assertEquals(
                    saved.clip,
                    reopened.favoriteAudio(favorite.id, "favorite-$stage")!!.clip
                )
                reopened.releaseOwner("favorite-$stage")
                assertTrue(File(saved.receipt.path).exists())
                assertEquals(
                    canonicalBefore,
                    database.conversationDao().getMessage(source.messageId)
                )
            }
            assertEquals(3, publisher.publications)
            assertTrue(publisher.deleteFailures >= 2)
        }

    private class DeleteFailurePublisher(private val delegate: CompanionVoiceFilePublisher) :
        CompanionVoiceFilePublisher by delegate {
        val failedAudioIds = mutableSetOf<String>()
        var afterPublication: suspend (CompanionVoiceAudio) -> Unit = {}
        var lastPublished: CompanionVoiceAudio? = null
        var publications = 0
        var deleteFailures = 0

        override suspend fun publish(
            claim: CompanionAudioClaim,
            b64Audio: String,
            clip: CompanionVoiceClip,
            isCurrent: suspend () -> Boolean,
        ): CompanionVoiceAudio {
            publications++
            return delegate.publish(claim, b64Audio, clip, isCurrent).also { audio ->
                lastPublished = audio
                afterPublication(audio)
            }
        }

        override suspend fun delete(audioId: String) {
            if (audioId in failedAudioIds) {
                deleteFailures++
                throw IOException("合成文件删除失败")
            }
            delegate.delete(audioId)
        }
    }

    private suspend fun committedClip(): CompanionVoiceClip {
        val prepared = prepared()
        received(prepared, "<narration>所选旁白🙂。</narration><dialogue>旧台词。</dialogue>")
        val committed =
            repository.commitAttempt(prepared.attempt.id) as CompanionCommitResult.Committed
        val message = store.listMessages(committed.replyMessage.conversationId)
            .single { it.id == committed.replyMessage.id }
        val unit =
            CompanionSpeechSupport.units(requireNotNull(CompanionReplyProjector.project(message)))
                .first()
        return syntheticVoiceClip(unit = unit)
    }

    private suspend fun publish(
        voice: CompanionVoiceRepository,
        key: SpeechRequestKey,
        owner: String,
        clip: CompanionVoiceClip
    ): CompanionVoiceAudio {
        val claim = (voice.claim(key, owner) as CompanionAudioClaimResult.Granted).claim
        return voice.publish(claim, encodedWave(), clip) { true }
    }

    private fun encodedWave() = syntheticVoiceWave().toByteString().base64()
    private fun key(text: String) = SpeechRequestKey(companionRawHash(text))

    private class CountingPublisher(private val delegate: CompanionVoiceFilePublisher) :
        CompanionVoiceFilePublisher by delegate {
        var publications = 0
        override suspend fun publish(
            claim: CompanionAudioClaim,
            b64Audio: String,
            clip: CompanionVoiceClip,
            isCurrent: suspend () -> Boolean
        ): CompanionVoiceAudio {
            publications++
            return delegate.publish(claim, b64Audio, clip, isCurrent)
        }
    }
}
