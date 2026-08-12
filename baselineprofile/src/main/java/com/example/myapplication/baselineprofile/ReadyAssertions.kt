package com.example.myapplication.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlin.math.roundToInt

internal class ReadyAssertions private constructor(
    private val targetPackage: String,
    private val device: UiDevice,
    private val fixtureClient: BenchmarkFixtureClient,
    private val topClickGuardPx: Int,
    private val bottomClickGuardPx: Int,
) {
    fun prepareFixtureOrThrow(
        scope: MacrobenchmarkScope,
        scenario: BenchmarkScenario,
    ): BenchmarkSession {
        clearTargetDataOrThrow()
        val session = fixtureClient.prepareFixtureOrThrow(scenario)
        scope.killProcess()
        pressHomeOrThrow()
        return session
    }

    fun prepareProfileFixtureOrThrow(scenario: BenchmarkScenario): BenchmarkSession {
        clearTargetDataOrThrow()
        val session = fixtureClient.prepareFixtureOrThrow(scenario)
        forceStopTargetOrThrow()
        pressHomeOrThrow()
        return session
    }

    fun startScenarioOrThrow(
        scope: MacrobenchmarkScope,
        session: BenchmarkSession,
        targetReadyId: String = session.scenario.initialReadyId,
    ) {
        scope.startActivityAndWait { intent ->
            intent.putExtra(SESSION_ID_INTENT_EXTRA, session.sessionId)
        }
        waitForReadyOrThrow(targetReadyId)
    }

    fun waitForReadyOrThrow(
        readyId: String,
        timeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
    ) {
        require(readyId in BenchmarkReadyIds.ALL) { "未登记的 benchmark ready：$readyId" }
        val selector = By.res(readyId)
        val found = device.wait(Until.hasObject(selector), timeoutMillis)
        if (!found) {
            val visibleIds = visibleBenchmarkIds()
            throw AssertionError(
                "等待 benchmark ready 超时：$readyId；当前可见 benchmark 标签：" +
                    visibleIds.ifEmpty { listOf("无") }.joinToString(),
            )
        }
    }

    fun findAndClickOrThrow(
        actionId: String,
        timeoutMillis: Long = DEFAULT_ACTION_TIMEOUT_MILLIS,
    ) {
        require(actionId in BenchmarkActionIds.ALL) { "未登记的 benchmark action：$actionId" }
        findAndClickOrThrow(
            selector = By.res(actionId),
            safeLabel = actionId,
            deadline = SystemClock.uptimeMillis() + timeoutMillis,
        )
    }

    fun clickAndWaitForReadyOrThrow(
        actionId: String,
        readyId: String,
    ) {
        require(readyId in BenchmarkReadyIds.ALL) { "未登记的 benchmark ready：$readyId" }
        require(actionId in BenchmarkActionIds.ALL) { "未登记的 benchmark action：$actionId" }
        val actionDeadline = SystemClock.uptimeMillis() + DEFAULT_ACTION_TIMEOUT_MILLIS
        val actionSelector = By.res(actionId)
        findAndClickOrThrow(
            selector = actionSelector,
            safeLabel = actionId,
            deadline = actionDeadline,
        )
        val readySelector = By.res(readyId)
        val retryGraceMillis = minOf(
            CLICK_RETRY_GRACE_MILLIS,
            remainingMillis(actionDeadline),
        )
        if (
            retryGraceMillis > 0L &&
            device.wait(Until.hasObject(readySelector), retryGraceMillis)
        ) {
            return
        }

        // 刚滚入视口的首个点击可能只会终止惯性滚动；宽限等待后仅对仍处于安全区的节点重试。
        clickCurrentSafeTarget(actionSelector, actionDeadline)
        waitForReadyOrThrow(
            readyId = readyId,
            timeoutMillis = DEFAULT_READY_TIMEOUT_MILLIS - CLICK_RETRY_GRACE_MILLIS,
        )
    }

    fun pressBackAndWaitForReadyOrThrow(readyId: String) {
        // UIAutomator 在 Compose 导航已处理返回时仍可能报告 false，目标 ready 才是可靠结果。
        device.pressBack()
        waitForReadyOrThrow(readyId)
    }

    fun performFixedScrollRoundTripOrThrow(
        topReadyId: String,
        bottomReadyId: String,
    ) {
        scrollUntilReadyOrThrow(
            readyId = topReadyId,
            fromYRatio = 0.28f,
            toYRatio = 0.82f,
        )
        scrollUntilReadyOrThrow(
            readyId = bottomReadyId,
            fromYRatio = 0.82f,
            toYRatio = 0.28f,
        )
    }

    fun clearSessionOrThrow() {
        var clearFailure: Throwable? = null
        try {
            fixtureClient.clearSessionOrThrow()
        } catch (throwable: Throwable) {
            clearFailure = throwable
            throw throwable
        } finally {
            try {
                forceStopTargetOrThrow()
            } catch (stopFailure: Throwable) {
                if (clearFailure != null) {
                    clearFailure.addSuppressed(stopFailure)
                } else {
                    throw stopFailure
                }
            }
        }
    }

    fun <T> runWithSessionCleanup(block: () -> T): T {
        var primaryFailure: Throwable? = null
        try {
            return block()
        } catch (throwable: Throwable) {
            primaryFailure = throwable
            throw throwable
        } finally {
            try {
                clearSessionOrThrow()
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            }
        }
    }

    private fun findAndClickOrThrow(
        selector: BySelector,
        safeLabel: String,
        deadline: Long,
    ) {
        while (remainingMillis(deadline) > 0L) {
            val remaining = remainingMillis(deadline)
            val target = device.wait(
                Until.findObject(selector),
                minOf(ACTION_SCROLL_POLL_MILLIS, remaining),
            )
            if (target != null) {
                if (clickStableTargetOrScrollIntoSafeArea(selector, deadline)) {
                    return
                }
                continue
            }
            if (!hasSwipeBudget(deadline)) {
                break
            }
            swipeOrThrow(
                fromYRatio = 0.82f,
                toYRatio = 0.28f,
            )
        }

        // 截止后只采样当前语义树用于错误信息，不再点击或滚动，避免动作越过调用方 deadline。
        val visibleIds = visibleBenchmarkIds()
        throw AssertionError(
            "滚动查找后仍找不到 benchmark 目标控件：$safeLabel；当前可见 benchmark 标签：" +
                visibleIds.ifEmpty { listOf("无") }.joinToString(),
        )
    }

    private fun clickStableTargetOrScrollIntoSafeArea(
        selector: BySelector,
        deadline: Long,
    ): Boolean {
        if (!waitForIdleUntil(deadline)) {
            return false
        }
        val stableTarget = device.findObject(selector) ?: return false
        if (!clickTargetIfSafe(stableTarget, deadline)) {
            // Compose 语义树可能提前暴露仅贴着底部系统导航区的节点；先小幅上滚，
            // 避免 UiObject2 把点击落到系统栏内却仍返回正常。
            if (!hasSwipeBudget(deadline)) {
                return false
            }
            swipeOrThrow(
                fromYRatio = 0.82f,
                toYRatio = 0.62f,
            )
            return false
        }
        return true
    }

    private fun clickCurrentSafeTarget(
        selector: BySelector,
        deadline: Long,
    ): Boolean {
        val target = device.findObject(selector) ?: return false
        return clickTargetIfSafe(target, deadline)
    }

    private fun clickTargetIfSafe(
        target: UiObject2,
        deadline: Long,
    ): Boolean {
        if (remainingMillis(deadline) <= 0L) {
            return false
        }
        if (!isTargetInsideSafeClickArea(target)) {
            return false
        }
        if (remainingMillis(deadline) <= 0L) {
            return false
        }
        target.click()
        return true
    }

    private fun isTargetInsideSafeClickArea(target: UiObject2): Boolean {
        val bounds = target.visibleBounds
        val safeBottom = device.displayHeight - bottomClickGuardPx
        return bounds.width() > 0 &&
            bounds.height() > 0 &&
            bounds.top >= topClickGuardPx &&
            bounds.bottom <= safeBottom
    }

    private fun waitForIdleUntil(deadline: Long): Boolean {
        val remaining = remainingMillis(deadline)
        if (remaining <= 0L) {
            return false
        }
        device.waitForIdle(remaining)
        return remainingMillis(deadline) > 0L
    }

    private fun hasSwipeBudget(deadline: Long): Boolean {
        return remainingMillis(deadline) >= MIN_SWIPE_REMAINING_MILLIS
    }

    private fun remainingMillis(deadline: Long): Long {
        return (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
    }

    private fun visibleBenchmarkIds(): List<String> {
        val registeredIds = BenchmarkReadyIds.ALL + BenchmarkActionIds.ALL
        return runCatching {
            device.findObjects(By.res(BENCHMARK_RESOURCE_ID_PATTERN))
                .mapNotNull(UiObject2::getResourceName)
                .filter(registeredIds::contains)
        }.getOrDefault(emptyList())
            .distinct()
            .sorted()
    }

    private fun clearTargetDataOrThrow() {
        val output = device.executeShellCommand("pm clear $targetPackage").trim()
        if (output != "Success") {
            throw AssertionError("清理 benchmark 目标数据失败")
        }
    }

    private fun forceStopTargetOrThrow() {
        val output = device.executeShellCommand("am force-stop $targetPackage").trim()
        if (output.isNotEmpty()) {
            throw AssertionError("停止 benchmark 目标进程失败")
        }
        repeat(PROCESS_STOP_POLL_COUNT) {
            if (device.executeShellCommand("pidof $targetPackage").trim().isEmpty()) {
                return
            }
            SystemClock.sleep(PROCESS_STOP_POLL_MILLIS)
        }
        throw AssertionError("benchmark 目标进程未在超时内停止")
    }

    private fun pressHomeOrThrow() {
        device.pressHome()
        val targetGone = device.wait(
            Until.gone(By.pkg(targetPackage).depth(0)),
            DEFAULT_ACTION_TIMEOUT_MILLIS,
        )
        if (!targetGone) {
            throw AssertionError("benchmark Home 操作后目标应用仍在前台")
        }
    }

    private fun swipeOrThrow(
        fromYRatio: Float,
        toYRatio: Float,
    ) {
        val width = device.displayWidth
        val height = device.displayHeight
        val accepted = device.swipe(
            width / 2,
            (height * fromYRatio).toInt(),
            width / 2,
            (height * toYRatio).toInt(),
            SWIPE_STEPS,
        )
        if (!accepted) {
            throw AssertionError("benchmark 固定滚动手势执行失败")
        }
    }

    private fun scrollUntilReadyOrThrow(
        readyId: String,
        fromYRatio: Float,
        toYRatio: Float,
    ) {
        require(readyId in BenchmarkReadyIds.ALL) { "未登记的 benchmark ready：$readyId" }
        val selector = By.res(readyId)
        if (device.hasObject(selector)) {
            return
        }
        repeat(MAX_SCROLL_GESTURE_COUNT) {
            swipeOrThrow(fromYRatio = fromYRatio, toYRatio = toYRatio)
            if (device.wait(Until.hasObject(selector), SCROLL_READY_POLL_MILLIS)) {
                return
            }
        }
        throw AssertionError(
            "固定滚动超过 $MAX_SCROLL_GESTURE_COUNT 次仍未到达 benchmark ready：$readyId",
        )
    }

    companion object {
        private const val SESSION_ID_INTENT_EXTRA = "benchmark_session_id"
        private const val DEFAULT_READY_TIMEOUT_MILLIS = 10_000L
        private const val DEFAULT_ACTION_TIMEOUT_MILLIS = 10_000L
        private const val PROCESS_STOP_POLL_COUNT = 20
        private const val PROCESS_STOP_POLL_MILLIS = 50L
        private const val MAX_SCROLL_GESTURE_COUNT = 40
        private const val SCROLL_READY_POLL_MILLIS = 250L
        private const val ACTION_SCROLL_POLL_MILLIS = 250L
        private const val CLICK_RETRY_GRACE_MILLIS = 750L
        private const val MIN_SWIPE_REMAINING_MILLIS = ACTION_SCROLL_POLL_MILLIS
        private const val ACTION_CLICK_GUARD_DP = 8f
        private const val FALLBACK_STATUS_BAR_GUARD_RATIO = 0.04f
        private const val FALLBACK_NAVIGATION_BAR_GUARD_RATIO = 0.05f
        private const val SWIPE_STEPS = 20
        private val BENCHMARK_RESOURCE_ID_PATTERN = Pattern.compile("^benchmark_.*")

        fun create(): ReadyAssertions {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetPackage = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: error("targetAppId not passed as instrumentation runner arg")
            require(targetPackage.matches(Regex("[A-Za-z0-9_.]+"))) { "targetAppId 格式无效" }
            val device = UiDevice.getInstance(instrumentation)
            val resources = instrumentation.context.resources
            val statusBarHeightId = resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android",
            )
            val navigationBarHeightId = resources.getIdentifier(
                "navigation_bar_height",
                "dimen",
                "android",
            )
            val statusBarHeightPx = if (statusBarHeightId != 0) {
                resources.getDimensionPixelSize(statusBarHeightId)
            } else {
                (device.displayHeight * FALLBACK_STATUS_BAR_GUARD_RATIO).roundToInt()
            }
            val navigationBarHeightPx = if (navigationBarHeightId != 0) {
                resources.getDimensionPixelSize(navigationBarHeightId)
            } else {
                (device.displayHeight * FALLBACK_NAVIGATION_BAR_GUARD_RATIO).roundToInt()
            }
            val clickSafetyMarginPx =
                (ACTION_CLICK_GUARD_DP * resources.displayMetrics.density).roundToInt()
            return ReadyAssertions(
                targetPackage = targetPackage,
                device = device,
                fixtureClient = BenchmarkFixtureClient(instrumentation.context, targetPackage),
                topClickGuardPx = statusBarHeightPx + clickSafetyMarginPx,
                bottomClickGuardPx = navigationBarHeightPx + clickSafetyMarginPx,
            )
        }
    }
}
