package com.example.myapplication.data.repository.asr

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/** 单次录音上限；超时自动停止，避免忘记松手产生超大文件。 */
const val MAX_RECORDING_MILLIS = 60_000L

/** 短于此时长的录音直接丢弃：多半是误触。 */
const val MIN_RECORDING_MILLIS = 500L

private const val RECORDING_DIR = "asr_recordings"
private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_COUNT = 1
private const val ENCODING_BIT_RATE = 32_000

/**
 * 录音采集。写 cacheDir，识别结束后由调用方删除。
 *
 * 采样率与声道数按转写模型的通行输入规格（16kHz 单声道）设定，体积远小于 WAV。
 * 非线程安全：只允许单个 [VoiceInputController] 持有并串行调用。
 */
class AudioRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    /**
     * 开始录音。
     *
     * @return 录音将写入的文件；失败抛出异常，调用方负责转成中文提示。
     */
    fun start(): File {
        check(recorder == null) { "上一次录音尚未结束" }
        val directory = File(context.cacheDir, RECORDING_DIR).apply { mkdirs() }
        val target = File(directory, "asr_${UUID.randomUUID()}.m4a")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE_HZ)
                setAudioChannels(CHANNEL_COUNT)
                setAudioEncodingBitRate(ENCODING_BIT_RATE)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
        } catch (throwable: Throwable) {
            runCatching { created.release() }
            target.delete()
            throw throwable
        }
        recorder = created
        outputFile = target
        return target
    }

    /**
     * 停止录音并返回音频文件；没有在录音时返回 null。
     *
     * 即便 stop 抛异常也会释放 recorder，避免麦克风被一直占用。
     */
    fun stop(): File? {
        val active = recorder ?: return null
        val target = outputFile
        recorder = null
        outputFile = null
        val stopped = runCatching { active.stop() }.isSuccess
        runCatching { active.release() }
        if (!stopped) {
            target?.delete()
            return null
        }
        return target?.takeIf { it.exists() && it.length() > 0L }
    }

    /** 放弃本次录音并删除文件，用于上滑取消。 */
    fun cancel() {
        val active = recorder
        val target = outputFile
        recorder = null
        outputFile = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        target?.delete()
    }

    /** 当前振幅，归一化到 0f..1f，用于驱动波形动画。未在录音时返回 0f。 */
    fun amplitude(): Float {
        val active = recorder ?: return 0f
        val raw = runCatching { active.maxAmplitude }.getOrDefault(0)
        if (raw <= 0) return 0f
        return (raw / MAX_AMPLITUDE).coerceIn(0f, 1f)
    }

    private companion object {
        /** MediaRecorder.maxAmplitude 的理论上限。 */
        const val MAX_AMPLITUDE = 32_767f
    }
}
