package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorldBookContentFullscreenSheet(
    initialContent: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val outlineColors =
        rememberSettingsOutlineColors().copy(focusedLabelColor = MaterialTheme.colorScheme.onSurface)
    var draft by rememberSaveable { mutableStateOf(initialContent) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = NarraShapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(PaddingValues(horizontal = NarraUi.space16, vertical = NarraUi.space12)),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑正文",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                NarraIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(NarraMinimumTouchTarget)
                ) {
                    Icon(Lucide.X, contentDescription = "取消正文编辑")
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("worldbook_fullscreen_content")
                    .semantics { contentDescription = "正文" }
                    .weight(1f),
                shape = NarraShapes.medium,
                colors = outlineColors,
            )
            AnimatedSettingButton(
                text = "保存并返回",
                onClick = {
                    onConfirm(draft)
                    onDismiss()
                },
                enabled = true,
                isPrimary = true,
                shape = NarraShapes.medium,
                contentPadding = PaddingValues(
                    horizontal = NarraUi.space16,
                    vertical = NarraUi.space12
                ),
            )
        }
    }
}
