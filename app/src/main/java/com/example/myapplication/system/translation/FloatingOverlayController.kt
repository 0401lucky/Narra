package com.example.myapplication.system.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingOverlayController(
    private val context: Context,
    private val callback: Callback,
) {
    private companion object {
        const val SelectedTextActionAutoHideMillis = 4_000L
    }

    interface Callback {
        fun onFloatingBallClicked()
        fun onFloatingBallHidden()
        fun onFloatingBallPositionChanged(normalizedX: Float, normalizedY: Float)
        fun onSelectedTextTranslationConfirmed()
        fun onTargetLanguageSelected(language: String)
        fun onRetranslateLast()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private var floatingBallView: View? = null
    private var selectedTextActionView: View? = null
    private var resultPanelView: View? = null
    private val hideSelectedTextActionRunnable = Runnable {
        hideSelectedTextAction()
    }

    fun showFloatingBall(
        normalizedX: Float,
        normalizedY: Float,
    ) {
        val view = floatingBallView ?: createFloatingBallView().also {
            floatingBallView = it
            windowManager.addView(it, createBallLayoutParams(normalizedX, normalizedY))
            return
        }
        val params = view.layoutParams as WindowManager.LayoutParams
        val position = resolveBallPosition(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            viewWidth = view.measuredWidth.takeIf { it > 0 } ?: dp(56),
            viewHeight = view.measuredHeight.takeIf { it > 0 } ?: dp(56),
        )
        params.x = position.first
        params.y = position.second
        windowManager.updateViewLayout(view, params)
    }

    fun hideFloatingBall() {
        floatingBallView?.let(::removeOverlayView)
        floatingBallView = null
    }

    fun showSelectedTextAction(
        previewText: String,
        appLabel: String,
    ) {
        val view = selectedTextActionView ?: createSelectedTextActionView().also {
            selectedTextActionView = it
            windowManager.addView(it, createTopBannerLayoutParams())
        }
        val titleView = view.findViewWithTag<TextView>("selected-title")
        titleView.text = buildString {
            append("来自 ")
            append(appLabel.ifBlank { "当前应用" })
            append(" 的选中文本")
            if (previewText.isNotBlank()) {
                append("：")
                append(previewText.take(18))
            }
        }
        view.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideSelectedTextActionRunnable)
        mainHandler.postDelayed(
            hideSelectedTextActionRunnable,
            SelectedTextActionAutoHideMillis,
        )
    }

    fun hideSelectedTextAction() {
        mainHandler.removeCallbacks(hideSelectedTextActionRunnable)
        selectedTextActionView?.let(::removeOverlayView)
        selectedTextActionView = null
    }

    fun showLoadingPanel(
        appLabel: String,
        sourceText: String,
        targetLanguage: String,
        showSourceText: Boolean,
    ) {
        ensureResultPanel()
        bindPanel(
            title = "翻译中",
            appLabel = appLabel,
            sourceText = sourceText,
            translatedText = "正在生成译文…",
            targetLanguage = targetLanguage,
            showSourceText = showSourceText,
            allowRetranslate = false,
            availableLanguages = emptyList(),
        )
    }

    fun showTranslationResult(
        appLabel: String,
        sourceText: String,
        translatedText: String,
        targetLanguage: String,
        availableLanguages: List<String>,
        showSourceText: Boolean,
        allowRetranslate: Boolean,
    ) {
        ensureResultPanel()
        bindPanel(
            title = "翻译结果",
            appLabel = appLabel,
            sourceText = sourceText,
            translatedText = translatedText,
            targetLanguage = targetLanguage,
            showSourceText = showSourceText,
            allowRetranslate = allowRetranslate,
            availableLanguages = availableLanguages,
        )
    }

    fun hideResultPanel() {
        resultPanelView?.let(::removeOverlayView)
        resultPanelView = null
    }

    fun dismissAll() {
        hideFloatingBall()
        hideSelectedTextAction()
        hideResultPanel()
    }

    private fun ensureResultPanel() {
        if (resultPanelView != null) {
            return
        }
        val view = createResultPanelView()
        resultPanelView = view
        windowManager.addView(view, createResultPanelLayoutParams())
    }

    private fun bindPanel(
        title: String,
        appLabel: String,
        sourceText: String,
        translatedText: String,
        targetLanguage: String,
        showSourceText: Boolean,
        allowRetranslate: Boolean,
        availableLanguages: List<String>,
    ) {
        val view = resultPanelView ?: return
        view.findViewWithTag<TextView>("panel-title").text = title
        view.findViewWithTag<TextView>("panel-app").text = appLabel.ifBlank { "当前应用" }
        view.findViewWithTag<TextView>("panel-source").apply {
            text = sourceText.ifBlank { "无原文内容" }
            visibility = if (showSourceText) View.VISIBLE else View.GONE
        }
        view.findViewWithTag<TextView>("panel-result").text = translatedText.ifBlank { "暂无译文" }
        view.findViewWithTag<Button>("panel-language").apply {
            text = targetLanguage
            setOnClickListener { anchor ->
                if (availableLanguages.isEmpty()) return@setOnClickListener
                PopupMenu(context, anchor).apply {
                    availableLanguages.forEachIndexed { index, language ->
                        menu.add(0, index, index, language)
                    }
                    setOnMenuItemClickListener { item ->
                        callback.onTargetLanguageSelected(item.title.toString())
                        true
                    }
                }.show()
            }
        }
        view.findViewWithTag<Button>("panel-copy").setOnClickListener {
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("screen-translation", translatedText),
            )
            Toast.makeText(context, "译文已复制", Toast.LENGTH_SHORT).show()
        }
        view.findViewWithTag<Button>("panel-retry").apply {
            visibility = if (allowRetranslate) View.VISIBLE else View.GONE
            setOnClickListener { callback.onRetranslateLast() }
        }
        view.findViewWithTag<Button>("panel-close").setOnClickListener {
            hideResultPanel()
        }
    }

    private fun createFloatingBallView(): View {
        val textView = TextView(context).apply {
            text = "译"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3B82F6"))
            }
            elevation = dp(10).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        textView.setOnLongClickListener {
            callback.onFloatingBallHidden()
            true
        }
        textView.setOnTouchListener { view, event ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) {
                        moved = true
                    }
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        callback.onFloatingBallClicked()
                    } else {
                        snapBallToEdge(view, params)
                    }
                    true
                }

                else -> false
            }
        }
        return textView
    }

    private fun snapBallToEdge(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        val metrics = context.resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: dp(56)
        val viewHeight = view.height.takeIf { it > 0 } ?: dp(56)
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = if (params.x + viewWidth / 2 >= metrics.widthPixels / 2) {
            maxX
        } else {
            0
        }
        params.y = params.y.coerceIn(0, maxY)
        windowManager.updateViewLayout(view, params)
        callback.onFloatingBallPositionChanged(
            normalizedX = if (maxX == 0) 0f else params.x.toFloat() / maxX,
            normalizedY = if (maxY == 0) 0f else params.y.toFloat() / maxY,
        )
    }

    private fun createSelectedTextActionView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            elevation = dp(8).toFloat()

            addView(
                TextView(context).apply {
                    tag = "selected-title"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(
                Button(context).apply {
                    text = "翻译"
                    setOnClickListener {
                        mainHandler.removeCallbacks(hideSelectedTextActionRunnable)
                        callback.onSelectedTextTranslationConfirmed()
                    }
                },
            )
        }
    }

    private fun createResultPanelView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#F8FAFC"))
                setStroke(dp(1), Color.parseColor("#CBD5E1"))
            }
            elevation = dp(12).toFloat()

            addView(
                TextView(context).apply {
                    tag = "panel-title"
                    setTextColor(Color.parseColor("#0F172A"))
                    textSize = 18f
                },
            )
            addView(
                TextView(context).apply {
                    tag = "panel-app"
                    setTextColor(Color.parseColor("#475569"))
                    textSize = 13f
                },
            )
            addView(
                ScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(220),
                    ).apply {
                        topMargin = dp(12)
                    }
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(
                                TextView(context).apply {
                                    tag = "panel-source"
                                    setTextColor(Color.parseColor("#334155"))
                                    textSize = 14f
                                },
                            )
                            addView(
                                TextView(context).apply {
                                    tag = "panel-result"
                                    setTextColor(Color.parseColor("#0F172A"))
                                    textSize = 16f
                                    setLineSpacing(0f, 1.16f)
                                },
                            )
                        },
                    )
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(14)
                    }
                    addView(
                        Button(context).apply {
                            tag = "panel-language"
                            text = "简体中文"
                        },
                    )
                    addView(
                        Button(context).apply {
                            tag = "panel-copy"
                            text = "复制"
                        },
                    )
                    addView(
                        Button(context).apply {
                            tag = "panel-retry"
                            text = "重译"
                        },
                    )
                    addView(
                        Button(context).apply {
                            tag = "panel-close"
                            text = "关闭"
                        },
                    )
                },
            )
        }
    }

    private fun createBallLayoutParams(
        normalizedX: Float,
        normalizedY: Float,
    ): WindowManager.LayoutParams {
        val (x, y) = resolveBallPosition(normalizedX, normalizedY, dp(56), dp(56))
        return WindowManager.LayoutParams(
            dp(56),
            dp(56),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun createTopBannerLayoutParams(): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        return WindowManager.LayoutParams(
            (metrics.widthPixels * 0.82f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(88)
        }
    }

    private fun createResultPanelLayoutParams(): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        return WindowManager.LayoutParams(
            (metrics.widthPixels * 0.9f).toInt().coerceAtMost(dp(360)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun resolveBallPosition(
        normalizedX: Float,
        normalizedY: Float,
        viewWidth: Int,
        viewHeight: Int,
    ): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        val x = (maxX * normalizedX.coerceIn(0f, 1f)).toInt()
        val y = (maxY * normalizedY.coerceIn(0f, 1f)).toInt()
        return x to y
    }

    private fun removeOverlayView(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
