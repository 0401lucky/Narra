package com.example.myapplication.data.repository.roleplay

import com.example.myapplication.model.*
import kotlinx.coroutines.flow.Flow

/** 仅管理音频资源，不拥有翻译/TTS 网络回调；生成始终由会话声音 owner 触发。 */
interface CompanionVoiceRepository {
    /** 校验实际文件、补挂接并 pin；清理不能删除当前 owner 正在使用的音频。 */
    suspend fun lookup(key: SpeechRequestKey, ownerToken: String): CompanionAudioReceipt?
    suspend fun claim(
        key: SpeechRequestKey,
        ownerToken: String,
        explicitRegeneration: Boolean = false,
    ): CompanionAudioClaimResult

    /** 私有文件发布后再挂接 Room；每个异步恢复点核验 isCurrent，取消不得发布成功缓存。 */
    suspend fun publish(
        claim: CompanionAudioClaim,
        b64Audio: String,
        clip: CompanionVoiceClip,
        isCurrent: suspend () -> Boolean,
    ): CompanionVoiceAudio

    /** 消费已持久回执，绝不重新生成；挂接失败时原回执仍可在重开后恢复。 */
    suspend fun retryAttachment(audio: CompanionVoiceAudio): CompanionVoiceAudio
    suspend fun recoverPending(): List<CompanionVoiceAudio>
    suspend fun releaseOwner(ownerToken: String)
    suspend fun isSourceCurrent(source: CompanionReplySource): Boolean
    fun observeSourceChanges(): Flow<Unit>
    fun observeFavorites(characterId: String): Flow<List<CompanionVoiceFavorite>>
    fun observeUsage(): Flow<CompanionVoiceCacheUsage>
    suspend fun favorite(audio: CompanionVoiceAudio): CompanionVoiceFavorite
    suspend fun unfavorite(favoriteId: String)
    suspend fun favoriteAudio(favoriteId: String, ownerToken: String): CompanionVoiceAudio?
    suspend fun usage(): CompanionVoiceCacheUsage
    suspend fun trimCache(maxBytes: Long): CompanionVoiceCacheUsage
    suspend fun clearCache(): CompanionVoiceCacheUsage
}

/** 文件与 Room 分阶段；sidecar 保留合法发布回执，不能把路径当作已挂接成功。 */
interface CompanionVoiceFilePublisher {
    suspend fun publish(
        claim: CompanionAudioClaim,
        b64Audio: String,
        clip: CompanionVoiceClip,
        isCurrent: suspend () -> Boolean,
    ): CompanionVoiceAudio

    suspend fun validate(receipt: CompanionAudioReceipt): Boolean
    suspend fun publishedReceipts(): List<CompanionVoiceAudio>
    suspend fun markAttached(audio: CompanionVoiceAudio)
    suspend fun delete(audioId: String)

    /** 引用清单由仓储完整提供；仅清理超龄的本层无引用文件，损坏 sidecar 不代表无收藏。 */
    suspend fun cleanupOrphans(retainedAudioIds: Set<String>)
}
