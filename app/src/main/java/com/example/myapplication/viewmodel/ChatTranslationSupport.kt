package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiTranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object ChatTranslationSupport {
    fun startTranslation(
        scope: CoroutineScope,
        translationService: AiTranslationService,
        sourceText: String,
        onStart: () -> Unit,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        onStart()
        scope.launch {
            runCatching {
                translationService.translateText(sourceText)
            }.onSuccess { translatedText ->
                onSuccess(translatedText)
            }.onFailure { throwable ->
                onFailure(throwable.message ?: "翻译失败")
            }
        }
    }
}
