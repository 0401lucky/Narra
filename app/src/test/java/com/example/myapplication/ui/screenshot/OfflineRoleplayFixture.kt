package com.example.myapplication.ui.screenshot

import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel

/** P04 合成内容；线上 fixture 与全部既有基准参数保持独立。 */
object OfflineRoleplayFixture {
    val DialogueScenario = ScreenshotFixture.SuwanScenario.copy(
        interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
    )
    const val LongName = "苏晚与雨夜便利店的漫长故事"
    val LongformScenario = DialogueScenario.copy(interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM)
    const val ThoughtText = "她希望这场雨能下得慢一些。"
    val MixedMessages = ScreenshotFixture.stageMessages() + listOf(
        ScreenshotFixture.characterMessage("offline-thought", ThoughtText)
            .copy(contentType = RoleplayContentType.THOUGHT),
        ScreenshotFixture.characterMessage("offline-longform", "雨声渐缓。\n\n「明天还会来吗？」她轻声问。")
            .copy(contentType = RoleplayContentType.LONGFORM),
    )
    val Suggestions = listOf(
        RoleplaySuggestionUiModel("offline-suggestion-plot", "走进灯光里",
            "我接过毛巾，走到柜台前，问她今晚店里有没有发生什么有趣的事。", RoleplaySuggestionAxis.PLOT),
        RoleplaySuggestionUiModel("offline-suggestion-emotion", "表达关心",
            "我把伞收好，轻声问她一个人守着这间店会不会有些寂寞。", RoleplaySuggestionAxis.EMOTION),
        RoleplaySuggestionUiModel("offline-suggestion-info", "问起雨夜",
            "我看向窗外，问她这场雨已经下了多久。", RoleplaySuggestionAxis.INFO),
    )
}
