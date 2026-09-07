@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.model.resolveVoiceMessageDurationSeconds
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraUi
import kotlin.math.abs

val VoiceMessageDraftSaver: Saver<VoiceMessageDraft, Any> = listSaver(
    save = { draft ->
        listOf(
            draft.content,
            draft.durationSeconds?.toString().orEmpty(),
        )
    },
    restore = {
        VoiceMessageDraft(
            content = (it.getOrNull(0) as? String).orEmpty(),
            durationSeconds = (it.getOrNull(1) as? String).orEmpty().toIntOrNull(),
        )
    },
)

@Composable
internal fun VoiceMessageEditorSheet(
    draft: VoiceMessageDraft,
    isSending: Boolean,
    onDraftChange: (VoiceMessageDraft) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = MaterialTheme.colorScheme.primary
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val resolvedDuration = resolveVoiceMessageDurationSeconds(
        content = draft.content,
        preferredDurationSeconds = draft.durationSeconds,
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = NarraUi.cardRadius, topEnd = NarraUi.cardRadius),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "发送语音",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    NarraIconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "语音消息说明")
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        VoiceSheetField(
                            label = "语音内容",
                            value = draft.content,
                            placeholder = "例如：我一会儿回你电话。",
                            accentColor = accentColor,
                            minLines = 3,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(content = it),
                                )
                            },
                        )
                        VoiceSheetField(
                            label = "时长（秒）",
                            value = draft.durationSeconds?.toString().orEmpty(),
                            placeholder = "自动估算",
                            accentColor = accentColor,
                            keyboardType = KeyboardType.Number,
                            onValueChange = { nextValue ->
                                val numericValue = nextValue.filter(Char::isDigit).take(2)
                                onDraftChange(
                                    draft.copy(
                                        durationSeconds = numericValue.toIntOrNull(),
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "发送后预览",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VoicePreviewCard(
                            transcript = draft.content.trim().ifBlank { "" },
                            durationSeconds = resolvedDuration,
                            accentColor = accentColor,
                        )
                    }
                }
            }

            item {
                NarraButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending && draft.content.trim().isNotBlank(),
                    shape = RoundedCornerShape(NarraUi.smallRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp),
                ) {
                    Text(
                        text = if (isSending) "发送中…" else "发送语音消息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                NarraTextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消")
                }
            }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            shape = RoundedCornerShape(NarraUi.cardRadius),
            title = { Text("语音消息说明") },
            text = {
                Text(
                    text = "输入的文字会作为语音消息内容发送，不会录制麦克风声音。时长可填 1 至 60 秒，留空时按内容自动估算。文字内容仍可复制，也会发送给角色。",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                NarraTextButton(onClick = { showHelp = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun VoiceSheetField(
    label: String,
    value: String,
    placeholder: String,
    accentColor: Color,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(NarraUi.smallRadius),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun VoicePreviewCard(
    transcript: String,
    durationSeconds: Int,
    accentColor: Color,
) {
    val waveform = buildVoicePreviewWaveform("$transcript:$durationSeconds")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = accentColor,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.KeyboardVoice,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    waveform.forEach { baseHeight ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height((10f + baseHeight * 18f).dp)
                                .background(
                                    color = accentColor.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
                Text(
                    text = "${durationSeconds}″",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (transcript.isNotBlank()) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun buildVoicePreviewWaveform(seed: String): List<Float> {
    var state = seed.hashCode().let { if (it == Int.MIN_VALUE) 11 else abs(it) + 11 }
    return List(10) {
        state = (state * 1103515245 + 12345)
        val normalized = abs(state % 1000) / 1000f
        0.35f + normalized * 0.75f
    }
}
