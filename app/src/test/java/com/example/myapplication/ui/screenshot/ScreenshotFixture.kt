package com.example.myapplication.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.deriveImmersiveGlassPalette

/**
 * 视觉回归安全网的合成 fixture。
 *
 * 硬性约束：固定日期与时间戳、固定角色/会话/消息文本、本地 Canvas 绘制图片；
 * 不读取真实数据库、用户文件、网络头像，也不使用 System.currentTimeMillis 或 Random。
 * 所有用例文本集中在这里，测试与断言都引用同一份常量，避免散落字符串漂移。
 */
object ScreenshotFixture {

    /** 常规宿主：360x800dp、density 2、fontScale 1、简体中文。 */
    const val HostQualifiers = "zh-rCN-w360dp-h800dp-xhdpi"

    /** 窄屏宿主：320x640dp、density 2，配合 fontScale 2。 */
    const val NarrowQualifiers = "zh-rCN-w320dp-h640dp-xhdpi"

    const val HostDensity = 2f
    const val NormalFontScale = 1f
    const val NarrowFontScale = 2f

    /** 2026-10-14 12:00:00 Asia/Shanghai，避免使用系统当前时间。 */
    const val FixedEpochMillis = 1_791_950_400_000L
    private const val TenMinutesMillis = 10 * 60 * 1000L
    private const val OneDayMillis = 24 * 60 * 60 * 1000L

    // ---- 角色 ----

    val SuwanAssistant = Assistant(
        id = "shot-assistant-suwan",
        name = "苏晚",
        iconName = "auto_stories",
        description = "夜班便利店店员",
        tags = listOf("都市", "慢热"),
        memoryEnabled = true,
    )

    val LinchenAssistant = Assistant(
        id = "shot-assistant-linchen",
        name = "林澈",
        iconName = "code",
        description = "独立开发者",
        tags = listOf("理性", "熬夜"),
    )

    val XiazhiAssistant = Assistant(
        id = "shot-assistant-xiazhi",
        name = "夏栀",
        iconName = "music_note",
        description = "音乐系学生",
        tags = listOf("活泼", "琴房"),
    )

    val Assistants = listOf(SuwanAssistant, LinchenAssistant, XiazhiAssistant)

    // ---- 会话 ----

    const val SuwanScenarioId = "shot-scenario-suwan"
    const val LinchenScenarioId = "shot-scenario-linchen"
    const val XiazhiScenarioId = "shot-scenario-xiazhi"

    const val SuwanScenarioTitle = "雨夜便利店"
    const val LinchenScenarioTitle = "凌晨的代码"
    const val XiazhiScenarioTitle = "琴房回声"

    val SuwanScenario = onlineScenario(SuwanScenarioId, SuwanAssistant.id, SuwanScenarioTitle)
    val LinchenScenario = onlineScenario(LinchenScenarioId, LinchenAssistant.id, LinchenScenarioTitle)
    val XiazhiScenario = onlineScenario(
        id = XiazhiScenarioId,
        assistantId = XiazhiAssistant.id,
        title = XiazhiScenarioTitle,
        isPinned = true,
    )

    fun onlineScenario(
        id: String,
        assistantId: String,
        title: String,
        backgroundUri: String = "",
        isPinned: Boolean = false,
    ) = RoleplayScenario(
        id = id,
        title = title,
        assistantId = assistantId,
        backgroundUri = backgroundUri,
        interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        enableNarration = true,
        enableTimeAwareness = true,
        isPinned = isPinned,
        createdAt = FixedEpochMillis - OneDayMillis,
        updatedAt = FixedEpochMillis,
    )

    /** “已配置背景但未加载成功”的语义：uri 非空、imageBitmap 为 null。 */
    const val MissingBackgroundUri = "file:///narra-screenshot/missing-background.png"

    const val SuwanLastMessage = "「今天也辛苦啦。」她把热牛奶推到你面前。"
    const val LinchenLastMessage = "构建又失败了，我再看一眼日志。"
    const val XiazhiLastMessage = "明天下午琴房见，我占了三点那间。"

    val ChatSummaries = listOf(
        summary(
            scenario = SuwanScenario,
            lastMessageText = SuwanLastMessage,
            lastActiveAt = FixedEpochMillis,
            lastMessageRole = MessageRole.ASSISTANT,
            sessionId = "shot-session-suwan",
        ),
        summary(
            scenario = LinchenScenario,
            lastMessageText = LinchenLastMessage,
            lastActiveAt = FixedEpochMillis - TenMinutesMillis,
            lastMessageRole = MessageRole.ASSISTANT,
            sessionId = "shot-session-linchen",
        ),
        summary(
            scenario = XiazhiScenario,
            lastMessageText = XiazhiLastMessage,
            lastActiveAt = FixedEpochMillis - 2 * TenMinutesMillis,
            lastMessageRole = MessageRole.USER,
            sessionId = "shot-session-xiazhi",
        ),
    )

    /** 会话首页里角色名与标题的展示格式（P01 的 ChatSummaryRow 规则）。 */
    const val SuwanDisplayName = "苏晚 / $SuwanScenarioTitle"

    /** P02 搜索无匹配时使用的固定关键词（合成角色名都不包含它）。 */
    const val NoMatchQuery = "这个关键词不会命中任何角色"

