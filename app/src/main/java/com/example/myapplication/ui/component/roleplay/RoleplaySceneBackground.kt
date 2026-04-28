package com.example.myapplication.ui.component.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

@Composable
fun RoleplaySceneBackground(
    backgroundUri: String,
    modifier: Modifier = Modifier,
    fallbackBackgroundColor: Color? = null,
) {
    val backdropState = rememberImmersiveBackdropState(backgroundUri)
    ImmersiveBackdrop(
        backdropState = backdropState,
        modifier = modifier,
        fallbackBackgroundColor = fallbackBackgroundColor,
    )
}

@Composable
fun RoleplaySceneBackground(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
    fallbackBackgroundColor: Color? = null,
) {
    ImmersiveBackdrop(
        backdropState = backdropState,
        modifier = modifier,
        fallbackBackgroundColor = fallbackBackgroundColor,
    )
}
