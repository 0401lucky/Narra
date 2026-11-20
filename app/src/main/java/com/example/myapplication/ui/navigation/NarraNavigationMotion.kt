package com.example.myapplication.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

/**
 * Narra 全局页面层级视差转场：
 * - 新页面以物理弹簧从右侧轻快推入，底层页面轻微左移并微缩，强化空间层级感；
 * - 返回与预测性返回手势顺畅还原底层视差，取代默认 700ms 纯淡入淡出。
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.narraNavEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 380f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        ),
        initialOffsetX = { fullWidth -> (fullWidth * 0.28f).roundToInt() },
    ) + fadeIn(
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.narraNavExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -(fullWidth * 0.10f).roundToInt() },
    ) + scaleOut(
        targetScale = 0.975f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
    ) + fadeOut(
        animationSpec = tween(durationMillis = 190, easing = FastOutLinearInEasing),
        targetAlpha = 0.88f,
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.narraNavPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 400f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        ),
        initialOffsetX = { fullWidth -> -(fullWidth * 0.10f).roundToInt() },
    ) + scaleIn(
        initialScale = 0.975f,
        animationSpec = tween(durationMillis = 230, easing = LinearOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        initialAlpha = 0.88f,
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.narraNavPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> (fullWidth * 0.30f).roundToInt() },
    ) + fadeOut(
        animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
    )
}
