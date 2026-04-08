package com.example.myapplication.ui.screen.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.ui.component.copyPlainTextToClipboard

private data class ChatPreviewConsoleEntry(
    val level: String,
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMessageActionSheet(
    message: ChatMessage,
    onDismissRequest: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onOpenPreview: (() -> Unit)?,
    onOpenSearchResults: (() -> Unit)?,
    onExportMarkdown: () -> Unit,
    onShareMessage: () -> Unit,
    onEditUserMessage: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildMessagePreviewTitle(message),
                style = MaterialTheme.typography.titleLarge,
            )

            MessageActionCard(
                title = "选中复制",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    onDismissRequest()
                    onSelectAndCopy()
                },
            )

            if (onOpenPreview != null) {
                MessageActionCard(
                    title = "渲染预览",
                    icon = Icons.Default.Visibility,
                    onClick = {
                        onDismissRequest()
                        onOpenPreview()
                    },
                )
            }

            if (onOpenSearchResults != null) {
                MessageActionCard(
                    title = "查看搜索结果",
                    icon = Icons.Default.Search,
                    onClick = {
                        onDismissRequest()
                        onOpenSearchResults()
                    },
                )
            }

            MessageActionCard(
                title = "导出 Markdown",
                icon = Icons.Default.Description,
                onClick = {
                    onDismissRequest()
                    onExportMarkdown()
                },
            )

            MessageActionCard(
                title = "系统分享",
                icon = Icons.Default.Share,
                onClick = {
                    onDismissRequest()
                    onShareMessage()
                },
            )

            onEditUserMessage?.let { onEdit ->
                MessageActionCard(
                    title = "编辑后重发",
                    icon = Icons.Default.Edit,
                    onClick = {
                        onDismissRequest()
                        onEdit()
                    },
                )
            }

            onRegenerate?.let { onRetry ->
                MessageActionCard(
                    title = "重新生成",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        onDismissRequest()
                        onRetry()
                    },
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            ) {
                Text(
                    text = buildMessageActionMetaLine(message),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSearchResultPreviewSheet(
    payload: ChatSearchResultPreviewPayload,
    onDismissRequest: () -> Unit,
    onOpenUrlPreview: (String, String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                top = 8.dp,
                end = 18.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "搜索词：${payload.query.ifBlank { "未记录" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (payload.answer.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "搜索摘要",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = payload.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            items(payload.items) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenUrlPreview(
                                    item.url,
                                    item.title.ifBlank { item.url },
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.title.ifBlank { item.url },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (item.snippet.isNotBlank()) {
                            Text(
                                text = item.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = buildString {
                                append(item.sourceLabel.ifBlank { "搜索结果" })
                                append(" · ")
                                append(item.url)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMessageSelectionSheet(
    payload: ChatMessageSelectionPayload,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = payload.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "复制全文",
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            clipboardScope.copyPlainTextToClipboard(
                                clipboard = clipboard,
                                label = "message-selection",
                                text = payload.content,
                            )
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ) {
                SelectionContainer {
                    Text(
                        text = payload.content.ifBlank { "暂无可复制内容" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ChatMessagePreviewDialog(
    payload: ChatMessagePreviewPayload,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val consoleMessages = remember { mutableStateListOf<ChatPreviewConsoleEntry>() }
    var currentUrl by remember(payload) {
        mutableStateOf(
            when (payload) {
                is ChatMessagePreviewPayload.ExternalUrlPreview -> payload.url
                is ChatMessagePreviewPayload.MessageHtmlPreview -> payload.baseUrl
            },
        )
    }
    var pageTitle by remember(payload) { mutableStateOf(payload.title) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showConsoleSheet by rememberSaveable { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle.ifBlank { payload.title },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭预览",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward,
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "前进")
                        }
                        IconButton(
                            onClick = {
                                val browseUrl = currentUrl
                                if (browseUrl.startsWith("http://") || browseUrl.startsWith("https://")) {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse(browseUrl),
                                        ),
                                    )
                                }
                            },
                            enabled = currentUrl.startsWith("http://") || currentUrl.startsWith("https://"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "浏览器打开",
                            )
                        }
                        IconButton(onClick = { showConsoleSheet = true }) {
                            Icon(Icons.Default.Code, contentDescription = "控制台日志")
                        }
                    },
                )
            },
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { androidContext ->
                    WebView(androidContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title?.takeIf { it.isNotBlank() } ?: payload.title
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val message = consoleMessage ?: return false
                                consoleMessages += ChatPreviewConsoleEntry(
                                    level = message.messageLevel().name,
                                    message = message.message(),
                                    sourceId = message.sourceId().orEmpty(),
                                    lineNumber = message.lineNumber(),
                                )
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                currentUrl = url.orEmpty().ifBlank { currentUrl }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                        }
                        when (payload) {
                            is ChatMessagePreviewPayload.ExternalUrlPreview -> loadUrl(payload.url)
                            is ChatMessagePreviewPayload.MessageHtmlPreview -> {
                                loadDataWithBaseURL(
                                    payload.baseUrl,
                                    payload.html,
                                    "text/html",
                                    "utf-8",
                                    null,
                                )
                            }
                        }
                        webViewRef = this
                    }
                },
                update = { view ->
                    webViewRef = view
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                },
            )
        }
    }

    if (showConsoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "控制台日志",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (consoleMessages.isEmpty()) {
                    Text(
                        text = "当前没有控制台日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(consoleMessages.toList()) { entry ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${entry.level} · ${entry.sourceId}:${entry.lineNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = entry.message,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(payload) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

private fun buildMessageActionMetaLine(message: ChatMessage): String {
    val attachmentCount = message.parts.count { it.type != com.example.myapplication.model.ChatMessagePartType.TEXT }
        .takeIf { it > 0 }
        ?: message.attachments.size.takeIf { it > 0 }
        ?: 0
    return buildString {
        append(formatMessageCreatedAt(message.createdAt))
        if (message.modelName.isNotBlank()) {
            append(" · ").append(message.modelName)
        }
        if (message.reasoningSteps.isNotEmpty() || message.reasoningContent.isNotBlank()) {
            append(" · 思考 ")
            append(message.reasoningSteps.size.takeIf { it > 0 } ?: 1)
            append(" 段")
        }
        if (message.citations.isNotEmpty()) {
            append(" · 引用 ").append(message.citations.size).append(" 条")
        }
        if (attachmentCount > 0) {
            append(" · 附件 ").append(attachmentCount).append(" 个")
        }
    }
}
