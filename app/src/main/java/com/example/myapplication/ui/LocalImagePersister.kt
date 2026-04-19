package com.example.myapplication.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.myapplication.data.repository.LocalImageStore

/**
 * Composition-scope 单例：用户选图后复制到 app 私有存储的入口。
 * 由 [com.example.myapplication.ui.AppRoot] 通过 AppGraph 注入。
 */
val LocalImagePersister = staticCompositionLocalOf<LocalImageStore> {
    error("LocalImagePersister 未注入；请确认调用点位于 AppRoot 组合树内")
}
