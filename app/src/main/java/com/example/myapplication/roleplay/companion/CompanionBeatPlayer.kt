package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.CompanionBeat
import com.example.myapplication.model.CompanionReplySource
import com.example.myapplication.model.ParsedCompanionReply
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class CompanionBeatPlayerPhase {
    IDLE,
    TYPING,
    COMPLETE,
    END,
}

data class CompanionBeatPlayerState(
    val phase: CompanionBeatPlayerPhase = CompanionBeatPlayerPhase.IDLE,
    val source: CompanionReplySource? = null,
    val beat: CompanionBeat? = null,
    val beatIndex: Int = -1,
    val beatCount: Int = 0,
    val visibleText: String = "",
    val revealedGraphemes: Int = 0,
    val graphemeCount: Int = 0,
    val generation: Long = 0,
)

/**
 * 只播放调用方提供的完整本地投影；不负责生成、存储或声音。
 * setSource 自动开始首拍，stop 可重新进入，close 和父 scope 取消永久释放。
 */
class CompanionBeatPlayer(
    private val scope: CoroutineScope,
    private val characterDelayMillis: Long = 35L,
    private val delayCharacter: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    init {
        require(characterDelayMillis > 0) { "打字间隔必须大于零" }
    }

    private val lock = Any()
    private val mutableState = MutableStateFlow(CompanionBeatPlayerState())
    val state: StateFlow<CompanionBeatPlayerState> = mutableState.asStateFlow()

    private var currentReply: PlaybackReply? = null
    private var typingJob: Job? = null
    private var generation = 0L
    private var closed = false

    // 这个生命周期 Job 不挂载 timer 子任务，父 scope 取消时可立即清除显示状态。
    private val lifetimeJob = Job(scope.coroutineContext[Job])

    init {
        lifetimeJob.invokeOnCompletion {
            synchronized(lock) { releaseLocked() }
        }
    }

    fun setSource(reply: ParsedCompanionReply?) {
        synchronized(lock) {
            if (!isOpenLocked()) return
            if (reply == null) {
                if (currentReply != null) clearSourceLocked()
                return
            }
            if (currentReply?.matches(reply) == true) return

            val frozenReply = PlaybackReply(reply.source, reply.raw, reply.beats.toList())
            currentReply = frozenReply
            if (frozenReply.beats.isEmpty()) {
                invalidateTimerLocked()
                mutableState.value = CompanionBeatPlayerState(
                    source = reply.source,
                    generation = generation,
                )
            } else {
                startBeatLocked(0)
            }
        }
    }

    /** 打字中只补全本拍；完成后下一点才推进，END 的下一点从首拍本地重播。 */
    fun advanceOrReveal() {
        synchronized(lock) {
            if (!isOpenLocked()) return
            val current = mutableState.value
            when (current.phase) {
                CompanionBeatPlayerPhase.IDLE -> Unit
                CompanionBeatPlayerPhase.TYPING -> revealLocked()
                CompanionBeatPlayerPhase.COMPLETE -> {
                    val nextIndex = current.beatIndex + 1
                    if (nextIndex < current.beatCount) {
                        startBeatLocked(nextIndex)
                    } else {
                        invalidateTimerLocked()
                        mutableState.value = current.copy(
                            phase = CompanionBeatPlayerPhase.END,
                            generation = generation,
                        )
                    }
                }

                CompanionBeatPlayerPhase.END -> startBeatLocked(0)
            }
        }
    }

    fun replay() {
        synchronized(lock) {
            if (isOpenLocked() && currentReply?.beats?.isNotEmpty() == true) {
                startBeatLocked(0)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!closed) clearSourceLocked()
        }
    }

    override fun close() {
        synchronized(lock) {
            releaseLocked()
            lifetimeJob.cancel()
        }
    }

    private fun startBeatLocked(index: Int) {
        val reply = currentReply ?: return
        val beat = reply.beats[index]
        invalidateTimerLocked()
        val ends = companionGraphemeEnds(beat.content)
        val runGeneration = generation
        mutableState.value = CompanionBeatPlayerState(
            phase = if (ends.isEmpty()) {
                CompanionBeatPlayerPhase.COMPLETE
            } else {
                CompanionBeatPlayerPhase.TYPING
            },
            source = reply.source,
            beat = beat,
            beatIndex = index,
            beatCount = reply.beats.size,
            graphemeCount = ends.size,
            generation = generation,
        )
        if (ends.isEmpty() || !isCurrentLocked(runGeneration, reply.source, beat.id)) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for ((graphemeIndex, end) in ends.withIndex()) {
                    delayCharacter(characterDelayMillis)
                    coroutineContext.ensureActive()
                    val published = synchronized(lock) {
                        if (!isCurrentLocked(runGeneration, reply.source, beat.id)) {
                            false
                        } else {
                            val revealed = graphemeIndex + 1
                            mutableState.value = mutableState.value.copy(
                                phase = if (revealed == ends.size) {
                                    CompanionBeatPlayerPhase.COMPLETE
                                } else {
                                    CompanionBeatPlayerPhase.TYPING
                                },
                                visibleText = beat.content.substring(0, end),
                                revealedGraphemes = revealed,
                            )
                            true
                        }
                    }
                    if (!published) return@launch
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 本地动画时钟失败只降级为完整正文，不推进、不影响宿主 scope。
                synchronized(lock) {
                    if (isCurrentLocked(runGeneration, reply.source, beat.id)) revealLocked()
                }
            } finally {
                synchronized(lock) {
                    if (generation == runGeneration) {
                        typingJob = null
                        if (mutableState.value.phase == CompanionBeatPlayerPhase.TYPING) {
                            clearSourceLocked()
                        }
                    }
                }
            }
        }
        // 先安装唯一所有者再 start，兼容立即执行的 dispatcher 与立即完成的时钟。
        if (isCurrentLocked(runGeneration, reply.source, beat.id)) {
            typingJob = job
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun revealLocked() {
        val current = mutableState.value
        val beat = current.beat ?: return
        invalidateTimerLocked()
        mutableState.value = current.copy(
            phase = CompanionBeatPlayerPhase.COMPLETE,
            visibleText = beat.content,
            revealedGraphemes = current.graphemeCount,
            generation = generation,
        )
    }

    private fun isOpenLocked(): Boolean = !closed && lifetimeJob.isActive

    private fun isCurrentLocked(
        runGeneration: Long,
        source: CompanionReplySource,
        beatId: String,
    ): Boolean = isOpenLocked() && generation == runGeneration &&
            mutableState.value.source == source && mutableState.value.beat?.id == beatId &&
            mutableState.value.phase == CompanionBeatPlayerPhase.TYPING

    private fun invalidateTimerLocked() {
        generation++
        val previousJob = typingJob
        typingJob = null
        previousJob?.cancel()
    }

    private fun clearSourceLocked() {
        invalidateTimerLocked()
        currentReply = null
        mutableState.value = CompanionBeatPlayerState(generation = generation)
    }

    private fun releaseLocked() {
        if (closed) return
        closed = true
        clearSourceLocked()
    }

    private data class PlaybackReply(
        val source: CompanionReplySource,
        val raw: String,
        val beats: List<CompanionBeat>,
    ) {
        fun matches(reply: ParsedCompanionReply): Boolean = source == reply.source &&
                raw == reply.raw && beats == reply.beats
    }
}

/** 保留平台文字边界，并补齐旧版 BreakIterator 的 emoji 与 Indic 连写规则。 */
internal fun companionGraphemeEnds(text: String): IntArray {
    if (text.isEmpty()) return IntArray(0)
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val ends = ArrayList<Int>()
    var previous = text.codePointAt(0)
    var offset = Character.charCount(previous)
    var regionalCount = if (isRegionalIndicator(previous)) 1 else 0
    var pictographWithExtends = isExtendedPictographic(previous)
    var previousJoinerFollowsPictograph = false
    var indicConsonantStarted = isIndicConjunctConsonant(previous)
    var indicLinkerSeen = false

    while (offset < text.length) {
        val current = text.codePointAt(offset)
        val indicConsonant = isIndicConjunctConsonant(current)
        val indicLinker = isIndicConjunctLinker(current)
        val indicExtend = isIndicConjunctExtend(current)
        val breaks = when {
            previous == '\r'.code && current == '\n'.code -> false
            isGraphemeControl(previous) || isGraphemeControl(current) -> true
            isRegionalIndicator(previous) && isRegionalIndicator(current) -> regionalCount % 2 == 0
            isGraphemeExtend(current) || current == 0x200D || indicLinker || indicExtend -> false
            Character.getType(current) == Character.COMBINING_SPACING_MARK.toInt() -> false
            isGraphemePrepend(previous) -> false
            isGraphemePrepend(current) -> true
            indicConsonant -> !indicConsonantStarted || !indicLinkerSeen
            previous == 0x200D && previousJoinerFollowsPictograph && isExtendedPictographic(current) -> false
            else -> iterator.isBoundary(offset)
        }
        if (breaks) ends += offset

        // GB9c: Consonant (Extend|Linker)* Linker (Extend|Linker)* × Consonant。
        // 不能把任意组合符视为 InCB=Extend，例如 ZWNJ、CGJ、元音符和 variation selector。
        when {
            indicConsonant -> {
                indicConsonantStarted = true
                indicLinkerSeen = false
            }

            !breaks && indicConsonantStarted && indicLinker -> indicLinkerSeen = true
            !breaks && indicConsonantStarted && indicExtend -> Unit
            else -> {
                indicConsonantStarted = false
                indicLinkerSeen = false
            }
        }
        previousJoinerFollowsPictograph = current == 0x200D && pictographWithExtends
        pictographWithExtends = when {
            isExtendedPictographic(current) -> true
            isGraphemeExtend(current) && !breaks -> pictographWithExtends
            else -> false
        }
        regionalCount = if (isRegionalIndicator(current)) {
            if (isRegionalIndicator(previous)) regionalCount + 1 else 1
        } else {
            0
        }
        previous = current
        offset += Character.charCount(current)
    }
    ends += text.length
    return ends.toIntArray()
}

// Unicode 15.1 DerivedCoreProperties.txt 的 InCB 属性；固定表避免依赖宿主 JDK/Android 的版本。
// https://www.unicode.org/Public/15.1.0/ucd/DerivedCoreProperties.txt
private fun isIndicConjunctLinker(codePoint: Int): Boolean = when (codePoint) {
    0x094D, 0x09CD, 0x0ACD, 0x0B4D, 0x0C4D, 0x0D4D -> true
    else -> false
}

private fun isIndicConjunctConsonant(codePoint: Int): Boolean = when (codePoint) {
    in 0x0915..0x0939, in 0x0958..0x095F, in 0x0978..0x097F,
    in 0x0995..0x09A8, in 0x09AA..0x09B0, 0x09B2, in 0x09B6..0x09B9,
    in 0x09DC..0x09DD, 0x09DF, in 0x09F0..0x09F1,
    in 0x0A95..0x0AA8, in 0x0AAA..0x0AB0, in 0x0AB2..0x0AB3, in 0x0AB5..0x0AB9, 0x0AF9,
    in 0x0B15..0x0B28, in 0x0B2A..0x0B30, in 0x0B32..0x0B33, in 0x0B35..0x0B39,
    in 0x0B5C..0x0B5D, 0x0B5F, 0x0B71,
    in 0x0C15..0x0C28, in 0x0C2A..0x0C39, in 0x0C58..0x0C5A, in 0x0D15..0x0D3A -> true

    else -> false
}

private fun isIndicConjunctExtend(codePoint: Int): Boolean = when (codePoint) {
    in 0x0300..0x034E, in 0x0350..0x036F, in 0x0483..0x0487, in 0x0591..0x05BD, 0x05BF,
    in 0x05C1..0x05C2, in 0x05C4..0x05C5, 0x05C7, in 0x0610..0x061A, in 0x064B..0x065F, 0x0670,
    in 0x06D6..0x06DC, in 0x06DF..0x06E4, in 0x06E7..0x06E8, in 0x06EA..0x06ED, 0x0711,
    in 0x0730..0x074A, in 0x07EB..0x07F3, 0x07FD, in 0x0816..0x0819, in 0x081B..0x0823,
    in 0x0825..0x0827, in 0x0829..0x082D, in 0x0859..0x085B, in 0x0898..0x089F, in 0x08CA..0x08E1,
    in 0x08E3..0x08FF, 0x093C, in 0x0951..0x0954, 0x09BC, 0x09FE, 0x0A3C, 0x0ABC, 0x0B3C, 0x0C3C,
    in 0x0C55..0x0C56, 0x0CBC, in 0x0D3B..0x0D3C, in 0x0E38..0x0E3A, in 0x0E48..0x0E4B,
    in 0x0EB8..0x0EBA, in 0x0EC8..0x0ECB, in 0x0F18..0x0F19, 0x0F35, 0x0F37, 0x0F39, in 0x0F71..0x0F72,
    0x0F74, in 0x0F7A..0x0F7D, 0x0F80, in 0x0F82..0x0F84, in 0x0F86..0x0F87, 0x0FC6, 0x1037,
    in 0x1039..0x103A, 0x108D, in 0x135D..0x135F, 0x1714, 0x17D2, 0x17DD, 0x18A9, in 0x1939..0x193B,
    in 0x1A17..0x1A18, 0x1A60, in 0x1A75..0x1A7C, 0x1A7F, in 0x1AB0..0x1ABD, in 0x1ABF..0x1ACE, 0x1B34,
    in 0x1B6B..0x1B73, 0x1BAB, 0x1BE6, 0x1C37, in 0x1CD0..0x1CD2, in 0x1CD4..0x1CE0, in 0x1CE2..0x1CE8,
    0x1CED, 0x1CF4, in 0x1CF8..0x1CF9, in 0x1DC0..0x1DFF, 0x200D, in 0x20D0..0x20DC, 0x20E1,
    in 0x20E5..0x20F0, in 0x2CEF..0x2CF1, 0x2D7F, in 0x2DE0..0x2DFF, in 0x302A..0x302D,
    in 0x302E..0x302F, in 0x3099..0x309A, 0xA66F, in 0xA674..0xA67D, in 0xA69E..0xA69F,
    in 0xA6F0..0xA6F1, 0xA82C, in 0xA8E0..0xA8F1, in 0xA92B..0xA92D, 0xA9B3, 0xAAB0, in 0xAAB2..0xAAB4,
    in 0xAAB7..0xAAB8, in 0xAABE..0xAABF, 0xAAC1, 0xAAF6, 0xABED, 0xFB1E, in 0xFE20..0xFE2F, 0x101FD,
    0x102E0, in 0x10376..0x1037A, 0x10A0D, 0x10A0F, in 0x10A38..0x10A3A, 0x10A3F, in 0x10AE5..0x10AE6,
    in 0x10D24..0x10D27, in 0x10EAB..0x10EAC, in 0x10EFD..0x10EFF, in 0x10F46..0x10F50,
    in 0x10F82..0x10F85, 0x11070, 0x1107F, 0x110BA, in 0x11100..0x11102, in 0x11133..0x11134, 0x11173,
    0x111CA, 0x11236, in 0x112E9..0x112EA, in 0x1133B..0x1133C, in 0x11366..0x1136C,
    in 0x11370..0x11374, 0x11446, 0x1145E, 0x114C3, 0x115C0, 0x116B7, 0x1172B, 0x1183A, 0x1193E,
    0x11943, 0x11A34, 0x11A47, 0x11A99, 0x11D42, in 0x11D44..0x11D45, 0x11D97, 0x11F42,
    in 0x16AF0..0x16AF4, in 0x16B30..0x16B36, 0x1BC9E, 0x1D165, in 0x1D167..0x1D169,
    in 0x1D16E..0x1D172, in 0x1D17B..0x1D182, in 0x1D185..0x1D18B, in 0x1D1AA..0x1D1AD,
    in 0x1D242..0x1D244, in 0x1E000..0x1E006, in 0x1E008..0x1E018, in 0x1E01B..0x1E021,
    in 0x1E023..0x1E024, in 0x1E026..0x1E02A, 0x1E08F, in 0x1E130..0x1E136, 0x1E2AE,
    in 0x1E2EC..0x1E2EF, in 0x1E4EC..0x1E4EF, in 0x1E8D0..0x1E8D6, in 0x1E944..0x1E94A -> true

    else -> false
}

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

private fun isGraphemeExtend(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
    Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
    else -> codePoint == 0x200C || codePoint in 0xFE00..0xFE0F ||
            codePoint in 0x1F3FB..0x1F3FF || codePoint in 0xE0020..0xE007F ||
            codePoint in 0xE0100..0xE01EF
}

private fun isGraphemeControl(codePoint: Int): Boolean {
    if (codePoint == 0x200D || isGraphemeExtend(codePoint) || isGraphemePrepend(codePoint)) return false
    return when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(), Character.SURROGATE.toInt() -> true

        else -> false
    }
}

