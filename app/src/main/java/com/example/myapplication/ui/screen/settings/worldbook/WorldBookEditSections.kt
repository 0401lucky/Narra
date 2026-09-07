package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

/** 编辑页四段折叠态 + 可选的"试命中"预览段，默认展开前两段。 */
internal data class WorldBookEditExpandedState(
    val basicInfo: Boolean,
    val hitRule: Boolean,
    val scope: Boolean,
    val status: Boolean,
    val hitPreview: Boolean = false,
) {
    fun toggleBasicInfo() = copy(basicInfo = !basicInfo)
    fun toggleHitRule() = copy(hitRule = !hitRule)
    fun toggleScope() = copy(scope = !scope)
    fun toggleStatus() = copy(status = !status)
    fun toggleHitPreview() = copy(hitPreview = !hitPreview)

    companion object {
        val DEFAULT = WorldBookEditExpandedState(
            basicInfo = true,
            hitRule = true,
            scope = false,
            status = false,
            hitPreview = false,
        )

        val Saver: Saver<WorldBookEditExpandedState, *> = Saver(
            save = { listOf(it.basicInfo, it.hitRule, it.scope, it.status, it.hitPreview) },
            restore = { raw ->
                @Suppress("UNCHECKED_CAST")
                val bools = raw as List<Boolean>
                WorldBookEditExpandedState(
                    basicInfo = bools[0],
                    hitRule = bools[1],
                    scope = bools[2],
                    status = bools[3],
                    hitPreview = bools.getOrElse(4) { false },
                )
            },
        )
    }
}

@Composable
internal fun rememberWorldBookEditExpandedState(): MutableState<WorldBookEditExpandedState> {
    return rememberSaveable(stateSaver = WorldBookEditExpandedState.Saver) {
        mutableStateOf(WorldBookEditExpandedState.DEFAULT)
    }
}

/**
 * 分段表单保留独立的折叠和说明入口，避免整段表单层层套卡。
 */
@Composable
internal fun WorldBookCollapsibleSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onToggle)
                    .heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.title,
                )
                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (expanded) "折叠$title" else "展开$title",
                    tint = palette.body,
                )
            }
            SettingsHelpButton(title, description)
        }
        AnimatedVisibility(visible = expanded) {
            body()
        }
        HorizontalDivider(color = palette.border)
    }
}
