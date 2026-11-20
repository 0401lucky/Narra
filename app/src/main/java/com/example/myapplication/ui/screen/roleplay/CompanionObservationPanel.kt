package com.example.myapplication.ui.screen.roleplay

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.ParsedCompanionReply
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton

@Composable
fun CompanionObservationPanel(
    reply: ParsedCompanionReply?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val observation = reply?.observation?.takeIf { enabled } ?: return
    var expanded by rememberSaveable(
        reply.source.messageId,
        reply.source.revision,
        reply.source.rawHash
    ) { mutableStateOf(false) }
    NarraTextButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("companion_observation_open")
    ) {
        Text("查看本条观测")
    }
    if (expanded) NarraAlertDialog(
        title = "本条回复的观测",
        message = observation.fields.joinToString("\n\n") { "${it.label}：${it.value}" },
        onDismiss = { expanded = false }, onConfirm = { expanded = false },
        confirmLabel = "关闭", dismissLabel = null,
    )
}
