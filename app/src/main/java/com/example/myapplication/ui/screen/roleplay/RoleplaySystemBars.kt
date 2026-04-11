package com.example.myapplication.ui.screen.roleplay

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.calculateBottomRegionLuminance
import com.example.myapplication.ui.component.roleplay.calculateTopRegionLuminance

@Composable
internal fun ApplyRoleplaySystemBars(
    backdropState: ImmersiveBackdropState,
    immersiveMode: RoleplayImmersiveMode,
) {
    val view = LocalView.current

    LaunchedEffect(backdropState.imageBitmap) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val topLuminance = calculateTopRegionLuminance(backdropState.imageBitmap)
        val bottomLuminance = calculateBottomRegionLuminance(backdropState.imageBitmap)
        controller.isAppearanceLightStatusBars = topLuminance > 0.5f
        controller.isAppearanceLightNavigationBars = bottomLuminance > 0.5f
    }

    DisposableEffect(view, immersiveMode) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

            when (immersiveMode) {
                RoleplayImmersiveMode.EDGE_TO_EDGE -> {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }

                RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

                RoleplayImmersiveMode.NONE -> {
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                }
            }

            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }
}
