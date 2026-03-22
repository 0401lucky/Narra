package com.example.myapplication.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            Snackbar(
                modifier = Modifier.padding(12.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                snackbarData = data
            )
        }
    )
}
