package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.example.myapplication.model.BUILT_IN_TEMPLATES
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.bounceClick

@Composable
fun ProviderTemplateDialog(
    onSelectTemplate: (ProviderTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
            ) {
                Column {
                    Text(
                        text = "选择提供商模板",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "快速填充配置参数。",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(BUILT_IN_TEMPLATES) { template ->
                            TemplateCard(
                                template = template,
                                onClick = { onSelectTemplate(template) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        NarraTextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ProviderTemplate,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(interactionSource = interactionSource),
        shape = RoundedCornerShape(18.dp),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TemplateIcon(template)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TemplateIcon(template: ProviderTemplate) {
    val iconRes = template.type.iconRes
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = template.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = template.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
