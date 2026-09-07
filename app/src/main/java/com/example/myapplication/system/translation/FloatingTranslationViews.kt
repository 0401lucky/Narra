package com.example.myapplication.system.translation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import kotlin.math.roundToInt

internal data class FloatingTranslationColors(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val container: Int,
    val muted: Int,
    val outline: Int,
    val error: Int,
) {
    companion object {
        fun from(scheme: ColorScheme) = FloatingTranslationColors(
            primary = scheme.primary.toArgb(),
            onPrimary = scheme.onPrimary.toArgb(),
            surface = scheme.surface.toArgb(),
            onSurface = scheme.onSurface.toArgb(),
            container = scheme.surfaceContainerHigh.toArgb(),
            muted = scheme.onSurfaceVariant.toArgb(),
            outline = scheme.outlineVariant.toArgb(),
            error = scheme.error.toArgb(),
        )
    }
}

internal enum class FloatingTranslationPhase { LOADING, RESULT, ERROR }

internal data class FloatingTranslationPanelState(
    val phase: FloatingTranslationPhase,
    val appLabel: String,
    val sourceText: String,
    val translatedText: String,
    val targetLanguage: String,
    val showSourceText: Boolean,
    val availableLanguages: List<String> = emptyList(),
    val allowRetranslate: Boolean = false,
)

