package com.example.myapplication.system.translation

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ChatApplication
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.ScreenTextBlock
import com.example.myapplication.model.ScreenTranslationRequest
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.TranslationSourceType
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.example.myapplication.ui.theme.resolveAppColorScheme
import com.example.myapplication.viewmodel.DefaultAutoDetectLanguage
import com.example.myapplication.viewmodel.TranslationViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID

class ScreenTranslatorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translationTasks = ScreenTranslationTasks(serviceScope)
    private val app by lazy { application as ChatApplication }
    private val overlayController by lazy {
        FloatingOverlayController(
            context = this,
            callback = object : FloatingOverlayController.Callback {
                override fun onFloatingBallClicked() {
                    requestScreenTranslation()
                }

                override fun onFloatingBallHidden() {
                    serviceScope.launch {
                        persistScreenTranslationSettings(
                            latestScreenSettings.copy(overlayEnabled = false),
                        )
                    }
                }

                override fun onFloatingBallPositionChanged(normalizedX: Float, normalizedY: Float) {
                    serviceScope.launch {
                        persistScreenTranslationSettings(
                            latestScreenSettings.copy(
                                overlayOffsetX = normalizedX,
                                overlayOffsetY = normalizedY,
                            ),
                        )
                    }
                }

                override fun onSelectedTextTranslationConfirmed() {
                    pendingSelectedText?.let { translateSelectedText(it) }
                }

                override fun onTargetLanguageSelected(language: String) {
                    retranslateLast(
                        requestSettings = latestScreenSettings.copy(targetLanguage = language),
                        persistSettings = true,
                    )
                }

                override fun onRetranslateLast() {
                    retranslateLast()
                }

                override fun onTranslationPanelDismissed() {
                    translationTasks.cancel()
                }
            },
        )
    }
    private val ocrEngine: OcrEngine by lazy { MlKitOcrEngine(this) }
    private val screenCaptureEngine: ScreenCaptureEngine by lazy { MediaProjectionCaptureEngine(this) }

    private var latestScreenSettings: ScreenTranslationSettings = ScreenTranslationSettings()
    private var latestAppColorTheme = AppColorTheme.MATCHA
    private var latestThemeMode = ThemeMode.SYSTEM
    private var pendingSelectedText: SelectedTextPayload? = null
    private var latestInput: CachedTranslationInput? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensureForeground()
        serviceScope.launch {
            app.aiSettingsRepository.settingsFlow.collect { settings ->
                latestScreenSettings = settings.screenTranslationSettings
                latestAppColorTheme = settings.appColorTheme
                latestThemeMode = settings.themeMode
                updateOverlayTheme()
                applyOverlayState(settings.screenTranslationSettings)
                if (!settings.screenTranslationSettings.serviceEnabled) {
                    stopSelf()
                    return@collect
                }
                ensureForeground()
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                serviceScope.launch {
                    persistScreenTranslationSettings(
                        latestScreenSettings.copy(serviceEnabled = false),
                    )
                }
                stopSelf()
            }

            ACTION_TOGGLE_OVERLAY -> {
                serviceScope.launch {
                    persistScreenTranslationSettings(
                        latestScreenSettings.copy(overlayEnabled = !latestScreenSettings.overlayEnabled),
                    )
                }
            }

            ACTION_REQUEST_SCREEN_TRANSLATION -> {
                requestScreenTranslation()
            }

            ACTION_HANDLE_CAPTURE_RESULT -> {
                val requestId = intent.getLongExtra(EXTRA_CAPTURE_REQUEST_ID, 0L)
                if (!translationTasks.consumeCapturePermission(requestId) ||
                    !latestScreenSettings.serviceEnabled || !hasOverlayPermission()
                ) {
                    return START_STICKY
                }
                val resultCode = intent.getIntExtra(EXTRA_CAPTURE_RESULT_CODE, -1)
                val dataIntent = intent.getParcelableExtraCompat<Intent>(EXTRA_CAPTURE_DATA_INTENT)
                if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                    translateCapturedScreen(
                        resultCode = resultCode,
                        dataIntent = dataIntent,
                    )
                } else {
                    overlayController.showErrorPanel(
                        appLabel = "当前屏幕",
                        sourceText = "",
                        message = "未授予屏幕截取权限。再次轻点悬浮球，可重新请求授权。",
                        targetLanguage = latestScreenSettings.targetLanguage,
                        showSourceText = latestScreenSettings.showSourceText,
                        allowRetranslate = false,
                    )
                }
            }

            ACTION_SELECTED_TEXT_READY -> {
                val text = intent.getStringExtra(EXTRA_SELECTED_TEXT).orEmpty()
                if (text.isBlank() || !latestScreenSettings.selectedTextEnabled) {
                    return START_STICKY
                }
                pendingSelectedText = SelectedTextPayload(
                    text = text,
                    sourceAppPackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE).orEmpty(),
                    sourceAppLabel = intent.getStringExtra(EXTRA_SOURCE_APP_LABEL).orEmpty(),
                )
                if (hasOverlayPermission() && latestScreenSettings.serviceEnabled) {
                    overlayController.showSelectedTextAction(
                        previewText = text,
                        appLabel = pendingSelectedText?.sourceAppLabel.orEmpty(),
                    )
                }
            }

            ACTION_SELECTED_TEXT_DISMISSED -> {
                pendingSelectedText = null
                overlayController.hideSelectedTextAction()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        translationTasks.cancel()
        overlayController.dismissAll()
        ocrEngine.close()
        screenCaptureEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOverlayTheme()
        overlayController.onConfigurationChanged()
    }

    private fun updateOverlayTheme() {
        val darkTheme = when (latestThemeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        overlayController.updateTheme(resolveAppColorScheme(latestAppColorTheme, darkTheme))
    }

    private fun requestScreenTranslation() {
        if (!latestScreenSettings.serviceEnabled) {
            return
        }
        if (!hasOverlayPermission()) {
            return
        }
        if (screenCaptureEngine.hasActiveSession()) {
            translateCapturedScreen()
            return
        }
        val requestId = translationTasks.beginCapturePermission() ?: return
        latestInput = null
        overlayController.hideResultPanel()
        overlayController.hideSelectedTextAction()
        val intent = ScreenCapturePermissionActivity.createIntent(this, requestId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun translateCapturedScreen(
        resultCode: Int? = null,
        dataIntent: Intent? = null,
    ) {
        val requestSettings = latestScreenSettings
        latestInput = null
        overlayController.dismissAll()
        translationTasks.launch {
            try {
                if (!screenCaptureEngine.hasActiveSession()) {
                    val safeResultCode = resultCode ?: throw IllegalStateException("缺少屏幕共享授权结果")
                    val safeIntent = dataIntent ?: throw IllegalStateException("缺少屏幕共享授权数据")
                    promoteForMediaProjection()
                    withContext(Dispatchers.IO) {
                        screenCaptureEngine.startSession(safeResultCode, safeIntent)
                    }
                }
                promoteForMediaProjection()
                val sourceAppLabel = ForegroundAppSnapshot.appLabel.ifBlank { "当前屏幕" }
                val sourceAppPackage = ForegroundAppSnapshot.packageName
                // 先让悬浮窗从画面移除，再截取；否则 OCR 会读到上一次译文和加载面板。
                delay(120)
                val bitmap = withContext(Dispatchers.IO) {
                    screenCaptureEngine.capture()
                }
                val blocks = try {
                    applyOverlayState(latestScreenSettings)
                    ensureActive()
                    overlayController.showLoadingPanel(
                        appLabel = sourceAppLabel,
                        sourceText = "正在识别屏幕文本…",
                        targetLanguage = requestSettings.targetLanguage,
                        showSourceText = requestSettings.showSourceText,
                    )
                    withContext(Dispatchers.Default) {
                        ocrEngine.recognize(bitmap)
                    }
                } finally {
                    bitmap.recycle()
                }

                if (blocks.isEmpty()) {
                    overlayController.showErrorPanel(
                        appLabel = sourceAppLabel,
                        sourceText = "",
                        message = "未识别到可翻译文本。试着放大文字或切换页面，再轻点悬浮球。",
                        targetLanguage = requestSettings.targetLanguage,
                        showSourceText = requestSettings.showSourceText,
                        allowRetranslate = false,
                    )
                    return@launch
                }

                val request = ScreenTranslationRequest(
                    sourceType = TranslationSourceType.SCREEN_CAPTURE,
                    appPackage = sourceAppPackage,
                    appLabel = sourceAppLabel,
                    targetLanguage = requestSettings.targetLanguage,
                    segments = blocks,
                )
                latestInput = CachedTranslationInput.ScreenCapture(
                    appPackage = sourceAppPackage,
                    appLabel = sourceAppLabel,
                    segments = blocks,
                )
                val translationResult = withContext(Dispatchers.IO) {
                    app.aiTranslationService.translateStructuredSegments(request)
                }

                val sourceText = blocks.joinToString(separator = "\n") { it.text }
                val translatedText = translationResult.fullTranslation.ifBlank {
                    translationResult.translatedSegments.joinToString(separator = "\n") { it.translatedText }
                }
                overlayController.showTranslationResult(
                    appLabel = sourceAppLabel,
                    sourceText = sourceText,
                    translatedText = translatedText,
                    targetLanguage = requestSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = requestSettings.showSourceText,
                    allowRetranslate = true,
                )
                appendHistoryEntry(
                    sourceText = sourceText,
                    translatedText = translatedText,
                    sourceType = TranslationSourceType.SCREEN_CAPTURE,
                    sourceAppPackage = sourceAppPackage,
                    sourceAppLabel = sourceAppLabel,
                    targetLanguage = requestSettings.targetLanguage,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                ensureActive()
                if (throwable.message?.contains("当前没有可用的屏幕共享会话") == true) {
                    screenCaptureEngine.release()
                }
                overlayController.showErrorPanel(
                    appLabel = ForegroundAppSnapshot.appLabel.ifBlank { "当前屏幕" },
                    sourceText = "",
                    message = SensitiveTextRedactor.throwableMessageForUi(throwable, "屏幕翻译失败，请稍后重试。"),
                    targetLanguage = requestSettings.targetLanguage,
                    showSourceText = requestSettings.showSourceText,
                    allowRetranslate = latestInput != null,
                )
            } finally {
                if (isActive) {
                    applyOverlayState(latestScreenSettings)
                    if (!screenCaptureEngine.hasActiveSession()) ensureForeground()
                }
            }
        }
    }

    private fun translateSelectedText(
        payload: SelectedTextPayload,
        requestSettings: ScreenTranslationSettings = latestScreenSettings,
        persistSettings: Boolean = false,
    ) {
        overlayController.hideSelectedTextAction()
        translationTasks.launch {
            try {
                applyOverlayState(latestScreenSettings)
                ensureActive()
                latestInput = CachedTranslationInput.SelectedText(
                    text = payload.text,
                    appPackage = payload.sourceAppPackage,
                    appLabel = payload.sourceAppLabel,
                )
                overlayController.showLoadingPanel(
                    appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                    sourceText = payload.text,
                    targetLanguage = requestSettings.targetLanguage,
                    showSourceText = requestSettings.showSourceText,
                )
                if (persistSettings) persistScreenTranslationSettings(requestSettings)
                ensureActive()
                var latestTranslation = ""
                withContext(Dispatchers.IO) {
                    app.aiTranslationService.translateTextStream(
                        text = payload.text,
                        targetLanguage = requestSettings.targetLanguage,
                        sourceLanguage = DefaultAutoDetectLanguage,
                    ).collect { translated ->
                        latestTranslation = translated
                        withContext(Dispatchers.Main.immediate) {
                            overlayController.showLoadingPanel(
                                appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                                sourceText = payload.text,
                                translatedText = translated,
                                targetLanguage = requestSettings.targetLanguage,
                                showSourceText = requestSettings.showSourceText,
                            )
                        }
                    }
                }
                if (latestTranslation.isNotBlank()) {
                    overlayController.showTranslationResult(
                        appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                        sourceText = payload.text,
                        translatedText = latestTranslation,
                        targetLanguage = requestSettings.targetLanguage,
                        availableLanguages = supportedTargetLanguages(),
                        showSourceText = requestSettings.showSourceText,
                        allowRetranslate = true,
                    )
                    appendHistoryEntry(
                        sourceText = payload.text,
                        translatedText = latestTranslation,
                        sourceType = TranslationSourceType.SELECTED_TEXT,
                        sourceAppPackage = payload.sourceAppPackage,
                        sourceAppLabel = payload.sourceAppLabel,
                        targetLanguage = requestSettings.targetLanguage,
                    )
                } else {
                    overlayController.showErrorPanel(
                        appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                        sourceText = payload.text,
                        message = "暂未收到译文，请重试。",
                        targetLanguage = requestSettings.targetLanguage,
                        showSourceText = requestSettings.showSourceText,
                        allowRetranslate = true,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                ensureActive()
                overlayController.showErrorPanel(
                    appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                    sourceText = payload.text,
                    message = SensitiveTextRedactor.throwableMessageForUi(throwable, "选中文本翻译失败，请稍后重试。"),
                    targetLanguage = requestSettings.targetLanguage,
                    showSourceText = requestSettings.showSourceText,
                    allowRetranslate = true,
                )
            }
        }
    }

    private fun retranslateLast(
        requestSettings: ScreenTranslationSettings = latestScreenSettings,
        persistSettings: Boolean = false,
    ) {
        val input = latestInput ?: return
        if (input is CachedTranslationInput.SelectedText) {
            translateSelectedText(
                SelectedTextPayload(
                    text = input.text,
                    sourceAppPackage = input.appPackage,
                    sourceAppLabel = input.appLabel,
                ),
                requestSettings = requestSettings,
                persistSettings = persistSettings,
            )
            return
        }
        input as CachedTranslationInput.ScreenCapture
        translationTasks.launch {
            val sourceText = input.segments.joinToString(separator = "\n") { it.text }
            overlayController.showLoadingPanel(
                appLabel = input.appLabel.ifBlank { "当前屏幕" },
                sourceText = sourceText,
                targetLanguage = requestSettings.targetLanguage,
                showSourceText = requestSettings.showSourceText,
            )
            try {
                if (persistSettings) persistScreenTranslationSettings(requestSettings)
                ensureActive()
                val translationResult = withContext(Dispatchers.IO) {
                    app.aiTranslationService.translateStructuredSegments(
                        ScreenTranslationRequest(
                            sourceType = TranslationSourceType.SCREEN_CAPTURE,
                            appPackage = input.appPackage,
                            appLabel = input.appLabel,
                            targetLanguage = requestSettings.targetLanguage,
                            segments = input.segments,
                        ),
                    )
                }
                val translatedText = translationResult.fullTranslation.ifBlank {
                    translationResult.translatedSegments.joinToString(separator = "\n") { it.translatedText }
                }
                overlayController.showTranslationResult(
                    appLabel = input.appLabel.ifBlank { "当前屏幕" },
                    sourceText = sourceText,
                    translatedText = translatedText,
                    targetLanguage = requestSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = requestSettings.showSourceText,
                    allowRetranslate = true,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                ensureActive()
                overlayController.showErrorPanel(
                    appLabel = input.appLabel.ifBlank { "当前屏幕" },
                    sourceText = sourceText,
                    message = SensitiveTextRedactor.throwableMessageForUi(throwable, "重译失败，请稍后重试。"),
                    targetLanguage = requestSettings.targetLanguage,
                    showSourceText = requestSettings.showSourceText,
                    allowRetranslate = true,
                )
            }
        }
    }

    private suspend fun appendHistoryEntry(
        sourceText: String,
        translatedText: String,
        sourceType: TranslationSourceType,
        sourceAppPackage: String,
        sourceAppLabel: String,
        targetLanguage: String,
    ) {
        val settings = app.aiSettingsRepository.settingsFlow.first()
        val activeModel = settings.activeProvider()
            ?.resolveFunctionModel(com.example.myapplication.model.ProviderFunction.TRANSLATION)
            .orEmpty()
            .ifBlank { "未启用" }
        val entry = TranslationHistoryEntry(
            id = UUID.randomUUID().toString(),
            sourceText = sourceText,
            translatedText = translatedText,
            sourceLanguage = DefaultAutoDetectLanguage,
            targetLanguage = targetLanguage,
            modelName = activeModel,
            createdAt = System.currentTimeMillis(),
            sourceType = sourceType,
            sourceAppPackage = sourceAppPackage,
            sourceAppLabel = sourceAppLabel,
        )
        val deduplicatedHistory = listOf(entry) + settings.translationHistory.filterNot {
            it.sourceText == entry.sourceText &&
                it.targetLanguage == entry.targetLanguage &&
                it.sourceType == entry.sourceType
        }
        app.aiSettingsEditor.saveTranslationHistory(deduplicatedHistory)
    }

    private suspend fun persistScreenTranslationSettings(settings: ScreenTranslationSettings) {
        app.aiSettingsEditor.saveScreenTranslationSettings(settings)
        currentCoroutineContext().ensureActive()
        latestScreenSettings = settings
        applyOverlayState(settings)
        ensureForeground()
    }

    private fun applyOverlayState(settings: ScreenTranslationSettings) {
        if (!settings.serviceEnabled || !hasOverlayPermission()) {
            translationTasks.cancel()
            overlayController.dismissAll()
            return
        }
        if (settings.overlayEnabled) {
            overlayController.showFloatingBall(
                normalizedX = settings.overlayOffsetX,
                normalizedY = settings.overlayOffsetY,
            )
        } else {
            overlayController.hideFloatingBall()
        }
    }

    private fun supportedTargetLanguages(): List<String> {
        return TranslationViewModel.SupportedLanguages.filterNot { it == DefaultAutoDetectLanguage }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun ensureForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun promoteForMediaProjection() {
        val notification = buildNotification(contentText = "正在识别当前屏幕…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String = "悬浮翻译待命中") = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("悬浮翻译")
        .setContentText(contentText)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "翻译屏幕",
            PendingIntent.getService(
                this,
                2,
                Intent(this, ScreenTranslatorService::class.java).setAction(ACTION_REQUEST_SCREEN_TRANSLATION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            if (latestScreenSettings.overlayEnabled) "暂停悬浮球" else "恢复悬浮球",
            PendingIntent.getService(
                this,
                3,
                Intent(this, ScreenTranslatorService::class.java).setAction(ACTION_TOGGLE_OVERLAY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "关闭服务",
            PendingIntent.getService(
                this,
                4,
                Intent(this, ScreenTranslatorService::class.java).setAction(ACTION_STOP_SERVICE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "悬浮翻译",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "用于维持后台悬浮翻译服务"
            },
        )
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    private data class SelectedTextPayload(
        val text: String,
        val sourceAppPackage: String,
        val sourceAppLabel: String,
    )

    private sealed interface CachedTranslationInput {
        data class ScreenCapture(
            val appPackage: String,
            val appLabel: String,
            val segments: List<ScreenTextBlock>,
        ) : CachedTranslationInput

        data class SelectedText(
            val text: String,
            val appPackage: String,
            val appLabel: String,
        ) : CachedTranslationInput
    }

    companion object {
        private const val CHANNEL_ID = "screen_translation"
        private const val NOTIFICATION_ID = 2204
        private const val ACTION_START_SERVICE = "screen_translation.start"
        const val ACTION_STOP_SERVICE = "screen_translation.stop"
        const val ACTION_TOGGLE_OVERLAY = "screen_translation.toggle_overlay"
        const val ACTION_REQUEST_SCREEN_TRANSLATION = "screen_translation.request_screen"
        const val ACTION_HANDLE_CAPTURE_RESULT = "screen_translation.handle_capture"
        const val ACTION_SELECTED_TEXT_READY = "screen_translation.selected_text_ready"
        const val ACTION_SELECTED_TEXT_DISMISSED = "screen_translation.selected_text_dismissed"
        const val EXTRA_CAPTURE_RESULT_CODE = "extra_capture_result_code"
        const val EXTRA_CAPTURE_DATA_INTENT = "extra_capture_data_intent"
        private const val EXTRA_CAPTURE_REQUEST_ID = "extra_capture_request_id"
        const val EXTRA_SELECTED_TEXT = "extra_selected_text"
        const val EXTRA_SOURCE_PACKAGE = "extra_source_package"
        const val EXTRA_SOURCE_APP_LABEL = "extra_source_app_label"

        fun startService(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenTranslatorService::class.java).setAction(ACTION_START_SERVICE),
            )
        }

        fun stopService(context: Context) {
            context.startService(
                Intent(context, ScreenTranslatorService::class.java).setAction(ACTION_STOP_SERVICE),
            )
        }

        fun submitCapturePermissionResult(
            context: Context,
            resultCode: Int,
            dataIntent: Intent?,
            requestId: Long = 0L,
        ) {
            context.startService(
                Intent(context, ScreenTranslatorService::class.java)
                    .setAction(ACTION_HANDLE_CAPTURE_RESULT)
                    .putExtra(EXTRA_CAPTURE_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_CAPTURE_DATA_INTENT, dataIntent)
                    .putExtra(EXTRA_CAPTURE_REQUEST_ID, requestId),
            )
        }

        fun submitSelectedText(
            context: Context,
            text: String,
            sourcePackage: String,
            sourceAppLabel: String,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenTranslatorService::class.java)
                    .setAction(ACTION_SELECTED_TEXT_READY)
                    .putExtra(EXTRA_SELECTED_TEXT, text)
                    .putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .putExtra(EXTRA_SOURCE_APP_LABEL, sourceAppLabel),
            )
        }

        fun dismissSelectedTextPrompt(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenTranslatorService::class.java)
                    .setAction(ACTION_SELECTED_TEXT_DISMISSED),
            )
        }
    }
}
