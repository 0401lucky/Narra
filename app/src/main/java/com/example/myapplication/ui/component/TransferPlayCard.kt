package com.example.myapplication.ui.component

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.formatTransferAmount
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.transferDirectionLabel
import com.example.myapplication.model.transferStatusLabel

@Composable
fun TransferPlayCard(
    part: ChatMessagePart,
    isUserMessage: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!part.isTransferPart()) {
        return
    }

    val isAssistantToUser = part.specialDirection == TransferDirection.ASSISTANT_TO_USER
    val canConfirmReceipt = !isUserMessage &&
        isAssistantToUser &&
        part.specialStatus == TransferStatus.PENDING &&
        onConfirmTransferReceipt != null &&
        part.specialId.isNotBlank()
    val topColor = if (isUserMessage) {
        Color(0xFFF4C16F)
    } else {
        Color(0xFFF0B35A)
    }
    val bottomColor = if (isUserMessage) {
        Color(0xFFEAAA47)
    } else {
        Color(0xFFE19A3A)
    }
    val contentColor = Color.White

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(topColor, bottomColor),
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                }
                Text(
                    text = part.transferDirectionLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = part.formatTransferAmount(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            if (part.specialNote.isNotBlank()) {
                Text(
                    text = part.specialNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.92f),
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

            Text(
                text = part.transferStatusLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.92f),
            )

            if (canConfirmReceipt) {
                NarraFilledTonalButton(
                    onClick = { onConfirmTransferReceipt?.invoke(part.specialId) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF8A4B08),
                    ),
                ) {
                    Text("确认收款")
                }
            }
        }
    }
}