private fun isGraphemePrepend(codePoint: Int): Boolean = when (codePoint) {
    in 0x0600..0x0605, 0x06DD, 0x070F, in 0x0890..0x0891, 0x08E2, 0x0D4E,
    0x110BD, 0x110CD, in 0x111C2..0x111C3, 0x1193F, 0x11941, 0x11A3A,
    in 0x11A84..0x11A89, 0x11D46, 0x11F02 -> true

    else -> false
}

private fun isExtendedPictographic(codePoint: Int): Boolean = when (codePoint) {
    0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139,
    in 0x2194..0x2199, in 0x21A9..0x21AA, in 0x231A..0x231B, 0x2328, 0x2388,
    0x23CF, in 0x23E9..0x23F3, in 0x23F8..0x23FA, 0x24C2,
    in 0x25AA..0x25AB, 0x25B6, 0x25C0, in 0x25FB..0x25FE,
    in 0x2600..0x2605, in 0x2607..0x2612, in 0x2614..0x2685,
    in 0x2690..0x2705, in 0x2708..0x2712, 0x2714, 0x2716, 0x271D, 0x2721,
    0x2728, in 0x2733..0x2734, 0x2744, 0x2747, 0x274C, 0x274E,
    in 0x2753..0x2755, 0x2757, in 0x2763..0x2767, in 0x2795..0x2797,
    0x27A1, 0x27B0, 0x27BF, in 0x2934..0x2935, in 0x2B05..0x2B07,
    in 0x2B1B..0x2B1C, 0x2B50, 0x2B55, 0x3030, 0x303D, 0x3297, 0x3299,
    in 0x1F000..0x1FAFF, in 0x1FC00..0x1FFFD -> !isRegionalIndicator(codePoint) &&
            codePoint !in 0x1F3FB..0x1F3FF

    else -> false
}
