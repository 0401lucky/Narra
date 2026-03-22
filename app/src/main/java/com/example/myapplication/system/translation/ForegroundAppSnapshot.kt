package com.example.myapplication.system.translation

object ForegroundAppSnapshot {
    @Volatile
    var packageName: String = ""

    @Volatile
    var appLabel: String = ""
}