/** 原生视图只显示状态、发出动作；翻译、取消与持久化仍由 Service 持有。 */
internal class FloatingTranslationPanel(
    context: Context,
    private var colors: FloatingTranslationColors,
    private val onDismiss: () -> Unit,
    private val onCopy: (String) -> Unit,
    private val onRetry: () -> Unit,
    private val onLanguageSelected: (String) -> Unit,
) : LinearLayout(context) {
    private var state: FloatingTranslationPanelState? = null
    private var showingHelp = false
    private var choosingLanguage = false
    private var actionPending = false
    private val title = label(18f, bold = true).apply { tag = "panel-title"; maxLines = 2 }
    private val app = label(13f).apply { tag = "panel-app"; maxLines = 2 }
    private val language = actionButton(context, "", "panel-language") {
        choosingLanguage = !choosingLanguage
        updateContentVisibility()
    }
    private val sourceLabel = label(12f, bold = true).apply { text = "原文" }
    private val source = label(14f).apply { tag = "panel-source" }
    private val resultLabel = label(12f, bold = true)
    private val result = label(16f).apply {
        tag = "panel-result"
        setLineSpacing(0f, 1.2f)
    }
    private val progress = ProgressBar(context).apply {
        tag = "panel-progress"
        isIndeterminate = true
        contentDescription = "正在翻译"
    }
    private val reading = column()
    private val languages = column().apply { tag = "panel-languages" }
    private val helpText = label(14f).apply {
        tag = "panel-help-text"
        text = "轻点悬浮球翻译当前屏幕，拖动可调整位置，长按可隐藏悬浮球。\n\n" +
            "选中文字后，点“翻译”读取该选区。结果中的目标语言可以切换，原文是否显示由设置决定。\n\n" +
            "翻译进行中可随时取消。复制只复制译文；关闭面板不会关闭后台服务。可在通知栏恢复悬浮球或关闭服务。"
        setLineSpacing(0f, 1.2f)
    }
    private val scroll = ScrollView(context).apply {
        tag = "panel-scroll"
        isFillViewport = false
        clipToPadding = false
    }
    private val primary = actionButton(context, "复制译文", "panel-copy") {
        if (showingHelp) {
            showingHelp = false
            updateContentVisibility()
        } else {
            state?.takeIf { it.phase == FloatingTranslationPhase.RESULT && it.translatedText.isNotBlank() }
                ?.let { onCopy(it.translatedText) }
        }
    }
    private val secondary = actionButton(context, "重译", "panel-retry") {
        when {
            state?.phase == FloatingTranslationPhase.LOADING -> onDismiss()
            state?.allowRetranslate == true && !actionPending -> {
                actionPending = true
                updateActions()
                onRetry()
            }
            else -> onDismiss()
        }
    }
    private val close = iconButton(context, OverlayIcon.CLOSE, "关闭翻译面板", "panel-close", onDismiss)
    private val help = iconButton(context, OverlayIcon.HELP, "悬浮翻译：查看说明", "panel-help") {
        showingHelp = !showingHelp
        choosingLanguage = false
        updateContentVisibility()
    }

    init {
        tag = "translation-panel"
        orientation = VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(16))
        elevation = dp(8).toFloat()
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(help, LayoutParams(dp(48), dp(48)))
            addView(close, LayoutParams(dp(48), dp(48)))
        })
        reading.apply {
            addView(app)
            addView(language, fullWidthParams(8))
            addView(sourceLabel, fullWidthParams(16))
            addView(source, fullWidthParams(8))
            addView(resultLabel, fullWidthParams(16))
            addView(progress, LayoutParams(dp(24), dp(24)).apply { topMargin = dp(8) })
            addView(result, fullWidthParams(8))
        }
        scroll.addView(column().apply {
            addView(reading)
            addView(languages)
            addView(helpText)
        })
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(primary, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(secondary, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        }, fullWidthParams(8))
        applyColors(colors)
    }

    fun bind(newState: FloatingTranslationPanelState) {
        val previousState = state
        state = newState
        actionPending = false
        if (newState.phase == FloatingTranslationPhase.LOADING && previousState?.phase != newState.phase) {
            choosingLanguage = false
            showingHelp = false
            scroll.scrollTo(0, 0)
        }
        app.text = "来自 ${newState.appLabel.ifBlank { "当前应用" }}"
        language.text = "译为 · ${newState.targetLanguage}"
        language.contentDescription = "目标语言：${newState.targetLanguage}，点按切换"
        source.text = newState.sourceText
        val showSource = newState.showSourceText && newState.sourceText.isNotBlank()
        sourceLabel.visibility = if (showSource) VISIBLE else GONE
        source.visibility = sourceLabel.visibility
        resultLabel.text = if (newState.phase == FloatingTranslationPhase.ERROR) "暂时无法翻译" else "译文"
        resultLabel.setTextColor(if (newState.phase == FloatingTranslationPhase.ERROR) colors.error else colors.muted)
        result.text = newState.translatedText.ifBlank {
            if (newState.phase == FloatingTranslationPhase.LOADING) "正在生成译文…" else "暂无译文"
        }
        progress.visibility = if (newState.phase == FloatingTranslationPhase.LOADING) VISIBLE else GONE
        close.contentDescription = if (newState.phase == FloatingTranslationPhase.LOADING) "取消翻译并关闭" else "关闭翻译面板"
        if (previousState?.availableLanguages != newState.availableLanguages || previousState?.targetLanguage != newState.targetLanguage) {
            rebuildLanguages(newState)
        }
        updateContentVisibility()
    }

    fun applyColors(newColors: FloatingTranslationColors) {
        colors = newColors
        background = overlaySurface(context, colors)
        listOf(title, result, helpText).forEach { it.setTextColor(colors.onSurface) }
        listOf(app, sourceLabel, source, resultLabel).forEach { it.setTextColor(colors.muted) }
        if (state?.phase == FloatingTranslationPhase.ERROR) resultLabel.setTextColor(colors.error)
        progress.indeterminateTintList = ColorStateList.valueOf(colors.primary)
        styleIconButton(close, colors)
        styleIconButton(help, colors)
        styleActionButton(language, colors, primary = false)
        styleActionButton(primary, colors, primary = true)
        styleActionButton(secondary, colors, primary = false)
        state?.let(::rebuildLanguages)
    }

    private fun rebuildLanguages(current: FloatingTranslationPanelState) {
        languages.removeAllViews()
        current.availableLanguages.distinct().filter(String::isNotBlank).forEach { value ->
            languages.addView(actionButton(context, value, "panel-language-$value") {
                if (value == state?.targetLanguage) {
                    choosingLanguage = false
                    updateContentVisibility()
                } else if (!actionPending) {
                    actionPending = true
                    choosingLanguage = false
                    updateContentVisibility()
                    onLanguageSelected(value)
                }
            }.apply {
                isSelected = value == current.targetLanguage
                text = if (isSelected) "$value · 当前" else value
                styleActionButton(this, colors, primary = isSelected)
            }, fullWidthParams(8))
        }
    }

    private fun updateContentVisibility() {
        val readingVisibility = if (!showingHelp && !choosingLanguage) VISIBLE else GONE
        val languagesVisibility = if (choosingLanguage) VISIBLE else GONE
        val helpVisibility = if (showingHelp) VISIBLE else GONE
        val contentChanged = reading.visibility != readingVisibility ||
            languages.visibility != languagesVisibility || helpText.visibility != helpVisibility
        title.text = when {
            showingHelp -> "使用说明"
            choosingLanguage -> "翻译为"
            state?.phase == FloatingTranslationPhase.LOADING -> "正在翻译"
            state?.phase == FloatingTranslationPhase.ERROR -> "暂未完成"
            else -> "翻译结果"
        }
        reading.visibility = readingVisibility
        languages.visibility = languagesVisibility
        helpText.visibility = helpVisibility
        updateActions()
        if (contentChanged) {
            // 共用滚动容器时，帮助和语言列表不能继承长译文末尾的位置。
            scroll.scrollTo(0, 0)
            scroll.doOnLayout { scroll.scrollTo(0, 0) }
        }
    }

    private fun updateActions() {
        val current = state ?: return
        val loading = current.phase == FloatingTranslationPhase.LOADING
        primary.text = if (showingHelp) "知道了" else "复制译文"
        primary.visibility = if (showingHelp || current.phase != FloatingTranslationPhase.ERROR) VISIBLE else GONE
        primary.isEnabled = showingHelp || (current.phase == FloatingTranslationPhase.RESULT && current.translatedText.isNotBlank())
        secondary.visibility = if (showingHelp) GONE else VISIBLE
        (secondary.layoutParams as LayoutParams).marginStart = if (primary.visibility == GONE) 0 else dp(8)
        secondary.text = when {
            loading -> "取消翻译"
            current.allowRetranslate -> "重译"
            else -> "关闭"
        }
        secondary.isEnabled = !actionPending
        language.isEnabled = !loading && !actionPending && current.availableLanguages.isNotEmpty()
    }

    private fun label(size: Float, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun column() = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun fullWidthParams(top: Int = 0) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int) = context.overlayDp(value)
}