    fun summary(
        scenario: RoleplayScenario,
        lastMessageText: String,
        lastActiveAt: Long,
        lastMessageRole: MessageRole,
        sessionId: String,
    ) = RoleplayChatSummary(
        scenario = scenario,
        session = RoleplaySession(
            id = sessionId,
            scenarioId = scenario.id,
            conversationId = "shot-conversation-${scenario.id}",
            createdAt = FixedEpochMillis - OneDayMillis,
            updatedAt = lastActiveAt,
        ),
        lastMessageText = lastMessageText,
        lastMessageAt = lastActiveAt,
        lastActiveAt = lastActiveAt,
        lastMessageRole = lastMessageRole,
    )

    // ---- 消息 ----

    const val UserLineText = "我到店门口了，雨还在下。"
    const val CharacterLineText = "「先进来吧。」她递过来一条干毛巾。"
    const val NarrationLineText = "玻璃门上的水痕把霓虹拉成一条条细线。"
    const val StreamingPartialText = "她把手里的伞往你那边倾了倾，「雨好像更大了，要不"
    const val ErrorMessageText = "网络连接失败，这一回合没有生成完整回复。"

    val StreamingPartialMessage = characterMessage(
        id = "streaming-1",
        content = StreamingPartialText,
        isStreaming = true,
    )

    val ErrorMessage = characterMessage(
        id = "error-1",
        content = ErrorMessageText,
        status = MessageStatus.ERROR,
    )

    /** H03 长回复：跨多屏的固定文本，段落拆分为明确的行。 */
    val LongReplyText: String = (1..12).joinToString("\n\n") { index ->
        "第 $index 段：她把今天的见闻慢慢讲了一遍，从便利店门口的雨，到收银台上那盏忽明忽暗的灯，" +
            "再到凌晨两点最后一个客人离开时的脚步声。"
    }

    fun userMessage(
        id: String,
        content: String,
        createdAt: Long = FixedEpochMillis,
    ) = RoleplayMessageUiModel(
        uiItemId = "shot-$id",
        sourceMessageId = "shot-$id",
        contentType = RoleplayContentType.DIALOGUE,
        speaker = RoleplaySpeaker.USER,
        speakerName = "我",
        content = content,
        createdAt = createdAt,
    )

    fun characterMessage(
        id: String,
        content: String,
        createdAt: Long = FixedEpochMillis,
        isStreaming: Boolean = false,
        status: MessageStatus = MessageStatus.COMPLETED,
        emotion: String = "",
    ) = RoleplayMessageUiModel(
        uiItemId = "shot-$id",
        sourceMessageId = "shot-$id",
        contentType = RoleplayContentType.DIALOGUE,
        speaker = RoleplaySpeaker.CHARACTER,
        speakerName = SuwanAssistant.name,
        speakerIconName = SuwanAssistant.iconName,
        content = content,
        emotion = emotion,
        createdAt = createdAt,
        isStreaming = isStreaming,
        messageStatus = status,
    )

    fun narrationMessage(
        id: String,
        content: String,
        createdAt: Long = FixedEpochMillis,
    ) = RoleplayMessageUiModel(
        uiItemId = "shot-$id",
        sourceMessageId = "shot-$id",
        contentType = RoleplayContentType.NARRATION,
        speaker = RoleplaySpeaker.NARRATOR,
        speakerName = "",
        content = content,
        createdAt = createdAt,
    )

    /** 固定舞台消息：用户 / 角色 / 旁白各一条，时间戳间隔小于 20 分钟。 */
    fun stageMessages(): List<RoleplayMessageUiModel> = listOf(
        userMessage("b-skin-user", UserLineText),
        characterMessage("b-skin-character", CharacterLineText, createdAt = FixedEpochMillis + TenMinutesMillis),
        narrationMessage("b-skin-narration", NarrationLineText, createdAt = FixedEpochMillis + 2 * TenMinutesMillis),
    )

    // ---- 背景与玻璃 ----

    const val BackdropWidthPx = 720
    const val BackdropHeightPx = 1600
    const val NarrowBackdropWidthPx = 640
    const val NarrowBackdropHeightPx = 1280

    /**
     * 与生产完全同源的调色板：有背景位图时由 [deriveImmersiveGlassPalette] 从图片采样派生，
     * 无背景（null）时自动走主题回退，随应用主题与深浅色变化；固定输入保证确定性。
     */
    @Composable
    fun backdropState(
        image: ImageBitmap?,
        widthPx: Int = BackdropWidthPx,
        heightPx: Int = BackdropHeightPx,
    ) = ImmersiveBackdropState(
        imageBitmap = image,
        screenSize = IntSize(widthPx, heightPx),
        palette = deriveImmersiveGlassPalette(
            imageBitmap = image,
            colorScheme = MaterialTheme.colorScheme,
            highContrast = false,
        ),
    )

    /** 本地 Canvas 绘制的固定夜景：上下两条色带加固定的几何图形，保证玻璃有真实取样内容。 */
    fun syntheticBackdrop(
        widthPx: Int = BackdropWidthPx,
        heightPx: Int = BackdropHeightPx,
    ): ImageBitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = android.graphics.Color.rgb(36, 58, 92)
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx * 0.6f, paint)
        paint.color = android.graphics.Color.rgb(210, 150, 90)
        canvas.drawRect(0f, heightPx * 0.6f, widthPx.toFloat(), heightPx.toFloat(), paint)
        paint.color = android.graphics.Color.rgb(120, 96, 160)
        canvas.drawCircle(widthPx * 0.72f, heightPx * 0.22f, widthPx * 0.12f, paint)
        paint.color = android.graphics.Color.rgb(58, 128, 120)
        canvas.drawRect(widthPx * 0.1f, heightPx * 0.7f, widthPx * 0.5f, heightPx * 0.86f, paint)
        return bitmap.asImageBitmap()
    }
}
