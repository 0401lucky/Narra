package com.example.myapplication.system.translation

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ChatApplication
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.model.ScreenTextBlock
import com.example.myapplication.model.ScreenTranslationRequest
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.TranslationSourceType
import com.example.myapplication.viewmodel.DefaultAutoDetectLanguage
import com.example.myapplication.viewmodel.TranslationViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ScreenTranslatorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
                    pendingSelectedText?.let(::translateSelectedText)
                }

                override fun onTargetLanguageSelected(language: String) {
                    serviceScope.launch {
                        persistScreenTranslationSettings(
                            latestScreenSettings.copy(targetLanguage = language),
                        )
                        retranslateLast()
                    }
                }

                override fun onRetranslateLast() {
                    serviceScope.launch {
                        retranslateLast()
                    }
                }
            },
        )
    }
    private val ocrEngine: OcrEngine by lazy { MlKitOcrEngine(this) }
    private val screenCaptureEngine: ScreenCaptureEngine by lazy { MediaProjectionCaptureEngine(this) }

    private var latestScreenSettings: ScreenTranslationSettings = ScreenTranslationSettings()
    private var translationJob: Job? = null
    private var pendingSelectedText: SelectedTextPayload? = null
    private var latestInput: CachedTranslationInput? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensureForeground()
        serviceScope.launch {
            app.aiRepository.settingsFlow.collect { settings ->
                latestScreenSettings = settings.screenTranslationSettings
                if (!settings.screenTranslationSettings.serviceEnabled) {
                    stopSelf()
                    return@collect
                }
                applyOverlayState(settings.screenTranslationSettings)
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
                val resultCode = intent.getIntExtra(EXTRA_CAPTURE_RESULT_CODE, -1)
                val dataIntent = intent.getParcelableExtraCompat<Intent>(EXTRA_CAPTURE_DATA_INTENT)
                if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                    translateCapturedScreen(
                        resultCode = resultCode,
                        dataIntent = dataIntent,
                    )
                } else {
                    overlayController.showTranslationResult(
                        appLabel = "当前屏幕",
                        sourceText = "",
                        translatedText = "未授予屏幕截取权限，无法识别当前屏幕内容。",
                        targetLanguage = latestScreenSettings.targetLanguage,
                        availableLanguages = supportedTargetLanguages(),
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
        translationJob?.cancel()
        overlayController.dismissAll()
        ocrEngine.close()
        screenCaptureEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        val intent = ScreenCapturePermissionActivity.createIntent(this)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun translateCapturedScreen(
        resultCode: Int? = null,
        dataIntent: Intent? = null,
    ) {
        translationJob?.cancel()
        translationJob = serviceScope.launch {
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
                overlayController.showLoadingPanel(
                    appLabel = sourceAppLabel,
                    sourceText = "正在识别屏幕文本…",
                    targetLanguage = latestScreenSettings.targetLanguage,
                    showSourceText = latestScreenSettings.showSourceText,
                )
                val bitmap = withContext(Dispatchers.IO) {
                    screenCaptureEngine.capture()
                }
                val blocks = withContext(Dispatchers.Default) {
                    ocrEngine.recognize(bitmap)
                }
                bitmap.recycle()

                if (blocks.isEmpty()) {
                    overlayController.showTranslationResult(
                        appLabel = sourceAppLabel,
                        sourceText = "",
                        translatedText = "未识别到可翻译文本，请尝试切换页面、放大文字或重新截图。",
                        targetLanguage = latestScreenSettings.targetLanguage,
                        availableLanguages = supportedTargetLanguages(),
                        showSourceText = latestScreenSettings.showSourceText,
                        allowRetranslate = false,
                    )
                    return@launch
                }

                val request = ScreenTranslationRequest(
                    sourceType = TranslationSourceType.SCREEN_CAPTURE,
                    appPackage = sourceAppPackage,
                    appLabel = sourceAppLabel,
                    targetLanguage = latestScreenSettings.targetLanguage,
                    segments = blocks,
                )
                val translationResult = withContext(Dispatchers.IO) {
                    app.aiRepository.translateStructuredSegments(request)
                }
                latestInput = CachedTranslationInput.ScreenCapture(
                    appPackage = sourceAppPackage,
                    appLabel = sourceAppLabel,
                    segments = blocks,
                )

                val sourceText = blocks.joinToString(separator = "\n") { it.text }
                val translatedText = translationResult.fullTranslation.ifBlank {
                    translationResult.translatedSegments.joinToString(separator = "\n") { it.translatedText }
                }
                overlayController.showTranslationResult(
                    appLabel = sourceAppLabel,
                    sourceText = sourceText,
                    translatedText = translatedText,
                    targetLanguage = latestScreenSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = latestScreenSettings.showSourceText,
                    allowRetranslate = true,
                )
                appendHistoryEntry(
                    sourceText = sourceText,
                    translatedText = translatedText,
                    sourceType = TranslationSourceType.SCREEN_CAPTURE,
                    sourceAppPackage = sourceAppPackage,
                    sourceAppLabel = sourceAppLabel,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (throwable.message?.contains("当前没有可用的屏幕共享会话") == true) {
                    screenCaptureEngine.release()
                }
                overlayController.showTranslationResult(
                    appLabel = ForegroundAppSnapshot.appLabel.ifBlank { "当前屏幕" },
                    sourceText = "",
                    translatedText = throwable.message ?: "屏幕翻译失败",
                    targetLanguage = latestScreenSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = latestScreenSettings.showSourceText,
                    allowRetranslate = latestInput != null,
                )
            } finally {
                if (!screenCaptureEngine.hasActiveSession()) {
                    ensureForeground()
                }
            }
        }
    }

    private fun translateSelectedText(payload: SelectedTextPayload) {
        overlayController.hideSelectedTextAction()
        translationJob?.cancel()
        translationJob = serviceScope.launch {
            try {
                latestInput = CachedTranslationInput.SelectedText(
                    text = payload.text,
                    appPackage = payload.sourceAppPackage,
                    appLabel = payload.sourceAppLabel,
                )
                overlayController.showLoadingPanel(
                    appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                    sourceText = payload.text,
                    targetLanguage = latestScreenSettings.targetLanguage,
                    showSourceText = latestScreenSettings.showSourceText,
                )
                var latestTranslation = ""
                withContext(Dispatchers.IO) {
                    app.aiRepository.translateTextStream(
                        text = payload.text,
                        targetLanguage = latestScreenSettings.targetLanguage,
                        sourceLanguage = DefaultAutoDetectLanguage,
                    ).collect { translated ->
                        latestTranslation = translated
                        serviceScope.launch {
                            overlayController.showTranslationResult(
                                appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                                sourceText = payload.text,
                                translatedText = translated,
                                targetLanguage = latestScreenSettings.targetLanguage,
                                availableLanguages = supportedTargetLanguages(),
                                showSourceText = latestScreenSettings.showSourceText,
                                allowRetranslate = true,
                            )
                        }
                    }
                }
                if (latestTranslation.isNotBlank()) {
                    appendHistoryEntry(
                        sourceText = payload.text,
                        translatedText = latestTranslation,
                        sourceType = TranslationSourceType.SELECTED_TEXT,
                        sourceAppPackage = payload.sourceAppPackage,
                        sourceAppLabel = payload.sourceAppLabel,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                overlayController.showTranslationResult(
                    appLabel = payload.sourceAppLabel.ifBlank { "当前应用" },
                    sourceText = payload.text,
                    translatedText = throwable.message ?: "选中文本翻译失败",
                    targetLanguage = latestScreenSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = latestScreenSettings.showSourceText,
                    allowRetranslate = true,
                )
            }
        }
    }

    private suspend fun retranslateLast() {
        when (val input = latestInput) {
            is CachedTranslationInput.ScreenCapture -> {
                val translationResult = withContext(Dispatchers.IO) {
                    app.aiRepository.translateStructuredSegments(
                        ScreenTranslationRequest(
                            sourceType = TranslationSourceType.SCREEN_CAPTURE,
                            appPackage = input.appPackage,
                            appLabel = input.appLabel,
                            targetLanguage = latestScreenSettings.targetLanguage,
                            segments = input.segments,
                        ),
                    )
                }
                val sourceText = input.segments.joinToString(separator = "\n") { it.text }
                val translatedText = translationResult.fullTranslation.ifBlank {
                    translationResult.translatedSegments.joinToString(separator = "\n") { it.translatedText }
                }
                overlayController.showTranslationResult(
                    appLabel = input.appLabel.ifBlank { "当前屏幕" },
                    sourceText = sourceText,
                    translatedText = translatedText,
                    targetLanguage = latestScreenSettings.targetLanguage,
                    availableLanguages = supportedTargetLanguages(),
                    showSourceText = latestScreenSettings.showSourceText,
                    allowRetranslate = true,
                )
            }

            is CachedTranslationInput.SelectedText -> {
                translateSelectedText(
                    SelectedTextPayload(
                        text = input.text,
                        sourceAppPackage = input.appPackage,
                        sourceAppLabel = input.appLabel,
                    ),
                )
            }

            null -> Unit
        }
    }

    private suspend fun appendHistoryEntry(
        sourceText: String,
        translatedText: String,
        sourceType: TranslationSourceType,
        sourceAppPackage: String,
        sourceAppLabel: String,
    ) {
        val settings = app.aiRepository.settingsFlow.first()
        val activeModel = settings.activeProvider()
            ?.resolveFunctionModel(com.example.myapplication.model.ProviderFunction.TRANSLATION)
            .orEmpty()
            .ifBlank { settings.selectedModel }
        val entry = TranslationHistoryEntry(
            id = UUID.randomUUID().toString(),
            sourceText = sourceText,
            translatedText = translatedText,
            sourceLanguage = DefaultAutoDetectLanguage,
            targetLanguage = latestScreenSettings.targetLanguage,
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
        app.aiRepository.saveTranslationHistory(deduplicatedHistory)
    }

    private suspend fun persistScreenTranslationSettings(settings: ScreenTranslationSettings) {
        latestScreenSettings = settings
        app.aiRepository.saveScreenTranslationSettings(settings)
        applyOverlayState(settings)
        ensureForeground()
    }

    private fun applyOverlayState(settings: ScreenTranslationSettings) {
        if (!settings.serviceEnabled || !hasOverlayPermission()) {
            overlayController.hideFloatingBall()
            overlayController.hideSelectedTextAction()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
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
        ) {
            context.startService(
                Intent(context, ScreenTranslatorService::class.java)
                    .setAction(ACTION_HANDLE_CAPTURE_RESULT)
                    .putExtra(EXTRA_CAPTURE_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_CAPTURE_DATA_INTENT, dataIntent),
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
