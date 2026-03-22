package com.example.myapplication.system.translation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class VendorBackgroundGuide(
    val title: String,
    val summary: String,
    val steps: List<String>,
    val deepLinkIntent: Intent? = null,
)

fun resolveVendorBackgroundGuide(context: Context): VendorBackgroundGuide? {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        "xiaomi" in manufacturer || "redmi" in manufacturer -> VendorBackgroundGuide(
            title = "小米后台保护",
            summary = "建议开启自启动，并把电池策略调整为无限制。",
            steps = listOf(
                "打开自启动管理，允许本应用自动启动。",
                "在电池与性能设置中，将后台策略改为无限制。",
                "在最近任务里锁定本应用，减少被系统清理的概率。",
            ),
            deepLinkIntent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            },
        )

        "huawei" in manufacturer || "honor" in manufacturer -> VendorBackgroundGuide(
            title = "华为/荣耀后台保护",
            summary = "建议关闭自动管理，并允许后台活动。",
            steps = listOf(
                "在应用启动管理里关闭自动管理。",
                "允许自启动、关联启动和后台活动。",
                "在电池优化中把本应用设为不受限制。",
            ),
            deepLinkIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        "oppo" in manufacturer || "oneplus" in manufacturer -> VendorBackgroundGuide(
            title = "OPPO/一加后台保护",
            summary = "建议允许自启动并关闭应用速冻或智能限制。",
            steps = listOf(
                "打开自启动管理并允许本应用。",
                "在电池管理中关闭智能限制或睡眠优化。",
                "将本应用加入后台保护白名单。",
            ),
            deepLinkIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
        )

        "vivo" in manufacturer -> VendorBackgroundGuide(
            title = "vivo 后台保护",
            summary = "建议允许高耗电运行，并把本应用加入后台高耗电白名单。",
            steps = listOf(
                "在后台高耗电里允许本应用持续运行。",
                "在自启动管理里允许本应用自启动。",
                "关闭系统的后台冻结或深度睡眠限制。",
            ),
            deepLinkIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
        )

        else -> null
    }?.let { guide ->
        val intent = guide.deepLinkIntent?.apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        guide.copy(deepLinkIntent = intent)
    }
}
