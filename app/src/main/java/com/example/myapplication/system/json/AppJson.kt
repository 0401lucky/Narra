package com.example.myapplication.system.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 全局共享的 Gson 实例。
 *
 * 所有需要 Gson 的位置（Repository / Store / Tool / Adapter）都应使用 [AppJson.gson]，
 * 而不是各自 `private val gson = Gson()`。这样未来需要统一注册 TypeAdapter、
 * 命名策略或 lenient 模式时，只改这一处即可。
 *
 * Gson 本身线程安全，重复 `new` 无正确性问题，但会让统一改造变成散弹式改动。
 */
object AppJson {
    val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .create()
    }
}
