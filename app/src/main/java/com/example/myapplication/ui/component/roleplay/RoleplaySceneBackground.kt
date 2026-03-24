package com.example.myapplication.ui.component.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RoleplaySceneBackground(
    backgroundUri: String,
    modifier: Modifier = Modifier,
) {
    val backdropState = rememberImmersiveBackdropState(backgroundUri)
    ImmersiveBackdrop(
        backdropState = backdropState,
        modifier = modifier,
    )
}

@Composable
fun RoleplaySceneBackground(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
) {
    ImmersiveBackdrop(
        backdropState = backdropState,
        modifier = modifier,
    )
}
