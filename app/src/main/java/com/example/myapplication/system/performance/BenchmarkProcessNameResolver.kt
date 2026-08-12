package com.example.myapplication.system.performance

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

object BenchmarkProcessNameResolver {
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val currentPid = Process.myPid()
        return activityManager?.runningAppProcesses
            ?.firstOrNull { process -> process.pid == currentPid }
            ?.processName
            .orEmpty()
            .ifBlank { context.packageName }
    }
}
