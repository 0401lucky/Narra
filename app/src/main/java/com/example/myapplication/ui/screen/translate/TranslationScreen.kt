package com.example.myapplication.ui.screen.translate

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.viewmodel.DefaultAutoDetectLanguage
import com.example.myapplication.viewmodel.TranslationPageUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    uiState: TranslationPageUiState,
    supportedLanguages: List<String>,
    onInputTextChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancelTranslation: () -> Unit,
    onPasteText: (String) -> Unit,
    onSelectHistoryItem: (TranslationHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onUpdateTranslationModel: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var historySearchQuery by rememberSaveable { mutableStateOf("") }

    val filteredHistory = remember(uiState.history, historySearchQuery) {
        val query = historySearchQuery.trim().lowercase()
        if (query.isBlank()) {
            uiState.history
        } else {
            uiState.history.filter { entry ->
                entry.sourceText.lowercase().contains(query) ||
                    entry.translatedText.lowercase().contains(query) ||
                    entry.sourceLanguage.lowercase().contains(query) ||
                    entry.targetLanguage.lowercase().contains(query) ||
                    entry.modelName.lowercase().contains(query)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    NarraIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                title = {
                    Text("翻译")
                },
                actions = {
                    if (uiState.history.isNotEmpty()) {
                        NarraIconButton(onClick = { showHistorySheet = true }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "翻译记录",
                            )
                        }
                    }
                    NarraIconButton(onClick = { showModelSheet = true }) {
                        if (uiState.activeModelName.isNotBlank()) {
                            ModelIcon(
                                modelName = uiState.activeModelName,
                                size = 22.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "选择翻译模型",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = {
            AppSnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TranslationBottomBar(
                targetLanguage = uiState.targetLanguage,
                isTranslating = uiState.isTranslating,
                onOpenLanguageSheet = { showLanguageSheet = true },
                onTranslate = onTranslate,
                onCancelTranslation = onCancelTranslation,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("请输入要翻译的文本...")
                    },
                    minLines = 8,
                    maxLines = 14,
                    singleLine = false,
                    shape = RoundedCornerShape(24.dp),
                )
            }

            item {
                NarraFilledTonalButton(
                    onClick = {
                        val text = clipboardManager.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            onPasteText(text)
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text("粘贴文本", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                if (uiState.inputText.isNotBlank()) {
                    Text(
                        text = "自动检测：${uiState.detectedSourceLanguageLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = uiState.translatedText.ifBlank {
                        if (uiState.isTranslating) "翻译中…" else "翻译结果将显示在这里"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (uiState.translatedText.isNotBlank()) {
                item {
                    NarraOutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.translatedText))
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("复制翻译结果", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (showLanguageSheet) {
        SelectionSheet(
            title = "选择目标语言",
            options = supportedLanguages.filterNot { it == DefaultAutoDetectLanguage },
            currentValue = uiState.targetLanguage,
            onDismissRequest = { showLanguageSheet = false },
            onSelect = {
                onTargetLanguageChange(it)
                showLanguageSheet = false
            },
        )
    }

    if (showModelSheet) {
        SelectionSheet(
            title = "选择翻译模型",
            options = uiState.availableModels,
            currentValue = uiState.activeModelName,
            onDismissRequest = { showModelSheet = false },
            onSelect = {
                onUpdateTranslationModel(it)
                showModelSheet = false
            },
        )
    }

    if (showHistorySheet) {
        TranslationHistorySheet(
            history = filteredHistory,
            historySearchQuery = historySearchQuery,
            onHistorySearchQueryChange = { historySearchQuery = it },
            onSelectHistoryItem = {
                onSelectHistoryItem(it)
                showHistorySheet = false
            },
            onClearHistory = {
                onClearHistory()
                historySearchQuery = ""
            },
            onDismissRequest = { showHistorySheet = false },
        )
    }
}

@Composable
private fun TranslationBottomBar(
    targetLanguage: String,
    isTranslating: Boolean,
    onOpenLanguageSheet: () -> Unit,
    onTranslate: () -> Unit,
    onCancelTranslation: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NarraFilledTonalButton(
                onClick = onOpenLanguageSheet,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(targetLanguage)
            }

            NarraFilledTonalButton(
                onClick = {
                    if (isTranslating) onCancelTranslation() else onTranslate()
                },
                shape = RoundedCornerShape(999.dp),
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("取消", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Icon(Icons.Default.Translate, contentDescription = null)
                    Text("翻译", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSheet(
    title: String,
    options: List<String>,
    currentValue: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(options, key = { it }) { option ->
                AssistChip(
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    leadingIcon = if (option == currentValue) {
                        {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationHistorySheet(
    history: List<TranslationHistoryEntry>,
    historySearchQuery: String,
    onHistorySearchQueryChange: (String) -> Unit,
    onSelectHistoryItem: (TranslationHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "翻译记录",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (history.isNotEmpty()) {
                        NarraOutlinedButton(onClick = onClearHistory) {
                            Text("清空记录")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = onHistorySearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索原文、译文、语言或模型") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                )
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        text = "暂无匹配记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        onClick = { onSelectHistoryItem(entry) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${entry.sourceLanguage} → ${entry.targetLanguage}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = entry.sourceText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.translatedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.modelName.isNotBlank()) {
                                Text(
                                    text = entry.modelName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
