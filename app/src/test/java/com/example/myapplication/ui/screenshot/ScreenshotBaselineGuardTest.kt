package com.example.myapplication.ui.screenshot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 基准计数守卫（纯 JVM，不需要 Robolectric）。
 *
 * 作用：用例清单、登记表与 snapshots 目录三者必须一致——
 * - 缺场景 / 删用例 / 零测试：分族数量与冻结值不符即失败；
 * - 缺基准：verify 与普通单测运行时，任何已登记基准缺失或损坏即失败；
 * - 多基准：出现未登记的 PNG（不在显式白名单内）即失败。
 *
 * Roborazzi record 模式下基准正在生成，跳过存在性检查，但结构检查始终生效。
 */
class ScreenshotBaselineGuardTest {

    @Test
    fun registeredCases_matchMatrixAndSnapshotDirectory() {
        val all = ScreenshotCases.all

        assertEquals(
            "登记用例总数与分族冻结值不一致",
            ScreenshotCases.expectedFamilyCounts.values.sum(),
            all.size,
        )
        assertEquals(
            "分族用例数量与冻结值不一致",
            ScreenshotCases.expectedFamilyCounts,
            all.groupingBy { it.family }.eachCount(),
        )
        assertEquals("用例 ID 必须唯一", all.size, all.map { it.id }.toSet().size)
        assertEquals("基准相对路径必须唯一", all.size, all.map { it.relativePath }.toSet().size)
        assertTrue(
            "用例 ID 必须以矩阵 ID 开头，例如 H01_p01-messages-matcha-light",
            all.all { it.id.startsWith("${it.family}_") },
        )
        assertTrue(
            "基准文件名必须包含矩阵 ID 前缀",
            all.all { it.fileName.startsWith("${it.family}_") },
        )
        assertTrue(
            "每个用例必须有测试类与说明",
            all.all { it.testClass.isNotBlank() && it.description.isNotBlank() },
        )
        assertEquals(
            "用例 ID 前缀必须与矩阵 ID 集合一致",
            ScreenshotCases.expectedFamilyCounts.keys,
            all.map { it.family }.toSet(),
        )

        // 测试类必须真实存在，避免登记到不存在的类或拼错类名。
        all.map { it.testClass }.toSet().forEach { testClass ->
            // 只验证类存在，不触发静态初始化（initialize=false），
            // 避免把测试类静态初始化阶段的失败与登记表问题混在一起。
            Class.forName(
                "com.example.myapplication.ui.screenshot.$testClass",
                false,
                javaClass.classLoader,
            )
        }

        // 每个测试类的 @Test 方法数量必须与登记用例数一致：
        // 删除测试方法但保留登记表与基准时，数量不符即失败，杜绝静默缺失。
        all.groupBy { it.testClass }.forEach { (testClass, cases) ->
            val loaded = Class.forName(
                "com.example.myapplication.ui.screenshot.$testClass",
                false,
                javaClass.classLoader,
            )
            val testMethodCount = loaded.declaredMethods.count {
                it.isAnnotationPresent(Test::class.java)
            }
            assertEquals(
                "测试类 $testClass 的 @Test 方法数($testMethodCount)与登记用例数(${cases.size})不一致",
                cases.size,
                testMethodCount,
            )
        }

        val snapshotDir = resolveSnapshotDirectory()
        assertTrue("截图基准目录不存在：$snapshotDir", snapshotDir.isDirectory)

        val pngs = snapshotDir.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .map { it.relativeTo(snapshotDir).invariantSeparatorsPath }
            .toSet()
        val registered = all.map { it.relativePath }.toSet()

        val unexpected = pngs - registered - ExplicitlyAllowedBaselines
        assertTrue("发现未登记的基准 PNG：$unexpected", unexpected.isEmpty())

        if (isRecordMode()) {
            println("Roborazzi record 模式：跳过基准存在性检查（结构检查仍然生效）")
            return
        }

        val missing = registered - pngs
        assertTrue("缺少已登记的基准 PNG：$missing", missing.isEmpty())
        registered.forEach { relativePath ->
            val file = File(snapshotDir, relativePath)
            assertTrue("基准文件过小，疑似空文件：$relativePath", file.length() > MinPngBytes)
            assertTrue("基准文件不是有效 PNG：$relativePath", file.looksLikePng())
        }
    }

    private fun isRecordMode(): Boolean = System.getProperty("roborazzi.test.record") == "true"

    /**
     * 基准目录由 app/build.gradle 注入系统属性；缺失时按常见工作目录兜底，
     * 都找不到就直接失败，不猜测运行目录。
     */
    private fun resolveSnapshotDirectory(): File {
        val injected = System.getProperty(SnapshotDirectoryProperty)
        if (!injected.isNullOrBlank()) {
            return File(injected)
        }
        val candidates = listOf(
            File("src/test/snapshots"),
            File("app/src/test/snapshots"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("无法定位截图基准目录：未设置系统属性 $SnapshotDirectoryProperty，且常见相对路径都不存在")
    }

    private fun File.looksLikePng(): Boolean {
        if (!isFile) return false
        val header = ByteArray(PngSignature.size)
        val read = inputStream().use { it.read(header) }
        return read == PngSignature.size && header.contentEquals(PngSignature)
    }

    private companion object {
        const val SnapshotDirectoryProperty = "narra.screenshot.snapshotDir"
        const val MinPngBytes = 2_000L

        val PngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        /**
         * 显式白名单：上一轮最小兼容性冒烟用例的基准（MessageBubbleScreenshotTest）。
         * 这里只允许列出的文件，其他任何未登记 PNG 都会让守卫失败。
         */
        val ExplicitlyAllowedBaselines = setOf(
            "MessageBubbleScreenshotTest/chat-user-message-matcha-light.png",
            "MessageBubbleScreenshotTest/roleplay-character-bubble-glass.png",
        )
    }
}
