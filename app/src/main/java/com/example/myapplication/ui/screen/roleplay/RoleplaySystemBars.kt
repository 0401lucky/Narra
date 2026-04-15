package com.example.myapplication.ui.screen.roleplay

import android.app.Activity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.calculateBottomRegionLuminance
import com.example.myapplication.ui.component.roleplay.calculateTopRegionLuminance

/**
 * 根据背景图亮度自适应系统栏图标颜色，并按 [immersiveMode] 控制系统栏显隐。
 *
 * 注意：`enableEdgeToEdge()` 已在 [com.example.myapplication.MainActivity] 中全局启用，
 * 且从 API 35 起 edge-to-edge 为系统强制行为，因此此处**不再**操作
 * `setDecorFitsSystemWindows`。布局层的安全区 padding 统一由 [roleplayImmersivePadding]
 * 扩展函数处理。
 */
@Composable
internal fun ApplyRoleplaySystemBars(
    backdropState: ImmersiveBackdropState,
    immersiveMode: RoleplayImmersiveMode,
) {
    val view = LocalView.current

    // 根据背景图顶部/底部区域亮度，自动切换系统栏图标深浅色
    LaunchedEffect(backdropState.imageBitmap) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val topLuminance = calculateTopRegionLuminance(backdropState.imageBitmap)
        val bottomLuminance = calculateBottomRegionLuminance(backdropState.imageBitmap)
        controller.isAppearanceLightStatusBars = topLuminance > 0.5f
        controller.isAppearanceLightNavigationBars = bottomLuminance > 0.5f
    }

    // 按沉浸模式控制系统栏显隐
    DisposableEffect(view, immersiveMode) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)

            when (immersiveMode) {
                // 边到边 / 标准：保持系统栏可见
                RoleplayImmersiveMode.EDGE_TO_EDGE,
                RoleplayImmersiveMode.NONE,
                -> {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }

                // 全屏：隐藏系统栏，边缘手势可临时唤出
                RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            onDispose {
                // 离开角色扮演页面时恢复系统栏可见
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }
}

/**
 * 根据 [RoleplayImmersiveMode] 添加系统栏安全区 padding。
 *
 * - [RoleplayImmersiveMode.EDGE_TO_EDGE] / [RoleplayImmersiveMode.NONE]：
 *   添加 `statusBarsPadding()` + `navigationBarsPadding()`，让内容不被系统栏遮挡。
 * - [RoleplayImmersiveMode.HIDE_SYSTEM_BARS]：
 *   不添加额外 padding，因为系统栏已被隐藏。
 */
fun Modifier.roleplayImmersivePadding(
    mode: RoleplayImmersiveMode,
): Modifier = composed {
    when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE,
        RoleplayImmersiveMode.NONE,
        -> this
            .statusBarsPadding()
            .navigationBarsPadding()

        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> this
    }
}

/**
 * 仅添加状态栏 padding（用于需要单独控制顶部间距的场景）。
 *
 * [RoleplayImmersiveMode.HIDE_SYSTEM_BARS] 时返回 0.dp（系统栏已隐藏，无需偏移）。
 */
fun Modifier.roleplayStatusBarPadding(
    mode: RoleplayImmersiveMode,
): Modifier = composed {
    when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE,
        RoleplayImmersiveMode.NONE,
        -> this.statusBarsPadding()

        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> this
    }
}

/**
 * 仅添加导航栏 padding。
 *
 * [RoleplayImmersiveMode.HIDE_SYSTEM_BARS] 时不添加。
 */
fun Modifier.roleplayNavigationBarPadding(
    mode: RoleplayImmersiveMode,
): Modifier = composed {
    when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE,
        RoleplayImmersiveMode.NONE,
        -> this.navigationBarsPadding()

        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> this
    }
}
