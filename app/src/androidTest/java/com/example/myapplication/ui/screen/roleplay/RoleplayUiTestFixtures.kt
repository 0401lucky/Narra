package com.example.myapplication.ui.screen.roleplay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette

internal fun roleplayTestBackdropState(): ImmersiveBackdropState {
    return ImmersiveBackdropState(
        imageBitmap = null,
        screenSize = IntSize(1080, 1920),
        palette = ImmersiveGlassPalette(
            panelTint = Color(0xFF28415B),
            panelTintStrong = Color(0xFF20344A),
            panelHighlight = Color.White.copy(alpha = 0.18f),
            panelBorder = Color.White.copy(alpha = 0.18f),
            shadowColor = Color.Black.copy(alpha = 0.32f),
            scrimTop = Color.Black.copy(alpha = 0.20f),
            scrimBottom = Color.Black.copy(alpha = 0.40f),
            onGlass = Color.White,
            onGlassMuted = Color.White.copy(alpha = 0.72f),
            chipTint = Color(0xFF5B8FD9),
            chipText = Color.White,
            characterAccent = Color(0xFF9FD3FF),
            userAccent = Color(0xFFFFD79A),
            thoughtText = Color(0xFFF2E9FF),
            readingSurface = Color(0xFF1F2F42),
            readingBorder = Color(0xFF7FB8F4),
        ),
    )
}
