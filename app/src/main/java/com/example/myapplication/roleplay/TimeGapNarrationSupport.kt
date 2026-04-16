package com.example.myapplication.roleplay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 时间断层旁白支持。
 * 当用户两次发消息之间间隔超过阈值时，生成一条系统旁白注入到 prompt 中，
 * 让 AI 感知时间流逝并自然调整回复状态。
 */
object TimeGapNarrationSupport {

    // 30 分钟以上才生成旁白
    private val MIN_GAP_MILLIS = TimeUnit.MINUTES.toMillis(30)
    // 2 小时分界点
    private val MEDIUM_GAP_MILLIS = TimeUnit.HOURS.toMillis(2)
    // 12 小时分界点
    private val LONG_GAP_MILLIS = TimeUnit.HOURS.toMillis(12)

    /**
     * 根据上次互动时间和当前时间生成时间断层旁白。
     * @return 旁白文本，如果间隔不足阈值则返回 null。
     */
    fun buildTimeGapNarration(
        lastTimestamp: Long,
        currentTimestamp: Long,
    ): String? {
        if (lastTimestamp <= 0L || currentTimestamp <= lastTimestamp) {
            return null
        }
        val gapMillis = currentTimestamp - lastTimestamp
        if (gapMillis < MIN_GAP_MILLIS) {
            return null
        }
        val gapDescription = formatGapDescription(gapMillis)
        val currentTimeStr = formatAbsoluteTime(currentTimestamp)
        return buildString {
            append("【时间旁白】距离上一条消息经过了")
            append(gapDescription)
            append("。现在时间来到了")
            append(currentTimeStr)
            append("。")
        }
    }

    /**
     * 格式化时间间隔的自然语言描述。
     */
    internal fun formatGapDescription(gapMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(gapMillis)
        val hours = minutes / 60.0

        return when {
            gapMillis < MEDIUM_GAP_MILLIS -> {
                // 30min - 2h：用分钟或"约 X 小时"
                if (minutes < 60) {
                    "${minutes}分钟"
                } else {
                    val h = minutes / 60
                    val m = minutes % 60
                    if (m == 0L) "${h}小时" else "${h}小时${m}分钟"
                }
            }

            gapMillis < LONG_GAP_MILLIS -> {
                // 2h - 12h：用小时
                val h = String.format(Locale.ROOT, "%.1f", hours).removeSuffix(".0")
                "${h}小时"
            }

            else -> {
                // 12h+：用天和小时
                val days = TimeUnit.MILLISECONDS.toDays(gapMillis)
                val remainingHours = TimeUnit.MILLISECONDS.toHours(gapMillis) - days * 24
                if (days == 0L) {
                    "${remainingHours}小时"
                } else if (remainingHours == 0L) {
                    "${days}天"
                } else {
                    "${days}天${remainingHours}小时"
                }
            }
        }
    }

    private fun formatAbsoluteTime(timestamp: Long): String {
        val format = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
        return format.format(Date(timestamp))
    }
}
