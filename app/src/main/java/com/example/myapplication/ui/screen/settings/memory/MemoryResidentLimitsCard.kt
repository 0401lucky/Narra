package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import com.example.myapplication.model.MemoryResidentLimits

@Composable
internal fun MemoryResidentLimitsCard(
    limits: MemoryResidentLimits,
    onUpdate: (MemoryResidentLimits) -> Unit
) {
    var values by rememberSaveable(limits) {
        mutableStateOf(
            listOf(
                limits.user,
                limits.character,
                limits.relationship,
                limits.shared
            ).map(Int::toString)
        )
    }
    val numbers = values.map { it.toIntOrNull() }
    val valid = numbers.all { it != null && it in 0..100 }
    ArchiveCard {
        Text("长期认知常驻数量", style = MaterialTheme.typography.titleMedium)
        Text(
            "默认 12 / 10 / 10 / 8。只限制每轮常驻读取，仍受本轮预算约束；不会删除历史认知。",
            style = MaterialTheme.typography.bodySmall
        )
        listOf("关于对方", "关于自己", "彼此关系", "共同背景").forEachIndexed { index, label ->
            OutlinedTextField(
                values[index],
                { value -> values = values.toMutableList().also { it[index] = value } },
                Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = numbers[index]?.let { it !in 0..100 } ?: true)
        }
        if (!valid) Text("每类填写 0–100。", color = MaterialTheme.colorScheme.error)
        ArchiveAction(
            "保存认知数量",
            {
                onUpdate(
                    MemoryResidentLimits(
                        numbers[0]!!,
                        numbers[1]!!,
                        numbers[2]!!,
                        numbers[3]!!
                    )
                )
            },
            valid
        )
    }
}
