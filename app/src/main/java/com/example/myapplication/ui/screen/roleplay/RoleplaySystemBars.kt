package com.example.myapplication.ui.screen.roleplay

import android.app.Activity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    surfaceColor: Color? = null,
) {
    val topLuminance = remember(backdropState.imageBitmap, surfaceColor) {
        surfaceColor?.luminance() ?: calculateTopRegionLuminance(backdropState.imageBitmap)
    }
    val bottomLuminance = remember(backdropState.imageBitmap, surfaceColor) {
        surfaceColor?.luminance() ?: calculateBottomRegionLuminance(backdropState.imageBitmap)
    }
    ApplyRoleplayWindowBars(topLuminance > 0.5f, bottomLuminance > 0.5f, immersiveMode)
}

@Composable
internal fun ApplyRoleplaySurfaceSystemBars(
    surfaceColor: Color = MaterialTheme.colorScheme.background,
    immersiveMode: RoleplayImmersiveMode,
) {
    val lightIconsBackground = surfaceColor.luminance() > 0.5f
    ApplyRoleplayWindowBars(lightIconsBackground, lightIconsBackground, immersiveMode)
}

@Composable
private fun ApplyRoleplayWindowBars(
    lightStatusBar: Boolean,
    lightNavigationBar: Boolean,
    immersiveMode: RoleplayImmersiveMode,
) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val themeSurface by rememberUpdatedState(MaterialTheme.colorScheme.background)
    val currentLightStatus by rememberUpdatedState(lightStatusBar)
    val currentLightNavigation by rememberUpdatedState(lightNavigationBar)
    val currentMode by rememberUpdatedState(immersiveMode)

    // 导航过渡会同时组合两页，只有恢复到前台的目的地可以设置窗口。
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        applyRoleplayWindowState(controller, lightStatusBar, lightNavigationBar, immersiveMode)
    }

    DisposableEffect(view, lifecycleOwner) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)

            fun restoreTheme() {
                val light = themeSurface.luminance() > 0.5f
                applyRoleplayWindowState(controller, light, light, RoleplayImmersiveMode.NONE)
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> applyRoleplayWindowState(
                        controller, currentLightStatus, currentLightNavigation, currentMode,
                    )
                    Lifecycle.Event.ON_PAUSE -> restoreTheme()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    restoreTheme()
                }
            }
        }
    }
}

private fun applyRoleplayWindowState(
    controller: WindowInsetsControllerCompat,
    lightStatusBar: Boolean,
    lightNavigationBar: Boolean,
    mode: RoleplayImmersiveMode,
) {
    controller.isAppearanceLightStatusBars = lightStatusBar
    controller.isAppearanceLightNavigationBars = lightNavigationBar
    if (mode == RoleplayImmersiveMode.HIDE_SYSTEM_BARS) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
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
