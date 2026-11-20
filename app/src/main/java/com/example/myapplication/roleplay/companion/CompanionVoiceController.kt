package com.example.myapplication.roleplay.companion

import com.example.myapplication.data.repository.ai.SnapshotAiTranslationService
import com.example.myapplication.data.repository.ai.TranslationRequestSnapshot
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.data.repository.tts.MimoTtsRequest
import com.example.myapplication.data.repository.tts.MimoTtsResult
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 翻译、合成、文件回执、准备/播放和下一段共用一个会话代次。 */
class CompanionVoiceController(
    scope: CoroutineScope,
    private val repository: CompanionVoiceRepository,
    private val translationService: SnapshotAiTranslationService,
    private val playerFactory: CompanionVoicePlayerFactory,
    private val getTurn: suspend (String) -> CompanionTurn?,
    private val synthesize: suspend (String, MimoTtsRequest, String) -> MimoTtsResult,
    private val requestFactory: CompanionTtsRequestFactory = CompanionTtsRequestFactory(),
    private val translationCache: CompanionTranslationCache = CompanionTranslationCache(),
) : CompanionVoiceSession {
    private val lock = Any()
    private val parentJob = scope.coroutineContext[Job]
    private val lifetimeJob = Job(parentJob)
    private val sessionJob = SupervisorJob(parentJob)
    private val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
    private val mutableState = MutableStateFlow(CompanionVoiceState())
    override val state: StateFlow<CompanionVoiceState> = mutableState.asStateFlow()
    private var owner: CompanionVoiceOwner? = null
    private var contextSettings: AppSettings? = null
    private var contextUnits = emptyList<CompanionSpeechUnit>()
    private var generation = 0L
    private var closed = false
    private var currentRun: VoiceRun? = null
    private var work: Job? = null
    private var sourceObserver: Job? = null
    private var sourceObserverEpoch = 0L
    private var currentPlayer: PlayerSlot? = null
    private var lastRequest: VoiceRequest? = null
    private var lastFavoriteId: String? = null
    private var pendingSynthesis: PendingSynthesis? = null
    private var automaticInitialized = false
    private val automaticSeen = mutableSetOf<CompanionReplySource>()

    init {
        // 独立生命周期 Job 不等 NonCancellable 的旧请求结束，父取消立即撤销声音权威。
        lifetimeJob.invokeOnCompletion { close() }
        sessionJob.invokeOnCompletion { synchronized(lock) { closeLocked() } }
        synchronized(lock) { ensureSourceObserverLocked() }
    }

    /** 只恢复监听，不恢复旧声音请求；用户下一次显式动作才重建失效观察。 */
    private fun ensureSourceObserverLocked(): Boolean {
        if (closed || !sessionJob.isActive) return false
        if (sourceObserver?.isActive == true) return true
        val epoch = ++sourceObserverEpoch
        val observer = sessionScope.launch(start = CoroutineStart.LAZY) { observeSources(epoch) }
        sourceObserver = observer
        observer.start()
        return sourceObserver?.isActive == true
    }

    private suspend fun observeSources(epoch: Long) {
        try {
            repository.observeSourceChanges().collect {
                val run = synchronized(lock) {
                    if (closed || sourceObserverEpoch != epoch) null
                    else currentRun?.takeUnless { it.favoriteId != null }
                } ?: return@collect
                val valid = run.sources.all { repository.isSourceCurrent(it) }
                synchronized(lock) {
                    if (sourceObserverEpoch == epoch && !valid && isCurrentLocked(run)) invalidateLocked(
                        clearAudio = true,
                        error = "所选原文已修改或删除，请重新选择。",
                        stage = CompanionVoiceFailureStage.SOURCE,
                    )
                }
            }
            throw IllegalStateException("原文状态监听已结束，请再次点播恢复。")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (!closed && sourceObserverEpoch == epoch && currentRun?.favoriteId == null && lastFavoriteId == null) invalidateLocked(
                    clearAudio = true,
                    error = safeError(failure, CompanionVoiceFailureStage.SOURCE),
                    stage = CompanionVoiceFailureStage.SOURCE,
                )
            }
        } finally {
            synchronized(lock) {
                if (sourceObserverEpoch == epoch) sourceObserver = null
            }
        }
    }

    override fun updateContext(
        owner: CompanionVoiceOwner,
        settings: AppSettings,
        units: List<CompanionSpeechUnit>
    ) {
        synchronized(lock) {
            if (closed) return
            val ownerChanged = this.owner != owner
            val configChanged = contextSettings?.let {
                voiceConfig(it, owner.characterId) != voiceConfig(
                    settings,
                    owner.characterId
                )
            } == true
            val selection = currentRun?.takeUnless { it.favoriteId != null }?.request?.selection
                ?: mutableState.value.selection
            val sourceChanged =
                selection != null && !CompanionSpeechSupport.isCurrent(selection, units)
            if (ownerChanged || configChanged || sourceChanged) invalidateLocked(clearAudio = true)
            if (ownerChanged) {
                automaticInitialized = false; automaticSeen.clear()
            }
            this.owner = owner
            contextSettings = settings
            contextUnits = units.toList()
        }
    }

    override fun select(selection: CompanionSpeechSelection?) {
        synchronized(lock) {
            if (closed || selection == mutableState.value.selection) return
            invalidateLocked(clearAudio = true)
            mutableState.value = mutableState.value.copy(selection = selection)
        }
    }

    override fun playSelection(
        selection: CompanionSpeechSelection,
        settings: AppSettings,
        automatic: Boolean
    ) {
        start(VoiceRequest(selection, settings, automatic = automatic))
    }

    override fun offerAutomatic(units: List<CompanionSpeechUnit>, settings: AppSettings) {
        val candidates = synchronized(lock) {
            if (closed) return
            val sources = units.map { it.source }.toSet()
            if (!automaticInitialized) {
                automaticInitialized = true
                automaticSeen.addAll(sources)
                return
            }
            val added = sources - automaticSeen
            automaticSeen.addAll(sources)
            val characterId = owner?.characterId ?: return
            val preferences = settings.companionSettings(characterId).voice
            if (preferences.schemaVersion != 1 || !preferences.enabled || !preferences.autoPlay) return
            units.filter { it.source in added && it.contentType == RoleplayContentType.DIALOGUE }
        }
        if (candidates.isEmpty()) return
        val selection = CompanionSpeechSelection(
            candidates,
            candidates.map { CompanionSpeechRange(it.id, 0, it.text.length) })
        start(VoiceRequest(selection, settings, automatic = true))
    }

    override fun generateAndFavorite(selection: CompanionSpeechSelection, settings: AppSettings) {
        start(VoiceRequest(selection, settings, generateFavorite = true))
    }

    override fun favoriteCurrent() {
        val captured = synchronized(lock) {
            if (closed) return
            val audio = mutableState.value.currentAudio ?: return
            generation to audio
        }
        sessionScope.launch {
            try {
                val audio = repository.retryAttachment(captured.second)
                repository.favorite(audio)
                synchronized(lock) {
                    if (!closed && generation == captured.first) mutableState.value =
                        mutableState.value.copy(
                            currentAudio = audio,
                            errorMessage = null,
                            noticeMessage = "已收藏实际音频。",
                        )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                synchronized(lock) {
                    if (!closed && generation == captured.first) mutableState.value =
                        mutableState.value.copy(
                            errorMessage = safeError(failure, CompanionVoiceFailureStage.STORAGE),
                            noticeMessage = null,
                        )
                }
            }
        }
    }

    override fun playFavorite(favoriteId: String) {
        val settings = synchronized(lock) { contextSettings } ?: return
        start(
            VoiceRequest(CompanionSpeechSelection(emptyList(), emptyList()), settings),
            favoriteId
        )
    }

    override fun retry() {
        val captured = synchronized(lock) {
            if (closed || mutableState.value.needsExplicitRegeneration) return
            Triple(
                lastRequest?.copy(startIndex = mutableState.value.currentIndex),
                lastFavoriteId,
                mutableState.value.currentAudio?.takeIf {
                    it.receipt.attachmentState == CompanionAudioAttachmentState.PENDING
                }?.let { generation to it },
            )
        }
        val request = captured.first
        if (request != null) start(
            request,
            captured.second,
            reuse = true,
            allowSourceRecovery = true
        )
        else captured.third?.let { retryStoppedAudio(it.second, it.first) }
    }

    /** 停止队列后只补保存既有文件；不重建翻译、合成或播放器。 */
    private fun retryStoppedAudio(audio: CompanionVoiceAudio, expectedGeneration: Long) {
        val savingGeneration: Long
        val job: Job
        synchronized(lock) {
            if (!ownsGenerationLocked(expectedGeneration) || lastRequest != null || mutableState.value.currentAudio != audio) return
            invalidateLocked(clearAudio = false)
            savingGeneration = generation
            mutableState.value = mutableState.value.copy(phase = CompanionVoicePhase.PUBLISHING)
            job = sessionScope.launch(start = CoroutineStart.LAZY) {
                saveExistingAudio(
                    audio,
                    savingGeneration
                )
            }
            work = job
        }
        job.start()
    }

    private suspend fun saveExistingAudio(audio: CompanionVoiceAudio, savingGeneration: Long) {
        val savingJob = currentCoroutineContext()[Job]
        try {
            currentCoroutineContext().ensureActive()
            val attached = repository.retryAttachment(audio)
            currentCoroutineContext().ensureActive()
            check(attached.receipt.attachmentState == CompanionAudioAttachmentState.ATTACHED) {
                "已有音频仍待保存，可稍后重试。"
            }
            synchronized(lock) {
                if (ownsGenerationLocked(savingGeneration) && work === savingJob) mutableState.value =
                    mutableState.value.copy(
                        currentAudio = attached,
                        phase = CompanionVoicePhase.IDLE,
                        failureStage = null,
                        errorMessage = null,
                        noticeMessage = "已有音频已补保存，没有重新生成。",
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (ownsGenerationLocked(savingGeneration) && work === savingJob) mutableState.value =
                    mutableState.value.copy(
                        phase = CompanionVoicePhase.ERROR,
                        failureStage = CompanionVoiceFailureStage.STORAGE,
                        errorMessage = safeError(failure, CompanionVoiceFailureStage.STORAGE),
                        noticeMessage = null,
                    )
            }
        } finally {
            synchronized(lock) {
                if (generation == savingGeneration && work === savingJob) work = null
            }
        }
    }

    override fun readOriginal() {
        val request = synchronized(lock) {
            if (closed || mutableState.value.failureStage != CompanionVoiceFailureStage.TRANSLATION) return
            lastRequest?.copy(original = true, startIndex = mutableState.value.currentIndex)
                ?: return
        }
        start(request, allowSourceRecovery = true)
    }

    override fun regenerateExplicitly() {
        val request = synchronized(lock) {
            if (closed || !mutableState.value.needsExplicitRegeneration) return
            lastRequest?.copy(
                explicitRegeneration = true,
                startIndex = mutableState.value.currentIndex
            ) ?: return
        }
        start(request, allowSourceRecovery = true)
    }

    override fun stop() = synchronized(lock) { if (!closed) invalidateLocked(clearAudio = false) }
    override fun close() {
        synchronized(lock) { closeLocked() }
        sessionJob.cancel()
        lifetimeJob.cancel()
    }

    private fun start(
        request: VoiceRequest,
        favoriteId: String? = null,
        reuse: Boolean = false,
        allowSourceRecovery: Boolean = !request.automatic,
    ) {
        val run: VoiceRun
        val job: Job
        synchronized(lock) {
            if (closed || owner == null) return
            if (favoriteId == null && sourceObserver?.isActive != true) {
                if (!allowSourceRecovery || !ensureSourceObserverLocked()) return
            }
            val savedPending = if (reuse) pendingSynthesis else null
            val savedAudio = if (reuse) mutableState.value.currentAudio else null
            invalidateLocked(clearAudio = true)
            run = VoiceRun(
                generation,
                UUID.randomUUID().toString(),
                requireNotNull(owner),
                request,
                favoriteId,
                savedPending,
                savedAudio
            )
            currentRun = run
            lastRequest = request
            lastFavoriteId = favoriteId
            pendingSynthesis = savedPending
            mutableState.value = CompanionVoiceState(
                generation = generation,
                selection = request.selection.takeIf { it.units.isNotEmpty() },
                phase = CompanionVoicePhase.PREPARING,
                currentIndex = request.startIndex,
                currentAudio = savedAudio,
            )
            job = sessionScope.launch(start = CoroutineStart.LAZY) { execute(run) }
            work = job
        }
        job.start()
    }

    private suspend fun execute(run: VoiceRun) {
        try {
            if (run.favoriteId != null) {
                run.stage = CompanionVoiceFailureStage.STORAGE
                val audio = repository.favoriteAudio(run.favoriteId, run.token)
                    ?: throw IllegalStateException("收藏音频文件不可用，请保留原文并重新生成。")
                check(audio.clip.segment.unit.source.characterId == run.owner.characterId) { "收藏不属于当前角色" }
                requireCurrent(run, checkSource = false)
                update(run) {
                    it.copy(
                        total = 1,
                        currentAudio = audio,
                        originalText = audio.clip.segment.originalText,
                        spokenText = audio.clip.spokenText,
                        languageTag = audio.clip.languageTag
                    )
                }
                playAudio(run, audio)
            } else {
                executeSelection(run)
            }
            update(run) {
                it.copy(
                    phase = CompanionVoicePhase.COMPLETED,
                    errorMessage = null,
                    failureStage = null
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            update(run) {
                it.copy(
                    phase = CompanionVoicePhase.ERROR, failureStage = run.stage,
                    errorMessage = safeError(failure, run.stage),
                    needsExplicitRegeneration = failure is ExplicitRegenerationRequired,
                )
            }
        } finally {
            synchronized(lock) {
                if (currentPlayer?.run === run) releasePlayerLocked(requireNotNull(currentPlayer))
                if (isCurrentLocked(run)) {
                    work = null; currentRun = null
                }
            }
            try {
                withContext(NonCancellable) { repository.releaseOwner(run.token) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                synchronized(lock) {
                    if (!closed && generation == run.generation) mutableState.value =
                        mutableState.value.copy(
                            phase = CompanionVoicePhase.ERROR,
                            failureStage = CompanionVoiceFailureStage.STORAGE,
                            errorMessage = safeError(failure, CompanionVoiceFailureStage.STORAGE),
                        )
                }
            }
        }
    }

    private suspend fun executeSelection(run: VoiceRun) {
        run.stage = CompanionVoiceFailureStage.CONFIGURATION
        val prefs = run.request.settings.companionSettings(run.owner.characterId).voice.normalized()
        check(prefs.schemaVersion == 1 && prefs.enabled) { "陪伴语音已关闭，可在声音设置中开启。" }
        requireCurrent(run)
        var segments = CompanionVoiceSegments.from(run.request.selection)
        if (run.request.automatic) {
            val allowedTurns = segments.map { it.unit.source.turnId }.distinct()
                .associateWith { getTurn(it)?.kind }
            requireCurrent(run)
            segments = segments.filter {
                it.unit.contentType == RoleplayContentType.DIALOGUE && allowedTurns[it.unit.source.turnId] in listOf(
                    CompanionTurnKind.INPUT,
                    CompanionTurnKind.CONTINUE
                )
            }
            if (segments.isEmpty()) return
        }
        val translated =
            prefs.readMode == CompanionVoiceReadMode.TRANSLATED && !run.request.original
        var tts: FrozenCompanionTts? = null
        var translation: TranslationRequestSnapshot? = null
        update(run) { it.copy(total = segments.size) }
        var favoritesAdded = 0
        for (index in run.request.startIndex until segments.size) {
            val segment = segments[index]
            requireCurrent(run)
            update(run) {
                it.copy(
                    currentIndex = index,
                    currentAudio = null,
                    originalText = segment.originalText,
                    spokenText = "",
                    errorMessage = null,
                    noticeMessage = null
                )
            }
            val reusable = run.reuseAudio?.takeIf { it.clip.segment == segment }
            val pending = run.reusePending?.takeIf { it.clip.segment == segment }
            val audio = when {
                reusable != null -> {
                    run.stage = CompanionVoiceFailureStage.STORAGE
                    update(run) { it.copy(currentAudio = reusable) }
                    val receipt = repository.lookup(reusable.receipt.key, run.token)
                    requireCurrent(run)
                    val found = reusable.copy(receipt = receipt ?: reusable.receipt)
                    if (receipt != null && receipt.attachmentState == CompanionAudioAttachmentState.ATTACHED) found
                    else repository.retryAttachment(found)
                }

                pending != null -> publishRemembered(run, pending)
                else -> {
                    run.stage = CompanionVoiceFailureStage.CONFIGURATION
                    val frozenTts = tts ?: requestFactory.freeze(
                        run.request.settings.voiceSynthesisSettings,
                        run.owner.characterId
                    )
                        .also { tts = it }
                    requireCurrent(run)
                    if (translated && translation == null) {
                        run.stage = CompanionVoiceFailureStage.TRANSLATION
                        translation = translationService.freeze(run.request.settings)
                    }
                    requireCurrent(run)
                    obtainAudio(run, segment, prefs, frozenTts, translation)
                }
            }
            requireCurrent(run)
            update(run) {
                it.copy(
                    currentAudio = audio,
                    spokenText = audio.clip.spokenText,
                    languageTag = audio.clip.languageTag
                )
            }
            if (audio.receipt.attachmentState != CompanionAudioAttachmentState.ATTACHED) {
                run.stage = CompanionVoiceFailureStage.STORAGE
                throw IllegalStateException("音频文件已保存，引用尚未保存。重试将补保存已有音频，不会重新合成。")
            }
            if (run.request.generateFavorite) {
                run.stage = CompanionVoiceFailureStage.STORAGE
                repository.favorite(audio)
                requireCurrent(run)
                favoritesAdded++
                update(run) { it.copy(noticeMessage = "已收藏 $favoritesAdded 段实际音频。") }
            } else playAudio(run, audio)
            requireCurrent(run)
            run.stage = CompanionVoiceFailureStage.STORAGE
            // 本段已结束；先释放音频 pin，再回收，长队列不累积整队不可回收的文件。
            repository.releaseOwner(run.token)
            requireCurrent(run)
            repository.trimCache(prefs.cacheLimitBytes)
            requireCurrent(run)
        }
    }

    private suspend fun obtainAudio(
        run: VoiceRun, segment: CompanionVoiceSegment, prefs: CompanionVoicePreferences,
        tts: FrozenCompanionTts, translation: TranslationRequestSnapshot?,
    ): CompanionVoiceAudio {
        val sourceLanguage =
            segment.unit.languageTag?.trim()?.takeIf(String::isNotBlank) ?: prefs.sourceLanguage
        val languageTag = if (translation != null) prefs.targetLanguage else sourceLanguage
        val translationKey = translation?.let {
            CompanionSpeechRequestKeys.translation(
                it,
                segment.originalText,
                prefs.targetLanguage,
                sourceLanguage
            )
        }
        val spoken = if (translation != null && translationKey != null) {
            run.stage = CompanionVoiceFailureStage.TRANSLATION
            update(run) {
                it.copy(
                    phase = CompanionVoicePhase.TRANSLATING,
                    languageTag = languageTag
                )
            }
            translationCache.get(translationKey) ?: translationService.translateText(
                translation,
                segment.originalText,
                prefs.targetLanguage,
                sourceLanguage
            ).also {
                requireCurrent(run)
                check(it.isNotBlank()) { "翻译模型未返回有效内容" }
                synchronized(lock) {
                    if (isCurrentLocked(run)) translationCache.put(
                        translationKey,
                        it.trim()
                    )
                }
            }
        } else segment.originalText
        requireCurrent(run)
        val actualText = spoken.trim()
        check(actualText.isNotBlank()) { "所选范围没有可朗读文字" }
        val clip = CompanionVoiceClip(
            segment,
            actualText,
            sourceLanguage,
            languageTag,
            tts.identity,
            translationKey
        )
        val key = CompanionSpeechRequestKeys.speech(
            actualText,
            sourceLanguage,
            languageTag,
            tts.identity,
            translationKey
        )
        run.stage = CompanionVoiceFailureStage.STORAGE
        val existing = repository.lookup(key, run.token)
        requireCurrent(run)
        if (existing != null) return CompanionVoiceAudio(existing, clip)
        val claim = claim(run, key, run.request.explicitRegeneration)
        requireCurrent(run)
        if (claim is CompanionAudioClaimResult.Existing) return CompanionVoiceAudio(
            claim.receipt,
            clip
        )
        val granted = (claim as CompanionAudioClaimResult.Granted).claim
        run.stage = CompanionVoiceFailureStage.SYNTHESIS
        update(run) {
            it.copy(
                phase = CompanionVoicePhase.SYNTHESIZING,
                spokenText = actualText,
                languageTag = languageTag
            )
        }
        val result = synthesize(tts.apiKey, tts.request(actualText), tts.baseUrl)
        requireCurrent(run)
        val remembered = PendingSynthesis(key, clip, result.b64Audio)
        synchronized(lock) { if (isCurrentLocked(run)) pendingSynthesis = remembered }
        run.stage = CompanionVoiceFailureStage.STORAGE
        update(run) { it.copy(phase = CompanionVoicePhase.PUBLISHING) }
        val audio = repository.publish(granted, result.b64Audio, clip) { validForPublication(run) }
        requireCurrent(run)
        synchronized(lock) { if (isCurrentLocked(run)) pendingSynthesis = null }
        return audio
    }

    private suspend fun publishRemembered(
        run: VoiceRun,
        pending: PendingSynthesis
    ): CompanionVoiceAudio {
        run.stage = CompanionVoiceFailureStage.STORAGE
        val result = claim(run, pending.key, explicit = true)
        requireCurrent(run)
        if (result is CompanionAudioClaimResult.Existing) return CompanionVoiceAudio(
            result.receipt,
            pending.clip
        )
        update(run) { it.copy(phase = CompanionVoicePhase.PUBLISHING) }
        val audio = repository.publish(
            (result as CompanionAudioClaimResult.Granted).claim,
            pending.b64Audio,
            pending.clip
        ) { validForPublication(run) }
        requireCurrent(run)
        synchronized(lock) { if (isCurrentLocked(run)) pendingSynthesis = null }
        return audio
    }

    private suspend fun claim(
        run: VoiceRun,
        key: SpeechRequestKey,
        explicit: Boolean
    ): CompanionAudioClaimResult =
        when (val result = repository.claim(key, run.token, explicit)) {
            CompanionAudioClaimResult.Busy -> throw IllegalStateException("这段声音正在处理，请稍后重试。")
            CompanionAudioClaimResult.NeedsExplicitRegeneration -> throw ExplicitRegenerationRequired()
            else -> result
        }

    private suspend fun playAudio(run: VoiceRun, audio: CompanionVoiceAudio) = coroutineScope {
        run.stage = CompanionVoiceFailureStage.PLAYBACK
        requireCurrent(run, checkSource = run.favoriteId == null)
        update(run) { it.copy(phase = CompanionVoicePhase.PREPARING) }
        val slot = PlayerSlot(run, playerFactory.create())
        synchronized(lock) {
            if (!isCurrentLocked(run)) {
                releasePlayerLocked(slot); throw CancellationException()
            }
            currentPlayer = slot
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                fun finish(failure: Throwable?) {
                    val shouldResume = synchronized(lock) {
                        if (slot.finished) false else {
                            slot.finished = true
                            val current = isCurrentLocked(run) && currentPlayer === slot
                            releasePlayerLocked(slot)
                            current
                        }
                    }
                    if (shouldResume && continuation.isActive) {
                        if (failure == null) continuation.resume(Unit) else continuation.resumeWithException(
                            failure
                        )
                    }
                }
                continuation.invokeOnCancellation {
                    synchronized(lock) {
                        slot.finished = true; releasePlayerLocked(slot)
                    }
                }
                slot.player.setCallbacks(
                    onPrepared = {
                        launch {
                            try {
                                requireCurrent(run, checkSource = run.favoriteId == null)
                                synchronized(lock) {
                                    if (isCurrentLocked(run) && currentPlayer === slot && !slot.finished && continuation.isActive) {
                                        slot.player.start()
                                        if (isCurrentLocked(run) && currentPlayer === slot && !slot.finished && continuation.isActive) {
                                            mutableState.value =
                                                mutableState.value.copy(phase = CompanionVoicePhase.PLAYING)
                                        }
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                continuation.cancel(cancelled)
                            } catch (failure: Throwable) {
                                finish(failure)
                            }
                        }
                    },
                    onCompleted = { finish(null) },
                    onError = { finish(it ?: IllegalStateException("声音播放失败，请重试。")) },
                )
                try {
                    synchronized(lock) {
                        if (isCurrentLocked(run) && currentPlayer === slot && !slot.finished) slot.player.prepare(
                            audio.receipt.path
                        )
                        else continuation.cancel()
                    }
                } catch (failure: Throwable) {
                    finish(failure)
                }
            }
        } finally {
            synchronized(lock) { releasePlayerLocked(slot) }
        }
    }

    private suspend fun requireCurrent(run: VoiceRun, checkSource: Boolean = true) {
        currentCoroutineContext().ensureActive()
        if (!synchronized(lock) { isCurrentLocked(run) }) throw CancellationException("声音请求已撤销")
        if (checkSource) {
            if (!synchronized(lock) { sourceObserver?.isActive == true }) {
                run.stage = CompanionVoiceFailureStage.SOURCE
                throw IllegalStateException("原文状态监听不可用，请再次点播恢复。")
            }
            if (run.sources.any { it.characterId != run.owner.characterId } ||
                !run.sources.all { repository.isSourceCurrent(it) }) {
                run.stage = CompanionVoiceFailureStage.SOURCE
                throw IllegalStateException("所选原文已修改或删除，请重新选择。")
            }
            if (!synchronized(lock) {
                    isCurrentLocked(run) && CompanionSpeechSupport.isCurrent(
                        run.request.selection,
                        contextUnits
                    )
                })
                throw CancellationException("声音选区已撤销")
        }
        currentCoroutineContext().ensureActive()
        if (!synchronized(lock) { isCurrentLocked(run) }) throw CancellationException("声音请求已撤销")
    }

    private suspend fun validForPublication(run: VoiceRun): Boolean = try {
        requireCurrent(run)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalStateException) {
        false
    }

    private fun update(run: VoiceRun, transform: (CompanionVoiceState) -> CompanionVoiceState) {
        synchronized(lock) {
            if (isCurrentLocked(run)) mutableState.value = transform(mutableState.value)
        }
    }

    private fun isCurrentLocked(run: VoiceRun): Boolean =
        ownsGenerationLocked(run.generation) && currentRun === run && owner == run.owner

    private fun ownsGenerationLocked(expectedGeneration: Long): Boolean =
        !closed && parentJob?.isActive != false && sessionJob.isActive && lifetimeJob.isActive &&
                generation == expectedGeneration

    private fun invalidateLocked(
        clearAudio: Boolean,
        error: String? = null,
        stage: CompanionVoiceFailureStage? = null
    ) {
        generation++
        work?.cancel(); work = null
        currentRun = null
        currentPlayer?.let(::releasePlayerLocked)
        pendingSynthesis = null
        lastRequest = null
        lastFavoriteId = null
        mutableState.value = mutableState.value.copy(
            generation = generation,
            phase = if (error == null) CompanionVoicePhase.IDLE else CompanionVoicePhase.ERROR,
            selection = if (clearAudio) null else mutableState.value.selection,
            currentAudio = if (clearAudio) null else mutableState.value.currentAudio,
            originalText = if (clearAudio) "" else mutableState.value.originalText,
            spokenText = if (clearAudio) "" else mutableState.value.spokenText,
            languageTag = if (clearAudio) "" else mutableState.value.languageTag,
            errorMessage = error,
            noticeMessage = null,
            failureStage = stage,
            needsExplicitRegeneration = false,
        )
    }

    private fun releasePlayerLocked(slot: PlayerSlot) {
        if (!slot.released) {
            slot.released = true
            runCatching { slot.player.stop() }
            runCatching { slot.player.release() }
        }
        if (currentPlayer === slot) currentPlayer = null
    }

    private fun closeLocked() {
        if (closed) return
        invalidateLocked(clearAudio = true)
        closed = true
        sourceObserverEpoch++
        sourceObserver?.cancel()
        sourceObserver = null
        translationCache.clear()
    }

    private fun voiceConfig(settings: AppSettings, characterId: String): List<Any?> = listOf(
        settings.companionSettings(characterId).voice,
        settings.voiceSynthesisSettings.apiKey,
        settings.voiceSynthesisSettings.baseUrl,
        settings.voiceSynthesisSettings.profileForAssistant(characterId),
        settings.resolveFunctionProvider(ProviderFunction.TRANSLATION),
        settings.resolveFunctionModel(ProviderFunction.TRANSLATION),
    )

    private fun safeError(failure: Throwable, stage: CompanionVoiceFailureStage): String =
        SensitiveTextRedactor.throwableMessageForUi(
            failure, when (stage) {
                CompanionVoiceFailureStage.CONFIGURATION -> "声音配置不可用，请检查语音和角色音色设置。"
                CompanionVoiceFailureStage.SOURCE -> "所选原文已失效，请重新选择。"
                CompanionVoiceFailureStage.TRANSLATION -> "翻译失败，可重试或明确选择读原文。"
                CompanionVoiceFailureStage.SYNTHESIS -> "声音生成失败，原文仍然保留，可重试。"
                CompanionVoiceFailureStage.STORAGE -> "声音保存失败，已有音频可重试补保存。"
                CompanionVoiceFailureStage.PLAYBACK -> "声音播放失败，原文仍然保留，可重试。"
            }
        )

    private data class VoiceRequest(
        val selection: CompanionSpeechSelection,
        val settings: AppSettings,
        val automatic: Boolean = false,
        val generateFavorite: Boolean = false,
        val original: Boolean = false,
        val explicitRegeneration: Boolean = false,
        val startIndex: Int = 0,
    )

    private class VoiceRun(
        val generation: Long,
        val token: String,
        val owner: CompanionVoiceOwner,
        val request: VoiceRequest,
        val favoriteId: String?,
        val reusePending: PendingSynthesis?,
        val reuseAudio: CompanionVoiceAudio?,
        var stage: CompanionVoiceFailureStage = CompanionVoiceFailureStage.CONFIGURATION,
    ) {
        val sources = request.selection.units.map { it.source }.distinct()
    }

    private data class PendingSynthesis(
        val key: SpeechRequestKey,
        val clip: CompanionVoiceClip,
        val b64Audio: String
    )

    private class PlayerSlot(
        val run: VoiceRun,
        val player: CompanionVoicePlayer,
        var released: Boolean = false,
        var finished: Boolean = false
    )

    private class ExplicitRegenerationRequired : IllegalStateException(
        "上次生成没有留下可恢复的音频。可明确重新生成，可能再次计费；没有持久副本时无法保证进程结束后的恢复。",
    )
}
