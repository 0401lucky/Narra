package com.example.myapplication.system.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.ColorScheme
import androidx.core.graphics.ColorUtils
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.ui.theme.resolveAppColorScheme
import kotlin.math.abs

class FloatingOverlayController internal constructor(
    private val context: Context,
    private val callback: Callback,
    private val windowManager: WindowManager,
    private val overlayType: Int,
    private val windowToken: IBinder?,
    private val canShowOverlays: () -> Boolean,
) {
    constructor(context: Context, callback: Callback) : this(
        context = context,
        callback = callback,
        windowManager = context.getSystemService(WindowManager::class.java),
        overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        windowToken = null,
        canShowOverlays = { Settings.canDrawOverlays(context) },
    )

    interface Callback {
        fun onFloatingBallClicked()
        fun onFloatingBallHidden()
        fun onFloatingBallPositionChanged(normalizedX: Float, normalizedY: Float)
        fun onSelectedTextTranslationConfirmed()
        fun onTargetLanguageSelected(language: String)
        fun onRetranslateLast()
        fun onTranslationPanelDismissed()
    }

    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var colors = FloatingTranslationColors.from(resolveAppColorScheme(AppColorTheme.MATCHA, darkTheme = false))
    private var floatingBallView: TextView? = null
    private var selectedTextActionView: FloatingSelectedTextAction? = null
    private var resultPanelView: FloatingTranslationPanel? = null
    private var ballPosition = 0.85f to 0.42f
    private var selectedText: Pair<String, String>? = null
    private var panelState: FloatingTranslationPanelState? = null
    private val hideSelectedTextActionRunnable = Runnable { hideSelectedTextAction() }

    fun updateTheme(colorScheme: ColorScheme) {
        val updated = FloatingTranslationColors.from(colorScheme)
        if (colors == updated) return
        colors = updated
        floatingBallView?.let(::styleBall)
        resultPanelView?.applyColors(colors)
        selectedText?.let { (preview, label) ->
            hideSelectedTextAction()
            showSelectedTextAction(preview, label)
        }
    }

    fun onConfigurationChanged() {
        val showBall = floatingBallView != null
        val selected = selectedText
        val panel = panelState
        dismissAll()
        if (showBall) showFloatingBall(ballPosition.first, ballPosition.second)
        selected?.let { (preview, label) -> showSelectedTextAction(preview, label) }
        panel?.let(::showPanel)
    }

    fun showFloatingBall(normalizedX: Float, normalizedY: Float) {
        if (!canShowOverlays()) return
        ballPosition = normalizedX to normalizedY
        val geometry = geometry()
        val (x, y) = geometry.ballPosition(normalizedX, normalizedY)
        val existing = floatingBallView
        if (existing == null) {
            val view = createFloatingBallView()
            val params = layoutParams(geometry.ballSize, geometry.ballSize, Gravity.TOP or Gravity.START).apply {
                this.x = x
                this.y = y
            }
            if (attach(view, params)) floatingBallView = view
        } else {
            val params = existing.layoutParams as WindowManager.LayoutParams
            params.x = x
            params.y = y
            windowManager.updateViewLayout(existing, params)
        }
    }

    fun hideFloatingBall() {
        floatingBallView?.let(::removeOverlayView)
        floatingBallView = null
    }

    fun showSelectedTextAction(previewText: String, appLabel: String) {
        if (!canShowOverlays()) return
        val view = selectedTextActionView ?: FloatingSelectedTextAction(
            context = context,
            colors = colors,
            onTranslate = {
                hideSelectedTextAction()
                callback.onSelectedTextTranslationConfirmed()
            },
            onDismiss = ::hideSelectedTextAction,
        ).also {
            val geometry = geometry()
            val params = layoutParams(geometry.panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { y = geometry.bannerOffset }
            if (!attach(it, params)) return
            selectedTextActionView = it
        }
        selectedText = previewText to appLabel
        view.bind(previewText, appLabel)
        mainHandler.removeCallbacks(hideSelectedTextActionRunnable)
        val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(
                SELECTED_TEXT_AUTO_HIDE_MILLIS,
                AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
            )
        } else {
            SELECTED_TEXT_AUTO_HIDE_MILLIS
        }
        mainHandler.postDelayed(hideSelectedTextActionRunnable, timeout.toLong())
    }

    fun hideSelectedTextAction() {
        mainHandler.removeCallbacks(hideSelectedTextActionRunnable)
        selectedTextActionView?.let(::removeOverlayView)
        selectedTextActionView = null
        selectedText = null
    }

    fun showLoadingPanel(
        appLabel: String,
        sourceText: String,
        targetLanguage: String,
        showSourceText: Boolean,
        translatedText: String = "",
    ) = showPanel(FloatingTranslationPanelState(
        phase = FloatingTranslationPhase.LOADING,
        appLabel = appLabel,
        sourceText = sourceText,
        translatedText = translatedText,
        targetLanguage = targetLanguage,
        showSourceText = showSourceText,
    ))

    fun showTranslationResult(
        appLabel: String,
        sourceText: String,
        translatedText: String,
        targetLanguage: String,
        availableLanguages: List<String>,
        showSourceText: Boolean,
        allowRetranslate: Boolean,
    ) = showPanel(FloatingTranslationPanelState(
        phase = FloatingTranslationPhase.RESULT,
        appLabel = appLabel,
        sourceText = sourceText,
        translatedText = translatedText,
        targetLanguage = targetLanguage,
        showSourceText = showSourceText,
        availableLanguages = availableLanguages,
        allowRetranslate = allowRetranslate,
    ))

    fun showErrorPanel(
        appLabel: String,
        sourceText: String,
        message: String,
        targetLanguage: String,
        showSourceText: Boolean,
        allowRetranslate: Boolean,
    ) = showPanel(FloatingTranslationPanelState(
        phase = FloatingTranslationPhase.ERROR,
        appLabel = appLabel,
        sourceText = sourceText,
        translatedText = message,
        targetLanguage = targetLanguage,
        showSourceText = showSourceText,
        allowRetranslate = allowRetranslate,
    ))

    fun hideResultPanel() {
        resultPanelView?.let(::removeOverlayView)
        resultPanelView = null
        panelState = null
    }

    fun dismissAll() {
        hideFloatingBall()
        hideSelectedTextAction()
        hideResultPanel()
    }

    private fun showPanel(state: FloatingTranslationPanelState) {
        if (!canShowOverlays()) return
        val panel = resultPanelView ?: FloatingTranslationPanel(
            context = context,
            colors = colors,
            onDismiss = {
                hideResultPanel()
                callback.onTranslationPanelDismissed()
            },
            onCopy = { text ->
                clipboardManager.setPrimaryClip(ClipData.newPlainText("screen-translation", text))
                Toast.makeText(context, "译文已复制", Toast.LENGTH_SHORT).show()
            },
            onRetry = callback::onRetranslateLast,
            onLanguageSelected = callback::onTargetLanguageSelected,
        ).also {
            val geometry = geometry()
            if (!attach(it, layoutParams(geometry.panelWidth, geometry.panelHeight, Gravity.CENTER))) return
            resultPanelView = it
        }
        panelState = state
        panel.bind(state)
    }

    private fun createFloatingBallView(): TextView {
        val ball = TextView(context).apply {
            tag = "translation-ball"
            text = "译"
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            contentDescription = "屏幕翻译，轻点翻译，长按隐藏"
            elevation = context.overlayDp(8).toFloat()
            setOnClickListener { callback.onFloatingBallClicked() }
            setOnLongClickListener {
                hideFloatingBall()
                callback.onFloatingBallHidden()
                true
            }
        }
        styleBall(ball)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onSingleTapUp(event: MotionEvent): Boolean = ball.performClick()
            override fun onLongPress(event: MotionEvent) { ball.performLongClick() }
        })
        ball.setOnTouchListener { view, event ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) moved = true
                    if (moved) {
                        view.isPressed = false
                        val (x, y) = geometry().clampBall(initialX + deltaX, initialY + deltaY)
                        params.x = x
                        params.y = y
                        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    if (moved && view.isAttachedToWindow) {
                        ballPosition = geometry().snapBall(params.x, params.y)
                        showFloatingBall(ballPosition.first, ballPosition.second)
                        callback.onFloatingBallPositionChanged(ballPosition.first, ballPosition.second)
                    }
                }
            }
            detector.onTouchEvent(event)
            true
        }
        return ball
    }

    private fun styleBall(view: TextView) {
        view.setTextColor(colors.onPrimary)
        view.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.onPrimary, 48)),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(this@FloatingOverlayController.colors.primary)
            },
            null,
        )
    }

    private fun layoutParams(width: Int, height: Int, gravity: Int) = WindowManager.LayoutParams(
        width, height, overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        token = windowToken
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        }
    }

    private fun geometry(): FloatingOverlayGeometry {
        val metrics = context.resources.displayMetrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val window = windowManager.currentWindowMetrics
            val insets = window.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return FloatingOverlayGeometry(
                width = window.bounds.width() - insets.left - insets.right,
                height = window.bounds.height() - insets.top - insets.bottom,
                density = metrics.density,
            )
        }
        return FloatingOverlayGeometry(metrics.widthPixels, metrics.heightPixels, metrics.density)
    }

    private fun attach(view: View, params: WindowManager.LayoutParams): Boolean = try {
        windowManager.addView(view, params)
        true
    } catch (_: WindowManager.BadTokenException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun removeOverlayView(view: View) {
        try {
            // addView 后首帧之前也可能收到关闭或主题更新，不能只检查 isAttachedToWindow。
            windowManager.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // 宿主窗口已销毁。
        }
    }

    private companion object {
        const val SELECTED_TEXT_AUTO_HIDE_MILLIS = 4_000
    }
}