internal class FloatingSelectedTextAction(
    context: Context,
    colors: FloatingTranslationColors,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
) : LinearLayout(context) {
    private val title = TextView(context).apply {
        tag = "selected-title"
        textSize = 13f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(colors.muted)
    }
    private val preview = TextView(context).apply {
        tag = "selected-preview"
        textSize = 16f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(colors.onSurface)
    }
    private val translate = actionButton(context, "翻译选中文字", "selected-translate") { onTranslate() }

    init {
        tag = "selected-text-action"
        orientation = VERTICAL
        setPadding(context.overlayDp(16), context.overlayDp(8), context.overlayDp(16), context.overlayDp(16))
        elevation = context.overlayDp(8).toFloat()
        background = overlaySurface(context, colors)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(context, OverlayIcon.CLOSE, "关闭选中文本翻译", "selected-close", onDismiss).apply {
                styleIconButton(this, colors)
            }, LayoutParams(context.overlayDp(48), context.overlayDp(48)))
        })
        addView(preview)
        addView(translate, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.overlayDp(8)
        })
        styleActionButton(translate, colors, primary = true)
    }

    fun bind(previewText: String, appLabel: String) {
        title.text = "${appLabel.ifBlank { "当前应用" }} · 选中文本"
        preview.text = previewText
    }
}

internal fun Context.overlayDp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun overlaySurface(context: Context, colors: FloatingTranslationColors) = GradientDrawable().apply {
    cornerRadius = context.overlayDp(8).toFloat()
    setColor(colors.surface)
    setStroke(context.overlayDp(1), colors.outline)
}

internal fun styleActionButton(view: Button, colors: FloatingTranslationColors, primary: Boolean) {
    val foreground = if (primary) colors.onPrimary else colors.onSurface
    view.setTextColor(ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(ColorUtils.setAlphaComponent(foreground, 100), foreground),
    ))
    view.background = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 32)),
        GradientDrawable().apply {
            cornerRadius = view.context.overlayDp(8).toFloat()
            setColor(if (primary) colors.primary else colors.container)
        },
        null,
    )
}

private fun actionButton(context: Context, label: String, testTag: String, onClick: () -> Unit) = Button(context).apply {
    tag = testTag
    text = label
    textSize = 14f
    isAllCaps = false
    includeFontPadding = false
    minHeight = context.overlayDp(48)
    minimumHeight = context.overlayDp(48)
    minWidth = context.overlayDp(48)
    minimumWidth = context.overlayDp(48)
    setPadding(context.overlayDp(16), context.overlayDp(8), context.overlayDp(16), context.overlayDp(8))
    stateListAnimator = null
    backgroundTintList = null
    setOnClickListener { onClick() }
}

private enum class OverlayIcon { CLOSE, HELP }

private fun iconButton(context: Context, icon: OverlayIcon, description: String, testTag: String, onClick: () -> Unit) = ImageButton(context).apply {
    tag = testTag
    contentDescription = description
    setPadding(context.overlayDp(12), context.overlayDp(12), context.overlayDp(12), context.overlayDp(12))
    setImageDrawable(OverlayIconDrawable(icon, context.overlayDp(24)))
    setOnClickListener { onClick() }
}

private fun styleIconButton(view: ImageButton, colors: FloatingTranslationColors) {
    view.setColorFilter(colors.muted)
    view.backgroundTintList = null
    view.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.primary, 32)), null, GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colors.surface)
    })
}

private class OverlayIconDrawable(private val icon: OverlayIcon, private val size: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        if (icon == OverlayIcon.CLOSE) {
            canvas.drawLine(6f, 6f, 18f, 18f, paint)
            canvas.drawLine(18f, 6f, 6f, 18f, paint)
        } else {
            canvas.drawCircle(12f, 12f, 9f, paint)
            canvas.drawLine(12f, 11f, 12f, 16f, paint)
            canvas.drawPoint(12f, 8f, paint)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = size
    override fun getIntrinsicHeight(): Int = size
}
