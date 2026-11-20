package com.example.myapplication.system.audio

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.model.CompanionVoicePlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 隔离 AVD 上的合法合成 WAV smoke；不代表真实 MiMo 或物理设备音质。 */
@RunWith(AndroidJUnit4::class)
class CompanionVoicePlayerInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun syntheticWav_preparesStartsAndStopsThroughNativeAdapter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "d5-player-${UUID.randomUUID()}.wav")
        file.writeBytes(tone())
        val started = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        var player: CompanionVoicePlayer? = null
        try {
            instrumentation.runOnMainSync {
                val created = AndroidCompanionVoicePlayerFactory(context).create()
                player = created
                created.setCallbacks(
                    onPrepared = {
                        try {
                            created.start(); started.countDown()
                        } catch (error: Throwable) {
                            failure.set(error); started.countDown()
                        }
                    },
                    onCompleted = {},
                    onError = {
                        failure.set(
                            it ?: IllegalStateException("合成WAV播放失败")
                        ); started.countDown()
                    },
                )
                created.prepare(file.absolutePath)
            }
            assertTrue("合法WAV必须完成准备并调用start", started.await(10, TimeUnit.SECONDS))
            assertNull("原生adapter不应报告播放失败", failure.get())
            instrumentation.runOnMainSync { player?.let { it.stop(); it.release(); it.release() } }
        } finally {
            player?.let { value -> instrumentation.runOnMainSync { value.stop(); value.release() } }
            assertTrue(file.delete())
        }
    }

    private fun tone(): ByteArray {
        val sampleRate = 8000
        val frames = 2000
        val dataSize = frames * 2
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(dataSize)
            repeat(frames) {
                putShort(
                    (sin(2 * PI * 440 * it / sampleRate) * 2000).toInt().toShort()
                )
            }
        }.array()
    }
}
